package com.dfdx047.dragonfury.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.dfdx047.dragonfury.controls.ControlAction
import com.dfdx047.dragonfury.controls.ControlElement
import com.dfdx047.dragonfury.controls.ControlIcon
import com.dfdx047.dragonfury.controls.ControlLayout
import com.dfdx047.dragonfury.controls.ElementKind
import com.dfdx047.dragonfury.controls.GameActions
import com.dfdx047.dragonfury.controls.PadInput
import com.dfdx047.dragonfury.data.ControlStyle
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Controles por toque desenhados por cima do jogo (e no editor de layout).
 *
 * Estilos: DRAGON (circulos translucidos com icones de soco, chute, ki,
 * defesa..., nas cores do tema) e classico XBOX / PLAYSTATION (letras ou
 * simbolos). Os botoes de ajuda (ki, agarrao, transformacao...) usam sempre
 * o icone.
 */
@SuppressLint("ViewConstructor")
class TouchControlsView(context: Context) : View(context) {

    interface Listener {
        fun onPress(element: ControlElement)
        fun onRelease(element: ControlElement)
        fun onStick(x: Float, y: Float)
        fun onDpad(element: ControlElement, inputs: Set<PadInput>)
    }

    interface EditListener {
        fun onSelected(id: String?)
        fun onMoved(layout: ControlLayout)
    }

    var layout: ControlLayout = GameActions.defaultLayout()
        set(value) {
            field = value
            invalidate()
        }
    var style: ControlStyle = ControlStyle.DRAGON
        set(value) {
            field = value
            invalidate()
        }
    var globalOpacity: Float = 0.75f
        set(value) {
            field = value.coerceIn(0.1f, 1f)
            invalidate()
        }
    var haptics = true
    var editMode = false
    var selectedId: String? = null
        set(value) {
            field = value
            invalidate()
        }
    var listener: Listener? = null
    var editListener: EditListener? = null

    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val icons = HashMap<ControlIcon, Drawable>()

    private val pressed = HashSet<String>()
    /** pointerId -> id do elemento */
    private val pointers = HashMap<Int, String>()
    private var stickPointer = -1
    private var stickDx = 0f
    private var stickDy = 0f
    private val dpadState = HashMap<String, Set<PadInput>>()

    // Edicao
    private var dragId: String? = null
    private var dragOffX = 0f
    private var dragOffY = 0f

    // Cores do tema Dragon Fury
    private val cream = Color.parseColor("#FFE7B0")
    private val orange = Color.parseColor("#FF8A1F")
    private val yellow = Color.parseColor("#FFC94A")
    private val blue = Color.parseColor("#1E5BFF")

    init {
        isHapticFeedbackEnabled = true
    }

    private fun icon(i: ControlIcon): Drawable =
        icons.getOrPut(i) { ContextCompat.getDrawable(context, i.drawable)!!.mutate() }

    private fun cx(e: ControlElement) = e.x * width
    private fun cy(e: ControlElement) = e.y * height
    private fun radius(e: ControlElement) = e.sizeDp * density / 2f

    private fun alphaOf(e: ControlElement, base: Float): Int =
        (255 * base * e.opacity * (if (editMode) 1f else globalOpacity)).toInt().coerceIn(0, 255)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (e in layout.elements) {
            if (!e.visible) continue
            when (e.kind) {
                ElementKind.BUTTON -> drawButton(canvas, e)
                ElementKind.STICK -> drawStick(canvas, e)
                ElementKind.DPAD -> drawDpad(canvas, e)
            }
            if (editMode && e.id == selectedId) {
                ring.color = yellow
                ring.alpha = 255
                ring.strokeWidth = 2.5f * density
                ring.pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f * density, 6f * density), 0f)
                canvas.drawCircle(cx(e), cy(e), radius(e) + 6 * density, ring)
                ring.pathEffect = null
            }
        }
    }

    /** Entrada principal do botao (para o rotulo classico). */
    private fun mainInput(e: ControlElement): PadInput? =
        (e.action as? ControlAction.Press)?.inputs?.singleOrNull()

    private fun isFaceButton(e: ControlElement): Boolean =
        mainInput(e) in setOf(PadInput.A, PadInput.B, PadInput.X, PadInput.Y)

    private fun drawButton(c: Canvas, e: ControlElement) {
        val x = cx(e)
        val y = cy(e)
        val r = radius(e)
        val down = e.id in pressed
        val classic = style != ControlStyle.DRAGON && isFaceButton(e)

        // Fundo translucido; premido acende em laranja.
        if (down) {
            fill.shader = RadialGradient(x, y, r, intArrayOf(yellow, orange, Color.argb(0, 255, 138, 31)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            fill.alpha = alphaOf(e, 0.85f)
        } else {
            fill.shader = null
            fill.color = Color.BLACK
            fill.alpha = alphaOf(e, 0.35f)
        }
        c.drawCircle(x, y, r, fill)
        fill.shader = null

        ring.strokeWidth = 2f * density
        ring.color = if (classic) classicColor(mainInput(e)!!) else if (down) yellow else cream
        ring.alpha = alphaOf(e, 0.9f)
        c.drawCircle(x, y, r - ring.strokeWidth / 2, ring)
        if (style == ControlStyle.DRAGON && !down) {
            // Brilho fino laranja por dentro (aura).
            ring.strokeWidth = 1f * density
            ring.color = orange
            ring.alpha = alphaOf(e, 0.6f)
            c.drawCircle(x, y, r - 4 * density, ring)
        }

        if (classic) {
            drawClassicGlyph(c, mainInput(e)!!, x, y, r, alphaOf(e, 1f))
        } else if (e.icon == ControlIcon.MACRO && e.label.isNotBlank()) {
            text.color = if (down) Color.WHITE else cream
            text.alpha = alphaOf(e, 1f)
            text.textSize = r * 0.55f
            c.drawText(e.label.take(4), x, y + text.textSize / 3, text)
        } else {
            val d = icon(e.icon)
            val s = (r * 1.1f).toInt()
            d.setBounds((x - s / 2).toInt(), (y - s / 2).toInt(), (x + s / 2).toInt(), (y + s / 2).toInt())
            d.colorFilter = PorterDuffColorFilter(if (down) Color.WHITE else cream, PorterDuff.Mode.SRC_IN)
            d.alpha = alphaOf(e, 1f)
            d.draw(c)
            // Rotulo pequeno (LB, RT...) nos estilos classicos.
            if (style != ControlStyle.DRAGON) {
                val input = mainInput(e)
                if (input != null) {
                    text.textSize = r * 0.32f
                    text.color = yellow
                    text.alpha = alphaOf(e, 1f)
                    c.drawText(GameActions.classicLabel(input, style == ControlStyle.PLAYSTATION), x, y + r * 0.92f, text)
                }
            }
        }
    }

    private fun classicColor(i: PadInput): Int = if (style == ControlStyle.PLAYSTATION) {
        when (i) {
            PadInput.A -> Color.parseColor("#7CB2E8")
            PadInput.B -> Color.parseColor("#FF6666")
            PadInput.X -> Color.parseColor("#E899C9")
            else -> Color.parseColor("#40E2A0")
        }
    } else {
        when (i) {
            PadInput.A -> Color.parseColor("#5DC21E")
            PadInput.B -> Color.parseColor("#E5312C")
            PadInput.X -> Color.parseColor("#2C8BE5")
            else -> Color.parseColor("#F6C41B")
        }
    }

    private fun drawClassicGlyph(c: Canvas, i: PadInput, x: Float, y: Float, r: Float, alpha: Int) {
        val color = classicColor(i)
        if (style == ControlStyle.XBOX) {
            text.color = color
            text.alpha = alpha
            text.textSize = r * 0.9f
            c.drawText(GameActions.classicLabel(i, false), x, y + text.textSize / 3, text)
            return
        }
        ring.color = color
        ring.alpha = alpha
        ring.strokeWidth = r * 0.12f
        val s = r * 0.42f
        when (i) {
            PadInput.A -> { // cruz
                c.drawLine(x - s, y - s, x + s, y + s, ring)
                c.drawLine(x - s, y + s, x + s, y - s, ring)
            }
            PadInput.B -> c.drawCircle(x, y, s, ring)
            PadInput.X -> c.drawRect(x - s * 0.9f, y - s * 0.9f, x + s * 0.9f, y + s * 0.9f, ring)
            else -> {
                val p = Path()
                p.moveTo(x, y - s)
                p.lineTo(x + s, y + s * 0.75f)
                p.lineTo(x - s, y + s * 0.75f)
                p.close()
                c.drawPath(p, ring)
            }
        }
    }

    private fun drawStick(c: Canvas, e: ControlElement) {
        val x = cx(e)
        val y = cy(e)
        val r = radius(e)
        fill.shader = null
        fill.color = Color.BLACK
        fill.alpha = alphaOf(e, 0.28f)
        c.drawCircle(x, y, r, fill)
        ring.strokeWidth = 2f * density
        ring.color = cream
        ring.alpha = alphaOf(e, 0.7f)
        c.drawCircle(x, y, r - density, ring)
        // Marcas das 4 direcoes
        text.color = cream
        text.alpha = alphaOf(e, 0.6f)
        text.textSize = r * 0.18f
        c.drawText("▲", x, y - r * 0.72f, text)
        c.drawText("▼", x, y + r * 0.84f, text)
        c.drawText("◀", x - r * 0.78f, y + r * 0.06f, text)
        c.drawText("▶", x + r * 0.78f, y + r * 0.06f, text)
        // Pomo
        val active = stickPointer >= 0 && !editMode
        val kx = x + (if (active) stickDx else 0f) * r * 0.55f
        val ky = y + (if (active) stickDy else 0f) * r * 0.55f
        val kr = r * 0.42f
        fill.shader = RadialGradient(kx - kr * 0.3f, ky - kr * 0.3f, kr * 1.3f,
            intArrayOf(if (style == ControlStyle.DRAGON) yellow else Color.LTGRAY, if (style == ControlStyle.DRAGON) orange else Color.GRAY),
            null, Shader.TileMode.CLAMP)
        fill.alpha = alphaOf(e, if (active) 0.95f else 0.7f)
        c.drawCircle(kx, ky, kr, fill)
        fill.shader = null
        if (style == ControlStyle.DRAGON) {
            // Estrelas da esfera no pomo.
            text.color = Color.parseColor("#D7261E")
            text.alpha = alphaOf(e, 0.9f)
            text.textSize = kr * 0.55f
            c.drawText("★", kx, ky - kr * 0.12f, text)
            c.drawText("★★", kx, ky + kr * 0.42f, text)
        }
    }

    private fun drawDpad(c: Canvas, e: ControlElement) {
        val x = cx(e)
        val y = cy(e)
        val r = radius(e)
        val arm = r * 0.36f
        val state = dpadState[e.id].orEmpty()
        fun drawArm(dir: PadInput, l: Float, t: Float, rr: Float, b: Float) {
            fill.shader = null
            fill.color = if (dir in state) orange else Color.BLACK
            fill.alpha = alphaOf(e, if (dir in state) 0.85f else 0.35f)
            c.drawRoundRect(l, t, rr, b, arm * 0.3f, arm * 0.3f, fill)
            ring.strokeWidth = 1.5f * density
            ring.color = cream
            ring.alpha = alphaOf(e, 0.8f)
            c.drawRoundRect(l, t, rr, b, arm * 0.3f, arm * 0.3f, ring)
        }
        drawArm(PadInput.UP, x - arm, y - r, x + arm, y - arm)
        drawArm(PadInput.DOWN, x - arm, y + arm, x + arm, y + r)
        drawArm(PadInput.LEFT, x - r, y - arm, x - arm, y + arm)
        drawArm(PadInput.RIGHT, x + arm, y - arm, x + r, y + arm)
    }

    // ------------------------------------------------------------------ toque

    private fun hit(px: Float, py: Float): ControlElement? {
        var best: ControlElement? = null
        var bestD = Float.MAX_VALUE
        for (e in layout.elements) {
            if (!e.visible) continue
            val d = hypot(px - cx(e), py - cy(e))
            // Um pouco de margem fora do circulo ajuda os dedos.
            val reach = radius(e) * (if (e.kind == ElementKind.BUTTON) 1.15f else 1.25f)
            if (d <= reach && d / reach < bestD) {
                bestD = d / reach
                best = e
            }
        }
        return best
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (editMode) return onEditTouch(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex
                val e = hit(ev.getX(i), ev.getY(i)) ?: return true
                val pid = ev.getPointerId(i)
                when (e.kind) {
                    ElementKind.STICK -> {
                        if (stickPointer < 0) {
                            stickPointer = pid
                            pointers[pid] = e.id
                            updateStick(e, ev.getX(i), ev.getY(i))
                        }
                    }
                    ElementKind.DPAD -> {
                        pointers[pid] = e.id
                        updateDpad(e, ev.getX(i), ev.getY(i))
                    }
                    ElementKind.BUTTON -> {
                        pointers[pid] = e.id
                        pressButton(e)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    val pid = ev.getPointerId(i)
                    val id = pointers[pid] ?: continue
                    val e = layout.elements.firstOrNull { it.id == id } ?: continue
                    when (e.kind) {
                        ElementKind.STICK -> updateStick(e, ev.getX(i), ev.getY(i))
                        ElementKind.DPAD -> updateDpad(e, ev.getX(i), ev.getY(i))
                        ElementKind.BUTTON -> {
                            // Deslizar o dedo para outro botao troca de botao.
                            val other = hit(ev.getX(i), ev.getY(i))
                            if (other != null && other.id != id && other.kind == ElementKind.BUTTON) {
                                releaseButton(e)
                                pointers[pid] = other.id
                                pressButton(other)
                            }
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pid = ev.getPointerId(ev.actionIndex)
                endPointer(pid)
            }
            MotionEvent.ACTION_CANCEL -> {
                pointers.keys.toList().forEach(::endPointer)
            }
        }
        return true
    }

    private fun endPointer(pid: Int) {
        val id = pointers.remove(pid) ?: return
        val e = layout.elements.firstOrNull { it.id == id } ?: return
        when (e.kind) {
            ElementKind.STICK -> {
                stickPointer = -1
                stickDx = 0f
                stickDy = 0f
                listener?.onStick(0f, 0f)
                invalidate()
            }
            ElementKind.DPAD -> {
                dpadState.remove(e.id)
                listener?.onDpad(e, emptySet())
                invalidate()
            }
            ElementKind.BUTTON -> releaseButton(e)
        }
    }

    private fun pressButton(e: ControlElement) {
        if (!pressed.add(e.id)) return
        if (haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        listener?.onPress(e)
        invalidate()
    }

    private fun releaseButton(e: ControlElement) {
        if (!pressed.remove(e.id)) return
        listener?.onRelease(e)
        invalidate()
    }

    private fun updateStick(e: ControlElement, px: Float, py: Float) {
        val r = radius(e) * 0.55f
        var dx = (px - cx(e)) / r
        var dy = (py - cy(e)) / r
        val len = hypot(dx, dy)
        if (len > 1f) {
            dx /= len
            dy /= len
        }
        stickDx = dx
        stickDy = dy
        listener?.onStick(dx, dy)
        invalidate()
    }

    private fun updateDpad(e: ControlElement, px: Float, py: Float) {
        val dx = px - cx(e)
        val dy = py - cy(e)
        val dead = radius(e) * 0.2f
        val set = HashSet<PadInput>()
        if (hypot(dx, dy) > dead) {
            // 8 direcoes: cada setor de 45 graus.
            val a = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
            if (a > -67.5 && a < 67.5) set += PadInput.RIGHT
            if (a > 112.5 || a < -112.5) set += PadInput.LEFT
            if (a > 22.5 && a < 157.5) set += PadInput.DOWN
            if (a < -22.5 && a > -157.5) set += PadInput.UP
        }
        if (dpadState[e.id] != set) {
            if (set.isNotEmpty() && haptics && dpadState[e.id].isNullOrEmpty()) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            dpadState[e.id] = set
            listener?.onDpad(e, set)
            invalidate()
        }
    }

    /** Solta tudo (ex.: o jogo foi para segundo plano). */
    fun releaseAll() {
        pointers.keys.toList().forEach(::endPointer)
        pressed.clear()
        invalidate()
    }

    // ----------------------------------------------------------------- edicao

    private fun onEditTouch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val e = hit(ev.x, ev.y)
                selectedId = e?.id
                editListener?.onSelected(e?.id)
                if (e != null) {
                    dragId = e.id
                    dragOffX = ev.x - cx(e)
                    dragOffY = ev.y - cy(e)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val id = dragId ?: return true
                if (width == 0 || height == 0) return true
                val nx = ((ev.x - dragOffX) / width).coerceIn(0f, 1f)
                val ny = ((ev.y - dragOffY) / height).coerceIn(0f, 1f)
                layout = layout.copy(elements = layout.elements.map { if (it.id == id) it.copy(x = nx, y = ny) else it })
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragId != null) editListener?.onMoved(layout)
                dragId = null
            }
        }
        return true
    }
}
