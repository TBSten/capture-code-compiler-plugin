plugins {
    alias(libs.plugins.ksp).apply(false)
    alias(libs.plugins.shadow).apply(false)
}

allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}
