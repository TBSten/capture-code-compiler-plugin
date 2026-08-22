package me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation

import me.tbsten.capture.code.compat.CaptureCodeMessageCollectorHolder
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeFillerClassIds
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMetaAnnotation
import me.tbsten.capture.code.utils.fir.compilerMessageLocationOf
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.utils.isExpect
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjection
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType
import org.jetbrains.kotlin.fir.types.isNonPrimitiveArray
import org.jetbrains.kotlin.fir.types.isPrimitiveOrNullablePrimitive
import org.jetbrains.kotlin.fir.types.isUnsignedTypeOrNullableUnsignedType
import org.jetbrains.kotlin.name.StandardClassIds
import java.text.MessageFormat

/**
 * Logic F: marker annotation constraint validation.
 *
 * Reports diagnostics when a `@CaptureCode`-meta annotation class violates the
 * plugin's correctness constraints:
 *
 * 1. `isExpect == true` → reports [Diagnostics.markerIsExpect].
 * 2. Annotation parameter has a type that is not allowed in Kotlin annotation
 *    classes → reports [Diagnostics.markerParameterTypeInvalid] (per-parameter).
 * 3. Annotation parameter has a [filler][CaptureCodeFillerClassIds] type but no
 *    default value → reports [Diagnostics.markerFillerRequiresDefault].
 * 4. Marker class is declared `public` (no-modifier default を含む) / `protected` →
 *    [MarkerAnnotationErrors.NOT_INTERNAL_OR_PRIVATE] を
 *    [CaptureCodeMessageCollectorHolder.reportError] 経由で error 報告 (bug-008)。
 *
 * task-091: visibility / retention / target の 3 制約は 0.1.x まで強制していたが
 * 「不便なだけ」 という判断で撤廃。 その後 bug-008 で **visibility のみ復活**:
 * README の Constraints (「Marker annotations must be internal or private」) が enforce
 * されておらず、 public marker は下流 module から参照可能 → 下流の `@Marker` は silent に
 * capture されず `capturedSources<T>()` が runtime IllegalStateException になるため。
 * retention / target の撤廃は維持。
 *
 * bug-008 の visibility check は `KtDiagnosticFactory*` ではなく
 * [CaptureCodeMessageCollectorHolder.reportError] (MessageCollector ERROR) で報告する。
 * 新 factory の追加は全 compat-kXXX の diagnostics object に波及するため
 * (`CompatContext.diagnosticFactory(id)` は未登録 id に null を返す)、 compat 変更なしで
 * error を出せる IR phase と同じ機構を使う。 判定は **宣言上の visibility**
 * (`FirDeclarationStatus.visibility`) で行うため、 internal object 内の no-modifier
 * (= declared public) nested marker も error になる (= `internal` の明示が必要)。
 *
 * task-119: 各 `compat-kXXX/checker/K{XXX}MarkerAnnotationChecker.kt` に分散
 * していたロジック本体を main module に統一した版。 K2.0 baseline で書き、
 * 2.0.20+ の `fullyExpandedType` overload drift (drift D11) は
 * [CompatContext.fullyExpandedTypeOf] 経由で吸収する。
 *
 * ## Preconditions
 *
 * Caller (= 各 `compat-kXXX` の `K{XXX}MarkerAnnotationChecker`) は以下を保証する責務がある。
 * いずれも違反した場合は invoke が silently early-return (= diagnostic は発火しない)
 * もしくは内部の resolution fallback により正常な error 経路 (= `PARAMETER_TYPE_INVALID`)
 * に倒れる設計のため、 `require(...)` での fail-fast は導入していない。
 *
 * - `declaration` は FIR-resolved な `FirRegularClass`。 `classKind` が
 *   `ANNOTATION_CLASS` でない場合は invoke 冒頭で early-return する (= caller が
 *   non-annotation class を渡しても crash しない)。
 * - `declaration` の annotations に `@CaptureCode`-meta annotation が含まれること。
 *   含まれない場合は冒頭で early-return する (= marker でない annotation class
 *   に検証を走らせない)。
 * - `declaration.primaryConstructorIfAny(session)` が non-null (= annotation class
 *   なら必ず存在)。 null の場合は `checkParameters` 内で silent return する (=
 *   typical root cause: compiler 内部 bug / 半 resolve の declaration)。
 * - `parameterSymbol.resolvedReturnTypeRef` は FIR resolution phase 完了済 (=
 *   各 compat-kXXX checker は `K{XXX}CheckerExtensions` 経由で `Common` phase に
 *   登録されており、 type ref は resolve 済が保証される)。
 * - `compat: CompatContext` は同 module の `CompatContextImpl` (= 各 compat-kXXX
 *   の actual 実装) であり、 `coneTypeOrNullOf` / `fullyExpandedTypeOf` /
 *   `toRegularClassSymbolOrNull` / `classIdOfType` の SPI が正しく dispatch される。
 * - `diagnostics` は caller 自身が保有する `K{XXX}CaptureCodeDiagnostics` の
 *   `Diagnostics` view (= `markerIsExpect` / `markerParameterTypeInvalid` /
 *   `markerFillerRequiresDefault` の `KtDiagnosticFactory*` が registered)。
 */
public class ValidateMarkerAnnotation {

    /**
     * Diagnostic factories used by this logic. Each `compat-kXXX` module
     * supplies its own `K{XXX}CaptureCodeDiagnostics` instance via this
     * container so that the same factory identity is used at the
     * `KtDiagnosticsContainer` registration site.
     */
    public interface Diagnostics {
        public val markerIsExpect: KtDiagnosticFactory0
        public val markerParameterTypeInvalid: KtDiagnosticFactory1<String>
        public val markerFillerRequiresDefault: KtDiagnosticFactory1<String>
    }

    public operator fun invoke(
        context: CheckerContext,
        reporter: DiagnosticReporter,
        declaration: FirRegularClass,
        compat: CompatContext,
        diagnostics: Diagnostics,
    ) {
        if (declaration.classKind != ClassKind.ANNOTATION_CLASS) return
        if (!declaration.hasCaptureCodeMeta(context.session)) return

        val session = context.session
        val source = declaration.source

        if (declaration.isExpect) {
            reporter.reportOn(source, diagnostics.markerIsExpect, context)
        }

        checkVisibility(declaration, context, compat)

        checkParameters(declaration, session, context, reporter, compat, diagnostics)
    }

    /**
     * bug-008: marker class の宣言 visibility が `public` (no-modifier default を含む) /
     * `protected` なら [MarkerAnnotationErrors.NOT_INTERNAL_OR_PRIVATE] を error 報告する。
     *
     * 報告経路は [CaptureCodeMessageCollectorHolder.reportError] (MessageCollector ERROR)。
     * `KtDiagnosticFactory*` を使わない理由は class KDoc 参照 (compat-kXXX 変更なしで
     * 追加できる経路がこれしか無い)。 collector が未設定 (= registrar を通らない unit test)
     * の場合は silent no-op に degrade する。
     */
    private fun checkVisibility(
        declaration: FirRegularClass,
        context: CheckerContext,
        compat: CompatContext,
    ) {
        val visibility = declaration.visibility
        if (visibility != Visibilities.Public && visibility != Visibilities.Protected) return

        // drift D3: `FirRegularClassSymbol.classId` は SPI 経由で dispatch。
        val markerFqn = compat.classIdOf(declaration.symbol)?.asSingleFqName()?.asString()
            ?: declaration.name.asString()
        CaptureCodeMessageCollectorHolder.reportError(
            message = MessageFormat.format(
                MarkerAnnotationErrors.NOT_INTERNAL_OR_PRIVATE.message,
                markerFqn,
            ),
            location = compilerMessageLocationOf(
                source = declaration.source,
                fallbackFilePath = compat.containingFilePathOf(context),
            ),
        )
    }

    private fun FirRegularClass.hasCaptureCodeMeta(session: FirSession): Boolean =
        annotations.any { it.toAnnotationClassId(session) == CaptureCodeMetaAnnotation.classId }

    @OptIn(SymbolInternals::class)
    private fun checkParameters(
        declaration: FirRegularClass,
        session: FirSession,
        context: CheckerContext,
        reporter: DiagnosticReporter,
        compat: CompatContext,
        diagnostics: Diagnostics,
    ) {
        val primaryConstructor = declaration.primaryConstructorIfAny(session) ?: return

        for (parameterSymbol in primaryConstructor.valueParameterSymbols) {
            val parameterName = parameterSymbol.name.asString()
            // drift D11: 2-arg `fullyExpandedType(session)` overload は 2.0.20 で削除されたため、
            // CompatContext 経由で expand する。 各 compat-kXXX が自身の baseline に合った
            // dispatcher (reflection shim / direct call) を提供する。
            // drift D13: `FirTypeRef.coneTypeSafe<T>()` inline reified extension の root
            // (`FirResolvedTypeRef.getType()`) も SPI 経由で dispatch する。
            // `coneTypeSafe<T>()` 相当は `coneTypeOrNullOf(typeRef) as? T` で再現。
            val returnType = (compat.coneTypeOrNullOf(parameterSymbol.resolvedReturnTypeRef)
                as? ConeLookupTagBasedType)
                ?.let { compat.fullyExpandedTypeOf(it, session) } as? ConeLookupTagBasedType
            val parameterClassId = returnType?.let { compat.classIdOfType(it) }

            val isAllowed = returnType != null && isAllowedAnnotationParameterType(returnType, session, compat)
            if (!isAllowed) {
                reporter.reportOn(
                    parameterSymbol.source ?: declaration.source,
                    diagnostics.markerParameterTypeInvalid,
                    parameterName,
                    context,
                )
                continue
            }

            if (parameterClassId != null && CaptureCodeFillerClassIds.isFiller(parameterClassId)) {
                val hasDefault = parameterSymbol.hasDefaultValue
                if (!hasDefault) {
                    reporter.reportOn(
                        parameterSymbol.source ?: declaration.source,
                        diagnostics.markerFillerRequiresDefault,
                        parameterName,
                        context,
                    )
                }
            }
        }
    }

    private fun isAllowedAnnotationParameterType(
        type: ConeLookupTagBasedType,
        session: FirSession,
        compat: CompatContext,
    ): Boolean {
        // drift D14: `ConeKotlinType.classId` を SPI 経由で dispatch。
        val classId = compat.classIdOfType(type) ?: return false

        return when {
            type.isPrimitiveOrNullablePrimitive -> true
            type.isUnsignedTypeOrNullableUnsignedType -> true
            classId == StandardClassIds.String -> true
            classId == StandardClassIds.KClass -> true
            classId in StandardClassIds.primitiveArrayTypeByElementType.values -> true
            classId in StandardClassIds.unsignedArrayTypeByElementType.values -> true
            classId == StandardClassIds.Array -> isAllowedArrayElement(type, session, compat)
            else -> isAnnotationOrEnumClass(type, session, compat)
        }
    }

    private fun isAllowedArrayElement(
        arrayType: ConeLookupTagBasedType,
        session: FirSession,
        compat: CompatContext,
    ): Boolean {
        if (!arrayType.isNonPrimitiveArray) return false
        val elementType = (arrayType.typeArguments.firstOrNull() as? ConeKotlinTypeProjection)
            ?.type
            ?.let { compat.fullyExpandedTypeOf(it, session) }
            ?: return false
        // drift D14: `ConeKotlinType.classId` を SPI 経由で dispatch。
        val elementClassId = compat.classIdOfType(elementType) ?: return false
        return when {
            elementClassId == StandardClassIds.String -> true
            elementClassId == StandardClassIds.KClass -> true
            else -> {
                (elementType as? ConeLookupTagBasedType)?.let { isAnnotationOrEnumClass(it, session, compat) }
                    ?: false
            }
        }
    }

    private fun isAnnotationOrEnumClass(
        type: ConeLookupTagBasedType,
        session: FirSession,
        compat: CompatContext,
    ): Boolean {
        // drift D2: `toRegularClassSymbol` の package が 2.0.x の `fir.types` から
        // 2.1.x で `fir.resolve` に移動した。 CompatContext 経由で吸収する。
        val symbol = compat.toRegularClassSymbolOrNull(type, session) ?: return false
        val kind = symbol.classKind
        return kind == ClassKind.ANNOTATION_CLASS || kind == ClassKind.ENUM_CLASS
    }
}
