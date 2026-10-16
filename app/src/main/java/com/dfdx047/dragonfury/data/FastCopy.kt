package com.dfdx047.dragonfury.data

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Copia rapida de ficheiros grandes (importacao do jogo, ~2,5 GB).
 *
 * - Leituras posicionais num FileChannel (pread) em vez de InputStream: cada copia tem o seu
 *   descritor e pode ler de qualquer ponto do .iso sem seek partilhado.
 * - FileChannel.transferTo para o destino: no Android e um sendfile() no kernel, sem passar os
 *   bytes pela memoria da app (cai para um buffer direto de 8 MB se o kernel recusar).
 * - Duas copias em paralelo (as maiores primeiro): o armazenamento UFS e o FUSE do Android
 *   rendem mais com mais de um pedido em voo.
 * - Escreve em "<nome>.part" e so renomeia no fim: uma importacao interrompida nunca deixa
 *   um ficheiro truncado com o nome certo.
 */
object FastCopy {

    /** Origem aberta por cada copia (o seu proprio descritor). */
    class Source(val channel: FileChannel, private val closeable: Closeable) : Closeable {
        override fun close() {
            try { channel.close() } catch (_: IOException) {}
            closeable.close()
        }
    }

    class Job(
        val label: String,
        val target: File,
        val offset: Long,
        val size: Long,
        val open: () -> Source,
    )

    private const val CHUNK = 32L shl 20
    private const val BUFFER = 8 shl 20
    private const val REPORT_EVERY = 8L shl 20

    /**
     * Copia todos os [jobs]. [onProgress] recebe (ficheiro, bytes copiados no total, total) e
     * pode ser chamado de varias threads.
     */
    fun run(jobs: List<Job>, threads: Int = 2, onProgress: (String, Long, Long) -> Unit) {
        if (jobs.isEmpty()) return
        val total = jobs.sumOf { it.size }
        val done = AtomicLong(0)
        val lastReport = AtomicLong(0)
        val failed = AtomicBoolean(false)
        val pool = Executors.newFixedThreadPool(threads.coerceIn(1, jobs.size))
        try {
            val futures: List<Future<*>> = jobs.sortedByDescending { it.size }.map { job ->
                pool.submit {
                    if (failed.get()) return@submit
                    try {
                        copy(job) { n ->
                            val now = done.addAndGet(n)
                            val last = lastReport.get()
                            if (now - last >= REPORT_EVERY && lastReport.compareAndSet(last, now)) {
                                onProgress(job.label, now, total)
                            }
                            !failed.get()
                        }
                    } catch (e: Throwable) {
                        failed.set(true)
                        throw e
                    }
                }
            }
            var error: Throwable? = null
            for (f in futures) {
                try {
                    f.get()
                } catch (e: ExecutionException) {
                    if (error == null) error = e.cause ?: e
                }
            }
            error?.let { throw it }
            onProgress("", total, total)
        } finally {
            pool.shutdownNow()
        }
    }

    /** [onChunk] devolve false para parar (outra copia falhou). */
    private fun copy(job: Job, onChunk: (Long) -> Boolean) {
        job.target.parentFile?.mkdirs()
        val tmp = File(job.target.parentFile, job.target.name + ".part")
        try {
            job.open().use { source ->
                FileOutputStream(tmp).channel.use { out ->
                    var done = 0L
                    var direct = true
                    var buffer: ByteBuffer? = null
                    while (done < job.size) {
                        val want = minOf(CHUNK, job.size - done)
                        var n = 0L
                        if (direct) {
                            n = try {
                                source.channel.transferTo(job.offset + done, want, out)
                            } catch (_: IOException) {
                                0L
                            }
                            if (n <= 0L) direct = false
                        }
                        if (!direct) {
                            val buf = buffer ?: ByteBuffer.allocateDirect(BUFFER).also { buffer = it }
                            buf.clear()
                            buf.limit(minOf(BUFFER.toLong(), want).toInt())
                            val r = source.channel.read(buf, job.offset + done)
                            if (r <= 0) throw IOException("${job.label}: fim inesperado da origem")
                            buf.flip()
                            while (buf.hasRemaining()) out.write(buf)
                            n = r.toLong()
                        }
                        done += n
                        if (!onChunk(n)) throw IOException("cancelado")
                    }
                }
            }
            if (job.target.exists()) job.target.delete()
            if (!tmp.renameTo(job.target)) throw IOException("${job.target.name}: nao foi possivel renomear")
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
    }
}
