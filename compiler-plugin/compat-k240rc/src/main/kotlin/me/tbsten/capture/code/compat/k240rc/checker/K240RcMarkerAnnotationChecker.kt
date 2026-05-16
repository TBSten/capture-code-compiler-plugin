// Kotlin 2.2.x: FirChecker 系 base class が DeprecatedForRemovalCompilerApi で opt-in required になったため、 file 単位で OptIn。
@file:OptIn(org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class)

package me.tbsten.capture.code.compat.k240rc.checker

import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeFillerClassIds
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMetaAnnotation
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.extractEnumValueArgumentInfo
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.getRetention
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.utils.isExpect
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.unwrapAndFlattenArgument
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjection
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeSafe
import org.jetbrains.kotlin.fir.types.isNonPrimitiveArray
import org.jetbrains.kotlin.fir.types.isPrimitiveOrNullablePrimitive
import org.jetbrains.kotlin.fir.types.isUnsignedTypeOrNullableUnsignedType
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * Kotlin 2.2.x baseline 向けの **Logic F** ロジック本体 (marker annotation の制約違反診断)。
 *
 * Kotlin 2.2.x で `FirDeclarationChecker.check` が context-parameter signature
 * (`context(...) fun check(D)`) に切り替わり、 root KGP 2.0.0 で compile する本 module の
 * Kotlin source からは override を書けない。 そのため checker 本体は Java shim
 * ([K240RcMarkerAnnotationCheckerShim]) で `FirRegularClassChecker` を継承し、 本オブジェクトに
 * ロジック本体を切り出してデリゲートする。 ロジック自体は K210 版と同一。
 */
public object K240RcMarkerAnnotationCheckerLogic {
    @JvmStatic
    public fun run(
        context: CheckerContext,
        reporter: DiagnosticReporter,
        declaration: FirRegularClass,
    ) {
        if (declaration.classKind != ClassKind.ANNOTATION_CLASS) return
        if (!declaration.hasCaptureCodeMeta(context.session)) return

        val session = context.session
        val source = declaration.source

        if (declaration.isExpect) {
            reporter.reportOn(source, K240RcCaptureCodeDiagnostics.CC_MARKER_IS_EXPECT, context)
        }

        // task-091: visibility / retention / target の 3 制約は撤廃 (K230 と同じ)。
        checkParameters(declaration, session, context, reporter)
    }

    private fun FirRegularClass.hasCaptureCodeMeta(session: FirSession): Boolean =
        annotations.any { it.toAnnotationClassId(session) == CaptureCodeMetaAnnotation.classId }

    @OptIn(SymbolInternals::class)
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

            val isAllowed = returnType != null && isAllowedAnnotationParameterType(returnType, session)
            if (!isAllowed) {
                reporter.reportOn(
                    parameterSymbol.source ?: declaration.source,
                    K240RcCaptureCodeDiagnostics.CC_MARKER_PARAMETER_TYPE_INVALID,
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
                        K240RcCaptureCodeDiagnostics.CC_MARKER_FILLER_REQUIRES_DEFAULT,
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
    ): Boolean {
        val classId = type.classId ?: return false

        return when {
            type.isPrimitiveOrNullablePrimitive -> true
            type.isUnsignedTypeOrNullableUnsignedType -> true
            classId == StandardClassIds.String -> true
            classId == StandardClassIds.KClass -> true
            classId in StandardClassIds.primitiveArrayTypeByElementType.values -> true
            classId in StandardClassIds.unsignedArrayTypeByElementType.values -> true
            classId == StandardClassIds.Array -> isAllowedArrayElement(type, session)
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
        val symbol = type.toRegularClassSymbol(session) ?: return false
        val kind = symbol.classKind
        return kind == ClassKind.ANNOTATION_CLASS || kind == ClassKind.ENUM_CLASS
    }

}
