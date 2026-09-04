import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(name: String): String =
    localProps.getProperty(name) ?: System.getenv(name) ?: ""

android {
    namespace = "com.magicbill.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.magicbill.app"
        minSdk = 26
        targetSdk = 36
        // Must only ever go up: the phone compares codes to decide whether an update is newer.
        versionCode = 23
        versionName = "2.5.2"

        // The cloud's public address and anon key. Public by design (RLS is the wall), but kept
        // out of the source so the secret scanner has nothing to find in .kt.
        buildConfigField("String", "CLOUD_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "CLOUD_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")
    }

    signingConfigs {
        // Release signing MUST use keys/magic-bill-release.keystore (alias "magicbill") —
        // the same cert as every published build, or installed phones cannot update in place.
        create("release") {
            val storePath = secret("MB_KEYSTORE_FILE")
            if (storePath.isNotEmpty()) {
                storeFile = rootProject.file(storePath)
                storePassword = secret("MB_KEYSTORE_PASSWORD")
                keyAlias = secret("MB_KEY_ALIAS").ifEmpty { "magicbill" }
                keyPassword = secret("MB_KEY_PASSWORD").ifEmpty { secret("MB_KEYSTORE_PASSWORD") }
            }
        }
    }

    buildTypes {
        debug {
            // Signed with the release key too, so a dev build installs over the published app.
            if (secret("MB_KEYSTORE_FILE").isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (secret("MB_KEYSTORE_FILE").isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/LICENSE*")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.webkit)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.okhttp)
    implementation(libs.zxing.core)
    implementation(libs.bouncycastle)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.runtime)
}
