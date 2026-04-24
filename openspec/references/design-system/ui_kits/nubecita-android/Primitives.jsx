// Nubecita shared UI primitives — used across all screens
// All styles inline or via CSS vars from ../../colors_and_type.css

const NB = {
  // shortcut color refs from CSS vars
  v: (k) => `var(--${k})`,
};

// ── Icon ────────────────────────────────────────────────
function Icon({ name, size = 24, fill = 0, weight = 400, color, style }) {
  return (
    <span
      className="material-symbols-rounded"
      style={{
        fontSize: size,
        fontVariationSettings: `'FILL' ${fill}, 'wght' ${weight}, 'GRAD' 0, 'opsz' 24`,
        color,
        lineHeight: 1,
        userSelect: 'none',
        flexShrink: 0,
        ...style,
      }}
    >
      {name}
    </span>
  );
}

// ── Avatar ──────────────────────────────────────────────
function Avatar({ name = 'A', size = 44, hue = 210 }) {
  const initial = (name || '?').trim()[0]?.toUpperCase() || '?';
  return (
    <div
      style={{
        width: size,
        height: size,
        borderRadius: 999,
        background: `linear-gradient(135deg, hsl(${hue}, 70%, 70%), hsl(${(hue + 40) % 360}, 60%, 55%))`,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: '#fff',
        fontFamily: 'var(--font-body)',
        fontWeight: 600,
        fontSize: size * 0.4,
        flexShrink: 0,
      }}
    >
      {initial}
    </div>
  );
}

// ── Button ──────────────────────────────────────────────
function Button({ children, variant = 'filled', icon, onClick, size = 'md', style }) {
  const heights = { sm: 32, md: 40, lg: 56 };
  const pads = { sm: '0 16px', md: '0 24px', lg: '0 32px' };
  const fs = { sm: 13, md: 14, lg: 16 };
  const base = {
    height: heights[size],
    padding: pads[size],
    borderRadius: 999,
    border: 'none',
    fontFamily: 'var(--font-body)',
    fontWeight: 600,
    fontSize: fs[size],
    display: 'inline-flex',
    alignItems: 'center',
    gap: 8,
    cursor: 'pointer',
    transition: 'transform 200ms var(--ease-spring-bouncy), background 200ms ease',
    whiteSpace: 'nowrap',
  };
  const variants = {
    filled:   { background: 'var(--primary)', color: 'var(--on-primary)' },
    tonal:    { background: 'var(--primary-container)', color: 'var(--on-primary-container)' },
    outlined: { background: 'transparent', color: 'var(--primary)', border: '1px solid var(--outline)' },
    text:     { background: 'transparent', color: 'var(--primary)', padding: '0 12px' },
    elevated: { background: 'var(--surface-container-low)', color: 'var(--primary)', boxShadow: 'var(--elev-1)' },
  };
  return (
    <button
      onClick={onClick}
      style={{ ...base, ...variants[variant], ...style }}
      onMouseDown={(e) => { e.currentTarget.style.transform = 'scale(0.96)'; }}
      onMouseUp={(e) => { e.currentTarget.style.transform = 'scale(1)'; }}
      onMouseLeave={(e) => { e.currentTarget.style.transform = 'scale(1)'; }}
    >
      {icon && <Icon name={icon} size={18} />}
      {children}
    </button>
  );
}

// ── IconButton ──────────────────────────────────────────
function IconButton({ name, onClick, size = 40, iconSize = 24, color, filled = false, style }) {
  return (
    <button
      onClick={onClick}
      style={{
        width: size, height: size, borderRadius: 999,
        background: 'transparent', border: 'none', cursor: 'pointer',
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        transition: 'background 150ms ease',
        ...style,
      }}
      onMouseEnter={(e) => e.currentTarget.style.background = 'var(--state-hover)'}
      onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
    >
      <Icon name={name} size={iconSize} fill={filled ? 1 : 0} color={color || 'var(--on-surface-variant)'} />
    </button>
  );
}

// ── FAB ─────────────────────────────────────────────────
function FAB({ icon = 'edit', onClick, extended, label, style }) {
  return (
    <button
      onClick={onClick}
      style={{
        height: 56,
        minWidth: extended ? undefined : 56,
        padding: extended ? '0 20px' : 0,
        width: extended ? undefined : 56,
        borderRadius: 16,
        background: 'var(--primary)',
        color: 'var(--on-primary)',
        border: 'none',
        cursor: 'pointer',
        boxShadow: 'var(--elev-3)',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 10,
        fontFamily: 'var(--font-body)',
        fontWeight: 600,
        fontSize: 14,
        transition: 'transform 250ms var(--ease-spring-bouncy)',
        ...style,
      }}
      onMouseDown={(e) => e.currentTarget.style.transform = 'scale(0.94)'}
      onMouseUp={(e) => e.currentTarget.style.transform = 'scale(1)'}
      onMouseLeave={(e) => e.currentTarget.style.transform = 'scale(1)'}
    >
      <Icon name={icon} size={24} color="var(--on-primary)" />
      {extended && label}
    </button>
  );
}

// ── Chip ────────────────────────────────────────────────
function Chip({ children, selected, icon, onClick }) {
  return (
    <button
      onClick={onClick}
      style={{
        height: 32,
        padding: icon ? '0 14px 0 10px' : '0 14px',
        borderRadius: 8,
        background: selected ? 'var(--primary-container)' : 'transparent',
        color: selected ? 'var(--on-primary-container)' : 'var(--fg-1)',
        border: selected ? '1px solid var(--primary-container)' : '1px solid var(--outline-variant)',
        fontFamily: 'var(--font-body)',
        fontWeight: 600,
        fontSize: 13,
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        cursor: 'pointer',
        whiteSpace: 'nowrap',
        transition: 'background 150ms ease',
      }}
    >
      {icon && <Icon name={icon} size={16} color="inherit" />}
      {children}
    </button>
  );
}

// ── TopAppBar ───────────────────────────────────────────
function TopAppBar({ title, leading, trailing, style }) {
  return (
    <div
      style={{
        height: 64,
        display: 'flex',
        alignItems: 'center',
        padding: '0 4px',
        background: 'var(--surface)',
        position: 'sticky',
        top: 0,
        zIndex: 10,
        ...style,
      }}
    >
      <div style={{ width: 48, display: 'flex', justifyContent: 'center' }}>{leading}</div>
      <div style={{ flex: 1, fontFamily: 'var(--font-body)', fontWeight: 600, fontSize: 22, color: 'var(--fg-1)' }}>
        {title}
      </div>
      <div style={{ display: 'flex', gap: 0, paddingRight: 4 }}>{trailing}</div>
    </div>
  );
}

// ── Bottom Nav ──────────────────────────────────────────
function BottomNav({ active = 'home', onChange }) {
  const items = [
    { id: 'home', icon: 'home', label: 'Home' },
    { id: 'search', icon: 'search', label: 'Search' },
    { id: 'alerts', icon: 'notifications', label: 'Alerts' },
    { id: 'chats', icon: 'mail', label: 'Chats' },
    { id: 'you', icon: 'person', label: 'You' },
  ];
  return (
    <div
      style={{
        height: 80,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-around',
        background: 'var(--surface-container)',
        padding: '12px 8px',
        flexShrink: 0,
      }}
    >
      {items.map((it) => {
        const isActive = active === it.id;
        return (
          <button
            key={it.id}
            onClick={() => onChange?.(it.id)}
            style={{
              border: 'none',
              background: 'transparent',
              flex: 1,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: 4,
              cursor: 'pointer',
              color: isActive ? 'var(--on-primary-container)' : 'var(--on-surface-variant)',
              fontFamily: 'var(--font-body)',
              fontWeight: 600,
              fontSize: 11,
            }}
          >
            <div
              style={{
                height: 32,
                width: 64,
                borderRadius: 999,
                background: isActive ? 'var(--primary-container)' : 'transparent',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'background 300ms var(--ease-spring-fast), width 300ms var(--ease-spring-fast)',
              }}
            >
              <Icon name={it.icon} size={22} fill={isActive ? 1 : 0} color="inherit" />
            </div>
            {it.label}
          </button>
        );
      })}
    </div>
  );
}

// ── Post card (the feed primitive) ──────────────────────
function PostCard({ post, compact, onOpen, onLike, onRepost }) {
  const [liked, setLiked] = React.useState(post.liked);
  const [reposted, setReposted] = React.useState(post.reposted);
  const handleLike = (e) => {
    e.stopPropagation();
    setLiked(!liked);
    onLike?.(!liked);
  };
  const handleRepost = (e) => {
    e.stopPropagation();
    setReposted(!reposted);
    onRepost?.(!reposted);
  };
  return (
    <div
      onClick={onOpen}
      style={{
        padding: '16px 20px',
        display: 'grid',
        gridTemplateColumns: '44px 1fr',
        gap: 12,
        borderBottom: '1px solid var(--outline-variant)',
        cursor: onOpen ? 'pointer' : 'default',
        background: 'var(--surface)',
      }}
    >
      <Avatar name={post.name} hue={post.hue} />
      <div style={{ minWidth: 0 }}>
        {post.repostedBy && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4, color: 'var(--fg-3)', fontSize: 12, fontWeight: 600 }}>
            <Icon name="repeat" size={14} color="var(--fg-3)" />
            <span>Reposted by {post.repostedBy}</span>
          </div>
        )}
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, flexWrap: 'wrap' }}>
          <span style={{ fontWeight: 600, fontSize: 15, color: 'var(--fg-1)' }}>{post.name}</span>
          <span style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--fg-3)' }}>@{post.handle}</span>
          <span style={{ color: 'var(--fg-3)' }}>·</span>
          <span style={{ fontSize: 13, color: 'var(--fg-3)' }}>{post.when}</span>
          <div style={{ marginLeft: 'auto' }}>
            <IconButton name="more_horiz" size={32} iconSize={20} />
          </div>
        </div>
        <div
          style={{
            fontSize: 17,
            lineHeight: '26px',
            color: 'var(--fg-1)',
            margin: '4px 0 10px',
            textWrap: 'pretty',
          }}
        >
          {post.body}
        </div>
        {post.image && (
          <div
            style={{
              borderRadius: 16,
              overflow: 'hidden',
              border: '1px solid var(--outline-variant)',
              background: post.image,
              height: compact ? 160 : 220,
              marginBottom: 10,
            }}
          />
        )}
        <div style={{ display: 'flex', alignItems: 'center', gap: 0, color: 'var(--fg-2)', marginLeft: -8 }}>
          <PostAction icon="chat_bubble" count={post.replies} />
          <PostAction icon="repeat" count={post.reposts + (reposted ? 1 : 0)} active={reposted} activeColor="var(--success-40)" onClick={handleRepost} />
          <PostAction icon="favorite" count={post.likes + (liked ? 1 : 0)} active={liked} activeColor="var(--peach-60)" onClick={handleLike} fill />
          <PostAction icon="share" />
        </div>
      </div>
    </div>
  );
}

function PostAction({ icon, count, active, activeColor, onClick, fill }) {
  return (
    <button
      onClick={onClick}
      style={{
        background: 'transparent',
        border: 'none',
        cursor: 'pointer',
        padding: '8px 12px',
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        color: active ? activeColor : 'var(--fg-2)',
        fontFamily: 'var(--font-body)',
        fontSize: 13,
        fontWeight: 500,
        borderRadius: 999,
        minWidth: 60,
        transition: 'background 120ms ease, color 200ms ease, transform 200ms var(--ease-spring-bouncy)',
      }}
      onMouseEnter={(e) => e.currentTarget.style.background = 'var(--state-hover)'}
      onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
    >
      <Icon name={icon} size={20} fill={active && fill ? 1 : 0} color="inherit" />
      {count !== undefined && <span>{count > 0 ? count : ''}</span>}
    </button>
  );
}

Object.assign(window, { NB, Icon, Avatar, Button, IconButton, FAB, Chip, TopAppBar, BottomNav, PostCard, PostAction });
