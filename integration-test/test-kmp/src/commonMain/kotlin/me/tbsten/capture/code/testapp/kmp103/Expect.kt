package me.tbsten.capture.code.testapp.kmp103

/**
 * task-022 (ケース #103): expect 側にも `@Platform_KmpCase103` を付与する。
 *
 * expect / actual 両方に annotation が付くので、jvm target の compilation では
 * commonMain (この expect 宣言) と jvmMain (`Actual.kt` の actual 宣言) を独立に走査し、
 * `capturedSources<Platform_KmpCase103>()` には **2 件** がリストアップされる。
 */
@Platform_KmpCase103
internal expect fun kmpCase103_currentTimeMillis(): Long
