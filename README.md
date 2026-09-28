# NoskOS

Desktop estilo PC num APK Android.

| | |
|---|---|
| **Package** | `com.minios.elizierdias` |
| **Nome visível** | NoskOS |
| **Versão** | 0.5.1 |
| **Licença** | **Proprietária — All Rights Reserved** |

> **Aviso legal:** Este projeto é software proprietário.  
> É proibido copiar, modificar, distribuir, fazer fork ou reutilizar o código
> sem autorização prévia e por escrito do autor.  
> Ver ficheiro `LICENSE` e `COPYRIGHT.md`.

## O que funciona

- Desktop **landscape** (toque normal)
- **Wallpaper**: gradientes, foto, gif ou vídeo
- Janelas (mover, redimensionar por bordas/canto, min/max/fechar)
- Taskbar + Start Menu
- **Files** com acesso ao armazenamento do telefone
- Terminal, Settings, Software Center, Browser, MediaPlayerOS
- **Subsistema Linux** (PRoot + Debian + TigerVNC) — **em atualização**, não descontinuado
- Ponteiro virtual (clique esquerdo)

## Linux (atualização contínua)

O stack Linux **não foi abandonado**. Está a ser estabilizado para:

1. Instalação mais fiável (RootFS + PRoot + DNS + storage)
2. Terminal utilizável de forma previsível
3. Apps Linux gráficas reais via VNC (Openbox, xterm, Dillo, etc.)

Ver `docs/LINUX_V2.md` para o plano de melhorias.

### Setup rápido no Terminal

1. `install` (RootFS)
2. `setup-runtime`
3. `setup-storage`
4. `setup-dns`
5. No app **Linux**: Controlo → Pacotes → Iniciar

## Wallpaper de foto

1. Abre **Settings**
2. **Escolher foto da galeria**
3. A foto preenche o ecrã em landscape

## Armazenamento (Files)

1. Abre **Files**
2. Toca **Conceder permissão**
3. No Android 11+: ativa **Acesso a todos os ficheiros**
4. Navega em Downloads, Pictures, DCIM, etc.

## Build

Push na `main` → GitHub Actions → artifact `NoskOS-debug.apk`

## Licença

**Proprietária (All Rights Reserved)**  
Copyright © 2026 Elizier Layerti Gungui Dias.  
Ver `LICENSE` e `COPYRIGHT.md` para os termos completos.
