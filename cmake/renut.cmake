# Renut: o renderizador nativo de Direct3D do mgr-nx-deko (reNut + plume, Vulkan), escolhido no arranque com
# swordman_renderer = renut. Vem do Dragon Burst (cmake/renut.cmake de la, que vem do Zandatsu): os patches dele
# (patches/renut/0001-0026 do Zandatsu, 0100-0119 do Burst Limit) e por cima o 0200 dos Budokai HD (layout do device do
# MGR e os enderecos de cada jogo, budokai_hook_map.h). Mapa de hooks: docs/renut/HOOKMAP_BUDOKAI.md.
#
# Os dois jogos tem enderecos e shaders diferentes: o que depende deles (os hooks de d3d/hooks e os shaders traduzidos)
# e compilado uma vez por jogo (renut_gpu_b3, renut_gpu_b1, com RENUT_GAME_B3 / RENUT_GAME_B1); o resto uma so vez
# (renut_gpu_common), e cada libgame liga os dois.
#
# Do Dragon Burst, sem o que nao serve no Android:
#  - sem XenosRecomp, dump de shaders nem DXC aqui: tudo isso corre no PC (tools/renut-shaders.sh) e este ficheiro so
#    compila out/renut/<jogo>/shader_cache.cpp e inclui out/renut/host_shaders/*.spirv.h;
#  - sem deko3d, NVK, uam, SPIRV-Cross, LTO (Switch); sem X11; sem a copia propria do ImGui (usa a do SDK).
#
# Bibliotecas OBJECT, de proposito: os hooks substituem as definicoes fracas sub_X do codigo gerado, e os membros de
# um arquivo estatico so sao puxados por simbolos em falta (os hooks nunca entrariam).
#
# Usa: DBZ3_REPO_ROOT, REXSDK_ROOT (app/src/main/cpp/CMakeLists.txt). Define: df_renut_setup(<alvo> <b3|b1>).

set(SM_ROOT        "${DBZ3_REPO_ROOT}")
set(SM_GLUE_DIR    "${DBZ3_REPO_ROOT}/app/src/main/cpp/renut")
set(SM_MGRNX       "${SM_ROOT}/external/mgr-nx-deko")
set(RENUT_APP_DIR  "${SM_MGRNX}/app")
set(RENUT_GPU_DIR  "${RENUT_APP_DIR}/src/renut_engine/gpu")
set(RENUT_XR_DIR   "${RENUT_APP_DIR}/thirdparty/reblue-XenosRecomp")
set(SM_RENUT_OUT   "${SM_ROOT}/out/renut")

if(NOT EXISTS "${RENUT_GPU_DIR}/gpu.cmake")
    message(FATAL_ERROR "Falta ${SM_MGRNX}. Corre: bash tools/setup-renut.sh")
endif()
if(NOT EXISTS "${SM_RENUT_OUT}/b3/shader_cache.cpp" OR NOT EXISTS "${SM_RENUT_OUT}/b1/shader_cache.cpp"
   OR NOT EXISTS "${SM_RENUT_OUT}/host_shaders/present_ps.spirv.h")
    message(FATAL_ERROR "Faltam os shaders do Renut em ${SM_RENUT_OUT}. Corre: bash tools/renut-shaders.sh")
endif()
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${SM_RENUT_OUT}/.stamp")

# As alteracoes Android ao submodulo (patches/renut) tem de estar aplicadas.
file(GLOB SM_RENUT_PATCHES "${SM_ROOT}/patches/renut/[0-9][0-9][0-9][0-9]-*.patch")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS ${SM_RENUT_PATCHES})
execute_process(COMMAND bash "${SM_ROOT}/tools/apply-renut-patches.sh" --check
                RESULT_VARIABLE SM_RENUT_PATCH_MISSING ERROR_VARIABLE SM_RENUT_PATCH_ERROR)
if(SM_RENUT_PATCH_MISSING)
    message(FATAL_ERROR "Patches do Renut por aplicar: ${SM_RENUT_PATCH_ERROR}")
endif()

# plume (Vulkan; o ramo Android ja existe nele: VK_KHR_android_surface).
if(NOT TARGET plume)
    add_subdirectory("${RENUT_APP_DIR}/thirdparty/plume" "${CMAKE_BINARY_DIR}/plume")
endif()

# Descompressao do arquivo de shaders: zstd (so o descompressor), smol-v, xxHash, das copias do XenosRecomp.
set(RENUT_ZSTD_LIB_DIR "${RENUT_XR_DIR}/thirdparty/zstd/lib")
file(GLOB renut_zstd_sources "${RENUT_ZSTD_LIB_DIR}/common/*.c" "${RENUT_ZSTD_LIB_DIR}/decompress/*.c")
add_library(renut_zstd STATIC ${renut_zstd_sources})
target_include_directories(renut_zstd PUBLIC "${RENUT_ZSTD_LIB_DIR}")
target_compile_definitions(renut_zstd PRIVATE ZSTD_DISABLE_ASM)

set(RENUT_XXHASH_DIR "${RENUT_XR_DIR}/thirdparty/xxHash")
add_library(renut_xxhash STATIC "${RENUT_XXHASH_DIR}/xxhash.c")
target_include_directories(renut_xxhash PUBLIC "${RENUT_XXHASH_DIR}")

set(RENUT_SMOLV_DIR "${RENUT_XR_DIR}/thirdparty/smol-v/source")
add_library(renut_smolv STATIC "${RENUT_SMOLV_DIR}/smolv.cpp")
target_include_directories(renut_smolv PUBLIC "${RENUT_SMOLV_DIR}")

set_target_properties(renut_zstd renut_xxhash renut_smolv PROPERTIES POSITION_INDEPENDENT_CODE ON)

# O o1heap do SDK chama-se o1heap aqui; o Renut procura rex::o1heap.
if(TARGET o1heap AND NOT TARGET rex::o1heap)
    add_library(rex::o1heap ALIAS o1heap)
endif()

set(RENUT_GPU_SOURCES
    # O dispositivo, o frame e a thread de render.
    "${RENUT_GPU_DIR}/backend/backend.cpp"
    "${RENUT_GPU_DIR}/backend/gpu_profile.cpp"
    "${RENUT_GPU_DIR}/backend/memory_report.cpp"
    "${RENUT_GPU_DIR}/backend/native_window.cpp"
    "${RENUT_GPU_DIR}/backend/present.cpp"
    "${RENUT_GPU_DIR}/backend/queued.cpp"
    "${RENUT_GPU_DIR}/backend/render_thread.cpp"
    "${RENUT_GPU_DIR}/backend/video.cpp"
    # O Direct3D do jogo, nativo, e os seus hooks (sem d3d/hooks/state.cpp, como no deles).
    "${RENUT_GPU_DIR}/d3d/d3d_native_state.cpp"
    "${RENUT_GPU_DIR}/guest/callbacks.cpp"
    "${RENUT_GPU_DIR}/guest/command_buffers.cpp"
    "${RENUT_GPU_DIR}/guest/guest_records.cpp"
    "${RENUT_GPU_DIR}/guest/sa2_screen.cpp"
    "${RENUT_GPU_DIR}/overlay/app_integration.cpp"
    "${RENUT_GPU_DIR}/overlay/overlay_drawer.cpp"
    "${RENUT_GPU_DIR}/overlay/overlay_ui.cpp"
    "${RENUT_GPU_DIR}/render/draw.cpp"
    "${RENUT_GPU_DIR}/render/format.cpp"
    "${RENUT_GPU_DIR}/render/graphics_options.cpp"
    "${RENUT_GPU_DIR}/render/pipeline.cpp"
    "${RENUT_GPU_DIR}/render/shader_library.cpp"
    "${RENUT_GPU_DIR}/render/surfaces.cpp"
    "${RENUT_GPU_DIR}/render/texture_layout.cpp"
    "${RENUT_GPU_DIR}/render/textures.cpp"
    "${RENUT_GPU_DIR}/render/upload_ring.cpp"
    "${RENUT_GPU_DIR}/render/vertex_format.cpp"
    # Os do mgr_renut_gpu.cmake: escolha, afinacao, verificacoes, ritmo dos frames.
    "${RENUT_GPU_DIR}/banjo_renut_select.cpp"
    "${RENUT_GPU_DIR}/banjo_renut_tuning.cpp"
    "${RENUT_GPU_DIR}/banjo_renut_checks.cpp"
    "${RENUT_GPU_DIR}/banjo_renut_record.cpp"
    "${RENUT_GPU_DIR}/sa2_renut_pacing.cpp"
    "${RENUT_APP_DIR}/src/renut_engine/display_sync.cpp"
    "${RENUT_APP_DIR}/src/renut_engine/perf/frame_stats.cpp"
    "${RENUT_APP_DIR}/src/renut_engine/sa2_compat/frame_latency.cpp"
    "${RENUT_APP_DIR}/src/renut_engine/sa2_compat/sa2_compat.cpp"
    # Marcadores de GPU por draw (swordman_renut_gpu_markers; patch renut/0014 chama-os).
    "${SM_GLUE_DIR}/renut_gpu_markers.cpp"
    # Dump de um frame (swordman_renut_frame_dump; patch renut/0015 chama-o).
    "${SM_GLUE_DIR}/renut_frame_dump.cpp"
    # Sombras e brilho do MGR (swordman_renut_shadows / _bloom; patch renut/0016 chama-o). Escolhe os passes pelos
    # shaders do MGR: no Burst Limit nao reconhece nenhum e fica tudo como o jogo desenha.
    "${SM_GLUE_DIR}/renut_effects.cpp"
    # Fora: mgr_widescreen.cpp (hook do MGR; sem ele a largura fica a 1280) e mgr_audio_trace.cpp (Wwise do MGR).
    )

foreach(G common b3 b1)
string(TOUPPER "${G}" GU)
if(G STREQUAL "common")
    add_library(renut_gpu_common OBJECT ${RENUT_GPU_SOURCES})
else()
    # Os hooks com os enderecos do jogo e os shaders dele traduzidos no PC (dados do jogo: so no libgame encriptado).
    add_library(renut_gpu_${G} OBJECT
        "${RENUT_GPU_DIR}/d3d/hooks/device.cpp"
        "${RENUT_GPU_DIR}/d3d/hooks/draw.cpp"
        "${RENUT_GPU_DIR}/d3d/hooks/resource.cpp"
        "${RENUT_GPU_DIR}/d3d/hooks/shader.cpp"
        "${SM_RENUT_OUT}/${G}/shader_cache.cpp")
    target_compile_definitions(renut_gpu_${G} PRIVATE RENUT_GAME_${GU}=1 DF_GAME_${GU}=1)
endif()
target_include_directories(renut_gpu_${G} BEFORE PRIVATE
    # O xxhash.h do XenosRecomp, a frente do do SDK (outra versao).
    "${RENUT_XXHASH_DIR}")
target_include_directories(renut_gpu_${G} PRIVATE
    "${RENUT_APP_DIR}"
    "${RENUT_APP_DIR}/src"
    "${RENUT_APP_DIR}/src/renut_engine/sa2_compat"
    "${RENUT_GPU_DIR}/shaders"
    "${RENUT_XR_DIR}/XenosRecomp"
    # host_shaders/<nome>.spirv.h
    "${SM_RENUT_OUT}"
    "${REXSDK_ROOT}/src/core"
    # renut_gpu_markers.h e companhia, incluidos pelos patches renut/0014-0016.
    "${SM_GLUE_DIR}"
    "${REXSDK_ROOT}/thirdparty/imgui"
    "${CMAKE_BINARY_DIR}/rexglue-sdk/include")
target_compile_definitions(renut_gpu_${G} PRIVATE MGR_RENUT_RENDERER=1 SA2_RENUT_RENDERER=1 RENUT_RECOMP)
target_compile_options(renut_gpu_${G} PRIVATE -march=armv8.2-a -ffp-contract=off -fno-strict-aliasing)
target_link_libraries(renut_gpu_${G} PRIVATE rex::runtime plume renut_zstd renut_smolv renut_xxhash rex::o1heap)
endforeach()
set_source_files_properties("${SM_RENUT_OUT}/b3/shader_cache.cpp" "${SM_RENUT_OUT}/b1/shader_cache.cpp"
    PROPERTIES SKIP_PRECOMPILE_HEADERS ON)

# Liga o Renut do jogo <G> (b3 ou b1) ao libgame (<alvo>) e diz ao codigo do jogo que ele existe (DF_RENUT).
function(df_renut_setup TARGET G)
    string(TOUPPER "${G}" GU)
    # adrenotools: o driver Vulkan do plume (renut_glue.cpp, plume_android_vulkan_library).
    target_link_libraries(${TARGET} PRIVATE renut_gpu_common renut_gpu_${G} adrenotools)
    target_compile_definitions(${TARGET} PRIVATE DF_RENUT=1 RENUT_GAME_${GU}=1 MGR_RENUT_RENDERER=1 SA2_RENUT_RENDERER=1)
    target_include_directories(${TARGET} PRIVATE "${RENUT_APP_DIR}" "${RENUT_APP_DIR}/src" "${SM_GLUE_DIR}")
    target_sources(${TARGET} PRIVATE "${SM_GLUE_DIR}/renut_glue.cpp")
endfunction()
