# Linux 2.0 — plano do zero

O stack antigo (PRoot + Debian rootfs + TigerVNC + terminal one-shot) foi **desligado** na 0.5 porque:

- instalava RootFS pesado no dispositivo (centenas de MB → ~1GB)
- terminal não era shell interativo real
- GUI VNC frágil (locks, packages, performance)
- APK/build puxava proot no preBuild

## Objetivo futuro

Conseguir **abrir apps Linux reais** (gráficas e CLI) de forma estável, sem rebentear o telemóvel.

## Princípios

1. **Opt-in**: Linux só depois do utilizador escolher instalar (download sob demanda).
2. **Leve no APK**: zero rootfs e zero proot no APK base.
3. **Shell interativo de verdade** (PTY), não `sh -c` por comando.
4. **Apps gráficas** com caminho claro (X11/Wayland via proot/chroot ou runtime alternativo testado).
5. **Um botão “reparar / reinstal”** que não deixa o sistema a meio.

## Fora de escopo por agora

- Clique direito
- Ícones do Desktop arrastáveis
- Scroll Mode separado

## Estado atual

Código antigo em `app/.../linux/` e `apps/terminal/` **não está ligado** ao Desktop.
Quando formos reescrever, preferir nova pasta (ex. `linux2/`) em vez de reanimar o stack quebrado.
