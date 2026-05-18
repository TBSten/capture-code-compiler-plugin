package me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation

import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeFillerClassIds
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMetaAnnotation
import org.jetbrains.kotlin.descriptors.ClassKind
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
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjection
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType
import org.jetbrains.kotlin.fir.types.isNonPrimitiveArray
import org.jetbrains.kotlin.fir.types.isPrimitiveOrNullablePrimitive
import org.jetbrains.kotlin.fir.types.isUnsignedTypeOrNullableUnsignedType
import org.jetbrains.kotlin.name.StandardClassIds

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
 *
 * task-091: visibility / retention / target の 3 制約は 0.1.x まで強制していたが
 * 「不便なだけ」 という判断で撤廃。 残った check は marker parameter
 * の correctness のみ (型 / filler default / isExpect)。
 *
 * task-119: 各 `compat-kXXX/checker/K{XXX}MarkerAnnotationChecker.kt` に分散
 * していたロジック本体を main module に統一した版。 K2.0 baseline で書き、
 * 2.0.20+ の `fullyExpandedType` overload drift (drift D11) は
 * [CompatContext.fullyExpandedTypeOf] 経由で吸収する。
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

        checkParameters(declaration, session, context, reporter, compat, diagnostics)
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
