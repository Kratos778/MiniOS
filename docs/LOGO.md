# Logo MiniOS

## Adaptive icon (launcher)

O ícone do app usa:

- `res/mipmap-anydpi-v26/ic_launcher.xml`
- `res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Foreground: `res/drawable/ic_launcher_foreground.xml` (vector MiniOS)
- Background: `@color/ic_launcher_background` (`#0D1117`)

O `AndroidManifest.xml` referencia `@mipmap/ic_launcher` e `@mipmap/ic_launcher_round`.

## Foto do logo (`logo_final`)

Nomes de resources Android **só** podem usar `a-z`, `0-9` e `_`.

| Errado | Correto |
|--------|---------|
| `logo-final.jpg` | `logo_final.jpg` ou `logo_final.png` |

### Como usar a imagem fotográfica no launcher

1. Renomeia localmente:
   ```bash
   mv app/src/main/res/drawable/logo-final.jpg \
      app/src/main/res/drawable/logo_final.jpg
   ```
2. Opcional — foreground bitmap no adaptive icon:
   ```xml
   <!-- ic_launcher.xml -->
   <foreground android:drawable="@drawable/logo_final" />
   ```
3. Preferível: PNG 432×432 com a marca centrada na safe zone.

O ficheiro com hífen (`logo-final.jpg`) foi removido do projeto porque o AAPT2 rejeita o nome.
