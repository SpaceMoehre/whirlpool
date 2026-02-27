import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

fun findPythonCommand(): String? {
    val override = System.getenv("CHAQUOPY_BUILD_PYTHON")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    if (override != null) {
        return override
    }

    val pathEntries = System.getenv("PATH")
        .orEmpty()
        .split(File.pathSeparatorChar)
    val candidates = listOf("python3.11", "python3", "python")

    fun commandExists(command: String): Boolean {
        if (command.contains(File.separatorChar)) {
            val file = File(command)
            return file.exists() && file.canExecute()
        }
        return pathEntries.any { entry ->
            val file = File(entry, command)
            file.exists() && file.canExecute()
        }
    }

    return candidates.firstOrNull(::commandExists)
}

fun detectPythonMinorVersion(command: String): String? {
    val process = runCatching {
        ProcessBuilder(
            command,
            "-c",
            "import sys; print(f'{sys.version_info[0]}.{sys.version_info[1]}')",
        )
            .redirectErrorStream(true)
            .start()
    }.getOrNull() ?: return null

    return runCatching {
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val exitCode = process.waitFor()
        if (exitCode == 0 && Regex("""\d+\.\d+""").matches(output)) {
            output
        } else {
            null
        }
    }.getOrNull()
}

val chaquopyBuildPython = findPythonCommand()
val chaquopyPythonVersion = System.getenv("CHAQUOPY_PYTHON_VERSION")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: chaquopyBuildPython?.let(::detectPythonMinorVersion)
    ?: "3.11"

android {
    namespace = "com.whirlpool.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.whirlpool.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

chaquopy {
    defaultConfig {
        if (chaquopyBuildPython != null) {
            buildPython(chaquopyBuildPython)
        }
        version = chaquopyPythonVersion
        pip {
            install("yt-dlp")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
}
