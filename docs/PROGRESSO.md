# Progresso — Budokai HD Collection (Dragon Fury 0.7)

Registo do trabalho de 30/10/2026 em diante (branch `renut/budokai-hd`), com o Dragon Burst como modelo.
Horas de Lisboa. Aparelhos: RP5 (Adreno 650, `192.168.1.89`), RG Cube (Mali-G57, `192.168.1.183`).
Backups dos aparelhos antes de mexer: `out/device-backup-20261030/`.

Nota: este repositório não tinha `CLAUDE.md` (o pedido mandava lê-lo).

## Base do SDK (decisão)

O Optxbox tem um SDK único (fork BL `465f9ef` + `0000-0178`), mas este repositório usa o ReXGlue v0.10.0 com o
overlay do projeto de PC do Budokai 3 (`external/dbz3/patches/rexglue-sdk/`, 83 ficheiros que substituem ficheiros
do núcleo do SDK) e os patches `0001-0076`. O overlay não encaixa na base do SDK único, por isso fica a base deste
repositório; o que for preciso do SDK único entra como patches novos (`0077+`), um por assunto.

## Item 1 — Budokai 1 no launcher (30/10, ~2 h)

- `yae1_xenon.xex` (disco da HD Collection, pasta `DBZ1/`): mesmo motor do B3 (mesmas 180 importações do kernel,
  XDK 21119, mesmo title ID `4E4D0856`). Recompilado com o mesmo codegen (`tools/codegen-b1.sh`, config em
  `tools/b1/`): 9159 funções. A análise não separa algumas funções chamadas só por ponteiro (thunks de ajuste das
  vtables `addi r3,r3,-N; b`, `lwz/mtctr/bctr`, entradas da `.pdata`): `tools/b1-missing-funcs.py` encontra-as na
  imagem (38 + 3 iniciais). Prova: as duas chamadas indiretas que abortavam (`0x82221868` no arranque,
  `0x82223390` depois do título) passam, e o jogo corre até à luta.
- App mínima `app/src/main/cpp/b1/dbz1_app.cpp`: jogo em `DBZ1/`, saves em `user_data/dbz1` (o title ID é o mesmo
  do B3: pastas separadas), opções em `dbz1_user.toml` com as mesmas chaves `dbz3_*` do launcher.
- Launcher: um cartão por jogo na aba Início; opções, presets, controles e saves seguem o jogo escolhido; uma
  importação do disco (pasta ou `.iso`) traz os dois jogos. Mods, Texturas e Cheats são código do B3 (port de PC):
  no B1 essas abas não aparecem.
- Prova (RP5, Xenia, 30/10 02:12-02:25): abertura, título, New Game, "Save successful!", "Load Game" sem saves do B3
  ("No save data present"), modo história (cutscenes do Raditz) e a luta Goku vs Raditz, 56-60 fps (GPU 14-17 ms).
  Fotos: `out/shots/b1*.png`, `st.png`, `sh.png`.

## Item 2 — distribuição legal (30/10, ~1 h)

- `libmain.so` = só o leitor do xex (`loader/`); `libgame_b3.so` e `libgame_b1.so` vão cifrados no APK
  (`assets/libgame_<jogo>.zdat`, AES-256-GCM, chave = SHA-256 da imagem PE do xex de cada jogo) e são
  desencriptados na primeira vez com o xex do jogador (`GameLib.kt`). O `GameActivity` carrega o do jogo escolhido.
- `tools/check-clean.py` para os dois pares (libgame, xex): **LIMPO** (APK, repositório, 691 blobs do histórico;
  `out/check-clean-0.7.0.txt`). Duas correções na ferramenta: o controlo usa procura exata (o código gerado repete
  sequências e o `grep -o` não conta sobrepostos) e os pedaços que são texto (caminhos do SDK) não contam.
- Atualização por cima da publicada (Cube, 30/10 02:33): 0.6.13 instalada com um save do B3, 0.7.0 por cima: B3
  "Pronto para jogar" sem reimportar, `libgame_b3.so pronto (14 MB, 52 ms)`, "Load successful." e o
  `dbz3_user.toml` igual byte a byte ao de antes.

- Importação pelo `.iso` (Cube, 30/10 03:00, seletor de ficheiros do Android): os dois jogos numa importação,
  `libgame_b1.so pronto (10 MB, 39 ms)`; `yae1_xenon.xex`, `data_yah.afs` e 1 MB do meio de `data_us.afs` com o
  mesmo SHA-256 do disco. B1 no Cube: título e menus a 60 fps (GPU 8,8 ms), "No save data present" (saves à parte).

## Regressão encontrada no caminho (não publicada: 0.6.16)

- Cube (Mali), B3: 0.6.13 = 60 fps na abertura (GPU 8-10 ms); HEAD 0.6.18 = 35-45 fps (7-8 passes de transfer,
  ~20 ms). Causa: a 0.6.16 desligou o "Clear 4x direto em 1x" em todo o lado por causa do céu do mapa do Dragon
  Universe. Correção 0.7.0: ligado, e desligado só enquanto o jogo voa pelo mapa (sinal do jogo
  `NativeGameState.mapFlag`, cvar ao vivo). Prova: Cube de volta a 60 fps nos menus (GPU 9 ms).
- Defeito antigo, por resolver: no Cube (Mali) o céu do mapa do Dragon Universe sai em blocos no caminho Xenia
  com qualquer combinação (clear ligado/desligado, pré-passo 4x saltado ou não). Para os itens 4/10 (Renut).

## Ferramentas de teste novas

- Comando de teste por adb (`GameOverlay.testPad`, só adb/sistema: permissão DUMP): `out/tp.sh <aparelho> "start
  w:60 a"` carrega botões no comando virtual em qualquer aparelho; `out/cv.sh <aparelho> "cvar=valor"` muda cvars ao
  vivo (A/B de imagem). `out/nav_map_b3.sh` vai do launcher ao mapa do Dragon Universe sozinho.

## Item 3 — Renut nos dois jogos (em curso)

Base: o Renut do mgr-nx-deko (`ba1a837`) com os patches do Dragon Burst `patches/renut/0001-0119`, mais:

- `0200` (mapa de hooks): os Budokai têm o layout de device do MGR, não o do Burst Limit; os endereços vêm de
  `gpu/d3d/budokai_hook_map.h` (gerado por `tools/renut/budokai_hook_map.py` dos dois xex, com guarda FNV por
  função). 27 dos 29 hooks do MGR; os 2 que faltam estão provados em `docs/renut/HOOKMAP_BUDOKAI.md`.
- Shaders: todos no `.rdata` do xex (108 por jogo); `tools/renut-shaders.sh` tira-os e traduz (XenosRecomp, 108/108).
- `0201` (defeitos de imagem encontrados, 30/10 03:30-05:10, RP5):
  - Título do B1 cortado na diagonal e ampliado: o GS dos retângulos (e o dos point sprites) declarava 2 cores e
    as texcoords como array; o XenosRecomp deste Renut exporta 6 cores. Interface única `gs_vertex.hlsli`, um membro
    por interpolador.
  - Picos pretos por toda a luta do B3: o XenosRecomp não contava um vfetch para `r0.x` como escrita do índice do
    vértice, e os ossos (POSITION1-3 lidos pelo índice do osso em `r0.x`) eram lidos pelo índice do vértice.
  - Sombras do B3: o jogo desliga a textura (`SetTexture NULL`) mas o fetch constant continua a descrevê-la (tipo
    0); o Renut passa a ler o que o fetch descreve, como a GPU (e o Xenia com `gpu_allow_invalid_fetch_constants`):
    `swordman_renut_invalid_fetch`.
  - Sombra repetida em cada ladrilho do chão: o fork do MGR liga VS e PS pelo registo; os Budokai precisam da
    ligação por semântica que o D3D faz no bind (`RENUT_SEMANTIC_LINKAGE=1` no `renut-shaders.sh`; FOG0 vai no
    TEXCOORD15, que os jogos não usam).
  - Céus laranja e água cinzenta: as cores dos vértices (D3DCOLOR) vinham com R e B trocados; a declaração que o
    Renut reconstrói dos vfetch ligados passa a levar o swizzle que o bind escreveu (BGRA).
- Prova (RP5, Xenia | Renut, mesmas cenas): título do B1, ilha do Kame e luta Goku vs Raditz (B1), luta Goku vs
  Goku Kaioken (B3): iguais. Fotos `out/shots/cmp_b1_*.png`, `cmp_b3_fight*.png`. Renut a 60 fps com GPU 1,3-7 ms
  por frame (Xenia: 7-17 ms).
- `0202` (30/10 05:15-05:50): céu do mapa do Dragon Universe em blocos pretos. O jogo resolve o depth, limpa e
  depois devolve-o desenhando a resolução como cor sobre o EDRAM do depth (sem depth ligado); no Renut cor e depth
  são imagens à parte e o depth ficava com o da 1.ª passagem, onde o céu e as nuvens falhavam o teste. Agora esse
  draw vira um restauro do depth (`depth_restore_ps`: o float da resolução escrito como profundidade; ou cópia da
  imagem quando a resolução ficou no hand-over), `swordman_renut_depth_restore`. Prova: `cmp_b3_mapfix.png`
  (Xenia | Renut iguais); lutas dos dois jogos e abertura do B1 sem mudança (`reg_k.png`, `cmp_b1_story_k.png`).
- Por resolver: opção do renderizador no launcher e no menu do jogo; o Renut ainda não é o padrão.
- Renderizador no launcher e no menu do jogo (30/10 05:55-06:15): Desempenho > Renderizador (Auto | Renut | Xenia,
  por jogo; Auto = Renut em todas as GPUs) e Vídeo > Renderizador (próximo arranque) no menu do jogo
  (`swordman_renderer` passa a `kRequiresRestart` para o SaveConfig gravar a escolha; `swordman_renderer_choice`).
  Aviso de crash como no Dragon Burst: `renut_run.pending` (com o id do jogo) enquanto o jogo do Renut está em
  primeiro plano; o launcher mostra "O Budokai N fechou" com Exportar logs e as issues, e só do 2.º crash seguido em
  diante pergunta se quer o Xenia nesse jogo. Prova (RP5): aviso com 2 crashes simulados do B1 → "Usar o Xenia" mudou
  só o `dbz1_user.toml`; no menu do B3 Xenia → `swordman_renderer = "xenos"`, Auto → `"renut"`. Nota de teste: um
  `am force-stop` com o jogo à frente conta como crash (não passa pelo onPause); os scripts de teste apagam o marcador.
- `0203` (30/10 06:20-07:15): o RG Cube (Mali-G57, driver do sistema) encravava no 1.º frame do B3 com o Renut: o
  compilador de shaders da Mali (`cmpbe_v2_compile_multiple_shaders`) nunca acabava o pipeline do FXAA do jogo
  (lista de retângulos → `rectangle_list_gs`). Provado com qualquer GS nesse pipeline (até um GS que só repassa os 3
  vértices, com a interface reduzida ou completa, com índices fixos). Correção: na Mali as listas de retângulos não
  usam GS; o 4.º canto de cada retângulo é feito no CPU a partir dos vértices convertidos (a + b − c, por formato) e
  desenhado como 2 triângulos indexados, na mesma ordem do GS (`swordman_renut_rect_lists` = auto | gs | cpu; auto =
  cpu na Mali). Prova: no RP5 o título do B1 com `cpu` sai igual ao do GS (a 1.ª foto com o GS em arrays saiu
  quadriculada: o GS por membro do `0201` fica); Cube a 60 fps no título e menus do B3, save/história/Kame/Raditz do
  B1 (60 fps, GPU 11 ms), luta do B3 a 40 fps (GPU 22 ms a 720p: para o item 10). Regressão no RP5: B1 (save,
  abertura, Raditz), B3 (luta, mapa do Dragon Universe), FATAL=0. Fotos `out/shots/reg_0203.png`,
  `reg_cube_b1.png`, `g3c_2_s.png`, `g3r_map_s.png`. Diagnóstico: `swordman_renut_pipeline_log` (cada pipeline
  antes de compilar).
- Efeitos pelas opções no Renut (`0204` + `app/src/main/cpp/renut/renut_effects.cpp`, 30/10 07:15-07:45): FXAA,
  profundidade de campo e brilho seguem `dbz3_fx_*` (launcher Desempenho > Efeitos e menu do jogo > Efeitos, ao vivo;
  antes só ativos com o Xenia + RT diretos), pelos mesmos hashes de ucode e regras do caminho Xenia: FXAA/DOF
  aprendem o resolve com limpeza que os alimenta (1.º frame desenhado), depois a limpeza e o draw saem; o brilho
  salta a cadeia 555EBAAE, as fontes CB0605B4 no alvo de 320 e a composição 6E57A6E3 misturada que lê um alvo
  ≤ 160. Prova: frame dump (luta B3: 6 draws da cadeia, 5 fontes, composição, FXAA e DOF saltados, 1 limpeza
  tirada); ligar/desligar ao vivo na pausa: desligado = fundo nítido, ligado = desfoque, desligado de novo igual ao
  1.º (diferença 0) (`cmp_fx_b3.png`, `cmp_fx_crop.png`). B1: o FXAA tem o mesmo hash e sai igual (Raditz,
  `fx1_fight2_s.png`). GPU da luta do B3: RP5 7,9 → 5,6 ms; Cube 22 → 17 ms (40 → 46 fps a 720p).
- Resolução "Tela" (30/10 07:45-08:10, como no Dragon Burst d552361): a altura que a imagem ocupa na tela
  (`ScreenFit`: com barras min(altura, largura × 9/16), esticada a altura toda); o Renut usa a mais perto das suas
  alturas até 720 (360/480/540/720, empate para baixo), o Xenia a mais perto de 360/540/720
  (`swordman_resolution_choice = "screen"`, refeita a cada gravação). A predefinição Mali usa-a numa tela pequena.
  O Renut passa a seguir a proporção do launcher (`swordman_aspect` = original | stretch, também no menu do jogo).
  Prova (Cube 720x720): com barras "Tela (360p)" → `sa2_render_height_handheld = 360`, `draw_resolution_percent = 50`;
  esticada "Tela (720p)". Luta do B3 no Renut a 360p, efeitos desligados: 60,00 fps, GPU 8,6-9,2 ms
  (`ctela_1_s.png`). O FSR até 720p (`0114`) já vem na série do Dragon Burst. O boot guard do Burst (o "auto" passa
  ao Xenia sozinho) não entra: contraria a decisão do dono (só o aviso, e a pergunta depois de 2 crashes, já feitos).
- Nota: o contador do overlay mostra "Render 1280x720" também no Renut (é o do Xenia); para o item 10.
- Pausa fora da tela (30/10 07:50-08:15, como no Dragon Burst cfdffa4): com o jogo fora da tela (HOME, recentes,
  tela desligada) a luta continuava a 30-60 fps desenhada e não mostrada: RP5 134% de CPU e GPU 28%. Agora
  `NativeGameState.setBackground` (onPause/superfície destruída, numa thread própria) aplica a pausa dos menus (lógica
  e áudio) e uma porta (`DragonFuryWaitForeground`): a thread de render do jogo para no Swap (Xenia: `rex_vdswap_hook`;
  Renut: `renut_glue.cpp`), o render thread do Renut deixa de acordar (renut/0115) e o SDK `0079` (Dragon Burst 0189)
  para o vsync, o ciclo ocioso e WAIT_REG_MEM do command processor e as esperas do jogo. 60 s fora da tela a meio do
  jogo: RP5 Renut 12% de CPU (GPU 0-3%), Xenia 12% (era 37% só com a porta), Cube Renut 5,8%; o que resta é a thread
  de timers do SDK (`rex::thread::TimerQueue`, timers periódicos do jogo). Volta: o mesmo processo, a imagem e o som
  voltam, 60 fps, FATAL=0, nos dois jogos (`bgr2_back_s.png`, `bgx2_back_s.png`, `bgc_back_s.png`, `cmp_bg_b1.png`).
- M3 (30/10 08:20-09:55): a mesma cena com os dois renderizadores, RP5 no perfil Snapdragon 480+ (`device-profile.sh
  limit sd480p`), 720p, efeitos ligados, 40 s em cada; luta no Practice (o boneco não ataca, a luta não acaba:
  `out/nav_practice_b3.sh`, `nav_practice_b1.sh`), campanha no mapa do Dragon Universe e na história do B1 (Raditz).

  | Cena (RP5, 480+) | Renut fps / GPU ms | Xenia fps / gpu_ms |
  |---|---|---|
  | B3 luta (Goku × Goku, World Tournament) | 60,00 / 13,9-14,3 | 33,4 / 29,1 |
  | B3 campanha (mapa DU, casa do Goku) | 60,00 / 12,1 | 32,7 / 29,9 |
  | B1 luta (Goku × Krillin, Grassland) | 60,00 / 12,2-12,5 | 44,4 / 21,9 |
  | B1 história (Raditz, cena) | 60,00 / 10,8-11,2 | 48,8-50,2 / 19,2-19,7 |

  Cube (Mali-G57, Renut, efeitos desligados, opções do dono): luta B3 "Tela" 60,00 fps / GPU 8,7-9,1 ms; luta B1
  720p esticada 60,00 / 12,2. Fotos Xenia|Renut: `out/shots/m3_b3_fight.png`, `m3_b3_camp.png`, `m3_b1_fight.png`,
  `m3_b1_story.png`; números em `out/m3/*.txt`.
- `0205` (30/10 09:20-09:50): defeito de imagem visto no M3: no Renut a pele do Raditz (B1, história) saía às manchas
  por triângulo, no Xenia lisa. O passe de sombra do B1 (mapa 1024x1024 24_8, só profundidade) usa o polygon offset do
  Xenos (PA_SU_SC_MODE_CNTL bits 11/12/13, valores do device em 0x2A50: 168 unidades, inclinação 2 no frame dump),
  que o Renut ignorava: o lado iluminado de cada modelo falhava o próprio teste de sombra triângulo a triângulo.
  Agora a chave do pipeline leva o depth bias (como o caminho de alvos do host do Xenia: unidades de 24 bits,
  inclinação /16 × escala ≥ 1); lista de pipelines versão 5 (a 3 e a 4 são mantidas, com bias 0). Prova:
  `raditz_neck.png` (lisa), B3 Practice igual ao de antes (3% dos píxeis, a animação; GPU 14,3 ms igual),
  Cube B3/B1 a 60 fps (`m3_cube_b3_fight_Renut.png`, `m3_cube_b1_fight_Renut.png`).
- Item 4, B3 campanha e B1 história Renut × Xenia (30/10 10:00-11:50, RP5 480+): gravação em vídeo dos dois
  (`out/rec_b1.sh`: abertura e história do B1, 120-160 s; `out/rec_b3du.sh`: Dragon Universe nova história com o Goku,
  cutscene, cartões de habilidade e entrada no mapa, 90 s) e `out/vpair.py` (2 fps; cada frame do Renut com o do
  Xenia mais parecido; os mais diferentes numa montagem). Diferenças achadas: só a pele do Raditz (corrigida no
  `0205`); o resto é desfasamento das animações (o Renut corre ~1 s à frente: o logótipo com raios, o cartão a
  crescer). Fotos: luta e mapa iguais (`m3_b3_both.png`), `vp_b1intro.png`, `vp_mid.png`, `vp_logo.png`,
  `vp_b3du.png`. Investigado e não é defeito: a linha comprida do rasto da Kinto'un no plano da ilha do Kame (B1) sai
  igual no Xenia, frame a frame em vídeo (`isl_cmp.png`): é do jogo, nos primeiros 0,6 s do plano.
- Item 8, troca automática dos controlos de toque (30/10 13:20-13:47, Cube, Renut). O B1 não tinha sinal de luta
  (`controles.log`: "sem enderecos de luta neste jogo", o toque ficava sempre no layout de menus, também a lutar).
  Achado comparando a imagem do jogo (0x82000000, 6,6 MB) em 8 momentos (menu, seleção, escolha do cenário, luta no
  início, depois de andar para a direita e para a esquerda, pausa, seleção depois de sair): posições do P1 em
  0x82348B20 (x, y, z, 1; -14,8 no início, mexem com o direcional) e do P2 em +16 (x = +15). Ficam lá depois da luta;
  o que separa é 0x82339FF8 (0 nos menus e nas cutscenes, 3/9/15 na luta, na pausa e no "Continue?"). Provas
  (`controles.log` + fotos com os controlos ligados): B1 Practice menu -> luta 13:34:42, luta -> menus ao sair para a
  seleção 13:35:16, revanche -> luta 13:35:34 (`tr_b1_touch.png`); história: cutscenes em menus, luta com o Raditz ->
  luta 13:39:36, sair -> menus 13:43:09 (`tr_b1_story.png`). B3 igual ao que era (`tr_b3_touch.png`: menu -> luta
  13:45:41, seleção 13:46:27, revanche 13:46:49). Ficam no layout de luta a pausa do jogo e o "Continue?/Exit?"
  (são da cena da luta; os botões A e o direcional estão lá).
- Item 6, 120 FPS no Renut (30/10 13:48-14:07, Cube, B1).
  - 120 interpolado: já existia nos dois renderizadores (Xenia 0057, Renut 0025) e no launcher, só em telas de
    110 Hz ou mais. Agora também no menu do jogo (Vídeo -> Taxa de quadros: 30 / 60 / 120 interpolado, este só nessas
    telas; muda ao vivo e guarda no toml; a tela muda de taxa com `requestDisplayRate`). Prova no Cube (60 Hz): só
    30/60 aparecem; 60 -> 30 ao vivo dá `presented_fps=29.97` com o jogo a 60, e 30 -> 60 volta a 60,01.
  - Prova da interpolação numa tela de 60 Hz: o Renut não lia a cvar de taxa da tela (`dbz3_frame_interpolation_display_hz`,
    a do Xenia); a `renut/0206` passa a lê-la. Com `dbz3_frame_interpolation = true` e a taxa a 120 no Cube:
    `presented: game_fps=20.01 presented_fps=40.01 (402 shown, 201 of them generated)`, metade dos quadros gerados.
    (No Cube a GPU não aguenta os passes da interpolação a 720p: 20 fps; numa tela de 120 Hz mostra-se 60+60.)
  - 120 nativo: não sai. Três tentativas no Cube (B1, abertura gravada a 60 e a 120): vblank do jogo a 120
    (`video_mode_refresh_rate`, a pacing do Renut segue-o: "0.00 ms a frame paced; 601 frames late"), e o mesmo com
    `renut_vsync = false`. O jogo fica nos 60 (16,7 ms de Swap a Swap) e a abertura corta nas mesmas horas; numa tela
    de 60 Hz o present trava-o. O port de PC diz que a lógica do Budokai anda por frame (subir o vblank acelera o
    jogo), por isso o 120 nativo pede um passo fixo de 60 Hz na lógica, como o `burstlimit_patches` do Burst Limit,
    e uma tela de 120 Hz para medir. Fica por fazer (estimativa: 6-10 h com o Odin 3). Desbloqueado: o Android não o
    oferece (já visto no Dragon Burst, B3), fica de fora.
- Item 5, drivers no RP5 (30/10 11:50-14:40, perfil 480+). O Renut com o Turnip 26.3 R6 perdia a GPU no ecrã título
  (kgsl "GPU hang detected", `VK_ERROR_DEVICE_LOST`) nos níveis 0-3 do modo de compatibilidade. A/B com `env:TU_DEBUG`
  no `cvars.txt` (2 min cada, perdas contadas no logcat): `sysmem,nolrz,noubwc` cai (2 de 2), `syncdraw` só cai,
  `flushall` só cai, `sysmem,nolrz,noubwc,syncdraw` não cai; barreiras do Renut (`banjo_renut_precise_barriers`,
  `banjo_deko3d_barrier_merge/lean`) e retângulos no CPU não mudam nada. Correção (`GameActivity.applyTurnipCompat`):
  Turnip num Adreno 6xx com o Renut leva sempre `sysmem,nolrz,noubwc,syncdraw`. Falta achar a barreira que o driver da
  Qualcomm tolera (pendente). Tabela (driver x jogo x renderizador; 600 s nas 3 primeiras linhas, 120 s nas outras
  a pedido do dono; dmesg filtrado para "GPU hang"/"gpu fault"):

  | driver | jogo | renderizador | fps / GPU ms | perdas da GPU | hangs no dmesg |
  |---|---|---|---|---|---|
  | sistema (Qualcomm) | B3 | Renut | 60,0 / 13,7-14,9 | 0 | 0 |
  | sistema | B3 | Xenia | 34,0 / 28,6 | 0 | 0 |
  | sistema | B1 | Renut | 60,0 / 10,8-11,2 | 0 | 0 |
  | sistema | B1 | Xenia | 50,3 / 16,3 | 0 | 0 |
  | Turnip R6 | B3 | Renut | 60,0 / 8,2 | 0 | 0 |
  | Turnip R6 | B3 | Xenia | - | 1 (nível 0; o app sobe o nível) | 0 |
  | Turnip R6 | B1 | Renut | 59,8 / 15,5 | 0 | 0 |
  | Turnip R6 | B1 | Xenia | 28,2 / 18,7 | 0 | 6 (recuperados) |

  As normais verde-oliva do Xenia no Turnip A650 não apareceram nestas corridas (fotos `drv_r6.png`); o bisect com os
  builds do Zandatsu fica por fazer. Logs: `out/drv/*.txt`.
- Item 7, widescreen: a HD Collection já desenha em 16:9 (Original 16:9 / Esticar existem). Para lá de 16:9: no B1 o
  aspeto da projeção é a constante 0x82024B2C (16/9; com 2,37 o logótipo 3D fica mais estreito, `wide_b1_aspect_test.png`).
  Falta alargar os alvos do Renut e o HUD como o `renut/0022` do MGR faz; nos dois aparelhos de teste (16:9 e 1:1) não
  muda nada. Pendente (estimativa 6-8 h).
- Item 9, port de PC: o upstream vai na v1.5.5.5 (30 commits desde a nossa v1.4.5.1; 697 ficheiros, +226 mil linhas):
  Dragon HUB, Budokai 2 e Infinite World (precisam do disco de PS2), modo Herói, Lenda do Mr. Satan, Tag Team com IA,
  online lockstep, câmara livre, pack HD V1/V2, opção 480p. Não trazido nesta sessão (cada parte mexe no código do
  jogo e no launcher do PC; estimativa 10-15 h para o núcleo B3: Tag Team IA, câmaras, correções).
- Item 10, otimização: alvos cumpridos (480+: 60 fps a 720p na luta e na campanha nos dois jogos; Cube a "Tela": B3
  luta 60,0 / 8,7-9,1 ms, B3 mapa 60,0 / 7,7-8,1 ms, B1 luta 60,0 / 12,2 ms, B1 história 58-60 / 9-9,5 ms). Censo
  (`renut_gpu_profile = 10`, Cube, B1 luta 720p, 12,4 ms): cena 3,9; resolves de profundidade 2,3; resolves 1,9; 2 draws
  de profundidade em ecrã inteiro 1,7; present 1,2; cor 0,9; sombra 1024² 0,5.
- Fecho (30/10 14:40): notas da 0.7.0 em `docs/releases/v0.7.0.md` (PT/EN/ES) e READMEs nas três línguas com o B1 e o
  Renut. Aparelhos repostos: APK final nos dois, `dbz3_user.toml` e saves do B3 do backup de 30/10, B1 com o
  renderizador em Auto, sem `cvars.txt` nem driver alternativo, perfil 480+ do RP5 desfeito ("no profile active"),
  Cube com os controlos de toque em Automático e o B3 escolhido. Fica no RP5 o nível de compatibilidade do Turnip R6
  subido pelos testes (volta a 0 em Drivers -> "Voltar ao modo 0" com o R6 ativo).

## Segunda volta (30/10, 19:40-22:30 Lisboa): Odin 3, port de PC, widescreen, 120

O Odin 3 voltou por USB e passou para o adb por Wi-Fi (`adb tcpip 5555`, 192.168.1.134; tela de 120 Hz). Tinha a
0.6.13 e só o B3: backup em `out/device-backup-20261030/192.168.1.134/`, APK novo por cima e a pasta DBZ1 copiada
do RP5. Testes curtos a pedido do dono (ver que funciona e seguir; a abertura saltada com Start).

- Item 6, 120 FPS no Odin 3: interpolado no B3 a 1440p `game_fps=60.00 presented_fps=120.00 (1202 shown, 601 of them
  generated)`, tela a 120 Hz (`mActiveRenderFrameRate=120`). Nativo: com o vblank do guest a 120 (`video_mode_refresh_rate`,
  B1) o jogo continua nos 60 (`game_fps=60.12`) e a abertura anda igual à de 60 (frames lado a lado, `odin_hz.png`): a
  lógica do Budokai é de 60 por quadro. O port de PC também não tem 120 nativo (o `dbz3_frame_cap` só limita o
  apresentador e repete quadros). Fica o 120 interpolado; o nativo pedia reescrever a lógica (fora de alcance).
- Item 9, port de PC: `external/dbz3` na v1.5.5.5 (b5bff8a; ramo `android-v1.4.5.1` guarda a anterior). O codegen não
  mudou (config e manifest iguais). Patches: 0001/0002 aplicam; 0003 (identidade do online) e 0004 (entrada do
  Dragon Universe) refeitos à mão (o código mudou de sítio; 0004 agora em `DBZ3_HOOK_EU`); 0005 (Renut) com o
  include no sítio novo. O CMake do Android compila as 67 fontes do PC (eram 29) e gera o `escenarios_sel_gen.inc`.
  Compila sem erros e corre: HUB no menu principal (Tag Team, Online, Quick Battle, Torre, Sobrevivência, Kumite,
  Ranking, Laboratório; página 2: Gateway, Lenda do Mr. Satan, Missão 100, Modo Herói), tutoriais, Quick Battle a 60.
  Trazido vs. deixado:

  | do PC v1.5.5.5 | no Android |
  |---|---|
  | Dragon HUB, Tag Team com IA, modos novos (torre, sobrevivência, kumite, ranking, laboratório, missão 100, herói) | trazido (código do jogo) |
  | Gateway: Budokai 2, Infinite World, Lenda do Mr. Satan (ISOs de PS2) | trazido + importação de ISO no launcher (Configurações -> Gateway); o jogo procura em `ps2_games/` |
  | online lockstep v1.5.5 (relatório de dessincronia, revanche) | trazido (o patch 0003 mantém a identidade Android) |
  | opções HUB e tutoriais | no launcher (Configurações -> Jogo) |
  | câmara livre (teclado/rato) | compilada, sem botão (é de testes no PC) |
  | vídeos SFD do IW (FFmpeg só no Windows) | deixado: fora do Windows o PC também não os toca |
  | launcher do PC, atualizador, pack HD, opção 480p, D3D12 | deixado: o Android tem o seu launcher, a "Tela" e o Renut |
  | `res/datos`, `res/heroe` (dados medidos) | não vêm no repositório público; o código usa os valores de origem |

  Regressão (Odin): título, menus, HUB, Quick Battle, Dragon Universe (cutscene, cartas, mapa, voo), B1. RP5 480+ com o
  código novo: B3 luta 59,9 / GPU 14,1-14,5 ms (antes 13,9-14,3), B3 mapa 60,0 / 11,4 ms (antes 12,1). Odin a 1440p:
  B3 luta 60,0 / 11,8 ms; B1 abertura 60,0 / 3,5-5,5 ms.
- Item 7, widescreen (B3, Renut): `app/src/main/cpp/widescreen.cpp`. A vista 3D do B3 passa pelo frustum
  `sub_8224F768` (l, r, b, t, zn, zf; achado pelo padrão `fsubs f12,f2,f1 / fsubs f10,f5,f6`; l/r = ±0,78125,
  b/t = ±0,4395, 16:9): l e r × aspeto/(16/9) dá Hor+ (com 0,7 a vista aperta, foto `w_cmp`). O present mostra o
  quadro nesse aspeto (`SetGuestDisplayAspect`) e a callback de overlay do Renut (renut 0118 do Dragon Burst) põe o 2D
  na forma certa: o 2D do jogo é o shader com c0.x = 1/400 e c1.y = -1/225 (espaço 800x450; os contornos das
  personagens sem teste de profundidade também chegam lá e ficam de fora). Por cima de uma cena 3D o que cobre a
  largura toda estica até às pontas e, em luta, o HUD de cima e de baixo vai para as pontas; os ecrãs só de 2D (sem
  frustum no quadro, ou a roda de personagens) ficam inteiros ao centro com faixas, como no PC. Imagem passa a
  Original 16:9 / Esticar / Widescreen no launcher e no menu do jogo (ao vivo; só no B3 com o Renut). Testado com
  `dbz3_widescreen_aspect = 2.2` no Odin e no RP5 (16:9 com faixas em cima e em baixo): Practice, Quick Battle,
  título, menu principal, roda, cutscenes e cartas do Dragon Universe, mapa. B1: três tentativas sem efeito na
  vista 3D (as funções de frustum não são as da luta; a matriz da câmara em 0x8231F460 não muda nada; escrever a
  constante 0x82024B2C deixa o ecrã preto, porque também decide o modo de vídeo). Fica pendente no B1.

## Terceira volta (30/10 22:20 - 31/10 00:50 Lisboa, só o RP5): drivers e widescreen no B1

- Drivers no RP5 (o resto do item 5). Testes curtos com `out/drvq.sh` (driver, jogo, renderizador, `TU_DEBUG` pelo
  `cvars.txt`) e `out/boot.sh` (só o arranque, ou até ao menu com `MENU=1`). Cuidado nas medições: cada queda sobe o
  modo automático do Turnip (até ao 3: sincronização total e profundidade em float) e as corridas seguintes ficam mais
  lentas; voltar ao modo 0 na aba Drivers antes de medir.
  - Normais verde-oliva do Xenia: não aparecem em nenhum Turnip (R6, MrPurple T19, R8, Rutabaga v3.2; B3 e B1, fotos
    `q_*_Xenia_*`). Não há nada para bissetar; ficaram corrigidas pelos patches do SDK para os A6xx. O R8 e o Rutabaga
    perdem a GPU no Xenia no modo 0 e o app sobe-os para o modo 1, onde correm certos (45 fps).
  - Renut no Turnip do A650: são dois hangs diferentes. (1) No arranque, na primeira submissão (0,6 s): só o `noubwc`
    o tira (`sysmem,syncdraw` e `nolrz,syncdraw` caem). (2) No menu, ~25 s depois: kgsl `status 00E59005/00E715E5`,
    a fase de vértices não acaba (o mesmo do Zandatsu no MGR). Só passa com um draw de cada vez na GPU (`syncdraw`, ou
    os marcadores de GPU por draw do patch renut/0014). Sem efeito: geometry shaders desligados ou rect lists no CPU,
    `banjo_renut_precise_barriers`/`banjo_deko3d_barrier_merge`/`_lean` = false. A causa fina pede o snapshot do kgsl
    descodificado (XML do Mesa) e o anel de comandos no device lost do Dragon Fury (hoje só o Zandatsu o escreve).
  - Custo (B3 Practice, modo 0): `sysmem,nolrz,noubwc,syncdraw` (o padrão) 13,2 ms; `noubwc,syncdraw` 14,5; marcadores
    em vez do syncdraw 13,3-15,0. Fica o padrão.

  | driver (Renut, padrão do app) | B3 Practice | B1 Practice | Xenia |
  |---|---|---|---|
  | Turnip v26.3.0 R6 | 60,0 / 13,2-14,4 ms | (tabela de 30/10: 59,8 / 15,5) | certo |
  | **MrPurple T19** (25.1.0, o do Zandatsu) | **60,0 / 8,6-9,2 ms** | **60,0 / 7,0-7,2 ms** | certo (B1 30 fps) |
  | Turnip v26.0.0 R8 | 60,0 / 14,3-14,4 | - | certo no modo 1 |
  | Rutabaga v3.2 | 60,0 / 14,3-14,8 | - | certo no modo 1 |
  | sistema (Qualcomm) | 60,0 / 13,7-14,9 | 60,0 / 10,8-11,2 | certo |

  O MrPurple T19 é o mais leve no RP5 com o Renut; o app não o traz nem o recomenda (o padrão continua o driver do
  sistema). Está na pasta do Zandatsu (`drivers/turnip_mrpurple-T19-toasted.adpkg`).
- Widescreen no B1 (`widescreen.cpp`). Quarta abordagem: procurar na memória, em luta, uma projeção pura com
  m11/m00 = 16/9. Está em 0x8231F4A0 (m00 1,4837, m11 2,6378), escrita por `sub_82086F88` (a gémea em perspetiva de
  `sub_82087390`, que é o 2D) a cada quadro e consumida logo: mexer na matriz depois não muda nada (era o que falhava
  antes, com a 0x8231F460). A câmara vem em r3; o campo `[16]` é a escala horizontal: dividido por aspeto/(16/9), m00
  cai na mesma proporção (1,4837 -> 1,1990 a 2,2:1) e m11 fica igual, e o recorte do jogo alarga junto. O 16:9 vem
  da flag 0x82320775 e da constante 0x820007D4, não da 0x82024B2C (essa é a escolha do modo de vídeo,
  `sub_8213EF48`). Os menus e a história do B1 desenham a arte 4:3 com moldura pela mesma função (câmaras no heap,
  0x7010xxxx) e alargá-la desencontra a moldura: no B1 o widescreen vale só em luta; fora dela fica igual ao Original
  16:9. Testado no RP5 com `wm size 1080x2400` (20:9, aspeto da tela, sem forçar): B1 Practice (vista larga, barras
  nas pontas, tabelas ao centro, 60 fps), título, menu, história; B3 Practice a 20:9. Regressão a 16:9 nos dois.
  A opção Widescreen aparece agora nos dois jogos (launcher e menu do jogo).

## HUB e Gateway no RP5 e no Cube (02/11 21:15-23:00 Lisboa, ISOs USA de PS2)

Teste a pedido do dono, com as ISOs USA de Budokai (SLUS_205.91), Budokai 2 (SLUS_207.79) e Infinite World (SLUS_218.42).
O Cube ainda estava com o APK de antes do merge (sem HUB). Correções em `patches/game/0006` e `0007`, mais `dbz3_aspect`
no `GameConfig.kt` e `aaudio` no CMake.

| achado | efeito | correção |
|---|---|---|
| Som dos modos de PS2 só em Windows (winmm) e a mistura do B3 é calada nos modos | Dragon World e Dragon Mission mudos no Android | `TocarPcm` por AAudio em voz, ADX, música e efeitos; pausa com `g_dragonfury_pause` |
| Leitor pede `data_en`/`data_uk`/`adx_jp`; o disco USA só traz `DATA_US`/`ADX_US` | Lenda sem imagens (`charlas -, 0 imagenes`), títulos do Dragon World em falta, ADX de 0 s | apelidos em `AfsPs2::Abrir` e em `LeerLeyendaSatan` |
| Launcher não gravava `dbz3_aspect` e `GameArea` só tinha uma escala | Cube (1:1): HUB espremido, fila HUB sobre Options, texto fora do balão | `dbz3_aspect` no toml; `Area` com `sx`/`sy`; `GameAreaUniforme` nas telas próprias do B2 |
| "Pulsa A e escolhe a ISO" (seletor de PC, no Android não faz nada) | jogador fica sem saída | no Android aponta para Configurações -> Gateway |
| Textos pedem o Mod Kit de PC | sem caminho no Android | no Android dizem que ainda não há |

Limitações que ficam no Android:

- Saga de Namek, Fat Gotenks e absorções do Buu pedem o `mods/_roster` do Mod Kit de PC: em cinza, com a mensagem.
- Vídeos SFD do Infinite World: o reprodutor (`video_sfd.cpp`) é só de Windows; o modo segue com legendas.
- Vozes do B1 USA vêm do `ADX_US` com os índices do disco europeu: números de entrada não conferidos a ouvido.
- Nave de Babidi: fechada até o desejo a Shenron no Dragon World (como em B2); não foi aberta nestes testes.

Resultados finais (build `versionCode` 36, 0.7.0):

- RP5 e Cube: HUB, Lenda (título, intro, luta), Dragon World (história, tabuleiro, escolha de aliado; no RP5 também andar no tabuleiro) e Dragon Mission (mapa, Training No.1, luta). Nenhuma luta do Dragon World foi aberta nestes testes.
- Budokai 1 abre com o toml novo no RP5, 60 fps no título. O Cube abre o menu principal do B3 a 60 fps depois de instalar por cima.
- Cube: `VK_ERROR_DEVICE_LOST` uma vez (02/11 22:46, Dueling, driver do sistema, a gravar com `screenrecord`), não repetiu em Quick Battle, Lenda, Dragon World e Dragon Mission. O launcher já tinha registado o mesmo erro em 30/10 no Cube. Causa não investigada.
- Instalar por cima: mesmo certificado da 0.6.13 (`1a7325cf...`) e `versionCode` 36 > 29. `adb install -r` sobre a 35 nos dois aparelhos. A passagem 0.6.13 -> 0.7.0 com save foi testada em 30/10 no Cube e não foi repetida neste build.
