# NoskOS

Desktop estilo PC num APK Android.

| | |
|---|---|
| **Package** | `com.minios.elizierdias` |
| **Nome visível** | NoskOS |
| **Versão** | 0.5.0 |
| **Licença** | **Proprietária — All Rights Reserved** |

> **Aviso legal:** Este projeto é software proprietário.  
> É proibido copiar, modificar, distribuir, fazer fork ou reutilizar o código
> sem autorização prévia e por escrito do autor.  
> Ver ficheiro `LICENSE` e `COPYRIGHT.md`.

## O que funciona (0.5)

- Desktop **landscape** (toque normal)
- **Wallpaper**: gradientes, foto, gif ou vídeo
- Janelas (mover, **redimensionar** por bordas/canto, min/max/fechar)
- Taskbar + Start Menu
- **Files** com acesso ao armazenamento do telefone
- Settings, Browser, MediaPlayerOS, Software Center
- Ponteiro virtual (só clique esquerdo — direito fora de escopo)

## Removido nesta versão

- **Terminal / subsistema Linux (PRoot + Debian + VNC)** — desligado da UI e do build.
- O código antigo de Linux fica no repositório só como referência; **vai ser reescrito do zero** para correr apps Linux reais de forma estável e leve.

## Limpar dados antigos no telemóvel

Se tinhas RootFS instalado (~centenas de MB / 1GB):

1. Definições Android → Apps → NoskOS → **Limpar armazenamento / dados**
2. Ou apaga manualmente pastas tipo `/sdcard/MiniOS` se existirem
3. Reinstala o APK 0.5

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
