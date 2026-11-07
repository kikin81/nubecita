## 1. Rename (nubecita-ge3s.1)

- [x] 1.1 Rename `VideoComments*` to `PostComments*` across modules, with no behaviour change

## 2. Navigation (nubecita-ge3s.2)

- [x] 2.1 Add `onNavigate` to `rememberPostInteractions`
- [x] 2.2 Add `Navigator.closeAndOpenInMainShell` over `DeepLinkRouter`
- [x] 2.3 Move `VideoPlayerScreen`'s `Profile` and `PostDetailRoute` pushes onto it

## 3. Overlay (nubecita-ge3s.3)

- [x] 3.1 Keep the post in the ViewModel and delegate to `PostInteractionHandler`
- [x] 3.2 Build the overlay and gradient inside the chrome block
- [x] 3.3 Wire the author and caption taps, with the opened-from-post rule

## 4. Comments (nubecita-ge3s.4)

- [x] 4.1 Host the comments sheet, with Back order and the auto-fade pause
- [x] 4.2 Run the device pass
