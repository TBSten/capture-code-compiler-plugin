package me.tbsten.capture.code.testapp.case102

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation

/**
 * ケース #102 用 marker。
 *
 * commonMain で marker を定義し、commonMain (shared) と jvmMain (jvm-specific)
 * の双方に use site を配置する。jvm target でビルドすると 2 件 (shared + jvm)、
 * js target でビルドすると 1 件 (shared) が `capturedSources<Snippets_KmpCase102>()`
 * に展開されることを検証する。
 *
 * 詳細は `.local/test-cases.md` ケース #102 / `compiler-plugin-design.md` §7.6 参照。
 */
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_KmpCase102(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)
