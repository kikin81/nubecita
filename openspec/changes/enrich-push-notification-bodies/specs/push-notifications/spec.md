## ADDED Requirements

### Requirement: Reply, mention, and quote notifications carry the notifying post's text as the body

For `reply`, `mention`, and `quote` push notifications, the gateway SHALL set the notification body to the text of the notifying post (the reply / mention / quote post itself), sourced from the Jetstream firehose event it already consumes — without any additional network request. The body SHALL be truncated to a bounded length on a UTF-8 rune boundary. The text SHALL also be carried in the FCM `data` map as `bodyText` so the client can render it. For `like`, `repost`, and `follow` (and their via-repost variants), the body is unchanged — those events have no post text.

#### Scenario: Quote notification shows the quoting post's text

- **WHEN** a registered user is quoted and the gateway emits the `quote` notification
- **THEN** the notification title is the actor + reason line (e.g. "Alice quoted your post") and the body is the quoting post's text, with `data.bodyText` set to the same (truncated) text

#### Scenario: Reply and mention notifications show the post text

- **WHEN** the gateway emits a `reply` or `mention` notification for a registered user
- **THEN** the body is the replying / mentioning post's text and `data.bodyText` is set

#### Scenario: Engagement notifications without post text are unchanged

- **WHEN** the gateway emits a `like`, `repost`, `follow`, `like-via-repost`, or `repost-via-repost` notification
- **THEN** the body is the existing templated line (e.g. "Bob liked your post") and `bodyText` is absent

#### Scenario: Long post text is truncated

- **WHEN** a notifying post's text exceeds the bounded length
- **THEN** the body and `bodyText` are truncated (with an ellipsis) on a rune boundary and stay well within the push payload size limit

#### Scenario: Client renders the post text as expandable big-text

- **WHEN** the app receives a payload whose `data.bodyText` is present
- **THEN** `PushNotificationBuilder` renders it as the notification's content text wrapped in a big-text style so a long post expands in the shade

#### Scenario: Payload without bodyText still renders (backward compatible)

- **WHEN** the app receives a payload with no `bodyText` field (a like/repost/follow, or a gateway not yet redeployed)
- **THEN** the notification renders exactly as before (title only), with no crash or empty body
