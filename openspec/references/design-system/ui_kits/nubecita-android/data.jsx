// Fake data for Nubecita UI kit demos
const NB_POSTS = [
  {
    id: 'p1',
    name: 'Alice Chen',
    handle: 'alice.nubecita.app',
    hue: 210,
    when: '3h',
    body: 'The thing about building a Bluesky client in 2026 is you realize how much of the web we gave up trying to fix. Small clients, good defaults, readable text. ☁︎',
    replies: 12, reposts: 4, likes: 86, liked: true, reposted: false,
  },
  {
    id: 'p2',
    name: 'Marco Díaz',
    handle: 'marco.bsky.social',
    hue: 35,
    when: '4h',
    body: 'Mastodon has an onboarding problem. Bluesky has a discoverability problem. Both are fixable. Here\'s what we changed in Nubecita this week →',
    image: 'linear-gradient(135deg, #A6C8FF, #6250B0)',
    replies: 28, reposts: 14, likes: 210, liked: false, reposted: false,
  },
  {
    id: 'p3',
    name: 'Sana Okafor',
    handle: 'sana.nubecita.app',
    hue: 320,
    when: '5h',
    repostedBy: 'Alice Chen',
    body: 'Tiny victory: got adaptive layouts working on the Fold 6. Feed + thread + composer all open at once. No more losing context switching between them.',
    replies: 3, reposts: 18, likes: 124, liked: false, reposted: false,
  },
  {
    id: 'p4',
    name: 'Devon Park',
    handle: 'devon.dev',
    hue: 150,
    when: '7h',
    body: 'Shoutout to whoever decided pill buttons in M3 Expressive. The press-squish haptic feels right every single time.',
    replies: 6, reposts: 2, likes: 47, liked: false, reposted: false,
  },
  {
    id: 'p5',
    name: 'Priya Ramanathan',
    handle: 'priya.bsky.social',
    hue: 270,
    when: '9h',
    body: 'Reading recs for the plane: a novel, a book of essays, or the Bluesky custom feeds documentation? All three, obviously.',
    replies: 15, reposts: 3, likes: 91, liked: true, reposted: false,
  },
];

const NB_REPLIES = [
  { id: 'r1', name: 'Marco Díaz', handle: 'marco.bsky.social', hue: 35, when: '2h',
    body: 'This is exactly it. The readable-text part especially — it\'s not just a font choice, it\'s a whole posture.',
    replies: 2, reposts: 0, likes: 18, liked: false, reposted: false },
  { id: 'r2', name: 'Sana Okafor', handle: 'sana.nubecita.app', hue: 320, when: '2h',
    body: 'Can\'t wait to try this on my Fold. The three-pane view is going to be incredible for long threads.',
    replies: 1, reposts: 1, likes: 34, liked: true, reposted: false },
  { id: 'r3', name: 'Devon Park', handle: 'devon.dev', hue: 150, when: '1h',
    body: 'What\'s your default feed set up like? Asking for a friend who is me.',
    replies: 0, reposts: 0, likes: 7, liked: false, reposted: false },
];

const NB_NOTIFICATIONS = [
  { id: 'n1', type: 'like', names: ['Alice', 'Marco', 'Sana'], who: 'Alice Chen and 12 others', body: 'liked your post', preview: 'The thing about building a Bluesky client...', when: '2h', hue: 210 },
  { id: 'n2', type: 'follow', who: 'Priya Ramanathan', handle: 'priya.bsky.social', when: '3h', hue: 270 },
  { id: 'n3', type: 'repost', who: 'Marco Díaz', body: 'reposted your post', preview: 'Tiny victory: got adaptive layouts...', when: '5h', hue: 35 },
  { id: 'n4', type: 'reply', who: 'Devon Park', body: 'replied to your post', preview: 'This is exactly it. The readable-text part...', when: '7h', hue: 150 },
  { id: 'n5', type: 'mention', who: 'Sana Okafor', body: 'mentioned you', preview: 'Hey @alice, have you tried the new foldable layout?', when: '1d', hue: 320 },
];

const NB_FEEDS = [
  { id: 'fyp', name: 'For you', icon: 'auto_awesome' },
  { id: 'following', name: 'Following', icon: 'group' },
  { id: 'art', name: 'Art', icon: 'palette' },
  { id: 'tech', name: 'Tech', icon: 'memory' },
  { id: 'books', name: 'Books', icon: 'menu_book' },
];

const NB_SUGGESTED = [
  { name: 'Luna Park', handle: 'luna.nubecita.app', bio: 'Writing about cities, transit, and urban birds.', hue: 180 },
  { name: 'Kai Tanaka', handle: 'kai.bsky.social', bio: 'Design engineer. Cloud enthusiast (both kinds).', hue: 240 },
  { name: 'Rosa Veloso', handle: 'rosa.nubecita.app', bio: 'Illustrator. Making tiny things.', hue: 0 },
];

Object.assign(window, { NB_POSTS, NB_REPLIES, NB_NOTIFICATIONS, NB_FEEDS, NB_SUGGESTED });
