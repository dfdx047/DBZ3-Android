# DBZ3-Android (Dragon Fury) - nucleo nativo
#
# Compila o SDK ReXGlue (external/rexglue-sdk-0.10, com o overlay do projeto
# original e os patches Android) e os dois jogos do disco da HD Collection:
#
#   libgame_b3.so       Budokai 3 recompilado (external/dbz3, generated/ do yae3_xenon.xex) + codigo do projeto
#                       original + glue Android
#   libgame_b1.so       Budokai 1 recompilado (external/dbz1, generated/ do yae1_xenon.xex) + app minima (b1/)
#                       + glue Android
#   libmain.so          carregador: le o executavel do jogador (a chave de cada libgame)
#   librexruntime.so    runtime ReXGlue (kernel, memoria, audio, input, UI)
#   librexgpu-xenos.so  plugin da GPU Xenos (Vulkan)
#   libSDL3.so          SDL3 (janela, eventos, audio, comandos)
#
# Os libgame NAO vao no APK: so encriptados (assets/libgame_b3.zdat e libgame_b1.zdat, tools/pack-libgame.py), com a
# chave tirada do executavel de cada jogo (ver o fim deste ficheiro).

cmake_minimum_required(VERSION 3.25)
project(dbz3_android LANGUAGES C CXX)

get_filename_component(DBZ3_REPO_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../.." ABSOLUTE)
set(REXSDK_ROOT "${DBZ3_REPO_ROOT}/external/rexglue-sdk-0.10")
set(DBZ3_ROOT   "${DBZ3_REPO_ROOT}/external/dbz3")
set(DBZ3_GENERATED "${DBZ3_ROOT}/generated")
set(DBZ1_ROOT   "${DBZ3_REPO_ROOT}/external/dbz1")
set(DBZ1_GENERATED "${DBZ1_ROOT}/generated")

#-----------------------------------------------------------------------------
# Verificacoes antes de comecar (mensagens claras em vez de erros obscuros)
#-----------------------------------------------------------------------------
if(NOT EXISTS "${REXSDK_ROOT}/CMakeLists.txt")
    message(FATAL_ERROR "SDK ReXGlue nao encontrado em ${REXSDK_ROOT}")
endif()
if(NOT EXISTS "${REXSDK_ROOT}/include/rex/main_android.h")
    message(FATAL_ERROR
        "Os patches Android ainda nao foram aplicados ao SDK.\n"
        "Corre na raiz do repositorio:  bash tools/apply-sdk-patches.sh")
endif()
# Todos os patches de patches/ tem de estar aplicados (o script regista-os em
# .dbz3-android-patches). Evita compilar um SDK desatualizado sem dar por isso.
function(dbz3_check_patches PATCH_GLOB TARGET_DIR)
    file(GLOB PATCH_FILES "${PATCH_GLOB}")
    set(STAMP "${TARGET_DIR}/.dbz3-android-patches")
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS ${PATCH_FILES} "${STAMP}")
    set(APPLIED "")
    if(EXISTS "${STAMP}")
        file(STRINGS "${STAMP}" APPLIED)
    endif()
    foreach(PATCH ${PATCH_FILES})
        get_filename_component(PATCH_NAME "${PATCH}" NAME)
        if(NOT PATCH_NAME IN_LIST APPLIED)
            message(FATAL_ERROR
                "O patch ${PATCH_NAME} ainda nao foi aplicado em ${TARGET_DIR}.\n"
                "Corre na raiz do repositorio:  bash tools/apply-sdk-patches.sh")
        endif()
    endforeach()
endfunction()
dbz3_check_patches("${DBZ3_REPO_ROOT}/patches/[0-9][0-9][0-9][0-9]-*.patch" "${REXSDK_ROOT}")
dbz3_check_patches("${DBZ3_REPO_ROOT}/patches/game/[0-9][0-9][0-9][0-9]-*.patch" "${DBZ3_ROOT}")
if(NOT EXISTS "${DBZ3_GENERATED}/sources.cmake")
    message(FATAL_ERROR
        "Falta ${DBZ3_GENERATED}/sources.cmake.\n"
        "Corre o codegen do rexglue em external/dbz3 (ver docs/ESTADO.md).")
endif()
if(NOT EXISTS "${DBZ1_GENERATED}/sources.cmake")
    message(FATAL_ERROR
        "Falta ${DBZ1_GENERATED}/sources.cmake (Budokai 1).\n"
        "Corre na raiz do repositorio:  bash tools/codegen-b1.sh /caminho/para/yae1_xenon.xex")
endif()

#-----------------------------------------------------------------------------
# SDK ReXGlue
#-----------------------------------------------------------------------------
# O SDK fixa a pasta de saida em out/<plataforma>; aqui apontamo-la para a
# pasta que o Gradle espera, para as .so entrarem no APK.
set(REXGLUE_OUTPUT_DIR "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}" CACHE STRING "" FORCE)
set(REXGLUE_USE_VULKAN ON CACHE BOOL "" FORCE)
set(REXGLUE_ENABLE_TRACY OFF CACHE BOOL "" FORCE)
set(REXGLUE_BUILD_TESTS OFF CACHE BOOL "" FORCE)
# ARMv8.2-A tambem no SDK (runtime, plugin da GPU, terceiros), nao so no jogo: os
# atomicos LSE em linha substituem as chamadas __aarch64_cas*/ldadd*, que no port do
# Raging Blast 2 (RB2-Android) custavam varios % das threads do command processor e
# da UI. Todos os alvos (865, T820, 8 Gen x) sao ARMv8.2.
string(APPEND CMAKE_C_FLAGS " -march=armv8.2-a")
string(APPEND CMAKE_CXX_FLAGS " -march=armv8.2-a")
add_subdirectory("${REXSDK_ROOT}" rexglue-sdk)

#-----------------------------------------------------------------------------
# libadrenotools: permite carregar um driver Vulkan alternativo (Turnip/Mesa)
# em vez do driver Qualcomm do sistema. So e usado se a app definir
# REX_VULKAN_DRIVER_DIR/REX_VULKAN_DRIVER_NAME (ver MainActivity e o patch 0009).
#-----------------------------------------------------------------------------
include(FetchContent)
if(NOT CMAKE_ANDROID_ARCH_ABI)
    set(CMAKE_ANDROID_ARCH_ABI "${ANDROID_ABI}")
endif()
FetchContent_Declare(adrenotools
    GIT_REPOSITORY https://github.com/bylaws/libadrenotools.git
    GIT_TAG 8fae8ce254dfc1344527e05301e43f37dea2df80
    GIT_SUBMODULES_RECURSE TRUE
)
FetchContent_MakeAvailable(adrenotools)
# vulkan_instance.cpp (no rexui) usa a API; o rexruntime e quem liga a biblioteca.
target_link_libraries(rexui PRIVATE adrenotools)
target_compile_definitions(rexui PRIVATE REX_HAS_ADRENOTOOLS=1)
target_link_libraries(rexruntime PRIVATE adrenotools)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

#-----------------------------------------------------------------------------
# Jogo (mesma lista de fontes do CMakeLists.txt do projeto original v1.5.5.5,
# build US): inclui Tag Team, online (src/net), campanhas, escenarios de mods e
# os ecras proprios do jogo. Desde a v1.4.4.5 o main.cpp e o roster_ext.cpp
# chamam o Tag Team e os paineis diretamente, por isso ja nao sao opcionais.
#-----------------------------------------------------------------------------
# O external/dbz3 tem de estar na v1.5.5.5 ou mais nova (hub.cpp e o resto das fontes
# novas). Os patches de patches/game/ sao escritos contra essa versao.
if(NOT EXISTS "${DBZ3_ROOT}/src/datos.cpp")
    message(FATAL_ERROR
        "external/dbz3 desatualizado (falta src/datos.cpp, v1.5.5.5).\n"
        "Atualiza-o para origin/master do projeto original (ver docs/ESTADO.md,\n"
        "'Atualizar o external/dbz3') e corre bash tools/apply-sdk-patches.sh.")
endif()
# src/escenarios_sel_gen.inc sai do codigo gerado (US) e nao vem no repositorio publico.
if(NOT EXISTS "${DBZ3_ROOT}/src/escenarios_sel_gen.inc")
    find_package(Python3 REQUIRED COMPONENTS Interpreter)
    execute_process(COMMAND "${Python3_EXECUTABLE}" "${DBZ3_ROOT}/tools/gen_escenarios_sel.py" RESULT_VARIABLE DBZ3_ESC_RC)
    if(NOT DBZ3_ESC_RC EQUAL 0)
        message(FATAL_ERROR "tools/gen_escenarios_sel.py falhou (precisa do generated/ da US)")
    endif()
endif()
set(DBZ3_SOURCES
    ${DBZ3_ROOT}/src/main.cpp
    ${DBZ3_ROOT}/src/hooks.cpp
    ${DBZ3_ROOT}/src/roster_trace.cpp
    ${DBZ3_ROOT}/src/roster_ext.cpp
    ${DBZ3_ROOT}/src/select_ext.cpp
    ${DBZ3_ROOT}/src/stage_ext.cpp
    ${DBZ3_ROOT}/src/mods.cpp
    ${DBZ3_ROOT}/src/mods_opcionales.cpp
    ${DBZ3_ROOT}/src/native_mods.cpp
    ${DBZ3_ROOT}/src/region.cpp
    ${DBZ3_ROOT}/src/launcher/settings.cpp
    ${DBZ3_ROOT}/src/launcher/launcher_state.cpp
    ${DBZ3_ROOT}/src/launcher/ui_kit.cpp
    ${DBZ3_ROOT}/src/launcher/mod_pipeline.cpp
    ${DBZ3_ROOT}/src/launcher/i18n.cpp
    ${DBZ3_ROOT}/src/launcher/update_check.cpp
    ${DBZ3_ROOT}/src/launcher/novas_mods.cpp
    ${DBZ3_ROOT}/src/ingame/menu.cpp
    ${DBZ3_ROOT}/src/ingame/quick_settings.cpp
    ${DBZ3_ROOT}/src/ingame/game_style.cpp
    ${DBZ3_ROOT}/src/ingame/native_ui.cpp
    ${DBZ3_ROOT}/src/ingame/menu_pilot.cpp
    ${DBZ3_ROOT}/src/ingame/online_panel.cpp
    ${DBZ3_ROOT}/src/ingame/tagteam_panel.cpp
    ${DBZ3_ROOT}/src/ingame/color_panel.cpp
    ${DBZ3_ROOT}/src/ingame/tutorial.cpp
    ${DBZ3_ROOT}/src/net/netplay.cpp
    ${DBZ3_ROOT}/src/net/room.cpp
    ${DBZ3_ROOT}/src/net/netplay_hooks.cpp
    ${DBZ3_ROOT}/src/tagteam.cpp
    ${DBZ3_ROOT}/src/tagteam_slots.cpp
    ${DBZ3_ROOT}/src/campaigns.cpp
    ${DBZ3_ROOT}/src/hub.cpp
    ${DBZ3_ROOT}/src/escenarios.cpp
    ${DBZ3_ROOT}/src/clima.cpp
    ${DBZ3_ROOT}/src/cine.cpp
    ${DBZ3_ROOT}/src/gigantes.cpp
    ${DBZ3_ROOT}/src/pasarela.cpp
    ${DBZ3_ROOT}/src/pasarela_b1.cpp
    ${DBZ3_ROOT}/src/pasarela_misiones.cpp
    ${DBZ3_ROOT}/src/hub_menu.cpp
    ${DBZ3_ROOT}/src/ingame/hub_panel.cpp
    ${DBZ3_ROOT}/src/modos.cpp
    ${DBZ3_ROOT}/src/rpg.cpp
    ${DBZ3_ROOT}/src/heroe.cpp
    ${DBZ3_ROOT}/src/heroe_ui.cpp
    ${DBZ3_ROOT}/src/cac/cac.cpp
    ${DBZ3_ROOT}/src/fusion.cpp
    ${DBZ3_ROOT}/src/pasarela_iw.cpp
    ${DBZ3_ROOT}/src/pasarela_b2.cpp
    ${DBZ3_ROOT}/src/pasarela_nave.cpp
    ${DBZ3_ROOT}/src/iw_minijuegos.cpp
    ${DBZ3_ROOT}/src/cinematica.cpp
    ${DBZ3_ROOT}/src/freecam.cpp
    ${DBZ3_ROOT}/src/ports_guard.cpp
    ${DBZ3_ROOT}/src/ports_regs_luchadores.cpp
    ${DBZ3_ROOT}/src/combate.cpp
    ${DBZ3_ROOT}/src/transformaciones.cpp
    ${DBZ3_ROOT}/src/video_sfd.cpp
    ${DBZ3_ROOT}/src/anim_iw.cpp
    ${DBZ3_ROOT}/src/suelo_iw.cpp
    ${DBZ3_ROOT}/src/ps2_modelo.cpp
    ${DBZ3_ROOT}/src/iw_mini_botones.cpp
    ${DBZ3_ROOT}/src/iw_mini_vuelo.cpp
    ${DBZ3_ROOT}/src/iw_mini_disparo.cpp
    ${DBZ3_ROOT}/src/iw_mini_extra.cpp
    ${DBZ3_ROOT}/src/datos.cpp
)

# -Pdf.fullSources / DBZ3_FULL_SOURCES ficou sem efeito (as fontes acima sao
# sempre compiladas). Lido aqui so para o CMake nao avisar "variavel nao usada".
if(DEFINED DBZ3_FULL_SOURCES AND NOT DBZ3_FULL_SOURCES)
    message(STATUS "DBZ3_FULL_SOURCES=OFF ignorado: Tag Team e online sao sempre compilados")
endif()

# Codigo recompilado do yae3_xenon.xex (define GENERATED_SOURCES).
include("${DBZ3_GENERATED}/sources.cmake")

# Opcoes do codigo recompilado:
#  - so tabelas de linhas no debug info (os .o ficam pequenos e o link rapido,
#    mas os crashes no logcat continuam a mostrar ficheiro:linha);
#  - sem contracao de FMA: em ARM64 o clang funde a*b+c por omissao e isso
#    mudaria os resultados de virgula flutuante em relacao ao PowerPC.
set_source_files_properties(${GENERATED_SOURCES} PROPERTIES
    COMPILE_OPTIONS "-gline-tables-only;-ffp-contract=off;-fno-strict-aliasing")

add_library(game_b3 SHARED ${DBZ3_SOURCES} ${GENERATED_SOURCES})
set(DBZ3_GENERATED_SOURCES ${GENERATED_SOURCES})

# Hooks especificos do port Android (desempenho). Ver android_hooks.cpp.
target_sources(game_b3 PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/android_hooks.cpp"
    # Estado do combate para os controles por toque (JNI, ver NativeGameState.kt).
    "${CMAKE_CURRENT_LIST_DIR}/game_state_jni.cpp"
)

# Rastreio das funcoes D3D candidatas (tools/d3d_map.py + tools/d3d_trace_gen.py).
option(DBZ3_D3D_TRACE "Hooks de rastreio da biblioteca D3D (logs/d3d_trace.log)" OFF)
if(DBZ3_D3D_TRACE)
    if(EXISTS "${CMAKE_CURRENT_LIST_DIR}/d3d_trace_hooks.cpp")
        target_sources(game_b3 PRIVATE "${CMAKE_CURRENT_LIST_DIR}/d3d_trace_hooks.cpp")
    else()
        message(FATAL_ERROR "DBZ3_D3D_TRACE ligado mas falta d3d_trace_hooks.cpp: corre "
            "python3 tools/d3d_map.py && python3 tools/d3d_trace_gen.py")
    endif()
endif()

# O imgui.h vem com o SDK instalado no PC; aqui apontamos para o submodulo.
# (O codigo do ImGui em si ja esta dentro do librexruntime.so.)
target_include_directories(game_b3 PRIVATE
    "${DBZ3_ROOT}"
    "${DBZ3_GENERATED}"
    "${REXSDK_ROOT}/thirdparty/imgui"
    # rex/hash.h (usado pelos ecras do jogo da v1.4.5.1, ex. native_ui.cpp) inclui <xxhash.h>.
    "${REXSDK_ROOT}/thirdparty/xxHash"
)

target_compile_options(game_b3 PRIVATE
    -fno-strict-aliasing
    -ffp-contract=off
    -fno-char8_t
)

target_link_libraries(game_b3 PRIVATE
    rex::runtime
    rex::gpu-xenos
    android
    log
)

# Acrescenta windowed_app_main_sdl.cpp (com o SDL_main do Android) e
# rex_app.cpp, e as opcoes de plataforma do SDK.
rexglue_configure_target(game_b3 GPU_PLUGINS xenos)

# Alvo minimo: ARMv8.2-A (Snapdragon 865 / Cortex-A77 e A55 para cima, o que
# inclui o Unisoc T820). Ganha atomicos LSE em linha (usados pelas reservas
# lwarx/stwcx do PowerPC) e instrucoes que o armv8-a base nao tem. Fica depois
# do rexglue_configure_target, que poe -march=armv8-a, para prevalecer.
target_compile_options(game_b3 PRIVATE -march=armv8.2-a)
target_compile_definitions(game_b3 PRIVATE DF_GAME_B3=1)
# Chamadas entre funcoes do proprio libgame sem PLT (do Zandatsu/Dragon Burst): os hooks fortes continuam a ganhar as
# definicoes fracas sub_X no link.
target_link_options(game_b3 PRIVATE -Wl,-Bsymbolic-functions)

#-----------------------------------------------------------------------------
# Budokai 1 (yae1_xenon.xex): mesmo motor, sem port de PC. Codigo recompilado + a app minima de b1/ + o mesmo glue
# Android (android_hooks.cpp e game_state_jni.cpp com os enderecos do Budokai 1).
#-----------------------------------------------------------------------------
include("${DBZ1_GENERATED}/sources.cmake")
set(DBZ1_GENERATED_SOURCES ${GENERATED_SOURCES})
set_source_files_properties(${DBZ1_GENERATED_SOURCES} PROPERTIES
    COMPILE_OPTIONS "-gline-tables-only;-ffp-contract=off;-fno-strict-aliasing")
add_library(game_b1 SHARED
    "${CMAKE_CURRENT_LIST_DIR}/b1/dbz1_app.cpp"
    "${CMAKE_CURRENT_LIST_DIR}/android_hooks.cpp"
    "${CMAKE_CURRENT_LIST_DIR}/game_state_jni.cpp"
    ${DBZ1_GENERATED_SOURCES})
target_compile_definitions(game_b1 PRIVATE DF_GAME_B1=1)
target_include_directories(game_b1 PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}"
    "${DBZ1_ROOT}"
    "${DBZ1_GENERATED}"
    "${REXSDK_ROOT}/thirdparty/imgui"
    "${REXSDK_ROOT}/thirdparty/xxHash")
target_compile_options(game_b1 PRIVATE -fno-strict-aliasing -ffp-contract=off -fno-char8_t)
target_link_libraries(game_b1 PRIVATE rex::runtime rex::gpu-xenos android log)
rexglue_configure_target(game_b1 GPU_PLUGINS xenos)
target_compile_options(game_b1 PRIVATE -march=armv8.2-a)
target_link_options(game_b1 PRIVATE -Wl,-Bsymbolic-functions)

#-----------------------------------------------------------------------------
# Renut: renderizador nativo de Direct3D do mgr-nx-deko (do Dragon Burst / Zandatsu), escolhido no arranque com
# swordman_renderer = renut. Shaders: tools/renut-shaders.sh. Mapa de hooks: docs/renut/HOOKMAP_BUDOKAI.md.
#-----------------------------------------------------------------------------
option(DF_RENUT "Compila o Renut (swordman_renderer = renut)" ON)
if(DF_RENUT)
    include("${DBZ3_REPO_ROOT}/cmake/renut.cmake")
    df_renut_setup(game_b3 b3)
    df_renut_setup(game_b1 b1)
endif()

#-----------------------------------------------------------------------------
# Os libgame fora do APK (do Zandatsu / Dragon Burst). O codigo de cada jogo so pode estar no aparelho de quem tem o
# disco: o APK leva cada libgame encriptado (AES-256-GCM, chave = SHA-256 da imagem PE do executavel desse jogo) e o
# libmain.so, que no import le o executavel do jogador (loader/xex_image.cpp) para o GameLib.kt derivar a chave,
# desencriptar para getFilesDir() e o GameActivity carregar de la.
#-----------------------------------------------------------------------------
set(DF_XEX_B3 "${DBZ3_ROOT}/yae3_xenon.xex" CACHE FILEPATH "yae3_xenon.xex do codegen (chave do libgame_b3.so)")
set(DF_XEX_B1 "${DBZ1_ROOT}/yae1_xenon.xex" CACHE FILEPATH "yae1_xenon.xex do codegen (chave do libgame_b1.so)")
set(DF_PACK_DIR "${DBZ3_REPO_ROOT}/app/build/dragonfury-assets")
foreach(G b3 b1)
    string(TOUPPER "${G}" GU)
    add_custom_command(
        OUTPUT "${DF_PACK_DIR}/libgame_${G}.zdat" "${DF_PACK_DIR}/libgame_${G}.json"
        COMMAND python3 "${DBZ3_REPO_ROOT}/tools/pack-libgame.py" "$<TARGET_FILE:game_${G}>" "${CMAKE_STRIP}"
                "${DF_XEX_${GU}}" "${DF_PACK_DIR}" "libgame_${G}"
        DEPENDS game_${G} "${DBZ3_REPO_ROOT}/tools/pack-libgame.py" "${CMAKE_CURRENT_LIST_DIR}/loader/xex_image.cpp"
                "${DF_XEX_${GU}}"
        COMMENT "libgame_${G}.so -> assets/libgame_${G}.zdat (encriptado)"
        VERBATIM)
endforeach()
add_custom_target(game_pack DEPENDS
    "${DF_PACK_DIR}/libgame_b3.zdat" "${DF_PACK_DIR}/libgame_b3.json"
    "${DF_PACK_DIR}/libgame_b1.zdat" "${DF_PACK_DIR}/libgame_b1.json")

add_library(main SHARED "${CMAKE_CURRENT_LIST_DIR}/loader/game_lib_jni.cpp" "${CMAKE_CURRENT_LIST_DIR}/loader/xex_image.cpp")
target_include_directories(main PRIVATE "${CMAKE_CURRENT_LIST_DIR}/loader" "${REXSDK_ROOT}/thirdparty")
target_link_libraries(main PRIVATE mspack log)
target_compile_options(main PRIVATE -march=armv8.2-a)
# O Gradle so compila os alvos da lista (build.gradle.kts): os libgame e os pacotes vem por aqui.
add_dependencies(main game_pack)
