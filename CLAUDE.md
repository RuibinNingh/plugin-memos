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

## Image upload on flaky networks

The Memos instance is hosted in Hong Kong; the cross-border link drops often. Naive single-shot multipart uploads of large images therefore fail mid-flight, leaving the memo half-saved. Rules for Claude Code when touching any image upload path in this plugin:

1. Never push large image bytes straight through the Memos API in one HTTP request. Memos `0.29.1` has no native chunked upload.
2. Always upload images through Halo's Attachment API first (chunked `POST /upload` with `Content-Range` headers), then store the resulting attachment URL in the Memos memo `content` field. Treat Halo Attachment as the only supported image upload channel.
3. Client-side compression is mandatory before upload:
   - Long edge ≤ 1920 px
   - Re-encode as JPEG quality ≈ 0.85
   - Expect ~10 MB → ~200 KB reduction; size this in docs/comments when introducing a new upload path.
4. Server-side nginx on the HK host must include:
   ```nginx
   client_body_timeout 300s;
   send_timeout 300s;
   client_max_body_size 50m;
   proxy_request_buffering off;
   proxy_http_version 1.1;
   proxy_read_timeout 300s;
   ```
   `proxy_request_buffering off` is the critical line — without it nginx buffers the full body before forwarding, which amplifies upload failures on weak links.
5. When designing new flows, prefer: browser → mainland object store (OSS / COS) → plugin pulls asynchronously into Halo Attachment → URL written into Memos. Memo text saves immediately; image sync runs as a background job with retry. Only fall back to direct HK upload if the user explicitly rejects the OSS path.
6. If a local code path conflicts with rules 1–5 (e.g. an existing direct multipart upload), stop and surface the conflict before coding, the same way Memos local-docs vs official-docs conflicts are handled.

## Image delivery cache

The deployed Memos instance may be local to Halo, while the slow leg is blog server → visitor browser. For image delivery work:

1. Keep original Memos files available at `/memos/proxy/file/**`.
2. Serve theme/Console thumbnails through `/memos/proxy/image/**`, backed by local compressed derivative files under the Halo plugins root: `memos/cache/images/`.
3. Do not write compressed derivatives into the Memos data directory, and do not mutate Memos originals.
4. JPEG/JPG compress to JPEG; large PNG without alpha converts to JPEG; PNG with alpha stays PNG after resize; GIF/SVG/APNG use the original route.
5. Manual cache refresh is exposed in Console through `/apis/console.api.memos.plugin.halo.run/v1alpha1/cache/refresh`; keep this working when changing cache internals.
