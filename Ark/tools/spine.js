// Recipe dependency map, shared by the Recipe Gates chart and the showcase (tools/build_item_flow.py).
// renderSpine(host, trace, data): draws the map into host; trace is the panel that explains the focused item.
// Four stage columns (D.stages: Primitives, Early, Mid, End). Every item sits in the earliest stage where it can
// be made, in a card for the station or tool that makes it. A station's recipes start at its stage (spine node
// .stage), and an item is never earlier than its ingredients, its station or its floor (D.itemStage).
// A coloured arrow leads from each workbench or tool to every card it opens, in that workbench's colour (node
// .wb: the --sp-wbN tokens of spine.css, 0 is neutral). Ingredient lines, in the colour of the workbench that
// uses them, show for the hovered item (everything it needs back to bare hands and everything it unlocks), or
// all at once with "All ingredient lines". The map fills the width of its box and redraws when that changes;
// when host sits in a .sp-scroll, a toolbar (trace, show removed, all lines) goes before it.
// Fonts come from the --sp-body, --sp-mono and --sp-display tokens so text measurement matches the CSS.
window.renderSpine = function (host, trace, D) {
  const state = host.spState || (host.spState = { removed: false, lines: false, locked: null });
  const CH = 22, ROWG = 5, CGAP = 10, CPAD = 10, HEADH = 27, CARD_GAP = 14, PAD = 12, LANE = 7,
    CHIP_MIN = 150, CHIP_FIT = 212, CHIP_MAX = 250, HEADER = 58;
  const css = getComputedStyle(host);
  const family = (name, fallback) => (css.getPropertyValue(name) || '').trim() || fallback;
  const body = family('--sp-body', 'system-ui, sans-serif'), mono = family('--sp-mono', 'monospace'),
    display = family('--sp-display', 'sans-serif');
  const FONT = { chip: `500 12px ${body}`, bold: `700 12px ${body}`, mono: `500 9.5px ${mono}`, head: `600 10px ${mono}`,
    stage: `700 17px ${display}`, sub: `500 10px ${mono}`, tb: `600 14px ${display}` };
  const ctx = document.createElement('canvas').getContext('2d');
  const tw = (t, f) => { ctx.font = FONT[f]; return ctx.measureText(t || '').width; };
  const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const isWorld = id => id.startsWith('w_');
  const nameOf = id => (D.items[id] || { name: id }).name;
  const STATUS = { you: 'your spec', prop: 'proposed here', now: 'in the game', change: 'in the game, changes with the spec',
    cut: 'removed', block: 'blocked until a decision', tbd: 'yours to decide' };
  const STAGES = D.stages || [{ name: 'Primitives' }, { name: 'Early' }, { name: 'Mid' }, { name: 'End' }];
  const wbColour = k => k ? `var(--sp-wb${k})` : 'var(--sp-ink2)';

  // Recipes: every row of every station, plus one "any of" recipe per group member.
  const recipes = [];
  D.spine.forEach((n, ni) => n.rows.forEach((r, ri) => recipes.push({ n, r, ni, ri, st: r.st, station: n.item || null,
    ins: r.ins, outs: r.outs, cut: r.st === 'cut' || n.st === 'cut', base: n.stage || 0 })));
  for (const [g, members] of Object.entries(D.groups || {}))
    members.forEach((m, k) => recipes.push({ n: null, r: {}, ni: 1e3, ri: k, st: 'now', station: null,
      ins: [{ id: m, n: 1 }], outs: [{ id: g, n: 1 }], cut: false, any: true, base: 0 }));
  const stationOf = new Map(D.spine.filter(n => n.item).map(n => [n.item, n]));
  const floorOf = id => (D.itemStage || {})[id] || 0;

  // Steps (tier) and stage: the cheapest recipe sets each. Steps are one more than the latest ingredient or
  // station; the stage is the latest of the recipe's own stage, its ingredients and its station. Inputs nothing
  // makes (a damaged tool, a trim template) are free, like the world.
  const produced = new Set();
  for (const x of recipes) if (!x.cut) for (const o of x.outs) produced.add(o.id);
  const free = id => isWorld(id) || !produced.has(id);
  const tier = {}, stage = {};
  const need = (x, map, start) => {
    let req = start;
    for (const c of x.ins) {
      if (free(c.id)) continue;
      if (!(c.id in map)) return null;
      req = Math.max(req, map[c.id]);
    }
    if (x.station) {
      if (!(x.station in map)) return null;
      req = Math.max(req, map[x.station]);
    }
    return req;
  };
  const reqOf = x => need(x, tier, 0), stageOf = (x, o) => { const s = need(x, stage, x.base); return s === null ? null : Math.max(s, floorOf(o.id)); };
  const settle = (list, only) => {
    for (let changed = true; changed;) {
      changed = false;
      for (const x of list) {
        const req = reqOf(x);
        if (req !== null) for (const o of x.outs) if ((!only || only(o.id)) && (!(o.id in tier) || req + 1 < tier[o.id])) { tier[o.id] = req + 1; changed = true; }
        for (const o of x.outs) {
          const s = stageOf(x, o);
          if (s !== null && (!only || only(o.id)) && (!(o.id in stage) || s < stage[o.id])) { stage[o.id] = s; changed = true; }
        }
      }
    }
  };
  const live = recipes.filter(x => !x.cut);
  settle(live);
  const cutOnly = new Set();
  if (state.removed) {
    const cuts = recipes.filter(x => x.cut);
    for (const x of cuts) for (const o of x.outs) if (!(o.id in tier)) cutOnly.add(o.id);
    settle(cuts, id => cutOnly.has(id));
  }

  // The recipe that places each item (lowest stage, then fewest steps) and the alternatives.
  const recipesFor = {};
  for (const x of recipes) {
    if (x.cut && !state.removed) continue;
    for (const o of x.outs) {
      const req = reqOf(x), s = stageOf(x, o);
      const fits = req !== null && s !== null && s === stage[o.id] && (!x.cut || cutOnly.has(o.id));
      (recipesFor[o.id] ||= []).push({ x, o, req, s, fits });
    }
  }
  const primOf = {};
  for (const [id, list] of Object.entries(recipesFor)) {
    const best = list.filter(p => p.fits).sort((a, b) => a.req - b.req || (a.x.ni * 100 + a.x.ri) - (b.x.ni * 100 + b.x.ri))[0];
    if (best) primOf[id] = best;
  }

  // Nodes: every item with a stage. World sources are not drawn; the card header and the panel name them.
  const nodes = new Map();
  for (const id of Object.keys(stage)) {
    const prim = primOf[id];
    if (!prim || !(id in tier)) continue;
    const x = prim.x;
    nodes.set(id, { id, stage: stage[id], col: tier[id], kind: stationOf.has(id) ? 'station' : x.any ? 'any' : 'item',
      cluster: x.any ? 'any' : x.n.key, wb: x.any ? 0 : x.n.wb || 0, st: cutOnly.has(id) ? 'cut' : x.st,
      order: x.ni * 100 + x.ri, prim });
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

  // Ingredient links from the placing recipe; the station counts as a link for tracing.
  const edges = [], preds = {}, succs = {};
  const link = (a, b) => { (preds[b] ||= new Set()).add(a); (succs[a] ||= new Set()).add(b); };
  for (const nd of nodes.values()) {
    const x = nd.prim.x, seen = new Set();
    for (const c of x.ins) {
      if (!nodes.has(c.id) || seen.has(c.id)) continue;
      seen.add(c.id);
      edges.push({ from: c.id, to: nd.id, kind: x.any ? 'any' : 'in', wb: nd.wb });
      link(c.id, nd.id);
    }
    if (x.station && nodes.has(x.station)) link(x.station, nd.id);
  }
  const fanout = {};
  for (const e of edges) fanout[e.from] = (fanout[e.from] || 0) + 1;

  // Cards: one per stage and station, chips in spec order with the stations they make last (next to the gutter
  // their arrows leave by).
  const spineIndex = key => key === 'any' ? 1e4 : D.spine.findIndex(n => n.key === key);
  const cards = new Map();
  for (const nd of nodes.values()) {
    const key = nd.stage + '|' + nd.cluster;
    if (!cards.has(key)) {
      const n = nd.cluster === 'any' ? null : D.spine.find(s => s.key === nd.cluster);
      cards.set(key, { key: nd.cluster, stage: nd.stage, n, wb: nd.wb, chips: [] });
    }
    cards.get(key).chips.push(nd);
  }
  for (const card of cards.values()) {
    card.chips.sort((a, b) => (a.kind === 'station') - (b.kind === 'station') || a.order - b.order);
    const src = card.n && card.n.item && nodes.get(card.n.item);
    card.src = src && src.stage <= card.stage ? src : null;
  }
  const byStage = STAGES.map((_, s) => [...cards.values()].filter(c => c.stage === s));

  // Gutters: the arrow lanes between columns, sized to the arrows that pass. An arrow from a station to a card
  // in a later column runs down the gutter after the station's column (and, when it skips a column, along the
  // channel over the cards and down the gutter before the card's); one to a card in its own column runs down
  // the gutter after that column and enters the card from the right.
  const gates = [...cards.values()].filter(c => c.src).map(c => ({ card: c, src: c.src }));
  const nG = STAGES.length, gutterCount = Array.from({ length: nG }, () => ({ same: 0, cross: 0 }));
  let longCount = 0;
  for (const g of gates) {
    const a = g.src.stage, b = g.card.stage;
    if (a === b) gutterCount[a].same++;
    else {
      gutterCount[a].cross++;
      if (b > a + 1) { gutterCount[b - 1].cross++; g.lane = longCount++; }
    }
  }
  const gutterW = gutterCount.map((g, k) => k === nG - 1 ? (g.same ? 18 + g.same * LANE : 4) : Math.max(40, 22 + (g.same + g.cross) * LANE));

  // Chip width, then chip columns per stage: add a chip column to the tallest stage while the width allows.
  const stepOf = nd => (nd.prim.x.r && nd.prim.x.r.step) || '';
  const noteOf = nd => (nd.prim.x.r && nd.prim.x.r.noteNo) || 0;
  const labelFont = nd => nd.kind === 'station' ? 'bold' : 'chip';
  const chipNeed = nd => Math.ceil(30 + tw(nameOf(nd.id), labelFont(nd)) + 9 + (stepOf(nd) ? tw(stepOf(nd), 'mono') + 7 : 0) + (noteOf(nd) ? 19 : 0));
  let chip = Math.min(CHIP_FIT, Math.max(CHIP_MIN, ...[...nodes.values()].map(chipNeed)));
  const scroller = host.closest('.sp-scroll');
  const avail = (scroller || host).clientWidth || host.parentElement.clientWidth || 1280;
  state.width = avail;
  const colW = k => 2 * CPAD + k * chip + (k - 1) * CGAP;
  const inner = avail - 2 * PAD - gutterW.reduce((s, w) => s + w, 0);
  const cardH = (card, k) => { const rows = Math.ceil(card.chips.length / k); return HEADH + rows * CH + (rows - 1) * ROWG + CPAD; };
  const stageH = (s, k) => byStage[s].reduce((h, c, j) => h + (j ? CARD_GAP : 0) + cardH(c, k), 0);
  const ks = STAGES.map(() => 1);
  for (;;) {
    const s = ks.map((k, j) => j).sort((a, b) => stageH(b, ks[b]) - stageH(a, ks[a]))[0];
    const wide = ks.reduce((w, k, j) => w + colW(j === s ? k + 1 : k), 0);
    if (wide > inner || stageH(s, ks[s] + 1) >= stageH(s, ks[s])) break;
    ks[s]++;
  }
  const chipCols = ks.reduce((a, b) => a + b, 0);
  const spare = inner - ks.reduce((w, k) => w + colW(k), 0);
  if (spare > 0) chip = Math.min(CHIP_MAX, chip + Math.floor(spare / chipCols));
  const slack = Math.max(0, inner - ks.reduce((w, k) => w + colW(k), 0));
  if (slack > 0) for (let k = 0; k < nG - 1; k++) gutterW[k] += Math.min(48, slack / (nG - 1));

  // Columns left to right. Cards opened from an earlier column come first, in the order of their stations,
  // then cards with no station arrow, then cards opened from inside the column, below their station.
  const channel = longCount ? longCount * LANE + 10 : 0;
  const TOP = HEADER + channel;
  const columns = [];
  let cx = PAD;
  STAGES.forEach((stg, s) => {
    const k = ks[s], w = colW(k);
    const col = { s, x: cx, w, k, cards: [] };
    const list = byStage[s];
    const earlier = list.filter(c => c.src && c.src.stage < s).sort((a, b) => a.src.ya - b.src.ya);
    const plain = list.filter(c => !c.src && c.key !== 'any').sort((a, b) => spineIndex(a.key) - spineIndex(b.key));
    let same = list.filter(c => c.src && c.src.stage === s);
    const any = list.filter(c => c.key === 'any');
    let y = TOP;
    const put = card => {
      if (col.cards.length) y += CARD_GAP;
      card.x = col.x; card.y = y; card.w = w; card.h = cardH(card, k);
      const rows = Math.ceil(card.chips.length / k);
      card.chips.forEach((nd, i) => {
        nd.x = card.x + CPAD + Math.floor(i / rows) * (chip + CGAP);
        nd.w = chip;
        nd.ya = card.y + HEADH + (i % rows) * (CH + ROWG) + CH / 2;
      });
      y += card.h;
      col.cards.push(card);
    };
    earlier.forEach(put);
    plain.forEach(put);
    while (same.length) {
      const ready = same.filter(c => c.src.ya !== undefined).sort((a, b) => a.src.ya - b.src.ya);
      const next = ready[0] || same[0];
      put(next);
      same = same.filter(c => c !== next);
    }
    any.forEach(put);
    col.h = y - TOP;
    columns.push(col);
    cx += w + gutterW[s];
  });
  const W = Math.ceil(cx + PAD);
  const contentH = Math.max(0, ...columns.map(c => c.h));
  const bottom = TOP + contentH;

  // Arrow lanes. In each gutter, arrows that turn back into their own column take the lanes nearest it, inner
  // loops first; then arrows that climb, the highest first; arrows that drop take lanes from the far side, the
  // highest first. That way arrows running the same way never cross.
  const gutterLeft = s => columns[s].x + columns[s].w, gutterRight = s => s < nG - 1 ? columns[s + 1].x : gutterLeft(s) + gutterW[s];
  const lanes = Array.from({ length: nG }, () => ({ same: [], up: [], down: [] }));
  const cardY = c => c.y + HEADH / 2 - 1, chanY = g => TOP - 8 - g.lane * LANE;
  const vertical = (s, g, set, y1, y2) => (y2 < y1 ? lanes[s].up : lanes[s].down).push({ g, set, y1, y2 });
  for (const g of gates) {
    const a = g.src.stage, b = g.card.stage;
    if (a === b) lanes[a].same.push({ g, set: 'sameX', y1: g.src.ya, y2: cardY(g.card) });
    else if (b === a + 1) vertical(a, g, 'outX', g.src.ya, cardY(g.card));
    else { vertical(a, g, 'outX', g.src.ya, chanY(g)); vertical(b - 1, g, 'downX', chanY(g), cardY(g.card)); }
  }
  lanes.forEach((l, s) => {
    const span = e => Math.abs(e.y2 - e.y1);
    l.same.sort((p, q) => span(p) - span(q)).forEach((e, i) => { e.g[e.set] = gutterLeft(s) + 10 + i * LANE; });
    l.up.sort((p, q) => p.y1 - q.y1).forEach((e, i) => { e.g[e.set] = gutterLeft(s) + 10 + (l.same.length + i) * LANE; });
    l.down.sort((p, q) => p.y1 - q.y1).forEach((e, i) => { e.g[e.set] = gutterRight(s) - 10 - i * LANE; });
  });
  const ortho = pts => {
    pts = pts.filter((p, i) => !i || p[0] !== pts[i - 1][0] || p[1] !== pts[i - 1][1]);
    let d = `M${pts[0][0]} ${pts[0][1]}`;
    for (let i = 1; i < pts.length - 1; i++) {
      const [px, py] = pts[i - 1], [x, y] = pts[i], [nx, ny] = pts[i + 1];
      const l1 = Math.hypot(x - px, y - py), l2 = Math.hypot(nx - x, ny - y), r = Math.min(6, l1 / 2, l2 / 2);
      d += `L${x - (x - px) / l1 * r} ${y - (y - py) / l1 * r}Q${x} ${y} ${x + (nx - x) / l2 * r} ${y + (ny - y) / l2 * r}`;
    }
    const [lx, ly] = pts[pts.length - 1];
    return d + `L${lx} ${ly}`;
  };
  const tip = (x, y, dir) => `M${x} ${y}l${-7 * dir} -4v8z`;
  const gateSvg = g => {
    const s = g.src, c = g.card, ty = cardY(c), sx = s.x + s.w, pts = [[sx, s.ya]];
    let end, dir;
    if (s.stage === c.stage) {
      pts.push([g.sameX, s.ya], [g.sameX, ty], [c.x + c.w, ty]);
      end = c.x + c.w; dir = -1;
    } else {
      pts.push([g.outX, s.ya]);
      if (c.stage > s.stage + 1) {
        const cy = chanY(g);
        pts.push([g.outX, cy], [g.downX, cy], [g.downX, ty]);
      } else pts.push([g.outX, ty]);
      pts.push([c.x - 1, ty]);
      end = c.x - 1; dir = 1;
    }
    const members = c.chips.map(nd => nd.id).join(' ');
    return `<g class="sp-gate" data-from="${s.id}" data-members="${members}" style="--wb:${wbColour(c.wb)}">`
      + `<path class="sp-gate-line" d="${ortho(pts)}"/><path class="sp-gate-tip" d="${tip(end, ty, dir)}"/></g>`;
  };

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
  const fitText = (text, font, max, cls, tx, ty, extra = '') => {
    let t = text;
    if (tw(t, font) > max * 1.12) { while (t.length > 2 && tw(t + '…', font) > max * 1.12) t = t.slice(0, -1); t = t.trimEnd() + '…'; }
    const adj = tw(t, font) > max ? ` textLength="${Math.floor(max)}" lengthAdjust="spacingAndGlyphs"` : '';
    return `<text class="${cls}" x="${tx}" y="${ty}"${adj}${extra}>${esc(t)}</text>`;
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
    if (!reach.has(nd.id)) cls.push('sp-locked');
    const own = stationOf.get(nd.id);
    let s = `<g class="${cls.join(' ')}" data-id="${nd.id}" tabindex="0" role="button" aria-label="${esc(nameOf(nd.id))}"><title>${esc(tipOf(nd))}</title>`;
    s += `<rect class="sp-shape" x="${cx}" y="${t}" width="${w}" height="${CH}" rx="3"/>`;
    if (own) s += `<rect class="sp-own" x="${cx + 1}" y="${t + 1}" width="3.5" height="${CH - 2}" style="fill:${wbColour(own.wb)}"/>`;
    s += icon(nd.id, cx + 6, t + 3, 16);
    const step = stepOf(nd), note = noteOf(nd);
    const room = w - 31 - 8 - (step ? tw(step, 'mono') + 7 : 0) - (note ? 19 : 0);
    s += fitText(nameOf(nd.id), labelFont(nd), room, 'sp-label', cx + 27, cy + 4);
    if (cutOnly.has(nd.id)) s += `<line class="sp-strike" x1="${cx + 25}" x2="${cx + w - 4}" y1="${cy}" y2="${cy}"/>`;
    let rx = cx + w - 6;
    if (note) { s += `<g class="sp-nmark"><circle cx="${rx - 7}" cy="${cy}" r="7"/><text x="${rx - 7}" y="${cy + 3}" text-anchor="middle">${note}</text></g>`; rx -= 19; }
    if (step) s += `<text class="sp-step st-${nd.st}" x="${rx}" y="${cy + 3.5}" text-anchor="end">${esc(step)}</text>`;
    return s + '</g>';
  };
  const headLabel = card => {
    if (card.key === 'any') return 'ANY OF';
    const n = card.n;
    const verb = n.kind === 'gate' && n.item && n.rows[0] ? ' · ' + n.rows[0].op : '';
    return (n.key === 'inv' ? 'INVENTORY 2×2' : n.name.toUpperCase()) + verb.toUpperCase();
  };
  const cardSvg = card => {
    const { x, y, w, h } = card, n = card.n;
    const glyph = n && ((n.item && D.items[n.item] && D.items[n.item].icon) || (D.glyphs || {})[n.glyph]);
    let s = `<g class="sp-card" style="--wb:${wbColour(card.wb)}"><rect class="sp-cardbox" x="${x}" y="${y}" width="${w}" height="${h}" rx="5"/>`;
    s += `<path class="sp-cardbar" d="M${x + 5} ${y}h${w - 10}a5 5 0 0 1 5 5v1H${x}v-1a5 5 0 0 1 5-5z"/>`;
    let hx = x + CPAD;
    if (glyph) { s += `<image href="${glyph}" x="${hx}" y="${y + 9}" width="13" height="13" style="image-rendering:pixelated"/>`; hx += 18; }
    else { s += `<rect class="sp-cardkey" x="${hx}" y="${y + 11}" width="9" height="9" rx="2"/>`; hx += 15; }
    const count = String(card.chips.length);
    s += fitText(headLabel(card), 'head', w - (hx - x) - CPAD - tw(count, 'head') - 8, 'sp-head', hx, y + 19.5);
    s += `<text class="sp-count" x="${x + w - CPAD}" y="${y + 19.5}" text-anchor="end">${count}</text>`;
    return s + '</g>';
  };
  const curve = (a, b) => {
    const x1 = a.x + a.w, y1 = a.ya, x2 = b.x - 1, y2 = b.ya;
    const dx = x2 > x1 + 20 ? Math.max(24, Math.min(110, (x2 - x1) / 2)) : 46;
    return `M${x1} ${y1}C${x1 + dx} ${y1} ${x2 - dx} ${y2} ${x2} ${y2}`;
  };

  const back = [], mid = [], front = [];
  columns.forEach((col, s) => {
    const stg = STAGES[s], items = col.cards.reduce((n, c) => n + c.chips.length, 0);
    back.push(`<rect class="sp-band" x="${col.x - 6}" y="${HEADER - 10}" width="${col.w + 12}" height="${bottom - HEADER + 22}" rx="8"/>`);
    back.push(fitText(stg.name, 'stage', col.w - 60, 'sp-stage', col.x, 22));
    back.push(`<text class="sp-colno" x="${col.x + col.w}" y="22" text-anchor="end">${items} item${items === 1 ? '' : 's'}</text>`);
    if (stg.sub) back.push(fitText(stg.sub, 'sub', col.w, 'sp-stagesub', col.x, 39));
    if (s < nG - 1) {
      const ax = col.x + col.w + gutterW[s] / 2;
      back.push(`<path class="sp-stagearrow" d="M${ax - 9} 17h16m-5-5 5 5-5 5"/>`);
    }
  });
  columns.forEach(col => col.cards.forEach(card => back.push(cardSvg(card))));
  gates.forEach(g => mid.push(gateSvg(g)));
  for (const e of edges) {
    const a = nodes.get(e.from), b = nodes.get(e.to), common = e.kind === 'in' && fanout[e.from] >= 7 ? ' e-common' : '';
    mid.push(`<path class="sp-edge e-${e.kind}${common}" data-from="${e.from}" data-to="${e.to}" style="--wb:${wbColour(e.wb)}" d="${curve(a, b)}"/>`);
  }
  columns.forEach(col => col.cards.forEach(card => card.chips.forEach(nd => front.push(chipSvg(nd)))));

  const notes = D.titleBlock.notes || [];
  const tb = D.titleBlock, stack = W < 1180;
  const tbx = stack ? PAD : W - 404, notesY = bottom + 34, tby = stack ? notesY + notes.length * 18 + 8 : bottom + 30;
  const H = Math.ceil(stack ? tby + 96 + 12 : bottom + 36 + Math.max(96, notes.length * 18) + 16);
  const cell = (k, v, cx, cy, max) => `<text class="sp-k" x="${cx}" y="${cy}">${k}</text>` + fitText(v, 'tb', max, 'sp-v', cx, cy + 17);
  const title = `<g class="sp-tb"><rect x="${tbx}" y="${tby}" width="380" height="96"/>`
    + `<line x1="${tbx}" x2="${tbx + 380}" y1="${tby + 36}" y2="${tby + 36}"/><line x1="${tbx}" x2="${tbx + 380}" y1="${tby + 66}" y2="${tby + 66}"/>`
    + `<line x1="${tbx + 230}" x2="${tbx + 230}" y1="${tby + 36}" y2="${tby + 96}"/>`
    + cell('TITLE', tb.title, tbx + 10, tby + 13, 360) + cell('PROJECT', tb.project, tbx + 10, tby + 47, 212) + cell('REV', tb.rev, tbx + 240, tby + 47, 130)
    + cell('SOURCE', tb.source, tbx + 10, tby + 77, 212) + cell('DATE', tb.date, tbx + 240, tby + 77, 130) + `</g>`
    + notes.map((t, k) => `<text class="sp-tbnote" x="${PAD}" y="${notesY + 4 + k * 18}">${esc(t)}</text>`).join('');
  const defs = `<defs><pattern id="sp-hatch" width="4" height="4" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect class="sp-hatch-bg" width="4" height="4"/><line class="sp-hatch-line" x1="0" y1="0" x2="0" y2="4"/></pattern></defs>`;
  host.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" class="${state.lines ? 'sp-lines' : ''}" viewBox="0 0 ${W} ${H}" width="${W}" height="${H}" role="img" aria-label="Recipe dependency map in four stages: every item, the workbench that makes it, what it needs and what it unlocks">${defs}${back.join('')}${mid.join('')}${front.join('')}${title}</svg>`;
  const svg = host.querySelector('svg');

  // Redraw when the box changes width (window resize, or the page showing a hidden section).
  if (!host.spObserver && window.ResizeObserver) {
    const box = scroller || host;
    host.spObserver = new ResizeObserver(() => {
      const w = box.clientWidth;
      if (w && Math.abs(w - host.spState.width) > 4) window.renderSpine(host, trace, D);
    });
    host.spObserver.observe(box);
  }

  // Toolbar and drag-to-scroll, when the page gives the map a scroll box.
  if (scroller) {
    const pan = W > scroller.clientWidth + 2;
    scroller.classList.toggle('sp-pan', pan);
    scroller.classList.remove('sp-fit');
    let bar = scroller.previousElementSibling;
    if (bar && bar.classList.contains('sp-jump')) { bar.remove(); bar = scroller.previousElementSibling; }
    if (!bar || !bar.classList.contains('sp-tools')) {
      bar = document.createElement('div');
      bar.className = 'sp-tools';
      scroller.before(bar);
    }
    const options = [...nodes.values()].sort((a, b) => nameOf(a.id).localeCompare(nameOf(b.id)))
      .map(nd => `<option value="${nd.id}"${state.locked === nd.id ? ' selected' : ''}>${esc(nameOf(nd.id))}</option>`).join('');
    bar.innerHTML = `<label>Trace <select class="sp-find"><option value="">an item…</option>${options}</select></label>`
      + `<label><input type="checkbox" class="sp-lines-box"${state.lines ? ' checked' : ''}> All ingredient lines</label>`
      + `<label><input type="checkbox" class="sp-removed"${state.removed ? ' checked' : ''}> Show removed</label>`
      + (pan ? `<span class="sp-hint">Drag sideways or scroll with Shift</span>` : '');
    bar.querySelector('.sp-find').addEventListener('change', ev => {
      state.locked = ev.target.value || null;
      state.locked ? show(state.locked) : clear();
      const nd = state.locked && nodes.get(state.locked);
      if (nd && pan) scroller.scrollTo({ left: Math.max(0, nd.x - scroller.clientWidth / 3), behavior: 'smooth' });
    });
    bar.querySelector('.sp-lines-box').addEventListener('change', ev => { state.lines = ev.target.checked; svg.classList.toggle('sp-lines', state.lines); });
    bar.querySelector('.sp-removed').addEventListener('change', ev => { state.removed = ev.target.checked; window.renderSpine(host, trace, D); });
    if (!scroller.dataset.pan) {
      scroller.dataset.pan = '1';
      let start = null, dragged = false;
      scroller.addEventListener('pointerdown', e => {
        if (e.pointerType !== 'mouse' || e.button !== 0 || !scroller.classList.contains('sp-pan')) return;
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
  const listOf = sets => Object.fromEntries(Object.entries(sets).map(([k, v]) => [k, [...v]]));
  const show = id => {
    const anc = walk(id, listOf(preds)), desc = walk(id, listOf(succs));
    svg.classList.add('sp-tracing');
    svg.querySelectorAll('.sp-chip').forEach(e => {
      const k = e.dataset.id;
      e.classList.toggle('sp-hot', k === id);
      e.classList.toggle('sp-up', anc.has(k));
      e.classList.toggle('sp-down', desc.has(k));
    });
    svg.querySelectorAll('.sp-edge, .sp-gate').forEach(e => {
      const from = e.dataset.from, tos = e.dataset.to ? [e.dataset.to] : e.dataset.members.split(' ');
      e.classList.toggle('sp-e-up', anc.has(from) && tos.some(t => t === id || anc.has(t)));
      e.classList.toggle('sp-e-down', (from === id || desc.has(from)) && tos.some(t => desc.has(t)));
    });
    if (!trace) return;
    const nd = nodes.get(id), it = D.items[id] || {};
    const lines = [];
    let head = `<b>${esc(nameOf(id))}</b>`;
    if (it.was) head += ` <span class="sp-meta">today: ${esc(it.was)}</span>`;
    head += ` <span class="sp-meta">${esc(STAGES[nd.stage].name)} · ${nd.col === 1 ? '1 step' : nd.col + ' steps'} from bare hands · ${esc(STATUS[nd.st] || '')}${reach.has(id) ? '' : ' · waits on a blocked decision'}</span>`;
    lines.push(head);
    lines.push(esc(recipeText(nd.prim)));
    const alts = (recipesFor[id] || []).filter(p => p !== nd.prim && p.req !== null && p.s !== null);
    if (alts.length) lines.push('Also: ' + esc(alts.map(recipeText).join('; ')));
    const gatesOnWay = [...anc].filter(k => stationOf.has(k) && nodes.has(k)).sort((a, b) => tier[a] - tier[b]).map(nameOf);
    lines.push(gatesOnWay.length ? 'Gates on the way: ' + esc(gatesOnWay.join(' → ')) : 'Needs no station or tool.');
    const items = [...anc].filter(k => nodes.get(k).kind !== 'any');
    const uses = [...(succs[id] || [])];
    lines.push(`Needs ${items.length} item${items.length === 1 ? '' : 's'} back to bare hands · `
      + (uses.length ? `used by ${esc(list(uses, 6))}` + (desc.size > uses.length ? `, ${desc.size} things down the line` : '') : 'used by nothing yet'));
    const note = nd.prim.x.r && nd.prim.x.r.noteNo;
    if (note) lines.push(`<span class="sp-meta">Note ${note}: ${esc(D.notes[note - 1])}</span>`);
    if (state.locked === id) lines.push('<span class="sp-meta">Pinned · click it again to release</span>');
    trace.innerHTML = lines.map(l => `<span class="sp-line">${l}</span>`).join('');
  };
  const clear = () => {
    if (state.locked && nodes.has(state.locked)) return show(state.locked);
    svg.classList.remove('sp-tracing');
    svg.querySelectorAll('.sp-hot, .sp-up, .sp-down, .sp-e-up, .sp-e-down').forEach(e => e.classList.remove('sp-hot', 'sp-up', 'sp-down', 'sp-e-up', 'sp-e-down'));
    if (trace) trace.innerHTML = ['Hover or tap an item: everything it needs lights up (green rim), all the way back to bare hands, and everything it unlocks (blue rim). Its ingredient lines take the colour of the workbench that uses them. Click to pin it.',
      'Columns are the four stages. Each card holds what one workbench or tool makes in that stage, and an arrow in the workbench\'s colour leads to it from the workbench.',
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
