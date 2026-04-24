# OAuth client metadata

`client-metadata.json` is the public document Bluesky's authorization
server fetches during PAR (Pushed Authorization Request) to validate
Nubecita's `client_id`.

## Served at

`https://kikin81.github.io/nubecita/oauth/client-metadata.json`
(must be `Content-Type: application/json`; GitHub Pages sets this
automatically for `.json` files.)

## Field rules (AT Protocol + Bluesky-specific)

| Field | Value | Why |
|-------|-------|-----|
| `client_id` | `https://kikin81.github.io/nubecita/oauth/client-metadata.json` | **Must** match the hosting URL exactly. |
| `application_type` | `native` | Android app, not web. |
| `client_uri`, `tos_uri`, `policy_uri` | `https://kikin81.github.io/nubecita` | Same origin as `client_id` required. |
| `dpop_bound_access_tokens` | `true` | Bluesky requires DPoP-bound tokens. |
| `token_endpoint_auth_method` | `none` | Public client (no client secret). |
| `grant_types` | `["authorization_code", "refresh_token"]` | Standard OAuth 2.0 flow + refresh. |
| `response_types` | `["code"]` | Authorization code flow. |
| `redirect_uris` | `["net.kikin.nubecita:/oauth-redirect"]` | **Single slash** after the scheme, not `://`. Matches the app's `applicationId`. |
| `scope` | `atproto transition:generic` | Full AT Protocol API access; `transition:generic` is the current umbrella scope Bluesky accepts. |

## Redirect URI convention

The redirect URI scheme `net.kikin.nubecita` must equal the Android
`applicationId` in `app/build.gradle.kts`. The receiving intent filter
is wired in `AndroidManifest.xml` (delivered under `nubecita-ck0` —
this task only hosts the metadata).

## Dev → prod swap

Moving to a custom domain (e.g. `https://nubecita.kikin.net`) changes
`client_id`. Every existing session is signed against the old
`client_id` and will be invalidated by the auth server. Acceptable
while pre-launch; flag explicitly in release notes when we do the swap.

## Validation

Quick validation after GitHub Pages publishes:

```bash
curl -I https://kikin81.github.io/nubecita/oauth/client-metadata.json
# Expect: 200 OK, Content-Type: application/json

curl -s https://kikin81.github.io/nubecita/oauth/client-metadata.json | jq .
# Should parse cleanly.
```

End-to-end validation happens once `nubecita-ck0` lands and we can run
`AtOAuth.beginLogin(handle)` — the authorization server fetches this
document and rejects PAR if any field is malformed.
