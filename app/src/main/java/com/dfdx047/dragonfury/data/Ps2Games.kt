package com.dfdx047.dragonfury.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * ISOs de PS2 do Gateway do Budokai 3 (codigo do port de PC v1.5.5.5): Lenda do Mr. Satan (Budokai 1 de PS2),
 * Budokai 2 e Infinite World. O jogo procura-as em ps2_games/ pelo nome (pasarela_b1.cpp BuscarIsoB1 /
 * BuscarJuego / BuscarIW), por isso cada uma e copiada com um nome fixo que essas buscas reconhecem.
 */
object Ps2Games {
    enum class Kind(val fileName: String) {
        BUDOKAI("Dragon Ball Z - Budokai (PS2).iso"),
        BUDOKAI2("Dragon Ball Z - Budokai 2 (PS2).iso"),
        INFINITE_WORLD("Dragon Ball Z - Infinite World (PS2).iso"),
    }

    fun file(kind: Kind): File = File(AppPaths.ps2GamesDir, kind.fileName)

    fun installed(): List<Kind> = Kind.values().filter { file(it).length() > 0 }

    /** Copia a ISO escolhida; [onProgress] recebe (bytes copiados, total). Uma copia parcial e apagada. */
    fun import(context: Context, uri: Uri, kind: Kind, onProgress: (Long, Long) -> Unit) {
        val total = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        val dest = file(kind)
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")
        try {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 20)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(done, total)
                    }
                }
            }
            if (!tmp.renameTo(dest)) throw java.io.IOException("rename ${tmp.name}")
        } finally {
            tmp.delete()
        }
    }
}
