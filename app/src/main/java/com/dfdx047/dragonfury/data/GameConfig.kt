package com.dfdx047.dragonfury.data

import java.io.File

/**
 * Opcoes do jogo, guardadas no dbz3_user.toml que o runtime le no arranque
 * (LoadUserSettings -> rex::cvar::LoadConfig). O launcher escreve o ficheiro
 * antes de abrir o jogo, por isso ate as opcoes "so no arranque" (resolucao
 * interna, MSAA, anisotropico) entram sem reiniciar. As mudancas feitas no
 * menu rapido do jogo (F1 / Back+Start) ficam no mesmo ficheiro e o launcher
 * le-as ao abrir.
 *
 * So entram opcoes que funcionam no Vulkan/Android. Ficam de fora: DLSS e FSR 3
 * (no Vulkan o FSR 3 e um esboco sem dados temporais), texturas HD por upscale
 * (so D3D12), VRR, modo de ecra inteiro e vsync (sempre ligado).
 */
data class GameConfig(
    // Desempenho / imagem
    val resolutionScale: Int = 1,             // dbz3_resolution_scale 1-4 (720p x N)
    val presentEffect: String = "fsr",        // dbz3_present_effect bilinear|cas|fsr
    val fsrRender: String = "native",         // dbz3_fsr_render native|quality|balanced|performance|ultra_performance
    val fsrSharpness: Double = 0.2,           // dbz3_fsr_sharpness 0-2 (0 = mais nitido)
    val casSharpness: Double = 0.0,           // dbz3_cas_sharpness 0-1
    val fxaa: String = "none",                // dbz3_fxaa none|fxaa|fxaa_extreme
    val msaa2x: Boolean = true,               // dbz3_native_2x_msaa
    val anisotropic: Int = 5,                 // dbz3_anisotropic 0-5 (5 = 16x)
    val rimLight: Double = 1.0,               // dbz3_hd_rim_light 0-1 (brilho HD)
    val dither: Boolean = false,              // dbz3_present_dither
    val frameCap: Int = 60,                   // dbz3_frame_cap (30 ou 60)
    val showFps: Boolean = false,             // dbz3_show_fps
    val letterbox: Boolean = true,            // present_letterbox (16:9 com barras / esticar)
    // Jogo
    val gameLanguage: Int = 1,                // dbz3_language 1 EN, 2 JP, 3 DE, 4 FR, 5 ES, 6 IT
    val tagTeam: Boolean = true,              // dbz3_tag_team_enabled
    val texturePacksOn: Boolean = true,       // dbz3_texture_packs_on
    // Audio
    val masterVolume: Double = 1.0,           // dbz3_master_volume
    val mute: Boolean = false,                // dbz3_mute
    // Comando fisico
    val deadzone: Double = 0.1,               // dbz3_deadzone
    val rumble: Boolean = true,               // dbz3_rumble
    // Cheats
    val unlockAllCharacters: Boolean = false, // dbz3_unlock_all_characters (patch 0001)
    // Depuracao
    val verboseLog: Boolean = false,          // log_level = "debug" (senao "info")
    val perfLogging: Boolean = false,         // dbz3_perf_logging + logs/gpu_stats.log
) {
    fun toToml(): Map<String, Any> = linkedMapOf(
        // "manual": o preset "auto" do PC reescreveria estas opcoes no arranque.
        "dbz3_quality_preset" to "manual",
        "dbz3_input_backend" to "sdl",
        "dbz3_resolution_scale" to resolutionScale.coerceIn(1, 4),
        "dbz3_present_effect" to presentEffect,
        "dbz3_fsr_render" to fsrRender,
        "dbz3_fsr_sharpness" to fsrSharpness.coerceIn(0.0, 2.0),
        "dbz3_cas_sharpness" to casSharpness.coerceIn(0.0, 1.0),
        "dbz3_fxaa" to fxaa,
        "dbz3_native_2x_msaa" to msaa2x,
        "dbz3_anisotropic" to anisotropic.coerceIn(0, 5),
        "dbz3_hd_rim_light" to rimLight.coerceIn(0.0, 1.0),
        "dbz3_present_dither" to dither,
        "dbz3_frame_cap" to frameCap,
        "dbz3_show_fps" to showFps,
        "present_letterbox" to letterbox,
        "dbz3_language" to gameLanguage.coerceIn(1, 6),
        "dbz3_tag_team_enabled" to tagTeam,
        "dbz3_texture_packs_on" to texturePacksOn,
        "dbz3_master_volume" to masterVolume.coerceIn(0.0, 1.0),
        "dbz3_mute" to mute,
        "dbz3_deadzone" to deadzone.coerceIn(0.0, 0.9),
        "dbz3_rumble" to rumble,
        "dbz3_unlock_all_characters" to unlockAllCharacters,
        "log_level" to if (verboseLog) "debug" else "info",
        "dbz3_perf_logging" to perfLogging,
    )

    companion object {
        val EFFECTS = listOf("bilinear", "cas", "fsr")
        val FSR_RENDER = listOf("native", "quality", "balanced", "performance", "ultra_performance")
        val FXAA = listOf("none", "fxaa", "fxaa_extreme")

        fun load(file: File = AppPaths.userToml): GameConfig {
            val m = try {
                TomlLite.read(file)
            } catch (e: TomlLite.ParseError) {
                emptyMap()
            }
            val d = GameConfig()
            fun str(k: String, def: String) = (m[k] as? TomlLite.Value.Str)?.v ?: def
            fun bool(k: String, def: Boolean) = (m[k] as? TomlLite.Value.Bool)?.v ?: def
            fun int(k: String, def: Int) = when (val v = m[k]) {
                is TomlLite.Value.Num -> v.v.toInt()
                is TomlLite.Value.Dbl -> v.v.toInt()
                else -> def
            }
            fun dbl(k: String, def: Double) = when (val v = m[k]) {
                is TomlLite.Value.Num -> v.v.toDouble()
                is TomlLite.Value.Dbl -> v.v
                else -> def
            }
            return GameConfig(
                resolutionScale = int("dbz3_resolution_scale", d.resolutionScale).coerceIn(1, 4),
                // fsr3/dlss vindos do PC caem para o FSR 1, que funciona no Vulkan.
                presentEffect = str("dbz3_present_effect", d.presentEffect).let { if (it in EFFECTS) it else "fsr" },
                fsrRender = str("dbz3_fsr_render", d.fsrRender).let { if (it in FSR_RENDER) it else "native" },
                fsrSharpness = dbl("dbz3_fsr_sharpness", d.fsrSharpness),
                casSharpness = dbl("dbz3_cas_sharpness", d.casSharpness),
                fxaa = str("dbz3_fxaa", d.fxaa).let { if (it in FXAA) it else "none" },
                msaa2x = bool("dbz3_native_2x_msaa", d.msaa2x),
                anisotropic = int("dbz3_anisotropic", d.anisotropic),
                rimLight = dbl("dbz3_hd_rim_light", d.rimLight),
                dither = bool("dbz3_present_dither", d.dither),
                frameCap = int("dbz3_frame_cap", d.frameCap).let { if (it in 1..30) 30 else 60 },
                showFps = bool("dbz3_show_fps", d.showFps),
                letterbox = bool("present_letterbox", d.letterbox),
                gameLanguage = int("dbz3_language", d.gameLanguage),
                tagTeam = bool("dbz3_tag_team_enabled", d.tagTeam),
                texturePacksOn = bool("dbz3_texture_packs_on", d.texturePacksOn),
                masterVolume = dbl("dbz3_master_volume", d.masterVolume),
                mute = bool("dbz3_mute", d.mute),
                deadzone = dbl("dbz3_deadzone", d.deadzone),
                rumble = bool("dbz3_rumble", d.rumble),
                unlockAllCharacters = bool("dbz3_unlock_all_characters", d.unlockAllCharacters),
                verboseLog = str("log_level", "info").let { it == "debug" || it == "trace" },
                perfLogging = bool("dbz3_perf_logging", d.perfLogging),
            )
        }

        fun save(config: GameConfig, file: File = AppPaths.userToml) {
            TomlLite.merge(file, config.toToml())
        }
    }
}
