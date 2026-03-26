# Persistent Bottom Bar Architecture (Android 9)

Este documento detalha o funcionamento e a arquitetura da barra inferior customizada e persistente utilizando o `WindowManager` e o `overscan`.

## Visão Geral
A implementação depende fortemente em manipular as restrições físicas da área de renderização percebida pelo sistema e adicionar uma camada flutuante.

### 1. Gestão de Layout com wm overscan
Ao invés de exibir a barra interativa apenas como sobreposição (o que faria as UIs de terceiros renderizarem atrás dela e tornaria botões inalcançáveis), utilizamos o shell script do sistema interno:
`wm overscan [left],[top],[right],[bottom]`

Este processo:
- Aplica um corte falso na renderização de tela no nível do SurfaceFlinger.
- Informa aos ActivityManagers e InputMethodManagers (Teclados) qual é o escopo real do dispositivo.
- A barra entra neste buraco invisível utilizando layouts customizados.

### 2. Rendering (WindowManager)
Um serviço Android de foreground será o host dessa View persistente.
Para ser desenhado sobrepondo tudo, mas ainda ficar restrito ao local:
- **Type**: `TYPE_APPLICATION_OVERLAY`
- **Flags Importantes**: 
  - `FLAG_NOT_FOCUSABLE`: Para não roubar o foco permanente da tela. Se tiver que usar o teclado na barra, precisará gerenciar esse recurso momentaneamente.
  - `FLAG_LAYOUT_IN_SCREEN`: Isso assegura que o WindowManager ignorará limites convencionais, como a nova zona "morta" que o overscan criou, posicionando nossa View precisamente ali.

### 3. Permissões
É demandado o consentimento explícito:
- `SYSTEM_ALERT_WINDOW`: Para flutuar com TYPE_APPLICATION_OVERLAY.
Privilégios para execução do Shell Requer Root ou App rodando via Shizuku. O projeto Haval Shisuku possui infraestrutura implementada.

### 4. Controle Dinâmico (HVAC & System)
- **AC Fan Speed**: Implementado via `CAR_HVAC_FAN_SPEED`. Utiliza uma UI de 7 passos com renderização em `Canvas` para feedback visual imediato.
- **Navegação (Shizuku)**: Os comandos de sistema (Back, Home, Recents) são executados via `input keyevent` em threads separadas (`CoroutineScope`) para evitar bloqueio da UI Thread e garantir latência próxima de zero.
