// Recipe tree renderer for the showcase's Recipes page (data: tools/recipe_tree.py).
// renderSpine(host, trace, data): draws the SVG into host; trace is the element that names the traced item.
// The stations of an age run left to right in the order they open, Hands and the Inventory first. Each recipe is a
// column on its station: its inputs, with their counts, hang above the station band and its outputs below. A world
// source is dashed, an item gathered or made in another column is a label pointing back at the line it joins. When host
// sits in a .sp-scroll, a station jump bar is added before it and the chart can be dragged sideways.
// Fonts come from the --sp-body, --sp-mono and --sp-display tokens so text measurement matches the CSS.
window.renderSpine = function (host, trace, D) {
  const CH = 24, BAND = 64, TOP = 40, GAP_IN = 20, GAP_OUT = 20, PLUS = 14, CG = 18;
  const NODE_GAP = 52, X_START = 24;
  const css = getComputedStyle(host);
  const family = (name, fallback) => (css.getPropertyValue(name) || '').trim() || fallback;
  const body = family('--sp-body', 'system-ui, sans-serif'), mono = family('--sp-mono', 'monospace'),
    display = family('--sp-display', 'sans-serif');
  const FONT = { chip: `500 12px ${body}`, mono: `500 10px ${mono}`, head: `700 17px ${display}`, val: `400 9.5px ${mono}` };
  const ctx = document.createElement('canvas').getContext('2d');
  const tw = (t, f) => { ctx.font = FONT[f]; return ctx.measureText(t || '').width; };
  const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  // Where each item is made and used, by station, for the trace readout. Keys tie an item to the tags it fills.
  const made = {}, used = {};
  const note = (book, key, station) => { (book[key] = book[key] || new Set()).add(station); };
  for (const s of D.stations) for (const r of s.rows) {
    for (const c of r.outs) for (const k of c.k) note(made, k, s.name);
    for (const c of r.ins) if (!c.world) for (const k of c.k) note(used, k, s.name);
  }

  const kindOf = c => (c.world ? 'world' : 'in');
  const label = c => {
    if (c.world) return c.world;
    const name = c.name || D.items[c.id].name;
    return (c.n > 1 ? c.n + '× ' : '') + name + (c.tool ? ', held' : '');
  };
  const chipW = (c, kind) => Math.ceil(28 + tw(label(c), 'chip') + 9 + (kind === 'in' ? 9 : 0));
  // Room an output takes: the rate or note under it may be wider than the chip.
  const slotW = c => Math.max(chipW(c, 'out'), c.val ? Math.ceil(tw(c.val, 'val')) + 8 : 0);

  function tip(c) {
    const t = [c.world ? c.world + ' (in the world)' : D.items[c.id].name];
    if (c.n > 1) t.push(c.n + ' of them');
    if (c.tool) t.push('A tool held while gathering');
    if (c.val) t.push(c.val);
    return t.join('\n');
  }

  // A chip is drawn from its left edge x at centre line y. Labels of inputs point left, at the wire they join.
  function chip(c, x, y, kind) {
    const it = D.items[c.id] || {}, w = chipW(c, kind), t = y - CH / 2, ix = x + (kind === 'in' ? 14 : 6), iy = t + 4;
    let s = `<g class="sp-chip sp-${kind}${c.tool ? ' sp-tool' : ''}" data-k="${esc(c.k.join(' '))}" tabindex="0" role="button" aria-label="${esc(label(c))}"><title>${esc(tip(c))}</title>`;
    s += kind === 'in'
      ? `<path class="sp-shape" d="M${x} ${y}l10 ${-CH / 2}h${w - 10}v${CH}h${-(w - 10)}z"/>`
      : `<rect class="sp-shape" x="${x}" y="${t}" width="${w}" height="${CH}" rx="2"/>`;
    if (it.icon) s += `<image href="${it.icon}" x="${ix}" y="${iy}" width="16" height="16" preserveAspectRatio="none" style="image-rendering:pixelated"/>`;
    s += `<text class="sp-label" x="${ix + 22}" y="${y + 4}">${esc(label(c))}</text>`;
    if (c.val && kind === 'out') s += `<text class="sp-val" x="${x + 2}" y="${t + CH + 11}">${esc(c.val)}</text>`;
    return s + '</g>';
  }

  function fit(text, font, max, cls, x, y) {
    const adj = tw(text, font) > max ? ` textLength="${Math.floor(max)}" lengthAdjust="spacingAndGlyphs"` : '';
    return `<text class="${cls}" x="${x}" y="${y}"${adj}>${esc(text)}</text>`;
  }

  const opText = r => r.op + (r.lvl ? ' · lvl ' + r.lvl : '');

  // Measure every recipe column first: the tallest input stack sets where the line runs.
  const cols = new Map();
  let maxIn = 0, maxOut = 0;
  for (const s of D.stations) for (const r of s.rows) {
    const hasVal = r.outs.some(c => c.val), outStep = CH + (hasVal ? 20 : 10);
    const inMax = r.ins.length ? Math.max(...r.ins.map(c => chipW(c, kindOf(c)))) : 0;
    const outMax = r.outs.length ? Math.max(...r.outs.map(slotW)) : 0;
    const w = Math.ceil(Math.max(10 + inMax, 12 + outMax, 12 + tw(opText(r), 'mono'), 44));
    const inH = r.ins.length ? GAP_IN + r.ins.length * CH + (r.ins.length - 1) * PLUS : 0;
    const outH = r.outs.length ? GAP_OUT + (r.outs.length - 1) * outStep + CH + (hasVal ? 13 : 0) : 0;
    cols.set(r, { w, outStep });
    maxIn = Math.max(maxIn, inH);
    maxOut = Math.max(maxOut, outH);
  }
  const SY = TOP + maxIn + BAND / 2, BT = SY - BAND / 2, BB = SY + BAND / 2;

  function columnSvg(r, x0) {
    const { w, outStep } = cols.get(r), bx = x0 + 4;
    let back = '', front = '';
    // Inputs stack upwards from the band, the last one nearest the station, joined by "+".
    const n = r.ins.length;
    const inY = k => BT - GAP_IN - CH / 2 - (n - 1 - k) * (CH + PLUS);
    if (n) back += `<path class="sp-wire" d="M${bx} ${BT}V${inY(0)}"/>`;
    r.ins.forEach((c, k) => {
      const y = inY(k), px = bx + 6;
      back += `<path class="sp-wire" d="M${bx} ${y}H${px}"/>`;
      front += chip(c, px, y, kindOf(c));
      if (k < n - 1) front += `<text class="sp-plus" x="${px + 22}" y="${y + CH / 2 + PLUS / 2 + 4}" text-anchor="middle">+</text>`;
    });
    // Outputs hang below the band on a bus.
    const outY = k => BB + GAP_OUT + CH / 2 + k * outStep;
    if (r.outs.length) back += `<path class="sp-wire" d="M${bx} ${BB}V${outY(r.outs.length - 1)}"/>`;
    r.outs.forEach((c, k) => {
      const y = outY(k), ox = bx + 8;
      back += `<path class="sp-wire" d="M${bx} ${y}H${ox}"/>`;
      front += chip(c, ox, y, 'out');
    });
    // Inside the band: the through line, both pins and the operation with its level.
    front += `<line class="sp-through" x1="${bx}" x2="${bx}" y1="${BT + 3}" y2="${BB - 3}"/>`;
    if (n) front += `<rect class="sp-pin" x="${bx - 3}" y="${BT - 3}" width="6" height="6"/>`;
    if (r.outs.length) front += `<rect class="sp-pin" x="${bx - 3}" y="${BB - 3}" width="6" height="6"/>`;
    front += fit(opText(r), 'mono', w - 8, 'sp-op' + (r.lvl ? ' sp-gated' : ''), bx + 6, BB - 10);
    return { back, front };
  }

  function nodeSvg(s, x0, x1, head) {
    const cls = `sp-node${s.gate ? ' sp-gate' : ''}`;
    let out = s.gate
      ? `<path class="${cls}" d="M${x0 + 12} ${BT}H${x1 - 12}L${x1} ${BT + 12}V${BB - 12}L${x1 - 12} ${BB}H${x0 + 12}L${x0} ${BB - 12}V${BT + 12}z"/>`
      : `<rect class="${cls}" x="${x0}" y="${BT}" width="${x1 - x0}" height="${BAND}"/>`;
    if (s.glyph) out += `<image href="${s.glyph}" x="${x0 + 8}" y="${SY - 14}" width="28" height="28" style="image-rendering:pixelated"/>`;
    out += fit(s.name.toUpperCase(), 'head', head - 56, 'sp-name', x0 + 44, SY - 3);
    out += fit(s.sub || (s.rows.length + (s.rows.length === 1 ? ' recipe' : ' recipes')), 'mono', head - 56, 'sp-sub', x0 + 44, SY + 13);
    if (s.rows.length) out += `<line class="sp-hrule" x1="${x0 + head - 4}" x2="${x0 + head - 4}" y1="${BT + 6}" y2="${BB - 6}"/>`;
    return out;
  }

  function titleBlock(x, y) {
    const w = 380, rows = D.stations.reduce((n, s) => n + s.rows.length, 0);
    const cell = (k, v, cx, cy) => `<text class="sp-k" x="${cx}" y="${cy}">${k}</text><text class="sp-v" x="${cx}" y="${cy + 17}">${esc(v)}</text>`;
    return `<g class="sp-tb"><rect x="${x}" y="${y}" width="${w}" height="96"/>`
      + `<line x1="${x}" x2="${x + w}" y1="${y + 36}" y2="${y + 36}"/><line x1="${x}" x2="${x + w}" y1="${y + 66}" y2="${y + 66}"/>`
      + `<line x1="${x + 230}" x2="${x + 230}" y1="${y + 36}" y2="${y + 96}"/>`
      + cell('TITLE', D.title + ' recipe sequence', x + 10, y + 13)
      + cell('STATIONS', D.stations.length, x + 10, y + 47) + cell('RECIPES', rows, x + 240, y + 47)
      + cell('SOURCE', 'design/workstations', x + 10, y + 77) + cell('NEXT', D.next || '', x + 240, y + 77)
      + `</g>`;
  }

  const back = [], front = [], stops = [];
  let x = X_START, prev = null;
  for (const s of D.stations) {
    if (prev !== null) x = prev + NODE_GAP;
    const x0 = x;
    const head = Math.round(Math.min(260, Math.max(160, Math.max(tw(s.name.toUpperCase(), 'head'), tw(s.sub, 'mono')) + 60)));
    let cx = x0 + head + 6;
    const parts = [];
    for (const r of s.rows) { parts.push(columnSvg(r, cx)); cx += cols.get(r).w + CG; }
    const x1 = s.rows.length ? cx - CG + 12 : x0 + head;
    if (prev !== null) back.push(`<line class="sp-spine" x1="${prev}" x2="${x0 - 2}" y1="${SY}" y2="${SY}" marker-end="url(#sp-arr)"/>`);
    back.push(...parts.map(p => p.back));
    front.push(nodeSvg(s, x0, x1, head), ...parts.map(p => p.front));
    stops.push({ x: x0, name: s.name });
    prev = x1;
  }
  const bottom = BB + maxOut;
  const H = Math.ceil(Math.max(bottom, SY + 48) + 24);
  // The age ends at the last station: the next one opens past the line.
  const age = prev + 40, tbx = age + 64, W = Math.ceil(tbx + 380 + 24);
  back.push(`<g class="sp-divider"><line x1="${age}" x2="${age}" y1="10" y2="${H - 10}"/><text x="${age + 8}" y="24">${esc((D.next || '').toUpperCase())}</text></g>`);
  back.push(`<g class="sp-divider"><text x="${X_START}" y="24">${esc(D.title.toUpperCase())}</text></g>`);
  const defs = `<defs><marker id="sp-arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path class="sp-arrowhead" d="M0 0L10 5L0 10z"/></marker></defs>`;
  host.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="${W}" height="${H}" role="img" aria-label="${esc(D.title)} recipe tree: every station left to right with its recipes">${defs}${back.join('')}${front.join('')}${titleBlock(tbx, SY - 48)}</svg>`;

  // Station jump bar and drag-to-scroll, when the page gives the chart a scroll box.
  const scroller = host.closest('.sp-scroll');
  if (scroller) {
    let bar = scroller.previousElementSibling;
    if (!bar || !bar.classList.contains('sp-jump')) {
      bar = document.createElement('nav');
      bar.className = 'sp-jump';
      bar.setAttribute('aria-label', 'Jump to a station');
      scroller.before(bar);
    }
    bar.innerHTML = stops.map(s => `<button type="button" data-x="${s.x}">${esc(s.name)}</button>`).join('');
    const still = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    bar.querySelectorAll('button').forEach(b => b.addEventListener('click', () =>
      scroller.scrollTo({ left: Math.max(0, +b.dataset.x - 24), behavior: still ? 'auto' : 'smooth' })));
    if (!scroller.dataset.pan) {
      scroller.dataset.pan = '1';
      let start = null, dragged = false;
      scroller.addEventListener('pointerdown', e => {
        if (e.pointerType !== 'mouse' || e.button !== 0) return;
        start = { x: e.clientX, left: scroller.scrollLeft };
        dragged = false;
      });
      window.addEventListener('pointermove', e => {
        if (!start) return;
        const dx = e.clientX - start.x;
        if (!dragged && Math.abs(dx) > 5) { dragged = true; scroller.classList.add('sp-drag'); }
        if (dragged) scroller.scrollLeft = start.left - dx;
      });
      window.addEventListener('pointerup', () => { start = null; scroller.classList.remove('sp-drag'); });
      // A drag ends with a click on whatever is under the pointer; it must not lock a trace.
      scroller.addEventListener('click', e => { if (dragged) { e.stopPropagation(); dragged = false; } }, true);
    }
  }

  // Tracing: hover or focus lights every chip of the same item (or a tag it fills); a click keeps it lit.
  const svg = host.querySelector('svg'), chips = [...svg.querySelectorAll('.sp-chip')];
  const keysOf = e => e.dataset.k.split(' ');
  const list = set => set ? [...set].join(', ') : 'nowhere in this age';
  const show = keys => {
    svg.classList.add('sp-tracing');
    chips.forEach(e => e.classList.toggle('sp-hot', keysOf(e).some(k => keys.includes(k))));
    if (!trace) return;
    const name = (D.items[keys[0]] || {}).name || keys[0];
    const where = (book) => { const all = new Set(); keys.forEach(k => (book[k] || []).forEach(s => all.add(s))); return all.size ? all : null; };
    trace.innerHTML = `<b>${esc(name)}</b> · gathered or made at ${esc(list(where(made)))} · used at ${esc(list(where(used)))}${host.dataset.locked ? ' · click again to release' : ''}`;
  };
  const clear = () => {
    if (host.dataset.locked) return show(host.dataset.locked.split(' '));
    svg.classList.remove('sp-tracing');
    chips.forEach(e => e.classList.remove('sp-hot'));
    if (trace) trace.textContent = 'Hover an item to see where it is made and used; click to keep it lit.';
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
