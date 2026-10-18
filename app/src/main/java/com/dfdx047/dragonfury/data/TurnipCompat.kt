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

    /** Turnip num Adreno 6xx so com autorizacao (pref lida pelo GameActivity). */
    fun a6xxAllowed(context: Context): Boolean = prefs(context).getBoolean(KEY_A6XX_ALLOW, false)

    fun setA6xxAllowed(context: Context, allowed: Boolean) {
        prefs(context).edit().putBoolean(KEY_A6XX_ALLOW, allowed).apply()
    }

    private const val KEY_A6XX_ALLOW = "turnip_a6xx_allow"

    /** Adreno 6xx? Mesmas pistas que o GameActivity.isAdreno6xx. */
    fun isAdreno6xx(): Boolean {
        try {
            val status = java.io.File(AppPaths.logsDir, "vulkan_driver_status.txt")
            if (status.isFile) {
                Regex("Adreno \\(TM\\) (\\d)\\d\\d").find(status.readText())?.let { return it.groupValues[1] == "6" }
            }
        } catch (_: Exception) {
        }
        try {
            val model = java.io.File("/sys/class/kgsl/kgsl-3d0/gpu_model").readText().trim().lowercase()
            if (model.startsWith("adreno")) {
                val digits = model.substring(6).takeWhile { it.isDigit() }
                if (digits.isNotEmpty()) return digits.length == 3 && digits[0] == '6'
            }
        } catch (_: Exception) {
        }
        val soc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) android.os.Build.SOC_MODEL else ""
        if (soc.uppercase() in A6XX_SOCS) return true
        return android.os.Build.BOARD.lowercase() in setOf(
            "kona", "msmnile", "lito", "trinket", "bengal", "holi", "atoll", "lahaina", "sdm845",
        )
    }

    private val A6XX_SOCS = setOf(
        "SM8250", "SM8150", "SM7250", "SM7225", "SM7150", "SM7125", "SM6150", "SM6125",
        "SM6115", "SM6225", "SM6350", "SM6375", "SM4350", "SM4375", "SM7325", "SM7350",
        "SM8350", "SDM845", "SDM855", "SDM730", "SDM710", "SDM670", "SM7315", "SM6450",
    )

    fun reset(context: Context, driver: String?) {
        if (driver == null) return
        prefs(context).edit().remove(PREFIX + driver).apply()
    }
}
