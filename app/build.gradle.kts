plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.zakkyhidayat.fontmanager"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.zakkyhidayat.fontmanager"
        // Android 11+: izin "Akses semua file" (MANAGE_EXTERNAL_STORAGE) dipakai untuk memindai dan memindahkan font.
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Aplikasi pribadi: rilis ditandatangani kunci debug supaya langsung bisa dipasang lewat adb.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE*,NOTICE*}"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    // Membaca arsip zip, 7z, dan tar (.gz/.xz/.bz2). xz diperlukan untuk LZMA di 7z dan .tar.xz.
    implementation(libs.commons.compress)
    implementation(libs.xz)
    testImplementation(libs.junit)
}
