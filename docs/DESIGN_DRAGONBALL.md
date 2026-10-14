# Design dos ports de Dragon Ball (launcher + controles na tela)

Conceito visual comum aos ports de Dragon Ball do dfdx047 (Budokai Tenkaichi 3, Dragon Fury /
Budokai 3 HD e os próximos). Referência de código: este repositório
(`app/src/main/java/com/dfdx047/dragonfury/`). Ao portar outro jogo, copie a estrutura e
mude só o que está em **"O que muda por jogo"** no fim.

## 1. Identidade

- **Ideia:** esfera do dragão + aura de ki. Laranja de esfera, azul de aura, amarelo de ki, fundo
  noturno azul-arroxeado. Nada de logos ou arte oficial da Bandai/Toei: ícone, banner e botões são
  desenhos próprios.
- **Nome em caixa alta, peso Black**, com espaçamento largo: `DRAGON FURY` (LogoStyle: Black,
  30 sp, letterSpacing 1 sp; no banner do GitHub 78 px, letterSpacing 4, contorno marrom escuro).
- **Ícone** (`docs/icone-dragon-fury.svg`, adaptive icon `ic_launcher_*`): quadrado de cantos
  redondos com fundo radial azul (`#1E5BFF` → `#0B1A4A`), duas estrelas/explosões de ki atrás
  (`#FFC94A` 55% e `#FF8A1F` 85%), esfera laranja com gradiente radial (`#FFD27A` → `#FF8A1F` →
  `#C9480A`), brilho branco e estrelas vermelhas (`#D7261E`). Um selo redondo com o **número do
  jogo** ("3") no canto inferior direito da esfera.
- **Banner do GitHub** (`docs/banner.svg` → `banner.png`, 1280x420): gradiente diagonal
  `#0B1A4A` → `#14327F` → `#061031`, brilho laranja radial atrás do ícone, raios finos amarelos
  (12% de opacidade) a sair do centro, ícone centrado em cima, nome em gradiente amarelo→laranja
  (`#FFE08A` → `#FFA21F` → `#E0590C`), nome do jogo em branco por baixo e uma linha técnica em
  azul claro (`#B9C8FF`): "NATIVE ANDROID PORT · ARM64 · VULKAN · 60 FPS".

## 2. Paleta (`ui/theme/Theme.kt`, objeto `DfColors`)

| Nome | Hex | Uso |
|---|---|---|
| Orange | `#FF8A1F` | primária no escuro, botão JOGAR, brilho de botão premido |
| OrangeDeep | `#E0560B` | primária no claro, fim do gradiente do cabeçalho |
| Yellow | `#FFC94A` | terciária no escuro, logo, pill "pronto", borda de premido |
| Blue | `#1E5BFF` | secundária no claro |
| BlueDeep | `#0B2A8C` | meio dos gradientes, secondaryContainer no escuro |
| Navy | `#0B1A4A` | início dos gradientes |
| StarRed | `#D7261E` | estrelas da esfera, erro |
| Ok / Warn / Error | `#2E9E5B` / `#E0A000` / `#D7261E` | estados (StatusLine, Pill) |

Esquema **escuro** (padrão nos portáteis): fundo/superfície `#120C1E`, surfaceContainer
`#1C1529`, surfaceContainerHigh `#241C33`, surfaceVariant `#2A2236`, texto `#F1E6DD`,
primária `#FF8A1F` (on `#3A1500`), primaryContainer `#7A3000`, secundária `#8FA8FF`,
terciária `#FFC94A`, erro `#FF6B5E`.

Esquema **claro**: fundo `#FFF7EE` (creme), surfaceContainer `#FCEBDD`, primária `#E0560B`,
primaryContainer `#FFDCC2`, secundária `#1E5BFF`, terciária `#8A6100`.

Temas: **Dragon Fury** (este) ou **Material You** (cores dinâmicas do Android 12+); modo
claro/escuro/sistema.

## 3. Tipografia

Material 3 padrão com pesos reforçados: headlineMedium Black, headlineSmall/titleLarge
ExtraBold, titleMedium Bold, labelLarge Bold com letterSpacing 0,6 sp. Botões e rótulos curtos
em caixa alta quando são ação principal ("JOGAR").

## 4. Estrutura do launcher (`ui/LauncherApp.kt`)

- **Adaptativo:** largura ≥ 600 dp e paisagem → **trilho lateral** de 96 dp (surfaceContainer,
  ícone do app de 56 dp no topo, `NavigationRailItem` com ícone + rótulo); retrato →
  **cabeçalho** + `ScrollableTabRow` com ícone e texto.
- **Cabeçalho (retrato):** faixa com gradiente horizontal Navy → BlueDeep → OrangeDeep, ícone
  48 dp, nome em Yellow (LogoStyle) e subtítulo branco 85%.
- **Abas (ordem):** Início · Desempenho · Controles · Mods · Texturas · Cheats · Drivers ·
  Configurações · Sobre. Ícones Material: Home, Speed, Gamepad, Extension, Texture,
  AutoFixHigh, Memory, Settings, Info.
- **Barra de progresso** fina (BusyBar) por baixo da navegação durante importações/downloads.
- Snackbar para mensagens; diálogos `AlertDialog` com texto rolável (máx. 360 dp).
- Respeita `WindowInsets.safeDrawing` (recortes e barras).

### Início (`HomeScreen.kt`)
- **Cartão herói**: gradiente linear Navy → BlueDeep → Orange, cantos 24 dp, padding 20 dp;
  ícone 72 dp, título do jogo (titleLarge branco) e subtítulo (branco 80%); **Pill** de estado
  (pronto em Yellow, aviso/erro nas cores de estado); botão **JOGAR** largura total, 56 dp de
  altura, fundo Orange, texto `#2B1000`, cantos 16 dp, ícone PlayArrow.
- Depois: cartão "Arquivos do jogo" (importar / verificar / ver arquivos), atalho na tela
  inicial e uma linha de atalhos rápidos (FilledTonalButton): Desempenho / Controles / Drivers.

### Componentes (`ui/components/Common.kt`)
- **SectionCard**: superfície com cantos 20 dp, padding 16 dp, espaçamento 10 dp; cabeçalho com
  um "chip" de ícone 34 dp (primaryContainer, cantos 10 dp, ícone 20 dp) + título.
- **SettingSwitch / SettingSlider / SettingSegmented / SettingDropdown**: título + subtítulo à
  esquerda, controle à direita; segmentado para 2–4 opções curtas, dropdown para listas.
- **Pill**: texto numa cápsula com a cor a 16% de fundo.
- **StatusLine**: ponto de 8 dp na cor do estado + texto (INFO / OK / WARN / ERROR).
- Telas em coluna rolável com padding 16 dp e espaçamento 14 dp entre cartões.

### Avisos e idiomas
- Aviso de GPU/drivers no primeiro arranque (botões Entendi / Drivers / Desempenho), reabrível
  no Sobre.
- Inglês, português (Brasil) e espanhol; segue o idioma do aparelho (pt → PT, es → ES, resto →
  EN) e pode ser trocado nas Configurações. Notas de versão com secções `## PT`, `## EN`, `## ES`.

## 5. Controles na tela (`game/TouchControlsView.kt`, `controls/ControlModel.kt`)

### Visual: "vidro fosco + ki"
- **Botão**: círculo de vidro — gradiente radial deslocado para cima (branco 95/255 → 38/255 →
  `rgba(20,16,40,26)`), borda fina de 1,5 dp em `#FFF3DE` a 55%. **Premido**: acende por
  dentro em laranja (`rgba(255,201,74,170)` → `rgba(255,138,31,110)` → transparente) e a borda
  fica Yellow a 95%.
- **Ícone** do botão desenhado em **dourado** `#FFD27A` (branco quando premido), ~105% do raio.
  Conjunto próprio (vetores `res/drawable/ic_btn_*`): soco, chute, ki, defesa, carregar ki,
  agarrão, transformação, hyper, contra-ataque, trocar (Tag Team), start, back, macro, menu,
  pausa, layout.
- **Selo** (badge) no canto de cada botão com o botão real do comando: círculo/cápsula escura
  `rgba(12,10,28,160)` com o símbolo Xbox (A verde `#5DC21E`, B vermelho `#E5312C`, X azul
  `#2C8BE5`, Y amarelo `#F6C41B`) ou PlayStation (✕ `#7CB2E8`, ○ `#FF6666`, □ `#E899C9`,
  △ `#40E2A0`); combinações aparecem como "X+Y", sequências com sufixo. Pode ser desligado.
- **Analógico**: base de vidro com setas ▲▼◀▶ douradas (70%); o **pomo é uma esfera do dragão**
  (gradiente Yellow → Orange, estrelas vermelhas ★ / ★★) que acompanha o dedo até 55% do raio.
- **D-pad de 8 direções**: vidro redondo com cruz branca translúcida (cantos arredondados); as
  direções premidas (incluindo diagonais) acendem em Orange 75%.
- **Estilos**: Dragon Fury (ícones + selo), Xbox e PlayStation (símbolos clássicos coloridos nos
  botões de face). Nos layouts de menu os botões de face mostram sempre o símbolo do comando.
- Opacidade global ajustável; vibração ao tocar; no editor o elemento selecionado leva um anel
  amarelo tracejado.

### Layouts (trocam sozinhos pelo estado do jogo)
1. **Luta**: direcional à esquerda (centro em 15% / 66% da tela, 150 dp); losango de 4 botões
   de 62 dp à direita (centro 87,5% / 68%; esquerda soco, cima chute, direita ki, baixo
   defesa); botões de ajuda de 50 dp num arco por cima/à esquerda do losango (carregar ki,
   agarrão LB, transformação RB, hyper LT, contra-ataque RT); Back (trocar) e Start de 42 dp em
   cima (33% e 67% da largura, 9% da altura).
2. **Menus**: direcional + A/B/X/Y com os símbolos do comando + Back/Start.
3. **Mapa / exploração** (ex.: voo livre do Dragon Universe): comando completo — analógico
   sempre visível, A/B/X/Y, LB/RB/LT/RT, Back/Start; troca com ~1,5 s de atraso ao sair.

Posições em fração da tela (paisagem), tamanhos em dp, guardados em JSON por layout; o
editor permite mover, redimensionar, mudar opacidade, esconder e **adicionar botões de ajuda e
macros** (sequências com o último passo segurado, ex. carregar ki = defesa + trás, trás).
"Para a frente/para trás" são relativos ao lado do lutador (o motor de ajuda inverte quando o
P1 está à direita).

### Botões flutuantes do overlay (`game/GameOverlay.kt`)
- **Engrenagem** no canto superior esquerdo: menu dos controles e editor.
- **Pausa** em cima ao centro: menu do port por cima do jogo (opções rápidas, voltar ao
  launcher). Com um menu aberto, o jogo fica pausado.
- **Trocar layout** manual (ciclo menus → luta → mapa).
- Modo dos controles: automático (só sem comando físico), sempre ligado ou desligado.

## 6. O que muda por jogo

- Nome e subtítulo (`app_name`, `game_title`, `game_subtitle`, `header_subtitle`) e o texto do
  banner.
- Ícone: mesma composição; muda o **número do selo** (ou um símbolo do jogo) e, se quiser, o
  número de estrelas na esfera. Mantém a paleta.
- Ações dos botões e ícones (`GameActions`, `ControlIcon`): mapear os comandos do jogo novo
  (ex. Raging Blast: ataque, ki blast, guarda, salto/voo, Ultimate) e desenhar só os ícones que
  faltarem, no mesmo traço dourado.
- Layouts: manter os três tipos (luta, menus, exploração) e ajustar posições ao que o jogo usa.
- Abas: as mesmas; esconder Mods/Texturas/Cheats se o jogo ainda não tiver suporte.
- Paleta: igual em todos (identidade da série); variações só no gradiente do banner, se desejado.
