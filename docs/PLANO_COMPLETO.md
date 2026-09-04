MINIOS — PLANO COMPLETO DE DESENVOLVIMENTO
=============================================

Documento de referência do que pretendemos criar, melhorar e integrar no MiniOS.

OBJETIVO
--------
Transformar o MiniOS numa interface desktop completa para Android, com comportamento semelhante ao de um PC, mantendo o sistema modular, leve e adequado também para dispositivos Android Go.

IMPORTANTE
----------
- Os ícones do Desktop NÃO serão arrastáveis.
- O movimento atual do ponteiro virtual NÃO deve ser alterado.
- O ponteiro é independente do dedo.
- O botão esquerdo será o principal método de interação.
- O botão direito é opcional e secundário.
- Não haverá "Scroll Mode" separado.
- O scroll deverá funcionar como num PC.
- Não usar PNG/JPG como sprites para os MiniGames.
- Imagens e vídeos originais não devem ser destruídos ou reduzidos permanentemente apenas por causa de desempenho.
- Otimizações de desempenho devem ocorrer na renderização/processamento, preservando os ficheiros originais.

============================================================
1. SISTEMA DE PONTEIRO / MOUSE
============================================================

PONTEIRO VIRTUAL
----------------
[ ] Movimento livre pela interface.
[ ] Ponteiro independente do dedo.
[ ] Manter o sistema atual de movimento do ponteiro.
[ ] Ponteiro sempre renderizado acima de Desktop, janelas, menus e conteúdos.
[ ] Cursor com posição X/Y global.
[ ] Estado de ponteiro: normal, pressionado, arrastando etc., se necessário.

BOTÃO ESQUERDO — PRINCIPAL
--------------------------
[ ] Clique simples.
[ ] Selecionar ficheiros.
[ ] Selecionar pastas.
[ ] Selecionar itens.
[ ] Ativar botões.
[ ] Abrir menus.
[ ] Ativar/desativar checkboxes.
[ ] Selecionar configurações.
[ ] Clicar em links.
[ ] Controlar MediaPlayerOS.
[ ] Interagir com Start Menu.
[ ] Interagir com Files.
[ ] Interagir com Settings.
[ ] Interagir com Browser.
[ ] Interagir com Software Center.
[ ] Interagir com futuras aplicações.

DUPLO CLIQUE
------------
[ ] Abrir aplicações.
[ ] Abrir pastas.
[ ] Abrir ficheiros.
[ ] Executar ações que necessitem de duplo clique.
[ ] Detetar dois cliques dentro de uma janela de tempo apropriada.

CLIQUE + SEGURAR
----------------
[ ] Iniciar arrasto.
[ ] Arrastar scrollbar.
[ ] Mover janela pela barra de título.
[ ] Redimensionar janela pelas bordas/cantos, quando aplicável.
[ ] Manter estado de pressionado durante o arrasto.

ARRASTAR
--------
[ ] Mover janelas.
[ ] Arrastar scrollbar.
[ ] Selecionar texto/blocos quando aplicável.
[ ] Navegar por conteúdo que suporte drag.
[ ] Não permitir arrastar os ícones do Desktop.

BOTÃO DIREITO — OPCIONAL
------------------------
[ ] Clique direito.
[ ] Menu contextual.
[ ] Abrir.
[ ] Copiar.
[ ] Colar.
[ ] Eliminar.
[ ] Renomear.
[ ] Propriedades.
[ ] Outras ações específicas por contexto.

O botão direito não é obrigatório para o funcionamento principal do MiniOS.

============================================================
2. HIT-TESTING / SISTEMA DE INTERAÇÃO
============================================================

[ ] Criar/organizar sistema de hit-testing.
[ ] Determinar qual elemento está exatamente sob o ponteiro.
[ ] Entregar o clique ao elemento correto.
[ ] Priorizar o elemento da janela em foco.
[ ] Respeitar menus sobrepostos.
[ ] Respeitar scrollbars.
[ ] Respeitar botões e controlos.
[ ] Respeitar barra de título.
[ ] Respeitar bordas de redimensionamento.
[ ] Impedir que elementos atrás de outro recebam cliques indevidos.

FLUXO:
Ponteiro
  -> posição X/Y
  -> elemento sob o ponteiro
  -> evento
  -> ação correta

============================================================
3. SCROLL ESTILO PC
============================================================

RODA DO MOUSE
-------------
[ ] Roda para cima = conteúdo sobe.
[ ] Roda para baixo = conteúdo desce.
[ ] Suporte a rolagem horizontal quando necessário.
[ ] Scroll baseado na posição atual do ponteiro.
[ ] Não alterar a posição do ponteiro durante o scroll.

SCROLLBAR
---------
[ ] Barra vertical visual estilo PC.
[ ] Barra horizontal quando necessário.
[ ] Mostrar scrollbar quando houver conteúdo fora da área visível.
[ ] Atualizar scrollbar de acordo com a posição do conteúdo.
[ ] Clicar e arrastar scrollbar com botão esquerdo.
[ ] Scrollbar deve receber corretamente os eventos do ponteiro.
[ ] Scrollbar fica acima do conteúdo.
[ ] Ponteiro fica acima da scrollbar.

APLICAÇÕES COM SCROLL
---------------------
[ ] Browser.
[ ] MediaPlayerOS.
[ ] Settings.
[ ] Files.
[ ] Software Center.
[ ] System Center.
[ ] Start Menu quando necessário.
[ ] Futuras aplicações com conteúdo longo.

Não criar um "Scroll Mode" separado.

============================================================
4. SISTEMA DE JANELAS
============================================================

[ ] Clique numa janela coloca-a em foco.
[ ] Janela em foco fica acima das outras.
[ ] Barra de título arrastável.
[ ] Mover janela pela barra de título.
[ ] Minimizar.
[ ] Maximizar.
[ ] Restaurar.
[ ] Fechar.
[ ] Redimensionar.
[ ] Redimensionar pelas bordas/cantos.
[ ] Ordem correta das janelas.
[ ] Gestão de janelas sobrepostas.
[ ] Estado da janela.
[ ] Ponteiro sempre acima de todas as janelas.
[ ] Conteúdo não deve mover a janela acidentalmente.
[ ] Apenas a barra de título deve mover a janela.

NÃO FAZER
---------
[ ] Não permitir arrastar ícones do Desktop.

============================================================
5. START MENU / LAUNCHER
============================================================

[ ] Start Menu totalmente clicável pelo ponteiro.
[ ] Selecionar aplicações.
[ ] Abrir aplicações.
[ ] Menus e submenus funcionais.
[ ] Scroll quando a lista for grande.
[ ] Scrollbar quando necessário.
[ ] Pesquisa de aplicações.
[ ] Categorias.
[ ] Aplicações favoritas.
[ ] Histórico de aplicações.
[ ] Organização por categorias.

CATEGORIAS PLANEADAS
--------------------
[ ] System.
[ ] Utilities.
[ ] Multimedia.
[ ] Internet.
[ ] Games.

============================================================
6. SISTEMA DE APLICAÇÕES DO MINIOS
============================================================

APLICAÇÕES EXISTENTES / INTEGRADAS
-----------------------------------
[ ] Files.
[ ] Terminal.
[ ] Settings.
[ ] Browser.
[ ] Software Center.
[ ] MediaPlayerOS.
[ ] SmartPlay.
[ ] Desktop.
[ ] Personalização / Wallpapers.

NOVAS APLICAÇÕES PLANEADAS
--------------------------
[ ] Calculator.
[ ] Image Viewer.
[ ] Screenshot.
[ ] System Center.
[ ] MiniGames.

============================================================
7. SYSTEM CENTER
============================================================

Criar um único centro para análise do sistema.

[ ] Informações do dispositivo.
[ ] Modelo do dispositivo.
[ ] Versão do Android.
[ ] Arquitetura.
[ ] CPU.
[ ] Informações úteis do processador.
[ ] RAM total.
[ ] RAM usada.
[ ] RAM disponível.
[ ] Armazenamento total.
[ ] Armazenamento usado.
[ ] Armazenamento livre.
[ ] Bateria.
[ ] Percentagem da bateria.
[ ] Estado de carregamento.
[ ] Temperatura quando disponível.
[ ] Aplicações instaladas.
[ ] Processos/aplicações em execução quando permitido pelo Android.
[ ] Informações de desempenho.
[ ] Atualização dos dados em tempo real ou periódico.
[ ] Interface organizada por secções.

============================================================
8. TASK MANAGER
============================================================

Pode ser integrado ao System Center ou existir como secção própria.

[ ] Ver aplicações abertas.
[ ] Ver aplicações MiniOS em execução.
[ ] Ver memória utilizada quando disponível.
[ ] Ver CPU quando disponível.
[ ] Identificar consumo de recursos.
[ ] Fechar/janela de aplicações MiniOS.
[ ] Gestão das janelas abertas.
[ ] Atualização dinâmica.
[ ] Evitar funções que o Android não permite a aplicações normais.

============================================================
9. QUICK SETTINGS
============================================================

Criar painel rápido do MiniOS.

[ ] Wi-Fi.
[ ] Bluetooth.
[ ] Brilho.
[ ] Volume.
[ ] Rotação.
[ ] Dark Mode.
[ ] Estado da bateria.
[ ] Outras opções úteis permitidas pelo Android.

NOTA:
Funções que exigem permissões especiais ou APIs protegidas devem usar apenas métodos permitidos pelo Android.

============================================================
10. NOTIFICATION CENTER
============================================================

[ ] Centro de notificações.
[ ] Visualização organizada.
[ ] Notificações recentes.
[ ] Abrir aplicações relacionadas.
[ ] Marcar/dispensar quando permitido.
[ ] Interface semelhante a desktop.
[ ] Não interferir com notificações normais do Android sem suporte apropriado.

============================================================
11. GLOBAL SEARCH
============================================================

Criar pesquisa global do MiniOS.

Pesquisar:
[ ] Aplicações.
[ ] Ficheiros.
[ ] Configurações.
[ ] Jogos.
[ ] Documentos.
[ ] Outros conteúdos indexados pelo MiniOS.

[ ] Resultados rápidos.
[ ] Abrir resultado diretamente.
[ ] Categorizar resultados.
[ ] Pesquisa pelo Start Menu.

============================================================
12. APP HISTORY
============================================================

[ ] Histórico de aplicações utilizadas.
[ ] Aplicações recentemente abertas.
[ ] Reabrir aplicação.
[ ] Limpar histórico.
[ ] Integrar com launcher.

============================================================
13. FAVORITES
============================================================

[ ] Favoritar aplicações.
[ ] Remover dos favoritos.
[ ] Mostrar favoritos no launcher.
[ ] Manter favoritos após reiniciar o MiniOS.

============================================================
14. CLIPBOARD MANAGER
============================================================

[ ] Histórico da área de transferência quando tecnicamente permitido.
[ ] Copiar.
[ ] Colar.
[ ] Selecionar item anterior.
[ ] Limpar histórico.
[ ] Interface integrada ao MiniOS.

============================================================
15. AUTO-SAVE / RESTORE
============================================================

[ ] Guardar estado das janelas.
[ ] Guardar posição das janelas.
[ ] Guardar tamanho das janelas.
[ ] Guardar aplicações abertas quando apropriado.
[ ] Restaurar estado após reinício quando possível.
[ ] Não restaurar estados inválidos ou aplicações que não estejam disponíveis.

============================================================
16. PERFORMANCE MODE
============================================================

Criar modos de desempenho para dispositivos diferentes, especialmente Android Go.

[ ] Economy.
[ ] Balanced.
[ ] Performance.

ECONOMY
-------
[ ] Reduzir efeitos.
[ ] Reduzir animações.
[ ] Reduzir atualizações não essenciais.
[ ] Menor uso de memória.
[ ] Menor carga de CPU/GPU.

BALANCED
--------
[ ] Equilíbrio entre qualidade e desempenho.

PERFORMANCE
-----------
[ ] Mais fluidez.
[ ] Maior frequência de atualização quando suportada.
[ ] Efeitos completos quando o dispositivo conseguir.

REGRA IMPORTANTE
----------------
[ ] Não reduzir ou destruir permanentemente fotos/vídeos originais.
[ ] Otimizações devem ocorrer durante a renderização/processamento.
[ ] Preservar os ficheiros originais.

============================================================
17. MEDIAPLAYEROS — REESTRUTURAÇÃO
============================================================

Objetivo: transformar o MediaPlayerOS numa aplicação realmente funcional e integrada ao MiniOS.

INTERAÇÃO
---------
[ ] Ponteiro sempre acima do menu.
[ ] Botões realmente clicáveis.
[ ] Menus funcionais.
[ ] Seleção de ficheiros.
[ ] Navegação funcional.
[ ] Controles funcionais.
[ ] Integração correta com o sistema de janelas.
[ ] Hit-testing correto.

SCROLL
------
[ ] Scroll com roda do mouse.
[ ] Scrollbar vertical.
[ ] Scrollbar horizontal quando necessário.
[ ] Arrastar scrollbar.
[ ] Navegação por conteúdo.

MEDIA
-----
[ ] Procurar ficheiros de mídia.
[ ] Selecionar ficheiros.
[ ] Abrir mídia.
[ ] Reproduzir vídeo.
[ ] Reproduzir áudio.
[ ] Pausar.
[ ] Retomar.
[ ] Parar.
[ ] Avançar.
[ ] Retroceder.
[ ] Barra de progresso.
[ ] Volume.
[ ] Mute.
[ ] Tela cheia quando apropriado.
[ ] Informações da mídia.
[ ] Lista/playlist quando aplicável.

DESEMPENHO
----------
[ ] Manter otimizações para dispositivos fracos.
[ ] Usar buffers adequados.
[ ] Preservar arquivos originais.
[ ] Suportar vídeos grandes quando possível.
[ ] Não remover qualidade do arquivo original para economizar recursos.

============================================================
18. BROWSER — MELHORIAS
============================================================

[ ] Ponteiro funcional.
[ ] Clique em links.
[ ] Seleção.
[ ] Scroll com roda.
[ ] Scrollbar vertical.
[ ] Scrollbar horizontal quando necessário.
[ ] Arrastar scrollbar.
[ ] Navegação de páginas.
[ ] Interação com páginas.
[ ] Abas.
[ ] Navegação entre abas.
[ ] Controles clicáveis.
[ ] Menus funcionais.
[ ] Janela corretamente integrada.
[ ] Ponteiro acima do conteúdo.

============================================================
19. FILES — MELHORIAS
============================================================

[ ] Ponteiro funcional.
[ ] Clique em ficheiros.
[ ] Seleção de ficheiros.
[ ] Abrir pastas.
[ ] Abrir ficheiros.
[ ] Duplo clique.
[ ] Scroll.
[ ] Scrollbar.
[ ] Arrastar scrollbar.
[ ] Navegação de diretórios.
[ ] Menus funcionais.
[ ] Context menu opcional.
[ ] Renomear quando implementado.
[ ] Eliminar quando implementado.
[ ] Copiar.
[ ] Colar.
[ ] Informações/propriedades quando implementado.

============================================================
20. SETTINGS — MELHORIAS
============================================================

[ ] Ponteiro funcional.
[ ] Todos os controlos clicáveis.
[ ] Scroll.
[ ] Scrollbar.
[ ] Arrastar scrollbar.
[ ] Menus.
[ ] Seleção de opções.
[ ] Checkboxes.
[ ] Navegação entre secções.
[ ] Interface organizada.

============================================================
21. SOFTWARE CENTER — MELHORIAS
============================================================

[ ] Ponteiro funcional.
[ ] Aplicações selecionáveis.
[ ] Botões funcionais.
[ ] Scroll.
[ ] Scrollbar.
[ ] Pesquisa.
[ ] Categorias.
[ ] Informações das aplicações.
[ ] Interface preparada para distribuição de aplicações.

============================================================
22. CALCULATOR
============================================================

Criar aplicação Calculator.

[ ] Operações básicas.
[ ] Adição.
[ ] Subtração.
[ ] Multiplicação.
[ ] Divisão.
[ ] Decimais.
[ ] Percentagem.
[ ] Apagar.
[ ] Limpar.
[ ] Histórico quando apropriado.
[ ] Interface compatível com mouse e toque.

============================================================
23. IMAGE VIEWER
============================================================

Criar Image Viewer.

[ ] Abrir imagens.
[ ] Navegar entre imagens.
[ ] Zoom.
[ ] Ajustar à janela.
[ ] Tamanho real.
[ ] Scroll quando a imagem ultrapassar a área visível.
[ ] Scrollbar quando necessário.
[ ] Rotação quando implementada.
[ ] Preservar imagem original.
[ ] Não alterar permanentemente o arquivo aberto.

============================================================
24. SCREENSHOT
============================================================

Criar ferramenta Screenshot.

[ ] Capturar o ambiente MiniOS quando permitido.
[ ] Capturar janela quando tecnicamente possível.
[ ] Guardar captura.
[ ] Escolher local de armazenamento.
[ ] Visualizar captura.
[ ] Interface simples.
[ ] Respeitar limitações e permissões do Android.

============================================================
25. MINIGAMES
============================================================

Criar UMA única aplicação chamada MiniGames.

IMPORTANTE
----------
Não criar oito aplicações separadas.
Todos os jogos ficam dentro do mesmo MiniGames.

ECRÃ PRINCIPAL
--------------
[ ] Menu de seleção de jogos.
[ ] Nome dos jogos.
[ ] Seleção pelo ponteiro.
[ ] Seleção por toque.
[ ] Navegação.
[ ] Voltar ao menu.
[ ] Sistema comum de pontuação.
[ ] Recorde.
[ ] Dificuldade/níveis.
[ ] Pausa.
[ ] Reiniciar.
[ ] Game Over.
[ ] Tela de resultado.
[ ] Controles por mouse.
[ ] Controles por teclado quando aplicável.
[ ] Controles por toque.
[ ] Código modular.
[ ] Sistema comum de estados.

REGRAS VISUAIS
--------------
[ ] Gráficos desenhados por código.
[ ] Não usar PNG/JPG como sprites.
[ ] Não usar spritesheets.
[ ] Não usar tilesets externos.
[ ] Visual procedural.
[ ] Manter bom desempenho em Android Go.

JOGO 1 — REACTION
-----------------
[ ] Teste de tempo de reação.
[ ] Estado de espera.
[ ] Sinal de reação.
[ ] Medição do tempo.
[ ] Pontuação.
[ ] Recorde.
[ ] Dificuldade progressiva.
[ ] Reiniciar.
[ ] Resultado.

JOGO 2 — MEMORY GRID
--------------------
[ ] Grelha de memória.
[ ] Mostrar sequência/padrão.
[ ] Jogador reproduz padrão.
[ ] Níveis progressivos.
[ ] Pontuação.
[ ] Recorde.
[ ] Game Over.
[ ] Reiniciar.

JOGO 3 — DODGE
--------------
[ ] Desviar de obstáculos.
[ ] Movimento do jogador.
[ ] Obstáculos progressivos.
[ ] Pontuação por sobrevivência.
[ ] Dificuldade progressiva.
[ ] Game Over.
[ ] Recorde.
[ ] Reiniciar.

JOGO 4 — TARGET
---------------
[ ] Alvos gerados na arena.
[ ] Clicar/tocar nos alvos.
[ ] Tempo limitado quando aplicável.
[ ] Pontuação.
[ ] Alvos progressivamente mais difíceis.
[ ] Recorde.
[ ] Níveis.
[ ] Reiniciar.

JOGO 5 — RUNNER
---------------
[ ] Personagem/objeto controlado pelo jogador.
[ ] Obstáculos.
[ ] Movimento contínuo.
[ ] Pontuação por distância.
[ ] Dificuldade progressiva.
[ ] Game Over.
[ ] Recorde.
[ ] Reiniciar.

JOGO 6 — COLOR MATCH
--------------------
[ ] Desafio de correspondência de cores.
[ ] Escolha correta.
[ ] Tempo/desafio progressivo.
[ ] Pontuação.
[ ] Níveis.
[ ] Recorde.
[ ] Game Over.
[ ] Reiniciar.

JOGO 7 — CLICK CHALLENGE
------------------------
[ ] Desafios rápidos de clique.
[ ] Objetivos variados.
[ ] Tempo limitado quando aplicável.
[ ] Pontuação.
[ ] Recorde.
[ ] Níveis.
[ ] Dificuldade progressiva.
[ ] Reiniciar.

JOGO 8 — MINI DEFENDER
----------------------
[ ] Arena de defesa.
[ ] Inimigos gerados por código.
[ ] Movimento/controlos.
[ ] Ataque/defesa.
[ ] Ondas.
[ ] Pontuação.
[ ] Níveis.
[ ] Dificuldade progressiva.
[ ] Game Over.
[ ] Recorde.
[ ] Reiniciar.

============================================================
26. ARQUITETURA DOS MINIGAMES
============================================================

[ ] GameManager.
[ ] GameState.
[ ] MenuManager.
[ ] ScoreManager.
[ ] HighScore/RecordManager.
[ ] DifficultyManager.
[ ] InputManager.
[ ] PauseManager.
[ ] Renderização procedural.
[ ] Sistema comum de colisões quando necessário.
[ ] Object pooling quando necessário.
[ ] Delta time.
[ ] requestAnimationFrame/equivalente quando aplicável.
[ ] Evitar alocações desnecessárias.
[ ] Código modular.

============================================================
27. PERSONALIZAÇÃO / WALLPAPERS
============================================================

[ ] Melhorar personalização.
[ ] Wallpapers.
[ ] AnimatedWallpaper.
[ ] Seleção de wallpaper.
[ ] Preservar mídia original.
[ ] Evitar processamento destrutivo.
[ ] Otimizações específicas para Android Go.
[ ] Modos de qualidade quando apropriado.

============================================================
28. MINI OS STORE — FUTURO
============================================================

Criar futuramente uma loja dentro do MiniOS para distribuição de conteúdo.

[ ] Distribuição de aplicações.
[ ] Distribuição de temas.
[ ] Distribuição de extensões.
[ ] Página de cada aplicação.
[ ] Sistema de categorias.
[ ] Pesquisa.
[ ] Instalação.
[ ] Atualizações.
[ ] Sistema de contas quando necessário.
[ ] Sistema de pagamentos quando necessário.
[ ] Modelo de receita para aplicações/conteúdo de terceiros.
[ ] Percentagem da plataforma quando legal e tecnicamente estruturada.
[ ] Proteção dos direitos do projeto MiniOS.

============================================================
29. NOTIFICAÇÕES E ESTADO GLOBAL
============================================================

[ ] Estado global do MiniOS.
[ ] Comunicação entre componentes quando necessária.
[ ] Eventos globais.
[ ] Notificações internas.
[ ] Atualizações de estado.
[ ] Não duplicar sistemas existentes sem necessidade.

============================================================
30. INTEGRAÇÃO COM ANDROID
============================================================

[ ] Detectar aplicações instaladas quando permitido.
[ ] Abrir aplicações Android pelo package name.
[ ] Integrar SmartPlay corretamente.
[ ] Respeitar AndroidManifest.
[ ] Usar APIs oficiais.
[ ] Respeitar permissões.
[ ] Não depender de acesso impossível ao sistema.
[ ] Compatibilidade com versões Android suportadas pelo projeto.

============================================================
31. DESEMPENHO / ANDROID GO
============================================================

[ ] Baixo consumo de RAM.
[ ] Baixo uso de CPU.
[ ] Evitar recomposições desnecessárias.
[ ] Evitar animações pesadas.
[ ] Lazy loading em listas grandes.
[ ] Atualizações periódicas em vez de loops excessivos quando possível.
[ ] Object pooling em jogos/sistemas apropriados.
[ ] Limitar efeitos gráficos em Economy.
[ ] Manter interface responsiva.
[ ] Não sacrificar arquivos originais.
[ ] Testar em hardware fraco.

============================================================
32. UX / INTERFACE DESKTOP
============================================================

[ ] Interface coerente entre aplicações.
[ ] Janelas com comportamento consistente.
[ ] Barra de título consistente.
[ ] Botões consistentes.
[ ] Scrollbars consistentes.
[ ] Ponteiro consistente.
[ ] Menus consistentes.
[ ] Estados de foco/seleção visíveis.
[ ] Feedback visual de clique.
[ ] Feedback visual de hover quando suportado.
[ ] Interface adaptada para toque e mouse.
[ ] Experiência semelhante a um PC.

============================================================
33. COMPATIBILIDADE DE INPUT
============================================================

[ ] Mouse físico.
[ ] Ponteiro virtual.
[ ] Toque.
[ ] Teclado físico quando disponível.
[ ] Teclado virtual quando necessário.
[ ] Gamepad quando aplicável.
[ ] Não quebrar o sistema de ponteiro existente ao adicionar novos inputs.

============================================================
34. PRIORIDADE DE IMPLEMENTAÇÃO
============================================================

FASE 1 — FUNDAÇÃO
-----------------
[ ] Corrigir hit-testing.
[ ] Fazer clique esquerdo funcionar globalmente.
[ ] Fazer seleção funcionar.
[ ] Fazer duplo clique funcionar.
[ ] Fazer clique + segurar funcionar.
[ ] Fazer arrasto funcional.
[ ] Colocar ponteiro acima de toda a interface.
[ ] Corrigir foco das janelas.

FASE 2 — SCROLL
---------------
[ ] Scroll com roda.
[ ] Scrollbar vertical.
[ ] Scrollbar horizontal quando necessário.
[ ] Arrastar scrollbar.
[ ] Aplicar primeiro em Browser, MediaPlayerOS, Settings e Files.

FASE 3 — JANELAS
----------------
[ ] Mover.
[ ] Redimensionar.
[ ] Minimizar.
[ ] Maximizar.
[ ] Restaurar.
[ ] Fechar.
[ ] Ordem/foco.

FASE 4 — APLICAÇÕES
-------------------
[ ] Corrigir MediaPlayerOS.
[ ] Melhorar Browser.
[ ] Melhorar Files.
[ ] Melhorar Settings.
[ ] Melhorar Software Center.
[ ] Garantir SmartPlay.

FASE 5 — NOVAS FERRAMENTAS
--------------------------
[ ] System Center.
[ ] Calculator.
[ ] Image Viewer.
[ ] Screenshot.

FASE 6 — EXPERIÊNCIA DO SISTEMA
-------------------------------
[ ] Task Manager.
[ ] Global Search.
[ ] Quick Settings.
[ ] Notification Center.
[ ] App History.
[ ] Favorites.
[ ] Clipboard Manager.
[ ] Auto-save/Restore.
[ ] Performance Mode.
[ ] Launcher por categorias.

FASE 7 — MINIGAMES
------------------
[ ] Criar estrutura MiniGames.
[ ] Reaction.
[ ] Memory Grid.
[ ] Dodge.
[ ] Target.
[ ] Runner.
[ ] Color Match.
[ ] Click Challenge.
[ ] Mini Defender.

FASE 8 — FUTURO
---------------
[ ] MiniOS Store.
[ ] Mais aplicações.
[ ] Mais personalização.
[ ] Mais integração com Android.
[ ] Melhorias contínuas de desempenho.

============================================================
35. REGRAS DEFINITIVAS DO PROJETO
============================================================

1. O Desktop não terá ícones arrastáveis.

2. O ponteiro virtual continua independente do dedo.

3. Não alterar o mecanismo atual de movimento do ponteiro sem necessidade.

4. O botão esquerdo é o botão principal.

5. O botão direito é opcional e serve principalmente para menus contextuais.

6. O scroll deve funcionar como num PC.

7. Áreas com conteúdo maior que a janela devem ter scrollbar visual.

8. A scrollbar deve poder ser arrastada com o botão esquerdo.

9. O ponteiro deve ser renderizado acima de todos os elementos.

10. O hit-testing deve entregar os eventos ao elemento que realmente está sob o ponteiro.

11. Janelas são movidas pela barra de título, não pelo conteúdo.

12. Todas as aplicações devem seguir uma lógica consistente de mouse, scroll e foco.

13. MediaPlayerOS deve deixar de ser apenas uma interface visual e tornar-se funcional.

14. MiniGames será uma única aplicação com vários jogos internos.

15. MiniGames não usará sprites PNG/JPG, spritesheets ou tilesets externos.

16. O projeto deve continuar leve e compatível com Android Go.

17. Performance Mode deve otimizar a execução sem destruir os ficheiros originais.

18. Novos recursos devem ser integrados à arquitetura existente em vez de criar sistemas duplicados.

19. Funções limitadas pelo Android devem ser implementadas somente através das APIs/permissões permitidas.

20. O objetivo final é fazer o MiniOS comportar-se como um verdadeiro ambiente desktop no Android, e não apenas parecer um desktop.

============================================================
FIM DO DOCUMENTO
============================================================
