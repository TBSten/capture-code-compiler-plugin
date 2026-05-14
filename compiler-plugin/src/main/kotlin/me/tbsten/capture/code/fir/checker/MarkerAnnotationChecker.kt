package me.tbsten.capture.code.fir.checker

import me.tbsten.capture.code.compat.CaptureCodeCompatHolder
import me.tbsten.capture.code.error.CaptureCodeDiagnostics
import me.tbsten.capture.code.error.CaptureCodeFillerClassIds
import me.tbsten.capture.code.fir.marker.CaptureCodeMetaAnnotation
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.getRetention
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.utils.isExpect
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.declarations.extractEnumValueArgumentInfo
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.unwrapAndFlattenArgument
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjection
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeSafe
import org.jetbrains.kotlin.fir.types.isNonPrimitiveArray
import org.jetbrains.kotlin.fir.types.isPrimitiveOrNullablePrimitive
import org.jetbrains.kotlin.fir.types.isUnsignedTypeOrNullableUnsignedType
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * Logic F (task-010): `@CaptureCode` メタ付き marker annotation の制約違反を診断する FIR checker。
 *
 * `compiler-plugin-design.md` §5 Logic F に列挙された 6 つの制約をチェックし、違反時に
 * `CaptureCodeDiagnostics` の対応する factory で `reporter.reportOn(...)` を行う。
 *
 * 1. visibility が `internal` / `private` のいずれでもない → [CaptureCodeDiagnostics.MARKER_NOT_INTERNAL_OR_PRIVATE]
 * 2. `@Retention` が `SOURCE` 以外 → [CaptureCodeDiagnostics.MARKER_RETENTION_NOT_SOURCE]
 * 3. `@Target(...)` が空 / 未指定 → [CaptureCodeDiagnostics.MARKER_TARGET_EMPTY]
 * 4. parameter 型が Kotlin annotation 制約外 → [CaptureCodeDiagnostics.MARKER_PARAMETER_TYPE_INVALID]
 * 5. filler 型 parameter にデフォルト値が無い → [CaptureCodeDiagnostics.MARKER_FILLER_REQUIRES_DEFAULT]
 * 6. marker 自身が `expect` 宣言 → [CaptureCodeDiagnostics.MARKER_IS_EXPECT_ANNOTATION]
 *
 * Logic A の `CaptureCodeMarkerClassChecker` (registration only) とは責務が分離されている。
 * 本 checker は **診断のみ** を行い、registry への登録は副作用としても起こさない。
 *
 * `MppCheckerKind.Common` を指定する理由は Logic A の checker と同じ:
 * marker annotation は v1 では single module 内に閉じているので、leaf platform 側ではなく
 * 「declaration の所属 session」で 1 回だけ診断すれば十分。
 */
internal object MarkerAnnotationChecker : FirRegularClassChecker(MppCheckerKind.Common) {

    override fun check(
        declaration: FirRegularClass,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        // 1. annotation class 以外は対象外
        if (declaration.classKind != ClassKind.ANNOTATION_CLASS) return

        // 2. `@CaptureCode` meta-annotation が付いていない annotation は対象外
        if (!declaration.hasCaptureCodeMeta(context.session)) return

        val session = context.session
        val source = declaration.source

        // ------- (6) expect annotation -------
        // marker 自身を expect 宣言にすることは design §7.6 で非対応。
        // 一番先に判定して、他の診断が expect 側の status とぶつからないようにする。
        if (declaration.isExpect) {
            reporter.reportOn(source, CaptureCodeDiagnostics.MARKER_IS_EXPECT_ANNOTATION, context)
        }

        // ------- (1) visibility -------
        checkVisibility(declaration, context, reporter)

        // ------- (2) @Retention -------
        checkRetention(declaration, context, reporter)

        // ------- (3) @Target -------
        checkTargetSites(declaration, context, reporter)

        // ------- (4) (5) parameter 型 / filler default -------
        checkParameters(declaration, session, context, reporter)
    }

    private fun FirRegularClass.hasCaptureCodeMeta(session: FirSession): Boolean =
        annotations.any { it.toAnnotationClassId(session) == CaptureCodeMetaAnnotation.classId }

    private fun checkVisibility(
        declaration: FirRegularClass,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        val visibility = declaration.visibility
        if (visibility != Visibilities.Internal && visibility != Visibilities.Private) {
            reporter.reportOn(
                declaration.source,
                CaptureCodeDiagnostics.MARKER_NOT_INTERNAL_OR_PRIVATE,
                context,
            )
        }
    }

    private fun checkRetention(
        declaration: FirRegularClass,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        // `getRetention` は `@Retention` 未指定時 `RUNTIME` を返す。RUNTIME は SOURCE ではないため
        // 「未指定 (= default RUNTIME)」も `MARKER_RETENTION_NOT_SOURCE` で報告される。
        if (declaration.getRetention(context.session) != AnnotationRetention.SOURCE) {
            reporter.reportOn(
                declaration.source,
                CaptureCodeDiagnostics.MARKER_RETENTION_NOT_SOURCE,
                context,
            )
        }
    }

    private fun checkTargetSites(
        declaration: FirRegularClass,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        // `getAllowedAnnotationTargets` は `@Target` 未指定時 `DEFAULT_TARGET_SET` (空ではない) を
        // 返すため、これでは「未指定」を検出できない。
        //
        // 代わりに `getTargetAnnotation` で `@Target` annotation 自体の有無を見る:
        // - `@Target` が無い → 未指定なので空とみなす (= error)
        // - `@Target()` (引数空) → site が 0 → 空 (= error)
        // - `@Target(EXPRESSION, ...)` → site が 1+ (= OK)
        val targetAnnotation = targetAnnotationOf(declaration, context.session)
        if (targetAnnotation == null) {
            // @Target が無い場合は declaration 全体に対して reportOn
            reporter.reportOn(
                declaration.source,
                CaptureCodeDiagnostics.MARKER_TARGET_EMPTY,
                context,
            )
            return
        }
        val sites = extractTargetSites(targetAnnotation, context.session)
        if (sites.isEmpty()) {
            reporter.reportOn(
                targetAnnotation.source,
                CaptureCodeDiagnostics.MARKER_TARGET_EMPTY,
                context,
            )
        }
    }

    /**
     * `@Target(...)` annotation の引数 (`AnnotationTarget.XXX` 集合) を抽出する。
     *
     * `vararg` の `arrayOf(...)` / single value のいずれも `unwrapAndFlattenArgument` で
     * フラットにできる。enum value は `extractEnumValueArgumentInfo` で名前を得る。
     */
    private fun extractTargetSites(
        targetAnnotation: FirAnnotation,
        session: FirSession,
    ): List<String> {
        val args = targetAnnotation
            .findArgumentByName(StandardClassIds.Annotations.ParameterNames.targetAllowedTargets)
            ?.unwrapAndFlattenArgument(flattenArrays = true)
            .orEmpty()
        return args.mapNotNull { it.extractEnumValueArgumentInfo()?.enumEntryName?.asString() }
    }

    private fun checkParameters(
        declaration: FirRegularClass,
        session: FirSession,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        val primaryConstructor = declaration.primaryConstructorIfAny(session) ?: return

        for (parameterSymbol in primaryConstructor.valueParameterSymbols) {
            val parameterName = parameterSymbol.name.asString()
            val returnType = parameterSymbol.resolvedReturnTypeRef.coneTypeSafe<ConeLookupTagBasedType>()
                ?.fullyExpandedType(session) as? ConeLookupTagBasedType
            val parameterClassId = returnType?.classId

            // ------- (4) parameter 型が Kotlin annotation 制約外 -------
            val isAllowed = returnType != null && isAllowedAnnotationParameterType(returnType, session)
            if (!isAllowed) {
                reporter.reportOn(
                    parameterSymbol.source ?: declaration.source,
                    CaptureCodeDiagnostics.MARKER_PARAMETER_TYPE_INVALID,
                    parameterName,
                    context,
                )
                // 型自体が不正なら filler チェックは skip (filler 型なら型は OK で別軸の問題)
                continue
            }

            // ------- (5) filler 型 parameter のデフォルト値 -------
            if (parameterClassId != null && CaptureCodeFillerClassIds.isFiller(parameterClassId)) {
                val hasDefault = parameterSymbol.hasDefaultValue
                if (!hasDefault) {
                    reporter.reportOn(
                        parameterSymbol.source ?: declaration.source,
                        CaptureCodeDiagnostics.MARKER_FILLER_REQUIRES_DEFAULT,
                        parameterName,
                        context,
                    )
                }
            }
        }
    }

    /**
     * Kotlin annotation parameter 制約 (primitives / String / KClass / enum / annotation /
     * それらの配列) に従う型かどうかを判定する。
     *
     * 参考: `FirAnnotationClassDeclarationChecker.checkAnnotationClassMember`。
     * ただし本 checker は **plugin が認識する marker** の追加制約を適用するため、Kotlin 標準の
     * `LOCAL_ANNOTATION_CLASS_ERROR` 等とは独立に動作する。
     */
    private fun isAllowedAnnotationParameterType(
        type: ConeLookupTagBasedType,
        session: FirSession,
    ): Boolean {
        // null type は呼び出し側で reject 済み
        val classId = type.classId ?: return false

        return when {
            // primitives (Int, Long, ..., Boolean 等。nullable は Kotlin 既に弾く)
            type.isPrimitiveOrNullablePrimitive -> true
            // unsigned primitives (UInt, ULong 等)
            type.isUnsignedTypeOrNullableUnsignedType -> true
            // String
            classId == StandardClassIds.String -> true
            // KClass<*>
            classId == StandardClassIds.KClass -> true
            // primitive array types (IntArray 等)
            classId in StandardClassIds.primitiveArrayTypeByElementType.values -> true
            // unsigned array types (UIntArray 等)
            classId in StandardClassIds.unsignedArrayTypeByElementType.values -> true
            // generic Array<T>
            classId == StandardClassIds.Array -> isAllowedArrayElement(type, session)
            // enum / annotation
            else -> isAnnotationOrEnumClass(type, session)
        }
    }

    private fun isAllowedArrayElement(
        arrayType: ConeLookupTagBasedType,
        session: FirSession,
    ): Boolean {
        if (!arrayType.isNonPrimitiveArray) return false
        val elementType = (arrayType.typeArguments.firstOrNull() as? ConeKotlinTypeProjection)
            ?.type
            ?.fullyExpandedType(session)
            ?: return false
        val elementClassId = elementType.classId ?: return false
        return when {
            elementClassId == StandardClassIds.String -> true
            elementClassId == StandardClassIds.KClass -> true
            else -> {
                (elementType as? ConeLookupTagBasedType)?.let { isAnnotationOrEnumClass(it, session) }
                    ?: false
            }
        }
    }

    private fun isAnnotationOrEnumClass(
        type: ConeLookupTagBasedType,
        session: FirSession,
    ): Boolean {
        // task-030 v2: `toRegularClassSymbol` extension の package 移動 drift (D2) を
        // CompatContext.toRegularClassSymbolOrNull 経由で吸収。
        val symbol = CaptureCodeCompatHolder.context.toRegularClassSymbolOrNull(type, session)
            ?: return false
        val kind = symbol.classKind
        return kind == ClassKind.ANNOTATION_CLASS || kind == ClassKind.ENUM_CLASS
    }
}

/**
 * `@Target` annotation を取得する補助関数。
 *
 * Kotlin compiler 内部の `getTargetAnnotation` ヘルパと同等の動作をするが、本 plugin の
 * ファイル内で完結させて余計な internal 依存を避ける。
 */
private fun targetAnnotationOf(
    declaration: FirRegularClass,
    session: FirSession,
): FirAnnotation? =
    declaration.annotations.firstOrNull { annotation ->
        annotation.toAnnotationClassId(session) == StandardClassIds.Annotations.Target
    }
