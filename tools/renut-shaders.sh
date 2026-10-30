#!/usr/bin/env bash
# Shaders do Renut (renderizador nativo do mgr-nx-deko), gerados no PC a partir dos executaveis do jogador:
#
#   yae3_xenon.xex / yae1_xenon.xex (tools/renut/extract_budokai_shaders.py: 108 contentores Xenos diferentes cada, no
#   .rdata; as .afs do disco nao tem nenhum) -> XenosRecomp (RENUT_RECOMP) + DXC Linux
#     -> out/renut/<b3|b1>/shader_cache.cpp   (os shaders de cada jogo em SPIR-V: dados do jogo, nunca no git)
#   shaders do proprio Renut (gpu/shaders/host/*.hlsl) -> DXC
#     -> out/renut/host_shaders/<nome>.spirv.h
#
# O cmake/renut.cmake compila estes ficheiros nos libgame (encriptados no APK). As ferramentas (XenosRecomp) sao
# compiladas com o compilador do PC em out/host/build (tools/renut-host/CMakeLists.txt), nunca com o NDK. O DXC vem
# do release Linux oficial (fixo abaixo) para out/host/dxc.
#
# So refaz o trabalho quando muda alguma entrada (carimbo em out/renut/.stamp): os dois xex, o commit do mgr-nx-deko,
# os patches de patches/renut, este script, o extrator ou a versao do DXC.
# Os shaders que o jogo montar em tempo de execucao o Renut captura no aparelho em files/shader_capture/; copiados para
# out/renut/captured/<b3|b1>/ entram aqui tambem.
#
# Uso (a partir da raiz do repositorio):
#   bash tools/renut-shaders.sh            # so se mudou alguma coisa
#   bash tools/renut-shaders.sh --force    # sempre
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MGRNX="external/mgr-nx-deko"
XR="$MGRNX/app/thirdparty/reblue-XenosRecomp"
HOST_HLSL="$MGRNX/app/src/renut_engine/gpu/shaders/host"
XEX_b3="${DF_XEX_B3:-external/dbz3/yae3_xenon.xex}"
XEX_b1="${DF_XEX_B1:-external/dbz1/yae1_xenon.xex}"
CAPTURED="out/renut/captured"
OUT="out/renut"
HOST="out/host"

DXC_TAG="v1.9.2609"
DXC_TGZ="linux_dxc_2026_09_28.x86_x64.tar.gz"
DXC_SHA256="96faadc7f5c282d2ffda49804beb4c3ee38127bc252b723234e3c5cdf7aa39a1"

for X in "$XEX_b3" "$XEX_b1"; do
  if [ ! -f "$X" ]; then
    echo "ERRO: falta $X (o executavel do jogo, do disco; DF_XEX_B3 / DF_XEX_B1 = o caminho dele)." >&2
    exit 1
  fi
done
if [ ! -f "$XR/XenosRecomp/main.cpp" ]; then
  echo "ERRO: $MGRNX vazio. Corre: bash tools/setup-external.sh" >&2
  exit 1
fi

STAMP="$( {
  sha1sum "$XEX_b3" "$XEX_b1"
  cat "$CAPTURED"/*/*.bin 2>/dev/null || true
  git -C "$MGRNX" rev-parse HEAD
  cat patches/renut/*.patch 2>/dev/null || true
  cat "$0" tools/renut-host/CMakeLists.txt tools/renut/extract_budokai_shaders.py
  # As fontes como estao na arvore (patches ainda por gerar incluidos): o XenosRecomp e os shaders do host.
  cat "$XR"/XenosRecomp/*.cpp "$XR"/XenosRecomp/*.h "$HOST_HLSL"/*.hlsl* 2>/dev/null || true
} | sha1sum | cut -d' ' -f1)"
if [ "${1:-}" != "--force" ] && [ -f "$OUT/b3/shader_cache.cpp" ] && [ -f "$OUT/b1/shader_cache.cpp" ] && [ "$(cat "$OUT/.stamp" 2>/dev/null)" = "$STAMP" ]; then
  echo "shaders do Renut: nada mudou, saltado"
  exit 0
fi

# DXC (release Linux oficial, fixo).
if [ ! -x "$HOST/dxc/bin/dxc" ]; then
  echo "DXC $DXC_TAG -> $HOST/dxc"
  mkdir -p "$HOST/dxc-dl" "$HOST/dxc"
  if [ ! -f "$HOST/dxc-dl/$DXC_TGZ" ]; then
    curl -fL -o "$HOST/dxc-dl/$DXC_TGZ" \
      "https://github.com/microsoft/DirectXShaderCompiler/releases/download/$DXC_TAG/$DXC_TGZ"
  fi
  echo "$DXC_SHA256  $HOST/dxc-dl/$DXC_TGZ" | sha256sum -c --quiet
  tar xzf "$HOST/dxc-dl/$DXC_TGZ" -C "$HOST/dxc"
fi
DXC="$ROOT/$HOST/dxc/bin/dxc"

# Ferramentas do PC.
GEN=()
command -v ninja > /dev/null && GEN=(-G Ninja)
CC_HOST="$(command -v clang || command -v gcc)"
CXX_HOST="$(command -v clang++ || command -v g++)"
cmake -S tools/renut-host -B "$HOST/build" "${GEN[@]}" -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER="$CC_HOST" -DCMAKE_CXX_COMPILER="$CXX_HOST" -DDXC_ROOT="$ROOT/$HOST/dxc" \
  -Wno-dev > "$HOST/configure.log" 2>&1 || { cat "$HOST/configure.log"; exit 1; }
cmake --build "$HOST/build" --target XenosRecomp > "$HOST/build.log" 2>&1 \
  || { tail -40 "$HOST/build.log"; exit 1; }

# 1-2. Por jogo: contentores do executavel (e os capturados no aparelho) e a traducao.
for G in b3 b1; do
  XEX_VAR="XEX_$G"
  rm -rf "$OUT/$G/containers"
  mkdir -p "$OUT/$G"
  python3 -I tools/renut/extract_budokai_shaders.py "$G" "${!XEX_VAR}" - "$OUT/$G/containers"
  if compgen -G "$CAPTURED/$G/*.bin" > /dev/null; then
    cp "$CAPTURED/$G"/*.bin "$OUT/$G/containers/"
    echo "  $(ls "$CAPTURED/$G"/*.bin | wc -l) capturados no aparelho ($CAPTURED/$G)"
  fi
  TMP="$OUT/$G/shader_cache.cpp.tmp"
  # Os Budokai ligam VS e PS por semantica (o D3D reescreve os exports do VS no bind); o MGR por registo.
  if ! RENUT_SEMANTIC_LINKAGE=1 "$HOST/build/xr/XenosRecomp" "$OUT/$G/containers" "$TMP" "$XR/XenosRecomp/shader_common.h" \
       > "$OUT/$G/xenosrecomp.log" 2>&1; then
    grep -v '^Recompiling shaders' "$OUT/$G/xenosrecomp.log" | tail -40
    exit 1
  fi
  mv "$TMP" "$OUT/$G/shader_cache.cpp"
  echo "  $G: $(grep -o 'g_shaderCacheEntryCount = [0-9]*' "$OUT/$G/shader_cache.cpp")"
done

# 3. Shaders do Renut (como o renut_host_shader do gpu.cmake deles: SPIR-V, layout D3D, Y invertido nos VS).
mkdir -p "$OUT/host_shaders"
for SP in overlay_vs:vs_6_0 overlay_ps:ps_6_0 present_vs:vs_6_0 present_ps:ps_6_0 \
          depth_resolve_ps:ps_6_0 depth_resolve_ms_ps:ps_6_0 texture_mip_ps:ps_6_0 resolve_copy_ps:ps_6_0 \
          rectangle_list_gs:gs_6_0 point_sprite_gs:gs_6_0 point_sprite_ubo_gs:gs_6_0 stream_convert_cs:cs_6_0 target_readback_cs:cs_6_0 bc_decode_cs:cs_6_0 \
          present_easu_ps:ps_6_0 present_rcas_ps:ps_6_0 present_cas_ps:ps_6_0 frame_interp_ps:ps_6_0; do
  S="${SP%%:*}"; P="${SP##*:}"
  EXTRA=()
  HV=2021
  [[ "$P" == vs* ]] && EXTRA=(-fvk-invert-y)
  # FSR 1 e CAS (renut/0019): os headers da AMD (third_party/fidelityfx, MIT) sao de antes do HLSL 2021 (ternario
  # com vetores).
  [[ "$S" == present_easu_ps || "$S" == present_rcas_ps || "$S" == present_cas_ps ]] &&
    HV=2018 && EXTRA+=(-I "$ROOT/third_party/fidelityfx")
  "$DXC" -T "$P" -E main -HV "$HV" -all-resources-bound -Wno-ignored-attributes -spirv -fvk-use-dx-layout \
    "${EXTRA[@]}" -Fh "$OUT/host_shaders/$S.spirv.h" -Vn "g_${S}_spirv" "$HOST_HLSL/$S.hlsl"
done
echo "  $(ls "$OUT/host_shaders" | wc -l) shaders do host"

echo "$STAMP" > "$OUT/.stamp"
echo "shaders do Renut prontos em $OUT"
