# CarPlay D3 Field Report - 2026-05-29 18:22

## Estado Preservado

- Commit salvo: `290341e fix: stabilize CarPlay D3 native buffer baseline`.
- PR aberto para `preview`: `https://github.com/bobaoapae/haval-app-tool-multimidia/pull/84`.
- Baseline funcional: `native1904x704_v12`.
- `TsCarPlayApp.apk`: `ec5053d91d8364d9451937981e08a04a`.
- `TsCarPlayService.apk`: `f0269fc640778825843762dcf55a8b83`.
- D3 permanece com stack/window `[0,0][1920,720]`.
- Buffer nativo CarPlay validado: `1904x704`.

## Observacao de Campo do Usuario

Depois de a central ficar desligada por um periodo:

1. Primeira conexao USB: CarPlay abriu limpo no display 0.
2. Primeiro envio D0 -> D3: CarPlay apareceu sujo/cinza no D3.
3. Usuario desconectou e reconectou o USB.
4. Segunda conexao USB: CarPlay abriu limpo no display 0.
5. Segundo envio D0 -> D3: CarPlay apareceu limpo no D3.
6. No estado mais recente, AC/HVAC e camera/AVM nao geraram tela preta.

Em deslocamento anterior do mesmo dia, camera/AVM nao gerou tela preta, mas AC/HVAC ainda gerou
tela preta. Portanto, a falha parece depender de timing/estado frio e ainda nao esta declarada como
resolvida definitivamente.

## Hipoteses A Confirmar

- O auto-mount v12 pode completar depois de alguma parte da primeira inicializacao do CarPlay.
- O primeiro renderer pode nascer com Surface/buffer/crop stale e so alinhar apos nova enumeracao
  USB.
- O primeiro handoff D0 -> D3 pode reutilizar instancia top-most ou dimensao herdada do D0.
- A reconexao USB pode forcar reabertura do host/decoder com `setSurfaceSize(1904,704)` no momento
  correto.

## Plano Para Quando A Central Estiver Acessivel

Nao alterar codigo antes de capturar as quatro fases:

```bash
HEADUNIT_HOST=<ip> HEADUNIT_LOCAL_HOST=192.168.15.35 \
  ./tools/headunit-dev/headunit.sh carplay-baseline cp-field-00-arrival-before-touch

HEADUNIT_HOST=<ip> HEADUNIT_LOCAL_HOST=192.168.15.35 \
  ./tools/headunit-dev/headunit.sh carplay-baseline cp-field-01-first-usb-d0-clean

HEADUNIT_HOST=<ip> HEADUNIT_LOCAL_HOST=192.168.15.35 \
  ./tools/headunit-dev/headunit.sh carplay-baseline cp-field-02-first-d3-dirty

HEADUNIT_HOST=<ip> HEADUNIT_LOCAL_HOST=192.168.15.35 \
  ./tools/headunit-dev/headunit.sh carplay-visual cp-field-02-first-d3-dirty

HEADUNIT_HOST=<ip> HEADUNIT_LOCAL_HOST=192.168.15.35 \
  ./tools/headunit-dev/headunit.sh carplay-baseline cp-field-03-second-usb-d0-clean

HEADUNIT_HOST=<ip> HEADUNIT_LOCAL_HOST=192.168.15.35 \
  ./tools/headunit-dev/headunit.sh carplay-baseline cp-field-04-second-d3-clean
```

Depois comparar:

```bash
./tools/headunit-dev/headunit.sh carplay-compare \
  tools/headunit-dev/output/carplay-baseline-<cp-field-02-first-d3-dirty> \
  tools/headunit-dev/output/carplay-baseline-<cp-field-04-second-d3-clean>
```

## Evidencia Que Deve Decidir A Proxima Correcao

- MD5s montados e `carPlayPatchAutoMountPatchVersion`.
- `persist.haval.carplay.desired_display` e `persist.haval.carplay.video.height`.
- `am stack list` e se houve duplicata D0/D3.
- `dumpsys window` com bounds reais da Activity.
- `SurfaceFlinger` com `SurfaceView` e `activeBuffer`.
- Logcat de `CarPlayPatchManager`, `DisplayAppLauncher`, `CarPlayDisplayActivity`,
  `SurfaceHolder`, `setSurfaceSize`, `onSurfaceChanged`, `CarPlayManager`,
  `requestVideoFocusChange`, `ScreenResourceManager`, `cpScreen`, `NdkMediaCodec` e
  `ActivityTaskManager`.

## Guardrails

- Nao reintroduzir `VIDEO_FOCUS_CHANGE` em evento de AC/camera/app enquanto CarPlay real segue no
  D3.
- Nao usar `force-stop com.ts.carplay.app` em handoff normal.
- Nao redimensionar a window do D3 para `1904x704`; o ajuste validado e apenas no buffer nativo.
- Nao mexer em Android Auto.
- AC/HVAC e camera/AVM devem permanecer testes de regressao obrigatorios, porque no melhor estado
  atual nao estao gerando tela preta.
