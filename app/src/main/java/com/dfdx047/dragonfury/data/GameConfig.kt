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
    val resolutionScale: Int = 1,             // dbz3_resolution_scale 1-3 (720p x N; so escalas inteiras)
    // Abaixo de 720p (patches 0055: draw_resolution_percent no Vulkan + atalhos exatos): 100, 75
    // (540p, todos os tamanhos do Budokai 3 exatos) ou 50 (360p). So com resolutionScale = 1.
    val resolutionPercent: Int = 100,         // draw_resolution_percent
    // "Tela" (30/10, como no Dragon Burst): a altura que a imagem ocupa na tela (ScreenFit), refeita a cada gravacao.
    val screenRes: Boolean = false,           // swordman_resolution_choice = "screen"
    val presentEffect: String = "bilinear",        // dbz3_present_effect bilinear|cas|fsr
    val fsrRender: String = "native",         // dbz3_fsr_render native|quality|balanced|performance|ultra_performance
    val fsrSharpness: Double = 0.2,           // dbz3_fsr_sharpness 0-2 (0 = mais nitido)
    val casSharpness: Double = 0.0,           // dbz3_cas_sharpness 0-1
    val fxaa: String = "none",                // dbz3_fxaa none|fxaa|fxaa_extreme
    val msaa2x: Boolean = true,               // dbz3_native_2x_msaa
    val anisotropic: Int = 5,                 // dbz3_anisotropic 0-5 (5 = 16x)
    val rimLight: Double = 1.0,               // dbz3_hd_rim_light 0-1 (brilho HD)
    val dither: Boolean = false,              // dbz3_present_dither
    val frameCap: Int = 60,                   // dbz3_frame_cap (30 ou 60) + dbz3_frameskip (30 = 1)
    // 120 FPS interpolado (patch 0057): o jogo a 60 e o presenter gera um quadro entre cada dois,
    // com a tela a 120 Hz (GameActivity.requestDisplayRate). So em telas de 110 Hz ou mais.
    val frameInterp: Boolean = false,         // dbz3_frame_interpolation
    val showFps: Boolean = false,             // dbz3_show_fps
    val letterbox: Boolean = true,            // present_letterbox (16:9 com barras / esticar)
    // Jogo
    val gameLanguage: Int = 1,                // dbz3_language 1 EN, 2 JP, 3 DE, 4 FR, 5 ES, 6 IT
    val tagTeam: Boolean = true,              // dbz3_tag_team_enabled
    val online: Boolean = true,               // dbz3_online_enabled (ONLINE no Duelo/SELECT)
    val formSelect: Boolean = true,           // dbz3_form_select_enabled (LT/RT escolhem a forma inicial)
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
    val directResolve: Int = 1,               // dbz3_direct_resolve_texture 0/1/2 (atalho de efeitos)
    val directResolveFast: Boolean = true,    // dbz3_direct_resolve_skip_memory (modo rapido)
    val rtDirect: Boolean = true,             // dbz3_rt_direct (render targets sem transferencias, patch 0028)
    val specShaders: Boolean = true,          // dbz3_spec_texture_signs + dbz3_tex_implicit_lod (patch 0035)
    // Efeitos do jogo (patch 0047; so atuam com dbz3_rt_direct). Desligar ajuda GPUs fracos (Mali).
    val fxBloom: Boolean = true,              // dbz3_fx_bloom
    val fxDof: Boolean = true,                // dbz3_fx_dof (profundidade de campo)
    val fxFxaa: Boolean = true,               // dbz3_fx_fxaa (FXAA do proprio jogo, nao o dbz3_fxaa)
    // Renderizador (30/10, decisao do dono, como no Dragon Burst): auto (por omissao) = o Renut (o Direct3D do jogo
    // nativo, docs/renut) em todas as GPUs; o Xenia so quando o jogador o escolhe ("xenos"). Nunca troca sozinho: se o
    // Renut fechar o jogo, o launcher mostra o aviso e, do 2.o crash seguido em diante, pergunta (RenutCrash).
    val renderer: String = "auto",            // swordman_renderer_choice auto|renut|xenos
) {
    /** O jogo arranca no Renut com estas opcoes. */
    val useRenut: Boolean get() = renderer != "xenos"

    /**
     * sa2_render_height_handheld: a altura que o Renut desenha (render_scale.h: 360, 480, 540, 720, 1080, 1440, 2160).
     * A mesma escolha de resolucao do Xenia (720p x escala, ou 50/75% de 720p).
     */
    val renutHeight: Int get() = when {
        resolutionScale > 1 -> 720 * resolutionScale.coerceAtMost(MAX_RESOLUTION_SCALE)
        screenRes -> ScreenFit.renutHeight(letterbox)
        else -> 720 * effectivePercent / 100
    }

    fun toToml(): Map<String, Any> = linkedMapOf(
        // "manual": o preset "auto" do PC reescreveria estas opcoes no arranque.
        "dbz3_quality_preset" to "manual",
        "dbz3_input_backend" to "sdl",
        // O menu rapido do PC (ImGui) fica desligado no controle: Select + Start e o Voltar abrem o
        // menu do jogo do app (InGameMenu), com as opcoes que servem no Android.
        "quick_menu_buttons" to "none",
        // So escalas inteiras: o renderizador nao suporta 1.5x. 4x ficou de fora (memoria
        // demais para um telemovel: o buffer de resolves escalados e 512 MB x escala^2).
        "dbz3_resolution_scale" to resolutionScale.coerceIn(1, MAX_RESOLUTION_SCALE),
        "draw_resolution_percent" to effectivePercent,
        "dbz3_present_effect" to presentEffect,
        "dbz3_fsr_render" to fsrRender,
        "dbz3_fsr_sharpness" to fsrSharpness.coerceIn(0.0, 2.0),
        "dbz3_cas_sharpness" to casSharpness.coerceIn(0.0, 1.0),
        "dbz3_fxaa" to fxaa,
        "dbz3_native_2x_msaa" to msaa2x,
        "dbz3_anisotropic" to anisotropic.coerceIn(0, 5),
        "dbz3_hd_rim_light" to rimLight.coerceIn(0.0, 1.0),
        "dbz3_present_dither" to dither,
        "dbz3_frame_cap" to if (frameInterp) 60 else frameCap,
        // 30 FPS de verdade: o jogo continua a 60 e so metade dos frames e desenhada
        // (patch 0023). Antes o limite so atrasava a apresentacao e a GPU fazia tudo.
        "dbz3_frameskip" to if (!frameInterp && frameCap in 1..30) 1 else 0,
        "dbz3_frame_interpolation" to frameInterp,
        "dbz3_show_fps" to showFps,
        "present_letterbox" to letterbox,
        "dbz3_language" to gameLanguage.coerceIn(1, 6),
        "dbz3_tag_team_enabled" to tagTeam,
        "dbz3_online_enabled" to online,
        "dbz3_form_select_enabled" to formSelect,
        "dbz3_texture_packs_on" to texturePacksOn,
        "dbz3_master_volume" to masterVolume.coerceIn(0.0, 1.0),
        "dbz3_mute" to mute,
        "dbz3_deadzone" to deadzone.coerceIn(0.0, 0.9),
        "dbz3_rumble" to rumble,
        "dbz3_unlock_all_characters" to unlockAllCharacters,
        "log_level" to if (verboseLog) "debug" else "info",
        "dbz3_perf_logging" to perfLogging,
        "dbz3_direct_resolve_texture" to directResolve.coerceIn(0, 2),
        "dbz3_direct_resolve_skip_memory" to directResolveFast,
        "dbz3_rt_direct" to rtDirect,
        "dbz3_spec_texture_signs" to specShaders,
        "dbz3_tex_implicit_lod" to specShaders,
        "dbz3_fx_bloom" to fxBloom,
        "dbz3_fx_dof" to fxDof,
        "dbz3_fx_fxaa" to fxFxaa,
        // Memoria do jogo num buffer normal (sem sparse binding): o RB2-Android e o
        // Skate3-Mobile desligam-no nos Adreno; menos um caminho com bugs de driver.
        "vulkan_sparse_shared_memory" to false,
        "swordman_renderer" to if (useRenut) "renut" else "xenos",
        "swordman_renderer_choice" to renderer,
        "sa2_render_height_handheld" to renutHeight,
        "swordman_resolution_choice" to if (screenRes && resolutionScale == 1) "screen" else "",
        // A proporcao no Renut (present.cpp): a mesma escolha do present_letterbox do Xenia.
        "swordman_aspect" to if (letterbox) "original" else "stretch",
    )

    /** Percentagem que o jogo usa: abaixo de 100 so a 1x. */
    val effectivePercent: Int
        get() = when {
            resolutionScale > 1 -> 100
            screenRes -> ScreenFit.xeniaPercent(letterbox)
            else -> resolutionPercent.let { if (it in PERCENTS) it else 100 }
        }

    /** Taxa de saida que esta config pede a tela (120 com a interpolacao, senao 60). */
    val outputHz: Int get() = if (frameInterp) 120 else 60

    /** Resolucao escolhida como uma so lista: (escala, percentagem). */
    val resolutionChoice: Pair<Int, Int> get() = if (screenRes && resolutionScale == 1) 1 to SCREEN_PERCENT
        else resolutionScale to effectivePercent

    /**
     * Predefinicao das GPUs Mali (RG Cube, 17/10): 540p e os efeitos do jogo desligados dao 60 FPS
     * na luta (GPU 11-18 ms; a 720p com efeitos eram ~30 ms). So mexe na resolucao se estava em
     * 720p 1x.
     */
    fun withMaliPreset(): GameConfig = copy(
        resolutionPercent = if (resolutionScale == 1 && resolutionPercent == 100) 75 else resolutionPercent,
        // Tela pequena (RG Cube, 720x720: a imagem tem 405 linhas): a "Tela" (30/10).
        screenRes = screenRes || (resolutionScale == 1 && resolutionPercent == 100 && ScreenFit.small()),
        fxBloom = false,
        fxDof = false,
        fxFxaa = false,
    )

    fun withResolution(choice: Pair<Int, Int>): GameConfig =
        if (choice.second == SCREEN_PERCENT) copy(resolutionScale = 1, resolutionPercent = 100, screenRes = true)
        else copy(resolutionScale = choice.first.coerceIn(1, MAX_RESOLUTION_SCALE),
            resolutionPercent = if (choice.first > 1) 100 else choice.second, screenRes = false)

    companion object {
        /** Abaixo de 720p: 75% (960x540) e 50% (640x360) dao os tamanhos do jogo sem arredondar. */
        val PERCENTS = listOf(50, 75, 100)
        /** A "Tela" na lista de resolucoes (resolutionChoice = 1 to SCREEN_PERCENT). */
        const val SCREEN_PERCENT = 0
        val EFFECTS = listOf("bilinear", "cas", "fsr")
        val FSR_RENDER = listOf("native", "quality", "balanced", "performance", "ultra_performance")
        val FXAA = listOf("none", "fxaa", "fxaa_extreme")
        const val MAX_RESOLUTION_SCALE = 3
        val RENDERERS = listOf("auto", "renut", "xenos")

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
                resolutionScale = int("dbz3_resolution_scale", d.resolutionScale).coerceIn(1, MAX_RESOLUTION_SCALE),
                resolutionPercent = int("draw_resolution_percent", d.resolutionPercent).let { if (it in PERCENTS) it else 100 },
                screenRes = str("swordman_resolution_choice", "") == "screen",
                // fsr3/dlss vindos do PC caem para o FSR 1, que funciona no Vulkan.
                presentEffect = str("dbz3_present_effect", d.presentEffect).let { if (it in EFFECTS) it else "bilinear" },
                fsrRender = str("dbz3_fsr_render", d.fsrRender).let { if (it in FSR_RENDER) it else "native" },
                fsrSharpness = dbl("dbz3_fsr_sharpness", d.fsrSharpness),
                casSharpness = dbl("dbz3_cas_sharpness", d.casSharpness),
                fxaa = str("dbz3_fxaa", d.fxaa).let { if (it in FXAA) it else "none" },
                msaa2x = bool("dbz3_native_2x_msaa", d.msaa2x),
                anisotropic = int("dbz3_anisotropic", d.anisotropic),
                rimLight = dbl("dbz3_hd_rim_light", d.rimLight),
                dither = bool("dbz3_present_dither", d.dither),
                frameCap = int("dbz3_frame_cap", d.frameCap).let { if (it in 1..30) 30 else 60 },
                frameInterp = bool("dbz3_frame_interpolation", d.frameInterp),
                showFps = bool("dbz3_show_fps", d.showFps),
                letterbox = bool("present_letterbox", d.letterbox),
                gameLanguage = int("dbz3_language", d.gameLanguage),
                tagTeam = bool("dbz3_tag_team_enabled", d.tagTeam),
                online = bool("dbz3_online_enabled", d.online),
                formSelect = bool("dbz3_form_select_enabled", d.formSelect),
                texturePacksOn = bool("dbz3_texture_packs_on", d.texturePacksOn),
                masterVolume = dbl("dbz3_master_volume", d.masterVolume),
                mute = bool("dbz3_mute", d.mute),
                deadzone = dbl("dbz3_deadzone", d.deadzone),
                rumble = bool("dbz3_rumble", d.rumble),
                unlockAllCharacters = bool("dbz3_unlock_all_characters", d.unlockAllCharacters),
                verboseLog = str("log_level", "info").let { it == "debug" || it == "trace" },
                perfLogging = bool("dbz3_perf_logging", d.perfLogging),
                // 2 (cores trocadas) era so um teste: confirmado que 1 e o certo.
                directResolve = if (int("dbz3_direct_resolve_texture", d.directResolve) == 0) 0 else 1,
                directResolveFast = bool("dbz3_direct_resolve_skip_memory", d.directResolveFast),
                rtDirect = bool("dbz3_rt_direct", d.rtDirect),
                specShaders = bool("dbz3_spec_texture_signs", d.specShaders),
                fxBloom = bool("dbz3_fx_bloom", d.fxBloom),
                fxDof = bool("dbz3_fx_dof", d.fxDof),
                fxFxaa = bool("dbz3_fx_fxaa", d.fxFxaa),
                renderer = str("swordman_renderer_choice", d.renderer).let { if (it in RENDERERS) it else "auto" },
            )
        }

        fun save(config: GameConfig, file: File = AppPaths.userToml) {
            TomlLite.merge(file, config.toToml())
        }
    }
}

/**
 * O Renut fechou o jogo (30/10, decisao do dono: o Xenia nao e muleta; como no Dragon Burst). O GameActivity deixa
 * renut_run.pending (com o id do jogo) enquanto o jogo do Renut esta em primeiro plano e zera a contagem ao sair
 * normalmente. Se o launcher encontra o marcador, o jogo fechou: mais um crash seguido e o aviso (Exportar logs, issues);
 * do 2.o seguido em diante pergunta se o jogador quer o Xenia por agora (nesse jogo). Nunca troca sozinho. O motivo,
 * quando o jogo o sabe (o driver sem o que o Renut precisa), vem do renut_support.txt.
 */
object RenutCrash {
    private val pending get() = File(AppPaths.appDir, "renut_run.pending")
    private val count get() = File(AppPaths.appDir, "renut_crashes.txt")

    /** Crashes seguidos ate agora (0 = a ultima sessao do Renut saiu normalmente). */
    fun consecutive(): Int = try {
        count.takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull() ?: 0
    } catch (_: Exception) {
        0
    }

    /**
     * Com o jogo fechado: a ultima sessao do Renut caiu? Conta-a e devolve os crashes seguidos e o jogo que caiu
     * (0 = nao caiu).
     */
    fun check(): Pair<Int, Game> = try {
        if (!pending.exists()) 0 to Game.B3 else {
            val game = Game.byId(pending.readText().trim())
            pending.delete()
            val n = consecutive() + 1
            count.writeText("$n\n")
            n to game
        }
    } catch (_: Exception) {
        0 to Game.B3
    }

    /** O que o jogo disse do driver na ultima tentativa (renut_support.txt "no" + a lista), ou "". */
    fun missing(): String = try {
        val lines = File(AppPaths.appDir, "renut_support.txt").takeIf { it.isFile }?.readLines().orEmpty()
        if (lines.firstOrNull()?.trim() == "no") lines.getOrNull(1)?.trim().orEmpty() else ""
    } catch (_: Exception) {
        ""
    }
}

/**
 * A altura que a imagem do jogo ocupa na tela (deitada), como no Dragon Burst: com barras (16:9) min(altura,
 * largura * 9 / 16); esticada, a altura toda. Pela tela fisica: no RG Cube (720x720) 405, no RP5 (1920x1080) 1080.
 */
object ScreenFit {
    fun height(letterbox: Boolean): Int {
        val m = android.content.res.Resources.getSystem().displayMetrics
        val w = maxOf(m.widthPixels, m.heightPixels)
        val h = minOf(m.widthPixels, m.heightPixels)
        val fit = if (letterbox) minOf(h, w * 9 / 16) else h
        return (fit and 1.inv()).coerceIn(360, 2160)
    }

    // A mais perto da tela, no empate a menor.
    private fun nearest(heights: IntArray, fit: Int): Int =
        heights.minByOrNull { kotlin.math.abs(it - fit) * 2 + (if (it > fit) 1 else 0) } ?: 720

    /**
     * As alturas que o Renut desenha (render_scale.h: 1/2, 2/3, 3/4, 1, 3/2, 2, 3 de 720p; 405 nao e uma delas e mexer
     * na tabela recompila a cache de shaders). Ate 720 (a escala 1, como as outras opcoes de 1x).
     */
    fun renutHeight(letterbox: Boolean): Int = nearest(intArrayOf(360, 480, 540, 720), height(letterbox))

    /** O Xenia so tem os tamanhos exatos de GameConfig.PERCENTS (360, 540, 720). */
    fun xeniaPercent(letterbox: Boolean): Int = nearest(intArrayOf(360, 540, 720), height(letterbox)) * 100 / 720

    /** Tela pequena: a imagem ocupa menos de 540 linhas. */
    fun small(): Boolean = height(true) < 540
}
