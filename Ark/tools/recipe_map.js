// Recipe map renderer for the showcase's Recipes page (data: tools/recipe_tree.py).
// renderRecipeMap(box, D): draws the age into box's .rm-map, filling it without scrolling, and redraws it when the box
// changes size. A row per station (where a thing is made), a column per step from bare hands (one past the deepest of
// its inputs, held tools and needed station); every thing made is an icon with no name. Hovering or focusing one names
// it, gives its recipe and what it goes into, and lights the chain it needs and the things it makes; a click keeps it lit.
window.renderRecipeMap = function (box, D) {
  const map = box.querySelector('.rm-map'), note = box.querySelector('.rm-note');
  const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const N = D.nodes, R = D.rows, S = D.stations;
  const item = id => D.items[id] || {};
  const label = i => N[i].name || item(N[i].id).name || N[i].id;
  const img = (id, cls) => item(id).icon ? `<img${cls ? ` class="${cls}"` : ''} src="${item(id).icon}" alt="">` : '';

  // What each node needs (its inputs, held tools and the station's block or fire) and what it goes into.
  const needs = N.map(n => R[n.r].ins.filter(c => c.from != null).map(c => c.from).concat(R[n.r].need));
  const users = N.map(() => new Set());
  needs.forEach((list, i) => list.forEach(j => users[j].add(i)));
  const opens = new Set(R.flatMap(r => r.need)), finale = new Set(D.finale);

  // Bands are the stations that make something, columns the steps; a cell holds its nodes in the sequence's order.
  const bands = S.map((_, i) => i).filter(i => R.some(r => r.s === i));
  const steps = Math.max(...R.map(r => r.d)) + 1;
  const cells = new Map();
  N.forEach((n, i) => {
    const key = bands.indexOf(n.s) + '/' + R[n.r].d;
    if (!cells.has(key)) cells.set(key, []);
    cells.get(key).push(i);
  });
  const size = (b, d) => (cells.get(b + '/' + d) || []).length;

  // The icon size that fits: every cell packs its nodes into the rows of its band and the columns of its step, and the
  // widest cell per band and per step sets them; the packing that gives the largest icons wins.
  const GAP = 0.2, PAD_X = 1.0, PAD_Y = 0.45, MIN = 18, MAX = 52;
  function fit(w, h) {
    let best = null;
    for (let wide = 1; wide <= 16; wide++) {
      const rows = bands.map((_, b) => Math.max(1, ...Array.from({ length: steps }, (_, d) => Math.ceil(size(b, d) / wide))));
      const cols = Array.from({ length: steps }, (_, d) => Math.max(1, ...bands.map((_, b) => Math.ceil(size(b, d) / rows[b]))));
      const uw = cols.reduce((t, c) => t + c + (c - 1) * GAP + PAD_X, 0), uh = rows.reduce((t, r) => t + r + (r - 1) * GAP + PAD_Y, 0);
      const s = Math.min(w / uw, h / uh);
      if (!best || s > best.s) best = { s, rows, cols, uw, uh };
    }
    return best;
  }

  let nodes = [], wires = [], tip = null, locked = null;
  function draw() {
    const W = map.clientWidth, H = map.clientHeight;
    if (!W || !H) return;
    const gutter = W >= 900 ? 132 : 40, head = 22;
    const f = fit(W - gutter, H - head);
    // The gap rounds down, so a map that fits stays inside its box.
    const s = Math.max(MIN, Math.min(MAX, Math.floor(f.s))), g = Math.floor(s * GAP);
    // Space left over widens the lanes between steps and the bands, so the map always fills its box.
    const padX = Math.max(PAD_X * s, PAD_X * s + (W - gutter - f.uw * s) / steps);
    const padY = Math.max(PAD_Y * s, PAD_Y * s + (H - head - f.uh * s) / bands.length);
    const colX = [], colW = [], bandY = [], bandH = [];
    let x = gutter, y = head;
    f.cols.forEach((c, d) => { colX[d] = x; colW[d] = c * s + (c - 1) * g + padX; x += colW[d]; });
    f.rows.forEach((r, b) => { bandY[b] = y; bandH[b] = r * s + (r - 1) * g + padY; y += bandH[b]; });
    // Only a map that cannot fit at the smallest icons scrolls: a scrollbar that came and went with the map's own size
    // would shrink the box, redraw it to fit, drop the scrollbar and grow it again, without end.
    map.style.overflow = f.s < MIN ? 'auto' : 'hidden';

    const at = [];
    cells.forEach((list, key) => {
      const [b, d] = key.split('/').map(Number), wide = f.cols[d], lines = Math.ceil(list.length / wide);
      const top = bandY[b] + (bandH[b] - (lines * s + (lines - 1) * g)) / 2;
      list.forEach((i, k) => {
        const line = Math.floor(k / wide), inLine = Math.min(wide, list.length - line * wide);
        const start = Math.round(colX[d] + (colW[d] - (inLine * s + (inLine - 1) * g)) / 2);
        at[i] = { x: start + (k % wide) * (s + g), y: Math.round(top + line * (s + g)), from: start, to: start + inLine * s + (inLine - 1) * g };
      });
    });

    let html = bands.map((st, b) => `<div class="rm-band" style="top:${bandY[b]}px;height:${bandH[b]}px">`
      + `<span class="rm-station" title="${esc(S[st].name + (S[st].sub ? ': ' + S[st].sub : ''))}">`
      + (S[st].glyph ? `<img src="${S[st].glyph}" alt="">` : '') + (gutter > 40 ? `<span>${esc(S[st].name)}</span>` : '')
      + '</span></div>').join('');
    html += `<span class="rm-step rm-corner" style="width:${gutter - 8}px">${gutter > 40 ? 'Step' : ''}</span>`
      + Array.from({ length: steps }, (_, d) => `<span class="rm-step" style="left:${colX[d]}px;width:${colW[d]}px">${d}</span>`).join('');
    const paths = [];
    wires = [];
    N.forEach((n, i) => {
      // A wire leaves the end of its maker's line of icons and enters the start of its user's, so it never runs behind
      // a neighbour; lighting a node tells which icon of the line it is.
      const link = (j, kind) => {
        const a = at[j], b = at[i], x1 = a.to + 2, y1 = a.y + s / 2, x2 = b.from - 2, y2 = b.y + s / 2, dx = Math.max(16, (x2 - x1) / 2);
        paths.push(`<path class="rm-${kind}" d="M${x1} ${y1}C${x1 + dx} ${y1} ${x2 - dx} ${y2} ${x2} ${y2}"/>`);
        wires.push({ from: j, to: i });
      };
      R[n.r].ins.forEach(c => { if (c.from != null) link(c.from, c.tool ? 'tool' : 'in'); });
      R[n.r].need.forEach(j => link(j, 'need'));
    });
    html += `<svg class="rm-wires" width="${x}" height="${y}" aria-hidden="true">${paths.join('')}</svg>`;
    const count = Math.max(9, Math.round(s * 0.3));
    html += N.map((n, i) => `<button type="button" class="rm-node${opens.has(i) ? ' rm-opens' : ''}${finale.has(i) ? ' rm-finale' : ''}"`
      + ` data-i="${i}" aria-label="${esc(label(i))}" style="left:${at[i].x}px;top:${at[i].y}px;width:${s}px;height:${s}px;--rm-count:${count}px">`
      + img(n.id) + (n.n > 1 ? `<b>${n.n}</b>` : '') + '</button>').join('');
    html += '<div class="rm-tip" hidden></div>';
    map.innerHTML = html;
    nodes = [...map.querySelectorAll('.rm-node')];
    const paint = [...map.querySelectorAll('.rm-wires path')];
    wires.forEach((w, k) => { w.el = paint[k]; });
    tip = map.querySelector('.rm-tip');
    nodes.forEach((e, i) => {
      e.addEventListener('mouseenter', () => show(i));
      e.addEventListener('mouseleave', clear);
      e.addEventListener('focus', () => show(i));
      e.addEventListener('blur', clear);
      e.addEventListener('click', () => { locked = locked === i ? null : i; show(i); });
    });
    locked == null ? clear() : show(locked);
  }

  // Everything a node needs, all the way back to bare hands.
  function chain(i) {
    const up = new Set(), stack = [i];
    while (stack.length) for (const j of needs[stack.pop()]) if (!up.has(j)) { up.add(j); stack.push(j); }
    return up;
  }
  function card(i) {
    const n = N[i], r = R[n.r], st = S[n.s], world = r.ins.find(c => c.world);
    const where = world ? `${esc(r.op)} ${esc(world.world.toLowerCase())}` : `${esc(r.op)} at ${esc(st.name)}`;
    const line = c => `<li>${img(c.id)}<b>${c.tool ? 'held' : (c.n || 1) + '&times;'}</b><span>${esc(c.name || item(c.id).name || c.id)}</span></li>`;
    const cost = r.ins.filter(c => !c.world);
    const into = [...new Set([...users[i]].map(j => N[j].id))];
    const at = [...new Set([...users[i]].map(j => S[N[j].s].name))];
    let html = `<h4>${esc(label(i))}${n.n > 1 ? ` &times;${n.n}` : ''}</h4>`
      + `<div class="rm-where">${st.glyph ? `<img src="${st.glyph}" alt="">` : ''}<span>${where}`
      + (r.lvl ? ` · <em>level ${r.lvl}</em>` : '') + ` · step ${r.d}</span></div>`;
    if (cost.length) html += `<ul>${cost.map(line).join('')}</ul>`;
    if (n.val) html += `<div class="rm-val">${esc(n.val)}</div>`;
    if (finale.has(i)) html += `<small>Ends the age: it opens the ${esc(D.next)}.</small>`;
    html += into.length
      ? `<small>Goes into ${into.length} ${into.length === 1 ? 'thing' : 'things'} at ${esc(at.join(', '))}:</small>`
        + `<div class="rm-into">${into.slice(0, 32).map(id => img(id)).join('')}${into.length > 32 ? `<small>+${into.length - 32}</small>` : ''}</div>`
      : (finale.has(i) ? '' : '<small>Goes into nothing else in this age.</small>');
    return html;
  }
  function show(i) {
    const up = chain(i), down = users[i];
    map.classList.add('rm-lit');
    nodes.forEach((e, j) => {
      e.classList.toggle('rm-on', j === i || up.has(j) || down.has(j) || N[j].id === N[i].id);
      e.classList.toggle('rm-self', j === i);
      e.classList.toggle('rm-same', j !== i && N[j].id === N[i].id);
    });
    wires.forEach(w => w.el.classList.toggle('rm-on', ((w.to === i || up.has(w.to)) && up.has(w.from)) || (w.from === i && down.has(w.to))));
    tip.innerHTML = card(i) + (locked === i ? '<small>Click it again to let go.</small>' : '');
    tip.hidden = false;
    const e = nodes[i], s = e.offsetWidth, x = e.offsetLeft - map.scrollLeft, y = e.offsetTop - map.scrollTop;
    const tw = tip.offsetWidth, th = tip.offsetHeight, W = map.clientWidth, H = map.clientHeight;
    const left = x + s + 12 + tw <= W ? x + s + 12 : Math.max(4, x - 12 - tw);
    tip.style.left = left + map.scrollLeft + 'px';
    tip.style.top = Math.max(4, Math.min(H - th - 4, y - 8)) + map.scrollTop + 'px';
  }
  function clear() {
    if (locked != null) return show(locked);
    map.classList.remove('rm-lit');
    nodes.forEach(e => e.classList.remove('rm-on', 'rm-self', 'rm-same'));
    wires.forEach(w => w.el.classList.remove('rm-on'));
    if (tip) tip.hidden = true;
  }
  map.addEventListener('keydown', ev => { if (ev.key === 'Escape' && locked != null) { locked = null; clear(); } });

  if (note) note.textContent = D.problems.length ? D.problems.join(' · ') : `${N.length} things over ${steps - 1} steps from bare hands; every input is gathered or made in the age.`;
  const full = box.querySelector('.rm-full');
  if (full && box.requestFullscreen) full.addEventListener('click', () => document.fullscreenElement ? document.exitFullscreen() : box.requestFullscreen());
  else if (full) full.hidden = true;
  let queued = false;
  new ResizeObserver(() => { if (!queued) { queued = true; requestAnimationFrame(() => { queued = false; draw(); }); } }).observe(map);
  draw();
};
