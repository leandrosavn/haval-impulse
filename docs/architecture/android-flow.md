# Android Flow

Atualizado em: 2026-05-24

## Fluxo Identificado

1. `App.onCreate()` salva application context, aplica mounts Android Auto quando instalados e inicia `ForegroundService`.
2. `ForegroundService` inicia como foreground service e tenta inicializar Shizuku via telnet local.
3. Após Shizuku, o serviço inicia rotinas como ADB/SSH, iptables, patches e demais serviços internos.
4. `ServiceManager` integra serviços veiculares, cache de dados e eventos para UI/projectors.
5. `ProjectorManager` cria projectors nos displays secundários.
6. Telas Compose em `ui/screens` expõem configurações para usuário.

## Arquivos Relacionados

- `App.java`
- `ForegroundService.java`
- `BootReceiver.java`
- `ServiceManager.java`
- `MainActivity.kt`
- `SplashActivity.kt`
- `ui/screens/TelasScreen.kt`
- `ui/screens/InstallAppsScreen.kt`

## Riscos

- Falha em Shizuku impede comandos privilegiados.
- Alterar `ForegroundService` pode quebrar boot.
- Alterar receivers pode impedir start após atualização/reboot.
- `ServiceManager` tem grande superfície de integração veicular.

## A Confirmar

- Sequência exata de inicialização em cold boot real na central.
- Quais permissões precisam ser concedidas manualmente em cada instalação.
