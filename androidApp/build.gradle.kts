import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(projects.shared)

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.arcasmartfinance"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        resValues = true
    }

    flavorDimensions += "environment"

    productFlavors {
        create("dev") {
            dimension = "environment"

            val devApiBaseUrl =
                localProperties.getProperty("DEV_API_BASE_URL")
                    ?: "http://10.0.2.2:8080"

            resValue("string", "api_base_url", devApiBaseUrl)
        }

        create("staging") {
            dimension = "environment"

            resValue(
                "string",
                "api_base_url",
                "https://staging-api.arcasmartfinance.com"
            )
        }

        create("prod") {
            dimension = "environment"

            resValue(
                "string",
                "api_base_url",
                "https://api.arcasmartfinance.com"
            )
        }
    }


    defaultConfig {
        applicationId = "com.arcasmartfinance"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 2
        versionName = "1.0.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}