MINI OS — MECÂNICA OFICIAL DE MOUSE, PONTEIRO VIRTUAL E TOUCH

Objetivo

Implementar no MiniOS um sistema de interação semelhante ao mouse de um PC, baseado em um ponteiro virtual.

O sistema deve funcionar com:

- mouse físico, quando disponível;
- Touch, quando o mouse físico estiver desativado ou indisponível.

IMPORTANTE: desativar o mouse físico NÃO significa remover a mecânica de mouse.

Quando o mouse físico estiver desativado, o Touch assume o papel do mouse, controlando o mesmo ponteiro virtual e utilizando as mesmas regras de clique, duplo clique, segurar, arrastar e interação.

---

1. PONTEIRO VIRTUAL

O MiniOS possui um ponteiro virtual semelhante ao cursor de um PC.

O ponteiro é uma entidade independente da posição física do dedo.

O mecanismo atual de movimento do ponteiro deve ser PRESERVADO.

Não substituir o ponteiro virtual por toque direto na interface.

Funcionamento:

TOUCH / MOUSE
      ↓
movimenta o sistema de entrada
      ↓
PONTEIRO VIRTUAL
      ↓
posição atual do ponteiro

O dedo pode estar em uma posição diferente do ponteiro.

A posição do dedo NÃO determina diretamente onde ocorre a interação.

---

2. MOUSE FÍSICO

Quando um mouse físico estiver disponível:

Mouse físico
     ↓
movimento
     ↓
ponteiro virtual

O botão esquerdo do mouse controla o clique esquerdo do MiniOS.

O comportamento deve ser semelhante ao de um computador.

---

3. TOUCH COMO MOUSE

Quando o mouse físico estiver desativado ou indisponível:

MOUSE DESATIVADO
       ↓
TOUCH ASSUME O PAPEL DO MOUSE
       ↓
controla o ponteiro virtual
       ↓
mesmas mecânicas do mouse

O Touch não deve criar uma segunda mecânica independente.

Ele deve funcionar como um dispositivo de entrada que controla o mesmo sistema de mouse virtual.

Portanto:

Mouse físico ─────┐
                  ├──→ Sistema de Mouse Virtual
Touch ────────────┘
                         ↓
                    Ponteiro
                         ↓
                    Interação

A interface não deve precisar saber se a ação veio do mouse físico ou do Touch.

---

4. CLIQUE ESQUERDO

O clique esquerdo é o botão principal do MiniOS.

Quando o clique esquerdo é acionado:

PONTEIRO
   ↓
posição atual
   ↓
elemento nessa posição
   ↓
ação correspondente

Pode interagir com:

- aplicações;
- botões;
- ficheiros;
- pastas;
- links;
- menus;
- checkboxes;
- controles;
- barras de título;
- bordas;
- scrollbars;
- outros elementos interativos.

Clique simples

clique
  ↓
verificar elemento sob o ponteiro
  ↓
executar ação correspondente

Exemplos:

Aplicação + clique → seleciona/ativa
Botão + clique → pressiona
Ficheiro + clique → seleciona
Link + clique → abre
Menu + clique → abre
Checkbox + clique → altera estado

---

5. DUPLO CLIQUE

Dois cliques rápidos na mesma região devem ser reconhecidos como duplo clique.

Exemplo:

Ponteiro
   ↓
Pasta
   ↓
clique
   ↓
clique rápido novamente
   ↓
Pasta abre

Pode ser utilizado para:

- abrir pastas;
- abrir ficheiros;
- iniciar aplicações;
- executar elementos que utilizem duplo clique.

---

6. CLIQUE + SEGURAR + ARRASTAR

Quando o clique esquerdo é pressionado e mantido:

PRESSIONAR
    ↓
SEGURAR
    ↓
MOVER PONTEIRO
    ↓
ARRASTAR

A ação depende do elemento sob o ponteiro.

Barra de título

barra de título
      ↓
segurar clique esquerdo
      ↓
mover ponteiro
      ↓
mover janela

Borda/canto

borda/canto
     ↓
segurar
     ↓
mover ponteiro
     ↓
redimensionar janela

Scrollbar

scrollbar
    ↓
segurar
    ↓
mover ponteiro
    ↓
arrastar scrollbar
    ↓
conteúdo acompanha

---

7. SCROLL

Não criar um "Scroll Mode".

A scrollbar é um elemento interativo normal.

Scroll vertical

PONTEIRO
   ↓
SCROLLBAR
   ↓
CLIQUE ESQUERDO
   ↓
SEGURAR
   ↓
MOVER
   ↓
SCROLL

Quando o conteúdo ultrapassar a área disponível:

- mostrar scrollbar vertical;
- permitir que o ponteiro interaja com ela;
- clique esquerdo + segurar permite agarrar a scrollbar;
- mover o ponteiro movimenta a scrollbar;
- o conteúdo acompanha;
- soltar o clique termina o arrasto.

Scroll horizontal

Utilizar exatamente a mesma lógica:

scrollbar horizontal
        ↓
clique esquerdo
        ↓
segurar
        ↓
arrastar
        ↓
conteúdo desloca horizontalmente

Não transformar automaticamente o movimento do dedo em scroll.

Se o Touch estiver assumindo o papel do mouse, o utilizador controla o ponteiro e interage com a scrollbar através da mecânica de mouse.

---

8. SISTEMA DE JANELAS

O clique esquerdo controla as janelas.

Foco

clique numa janela
       ↓
janela recebe foco
       ↓
fica na camada apropriada

Mover

barra de título
      ↓
segurar clique esquerdo
      ↓
mover ponteiro
      ↓
janela acompanha

Redimensionar

borda/canto
     ↓
segurar
     ↓
mover
     ↓
redimensionar

Controles

Os controles de:

- minimizar;
- maximizar;
- restaurar;
- fechar;

devem responder ao clique esquerdo.

---

9. HIT-TESTING

O MiniOS deve determinar qual elemento está exatamente sob a posição atual do ponteiro.

posição do ponteiro
        ↓
qual elemento está aqui?
        ↓
é interativo?
        ↓
qual ação suporta?
        ↓
executar ação

O hit-testing deve funcionar independentemente da origem da entrada:

Mouse físico
     ↓
     ┐
     ├──→ Ponteiro → Hit-testing → Ação
     ┘
Touch

Isso evita:

- cliques no elemento errado;
- botões que não respondem;
- menus que não recebem interação;
- scrollbars que não podem ser arrastadas;
- janelas que não podem ser movimentadas.

---

10. CAMADAS DE RENDERIZAÇÃO

O ponteiro deve ser renderizado por último.

Ordem:

Desktop
   ↓
Janelas
   ↓
Conteúdo
   ↓
Menus
   ↓
Scrollbars
   ↓
PONTEIRO

O ponteiro deve permanecer visualmente acima da interface.

Nenhum menu, janela ou conteúdo deve simplesmente desenhar-se por cima do ponteiro.

---

11. DESKTOP

Os ícones do Desktop são fixos.

Ícone
  ↓
clique esquerdo
  ↓
selecionar/abrir

Não implementar arrastamento dos ícones:

Ícone
  ↓
segurar + mover
  ↓
NÃO ARRATAR

Os ícones permanecem nas posições definidas pelo Desktop.

---

12. TASKBAR

A Taskbar utiliza a mesma mecânica.

Exemplos:

Aplicação na Taskbar + clique
        ↓
abrir/ativar aplicação

Aplicação ativa + clique
        ↓
focar/minimizar conforme o comportamento definido

Todos os controles da Taskbar devem utilizar o sistema de hit-testing do MiniOS.

---

13. FILES

O Files deve utilizar a mesma mecânica de mouse.

- clique → selecionar;
- duplo clique → abrir;
- clique + segurar + mover → arrastar somente elementos que sejam definidos como arrastáveis;
- scrollbar → clique esquerdo + segurar + arrastar;
- menus → clique;
- botões → clique.

---

14. BROWSER

O Browser deve utilizar o mesmo sistema.

- links → clique;
- botões → clique;
- abas → clique;
- controles → clique;
- scrollbar → clique esquerdo + segurar + arrastar;
- conteúdo → interação através do ponteiro;
- ponteiro → sempre acima da interface.

---

15. MEDIAPLAYEROS

O MediaPlayerOS também deve utilizar o mesmo sistema de entrada.

Corrigir:

- botões que não respondem;
- menus que não respondem;
- seleção de ficheiros;
- navegação;
- controles;
- scrollbar;
- hit-testing;
- camada do ponteiro.

O ponteiro deve permanecer acima dos menus e controles.

---

16. SETTINGS

Settings deve utilizar:

- clique esquerdo;
- seleção;
- botões;
- checkboxes;
- menus;
- opções;
- scrollbar;
- navegação.

Tudo deve passar pelo mesmo sistema de hit-testing.

---

17. REGRA FUNDAMENTAL DO TOUCH

O Touch não substitui o ponteiro virtual.

O Touch substitui o dispositivo físico de mouse quando este estiver desativado ou indisponível.

Portanto:

MOUSE ATIVO
    ↓
Mouse controla ponteiro

ou:

MOUSE DESATIVADO
    ↓
Touch controla ponteiro

Em ambos os casos:

          PONTEIRO
             ↓
       mesma posição
             ↓
       mesmo hit-testing
             ↓
       mesma ação

A interface e as aplicações não devem implementar duas lógicas diferentes.

---

18. NÃO FAZER

Não implementar:

- botão direito;
- sistema de três botões;
- Scroll Mode;
- scroll diretamente pelo movimento do dedo;
- clique diretamente na posição física do dedo;
- substituição do ponteiro por um cursor baseado no dedo;
- ícones do Desktop arrastáveis;
- gestos desnecessários;
- uma mecânica Touch separada da mecânica do mouse;
- alteração do mecanismo atual de movimento do ponteiro.

---

REGRA CENTRAL

O MiniOS deve possuir um único sistema de interação baseado em ponteiro virtual.

O mouse físico e o Touch são apenas diferentes formas de controlar esse ponteiro.

                  ┌── Mouse físico
                  │
ENTRADA ──────────┤
                  │
                  └── Touch
                        ↓
                 PONTEIRO VIRTUAL
                        ↓
                 POSIÇÃO ATUAL
                        ↓
                    HIT-TESTING
                        ↓
               ELEMENTO SOB PONTEIRO
                        ↓
               AÇÃO CORRESPONDENTE

Nunca remover o ponteiro virtual quando o mouse for desativado.

Quando o mouse estiver desativado, o Touch assume o papel do mouse e continua utilizando exatamente a mesma mecânica de ponteiro, clique esquerdo, duplo clique, segurar, arrastar, janelas e scrollbars.
