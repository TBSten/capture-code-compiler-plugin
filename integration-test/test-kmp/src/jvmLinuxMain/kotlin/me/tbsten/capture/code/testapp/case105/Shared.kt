package me.tbsten.capture.code.testapp.case105

/**
 * ケース #105 の use site。
 *
 * intermediate source set `jvmLinuxMain` に配置されているため、
 * leaf target である `jvmMain` と `linuxX64Main` からはキャプチャされ、
 * `jsMain` / `wasmJsMain` / `mingwX64Main` / `appleMain` からは不可視となる。
 */
@Snippets_KmpCase105
fun kmpCase105_jvmOrAndroidOnly() = "jvm+android"
