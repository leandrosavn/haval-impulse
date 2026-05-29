# Regression Checklist

## Android

- Build debug passa.
- App inicia sem crash.
- `ForegroundService` sobe.
- Shizuku inicializa.
- `ServiceManager` recebe dados.

## Cluster

- Display 3 renderiza WebView.
- Card 3 mostra main menu quando esperado.
- AC aparece e responde a foco.
- Modo mapa aparece quando selecionado.
- Sem overlay de simulação em produção.

## Projeções

- CarPlay display 0 -> cluster 3.
- CarPlay cluster 3 -> display 0.
- Android Auto display 0 -> cluster 3.
- CarPlay e Android Auto não usam recuperação cruzada.
- Com CarPlay no cluster 3, abrir AC/HVAC no display 0 e tocar na tela do CarPlay: D3 continua
  mostrando CarPlay sem tela preta.
- Com CarPlay no cluster 3, abrir câmera/AVM física no display 0: D3 continua mostrando CarPlay.
- Com CarPlay no cluster 3, navegar pelos cards fisicos do volante:
  - `cardId=1` mostra o main menu sobre o CarPlay sem fundo preto;
  - `cardId=3` mostra o card de AC sobre o CarPlay sem fundo preto;
  - card original/neutro volta ao Mapa/CarPlay limpo.
- Antes de deploy/merge que toque CarPlay, rodar:

```bash
python3 scripts/carplay-patches/verify_regression_lock.py
```

## Deploy

- APK enviado completo via curl.
- HTML hot deploy substitui `/data/local/tmp/app.html`.
- Rollback remove `/data/local/tmp/app.html`.

## Evidência

- Comandos executados.
- Logs relevantes.
- Screenshots se houver UI.
