# Estrategia de Patch Nativo: Android Auto x CarPlay

Atualizado em: 2026-05-31 21:55 -03

## Objetivo

Documentar por que o projeto trata Android Auto e CarPlay com estrategias diferentes na central Haval/GWM, especialmente no contexto de projecao no cluster 3 e da regressao de tela preta ao acionar camera/AVM, AC/HVAC ou apps no display 0.

Este documento nao declara a tela preta do CarPlay resolvida. Ele registra a estrategia atual, as evidencias ja observadas e os limites que devem orientar os proximos testes fisicos.

## Resumo Executivo

Android Auto esta usando um caminho patchado e mais agressivo de recuperacao porque esse fluxo foi implementado, validado como montavel e documentado como melhor para dimensoes/foco no cluster.

CarPlay saiu do estado stock para uma excecao controlada de foco D3 em 2026-05-28/29:
`TsCarPlayApp.apk` mantem `view_state=foreground` durante `onPause` e
`TsCarPlayService.apk` ignora a prioridade HVAC `0x6` e o release simetrico
`priority=0/action=1/borrowId=uiNotification`. O patch visual tambem ignora retorno normal de foco
do display 0 enquanto CarPlay continua no stack, e impede que `FINISH_ACTIVITY` finalize uma
Activity CarPlay que esta em display secundario. A variante atual preserva a protecao visual v7
para apps normais do D0 apos reboot e usa um service de camera condicional: camera/AVM permanece
stock quando o alvo desejado nao e D3, mas envia `sendMessage(6)` quando
`persist.haval.carplay.desired_display == 3`.
As variantes antigas continuam proibidas porque causaram crash, frame sujo ou retorno dos sintomas.

Portanto, a estrategia atual e:

- manter Android Auto no fluxo patchado e isolado;
- manter CarPlay no patch D3 v13 `native1904x704`, sustentado por MD5 e sentinels;
- nao misturar comandos de recuperacao Android Auto com CarPlay;
- investigar CarPlay no caminho nativo de foco/video, principalmente `CarPlayManager.requestVideoFocusChange` e `ScreenResourceManager.screenResourceRequest`.
- tratar o foco do proprio app Haval no D0 como excecao app-side: se o D3 fica preto mas
  `am stack list`/WindowManager ainda mostram CarPlay fullscreen no cluster, confirmar
  `SurfaceView activeBuffer=1x1` no SurfaceFlinger e reassertar somente a Activity existente no D3
  com `REFRESH_RENDER` + `am start --display 3`, sem foco de video, sem resize e sem `force-stop`.
- antes de tratar um D3 sujo/cinza como regressao do app, repetir o envio D0 -> D3 com preflight:
  CarPlay aberto e limpo no D0, preparo `PREPARING_D3` pelo orquestrador e envio pelo fluxo do
  Impulse/app. `am start --display 3` direto continua permitido apenas como diagnostico.

## Estado Atual dos APKs Nativos

| Item | Android Auto | CarPlay |
| --- | --- | --- |
| Pacote visual | `com.ts.androidauto.app` | `com.ts.carplay.app` |
| Pacote/host nativo | `com.ts.androidauto.projectionservice` / `com.ts.androidauto` | `com.ts.carplay` |
| Activity visual | `com.ts.androidauto.app.display.AapActivity` | `com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity` |
| Estrategia atual | APK patchado via bind mount em `/vendor/app/...` | Patch minimo em `/system/app/TsCarPlayApp` + `/vendor/app/TsCarPlayService` |
| Patch runtime | Ativo quando instalado/montado | Ativo para CarPlay D3 v13 |
| Recuperacao permitida | Mais agressiva no app visual e foco | Conservadora, baseada em estado real e logs |
| Evidencia recente | Usuario reportou que nao reproduz a tela preta | HVAC corrigido; app normal no D0 validado por stack + screencap; camera/AVM ainda depende de teste fisico |

MD5s do estado protegido:

- `TsCarPlayApp.apk`: `9d48c33f49dbeeb020c2fdc7e16bbc53`;
- `TsCarPlayService.apk`: `f0269fc640778825843762dcf55a8b83`.

Verificacao estatica obrigatoria:

```bash
python3 scripts/carplay-patches/verify_regression_lock.py
```

## Por Que Android Auto Esta Patchado

Confirmado por codigo:

- `AndroidAutoPatchManager` instala patches a partir de `assets/aa_patches`.
- O patch e montado sobre `/vendor/app/AndroidAutoApp/AndroidAutoApp.apk`.
- O fluxo limpa oat/dalvik e reinicia os processos Android Auto para garantir carregamento do APK montado.
- `DisplayAppLauncher` chama `AndroidAutoPatchManager.ensureMounted()` antes do fluxo de handoff.
- O fluxo de Android Auto envia `ts.car.androidauto.view_state` e `com.ts.androidauto.action.AndroidAutoService` com comando `requestVideoFocus`.
- Se a Activity visual nao aparece no display alvo, o fluxo pode reiniciar `com.ts.androidauto.app` sem reiniciar necessariamente o servico principal de projecao.

Motivo tecnico:

- O Android Auto ja tinha problema conhecido de foco/dimensoes no cluster.
- O patch foi criado especificamente para melhorar dimensoes e foco.
- A propria UI do app descreve o patch como "Ajusta melhor as dimensoes do cluster e nao perde o foco".
- O fluxo de recuperacao Android Auto ficou isolado, sem depender de regras do CarPlay.

Consequencia:

- Android Auto tem uma combinacao de APK patchado + foco explicito + recuperacao visual dedicada.
- Isso explica por que ele pode sobreviver melhor a AC/camera/app no display 0.

## Por Que CarPlay Nao Esta Mais Stock

Confirmado por codigo:

- `CarPlayPatchManager` tem `PATCH_RUNTIME_ENABLED = true`.
- `ensureMounted()` instala e monta `TsCarPlayApp.apk` e `TsCarPlayService.apk`.
- `ForegroundService` usa a chave `app_visual_d0_focus_service_conditional_camera_native1904x704_v13` para ativar o auto-mount.
- Se o mount muda enquanto a task visual do CarPlay ja esta ativa, o manager recarrega
  `com.ts.carplay.app` e `com.ts.carplay` para carregar o dex novo e reabre a Activity visual no
  display onde ela estava. Isso e restrito ao carregamento de patch, nao ao handoff normal.

Confirmado por historico/logs salvos:

- O APK stock atual esperado e `/system/app/TsCarPlayApp/TsCarPlayApp.apk`.
- MD5 stock esperado: `6c4815c20732b3643b008c85063fead6`.
- Sem `/data/local/tmp/carplay_patches` e sem bind mount ativo.
- Variante patchada `3ce0a58270607f0e854638cfab809a39` crashou com `IllegalAccessError`.
- Variante patchada `9a64672d3f4f69376b8a24c55431b5e9` abriu, mas voltou ao problema com bounds parciais.

Motivo tecnico:

- CarPlay e mais sensivel a recriacao de Activity, foco de video e decoder.
- `VIDEO_FOCUS_CHANGE` pode renegociar rota de video e produzir frames pretos.
- `force-stop com.ts.carplay.app` pode derrubar a sessao visual e exigir reconexao.
- Os testes com patch nativo nao provaram ganho; ao contrario, introduziram crash ou frame sujo/preto.

Consequencia:

- O CarPlay fica em patch minimo e versionado, nao em patches visuais antigos.
- O visual app ignora `priority=0/action=1/borrowId=""` de apps normais do display 0 quando o
  CarPlay ainda esta no stack ou quando `persist.haval.carplay.desired_display == 3`; isso evita
  `changeVideoFocus` para AppList/app normal no D0.
- O visual app ignora `FINISH_ACTIVITY` quando o receiver pertence a uma Activity em display
  secundario; isso evita que o retorno para AppList no D0 remova a task do CarPlay no D3.
- O visual app ignora `requestVideoFocus(1/2)` no display secundario, preservando o finish stock no
  display 0.
- O service patch embarcado trata HVAC e camera condicional:
  entrada `0x6` e fechamento `priority=0/action=1/borrowId=uiNotification` sao roteados para
  `sendMessage(6)`. Camera `0x7` e `backCameraStatusChangedTo(APP_ON/OFF)` ficam stock quando
  `persist.haval.carplay.desired_display != 3`, e tambem sao roteados para `sendMessage(6)` quando
  o alvo desejado e D3.
- A correcao atual nao deve reabilitar restores agressivos nem misturar Android Auto. A excecao
  permitida e objetiva: se o alvo desejado do usuario continua sendo D3 e a central nativa remove a
  Activity visual do CarPlay ou recria o visual no D0, o watchdog pode recriar a Activity no D3 sem
  `force-stop` e limpar duplicata somente depois que o D3 existir.
- Com o patch atual preservando HVAC/apps do D0 e a camera validada sem o service camera v7, a camada `InstrumentProjector2` nao deve
  mais esconder a `Presentation` por `windowAlpha=0` durante painel nativo. O display efetivo
  `Mapa` deve permanecer visivel/transparente sobre o CarPlay no D3; esconder a WebView remove os
  widgets do Mapa sem corrigir foco ou decoder.

## Diferenca de Recuperacao Entre Android Auto e CarPlay

Android Auto:

- pode fazer `requestVideoFocus` via comando proprio;
- pode reiniciar a Activity visual `com.ts.androidauto.app` como ultimo recurso;
- pode reaplicar foco em passes posteriores;
- tem patch nativo conhecido no fluxo.

CarPlay:

- nao deve receber `force-stop com.ts.carplay.app` em handoff normal;
- nao deve receber `VIDEO_FOCUS_CHANGE` em eventos de camera/AC quando ja esta vivo no display 3;
- nao deve ser redimensionado exceto quando houver violacao objetiva do contrato fullscreen;
- deve preservar stack/surface sempre que possivel;
- deve usar verificacao/cooldown quando a task esta viva no D3;
- pode recriar a Activity visual no D3 quando o alvo desejado e D3, USB segue configurado e nao ha
  task visual ativa ou a task ficou sustentada no D0.

Essa diferenca e intencional. Copiar o comportamento agressivo do Android Auto para o CarPlay ja mostrou risco de tela preta, bounce entre displays ou perda da task visual.

## Evidencia Que Mudou a Hipotese

O usuario reportou em 2026-05-26 que Android Auto nao sofre o mesmo problema da tela preta ao acionar camera/AC.

Confirmado por codigo:

- A camada comum do cluster (`InstrumentProjector2` + frontend) trata CarPlay e Android Auto de forma muito parecida quando qualquer projecao real esta no display 3.
- Se a causa principal fosse apenas CSS/WebView/Presentation, seria esperado que Android Auto sofresse sintoma semelhante.

Confirmado por logs salvos do CarPlay:

- CarPlay dispara `CarPlayManager.requestVideoFocusChange` durante eventos de camera/AVM/HVAC.
- Ja houve logs `cpScreen` / `NdkMediaCodec` com erro `-38`.
- Ja houve estado ruim de fullscreen: bounds `[0,62][1920,658]`, Window requested `1920x596`, Surface `1920x596`.
- Tambem ja houve estado com fullscreen correto `1920x720`, mas feed visual preto.
- Em 2026-05-31, o usuario confirmou fisicamente que qualquer funcao no display 0 deixava o D3
  preto, enquanto Camera/AVM nao deixava. No mesmo estado, `am stack list` e WindowManager ainda
  mostravam `com.ts.carplay.app/.ui.display.view.CarPlayDisplayActivity` no display 3 com
  `[0,0][1920,720]`, mas o SurfaceFlinger mostrava o `SurfaceView` do CarPlay com
  `activeBuffer=[1x1]`. Ao acionar Camera/AVM, os logs mantiveram o CarPlay na lista de foco junto
  do `backcamera priority: 7`; ao focar o app Haval, o CarPlay saiu da lista de foco e a Surface
  ficou stale.
- Ainda em 2026-05-31, apos build v72, o usuario abriu CarPlay no D0 e um envio direto por Telnet
  colocou a Activity fullscreen no D3 com Surface `1904x704`, mas o D3 ficou fisicamente
  sujo/cinza e continuou sujo apos reconexao USB. O usuario confirmou que AC e camera nao deixaram
  preto. Essa evidencia deve ser repetida pelo fluxo preparado do app antes de escolher nova camada
  de correcao.

Leitura atual:

- A causa mais provavel esta no host nativo do CarPlay, nao na camada generica do cluster.
- A diferenca Android Auto x CarPlay deve ser usada como comparativo para isolar foco/video/surface.
- Para o caso especifico do foco do proprio app no D0, a correcao aceita fica na camada app-side e
  nao no patch nativo: detectar `activeBuffer=1x1` e reassertar a Activity existente no D3 sem
  renegociar foco de video. Camera/AVM permanece fora dessa excecao porque o teste fisico mostrou
  que nao escureceu o D3.

## Estrategia Atual de Teste

Antes do teste:

- Patch CarPlay D3 v7 deve continuar confirmado por MD5.
- `persist.haval.carplay.video.height` deve ser `720`.
- `/data/local/tmp/app.html` deve permanecer ausente para validar HTML embarcado.
- `projectionNativePanelFallbackActive` deve permanecer desabilitado.

Durante teste fisico:

- Para CarPlay, preparar o D0 antes do D3: abrir pelo icone nativo, aguardar feed limpo e enviar
  pelo fluxo do Impulse/app.
- Testar Android Auto no cluster 3 em roteiro separado.
- Acionar AC/HVAC e app no display 0.
- Acionar camera/AVM somente por ultimo e manualmente.
- Coletar diagnostico read-only com `tools/headunit-dev/diagnose-projection-focus-compare.sh`.
- Repetir exatamente o mesmo roteiro com CarPlay.

Comparar:

- `am stack list`;
- `dumpsys window windows`;
- `dumpsys SurfaceFlinger --list`;
- logs de `requestVideoFocus`, `requestVideoFocusChange`, `ScreenResourceManager`, `VideoResource`, `cpScreen`, `NdkMediaCodec`;
- presenca ou ausencia da Activity visual no display 3;
- bounds e Surface reais.

## Direcao Para Uma Correcao Definitiva

A correcao definitiva do CarPlay provavelmente nao vira de CSS, transparencia ou fallback visual.

O proximo caminho tecnico deve ser:

1. confirmar com logs comparativos que Android Auto mantem feed enquanto CarPlay apaga;
2. localizar no APK/smali do CarPlay o ponto exato de `CarPlayManager.requestVideoFocusChange`;
3. verificar a relacao com `ScreenResourceManager.screenResourceRequest`;
4. manter o patch nativo HVAC-only/release enquanto a camera/AVM fisica preservar o feed no display 0 e o
   CarPlay no D3;
5. manter rollback facil para APK stock.

Um novo patch CarPlay so deve ser considerado se:

- nao crashar ao abrir CarPlay;
- nao produzir frame branco/sujo no display 0;
- nao alterar Android Auto;
- preservar `persist.haval.carplay.video.height=720`;
- manter stack/window `1920x720` no cluster 3; a SurfaceView pode usar buffer nativo validado
  `1904x704` escalado para fullscreen;
- passar no roteiro AC/camera/app display 0 sem derrubar a sessao.

## Regras de Preservacao

- Nao misturar Android Auto e CarPlay.
- Nao usar comandos Android Auto para recuperar CarPlay.
- Nao reativar patches CarPlay antigos.
- Nao usar `force-stop com.ts.carplay.app` como correcao normal.
- Nao insistir em fallback visual do cluster para esconder preto do CarPlay.
- Nao declarar resolvido sem teste fisico no veiculo.
- Qualquer patch nativo CarPlay deve ser minimo, documentado, reversivel e validado contra APK stock.

## Arquivos Relacionados

- `app/src/main/java/br/com/redesurftank/havalshisuku/managers/AndroidAutoPatchManager.kt`
- `app/src/main/java/br/com/redesurftank/havalshisuku/managers/CarPlayPatchManager.kt`
- `app/src/main/java/br/com/redesurftank/havalshisuku/managers/DisplayAppLauncher.kt`
- `app/src/main/java/br/com/redesurftank/havalshisuku/projectors/InstrumentProjector2.kt`
- `docs/carplay-cluster-regression-contract.md`
- `tools/headunit-dev/diagnose-projection-focus-compare.sh`
