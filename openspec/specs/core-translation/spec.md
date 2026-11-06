# core-translation Specification

## Purpose
The on-device translation boundary: the engine that translates text, the
identifier that guesses a post's language when it declares none, and the
repository that owns the language packs on disk.

It exists so the translation SDK lives behind one seam. No SDK type crosses
it, which is what makes the provider replaceable by rewriting a single module
rather than grepping the codebase — the same bargain `:core:billing` strikes
with RevenueCat.

## Requirements
### Requirement: SDK-agnostic translation boundary
`:core:translation` SHALL expose `TranslationEngine`, `LanguageIdentifier` and `TranslationModelRepository` as interfaces over `:data:models` types. No translation-SDK type (`Translator`, `TranslatorOptions`, `TranslateLanguage`, `LanguageIdentification`, …) SHALL appear in any public signature of the module or in any module that depends on it. Wire↔model translation lives in a dedicated mapper file and is unit-tested by mocking only SDK value types.

#### Scenario: No SDK type crosses the module edge
- **WHEN** any module outside `:core:translation` resolves a translation
- **THEN** it sees only `:data:models` types and the three interfaces, and the engine could be swapped by rewriting one module

### Requirement: Model downloads are Wi-Fi gated and consented per language
`TranslationModelRepository.request(languageTag)` SHALL require an unmetered connection and SHALL NOT start on a metered one. The repository SHALL expose a state per language distinguishing `NotDownloaded`, `WaitingForUnmeteredNetwork`, `Downloading`, and `Downloaded(sizeBytes)`. A requested download on a metered connection SHALL be recorded and SHALL start automatically when an unmetered connection becomes available.

#### Scenario: Download deferred off Wi-Fi
- **WHEN** a reader accepts a language while on cellular
- **THEN** the language enters `WaitingForUnmeteredNetwork`, nothing is downloaded, and the download begins when Wi-Fi is available

#### Scenario: Download reports real size
- **WHEN** a language finishes downloading
- **THEN** `Downloaded(sizeBytes)` reports the measured on-disk size of that language's model directory, not a constant

### Requirement: Model storage is enumerable and reclaimable
`TranslationModelRepository` SHALL expose the set of downloaded languages with per-language on-disk size and a total, and SHALL support deleting any downloaded language. Deleting a language SHALL remove it from the auto-translate list.

#### Scenario: Reader reclaims storage
- **WHEN** the reader deletes a downloaded language
- **THEN** the model files are removed, the reported total drops by that language's size, and the language is no longer auto-translated

### Requirement: Translators are released when the app backgrounds
Engine instances SHALL be closed when the application stops. Holding five loaded translators was measured at ~400 MB RSS, so no translator may be retained across a background transition.

#### Scenario: Translators closed on background
- **WHEN** the app moves to the background
- **THEN** every open translator is closed and reopened lazily on next use

### Requirement: Identification is advisory
`LanguageIdentifier` SHALL return an undetermined result rather than guessing when confidence is low, and callers SHALL treat its output as a hint. Identification SHALL NOT run at all while the master translation toggle is off.

#### Scenario: Identification is not run when the feature is off
- **WHEN** the master translation toggle is off
- **THEN** no identification model is loaded and no inference is performed
