plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "ru.maxrubtsoff.bankagg"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.maxrubtsoff.bankagg"
        minSdk = 26
        targetSdk = 34
        // F-28: the number grows with every CI build (RuStore and updates need it); 1 locally.
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = run ?: 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Real phones (64/32-bit ARM) + the x86_64 emulator. Dropping 32-bit x86
        // keeps the native OCR/ONNX libraries from bloating the APK.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    // F-28: the publishing key comes from CI secrets when they are set; until then
    // release keeps the committed key below, so updates still install over.
    val publishKeystore = System.getenv("SIGNING_KEYSTORE_PATH")?.let(::file)?.takeIf { it.exists() }

    signingConfigs {
        // Committed debug keystore so every CI build is signed with the SAME
        // key. Without this, GitHub's runner generates a fresh debug key each
        // run and Android refuses to install the update ("signatures differ").
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (publishKeystore != null) create("publish") {
            storeFile = publishKeystore
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            // F-28: a debug build installs beside the real app, never over it — its
            // run-as access then reaches only its own empty data.
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Itogo debug")
        }
        // The APK people install (B-18): not debuggable, so Compose runs at full
        // speed and the baseline profiles apply. R8 shrinks and optimises it
        // (F-28, U-31); proguard-rules.pro keeps what JNI and the manifest name.
        release {
            signingConfig = signingConfigs.findByName("publish") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // U-31: the release build as installed, measurable by Macrobenchmark.
        create("benchmark") {
            initWith(getByName("release"))
            proguardFiles("benchmark-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // The embedder model is read as raw bytes; keep it uncompressed in the APK.
        noCompress += "onnx"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// U-31: domain models are immutable — Compose can skip what didn't change.
composeCompiler {
    stabilityConfigurationFile = rootProject.layout.projectDirectory.file("app/compose-stability.conf")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    // U-31: installs the app's Baseline Profile on devices without Play (RuStore, sideload).
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")
    // Fingerprint unlock (F-10): the system BiometricPrompt, offline.
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Room — local storage
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    // F-26: the database file is encrypted (SQLCipher, offline, open source); its key lives in the Keystore.
    implementation("net.zetetic:sqlcipher-android:4.6.1@aar")
    implementation("androidx.sqlite:sqlite:2.4.0")

    // Coil — async image loading for shot previews/thumbnails
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Glance — home-screen app widget (кэшбэк по категориям с процентами)
    implementation("androidx.glance:glance-appwidget:1.1.0")
    // Daily reminders (F-24): deposits closing, cashback of a new month. Offline.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // EXIF — correct photo rotation before OCR
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Tesseract (offline OCR, Cyrillic-capable) — ML Kit can't do Cyrillic.
    // Published on JitPack (see settings.gradle.kts), not Maven Central.
    // Use the single `tesseract4android` module — the bare repo artifact pulls
    // BOTH it and `-openmp`, which collide (duplicate leptonica classes).
    implementation("com.github.adaptech-cz.Tesseract4Android:tesseract4android:4.8.0")

    // ONNX Runtime — runs the offline similarity model (assets/embedder).
    // One version for the APK and the JVM tests, so tests check what ships.
    // 1.20.0: the 1.21 JVM build needs a newer MSVC runtime than dev machines have.
    val onnxRuntime = "1.20.0"
    implementation("com.microsoft.onnxruntime:onnxruntime-android:$onnxRuntime")
    // Unit tests (JVM)
    // JVM build of ONNX Runtime so tests can run the real model on the desktop.
    testImplementation("com.microsoft.onnxruntime:onnxruntime:$onnxRuntime")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // Instrumented / Compose UI tests
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Unit tests use the JVM ONNX Runtime; the Android AAR (same classes, Android
// natives) must not be on their classpath.
configurations.matching { it.name.contains("UnitTest") }.configureEach {
    exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime-android")
}
