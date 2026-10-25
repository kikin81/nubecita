## ADDED Requirements

### Requirement: DM poll feeds the watch snapshot when a watch is paired
While a node advertising `nubecita_wear` is paired, each DM poll run that passes its existing gates SHALL fetch messages only for conversations `getLog` reports as changed, and SHALL publish the DM snapshot defined by `wear-messages`. With no watch paired, the poll's network calls SHALL be unchanged.

#### Scenario: No watch paired
- **WHEN** the DM poll runs and no node advertises `nubecita_wear`
- **THEN** it makes only its existing `listConvos` and `getLog` calls
