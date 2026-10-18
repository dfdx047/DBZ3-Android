package com.dfdx047.dragonfury.data

import android.content.Context
import com.dfdx047.dragonfury.drivers.DriverRepository

/**
 * Modo de compatibilidade automatico do Turnip (0.6.2).
 *
 * O Turnip num Adreno 6xx (SD865 do Retroid Pocket 5, 18/10) travava a GPU logo no ecra
 * titulo (VK_ERROR_DEVICE_LOST). O runtime ja liga um modo seguro nesses aparelhos (patch 0065);
 * se mesmo assim a GPU cair, o launcher sobe aqui o nivel desse driver e o proximo arranque junta
 * mais correcoes (GameActivity.turnipAutoFlags). Nivel guardado por driver (nome da pasta).
 */
object TurnipCompat {
    const val MAX_LEVEL = 3
    private const val PREFIX = "turnip_auto_level:"

    @Suppress("DEPRECATION")
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(LauncherPrefs.PREFS_NAME, Context.MODE_MULTI_PROCESS)

    /** O driver ativo e um Turnip (pelo nome do pacote ou pelo resultado do ultimo arranque)? */
    fun isTurnip(driver: String?): Boolean {
        if (driver == null) return false
        if (driver.contains("turnip", true) || driver.contains("freedreno", true) ||
            driver.contains("rutabaga", true) || driver.contains("mesa", true)
        ) {
            return true
        }
        val status = DriverRepository.lastStatus()
        return status != null && (status.detail.contains("Turnip", true) || status.driver.contains("freedreno", true))
    }

    fun level(context: Context, driver: String?): Int =
        if (driver == null) 0 else prefs(context).getInt(PREFIX + driver, 0)

    /**
     * A GPU caiu com este driver: sobe um nivel. Devolve o nivel novo, ou -1 se ja estava no
     * ultimo (falta trocar de driver).
     */
    fun raise(context: Context, driver: String): Int {
        val current = level(context, driver)
        if (current >= MAX_LEVEL) return -1
        val next = current + 1
        prefs(context).edit().putInt(PREFIX + driver, next).apply()
        return next
    }

    fun reset(context: Context, driver: String?) {
        if (driver == null) return
        prefs(context).edit().remove(PREFIX + driver).apply()
    }
}
