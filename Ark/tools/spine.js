// Workstation spine renderer, shared by the Recipe Gates chart and the showcase (tools/build_item_flow.py).
// renderSpine(host, trace, data): draws the SVG into host; trace is the element that names the traced item.
// Fonts come from the --sp-body, --sp-mono and --sp-display tokens so text measurement matches the CSS.
window.renderSpine = function (host, trace, D) {
  const W = 1240, SX = 620, SW = 236, X0 = SX - SW / 2, X1 = SX + SW / 2;
  const XL = X0 - 48, XR = X1 + 48, LMAX = XL - 20, RMAX = W - XR - 44;
  const CH = 24, HEAD = 50;
  const css = getComputedStyle(host);
  const family = (name, fallback) => (css.getPropertyValue(name) || '').trim() || fallback;
  const body = family('--sp-body', 'system-ui, sans-serif'), mono = family('--sp-mono', 'monospace'),
    display = family('--sp-display', 'sans-serif');
  const FONT = { chip: `500 12px ${body}`, mono: `500 10px ${mono}`, head: `700 17px ${display}`, val: `400 9.5px ${mono}` };
  const ctx = document.createElement('canvas').getContext('2d');
  const tw = (t, f) => { ctx.font = FONT[f]; return ctx.measureText(t).width; };
  const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  // Where each item is made and used, for the trace readout.
  const made = {}, used = {};
  for (const n of D.spine) for (const r of n.rows) {
    for (const c of r.outs) made[c.id] = (made[c.id] || 0) + 1;
    for (const c of r.ins) if (!c.world) used[c.id] = (used[c.id] || 0) + 1;
  }

  const label = c => (c.n > 1 ? c.n + '× ' : '') + D.items[c.id].name;
  const chipW = (c, kind) => Math.ceil(28 + tw(label(c), 'chip') + 9 + (c.ch ? tw(c.ch, 'mono') + 6 : 0) + (kind === 'in' ? 9 : 0));
  // Room a chip takes in its line: the value text under an output may be wider than the chip.
  const slotW = (c, kind) => Math.max(chipW(c, kind), c.val ? Math.ceil(tw(c.val, 'val')) + 8 : 0);

  function pack(chips, kind, max, sep) {
    const lines = []; let cur = [], x = 0;
    for (const c of chips) {
      const w = slotW(c, kind);
      if (cur.length && x + sep + w > max) { lines.push(cur); cur = []; x = 0; }
      x = cur.length ? x + sep + w : w;
      cur.push({ c, w });
    }
    if (cur.length) lines.push(cur);
    return lines;
  }
  const width = (line, sep) => line.reduce((s, p) => s + p.w, 0) + sep * (line.length - 1);

  function tip(c) {
    const it = D.items[c.id], t = [it.name];
    if (it.was) t.push('In game today: ' + it.was);
    if (it.art === 'new') t.push('Needs a new sprite');
    if (it.art === 'standin') t.push('Drawn with a borrowed sprite today');
    if (c.val) t.push(c.val);
    return t.join('\n');
  }

  const cube = (x, y) => `<path class="sp-cube" d="M${x + 8} ${y + 1}l7 3.5v8L${x + 8} ${y + 16}l-7-3.5v-8zM${x + 1} ${y + 4.5}l7 3.5l7-3.5M${x + 8} ${y + 8}v8"/>`;

  function chip(c, x, y, kind, st) {
    const it = D.items[c.id], w = chipW(c, kind), t = y - CH / 2, ix = x + 6, iy = t + 4;
    let s = `<g class="sp-chip sp-${kind}${kind === 'out' ? ' st-' + st : ''}" data-net="${c.id}" tabindex="0" role="button" aria-label="${esc(it.name)}"><title>${esc(tip(c))}</title>`;
    s += kind === 'in'
      ? `<path class="sp-shape" d="M${x} ${t}h${w - 10}l10 ${CH / 2}l-10 ${CH / 2}h${-(w - 10)}z"/>`
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

  function rowSvg(r, top) {
    const ins = pack(r.ins, 'in', LMAX, 18), outs = pack(r.outs, 'out', RMAX, 8);
    const hasVal = r.outs.some(c => c.val);
    const lineH = hasVal ? 44 : 31, n = Math.max(ins.length, outs.length, 1);
    const ys = Array.from({ length: n }, (_, j) => top + 5 + CH / 2 + j * lineH);
    const h = n * lineH + (hasVal ? 2 : 6);
    const y0 = ys[0], wire = `sp-wire st-${r.st}`;
    let s = '';
    // Inputs sit right-aligned against the station, joined by "+".
    ins.forEach((line, j) => {
      let x = XL - width(line, 18);
      line.forEach((p, k) => {
        s += chip(p.c, x, ys[j], p.c.world ? 'world' : 'in', r.st);
        x += p.w;
        if (k < line.length - 1) { s += `<text class="sp-plus" x="${x + 9}" y="${ys[j] + 4}" text-anchor="middle">+</text>`; x += 18; }
      });
    });
    if (ins.length > 1) {
      ys.slice(0, ins.length).forEach(y => { s += `<path class="${wire}" d="M${XL} ${y}H${XL + 18}"/>`; });
      s += `<path class="${wire}" d="M${XL + 18} ${ys[0]}V${ys[ins.length - 1]}M${XL + 18} ${y0}H${X0}"/>`;
    } else if (ins.length) s += `<path class="${wire}" d="M${XL} ${y0}H${X0}"/>`;
    outs.forEach((line, j) => {
      let x = XR;
      line.forEach(p => { s += chip(p.c, x, ys[j], 'out', r.st); x += p.w + 8; });
    });
    if (outs.length > 1) {
      ys.slice(0, outs.length).forEach(y => { s += `<path class="${wire}" d="M${XR - 18} ${y}H${XR}"/>`; });
      s += `<path class="${wire}" d="M${X1} ${y0}H${XR - 18}V${ys[outs.length - 1]}"/>`;
    } else if (outs.length) s += `<path class="${wire}" d="M${X1} ${y0}H${XR}"/>`;
    s += `<rect class="sp-pin" x="${X0 - 3}" y="${y0 - 3}" width="6" height="6"/><rect class="sp-pin" x="${X1 - 3}" y="${y0 - 3}" width="6" height="6"/>`;
    if (r.step) s += `<text class="sp-step st-${r.st}" x="${X0 + 10}" y="${y0 + 3.5}">${esc(r.step)}</text>`;
    if (r.op) s += `<text class="sp-op" x="${SX + 12}" y="${y0 + 3.5}" text-anchor="middle">${esc(r.op)}</text>`;
    if (r.noteNo) {
      const nx = XR + (outs.length ? width(outs[0], 8) : 0) + 14;
      s += `<g class="sp-nmark"><circle cx="${nx}" cy="${y0}" r="8"/><text x="${nx}" y="${y0 + 3.2}" text-anchor="middle">${r.noteNo}</text></g>`;
    }
    return { s, h };
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

  function fit(text, font, max, cls, x, y) {
    const adj = tw(text, font) > max ? ` textLength="${max}" lengthAdjust="spacingAndGlyphs"` : '';
    return `<text class="${cls}" x="${x}" y="${y}"${adj}>${esc(text)}</text>`;
  }

  function nodeSvg(n, top, bottom) {
    const cls = `sp-node${n.kind === 'gate' ? ' sp-gate' : ''}${n.st === 'cut' ? ' sp-cutnode' : ''}`;
    let s = n.kind === 'gate'
      ? `<path class="${cls}" d="M${X0 + 12} ${top}H${X1 - 12}L${X1} ${top + 12}V${bottom - 12}L${X1 - 12} ${bottom}H${X0 + 12}L${X0} ${bottom - 12}V${top + 12}z"/>`
      : `<rect class="${cls}" x="${X0}" y="${top}" width="${SW}" height="${bottom - top}"/>`;
    s += glyph(n, X0 + 12, top + 13);
    s += fit(n.name.toUpperCase(), 'head', SW - 54, 'sp-name' + (n.st === 'cut' ? ' sp-cutname' : ''), X0 + 42, top + 23);
    s += fit(n.sub, 'mono', SW - 54, 'sp-sub', X0 + 42, top + 38);
    s += `<line class="sp-hrule" x1="${X0 + 6}" x2="${X1 - 6}" y1="${top + HEAD - 4}" y2="${top + HEAD - 4}"/>`;
    return s;
  }

  function titleBlock(y) {
    const x = W - 400, w = 380, tb = D.titleBlock;
    const cell = (k, v, cx, cy) => `<text class="sp-k" x="${cx}" y="${cy}">${k}</text><text class="sp-v" x="${cx}" y="${cy + 17}">${esc(v)}</text>`;
    return `<g class="sp-tb"><rect x="${x}" y="${y}" width="${w}" height="96"/>`
      + `<line x1="${x}" x2="${x + w}" y1="${y + 36}" y2="${y + 36}"/><line x1="${x}" x2="${x + w}" y1="${y + 66}" y2="${y + 66}"/>`
      + `<line x1="${x + 230}" x2="${x + 230}" y1="${y + 36}" y2="${y + 96}"/>`
      + cell('TITLE', tb.title, x + 10, y + 13)
      + cell('PROJECT', tb.project, x + 10, y + 47) + cell('REV', tb.rev, x + 240, y + 47)
      + cell('SOURCE', tb.source, x + 10, y + 77) + cell('DATE', tb.date, x + 240, y + 77)
      + `</g>` + tb.notes.map((t, i) => `<text class="sp-tbnote" x="24" y="${y + 20 + i * 18}">${esc(t)}</text>`).join('');
  }

  const back = [], front = [];
  let y = 28, prev = null;
  for (const n of D.spine) {
    if (n.divider) {
      const dy = y + 6;
      back.push(`<g class="sp-divider"><line x1="16" x2="${W - 16}" y1="${dy}" y2="${dy}"/><text x="18" y="${dy - 7}">${esc(n.divider.toUpperCase())}</text></g>`);
      y += 30;
    }
    const top = y;
    let ry = top + HEAD;
    const rows = [];
    for (const r of n.rows) { const { s, h } = rowSvg(r, ry); rows.push(s); ry += h; }
    const bottom = ry + 8;
    if (prev !== null) back.push(`<line class="sp-spine" x1="${SX}" x2="${SX}" y1="${prev}" y2="${top - 2}" marker-end="url(#sp-arr)"/>`);
    front.push(nodeSvg(n, top, bottom), ...rows);
    prev = bottom;
    y = bottom + 36;
  }
  const H = y + 110;
  const defs = `<defs><marker id="sp-arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path class="sp-arrowhead" d="M0 0L10 5L0 10z"/></marker>`
    + `<pattern id="sp-hatch" width="4" height="4" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect class="sp-hatch-bg" width="4" height="4"/><line class="sp-hatch-line" x1="0" y1="0" x2="0" y2="4"/></pattern></defs>`;
  host.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="${W}" height="${H}" role="img" aria-label="Workstation spine with every recipe">${defs}${back.join('')}${front.join('')}${titleBlock(y)}</svg>`;

  // Tracing: hover or focus lights every row that uses an item; a click keeps it lit.
  const svg = host.querySelector('svg'), chips = [...svg.querySelectorAll('.sp-chip')];
  const show = net => {
    svg.classList.add('sp-tracing');
    chips.forEach(e => e.classList.toggle('sp-hot', e.dataset.net === net));
    if (!trace) return;
    const it = D.items[net], m = made[net] || 0, u = used[net] || 0;
    trace.innerHTML = `<b>${esc(it.name)}</b>${it.was ? ' (today: ' + esc(it.was) + ')' : ''} · made on ${m} row${m === 1 ? '' : 's'} · used on ${u} row${u === 1 ? '' : 's'}${host.dataset.locked ? ' · click again to release' : ''}`;
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
