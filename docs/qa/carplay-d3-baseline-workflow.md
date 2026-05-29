# Baseline CarPlay D3

## Objetivo

Criar um baseline reproduzivel do fluxo CarPlay no D3 antes de qualquer nova alteracao funcional.
Esse baseline existe para bloquear regressao cruzada entre AC, camera/AVM, apps do D0, reboot e
ajustes de UI.

## Regras

- Nao misturar Android Auto neste fluxo.
- Nao alterar mais de uma camada por vez:
  - patch nativo CarPlay;
  - watchdog/handoff Kotlin;
  - `InstrumentProjector2`/WebView/UI.
- Nao chamar um estado de "corrigido" sem repetir o roteiro apos reboot.
- Sempre salvar a evidencia do baseline e da tentativa nova em diretorios separados.

## Comandos

Captura de evidencia por cenario:

```bash
HEADUNIT_HOST=192.168.15.100 HEADUNIT_LOCAL_HOST=192.168.15.35 \
  ./tools/headunit-dev/headunit.sh carplay-baseline cp-02-d3-clean
```

Com expectativa explicita:

```bash
HEADUNIT_HOST=192.168.15.100 HEADUNIT_LOCAL_HOST=192.168.15.35 \
  BASELINE_EXPECTED="CarPlay permanece visivel no D3 com AC aberto" \
  ./tools/headunit-dev/headunit.sh carplay-baseline cp-03-ac-open
```

Comparacao entre duas capturas:

```bash
./tools/headunit-dev/headunit.sh carplay-compare \
  tools/headunit-dev/output/carplay-baseline-<baseline> \
  tools/headunit-dev/output/carplay-baseline-<candidate>
```

Trava estatica antes de qualquer deploy CarPlay:

```bash
python3 scripts/carplay-patches/verify_regression_lock.py
```

## Matriz Obrigatoria

| Cenario | Quando capturar | Esperado |
| --- | --- | --- |
| `cp-01-d0-clean` | CarPlay aberto no D0 | CarPlay limpo no D0 |
| `cp-02-d3-clean` | Apos enviar D0 -> D3 | CarPlay limpo no D3 |
| `cp-03-ac-open` | Com AC/HVAC aberto no D0 | CarPlay permanece visivel no D3 |
| `cp-04-app-d0` | Com app comum aberto no D0 | CarPlay permanece visivel no D3 |
| `cp-05-camera` | Com camera/AVM fisica ativa | CarPlay permanece visivel no D3 |
| `cp-06-reboot-d3-clean` | Apos reboot e reconexao | CarPlay volta limpo no D3 |
| `cp-07-reboot-ac-open` | Apos reboot, com AC/HVAC aberto | CarPlay permanece visivel no D3 |
| `cp-08-reboot-app-d0` | Apos reboot, com app comum no D0 | CarPlay permanece visivel no D3 |
| `cp-09-reboot-camera` | Apos reboot, com camera/AVM ativa | CarPlay permanece visivel no D3 |

## Evidencia Minima

Cada captura deve conter:

- `manifest.txt` com branch, commit, lock, MD5s e hosts.
- `local/verify-regression-lock.txt`.
- `remote/` com props, packages, mounts, processos, USB, `am stack list`, `dumpsys` e `logcat`.
- `filtered/` com foco nos arquivos comparaveis.
- `screenshots/` com tentativas de D0/D3/cluster fisico.

## Critério de Aprovação

Uma mudanca so pode substituir o baseline anterior se:

- passar `verify_regression_lock.py`;
- passar a matriz obrigatoria antes do reboot;
- passar a matriz relevante apos reboot;
- nao puxar CarPlay do D3 para o D0;
- nao introduzir tela preta, frame sujo sustentado ou loop do Impulse.

## Limites

- `screencap` continua sendo evidencia auxiliar, nao prova visual absoluta.
- A confirmacao final de camera/AVM continua dependendo do teste fisico do usuario.
- Se um cenario falhar, coletar a captura do estado ruim antes de reiniciar, desconectar ou
  implantar novo patch.
