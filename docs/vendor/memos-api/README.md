# Memos API Reference Notes

Target deployment on Mini-RuibinNingh:

```text
Memos version: 0.29.1
Local base URL: http://127.0.0.1:5230
Official API docs: https://usememos.com/docs/api/latest
Official docs home: https://usememos.com/docs
GitHub releases: https://github.com/usememos/memos/releases
```

This file is a project-local guide for Claude Code. It is not a full copy of the official docs. When implementing Memos integration, use this as the first local checklist, then verify details against the official API reference if network is available.

## Core rules

1. Do not rely on model memory for Memos API details.
2. Target Memos `0.29.1` unless the user says otherwise.
3. Prefer official API docs over old examples found online.
4. Protected APIs use Bearer token authentication.
5. Never hardcode tokens, cookies, or `Authorization` header values.
6. Store tokens in environment variables or secret config outside git.
7. If an endpoint returns `401`, do not work around it with cookies; use a proper Memos access token.

## Authentication

Official docs state that the Memos API uses Bearer Token authentication.

Request shape:

```http
Authorization: Bearer <access-token>
```

Recommended app configuration:

```bash
MEMOS_BASE_URL=http://127.0.0.1:5230
MEMOS_ACCESS_TOKEN=...   # never commit this
```

In code, load these from environment/config. Do not log the token.

## Verified local service status

As of setup, the local Memos container responds on:

```text
GET http://127.0.0.1:5230/ -> 200
```

Container logs confirmed:

```text
Memos 0.29.1 started successfully
Data directory: /var/opt/memos
Database driver: sqlite
Server running on port 5230
```

## Important endpoint docs

Official API reference index:

```text
https://usememos.com/docs/api/latest
```

Memo service pages found from official search results:

```text
https://usememos.com/docs/api/latest/memoservice/ListMemos
https://usememos.com/docs/api/latest/memoservice/CreateMemo
https://usememos.com/docs/api/latest/memoservice/UpdateMemo
https://usememos.com/docs/api/latest/memoservice/ListMemoComments
https://usememos.com/docs/api/latest/memoservice/CreateMemoShare
```

User/token related page:

```text
https://usememos.com/docs/api/latest/userservice/CreatePersonalAccessToken
```

## Locally probed endpoints

These were probed against the live local `0.29.1` instance without a token. Use them as hints only; verify with official docs before coding.

### List memos

```http
GET /api/v1/memos
```

Unauthenticated local result: `200`, returning public memos.

Observed response shape begins like:

```json
{
  "memos": [
    {
      "name": "memos/<id>",
      "state": "NORMAL",
      "creator": "users/<username>",
      "createTime": "2026-05-16T10:24:49Z",
      "updateTime": "2026-05-16T10:24:49Z",
      "content": "...",
      "visibility": "PUBLIC",
      "tags": [],
      "pinned": false,
      "attachments": []
    }
  ]
}
```

Notes:

- Resource names are gRPC-style strings such as `memos/{memo}` and `users/{user}`.
- Do not assume older Memos v0.14/v0.2x field names still apply.
- Use the official ListMemos docs for pagination/filter parameters.

### Users

```http
GET /api/v1/users
```

Unauthenticated local result: `401` with:

```json
{"code":16,"message":"user not authenticated","details":[]}
```

Use Bearer token authentication for protected user/admin APIs.

### Non-endpoints / old guesses that returned 404 locally

The following returned 404 on the local `0.29.1` instance and should not be guessed as valid without checking docs:

```text
/api/v1
/api/v1/resources
/api/v1/tags
/api/v1/markdown
```

## Implementation checklist for Claude Code

Before writing code that calls Memos:

1. Read this file.
2. Open the relevant official docs page under `https://usememos.com/docs/api/latest`.
3. Confirm:
   - HTTP method
   - endpoint path
   - query parameters
   - request body
   - response shape
   - auth requirement
4. Add configuration for `MEMOS_BASE_URL` and `MEMOS_ACCESS_TOKEN` if needed.
5. Add error handling for:
   - missing token
   - 401/403 auth failures
   - 404 endpoint mismatch
   - network timeout
   - schema changes
6. Do not print secret values in logs.

## Example curl patterns

Public list memos check:

```bash
curl -sS "$MEMOS_BASE_URL/api/v1/memos"
```

Authenticated request pattern:

```bash
curl -sS \
  -H "Authorization: Bearer ${MEMOS_ACCESS_TOKEN}" \
  "$MEMOS_BASE_URL/api/v1/users"
```

Never paste real token values into chat, source code, shell history, or docs.
