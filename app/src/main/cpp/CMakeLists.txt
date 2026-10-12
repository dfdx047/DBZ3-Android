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

# Hooks especificos do port Android (desempenho). Ver android_hooks.cpp.
target_sources(main PRIVATE "${CMAKE_CURRENT_LIST_DIR}/android_hooks.cpp")

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

# Alvo minimo: ARMv8.2-A (Snapdragon 865 / Cortex-A77 e A55 para cima, o que
# inclui o Unisoc T820). Ganha atomicos LSE em linha (usados pelas reservas
# lwarx/stwcx do PowerPC) e instrucoes que o armv8-a base nao tem. Fica depois
# do rexglue_configure_target, que poe -march=armv8-a, para prevalecer.
target_compile_options(main PRIVATE -march=armv8.2-a)
