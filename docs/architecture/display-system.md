# Display System

Atualizado em: 2026-05-24

## O Que Foi Identificado

O app usa displays Android secundários para renderizar camadas e apps:

- Display 0: central principal.
- Display 1: usado por `InstrumentProjector` como camada de refresh/HUD.
- Display 3: cluster principal, usado por `InstrumentProjector2` e por apps/projeções.

`ProjectorManager` procura displays via `DisplayManager` e instancia presentations quando o display alvo existe. Se um display ainda não existe, registra listener.

## Arquivos Relacionados

- `ProjectorManager.java`
- `BaseProjector.kt`
- `InstrumentProjector.kt`
- `InstrumentProjector2.kt`
- `DisplayAppLauncher.kt`

## Regras Observadas

- O cluster usa fullscreen lógico de 1920x720 em fluxos críticos.
- Apps em display 3 podem exigir bounds customizados, exceto CarPlay que deve ficar fullscreen físico.
- A WebView do cluster é transparente e pode coexistir com apps nativos.

## Riscos

- Bounds incorretos podem deslocar ou ocultar projeções.
- `mAppBounds` pode diferir de `mBounds`.
- Remover stack errada pode tirar app do display 3.

## A Confirmar

- Mapeamento completo dos displays em todas as versões da central.
- Comportamento do display 1 além da camada de refresh descrita no código.
