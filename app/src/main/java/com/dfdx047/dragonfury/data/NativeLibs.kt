package com.dfdx047.dragonfury.data

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

/**
 * APK universal (0.7.1): dois conjuntos nativos no mesmo APK, escolhidos pelo processador.
 *
 * - ARMv8.2-A (todos os nucleos com "atomics" no /proc/cpuinfo): lib/arm64-v8a do APK e assets/libgame_*.zdat, com os
 *   atomicos LSE em linha (as reservas lwarx/stwcx do PowerPC, o command processor). E o de sempre.
 * - ARMv8.0 (Cortex-A53/A73: Snapdragon 665, 660, 835...): assets/legacy/ (app/build.gradle.kts, legacyNativeBuild),
 *   compilado com -march=armv8-a. As .so sao extraidas para getFilesDir()/legacy-lib e carregadas por caminho antes do
 *   libgame: o linker do Android resolve as dependencias dele (librexruntime.so, librexgpu-xenos.so, libSDL3.so) pelo
 *   soname das ja carregadas, e o runtime abre o plugin da GPU e os hooks do adrenotools de REX_NATIVE_LIB_DIR, que
 *   passa a ser essa pasta.
 *
 * Para testar o conjunto ARMv8.0 num aparelho ARMv8.2: criar o ficheiro <getExternalFilesDir>/legacy_cpu.
 */
object NativeLibs {
    private const val ASSET_DIR = "legacy/lib"

    @Volatile private var cached: Boolean? = null

    /** true: este aparelho usa o conjunto ARMv8.0. */
    @JvmStatic
    fun legacy(context: Context): Boolean = cached ?: run {
        val forced = context.getExternalFilesDir(null)?.let { File(it, "legacy_cpu").exists() } == true
        (com.dfdx047.dragonfury.BuildConfig.LEGACY_SET && (forced || !SystemCheck.cpu().armv82)).also { cached = it }
    }

    /** "legacy/" ou "": prefixo dos assets libgame_<id>.zdat/.json deste aparelho. */
    fun assetPrefix(context: Context): String = if (legacy(context)) "legacy/" else ""

    /** Pasta das .so deste aparelho (REX_NATIVE_LIB_DIR): a do APK, ou a do conjunto ARMv8.0 ja extraido. */
    @JvmStatic
    fun dir(context: Context): File =
        if (legacy(context)) prepare(context) else File(context.applicationInfo.nativeLibraryDir)

    /** libmain.so (leitor do executavel do jogo, GameLib). */
    @JvmStatic
    fun loadMain(context: Context) {
        if (legacy(context)) System.load(File(prepare(context), "libmain.so").absolutePath) else System.loadLibrary("main")
    }

    /**
     * GameActivity, antes do libgame: as do conjunto ARMv8.0 por caminho, em ordem de dependencia (cada uma ja encontra
     * as anteriores pelo soname). false = conjunto ARMv8.2: o SDLActivity carrega libSDL3/libmain do APK como sempre.
     */
    @JvmStatic
    fun loadGameDeps(context: Context): Boolean {
        if (!legacy(context)) return false
        val dir = prepare(context)
        for (lib in listOf("libSDL3.so", "librexruntime.so", "librexgpu-xenos.so", "libmain.so")) {
            System.load(File(dir, lib).absolutePath)
        }
        return true
    }

    /**
     * Extrai assets/legacy/lib para getFilesDir()/legacy-lib se o manifest.txt (nome, tamanho, SHA-256 de cada .so)
     * mudou, ou seja, no primeiro arranque e depois de cada atualizacao do APK. Com trinco entre processos (launcher e
     * :game). Cada .so entra por rename: um processo que ja a tenha mapeada continua com a antiga.
     */
    @Synchronized
    private fun prepare(context: Context): File {
        val dir = File(context.filesDir, "legacy-lib").apply { mkdirs() }
        val manifest = context.assets.open("$ASSET_DIR/manifest.txt").use { it.readBytes().decodeToString() }
        val stamp = File(dir, "manifest.txt")
        if (stamp.isFile && stamp.readText() == manifest) return dir
        RandomAccessFile(File(context.filesDir, "legacy-lib.lock"), "rw").use { lock ->
            lock.channel.lock().use {
                if (stamp.isFile && stamp.readText() == manifest) return dir
                val started = System.currentTimeMillis()
                stamp.delete()
                val names = manifest.lines().filter { it.isNotBlank() }.map { it.substringBefore(' ') }
                for (name in names) {
                    val part = File(dir, "$name.part")
                    context.assets.open("$ASSET_DIR/$name").use { input -> part.outputStream().use { input.copyTo(it, 1 shl 20) } }
                    if (!part.renameTo(File(dir, name))) throw java.io.IOException("legacy-lib/$name")
                }
                dir.listFiles()?.filter { it.name.endsWith(".so") && it.name !in names }?.forEach { it.delete() }
                stamp.writeText(manifest)
                android.util.Log.i(
                    "DragonFury",
                    "legacy-lib: ${names.size} bibliotecas ARMv8.0 extraidas em ${System.currentTimeMillis() - started} ms",
                )
            }
        }
        return dir
    }
}
