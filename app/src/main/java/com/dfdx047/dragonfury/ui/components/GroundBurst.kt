package com.dfdx047.dragonfury.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Animacao da tela Jogar: um terreno rochoso numa noite de tempestade, como antes de um
 * guerreiro liberar o poder. O chao racha a partir do centro (com um brilho quente por baixo),
 * pedras soltam-se e sobem devagar, ha poeira junto ao chao e, de tempos a tempos, cai um raio
 * que ilumina o ceu. Sem personagens nem cores fortes: tons de pedra, ceu azul-escuro e o
 * laranja so nas fendas. Desenhado num Canvas, com o relogio de frames (para quando o launcher
 * nao esta visivel).
 */
@Composable
fun GroundBurstAnimation(modifier: Modifier = Modifier) {
    val time by produceState(0f) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> value = (now - start) / 1_000_000_000f }
        }
    }
    Canvas(modifier) { drawGroundBurst(time) }
}

/** Pseudo-aleatorio estavel (0..1) a partir de dois inteiros. */
private fun rnd(i: Int, j: Int): Float {
    var x = i * 374761393 + j * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777216f
}

private fun smooth(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private const val CYCLE = 7f        // segundos de cada rachadura
private const val BOLT_EVERY = 2.6f // segundos entre raios possiveis

private val SkyTop = Color(0xFF05070F)
private val SkyMid = Color(0xFF111A2E)
private val SkyLow = Color(0xFF26304A)
private val Mountain = Color(0xFF0B0F1A)
private val GroundFar = Color(0xFF3A3530)
private val GroundNear = Color(0xFF1C1915)
private val RockLight = Color(0xFF8A8076)
private val RockDark = Color(0xFF4A433C)
private val Ember = Color(0xFFFF9A3C)
private val EmberCore = Color(0xFFFFE2A8)
private val BoltColor = Color(0xFFDDEBFF)
private val BoltGlow = Color(0xFF8FB4FF)

private fun DrawScope.drawGroundBurst(t: Float) {
    val w = size.width
    val h = size.height
    val unit = min(w, h)
    val groundY = h * 0.66f
    val center = Offset(w * 0.5f, groundY + (h - groundY) * 0.38f)

    // Raio atual (se houver): ilumina o ceu.
    val boltIndex = floor(t / BOLT_EVERY).toInt()
    val boltT = t - boltIndex * BOLT_EVERY
    val boltOn = rnd(boltIndex, 7) > 0.3f && boltT < 0.32f
    val flicker = if (boltT < 0.06f || (boltT in 0.12f..0.18f)) 1f else 0.45f
    val flash = if (boltOn) (1f - boltT / 0.32f) * flicker else 0f

    // ------------------------------------------------------------------ ceu
    drawRect(Brush.verticalGradient(listOf(SkyTop, SkyMid, SkyLow), endY = groundY))
    if (flash > 0f) drawRect(BoltGlow.copy(alpha = 0.22f * flash), size = androidx.compose.ui.geometry.Size(w, groundY))
    // Nuvens baixas (faixas escuras a passar devagar).
    for (k in 0 until 5) {
        val y = groundY * (0.08f + 0.13f * k)
        val drift = ((t * (6f + 3f * k) + rnd(k, 1) * w) % (w * 1.6f)) - w * 0.3f
        drawOval(
            Color(0xFF1A2236).copy(alpha = 0.55f + 0.3f * flash),
            topLeft = Offset(drift - w * 0.35f, y),
            size = androidx.compose.ui.geometry.Size(w * 0.7f, groundY * 0.12f),
        )
    }

    // ------------------------------------------------------------- montanhas
    val mountains = Path().apply {
        moveTo(0f, groundY)
        val n = 9
        for (i in 0..n) {
            val x = w * i / n
            val peak = groundY - h * (0.08f + 0.14f * rnd(i, 3))
            lineTo(x, peak)
        }
        lineTo(w, groundY)
        close()
    }
    drawPath(mountains, Mountain)

    // ------------------------------------------------------------------ chao
    drawRect(
        Brush.verticalGradient(listOf(GroundFar, GroundNear), startY = groundY, endY = h),
        topLeft = Offset(0f, groundY),
        size = androidx.compose.ui.geometry.Size(w, h - groundY),
    )
    // Estratos/irregularidades leves.
    for (k in 0 until 7) {
        val y = groundY + (h - groundY) * (0.1f + 0.13f * k)
        drawLine(Color.Black.copy(alpha = 0.12f), Offset(0f, y), Offset(w, y + unit * 0.01f * (rnd(k, 4) - 0.5f)), strokeWidth = 1.5f)
    }
    if (flash > 0f) {
        drawRect(
            BoltColor.copy(alpha = 0.10f * flash),
            topLeft = Offset(0f, groundY),
            size = androidx.compose.ui.geometry.Size(w, h - groundY),
        )
    }

    // -------------------------------------------------------------- rachaduras
    val cycle = floor(t / CYCLE).toInt()
    val p = (t - cycle * CYCLE) / CYCLE
    val grow = smooth(0.02f, 0.38f, p)
    val heat = smooth(0.05f, 0.3f, p) * (1f - smooth(0.7f, 1f, p))
    // Brilho quente no centro (o poder por baixo do chao).
    drawOval(
        Brush.radialGradient(
            listOf(Ember.copy(alpha = 0.35f * heat), Color.Transparent),
            center = center,
            radius = unit * 0.35f,
        ),
        topLeft = Offset(center.x - unit * 0.5f, center.y - unit * 0.14f),
        size = androidx.compose.ui.geometry.Size(unit, unit * 0.28f),
    )
    val cracks = 10
    for (i in 0 until cracks) {
        val baseAngle = (2f * PI.toFloat() * i / cracks) + (rnd(cycle, i) - 0.5f) * 0.5f
        val length = unit * (0.22f + 0.28f * rnd(cycle, i + 20)) * grow
        if (length < 1f) continue
        val path = Path().apply { moveTo(center.x, center.y) }
        var x = center.x
        var y = center.y
        var angle = baseAngle
        val segments = 7
        for (s in 1..segments) {
            angle += (rnd(cycle * 31 + i, s) - 0.5f) * 0.7f
            val step = length / segments
            x += cos(angle) * step
            // Perspetiva: o chao e um plano visto de lado, por isso as fendas achatam em y.
            y += sin(angle) * step * 0.32f
            path.lineTo(x, y)
        }
        drawPath(path, Color.Black.copy(alpha = 0.75f), style = Stroke(width = unit * 0.009f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (heat > 0f) {
            drawPath(path, Ember.copy(alpha = 0.55f * heat), style = Stroke(width = unit * 0.006f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, EmberCore.copy(alpha = 0.5f * heat), style = Stroke(width = unit * 0.002f, cap = StrokeCap.Round))
        }
    }

    // --------------------------------------------------------------- poeira
    for (d in 0 until 24) {
        val life = 4f
        val local = ((t + rnd(d, 9) * life) % life) / life
        val x = center.x + (rnd(d, 10) - 0.5f) * w * 0.9f + local * unit * 0.1f * (rnd(d, 11) - 0.5f)
        val y = groundY + (h - groundY) * (0.2f + 0.6f * rnd(d, 12)) - local * unit * 0.05f
        val a = sin(local * PI.toFloat()) * 0.18f
        drawCircle(Color(0xFF8C8173).copy(alpha = a), radius = unit * (0.02f + 0.03f * rnd(d, 13)), center = Offset(x, y))
    }

    // --------------------------------------------------------- pedras a subir
    val lift = 0.35f + 0.65f * heat
    for (j in 0 until 22) {
        val life = 3.2f + 2.4f * rnd(j, 1)
        val local = ((t + rnd(j, 2) * life) % life) / life
        val spread = (rnd(j, 3) - 0.5f) * w * 0.75f
        val startX = center.x + spread
        val startY = groundY + (h - groundY) * (0.15f + 0.6f * rnd(j, 4))
        val rise = (1f - (1f - local) * (1f - local)) * h * (0.25f + 0.35f * rnd(j, 5)) * lift
        val cx = startX + sin(t * 0.8f + j) * unit * 0.008f
        val cy = startY - rise
        val alpha = smooth(0f, 0.12f, local) * (1f - smooth(0.75f, 1f, local))
        if (alpha <= 0.01f) continue
        val r = unit * (0.008f + 0.022f * rnd(j, 6))
        val rot = t * (0.6f + rnd(j, 7)) * (if (rnd(j, 8) > 0.5f) 1f else -1f)
        val rock = Path()
        val corners = 5 + (rnd(j, 14) * 2f).toInt()
        for (c in 0 until corners) {
            val a = rot + 2f * PI.toFloat() * c / corners
            val rr = r * (0.7f + 0.5f * rnd(j * 13 + c, 15))
            val px = cx + cos(a) * rr
            val py = cy + sin(a) * rr * 0.85f
            if (c == 0) rock.moveTo(px, py) else rock.lineTo(px, py)
        }
        rock.close()
        drawPath(rock, Brush.verticalGradient(listOf(RockLight, RockDark), startY = cy - r, endY = cy + r), alpha = alpha)
        // Contra-luz quente vinda das fendas.
        drawPath(rock, Ember.copy(alpha = 0.25f * heat * alpha), style = Stroke(width = 1.2f))
    }

    // ------------------------------------------------------------------ raio
    if (boltOn) {
        val startX = w * (0.12f + 0.76f * rnd(boltIndex, 8))
        val endX = startX + (rnd(boltIndex, 9) - 0.5f) * w * 0.2f
        val endY = groundY + (h - groundY) * (0.1f + 0.3f * rnd(boltIndex, 10))
        val bolt = Path().apply { moveTo(startX, 0f) }
        val branch = Path()
        val steps = 12
        var bx = startX
        var by = 0f
        val branchAt = 4 + (rnd(boltIndex, 11) * 5).toInt()
        for (s in 1..steps) {
            val f = s / steps.toFloat()
            bx = startX + (endX - startX) * f + (rnd(boltIndex * 17 + s, 12) - 0.5f) * unit * 0.07f
            by = endY * f
            bolt.lineTo(bx, by)
            if (s == branchAt) {
                branch.moveTo(bx, by)
                var cx = bx
                var cy = by
                for (b in 1..4) {
                    cx += (rnd(boltIndex, 40 + b) - 0.3f) * unit * 0.06f
                    cy += unit * 0.05f
                    branch.lineTo(cx, cy)
                }
            }
        }
        val a = flicker
        drawPath(bolt, BoltGlow.copy(alpha = 0.35f * a), style = Stroke(width = unit * 0.02f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(bolt, BoltColor.copy(alpha = 0.95f * a), style = Stroke(width = unit * 0.005f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(branch, BoltColor.copy(alpha = 0.6f * a), style = Stroke(width = unit * 0.003f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Clarão onde o raio toca o chao.
        drawCircle(
            Brush.radialGradient(listOf(BoltColor.copy(alpha = 0.5f * a), Color.Transparent), center = Offset(bx, by), radius = unit * 0.08f),
            radius = unit * 0.08f,
            center = Offset(bx, by),
        )
    }

    // Vinheta para o texto por cima ficar legivel.
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), startY = h * 0.55f, endY = h))
    drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.25f))))
}
