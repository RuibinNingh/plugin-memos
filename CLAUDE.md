# Project Context — Halo Plugin Starter

## Runtime / development environment

- This is a Halo plugin development project on Mini-RuibinNingh.
- Main project path: `/home/ruibinningh/projects/halo-plugins/plugin-starter`.
- Run Claude Code as Linux user `ruibinningh`, not root.
- Development Halo console: `http://192.168.0.145:8092/console/`.
- Development Halo container: `halo-for-plugin-development`.
- Production Halo is separate on port `8090`; do not modify production unless explicitly asked.
- This plugin is currently backend-only. Do not add a Vue/pnpm UI unless the user explicitly asks for a Console UI.
- The local theme `thyuu-xingdu` is bind-mounted into the development Halo instance for plugin + theme integration testing.

## Key commands

```bash
# Start the development Halo instance
sg docker -c './gradlew haloServer'

# Build the plugin
./gradlew clean build --quiet
```

If Docker permission fails in an old SSH session, use `sg docker -c '...'`.

## Halo plugin guidance

- Use the Claude Code skill `/halo-plugin-dev` for Halo plugin work.
- Plugin Java code lives under `src/main/java/`.
- Plugin manifest lives at `src/main/resources/plugin.yaml`.
- Backend-only plugins may show no Console app entry; verify backend status via plugin phase/logs rather than guessing UI failure.

## Memos API usage

This project may integrate with the self-hosted Memos instance on Mini-RuibinNingh.

Current deployed Memos version:

```text
0.29.1
```

Official API documentation:

```text
https://usememos.com/docs/api/latest
```

Local project mirror / notes:

```text
docs/vendor/memos-api/README.md
```

Rules for Claude Code when writing Memos integration code:

1. Do not guess Memos endpoint paths, request bodies, or response fields from memory.
2. Before coding against Memos, read `docs/vendor/memos-api/README.md` and, if network is available, consult `https://usememos.com/docs/api/latest`.
3. Target Memos `0.29.1` unless the user says it was upgraded.
4. Use Bearer token authentication for protected APIs.
5. Never hardcode access tokens, cookies, or Authorization headers in source code, docs, tests, or logs.
6. Read tokens from environment variables or secret configuration only.
7. If local docs and official docs conflict, stop and report the conflict before coding.

## Memos local service facts

- Local container name: `memos`.
- Local port on Mini-RuibinNingh: `5230`.
- Local HTTP base for server-side development: `http://127.0.0.1:5230`.
- Docker compose path: `/www/server/panel/data/compose/memos/docker-compose.yaml`.
- Data directory: `/www/server/panel/data/compose/memos/data`.

Do not modify or upgrade Memos from this plugin project unless the user explicitly asks for operations work.
