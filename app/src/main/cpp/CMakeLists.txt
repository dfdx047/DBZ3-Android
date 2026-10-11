# DBZ3-Android - nucleo nativo
#
# Compila o SDK ReXGlue (external/rexglue-sdk-0.10, com o overlay do projeto
# original e os patches Android) e o jogo recompilado (external/dbz3, com o
# generated/ do codegen) numa biblioteca libmain.so que o SDLActivity carrega.
#
# Resultado (todas em arm64-v8a, empacotadas no APK):
#   libmain.so          jogo recompilado + launcher/hooks do projeto original
#   librexruntime.so    runtime ReXGlue (kernel, memoria, audio, input, UI)
#   librexgpu-xenos.so  plugin da GPU Xenos (Vulkan)
#   libSDL3.so          SDL3 (janela, eventos, audio, comandos)

cmake_minimum_required(VERSION 3.25)
project(dbz3_android LANGUAGES C CXX)

get_filename_component(DBZ3_REPO_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../.." ABSOLUTE)
set(REXSDK_ROOT "${DBZ3_REPO_ROOT}/external/rexglue-sdk-0.10")
set(DBZ3_ROOT   "${DBZ3_REPO_ROOT}/external/dbz3")
set(DBZ3_GENERATED "${DBZ3_ROOT}/generated")

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
if(NOT EXISTS "${DBZ3_GENERATED}/sources.cmake")
    message(FATAL_ERROR
        "Falta ${DBZ3_GENERATED}/sources.cmake.\n"
        "Corre o codegen do rexglue em external/dbz3 (ver docs/ESTADO.md).")
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
add_subdirectory("${REXSDK_ROOT}" rexglue-sdk)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

#-----------------------------------------------------------------------------
# Jogo (mesma lista de fontes do CMakeLists.txt do projeto original, build US)
#-----------------------------------------------------------------------------
set(DBZ3_SOURCES
    ${DBZ3_ROOT}/src/main.cpp
    ${DBZ3_ROOT}/src/hooks.cpp
    ${DBZ3_ROOT}/src/roster_trace.cpp
    ${DBZ3_ROOT}/src/roster_ext.cpp
    ${DBZ3_ROOT}/src/select_ext.cpp
    ${DBZ3_ROOT}/src/mods.cpp
    ${DBZ3_ROOT}/src/native_mods.cpp
    ${DBZ3_ROOT}/src/region.cpp
    ${DBZ3_ROOT}/src/launcher/settings.cpp
    ${DBZ3_ROOT}/src/launcher/launcher_state.cpp
    ${DBZ3_ROOT}/src/launcher/ui_kit.cpp
    ${DBZ3_ROOT}/src/launcher/mod_pipeline.cpp
    ${DBZ3_ROOT}/src/launcher/i18n.cpp
    ${DBZ3_ROOT}/src/launcher/update_check.cpp
    ${DBZ3_ROOT}/src/ingame/menu.cpp
    ${DBZ3_ROOT}/src/ingame/quick_settings.cpp
)

# Codigo recompilado do yae3_xenon.xex (define GENERATED_SOURCES).
include("${DBZ3_GENERATED}/sources.cmake")

# Opcoes do codigo recompilado:
#  - so tabelas de linhas no debug info (os .o ficam pequenos e o link rapido,
#    mas os crashes no logcat continuam a mostrar ficheiro:linha);
#  - sem contracao de FMA: em ARM64 o clang funde a*b+c por omissao e isso
#    mudaria os resultados de virgula flutuante em relacao ao PowerPC.
set_source_files_properties(${GENERATED_SOURCES} PROPERTIES
    COMPILE_OPTIONS "-gline-tables-only;-ffp-contract=off;-fno-strict-aliasing")

add_library(main SHARED ${DBZ3_SOURCES} ${GENERATED_SOURCES})

# O imgui.h vem com o SDK instalado no PC; aqui apontamos para o submodulo.
# (O codigo do ImGui em si ja esta dentro do librexruntime.so.)
target_include_directories(main PRIVATE
    "${DBZ3_ROOT}"
    "${DBZ3_GENERATED}"
    "${REXSDK_ROOT}/thirdparty/imgui"
)

target_compile_options(main PRIVATE
    -fno-strict-aliasing
    -ffp-contract=off
    -fno-char8_t
)

target_link_libraries(main PRIVATE
    rex::runtime
    rex::gpu-xenos
    android
    log
)

# Acrescenta windowed_app_main_sdl.cpp (com o SDL_main do Android) e
# rex_app.cpp, e as opcoes de plataforma do SDK.
rexglue_configure_target(main GPU_PLUGINS xenos)
