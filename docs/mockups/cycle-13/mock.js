/* Помощники макетов цикла 13: телефон, суммы, график, строки. Только для макетов. */
const M = (() => {
  const ic = {
    back: '<svg class="i" viewBox="0 0 24 24"><path d="M15 5l-7 7 7 7"/></svg>',
    more: '<svg class="i" viewBox="0 0 24 24"><circle cx="12" cy="5" r="1.2"/><circle cx="12" cy="12" r="1.2"/><circle cx="12" cy="19" r="1.2"/></svg>',
    chev: '<svg class="i" viewBox="0 0 24 24" style="width:18px;height:18px"><path d="M9 6l6 6-6 6"/></svg>',
    down: '<svg class="i" viewBox="0 0 24 24" style="width:18px;height:18px"><path d="M6 9l6 6 6-6"/></svg>',
    up: '<svg class="i" viewBox="0 0 24 24" style="width:18px;height:18px"><path d="M6 15l6-6 6 6"/></svg>',
    bell: '<svg class="i" viewBox="0 0 24 24"><path d="M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z"/><path d="M10 20a2 2 0 0 0 4 0"/></svg>',
    pen: '<svg class="i" viewBox="0 0 24 24"><path d="M4 20h4L19 9l-4-4L4 16z"/></svg>',
    trash: '<svg class="i" viewBox="0 0 24 24"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"/></svg>',
    eye: '<svg class="i" viewBox="0 0 24 24"><path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/></svg>',
    key: '<svg class="i" viewBox="0 0 24 24"><circle cx="8" cy="15" r="4"/><path d="M11 12l9-9M17 6l3 3M14 9l2 2"/></svg>',
    file: '<svg class="i" viewBox="0 0 24 24"><path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4"/></svg>',
    copy: '<svg class="i" viewBox="0 0 24 24"><rect x="4" y="4" width="16" height="16" rx="4"/><path d="M8 12l3 3 5-6"/></svg>',
    lock: '<svg class="i" viewBox="0 0 24 24"><rect x="5" y="10" width="14" height="10" rx="3"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg>',
    check: '<svg class="i" viewBox="0 0 24 24" style="width:20px;height:20px;color:var(--brass2)"><path d="M5 12l5 5 9-10"/></svg>',
  };
  // «184 320,50 ₽» → копейки отдельным span (как MoneyText)
  const m = t => t.replace(/,(\d\d)/, '<span class="k">,$1</span>');
  const amt = (t, cls = 'rowa') => `<span class="amt ${cls}">${m(t)}</span>`;
  const bk = (t, bg, fg = '#fff', cls = '') => `<span class="bk ${cls}" style="background:${bg};color:${fg}">${t}</span>`;
  const banks = {
    sber: (c = '') => bk('СБ', 'linear-gradient(160deg,#2FC25B,#138A36)', '#fff', c),
    tb: (c = '') => bk('Т', 'linear-gradient(160deg,#FFE14D,#F2C200)', '#14120E', c),
    alfa: (c = '') => bk('А', 'linear-gradient(160deg,#F04A3A,#C8251A)', '#fff', c),
    vtb: (c = '') => bk('ВТБ', 'linear-gradient(160deg,#2C6BEE,#1340B8)', '#fff', c),
  };
  const sbar = () => `<div class="sbar"><span>12:24</span><span class="ic"><i></i><i></i><i style="width:8px"></i></span></div>`;
  const hdr = (title, o = {}) => o.tab
    ? `<div class="hdr tab"><h1>${title}</h1>${o.icons || ''}</div>`
    : `<div class="hdr"><span class="back">${ic.back}</span><h1>${title}</h1>${o.icons || ''}</div>`;
  const tabs = (items, sel) => `<div class="tabs">${items.map((t, i) => `<span class="${i === sel ? 'on' : ''}">${t}</span>`).join('')}</div>`;
  const chips = (items, sel, sm = true) => `<div class="chips">${items.map((t, i) => `<span class="chip ${sm ? 'sm' : ''} ${i === sel ? 'on' : ''}">${t}</span>`).join('')}</div>`;
  const hl = (title, sub, sum, o = {}) => `<div class="hl ${o.lit ? 'lit' : ''} ${o.pressed ? 'pressed' : ''}">${o.lead || ''}<div class="tx"><b>${title}</b><span>${sub}${o.glyph ? ' ' + o.glyph : ''}</span></div>${sum ? amt(sum, 'small' + (sum.startsWith('+') ? ' inc' : '')) : ''}</div>`;
  const phone = (inner, o = {}) => `<div class="ph ${o.short ? 'short' : ''}">${sbar()}${inner}<div class="gbar"></div></div>`;
  const frame = (cap, ph, notes = [], pins = []) =>
    `<div class="fr"><div class="cap">${cap}</div><div class="phwrap">${ph}${pins.map((t, i) => `<span class="pin" style="top:${t}px">${i + 1}</span>`).join('')}</div>` +
    (notes.length ? `<ol>${notes.map((n, i) => `<li data-n="${pins[i] != null ? i + 1 : '•'}">${n}</li>`).join('')}</ol>` : '') + `</div>`;

  /* CapitalChart: линия 2 dp, заливка, точка «сегодня» или выбранная; шкала не уже 2 % среднего */
  let gid = 0;
  function chart(pts, o = {}) {
    const w = o.w || 322, h = o.h || 150, c = o.color || 'var(--brass2)';
    let lo = Math.min(...pts), hi = Math.max(...pts);
    const avg = pts.reduce((a, b) => a + b, 0) / pts.length;
    if (hi - lo < avg * 0.02) { const mid = (hi + lo) / 2; lo = mid - avg * 0.01; hi = mid + avg * 0.01; }
    const pad = 8, X = i => pad + (w - 2 * pad) * i / (pts.length - 1), Y = v => pad + (h - 2 * pad) * (1 - (v - lo) / (hi - lo));
    const d = pts.map((v, i) => (i ? 'L' : 'M') + X(i).toFixed(1) + ' ' + Y(v).toFixed(1)).join(' ');
    const id = 'cg' + (gid++);
    let s = `<svg class="chart" viewBox="0 0 ${w} ${h}" width="100%" height="${h}"><defs><linearGradient id="${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" style="stop-color:${c};stop-opacity:.22"/><stop offset="1" style="stop-color:${c};stop-opacity:0"/></linearGradient></defs>`;
    s += `<path d="${d} L${X(pts.length - 1)} ${h} L${X(0)} ${h} Z" style="fill:url(#${id})"/>`;
    s += `<path d="${d}" style="fill:none;stroke:${c};stroke-width:2;stroke-linejoin:round;stroke-linecap:round"/>`;
    (o.marks || []).forEach(i => { s += `<circle cx="${X(i)}" cy="${Y(pts[i])}" r="5" style="fill:${c};stroke:var(--bg);stroke-width:2"/>`; });
    if (o.sel != null) {
      const x = X(o.sel), y = Y(pts[o.sel]);
      s += `<line x1="${x}" x2="${x}" y1="0" y2="${h}" style="stroke:var(--brass2);stroke-opacity:.45;stroke-dasharray:3 3"/><circle cx="${x}" cy="${y}" r="5.5" style="fill:var(--bg);stroke:${c};stroke-width:2.5"/>`;
    } else {
      s += `<circle cx="${X(pts.length - 1)}" cy="${Y(pts[pts.length - 1])}" r="4" style="fill:${c}"/>`;
    }
    return s + '</svg>';
  }
  const xAt = (i, n, w = 322) => 8 + (w - 16) * i / (n - 1);

  function tabsInit() {
    document.querySelectorAll('.vtabs').forEach(bar => {
      const btns = [...bar.querySelectorAll('button')];
      const show = id => {
        btns.forEach(b => b.setAttribute('aria-selected', String(b.dataset.v === id)));
        btns.forEach(b => { const v = document.getElementById(b.dataset.v); if (v) v.hidden = b.dataset.v !== id; });
      };
      btns.forEach(b => b.addEventListener('click', () => show(b.dataset.v)));
      const h = location.hash.slice(1);
      show(btns.some(b => b.dataset.v === h) ? h : btns[0].dataset.v);
    });
  }
  return { ic, m, amt, bk, banks, sbar, hdr, tabs, chips, hl, phone, frame, chart, xAt, tabsInit };
})();
