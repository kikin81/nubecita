# SessionTelemetry buckets the session-clear reason by scanning the marker
# exception's stack for these class names (SDK failRefresh on invalid_grant vs
# AtOAuth.logout on user sign-out). R8 renaming them would collapse every
# release-build session_cleared event to reason=unknown. -keepnames only pins
# the names; the classes remain shrinkable/optimizable.
-keepnames class io.github.kikin81.atproto.oauth.DpopAuthProvider
-keepnames class io.github.kikin81.atproto.oauth.AtOAuth
