package com.dfdx047.dragonfury.controls

import com.dfdx047.dragonfury.R
import com.dfdx047.dragonfury.data.AppPaths
import org.json.JSONArray
import org.json.JSONObject

/**
 * Entradas do comando virtual. FORWARD/BACKWARD sao relativas ao lutador: o
 * HelperEngine traduz para esquerda/direita conforme o lado em que o P1 esta.
 */
enum class PadInput {
    A, B, X, Y, LB, RB, LT, RT, BACK, START, L3, R3,
    UP, DOWN, LEFT, RIGHT, FORWARD, BACKWARD,
}

/** Um passo de uma sequencia: estas entradas durante [frames] ticks de 60 Hz. */
data class MacroStep(val inputs: Set<PadInput>, val frames: Int)

/** O que um botao no ecra faz. */
sealed class ControlAction {
    /** Carrega os botoes do comando enquanto o dedo esta em cima (1 ou varios = combinacao). */
    data class Press(val inputs: Set<PadInput>) : ControlAction()

    /**
     * Toca a sequencia ao carregar. Com [holdLast], o ultimo passo fica
     * premido enquanto o dedo estiver em cima (ex.: carregar ki).
     */
    data class Sequence(val steps: List<MacroStep>, val holdLast: Boolean) : ControlAction()
}

enum class ElementKind { BUTTON, STICK, DPAD }

/** Icone desenhado no botao (estilo Dragon Fury). */
enum class ControlIcon(val drawable: Int) {
    PUNCH(R.drawable.ic_btn_punch),
    KICK(R.drawable.ic_btn_kick),
    KI(R.drawable.ic_btn_ki),
    GUARD(R.drawable.ic_btn_guard),
    CHARGE(R.drawable.ic_btn_charge),
    GRAB(R.drawable.ic_btn_grab),
    TRANSFORM(R.drawable.ic_btn_transform),
    HYPER(R.drawable.ic_btn_hyper),
    COUNTER(R.drawable.ic_btn_counter),
    SWAP(R.drawable.ic_btn_swap),
    START(R.drawable.ic_btn_start),
    BACK(R.drawable.ic_btn_back),
    MACRO(R.drawable.ic_btn_macro),
}

/**
 * Um elemento do layout. Posicao = centro, em fracao da largura/altura do
 * ecra (em paisagem), tamanho = diametro em dp.
 */
data class ControlElement(
    val id: String,
    val kind: ElementKind,
    val action: ControlAction,
    val icon: ControlIcon,
    val label: String,
    val x: Float,
    val y: Float,
    val sizeDp: Float,
    val opacity: Float = 1f,
    val visible: Boolean = true,
)

/**
 * [menu] = layout fora da luta (A/B/X/Y com o simbolo do comando); [map] = o do voo livre
 * pelo mapa do Dragon Universe (comando completo: analogico, A/B/X/Y, LB/RB/LT/RT, Start e
 * Back; tambem tem [menu] = true). O outro e o da luta. O GameOverlay troca sozinho.
 */
data class ControlLayout(
    val elements: List<ControlElement>,
    val version: Int = 1,
    val menu: Boolean = false,
    val map: Boolean = false,
) {
    val kind: LayoutKind get() = when {
        map -> LayoutKind.MAP
        menu -> LayoutKind.MENU
        else -> LayoutKind.GAMEPLAY
    }
}

enum class LayoutKind { GAMEPLAY, MENU, MAP }

/**
 * Acoes do Budokai 3 (Xbox 360): X soco, Y chute, A defesa, B ki. Os gatilhos
 * ja sao atalhos do proprio jogo (manual/PS2: L1 agarrao = soco+defesa,
 * R1 transformacao = soco+chute+defesa, L2 Modo Hyper = os quatro, R2
 * contra-ataque = soco+chute). Carregar ki = segurar defesa e tocar duas vezes
 * para tras, segurando na segunda.
 */
object GameActions {
    val PUNCH = setOf(PadInput.X)
    val KICK = setOf(PadInput.Y)
    val KI = setOf(PadInput.B)
    val GUARD = setOf(PadInput.A)

    val CHARGE_KI = ControlAction.Sequence(
        listOf(
            MacroStep(setOf(PadInput.A), 2),
            MacroStep(setOf(PadInput.A, PadInput.BACKWARD), 4),
            MacroStep(setOf(PadInput.A), 4),
            MacroStep(setOf(PadInput.A, PadInput.BACKWARD), 1),
        ),
        holdLast = true,
    )

    /** Ids dos botoes de ajuda, para o menu "Adicionar" do editor. */
    val HELPERS: List<Triple<String, ControlAction, ControlIcon>> = listOf(
        Triple("charge", CHARGE_KI, ControlIcon.CHARGE),
        Triple("grab", ControlAction.Press(setOf(PadInput.LB)), ControlIcon.GRAB),
        Triple("transform", ControlAction.Press(setOf(PadInput.RB)), ControlIcon.TRANSFORM),
        Triple("hyper", ControlAction.Press(setOf(PadInput.LT)), ControlIcon.HYPER),
        Triple("counter", ControlAction.Press(setOf(PadInput.RT)), ControlIcon.COUNTER),
        Triple("swap", ControlAction.Press(setOf(PadInput.BACK)), ControlIcon.SWAP),
    )

    fun defaultLayout(): ControlLayout {
        val cx = 0.875f
        val cy = 0.68f
        return ControlLayout(
            listOf(
                ControlElement("stick", ElementKind.STICK, ControlAction.Press(emptySet()), ControlIcon.MACRO, "", 0.15f, 0.66f, 150f),
                ControlElement("dpad", ElementKind.DPAD, ControlAction.Press(emptySet()), ControlIcon.MACRO, "", 0.15f, 0.66f, 150f),
                ControlElement("punch", ElementKind.BUTTON, ControlAction.Press(PUNCH), ControlIcon.PUNCH, "X", cx - 0.075f, cy, 62f),
                ControlElement("kick", ElementKind.BUTTON, ControlAction.Press(KICK), ControlIcon.KICK, "Y", cx, cy - 0.18f, 62f),
                ControlElement("ki", ElementKind.BUTTON, ControlAction.Press(KI), ControlIcon.KI, "B", cx + 0.075f, cy, 62f),
                ControlElement("guard", ElementKind.BUTTON, ControlAction.Press(GUARD), ControlIcon.GUARD, "A", cx, cy + 0.18f, 62f),
                ControlElement("charge", ElementKind.BUTTON, CHARGE_KI, ControlIcon.CHARGE, "KI", 0.70f, 0.88f, 50f),
                ControlElement("grab", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.LB)), ControlIcon.GRAB, "LB", 0.70f, 0.64f, 50f),
                ControlElement("transform", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.RB)), ControlIcon.TRANSFORM, "RB", 0.74f, 0.42f, 50f),
                ControlElement("hyper", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.LT)), ControlIcon.HYPER, "LT", 0.82f, 0.30f, 50f),
                ControlElement("counter", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.RT)), ControlIcon.COUNTER, "RT", 0.95f, 0.40f, 50f),
                ControlElement("swap", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.BACK)), ControlIcon.SWAP, "BACK", 0.33f, 0.09f, 42f),
                ControlElement("start", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.START)), ControlIcon.START, "START", 0.67f, 0.09f, 42f),
            ),
        )
    }

    /**
     * Menus do jogo (versao 2, 0.6.18). O Budokai 3 HD usa nos menus o comando quase todo:
     * A confirma, Y volta (os ✕/△ do PS2), X abre o TAG TEAM no menu principal e serve noutros
     * ecras (como B), LT/RT trocam a forma na escolha de personagem ("LT Previous / RT Next"),
     * LB/RB mudam de pagina nas listas (capsulas, edicao de habilidades), Back/Select mostra a
     * ajuda e Start comeca/pausa. A v1 so tinha A, Y e Select. Mesma disposicao do mapa e da
     * luta: losango A/B/X/Y a direita, gatilhos num arco por cima, Back e Start em cima.
     */
    const val MENU_LAYOUT_VERSION = 2

    fun defaultMenuLayout(): ControlLayout {
        fun pad(id: String, input: PadInput, x: Float, y: Float, size: Float) =
            ControlElement(id, ElementKind.BUTTON, ControlAction.Press(setOf(input)), ControlIcon.MACRO,
                classicLabel(input, false), x, y, size)
        val cx = 0.875f
        val cy = 0.68f
        return ControlLayout(
            listOf(
                ControlElement("stick", ElementKind.STICK, ControlAction.Press(emptySet()), ControlIcon.MACRO, "", 0.15f, 0.66f, 150f),
                ControlElement("dpad", ElementKind.DPAD, ControlAction.Press(emptySet()), ControlIcon.MACRO, "", 0.15f, 0.66f, 150f),
                pad("m_x", PadInput.X, cx - 0.075f, cy, 60f),
                pad("m_y", PadInput.Y, cx, cy - 0.18f, 60f),
                pad("m_b", PadInput.B, cx + 0.075f, cy, 60f),
                pad("m_a", PadInput.A, cx, cy + 0.18f, 64f),
                pad("m_lb", PadInput.LB, 0.70f, 0.46f, 48f),
                pad("m_lt", PadInput.LT, 0.72f, 0.26f, 48f),
                pad("m_rt", PadInput.RT, 0.85f, 0.26f, 48f),
                pad("m_rb", PadInput.RB, 0.95f, 0.40f, 48f),
                ControlElement("m_back", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.BACK)), ControlIcon.BACK, "BACK", 0.33f, 0.09f, 42f),
                ControlElement("m_start", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.START)), ControlIcon.START, "START", 0.67f, 0.09f, 42f),
            ),
            version = MENU_LAYOUT_VERSION,
            menu = true,
        )
    }

    /**
     * Voo livre pelo mapa do Dragon Universe: o jogo pede o analogico (movimento com
     * velocidade) e os botoes do comando sem traducao de luta, por isso e um comando
     * completo com os rotulos classicos. O D-pad vem escondido (o analogico aparece
     * sempre neste layout, seja qual for o direcional escolhido).
     */
    fun defaultMapLayout(): ControlLayout {
        fun pad(id: String, input: PadInput, x: Float, y: Float, size: Float) =
            ControlElement(id, ElementKind.BUTTON, ControlAction.Press(setOf(input)), ControlIcon.MACRO,
                classicLabel(input, false), x, y, size)
        val cx = 0.875f
        val cy = 0.68f
        return ControlLayout(
            listOf(
                ControlElement("stick", ElementKind.STICK, ControlAction.Press(emptySet()), ControlIcon.MACRO, "", 0.15f, 0.66f, 150f),
                ControlElement("dpad", ElementKind.DPAD, ControlAction.Press(emptySet()), ControlIcon.MACRO, "D-pad", 0.15f, 0.66f, 150f,
                    visible = false),
                pad("u_x", PadInput.X, cx - 0.075f, cy, 60f),
                pad("u_y", PadInput.Y, cx, cy - 0.18f, 60f),
                pad("u_b", PadInput.B, cx + 0.075f, cy, 60f),
                pad("u_a", PadInput.A, cx, cy + 0.18f, 60f),
                pad("u_lb", PadInput.LB, 0.70f, 0.46f, 50f),
                pad("u_lt", PadInput.LT, 0.72f, 0.26f, 50f),
                pad("u_rb", PadInput.RB, 0.95f, 0.40f, 50f),
                pad("u_rt", PadInput.RT, 0.85f, 0.26f, 50f),
                ControlElement("u_back", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.BACK)), ControlIcon.BACK, "BACK", 0.33f, 0.09f, 42f),
                ControlElement("u_start", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.START)), ControlIcon.START, "START", 0.67f, 0.09f, 42f),
            ),
            menu = true,
            map = true,
        )
    }

    fun defaultLayout(kind: LayoutKind): ControlLayout = when (kind) {
        LayoutKind.MENU -> defaultMenuLayout()
        LayoutKind.MAP -> defaultMapLayout()
        LayoutKind.GAMEPLAY -> defaultLayout()
    }

    /** Rotulo do comando classico (Xbox / PlayStation) para uma entrada. */
    fun classicLabel(input: PadInput, playstation: Boolean): String = when (input) {
        PadInput.A -> if (playstation) "✕" else "A"
        PadInput.B -> if (playstation) "○" else "B"
        PadInput.X -> if (playstation) "□" else "X"
        PadInput.Y -> if (playstation) "△" else "Y"
        PadInput.LB -> if (playstation) "L1" else "LB"
        PadInput.RB -> if (playstation) "R1" else "RB"
        PadInput.LT -> if (playstation) "L2" else "LT"
        PadInput.RT -> if (playstation) "R2" else "RT"
        PadInput.BACK -> if (playstation) "SELECT" else "BACK"
        PadInput.START -> "START"
        PadInput.L3 -> "L3"
        PadInput.R3 -> "R3"
        PadInput.UP -> "↑"
        PadInput.DOWN -> "↓"
        PadInput.LEFT -> "←"
        PadInput.RIGHT -> "→"
        PadInput.FORWARD -> "→•"
        PadInput.BACKWARD -> "←•"
    }
}

/** Gravacao do layout em JSON (pasta interna da app). */
object ControlLayoutStore {

    private fun file(kind: LayoutKind) = when (kind) {
        LayoutKind.MENU -> AppPaths.controlsMenuFile
        LayoutKind.MAP -> AppPaths.controlsMapFile
        LayoutKind.GAMEPLAY -> AppPaths.controlsFile
    }

    fun load(kind: LayoutKind = LayoutKind.GAMEPLAY): ControlLayout {
        val f = file(kind)
        if (!f.isFile) return GameActions.defaultLayout(kind)
        return try {
            val saved = fromJson(JSONObject(f.readText()))
            // 0.6.18: o layout dos menus gravado pela v1 (so A, Y e Select) da lugar ao novo,
            // com o comando completo; a opacidade escolhida fica.
            if (kind == LayoutKind.MENU && saved.version < GameActions.MENU_LAYOUT_VERSION) {
                val opacity = saved.elements.firstOrNull()?.opacity ?: 1f
                val fresh = GameActions.defaultMenuLayout()
                return fresh.copy(elements = fresh.elements.map { it.copy(opacity = opacity) })
            }
            saved.copy(menu = kind != LayoutKind.GAMEPLAY, map = kind == LayoutKind.MAP)
        } catch (e: Exception) {
            GameActions.defaultLayout(kind)
        }
    }

    fun save(layout: ControlLayout) {
        val f = file(layout.kind)
        f.parentFile?.mkdirs()
        val tmp = java.io.File(f.parentFile, f.name + ".tmp")
        tmp.writeText(toJson(layout).toString(2))
        if (!tmp.renameTo(f)) {
            f.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun reset() {
        AppPaths.controlsFile.delete()
        AppPaths.controlsMenuFile.delete()
        AppPaths.controlsMapFile.delete()
    }

    private fun inputsToJson(s: Set<PadInput>) = JSONArray().apply { s.forEach { put(it.name) } }
    private fun inputsFromJson(a: JSONArray?): Set<PadInput> =
        if (a == null) emptySet() else (0 until a.length()).mapNotNull { i ->
            runCatching { PadInput.valueOf(a.getString(i)) }.getOrNull()
        }.toSet()

    private fun actionToJson(a: ControlAction): JSONObject = when (a) {
        is ControlAction.Press -> JSONObject().put("type", "press").put("inputs", inputsToJson(a.inputs))
        is ControlAction.Sequence -> JSONObject().put("type", "sequence").put("holdLast", a.holdLast)
            .put("steps", JSONArray().apply {
                a.steps.forEach { put(JSONObject().put("inputs", inputsToJson(it.inputs)).put("frames", it.frames)) }
            })
    }

    private fun actionFromJson(o: JSONObject): ControlAction = when (o.optString("type")) {
        "sequence" -> {
            val steps = o.optJSONArray("steps") ?: JSONArray()
            ControlAction.Sequence(
                (0 until steps.length()).map { i ->
                    val s = steps.getJSONObject(i)
                    MacroStep(inputsFromJson(s.optJSONArray("inputs")), s.optInt("frames", 2).coerceIn(1, 600))
                },
                o.optBoolean("holdLast", false),
            )
        }
        else -> ControlAction.Press(inputsFromJson(o.optJSONArray("inputs")))
    }

    fun toJson(layout: ControlLayout): JSONObject = JSONObject().put("version", layout.version).put(
        "elements",
        JSONArray().apply {
            layout.elements.forEach { e ->
                put(
                    JSONObject()
                        .put("id", e.id).put("kind", e.kind.name).put("action", actionToJson(e.action))
                        .put("icon", e.icon.name).put("label", e.label)
                        .put("x", e.x.toDouble()).put("y", e.y.toDouble()).put("size", e.sizeDp.toDouble())
                        .put("opacity", e.opacity.toDouble()).put("visible", e.visible),
                )
            }
        },
    )

    fun fromJson(o: JSONObject): ControlLayout {
        val arr = o.getJSONArray("elements")
        val list = (0 until arr.length()).map { i ->
            val e = arr.getJSONObject(i)
            ControlElement(
                id = e.getString("id"),
                kind = runCatching { ElementKind.valueOf(e.getString("kind")) }.getOrDefault(ElementKind.BUTTON),
                action = actionFromJson(e.optJSONObject("action") ?: JSONObject()),
                icon = runCatching { ControlIcon.valueOf(e.optString("icon")) }.getOrDefault(ControlIcon.MACRO),
                label = e.optString("label"),
                x = e.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                y = e.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
                sizeDp = e.optDouble("size", 60.0).toFloat().coerceIn(28f, 260f),
                opacity = e.optDouble("opacity", 1.0).toFloat().coerceIn(0.05f, 1f),
                visible = e.optBoolean("visible", true),
            )
        }
        return ControlLayout(list, o.optInt("version", 1))
    }
}
