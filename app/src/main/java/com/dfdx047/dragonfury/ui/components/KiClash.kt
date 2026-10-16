package com.dfdx047.dragonfury.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import com.dfdx047.dragonfury.ui.theme.DfColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Animacao da tela inicial: dois raios de ki (azul e dourado) a chocar no meio do ecra, num
 * ceu escuro — o "choque de raios" dos combates do jogo, sem personagens. O ponto do choque
 * vai e vem, a esfera de energia pulsa, saltam faiscas e raios eletricos e, de tempos a
 * tempos, sai uma onda de choque. Tudo desenhado num Canvas (sem imagens), com um relogio
 * de frames: para sozinho quando o launcher nao esta visivel.
 */
@Composable
fun KiClashAnimation(modifier: Modifier = Modifier) {
    val time by produceState(0f) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> value = (now - start) / 1_000_000_000f }
        }
    }
    Canvas(modifier) { drawKiClash(time) }
}

private val BlueBeam = listOf(DfColors.KiBlue, Color(0xFF7FE6FF), DfColors.KiWhite)
private val GoldBeam = listOf(DfColors.OrangeDeep, DfColors.Gold, Color(0xFFFFFBE8))

/** Pseudo-aleatorio estavel (0..1) a partir de dois inteiros. */
private fun hash(i: Int, j: Int): Float {
    var x = i * 374761393 + j * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777216f
}

private fun DrawScope.drawKiClash(t: Float) {
    val w = size.width
    val h = size.height
    val unit = min(w, h)

    // Ceu: azul-noite -> roxo, com brilho do choque.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF060A24), Color(0xFF151040), Color(0xFF2A0E2E))))

    // Estrelas a cintilar.
    for (i in 0 until 46) {
        val sx = hash(i, 1) * w
        val sy = hash(i, 2) * h * 0.75f
        val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * (1.5f + hash(i, 3) * 3f) + i))
        drawCircle(Color.White.copy(alpha = 0.55f * tw), radius = 0.6f + hash(i, 4) * 1.4f, center = Offset(sx, sy))
    }

    // O ponto do choque anda: um empurra, o outro responde.
    val push = 0.10f * sin(t * 0.85f) + 0.035f * sin(t * 3.1f) + 0.015f * sin(t * 11f)
    val cx = w * (0.5f + push)
    val cy = h * 0.56f
    val shake = (unit * 0.004f) * sin(t * 47f)
    val beamH = h * 0.16f

    translate(left = shake, top = shake * 0.6f) {
        // Chao distante iluminado pelo choque.
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color(0x33FF8A1F), Color(0x88140818)),
                startY = h * 0.72f,
                endY = h,
            ),
            topLeft = Offset(0f, h * 0.72f),
        )

        // Linhas de velocidade a partir do choque.
        for (i in 0 until 22) {
            val a = (i / 22f) * 2f * PI.toFloat() + t * 0.25f + hash(i, 7) * 0.2f
            val r0 = unit * (0.22f + hash(i, 8) * 0.1f)
            val r1 = r0 + unit * (0.35f + hash(i, 9) * 0.5f)
            drawLine(
                Color.White.copy(alpha = 0.03f + 0.04f * hash(i, 10)),
                Offset(cx + cos(a) * r0, cy + sin(a) * r0),
                Offset(cx + cos(a) * r1, cy + sin(a) * r1),
                strokeWidth = 1.5f,
            )
        }

        // Os dois raios.
        drawBeam(fromX = -beamH, toX = cx, cy = cy, thick = beamH, t = t, colors = BlueBeam, seed = 0)
        drawBeam(fromX = w + beamH, toX = cx, cy = cy, thick = beamH * 0.95f, t = t, colors = GoldBeam, seed = 1)

        // Ondas de choque.
        for (k in 0 until 2) {
            val phase = ((t + k * 0.8f) / 1.6f) % 1f
            val rr = unit * (0.08f + phase * 0.55f)
            drawCircle(
                Color.White.copy(alpha = 0.35f * (1f - phase)),
                radius = rr,
                center = Offset(cx, cy),
                style = Stroke(width = unit * 0.012f * (1f - phase) + 1f),
            )
        }

        // Esfera do choque.
        val pulse = 1f + 0.12f * sin(t * 13f) + 0.05f * sin(t * 37f)
        val core = beamH * 0.72f * pulse
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0x88FFFFFF), Color(0x55FFC94A), Color(0x223FD0FF), Color.Transparent),
                center = Offset(cx, cy),
                radius = core * 2.6f,
            ),
            radius = core * 2.6f,
            center = Offset(cx, cy),
            blendMode = BlendMode.Plus,
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White, Color(0xFFFFF4C8), DfColors.Gold, DfColors.Orange.copy(alpha = 0f)),
                center = Offset(cx, cy),
                radius = core * 1.35f,
            ),
            radius = core * 1.35f,
            center = Offset(cx, cy),
        )

        // Raios eletricos: mudam de forma ~12 vezes por segundo.
        val bolt = floor(t * 12f).toInt()
        for (b in 0 until 4) {
            val a = hash(bolt, b) * 2f * PI.toFloat()
            val len = core * (1.3f + hash(bolt, b + 10) * 1.2f)
            val path = Path()
            path.moveTo(cx, cy)
            val segments = 6
            for (s in 1..segments) {
                val f = s / segments.toFloat()
                val jitter = (hash(bolt * 31 + b, s) - 0.5f) * core * 0.45f
                path.lineTo(
                    cx + cos(a) * len * f - sin(a) * jitter,
                    cy + sin(a) * len * f + cos(a) * jitter,
                )
            }
            val color = if (b % 2 == 0) DfColors.KiWhite else DfColors.Gold
            drawPath(path, color.copy(alpha = 0.25f), style = Stroke(width = 6f, cap = StrokeCap.Round))
            drawPath(path, color.copy(alpha = 0.9f), style = Stroke(width = 1.8f, cap = StrokeCap.Round))
        }

        // Faiscas a saltar do choque (cada uma vive 0,9 s e renasce noutra direcao).
        val count = 70
        for (i in 0 until count) {
            val cycleT = t / 0.9f + i / count.toFloat()
            val cycle = floor(cycleT).toInt()
            val p = cycleT - cycle
            // Mais faiscas para cima e para os lados que para baixo.
            val a = (hash(i, cycle) * 1.6f - 1.3f) * PI.toFloat()
            val dist = unit * (0.12f + hash(i + 99, cycle) * 0.65f) * p + core * 0.8f
            val gravity = unit * 0.25f * p * p
            val x = cx + cos(a) * dist
            val y = cy + sin(a) * dist + gravity
            val tail = 0.22f
            val px = cx + cos(a) * dist * (1f - tail)
            val py = cy + sin(a) * dist * (1f - tail) + gravity * (1f - tail)
            val color = if (i % 3 == 0) DfColors.KiBlue else if (i % 3 == 1) DfColors.Gold else Color.White
            drawLine(
                color.copy(alpha = max(0f, 1f - p)),
                Offset(px, py),
                Offset(x, y),
                strokeWidth = 2f + (1f - p) * 3.5f,
                cap = StrokeCap.Round,
            )
        }
    }

    // Vinheta para o texto por cima ler bem.
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA05040F)), startY = h * 0.55f, endY = h),
    )
}

/**
 * Um raio de ki de [fromX] ate [toX]: brilho largo, corpo e nucleo branco; a grossura ondula
 * e cresce perto do choque.
 */
private fun DrawScope.drawBeam(
    fromX: Float,
    toX: Float,
    cy: Float,
    thick: Float,
    t: Float,
    colors: List<Color>,
    seed: Int,
) {
    val layers = listOf(2.4f to 0.22f, 1.45f to 0.75f, 0.62f to 1f)
    val dir = if (toX >= fromX) 1f else -1f
    val length = (toX - fromX) * dir
    if (length <= 1f) return
    val steps = max(8, (length / 14f).toInt())
    for ((li, layer) in layers.withIndex()) {
        val (scale, alpha) = layer
        val top = ArrayList<Offset>(steps + 1)
        val bottom = ArrayList<Offset>(steps + 1)
        for (s in 0..steps) {
            val f = s / steps.toFloat()
            val x = fromX + dir * length * f
            // Mais grosso perto do choque, com ondulacao que corre ao longo do raio.
            val wave = 1f + 0.09f * sin(t * 22f - f * 18f + seed * 2f) + 0.05f * sin(t * 41f + f * 7f)
            val half = thick * scale * 0.5f * (0.75f + 0.35f * f) * wave
            top += Offset(x, cy - half)
            bottom += Offset(x, cy + half)
        }
        val path = Path()
        path.moveTo(top[0].x, top[0].y)
        for (p in top) path.lineTo(p.x, p.y)
        for (i in bottom.indices.reversed()) path.lineTo(bottom[i].x, bottom[i].y)
        path.close()
        drawPath(
            path,
            colors[li].copy(alpha = alpha),
            blendMode = if (li == 0) BlendMode.Plus else BlendMode.SrcOver,
        )
    }
}
