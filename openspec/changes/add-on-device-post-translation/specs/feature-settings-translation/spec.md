# feature-settings-translation

## ADDED Requirements

### Requirement: Translation settings sub-page
Settings SHALL offer a Translation sub-page built from `NubecitaListGroup`, containing a master toggle, an auto-translate language list, a never-translate language list, and a downloaded-languages section. The master toggle SHALL default to **off**.

#### Scenario: Default state
- **WHEN** a reader opens Translation settings on a fresh install
- **THEN** the master toggle is off, both language lists are empty, and no language is downloaded

### Requirement: The never-translate list is the reader's language set
The never-translate list SHALL serve as the reader's declared "languages I read" and SHALL be unioned with the device `LocaleListCompat` to form the set of languages that are never offered for translation. There SHALL NOT be a separate "languages I read" list.

#### Scenario: A bilingual reader silences both their languages
- **WHEN** a reader adds Spanish to the never-translate list on an English-locale device
- **THEN** neither English nor Spanish posts are translated or offered a chip

#### Scenario: Removing a device language is possible
- **WHEN** the reader's device locale includes a language they do not actually read
- **THEN** the never-translate list is editable independently and the resolved set can be corrected

### Requirement: Storage is visible and reclaimable per language
The downloaded-languages section SHALL list each downloaded language with its measured on-disk size and a delete action, and SHALL show the combined total.

#### Scenario: The real cost is shown
- **WHEN** Japanese, German and Portuguese are downloaded
- **THEN** each is listed with its own measured size and a total is shown, reflecting that ML Kit stores both directions of each pair

#### Scenario: Deleting frees the space and stops the automation
- **WHEN** the reader deletes a language
- **THEN** its files are removed, the total drops, and the language is removed from the auto-translate list

### Requirement: Language selection reuses the existing picker
The language lists SHALL use the BCP-47 picker already used by the composer — localized display names, search by tag or name, selection pinned to the top on re-open — rather than a second, divergent picker.

#### Scenario: Searching by localized name works
- **WHEN** the reader searches in their own language for a language name
- **THEN** the matching tag is offered

### Requirement: New strings ship localized
Every new user-facing string SHALL ship with `es-419` and `pt-BR` translations in the same change.

#### Scenario: No untranslated string reaches a release
- **WHEN** the touched modules' lint runs
- **THEN** no `MissingTranslation` is reported
