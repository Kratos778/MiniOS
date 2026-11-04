# NoskOS — Camada Windows (Wine + Box64)

**Estado:** Fase 2 — instalação e lançamento reais via PRoot/Debian.

---

## Requisitos

1. Linux do NoskOS pronto (`full-setup` no Terminal)
2. Acesso a todos os ficheiros (Android 11+)
3. Espaço livre para pacotes Wine (~200–400 MB conforme repositório)

---

## Instalação

**Settings → Windows Compatibility → Instalar camada Windows**

O que acontece:
1. `apt-get update`
2. `apt-get install wine` (fallback wine64/wine32)
3. Tentativa opcional de `box64`
4. `wineboot` no prefixo default

Prefixo default (host):
```
/sdcard/MiniOS/wine/prefixes/default
```
Dentro do Debian:
```
/sdcard/MiniOS/wine/prefixes/default
```

---

## Executar um EXE

1. Abre **Files**
2. Navega até ao `.exe` (idealmente em Download, DCIM, MiniOS, etc. — caminhos sob `/sdcard`)
3. Toca no ficheiro
4. Confirma no diálogo
5. O NoskOS corre:
   ```bash
   WINEPREFIX=/sdcard/MiniOS/wine/prefixes/default wine /sdcard/.../programa.exe
   ```

Logs em:
```
/sdcard/MiniOS/wine/logs/
```

---

## Limitações atuais (Fase 2)

- Programas **gráficos** precisam da sessão VNC/Linux Desktop a correr (`DISPLAY=:1`)
- Compatibilidade depende do pacote Wine do Debian e da arquitetura do EXE
- Box64 só ajuda se o repositório o fornecer e o binário for x86_64 Linux intermédio
- EXE em pastas privadas da app (`/data/data/...`) **não** estão montadas no Debian — usa `/sdcard`
- Ainda não há integração de janelas Wine como janelas Compose nativas (usa-se o desktop Linux/VNC)

---

## Componentes

| Classe | Papel |
|--------|--------|
| `WindowsConfig` | Caminhos host/guest, marcadores |
| `FileAssociationEngine` | Detecta EXE/MSI/BAT |
| `PrefixManager` | Prefixos em /sdcard/MiniOS/wine |
| `WindowsProcessTracker` | Processos registados |
| `WindowsManager` | Install via apt + launch via LinuxRuntime |

---

## Próximas fases

- **Fase 3:** Garantir sessão gráfica (VNC) antes de lançar GUI apps; atalhos no Start Menu
- **Fase 4:** DXVK / backends; perfis por aplicação
- **Fase 5:** Controlos virtuais / gamepad

---

*Atualizado: 04 de novembro de 2026 — Fase 2*
