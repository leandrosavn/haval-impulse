# Estrategia de Patch Nativo: Android Auto x CarPlay

Atualizado em: 2026-05-28 23:06 -03

## Objetivo

Documentar por que o projeto trata Android Auto e CarPlay com estrategias diferentes na central Haval/GWM, especialmente no contexto de projecao no cluster 3 e da regressao de tela preta ao acionar camera/AVM, AC/HVAC ou apps no display 0.

Este documento nao declara a tela preta do CarPlay resolvida. Ele registra a estrategia atual, as evidencias ja observadas e os limites que devem orientar os proximos testes fisicos.

## Resumo Executivo

Android Auto esta usando um caminho patchado e mais agressivo de recuperacao porque esse fluxo foi implementado, validado como montavel e documentado como melhor para dimensoes/foco no cluster.

CarPlay saiu do estado stock somente para uma excecao controlada validada em 2026-05-28:
`TsCarPlayApp.apk` preserva video no D3 durante `onPause` em display secundario e
`TsCarPlayService.apk` ignora apenas a prioridade HVAC `0x6`. As variantes antigas continuam
proibidas porque causaram crash, frame sujo ou retorno dos sintomas.

Portanto, a estrategia atual e:

- manter Android Auto no fluxo patchado e isolado;
- manter CarPlay no patch minimo HVAC/D3 v2, sustentado por MD5 e sentinels;
- nao misturar comandos de recuperacao Android Auto com CarPlay;
- investigar CarPlay no caminho nativo de foco/video, principalmente `CarPlayManager.requestVideoFocusChange` e `ScreenResourceManager.screenResourceRequest`.

## Estado Atual dos APKs Nativos

| Item | Android Auto | CarPlay |
| --- | --- | --- |
| Pacote visual | `com.ts.androidauto.app` | `com.ts.carplay.app` |
| Pacote/host nativo | `com.ts.androidauto.projectionservice` / `com.ts.androidauto` | `com.ts.carplay` |
| Activity visual | `com.ts.androidauto.app.display.AapActivity` | `com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity` |
| Estrategia atual | APK patchado via bind mount em `/vendor/app/...` | Patch minimo em `/system/app/TsCarPlayApp` + `/vendor/app/TsCarPlayService` |
| Patch runtime | Ativo quando instalado/montado | Ativo para HVAC/D3 v2 |
| Recuperacao permitida | Mais agressiva no app visual e foco | Conservadora, baseada em estado real e logs |
| Evidencia recente | Usuario reportou que nao reproduz a tela preta | HVAC + toque no D3 corrigido; camera/AVM ainda depende de teste fisico |

MD5s do estado protegido:

- `TsCarPlayApp.apk`: `477529a8c454acbc25ab5adb848e18b4`;
- `TsCarPlayService.apk`: `4a76e74c5f9fc119287c5cc0f823856a`.

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
- `ForegroundService` usa a chave `app_service_hvac_focus_v2` para ativar o auto-mount.

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
- Camera `0x7` permanece stock; expansao para camera exige experimento separado.
- A correcao atual nao deve reabilitar restores agressivos nem misturar Android Auto.

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
- deve usar apenas verificacao, cooldown e reparo objetivo de bounds quando houver evidencia.

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

Leitura atual:

- A causa mais provavel esta no host nativo do CarPlay, nao na camada generica do cluster.
- A diferenca Android Auto x CarPlay deve ser usada como comparativo para isolar foco/video/surface.

## Estrategia Atual de Teste

Antes do teste:

- Patch CarPlay HVAC/D3 v2 deve continuar confirmado por MD5.
- `persist.haval.carplay.video.height` deve ser `720`.
- `/data/local/tmp/app.html` deve permanecer ausente para validar HTML embarcado.
- `projectionNativePanelFallbackActive` deve permanecer desabilitado.

Durante teste fisico:

- Testar Android Auto no cluster 3.
- Acionar AC/HVAC, camera/AVM e app no display 0.
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
4. criar um patch nativo novo e minimo somente se houver condicao clara para bloquear/alterar a renegociacao quando CarPlay ja esta fullscreen no display 3;
5. manter rollback facil para APK stock.

Um novo patch CarPlay so deve ser considerado se:

- nao crashar ao abrir CarPlay;
- nao produzir frame branco/sujo no display 0;
- nao alterar Android Auto;
- preservar `persist.haval.carplay.video.height=720`;
- manter Surface `1920x720` no cluster 3;
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
