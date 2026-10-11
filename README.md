# DBZ3-Android

Port Android do *Dragon Ball Z: Budokai 3 HD Collection* (recompilação ReXGlue),
baseado em [novapowers0/DBZ-Budokai-3-HD-Collection](https://github.com/novapowers0/DBZ-Budokai-3-HD-Collection).

## Estrutura local

```
DBZ3-Android/
├── external/              ← ignorado pelo git, fica só no PC
│   ├── rexglue-sdk-0.10/  ← SDK ReXGlue v0.10.0 + overlay do projeto
│   └── dbz3/              ← projeto original, com generated/ do codegen
└── ...                    ← projeto Android (Gradle + NDK)
```

O `generated/` é produzido localmente a partir do `yae3_xenon.xex` da tua cópia
e nunca vai para o repositório. Nenhum arquivo do jogo é distribuído.

## Compilar

Ver [docs/ESTADO.md](docs/ESTADO.md): passos de build, o que o patch Android
muda no SDK e a lista de próximos passos.
