package com.dfdx047.dragonfury.game

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.dfdx047.dragonfury.controls.ControlAction
import com.dfdx047.dragonfury.controls.MacroStep
import com.dfdx047.dragonfury.controls.PadInput
import org.libsdl.app.DragonFuryPad

/**
 * Junta tudo o que os controles por toque pedem (botoes premidos, analogico,
 * sequencias em curso) num estado de comando e envia so as diferencas para o
 * comando virtual do SDL. Corre a 60 Hz numa thread propria, como o jogo.
 *
 * Os botoes de ajuda que precisam de "para tras"/"para a frente" pedem o lado
 * do P1 ao NativeGameState a cada tick (o seguimento do lado precisa de
 * leituras frequentes para notar quando os lutadores trocam de lado).
 */
class HelperEngine {
    private val thread = HandlerThread("DragonFuryInput").apply { start() }
    private val handler = Handler(thread.looper)
    private val lock = Any()

    /** Entradas premidas diretamente, por elemento (id -> entradas). */
    private val held = HashMap<String, Set<PadInput>>()
    private class Running(val steps: List<MacroStep>, val holdLast: Boolean) {
        var index = 0
        var framesLeft = steps.firstOrNull()?.frames ?: 0
        var released = false
        val done get() = index >= steps.size
    }
    private val sequences = HashMap<String, Running>()
    private var stickX = 0f
    private var stickY = 0f

    private val sent = HashSet<PadInput>()
    private var sentLx = 0f
    private var sentLy = 0f
    private var running = false
    @Volatile private var side = NativeGameState.SIDE_UNKNOWN

    /** Menu dos controles aberto por cima do jogo: o jogo fica pausado. */
    @Volatile var menuOpen = false
        set(value) {
            field = value
            handler.post { step() }
        }

    /** Pausar tambem enquanto o menu rapido do port (Back+Start) estiver aberto. */
    @Volatile var pauseQuickMenu = true

    /** Se o jogo esta pausado agora (pelo menu dos controles ou pelo menu rapido). */
    @Volatile var paused = false
        private set

    private val tick = object : Runnable {
        override fun run() {
            val start = SystemClock.uptimeMillis()
            step()
            if (running) handler.postAtTime(this, start + 16)
        }
    }

    fun start() {
        synchronized(lock) {
            if (running) return
            running = true
        }
        handler.post(tick)
    }

    fun stop() {
        synchronized(lock) {
            running = false
            held.clear()
            sequences.clear()
            stickX = 0f
            stickY = 0f
        }
        handler.removeCallbacks(tick)
        handler.post { flush(emptySet(), 0f, 0f) }
    }

    fun release() {
        stop()
        thread.quitSafely()
    }

    fun press(id: String, action: ControlAction) {
        synchronized(lock) {
            when (action) {
                is ControlAction.Press -> held[id] = action.inputs
                is ControlAction.Sequence -> if (action.steps.isNotEmpty()) sequences[id] = Running(action.steps, action.holdLast)
            }
        }
        // Resposta imediata para botoes simples (nao espera pelo proximo tick).
        if (action is ControlAction.Press) handler.post { step() }
    }

    fun releaseElement(id: String) {
        synchronized(lock) {
            held.remove(id)
            sequences[id]?.let { seq ->
                seq.released = true
                // Sequencia com o ultimo passo "segurado": soltar o dedo acaba-a.
                if (seq.holdLast && seq.index >= seq.steps.size - 1) sequences.remove(id)
            }
        }
        handler.post { step() }
    }

    fun setStick(x: Float, y: Float) {
        synchronized(lock) {
            stickX = x.coerceIn(-1f, 1f)
            stickY = y.coerceIn(-1f, 1f)
        }
    }

    /** Uma "pressao" da direcao do D-pad do ecra (ou do analogico em modo digital). */
    fun setDpad(id: String, inputs: Set<PadInput>) {
        synchronized(lock) {
            if (inputs.isEmpty()) held.remove(id) else held[id] = inputs
        }
        handler.post { step() }
    }

    private fun step() {
        paused = NativeGameState.syncPause(menuOpen, pauseQuickMenu)
        side = NativeGameState.p1Side()
        val wanted = HashSet<PadInput>()
        val lx: Float
        val ly: Float
        synchronized(lock) {
            held.values.forEach { wanted.addAll(it) }
            val it = sequences.entries.iterator()
            while (it.hasNext()) {
                val (_, seq) = it.next()
                if (seq.done) {
                    it.remove()
                    continue
                }
                val stepNow = seq.steps[seq.index]
                wanted.addAll(stepNow.inputs)
                val last = seq.index == seq.steps.size - 1
                if (last && seq.holdLast && !seq.released) continue // segura ate soltar
                seq.framesLeft--
                if (seq.framesLeft <= 0) {
                    seq.index++
                    seq.framesLeft = seq.steps.getOrNull(seq.index)?.frames ?: 0
                }
            }
            lx = stickX
            ly = stickY
        }
        flush(resolveRelative(wanted), lx, ly)
    }

    /** FORWARD/BACKWARD -> RIGHT/LEFT conforme o lado do P1 (esquerda se desconhecido). */
    private fun resolveRelative(inputs: Set<PadInput>): Set<PadInput> {
        if (PadInput.FORWARD !in inputs && PadInput.BACKWARD !in inputs) return inputs
        val p1Right = side == NativeGameState.SIDE_RIGHT
        val out = inputs.toMutableSet()
        if (out.remove(PadInput.BACKWARD)) out += if (p1Right) PadInput.RIGHT else PadInput.LEFT
        if (out.remove(PadInput.FORWARD)) out += if (p1Right) PadInput.LEFT else PadInput.RIGHT
        return out
    }

    private fun flush(wanted: Set<PadInput>, lx: Float, ly: Float) {
        if (!DragonFuryPad.isAttached()) {
            sent.clear()
            return
        }
        for (i in PadInput.entries) {
            val on = i in wanted
            if (on == (i in sent)) continue
            when (i) {
                PadInput.LT -> DragonFuryPad.axis(4, if (on) 1f else -1f)
                PadInput.RT -> DragonFuryPad.axis(5, if (on) 1f else -1f)
                else -> keyFor(i)?.let { DragonFuryPad.button(it, on) }
            }
            if (on) sent += i else sent -= i
        }
        if (lx != sentLx) {
            DragonFuryPad.axis(0, lx)
            sentLx = lx
        }
        if (ly != sentLy) {
            DragonFuryPad.axis(1, ly)
            sentLy = ly
        }
    }

    private fun keyFor(i: PadInput): Int? = when (i) {
        PadInput.A -> DragonFuryPad.KEY_A
        PadInput.B -> DragonFuryPad.KEY_B
        PadInput.X -> DragonFuryPad.KEY_X
        PadInput.Y -> DragonFuryPad.KEY_Y
        PadInput.LB -> DragonFuryPad.KEY_LB
        PadInput.RB -> DragonFuryPad.KEY_RB
        PadInput.BACK -> DragonFuryPad.KEY_BACK
        PadInput.START -> DragonFuryPad.KEY_START
        PadInput.L3 -> DragonFuryPad.KEY_L3
        PadInput.R3 -> DragonFuryPad.KEY_R3
        PadInput.UP -> DragonFuryPad.KEY_UP
        PadInput.DOWN -> DragonFuryPad.KEY_DOWN
        PadInput.LEFT -> DragonFuryPad.KEY_LEFT
        PadInput.RIGHT -> DragonFuryPad.KEY_RIGHT
        else -> null
    }
}
