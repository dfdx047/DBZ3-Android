#!/usr/bin/env python3
"""Mapa de hooks do Renut nos Budokai HD (B3 e B1): gera budokai_hook_map.h (patch renut/0200).

Para cada endereco: os 64 bytes da imagem original e o FNV-32 deles (como o mgr_hook_map.h do mgr-nx-deko), para o
Renut recusar um executavel diferente em vez de crashar. As imagens vem do out/host/xex-image (nunca no git).
Provas de cada endereco: docs/renut/HOOKMAP_BUDOKAI.md.

Uso: budokai_hook_map.py <imagem B3> <imagem B1>  (escreve o cabecalho no stdout)
"""
import struct, sys

# papel, MGR, B3, B1
HOOKS = [
    ('CREATE_DEVICE', 'Direct3D_CreateDevice', 0x826B2418, 0x822360E8, 0x821C5228),
    ('INIT_PRESENT_PARAMS', 'InitializePresentationParameters', 0x826C0C48, 0x82246688, 0x821D57C8),
    ('INIT_API_STATE', 'InitializeApiState', 0x826C0278, 0x82245CB8, 0x821D4DF8),
    ('INSERT_CALLBACK', 'D3DDevice_InsertCallback', 0x826B59B0, 0x82232818, 0x821C1958),
    ('SYNC_PRESENT', 'SynchronizeToPresentationInterval', 0x826B3050, 0x82238EC0, 0x821C8000),
    ('KICKOFF', 'KickOff', 0x826B5688, 0x822324F0, 0x821C1630),
    ('KICKOFF_SEGMENT', 'KickOffSegment', 0x826B5348, 0x822321B0, 0x821C12F0),
    ('BEGIN_RING_BIG', 'BeginRingBig', 0x826B58C0, 0x82232728, 0x821C1868),
    ('RING_ALLOCATE', 'RingBufferDeviceAllocate', 0x826B4078, 0x82230EE0, 0x821C0020),
    ('ALLOC_INLINE', 'AllocateInlineData', 0x826B44C0, 0x82231328, 0x821C0468),
    ('NEW_SEGMENT', 'NewSegment', 0x826B45A8, 0x82231410, 0x821C0550),
    ('SET_RING_PARAMS', 'SetRingBufferParameters', 0x826B5A08, 0x82232870, 0x821C19B0),
    ('SWAP', 'D3DDevice_Swap', 0x826B3510, 0x82239380, 0x821C84C0),
    ('BLOCK_ON_FENCE', 'BlockOnFence', 0x826B42A8, 0x82231110, 0x821C0250),
    ('CBLOCKER_CHECK', 'CBlocker_Check', 0x826B1C88, 0x82235918, 0x821C4A58),
    ('IS_FENCE_PENDING', 'IsFencePending', 0x826B57C8, 0x82232630, 0x821C1770),
    ('DRAW_INDEXED', 'DrawIndexedVertices', 0x826BCD40, 0x8223BF08, 0x821CB048),
    ('BEGIN_VERTICES', 'BeginVertices', 0x826BC468, 0x8223BA00, 0x821CAB40),
    ('WINDOW_SCISSOR', 'WindowScissor', 0x826B5FC8, 0x8222D4A8, 0x821BC5E8),
    ('CLEARF', 'ClearF', 0x826BC260, 0x8223AD40, 0x821C9E80),
    ('RESOLVE', 'Resolve', 0x826BB0D0, 0x8223DF08, 0x821CD048),
    ('REGISTER_VS', 'XGRegisterVertexShader', 0x826ACD00, 0x82233F98, 0x821C30D8),
    ('REGISTER_PS', 'XGRegisterPixelShader', 0x826AC3D8, 0x82233680, 0x821C27C0),
    ('CREATE_DECL', 'CreateVertexDeclaration', 0x826AC938, 0x82233BE0, 0x821C2D20),
    ('RESOURCE_DESTROY', 'D3DResource_Destroy', 0x826AAF80, 0x82234F80, 0x821C40C0),
    ('XMEMFREE', 'XMemFree', 0x82715A90, 0x820A27A8, 0x8209F0C0),
]
# Dados do jogo: qword global copiada para o device (+11008) e o slot de importacao do VdGlobalDevice.
DATA = [('GLOBAL_QWORD_VA', 0x834FB7E0, 0x82379750, 0x8230F890),
        ('VD_GLOBAL_DEVICE_SLOT', 0x820005F8, 0x82000534, 0x82000534)]


def fnv(img, va, n=64):
    h = 2166136261
    for b in img[va - 0x82000000:va - 0x82000000 + n]:
        h = ((h ^ b) * 16777619) & 0xFFFFFFFF
    return h


imgs = [open(p, 'rb').read() for p in sys.argv[1:3]]
out = ['// Gerado por tools/renut/budokai_hook_map.py a partir dos executaveis do jogador (nunca no git).',
       '// Renut nos Budokai HD (XDK 21119, o layout do device do MGR): os enderecos de cada jogo, escolhidos por',
       '// RENUT_GAME_B3 / RENUT_GAME_B1, e as guardas (64 bytes da imagem original, FNV-32). Provas:',
       '// docs/renut/HOOKMAP_BUDOKAI.md. Fora do mapa do MGR: DrawVertices (nao ligado nestes executaveis: so o',
       '// DrawIndexedVertices e o BeginVertices/DrawVerticesUP escrevem draws) e EngineSwapProgress (motor do MGR).',
       '#pragma once', '#include <cstdint>', '']
for gi, (macro, name) in enumerate((('RENUT_GAME_B3', 'Budokai 3 HD (yae3_xenon.xex)'), ('RENUT_GAME_B1', 'Budokai 1 HD (yae1_xenon.xex)'))):
    out.append(('#if' if gi == 0 else '#elif') + f' defined({macro})  // {name}')
    out.append(f'#define RENUT_GAME_NAME "{name}"')
    for key, role, mgr, *addr in HOOKS:
        out.append(f'#define RENUT_SUB_{key} sub_{addr[gi]:08X}  // {role} [MGR sub_{mgr:08X}]')
    for key, mgr, *addr in DATA:
        out.append(f'#define RENUT_{key} 0x{addr[gi]:08X}u  // [MGR 0x{mgr:08X}]')
out += ['#else', '#error "Renut nos Budokai: define RENUT_GAME_B3 ou RENUT_GAME_B1"', '#endif', '',
        '// Um nivel a mais: os argumentos expandem antes dos ## dos macros do Renut.',
        '#define RENUT_HOOK(sub, fn) BANJO_RENUT_HOOK(sub, fn)',
        '#define RENUT_HOOK_SHARED(sub, fn) BANJO_RENUT_HOOK_SHARED(sub, fn)',
        '#define RENUT_HOOK_RAW(sub) BANJO_RENUT_HOOK_RAW(sub)',
        '#define RENUT_IMP_(sub) __imp__##sub', '#define RENUT_IMP(sub) RENUT_IMP_(sub)',
        '#define RENUT_NATIVE_(sub) renut_native_##sub', '#define RENUT_NATIVE(sub) RENUT_NATIVE_(sub)', '',
        'namespace renut::gpu::budokai {',
        'struct GuestCodeGuard { uint32_t address, bytes, fnv32; const char* name; };',
        'inline constexpr GuestCodeGuard kGuestCodeGuards[] = {']
for gi, macro in enumerate(('RENUT_GAME_B3', 'RENUT_GAME_B1')):
    out.append(('#if' if gi == 0 else '#else') + (f' defined({macro})' if gi == 0 else ''))
    for key, role, mgr, *addr in HOOKS:
        out.append(f'  {{0x{addr[gi]:08X}u, 64u, 0x{fnv(imgs[gi], addr[gi]):08X}u, "{role}"}},')
out += ['#endif', '};', '}  // namespace renut::gpu::budokai', '']
print('\n'.join(out))
