package me.tbsten.capture.code.testapp.kmp.targetspecific

// target-specific シナリオの jvm-only use site (jvmTest)。 jvmTest compilation でのみ可視。
@TargetSpecificKmpMarker
internal fun targetSpecificJvmOnly() = "jvm"
