// Nubecita screens — one component per screen. Composed into index.html.

// ── Feed Screen ─────────────────────────────────────────
function FeedScreen({ onOpenPost, onOpenCompose }) {
  const [feed, setFeed] = React.useState('fyp');
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--surface)', position: 'relative' }}>
      <TopAppBar
        leading={<IconButton name="menu" />}
        title={<div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <img src="../../assets/logomark.svg" height="26" />
          <span style={{ fontFamily: 'var(--font-body)', fontWeight: 700, fontSize: 19 }}>nubecita</span>
        </div>}
        trailing={<><IconButton name="search" /><IconButton name="notifications" /></>}
      />
      <div style={{ display: 'flex', gap: 8, padding: '8px 16px 12px', overflowX: 'auto', borderBottom: '1px solid var(--outline-variant)' }}>
        {NB_FEEDS.map(f => (
          <Chip key={f.id} icon={f.icon} selected={feed === f.id} onClick={() => setFeed(f.id)}>{f.name}</Chip>
        ))}
      </div>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {NB_POSTS.map(p => <PostCard key={p.id} post={p} onOpen={() => onOpenPost?.(p)} />)}
      </div>
      <FAB icon="edit" onClick={onOpenCompose} style={{ position: 'absolute', bottom: 20, right: 20 }} />
    </div>
  );
}

// ── Post Detail (thread) ────────────────────────────────
function PostDetailScreen({ post, onBack, onReply }) {
  if (!post) post = NB_POSTS[0];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--surface)' }}>
      <TopAppBar
        leading={<IconButton name="arrow_back" onClick={onBack} />}
        title="Post"
        trailing={<IconButton name="more_vert" />}
      />
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {/* Focused post — larger, no border below */}
        <div style={{ padding: '16px 20px 12px', borderBottom: '1px solid var(--outline-variant)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
            <Avatar name={post.name} hue={post.hue} size={48} />
            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 600, fontSize: 16 }}>{post.name}</div>
              <div style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--fg-3)' }}>@{post.handle}</div>
            </div>
            <Button variant="tonal" size="sm">Follow</Button>
          </div>
          <div style={{ fontSize: 19, lineHeight: '30px', color: 'var(--fg-1)', marginBottom: 12, textWrap: 'pretty' }}>{post.body}</div>
          {post.image && <div style={{ height: 240, borderRadius: 16, background: post.image, marginBottom: 12, border: '1px solid var(--outline-variant)' }} />}
          <div style={{ fontSize: 13, color: 'var(--fg-3)', marginBottom: 12 }}>3:24 PM · Mar 4 · 2.1k views</div>
          <div style={{ display: 'flex', gap: 16, fontSize: 14, paddingBottom: 8 }}>
            <span><b style={{ color: 'var(--fg-1)' }}>{post.reposts}</b> <span style={{ color: 'var(--fg-3)' }}>Reposts</span></span>
            <span><b style={{ color: 'var(--fg-1)' }}>{post.likes}</b> <span style={{ color: 'var(--fg-3)' }}>Likes</span></span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-around', borderTop: '1px solid var(--outline-variant)', paddingTop: 6, marginLeft: -8, marginRight: -8 }}>
            <PostAction icon="chat_bubble" />
            <PostAction icon="repeat" />
            <PostAction icon="favorite" active activeColor="var(--peach-60)" fill />
            <PostAction icon="share" />
          </div>
        </div>
        {/* Reply composer prompt */}
        <div onClick={onReply} style={{ padding: '12px 20px', display: 'flex', alignItems: 'center', gap: 12, borderBottom: '1px solid var(--outline-variant)', cursor: 'pointer' }}>
          <Avatar name="You" hue={120} size={36} />
          <div style={{ flex: 1, color: 'var(--fg-3)', fontSize: 15 }}>Post your reply</div>
          <Button variant="tonal" size="sm" onClick={onReply}>Reply</Button>
        </div>
        {/* Replies */}
        {NB_REPLIES.map(r => <PostCard key={r.id} post={r} />)}
      </div>
    </div>
  );
}

// ── Composer ────────────────────────────────────────────
function ComposerScreen({ onCancel, onPost, replyTo }) {
  const [text, setText] = React.useState(replyTo ? '' : '');
  const max = 300;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--surface)' }}>
      <div style={{ display: 'flex', alignItems: 'center', padding: '8px 16px', height: 64, gap: 8 }}>
        <Button variant="text" onClick={onCancel}>Cancel</Button>
        <div style={{ flex: 1 }} />
        <Button variant="filled" size="sm" onClick={onPost} icon={null}>{replyTo ? 'Reply' : 'Post'}</Button>
      </div>
      {replyTo && (
        <div style={{ padding: '8px 20px 0', fontSize: 13, color: 'var(--fg-3)' }}>
          Replying to <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--primary)' }}>@{replyTo.handle}</span>
        </div>
      )}
      <div style={{ flex: 1, padding: '12px 20px', display: 'flex', gap: 12 }}>
        <Avatar name="You" hue={120} />
        <textarea
          autoFocus
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={replyTo ? 'Post your reply' : 'What\'s up?'}
          style={{
            flex: 1, border: 'none', outline: 'none', resize: 'none',
            fontFamily: 'var(--font-body)', fontSize: 18, lineHeight: '28px',
            color: 'var(--fg-1)', background: 'transparent', minHeight: 200,
          }}
        />
      </div>
      <div style={{ borderTop: '1px solid var(--outline-variant)', padding: '8px 12px', display: 'flex', alignItems: 'center', gap: 4 }}>
        <IconButton name="image" color="var(--primary)" />
        <IconButton name="gif_box" color="var(--primary)" />
        <IconButton name="mood" color="var(--primary)" />
        <IconButton name="alternate_email" color="var(--primary)" />
        <div style={{ flex: 1 }} />
        <div style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: text.length > max ? 'var(--error)' : 'var(--fg-3)' }}>
          {max - text.length}
        </div>
      </div>
    </div>
  );
}

// ── Profile ─────────────────────────────────────────────
function ProfileScreen({ onBack }) {
  const [tab, setTab] = React.useState('posts');
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--surface)' }}>
      <TopAppBar
        leading={<IconButton name="arrow_back" onClick={onBack} />}
        title=""
        trailing={<><IconButton name="share" /><IconButton name="more_vert" /></>}
      />
      {/* Banner */}
      <div style={{ height: 120, background: 'linear-gradient(135deg, #A6C8FF 0%, #6250B0 100%)', position: 'relative', flexShrink: 0 }} />
      <div style={{ padding: '0 20px 16px', marginTop: -44 }}>
        <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', marginBottom: 12 }}>
          <div style={{ border: '4px solid var(--surface)', borderRadius: 999, background: 'var(--surface)' }}>
            <Avatar name="Alice" hue={210} size={88} />
          </div>
          <Button variant="filled" size="sm" icon="edit">Edit profile</Button>
        </div>
        <div style={{ fontWeight: 700, fontSize: 22 }}>Alice Chen</div>
        <div style={{ fontFamily: 'var(--font-mono)', fontSize: 14, color: 'var(--fg-3)', marginBottom: 8 }}>@alice.nubecita.app</div>
        <div style={{ fontSize: 15, lineHeight: '22px', marginBottom: 10 }}>Building Nubecita — a little cloud app for Bluesky. ☁︎ Reading, writing, and generally opinions about interfaces.</div>
        <div style={{ display: 'flex', gap: 18, fontSize: 14 }}>
          <span><b style={{ fontSize: 15 }}>342</b> <span style={{ color: 'var(--fg-3)' }}>Following</span></span>
          <span><b style={{ fontSize: 15 }}>2.1k</b> <span style={{ color: 'var(--fg-3)' }}>Followers</span></span>
        </div>
      </div>
      <div style={{ display: 'flex', borderBottom: '1px solid var(--outline-variant)', flexShrink: 0 }}>
        {['posts', 'replies', 'media', 'likes'].map(t => (
          <button key={t} onClick={() => setTab(t)} style={{
            flex: 1, border: 'none', background: 'transparent', cursor: 'pointer',
            padding: '12px 0 10px', fontFamily: 'var(--font-body)', fontWeight: 600, fontSize: 14,
            color: tab === t ? 'var(--primary)' : 'var(--fg-3)',
            borderBottom: tab === t ? '2px solid var(--primary)' : '2px solid transparent',
            textTransform: 'capitalize',
          }}>{t}</button>
        ))}
      </div>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {NB_POSTS.slice(0, 3).map(p => <PostCard key={p.id} post={p} />)}
      </div>
    </div>
  );
}

// ── Notifications ───────────────────────────────────────
function NotificationsScreen({ onBack }) {
  const [tab, setTab] = React.useState('all');
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--surface)' }}>
      <TopAppBar
        leading={<IconButton name="arrow_back" onClick={onBack} />}
        title="Notifications"
        trailing={<IconButton name="settings" />}
      />
      <div style={{ display: 'flex', padding: '8px 16px 12px', gap: 8 }}>
        {['all', 'mentions', 'likes'].map(t => (
          <Chip key={t} selected={tab === t} onClick={() => setTab(t)}>
            {t[0].toUpperCase() + t.slice(1)}
          </Chip>
        ))}
      </div>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {NB_NOTIFICATIONS.map(n => <NotifRow key={n.id} n={n} />)}
      </div>
    </div>
  );
}

function NotifRow({ n }) {
  const iconMap = { like: 'favorite', follow: 'person_add', repost: 'repeat', reply: 'chat_bubble', mention: 'alternate_email' };
  const colorMap = { like: 'var(--peach-60)', follow: 'var(--primary)', repost: 'var(--success-40)', reply: 'var(--primary)', mention: 'var(--tertiary)' };
  return (
    <div style={{ display: 'grid', gridTemplateColumns: '40px 1fr', gap: 12, padding: '14px 20px', borderBottom: '1px solid var(--outline-variant)' }}>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
        <Icon name={iconMap[n.type]} size={22} fill={n.type === 'like' ? 1 : 0} color={colorMap[n.type]} />
      </div>
      <div style={{ minWidth: 0 }}>
        <Avatar name={n.who} hue={n.hue} size={32} />
        <div style={{ marginTop: 6, fontSize: 15, lineHeight: '22px' }}>
          <b>{n.who}</b> <span style={{ color: 'var(--fg-2)' }}>{n.body || (n.type === 'follow' ? 'followed you' : '')}</span>
          <span style={{ color: 'var(--fg-3)', marginLeft: 6, fontSize: 13 }}>· {n.when}</span>
        </div>
        {n.preview && <div style={{ color: 'var(--fg-3)', fontSize: 14, marginTop: 4, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>{n.preview}</div>}
        {n.type === 'follow' && n.handle && <div style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--fg-3)' }}>@{n.handle}</div>}
      </div>
    </div>
  );
}

// ── Search / Discover ───────────────────────────────────
function SearchScreen({ onBack }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--surface)' }}>
      <div style={{ padding: '12px 16px', display: 'flex', alignItems: 'center', gap: 10 }}>
        <IconButton name="arrow_back" onClick={onBack} />
        <div style={{ flex: 1, display: 'flex', alignItems: 'center', gap: 10, background: 'var(--surface-container)', borderRadius: 999, padding: '10px 18px' }}>
          <Icon name="search" size={20} color="var(--fg-2)" />
          <input placeholder="Search posts, people, feeds" style={{ border: 'none', background: 'transparent', outline: 'none', fontSize: 15, flex: 1, fontFamily: 'var(--font-body)' }} />
        </div>
      </div>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        <div style={{ padding: '16px 20px 8px', fontWeight: 700, fontSize: 16 }}>Suggested for you</div>
        {NB_SUGGESTED.map(u => (
          <div key={u.handle} style={{ display: 'flex', alignItems: 'flex-start', gap: 12, padding: '10px 20px' }}>
            <Avatar name={u.name} hue={u.hue} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontWeight: 600 }}>{u.name}</div>
              <div style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--fg-3)' }}>@{u.handle}</div>
              <div style={{ fontSize: 14, color: 'var(--fg-2)', marginTop: 4 }}>{u.bio}</div>
            </div>
            <Button variant="tonal" size="sm">Follow</Button>
          </div>
        ))}
        <div style={{ padding: '16px 20px 8px', fontWeight: 700, fontSize: 16, borderTop: '1px solid var(--outline-variant)', marginTop: 12 }}>Trending</div>
        {['#cloudwatching', '#m3expressive', '#bluesky', '#foldables'].map((tag, i) => (
          <div key={tag} style={{ padding: '12px 20px', borderBottom: '1px solid var(--outline-variant)' }}>
            <div style={{ fontSize: 12, color: 'var(--fg-3)' }}>Trending in Tech · {i + 1}</div>
            <div style={{ fontWeight: 600, fontSize: 16, color: 'var(--primary)' }}>{tag}</div>
            <div style={{ fontSize: 13, color: 'var(--fg-3)' }}>{(12 - i * 3) * 1000} posts</div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ── Settings ────────────────────────────────────────────
function SettingsScreen({ onBack, theme, setTheme }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--surface)' }}>
      <TopAppBar leading={<IconButton name="arrow_back" onClick={onBack} />} title="Settings" />
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {[
          { section: 'Account', items: [
            { icon: 'person', label: 'Profile', sub: '@alice.nubecita.app' },
            { icon: 'shield', label: 'Privacy & safety' },
            { icon: 'lock', label: 'Account', sub: 'Password, sessions' },
          ]},
          { section: 'App', items: [
            { icon: 'palette', label: 'Appearance', sub: theme === 'dark' ? 'Dark' : 'Light', toggle: true },
            { icon: 'text_fields', label: 'Text size', sub: 'Default' },
            { icon: 'notifications', label: 'Notifications' },
            { icon: 'language', label: 'Language', sub: 'English' },
          ]},
          { section: 'About', items: [
            { icon: 'info', label: 'About Nubecita', sub: 'v1.0.0' },
            { icon: 'help', label: 'Help & feedback' },
          ]},
        ].map(group => (
          <div key={group.section}>
            <div style={{ padding: '20px 20px 6px', fontSize: 13, fontWeight: 600, color: 'var(--primary)', letterSpacing: '0.02em' }}>{group.section}</div>
            {group.items.map(it => (
              <div key={it.label} onClick={() => it.toggle && setTheme(theme === 'dark' ? 'light' : 'dark')}
                style={{ display: 'flex', alignItems: 'center', gap: 16, padding: '12px 20px', cursor: it.toggle ? 'pointer' : 'default' }}>
                <Icon name={it.icon} size={22} color="var(--fg-2)" />
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 15 }}>{it.label}</div>
                  {it.sub && <div style={{ fontSize: 13, color: 'var(--fg-3)' }}>{it.sub}</div>}
                </div>
                {it.toggle ? (
                  <div style={{ width: 52, height: 32, borderRadius: 999, background: theme === 'dark' ? 'var(--primary)' : 'var(--outline-variant)', position: 'relative', transition: 'background 250ms' }}>
                    <div style={{ width: 26, height: 26, borderRadius: 999, background: 'white', position: 'absolute', top: 3, left: theme === 'dark' ? 23 : 3, transition: 'left 250ms var(--ease-spring-fast)', boxShadow: 'var(--elev-1)' }} />
                  </div>
                ) : (
                  <Icon name="chevron_right" size={22} color="var(--fg-3)" />
                )}
              </div>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}

// ── Onboarding ──────────────────────────────────────────
function OnboardingScreen({ onDone }) {
  const [step, setStep] = React.useState(0);
  const steps = [
    { title: 'A little cloud for Bluesky', sub: 'Nubecita is a native Android client for the skies.', art: true },
    { title: 'Sign in or create an account', sub: 'Use your Bluesky handle, or start fresh.', fields: true },
    { title: 'Pick a few feeds', sub: 'We\'ll fill your home with good stuff.', chips: true },
  ];
  const s = steps[step];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--surface)' }}>
      <div style={{ flex: 1, padding: '48px 28px 20px', display: 'flex', flexDirection: 'column', gap: 24 }}>
        {s.art && <div style={{ display: 'flex', justifyContent: 'center', marginTop: 20 }}>
          <img src="../../assets/cloud-illustration.svg" style={{ width: '100%', maxWidth: 320 }} />
        </div>}
        <div>
          <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 36, lineHeight: '42px', fontWeight: 600, fontVariationSettings: '"SOFT" 60', margin: 0, letterSpacing: '-0.015em' }}>{s.title}</h1>
          <p style={{ fontSize: 17, lineHeight: '26px', color: 'var(--fg-2)', marginTop: 12 }}>{s.sub}</p>
        </div>
        {s.fields && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ background: 'var(--surface-container)', borderRadius: '12px 12px 0 0', borderBottom: '2px solid var(--primary)', padding: '8px 16px 9px' }}>
              <div style={{ fontSize: 11, fontWeight: 500, color: 'var(--primary)' }}>Handle</div>
              <input placeholder="you.bsky.social" style={{ border: 'none', background: 'transparent', outline: 'none', fontSize: 15, width: '100%', padding: '4px 0 0' }} />
            </div>
            <div style={{ background: 'var(--surface-container)', borderRadius: '12px 12px 0 0', borderBottom: '1px solid var(--outline)', padding: '8px 16px 10px' }}>
              <div style={{ fontSize: 11, fontWeight: 500, color: 'var(--fg-2)' }}>App password</div>
              <input type="password" placeholder="••••-••••-••••-••••" style={{ border: 'none', background: 'transparent', outline: 'none', fontSize: 15, width: '100%', padding: '4px 0 0' }} />
            </div>
          </div>
        )}
        {s.chips && (
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {['Art', 'Tech', 'Books', 'Music', 'Science', 'Cooking', 'Gaming', 'Film', 'Sports', 'News', 'Cloudwatching'].map((t, i) =>
              <Chip key={t} selected={[0, 1, 10].includes(i)}>{t}</Chip>
            )}
          </div>
        )}
      </div>
      <div style={{ padding: '16px 20px', display: 'flex', alignItems: 'center', gap: 10, borderTop: '1px solid var(--outline-variant)' }}>
        <div style={{ display: 'flex', gap: 6 }}>
          {steps.map((_, i) => <div key={i} style={{ width: i === step ? 20 : 6, height: 6, borderRadius: 999, background: i === step ? 'var(--primary)' : 'var(--outline-variant)', transition: 'all 300ms var(--ease-spring-fast)' }} />)}
        </div>
        <div style={{ flex: 1 }} />
        {step > 0 && <Button variant="text" onClick={() => setStep(step - 1)}>Back</Button>}
        <Button variant="filled" onClick={() => step < steps.length - 1 ? setStep(step + 1) : onDone()}>
          {step === steps.length - 1 ? 'Enter' : 'Continue'}
        </Button>
      </div>
    </div>
  );
}

// ── Media Viewer ────────────────────────────────────────
function MediaViewerScreen({ onClose }) {
  return (
    <div style={{ position: 'absolute', inset: 0, background: '#000', display: 'flex', flexDirection: 'column', zIndex: 100 }}>
      <div style={{ height: 56, display: 'flex', alignItems: 'center', padding: '0 4px' }}>
        <IconButton name="close" color="#fff" onClick={onClose} />
        <div style={{ flex: 1, color: '#fff', fontSize: 14, paddingLeft: 8 }}>@marco.bsky.social</div>
        <IconButton name="download" color="#fff" />
        <IconButton name="more_vert" color="#fff" />
      </div>
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }}>
        <div style={{ width: '100%', maxWidth: 360, aspectRatio: '3/4', background: 'linear-gradient(135deg, #A6C8FF, #6250B0)', borderRadius: 16 }} />
      </div>
      <div style={{ padding: '16px 20px 24px', color: '#fff' }}>
        <div style={{ fontSize: 15, marginBottom: 10 }}>Mastodon has an onboarding problem. Bluesky has a discoverability problem. Both are fixable. Here's what we changed in Nubecita this week →</div>
        <div style={{ display: 'flex', gap: 18, fontSize: 13, color: 'rgba(255,255,255,0.8)' }}>
          <span>💬 28</span><span>🔁 14</span><span>♡ 210</span>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, {
  FeedScreen, PostDetailScreen, ComposerScreen, ProfileScreen,
  NotificationsScreen, SearchScreen, SettingsScreen, OnboardingScreen, MediaViewerScreen,
});
