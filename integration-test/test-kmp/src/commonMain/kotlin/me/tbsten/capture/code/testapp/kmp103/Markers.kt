package me.tbsten.capture.code.testapp.kmp103

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation

/**
 * task-022 (ケース #103) で用いる marker annotation。
 *
 * `compiler-plugin-design.md` §7.6 の **expect / actual 独立カウント** シナリオ:
 * `@Platform_KmpCase103 expect fun foo()` + `@Platform_KmpCase103 actual fun foo() { ... }` の
 * 両方に annotation が付いている場合、`capturedSources<Platform_KmpCase103>()` は
 * **expect 側と actual 側で 2 件キャプチャ** する。
 *
 * commonMain に置くことで、jvmMain / jsMain / wasmJsMain / native ターゲット全てから可視。
 */
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Platform_KmpCase103(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)
