// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
}

tasks.register("cleanRunDebug") {
    group = "build"
    description = "Czyści build i instaluje świeży debug APK na podłączonym emulatorze/urządzeniu."

    dependsOn(":app:clean")
    dependsOn(":app:assembleDebug")
    dependsOn(":app:installDebug")
}
