# Phase 2 Spike Report: Headless Composer Extraction (`:core:composer-engine`)

See the full architectural blueprint and migration specification in:
* [docs/architecture/phase2-headless-composer-spike.md](file:///Users/francisco/code/nubecita/docs/architecture/phase2-headless-composer-spike.md)

### Summary of Blueprint:
* **Target Module**: `:core:composer-engine`
* **Purpose**: Enable inline comment composition directly inside the video comments sheet without launching the full-screen `ComposerRoute`.
* **Core Abstractions**:
  * `HeadlessComposerSession`: Manages `TextFieldState`, debounced mention/tag typeahead queries, grapheme length calculation (300-grapheme ATProtocol limit), and direct reply creation via `PostingRepository`.
  * `InlineCommentBar`: Expands the docked comment pill into an in-place `BasicTextField` with live grapheme counter ring and scoped IME insets.
* **Modularity**: Keeps `:core:post-interactions-ui` completely decoupled from `:feature:composer:impl`, avoiding illegal feature-to-feature dependencies.
