# Agent Operational Rules

Atualizado em: 2026-05-24

## Regras Para Agentes

- Ler `AGENTS.md` primeiro.
- Ler `.ai-context/` antes de editar.
- Consultar `docs/architecture/` para o fluxo afetado.
- Usar skills em `.agents/skills/` quando aplicável.
- Não alterar funcionalidade fora do escopo.
- Não reverter mudanças existentes que não foram feitas pelo agente.
- Registrar riscos e validações no handoff.

## Agentes Recomendados

- `haval-architect`: análise e plano.
- `haval-code-mapper`: mapeamento de fluxo.
- `haval-android`: implementação Kotlin/Java.
- `haval-webview`: bridge e WebView.
- `haval-frontend`: widgets HTML/CSS/JS.
- `haval-performance`: revisão read-only de performance.
- `haval-qa`: regressão.
- `haval-docs-scribe`: documentação e handoff.

## Riscos Operacionais

- Multi-agent sem handoff pode gerar decisões divergentes.
- Implementação antes de mapeamento pode quebrar display/projeção.
- Docs genéricas reduzem valor da memória persistente.

## Encerramento Obrigatório

Atualizar:

- `.ai-context/HANDOFF.md`
- `.ai-context/CHANGELOG-AI.md`
- `.ai-context/NEXT-STEPS.md`
- `.ai-context/KNOWN-ISSUES.md`, se houver novo risco.
