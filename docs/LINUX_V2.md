# Linux no NoskOS — atualização (não descontinuação)

O subsistema Linux **permanece parte central do projeto**.
O objetivo é atualizá-lo até apps Linux reais (CLI + GUI) funcionarem de forma estável.

## Stack atual (mantido)

- **PRoot** (UserLAnd arm64) embutido no APK via `jniLibs`
- **Debian ARM64** RootFS (instalação sob demanda no dispositivo)
- **Terminal** via `LinuxSession` + `LinuxRuntime.exec`
- **GUI** TigerVNC + Openbox + RFB viewer na app **Linux**

## Problemas conhecidos a corrigir (prioridade)

1. **Instalação frágil** — multi-passos (`install` → `setup-runtime` → …); falhas a meio deixam estado inconsistente
2. **Terminal one-shot** — cada comando é `proot … sh -c`, não PTY interativo (vim/top sofrem)
3. **VNC** — locks stale, packages em falta, geometry, input RFB
4. **Peso no dispositivo** — RootFS ocupa centenas de MB depois de instalado (opt-in; APK base só traz proot)
5. **Feedback de erro** — mensagens pouco claras quando falta permissão / DNS / dpkg

## Direção da atualização

| Fase | Objetivo |
|------|----------|
| A | Wizard único de setup (install+runtime+dns+storage) com progresso e retry |
| B | Terminal mais robusto (timeouts, repair, status, multi-linha estável) |
| C | VNC: start/stop fiável + lançar apps (xterm, dillo) sem locks mortos |
| D | (Futuro) shell interativo / PTY se for viável em PRoot |

## Princípios

1. **Atualizar**, não remover o Linux da UI
2. RootFS continua **opt-in** (utilizador corre `install`)
3. APK base leve: só libs proot; Debian baixa sob demanda
4. Um fluxo de **reparar / reinstall** claro
5. Clique direito e ícones arrastáveis continuam fora de escopo do desktop Nosk

## Ficheiros principais

- `linux/LinuxManager.kt`, `LinuxRuntime.kt`, `LinuxRootFs.kt`, `LinuxSession.kt`
- `linux/LinuxGuiRuntime.kt`, `linux/vnc/*`
- `apps/terminal/TerminalApp.kt`
- `apps/linuxgui/LinuxDesktopApp.kt`
