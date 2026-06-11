import java.util.Properties
import java.io.FileInputStream
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val releaseSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val missingReleaseSigningKeys = releaseSigningKeys.filter { key ->
    keystoreProperties.getProperty(key).isNullOrBlank()
}
val hasReleaseSigningConfig = keystorePropertiesFile.exists() && missingReleaseSigningKeys.isEmpty()

fun validateReleaseSigningConfig() {
    if (!keystorePropertiesFile.exists()) {
        throw GradleException(
            "Release signing requires keystore.properties. Copy keystore.properties.example, " +
                "fill it outside version control, and provide the upload keystore."
        )
    }
    if (missingReleaseSigningKeys.isNotEmpty()) {
        throw GradleException(
            "Release signing is missing keystore.properties keys: ${missingReleaseSigningKeys.joinToString()}"
        )
    }
}

android {
    namespace = "pl.syntaxdevteam.medstock"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigningConfig) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "pl.syntaxdevteam.medstock"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.9.4-Beta1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.code.scanner)
    implementation(libs.play.services.auth)
    implementation(libs.material)
    implementation(libs.poi)
    implementation(libs.poi.ooxml)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.core)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

val releaseSigningTaskNames = setOf(
    "preReleaseBuild",
    "assembleRelease",
    "bundleRelease",
    "packageRelease",
    "packageReleaseBundle",
    "signReleaseBundle",
)

tasks.configureEach {
    if (name in releaseSigningTaskNames) {
        doFirst { validateReleaseSigningConfig() }
    }
}
