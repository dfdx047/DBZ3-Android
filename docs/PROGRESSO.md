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
- Por resolver: o céu do mapa do Dragon Universe no Renut sai em blocos pretos (`cmp_b3_map*.png`); opção do
  renderizador no launcher e no menu do jogo; o Renut ainda não é o padrão.
