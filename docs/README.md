# `docs/` — GitHub Pages content

Everything in this folder is served verbatim at
`https://kikin81.github.io/nubecita/` by GitHub Pages.

## What's hosted here

| Path | Purpose |
|------|---------|
| `index.html` | Minimal landing page. Satisfies the same-origin requirement for the OAuth client metadata `client_uri`, `tos_uri`, and `policy_uri` fields. |
| `oauth/client-metadata.json` | AT Protocol OAuth 2.0 client metadata fetched by Bluesky's authorization server during PAR. See `oauth/README.md` for fields and constraints. |

## Repo-side setup (one-time)

GitHub Pages must be enabled for this repo with source = "Deploy from
branch, `main` / `/docs`". After merge, the first publish typically
takes 30–60 s. Subsequent commits to `docs/` on `main` redeploy
automatically.

If Pages is not yet enabled:

```bash
echo '{"source":{"branch":"main","path":"/docs"}}' \
  | gh api -X POST repos/kikin81/nubecita/pages --input -
```

(`gh api -f source='...'` doesn't work here — it sends the value as a
string, and the Pages API requires `source` to be a nested object.)

## Dev → prod URL swap

The `client_id` in `oauth/client-metadata.json` is the exact URL where
the JSON is served. Moving to a custom domain changes that URL, which
invalidates every session signed against the old `client_id`. This is
fine while pre-launch; flag it in release notes when we do it.
