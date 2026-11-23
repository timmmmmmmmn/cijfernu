import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Ondertekening: op GitHub uit de geheime instellingen, lokaal uit
// ../../AndroidStudioProjects/cijfernu-release.properties (staat buiten de repo).
val localSigning = Properties().apply {
    val file = rootProject.file("../../AndroidStudioProjects/cijfernu-release.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signing(name: String): String? = System.getenv(name) ?: localSigning.getProperty(name)

// Elke build op GitHub krijgt een hoger versienummer, zodat Android hem als update ziet.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "nl.timvanbergen.cijfernu"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "nl.timvanbergen.cijfernu"
        minSdk = 26
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
        }
    }

    // Som.kt en SomData.kt staan in gedeeld/: de horloge-app (../wear) gebruikt ze ook.
    sourceSets["main"].kotlin.directories.add("../gedeeld")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// De app-pagina's (../app) gaan in de app zelf; alleen SOMtoday gaat over het internet.
val webBundel by tasks.registering(Sync::class) {
    from(rootProject.file("../app")) { exclude(".DS_Store") }
    into(layout.projectDirectory.dir("src/main/assets/www"))
}
tasks.named("preBuild") { dependsOn(webBundel) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.customview)
    implementation(libs.androidx.work)
    implementation(libs.play.services.wearable)
    // play-services brengt een oude fragment-versie mee; registerForActivityResult wil minstens 1.3
    implementation(libs.androidx.fragment)
}
