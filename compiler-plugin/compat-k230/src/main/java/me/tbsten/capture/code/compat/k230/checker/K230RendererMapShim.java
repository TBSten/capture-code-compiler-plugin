/*
 * task-075: Kotlin 2.3.0 で `KtDiagnosticFactoryToRendererMap(String)` constructor の
 * Kotlin metadata visibility が `internal` のまま残っているため、 Kotlin source からは
 * 直接呼べない (JVM bytecode は public)。 Java shim 経由で構築することで Kotlin
 * metadata visibility check をバイパスする。
 *
 * 既存の Java shim (K230*CheckerShim.java) と同じ理由付け:
 * "Java compiler は Kotlin metadata の visibility annotation を尊重しないので、
 *  JVM 上 public な constructor / method を Java 経由で呼ぶことで Kotlin 側の
 *  internal 修飾子を回避できる" という pattern。
 */
package me.tbsten.capture.code.compat.k230.checker;

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap;

public final class K230RendererMapShim {
    private K230RendererMapShim() {}

    public static KtDiagnosticFactoryToRendererMap create(String name) {
        return new KtDiagnosticFactoryToRendererMap(name);
    }
}
