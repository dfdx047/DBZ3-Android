package com.dfdx047.dragonfury.game

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Linhas do menu do jogo. A mesma lista serve o toque (cada linha desenha os seus controles) e o
 * comando fisico (MenuNav move a selecao e muda o valor com as setas).
 */
sealed class MItem {
    abstract val label: String
    open val enabled: Boolean get() = true
    open val selectable: Boolean get() = true

    class Toggle(override val label: String, val value: Boolean, override val enabled: Boolean = true, val set: (Boolean) -> Unit) : MItem()

    class Choice(
        override val label: String,
        val options: List<String>,
        val index: Int,
        override val enabled: Boolean = true,
        val set: (Int) -> Unit,
    ) : MItem()

    class Slider(
        override val label: String,
        val value: Float,
        val range: ClosedFloatingPointRange<Float>,
        val step: Float,
        val format: (Float) -> String,
        override val enabled: Boolean = true,
        val set: (Float) -> Unit,
    ) : MItem()

    class Action(
        override val label: String,
        override val enabled: Boolean = true,
        val accent: Boolean = false,
        val detail: String? = null,
        val run: () -> Unit,
    ) : MItem()

    class Note(override val label: String) : MItem() {
        override val selectable get() = false
    }
}

/**
 * Estado da navegacao do menu (aba e linha selecionadas) e o tratamento das teclas do comando.
 * As linhas da aba atual sao registadas pela UI a cada recomposicao ([items]).
 */
class MenuNav {
    val tab = mutableIntStateOf(0)
    val row = mutableIntStateOf(0)
    var tabCount = 1
    var items: List<MItem> = emptyList()
        set(v) {
            field = v
            // A lista mudou (ex.: o filtro escondeu a nitidez): a selecao continua valida.
            if (v.isNotEmpty()) {
                val r = row.intValue.coerceIn(0, v.size - 1)
                row.intValue = if (v[r].selectable) r else nextSelectable(v, r, 1) ?: nextSelectable(v, r, -1) ?: 0
            }
        }

    /** B / Voltar dentro do menu: a UI pode tratar (ex.: cancelar a confirmacao de saida). */
    var onBack: () -> Boolean = { false }
    var close: () -> Unit = {}

    fun reset() {
        tab.intValue = 0
        row.intValue = 0
    }

    private fun nextSelectable(list: List<MItem>, from: Int, dir: Int): Int? {
        var i = from + dir
        while (i in list.indices) {
            if (list[i].selectable) return i
            i += dir
        }
        return null
    }

    fun move(dir: Int) {
        val list = items
        if (list.isEmpty()) return
        val next = nextSelectable(list, row.intValue, dir)
            // Nas pontas da volta ao outro lado.
            ?: if (dir > 0) nextSelectable(list, -1, 1) else nextSelectable(list, list.size, -1)
        if (next != null) row.intValue = next
    }

    fun switchTab(dir: Int) {
        tab.intValue = (tab.intValue + dir + tabCount) % tabCount
        row.intValue = 0
        // A lista nova chega na proxima recomposicao; o setter de items acerta a linha.
    }

    fun selectTab(t: Int) {
        tab.intValue = t
        row.intValue = 0
    }

    private fun current(): MItem? = items.getOrNull(row.intValue)

    fun change(dir: Int) {
        val item = current() ?: return
        if (!item.enabled) return
        when (item) {
            is MItem.Toggle -> item.set(!item.value)
            is MItem.Choice -> item.set((item.index + dir + item.options.size) % item.options.size)
            is MItem.Slider -> {
                val steps = ((item.value - item.range.start) / item.step).roundToInt() + dir
                val v = (item.range.start + steps * item.step).coerceIn(item.range.start, item.range.endInclusive)
                if (v != item.value) item.set(v)
            }
            is MItem.Action, is MItem.Note -> Unit
        }
    }

    fun activate() {
        val item = current() ?: return
        if (!item.enabled) return
        when (item) {
            is MItem.Toggle -> item.set(!item.value)
            is MItem.Choice -> change(1)
            is MItem.Slider -> Unit
            is MItem.Action -> item.run()
            is MItem.Note -> Unit
        }
    }

    fun back() {
        if (!onBack()) close()
    }

    /**
     * Tecla de um comando com o menu aberto. Devolve true se a consumiu (nada chega ao jogo).
     */
    fun onKey(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> if (down) move(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (down) move(1)
            KeyEvent.KEYCODE_DPAD_LEFT -> if (down) change(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (down) change(1)
            KeyEvent.KEYCODE_BUTTON_L1 -> if (down && e.repeatCount == 0) switchTab(-1)
            KeyEvent.KEYCODE_BUTTON_R1 -> if (down && e.repeatCount == 0) switchTab(1)
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE -> if (down && e.repeatCount == 0) activate()
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE -> if (down && e.repeatCount == 0) back()
            KeyEvent.KEYCODE_BUTTON_START -> if (down && e.repeatCount == 0) close()
            else -> {
                // Outros botoes do comando (X, Y, sticks...) tambem nao vao para o jogo.
                return KeyEvent.isGamepadButton(e.keyCode)
            }
        }
        return true
    }

    private var hatX = 0
    private var hatY = 0
    private var stickX = 0
    private var stickY = 0

    /** Cruz (eixos HAT) e analogico esquerdo, so na transicao. Os gatilhos ficam parados (alguns comandos mandam tecla e eixo ao mesmo tempo); as abas vao com LB/RB. */
    fun onMotion(e: MotionEvent): Boolean {
        val joystick = e.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            e.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        if (!joystick || e.actionMasked != MotionEvent.ACTION_MOVE) return false
        fun dir(v: Float, threshold: Float) = when {
            v > threshold -> 1
            v < -threshold -> -1
            else -> 0
        }
        val hx = dir(e.getAxisValue(MotionEvent.AXIS_HAT_X), 0.5f)
        val hy = dir(e.getAxisValue(MotionEvent.AXIS_HAT_Y), 0.5f)
        if (hx != hatX) {
            hatX = hx
            if (hx != 0) change(hx)
        }
        if (hy != hatY) {
            hatY = hy
            if (hy != 0) move(hy)
        }
        val sx = e.getAxisValue(MotionEvent.AXIS_X)
        val sy = e.getAxisValue(MotionEvent.AXIS_Y)
        // Histerese: entra com 0.6, sai abaixo de 0.3 (sticks com drift nao repetem).
        val nx = if (stickX != 0 && abs(sx) > 0.3f) stickX else dir(sx, 0.6f)
        val ny = if (stickY != 0 && abs(sy) > 0.3f) stickY else dir(sy, 0.6f)
        if (nx != stickX) {
            stickX = nx
            if (nx != 0) change(nx)
        }
        if (ny != stickY) {
            stickY = ny
            if (ny != 0) move(ny)
        }
        return true
    }
}
