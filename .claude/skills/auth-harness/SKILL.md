---
name: auth-harness
description: Use when verifying nubecita's session-loss telemetry end to end on a real device — phrases like "run the auth harness", "stage a fake logout", "simulate an invalid token", "did the logout telemetry actually fire", "test the session_cleared path", "verify the clear-telemetry recovery". Drives the opt-in AuthTestHarness (nubecita-zh7p) against a real PDS and a real account, then proves what reached logcat, GA4 and Crashlytics.
---

Project-local skill for `AuthTestHarness` — the opt-in harness that stages a real
token-refresh failure so the session-loss telemetry chain can be verified instead
of assumed.

**Announce at start:** "I'm using the auth-harness skill to stage a session loss and verify the telemetry."

## When to use

- "Run the auth harness" / "stage a logout" / "simulate an invalid token"
- "Did the logout telemetry actually fire?" / "is `session_cleared` reaching GA4?"
- After any change to `clear()`, `SessionTelemetry`, `SessionClearPendingStore`,
  or `SessionClearTelemetryRecovery` — those paths have no device coverage
  other than this.
- After a user reports a spurious logout that left no telemetry, to establish
  whether the pipeline works at all before suspecting the code.

**Do NOT use** for anything a unit test can answer. The harness costs a release
build, an uninstall, a manual sign-in, and a Play reinstall afterwards. It
answers exactly one question: *does the real chain report?*

## The cost, up front

Running this signs the user out of their real account and leaves a
non-Play-signed build on the device. Say so before starting. Finishing costs:

- one manual sign-in by the user (never enter their credentials yourself)
- an uninstall + Play reinstall afterwards, which costs a *second* sign-in

Budget two sign-ins and ask before you begin.

## What it does

Four scenarios. It does not fake a failure — it rewrites the stored tokens to
`"nubecita-harness-deliberately-invalid-token"` and leaves the DPoP keypair,
PDS URL, token endpoint and nonces intact, so the refresh that follows is a
well-formed request the real server rejects for exactly one reason. The only
lie told anywhere is the token string.

| Scenario | Stages | Expect |
|---|---|---|
| `BadRefreshToken` | both tokens poisoned | real `invalid_grant` → `session_cleared` → signed out |
| `BadAccessToken` | access token only, opaque | SDK's `invalid_token` branch; **one request may legitimately fail** — see below |
| `StashAccessToken` | saves the live JWT, logs its `exp`; changes nothing | the wait remaining. Does **not** halt |
| `ExpiredAccessToken` | writes the stashed token back once `exp` has passed | exactly one `token_refresh outcome=ok`, still signed in |
| `DiedInFlightMarker` | an in-flight refresh marker from a fabricated dead process | `TokenRefreshDiedInFlightException` + `outcome=process_died` next launch |
| `ProcessDeathMidClear` | pending marker written, session wiped, nothing reported | `session_clear_telemetry_lost` on next launch |
| `DirectClear` | plain `sessionStore.clear()` | `session_cleared`, `reason=unknown`, no refresh breadcrumbs |

**Always run `BadAccessToken` first.** It is the control: it exercises the
refresh telemetry path *without* destroying the session. If it reports nothing,
the pipeline is broken and nothing the others tell you means anything. Skipping
it is how you spend an afternoon reading `clear()` looking for a bug that a DNS
blackhole was causing.

Every scenario except `StashAccessToken` ends in `Runtime.halt(0)` — the paths
under test only complete on the NEXT launch.

### `BadAccessToken` is not real expiry — don't read its failure as a bug

The poison string is opaque, so `DpopAuthProvider.accessTokenExpiry()` returns
null and `isAccessTokenExpired()` is permanently false. The SDK only refreshes
after a 401, and it picks the path from evidence in the token:

- With a stale DPoP nonce, the first 401 carries only `use_dpop_nonce`, so
  `onUnauthorized` stores the new nonce and returns.
- The retry then gets `invalid_token`, which *does* trigger the refresh — but
  `XrpcClient` retries only once, so **that request can fail.**

A 4xx or an `api_error` out of this scenario is a harness artifact of an opaque
token, not a regression. Keep the scenario — it is the only one that covers the
`invalid_token` branch — but never read its failures as a finding.

Shifting the device clock is not an alternative: `calibrateClockFromHeader`
resets the offset from the server's `Date` header before the expiry check, so
the SDK's view of "now" ignores the device clock.

### Real expiry is a two-step scenario

`ExpiredAccessToken` reproduces what an idling user actually hits: a real,
server-signed JWT whose `exp` has passed, sitting next to a **valid** refresh
token and the same DPoP key. On the first 401 of the cold-start burst — even a
`use_dpop_nonce` one — `isAccessTokenExpired()` is true, so the SDK refreshes
single-flight before its one retry.

1. **Stash**, with the app signed in and running:

```bash
adb -s <serial> shell am broadcast -a net.kikin.nubecita.AUTH_HARNESS --es scenario StashAccessToken -p net.kikin.nubecita
```

   This changes nothing and does not halt. Read the logged `exp` and the wait.
   **If it refuses because the token is not a JWT with `exp`, stop** — the
   expiry path does not exist against that PDS and the scenario is unusable.
   That refusal is the assumption check, deliberately made at runtime rather
   than assumed in advance.

2. **Wait out the `exp`**, then apply:

```bash
adb -s <serial> shell am broadcast -a net.kikin.nubecita.AUTH_HARNESS --es scenario ExpiredAccessToken -p net.kikin.nubecita
```

   It refuses while the token is still valid and says how long remains. The
   stash is deleted once applied, so a second apply needs a new stash.

3. **Cold start straight into a profile** — the burst is the point, so don't
   open the app normally first:

```bash
adb -s <serial> shell am start -W -a android.intent.action.VIEW -d https://bsky.app/profile/bsky.app -p net.kikin.nubecita
```

Pass if **all** of:

- exactly one `token_refresh outcome=ok`, after its `nonce_challenge`
- every 401 is `use_dpop_nonce` or an expired-token 401, and each is retried once
- no other 4xx/5xx in `adb logcat -s AtProtoHttp` (debug builds only)
- no `api_error`
- the profile renders

### `DiedInFlightMarker` lands in a fleet count — filter it out, and log your runs

This stages the proven cause of the remaining MIUI logouts (nubecita-25a5)
without spending a real token: it writes only the in-flight marker, shaped as
the real ones were — `foreground = false`, a previous process's id.

Its output lands in **the same** `TokenRefreshDiedInFlightException` Crashlytics
issue and the same GA4 `token_refresh outcome=process_died` count used to
confirm the #1184 fix. One field separates it: the marker is staged under
caller **`harness`**, and `reportRefreshDiedInFlight` carries the marker's
caller to both sinks. So exclude with:

- GA4: `refresh_caller != harness` on `token_refresh`.
- Crashlytics: the `died_refresh_caller` custom key on the non-fatal reads
  `harness`.

Two things that do **not** work, so don't reach for them:

- **The fabricated process id** (prefixed `harness-`) is used only to decide
  "not my own refresh" and is then discarded — it reaches neither sink. It is
  visible in the stored marker and in logcat, and nowhere else.
- **A `harness=true` Crashlytics custom key** would be set in the staging
  process, while the report happens in the *next* process. Custom keys are
  per-process, so it would be silently absent for exactly this scenario.

Keep the run log anyway. The caller filter depends on someone applying it; a row
here is what lets a reader who didn't notice the field reconcile a count after
the fact. The tester is on a Xiaomi 23028RNCAG, so a run from an emulator or a
dev Pixel is separable by device model too.

| Date | Device | Scenario | Events staged |
|---|---|---|---|
| 2026-11-12 17:02 PST | emulator-5556 (sdk_gphone64_arm64) | DiedInFlightMarker | 1 × `process_died` / `TokenRefreshDiedInFlightException`, caller `harness` (GA4 delivery disabled on this debug build — see below) |
| _(append a row per run)_ | | | |

### Build type: you cannot observe HTTP and GA4 in the same run

Verified on device 2026-11-12. The two observation surfaces are mutually
exclusive, so pick the build type from what the scenario needs to prove:

| Build | `AtProtoHttp` logs | GA4 events |
|---|---|---|
| `productionDebug` | yes | **no** — `app/src/debug/AndroidManifest.xml` sets `firebase_analytics_collection_enabled=false` (enforced by `DebugAnalyticsCollectionTest`) |
| `productionRelease -PdebugSignedRelease=true` | **no** — `install(Logging)` is inside `if (BuildConfig.DEBUG)` in `HttpClientModule.kt` | yes |

`ExpiredAccessToken` needs `AtProtoHttp`, so build it debug. Running it on a
release build does not merely lose criterion 1: an empty `AtProtoHttp` log reads
as "no extra token requests" and "no other 4xx/5xx", so two criteria pass
vacuously. That is a false pass, not a void run.

Crashlytics is unaffected by either gate. And the caller of a reported dead
refresh is checkable without GA4 at all — it persists:

```bash
adb -s <device> shell run-as net.kikin.nubecita \
  cat /data/data/net.kikin.nubecita/files/datastore/auth_telemetry.preferences_pb | strings
```

`last_died_refresh_caller` is written as the final statement of
`reportRefreshDiedInFlight`, after `recordException` and the analytics call, so
seeing `harness` there proves the whole report path ran.

## Pre-flight

Each of these has produced a confident, wrong conclusion in this project. Run
all four.

### 1. DNS — before anything else

A device reporting zero Crashlytics non-fatals while GA4 works is almost always
a DNS block, not a telemetry bug.

```bash
adb shell ping -c 1 crashlyticsreports-pa.googleapis.com
```

`unknown host` means blocked. Both `crashlyticsreports-pa.googleapis.com` and
`firebaselogging-pa.googleapis.com` sit on common tracker blocklists; this
network has NXDOMAIN'd them before. The symptom inside logcat is
`Couldn't open connection, returning with 500` about 20 ms after
`Making request to` — a local connection failure, not a server rejection.
Querying `8.8.8.8` directly is **not** a control (transparent interception
answers it); use DNS-over-HTTPS:

```bash
curl -s -H 'accept: application/dns-json' 'https://dns.google/resolve?name=crashlyticsreports-pa.googleapis.com&type=A'
```

Resolving over DoH but failing locally = being filtered. Stop and have the user
allowlist both hosts. Do not interpret a silent Crashlytics until this is clean.

### 2. One device, and the right one

```bash
adb devices -l
```

Disconnect any TCP/Wi-Fi-paired extras. Pass `-s <serial>` on every subsequent
command — a bare `adb` with two devices attached is a coin flip, and
`gradle install*` targets ALL of them.

### 3. Logcat buffer

Verbose Firebase Analytics is extremely chatty and will rotate the event you
came for out of the buffer before you read it.

```bash
adb -s <serial> logcat -G 64M
adb -s <serial> shell setprop log.tag.FA VERBOSE
adb -s <serial> shell setprop log.tag.FA-SVC VERBOSE
```

GA4 DebugView is console-only with no API, so verbose logcat is the only way to
read event params from here.

### 4. The build on the device is the build you made

A failed `adb install` (device drops mid-transfer) leaves the PREVIOUS build
installed, and the next broadcast then exercises stale code and produces a
result that looks entirely real.

```bash
adb -s <serial> shell dumpsys package net.kikin.nubecita | grep lastUpdateTime
stat -f "%Sm" -t "%Y-%m-%d %H:%M:%S" app/build/outputs/apk/production/release/app-production-release.apk
```

`lastUpdateTime` must be at or after the APK's mtime. Delete the output APK
before rebuilding, so a failed Gradle build cannot leave a stale artifact for a
successful install to pick up.

## Build and install

```bash
rm -f app/build/outputs/apk/production/release/app-production-release.apk
```

```bash
./gradlew :app:assembleProductionRelease -PdebugSignedRelease=true -PauthTestHarness=true
```

`-PauthTestHarness=true` attaches the `authHarness` source sets in `:core:auth`
and `:app`. Without it the harness classes do not exist in the build at all —
there is nothing to strip and nothing to verify.

The same flag attaches the harness's own unit tests, which cover the expiry
arithmetic `ExpiredAccessToken` depends on. CI runs them in the `build` job's
harness step, but run them yourself before burning a sign-in cycle on a device:

```bash
./gradlew -PauthTestHarness=true :core:auth:testProductionDebugUnitTest
```

**The build refuses to run on a machine with release signing material.** If
`keystore.properties` exists or the `KEYSTORE_*` env vars are set, the flag
fails at configuration time with a message saying so. That is deliberate: it
makes shipping the harness structurally impossible rather than merely unlikely.
If you hit it, you are on the wrong machine — do not work around it.

Installing over a Play build fails on signature mismatch. Uninstalling is
destructive (it drops the session and all app data) — **ask the user first**:

```bash
adb -s <serial> uninstall net.kikin.nubecita
```

```bash
adb -s <serial> install app/build/outputs/apk/production/release/app-production-release.apk
```

Then have the **user** sign in. Confirm they are in before staging anything.

## Run a scenario

```bash
adb -s <serial> shell am broadcast -a net.kikin.nubecita.AUTH_HARNESS --es scenario BadAccessToken -p net.kikin.nubecita
```

The app must be running for the runtime-registered receiver to exist. An
unknown scenario name logs an error and does nothing rather than defaulting —
if nothing happens, check the spelling against the table above.

Then **relaunch the app** — every scenario's payoff is on the next launch,
except `StashAccessToken`, which deliberately leaves the process alive.

`ExpiredAccessToken` and `DiedInFlightMarker` have their own recipes above; use
those rather than this generic one.

## Verify — three observation points, all three required

Any one of them alone has produced a wrong answer before.

### logcat (immediate)

```bash
adb -s <serial> logcat -d | grep -E "AuthTestHarness|token_refresh|session_cleared|session_clear_telemetry_lost|TokenRefresh"
```

This is the only place you see the *params*; GA4 shows several of them as
`(not set)`, and the Crashlytics custom keys are the durable copy.

### GA4 (seconds to minutes)

Realtime via `analytics-mcp` (`run_realtime_report`), events `session_cleared`,
`session_clear_telemetry_lost`, `token_refresh`. GA4 custom dimensions are
text-only — `days_since_login` is sent bucketed as a string for exactly this
reason, so do not expect a number.

Check `first_open` too. A reinstall, downgrade, or second Android profile looks
exactly like a logout from the outside and emits ZERO auth telemetry — if
`first_open` fired, you may be chasing a reinstall rather than a logout.

### Crashlytics (~5 s once DNS is clean)

Via the `firebase` MCP: `crashlytics_list_events` / `crashlytics_get_report` on
app id `1:231886524292:android:39f7396cfb287cb01297d1`, looking for the
`SessionClearedException` non-fatal and its nine custom keys. Compare
`eventTime` to `receivedTime`: ~5 s is healthy. Tens of minutes means the
reports were queued behind a network block — go back to pre-flight 1.

Pass a `pageSize` large enough to see past the first page, and check for a
`nextPageToken` before concluding a version or event is absent.

Crashlytics persists `recordException` asynchronously. `DirectClear` waits 3 s
before halting for this reason; the other three report nothing before the halt
by design, so a missing non-fatal from them is expected, not a failure.

## Reading the result honestly

- A scenario "completing" is not evidence it measured anything. The broadcast
  succeeding and the process halting are consistent with a stale APK.
- Absence of a Crashlytics report is only evidence once DNS is clean AND the
  positive control (`BadAccessToken`, or a `DirectClear` non-fatal that did
  arrive) proves the path works from this device.
- Report what each of the three observation points actually showed, including
  the ones that showed nothing.

## Cleanup

The device is left on a non-Play-signed build that Play cannot update. To
restore it:

```bash
adb -s <serial> uninstall net.kikin.nubecita
```

then reinstall from Play — which costs the second sign-in. Tell the user that
is where things stand rather than leaving them to discover it.

## Why the harness lives in the repo and not in this skill

The session is Tink-encrypted (AES256-GCM, master key in the Android Keystore).
From outside the app you can delete the session file — a plain logout, no
refresh attempt — but you cannot *write* a valid encrypted session holding a
poisoned refresh token. Producing a real `invalid_grant` from the real PDS
requires code running in-process with the Keystore handle, so the harness has
to compile into the app.

Kept as skill assets instead, it would be the same ~320 lines in the repo with
nothing compiling them: `AuthTestHarness` injects four `internal` types from
`:core:auth`, and a rename would break it silently — discovered during the next
logout mystery, under time pressure.

Production exposure is two independent gates: the classes are absent from any
build that does not pass the flag, and the flag itself is refused on any machine
that could sign a distributable artifact.

Verifying the first gate: in a **debug** APK, count `AuthTestHarness` dex
strings — 0 without the flag, 59 with. In a **release** APK that count is not a
usable discriminator, because R8 renames the classes; the flagged release build
yields 1, not 59. Count a string literal R8 cannot rename instead — the poison
token `nubecita-harness-deliberately-invalid-token` or the broadcast action
`net.kikin.nubecita.AUTH_HARNESS`. Both read 0 unflagged and 1 flagged in
`productionRelease`. Checking only the class name in a release build would
report a near-zero either way and look like a pass.

Don't grep for the bare word `harness` either. Since the `TokenRefreshCaller`
enum gained a `Harness("harness")` entry, the strings `harness` and `Harness`
are in `:core:analytics` main source and therefore present in a clean release
APK — along with the framework's own `isRunningInTestHarness`. Verified counts
in `productionRelease` (flagged / unflagged):

| Marker | Flagged | Unflagged |
|---|---|---|
| `nubecita-harness-deliberately-invalid-token` | 1 | 0 |
| `net.kikin.nubecita.AUTH_HARNESS` | 1 | 0 |
| `auth_harness` | 1 | 0 |
| `stashed_access_token` | 2 | 0 |
| `harness-` (with the hyphen) | 2 | 0 |
| `harness` (bare) | — | **present: not a marker** |
