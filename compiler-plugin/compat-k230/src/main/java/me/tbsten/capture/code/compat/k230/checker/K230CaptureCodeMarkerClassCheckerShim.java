/*
 * Kotlin 2.2.x の FIR Checker 抽象メソッドは Kotlin の `context(...)` 構文で
 * 宣言されており、 JVM 上では `check(CheckerContext, DiagnosticReporter, D)` という
 * 引数順を持つ abstract method として現れる。 root KGP 2.0.0 で compile する compat-k230
 * の Kotlin source からは `context(...)` 構文を使えないため、 Java shim 経由で abstract
 * method を実装する。
 *
 * 2.2.0 では deprecated overload (`check(D, CheckerContext, DiagnosticReporter)`) も
 * 残るが、 2.2.20 以降では削除され abstract のみが残る。 Java shim でこれを直接 override
 * すれば、 2.2.0 / 2.2.20 双方で `AbstractMethodError` を回避できる。
 */
package me.tbsten.capture.code.compat.k230.checker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter;
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind;
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext;
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker;
import org.jetbrains.kotlin.fir.declarations.FirRegularClass;

// NOTE: Kotlin 2.2.x で `FirRegularClassChecker` は typealias
// (`= FirDeclarationChecker<FirRegularClass>`) になっており、 Java からは extend できない。
// そのため Java shim は generic 化された `FirDeclarationChecker<FirRegularClass>` を直接継承する。
public final class K230CaptureCodeMarkerClassCheckerShim extends FirDeclarationChecker<FirRegularClass> {
    public static final K230CaptureCodeMarkerClassCheckerShim INSTANCE =
        new K230CaptureCodeMarkerClassCheckerShim();

    private K230CaptureCodeMarkerClassCheckerShim() {
        super(MppCheckerKind.Common);
    }

    @Override
    public void check(
        @NotNull CheckerContext context,
        @NotNull DiagnosticReporter reporter,
        @NotNull FirRegularClass declaration
    ) {
        K230CaptureCodeMarkerClassCheckerLogic.INSTANCE.run(context, reporter, declaration);
    }
}
