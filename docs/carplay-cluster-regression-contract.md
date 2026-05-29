# Contrato de Regressao: CarPlay Display 0 / Cluster 3

Este arquivo e a fonte de verdade para qualquer mudanca em CarPlay entre a central nativa
(`displayId=0`) e o cluster (`displayId=3`). Se uma correcao nova violar este contrato, ela
deve ser rejeitada antes de deploy.

## Objetivo

Alternar o CarPlay entre display 0 e cluster 3 sem perder a sessao do telefone, sem tela preta
permanente, sem buffer cinza/sujo e sem exigir tirar/recolocar o cabo.

## Contrato Atual

1. CarPlay sempre usa a Activity nativa:
   `com.ts.carplay.app/.ui.display.view.CarPlayDisplayActivity`.
2. Display 0 e cluster 3 usam sempre fullscreen fisico: `[0,0][largura,altura]`.
3. `persist.haval.carplay.video.height` deve ficar sempre em `720`.
4. Retorno do CarPlay de display secundario para display 0 usa `am display move-stack` somente
   para preservar a Surface viva. Envio do display 0 para cluster continua recriando a Activity.
5. Troca de display do CarPlay nao usa `am force-stop com.ts.carplay.app`.
6. Ao enviar para cluster, a troca cria primeiro a nova Activity no display alvo e so remove
   duplicatas depois que a nova stack recebeu fullscreen, refresh e foco. A stack antiga nao deve
   ser removida antes da nova Surface existir, para evitar intervalo sem Surface e decoder preso.
   Ao voltar para display 0, a troca move a stack viva e limpa apenas duplicatas.
7. Os servicos `com.ts.carplay/.CarPlayService` e
   `com.ts.carplay.app/.service.CarPlayRemoteService` devem ser mantidos vivos antes de abrir a
   Activity.
8. O projector do cluster nunca redimensiona CarPlay. Ele deve ignorar `com.ts.carplay.app`.
9. Quando CarPlay ou Android Auto esta realmente no cluster 3, o projector aplica automaticamente o
   display efetivo `Mapa` e o tema `theme-mirror-cluster`, sem gravar essa escolha nas preferencias.
   Ao sair, voltar ao display 0, desconectar ou perder a task real no display 3, o cluster volta ao
   display salvo pelo usuario.
10. No modo `Mapa` automatico por projecao, os widgets do mapa que compoem a barra lateral/rodape
    (velocidade, autonomia/combustivel/bateria, EV, temperaturas e gauges inferiores) devem
    permanecer visiveis. Os backgrounds `.display-mapa .mask-top-bar` e
    `.display-mapa .dashboard-speed-content` fazem parte protegida do display `Mapa` e nao devem
    ser removidos pelo tema de espelhamento. Mascaras, circulos, velocimetro esportivo,
    barras/molduras invasivas e alertas que cobrem a projecao devem ficar ocultos. Excecao
    permitida: navegacao fisica por cards pode mostrar overlays transparentes de `main_menu`
    (`cardId=1`) e AC (`cardId=3`) sobre a projecao, desde que nao pintem fundo opaco, nao movam,
    nao reiniciem e nao pausem o CarPlay.
11. Camera, AVM, RVC, ar-condicionado/HVAC e UI nativa nao podem reiniciar, mover ou forcar stop
    do CarPlay.
12. Tocar no icone do CarPlay no display 0 significa recriar CarPlay no display 0.
13. Enviar CarPlay para cluster significa recriar CarPlay no display 3.
14. Fechar CarPlay pelo app remove somente a stack visual; a sessao/servicos ficam vivos.
15. Patch nativo do `TsCarPlayApp.apk` nao deve ser remontado automaticamente enquanto a variante
    atual estiver marcada como insegura. Em 2026-05-24, o patch v2.4 montado gerou Surface viva com
    frame branco/sujo no display 0 e depois deixou o decoder sem frames; ate novo patch validado, o
    app deve manter o APK stock.
16. A deteccao de CarPlay no cluster 3 deve depender do estado real do display 3 (`am stack list`
    ou foco/top package), nao apenas do alvo desejado salvo em preferencias.
17. A recuperacao permitida para tela preta apos handoff e: pedir `REFRESH_RENDER`, reenviar foco
    de video e recriar a Activity visual preservando a stack antiga ate a nova Surface estar pronta.
    Sessao desconectada isolada deve ficar em refresh/foco e diagnostico de USB/host, sem reiniciar
    `com.ts.carplay.app`. Reinicio de host `com.ts.carplay` fica reservado para recuperacao
    manual/diagnostica, pois pode derrubar a sessao USB em alguns estados.
18. Se CarPlay nao esta realmente no cluster 3, o cluster deve voltar ao display salvo pelo usuario
    (`Normal` por padrao). Se o usuario escolheu `Mapa`, essa escolha manual deve ser preservada.
19. A seta esquerda da bottom bar deve priorizar a projecao real ativa no display 0. Se CarPlay esta
    visivel no display 0, enviar ao cluster nao pode usar `selectedPackage` stale de outro app.
20. Se o Android reaproveitar a instancia top-most do CarPlay no display 0 ao tentar `0 -> 3`, o
    fluxo deve trazer um app nao-projecao do display 0 para frente e repetir o start no cluster,
    sem mover ou remover a stack antiga antes da nova Surface existir.
21. Abrir app, camera/AVM ou HVAC no display 0 enquanto o alvo desejado do CarPlay e cluster 3 pode
    acionar apenas um guardiao pontual, atrasado e com cooldown. Nas passes iniciais, se a Activity
    real do CarPlay segue no cluster 3, o guardiao deve ficar em verify-only: sem
    `VIDEO_FOCUS_CHANGE`, sem `view_state foreground`, sem `startservice` e sem `am stack resize`.
    Mesmo o broadcast "lite" `com.ts.carplay.action.VIDEO_FOCUS_CHANGE` renegocia a rota do decoder
    (`cpScreen`/`NdkMediaCodec`) e produz 1-2 frames pretos no cluster quando camera/AC/janelas
    nativas ganham foco no display 0. Se, apos double-check, o CarPlay continua **realmente** no
    display 3, com fullscreen correto `[0,0][1920,720]`, sem duplicata sustentada no display 0, o
    guardiao pode fazer **uma tentativa tardia e isolada de `REFRESH_RENDER`** (sem foco de video)
    para recuperar somente a Surface. Se a Activity realmente saiu para o display 0, o guardiao
    confirma a saida com um segundo check apos `delay(550ms)` antes de disparar o restore `0 -> 3`
    permitido. Nao pode haver loop nem disputa imediata com a transicao nativa.
22. Mudanca de janela nativa no display 0 (Accessibility `TYPE_WINDOW_STATE_CHANGED`), incluindo
    camera/AVM/HVAC que nao aparecem em `DisplayAppConfig`, deve passar pelo guardiao pontual do
    CarPlay antes de qualquer `return` por app nao configurado. O guardiao deve ser debounced para
    nao brigar com a animacao/transicao da UI nativa. Na central real o caminho de janela nativa
    deve permanecer verify-only nas passes iniciais enquanto a task real do CarPlay esta viva no
    cluster 3 — sem broadcast de video, sem `force-stop`, sem resize parcial, sem `view_state
    foreground` e sem `move-stack` no sentido proibido. A unica excecao permitida e um
    `REFRESH_RENDER` tardio, unico e com cooldown forte, somente se o contrato fullscreen real do
    cluster continuar correto apos double-check e nao houver duplicata sustentada no display 0. O
    restore `0 -> 3` permitido so dispara apos confirmacao com double-check de que a Activity
    realmente settou no display 0.
23. O frontend do cluster deve manter classes de projecao (`theme-mirror-cluster`,
    `projection-mirror-in-dash`, `projection-map-display-active`) sempre sincronizadas com a
    realidade do display 3. O `InstrumentProjector2` deve forcar push (bypass do dedup cache) do
    estado real de `carPlayInDash`/`projectionMirrorInDash` **antes** de propagar `cardId` para a
    WebView, para evitar uma janela de race em que o JS re-renderiza `screen-aircon` ou
    `screen-main-menu` enquanto `carPlayInDash` ainda esta stale-`false` no estado JS. Adicionalmente,
    o CSS deve aplicar fundo transparente em `.main-container`, `.main-menu-container`,
    `.menu-carousel` e `.ac-circle-container` sempre que `theme-mirror-cluster` esta ativo, como
    rede de seguranca contra qualquer frame intermediario.
24. Como o host nativo do CarPlay pode recriar sozinho a Activity visual no display 0 alguns
    segundos apos HVAC/camera/app nativo ganhar foco, deve existir um watchdog leve baseado em
    `am stack list` quando o alvo desejado e cluster 3. Esse watchdog nao pode tocar a rota de video
    se a task real continua no display 3; ele so pode remover duplicata no display 0 quando tambem
    existe stack viva no cluster, ou restaurar `0 -> 3` apos confirmar que o CarPlay ficou
    sustentado no display 0. O watchdog nao substitui eventos manuais nem altera Android Auto.
25. Quando CarPlay esta realmente no display 3 e um painel nativo do display 0 esta ativo
    (`sys.avm.preview_status` ou `car.hvac.panel_display_notify`), o app principal pode esconder
    temporariamente apenas a sua propria `Presentation` fullscreen para nao cobrir a Surface nativa
    do CarPlay: `windowAlpha=0`, `root/WebView` invisiveis. Ao fechar o painel ou quando CarPlay
    deixar o display 3, a `Presentation` deve restaurar `windowAlpha=1`. Esse bypass nao pode mover,
    reiniciar, redimensionar ou enviar broadcast de foco ao CarPlay e nao deve ser aplicado ao
    Android Auto sem validacao separada.

## Operacoes Proibidas Para CarPlay

- `am display move-stack` com `com.ts.carplay.app` fora do handoff
  `cluster/display secundario -> 0` usado para preservar a Surface viva do CarPlay.
- `am force-stop com.ts.carplay.app` em troca de display, camera, HVAC ou clique de usuario.
- Resize parcial no cluster quando `com.ts.carplay.app` esta no display 3.
- Loops de pulse/retry/recover baseados em camera, ar-condicionado ou mudanca de foco.
- Gravar `display=Mapa` como preferencia do usuario apenas porque CarPlay/Android Auto entrou no
  cluster; o override de `Mapa` por projecao deve ser transitorio/efetivo.
- Alterar video height para `540`.
- Usar Activity antiga `com.ts.carplay.app.display.AapActivity`.
- Criar stack vazia antes de abrir a Activity do CarPlay.

## Fluxo Unico Permitido

### Abrir no Display 0

1. Gravar alvo desejado como `0`.
2. Garantir `persist.haval.carplay.video.height=720`.
3. Garantir servicos do CarPlay vivos.
4. Se CarPlay esta vivo em display secundario, mover a stack viva para display 0:

```bash
am display move-stack <CARPLAY_STACK_ID> 0
```

5. Aplicar fullscreen do display 0 e enviar broadcasts de foco de video do CarPlay.
6. Limpar somente duplicatas, preservando a stack movida.
7. Se nao existe CarPlay vivo em display secundario, remover stacks visuais antigas e abrir:

```bash
am start --display 0 --windowingMode 1 --activity-multiple-task -f 0x18000000 \
  -n com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity
```

8. Notificar display 3 para limpar estado antigo do projector.

### Abrir no Cluster 3

1. Gravar alvo desejado como `3`.
2. Evitar que outro app fique no display 3.
3. Garantir `persist.haval.carplay.video.height=720`.
4. Garantir servicos do CarPlay vivos.
5. Preservar qualquer stack visual antiga de `com.ts.carplay.app` ate a nova Surface existir no
   cluster.
6. Se CarPlay estiver top-most no display 0, trazer um app nao-projecao do display 0 para frente
   antes do start no cluster. Isso evita o retorno "currently running top-most instance" do
   ActivityManager sem usar `force-stop` nem `move-stack` no sentido proibido.
7. Abrir:

```bash
am start --display 3 --windowingMode 5 --activity-multiple-task -f 0x18000000 \
  -n com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity
```

8. Aplicar resize fullscreen do cluster 3.
9. Pedir refresh de renderizacao da Surface quando o patch nativo suporta
   `br.com.redesurftank.havalshisuku.carplay.REFRESH_RENDER`.
10. Enviar broadcasts de foco de video do CarPlay.
11. Remover somente duplicatas visuais antigas, preservando a stack do cluster.
12. Aplicar display efetivo `Mapa` + `theme-mirror-cluster`: widgets de mapa ficam visiveis sobre o
   CarPlay no display 3, enquanto mascaras e velocimetro esportivo ficam ocultos. Menus e HVAC
   ficam ocultos por padrao, exceto quando acionados por navegacao fisica de cards como overlays
   transparentes (`cardId=1` para `main_menu`, `cardId=3` para AC).

## Matriz Minima de Teste

| Cenario | Resultado esperado |
| --- | --- |
| CarPlay no display 0, tocar icone CarPlay | CarPlay aparece no display 0 em fullscreen, sem menu nativo por cima |
| Enviar CarPlay 0 -> 3 | Cluster 3 exibe CarPlay em fullscreen com display `Mapa` automatico |
| Com CarPlay no 3, acionar camera/AVM | Camera aparece no display 0 e CarPlay permanece no 3 |
| Fechar camera/AVM | CarPlay continua no 3 sem tirar cabo |
| Com CarPlay no 3, acionar ar-condicionado | HVAC aparece no display 0, a `Presentation` do app pode sumir temporariamente e CarPlay permanece visivel no 3 |
| Fechar ar-condicionado | CarPlay continua no 3 sem tirar cabo |
| Com CarPlay no 3, acionar camera/AVM | AVM aparece no display 0, a `Presentation` do app pode sumir temporariamente e CarPlay permanece visivel no 3 |
| Com CarPlay no 3, janela nativa de camera/AVM/HVAC ganha foco no display 0 | Guardiao pontual usa foco leve ou apenas verifica que CarPlay segue no 3; nao envia `view_state`, nao redimensiona, nao reinicia servicos e nao puxa CarPlay para o 0 |
| Com CarPlay no 3, abrir outro app no display 0 | App abre no display 0 e CarPlay continua no 3 |
| Com CarPlay no 3 | Display `Mapa` aparece por cima da projecao; `.display-mapa .mask-top-bar`, `.display-mapa .dashboard-speed-content`, widgets de mapa e gauges esperados permanecem visiveis; esportivo/circulos/fundo fixo/mascara/menu/HVAC nao aparecem por cima salvo overlays transparentes acionados por cards fisicos |
| Com CarPlay no 3, navegar por cards fisicos para main menu ou AC | `main_menu` (`cardId=1`) e AC (`cardId=3`) aparecem como overlays transparentes e focaveis; CarPlay permanece visivel no D3, sem tela preta, pausa, resize ou restore |
| Com CarPlay no 3, navegar para card original/neutro | Menu lateral some e o overlay volta ao `Mapa`/projecao limpa; CarPlay permanece visivel no D3 |
| Com Android Auto no 3 | Display `Mapa` aparece por cima da projecao; `.display-mapa .mask-top-bar`, `.display-mapa .dashboard-speed-content` e widgets de mapa permanecem visiveis; esportivo/circulos/fundo fixo/mascara/menu/HVAC nao aparecem por cima |
| Trazer CarPlay 3 -> 0 | Display 0 mostra CarPlay, cluster 3 limpa sem frame cinza |
| Enviar CarPlay 0 -> 3 novamente | Cluster 3 volta a exibir CarPlay sem reconectar cabo |
| CarPlay fica preto apos troca de display | Fluxo tenta refresh/foco e recupera host sem `force-stop com.ts.carplay.app` |
| CarPlay esta no display 0 ou desconectado | Cluster 3 nao deve forcar `Mapa`, salvo escolha manual do usuario |
| CarPlay sai/perde conexao apos estar no 3 | Cluster volta ao display salvo anteriormente pelo usuario |

## Validacao Tecnica Depois do Deploy

Confirmar APK principal instalado:

```bash
HEADUNIT_TELNET_WAIT=10 HEADUNIT_HOST=172.20.10.2 \
  tools/headunit-dev/telnet-exec.sh \
  "dumpsys package br.com.redesurftank.havalshisuku | grep -E 'lastUpdateTime|versionName|versionCode'"
```

Confirmar patch nativo:

```bash
HEADUNIT_TELNET_WAIT=10 HEADUNIT_HOST=172.20.10.2 \
  tools/headunit-dev/telnet-exec.sh \
  "md5sum /system/app/TsCarPlayApp/TsCarPlayApp.apk /data/local/tmp/carplay_patches/TsCarPlayApp.apk; getprop persist.haval.carplay.video.height"
```

Confirmar stacks:

```bash
HEADUNIT_TELNET_WAIT=8 HEADUNIT_HOST=172.20.10.2 \
  tools/headunit-dev/telnet-exec.sh "am stack list"
```

Sinais de regressao:

- APK patchado do `TsCarPlayApp.apk` montado automaticamente sem validacao manual.
- `com.ts.carplay.app` aparece em mais de uma stack depois de uma troca.
- `com.ts.carplay.app` aparece no display errado depois do comando.
- Bounds diferentes de fullscreen no display 0 ou 3.
- `persist.haval.carplay.video.height` diferente de `720`.
- Logs com `force-stop com.ts.carplay.app` durante troca/camera/HVAC.
- Projector do cluster aplica resize em `com.ts.carplay.app`.
- Cluster 3 fica em tema `Mapa` apenas porque o alvo desejado era 3, sem task/foco real de
  CarPlay ou Android Auto no display 3.
- Cluster 3 mostra apenas velocimetro solto quando CarPlay/Android Auto esta realmente no display 3,
  sem a composicao do display `Mapa`.

## Arquivos de Implementacao

- `app/src/main/java/br/com/redesurftank/havalshisuku/managers/DisplayAppLauncher.kt`
  - Fluxo unico de abertura/recriacao visual do CarPlay, refresh de Surface, foco de video,
    deteccao real por display e recuperacao controlada do host.
- `app/src/main/java/br/com/redesurftank/havalshisuku/projectors/InstrumentProjector2.kt`
  - Mantem projector visivel como tema de espelhamento e ignora resize quando CarPlay/Android Auto
    esta no cluster.
- `app/src/main/java/br/com/redesurftank/havalshisuku/managers/CarPlayPatchManager.kt`
  - Montagem do APK nativo full-height 720 sem force-stop recorrente.
- `tools/headunit-dev/deploy-apk.sh`
  - Deploy do APK principal com barra de progresso.

## Disciplina de Mudanca

1. Toda correcao nova deve citar qual regra deste contrato protege.
2. Nenhuma correcao de camera/HVAC pode mover, reiniciar ou redimensionar CarPlay.
3. Nenhum ajuste de cluster pode redimensionar CarPlay.
4. Build e deploy nao bastam; sempre validar APK instalado, patch nativo, prop 720 e stack list.

## Regra 25 - Contrato Fullscreen Objetivo no Cluster 3

Quando o alvo desejado do usuario for CarPlay no cluster 3 e a Activity real `com.ts.carplay.app/.ui.display.view.CarPlayDisplayActivity` estiver no display 3, o contrato valido e:

- stack bounds exatamente `[0,0][1920,720]`;
- window requested `1920x720`;
- Surface do CarPlay `1920x720`;
- nenhuma duplicata sustentada no display 0.

Se logs/dumps mostrarem bounds parciais, por exemplo `[0,62][1920,658]` ou Surface `1920x596`, o app pode reparar somente o stackId real do CarPlay no display 3 com:

```bash
am stack resize <stackId> 0 0 1920 720
```

Obrigatorio para esse reparo:

- confirmar que o usuario deseja CarPlay no cluster 3;
- confirmar que o CarPlay real esta no display 3;
- fazer double-check antes do resize;
- aplicar cooldown e limite de tentativas;
- registrar antes/depois com tag `CARPLAY_CLUSTER_FULLSCREEN_REPAIR`;
- nao enviar `VIDEO_FOCUS_CHANGE` nesse caminho;
- nao enviar `REFRESH_RENDER` nesse caminho;
- nao chamar `startservice` nesse caminho;
- nao fazer `force-stop com.ts.carplay.app`;
- nao aplicar a Android Auto.

Se o contrato fullscreen ja estiver correto e a tela continuar preta, nao repetir resize: primeiro
considerar um `REFRESH_RENDER` tardio, unico e sem `VIDEO_FOCUS_CHANGE`; se ainda falhar, investigar
decoder/Surface (`cpScreen`, `NdkMediaCodec`, `jsurface`) ou overlay WebView/Presentation.

## Regra 26 - Presentation/WebView Transparente Durante Projecao Real

Quando CarPlay ou Android Auto esta realmente no cluster 3, a `Presentation` do app fica acima da
janela nativa para desenhar o overlay do display efetivo `Mapa` e os overlays de card explicitamente
permitidos. Nesse estado:

- eventos nativos de camera/AVM/HVAC/app no display 0 nao devem forcar tela invasiva opaca na
  WebView do cluster;
- navegacao fisica por cards pode renderizar `main_menu` quando `cardId=1` e AC quando `cardId=3`;
- esses overlays devem ficar transparentes e sem captura de toque, preservando a Surface nativa da
  projecao abaixo;
- qualquer outra navegacao deve continuar suprimida enquanto `carPlayInDash` ou
  `projectionMirrorInDash` estiver ativo;
- a tela efetiva do frontend deve ser neutra/transparente, preservando apenas os widgets do mapa;
- `html`, `body`, `#app` e o WebView devem permanecer transparentes;
- se o WebView precisar ficar sobre a Surface nativa, a transparencia deve ser priorizada em vez de
  pintar fallback preto;
- AC/HVAC, camera/AVM e apps no display 0 nao podem montar componentes opacos no display 3.

Se CarPlay estiver fullscreen (`[0,0][1920,720]`, Window requested `1920x720`, Surface `1920x720`)
e ainda houver tela preta enquanto AC/camera esta ativa no display 0, investigar primeiro se a
`Presentation` esta pintando preto; so depois investigar foco/decoder nativo (`cpScreen` /
`NdkMediaCodec`).

## Regra 27 - Fallback visual do cluster esta desabilitado

O fallback visual baseado em `projectionNativePanelFallbackActive` nao e mais permitido como
estrategia de correcao para CarPlay no display 3.

Motivo confirmado no teste fisico de 2026-05-26 10:45:

- ao acionar camera, o cluster ficou preto com o display `Normal` do app por cima;
- ao pressionar qualquer opcao no display 0, o cluster 3 ficou preto;
- o snapshot final registrou `Desired CarPlay target is cluster 3 but no visual task is active`,
  deixando de ser apenas um caso de feed preto com task/surface viva.

Contrato atual:

- `projectionNativePanelFallbackActive` deve permanecer `false` no Kotlin e no frontend;
- o frontend nao deve sair do modo `Mapa` transparente para renderizar `Normal` por cima do preto;
- se AC/camera/app no display 0 apagar o feed com CarPlay ainda em fullscreen, coletar comparativo
  CarPlay x Android Auto e investigar o caminho nativo do host (`CarPlayManager.requestVideoFocusChange`
  / `ScreenResourceManager.screenResourceRequest`);
- nao insistir em mais fallback visual, CSS ou transparencia sem nova evidencia.

## Regra 28 - Excecao experimental: fallback por foreground nao-passivo do display 0

Esta regra substitui temporariamente a Regra 27 apenas para a build de teste instalada em
`2026-05-26 20:54:10`.

A excecao permitida e estreita:

- CarPlay precisa estar real no display 3;
- stack/window/Surface do CarPlay precisam permanecer `1920x720`;
- display 0 em estado passivo (`com.beantechs.vehiclecenter` ou `com.beantechs.mediacenter`) nao
  ativa fallback;
- display 0 com foreground nao-passivo pode ativar `projectionNativePanelFallbackActive` para evitar
  preto perceptivel enquanto a funcao/app estiver ativa;
- nao mover, reiniciar ou redimensionar CarPlay;
- nao enviar `VIDEO_FOCUS_CHANGE`, `view_state foreground`, `REFRESH_RENDER` ou `force-stop` nesse
  caminho;
- Android Auto nao deve ser alterado por esta heuristica.

Motivo da excecao: o usuario reportou que qualquer funcao/app no display 0 podia deixar o cluster 3
preto, e os snapshots continuaram mostrando CarPlay fullscreen no display 3. Como nao ha `screencap`
funcional na central, esta build e explicitamente experimental e depende de validacao visual fisica.

Se o teste fisico mostrar novamente "preto com Normal por cima" ou perda da task visual do CarPlay,
esta excecao deve ser revertida e a investigacao deve voltar ao host nativo CarPlay.

## Regra 29 - CarPlay nao deve enviar background em onPause

Quando `CarPlayDisplayActivity` esta visivel no cluster 3, `onPause()` pode ocorrer por mudanca de
foco do display 0 sem que a Activity tenha saido do cluster. No teste pos-reboot de 2026-05-28, a
checagem por `getDisplay().getDisplayId() != 0` nao foi suficiente: ao fechar o AC/HVAC, o caminho
stock ainda enviou `ts.car.carplay.view_state=background`, o D3 ficou preto e a Activity visual foi
recriada no D0.

Contrato atual do patch `TsCarPlayApp.apk`:

- `onPause()` envia `ts.car.carplay.view_state=foreground`, nunca `background`;
- a excecao deixou de depender de `Activity.getDisplay()`, que se mostrou ambigua durante HVAC;
- `VideoModel.lambda$priorityChanged$3` tambem ignora foco HVAC `uiNotification`;
- nao alterar layout, launch mode, SurfaceView, finish receiver ou fluxo Android Auto.

Evidencia de validacao 2026-05-28:

- antes do patch v2, HVAC ativo + toque deixou D3 preto em
  `artifacts/headunit/screens/20260528-224551-ac-tap-d3.png`;
- depois do patch v2, HVAC ativo + toque manteve mapa no D3 em
  `artifacts/headunit/screens/20260528-225353-v2-ac-tap-d3.png`.

Camera/AVM continua fora desta regra; qualquer expansao para prioridade/safety de camera exige
teste fisico separado.

## Regra 30 - Estado validado deve ter trava de regressao

O estado validado em 2026-05-28 22:55 deve permanecer reproduzivel por verificacao estatica antes
de novos deploys envolvendo CarPlay:

- `TsCarPlayApp.apk` embarcado deve manter MD5 `6fa2ec71f8a10e11a8de94ab03987344`;
- `TsCarPlayService.apk` embarcado deve manter MD5 `4a76e74c5f9fc119287c5cc0f823856a`;
- `ForegroundService` deve manter a versao de auto-mount `app_service_hvac_focus_v3`;
- `CarPlayPatchManager` deve montar app + service e nao deve executar `force-stop` como parte do
  auto-mount;
- quando o MD5 embarcado mudar, `CarPlayPatchManager` deve recopy + reaplicar mounts para limpar
  dalvik/oat pelo fluxo `applyMounts()`, mesmo se o APK antigo ja estiver montado;
- `DisplayAppLauncher` deve manter verify-only quando a task real do CarPlay continua viva no D3,
  sem `VIDEO_FOCUS_CHANGE` em eventos de HVAC/camera/app;
- se o alvo desejado e D3 e a central nativa remover a task visual ou recriar CarPlay no D0, o
  watchdog pode restaurar o visual no D3 com `am start --display 3 ... CarPlayDisplayActivity`,
  sem `force-stop`, defocando antes o D0 e removendo duplicata do D0 somente depois que a task do
  D3 existir;
- `patch_logic_app_focus.py` deve manter os sentinels
  `CP_KEEP_VIDEO_FOCUS_FOR_HVAC_ONLY`, `CP_KEEP_CLUSTER_VIDEO_ON_SECONDARY_PAUSE` e
  `CP_KEEP_CLUSTER_VIDEO_FOREGROUND_ON_ANY_PAUSE`;
- `patch_logic_service.py` deve continuar default HVAC-only; camera `0x7` so pode ser incluida com
  decisao e teste fisico separados.

Comando obrigatorio antes de deploy ou merge que toque CarPlay:

```bash
python3 scripts/carplay-patches/verify_regression_lock.py
```
