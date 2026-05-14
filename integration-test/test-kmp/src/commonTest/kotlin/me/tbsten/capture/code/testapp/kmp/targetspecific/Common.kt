package me.tbsten.capture.code.testapp.kmp.targetspecific

// target-specific シナリオの shared use site (commonTest)。 jvm / 他 platform の
// test compilation すべてで可視。
@TargetSpecificKmpMarker
internal fun targetSpecificShared() = "common"
