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
import com.dfdx047.dragonfury.data.ButtonBadge
import com.dfdx047.dragonfury.data.ControlStyle
import com.dfdx047.dragonfury.data.Directional
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
    /** D-pad de 8 direcoes ou analogico (o outro fica escondido). */
    var directional: Directional = Directional.DPAD
        set(value) {
            field = value
            invalidate()
        }
    /** Indicacao do botao do comando (Xbox / PlayStation) no canto de cada botao. */
    var badge: ButtonBadge = ButtonBadge.XBOX
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

    // Cores Dragon Fury: icones dourados/laranja sobre vidro fosco.
    private val gold = Color.parseColor("#FFD27A")
    private val orange = Color.parseColor("#FF8A1F")
    private val yellow = Color.parseColor("#FFC94A")
    private val glassEdge = Color.parseColor("#FFF3DE")

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

    /** Visivel agora: escondido pelo usuario ou pelo tipo de direcional escolhido. */
    private fun isShown(e: ControlElement): Boolean = e.visible && when (e.kind) {
        ElementKind.STICK -> directional == Directional.STICK
        ElementKind.DPAD -> directional == Directional.DPAD
        ElementKind.BUTTON -> true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (e in layout.elements) {
            if (!isShown(e)) continue
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

    /**
     * Fundo "vidro fosco": branco muito translucido com um brilho em cima e uma
     * borda clara fina. Premido: o vidro acende em laranja por baixo do icone.
     */
    private fun drawGlass(c: Canvas, e: ControlElement, x: Float, y: Float, r: Float, down: Boolean) {
        fill.shader = RadialGradient(
            x, y - r * 0.45f, r * 1.4f,
            intArrayOf(Color.argb(95, 255, 255, 255), Color.argb(38, 255, 255, 255), Color.argb(26, 20, 16, 40)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        fill.alpha = alphaOf(e, 1f)
        c.drawCircle(x, y, r, fill)
        if (down) {
            fill.shader = RadialGradient(x, y, r, intArrayOf(Color.argb(170, 255, 201, 74), Color.argb(110, 255, 138, 31), Color.argb(0, 255, 138, 31)),
                floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            fill.alpha = alphaOf(e, 1f)
            c.drawCircle(x, y, r, fill)
        }
        fill.shader = null
        ring.strokeWidth = 1.5f * density
        ring.color = if (down) yellow else glassEdge
        ring.alpha = alphaOf(e, if (down) 0.95f else 0.55f)
        c.drawCircle(x, y, r - ring.strokeWidth / 2, ring)
    }

    private fun drawButton(c: Canvas, e: ControlElement) {
        val x = cx(e)
        val y = cy(e)
        val r = radius(e)
        val down = e.id in pressed
        // Nos menus os A/B/X/Y mostram sempre o simbolo do comando (no estilo Dragon
        // Fury, o da indicacao escolhida).
        val classic = (style != ControlStyle.DRAGON || layout.menu) && isFaceButton(e)
        val psGlyph = style == ControlStyle.PLAYSTATION || (style == ControlStyle.DRAGON && badge == ButtonBadge.PLAYSTATION)

        drawGlass(c, e, x, y, r, down)

        if (classic) {
            drawClassicGlyph(c, mainInput(e)!!, psGlyph, x, y, r * 0.95f, alphaOf(e, 1f))
        } else if (layout.menu && e.icon == ControlIcon.MACRO && mainInput(e) != null) {
            text.color = if (down) Color.WHITE else gold
            text.alpha = alphaOf(e, 1f)
            text.textSize = r * 0.5f
            c.drawText(GameActions.classicLabel(mainInput(e)!!, psGlyph), x, y + text.textSize / 3, text)
        } else if (e.icon == ControlIcon.MACRO && e.label.isNotBlank()) {
            text.color = if (down) Color.WHITE else gold
            text.alpha = alphaOf(e, 1f)
            text.textSize = r * 0.55f
            c.drawText(e.label.take(4), x, y + text.textSize / 3, text)
        } else {
            val d = icon(e.icon)
            val s = (r * 1.05f).toInt()
            d.setBounds((x - s / 2).toInt(), (y - s / 2).toInt(), (x + s / 2).toInt(), (y + s / 2).toInt())
            d.colorFilter = PorterDuffColorFilter(if (down) Color.WHITE else gold, PorterDuff.Mode.SRC_IN)
            d.alpha = alphaOf(e, 1f)
            d.draw(c)
        }
        // Indicacao do botao do comando no canto (nos classicos so nos que nao sao A/B/X/Y).
        val badgeStyle = when {
            style == ControlStyle.XBOX -> ButtonBadge.XBOX
            style == ControlStyle.PLAYSTATION -> ButtonBadge.PLAYSTATION
            else -> badge
        }
        if (badgeStyle != ButtonBadge.NONE && !classic && !layout.menu) drawBadge(c, e, badgeStyle == ButtonBadge.PLAYSTATION, x, y, r)
    }

    /** O que o botao faz no comando: um botao, uma combinacao ou "defesa + tras tras". */
    private fun badgeInputs(e: ControlElement): Pair<List<PadInput>, String> = when (val a = e.action) {
        is ControlAction.Press -> a.inputs.toList() to ""
        is ControlAction.Sequence -> {
            val firstButtons = a.steps.flatMap { it.inputs }.filter { it.ordinal <= PadInput.R3.ordinal }.distinct()
            val dirs = a.steps.count { PadInput.BACKWARD in it.inputs }
            firstButtons to (if (dirs > 0) "◀".repeat(dirs.coerceAtMost(2)) else "")
        }
    }

    private fun drawBadge(c: Canvas, e: ControlElement, ps: Boolean, x: Float, y: Float, r: Float) {
        val (inputs, suffix) = badgeInputs(e)
        if (inputs.isEmpty()) return
        // Selo pequeno a 45 graus, em cima a direita.
        val br = (r * 0.36f).coerceIn(9f * density, 15f * density)
        val bx = x + r * 0.72f
        val by = y - r * 0.72f
        val face = inputs.filter { it in setOf(PadInput.A, PadInput.B, PadInput.X, PadInput.Y) }
        val label = if (face.size == inputs.size && face.size == 1 && suffix.isEmpty()) null
        else inputs.joinToString("+") { GameActions.classicLabel(it, ps) } + suffix
        if (label != null) {
            // Texto (LB, R1, ✕◀◀, X+Y...): pilula escura translucida.
            text.textSize = br * 0.95f
            val w = text.measureText(label) + br * 0.9f
            fill.shader = null
            fill.color = Color.argb(150, 12, 10, 28)
            fill.alpha = alphaOf(e, 0.75f)
            c.drawRoundRect(bx - w / 2, by - br * 0.8f, bx + w / 2, by + br * 0.8f, br, br, fill)
            text.color = Color.WHITE
            text.alpha = alphaOf(e, 1f)
            c.drawText(label, bx, by + text.textSize / 3, text)
            return
        }
        fill.shader = null
        fill.color = Color.argb(160, 12, 10, 28)
        fill.alpha = alphaOf(e, 0.8f)
        c.drawCircle(bx, by, br, fill)
        drawClassicGlyph(c, face[0], ps, bx, by, br * 1.05f, alphaOf(e, 1f))
    }

    private fun classicColor(i: PadInput, ps: Boolean): Int = if (ps) {
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

    /** Letra do Xbox ou simbolo do PlayStation, centrado em (x, y) com raio r. */
    private fun drawClassicGlyph(c: Canvas, i: PadInput, ps: Boolean, x: Float, y: Float, r: Float, alpha: Int) {
        val color = classicColor(i, ps)
        if (!ps) {
            text.color = color
            text.alpha = alpha
            text.textSize = r * 0.9f
            c.drawText(GameActions.classicLabel(i, false), x, y + text.textSize / 3, text)
            return
        }
        ring.color = color
        ring.alpha = alpha
        ring.strokeWidth = (r * 0.12f).coerceAtLeast(1.5f * density)
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
        drawGlass(c, e, x, y, r, false)
        text.color = gold
        text.alpha = alphaOf(e, 0.7f)
        text.textSize = r * 0.16f
        c.drawText("▲", x, y - r * 0.72f, text)
        c.drawText("▼", x, y + r * 0.84f, text)
        c.drawText("◀", x - r * 0.78f, y + r * 0.06f, text)
        c.drawText("▶", x + r * 0.78f, y + r * 0.06f, text)
        // Pomo: esfera do dragao.
        val active = stickPointer >= 0 && !editMode
        val kx = x + (if (active) stickDx else 0f) * r * 0.55f
        val ky = y + (if (active) stickDy else 0f) * r * 0.55f
        val kr = r * 0.4f
        fill.shader = RadialGradient(kx - kr * 0.3f, ky - kr * 0.3f, kr * 1.3f,
            intArrayOf(if (style == ControlStyle.DRAGON) yellow else Color.LTGRAY, if (style == ControlStyle.DRAGON) orange else Color.GRAY),
            null, Shader.TileMode.CLAMP)
        fill.alpha = alphaOf(e, if (active) 0.9f else 0.65f)
        c.drawCircle(kx, ky, kr, fill)
        fill.shader = null
        if (style == ControlStyle.DRAGON) {
            text.color = Color.parseColor("#D7261E")
            text.alpha = alphaOf(e, 0.9f)
            text.textSize = kr * 0.55f
            c.drawText("★", kx, ky - kr * 0.12f, text)
            c.drawText("★★", kx, ky + kr * 0.42f, text)
        }
    }

    /**
     * D-pad de 8 direcoes: vidro redondo com as 4 setas e 4 marcas nas
     * diagonais; as direcoes premidas (incluindo diagonais) acendem.
     */
    private fun drawDpad(c: Canvas, e: ControlElement) {
        val x = cx(e)
        val y = cy(e)
        val r = radius(e)
        val state = dpadState[e.id].orEmpty()
        drawGlass(c, e, x, y, r, false)
        // Cruz
        val arm = r * 0.30f
        fill.shader = null
        fill.color = Color.argb(70, 255, 255, 255)
        fill.alpha = alphaOf(e, 0.6f)
        c.drawRoundRect(x - arm, y - r * 0.82f, x + arm, y + r * 0.82f, arm * 0.4f, arm * 0.4f, fill)
        c.drawRoundRect(x - r * 0.82f, y - arm, x + r * 0.82f, y + arm, arm * 0.4f, arm * 0.4f, fill)
        // Direcoes premidas acendem em laranja.
        fun lit(dir: PadInput, l: Float, t: Float, rr: Float, b: Float) {
            if (dir !in state) return
            fill.color = orange
            fill.alpha = alphaOf(e, 0.75f)
            c.drawRoundRect(l, t, rr, b, arm * 0.4f, arm * 0.4f, fill)
        }
        lit(PadInput.UP, x - arm, y - r * 0.82f, x + arm, y - arm)
        lit(PadInput.DOWN, x - arm, y + arm, x + arm, y + r * 0.82f)
        lit(PadInput.LEFT, x - r * 0.82f, y - arm, x - arm, y + arm)
        lit(PadInput.RIGHT, x + arm, y - arm, x + r * 0.82f, y + arm)
        // Setas
        text.color = gold
        text.alpha = alphaOf(e, 1f)
        text.textSize = r * 0.24f
        c.drawText("▲", x, y - r * 0.5f, text)
        c.drawText("▼", x, y + r * 0.62f, text)
        c.drawText("◀", x - r * 0.56f, y + r * 0.08f, text)
        c.drawText("▶", x + r * 0.56f, y + r * 0.08f, text)
        // Diagonais (marcas que acendem quando a diagonal esta premida)
        val d = r * 0.62f
        for ((dx, dy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
            val h = if (dx < 0) PadInput.LEFT else PadInput.RIGHT
            val v = if (dy < 0) PadInput.UP else PadInput.DOWN
            val on = h in state && v in state
            fill.color = if (on) yellow else glassEdge
            fill.alpha = alphaOf(e, if (on) 0.95f else 0.45f)
            c.drawCircle(x + dx * d * 0.72f, y + dy * d * 0.72f, r * (if (on) 0.07f else 0.045f), fill)
        }
        // Centro
        fill.color = Color.argb(60, 255, 255, 255)
        fill.alpha = alphaOf(e, 0.6f)
        c.drawCircle(x, y, arm * 0.7f, fill)
    }

    // ------------------------------------------------------------------ toque

    private fun hit(px: Float, py: Float): ControlElement? {
        var best: ControlElement? = null
        var bestD = Float.MAX_VALUE
        for (e in layout.elements) {
            if (!isShown(e)) continue
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
