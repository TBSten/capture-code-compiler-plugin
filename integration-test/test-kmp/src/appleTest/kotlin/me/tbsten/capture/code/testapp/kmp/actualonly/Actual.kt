package me.tbsten.capture.code.testapp.kmp.actualonly

// actualOnly シナリオ: 他 target の compile を成立させるための annotation **無し** actual。
// applyDefaultHierarchyTemplate() による appleTest (iosArm64Test / iosSimulatorArm64Test /
// iosX64Test / macosArm64Test / macosX64Test の親) に配置。
internal actual fun actualOnlyPlatformName(): String = "apple"
