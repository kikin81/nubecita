## ADDED Requirements

### Requirement: Widget refresh also refreshes the selected watch feeds
While a node advertising `nubecita_wear` is paired, the periodic widget refresh SHALL also refresh each feed selected in **Settings → Watch** into `:core:feed-cache`, then publish those feeds' snapshots to the watch. It SHALL NOT add a new periodic work request or a new `RefreshCaller` value.

#### Scenario: Watch paired with a custom feed selected
- **WHEN** the widget refresh runs and the user selected Following and one custom feed for the watch
- **THEN** both feeds are refreshed into the cache, and both are published as non-urgent snapshots, under the `RefreshCaller.Widget` tag

#### Scenario: No watch paired
- **WHEN** the widget refresh runs and no node advertises `nubecita_wear`
- **THEN** it refreshes only the widgets' feeds, as before
