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
    permitida: navegacao fisica por cards pode armar uma sessao de overlays transparentes de
    `main_menu` e AC sobre a projecao. Depois de armada por tecla fisica real, essa sessao pode
    permanecer visivel ao passar por cards neutros/originais, desde que nao pinte fundo opaco, nao
    mova, nao reinicie e nao pause o CarPlay.
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
    (`sys.avm.preview_status` ou `car.hvac.panel_display_notify`), o app principal deve manter sua
    `Presentation`/WebView visivel e transparente para preservar o display efetivo `Mapa`. O bypass
    antigo por `windowAlpha=0` nao deve ser usado durante camera/AVM/HVAC, porque remove os widgets
    protegidos do `Mapa` mesmo quando a Surface do CarPlay continua saudavel no D3.
26. Quando o proprio app Haval/Impulse ganha foco no display 0 e o CarPlay continua realmente no
    display 3, existe uma excecao conservadora para Surface stale: se o `dumpsys SurfaceFlinger`
    confirmar `SurfaceView - com.ts.carplay.app/...CarPlayDisplayActivity` com
    `activeBuffer=1x1`, o guardiao pode enviar `REFRESH_RENDER` e um `am start --display 3`
    idempotente para reassertar a Activity existente no D3. Essa excecao nao pode enviar
    `VIDEO_FOCUS_CHANGE`, nao pode enviar `view_state foreground`, nao pode redimensionar stack,
    nao pode remover stack e nao pode usar `force-stop`. Se o `activeBuffer` estiver saudavel
    (`1904x704`, `1920x720` ou equivalente), o guardiao deve ficar sem acao. Camera/AVM/HVAC e apps
    nativos continuam no caminho verify-only salvo evidencia nova e documentada.
27. Antes de qualquer prova funcional de envio D0 -> D3, o CarPlay deve ser preparado no D0. O
    preflight minimo e: patch/mount e propriedades confirmados, servicos CarPlay vivos, Activity
    nativa aberta no D0 pelo icone/fluxo nativo, feed D0 fisicamente limpo, cluster em estado
    `PREPARING_D3`/`Mapa` transitorio pelo orquestrador e so entao envio ao D3 pelo app. Um
    `am start --display 3` direto por Telnet pode ser usado para diagnostico, mas nao e prova de
    regressao/correcao do fluxo do app porque bypassa o preparo de terreno, o defocus do D0 e a
    reconciliacao do `CarPlayDisplayOrchestrator`.

## Contrato Unificado de Estado D3

Qualquer diagnostico ou correcao de projecao no cluster 3 deve separar estes campos antes de
escolher uma camada de mudanca:

| Campo | Fonte minima | Contrato esperado |
| --- | --- | --- |
| `projection_type` | pacote/activity real e comando de envio | `carplay` ou `android_auto`, sem misturar recuperacoes |
| `desired_display` | `desiredCarPlayDisplayId` e `persist.haval.carplay.desired_display` | alvo desejado, nao prova de task viva |
| `task_display` | `am stack list` / `dumpsys activity` | CarPlay D3 em stack/window `[0,0][1920,720]` |
| `usb_session` | `dumpsys usb` e processos nativos | `CONFIGURED` quando a sessao fisica esta ativa |
| `surface_real` | `dumpsys SurfaceFlinger` | uma `SurfaceView` visivel no D3, sem Surface stale `1x1` visivel |
| `activeBuffer` | `dumpsys SurfaceFlinger` | CarPlay D3 pode usar `1904x704`; `1x1` confirma Surface stale/preta; window/stack seguem `1920x720` |
| `foreground_d0` | `dumpsys window` / Accessibility logs | app, AC/HVAC ou camera no D0 nao move nem redimensiona D3 |
| `overlay_webview` | `InstrumentProjector2` logs e DOM/classes | WebView/Presentation visivel, transparente, sem `windowAlpha=0` |

Classificacao por camada:

- `mount/boot`: MD5, bind mount, `carPlayPatchAutoMountPatchVersion`, timing de Shizuku e dex.
- `handoff D0 -> D3`: reuse de Activity/top-most, duplicata D0/D3, preservacao de alvo D3.
- `Surface/buffer`: Surface stale `1x1`, Surface real `1904x704`, `activeBuffer`, crop/source.
- `patch nativo CarPlay`: foco/decoder/video do host, `VideoModel`, `ScreenResourceManager`.
- `overlay WebView/Presentation`: z-order, transparencia, Mapa, card overlay e `windowAlpha`.
- `watchdog/restore`: restore controlado de task ausente ou sustentada no D0.
- `Android Auto`: somente quando a evidencia reproduz bug no Android Auto; nunca como efeito colateral
  de uma correcao CarPlay.

Se o estado atual tiver stack/window fullscreen, Surface real `1904x704`, `activeBuffer` valido e
apenas `screencap -d 4` cinza/washed sem relato fisico ruim, a classificacao provisoria e
`screenshot/readback falso` e nao autoriza patch funcional.

## Orquestracao CarPlay D0/D3

O fluxo de CarPlay deve passar por um orquestrador unico de estado (`CarPlayDisplayOrchestrator`)
antes de chamar os comandos baixos de `DisplayAppLauncher`. Estados validos:

- `DISCONNECTED`;
- `CONNECTED_ON_D0`;
- `PREPARING_D3`;
- `MIRRORED_ON_D3`;
- `RETURNING_TO_D0`;
- `ERROR_RECOVERY`.

Durante `PREPARING_D3`, o cluster recebe `projectionPreparingD3=true` e deve aplicar o display
efetivo `Mapa` sem gravar a preferencia do usuario. Esse estado e transitorio e deve ser limpo ao
concluir, falhar ou cancelar a transicao. Cliques repetidos de envio para D3 ou retorno para D0
devem ser idempotentes: se a Activity real ja esta no alvo, a acao apenas reconcilia o alvo desejado
e dispara verificacao leve, sem criar nova Surface, listener, timer ou stack duplicada.

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
7. Se nao existe CarPlay vivo em display secundario, remover stacks visuais antigas e abrir por
   cold-start explicito sem `--display 0`:

```bash
am start -f 0x14000000 \
  -n com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity
```

Em 2026-05-29, `am start --display 0` foi observado sendo entregue a uma instancia top-most stale
no display 3 mesmo com alvo `persist.haval.carplay.desired_display=0`. No teste de boot/autostart
do mesmo dia, `am stack start 0` criou varias stacks vazias antes da Activity estar pronta. O
caminho aceito para cold-start no display 0 passa a ser o `am start` explicito acima. O handoff
D3 -> D0 com Surface viva continua usando `am display move-stack`.

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
   transparentes. Bootstrap sem tecla fisica recente continua desarmado; depois de uma tecla fisica
   real, o overlay armado pode atravessar cards neutros/originais sem sumir.

## Matriz Minima de Teste

| Cenario | Resultado esperado |
| --- | --- |
| CarPlay no display 0, tocar icone CarPlay | CarPlay aparece no display 0 em fullscreen, sem menu nativo por cima |
| Enviar CarPlay 0 -> 3 | Cluster 3 exibe CarPlay em fullscreen com display `Mapa` automatico |
| Com CarPlay no 3, acionar camera/AVM | Camera aparece no display 0 e CarPlay permanece no 3 |
| Fechar camera/AVM | CarPlay continua no 3 sem tirar cabo |
| Com CarPlay no 3, acionar ar-condicionado | HVAC aparece no display 0, a `Presentation` do app permanece visivel/transparente, o display `Mapa` continua ativo e CarPlay permanece visivel no 3 |
| Fechar ar-condicionado | CarPlay continua no 3 sem tirar cabo |
| Com CarPlay no 3, acionar camera/AVM | AVM aparece no display 0, a `Presentation` do app permanece visivel/transparente, o display `Mapa` continua ativo e CarPlay permanece visivel no 3 |
| Com CarPlay no 3, janela nativa de camera/AVM/HVAC ganha foco no display 0 | Guardiao pontual usa foco leve ou apenas verifica que CarPlay segue no 3; nao envia `view_state`, nao redimensiona, nao reinicia servicos e nao puxa CarPlay para o 0 |
| Com CarPlay no 3, abrir outro app no display 0 | App abre no display 0 e CarPlay continua no 3 |
| Com CarPlay no 3 | Display `Mapa` aparece por cima da projecao; `.display-mapa .mask-top-bar`, `.display-mapa .dashboard-speed-content`, widgets de mapa e gauges esperados permanecem visiveis; esportivo/circulos/fundo fixo/mascara/menu/HVAC nao aparecem por cima salvo overlays transparentes acionados por cards fisicos |
| Com CarPlay no 3, navegar por cards fisicos para main menu ou AC | `main_menu` e AC aparecem como overlays transparentes e focaveis; CarPlay permanece visivel no D3, sem tela preta, pausa, resize ou restore |
| Com CarPlay no 3, passar por card original/neutro depois de armar overlay fisico | O overlay transparente permanece visivel; nao ha fundo opaco, resize, restore nem pausa da projecao |
| Com CarPlay no 3, card original/neutro chega sem tecla fisica recente | Overlay continua desligado e o cluster fica no `Mapa`/projecao limpa; CarPlay permanece visivel no D3 |
| Com Android Auto no 3 | Display `Mapa` aparece por cima da projecao; `.display-mapa .mask-top-bar`, `.display-mapa .dashboard-speed-content` e widgets de mapa permanecem visiveis; esportivo/circulos/fundo fixo/mascara/menu/HVAC nao aparecem por cima |
| Trazer CarPlay 3 -> 0 | Display 0 mostra CarPlay, cluster 3 limpa sem frame cinza |
| Enviar CarPlay 0 -> 3 novamente | Cluster 3 volta a exibir CarPlay sem reconectar cabo |
| CarPlay fica preto apos troca de display | Fluxo tenta refresh/foco e recupera host sem `force-stop com.ts.carplay.app` |
| CarPlay esta no display 0 ou desconectado | Cluster 3 nao deve forcar `Mapa`, salvo escolha manual do usuario |
| CarPlay sai/perde conexao apos estar no 3 | Cluster volta ao display salvo anteriormente pelo usuario |

Evidencia obrigatoria por cenario: rodar `./tools/headunit-dev/headunit.sh carplay-proof <label>`
apos cada etapa relevante para gerar prints completos de D0 e D3, alem de stack/window/SurfaceFlinger
e logs. O teste de camera/AVM e sempre o ultimo da sequencia e depende de acionamento manual fisico;
nao usar comandos remotos de camera como substituto do teste final.

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

1. Antes de qualquer adequacao funcional, capturar baseline/logs/screenshot do estado atual quando
   a central estiver acessivel.
2. Toda correcao nova deve citar qual regra deste contrato protege.
3. Nenhuma correcao de camera/HVAC pode mover, reiniciar ou redimensionar CarPlay.
4. Nenhum ajuste de cluster pode redimensionar CarPlay.
4. Build e deploy nao bastam; sempre validar APK instalado, patch nativo, prop 720 e stack list.

## Regra 25 - Contrato Fullscreen Objetivo no Cluster 3

Quando o alvo desejado do usuario for CarPlay no cluster 3 e a Activity real `com.ts.carplay.app/.ui.display.view.CarPlayDisplayActivity` estiver no display 3, o contrato valido e:

- stack bounds exatamente `[0,0][1920,720]`;
- window requested `1920x720`;
- SurfaceView visivel em `[0,0][1920,720]`; o buffer nativo validado pode ser
  `1904x704`, escalado pelo SurfaceFlinger para preencher o cluster sem expor margem cinza;
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
- navegacao fisica por cards pode armar uma sessao de overlay para `main_menu`/AC;
- eventos de bootstrap do `ClusterService` como `0 -> 1 -> 1`, sem tecla fisica recente, nao contam
  como navegacao fisica e nao podem armar esses overlays sobre o CarPlay;
- o frontend so pode marcar `projection-card-overlay-active` quando
  `projectionCardOverlayAllowed=true`, estado armado pelo Kotlin somente apos input real recente do
  volante;
- depois de armado por input fisico, o overlay nao pode sumir apenas porque o carrossel nativo
  passou por card neutro/original; troca automatica de card sem tecla fisica recente deve manter o
  overlay desligado;
- esses overlays devem ficar transparentes e sem captura de toque, preservando a Surface nativa da
  projecao abaixo;
- qualquer outra navegacao deve continuar suprimida enquanto `carPlayInDash` ou
  `projectionMirrorInDash` estiver ativo;
- a tela efetiva do frontend deve ser neutra/transparente, preservando apenas os widgets do mapa;
- `html`, `body`, `#app` e o WebView devem permanecer transparentes;
- se o WebView precisar ficar sobre a Surface nativa, a transparencia deve ser priorizada em vez de
  pintar fallback preto;
- AC/HVAC, camera/AVM e apps no display 0 nao podem montar componentes opacos no display 3;
- camera/AVM/HVAC nao podem esconder a `Presentation` inteira por `windowAlpha=0`, pois isso remove
  o display `Mapa` mesmo com CarPlay renderizando corretamente no D3.

Se CarPlay estiver fullscreen (`[0,0][1920,720]`, Window requested `1920x720`, SurfaceView visivel
em `[0,0][1920,720]`)
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
- stack/window do CarPlay precisam permanecer `1920x720`;
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
- `VideoModel.lambda$priorityChanged$3` ignora retorno normal do display 0
  (`priority=0/action=1/borrowId=""`) enquanto o CarPlay ainda esta no stack, para evitar que o
  fechamento do HVAC roteie o video para AppList e force recriacao da task no D3;
- `SurfaceView` nativo deve usar `match_parent` para preencher o parent fullscreen da Activity no
  cluster 3. O layout stock `1896x700` centralizado gera margem cinza e buffer alinhado proximo de
  `1904x704`;
- `CarPlayDisplayFragment.initView` deve usar o sentinel
  `CP_SURFACE_FIXED_SIZE_BEFORE_CALLBACK_ON_SECONDARY` para aplicar
  `SurfaceHolder.setFixedSize(1904,704)` antes de registrar o callback da Surface em displays
  secundarios;
- `CarPlayDisplayFragment$2.surfaceChanged` deve usar o sentinel
  `CP_SURFACE_SHOW_NATIVE_1904_704_ON_SECONDARY` para passar `1904x704` ao renderer nativo em
  displays secundarios, mantendo Activity/window em `1920x720` e sem chamar `setFixedSize()` no
  meio do callback. Em 2026-05-29, teste fisico confirmou que este buffer alinhado elimina a area
  cinza no D3 enquanto AC e apps no D0 continuam estaveis;
- nao alterar launch mode, loops dinamicos de resize, crop/overscan ou fluxo Android Auto.

Evidencia de validacao 2026-05-28:

- antes do patch v2, HVAC ativo + toque deixou D3 preto em
  `artifacts/headunit/screens/20260528-224551-ac-tap-d3.png`;
- depois do patch v2, HVAC ativo + toque manteve mapa no D3 em
  `artifacts/headunit/screens/20260528-225353-v2-ac-tap-d3.png`.

Camera/AVM continua tratada por regra propria porque envolve o caminho safety de
`backcamera/backupCamera`.

## Regra 30 - Estado validado deve ter trava de regressao

O estado validado em 2026-05-29 10:45 deve permanecer reproduzivel por verificacao estatica antes
de novos deploys envolvendo CarPlay. Ele inclui a correcao do salto observado no fechamento do AC,
do app normal no D0 que podia trocar a rota de video, e a camera condicional: camera/AVM fica stock
no D0, mas nao toma a rota de video quando o alvo desejado do CarPlay e D3.

- `TsCarPlayApp.apk` embarcado deve manter MD5 `9d48c33f49dbeeb020c2fdc7e16bbc53`;
- `TsCarPlayService.apk` embarcado deve manter MD5 `f0269fc640778825843762dcf55a8b83`;
- `ForegroundService` deve manter a versao de auto-mount
  `app_visual_d0_focus_service_conditional_camera_native1904x704_v13`;
- `CarPlayPatchManager` deve montar app + service e, quando o mount muda com CarPlay visual ativo,
  pode recarregar `com.ts.carplay.app` e `com.ts.carplay` para carregar o dex patchado, reabrindo a
  Activity no display onde ela estava; esse caminho e exclusivo de patch load, nao de handoff
  normal;
- quando o MD5 embarcado mudar, `CarPlayPatchManager` deve recopy + reaplicar mounts para limpar
  dalvik/oat pelo fluxo `applyMounts()`, mesmo se o APK antigo ja estiver montado;
- `DisplayAppLauncher` deve manter `persist.haval.carplay.desired_display` sincronizado com a
  preferencia `desiredCarPlayDisplayId` para que o APK nativo saiba, apos reboot, que apps normais
  no D0 nao devem tomar a rota de video quando o alvo desejado e D3;
- `DisplayAppLauncher` deve manter verify-only quando a task real do CarPlay continua viva no D3,
  sem `VIDEO_FOCUS_CHANGE` em eventos de HVAC/camera/app;
- se o alvo desejado e D3 e a central nativa remover a task visual ou recriar CarPlay no D0, o
  watchdog pode restaurar o visual no D3 com `am start --display 3 ... CarPlayDisplayActivity`,
  sem `force-stop`, defocando antes o D0 e removendo duplicata do D0 somente depois que a task do
  D3 existir;
- apos reboot ou sem cabo/sessao CarPlay ativa, o watchdog nao pode recriar task visual ausente se
  nenhuma task real do CarPlay foi observada recentemente no D3; isso evita loop abrindo o Impulse
  no display 0;
- o estado USB usado pelo watchdog deve aceitar apenas `CONFIGURED` ou `CONNECTED` como tokens
  completos. `DISCONNECTED` nao pode ser aceito por substring, pois contem `CONNECTED` e dispara
  restore agressivo sem cabo/sessao ativa;
- `patch_logic_app_focus.py` deve manter os sentinels
  `CP_KEEP_VIDEO_FOCUS_FOR_HVAC_D0_APPS_AND_NORMAL_RETURN`,
  `CP_KEEP_CLUSTER_VIDEO_ON_SECONDARY_PAUSE` e
  `CP_KEEP_CLUSTER_VIDEO_FOREGROUND_ON_ANY_PAUSE`, alem de
  `CP_IGNORE_FINISH_BROADCAST_ON_SECONDARY_DISPLAY`,
  `CP_IGNORE_REQUEST_VIDEO_FOCUS_FINISH_ON_SECONDARY_DISPLAY` e
  `CP_SURFACE_MATCH_PARENT_FULLSCREEN`,
  `CP_SURFACE_FIXED_SIZE_BEFORE_CALLBACK_ON_SECONDARY`,
  `CP_SURFACE_SHOW_NATIVE_1904_704_ON_SECONDARY`;
- `patch_logic_service.py` deve continuar default HVAC-only, mas a variante embarcada deve ser
  gerada com `--conditional-camera`, mantendo os sentinels `CARPLAY_HVAC_KEEP_FOREGROUND_PATCH`,
  `CARPLAY_HVAC_RELEASE_KEEP_FOREGROUND_PATCH` e
  `CARPLAY_CAMERA_CONDITIONAL_KEEP_FOREGROUND_PATCH`.

Comando obrigatorio antes de deploy ou merge que toque CarPlay:

```bash
python3 scripts/carplay-patches/verify_regression_lock.py
```

## Regra 31 - D3 ignora FINISH_ACTIVITY de AppList/display 0

O caminho nativo `VideoModel.foregroundViewChanged()` chama `finishCarPlayActivity()` quando a
Activity topo vira `com.beantechs.applist`. Em AC/HVAC no display 0, esse evento pode ser apenas o
retorno da UI nativa do D0, mas o broadcast `com.ts.carplay.action.FINISH_ACTIVITY` e recebido pela
`CarPlayDisplayActivity` que esta no display 3, removendo a task e abandonando a Surface.

Contrato do patch visual v6:

- `CarPlayDisplayActivity$FinishActivityReceiver.onReceive()` deve checar o display da Activity;
- se `getDisplay().getDisplayId() != 0`, deve registrar
  `finish receiver patched: ignore finish on secondary display` e retornar sem chamar `finish()`;
- no display 0, o receiver preserva o comportamento stock e ainda pode chamar `finish()`;
- desconexao/link status e outros caminhos diretos de `finish()` nao sao bloqueados por esta regra;
- a regra vale para CarPlay somente; Android Auto nao deve ser alterado junto.

## Regra 32 - D3 ignora requestVideoFocus finish e app normal do D0

Abrir uma aplicacao normal no display 0 pode deixar `VideoModel` com foco topo
`priority=0/action=1/borrowId=""`, por exemplo `com.android.settings`, mesmo com a Activity e a
Surface do CarPlay ainda vivas no display 3. No estado ruim pos-reboot de 2026-05-29, esse caminho
nao removia a task do D3, mas trocava a rota de video e deixava o cluster preto.

Contrato do patch visual v7:

- `VideoModel.lambda$priorityChanged$3` deve ignorar esse foco normal do D0 quando o CarPlay ainda
  esta no stack ou quando `mConnStatus == 2` e
  `persist.haval.carplay.desired_display == 3`;
- o log esperado para esse caminho e
  `priorityChanged patched: keep CarPlay video focus for D3 display0 normal app`;
- `CarPlayDisplayActivity.requestVideoFocus(1/2)` deve preservar o comportamento stock no display 0,
  mas deve retornar sem `finish()` quando `getDisplay().getDisplayId() != 0`;
- o sentinel obrigatorio desse metodo e
  `CP_IGNORE_REQUEST_VIDEO_FOCUS_FINISH_ON_SECONDARY_DISPLAY`;
- o app principal deve sincronizar a property `persist.haval.carplay.desired_display` sempre que
  grava o alvo desejado do CarPlay e novamente no start do watchdog, para sobreviver a reboot;
- nao enviar `VIDEO_FOCUS_CHANGE`, `view_state foreground`, resize ou `force-stop` quando a task
  real do CarPlay continua viva no D3.

Evidencia remota 2026-05-29:

- com `com.android.settings` visivel no display 0, `am stack list` manteve
  `CarPlayDisplayActivity` na stack `17`, display `3`, bounds `[0,0][1920,720]`;
- `screencap` raw do display fisico do cluster convertido para PNG em
  `artifacts/headunit/screenshots/carplay-v7-live-20260529-085558/settings-d3.png` mostrou o mapa
  do CarPlay renderizado no D3 durante o app do D0.

## Regra 33 - Service embarcado usa camera condicional por desired_display

Camera/AVM usa prioridade nativa `0x7` e o metodo direto
`ScreenResourceManager.backCameraStatusChangedTo()`. Esse caminho e mais sensivel que HVAC porque
envolve safety e backup camera. Em 2026-05-29, o isolamento no carro mostrou que
`TsCarPlayService.apk` camera v7 (`d0aea9eb3fb5e0b9bd4d5e5ce0e0c642`) era suficiente para deixar o
CarPlay preto no display 0 durante startup, enquanto o service stock/HVAC-only mantinha o D0 limpo.
A solucao atual e condicionar a camera ao alvo desejado do CarPlay.

Contrato da variante atual:

- o asset embarcado de `TsCarPlayService.apk` deve ser conditional-camera/release, MD5
  `f0269fc640778825843762dcf55a8b83`;
- `patch_logic_service.py --include-camera` pode continuar existindo como ferramenta staged, mas o
  APK gerado por esse caminho nao pode ser o asset de auto-mount sem nova validacao fisica;
- `0x7` e `backCameraStatusChangedTo(APP_ON/OFF)` permanecem stock quando
  `persist.haval.carplay.desired_display != 3`;
- quando `persist.haval.carplay.desired_display == 3`, `0x7` e
  `backCameraStatusChangedTo(APP_ON/OFF)` devem enviar `sendMessage(6)` para nao emprestar a rota
  de video do CarPlay no cluster;
- o patch nao deve abrir, redirecionar, esconder ou substituir a camera original no display 0;
- Android Auto permanece fora desta regra.

## Regra 34 - Mapa permanece visivel durante camera/AVM/HVAC

Com CarPlay real no display 3, sinais nativos do display 0 como `sys.avm.preview_status` e
`car.hvac.panel_display_notify` nao podem esconder a `Presentation` do app. O overlay do cluster
deve permanecer no modo `Mapa`/`theme-mirror-cluster`, transparente sobre a Surface do CarPlay.

Motivo: em 2026-05-29, a camera nao deixava mais o D3 preto, mas o display `Mapa` sumia porque
`InstrumentProjector2` aplicava `windowAlpha=0` e `root/WebView` invisiveis.

Contrato:

- `InstrumentProjector2` deve manter `windowAlpha=1`, `root` visivel e WebView visivel quando
  CarPlay esta real no display 3;
- os sinais nativos de camera/AVM/HVAC podem atualizar estado/logs, mas nao ativam bypass visual;
- a correcao nao move, reinicia, redimensiona nem envia foco para o CarPlay;
- Android Auto permanece isolado e nao deve receber logica nova neste caminho.

## Regra 35 - Watchdog nao restaura CarPlay ausente sem sessao visual recente

`desiredCarPlayDisplayId=3` e `persist.haval.carplay.desired_display=3` indicam alvo desejado, nao
prova de que existe uma sessao CarPlay ativa. Apos reboot com o cabo do celular desconectado, pode
nao existir nenhuma task visual `com.ts.carplay.app`.

Contrato:

- o watchdog so pode recriar uma task visual ausente se o CarPlay foi visto recentemente no D3;
- durante o boot, se o alvo salvo ainda for D3, o app pode manter esse alvo em grace curto para que
  o autostart USB use D0 apenas como display de staging e depois restaure o D3 automaticamente;
- esse staging de boot nao pode gravar `desiredCarPlayDisplayId=0` quando o alvo anterior era D3;
- sem task visual recente, o watchdog deve registrar skip e ficar quieto;
- USB `DISCONNECTED` deve ser tratado como nao pronto, mesmo contendo a substring `CONNECTED`;
- se o alvo salvo ainda for D3 mas nao houver task real recente no D3, o app deve limpar esse alvo
  stale para `0` e sincronizar `persist.haval.carplay.desired_display=0`, permitindo que a proxima
  conexao fisica do cabo comece pelo fluxo nativo normal no D0;
- o defocus de display 0 nunca pode usar o proprio pacote do Impulse
  (`br.com.redesurftank.havalshisuku`) como app auxiliar;
- esse caminho nao pode abrir `SplashActivity`/`MainActivity` em loop;
- quando o usuario conectar o cabo e acionar CarPlay para D3, o fluxo normal volta a gravar o alvo
  e armar o watchdog a partir de uma task real.
