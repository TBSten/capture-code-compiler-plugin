package me.tbsten.capture.code.testapp.case105

// ケース105 の use site (jvmLinuxTest — intermediate test sourceset)。
// `commonTest → jvmLinuxTest → { jvmTest, linuxX64Test }` の階層により、
// jvm / linuxX64 の両 target test compilation から可視。
@Snippets_KmpCase105
internal fun kmpCase105_jvmOrLinuxOnly() = "jvm+linux"
