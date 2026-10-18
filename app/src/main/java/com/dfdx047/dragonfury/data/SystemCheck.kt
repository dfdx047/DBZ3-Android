package com.dfdx047.dragonfury.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.system.OsConstants
import java.io.File

/**
 * Verificacoes do aparelho antes e depois de jogar.
 *
 * - Processador: o port e compilado para ARMv8.2-A (atomicos LSE em linha). Nucleos ARMv8.0
 *   (Cortex-A53/A73: Snapdragon 665, 660, 835...) nao tem essas instrucoes e o jogo fecha logo
 *   ao abrir com SIGILL (Xiaomi Mi A3, 18/10). O /proc/cpuinfo diz se todos os nucleos tem
 *   "atomics".
 * - Como o jogo terminou: o Android guarda o motivo da saida de cada processo (API 30+). Se o
 *   processo :game morreu por um crash, por falta de memoria ou travado, o launcher mostra-o.
 */
object SystemCheck {

    data class Cpu(val supported: Boolean, val cores: Int, val coresWithoutAtomics: Int, val features: String)

    fun cpu(): Cpu {
        val lines = try {
            File("/proc/cpuinfo").readLines()
        } catch (e: Exception) {
            return Cpu(true, 0, 0, "?")
        }
        val features = lines.filter { it.startsWith("Features") }.map { it.substringAfter(':').trim() }
        if (features.isEmpty()) return Cpu(true, 0, 0, "?")
        val missing = features.count { f -> "atomics" !in f.split(' ') }
        return Cpu(missing == 0, features.size, missing, features.first())
    }

    /** RAM do aparelho em MB (/proc/meminfo): total e disponivel agora. 0 se nao der para ler. */
    data class Ram(val totalMb: Int, val availableMb: Int)

    fun ram(): Ram = try {
        var total = 0L
        var avail = 0L
        File("/proc/meminfo").forEachLine { line ->
            val kb = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L
            when {
                line.startsWith("MemTotal:") -> total = kb
                line.startsWith("MemAvailable:") -> avail = kb
            }
        }
        Ram((total / 1024).toInt(), (avail / 1024).toInt())
    } catch (e: Exception) {
        Ram(0, 0)
    }

    /** Fim anormal do processo do jogo. */
    data class GameExit(
        val reason: Int,
        val signal: Int,
        val description: String,
        val timestamp: Long,
        val importanceForeground: Boolean,
    ) {
        val isNativeCrash get() = reason == ApplicationExitInfo.REASON_CRASH_NATIVE
        val isIllegalInstruction get() = isNativeCrash && signal == OsConstants.SIGILL
        val isJavaCrash get() = reason == ApplicationExitInfo.REASON_CRASH
        val isLowMemory get() = reason == ApplicationExitInfo.REASON_LOW_MEMORY
        val isFrozen get() = reason == ApplicationExitInfo.REASON_ANR
        val isInitFailure get() = reason == ApplicationExitInfo.REASON_INITIALIZATION_FAILURE

        fun reasonName(): String = when (reason) {
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE (sinal $signal)"
            ApplicationExitInfo.REASON_CRASH -> "CRASH"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED ($signal)"
            else -> "reason=$reason"
        }
    }

    /**
     * A saida anormal mais recente do processo do jogo depois de [sinceMs]. Saidas normais (o
     * jogador fechou, o launcher terminou-o, o sistema matou-o em segundo plano) dao null.
     */
    fun lastGameExit(context: Context, sinceMs: Long): GameExit? {
        val am = context.getSystemService(ActivityManager::class.java) ?: return null
        val list = try {
            am.getHistoricalProcessExitReasons(context.packageName, 0, 16)
        } catch (e: Exception) {
            return null
        }
        val info = list.filter { it.processName.endsWith(":game") && it.timestamp > sinceMs }
            .maxByOrNull { it.timestamp } ?: return null
        val abnormal = when (info.reason) {
            ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_CRASH,
            ApplicationExitInfo.REASON_ANR,
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> true
            // Falta de memoria so conta se o jogo estava a frente (em segundo plano e normal).
            ApplicationExitInfo.REASON_LOW_MEMORY ->
                info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
            else -> false
        }
        if (!abnormal) return null
        return GameExit(
            info.reason,
            info.status,
            info.description ?: "",
            info.timestamp,
            info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE,
        )
    }

    /**
     * A GPU caiu na ultima sessao (VK_ERROR_DEVICE_LOST: o driver deu "GPU faulted or hung").
     * Visto no Retroid Pocket 5 com um Turnip; o jogo fica preso a falhar o present e nem
     * sempre termina com um crash, por isso olha-se para o log do runtime.
     */
    fun gpuLost(sinceMs: Long): Boolean = try {
        val f = AppPaths.lastRunLog
        f.isFile && f.lastModified() > sinceMs && f.useLines { lines ->
            lines.any { "VK_ERROR_DEVICE_LOST" in it || "GPU faulted or hung" in it }
        }
    } catch (e: Exception) {
        false
    }

    /** As ultimas linhas de aviso/erro do log do runtime (para a tela de erro). */
    fun lastRuntimeErrors(max: Int = 6): List<String> = try {
        AppPaths.lastRunLog.takeIf { it.isFile }?.readLines()
            ?.filter { it.contains("[error]") || it.contains("[critical]") || it.contains("[warning]") }
            ?.takeLast(max)
            ?.map { it.replace(Regex("^(\\[[^\\]]*\\] ){1,4}"), "").take(200) }
            ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
