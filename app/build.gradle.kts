plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.minios.elizierdias"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.minios.elizierdias"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.3.6"
        vectorDrawables { useSupportLibrary = true }
        ndk {
            // proot only shipped for arm64 (real devices)
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        // Extract .so from APK so ProcessBuilder can exec libproot.so
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// ─── Download PRoot into jniLibs (executable path on Android) ───
val prootJniDir = layout.projectDirectory.dir("src/main/jniLibs/arm64-v8a")
val prootLibFile = prootJniDir.file("libproot.so")
val prootUrl =
    "https://skirsten.github.io/proot-portable-android-binaries/aarch64/proot"

val downloadProot by tasks.registering {
    description = "Download static PRoot aarch64 as libproot.so for jniLibs"
    outputs.file(prootLibFile)
    doLast {
        val out = prootLibFile.asFile
        out.parentFile.mkdirs()
        if (out.exists() && out.length() > 100_000L) {
            println("PRoot already present: ${out.length()} bytes")
            return@doLast
        }
        println("Downloading PRoot from $prootUrl …")
        ant.invokeMethod(
            "get",
            mapOf(
                "src" to prootUrl,
                "dest" to out,
                "skipexisting" to false,
            ),
        )
        if (!out.exists() || out.length() < 100_000L) {
            throw GradleException(
                "Failed to download PRoot (size=${out.length()}). Check network.",
            )
        }
        // ELF magic check
        val magic = out.inputStream().use { it.readNBytes(4) }
        if (magic[0] != 0x7f.toByte() || magic[1] != 'E'.code.toByte()) {
            out.delete()
            throw GradleException("Downloaded file is not an ELF binary")
        }
        println("PRoot ready: ${out.absolutePath} (${out.length()} bytes)")
    }
}

tasks.named("preBuild").configure { dependsOn(downloadProot) }

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")

    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.tukaani:xz:1.9")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
