---
name: translate-strings
description: Use when translating this repo's Android `strings.xml` resources into another language (es-419, pt-BR, etc.) — adding a brand-new locale, or backfilling the strings the `MissingTranslation` lint guard flags after new keys land. Repo-owned translations (NOT Play Console cloud auto-translate), so the app is genuinely localized and the marketing screenshots can render real translated UI. Trigger on "/translate-strings <locale>", "translate the app strings to <lang>", "add a <locale> locale", or "lint says MissingTranslation".
---

Produce repo-owned `values-<qualifier>/strings.xml` translations with consistent terminology, preserved format args/plurals, and hard verification. This is how you satisfy the `MissingTranslation` lint error (promoted in both convention plugins) — a new string can't ship without its translations.

Why repo-owned and not Play's free Gemini auto-translate: Play injects translations into the *served* bundle at release time with **no export**, so they can't feed a local/bench build or be screenshotted. Owning them in `res/values-*` gives a real localized app + diffable PRs + screenshottable UI.

## Pick the mode

- **New locale (full)** — the locale has no `values-<qualifier>/` yet. Translate *every* source string across all modules. Fan out (below).
- **Backfill (incremental)** — the locale exists but is missing keys (a new string landed; `MissingTranslation` fails). Translate *only* the missing keys and merge them in. Usually small — do it inline, no fan-out.

## Locale → Android qualifier

Play/BCP-47 code → `res/values-<qualifier>/`:

| Locale | Qualifier folder | Notes |
|---|---|---|
| `es-419` | `values-b+es+419` | numeric UN-M.49 region **must** use the BCP-47 `b+` form (no `-r` form exists for `419`) |
| `pt-BR` | `values-pt-rBR` | 2-letter region → legacy `-r` form (more backward-compatible) |
| `es` / `pt` | `values-es` / `values-pt` | macro language (serves every region of that language) |
| `es-ES` / `pt-PT` | `values-b+es+ES` or `values-es-rES` / `values-pt-rPT` | 2-letter region → `-r` form works |
| `zh-Hans` | `values-b+zh+Hans` | script subtag → `b+` form |

The repo currently ships `es-419` (`values-b+es+419`) and `pt-BR` (`values-pt-rBR`).

## Find the work

```bash
# Source files (the English source of truth):
find . -name "strings.xml" -path "*/src/main/res/values/strings.xml" | grep -v "/build/"

# Backfill mode — keys missing in a locale, per module (QUAL = e.g. b+es+419):
keys() { grep -v 'translatable="false"' "$1" | grep -oE 'name="[^"]+"' | sort -u; }
for src in $(find . -name strings.xml -path "*/src/main/res/values/strings.xml" | grep -v /build/); do
  dir=$(dirname "$(dirname "$src")"); loc="$dir/values-QUAL/strings.xml"
  [ -f "$loc" ] && comm -23 <(keys "$src") <(keys "$loc") | sed "s#^#$src  missing: #"
done
```

## Translation rules (STRICT — a violation crashes the app or fails lint)

1. Keep every `name="..."` key EXACTLY. Translate only the human-readable text.
2. Preserve every format specifier verbatim — `%1$s`, `%2$d`, `%d`, `%s`, `%%` — in an order valid for the sentence. Positional args (`%1$s`) may be reordered; never drop, add, or mis-number.
3. `<plurals>`: es-419 and pt-BR use only `quantity="one"` and `quantity="other"`. Translate each item, keep its `%1$d`.
4. XML-escape: literal apostrophes as `\'`, `&` as `&amp;`; keep existing `\n` / `\"` escapes.
5. Preserve `tools:ignore="..."` and the `xmlns:tools` namespace if the source uses them. Preserve `<xliff:g>…</xliff:g>` tags + `xmlns:xliff` — translate *around* them, never inside.
6. Do NOT translate brand/proper nouns: `nubecita`, `Nubecita`, `Bluesky`, `Nubecita Pro`, `AT Protocol`, `Pro`, product names, `@handles`, URLs, `did:`/`at://` ids. Brand names keep their casing (`nubecita` stays lowercase).
7. OMIT the source's developer comments — output a clean file: `<?xml version="1.0" encoding="utf-8"?>`, `<resources>` (with any needed namespaces), then the `<string>`/`<plurals>` in the SAME order as the source.
8. Natural, idiomatic, concise UI language — match the source's friendly/direct tone; keep button labels short. Use the official Android term where one exists (e.g. picture-in-picture → *imagen en imagen* in es).
9. Follow the GLOSSARY for cross-module consistency.

## Glossary (extend as the app grows)

`en → es-419 / pt-BR`

- post (n) → publicación / post
- Post / publish (action) → Publicar / Postar
- reply (n) → respuesta / resposta · reply (v) → responder / responder
- repost → republicar / repostar · quote post → citar publicación / citar post
- like → Me gusta / Curtir
- follow → seguir / seguir · following → siguiendo / seguindo · followers → seguidores / seguidores
- feed → feed / feed · timeline → cronología / linha do tempo · thread → hilo / thread
- mention → mención / menção · mute → silenciar / silenciar · block → bloquear / bloquear · report → denunciar / denunciar
- search → buscar / buscar · profile → perfil / perfil · settings → ajustes / configurações · notifications → notificaciones / notificações
- compose → redactar / escrever · username/handle → nombre de usuario / nome de usuário
- chat → chat / chat · direct message → mensaje directo / mensagem direta
- sign in → iniciar sesión / entrar · sign out → cerrar sesión / sair
- Discover → Descubrir / Descobrir · Following (tab) → Siguiendo / Seguindo
- pinned → fijado / fixado · list → lista / lista · Supporter → Apoyador / Apoiador
- subscription → suscripción / assinatura · restore purchases → restaurar compras / restaurar compras
- picture-in-picture → imagen en imagen / picture-in-picture
- Save → Guardar / Salvar · Cancel → Cancelar / Cancelar · Done → Listo / Concluído
- Retry / Try again → Reintentar / Tentar de novo · Delete → Eliminar / Excluir · Edit → Editar / Editar · Refresh → Actualizar / Atualizar

Spanish notes: use the pronominal form (*se aplican*, not the anglicism *aplican*); adverb before adjective (*Sexualmente sugerente*).

## Execute

**Backfill (small):** translate the missing keys inline and insert each `<string>`/`<plurals>` into the locale file at the same position as the source (keep file order matching the source).

**New locale (full, ~hundreds of strings across ~19 modules):** fan out — one subagent per 1–3 modules, each given the rules + glossary above, each Reading its modules' source `values/strings.xml` and Writing both (or the one) locale files. Batch modules to ~80–110 strings per agent so the count stays manageable; run the agents concurrently. A shared glossary in every prompt is what keeps terminology consistent across the fan-out. (Reference: this is exactly how `nubecita-nea3` translated es-419 + pt-BR.)

Per-agent prompt skeleton: the **Translation rules** + **Glossary** sections verbatim, the locale→qualifier mapping, and the agent's assigned module paths. Tell each agent to report file paths written + counts and to touch nothing else.

## Verify (run ALL — these are the safety net)

```bash
# 1. Every locale file exists, is well-formed, and key-matches its source.
keys() { grep -v 'translatable="false"' "$1" | grep -oE 'name="[^"]+"' | sort -u; }
for src in $(find . -name strings.xml -path "*/src/main/res/values/strings.xml" | grep -v /build/); do
  dir=$(dirname "$(dirname "$src")")
  for q in values-b+es+419 values-pt-rBR; do            # ← edit to the locale(s) you touched
    f="$dir/$q/strings.xml"; [ -f "$f" ] || continue
    xmllint --noout "$f" || echo "MALFORMED: $f"
    diff <(keys "$src") <(keys "$f") >/dev/null || echo "KEY MISMATCH: $f"
  done
done

# 2. Format-arg parity (a mismatch = runtime crash). Compares the format-token
#    set of every string between source and each translation.
python3 - <<'PY'
import re, glob, os, xml.etree.ElementTree as ET
TOK = re.compile(r'%(?:\d+\$)?[sd]|%%')
def norm(t): return (tuple(sorted(x for x in t if '$' in x)), tuple(sorted(x for x in t if '$' not in x and x!='%%')))
def coll(p):
    o={}; r=ET.parse(p).getroot()
    for e in r:
        n=e.get('name')
        if n is None: continue
        o[n]=norm(TOK.findall("".join(e.itertext()))) if e.tag=='string' else norm([t for it in e for t in TOK.findall("".join(it.itertext()))])
    return o
bad=0
for src in glob.glob('**/src/main/res/values/strings.xml',recursive=True):
    if '/build/' in src: continue
    base=src[:src.index('/src/main')]; s=coll(src)
    for q in ('values-b+es+419','values-pt-rBR'):       # ← edit to the locale(s) you touched
        f=os.path.join(base,'src/main/res',q,'strings.xml')
        if not os.path.exists(f): continue
        t=coll(f)
        for n,v in s.items():
            if n in t and t[n]!=v: print(f"FORMAT MISMATCH {base} [{q}] {n}: src={v} loc={t[n]}"); bad+=1
print("FORMAT OK" if bad==0 else f"{bad} mismatch(es)")
PY

# 3. Resources actually compile (aapt2 validates plurals/format/escaping):
./gradlew :app:assembleDebug -q

# 4. Lint passes the MissingTranslation guard on the touched modules
#    (flavored modules use `:module:lint`, not `lintDebug`):
./gradlew :feature:settings:impl:lint :app:lint -q   # ← the modules you changed
```

Only commit once 1–4 are clean. Then hand off to the normal branch/PR flow (`bd-workflow`) — this skill produces resources, it doesn't open the PR.

## Boundaries

- This skill does NOT enable or touch Play Console's auto-translate (that path is unexportable and unscreenshottable — see `docs/screenshots.md` and the deep-link/i18n decision in `nubecita-nea3`).
- After a new locale ships, regenerating the **marketing screenshots** with real translated UI is a separate step (switch `MarketingScreenshotJourney` to per-locale capture — `docs/screenshots.md`).
- New string keys must be translated in *every* shipped locale or the `MissingTranslation` lint error fails CI — that's the intended gate; run this skill's backfill mode to satisfy it.
