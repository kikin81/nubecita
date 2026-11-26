# Spec Delta

## MODIFIED Requirements

### Requirement: `:core:auth` provides an `AtOAuth` Hilt binding

`:core:auth` SHALL provide a `@Singleton`-scoped Hilt binding for `AtOAuth`, constructed from `BuildConfig.OAUTH_CLIENT_METADATA_URL`, `OAuthSessionStore`, a Ktor `HttpClient`, and the token-mediation transport. The binding SHALL NOT hard-code the metadata URL or the token-mediation backend URL. The `AtOAuth` type SHALL NOT appear in `:app` `@Inject` parameters (consumers inject `AuthRepository`).

#### Scenario: Consumer injects AtOAuth through the DI graph

- **WHEN** a Hilt-managed class in `:core:auth` declares a constructor parameter of type `AtOAuth`
- **THEN** Hilt SHALL resolve it at graph construction time using the BuildConfig URL and the existing `OAuthSessionStore` binding

#### Scenario: URL is build-variant configurable

- **WHEN** `:app/build.gradle.kts` declares `buildConfigField("String", "OAUTH_CLIENT_METADATA_URL", ...)` and the module is rebuilt
- **THEN** the new URL SHALL be picked up by the provided `AtOAuth` without any source-file edit in `:core:auth`

#### Scenario: Backend URL is build-variant configurable

- **WHEN** a build sets the token-mediation backend URL through a Gradle property
- **THEN** the provided `AtOAuth` SHALL send mediated requests to that URL without any source-file edit in `:core:auth`

#### Scenario: `:app` does not directly inject AtOAuth

- **WHEN** `:app` source files are inspected
- **THEN** no `@Inject` constructor parameter, `@Provides` return type, or field in `:app` SHALL be typed as `AtOAuth`
