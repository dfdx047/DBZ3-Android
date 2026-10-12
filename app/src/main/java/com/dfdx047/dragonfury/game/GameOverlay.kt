package com.dfdx047.dragonfury.game

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dfdx047.dragonfury.R
import com.dfdx047.dragonfury.controls.ControlAction
import com.dfdx047.dragonfury.controls.ControlElement
import com.dfdx047.dragonfury.controls.ControlLayoutStore
import com.dfdx047.dragonfury.controls.MacroStep
import com.dfdx047.dragonfury.controls.PadInput
import com.dfdx047.dragonfury.data.AppPaths
import com.dfdx047.dragonfury.data.DarkMode
import com.dfdx047.dragonfury.data.LauncherPrefs
import com.dfdx047.dragonfury.data.ThemeMode
import com.dfdx047.dragonfury.ui.theme.DragonFuryTheme
import org.libsdl.app.DragonFuryPad

/**
 * Controles por toque dentro do jogo: o TouchControlsView por cima da
 * superficie do SDL, o comando virtual, a engrenagem que abre o menu dos
 * controles (com o jogo pausado) e o editor de layout por cima do jogo.
 *
 * Chamado pelo GameActivity (Java) nos eventos do ciclo de vida.
 */
class GameOverlay(private val activity: Activity) : InputManager.InputDeviceListener, InGameActions {
    private val prefs = LauncherPrefs(activity)
    private val engine = HelperEngine()
    private val main = Handler(Looper.getMainLooper())
    private val startedAt = SystemClock.uptimeMillis()
    private var view: TouchControlsView? = null
    private var gear: ImageView? = null
    private var compose: ComposeView? = null
    private val owner = OverlayOwner()
    private val screen = mutableStateOf(InGameScreen.CLOSED)
    private val inputManager = activity.getSystemService(Context.INPUT_SERVICE) as InputManager

    private val attachRunnable = Runnable { attachPad() }

    fun onCreate() {
        AppPaths.init(activity)
        engine.pauseQuickMenu = prefs.pauseOnMenus
        // O motor corre sempre (tambem pausa o jogo com o menu rapido do port),
        // mesmo com os controles por toque desligados.
        main.postDelayed(attachRunnable, 2500)
        if (!prefs.touchEnabled) return

        val density = activity.resources.displayMetrics.density
        val v = TouchControlsView(activity).apply {
            listener = object : TouchControlsView.Listener {
                override fun onPress(element: ControlElement) {
                    ensurePad()
                    engine.press(element.id, element.action)
                }

                override fun onRelease(element: ControlElement) = engine.releaseElement(element.id)

                override fun onStick(x: Float, y: Float) {
                    ensurePad()
                    engine.setStick(x, y)
                }

                override fun onDpad(element: ControlElement, inputs: Set<PadInput>) {
                    ensurePad()
                    engine.setDpad(element.id, inputs)
                }
            }
        }
        view = v
        applyAppearance()
        activity.addContentView(
            v,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        // Engrenagem (canto de cima a esquerda): abre o menu dos controles.
        val size = (40 * density).toInt()
        val g = ImageView(activity).apply {
            setImageResource(R.drawable.ic_btn_menu)
            setColorFilter(Color.parseColor("#FFD27A"))
            val pad = (9 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(60, 255, 255, 255))
                setStroke((1.5f * density).toInt(), Color.argb(140, 255, 243, 222))
            }
            alpha = prefs.touchOpacity.coerceAtLeast(0.4f)
            contentDescription = activity.getString(R.string.ingame_title)
            setOnClickListener { openMenu() }
        }
        activity.addContentView(
            g,
            FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.START).apply {
                leftMargin = (14 * density).toInt()
                topMargin = (10 * density).toInt()
            },
        )
        gear = g

        // Menu e editor (Compose), escondidos ate a engrenagem ser tocada.
        val c = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                DragonFuryTheme(themeMode = ThemeMode.DRAGON, darkMode = DarkMode.DARK) {
                    InGameOverlay(screen.value, prefs, this@GameOverlay)
                }
            }
            visibility = View.GONE
        }
        activity.addContentView(
            c,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        compose = c

        inputManager.registerInputDeviceListener(this, main)
        updateVisibility()
    }

    private fun ensurePad() {
        if (!DragonFuryPad.isAttached() && SystemClock.uptimeMillis() - startedAt > 1500) attachPad()
    }

    private fun attachPad() {
        if (view != null) DragonFuryPad.attach()
        engine.start()
    }

    // ---------------------------------------------------------------- menu

    fun isMenuOpen(): Boolean = screen.value != InGameScreen.CLOSED

    private fun setScreen(s: InGameScreen) {
        screen.value = s
        compose?.visibility = if (s == InGameScreen.CLOSED) View.GONE else View.VISIBLE
        if (s != InGameScreen.CLOSED) {
            // Os controles do jogo saem de cena enquanto o menu/editor esta aberto.
            view?.releaseAll()
            view?.visibility = View.GONE
            gear?.visibility = View.GONE
        } else {
            updateVisibility()
        }
        // Com o menu aberto o jogo fica pausado (GPU + audio), se o usuario quiser.
        engine.menuOpen = s != InGameScreen.CLOSED && prefs.pauseOnMenus
        if (!DragonFuryPad.isAttached()) engine.start()
    }

    private fun openMenu() = setScreen(InGameScreen.MENU)

    override fun resume() = setScreen(InGameScreen.CLOSED)

    override fun openEditor() = setScreen(InGameScreen.EDITOR)

    override fun closeEditor() {
        view?.layout = ControlLayoutStore.load()
        setScreen(InGameScreen.MENU)
    }

    /** Fecha o menu e carrega Back + Start: abre o menu rapido do port (que tambem pausa). */
    override fun openPortMenu() {
        setScreen(InGameScreen.CLOSED)
        ensurePad()
        engine.press(
            "__port_menu",
            ControlAction.Sequence(listOf(MacroStep(setOf(PadInput.BACK, PadInput.START), 8)), holdLast = false),
        )
    }

    override fun applyAppearance() {
        val v = view ?: return
        v.layout = ControlLayoutStore.load()
        v.style = prefs.controlStyle
        v.directional = prefs.directional
        v.badge = prefs.buttonBadge
        v.globalOpacity = prefs.touchOpacity
        v.haptics = prefs.touchHaptics
        gear?.alpha = prefs.touchOpacity.coerceAtLeast(0.4f)
    }

    // ------------------------------------------------------------ ciclo de vida

    fun onResume() {
        engine.pauseQuickMenu = prefs.pauseOnMenus
        if (view != null) {
            applyAppearance()
            updateVisibility()
        }
        if (DragonFuryPad.isAttached() || view == null) engine.start()
    }

    fun onPause() {
        view?.releaseAll()
        engine.stop()
    }

    fun onDestroy() {
        main.removeCallbacks(attachRunnable)
        if (view != null) inputManager.unregisterInputDeviceListener(this)
        engine.release()
        DragonFuryPad.detach()
        owner.destroy()
        view = null
    }

    /** Back do Android com o menu aberto: volta um nivel. Devolve true se tratou. */
    fun onBackPressed(): Boolean = when (screen.value) {
        InGameScreen.EDITOR -> {
            closeEditor()
            true
        }
        InGameScreen.MENU -> {
            resume()
            true
        }
        InGameScreen.CLOSED -> false
    }

    private fun physicalPadConnected(): Boolean =
        InputDevice.getDeviceIds().any { id ->
            val d = InputDevice.getDevice(id) ?: return@any false
            !d.isVirtual && (d.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                d.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
        }

    private fun updateVisibility() {
        val v = view ?: return
        val hide = prefs.touchHideWithPad && physicalPadConnected()
        if (hide) v.releaseAll()
        v.visibility = if (hide) View.GONE else View.VISIBLE
        gear?.visibility = if (screen.value == InGameScreen.CLOSED) View.VISIBLE else View.GONE
    }

    override fun onInputDeviceAdded(deviceId: Int) = updateVisibility()
    override fun onInputDeviceRemoved(deviceId: Int) = updateVisibility()
    override fun onInputDeviceChanged(deviceId: Int) = updateVisibility()
}
