// Recipe tree renderer for the showcase's Recipes page (data: tools/recipe_tree.py).
// renderSpine(host, trace, jump, data): draws the tree into host, a box that scrolls vertically; trace is the element
// that names the traced item, jump the element that gets one button per station.
// The stations of an age run top to bottom in the order they open, Hands and the Inventory first, on one spine. Each
// recipe is a row on its station: its inputs with their counts, the operation (and the level that unlocks it), what it
// makes. A world source is dashed, a held tool dashed long; hovering an item lights every row that makes or uses it.
window.renderSpine = function (host, trace, jump, D) {
  const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  // Where each item is made and used, by station, for the trace readout. Keys tie an item to the tags it fills.
  const made = {}, used = {};
  const note = (book, key, station) => { (book[key] = book[key] || new Set()).add(station); };
  for (const s of D.stations) for (const r of s.rows) {
    for (const c of r.outs) for (const k of c.k) note(made, k, s.name);
    for (const c of r.ins) if (!c.world) for (const k of c.k) note(used, k, s.name);
  }

  const name = c => c.world || c.name || (D.items[c.id] || {}).name || c.id;
  function tip(c) {
    const t = [c.world ? c.world + ' (in the world)' : name(c)];
    if (c.tool) t.push('A tool held while gathering');
    if (c.val) t.push(c.val);
    return t.join(' · ');
  }
  function chip(c, kind) {
    const it = D.items[c.id] || {};
    const cls = `sp-chip sp-${c.world ? 'world' : kind}${c.tool ? ' sp-tool' : ''}`;
    return `<span class="${cls}" data-k="${esc(c.k.join(' '))}" tabindex="0" role="button" title="${esc(tip(c))}">`
      + (it.icon ? `<img src="${it.icon}" alt="">` : '')
      + (c.n > 1 ? `<b>${c.n}&times;</b>` : '') + `<span>${esc(name(c))}${c.tool ? ', held' : ''}</span>`
      + (kind === 'out' && c.val ? `<small>${esc(c.val)}</small>` : '') + '</span>';
  }
  function row(r) {
    const ins = r.ins.map(c => chip(c, 'in')).join('<i class="sp-plus">+</i>');
    const op = esc(r.op) + (r.lvl ? ` <em>lvl ${r.lvl}</em>` : '');
    return `<li class="sp-row"><div class="sp-ins">${ins}</div><div class="sp-op"><span>${op}</span></div>`
      + `<div class="sp-outs">${r.outs.map(c => chip(c, 'out')).join('')}</div></li>`;
  }
  const count = s => s.rows.length + (s.rows.length === 1 ? ' recipe' : ' recipes');
  let html = `<div class="sp-v"><p class="sp-age">${esc(D.title)}</p>`;
  for (const s of D.stations) {
    html += `<section class="sp-st${s.gate ? ' sp-gate' : ''}" data-station="${esc(s.id)}">`
      + `<header class="sp-head">${s.glyph ? `<img src="${s.glyph}" alt="">` : ''}<b>${esc(s.name)}</b>`
      + `<small>${esc(s.sub)}${s.rows.length ? (s.sub ? ' · ' : '') + count(s) : ''}</small></header>`
      + (s.rows.length ? `<ol class="sp-rows">${s.rows.map(row).join('')}</ol>` : '') + '</section>';
  }
  const rows = D.stations.reduce((n, s) => n + s.rows.length, 0);
  html += `<p class="sp-age sp-next">${esc(D.next)}<small>${D.stations.length} stations · ${rows} recipes</small></p></div>`;
  host.innerHTML = html;

  // One button per station scrolls the box to it.
  if (jump) {
    jump.innerHTML = D.stations.map(s => `<button type="button" data-station="${esc(s.id)}">${esc(s.name)}</button>`).join('');
    const still = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    jump.querySelectorAll('button').forEach(b => b.addEventListener('click', () => {
      const st = host.querySelector(`.sp-st[data-station="${b.dataset.station}"]`);
      host.scrollTo({ top: st.offsetTop - 6, behavior: still ? 'auto' : 'smooth' });
    }));
  }

  // Tracing: hover or focus lights every chip of the same item (or a tag it fills); a click keeps it lit.
  const tree = host.querySelector('.sp-v'), chips = [...tree.querySelectorAll('.sp-chip')];
  const keysOf = e => e.dataset.k.split(' ');
  const list = set => set ? [...set].join(', ') : 'nowhere in this age';
  const show = keys => {
    tree.classList.add('sp-tracing');
    chips.forEach(e => e.classList.toggle('sp-hot', keysOf(e).some(k => keys.includes(k))));
    if (!trace) return;
    const where = book => { const all = new Set(); keys.forEach(k => (book[k] || []).forEach(s => all.add(s))); return all.size ? all : null; };
    trace.innerHTML = `<b>${esc((D.items[keys[0]] || {}).name || keys[0])}</b> is gathered or made at ${esc(list(where(made)))}`
      + ` and used at ${esc(list(where(used)))}.${host.dataset.locked ? ' Click it again to let go.' : ''}`;
  };
  const clear = () => {
    if (host.dataset.locked) return show(host.dataset.locked.split(' '));
    tree.classList.remove('sp-tracing');
    chips.forEach(e => e.classList.remove('sp-hot'));
    if (trace) trace.textContent = 'Hover an item to see where it is made and used; click it to keep it lit.';
  };
  for (const e of chips) {
    e.addEventListener('mouseenter', () => show(keysOf(e)));
    e.addEventListener('mouseleave', clear);
    e.addEventListener('focus', () => show(keysOf(e)));
    e.addEventListener('blur', clear);
    const toggle = () => {
      if (host.dataset.locked === e.dataset.k) delete host.dataset.locked; else host.dataset.locked = e.dataset.k;
      host.dataset.locked ? show(host.dataset.locked.split(' ')) : clear();
    };
    e.addEventListener('click', toggle);
    e.addEventListener('keydown', ev => { if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); toggle(); } });
  }
  clear();
};
