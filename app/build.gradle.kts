import java.security.MessageDigest
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CMake: por omissao usa o CMake do sistema (o mesmo que ja compilou o SDK no
// PC), indicado em local.properties com "cmake.dir=/usr". A versao e lida
// automaticamente do proprio binario, porque o Gradle exige a versao exata.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val cmakeDir: String? = localProperties.getProperty("cmake.dir")
val cmakeVersionFromDir: String? = cmakeDir?.let { dir ->
    val output = providers.exec {
        commandLine("$dir/bin/cmake", "--version")
    }.standardOutput.asText.get()
    Regex("""cmake version (\d+\.\d+\.\d+)""").find(output)?.groupValues?.get(1)
}

// "./gradlew assembleRelease -Pdf.uiOnly=true" compila so o launcher (Kotlin),
// sem o jogo nativo: util para mexer na interface sem esperar pelo NDK.
// O APK resultante abre o launcher mas nao consegue arrancar o jogo.
val uiOnly = (project.findProperty("df.uiOnly") as String?) == "true"
// "-Pdf.fullSources=true": compila tambem Tag Team, online e campanhas do projeto
// original (precisa de um external/dbz3 recente e do generated/ dessa versao).
val fullSources = (project.findProperty("df.fullSources") as String?) == "true"
// "-Pdf.d3dTrace=true": compila os hooks de rastreio das funcoes D3D
// (app/src/main/cpp/d3d_trace_hooks.cpp, gerado por tools/d3d_trace_gen.py).
// So para mapear a biblioteca D3D; nao usar em testes de desempenho.
val d3dTrace = (project.findProperty("df.d3dTrace") as String?) == "true"
// APK universal (0.7.1): o mesmo APK leva o nativo compilado para ARMv8.2-A (lib/arm64-v8a e assets/libgame_*.zdat,
// atomicos LSE em linha) e para ARMv8.0 (assets/legacy/: lib/*.so e libgame_*.zdat, -march=armv8-a, para Cortex-A53/A73:
// Snapdragon 665/660/835). O ARMv8.0 sai de um segundo build CMake (app/.cxx/legacy, tarefa legacyNativeBuild); o app
// escolhe pelo /proc/cpuinfo (NativeLibs.kt), por isso nenhum dos dois perde nada.
// "-Pdf.noLegacy=true" salta o conjunto ARMv8.0 (testes rapidos; o APK so serve aparelhos ARMv8.2).
val noLegacy = (project.findProperty("df.noLegacy") as String?) == "true"
val nativeTargets = listOf(
    "main", "rexruntime", "rexgpu-xenos", "SDL3-shared",
    // Hooks do libadrenotools (driver Vulkan alternativo, ex. Turnip).
    "main_hook", "file_redirect_hook", "gsl_alloc_hook", "hook_impl",
)

android {
    namespace = "com.dfdx047.dragonfury"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.dfdx047.dragonfury"
        // API 30 (Android 11): memfd, posix_spawn, strtod_l e ASharedMemory
        // estao todos disponiveis no bionic.
        minSdk = 30
        targetSdk = 35
        versionCode = 37
        versionName = "0.7.1"
        // O APK traz o conjunto ARMv8.0 (assets/legacy/, NativeLibs.kt).
        buildConfigField("boolean", "LEGACY_SET", (!noLegacy && !uiOnly).toString())

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        if (!uiOnly) {
            externalNativeBuild {
                cmake {
                    arguments += listOf(
                        "-DANDROID_STL=c++_shared",
                        // O runtime e sempre compilado em Release: em Debug o jogo
                        // recompilado fica lento demais para testar.
                        "-DCMAKE_BUILD_TYPE=Release",
                        "-DREXGLUE_ENABLE_TRACY=OFF",
                        "-DREXGLUE_BUILD_TESTS=OFF",
                        "-DDBZ3_FULL_SOURCES=" + (if (fullSources) "ON" else "OFF"),
                        "-DDBZ3_D3D_TRACE=" + (if (d3dTrace) "ON" else "OFF"),
                        "-DDF_MARCH=armv8.2-a",
                    )
                    targets += nativeTargets
                }
            }
        }
    }

    if (!uiOnly) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = cmakeVersionFromDir ?: "3.31.6"
            }
        }
    }

    sourceSets {
        getByName("main") {
            // Classes Java do SDL (SDLActivity e companhia), direto do submodulo
            // do SDK, para ficarem sempre na mesma versao que o libSDL3.so.
            java.srcDir("../external/rexglue-sdk-0.10/thirdparty/sdl3/android-project/app/src/main/java")
            // libgame_b3/b1.zdat + .json: o codigo de cada jogo encriptado (tools/pack-libgame.py, chamado pelo CMake).
            assets.srcDir("build/dragonfury-assets")
        }
    }

    packaging {
        jniLibs {
            // As .so ficam extraidas em nativeLibraryDir: o SDL abre libmain.so
            // por caminho e o runtime carrega o plugin da GPU de la.
            useLegacyPackaging = true
            // O codigo dos jogos so vai encriptado (assets/libgame_*.zdat), nunca como .so.
            excludes += "**/libgame_b3.so"
            excludes += "**/libgame_b1.so"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // Assinado com a chave de debug por enquanto, para instalar direto.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

// Conjunto ARMv8.0 do APK universal: o mesmo CMakeLists com -march=armv8-a, fora do AGP (que so faz um build por ABI).
// Os libgame vao encriptados para build/dragonfury-assets/legacy (DF_PACK_DIR); as outras .so, sem simbolos, para
// legacy/lib, com um manifest.txt (nome, tamanho, SHA-256) que o NativeLibs.kt usa para saber se ja as extraiu.
val legacyCxx = file(".cxx/legacy/arm64-v8a")
val legacyObj = file(".cxx/legacy/obj")
val legacyAssets = layout.buildDirectory.dir("dragonfury-assets/legacy").get().asFile
val cmakeBin = cmakeDir?.let { "$it/bin/cmake" } ?: "cmake"
val ninjaBin = cmakeDir?.let { "$it/bin/ninja" }?.takeIf { File(it).canExecute() } ?: "ninja"
val legacyConfigure = tasks.register<Exec>("legacyNativeConfigure") {
    onlyIf { !File(legacyCxx, "CMakeCache.txt").isFile }
    val ndk = androidComponents.sdkComponents.ndkDirectory.get().asFile.absolutePath
    commandLine(
        cmakeBin, "-H" + file("src/main/cpp").absolutePath, "-B" + legacyCxx.absolutePath, "-GNinja",
        "-DCMAKE_SYSTEM_NAME=Android", "-DCMAKE_SYSTEM_VERSION=30", "-DANDROID_PLATFORM=android-30",
        "-DANDROID_ABI=arm64-v8a", "-DCMAKE_ANDROID_ARCH_ABI=arm64-v8a",
        "-DANDROID_NDK=$ndk", "-DCMAKE_ANDROID_NDK=$ndk",
        "-DCMAKE_TOOLCHAIN_FILE=$ndk/build/cmake/android.toolchain.cmake", "-DCMAKE_MAKE_PROGRAM=$ninjaBin",
        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=" + legacyObj.absolutePath,
        "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=" + legacyObj.absolutePath,
        "-DANDROID_STL=c++_shared", "-DCMAKE_BUILD_TYPE=Release",
        "-DREXGLUE_ENABLE_TRACY=OFF", "-DREXGLUE_BUILD_TESTS=OFF",
        "-DDBZ3_FULL_SOURCES=" + (if (fullSources) "ON" else "OFF"),
        "-DDBZ3_D3D_TRACE=" + (if (d3dTrace) "ON" else "OFF"),
        "-DDF_MARCH=armv8-a", "-DDF_PACK_DIR=" + legacyAssets.absolutePath,
    )
}
val legacyNativeBuild = tasks.register("legacyNativeBuild") {
    dependsOn(legacyConfigure)
    val ndk = androidComponents.sdkComponents.ndkDirectory
    doLast {
        fun run(vararg cmd: String) {
            val p = ProcessBuilder(*cmd).inheritIO().start()
            if (p.waitFor() != 0) throw GradleException("falhou: ${cmd.joinToString(" ")}")
        }
        run(cmakeBin, "--build", legacyCxx.absolutePath, "--target", *nativeTargets.toTypedArray())
        val llvm = File(ndk.get().asFile, "toolchains/llvm/prebuilt/linux-x86_64")
        val strip = File(llvm, "bin/llvm-strip").absolutePath
        val out = File(legacyAssets, "lib").apply { deleteRecursively(); mkdirs() }
        val libs = listOf(
            "libSDL3.so", "librexruntime.so", "librexgpu-xenos.so", "libmain.so", "libc++_shared.so",
            "libhook_impl.so", "libmain_hook.so", "libfile_redirect_hook.so", "libgsl_alloc_hook.so",
        )
        val manifest = StringBuilder()
        for (lib in libs) {
            val dst = File(out, lib)
            // O libc++_shared.so nao passa pelo CMake: vem do NDK (o AGP faz o mesmo). Os hooks do adrenotools
            // precisam dele na mesma pasta (o namespace deles so procura la).
            val src = if (lib == "libc++_shared.so") File(llvm, "sysroot/usr/lib/aarch64-linux-android/$lib")
            else File(legacyObj, lib)
            run(strip, "--strip-unneeded", "-o", dst.absolutePath, src.absolutePath)
            val sha = MessageDigest.getInstance("SHA-256").digest(dst.readBytes())
                .joinToString("") { "%02x".format(it) }
            manifest.append("$lib ${dst.length()} $sha\n")
        }
        File(out, "manifest.txt").writeText(manifest.toString())
    }
}

// Os assets juntam-se depois do CMake (que escreve os libgame_*.zdat).
tasks.configureEach {
    val m = Regex("merge(\\w+)Assets").matchEntire(name)
    if (m != null && !uiOnly) {
        dependsOn("externalNativeBuild" + m.groupValues[1])
        if (!noLegacy) dependsOn(legacyNativeBuild)
    }
}
