import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Zelfde sleutel en zelfde applicationId als de telefoon-app: alleen dan mogen ze via de Wear OS-datalaag praten.
val localSigning = Properties().apply {
    val file = rootProject.file("../../AndroidStudioProjects/cijfernu-release.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signing(name: String): String? = System.getenv(name) ?: localSigning.getProperty(name)

val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "nl.timvanbergen.cijfernu"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "nl.timvanbergen.cijfernu"
        minSdk = 30
        targetSdk = 37
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        create("release") {
            signing("CIJFERNU_KEYSTORE")?.let { storeFile = file(it) }
            storePassword = signing("CIJFERNU_KEYSTORE_PASSWORD")
            keyAlias = signing("CIJFERNU_KEY_ALIAS")
            keyPassword = signing("CIJFERNU_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // Compose en play-services maken hem anders ruim 20 MB
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    sourceSets["main"].kotlin.directories.add("../gedeeld")

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.remote.interactions)
    implementation(libs.play.services.wearable)
}
