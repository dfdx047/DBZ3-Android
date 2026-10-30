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
