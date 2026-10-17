<p align="center"><img src="docs/banner.png" alt="Dragon Fury — Dragon Ball Z: Budokai 3 HD para Android" width="100%"></p>

<p align="center">
<a href="README.md">English</a> · <b>Português (Brasil)</b> · <a href="README.es.md">Español</a>
</p>

# Dragon Fury

**Dragon Fury** é um port nativo para Android de *Dragon Ball Z: Budokai 3 HD* (da *Budokai HD
Collection* do Xbox 360). Ele parte da recompilação para PC feita por
[novapowers0](https://github.com/novapowers0/DBZ-Budokai-3-HD-Collection) (ReXGlue SDK) e leva o
jogo para celulares e portáteis ARM64, com launcher próprio, controles por toque, Tag Team, online
e um caminho de GPU reescrito para as GPUs de celular.

> **Nenhum arquivo do jogo vem incluído.** Você precisa da sua própria cópia do jogo (versão USA).

## Destaques

- **60 FPS na resolução nativa de 720p num Snapdragon 865** (Retroid Pocket 5), com o driver da
  Qualcomm do próprio sistema. Começou em ~37 fps; o ganho veio de reescrever como o trabalho da
  GPU do Xbox 360 é feito nas GPUs tiler de celular (veja [Desempenho](#desempenho)).
- Roda com o **driver do sistema** em Adreno e Mali. O **Turnip** também funciona bem (opcional).
- Modo **Tag Team** e Duelo **online** (1P vs 2P) da versão de PC.
- Launcher com **mods**, **packs de texturas HD**, **cheats**, gerenciador de drivers, opções de
  desempenho por aparelho, editor dos controles por toque e exportação de logs.
- Inglês, português (Brasil) e espanhol — escolhido automaticamente pelo idioma do celular.

## Requisitos

| | |
|---|---|
| Android | 11 ou mais novo, 64 bits (arm64-v8a) |
| GPU | Vulkan 1.1 — Adreno 6xx ou mais nova recomendada; Mali (Valhall, ex. G57/G610/G710) funciona |
| Armazenamento | ~3 GB livres (o jogo é copiado para o app, ~2,5 GB) |
| Jogo | *Dragon Ball Z: Budokai HD Collection* — cópia **USA** do Xbox 360 |

### Aparelhos testados

| Aparelho | SoC / GPU | Resultado |
|---|---|---|
| Retroid Pocket 5 | Snapdragon 865 / Adreno 650 | 60 FPS cravados na luta (driver do sistema e Turnip) |
| AYN Odin 3 | Snapdragon 8 Elite | Roda (primeiro aparelho em que o port deu boot) |
| Anbernic RG Cube | Unisoc T770 / Mali-G57 | ~22–24 FPS na luta; use o modo 30 FPS e desligue os efeitos |

Outros chips da classe do Snapdragon 865 para cima (Snapdragon 8 Gen 1/2/3, Dimensity 1100+,
Exynos recentes) devem rodar na velocidade cheia ou perto disso. Relatos são bem-vindos.

## Instalação

1. Baixe o `DragonFury-*.apk` mais recente em [Releases](../../releases) e instale.
2. Copie o `.iso` do seu disco Budokai 3 HD Collection (USA) para o aparelho.
3. Abra o Dragon Fury → **Importar .iso** → escolha o arquivo. Só o executável do Budokai 3 e os
   dados dele são extraídos da imagem (cerca de 2,5 GB, alguns minutos); o executável é conferido
   por SHA-256. Depois você pode apagar o `.iso`.
   - Já extraiu o disco? **Pasta extraída** → escolha a pasta `DBZ3` (ela tem o `yae3_xenon.xex` e
     a pasta `us/`).
4. Toque em **Jogar**.

## Launcher

| Aba | O que faz |
|---|---|
| **Início** | Animação de choque de ki, jogar, importar o jogo (.iso ou pasta), links da comunidade (YouTube, Discord, GitHub, Ko-fi), atalhos. |
| **Desempenho** | 30/60/120 FPS, resolução interna (360p–2160p), efeitos do jogo (bloom, profundidade de campo, FXAA), filtros de imagem (FSR 1, CAS, nitidez), proporção, dicas de desempenho. |
| **Controles** | Controles por toque (layouts para menus, lutas e o mapa do Dragon Universe; editor na tela, opacidade, vibração) e controle físico (zona morta, vibração). |
| **Mods** | Instalar mods a partir de `.zip` (mesmo formato da pasta `mods/` da versão de PC). |
| **Texturas** | Importar e gerenciar packs de texturas HD. |
| **Cheats** | Desbloquear todos os personagens (outros cheats estão em engenharia reversa). |
| **Drivers** | Driver do sistema ou Turnip; baixe drivers dentro do app (Mr. Purple, StevenMXZ, K11MCH1) ou importe um `.zip`. |
| **Configurações** | Áudio, idioma do jogo, interruptores de Tag Team / Online / forma inicial, orientação, tema (Dragon Fury ou Material You), idioma do app, atualizações. |
| **Depuração** | Exportar logs, log detalhado, diagnóstico de desempenho, opções internas do renderizador, correções do Turnip para testes, `cvars.txt`. |
| **Sobre** | Ficha do jogo, o projeto original (NovaPowers), este port, créditos e licença. |

No primeiro arranque o app mostra um aviso curto sobre drivers e GPUs; dá para reabrir em **Desempenho**.

## Desempenho

O jogo é leve para um celular atual, mas emular a GPU do Xbox 360 (Xenos, com os seus 10 MB de
EDRAM) numa GPU tiler de celular era o gargalo: cada "quebra de passe" obriga a GPU a gravar na
memória a cor + profundidade inteiras de 1280x720 e a ler de volta. Este port:

- mantém cada render target na sua própria imagem, em vez de copiar o conteúdo do EDRAM de um lado
  para o outro;
- escreve as cópias da tela direto na textura que o jogo lê (sem passar pela memória emulada);
- especializa os shaders traduzidos por formato de textura;
- desenha o `Clear()` do XDK (no Xbox 360, um retângulo 4x MSAA de meia largura) direto em 1x;
- tira os uploads de memória de dentro dos passes e prepara as texturas copiadas antes da hora;
- pula os draws de "restauro" e as cópias que só existiam por causa do layout do EDRAM do Xbox 360.

Resultado no Snapdragon 865: ~37 → 60 FPS (tempo de GPU por frame ~26 → ~14,5 ms). No Mali-G57:
13 → 22–24 FPS. O registro completo das medições está em [`docs/ESTADO.md`](docs/ESTADO.md) e o
método reaproveitável em [`docs/GUIA_PORT_XBOX360_ANDROID.md`](docs/GUIA_PORT_XBOX360_ANDROID.md).

### Dicas para GPUs mais fracas (Mali, Adreno 6xx abaixo do 865)

- **Desempenho → Modo 30 FPS**: o jogo continua rodando na velocidade cheia e desenha um frame sim,
  outro não, entregue num ritmo fixo de 30 FPS.
- **Desempenho → Efeitos do jogo**: desligue bloom, profundidade de campo e FXAA.
- **Desempenho → Resolução interna → 540p**: desenha 56% dos pixels e todos os tamanhos do jogo
  ficam exatos (360p é a mais leve).
- Em telas de 120 Hz, **Desempenho → Taxa de quadros → 120 (interp.)** gera um quadro entre cada
  dois (precisa de 60 FPS estáveis antes).
- Depois de cada atualização, o **autoteste do render** (menu do jogo, numa luta) diz qual
  otimização muda a imagem na sua GPU e quanto cada uma vale; no fim o zip dos logs vai para
  Download/.

## Drivers

O port foi otimizado no **driver da Qualcomm do sistema**: Snapdragon 865 para cima roda com o
driver que vem no aparelho. O **Turnip** (driver de código aberto para Adreno) também funciona bem
agora e pode gastar menos bateria em alguns aparelhos; escolha em **Drivers** se quiser. Aparelhos
Mali usam o driver do sistema.

## Tag Team

As batalhas em equipe da versão de PC: **TAG TEAM** aparece no menu principal. Monte o seu time e
troque de lutador durante a luta com **Back/Select** (o botão "trocar" dos controles por toque).
Dá para desligar em **Configurações**.

## Online (1P vs 2P)

1. Os dois jogadores precisam da **mesma versão do APK do Dragon Fury** (Android joga só com
   Android; não dá para jogar contra a versão de PC) e dos mesmos mods.
2. No menu **Duelo**, escolha a quinta linha, **ONLINE**.
3. **Anfitrião:** toque em **Criar partida**. Aparece um **código de sala** de 10 letras
   (`XXXXX-XXXXX`). **Convidado:** digite o código (ou o IP do anfitrião) e toque em **Entrar**.
4. O anfitrião toca em **Começar**; escolham **1P vs 2P** — o anfitrião é o jogador 1.
5. Aperte **X** no menu Duelo para sair.

Sobre a rede:
- No Android não há abertura automática de porta (UPnP). Na **mesma rede Wi-Fi** funciona direto
  (use o IP local do anfitrião). Pela internet, o anfitrião precisa liberar a **porta UDP 7777** no
  roteador, ou os dois usam uma VPN como Tailscale/ZeroTier.
- Dados móveis normalmente não conseguem hospedar (NAT da operadora).
- A partida roda em lockstep: conexão estável importa mais que velocidade. Pausar o app encerra a
  sessão.

## Mods, texturas e cheats

- **Mods:** mesmo formato da versão de PC (uma pasta por mod). Packs de personagens precisam vir com
  a pasta `_roster` já montada (o montador em Python não roda no Android). Mods de personagens
  precisam da cópia USA.
- **Texturas HD:** importe os packs na aba **Texturas**.
- **Cheats:** "Desbloquear todos os personagens" (não mexe no seu save). Cápsulas, Zeni e cenários
  estão em engenharia reversa.

## Compilar a partir do código

O repositório tem o projeto Android e os patches, não o jogo nem o código gerado.

1. Clone este repositório em `~/AndroidStudioProjects/DBZ3-Android`.
2. Coloque o ReXGlue SDK v0.10 em `external/rexglue-sdk-0.10` e o
   [projeto original](https://github.com/novapowers0/DBZ-Budokai-3-HD-Collection) em
   `external/dbz3`, com o `generated/` produzido pelo codegen do ReXGlue a partir do seu
   `yae3_xenon.xex`.
3. `bash tools/sync-upstream-overlay.sh && bash tools/apply-sdk-patches.sh`
4. `./gradlew assembleRelease`

Detalhes em [`docs/ESTADO.md`](docs/ESTADO.md).

## Próximos passos

- Mais otimização para GPUs Mali (shaders próprios para os ~33 shaders do jogo, renderizador nativo).
- Corrigir a piscada preta ocasional nos menus no Mali.
- Mais cheats (cápsulas, Zeni, cenários) e patch de widescreen.

## Créditos

- [novapowers0](https://github.com/novapowers0/DBZ-Budokai-3-HD-Collection) — recompilação para PC,
  Tag Team, online, mods.
- ReXGlue SDK e o projeto [Xenia](https://xenia.jp) — runtime da recompilação e emulação da GPU.
- SDL, Vulkan, libadrenotools, Turnip/Mesa e quem empacota os drivers (Mr. Purple, StevenMXZ, K11MCH1).
- Port para Android, launcher e trabalho de GPU para celular: **dfdx047**, com o Claude (Anthropic).

## Aviso

O Dragon Fury é um projeto de fã, sem ligação com a Bandai Namco, a Toei Animation ou a Microsoft.
*Dragon Ball* é marca dos seus respectivos donos. Nenhum dado do jogo protegido por direitos
autorais é distribuído; você precisa ter o jogo.
