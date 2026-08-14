## ADDED Requirements

### Requirement: The viewer offers a save action for the image currently on screen

The fullscreen viewer SHALL present a save action in its chrome. The action SHALL apply to the image on the current page — never to the whole post's image set — so a multi-image post needs no disambiguating picker.

#### Scenario: Saving acts on the current page

- **GIVEN** a post with several images is open in the viewer
- **WHEN** the user pages to the third image and activates save
- **THEN** the third image is the one saved

#### Scenario: The save action follows the chrome

- **GIVEN** no save is in flight
- **WHEN** the viewer's chrome is hidden by the auto-fade timer
- **THEN** the save action is hidden with it
- **AND** it reappears when the chrome is restored

### Requirement: The viewer reports the outcome of a save

The viewer SHALL tell the user whether a save succeeded or failed. Failure messages SHALL distinguish the reason reported by the save capability rather than showing one generic message for every cause.

#### Scenario: A successful save is confirmed

- **WHEN** a save succeeds
- **THEN** the viewer shows a confirmation

#### Scenario: A failed save is reported with its cause

- **WHEN** a save fails because the image could not be retrieved
- **THEN** the viewer shows a message describing that cause
- **AND** the message differs from the one shown when the gallery write fails

### Requirement: The save action is absent where the device cannot save

Where the save capability reports itself unsupported, the viewer SHALL omit the save action entirely rather than presenting it disabled or failing on activation. A control that cannot ever work is worse than no control.

#### Scenario: An unsupported device shows no save action

- **GIVEN** the save capability reports itself unsupported
- **WHEN** the viewer renders its chrome
- **THEN** no save action is present

### Requirement: A save in progress is visible and cannot be re-triggered

While a save is in flight the viewer SHALL indicate progress and SHALL ignore further activations, so an impatient double-tap cannot write the same image twice.

#### Scenario: A second activation during a save is ignored

- **GIVEN** a save is in flight
- **WHEN** the user activates save again
- **THEN** no second save is started

#### Scenario: Progress is visible while saving

- **WHEN** a save is in flight
- **THEN** the viewer indicates that work is in progress

#### Scenario: The chrome does not auto-fade out from under a save in progress

- **GIVEN** a save is in flight
- **AND** the progress indication is presented within the chrome
- **WHEN** the chrome auto-fade timer would otherwise elapse
- **THEN** the chrome remains visible
- **AND** the auto-fade resumes once the save completes

#### Scenario: The indicator clears on completion

- **WHEN** an in-flight save finishes, whether it succeeded or failed
- **THEN** the progress indication is cleared
- **AND** the save action becomes activatable again

### Requirement: The presenter emits save outcomes without referencing platform resources

Consistent with the viewer's existing error handling, the presenter SHALL emit the save outcome as a typed value and leave the choice of user-facing wording to the screen. The presenter MUST NOT resolve strings itself.

#### Scenario: The outcome effect carries a type, not a message

- **WHEN** the presenter emits a save outcome
- **THEN** the emitted value identifies the outcome by type
- **AND** it carries no user-facing string
