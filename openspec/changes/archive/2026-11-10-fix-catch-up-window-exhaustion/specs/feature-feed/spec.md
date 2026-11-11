## MODIFIED Requirements

### Requirement: Reaching the catch-up budget offers jump-to-newest

When the configured budget is reached with the range still unexhausted, the seam SHALL become a passive gap marker offering **jump to newest**, which performs the destructive refresh rather than another fill.

Jump-to-newest SHALL be optional: reaching the budget SHALL NOT stop later catch-ups from bringing new posts above the gap, and the reader SHALL keep their place without tapping it.

#### Scenario: The budget offers jump-to-newest rather than another fill

- **WHEN** the catch-up budget is reached with the range unexhausted
- **THEN** the seam renders a jump-to-newest affordance

#### Scenario: Jumping to newest starts a fresh timeline

- **WHEN** the reader taps jump-to-newest
- **THEN** the destructive refresh runs and the reader lands at the top of a fresh timeline

#### Scenario: New posts keep arriving after the budget is reached

- **WHEN** a catch-up reached the budget, the reader did not tap jump-to-newest, and more posts are published
- **THEN** reopening the feed, tapping the new-posts pill or re-tapping the Feed tab brings those posts in above the gap, and the reader's saved place is unchanged

#### Scenario: A passive gap does not fill on scroll

- **WHEN** the reader scrolls a passive gap marker into view
- **THEN** no fetch is started for it, and it keeps rendering the jump-to-newest affordance
