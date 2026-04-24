// Tablet + Foldable adaptive layouts for Nubecita

// ── Nav Rail (tablet medium) ────────────────────────────
function NavRail({ active = 'home', onChange, expanded = false }) {
  const items = [
    { id: 'home', icon: 'home', label: 'Home' },
    { id: 'search', icon: 'search', label: 'Search' },
    { id: 'alerts', icon: 'notifications', label: 'Alerts' },
    { id: 'chats', icon: 'mail', label: 'Chats' },
    { id: 'you', icon: 'person', label: 'You' },
  ];
  return (
    <div style={{
      width: expanded ? 240 : 96,
      display: 'flex', flexDirection: 'column', alignItems: 'stretch',
      gap: 8, padding: '16px 12px', background: 'var(--surface)',
      borderRight: '1px solid var(--outline-variant)',
      flexShrink: 0, transition: 'width 350ms var(--ease-spring-fast)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 8px 16px' }}>
        <img src="../../assets/logomark.svg" height="28" />
        {expanded && <span style={{ fontWeight: 700, fontSize: 18 }}>nubecita</span>}
      </div>
      <FAB icon="edit" extended={expanded} label="New post" style={{ alignSelf: expanded ? 'flex-start' : 'center', marginBottom: 12 }} />
      {items.map(it => {
        const isActive = active === it.id;
        return (
          <button key={it.id} onClick={() => onChange?.(it.id)} style={{
            border: 'none', background: 'transparent', cursor: 'pointer',
            display: 'flex', flexDirection: expanded ? 'row' : 'column', alignItems: 'center',
            gap: expanded ? 12 : 4, padding: expanded ? '0 16px' : 0,
            color: isActive ? 'var(--on-primary-container)' : 'var(--on-surface-variant)',
            fontFamily: 'var(--font-body)', fontWeight: 600, fontSize: 12,
          }}>
            <div style={{
              width: expanded ? 'auto' : 56, height: 32, minWidth: 56,
              padding: expanded ? '0 16px' : 0,
              borderRadius: 999,
              background: isActive ? 'var(--primary-container)' : 'transparent',
              display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
            }}>
              <Icon name={it.icon} size={22} fill={isActive ? 1 : 0} color="inherit" />
              {expanded && <span style={{ fontSize: 14 }}>{it.label}</span>}
            </div>
            {!expanded && it.label}
          </button>
        );
      })}
    </div>
  );
}

// ── Tablet Dual-Pane ────────────────────────────────────
function TabletLayout() {
  const [selected, setSelected] = React.useState(NB_POSTS[1]);
  return (
    <div style={{ display: 'flex', height: '100%', background: 'var(--surface)', fontFamily: 'var(--font-body)' }}>
      <NavRail active="home" />
      {/* Feed list */}
      <div style={{ width: 400, borderRight: '1px solid var(--outline-variant)', display: 'flex', flexDirection: 'column', flexShrink: 0 }}>
        <div style={{ padding: '16px 20px 12px', display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{ fontFamily: 'var(--font-display)', fontSize: 24, fontWeight: 600, fontVariationSettings: '"SOFT" 60' }}>Home</div>
          <div style={{ flex: 1 }} />
          <IconButton name="tune" />
        </div>
        <div style={{ display: 'flex', gap: 8, padding: '0 16px 12px', overflowX: 'auto' }}>
          {NB_FEEDS.slice(0, 4).map((f, i) => <Chip key={f.id} icon={f.icon} selected={i === 0}>{f.name}</Chip>)}
        </div>
        <div style={{ flex: 1, overflowY: 'auto' }}>
          {NB_POSTS.map(p => (
            <div key={p.id} onClick={() => setSelected(p)} style={{
              background: selected?.id === p.id ? 'var(--primary-container)' : 'transparent',
              transition: 'background 200ms ease',
            }}>
              <PostCard post={p} compact />
            </div>
          ))}
        </div>
      </div>
      {/* Post detail */}
      <div style={{ flex: 1, minWidth: 0 }}>
        <PostDetailScreen post={selected} />
      </div>
    </div>
  );
}

// ── Foldable Three-Pane ─────────────────────────────────
function FoldableLayout() {
  const [selected, setSelected] = React.useState(NB_POSTS[1]);
  return (
    <div style={{ display: 'flex', height: '100%', background: 'var(--surface)', fontFamily: 'var(--font-body)' }}>
      <NavRail active="home" expanded />
      {/* Feed list */}
      <div style={{ width: 360, borderRight: '1px solid var(--outline-variant)', display: 'flex', flexDirection: 'column', flexShrink: 0 }}>
        <div style={{ padding: '16px 20px 8px', display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{ fontFamily: 'var(--font-display)', fontSize: 22, fontWeight: 600 }}>Home</div>
        </div>
        <div style={{ display: 'flex', gap: 8, padding: '0 16px 12px', overflowX: 'auto' }}>
          {NB_FEEDS.slice(0, 3).map((f, i) => <Chip key={f.id} icon={f.icon} selected={i === 0}>{f.name}</Chip>)}
        </div>
        <div style={{ flex: 1, overflowY: 'auto' }}>
          {NB_POSTS.map(p => (
            <div key={p.id} onClick={() => setSelected(p)} style={{
              background: selected?.id === p.id ? 'var(--primary-container)' : 'transparent',
            }}>
              <PostCard post={p} compact />
            </div>
          ))}
        </div>
      </div>
      {/* Thread */}
      <div style={{ flex: 1, minWidth: 0, borderRight: '1px solid var(--outline-variant)' }}>
        <PostDetailScreen post={selected} />
      </div>
      {/* Side panel: suggested + trending */}
      <div style={{ width: 320, overflowY: 'auto', padding: '20px 20px', display: 'flex', flexDirection: 'column', gap: 20, flexShrink: 0, background: 'var(--surface-container-low)' }}>
        <div>
          <div style={{ fontWeight: 700, fontSize: 16, marginBottom: 10 }}>Suggested</div>
          {NB_SUGGESTED.map(u => (
            <div key={u.handle} style={{ display: 'flex', gap: 10, marginBottom: 12 }}>
              <Avatar name={u.name} hue={u.hue} size={36} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 600, fontSize: 14 }}>{u.name}</div>
                <div style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--fg-3)' }}>@{u.handle}</div>
              </div>
              <Button variant="tonal" size="sm">Follow</Button>
            </div>
          ))}
        </div>
        <div>
          <div style={{ fontWeight: 700, fontSize: 16, marginBottom: 10 }}>Trending</div>
          {['#cloudwatching', '#m3expressive', '#foldables'].map((tag, i) => (
            <div key={tag} style={{ padding: '8px 0', borderBottom: '1px solid var(--outline-variant)' }}>
              <div style={{ fontSize: 11, color: 'var(--fg-3)' }}>Trending · {i + 1}</div>
              <div style={{ fontWeight: 600, fontSize: 14, color: 'var(--primary)' }}>{tag}</div>
              <div style={{ fontSize: 12, color: 'var(--fg-3)' }}>{(12 - i * 3) * 1000} posts</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { NavRail, TabletLayout, FoldableLayout });
