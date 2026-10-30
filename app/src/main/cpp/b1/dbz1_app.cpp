// Dragon Fury - Budokai 1 (yae1_xenon.xex do disco da HD Collection).
//
// O Budokai 1 HD e o mesmo motor do Budokai 3 HD (as mesmas 180 importacoes do kernel, o mesmo Direct3D do XDK
// 21119), mas nao tem port de PC: esta e a app minima do runtime para ele. Le o jogo da pasta da app (DBZ1/, com o
// yae1_xenon.xex e a us/ do disco, importadas pelo launcher), guarda saves e cache em user_data/dbz1 (os dois jogos
// tem o mesmo title ID 4E4D0856: pastas separadas para nao misturar saves) e as opcoes em dbz1_user.toml.
//
// As opcoes: o launcher escreve no toml as mesmas chaves dbz3_* que usa no Budokai 3 (resolucao, ampliacao, limite de
// FPS, audio...). No Budokai 3 e o codigo do jogo de PC (settings.cpp, ApplyRuntimeSettingsToSdk) que as passa para
// as cvars do SDK; aqui as mesmas cvars sao definidas e passadas da mesma maneira (so as que tem efeito no Budokai 1).

#include "dbz1_app.h"

#include <algorithm>
#include <filesystem>
#include <memory>
#include <string>

#include <rex/audio/sdl/sdl_audio_system.h>
#include <rex/cvar.h>
#include <rex/filesystem.h>
#include <rex/input/input_system.h>
#include <rex/kernel/init.h>
#include <rex/logging.h>
#include <rex/rex_app.h>
#include <rex/runtime.h>

#include "generated/dbz1_init.h"

// O Renut (swordman_renderer = renut), renut/df_renut_app.h.
#if defined(DF_RENUT)
#include "renut/df_renut_app.h"
#endif

// As opcoes do launcher (mesmos nomes, tipos e valores por omissao do settings.cpp do Budokai 3).
REXCVAR_DEFINE_INT32(dbz3_resolution_scale, 1, "DBZ1/Video", "Internal render scale (1x-4x of the 720p framebuffer)");
REXCVAR_DEFINE_STRING(dbz3_present_effect, "fsr", "DBZ1/Video", "Upscaling effect: bilinear, cas, fsr");
REXCVAR_DEFINE_STRING(dbz3_fsr_render, "native", "DBZ1/Video", "FSR render resolution");
REXCVAR_DEFINE_DOUBLE(dbz3_fsr_sharpness, 0.2, "DBZ1/Video", "FSR sharpness reduction in stops (0.0 - 2.0)");
REXCVAR_DEFINE_DOUBLE(dbz3_cas_sharpness, 0.0, "DBZ1/Video", "CAS additional sharpness (0.0 - 1.0)");
REXCVAR_DEFINE_STRING(dbz3_fxaa, "none", "DBZ1/Video", "FXAA: none, fxaa, fxaa_extreme");
REXCVAR_DEFINE_BOOL(dbz3_native_2x_msaa, true, "DBZ1/Video", "Native 2x MSAA for guest 2x MSAA surfaces");
REXCVAR_DEFINE_INT32(dbz3_anisotropic, 5, "DBZ1/Video", "Anisotropic filtering override (0 = off, 1..5)");
REXCVAR_DEFINE_DOUBLE(dbz3_hd_rim_light, 1.0, "DBZ1/Video", "HD rim light on character models (0 = off)");
REXCVAR_DEFINE_BOOL(dbz3_present_dither, false, "DBZ1/Video", "Dither the final image");
REXCVAR_DEFINE_INT32(dbz3_frame_cap, 60, "DBZ1/Video", "Frame cap in FPS (0 = uncapped)");
REXCVAR_DEFINE_BOOL(dbz3_show_fps, false, "DBZ1/Dev", "Show the FPS counter overlay");
REXCVAR_DEFINE_STRING(dbz3_quality_preset, "unset", "DBZ1/Video", "Quality preset (launcher)");
REXCVAR_DEFINE_DOUBLE(dbz3_master_volume, 1.0, "DBZ1/Audio", "Master volume (0.0 - 1.0)");
REXCVAR_DEFINE_BOOL(dbz3_mute, false, "DBZ1/Audio", "Mute all audio output");
REXCVAR_DEFINE_DOUBLE(dbz3_deadzone, 0.1, "DBZ1/Input", "Left stick deadzone (0.0 - 1.0)");
REXCVAR_DEFINE_BOOL(dbz3_rumble, true, "DBZ1/Input", "Enable controller vibration");
REXCVAR_DEFINE_STRING(dbz3_input_backend, "sdl", "DBZ1/Input", "Controller backend (sdl on Android)");

namespace dbz1 {
namespace {
std::filesystem::path g_config_path;

void Set(const char* name, const std::string& value) {
  if (!rex::cvar::SetFlagByName(name, value)) REXLOG_DEBUG("dbz1: cvar {} nao existe", name);
}
std::string Num(double v) {
  return std::to_string(v);
}

// O que o ApplyRuntimeSettingsToSdk do Budokai 3 faz, para as opcoes acima. for_game: o limite de FPS do jogador.
void ApplyRuntimeSettings(bool for_game) {
  const int32_t scale = std::clamp(REXCVAR_GET(dbz3_resolution_scale), 1, 4);
  Set("draw_resolution_scale_x", std::to_string(scale));
  Set("draw_resolution_scale_y", std::to_string(scale));
  // O vblank do convidado marca o ritmo da logica: sempre ligado e a 60 Hz.
  Set("vsync", "true");
  Set("video_mode_refresh_rate", "60");
  Set("native_2x_msaa", REXCVAR_GET(dbz3_native_2x_msaa) ? "true" : "false");
  Set("anisotropic_override", std::to_string(REXCVAR_GET(dbz3_anisotropic)));
  Set("present_effect", REXCVAR_GET(dbz3_present_effect));
  const std::string render = REXCVAR_GET(dbz3_fsr_render);
  Set("present_fsr_quality_mode", render == "native" ? "nativeaa" : render);
  Set("debug_overlay", REXCVAR_GET(dbz3_show_fps) ? "true" : "false");
  Set("present_fsr_sharpness_reduction", Num(REXCVAR_GET(dbz3_fsr_sharpness)));
  Set("present_cas_additional_sharpness", Num(REXCVAR_GET(dbz3_cas_sharpness)));
  Set("swap_post_effect", REXCVAR_GET(dbz3_fxaa));
  Set("present_dither", REXCVAR_GET(dbz3_present_dither) ? "true" : "false");
  Set("dbz3_rim_light_scale", Num(std::clamp(REXCVAR_GET(dbz3_hd_rim_light), 0.0, 1.0)));
  Set("audio_gain", Num(REXCVAR_GET(dbz3_master_volume)));
  Set("audio_mute", REXCVAR_GET(dbz3_mute) ? "true" : "false");
  Set("host_present_from_non_ui_thread", "true");
  if (for_game) {
    const int32_t cap = REXCVAR_GET(dbz3_frame_cap);
    Set("frame_cap", std::to_string(cap == 0 ? 0 : std::clamp(cap, 15, 1000)));
  }
  REXLOG_INFO("dbz1: opcoes -> escala {}x, ampliacao {}, fxaa {}, limite {}, msaa {}, aniso {}", scale,
              REXCVAR_GET(dbz3_present_effect), REXCVAR_GET(dbz3_fxaa), REXCVAR_GET(dbz3_frame_cap),
              REXCVAR_GET(dbz3_native_2x_msaa), REXCVAR_GET(dbz3_anisotropic));
}
}  // namespace

void SaveUserSettings() {
  ApplyRuntimeSettings(true);
  if (g_config_path.empty()) return;
  rex::cvar::SaveConfig(g_config_path);
}
}  // namespace dbz1

class Dbz1App : public rex::ReXApp {
 public:
  static std::unique_ptr<rex::ui::WindowedApp> Create(rex::ui::WindowedAppContext& ctx) {
    return std::make_unique<Dbz1App>(ctx);
  }

  explicit Dbz1App(rex::ui::WindowedAppContext& ctx) : ReXApp(ctx, "dbz1", PPCImageConfig) {}

 protected:
  void OnConfigurePaths(rex::PathConfig& paths) override {
    // No Android a "pasta do executavel" e a pasta de dados da app (REX_APP_DIR).
    const auto app_dir = rex::filesystem::GetExecutableFolder();
    paths.game_data_root = app_dir / "DBZ1";
    paths.user_data_root = app_dir / "user_data" / "dbz1";
    paths.cache_root = paths.user_data_root / "cache";
    paths.config_path = app_dir / "dbz1_user.toml";
    std::error_code ec;
    std::filesystem::create_directories(paths.cache_root, ec);
    dbz1::g_config_path = paths.config_path;
  }

  void OnLoadXexImage(std::string& xex_image) override {
    // O disco guarda o Budokai 1 como DBZ1/yae1_xenon.xex (o default.xex da raiz e o menu da colecao).
    xex_image = "game:\\yae1_xenon.xex";
  }

  void OnPostInitLogging() override {
    REXLOG_INFO("dbz1: Budokai 1 (yae1_xenon.xex), jogo em {}, saves em {}", game_data_root().string(),
                user_data_root().string());
  }

  void OnPreSetup(rex::RuntimeConfig& config) override {
    config.gpu_plugin = "xenos";
    config.audio_factory = REX_AUDIO_BACKEND(rex::audio::sdl::SDLAudioSystem);
    config.input_factory = REX_INPUT_BACKEND(rex::input::CreateDefaultInputSystem);
    config.kernel_init = rex::kernel::InitializeKernel;
    rex::cvar::SetFlagByName("fullscreen", "false");
#if defined(DF_RENUT)
    DfRenutPreSetup(config);
#endif
  }

#if defined(DF_RENUT)
  // A janela ja existe: o dispositivo Vulkan do Renut antes do runtime.
  std::optional<rex::PathConfig> OnFinalizePaths(const rex::PathConfig& defaults,
                                                 std::function<void(rex::PathConfig)> resume) override {
    DfRenutStart(app_context(), [this] { return imgui_drawer(); });
    return ReXApp::OnFinalizePaths(defaults, std::move(resume));
  }

  // O ImGui do SDK (menus) desenhado pelo plume, e a janela dele.
  std::unique_ptr<rex::ui::ImmediateDrawer> OnCreateImmediateDrawer() override {
    if (banjo::renut_gpu::Active()) return renut::gpu::CreateOverlayDrawer();
    return ReXApp::OnCreateImmediateDrawer();
  }

  void OnWindowPixelSizeChanged(uint32_t width, uint32_t height) override {
    ReXApp::OnWindowPixelSizeChanged(width, height);
    if (banjo::renut_gpu::Active()) renut::gpu::OnWindowResized();
  }

  void OnShutdown() override {
    if (banjo::renut_gpu::Active()) renut::gpu::ShutdownRenderer();
  }
#endif

  void OnPostSetup() override {
    // Como no Budokai 3: draws com fetch constants invalidas (passes de sombra) e as cvars do runtime/GPU, que so
    // existem depois de o plugin da GPU as registar.
    rex::cvar::SetFlagByName("gpu_allow_invalid_fetch_constants", "true");
    dbz1::ApplyRuntimeSettings(true);
  }
};

XE_DEFINE_WINDOWED_APP(dbz1, Dbz1App::Create)
