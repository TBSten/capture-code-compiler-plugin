package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.userargs

import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.capturedSources.UserArgValue
import me.tbsten.capture.code.warning.CaptureCodeCompilerPluginWarning
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVarargElement
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import java.text.MessageFormat

/**
 * EXPRESSION 起源 (式 annotation) の場合、 IR phase で marker `IrConstructorCall` が残らない
 * ため、 FIR session storage から渡された引数 ([UserArgValue]) を IR 式に再構築する helper
 * class。 task-120-B Phase 4b で concrete 化、 bug-004 で再構築対象を拡張。
 *
 * ## サポートする引数種別
 *
 * - primitive: `Int`, `Long`, `Short`, `Byte`, `Boolean`, `Char`, `Float`, `Double`, `String`
 *   ([UserArgValue.IntValue] / [UserArgValue.StringValue] 等の各 branch)
 * - enum: [UserArgValue.EnumRef] (FqN を受け取り `IrGetEnumValue` を組み立てる)
 * - `::class`: [UserArgValue.ClassRef] (bug-004 で対応。 [IrClassReferenceShim] 経由で
 *   `IrClassReference` を構築する。 shim の KDoc に drift 検証の経緯を記載)
 * - 配列 literal: [UserArgValue.ArrayValue] (bug-004 で対応。
 *   [CompatContext.newIrVararg] で `IrVararg` を構築する)
 * - nested annotation: [UserArgValue.AnnotationValue] (bug-004 で対応。
 *   [CompatContext.newIrConstructorCall] + [CompatContext.putCallValueArgument] で
 *   `IrConstructorCall` を再帰構築する)
 *
 * ## 非対応 (warning + default fallback)
 *
 * - FIR phase で変換できなかった複合定数式等 ([UserArgValue.UnsupportedExpression]) は
 *   [UserArgWarnings.EXPRESSION_UNSUPPORTED] を発火して `null` を返す
 *
 * ## Preconditions
 *
 * Caller (= [me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.BuildMarkerInstance.buildSingle])
 * は以下を保証する責務がある。 違反時は `null` 返却 + caller の default fallback に倒れる設計の
 * ため、 `require(...)` での fail-fast は導入していない。
 *
 * - `value: UserArgValue?` は FIR phase の `CollectExpressionSite.collectUserArgs`
 *   (実体は `ConvertUserArgExpression`) が `linkedMapOf` に詰めた sealed 値。 `null` は
 *   「parameter override 無し」 を表し、 caller の default fallback 経路に倒れる。
 * - `parameter: IrValueParameter` は marker primary constructor の対応する parameter (IR
 *   resolution 完了済)。 `parameter.type` は IR resolved。 enum 経路では `IrSimpleType`
 *   であることが期待される (= classifier 解決可能)。 違反 (IrErrorType / IrDynamicType) は
 *   warning + null fallback。
 * - `pluginContext: IrPluginContext` は IR phase で resolved。 `referenceClass` /
 *   `irBuiltIns` を class ref / 配列 / nested annotation の symbol・型解決に使う
 *   (`BuildMarkerInstance` が同 API を marker 解決に使う proven pattern)。
 * - `compat: CompatContext` は `newIrConstPrimitive` / `newIrConstString` /
 *   `newIrGetEnumValue` / `newIrVararg` / `newIrConstructorCall` / `putCallValueArgument` /
 *   `valueParametersOf` の SPI が正しく dispatch される (= IR builder の K2.0+ host class
 *   drift を吸収)。
 * - `messageCollector: MessageCollector` は IR phase collector。 default [MessageCollector.NONE]
 *   は silent (既存 unit test 互換)。
 *
 * ## 発火する warning (すべて emit 後 `null` fallback)
 *
 * - [UserArgWarnings.ENUM_NOT_FOUND] — enum entry FqN の末尾セグメントが parameter 型の
 *   owner class に見つからない (task-134)
 * - [UserArgWarnings.CLASS_NOT_FOUND] — `::class` / nested annotation の class FqN が
 *   IR classpath で解決できない (bug-004)
 * - [UserArgWarnings.EXPRESSION_UNSUPPORTED] — FIR phase で変換不能だった式 (bug-004。
 *   旧: 誤って `CC_USERARG_ENUM_NOT_FOUND` 文面になる / Array は silent、 だった経路)
 */
internal class BuildUserArgPrimitive {

    /**
     * FIR から push された [value] を [parameter] の型に合った IR 式に変換して返す。 変換不可なら
     * warning 発火後に `null` (= 上位 `BuildMarkerInstance` が [BuildUserArg] の default 値経路に
     * fallback する)。
     *
     * task-0.2.0-cifix-ir (2026-05-19): IR `IrConst*` / `IrGetEnumValue` の構築は
     * [CompatContext] SPI 経由で行う (host class drift 吸収)。
     *
     * task-133 (2026-05-21): [value] を sealed [UserArgValue] 化し exhaustive `when` で dispatch。
     *
     * task-134 (2026-05-21): silent failure 経路を warning として [messageCollector] に通知。
     *
     * bug-004: ClassRef / ArrayValue / AnnotationValue の実値再構築を実装し、 FIR 変換不能式は
     * [UserArgValue.UnsupportedExpression] として実態に合った文面の warning にした。 再帰構築
     * (配列要素 / nested annotation 引数) のため実体は [build] に移し、 型情報は
     * [IrValueParameter] ではなく `IrType` (+ vararg element type hint) で受け渡す。
     */
    internal operator fun invoke(
        value: UserArgValue?,
        parameter: IrValueParameter,
        pluginContext: IrPluginContext,
        compat: CompatContext,
        messageCollector: MessageCollector = MessageCollector.NONE,
    ): IrExpression? {
        if (value == null) return null
        return build(
            value = value,
            type = parameter.type,
            varargElementTypeHint = parameter.varargElementType,
            pluginContext = pluginContext,
            compat = compat,
            messageCollector = messageCollector,
        )
    }

    /**
     * [value] を [type] の IR 式に再構築する再帰本体。
     *
     * @param varargElementTypeHint parameter が `vararg` 宣言の場合の要素型。 配列再構築で
     *   `Array<T>` の型引数より優先して使う (非 vararg parameter では `null`)。
     */
    private fun build(
        value: UserArgValue,
        type: IrType,
        varargElementTypeHint: IrType?,
        pluginContext: IrPluginContext,
        compat: CompatContext,
        messageCollector: MessageCollector,
    ): IrExpression? = when (value) {
        UserArgValue.NullValue -> null
        is UserArgValue.BoolValue ->
            compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Boolean, value.value)
        is UserArgValue.CharValue ->
            compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Char, value.value)
        is UserArgValue.ByteValue ->
            compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Byte, value.value)
        is UserArgValue.ShortValue ->
            compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Short, value.value)
        is UserArgValue.IntValue ->
            compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Int, value.value)
        is UserArgValue.LongValue ->
            compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Long, value.value)
        is UserArgValue.FloatValue ->
            compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Float, value.value)
        is UserArgValue.DoubleValue ->
            compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Double, value.value)
        is UserArgValue.StringValue ->
            compat.newIrConstString(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, value.value)
        is UserArgValue.EnumRef -> buildEnum(value.entryFqn, type, compat)
            ?: run {
                reportUserArgWarning(messageCollector, UserArgWarnings.ENUM_NOT_FOUND, value.entryFqn)
                null
            }
        is UserArgValue.ClassRef ->
            buildClassRef(value.classFqn, pluginContext, messageCollector)
        is UserArgValue.ArrayValue ->
            buildArray(value, type, varargElementTypeHint, pluginContext, compat, messageCollector)
        is UserArgValue.AnnotationValue ->
            buildAnnotation(value, pluginContext, compat, messageCollector)
        is UserArgValue.UnsupportedExpression -> {
            // FIR phase (`ConvertUserArgExpression`) で変換できなかった式。 bug-004 以前は
            // 「解決できない enum entry」 という誤 warning (Array は silent) だった経路を、
            // 実態に合った文面で通知して default fallback する。
            reportUserArgWarning(messageCollector, UserArgWarnings.EXPRESSION_UNSUPPORTED, value.description)
            null
        }
    }

    /**
     * parameter 型が enum class の場合に、 末尾の `.Xxx` を entry 名として
     * SPI 経由で `IrGetEnumValue` を組み立てる。
     *
     * [entryFqn] は `com.example.Verb.GET` のような FqN 想定。 末尾セグメントを entry 名とする。
     */
    private fun buildEnum(
        entryFqn: String,
        type: IrType,
        compat: CompatContext,
    ): IrExpression? {
        val classifier = (type as? IrSimpleType)?.classifier
        val ownerClass = classifier?.owner as? IrClass ?: return null
        val entryName = entryFqn.substringAfterLast('.')
        val entry = ownerClass.declarations
            .filterIsInstance<IrEnumEntry>()
            .firstOrNull { it.name.asString() == entryName } ?: return null
        return compat.newIrGetEnumValue(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = type,
            symbol = entry.symbol,
        )
    }

    /**
     * bug-004: `::class` 参照 ([UserArgValue.ClassRef]) を `IrClassReference` に再構築する。
     * 実体 constructor の呼び出しは [IrClassReferenceShim] (Java) 経由 (drift 検証の経緯は
     * shim の KDoc 参照)。 class FqN が IR classpath で解決できない場合は
     * [UserArgWarnings.CLASS_NOT_FOUND] + null fallback。
     */
    private fun buildClassRef(
        classFqn: String,
        pluginContext: IrPluginContext,
        messageCollector: MessageCollector,
    ): IrExpression? {
        val symbol = referenceClassByFqn(pluginContext, classFqn) ?: run {
            reportUserArgWarning(messageCollector, UserArgWarnings.CLASS_NOT_FOUND, classFqn)
            return null
        }
        val classType = symbol.typeWith()
        val kClassType = pluginContext.irBuiltIns.kClassClass.typeWith(classType)
        return IrClassReferenceShim.create(UNDEFINED_OFFSET, UNDEFINED_OFFSET, kClassType, symbol, classType)
    }

    /**
     * bug-004: 配列 literal ([UserArgValue.ArrayValue]) を `IrVararg` に再構築する。
     * annotation の array 引数は IR 上 `IrVararg` として表現されるため、
     * [CompatContext.newIrVararg] (drift D-IR-16 吸収済 SPI) で組み立てる。
     *
     * 要素型は vararg parameter なら [varargElementTypeHint]、 `Array<T>` parameter なら
     * 型引数、 primitive array (`IntArray` 等) なら `irBuiltIns` の対応 primitive 型。
     * 要素 1 つでも再構築できない場合は配列全体を null fallback する (要素側で warning 発火済)。
     */
    private fun buildArray(
        value: UserArgValue.ArrayValue,
        type: IrType,
        varargElementTypeHint: IrType?,
        pluginContext: IrPluginContext,
        compat: CompatContext,
        messageCollector: MessageCollector,
    ): IrExpression? {
        val elementType = varargElementTypeHint ?: arrayElementTypeOf(type, pluginContext) ?: run {
            reportUserArgWarning(
                messageCollector,
                UserArgWarnings.EXPRESSION_UNSUPPORTED,
                "array argument for parameter type '${type.classFqName?.asString()}'",
            )
            return null
        }
        val elements = ArrayList<IrVarargElement>(value.elements.size)
        for (element in value.elements) {
            val expr = build(element, elementType, null, pluginContext, compat, messageCollector)
                ?: return null
            elements.add(expr)
        }
        return compat.newIrVararg(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = type,
            varargElementType = elementType,
            elements = elements,
        )
    }

    /**
     * bug-004: nested annotation ([UserArgValue.AnnotationValue]) を `IrConstructorCall` に
     * 再構築する。 symbol / constructor / parameter の解決は `FillSource.resolveOrNull` と
     * 同じ proven pattern (`referenceClass` + `constructors.firstOrNull()` +
     * [CompatContext.valueParametersOf])。
     *
     * 引数 map に無い parameter は `putCallValueArgument` を skip し、 annotation class 側の
     * default 値に任せる (= `BuildMarkerInstance.buildSingle` と同 pattern)。 引数 1 つでも
     * 再構築できない場合は annotation 全体を null fallback する (引数側で warning 発火済)。
     */
    private fun buildAnnotation(
        value: UserArgValue.AnnotationValue,
        pluginContext: IrPluginContext,
        compat: CompatContext,
        messageCollector: MessageCollector,
    ): IrExpression? {
        val symbol = referenceClassByFqn(pluginContext, value.classFqn) ?: run {
            reportUserArgWarning(messageCollector, UserArgWarnings.CLASS_NOT_FOUND, value.classFqn)
            return null
        }
        val constructor = symbol.owner.constructors.firstOrNull()?.symbol ?: run {
            reportUserArgWarning(messageCollector, UserArgWarnings.CLASS_NOT_FOUND, value.classFqn)
            return null
        }
        val ctorCall = compat.newIrConstructorCall(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = symbol.typeWith(),
            constructorSymbol = constructor,
        )
        val parameters = compat.valueParametersOf(constructor.owner)
        for ((index, param) in parameters.withIndex()) {
            val arg = value.args[param.name.asString()] ?: continue
            val expr = build(arg, param.type, param.varargElementType, pluginContext, compat, messageCollector)
                ?: return null
            compat.putCallValueArgument(ctorCall, index, expr)
        }
        return ctorCall
    }

    /**
     * class FqN を [IrClassSymbol] に解決する。 top-level class は
     * `ClassId.topLevel` で 1 発、 nested class (`example.Outer.Meta` — FIR 側で
     * `asSingleFqName` によって nesting 情報が落ちた FqN) は package / relative 境界を
     * 右から順に試して解決する。
     */
    private fun referenceClassByFqn(
        pluginContext: IrPluginContext,
        classFqn: String,
    ): IrClassSymbol? {
        pluginContext.referenceClass(ClassId.topLevel(FqName(classFqn)))?.let { return it }
        val segments = classFqn.split('.')
        for (packageCount in segments.size - 2 downTo 0) {
            val classId = ClassId(
                FqName(segments.take(packageCount).joinToString(".")),
                FqName(segments.drop(packageCount).joinToString(".")),
                false,
            )
            pluginContext.referenceClass(classId)?.let { return it }
        }
        return null
    }

    /**
     * annotation array parameter の要素型を導出する。 `Array<T>` は型引数、
     * primitive array は `irBuiltIns` の対応 primitive 型。 それ以外は `null`。
     */
    private fun arrayElementTypeOf(type: IrType, pluginContext: IrPluginContext): IrType? {
        val fqn = type.classFqName?.asString() ?: return null
        val builtIns = pluginContext.irBuiltIns
        return when (fqn) {
            "kotlin.Array" ->
                ((type as? IrSimpleType)?.arguments?.firstOrNull() as? IrTypeProjection)?.type
            "kotlin.IntArray" -> builtIns.intType
            "kotlin.LongArray" -> builtIns.longType
            "kotlin.ShortArray" -> builtIns.shortType
            "kotlin.ByteArray" -> builtIns.byteType
            "kotlin.BooleanArray" -> builtIns.booleanType
            "kotlin.CharArray" -> builtIns.charType
            "kotlin.FloatArray" -> builtIns.floatType
            "kotlin.DoubleArray" -> builtIns.doubleType
            else -> null
        }
    }

    /**
     * task-134 helper: emit [warning] (1 String 引数) via [messageCollector]。
     *
     * `MessageCollector.report(...)` の bytecode は K2.0 .. K2.4-RC で identical で、 同 pattern を
     * [me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.BuildMarkerInstance.reportWarning]
     * + [me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.warnIfNoMarkerFound.WarnIfNoMarkerFound]
     * が既に採用しているため main 側に閉じた helper として再利用しやすい。
     *
     * location は `null` を渡す (= IR const 再構築 path では IrFile を直接保持しないため)。 warning
     * message body ([arg]) で対象が特定できる。 collector が [MessageCollector.NONE] の場合は
     * 早期 return (= 既存 unit test 経路を維持)。
     */
    private fun reportUserArgWarning(
        messageCollector: MessageCollector,
        warning: CaptureCodeCompilerPluginWarning,
        arg: String,
    ) {
        if (messageCollector === MessageCollector.NONE) return
        val text = MessageFormat.format(warning.message, arg)
        messageCollector.report(CompilerMessageSeverity.WARNING, text, null)
    }
}
