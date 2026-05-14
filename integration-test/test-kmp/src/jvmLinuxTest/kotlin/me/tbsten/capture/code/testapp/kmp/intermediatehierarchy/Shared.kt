package me.tbsten.capture.code.testapp.kmp.intermediatehierarchy

// intermediate hierarchy シナリオの use site (jvmLinuxTest — intermediate test sourceset)。
// `commonTest → jvmLinuxTest → { jvmTest, linuxX64Test }` の階層により、
// jvm / linuxX64 の両 target test compilation から可視。
@IntermediateHierarchyMarker
internal fun intermediateHierarchyJvmOrLinuxOnly() = "jvm+linux"
