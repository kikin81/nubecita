# `docs/` — repository documentation

Plain in-repo documentation: design-system contracts, runbooks
(`beads-multi-machine.md`), architecture notes (`adaptive-layouts.md`),
and the OAuth client-metadata field reference (`oauth/README.md`). Read
it on GitHub or in your editor — nothing here is deployed anywhere.

## History: this folder used to be a GitHub Pages site

Until `nubecita-i6st` (2026-07-05), GitHub Pages served this folder at
`https://kikin81.github.io/nubecita/` for one load-bearing reason:
hosting the development OAuth client metadata
(`oauth/client-metadata.json`, whose URL *was* the dev `client_id`)
before `nubecita.app` existed. Production moved to
`https://nubecita.app/oauth/client-metadata.json` (hosted from the
`nubecita-web` repo) on 2026-05-16 (`90031fd4`); once every session
bound to the old client_id had aged past the 2-week public-client cap,
the Pages site had no remaining consumers and was disabled — it had
been redeploying on every push to `main` (legacy branch mode) and
racing itself into spurious `pages build and deployment` failures
whenever the release bot pushed right after a merge.

If Pages hosting is ever needed again, prefer workflow-mode deploys
gated on `docs/**` changes with a concurrency group, not legacy branch
mode.
