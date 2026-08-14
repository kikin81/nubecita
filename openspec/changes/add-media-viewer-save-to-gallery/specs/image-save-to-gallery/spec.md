## ADDED Requirements

### Requirement: An image can be saved into the device's shared photo gallery

The system SHALL expose a capability that writes a remote image into the device's shared photo gallery, where the device's own gallery application can find it. Saved images SHALL be grouped under a `Nubecita` album inside the device's standard pictures location rather than scattered into the gallery root.

#### Scenario: Saving places the image in the Nubecita album

- **WHEN** an image is saved
- **THEN** the image appears in the device gallery under a `Nubecita` album
- **AND** the caller receives a reference identifying the saved image

#### Scenario: Saving the same image twice does not overwrite the first copy

- **WHEN** an image that has already been saved is saved again
- **THEN** both copies exist in the gallery
- **AND** neither copy is truncated or corrupted

### Requirement: Saved bytes are the original bytes, sourced from cache where possible

The system SHALL write the image's original encoded bytes unchanged. It MUST NOT decode and re-encode the image, so that the saved file is byte-identical to what the server served. When the image is already present in the application's image cache, the system MUST NOT issue a second network request to obtain it.

#### Scenario: A cached image is saved without a network request

- **GIVEN** the image has already been displayed and is present in the image cache
- **WHEN** the image is saved
- **THEN** no network request is issued for the image
- **AND** the saved file's bytes are identical to the cached bytes

#### Scenario: An image absent from the cache is fetched, then saved

- **GIVEN** the image is not present in the image cache
- **WHEN** the image is saved
- **THEN** the image is fetched
- **AND** the saved file's bytes are identical to the fetched bytes

### Requirement: A partially written image is never visible to the gallery

The system SHALL withhold the image from gallery indexing until its bytes are completely written, so an interrupted save can never surface as a truncated or blank entry in the user's gallery.

#### Scenario: A save interrupted mid-write leaves no gallery entry

- **WHEN** a save fails after writing some but not all of the image's bytes
- **THEN** no entry for that image is visible in the device gallery

### Requirement: Content type is determined from the image's own bytes

The system SHALL determine each saved image's content type by inspecting the image data itself. It MUST NOT assume a content type from the file extension, the URL, or a fixed default, because the content delivery network serves more than one image format.

#### Scenario: A PNG is saved as a PNG

- **WHEN** an image whose bytes are PNG-encoded is saved
- **THEN** the gallery entry's recorded content type identifies it as PNG

#### Scenario: A WebP is saved as a WebP

- **WHEN** an image whose bytes are WebP-encoded is saved
- **THEN** the gallery entry's recorded content type identifies it as WebP

#### Scenario: A JPEG is saved as a JPEG

- **WHEN** an image whose bytes are JPEG-encoded is saved
- **THEN** the gallery entry's recorded content type identifies it as JPEG

### Requirement: The poster's alt text is preserved as the saved image's description

When the source image carries alt text, the system SHALL record it as the saved image's description, so accessibility text written by the poster survives into the user's own gallery.

#### Scenario: Alt text is carried into the gallery entry

- **GIVEN** the image has alt text
- **WHEN** the image is saved
- **THEN** the gallery entry's description is that alt text

#### Scenario: Absent alt text saves successfully with no description

- **GIVEN** the image has no alt text
- **WHEN** the image is saved
- **THEN** the save succeeds
- **AND** the gallery entry has no description

### Requirement: The capability declares no Android permission

The application SHALL NOT declare any storage or media permission in order to provide this capability. Where a platform version cannot write to the shared gallery without such a permission, the capability SHALL report itself unsupported rather than requesting one.

This exists to protect the store listing. Declaring a storage permission would add the application's first *dangerous* permission — visible to every prospective user — in order to serve a measured 1.0% of active users (76 of 7,258, 90-day window). It could not be confirmed that capping the declaration by platform version removes it from the store listing's permission display, and that uncertainty runs entirely against the user.

#### Scenario: No storage permission is declared

- **WHEN** the application's merged manifest is inspected
- **THEN** it declares no storage or media permission

#### Scenario: An unsupported platform reports itself unsupported

- **GIVEN** a platform version that cannot write to the shared gallery without a permission
- **WHEN** the capability's support flag is read
- **THEN** it reports unsupported

#### Scenario: An unsupported platform never triggers a permission request

- **GIVEN** a platform version that reports itself unsupported
- **WHEN** a save is nevertheless attempted
- **THEN** the save fails
- **AND** no permission request is shown to the user

### Requirement: Failures are reported distinguishably

The system SHALL report a failed save as a typed failure that its caller can distinguish, so the user can be told whether the image could not be retrieved, could not be written, or is unsupported on their device.

#### Scenario: The image cannot be retrieved

- **WHEN** the image can neither be read from cache nor fetched
- **THEN** the caller receives a failure identifying retrieval as the cause

#### Scenario: The image cannot be written

- **WHEN** the gallery write fails
- **THEN** the caller receives a failure identifying storage as the cause

### Requirement: Saving does not block the caller's thread

Saving SHALL perform its network, decode-free copy, and storage work off the main thread, so a save of a large image cannot stall the interface.

#### Scenario: A save runs off the main thread

- **WHEN** a save is performed
- **THEN** the byte transfer and gallery write occur off the main thread
