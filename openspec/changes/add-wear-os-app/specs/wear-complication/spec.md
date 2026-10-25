## ADDED Requirements

### Requirement: Push-only unread-DMs complication
The watch SHALL provide a complication data source built on `SuspendingComplicationDataSourceService`. It SHALL offer `SHORT_TEXT` and `MONOCHROMATIC_IMAGE` with the unread DM count, and SHALL implement `getPreviewData()`. It SHALL declare `UPDATE_PERIOD_SECONDS = 0` and be protected by `BIND_COMPLICATION_PROVIDER`. It SHALL request an update whenever a DM snapshot is ingested. Tapping it SHALL open the watch's conversation list.

#### Scenario: New DM snapshot
- **WHEN** a DM snapshot with a different unread count is ingested
- **THEN** the complication requests an update and shows the new count, without any periodic polling

#### Scenario: Watch-face editor
- **WHEN** the user adds the complication in the watch-face editor
- **THEN** preview data with a sample unread count is shown

### Requirement: Store listing mentions the complication
Once the complication ships, the Play listing description SHALL mention it in every shipped locale.

#### Scenario: Release with the complication
- **WHEN** the first release containing the complication is promoted to production
- **THEN** the Wear listing description in en, es-419 and pt-BR mentions the unread-messages complication
