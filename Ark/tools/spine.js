// Recipe dependency map, shared by the Recipe Gates chart and the showcase (tools/build_item_flow.py).
// renderSpine(host, trace, data): draws the map into host; trace is the panel that explains the focused item.
// Every item sits in the first column where it can be made (crafting steps from bare hands), grouped under
// the station or tool that makes it. A line joins each ingredient to what it makes, and a dashed line joins
// a station to each group it makes. Hovering or focusing an item lights everything it needs, all the way
// back to bare hands, and everything it unlocks; a click keeps it lit. When host sits in a .sp-scroll, a
// toolbar (find, show removed, fit) goes before it and the map can be dragged sideways.
// Fonts come from the --sp-body, --sp-mono and --sp-display tokens so text measurement matches the CSS.
window.renderSpine = function (host, trace, D) {
  const state = host.spState || (host.spState = { removed: false, fit: false, locked: null });
  const CH = 22, GAP = 5, HEAD = 19, CLUSTER_GAP = 13, COL_GAP = 78, SUBGAP = 26, PAD = 24, CHIP_MAX = 196, ROWS = 15;
  const css = getComputedStyle(host);
  const family = (name, fallback) => (css.getPropertyValue(name) || '').trim() || fallback;
  const body = family('--sp-body', 'system-ui, sans-serif'), mono = family('--sp-mono', 'monospace'),
    display = family('--sp-display', 'sans-serif');
  const FONT = { chip: `500 12px ${body}`, bold: `700 12px ${body}`, mono: `500 9.5px ${mono}`, head: `600 10px ${mono}`,
    gate: `700 13px ${display}` };
  const ctx = document.createElement('canvas').getContext('2d');
  const tw = (t, f) => { ctx.font = FONT[f]; return ctx.measureText(t || '').width; };
  const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const isWorld = id => id.startsWith('w_');
  const nameOf = id => (D.items[id] || { name: id }).name;
  const STATUS = { you: 'your spec', prop: 'proposed here', now: 'in the game', change: 'in the game, changes with the spec',
    cut: 'removed', block: 'blocked until a decision', tbd: 'yours to decide' };

  // Recipes: every row of every station, plus one "any of" recipe per group member.
  const recipes = [];
  D.spine.forEach((n, ni) => n.rows.forEach((r, ri) => recipes.push({ n, r, ni, ri, st: r.st, station: n.item || null,
    ins: r.ins, outs: r.outs, cut: r.st === 'cut' || n.st === 'cut' })));
  for (const [g, members] of Object.entries(D.groups || {}))
    members.forEach((m, k) => recipes.push({ n: null, r: {}, ni: 1e3, ri: k, st: 'now', station: null,
      ins: [{ id: m, n: 1 }], outs: [{ id: g, n: 1 }], cut: false, any: true }));
  const stationOf = new Map(D.spine.filter(n => n.item).map(n => [n.item, n]));

  // Tiers: an item's column is one more than the latest thing its earliest recipe needs (ingredients and
  // station). Inputs nothing makes (a damaged tool, a trim template) are free, like the world.
  const produced = new Set();
  for (const x of recipes) if (!x.cut) for (const o of x.outs) produced.add(o.id);
  const free = id => isWorld(id) || !produced.has(id);
  const tier = {};
  const reqOf = x => {
    let req = 0;
    for (const c of x.ins) {
      if (free(c.id)) continue;
      if (!(c.id in tier)) return null;
      req = Math.max(req, tier[c.id]);
    }
    if (x.station) {
      if (!(x.station in tier)) return null;
      req = Math.max(req, tier[x.station]);
    }
    return req;
  };
  const live = recipes.filter(x => !x.cut);
  for (let changed = true; changed;) {
    changed = false;
    for (const x of live) {
      const req = reqOf(x);
      if (req === null) continue;
      for (const o of x.outs) if (!(o.id in tier) || req + 1 < tier[o.id]) { tier[o.id] = req + 1; changed = true; }
    }
  }
  const cutOnly = new Set();
  if (state.removed) for (const x of recipes) {
    if (!x.cut) continue;
    const req = reqOf(x);
    if (req !== null) for (const o of x.outs) if (!(o.id in tier) || cutOnly.has(o.id)) {
      tier[o.id] = Math.min(tier[o.id] ?? Infinity, req + 1);
      cutOnly.add(o.id);
    }
  }

  // Which recipes draw lines (the ones that set the column) and which are listed as alternatives.
  const recipesFor = {};
  for (const x of recipes) {
    if (x.cut && !state.removed) continue;
    const req = reqOf(x);
    for (const o of x.outs) {
      const primary = req !== null && req + 1 === tier[o.id] && (!x.cut || cutOnly.has(o.id));
      (recipesFor[o.id] ||= []).push({ x, o, req, primary });
    }
  }
  const primaryOf = id => (recipesFor[id] || []).filter(p => p.primary);

  // Nodes: every item with a column. World sources are not drawn; the group header ("ROCK SWORD · KILL")
  // and the panel name them.
  const nodes = new Map();
  for (const id of Object.keys(tier)) {
    const prim = primaryOf(id)[0];
    if (!prim) continue;
    const x = prim.x;
    nodes.set(id, { id, col: tier[id], kind: stationOf.has(id) ? 'station' : x.any ? 'any' : 'item',
      cluster: x.any ? 'any' : x.n.key, st: cutOnly.has(id) ? 'cut' : x.st, order: x.ni * 100 + x.ri, prim });
  }
  if (!state.hashRead) {
    state.hashRead = true;
    const m = /(?:^#|&)trace=([\w-]+)/.exec(location.hash || '');
    if (m && nodes.has(m[1])) state.locked = m[1];
  }

  // Reach: an item waits on a blocked decision when every way to make it passes through a blocked recipe.
  const reach = new Set();
  for (let changed = true; changed;) {
    changed = false;
    for (const x of live) {
      if (x.st === 'block' || (x.station && !reach.has(x.station))) continue;
      if (!x.ins.every(c => free(c.id) || reach.has(c.id))) continue;
      for (const o of x.outs) if (!reach.has(o.id)) { reach.add(o.id); changed = true; }
    }
  }

  // Edges: ingredient lines from every input of a column-setting recipe, station lines per group.
  const edges = [], seen = new Set(), preds = {}, succs = {};
  const link = (a, b) => { (preds[b] ||= new Set()).add(a); (succs[a] ||= new Set()).add(b); };
  for (const nd of nodes.values()) {
    for (const p of primaryOf(nd.id)) {
      for (const c of p.x.ins) {
        if (!nodes.has(c.id) || seen.has(c.id + '>' + nd.id)) continue;
        seen.add(c.id + '>' + nd.id);
        edges.push({ from: c.id, to: nd.id, kind: p.x.any ? 'any' : 'in' });
        link(c.id, nd.id);
      }
      if (p.x.station && nodes.has(p.x.station)) link(p.x.station, nd.id);
    }
  }
  // Materials that feed many recipes (sticks, fiber, rock...) draw fainter lines, so specific chains stand out.
  const fanout = {};
  for (const e of edges) fanout[e.from] = (fanout[e.from] || 0) + 1;

  // Columns of clusters (one cluster per station or tool within a column), then crossing reduction.
  const colIds = [...new Set([...nodes.values()].map(nd => nd.col))].sort((a, b) => a - b);
  const columns = colIds.map(c => {
    const byKey = new Map();
    [...nodes.values()].filter(nd => nd.col === c).sort((a, b) => a.order - b.order).forEach(nd => {
      if (!byKey.has(nd.cluster)) byKey.set(nd.cluster, { key: nd.cluster, chips: [] });
      byKey.get(nd.cluster).chips.push(nd);
    });
    const rank = k => k === 'world' ? -1 : k === 'any' ? 1e4 : D.spine.findIndex(n => n.key === k);
    return { tier: c, clusters: [...byKey.values()].sort((a, b) => rank(a.key) - rank(b.key)) };
  });
  const place = col => {
    let y = 0;
    col.clusters.forEach((cl, k) => {
      if (k) y += CLUSTER_GAP;
      cl.y = y;
      y += HEAD;
      cl.chips.forEach((nd, j) => { nd.y = y + CH / 2; y += CH + (j < cl.chips.length - 1 ? GAP : 0); });
    });
    col.h = y;
    col.clusters.forEach(cl => { cl.yc = cl.y - y / 2; cl.chips.forEach(nd => { nd.yc = nd.y - y / 2; }); });
  };
  columns.forEach(place);
  const mean = v => v.reduce((s, x) => s + x, 0) / v.length;
  const sweep = (order, neighbours) => order.forEach(col => {
    for (const cl of col.clusters) {
      for (const nd of cl.chips) {
        const ys = [...(neighbours[nd.id] || [])].map(id => nodes.get(id)).filter(o => o && o.col !== nd.col).map(o => o.yc);
        nd.bc = ys.length ? mean(ys) : nd.yc;
      }
      cl.chips.sort((a, b) => a.bc - b.bc);
      cl.bc = mean(cl.chips.map(nd => nd.bc));
    }
    col.clusters.sort((a, b) => a.bc - b.bc);
    place(col);
  });
  for (let k = 0; k < 4; k++) { sweep(columns.slice(1), preds); sweep(columns.slice(0, -1).reverse(), succs); }
  sweep(columns.slice(1), preds);

  // Geometry.
  const stepOf = nd => (nd.prim && nd.prim.x.r && nd.prim.x.r.step) || '';
  const noteOf = nd => { const p = nd.prim && primaryOf(nd.id).find(q => q.x.r && q.x.r.noteNo); return p ? p.x.r.noteNo : 0; };
  const labelW = nd => tw(nameOf(nd.id), nd.kind === 'station' ? 'bold' : 'chip');
  const chipW = nd => Math.min(CHIP_MAX, Math.ceil(30 + labelW(nd) + 9 + (stepOf(nd) ? tw(stepOf(nd), 'mono') + 7 : 0) + (noteOf(nd) ? 19 : 0)));
  const headLabel = key => {
    if (key === 'world') return 'GATHERED';
    if (key === 'any') return 'ANY OF';
    const n = D.spine.find(s => s.key === key);
    const verb = n.kind === 'gate' && n.item && n.rows[0] ? ' · ' + n.rows[0].op : '';
    return (n.key === 'inv' ? 'INVENTORY 2×2' : n.name.toUpperCase()) + verb.toUpperCase();
  };
  const gatesIn = col => col.clusters.flatMap(cl => cl.chips).filter(nd => nd.kind === 'station').map(nd => nameOf(nd.id).toUpperCase());
  const headLines = Math.max(1, ...columns.map(col => gatesIn(col).length));
  const TOP = 34 + headLines * 15 + 16;
  columns.forEach(col => {
    const subs = [];
    let cur = null, rows = 0;
    const open = () => { cur = { parts: [] }; subs.push(cur); rows = 0; };
    open();
    for (const cl of col.clusters) {
      const chips = cl.chips.slice();
      while (chips.length) {
        const need = chips.length + 1;
        // Start a fresh sub-column when the group does not fit, unless it is too big for any sub-column.
        if (rows && rows + need > ROWS && (need <= ROWS || rows + 3 > ROWS)) { open(); continue; }
        const take = Math.min(chips.length, Math.max(ROWS - rows - 1, 1));
        cur.parts.push({ key: cl.key, chips: chips.splice(0, take) });
        rows += take + 1;
      }
    }
    col.subs = subs;
  });
  let x = PAD;
  columns.forEach(col => {
    let sx = x;
    col.subs.forEach((sub, k) => {
      if (k) sx += SUBGAP;
      sub.w = Math.ceil(Math.min(CHIP_MAX + 20, Math.max(96, ...sub.parts.flatMap(part =>
        [tw(headLabel(part.key), 'head') + 18, ...part.chips.map(chipW)]))));
      sub.x = sx;
      sx += sub.w;
      let y = 0;
      sub.parts.forEach((part, j) => {
        if (j) y += CLUSTER_GAP;
        part.y = y;
        y += HEAD;
        part.chips.forEach((nd, i) => { nd.y = y + CH / 2; y += CH + (i < part.chips.length - 1 ? GAP : 0); });
      });
      sub.h = y;
    });
    col.x = x;
    col.w = Math.max(sx - x, ...gatesIn(col).map(g => Math.min(tw(g, 'gate'), CHIP_MAX + 20)));
    x += col.w + COL_GAP;
  });
  const contentH = Math.max(...columns.flatMap(col => col.subs.map(sub => sub.h)));
  const bottom = TOP + contentH;
  columns.forEach(col => col.subs.forEach(sub => {
    const off = TOP + (contentH - sub.h) / 2;
    sub.parts.forEach(part => { part.ya = off + part.y; part.chips.forEach(nd => { nd.ya = off + nd.y; nd.x = sub.x; nd.w = chipW(nd); }); });
  }));
  const notes = D.titleBlock.notes || [];
  const W = Math.max(x - COL_GAP + PAD, 820), H = Math.ceil(bottom + 36 + Math.max(96, notes.length * 18) + 16);

  // Drawing.
  const cube = (cx, cy) => `<path class="sp-cube" d="M${cx + 8} ${cy + 1}l7 3.5v8L${cx + 8} ${cy + 16}l-7-3.5v-8zM${cx + 1} ${cy + 4.5}l7 3.5l7-3.5M${cx + 8} ${cy + 8}v8"/>`;
  const icon = (id, ix, iy, size) => {
    const it = D.items[id] || {};
    let s = '';
    if (it.art === 'new') s += `<rect x="${ix}" y="${iy}" width="${size}" height="${size}" fill="url(#sp-hatch)"/>`;
    if (it.icon) s += `<image href="${it.icon}" x="${ix}" y="${iy}" width="${size}" height="${size}" preserveAspectRatio="xMidYMid meet"${it.smooth ? '' : ' style="image-rendering:pixelated"'}/>`;
    else if (it.cube) s += cube(ix, iy);
    if (it.art === 'standin') s += `<path class="sp-standin" d="M${ix + size - 6} ${iy - 1}h7v7z"/>`;
    return s;
  };
  const fitText = (text, font, max, cls, tx, ty) => {
    const adj = tw(text, font) > max ? ` textLength="${Math.floor(max)}" lengthAdjust="spacingAndGlyphs"` : '';
    return `<text class="${cls}" x="${tx}" y="${ty}"${adj}>${esc(text)}</text>`;
  };
  const tipOf = nd => {
    const it = D.items[nd.id] || {}, t = [nameOf(nd.id)];
    if (it.was) t.push('In game today: ' + it.was);
    if (it.art === 'new') t.push('Needs a new sprite');
    if (it.art === 'standin') t.push('Drawn with a borrowed sprite today');
    return t.join('\n');
  };
  const chipSvg = nd => {
    const { x: cx, ya: cy, w } = nd, t = cy - CH / 2;
    const cls = ['sp-chip', 'k-' + nd.kind, 'st-' + nd.st];
    if (nd.kind !== 'world' && !reach.has(nd.id)) cls.push('sp-locked');
    let s = `<g class="${cls.join(' ')}" data-id="${nd.id}" tabindex="0" role="button" aria-label="${esc(nameOf(nd.id))}"><title>${esc(tipOf(nd))}</title>`;
    s += `<rect class="sp-shape" x="${cx}" y="${t}" width="${w}" height="${CH}" rx="${nd.kind === 'world' ? CH / 2 : 3}"/>`;
    s += icon(nd.id, cx + 5, t + 3, 16);
    const step = stepOf(nd), note = noteOf(nd);
    const room = w - 30 - 9 - (step ? tw(step, 'mono') + 7 : 0) - (note ? 19 : 0);
    s += fitText(nameOf(nd.id), nd.kind === 'station' ? 'bold' : 'chip', room, 'sp-label', cx + 26, cy + 4);
    if (cutOnly.has(nd.id)) s += `<line class="sp-strike" x1="${cx + 24}" x2="${cx + w - 4}" y1="${cy}" y2="${cy}"/>`;
    let rx = cx + w - 6;
    if (note) { s += `<g class="sp-nmark"><circle cx="${rx - 7}" cy="${cy}" r="7"/><text x="${rx - 7}" y="${cy + 3}" text-anchor="middle">${note}</text></g>`; rx -= 19; }
    if (step) s += `<text class="sp-step st-${nd.st}" x="${rx}" y="${cy + 3.5}" text-anchor="end">${esc(step)}</text>`;
    return s + '</g>';
  };
  const curve = (x1, y1, x2, y2) => { const dx = Math.max(24, Math.min(110, (x2 - x1) / 2)); return `M${x1} ${y1}C${x1 + dx} ${y1} ${x2 - dx} ${y2} ${x2} ${y2}`; };

  const back = [], front = [];
  columns.forEach((col, k) => {
    if (k % 2) back.push(`<rect class="sp-band" x="${col.x - COL_GAP / 2}" y="${TOP - 14}" width="${col.w + COL_GAP}" height="${contentH + 28}"/>`);
    back.push(`<text class="sp-colno" x="${col.x}" y="24">${col.tier === 0 ? 'START' : col.tier === 1 ? '1 STEP' : col.tier + ' STEPS'}</text>`);
    gatesIn(col).forEach((g, j) => back.push(fitText(g, 'gate', col.w, 'sp-gatename', col.x, 42 + j * 15)));
  });
  back.push(`<line class="sp-colrule" x1="${PAD}" x2="${W - PAD}" y1="30" y2="30"/>`);
  for (const e of edges) {
    const a = nodes.get(e.from), b = nodes.get(e.to), common = e.kind === 'in' && fanout[e.from] >= 7 ? ' e-common' : '';
    back.push(`<path class="sp-edge e-${e.kind}${common}" data-from="${e.from}" data-to="${e.to}" d="${curve(a.x + a.w, a.ya, b.x - 1, b.ya)}"/>`);
  }
  columns.forEach(col => col.subs.forEach(sub => sub.parts.forEach(part => {
    const n = D.spine.find(s => s.key === part.key), st = n && n.item && nodes.get(n.item);
    if (!st) return;
    back.push(`<path class="sp-edge e-station" data-from="${st.id}" data-members="${part.chips.map(nd => nd.id).join(' ')}" d="${curve(st.x + st.w, st.ya, sub.x - 6, part.ya + HEAD / 2 - 3)}"/>`);
  })));
  columns.forEach(col => col.subs.forEach(sub => sub.parts.forEach(part => {
    front.push(fitText(headLabel(part.key), 'head', sub.w - 2, 'sp-head', sub.x + 1, part.ya + 11));
    part.chips.forEach(nd => front.push(chipSvg(nd)));
  })));

  const tb = D.titleBlock, tbx = W - 404, tby = bottom + 30;
  const cell = (k, v, cx, cy) => `<text class="sp-k" x="${cx}" y="${cy}">${k}</text><text class="sp-v" x="${cx}" y="${cy + 17}">${esc(v)}</text>`;
  const title = `<g class="sp-tb"><rect x="${tbx}" y="${tby}" width="380" height="96"/>`
    + `<line x1="${tbx}" x2="${tbx + 380}" y1="${tby + 36}" y2="${tby + 36}"/><line x1="${tbx}" x2="${tbx + 380}" y1="${tby + 66}" y2="${tby + 66}"/>`
    + `<line x1="${tbx + 230}" x2="${tbx + 230}" y1="${tby + 36}" y2="${tby + 96}"/>`
    + cell('TITLE', tb.title, tbx + 10, tby + 13) + cell('PROJECT', tb.project, tbx + 10, tby + 47) + cell('REV', tb.rev, tbx + 240, tby + 47)
    + cell('SOURCE', tb.source, tbx + 10, tby + 77) + cell('DATE', tb.date, tbx + 240, tby + 77) + `</g>`
    + notes.map((t, k) => `<text class="sp-tbnote" x="${PAD}" y="${tby + 18 + k * 18}">${esc(t)}</text>`).join('');
  const defs = `<defs><pattern id="sp-hatch" width="4" height="4" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect class="sp-hatch-bg" width="4" height="4"/><line class="sp-hatch-line" x1="0" y1="0" x2="0" y2="4"/></pattern></defs>`;
  host.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="${W}" height="${H}" role="img" aria-label="Recipe dependency map: every item, what it needs and what it unlocks">${defs}${back.join('')}${front.join('')}${title}</svg>`;
  const svg = host.querySelector('svg');

  // Toolbar and drag-to-scroll, when the page gives the map a scroll box.
  const scroller = host.closest('.sp-scroll');
  if (scroller) {
    scroller.classList.toggle('sp-fit', state.fit);
    let bar = scroller.previousElementSibling;
    if (bar && bar.classList.contains('sp-jump')) { bar.remove(); bar = scroller.previousElementSibling; }
    if (!bar || !bar.classList.contains('sp-tools')) {
      bar = document.createElement('div');
      bar.className = 'sp-tools';
      scroller.before(bar);
    }
    const options = [...nodes.values()].filter(nd => nd.kind !== 'world').sort((a, b) => nameOf(a.id).localeCompare(nameOf(b.id)))
      .map(nd => `<option value="${nd.id}"${state.locked === nd.id ? ' selected' : ''}>${esc(nameOf(nd.id))}</option>`).join('');
    bar.innerHTML = `<label>Trace <select class="sp-find"><option value="">an item…</option>${options}</select></label>`
      + `<label><input type="checkbox" class="sp-removed"${state.removed ? ' checked' : ''}> Show removed</label>`
      + `<button type="button" class="sp-fitbtn" aria-pressed="${state.fit}">Fit to width</button>`
      + `<span class="sp-hint">Drag sideways or scroll with Shift</span>`;
    bar.querySelector('.sp-find').addEventListener('change', ev => {
      state.locked = ev.target.value || null;
      state.locked ? show(state.locked) : clear();
      const nd = state.locked && nodes.get(state.locked);
      if (nd && !state.fit) scroller.scrollTo({ left: Math.max(0, nd.x - scroller.clientWidth / 3), behavior: 'smooth' });
    });
    bar.querySelector('.sp-removed').addEventListener('change', ev => { state.removed = ev.target.checked; window.renderSpine(host, trace, D); });
    bar.querySelector('.sp-fitbtn').addEventListener('click', () => { state.fit = !state.fit; window.renderSpine(host, trace, D); });
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

  // Tracing.
  const walk = (start, next) => {
    const out = new Set(), stack = [start];
    while (stack.length) for (const id of next[stack.pop()] || []) if (!out.has(id)) { out.add(id); stack.push(id); }
    return out;
  };
  const recipeText = p => {
    const x = p.x;
    if (x.any) return 'any of: ' + D.groups[p.o.id].map(nameOf).join(', ');
    const ins = x.ins.map(c => (c.n > 1 ? c.n + '× ' : '') + nameOf(c.id) + (c.ch ? ' (' + c.ch + ')' : '')).join(' + ');
    const where = x.n.key === 'hands' ? 'Gathered' : x.n.key === 'inv' ? 'In the 2×2 inventory'
      : !x.n.item ? x.n.name : (x.n.kind === 'gate' ? 'With the ' : 'At the ') + x.n.name;
    const out = (p.o.n > 1 ? ' → ' + p.o.n + '×' : '') + (p.o.ch ? ' (' + p.o.ch + ')' : '');
    return `${where}: ${ins}${x.r.op ? ' · ' + x.r.op : ''}${out}${p.o.val ? ' — ' + p.o.val : ''}`;
  };
  const list = (ids, max) => {
    const names = ids.map(nameOf);
    return names.length > max ? names.slice(0, max).join(', ') + ` and ${names.length - max} more` : names.join(', ');
  };
  const show = id => {
    const anc = walk(id, Object.fromEntries(Object.entries(preds).map(([k, v]) => [k, [...v]])));
    const desc = walk(id, Object.fromEntries(Object.entries(succs).map(([k, v]) => [k, [...v]])));
    svg.classList.add('sp-tracing');
    svg.querySelectorAll('.sp-chip').forEach(e => {
      const k = e.dataset.id;
      e.classList.toggle('sp-hot', k === id);
      e.classList.toggle('sp-up', anc.has(k));
      e.classList.toggle('sp-down', desc.has(k));
    });
    svg.querySelectorAll('.sp-edge').forEach(e => {
      const from = e.dataset.from, tos = e.dataset.to ? [e.dataset.to] : e.dataset.members.split(' ');
      e.classList.toggle('sp-e-up', anc.has(from) && tos.some(t => t === id || anc.has(t)));
      e.classList.toggle('sp-e-down', (from === id || desc.has(from)) && tos.some(t => desc.has(t)));
    });
    if (!trace) return;
    const nd = nodes.get(id), it = D.items[id] || {};
    const lines = [];
    let head = `<b>${esc(nameOf(id))}</b>`;
    if (it.was) head += ` <span class="sp-meta">today: ${esc(it.was)}</span>`;
    if (nd.kind === 'world') head += ' <span class="sp-meta">gathered from the world</span>';
    else head += ` <span class="sp-meta">${nd.col === 1 ? '1 step' : nd.col + ' steps'} from bare hands · ${esc(STATUS[nd.st] || '')}${reach.has(id) ? '' : ' · waits on a blocked decision'}</span>`;
    lines.push(head);
    if (nd.kind === 'world') lines.push('Gives: ' + esc(list([...(succs[id] || [])], 8)));
    else {
      primaryOf(id).forEach(p => lines.push(esc(recipeText(p))));
      const alts = (recipesFor[id] || []).filter(p => !p.primary && p.req !== null);
      if (alts.length) lines.push('Also: ' + esc(alts.map(recipeText).join('; ')));
      const gates = [...anc].filter(k => stationOf.has(k) && k in tier).sort((a, b) => tier[a] - tier[b]).map(nameOf);
      lines.push(gates.length ? 'Gates on the way: ' + esc(gates.join(' → ')) : 'Needs no station or tool.');
      const items = [...anc].filter(k => !isWorld(k) && nodes.get(k).kind !== 'any');
      const uses = [...(succs[id] || [])];
      lines.push(`Needs ${items.length} item${items.length === 1 ? '' : 's'} back to bare hands · `
        + (uses.length ? `used by ${esc(list(uses, 6))}` + (desc.size > uses.length ? `, ${desc.size} things down the line` : '') : 'used by nothing yet'));
      const note = primaryOf(id).map(p => p.x.r && p.x.r.noteNo).find(Boolean);
      if (note) lines.push(`<span class="sp-meta">Note ${note}: ${esc(D.notes[note - 1])}</span>`);
    }
    if (state.locked === id) lines.push('<span class="sp-meta">Pinned · click it again to release</span>');
    trace.innerHTML = lines.map(l => `<span class="sp-line">${l}</span>`).join('');
  };
  const clear = () => {
    if (state.locked && nodes.has(state.locked)) return show(state.locked);
    svg.classList.remove('sp-tracing');
    svg.querySelectorAll('.sp-hot, .sp-up, .sp-down, .sp-e-up, .sp-e-down').forEach(e => e.classList.remove('sp-hot', 'sp-up', 'sp-down', 'sp-e-up', 'sp-e-down'));
    if (trace) trace.innerHTML = ['Hover or tap an item: green is everything it needs, all the way back to bare hands; blue is everything it unlocks. Click to pin it.',
      'Columns count crafting steps from bare hands. Items sit under the station or tool that makes them, and a dashed line leads from each station to its groups.',
      'Faded items wait on a blocked decision further back (Q1: the rock pickaxe cannot mine metal ore yet).']
      .map(l => `<span class="sp-line">${l}</span>`).join('');
  };
  for (const e of svg.querySelectorAll('.sp-chip')) {
    const id = e.dataset.id;
    e.addEventListener('mouseenter', () => show(id));
    e.addEventListener('mouseleave', clear);
    e.addEventListener('focus', () => show(id));
    e.addEventListener('blur', clear);
    const toggle = () => {
      state.locked = state.locked === id ? null : id;
      const find = scroller && scroller.previousElementSibling && scroller.previousElementSibling.querySelector('.sp-find');
      if (find) find.value = state.locked || '';
      state.locked ? show(id) : clear();
    };
    e.addEventListener('click', toggle);
    e.addEventListener('keydown', ev => { if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); toggle(); } });
  }
  clear();
};
