package me.tbsten.capture.code.compat.k200.checker

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
import org.jetbrains.kotlin.fir.declarations.extractEnumValueArgumentInfo
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.getRetention
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.utils.isExpect
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.unwrapAndFlattenArgument
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjection
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeSafe
import org.jetbrains.kotlin.fir.types.isNonPrimitiveArray
import org.jetbrains.kotlin.fir.types.isPrimitiveOrNullablePrimitive
import org.jetbrains.kotlin.fir.types.isUnsignedTypeOrNullableUnsignedType
import org.jetbrains.kotlin.fir.types.toRegularClassSymbol
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * Kotlin 2.0.x baseline 向けの **Logic F** marker annotation 制約違反診断 checker。
 *
 * task-072 で `:compiler-plugin` main module の `MarkerAnnotationChecker` を compat-k200 layer に
 * 移動した版。 ロジックは main 版と同一で、 abstract method `check(declaration, context, reporter)`
 * を **K2.0 baseline の signature** で override する。 K2.0 → K2.2.x で abstract method の引数順が
 * `check(context, reporter, declaration)` に変わったため、 2.0.0 で compile した bytecode は
 * 2.2.x runtime で AbstractMethodError を起こす。 compat-k200 module 自体が **常に 2.0.0 baseline**
 * (`kotlin-compiler-embeddable-k200` ref) で compile されるため、 ServiceLoader 経由で
 * compat-k200 が選択された環境では runtime も 2.0.x 〜 2.0.21 までと整合する。
 *
 * 詳細な制約とコメントは `:compiler-plugin/src/main/.../MarkerAnnotationChecker.kt` の oldsite を
 * 参照 (本 ticket 完了時に同 file を削除)。
 */
internal object K200MarkerAnnotationChecker : FirRegularClassChecker(MppCheckerKind.Common) {

    override fun check(
        declaration: FirRegularClass,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        if (declaration.classKind != ClassKind.ANNOTATION_CLASS) return
        if (!declaration.hasCaptureCodeMeta(context.session)) return

        val session = context.session
        val source = declaration.source

        if (declaration.isExpect) {
            reporter.reportOn(source, K200CaptureCodeDiagnostics.CC_MARKER_IS_EXPECT, context)
        }

        checkVisibility(declaration, context, reporter)
        checkRetention(declaration, context, reporter)
        checkTargetSites(declaration, context, reporter)
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
                K200CaptureCodeDiagnostics.CC_MARKER_VISIBILITY_VIOLATION,
                context,
            )
        }
    }

    private fun checkRetention(
        declaration: FirRegularClass,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        if (declaration.getRetention(context.session) != AnnotationRetention.SOURCE) {
            reporter.reportOn(
                declaration.source,
                K200CaptureCodeDiagnostics.CC_MARKER_RETENTION_VIOLATION,
                context,
            )
        }
    }

    private fun checkTargetSites(
        declaration: FirRegularClass,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        val targetAnnotation = targetAnnotationOf(declaration, context.session)
        if (targetAnnotation == null) {
            reporter.reportOn(
                declaration.source,
                K200CaptureCodeDiagnostics.CC_MARKER_TARGET_EMPTY,
                context,
            )
            return
        }
        val sites = extractTargetSites(targetAnnotation)
        if (sites.isEmpty()) {
            reporter.reportOn(
                targetAnnotation.source,
                K200CaptureCodeDiagnostics.CC_MARKER_TARGET_EMPTY,
                context,
            )
        }
    }

    private fun extractTargetSites(targetAnnotation: FirAnnotation): List<String> {
        val args = targetAnnotation
            .findArgumentByName(StandardClassIds.Annotations.ParameterNames.targetAllowedTargets)
            ?.unwrapAndFlattenArgument(flattenArrays = true)
            .orEmpty()
        return args.mapNotNull { it.extractEnumValueArgumentInfo()?.enumEntryName?.asString() }
    }

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
            // task-080: dispatch type alias expansion via [FullyExpandedTypeShim]
            // instead of a direct `fullyExpandedType(session)` call. Both the
            // `ConeKotlinType` and `ConeSimpleKotlinType` 2-arg overloads available
            // in 2.0.0 were removed in 2.0.20 (JetBrains/kotlin commit e0126530),
            // and the surviving 3-arg overload `(ConeKotlinType, FirSession, Function1)`
            // does not exist in 2.0.0. A reflection shim is the only drift-safe
            // path inside compat-k200 (which gets selected for every 2.0.x consumer).
            val returnType = parameterSymbol.resolvedReturnTypeRef.coneTypeSafe<ConeLookupTagBasedType>()
                ?.let { FullyExpandedTypeShim.expand(it, session) } as? ConeLookupTagBasedType
            val parameterClassId = returnType?.classId

            val isAllowed = returnType != null && isAllowedAnnotationParameterType(returnType, session)
            if (!isAllowed) {
                reporter.reportOn(
                    parameterSymbol.source ?: declaration.source,
                    K200CaptureCodeDiagnostics.CC_MARKER_PARAMETER_TYPE_INVALID,
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
                        K200CaptureCodeDiagnostics.CC_MARKER_FILLER_REQUIRES_DEFAULT,
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
        // task-080: see [FullyExpandedTypeShim] — same 2.0.x drift applies here.
        val elementType = (arrayType.typeArguments.firstOrNull() as? ConeKotlinTypeProjection)
            ?.type
            ?.let { FullyExpandedTypeShim.expand(it, session) }
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
        // K2.0: `toRegularClassSymbol` lives in `org.jetbrains.kotlin.fir.types`
        val symbol = type.toRegularClassSymbol(session) ?: return false
        val kind = symbol.classKind
        return kind == ClassKind.ANNOTATION_CLASS || kind == ClassKind.ENUM_CLASS
    }
}

private fun targetAnnotationOf(
    declaration: FirRegularClass,
    session: FirSession,
): FirAnnotation? =
    declaration.annotations.firstOrNull { annotation ->
        annotation.toAnnotationClassId(session) == StandardClassIds.Annotations.Target
    }
