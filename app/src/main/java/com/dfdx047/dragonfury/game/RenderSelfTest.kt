package com.dfdx047.dragonfury.game

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Gravity
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.dfdx047.dragonfury.data.AppPaths
import com.dfdx047.dragonfury.data.LogExporter
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10

/**
 * Autoteste do renderizador (menu do jogo, so numa luta), portado do Dragon Burst.
 *
 * O Budokai 3 nao tem um "congelar a luta" como o freeze_game do Burst Limit: aqui quem congela
 * e a GPU (dbz3_freeze_frame, patch 0056) - para depois de uma troca e repete esse frame com as
 * otimizacoes a mudar ao vivo, por isso a imagem e sempre a mesma. O teste tira duas fotos da
 * configuracao atual (a referencia e o ruido), depois troca UMA otimizacao de cada vez ("sem X"
 * se estava ligada, "com X" se estava desligada) e compara a foto com a referencia. Se trocar uma
 * otimizacao muda a imagem, e essa que esta a estragar alguma coisa. Cada estado fica ~8 s, para
 * a linha "perf" do log (a cada 5 s) mostrar quanto cada uma vale.
 *
 * Resultado em logs/autoteste.txt (+ uma foto por estado, logs/autoteste_NN.jpg) e o zip do
 * "Exportar logs" vai sozinho para Download/ no fim.
 */
class RenderSelfTest(
    private val activity: Activity,
    private val surface: SurfaceView,
    private val onDone: () -> Unit,
) {
    /** Otimizacao: rotulo + cvars que a ligam (valor "ligado" to "desligado"). */
    private class Opt(val label: String, val cvars: Map<String, Pair<String, String>>)

    private val opts = listOf(
        // Cortes genericos do Dragon Burst (patches 0051-0054 daqui).
        Opt("resolves repetidos/sem leitor", mapOf("dbz3_resolve_dedupe" to ("true" to "false"))),
        Opt("draws nulos", mapOf("dbz3_skip_null_draws" to ("true" to "false"))),
        Opt("passe sem carregar a cor", mapOf("dbz3_discard_full_overwrite" to ("true" to "false"))),
        Opt("passes direto na textura (passo 4)", mapOf("dbz3_render_into_texture" to ("true" to "false"))),
        // Partes dos render targets diretos.
        Opt("RT diretos: restauros ignorados", mapOf("dbz3_rt_elide_restores" to ("true" to "false"))),
        Opt("RT diretos: restauro da profundidade", mapOf("dbz3_skip_depth_restore" to ("true" to "false"))),
        Opt("RT diretos: Clear 4x direto em 1x", mapOf("dbz3_clear_to_1x" to ("true" to "false"))),
        Opt("RT diretos: pre-passo 4x saltado", mapOf("dbz3_skip_msaa_prepass" to ("true" to "false"))),
        Opt("RT diretos: transferencias saltadas", mapOf("dbz3_rt_direct_drop_transfers" to ("true" to "false"))),
        Opt("RT diretos: limpa as imagens irmas", mapOf("dbz3_rt_direct_clear_siblings" to ("true" to "false"))),
        // Cada uma mantem so um tipo de transferencia: diz qual faz falta (e quanto custa).
        Opt("transferencias profundidade->profundidade", mapOf("dbz3_rt_keep_dd" to ("true" to "false"))),
        Opt("transferencias cor->profundidade", mapOf("dbz3_rt_keep_cd" to ("true" to "false"))),
        Opt("transferencias profundidade->cor", mapOf("dbz3_rt_keep_dc" to ("true" to "false"))),
        Opt("transferencias cor->cor", mapOf("dbz3_rt_keep_cc" to ("true" to "false"))),
        Opt("area de render = area desenhada", mapOf("dbz3_tight_render_area" to ("true" to "false"))),
        Opt("uploads antes do passe", mapOf("dbz3_hoist_uploads" to ("true" to "false"))),
        Opt("resolve: leitura logo no fim", mapOf("dbz3_resolve_early_transition" to ("true" to "false"))),
        Opt("render targets diretos", mapOf("dbz3_rt_direct" to ("true" to "false"))),
        Opt("resolve direto na textura", mapOf("dbz3_direct_resolve_texture" to ("1" to "0"))),
        Opt("resolve sem memoria", mapOf("dbz3_direct_resolve_skip_memory" to ("true" to "false"))),
    )

    /** Estado de referencia: o que o jogo tem agora (o da config). */
    private var base = emptyMap<String, String>()
    /** So as otimizacoes que este build conhece (getCvar vazio = cvar que nao existe). */
    private var active = emptyList<Opt>()

    private val main = Handler(Looper.getMainLooper())
    private val worker = HandlerThread("DragonFurySelfTest").apply { start() }
    private val workHandler = Handler(worker.looper)
    private val report = StringBuilder()
    private var original = emptyMap<String, String>()
    private var reference: IntArray? = null
    private var noise = 0.0
    private var status: TextView? = null
    private var shot = 0

    fun start() {
        val all = opts.flatMap { it.cvars.keys } + FREEZE
        original = all.associateWith { NativeGameState.getCvar(it) }
        active = opts.filter { o -> o.cvars.keys.all { original[it].orEmpty().isNotEmpty() } }
        // Valores normalizados (o direct_resolve_texture pode estar a 2 nos testes de cor).
        base = active.flatMap { o ->
            o.cvars.map { (k, v) ->
                val cur = original[k].orEmpty()
                k to if (cur == v.first || cur == v.second) cur else v.first
            }
        }.toMap()
        report.append("Autoteste do render - ").append(java.util.Date()).append('\n')
        report.append("Referencia: as otimizacoes como estao na config. Cada linha troca UMA delas\n")
        report.append("('sem' = desligada, 'com' = ligada) e compara a imagem com a referencia.\n")
        report.append("Se a diferenca for maior que o ruido, essa otimizacao muda a imagem (provavel erro).\n")
        if (original[FREEZE].isNullOrEmpty()) {
            report.append("AVISO: este build nao tem dbz3_freeze_frame; a imagem nao fica congelada.\n")
        }
        val percent = NativeGameState.getCvar("draw_resolution_percent")
        if (percent.isNotEmpty()) {
            report.append("Resolucao interna: draw_resolution_percent=").append(percent)
                .append(" escala=").append(NativeGameState.getCvar("draw_resolution_scale_x")).append('\n')
        }
        // Como os quadros chegam a tela. A interpolacao nao muda a imagem do jogo (as fotos sao da
        // saida do jogo), por isso nao e um dos interruptores; esta linha diz em que modo correu.
        val present = NativeGameState.presentInfo()
        report.append("Apresentacao: ").append(present.ifEmpty { "desconhecida" })
            .append(" | dbz3_frame_interpolation=").append(NativeGameState.getCvar("dbz3_frame_interpolation"))
            .append(" dbz3_frameskip=").append(NativeGameState.getCvar("dbz3_frameskip"))
            .append('\n')
        report.append("Config: " + base.entries.joinToString(" ") { "${it.key}=${it.value}" } + "\n\n")
        showStatus("Autoteste do render: a congelar a luta...")
        log("autoteste: inicio (${active.size} otimizacoes)")
        NativeGameState.setCvarsLive(mapOf(FREEZE to "true") + base)
        // Referencia: a config atual, duas fotos para medir o ruido.
        after(3500) {
            capture { a ->
                after(600) {
                    capture { b ->
                        reference = a
                        noise = if (a != null && b != null) diff(a, b).first else 0.0
                        report.append(String.format(Locale.ROOT, "%-46s ruido %.3f%%\n", "referencia (config)", noise))
                        log("autoteste: referencia, ruido %.3f%%".format(Locale.ROOT, noise))
                        after(4000) { step(0) }
                    }
                }
            }
        }
    }

    private fun step(i: Int) {
        if (i > active.size) {
            finish()
            return
        }
        val next: Map<String, String>
        val label: String
        if (i == active.size) {
            label = "TUDO desligado"
            next = active.flatMap { o -> o.cvars.map { it.key to it.value.second } }.toMap()
        } else {
            val o = active[i]
            // Liga a que estava desligada na config, desliga a que estava ligada.
            val isOn = o.cvars.all { base[it.key] == it.value.first }
            label = (if (isOn) "sem " else "com ") + o.label
            next = base + o.cvars.mapValues { if (isOn) it.value.second else it.value.first }
        }
        showStatus("Autoteste do render ${i + 1}/${active.size + 1}: $label")
        log("autoteste: inicio '$label'")
        NativeGameState.setCvarsLive(next)
        after(3500) {
            capture { img ->
                val ref = reference
                val line = if (img == null || ref == null) {
                    String.format(Locale.ROOT, "%-46s sem foto\n", label)
                } else {
                    val (pct, psnr) = diff(ref, img)
                    val verdict = when {
                        pct <= noise * 2 + 0.05 -> "igual"
                        pct < 2.0 -> "PEQUENA diferenca"
                        else -> "DIFERENTE"
                    }
                    String.format(Locale.ROOT, "%-46s %.3f%% pixels, PSNR %.1f dB -> %s\n", label, pct, psnr, verdict)
                }
                report.append(line)
                log("autoteste: " + line.trim())
                after(4500) { step(i + 1) }
            }
        }
    }

    private fun finish() {
        // Volta tudo ao que estava (e descongela).
        NativeGameState.setCvarsLive(original.filterValues { it.isNotEmpty() } + (FREEZE to "false"))
        log("autoteste: fim")
        report.append("\nCorrelacione com as linhas 'perf' do dragonfury_last.log pelos horarios 'autoteste:'.\n")
        try {
            File(AppPaths.logsDir, "autoteste.txt").writeText(report.toString())
        } catch (_: Exception) {
        }
        showStatus("Autoteste terminado: a exportar os logs para Download/...")
        // O zip vai logo para Download/ (o mesmo do "Exportar logs"): e so mandar.
        workHandler.post {
            val name = try {
                LogExporter.export(activity)
            } catch (e: Exception) {
                null
            }
            showStatus(if (name != null) "Autoteste terminado: Download/$name" else "Autoteste terminado (exportar falhou: use Exportar logs)")
            main.postDelayed({
                status?.let { (it.parent as? ViewGroup)?.removeView(it) }
                status = null
                worker.quitSafely()
                onDone()
            }, 6000)
        }
    }

    /** Se a atividade fechar a meio: descongela e repoe as otimizacoes. */
    fun abort() {
        main.removeCallbacksAndMessages(null)
        NativeGameState.setCvarsLive(original.filterValues { it.isNotEmpty() } + (FREEZE to "false"))
        worker.quitSafely()
    }

    // ------------------------------------------------------------------ fotos

    private fun capture(done: (IntArray?) -> Unit) {
        val w = surface.width
        val h = surface.height
        if (w <= 0 || h <= 0) {
            done(null)
            return
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(surface, bmp, { result ->
                if (result != PixelCopy.SUCCESS) {
                    main.post { done(null) }
                    return@request
                }
                workHandler.post {
                    // 320x180 chega para ver um efeito a faltar e ignora o serrilhado.
                    val small = Bitmap.createScaledBitmap(bmp, 320, 180, true)
                    val px = IntArray(320 * 180)
                    small.getPixels(px, 0, 320, 0, 0, 320, 180)
                    try {
                        val f = File(AppPaths.logsDir, "autoteste_%02d.jpg".format(Locale.ROOT, shot++))
                        FileOutputStream(f).use { Bitmap.createScaledBitmap(bmp, 640, 360, true).compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    } catch (_: Exception) {
                    }
                    main.post { done(px) }
                }
            }, workHandler)
        } catch (e: Exception) {
            done(null)
        }
    }

    /** % de pixels com diferenca visivel (>24 em algum canal) e PSNR. */
    private fun diff(a: IntArray, b: IntArray): Pair<Double, Double> {
        var changed = 0
        var se = 0.0
        for (k in a.indices) {
            val p = a[k]
            val q = b[k]
            val dr = abs(Color.red(p) - Color.red(q))
            val dg = abs(Color.green(p) - Color.green(q))
            val db = abs(Color.blue(p) - Color.blue(q))
            if (dr > 24 || dg > 24 || db > 24) changed++
            se += (dr * dr + dg * dg + db * db).toDouble()
        }
        val mse = se / (a.size * 3.0)
        val psnr = if (mse <= 1e-9) 99.0 else 10 * log10(255.0 * 255.0 / mse)
        return changed * 100.0 / a.size to psnr
    }

    // ------------------------------------------------------------------- util

    private fun after(ms: Long, r: () -> Unit) {
        main.postDelayed(r, ms)
    }

    private fun log(line: String) {
        android.util.Log.i("dbz3", line)
        NativeGameState.logLine(line)
    }

    private fun showStatus(text: String) {
        main.post {
            val tv = status ?: TextView(activity).also { t ->
                t.setTextColor(Color.parseColor("#FFD63C"))
                t.setBackgroundColor(Color.argb(170, 22, 10, 38))
                t.textSize = 13f
                val pad = (8 * activity.resources.displayMetrics.density).toInt()
                t.setPadding(pad, pad / 2, pad, pad / 2)
                activity.addContentView(
                    t,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                    ).apply { bottomMargin = pad },
                )
                status = t
            }
            tv.text = text
        }
    }

    private companion object {
        const val FREEZE = "dbz3_freeze_frame"
    }
}
