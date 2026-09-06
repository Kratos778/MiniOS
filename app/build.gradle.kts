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
        versionCode = 4
        versionName = "0.3.8"
        vectorDrawables { useSupportLibrary = true }
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            // debug mantém símbolos; performance real mede-se em release
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
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

val jniArm64 = layout.projectDirectory.dir("src/main/jniLibs/arm64-v8a")
val prootSo = jniArm64.file("libproot.so")
val loaderSo = jniArm64.file("libproot_loader.so")
val tallocSo = jniArm64.file("libtalloc.so")
val userlandZipUrl =
    "https://github.com/CypherpunkArmory/UserLAnd-Assets-Support/releases/download/v1.5.1/arm64-v8a-assets.zip"

val downloadProotStack by tasks.registering {
    description = "Download UserLAnd proot+loader+talloc into jniLibs"
    outputs.files(prootSo, loaderSo, tallocSo)
    doLast {
        val outDir = jniArm64.asFile
        outDir.mkdirs()
        val proot = prootSo.asFile
        val loader = loaderSo.asFile
        val talloc = tallocSo.asFile

        if (proot.exists() && proot.length() > 50_000 &&
            loader.exists() && loader.length() > 5_000 &&
            talloc.exists() && talloc.length() > 10_000
        ) {
            println("PRoot stack already present in jniLibs")
            return@doLast
        }

        val tmpZip = File(outDir, "_ul_assets.zip")
        val tmpDir = File(outDir, "_ul_extract")
        tmpDir.mkdirs()

        println("Downloading UserLAnd arm64 assets…")
        ant.invokeMethod(
            "get",
            mapOf("src" to userlandZipUrl, "dest" to tmpZip, "skipexisting" to false),
        )
        if (!tmpZip.exists() || tmpZip.length() < 1_000_000) {
            throw GradleException("Failed to download UserLAnd assets (${tmpZip.length()} bytes)")
        }

        ant.invokeMethod(
            "unzip",
            mapOf("src" to tmpZip, "dest" to tmpDir, "overwrite" to true),
        )

        fun findFile(name: String): File {
            val matches = tmpDir.walkTopDown().filter { it.isFile && it.name == name }.toList()
            if (matches.isEmpty()) throw GradleException("Missing $name in UserLAnd zip")
            return matches.first()
        }

        val srcProot = findFile("proot")
        val srcLoader = findFile("loader")
        val srcTalloc = tmpDir.walkTopDown()
            .filter { it.isFile && (it.name == "libtalloc.so.2" || it.name.startsWith("libtalloc")) }
            .firstOrNull() ?: throw GradleException("Missing libtalloc in UserLAnd zip")

        srcProot.copyTo(proot, overwrite = true)
        srcLoader.copyTo(loader, overwrite = true)
        srcTalloc.copyTo(talloc, overwrite = true)

        listOf(proot, loader, talloc).forEach { f ->
            val magic = f.inputStream().use { it.readNBytes(4) }
            if (magic[0] != 0x7f.toByte() || magic[1] != 'E'.code.toByte()) {
                throw GradleException("${f.name} is not ELF")
            }
            println("OK ${f.name} (${f.length()} bytes)")
        }

        tmpZip.delete()
        tmpDir.deleteRecursively()
    }
}

tasks.named("preBuild").configure { dependsOn(downloadProotStack) }

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
