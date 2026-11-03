# NoskOS — Camada Windows (Wine + Box64)

Documento de referência da camada de compatibilidade Windows.

**Estado atual:** Fundação (Fase 1) — modelos, gestores e contratos criados.  
A execução real via Wine/Box64 será ligada na Fase 2.

---

## Princípios

1. A camada é **opcional** e instalada sob demanda.
2. O Linux (Debian + PRoot) continua a ser a base.
3. Nunca se assume compatibilidade universal.
4. EXE / MSI / BAT / CMD exigem confirmação do utilizador.
5. Prefixos Wine são persistentes.

---

## Estrutura de pastas

```
/data/data/com.minios.elizierdias/files/windows_runtime/
├── prefixes/
│   └── default/              ← prefixo Wine global
├── runtime/                  ← Wine + Box64 (quando instalados)
├── logs/
└── configs/
    ├── app_profiles.json
    └── file_associations.json
```

---

## Componentes (pacote `com.minios.elizierdias.windows`)

| Classe | Responsabilidade |
|--------|------------------|
| `WindowsConfig` | Caminhos e marcadores |
| `WindowsModels` | Data classes e enums |
| `FileAssociationEngine` | Decide como abrir cada tipo de ficheiro |
| `PrefixManager` | Cria / lista / apaga prefixos Wine |
| `WindowsProcessTracker` | Regista processos Windows em execução |
| `WindowsManager` | Orquestra instalação, lançamento e estado |

---

## Fluxo de abertura de um EXE

1. Utilizador escolhe o ficheiro no Files.
2. `FileAssociationEngine.resolve()` identifica como `EXECUTABLE_WINDOWS`.
3. `WindowsManager.prepareLaunch()` devolve `ConfirmationRequired`.
4. UI mostra diálogo com caminho + tamanho.
5. Após confirmação → `launchConfirmed()`.
6. Processo é registado no `WindowsProcessTracker`.
7. (Fase 2) Comando Wine é executado via LinuxRuntime/PRoot.

---

## Fases de implementação

| Fase | Conteúdo |
|------|----------|
| **1 (atual)** | Modelos, gestores, associação de ficheiros, estrutura de pastas |
| **2** | Instalação real de Wine + Box64, execução de EXE/CMD via PRoot |
| **3** | Integração gráfica (sessão VNC/Openbox) |
| **4** | DXVK / backends gráficos, perfis por aplicação |
| **5** | Controlos virtuais / gamepad |
| **6** | Integração avançada de janelas (opcional) |

---

## Segurança

- Confirmação obrigatória antes de executar qualquer EXE/MSI.
- Pastas partilhadas devem ser limitadas.
- Não executar automaticamente ficheiros ao navegar.
- Logs não devem expor dados privados desnecessários.

---

## Relação com o Linux existente

A camada Windows **reutiliza** o `LinuxRuntime` / PRoot já presente no NoskOS.  
Não cria um segundo container isolado na Fase 1/2.  
Wine corre dentro do mesmo ambiente Debian ARM64.

---

*Última atualização: 03 de novembro de 2026*
