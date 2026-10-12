package com.dfdx047.dragonfury.game

import android.app.Activity
import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.View
import android.view.ViewGroup
import com.dfdx047.dragonfury.controls.ControlElement
import com.dfdx047.dragonfury.controls.ControlLayoutStore
import com.dfdx047.dragonfury.controls.PadInput
import com.dfdx047.dragonfury.data.AppPaths
import com.dfdx047.dragonfury.data.LauncherPrefs
import org.libsdl.app.DragonFuryPad

/**
 * Controles por toque dentro do jogo: poe o TouchControlsView por cima da
 * superficie do SDL, liga o comando virtual e esconde tudo quando ha um
 * comando fisico ligado (se o usuario quiser).
 *
 * Chamado pelo GameActivity (Java) nos eventos do ciclo de vida.
 */
class GameOverlay(private val activity: Activity) : InputManager.InputDeviceListener {
    private val prefs = LauncherPrefs(activity)
    private val engine = HelperEngine()
    private val main = Handler(Looper.getMainLooper())
    private val startedAt = SystemClock.uptimeMillis()
    private var view: TouchControlsView? = null
    private val inputManager = activity.getSystemService(Context.INPUT_SERVICE) as InputManager

    private val attachRunnable = Runnable { attachPad() }

    fun onCreate() {
        if (!prefs.touchEnabled) return
        AppPaths.init(activity)
        val v = TouchControlsView(activity).apply {
            layout = ControlLayoutStore.load()
            style = prefs.controlStyle
            globalOpacity = prefs.touchOpacity
            haptics = prefs.touchHaptics
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
        activity.addContentView(
            v,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        view = v
        inputManager.registerInputDeviceListener(this, main)
        updateVisibility()
        // O SDL e o runtime arrancam numa thread propria: dar-lhes uns segundos
        // antes de registar o comando virtual.
        main.postDelayed(attachRunnable, 2500)
    }

    private fun ensurePad() {
        if (!DragonFuryPad.isAttached() && SystemClock.uptimeMillis() - startedAt > 1500) attachPad()
    }

    private fun attachPad() {
        if (view == null) return
        if (DragonFuryPad.attach()) engine.start()
    }

    fun onResume() {
        if (view == null) return
        updateVisibility()
        if (DragonFuryPad.isAttached()) engine.start()
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
        view = null
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
    }

    override fun onInputDeviceAdded(deviceId: Int) = updateVisibility()
    override fun onInputDeviceRemoved(deviceId: Int) = updateVisibility()
    override fun onInputDeviceChanged(deviceId: Int) = updateVisibility()
}
