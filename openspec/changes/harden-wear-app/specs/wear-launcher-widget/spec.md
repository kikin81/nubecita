# Spec Delta

## Purpose

The Nubecita launcher surface in the watch's widget and tile carousel: what it shows, where it takes the user, and how it renders in each container.

## ADDED Requirements

### Requirement: Background matches the container
In a widget container the surface SHALL fill the host's container with a themed background, so that no black area shows between the container edge and the content. In the full-screen tile form the background SHALL be black.

#### Scenario: Squircle widget container
- **WHEN** the large widget is shown in a squircle container
- **THEN** the container is filled edge to edge and the tiles do not float on black

#### Scenario: Tile form
- **WHEN** the surface is shown as a full-screen tile
- **THEN** its background is black

### Requirement: Small container
The surface SHALL also be available as a small widget: one row that shows the unread-message label and opens Messages when tapped anywhere.

#### Scenario: Adding the small widget
- **WHEN** the user adds the small Nubecita widget and taps it
- **THEN** the watch app opens on the conversation list

#### Scenario: Small widget, signed out
- **WHEN** the phone app is signed out
- **THEN** the small widget shows the sign-in prompt

### Requirement: Rendering does not wait on the phone
The surface SHALL render from state stored on the watch. A slow or unavailable Play services call SHALL NOT leave it blank.

#### Scenario: Play services stalls
- **WHEN** the surface is rendered while Play services does not answer
- **THEN** it renders with the last known signed-in state and unread count

### Requirement: Every target is at least 48dp and has one description
Each tappable area of the surface SHALL be at least 48dp in both dimensions in every container, and SHALL be announced to accessibility services as a single element.

#### Scenario: Smallest large container
- **WHEN** the large widget is shown in the smallest squircle container
- **THEN** Home and New post are each at least 48dp tall

### Requirement: Picker previews are generated from the surface
The picker preview image for each declared container SHALL be rendered from the surface's own layout at that container's rectangular size, and a test SHALL fail when the shipped image is not byte-identical to the committed reference image of that preview. The test SHALL compare the two committed files and SHALL NOT render anything, so its result does not depend on the machine it runs on.

#### Scenario: Layout changes without a new preview
- **WHEN** the large layout's reference image is regenerated and the shipped preview image is not updated
- **THEN** a test fails

#### Scenario: Same result on every machine
- **WHEN** the test runs on a developer machine and on CI with the same committed files
- **THEN** it gives the same result on both
