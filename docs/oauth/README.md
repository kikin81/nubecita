# OAuth client metadata (reference)

The production client metadata document — the JSON that Bluesky's
authorization server fetches during PAR to validate Nubecita's
`client_id` — is served at:

`https://nubecita.app/oauth/client-metadata.json`

and is **hosted from the `nubecita-web` repo**, not this one. This repo
previously served a development copy from GitHub Pages
(`kikin81.github.io/nubecita/...`); that legacy client was superseded on
2026-05-16 (`90031fd4`) and the Pages hosting removed (`nubecita-i6st`).
The field rules below still apply to the production document and are
kept here because they encode hard-won AT Protocol constraints.

## Field rules (AT Protocol + Bluesky-specific)

| Field | Rule |
|-------|------|
| `client_id` | **Must** match the hosting URL exactly — the URL *is* the client identity. Changing it invalidates every session signed against the old value. |
| `application_type` | `native` — Android app, not web. |
| `client_uri`, `tos_uri`, `policy_uri` | Same origin as `client_id` required. |
| `dpop_bound_access_tokens` | `true` — Bluesky requires DPoP-bound tokens. |
| `token_endpoint_auth_method` | `none` — public client (no client secret). |
| `grant_types` | `["authorization_code", "refresh_token"]`. |
| `response_types` | `["code"]`. |
| `redirect_uris` | The app-scheme form uses a **single slash** after the scheme, not `://`. The verified-App-Link form (`https://nubecita.app/oauth-redirect/`) needs the **trailing slash** — GitHub Pages 301s the no-slash form, which drops the OAuth `code` (nubecita-o4rv.1). |
| `scope` | `atproto transition:generic transition:chat.bsky`. `atproto` is the required base scope; `transition:generic` covers standard AppView RPCs; `transition:chat.bsky` covers `chat.bsky.*` via the chat AppView (`did:web:api.bsky.chat`) — without it every chat call returns `403 ScopeMissingError`. The consent screen lets users opt out of chat individually. |

## Redirect URI convention — FQDN reversed, NOT the app's applicationId

For custom-scheme redirects, AT Protocol's Discoverable Client rule
mandates the scheme be the FQDN of `client_id` reversed — it does
**not** match the Android `applicationId`. Bluesky's authorization
server enforces this at PAR time and rejects mismatches with
`HTTP 400 invalid_redirect_uri`. The receiving intent filter in
`app/src/main/AndroidManifest.xml` must declare the same scheme + path;
if the hosting domain ever changes, both sides update together as a
single PR — and every existing session is invalidated (flag it in
release notes).

## Validation

```bash
curl -I https://nubecita.app/oauth/client-metadata.json
# Expect: 200 OK, Content-Type: application/json

curl -s https://nubecita.app/oauth/client-metadata.json | jq .
# Should parse cleanly.
```

End-to-end: `AtOAuth.beginLogin(handle)` — the authorization server
fetches the document and rejects PAR if any field is malformed.
