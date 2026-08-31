# Tema GT

Dois mostradores redondos com aro metálico — velocidade à esquerda, RPM + potência
elétrica à direita — em dois layouts:

| Layout | Geometria | Uso |
| :-- | :-- | :-- |
| `GT` | R=252, centro y=320 | mostradores grandes |
| `GT Compacto` | R=222, centro y=358 | libera a área central para o mapa projetado |

**GT Compacto é o padrão.**

O app não conhece os nomes dos nossos layouts: o `normalizeClusterDisplay()` do
`InstrumentProjector2` só deixa passar `Normal`/`Reduzido`/`Clean`/`EsportivoClean` e
converte qualquer outra coisa em `Normal`. Por isso o tema traduz (`DISPLAY_MAP`):

| O app manda | O GT usa |
| :-- | :-- |
| `Normal` (padrão da preferência), `Reduzido` | **GT Compacto** |
| `Clean`, `EsportivoClean` | GT (mostradores grandes) |

Enquanto o GT não tem menu próprio, para usar os mostradores grandes: trocar
temporariamente para o tema Basic, escolher **Clean** no menu Display do cluster e voltar
para o GT — a preferência é do app, não do tema.

## Build

O tema é um único HTML sem dependências (canvas + DOM puro, sem framework e sem
build de bundler). O `build.js` só embute o CSS das fontes e escreve o arquivo final:

```bash
node build.js
```

Saída: `../Themes/GT/index.html` — que é o que o `ThemeManager` baixa para o carro.

> **Ao publicar, bumpar a versão nos DOIS lugares:** `Themes/GT/theme.xml` **e**
> `Themes/themes.json`. Do app 1.0.0.75 em diante a versão que o carro compara vem
> do manifesto `themes.json`; só o `theme.xml` não faz o update aparecer.

## Arquitetura

- **`#cvStatic`** — aro, marcas, números e rótulos. Desenhado **uma vez** por layout.
- **`#cvDyn`** — ponteiros e o preenchimento do arco de média. Redesenhado só quando
  o dado muda, coalescido por `requestAnimationFrame`.
- **`#ui`** — os textos são DOM (tipografia melhor que `fillText`), criados uma vez
  e atualizados por `textContent`.

O aro cromado usa um gradiente cônico **feito à mão** (240 fatias interpoladas),
porque `createConicGradient` pode não existir na WebView da central.

## Contrato com o app

Vem do `InstrumentProjector2`:

| Chamada | Efeito |
| :-- | :-- |
| `control(key, value)` | dados do veículo (ver `MAP` no fonte) |
| `updateWarning(key, value)` | alerta crítico → o tema se esconde e devolve a tela ao painel original |
| `Android.heartbeat()` | chamado a cada 5s; sem isso o watchdog recarrega a WebView a cada 15s |
| `Android.setCardId(n)` | o tema devolve o `cardId` recebido; o app precisa dele para redimensionar os apps projetados |
| `showScreen(nome)` / `focus(item)` | trocam a tela e movem a seleção do menu |
| `cleanup()` | no-op |

### Menu do Impulse

O núcleo do mostrador **direito** vira o menu quando o app seleciona o card dele —
`cardId == 1` abre o menu principal e `cardId == 3` o ar, mesma regra do tema Basic. Nos
demais cards o mostrador volta a ser conta-giros; em `cardId == 0` ele some (card do
painel original).

Telas desenhadas: `main_menu`, `display_selection`, `regen`, `aircon`, `graph`. A seleção
vem de `focus(item)` e é destacada num carrossel (o item focado grande no centro, vizinhos
esmaecidos).

⚠️ **A lógica é toda do lado Java** (`MainMenu`, `RegenScreen`, `DisplaySelectionScreen`…):
o tema apenas desenha o estado que recebe. Não há ação disparada pelo tema.

### Quando o tema sai da frente (`sideVisible`)

| Situação | Efeito |
| :-- | :-- |
| `cardId == 0` | o mostrador **direito** some — aquele espaço é do card do painel original (Viagem A/B, odômetro) |
| `appInDash == 'left'` / `'right'` | o mostrador daquele lado some, liberando o app projetado |
| `appInDash == true` | o tema inteiro se esconde |
| `clusterEnabled == false` | o tema inteiro se esconde |

### Chaves que o projector já empurra e o tema consome

`carSpeed`, `engineRPM`, `evPowerKw`, `gasConsumption`, `fuelPercent`, `fuelRange`,
`batteryPercent`, `batteryRange`, `odometer`, `outside_temp`, `inside_temp`,
`gearState`, `drivingMode`, `clockTime`, `display`.

### Chaves adicionadas para este tema (branch `feat/cluster-tsr-hev`)

| Chave | Origem |
| :-- | :-- |
| `speedLimit` | `car.map.tsr.nav_speed_limit` + `nav_speed_limit_sign_status`, via `speedLimitValue()` no `InstrumentProjector2`. Devolve `0` quando não há placa; o tema esconde o sinal nesse caso. |
| `isHev` | `isHev()` (`car.ev.setting.avas_config == 0`), que já existia e agora é empurrado para o tema. |

As duas chaves entraram também no `DEFAULT_KEYS` do `ServiceManager`, senão não chega
notificação de mudança. Exigem APK novo — o tema em si é atualizável sem release.

> ⚠️ A codificação das chaves `car.map.tsr.*` **não foi confirmada ao vivo**. A leitura é
> defensiva (só aceita inteiro entre 5 e 200), mas o comportamento real precisa de recon no
> carro: marcar as duas chaves em "Dados do veículo" e observar o que aparece com e sem placa
> reconhecida. Se a semântica for outra, o ajuste é dentro de `speedLimitValue()`.

## Pendências antes de rodar no carro

- **Recorte do topo.** A WebView fica por cima do cluster stock: onde o tema pinta, o
  original some. É preciso um `adb shell screencap` para saber o retângulo exato do
  relógio do stock e mascará-lo **sem** cobrir as luzes espia (setas, farol alto, ABS,
  cinto, airbag). Enquanto isso não for medido, o relógio do tema pode duplicar com o
  do painel original.
- Mesma decisão vale para marcha, TSR, modo de condução e temperatura externa.
- Calibrar ao vivo: render headless não desenha canvas e fontes igual à central.
