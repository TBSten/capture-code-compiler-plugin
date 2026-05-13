package me.tbsten.capture.code.testapp.case101

/**
 * ケース #101: commonMain に置いた use site。
 *
 * 各 target compilation で plugin が IR transformer を走らせるため、jvm / js /
 * wasmJs / linuxX64 / mingwX64 / (opt-in な appleMain) のいずれをビルドしても
 * 同じ 1 件のエントリがキャプチャされる想定。
 */
@Snippets_KmpCase101
internal fun kmpCase101_shared() = "from commonMain"
