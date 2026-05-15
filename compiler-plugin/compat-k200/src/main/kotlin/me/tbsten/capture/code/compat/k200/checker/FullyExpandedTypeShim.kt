package me.tbsten.capture.code.compat.k200.checker

import java.lang.reflect.InvocationTargetException
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.types.ConeKotlinType

/**
 * task-080: Reflection-based shim for `ConeKotlinType.fullyExpandedType(FirSession)`
 * to absorb the 2-arg → 3-arg signature drift inside the 2.0.x minor.
 *
 * - **Kotlin 2.0.0 .. 2.0.10**: `TypeExpansionUtilsKt` exposes
 *   `fullyExpandedType(ConeKotlinType, FirSession): ConeKotlinType` and
 *   `fullyExpandedType(ConeSimpleKotlinType, FirSession): ConeSimpleKotlinType`
 *   as standalone 2-arg overloads (no default lambda).
 * - **Kotlin 2.0.20+**: both 2-arg overloads were removed (JetBrains/kotlin commit
 *   e0126530, 2024-02-29). The remaining static method has a 3-arg shape
 *   `fullyExpandedType(ConeKotlinType, FirSession, Function1)` and a 5-arg
 *   `fullyExpandedType$default(..., Function1, int, Object)` synthetic for
 *   default args.
 *
 * Because `compat-k200` is compiled against the 2.0.0 baseline, any direct
 * Kotlin source-level call `coneType.fullyExpandedType(session)` would emit
 * an `INVOKESTATIC` to the 2-arg overload, which no longer exists on a 2.0.20+
 * consumer runtime — yielding `NoSuchMethodError` (task-079 残課題).
 *
 * Since compat-k200 is selected by [me.tbsten.capture.code.compat.CompatContext]
 * for **any 2.0.x consumer < 2.1.0** (semver-based factory resolution), we
 * cannot statically link to either signature. Reflection is the only
 * drift-safe path: probe the available methods on `TypeExpansionUtilsKt`
 * at first call, cache the resolved [java.lang.reflect.Method], and dispatch
 * through it for the lifetime of the JVM.
 */
internal object FullyExpandedTypeShim {

    /** FQN of the static helper Kotlin file class that hosts the extensions. */
    private const val TYPE_EXPANSION_UTILS_KT =
        "org.jetbrains.kotlin.fir.resolve.TypeExpansionUtilsKt"

    /**
     * Lazily-resolved dispatcher. Probes the runtime classpath once and
     * returns a function that, given `(type, session)`, invokes the available
     * overload reflectively.
     */
    private val dispatcher: (ConeKotlinType, FirSession) -> ConeKotlinType by lazy {
        resolveDispatcher()
    }

    /**
     * Drift-safe `coneType.fullyExpandedType(session)`. Returns the type
     * unchanged if reflective resolution fails — preferring graceful
     * degradation over `NoSuchMethodError` (the checker uses the expanded
     * type only to decide annotation-parameter validity, so falling back
     * to the unexpanded type yields at worst a slightly less precise
     * diagnostic, never a wrong-bytecode crash).
     */
    fun expand(type: ConeKotlinType, session: FirSession): ConeKotlinType =
        runCatching { dispatcher(type, session) }.getOrDefault(type)

    private fun resolveDispatcher(): (ConeKotlinType, FirSession) -> ConeKotlinType {
        val utilsClass = Class.forName(TYPE_EXPANSION_UTILS_KT)

        // Look for the 2.0.0-baseline 2-arg overload first
        // (ConeKotlinType, FirSession) -> ConeKotlinType.
        val twoArg = utilsClass.declaredMethods.firstOrNull { m ->
            m.name == "fullyExpandedType" &&
                m.parameterCount == 2 &&
                m.parameterTypes[0] == ConeKotlinType::class.java &&
                m.parameterTypes[1] == FirSession::class.java
        }
        if (twoArg != null) {
            twoArg.isAccessible = true
            return { type, session ->
                invokeAndUnwrap(twoArg, arrayOf<Any?>(type, session)) as ConeKotlinType
            }
        }

        // Fall back to the 2.0.20+ 3-arg overload (FirSession, Function1) and
        // dispatch through the `$default` synthetic which accepts a null
        // Function1 + the per-arg-default-bitmask (bit 0b10 = use default for
        // arg index 1).
        val threeArgDefault = utilsClass.declaredMethods.firstOrNull { m ->
            m.name == "fullyExpandedType\$default" &&
                m.parameterCount == 5 &&
                m.parameterTypes[0] == ConeKotlinType::class.java &&
                m.parameterTypes[1] == FirSession::class.java
        }
        if (threeArgDefault != null) {
            threeArgDefault.isAccessible = true
            return { type, session ->
                invokeAndUnwrap(
                    threeArgDefault,
                    arrayOf<Any?>(type, session, null, 0b10, null),
                ) as ConeKotlinType
            }
        }

        // No known overload — surface an explicit error so we notice when a
        // future Kotlin version drifts yet again.
        error(
            "Capture-Code compat-k200 shim: cannot locate any compatible " +
                "`fullyExpandedType(ConeKotlinType, FirSession[, ...])` on " +
                "`$TYPE_EXPANSION_UTILS_KT` at runtime. Available methods: " +
                utilsClass.declaredMethods
                    .filter { it.name.startsWith("fullyExpandedType") }
                    .joinToString { it.toString() },
        )
    }

    private fun invokeAndUnwrap(
        method: java.lang.reflect.Method,
        args: Array<Any?>,
    ): Any? = try {
        method.invoke(null, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}
