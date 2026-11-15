import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing. The key lives outside the repository; the git-ignored keystore.properties in the project root
// says where. Without that file (e.g. on F-Droid's build server, which signs with its own key) the release
// build is left unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.example.amoledgallery"
    compileSdk = 34

    defaultConfig {
        // Store identity of Prism Gallery (the Kotlin namespace stays as it is; it is not visible to users).
        applicationId = "com.prismgallery.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 5
        versionName = "1.2"
        // True while this version is still in testing; set to false for the published release. The
        // About page shows it (and whether this is a debug build) next to the version.
        buildConfigField("boolean", "PRERELEASE", "true")
        // When set, the app only sees media under this relative path (see the profile build type).
        buildConfigField("String", "TEST_MEDIA_PATH", "\"\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        keystoreProperties.getProperty("storeFile")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Debug builds install next to the release app instead of replacing it (they are signed with a
        // different key, so replacing would mean uninstalling the release app and losing its data).
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // The release build (R8, baseline profile, not debuggable) signed with the debug key, for measuring
        // performance on a device. Installs next to the release app like the debug build.
        create("profile") {
            initWith(getByName("release"))
            applicationIdSuffix = ".profile"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            // Test phones hold private photos: this build sees only the generated test media in this folder.
            buildConfigField("String", "TEST_MEDIA_PATH", "\"Pictures/PrismPerfTest/\"")
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Compose UI & Material 3
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Coil 3 for Compose, Video Frame Decoder, and GIF decoder
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-video:3.0.4")
    implementation("io.coil-kt.coil3:coil-gif:3.0.4")

    // Real backdrop blur for the glass bars, sheets and dialogs (iOS Liquid Glass, One UI and
    // HyperOS frosted surfaces). 1.1.x is the last line built against Compose 1.7 / Kotlin 2.0.
    implementation("dev.chrisbanes.haze:haze-android:1.1.1")

    // Media3 ExoPlayer for native video playback
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    // Media3 Transformer for exporting cropped / rotated / mirrored videos in the editor
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")

    // ExifInterface for reading image camera metadata
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Printing photos through Android's print service
    implementation("androidx.print:print:1.0.0")

    // Installs the baseline profile bundled in src/main/baseline-prof.txt so ART AOT-compiles the
    // scroll/startup hot paths. Without it a sideloaded APK installs with compiler filter "verify"
    // and runs fully interpreted - which was measured to be the dominant cause of scroll jank.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
