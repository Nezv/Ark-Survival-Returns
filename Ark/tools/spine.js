// Workstation spine renderer, shared by the Recipe Gates chart and the showcase (tools/build_item_flow.py).
// renderSpine(host, trace, data): draws the SVG into host; trace is the element that names the traced item.
// The spine runs left to right in unlock order. Each recipe is a column on its station: the inputs hang
// above the station band, the outputs below. When host sits in a .sp-scroll, a station jump bar is added
// before it and the chart can be dragged sideways.
// Fonts come from the --sp-body, --sp-mono and --sp-display tokens so text measurement matches the CSS.
window.renderSpine = function (host, trace, D) {
  const CH = 24, BAND = 64, TOP = 40, GAP_IN = 20, GAP_OUT = 20, PLUS = 14, CG = 18;
  const NODE_GAP = 52, DIV_GAP = 96, X_START = 24;
  const css = getComputedStyle(host);
  const family = (name, fallback) => (css.getPropertyValue(name) || '').trim() || fallback;
  const body = family('--sp-body', 'system-ui, sans-serif'), mono = family('--sp-mono', 'monospace'),
    display = family('--sp-display', 'sans-serif');
  const FONT = { chip: `500 12px ${body}`, mono: `500 10px ${mono}`, head: `700 17px ${display}`, val: `400 9.5px ${mono}` };
  const ctx = document.createElement('canvas').getContext('2d');
  const tw = (t, f) => { ctx.font = FONT[f]; return ctx.measureText(t || '').width; };
  const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  // Where each item is made and used, for the trace readout.
  const made = {}, used = {};
  for (const n of D.spine) for (const r of n.rows) {
    for (const c of r.outs) made[c.id] = (made[c.id] || 0) + 1;
    for (const c of r.ins) if (!c.world) used[c.id] = (used[c.id] || 0) + 1;
  }

  const kindOf = c => (c.world ? 'world' : 'in');
  const label = c => (c.n > 1 ? c.n + '× ' : '') + D.items[c.id].name;
  const chipW = (c, kind) => Math.ceil(28 + tw(label(c), 'chip') + 9 + (c.ch ? tw(c.ch, 'mono') + 6 : 0) + (kind === 'in' ? 9 : 0));
  // Room an output takes: the value text under it may be wider than the chip.
  const slotW = c => Math.max(chipW(c, 'out'), c.val ? Math.ceil(tw(c.val, 'val')) + 8 : 0);

  function tip(c) {
    const it = D.items[c.id], t = [it.name];
    if (it.was) t.push('In game today: ' + it.was);
    if (it.art === 'new') t.push('Needs a new sprite');
    if (it.art === 'standin') t.push('Drawn with a borrowed sprite today');
    if (c.val) t.push(c.val);
    return t.join('\n');
  }

  const cube = (x, y) => `<path class="sp-cube" d="M${x + 8} ${y + 1}l7 3.5v8L${x + 8} ${y + 16}l-7-3.5v-8zM${x + 1} ${y + 4.5}l7 3.5l7-3.5M${x + 8} ${y + 8}v8"/>`;

  // A chip is drawn from its left edge x at centre line y. Net labels point left, at the wire they join.
  function chip(c, x, y, kind, st) {
    const it = D.items[c.id], w = chipW(c, kind), t = y - CH / 2, ix = x + (kind === 'in' ? 14 : 6), iy = t + 4;
    let s = `<g class="sp-chip sp-${kind}${kind === 'out' ? ' st-' + st : ''}" data-net="${c.id}" tabindex="0" role="button" aria-label="${esc(it.name)}"><title>${esc(tip(c))}</title>`;
    s += kind === 'in'
      ? `<path class="sp-shape" d="M${x} ${y}l10 ${-CH / 2}h${w - 10}v${CH}h${-(w - 10)}z"/>`
      : `<rect class="sp-shape" x="${x}" y="${t}" width="${w}" height="${CH}" rx="2"/>`;
    if (it.art === 'new') s += `<rect x="${ix}" y="${iy}" width="16" height="16" fill="url(#sp-hatch)"/>`;
    if (it.icon) s += `<image href="${it.icon}" x="${ix}" y="${iy}" width="16" height="16" preserveAspectRatio="none" style="image-rendering:pixelated"/>`;
    else if (it.cube) s += cube(ix, iy);
    if (it.art === 'standin') s += `<path class="sp-standin" d="M${ix + 10} ${iy - 1}h7v7z"/>`;
    const text = label(c), tx = ix + 22;
    s += `<text class="sp-label" x="${tx}" y="${y + 4}">${esc(text)}</text>`;
    if (c.ch) s += `<text class="sp-ch" x="${tx + tw(text, 'chip') + 6}" y="${y + 3.5}">${esc(c.ch)}</text>`;
    if (kind === 'out' && st === 'cut') s += `<line class="sp-strike" x1="${x + 4}" x2="${x + w - 4}" y1="${y}" y2="${y}"/>`;
    if (c.val) s += `<text class="sp-val" x="${x + 2}" y="${t + CH + 11}">${esc(c.val)}</text>`;
    return s + '</g>';
  }

  function fit(text, font, max, cls, x, y) {
    const adj = tw(text, font) > max ? ` textLength="${Math.floor(max)}" lengthAdjust="spacingAndGlyphs"` : '';
    return `<text class="${cls}" x="${x}" y="${y}"${adj}>${esc(text)}</text>`;
  }

  // Measure every recipe column first: the tallest input stack sets where the spine runs.
  const cols = new Map();
  let maxIn = 0, maxOut = 0;
  for (const n of D.spine) for (const r of n.rows) {
    const hasVal = r.outs.some(c => c.val), outStep = CH + (hasVal ? 20 : 10);
    const inMax = r.ins.length ? Math.max(...r.ins.map(c => chipW(c, kindOf(c)))) : 0;
    const outMax = r.outs.length ? Math.max(...r.outs.map(slotW)) : 0;
    const w = Math.ceil(Math.max(10 + inMax, 12 + outMax, 12 + tw(r.op, 'mono'), 12 + tw(r.step, 'mono') + (r.noteNo ? 22 : 0), 44));
    const inH = r.ins.length ? GAP_IN + r.ins.length * CH + (r.ins.length - 1) * PLUS : 0;
    const outH = r.outs.length ? GAP_OUT + (r.outs.length - 1) * outStep + CH + (hasVal ? 13 : 0) : 0;
    cols.set(r, { w, outStep, hasVal });
    maxIn = Math.max(maxIn, inH);
    maxOut = Math.max(maxOut, outH);
  }
  const SY = TOP + maxIn + BAND / 2, BT = SY - BAND / 2, BB = SY + BAND / 2;

  function columnSvg(r, x0) {
    const { w, outStep } = cols.get(r), bx = x0 + 4, wire = `sp-wire st-${r.st}`;
    let back = '', front = '';
    // Inputs stack upwards from the band, the last one nearest the station, joined by "+".
    const n = r.ins.length;
    const inY = k => BT - GAP_IN - CH / 2 - (n - 1 - k) * (CH + PLUS);
    if (n) back += `<path class="${wire}" d="M${bx} ${BT}V${inY(0)}"/>`;
    r.ins.forEach((c, k) => {
      const y = inY(k), px = bx + 6;
      back += `<path class="${wire}" d="M${bx} ${y}H${px}"/>`;
      front += chip(c, px, y, kindOf(c), r.st);
      if (k < n - 1) front += `<text class="sp-plus" x="${px + 22}" y="${y + CH / 2 + PLUS / 2 + 4}" text-anchor="middle">+</text>`;
    });
    // Outputs hang below the band on a bus.
    const outY = k => BB + GAP_OUT + CH / 2 + k * outStep;
    if (r.outs.length) back += `<path class="${wire}" d="M${bx} ${BB}V${outY(r.outs.length - 1)}"/>`;
    r.outs.forEach((c, k) => {
      const y = outY(k), ox = bx + 8;
      back += `<path class="${wire}" d="M${bx} ${y}H${ox}"/>`;
      front += chip(c, ox, y, 'out', r.st);
    });
    // Inside the band: the through line, both pins, the step, the note mark and the operation.
    front += `<line class="sp-through" x1="${bx}" x2="${bx}" y1="${BT + 3}" y2="${BB - 3}"/>`;
    if (n) front += `<rect class="sp-pin" x="${bx - 3}" y="${BT - 3}" width="6" height="6"/>`;
    if (r.outs.length) front += `<rect class="sp-pin" x="${bx - 3}" y="${BB - 3}" width="6" height="6"/>`;
    if (r.step) front += `<text class="sp-step st-${r.st}" x="${bx + 6}" y="${BT + 18}">${esc(r.step)}</text>`;
    if (r.noteNo) {
      const nx = x0 + w - 9;
      front += `<g class="sp-nmark"><circle cx="${nx}" cy="${BT + 14}" r="8"/><text x="${nx}" y="${BT + 17.2}" text-anchor="middle">${r.noteNo}</text></g>`;
    }
    if (r.op) front += fit(r.op, 'mono', w - 8, 'sp-op', bx + 6, BB - 10);
    return { back, front };
  }

  function glyph(n, x, y) {
    if (n.glyph === 'empty') return `<text class="sp-glyphtxt" x="${x + 1}" y="${y + 16}">∅</text>`;
    if (n.glyph === 'grid2' || n.glyph === 'grid3') {
      const k = n.glyph === 'grid2' ? 2 : 3, c = 20 / k;
      let s = '';
      for (let a = 0; a < k; a++) for (let b = 0; b < k; b++) s += `<rect class="sp-glyphgrid" x="${x + a * c}" y="${y + b * c}" width="${c}" height="${c}"/>`;
      return s;
    }
    if (n.glyph === 'cube') return `<g transform="translate(${x} ${y}) scale(1.25)">${cube(0, 0)}</g>`;
    const src = D.glyphs[n.glyph];
    return src ? `<image href="${src}" x="${x}" y="${y}" width="20" height="20" style="image-rendering:pixelated"/>` : '';
  }

  function nodeSvg(n, x0, x1, head) {
    const cls = `sp-node${n.kind === 'gate' ? ' sp-gate' : ''}${n.st === 'cut' ? ' sp-cutnode' : ''}`;
    let s = n.kind === 'gate'
      ? `<path class="${cls}" d="M${x0 + 12} ${BT}H${x1 - 12}L${x1} ${BT + 12}V${BB - 12}L${x1 - 12} ${BB}H${x0 + 12}L${x0} ${BB - 12}V${BT + 12}z"/>`
      : `<rect class="${cls}" x="${x0}" y="${BT}" width="${x1 - x0}" height="${BAND}"/>`;
    s += glyph(n, x0 + 12, SY - 20);
    s += fit(n.name.toUpperCase(), 'head', head - 56, 'sp-name' + (n.st === 'cut' ? ' sp-cutname' : ''), x0 + 42, SY - 3);
    s += fit(n.sub, 'mono', head - 56, 'sp-sub', x0 + 42, SY + 13);
    s += `<line class="sp-hrule" x1="${x0 + head - 4}" x2="${x0 + head - 4}" y1="${BT + 6}" y2="${BB - 6}"/>`;
    return s;
  }

  function titleBlock(x, y) {
    const w = 380, tb = D.titleBlock;
    const cell = (k, v, cx, cy) => `<text class="sp-k" x="${cx}" y="${cy}">${k}</text><text class="sp-v" x="${cx}" y="${cy + 17}">${esc(v)}</text>`;
    return `<g class="sp-tb"><rect x="${x}" y="${y}" width="${w}" height="96"/>`
      + `<line x1="${x}" x2="${x + w}" y1="${y + 36}" y2="${y + 36}"/><line x1="${x}" x2="${x + w}" y1="${y + 66}" y2="${y + 66}"/>`
      + `<line x1="${x + 230}" x2="${x + 230}" y1="${y + 36}" y2="${y + 96}"/>`
      + cell('TITLE', tb.title, x + 10, y + 13)
      + cell('PROJECT', tb.project, x + 10, y + 47) + cell('REV', tb.rev, x + 240, y + 47)
      + cell('SOURCE', tb.source, x + 10, y + 77) + cell('DATE', tb.date, x + 240, y + 77)
      + `</g>`;
  }

  const back = [], front = [], dividers = [], stops = [];
  let x = X_START, prev = null;
  for (const n of D.spine) {
    if (n.divider) {
      const dx = prev === null ? x : prev + DIV_GAP / 2;
      dividers.push({ x: dx, text: n.divider });
      x = prev === null ? x + 24 : prev + DIV_GAP;
    } else if (prev !== null) x = prev + NODE_GAP;
    const x0 = x;
    const head = Math.round(Math.min(250, Math.max(150, Math.max(tw(n.name.toUpperCase(), 'head'), tw(n.sub, 'mono')) + 58)));
    let cx = x0 + head + 6;
    const parts = [];
    for (const r of n.rows) { parts.push(columnSvg(r, cx)); cx += cols.get(r).w + CG; }
    const x1 = cx - CG + 12;
    if (prev !== null) back.push(`<line class="sp-spine" x1="${prev}" x2="${x0 - 2}" y1="${SY}" y2="${SY}" marker-end="url(#sp-arr)"/>`);
    back.push(...parts.map(p => p.back));
    front.push(nodeSvg(n, x0, x1, head), ...parts.map(p => p.front));
    stops.push({ x: x0, name: n.name });
    prev = x1;
  }
  const bottom = BB + maxOut;
  const notes = D.titleBlock.notes || [];
  const H = Math.ceil(Math.max(bottom, SY + 48) + 24 + notes.length * 18 + 8);
  const tbx = prev + 64, W = Math.ceil(tbx + 380 + 24);
  for (const d of dividers) {
    back.push(`<g class="sp-divider"><line x1="${d.x}" x2="${d.x}" y1="10" y2="${H - 10}"/><text x="${d.x + 8}" y="24">${esc(d.text.toUpperCase())}</text></g>`);
  }
  const noteSvg = notes.map((t, i) => `<text class="sp-tbnote" x="${X_START}" y="${bottom + 26 + i * 18}">${esc(t)}</text>`).join('');
  const defs = `<defs><marker id="sp-arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path class="sp-arrowhead" d="M0 0L10 5L0 10z"/></marker>`
    + `<pattern id="sp-hatch" width="4" height="4" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect class="sp-hatch-bg" width="4" height="4"/><line class="sp-hatch-line" x1="0" y1="0" x2="0" y2="4"/></pattern></defs>`;
  host.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="${W}" height="${H}" role="img" aria-label="Workstation spine with every recipe, left to right">${defs}${back.join('')}${front.join('')}${titleBlock(tbx, SY - 48)}${noteSvg}</svg>`;

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

  // Tracing: hover or focus lights every recipe that uses an item; a click keeps it lit.
  const svg = host.querySelector('svg'), chips = [...svg.querySelectorAll('.sp-chip')];
  const show = net => {
    svg.classList.add('sp-tracing');
    chips.forEach(e => e.classList.toggle('sp-hot', e.dataset.net === net));
    if (!trace) return;
    const it = D.items[net], m = made[net] || 0, u = used[net] || 0;
    trace.innerHTML = `<b>${esc(it.name)}</b>${it.was ? ' (today: ' + esc(it.was) + ')' : ''} · made by ${m} recipe${m === 1 ? '' : 's'} · used by ${u} recipe${u === 1 ? '' : 's'}${host.dataset.locked ? ' · click again to release' : ''}`;
  };
  const clear = () => {
    if (host.dataset.locked) return show(host.dataset.locked);
    svg.classList.remove('sp-tracing');
    chips.forEach(e => e.classList.remove('sp-hot'));
    if (trace) trace.textContent = 'Nothing traced.';
  };
  for (const e of chips) {
    e.addEventListener('mouseenter', () => show(e.dataset.net));
    e.addEventListener('mouseleave', clear);
    e.addEventListener('focus', () => show(e.dataset.net));
    e.addEventListener('blur', clear);
    const toggle = () => {
      if (host.dataset.locked === e.dataset.net) delete host.dataset.locked; else host.dataset.locked = e.dataset.net;
      host.dataset.locked ? show(host.dataset.locked) : clear();
    };
    e.addEventListener('click', toggle);
    e.addEventListener('keydown', ev => { if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); toggle(); } });
  }
  if (host.dataset.locked) show(host.dataset.locked);
};
