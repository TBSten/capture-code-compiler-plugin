/*
 * task-0.2.0-cifix-3: K2.2.20 で `KtDiagnosticFactory0` / `KtDiagnosticFactory1` ctor
 * signature が変わった (drift D-DIAG-1 と命名)。 同じく `DiagnosticFactory0DelegateProvider`
 * / `DiagnosticFactory1DelegateProvider` も同様に 3-arg → 4-arg に変わっている。
 *
 * <p>compat-k220 module は K2.2.0 baseline で compile されており、 bytecode 上には
 * 「3-arg ctor」 (K2.2.10 までの形) が直接書き込まれている。 K2.2.20+ runtime ではこの
 * 3-arg ctor 自体が削除されたため、 NSME (`NoSuchMethodError`) で plugin load が即落ちる。
 *
 * <p>K{XXX}RendererMapShim のように Kotlin metadata visibility をバイパスする shim とは
 * 目的が異なり、 本 shim は <strong>runtime に存在する ctor (4-arg or 5-arg) を reflection
 * で動的に呼び分ける</strong> ことで K2.2.10 と K2.2.20 の両 baseline で動かす。
 *
 * <p>差分は次のとおり (K2.2.0 / K2.2.10):
 * <pre>{@code
 *   public KtDiagnosticFactory0(String name, Severity severity,
 *       AbstractSourceElementPositioningStrategy posStrategy, KClass<?> psiType)
 *   public KtDiagnosticFactory1(String name, Severity severity,
 *       AbstractSourceElementPositioningStrategy posStrategy, KClass<?> psiType)
 * }</pre>
 *
 * <p>K2.2.20 / K2.2.21 では:
 * <pre>{@code
 *   public KtDiagnosticFactory0(String name, Severity severity,
 *       AbstractSourceElementPositioningStrategy posStrategy, KClass<?> psiType,
 *       BaseDiagnosticRendererFactory rendererFactory)
 *   public KtDiagnosticFactory1(String name, Severity severity,
 *       AbstractSourceElementPositioningStrategy posStrategy, KClass<?> psiType,
 *       BaseDiagnosticRendererFactory rendererFactory)
 * }</pre>
 *
 * <p>本 shim は両 ctor を runtime 探索し、 利用可能な方を呼ぶ。 5-arg ctor が利用可能な
 * ときは [rendererFactory] が渡される。 K2.2.10 までは [rendererFactory] は無視される。
 */
package me.tbsten.capture.code.compat.k220.checker;

import kotlin.reflect.KClass;
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy;
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0;
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1;
import org.jetbrains.kotlin.diagnostics.Severity;
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory;
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class K220DiagnosticFactoryShim {
    private K220DiagnosticFactoryShim() {}

    /**
     * Constructs a {@link KtDiagnosticFactory0} for K2.2.x runtime, dispatching to whichever
     * ctor exists on the loaded compiler classes.
     *
     * @param rendererFactory used only when the 5-arg ctor (K2.2.20+) is available; ignored
     *                         on K2.2.10 baselines.
     */
    public static KtDiagnosticFactory0 createFactory0(
        String name,
        Severity severity,
        AbstractSourceElementPositioningStrategy positioningStrategy,
        KClass<?> psiType,
        BaseDiagnosticRendererFactory rendererFactory
    ) {
        try {
            // Try 5-arg (K2.2.20+) ctor first.
            try {
                Constructor<KtDiagnosticFactory0> ctor5 = KtDiagnosticFactory0.class
                    .getDeclaredConstructor(
                        String.class,
                        Severity.class,
                        AbstractSourceElementPositioningStrategy.class,
                        KClass.class,
                        BaseDiagnosticRendererFactory.class
                    );
                return ctor5.newInstance(name, severity, positioningStrategy, psiType, rendererFactory);
            } catch (NoSuchMethodException ignored) {
                // Fall through to 4-arg ctor (K2.2.10 and earlier).
            }
            Constructor<KtDiagnosticFactory0> ctor4 = KtDiagnosticFactory0.class
                .getDeclaredConstructor(
                    String.class,
                    Severity.class,
                    AbstractSourceElementPositioningStrategy.class,
                    KClass.class
                );
            return ctor4.newInstance(name, severity, positioningStrategy, psiType);
        } catch (ReflectiveOperationException e) {
            throw new LinkageError(
                "K220DiagnosticFactoryShim: failed to construct KtDiagnosticFactory0 '"
                    + name + "' via reflection. This indicates a Kotlin compiler API drift "
                    + "that compat-k220 does not yet cover.",
                e
            );
        }
    }

    /**
     * Resolves the source path of the file enclosing {@code context}. Dispatches between
     * the K2.2.0 / K2.2.10 baseline (which exposes {@code CheckerContext.getContainingFile()}
     * directly) and the K2.2.20+ baseline (which removed that method in favour of
     * {@code CheckerContext.getContainingFileSymbol()}). Returns {@code null} when either
     * lookup is unavailable on the runtime classpath.
     */
    public static String containingFilePath(CheckerContext context) {
        if (context == null) return null;
        // Try K2.2.0 / K2.2.10 first (FirFile).
        try {
            Method getContainingFile = context.getClass().getMethod("getContainingFile");
            Object firFile = getContainingFile.invoke(context);
            if (firFile != null) {
                Method getSourceFile = firFile.getClass().getMethod("getSourceFile");
                Object sourceFile = getSourceFile.invoke(firFile);
                if (sourceFile != null) {
                    Method getPath = sourceFile.getClass().getMethod("getPath");
                    return (String) getPath.invoke(sourceFile);
                }
            }
            return null;
        } catch (NoSuchMethodException ignored) {
            // Fall through to K2.2.20+ shape.
        } catch (ReflectiveOperationException ignored) {
            // Fall through; try the other shape as a best-effort.
        }
        // Try K2.2.20+ (FirFileSymbol).
        try {
            Method getContainingFileSymbol = context.getClass().getMethod("getContainingFileSymbol");
            Object fileSymbol = getContainingFileSymbol.invoke(context);
            if (fileSymbol == null) return null;
            Method getSourceFile = fileSymbol.getClass().getMethod("getSourceFile");
            Object sourceFile = getSourceFile.invoke(fileSymbol);
            if (sourceFile != null) {
                Method getPath = sourceFile.getClass().getMethod("getPath");
                return (String) getPath.invoke(sourceFile);
            }
        } catch (ReflectiveOperationException ignored) {
            // No usable accessor on this runtime; report unknown path.
        }
        return null;
    }

    /**
     * Registers the given {@link BaseDiagnosticRendererFactory} on the legacy
     * {@code RootDiagnosticRendererFactory.registerFactory(...)} API if it is still
     * present on the runtime classpath (K2.2.0 / K2.2.10). K2.2.20+ removed this API in
     * favour of the per-{@code KtDiagnosticsContainer} renderer factory, so this method
     * is a silent no-op on those runtimes.
     */
    public static void registerRootRendererFactoryIfAvailable(BaseDiagnosticRendererFactory factory) {
        try {
            Class<?> rootClass = Class.forName(
                "org.jetbrains.kotlin.diagnostics.rendering.RootDiagnosticRendererFactory"
            );
            Method register = rootClass.getDeclaredMethod(
                "registerFactory",
                BaseDiagnosticRendererFactory.class
            );
            // `RootDiagnosticRendererFactory` is an `object` (companion) in Kotlin source;
            // the `registerFactory` method is exposed as a static (`@JvmStatic`) method on
            // the class itself in K2.2.0 / K2.2.10 baselines.
            register.invoke(null, factory);
        } catch (ClassNotFoundException ignored) {
            // K2.2.20+ removed the legacy API. Per-container renderer factory takes over.
        } catch (ReflectiveOperationException e) {
            // Silently swallow; an out-of-spec runtime layout should not crash the plugin.
        }
    }

    /**
     * Constructs a {@link KtDiagnosticFactory1} for K2.2.x runtime, dispatching to whichever
     * ctor exists on the loaded compiler classes.
     */
    public static <A> KtDiagnosticFactory1<A> createFactory1(
        String name,
        Severity severity,
        AbstractSourceElementPositioningStrategy positioningStrategy,
        KClass<?> psiType,
        BaseDiagnosticRendererFactory rendererFactory
    ) {
        try {
            try {
                @SuppressWarnings("unchecked")
                Constructor<KtDiagnosticFactory1<A>> ctor5 = (Constructor<KtDiagnosticFactory1<A>>)
                    (Constructor<?>) KtDiagnosticFactory1.class.getDeclaredConstructor(
                        String.class,
                        Severity.class,
                        AbstractSourceElementPositioningStrategy.class,
                        KClass.class,
                        BaseDiagnosticRendererFactory.class
                    );
                return ctor5.newInstance(name, severity, positioningStrategy, psiType, rendererFactory);
            } catch (NoSuchMethodException ignored) {
                // Fall through to 4-arg ctor.
            }
            @SuppressWarnings("unchecked")
            Constructor<KtDiagnosticFactory1<A>> ctor4 = (Constructor<KtDiagnosticFactory1<A>>)
                (Constructor<?>) KtDiagnosticFactory1.class.getDeclaredConstructor(
                    String.class,
                    Severity.class,
                    AbstractSourceElementPositioningStrategy.class,
                    KClass.class
                );
            return ctor4.newInstance(name, severity, positioningStrategy, psiType);
        } catch (ReflectiveOperationException e) {
            throw new LinkageError(
                "K220DiagnosticFactoryShim: failed to construct KtDiagnosticFactory1 '"
                    + name + "' via reflection.",
                e
            );
        }
    }
}
