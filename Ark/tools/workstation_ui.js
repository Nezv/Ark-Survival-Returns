/*
 * Workstation screen preview (P14). Draws a tools/workstation_graph.js graph the way the mod's WorkstationScreen
 * will: laid out in design units (the panel is graph_style.panel.width x height, set in the bench's frame), with
 * the whole screen scaled so the panel spans panel.widthFraction of the window. The canvas renders a whole number of
 * device pixels per unit and the browser only scales down, so item icons and the vanilla bitmap font stay crisp.
 * Drawing goes through a GuiGraphics-sized set of calls (fill, blit, item, text, tooltip), so the Java screen can
 * port it call for call. Crafted items go straight into the inventory.
 *
 * ArkWorkstationUI.mount(root, payload) wires one preview: the canvas, the bench tabs, the preview-only controls
 * (level, inventory) and the Armoury recipe table. The payload comes from tools/showcase_recipes.py.
 */
window.ArkWorkstationUI = (function () {
  'use strict';
  var G = window.ArkWorkstationGraph;
  var REDUCED = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var WHITE = '#ffffff', GRAY = '#aaaaaa';

  // ------------------------------------------------------------------------------------------------ colours

  var parsed = {};
  /** '#rrggbb' or '#aarrggbb' (Minecraft ARGB order) as [r, g, b, a]. */
  function rgba(hex) {
    if (parsed[hex]) return parsed[hex];
    var h = hex.slice(1), a = 255;
    if (h.length === 8) { a = parseInt(h.slice(0, 2), 16); h = h.slice(2); }
    return (parsed[hex] = [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16), a]);
  }
  function css(hex, alpha) {
    var c = rgba(hex);
    return 'rgba(' + c[0] + ',' + c[1] + ',' + c[2] + ',' + (c[3] / 255 * (alpha == null ? 1 : alpha)).toFixed(3) + ')';
  }
  function hex2(v) { return ('0' + Math.round(Math.max(0, Math.min(255, v))).toString(16)).slice(-2); }
  function mix(a, b, t) {
    var x = rgba(a), y = rgba(b);
    return '#' + hex2(x[0] + (y[0] - x[0]) * t) + hex2(x[1] + (y[1] - x[1]) * t) + hex2(x[2] + (y[2] - x[2]) * t);
  }
  /** Minecraft's text shadow: the colour at a quarter brightness. */
  function shadowOf(hex) { var c = rgba(hex); return '#' + hex2(c[0] >> 2) + hex2(c[1] >> 2) + hex2(c[2] >> 2); }
  function ease(t) { t = Math.max(0, Math.min(1, t)); return 1 - (1 - t) * (1 - t) * (1 - t); }

  // --------------------------------------------------------------------------------------------------- font

  /** The vanilla bitmap font: 8x8 glyph cells in one row, advance = glyph width + 1, space = 4. */
  function Font(spec) {
    this.image = new Image();
    this.image.src = spec.atlas;
    this.index = {};
    for (var i = 0; i < spec.chars.length; i++) this.index[spec.chars[i]] = i;
    this.advances = spec.advances;
    this.tints = {};
  }
  Font.prototype.glyph = function (ch) { var i = this.index[ch]; return i == null ? this.index['?'] : i; };
  Font.prototype.advance = function (ch) { return ch === ' ' ? 4 : this.advances[this.glyph(ch)]; };
  Font.prototype.width = function (text) {
    var w = 0;
    for (var i = 0; i < text.length; i++) w += this.advance(text[i]);
    return w;
  };
  Font.prototype.tint = function (hex) {
    if (this.tints[hex]) return this.tints[hex];
    if (!this.image.complete || !this.image.naturalWidth) return null;
    var c = document.createElement('canvas');
    c.width = this.image.naturalWidth; c.height = this.image.naturalHeight;
    var x = c.getContext('2d');
    x.drawImage(this.image, 0, 0);
    x.globalCompositeOperation = 'source-in';
    x.fillStyle = css(hex);
    x.fillRect(0, 0, c.width, c.height);
    return (this.tints[hex] = c);
  };
  Font.prototype.draw = function (g, text, x, y, hex, shadow) {
    if (shadow) this.run(g, text, x + 1, y + 1, shadowOf(hex));
    this.run(g, text, x, y, hex);
  };
  Font.prototype.run = function (g, text, x, y, hex) {
    var sheet = this.tint(hex);
    if (!sheet) return;
    for (var i = 0; i < text.length; i++) {
      if (text[i] !== ' ') g.drawImage(sheet, this.glyph(text[i]) * 8, 0, 8, 8, x, y, 8, 8);
      x += this.advance(text[i]);
    }
  };
  /** The text cut to fit `width`, ending in an ellipsis when it had to be cut. */
  Font.prototype.fit = function (text, width) {
    if (this.width(text) <= width) return text;
    while (text.length > 1 && this.width(text + '...') > width) text = text.slice(0, -1);
    return text.replace(/\s+$/, '') + '...';
  };
  Font.prototype.wrap = function (text, width) {
    var lines = [], line = '';
    text.split(' ').forEach(function (word) {
      var next = line ? line + ' ' + word : word;
      if (line && this.width(next) > width) { lines.push(line); line = word; } else line = next;
    }, this);
    if (line) lines.push(line);
    return lines;
  };

  // ----------------------------------------------------------------------------------------- pixel shapes

  var shapes = {};
  /** A pixel disc (thick 0) or ring of radius r on a 2r x 2r texture, one texel per unit. */
  function shape(r, thick, hex) {
    var key = r + '/' + thick + '/' + hex;
    if (shapes[key]) return shapes[key];
    var size = 2 * r, c = document.createElement('canvas');
    c.width = c.height = size;
    var x = c.getContext('2d'), img = x.createImageData(size, size), col = rgba(hex);
    for (var py = 0; py < size; py++) {
      for (var px = 0; px < size; px++) {
        var dx = px + 0.5 - r, dy = py + 0.5 - r, d = Math.sqrt(dx * dx + dy * dy);
        if (d > r - 0.2 || (thick && d <= r - thick - 0.2)) continue;
        var o = (py * size + px) * 4;
        img.data[o] = col[0]; img.data[o + 1] = col[1]; img.data[o + 2] = col[2]; img.data[o + 3] = col[3];
      }
    }
    x.putImageData(img, 0, 0);
    return (shapes[key] = c);
  }

  /** The faint pattern in each bench's graph well, seeded so it never moves. */
  function wellPattern(kind, w, h, pal) {
    var c = document.createElement('canvas');
    c.width = w; c.height = h;
    var x = c.getContext('2d'), seed = 7;
    function rnd() { seed = (seed * 16807) % 2147483647; return seed / 2147483647; }
    x.fillStyle = css(pal.well);
    x.fillRect(0, 0, w, h);
    x.fillStyle = css(pal.wellDot);
    var i, px, py;
    if (kind === 'grain') {
      for (py = 3; py < h; py += 5) {
        for (px = 0; px < w; px++) if (rnd() < 0.55) x.fillRect(px, py + (rnd() < 0.1 ? 1 : 0), 1, 1);
      }
    } else if (kind === 'speckle') {
      for (i = 0; i < w * h / 26; i++) x.fillRect(Math.floor(rnd() * w), Math.floor(rnd() * h), rnd() < 0.2 ? 2 : 1, 1);
    } else if (kind === 'leaves') {
      for (i = 0; i < w * h / 180; i++) {
        px = Math.floor(rnd() * w); py = Math.floor(rnd() * h);
        x.fillRect(px, py, 2, 1); x.fillRect(px + 1, py + 1, 2, 1);
      }
    } else if (kind === 'stitches') {
      for (i = 0, py = 5; py < h - 2; py += 9, i++) {
        for (px = 3 + i % 2 * 3; px < w - 4; px += 6) x.fillRect(px, py, 3, 1);
      }
    } else if (kind === 'sparks') {
      for (i = 0; i < w * h / 150; i++) {
        x.fillStyle = css(rnd() < 0.3 ? '#3a2410' : pal.wellDot);
        x.fillRect(Math.floor(rnd() * w), Math.floor(rnd() * h), 1, 1);
      }
    } else if (kind === 'embers') {
      for (i = 0; i < w * h / 120; i++) {
        x.fillStyle = css(rnd() < 0.3 ? '#43200e' : pal.wellDot);
        px = Math.floor(rnd() * w); py = Math.floor(rnd() * h);
        x.fillRect(px, py, 1, 1);
        if (rnd() < 0.15) x.fillRect(px, py - 1, 1, 1);
      }
    } else if (kind === 'soot' || kind === 'ashlar') {
      // Faint courses: brick for the forge (short, staggered), dressed stone for the Stonecutter (long, uneven).
      var course = kind === 'soot' ? 8 : 12, row = 0;
      for (py = course - 1; py < h; py += course, row++) {
        for (px = 0; px < w; px++) if (rnd() < 0.6) x.fillRect(px, py, 1, 1);
        px = kind === 'soot' ? (row % 2 ? 0 : 9) : Math.floor(rnd() * 20);
        for (; px < w; px += kind === 'soot' ? 18 : 20 + Math.floor(rnd() * 18)) {
          for (i = 1; i < course; i++) if (rnd() < 0.6) x.fillRect(px, py - i, 1, 1);
        }
      }
    } else if (kind === 'swarf') {
      // Curls of metal cut from the work: short two-unit strokes on the diagonal.
      for (i = 0; i < w * h / 160; i++) {
        px = Math.floor(rnd() * w); py = Math.floor(rnd() * h);
        x.fillRect(px, py, 1, 1); x.fillRect(px + 1, py + (rnd() < 0.5 ? 1 : -1), 1, 1);
      }
    } else if (kind === 'sawdust') {
      for (i = 0; i < w * h / 18; i++) x.fillRect(Math.floor(rnd() * w), Math.floor(rnd() * h), 1, 1);
    } else if (kind === 'plates') {
      for (px = 24; px < w; px += 48) for (py = 0; py < h; py++) if (rnd() < 0.7) x.fillRect(px, py, 1, 1);
      for (py = 30; py < h; py += 36) for (px = 0; px < w; px++) if (rnd() < 0.7) x.fillRect(px, py, 1, 1);
      for (px = 24; px < w; px += 48) {
        for (py = 30; py < h; py += 36) [[-3, -3], [2, -3], [-3, 2], [2, 2]].forEach(function (d) { x.fillRect(px + d[0], py + d[1], 1, 1); });
      }
    } else {
      for (px = 6; px < w - 2; px += 12) for (py = 6; py < h - 2; py += 12) x.fillRect(px, py, 1, 1);
    }
    return c;
  }

  // ------------------------------------------------------------------------------------------------ mount

  function mount(root, payload) {
    var style = payload.style, P = style.panel, pal = style.palette, M = style.motion, A = G.graphArea(style);
    var frameEl = root.querySelector('.ws-frame'), canvas = root.querySelector('.ws-canvas'), ctx = canvas.getContext('2d');
    var off = document.createElement('canvas'), g = off.getContext('2d');
    var font = new Font(payload.font), atlas = new Image(), world = document.querySelector('.hero > img.bg');
    var backdrop = document.createElement('canvas'), backdropKey = '', crest = null, well = null;
    var view = null, running = false, dirty = true, lastTime = 0, acc = 0, widgets = [];
    var graph = null;
    atlas.onload = function () { dirty = true; };
    atlas.src = payload.atlas.image;
    font.image.onload = function () { dirty = true; };
    if (world && !world.complete) world.addEventListener('load', function () { backdropKey = ''; dirty = true; });
    var st = { station: 0, level: 8, preset: 'starter', inv: {}, amount: 1, mouse: null, hover: null, widget: null,
               press: null, dragging: null, flies: [], note: null };

    function station() { return payload.stations[st.station]; }
    /** The bench's frame round the panel (its image and how far it reaches past the panel), or null. */
    function frameMeta() { return station().crest; }
    function meta(id) { return payload.items[id] || { name: id, icon: 0 }; }
    function have(id) { return st.inv[id] || 0; }
    function nodeId(n) {
      return n.kind === 'category' ? n.cat.icon : n.kind === 'group' ? n.entry.icon : n.kind === 'item' ? n.item.item : n.ingredient;
    }
    function enough(n) { return have(nodeId(n)) >= n.need * st.amount; }
    function title(n) {
      if (n.kind === 'category') return n.cat.title;
      if (n.kind === 'group') return n.entry.title;
      return meta(nodeId(n)).name;
    }
    function familyTitle(n) { return n.entry.title || vname(n.item); }
    /** A variant's own name (a trim is named after its pattern), else its item's. */
    function vname(variant) { return variant.name || meta(variant.item).name; }

    // ------------------------------------------------------------------ GuiGraphics-sized drawing calls

    function fill(x0, y0, x1, y1, hex, alpha) {
      g.fillStyle = css(hex, alpha);
      g.fillRect(x0, y0, x1 - x0, y1 - y0);
    }
    function text(s, x, y, hex, shadow) { font.draw(g, s, x, y, hex, shadow); }
    function item(id, x, y) {
      var cell = meta(id).icon || 0, cols = payload.atlas.cols, size = payload.atlas.size;
      if (atlas.complete && atlas.naturalWidth) {
        g.drawImage(atlas, (cell % cols) * size, Math.floor(cell / cols) * size, size, size, x, y, 16, 16);
      }
    }
    /** An item count in the slot's bottom right, the way the inventory draws stack sizes. */
    function count(n, x, y, hex) { var s = String(n); text(s, x + 17 - font.width(s), y + 9, hex, true); }
    function slot(x, y) {
      fill(x, y, x + 18, y + 18, pal.bevelDark);
      fill(x + 1, y + 1, x + 18, y + 18, pal.bevelLight);
      fill(x + 1, y + 1, x + 17, y + 17, pal.slotFace);
    }
    function button(id, b, label, enabled) {
      var hot = enabled && st.widget === id, down = hot && st.press && st.press.widget === id;
      fill(b.x, b.y, b.x + b.w, b.y + b.h, pal.outline);
      fill(b.x + 1, b.y + 1, b.x + b.w - 1, b.y + b.h - 1, !enabled ? pal.buttonOff : hot ? pal.buttonHot : pal.button);
      if (enabled) {
        fill(b.x + 1, b.y + 1, b.x + b.w - 1, b.y + 2, hot ? pal.buttonHotLight : pal.buttonLight);
        fill(b.x + 1, b.y + b.h - 2, b.x + b.w - 1, b.y + b.h - 1, pal.bevelDark);
      }
      var w = font.width(label) - 1;
      text(label, b.x + Math.floor((b.w - w) / 2), b.y + Math.floor((b.h - 8) / 2) + 1 + (down ? 1 : 0),
           enabled ? pal.buttonText : pal.buttonTextOff, enabled);
      widgets.push({ id: id, x: b.x, y: b.y, w: b.w, h: b.h, enabled: enabled });
    }
    /** Minecraft's tooltip: a panel 3 units around the text, a 1-unit frame shading from top to bottom colour. */
    function tooltip(lines, mx, my) {
      var w = 0, h = lines.length * 10 - 2 + (lines.length > 1 ? 2 : 0);
      lines.forEach(function (l) { w = Math.max(w, font.width(l[0]) - 1); });
      var x = mx + 12, y = my - 12;
      if (x + w + 4 > view.uw) x = Math.max(4, mx - 16 - w);
      y = Math.max(4, Math.min(y, view.uh - h - 4));
      var bg = pal.tooltip;
      fill(x - 3, y - 4, x + w + 3, y - 3, bg); fill(x - 3, y + h + 3, x + w + 3, y + h + 4, bg);
      fill(x - 3, y - 3, x + w + 3, y + h + 3, bg);
      fill(x - 4, y - 3, x - 3, y + h + 3, bg); fill(x + w + 3, y - 3, x + w + 4, y + h + 3, bg);
      for (var i = 0; i < h + 4; i++) {
        var c = mix(pal.tooltipTop, pal.tooltipBottom, (i + 1) / (h + 5));
        fill(x - 3, y - 2 + i, x - 2, y - 1 + i, c);
        fill(x + w + 2, y - 2 + i, x + w + 3, y - 1 + i, c);
      }
      fill(x - 3, y - 3, x + w + 3, y - 2, pal.tooltipTop); fill(x - 3, y + h + 2, x + w + 3, y + h + 3, pal.tooltipBottom);
      lines.forEach(function (l, i) { text(l[0], x, y + i * 10 + (i ? 2 : 0), l[1], true); });
    }

    // ------------------------------------------------------------------------------------ view and layout

    /** Units the bench's frame adds above and below the panel; the panel and its frame are centred together. */
    function frameTop() { var c = frameMeta(); return c ? c.top : 0; }
    function frameBottom() { var c = frameMeta(); return c ? c.bottom || 0 : 0; }

    function layout() {
      var rect = canvas.getBoundingClientRect(), dpr = window.devicePixelRatio || 1;
      if (!rect.width || !rect.height) return false;
      var tall = P.height + frameTop() + frameBottom();
      var panel = Math.min(rect.width * P.widthFraction, rect.height * P.maxHeightFraction * P.width / tall);
      var scale = panel / P.width, k = Math.max(1, Math.min(8, Math.ceil(scale * dpr - 0.01)));
      var uw = Math.ceil(rect.width / scale), uh = Math.ceil(rect.height / scale);
      view = { dpr: dpr, scale: scale, k: k, uw: uw, uh: uh, px: Math.floor((uw - P.width) / 2),
               py: Math.floor((uh - tall) / 2) + frameTop() };
      canvas.width = Math.round(rect.width * dpr);
      canvas.height = Math.round(rect.height * dpr);
      off.width = uw * k;
      off.height = uh * k;
      backdropKey = '';
      dirty = true;
      return true;
    }

    /** The world behind the screen: the title-screen art, blurred and dimmed the way the game dims it. */
    function drawBackdrop() {
      var key = off.width + 'x' + off.height;
      if (backdropKey !== key) {
        backdrop.width = off.width; backdrop.height = off.height;
        var b = backdrop.getContext('2d');
        b.fillStyle = '#10120f';
        b.fillRect(0, 0, backdrop.width, backdrop.height);
        if (world && world.complete && world.naturalWidth) {
          var s = Math.max(backdrop.width / world.naturalWidth, backdrop.height / world.naturalHeight);
          var w = world.naturalWidth * s, h = world.naturalHeight * s;
          b.filter = 'blur(' + Math.round(2 * view.k) + 'px) saturate(0.75)';
          b.drawImage(world, (backdrop.width - w) / 2, (backdrop.height - h) * 0.6, w, h);
          b.filter = 'none';
          backdropKey = key;
        }
        b.fillStyle = css(pal.backdrop);
        b.fillRect(0, 0, backdrop.width, backdrop.height);
      }
      g.save();
      g.setTransform(1, 0, 0, 1, 0, 0);
      g.drawImage(backdrop, 0, 0);
      g.restore();
    }

    function drawPanel() {
      var w = P.width, h = P.height, s = station();
      // Rounded one-unit outline, light bevel top-left, dark bottom-right, like a vanilla container.
      fill(1, 0, w - 1, 1, pal.outline); fill(1, h - 1, w - 1, h, pal.outline);
      fill(0, 1, 1, h - 1, pal.outline); fill(w - 1, 1, w, h - 1, pal.outline);
      fill(1, 1, w - 1, h - 1, pal.bevelLight);
      fill(2, 2, w - 1, h - 1, pal.bevelDark);
      fill(2, 2, w - 2, h - 2, pal.face);
      text(s.title, 8, 4, s.accent || pal.title, false);
      var level = 'Level ' + st.level;
      text(level, w - 8 - font.width(level) + 1, 4, pal.level, true);
      // The graph well, sunk into the face, with the bench's pattern.
      fill(A.x - 1, A.y - 1, A.x + A.w + 1, A.y + A.h + 1, pal.bevelLight);
      fill(A.x - 1, A.y - 1, A.x + A.w, A.y + A.h, pal.bevelDark);
      if (!well || well.kind !== s.well) { well = wellPattern(s.well, A.w, A.h, pal); well.kind = s.well; }
      g.drawImage(well, A.x, A.y);
      // The bench's frame is the panel's edge: it covers the outline and bevel above, and the crossbar sits on the
      // well's lower edge. The graph, clipped to the well, and the craft bar draw over it.
      var meta = frameMeta();
      if (crest && meta) g.drawImage(crest, -meta.margin, -meta.top);
    }

    function ringColour(n, locked) {
      if (locked) return pal.faint;
      if (n === graph.selected) return pal.select;
      if (n.kind === 'category' || n.kind === 'group') return n.cat.color;
      if (n.kind === 'ingredient') return enough(n) ? pal.craftable : pal.missing;
      return G.maxCrafts(n.item, st.inv) > 0 ? pal.craftable : mix(n.cat.color, pal.nodeFill, 0.4);
    }

    function drawGraph() {
      var hot = st.hover && st.hover.node;
      g.save();
      g.beginPath();
      g.rect(A.x, A.y, A.w, A.h);
      g.clip();
      g.lineWidth = 1;
      g.lineCap = 'round';
      graph.links.forEach(function (l) {
        var a = l.a, b = l.b, t = Math.min(ease(a.grow), ease(b.grow)), color, alpha;
        if (l.kind === 'chain') { color = pal.link; alpha = 1; }
        else if (l.kind === 'child') { color = a.cat.color; alpha = 0.4; }
        else { color = enough(b) ? pal.craftable : pal.missing; alpha = 0.75; }
        if (hot && (a === hot || b === hot)) { if (l.kind === 'chain') color = pal.linkHot; alpha = 1; }
        if (!graph.unlocked(a) || !graph.unlocked(b)) alpha *= 0.5;
        g.strokeStyle = css(color, alpha * t);
        g.beginPath();
        g.moveTo(a.x, a.y);
        g.lineTo(b.x, b.y);
        g.stroke();
      });
      graph.nodes.forEach(function (n) { drawNode(n, n === hot); });
      g.restore();
    }

    function drawNode(n, hot) {
      var s = ease(n.grow);
      if (s < 0.03) return;
      var r = n.r, locked = !graph.unlocked(n), ring = ringColour(n, locked);
      var emphasis = n === graph.selected ? pal.select : n === graph.open || n === graph.openGroup ? n.cat.color : null;
      if (hot && !locked) ring = mix(ring, WHITE, 0.4);
      if (n.pulse) s *= 1 - 0.14 * Math.sin(Math.PI * n.pulse / M.pulse);
      g.save();
      g.translate(n.x + (n.shake ? Math.sin(n.shake * 1.7) * 1.6 : 0), n.y);
      if (s !== 1) g.scale(s, s);
      g.globalAlpha = Math.min(1, s * 1.5);
      if (emphasis) {
        g.globalAlpha *= 0.4;
        g.drawImage(shape(r + 3, 1, emphasis), -r - 3, -r - 3);
        g.globalAlpha = Math.min(1, s * 1.5);
      }
      g.drawImage(shape(r, 0, pal.nodeFill), -r, -r);
      g.drawImage(shape(r, emphasis ? 2 : 1, ring), -r, -r);
      if (n.kind === 'group') g.drawImage(shape(r - 2, 1, mix(ring, pal.nodeFill, 0.45)), -r + 2, -r + 2);  // a folder: a second ring
      item(nodeId(n), -8, -8);
      if (n.kind === 'item' && n.entry.variants.length > 1) {                                            // variant dots
        for (var i = 0; i < Math.min(3, n.entry.variants.length); i++) fill(-3 + i * 2, r - 2, -2 + i * 2, r - 1, i === 0 ? ring : pal.faint);
      }
      if (locked) g.drawImage(shape(r - 1, 0, pal.dim), -r + 1, -r + 1);
      if (n.need) count(n.need * st.amount, -8, -8, enough(n) ? WHITE : pal.missing);
      g.restore();
    }

    /** Where everything in the craft bar sits, right to left; drawBar and the input share it. */
    function bar() {
      var y = A.y + A.h, right = P.width - 8, o = { y: y, slot: { x: 8, y: y + 6 } };
      o.craft = { x: right - 34, y: y + 7, w: 34, h: 16 }; right -= 34 + 6;
      o.max = { x: right - 20, y: y + 9, w: 20, h: 12 }; right -= 21;
      o.plus = { x: right - 11, y: y + 9, w: 11, h: 12 }; right -= 12;
      o.count = { x: right - 18, y: y + 9, w: 18, h: 12 }; right -= 19;
      o.minus = { x: right - 11, y: y + 9, w: 11, h: 12 }; right -= 11;
      o.text = { x: 32, w: right - 6 - 32 };
      o.next = { x: right - 16, y: y + 5, w: 10, h: 10 };
      o.prev = { x: right - 27, y: y + 5, w: 10, h: 10 };
      return o;
    }

    /** What the bench does (its design's verb: Craft, Cook, Cut, Grind, Smelt, Mix, Forge, Press) and its past tense. */
    var PAST = { Cut: 'Cut', Grind: 'Ground', Saw: 'Sawn' };
    function verb() { var d = station().data; return (d && d.verb) || 'Craft'; }
    function done() { var v = verb(); return PAST[v] || v + (/e$/.test(v) ? 'd' : 'ed'); }

    /** Why the current craft cannot happen, or '' when it can. */
    function blocker(times) {
      var sel = graph.selected;
      if (!graph.unlocked(sel)) return 'Needs level ' + G.levelOf(sel.cat, sel.entry, sel.group);
      if (sel.entry.planned || meta(sel.item.item).planned) return 'Planned: not in the game yet';
      for (var id in sel.item.cost) {
        var short = sel.item.cost[id] * times - have(id);
        if (short > 0) return 'Missing ' + short + ' ' + meta(id).name;
      }
      return '';
    }

    function drawBar() {
      widgets = [];
      var o = bar(), sel = graph.selected;
      slot(o.slot.x, o.slot.y);
      if (!sel) {
        var hint = graph.openGroup ? 'Pick a recipe to see its cost' : graph.open ? 'Pick a group or a recipe' : 'Pick a material to see what it makes';
        text(font.fit(hint, P.width - 40), 32, o.y + 8, pal.muted, false);
        text(font.fit('Names show on hover; dimmed needs a higher level', P.width - 40), 32, o.y + 18, pal.faint, false);
        return;
      }
      var entry = sel.entry, variant = sel.item, locked = !graph.unlocked(sel), max = G.maxCrafts(variant, st.inv);
      var many = entry.variants.length > 1, textW = o.text.w - (many ? 30 : 0);
      item(variant.item, o.slot.x + 1, o.slot.y + 1);
      text(font.fit(vname(variant), textW), o.text.x, o.y + 7, locked ? pal.muted : pal.title, false);
      if (many) {
        button('prev', o.prev, '<', true);
        button('next', o.next, '>', true);
      }
      var sub = locked ? ['Needs level ' + G.levelOf(sel.cat, entry, sel.group), pal.missing]
          : entry.planned || meta(variant.item).planned ? ['Planned item', pal.planned]
          : variant.apply ? ['Trims an armour piece', pal.muted]
          : [((variant.count || 1) > 1 ? 'Makes ' + variant.count + ' · have ' : 'Have ') + have(variant.item) +
             (variant.time ? ' · ' + variant.time + ' s' : '') +
             (many ? ' · ' + (sel.variant + 1) + '/' + entry.variants.length : ''), pal.muted];
      if (st.note) sub = [st.note.text, st.note.color];
      text(font.fit(sub[0], o.text.w), o.text.x, o.y + 17, sub[1], false);
      button('minus', o.minus, '-', !locked && st.amount > 1);
      fill(o.count.x, o.count.y, o.count.x + o.count.w, o.count.y + o.count.h, pal.bevelDark);
      fill(o.count.x + 1, o.count.y + 1, o.count.x + o.count.w, o.count.y + o.count.h, pal.bevelLight);
      fill(o.count.x + 1, o.count.y + 1, o.count.x + o.count.w - 1, o.count.y + o.count.h - 1, pal.slotFace);
      var amount = String(st.amount);
      text(amount, o.count.x + Math.floor((o.count.w - font.width(amount) + 1) / 2), o.count.y + 3, locked ? pal.faint : WHITE, false);
      widgets.push({ id: 'count', x: o.count.x, y: o.count.y, w: o.count.w, h: o.count.h, enabled: !locked });
      button('plus', o.plus, '+', !locked && st.amount < 99);
      button('max', o.max, 'Max', !locked && max > 0);
      button('craft', o.craft, variant.apply ? 'Apply' : verb(), !blocker(st.amount));
    }

    function drawPending() {
      widgets = [];
      var s = station(), cx = P.width / 2, lines = font.wrap(s.about || '', 220);
      text('Design pending', Math.round(cx - (font.width('Design pending') - 1) / 2), A.y + 52, pal.title, false);
      lines.forEach(function (line, i) {
        text(line, Math.round(cx - (font.width(line) - 1) / 2), A.y + 70 + i * 10, pal.muted, false);
      });
    }

    function drawFlies() {
      st.flies.forEach(function (f) {
        var t = ease(f.t), x = f.x0 + (f.x1 - f.x0) * t, y = f.y0 + (f.y1 - f.y0) * t - 14 * Math.sin(Math.PI * f.t);
        g.save();
        g.globalAlpha = Math.max(0, 1 - f.t * f.t);
        item(f.id, x - 8, y - 8);
        g.restore();
      });
    }

    function tooltipLines() {
      var h = st.hover;
      if (!h || st.dragging) return null;
      if (h.node) {
        var n = h.node, lines = [[title(n), WHITE]], locked = !graph.unlocked(n);
        if (n.kind === 'category' || n.kind === 'group') {
          var entries = n.kind === 'category' ? n.cat.items || [] : n.entry.items || [];
          var recipes = entries.reduce(function (sum, e) { return sum + (e.items ? e.items.length : 1); }, 0);
          lines.push(locked ? ['Requires level ' + (n.kind === 'category' ? n.cat.level : G.levelOf(n.cat, null, n.group)), pal.missing]
                            : [recipes + (recipes === 1 ? ' recipe' : ' recipes'), GRAY]);
          if (n.cat.planned) lines.push(['Planned: Iron Age', pal.planned]);
          return lines;
        }
        var id = nodeId(n), info = meta(id);
        if (n.kind === 'item') {
          lines = [[n.entry.variants.length > 1 ? familyTitle(n) : vname(n.item), WHITE]];
          if (n.entry.variants.length > 1) lines.push([vname(n.item) + '  ' + (n.variant + 1) + '/' + n.entry.variants.length, GRAY]);
          if (locked) lines.push(['Requires level ' + G.levelOf(n.cat, n.entry, n.group), pal.missing]);
          else if (n === graph.selected) lines.push([n.item.apply ? 'Click again to apply' : 'Click again to ' + verb().toLowerCase(), GRAY]);
          if ((n.item.count || 1) > 1) lines.push(['Makes ' + n.item.count, GRAY]);
          if (n.item.time) lines.push(['Takes ' + n.item.time + ' s over the fire', GRAY]);
          if (n.entry.variants.length > 1) lines.push(['Scroll for the other variants', GRAY]);
          if (n.entry.planned || info.planned) lines.push(['Planned item', pal.planned]);
        }
        if (n.need) lines.push(['Have ' + have(id) + ' / ' + n.need * st.amount, enough(n) ? GRAY : pal.missing]);
        return lines;
      }
      var sel = graph && graph.selected;
      if (!sel) return null;
      if (h.widget === 'craft') {
        var why = blocker(st.amount), made = (sel.item.count || 1) * st.amount;
        return [[(sel.item.apply ? 'Apply ' : verb() + ' ') + (made > 1 ? made + ' ' : '') + vname(sel.item), WHITE]]
            .concat(why ? [[why, pal.missing]] : [['Shift: all you can', GRAY]]);
      }
      if (h.widget === 'max') return [['As many as you can make', WHITE], [String(G.maxCrafts(sel.item, st.inv)), GRAY]];
      if (h.widget === 'count') return [['Amount', WHITE], ['Scroll or use - and +', GRAY]];
      if (h.widget === 'prev' || h.widget === 'next') return [['Other variant', WHITE], [(sel.variant + 1) + ' of ' + sel.entry.variants.length, GRAY]];
      return null;
    }

    function draw() {
      if (!view && !layout()) return;
      g.setTransform(view.k, 0, 0, view.k, 0, 0);
      g.imageSmoothingEnabled = false;
      drawBackdrop();
      g.save();
      g.translate(view.px, view.py);
      drawPanel();
      if (graph) { drawGraph(); drawBar(); } else drawPending();
      drawFlies();
      g.restore();
      var lines = st.mouse && tooltipLines();
      if (lines) tooltip(lines, st.mouse.ux, st.mouse.uy);
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(off, 0, 0, off.width, off.height, 0, 0, view.uw * view.scale * view.dpr, view.uh * view.scale * view.dpr);
      dirty = false;
    }

    // ------------------------------------------------------------------------------------------ crafting

    function note(message, color) { st.note = { text: message, color: color || pal.missing, ttl: 90 }; }

    function craft(times) {
      var sel = graph.selected;
      if (!sel) return;
      var variant = sel.item, max = G.maxCrafts(variant, st.inv);
      if (times < 0) times = Math.max(1, max);
      var why = blocker(times);
      if (why) { sel.shake = M.shake; note(why); return; }
      var made = (variant.count || 1) * times;
      if (variant.apply) {
        for (var id in variant.cost) if (id.indexOf('trimmable') < 0) st.inv[id] = have(id) - variant.cost[id] * times;
        note('Trim applied', pal.craftable);
      } else {
        G.pay(variant, times, st.inv);
        st.inv[variant.item] = have(variant.item) + made;
        note(done() + ' ' + (made > 1 ? made + ' ' : '') + vname(variant), pal.craftable);
      }
      graph.nodes.forEach(function (n) { if (n.need) n.pulse = M.pulse; });
      sel.pulse = M.pulse;
      if (!REDUCED && !variant.apply) st.flies.push({ id: variant.item, x0: sel.x, y0: sel.y, t: 0, x1: P.width - 12, y1: P.height - 10 });
      inventory(variant.item);
    }

    function cycle(node, step) {
      if (!node || node.kind !== 'item' || node.entry.variants.length < 2) return false;
      graph.setVariant(node, node.variant + step);
      st.note = null;
      dirty = true;
      return true;
    }

    // ---------------------------------------------------------------------------------------------- input

    function units(ev) {
      var rect = canvas.getBoundingClientRect(), ux = (ev.clientX - rect.left) / view.scale, uy = (ev.clientY - rect.top) / view.scale;
      return { ux: ux, uy: uy, lx: ux - view.px, ly: uy - view.py };
    }
    function inWell(m) { return m.lx >= A.x && m.lx < A.x + A.w && m.ly >= A.y && m.ly < A.y + A.h; }
    function widgetAt(m) {
      for (var i = 0; i < widgets.length; i++) {
        var w = widgets[i];
        if (m.lx >= w.x && m.lx < w.x + w.w && m.ly >= w.y && m.ly < w.y + w.h) return w;
      }
      return null;
    }
    function hover() {
      var m = st.mouse, before = st.hover && (st.hover.node || st.hover.widget), beforeWidget = st.widget;
      st.hover = null;
      st.widget = null;
      if (m && graph) {
        if (inWell(m)) {
          var n = graph.nodeAt(m.lx, m.ly);
          if (n) st.hover = { node: n };
        } else {
          var w = widgetAt(m);
          if (w) { st.hover = { widget: w.id }; if (w.enabled) st.widget = w.id; }
        }
      }
      canvas.style.cursor = st.dragging ? 'grabbing' : st.hover && (st.hover.node || st.widget) ? 'pointer' : 'default';
      if ((st.hover && (st.hover.node || st.hover.widget)) !== before || st.widget !== beforeWidget || st.hover) dirty = true;
    }

    function settleNow() { if (REDUCED && graph) graph.settle(900); }

    function clickNode(node, shift) {
      var result = graph.click(node);
      if (result === 'select') { st.amount = 1; st.note = null; }
      if (result === 'craft') craft(shift ? -1 : st.amount);
      if (result === 'locked') {
        note('Requires level ' + (node.kind === 'category' ? node.cat.level : G.levelOf(node.cat, node.kind === 'item' ? node.entry : null, node.group)));
      }
      settleNow();
    }

    function clickWidget(id, shift) {
      var sel = graph.selected;
      if (!sel) return;
      if (id === 'minus') st.amount = Math.max(1, st.amount - (shift ? 10 : 1));
      if (id === 'plus') st.amount = Math.min(99, st.amount + (shift ? 10 : 1));
      if (id === 'max') st.amount = Math.max(1, Math.min(99, G.maxCrafts(sel.item, st.inv)));
      if (id === 'craft') craft(shift ? -1 : st.amount);
      if (id === 'prev') cycle(sel, -1);
      if (id === 'next') cycle(sel, 1);
    }

    canvas.addEventListener('pointerdown', function (ev) {
      if (ev.button !== 0 || !view) return;
      st.mouse = units(ev);
      hover();
      st.press = { lx: st.mouse.lx, ly: st.mouse.ly, node: st.hover && st.hover.node || null, widget: st.widget, well: inWell(st.mouse) };
      canvas.setPointerCapture(ev.pointerId);
      dirty = true;
    });
    canvas.addEventListener('pointermove', function (ev) {
      if (!view) return;
      st.mouse = units(ev);
      var p = st.press;
      if (p && p.node && !st.dragging && graph && Math.abs(st.mouse.lx - p.lx) + Math.abs(st.mouse.ly - p.ly) > 3) {
        st.dragging = p.node;
        graph.grab(p.node);
      }
      if (st.dragging) graph.moveTo(st.dragging, st.mouse.lx, st.mouse.ly);
      hover();
    });
    canvas.addEventListener('pointerup', function (ev) {
      var p = st.press;
      st.press = null;
      if (st.dragging) { graph.drop(st.dragging); st.dragging = null; hover(); return; }
      if (!p || !graph || !view) return;
      st.mouse = units(ev);
      hover();
      if (p.node) { if (st.hover && st.hover.node === p.node) clickNode(p.node, ev.shiftKey); }
      else if (p.widget) { if (st.widget === p.widget) clickWidget(p.widget, ev.shiftKey); }
      else if (p.well && inWell(st.mouse) && !(st.hover && st.hover.node)) { graph.click(null); settleNow(); }
      dirty = true;
    });
    canvas.addEventListener('pointerleave', function () { if (!st.press) { st.mouse = null; hover(); dirty = true; } });
    canvas.addEventListener('wheel', function (ev) {
      if (!graph || !st.hover) return;
      var step = ev.deltaY < 0 ? -1 : 1;
      if (st.hover.node && cycle(st.hover.node, step)) { ev.preventDefault(); return; }
      if (st.hover.widget === 'count' && graph.selected) {
        ev.preventDefault();
        st.amount = Math.max(1, Math.min(99, st.amount - step));
        dirty = true;
      } else if ((st.hover.widget === 'prev' || st.hover.widget === 'next') && cycle(graph.selected, step)) {
        ev.preventDefault();
      }
    }, { passive: false });
    canvas.addEventListener('keydown', function (ev) {
      if (ev.key === 'Escape' && graph && (graph.selected || graph.openGroup || graph.open)) {
        ev.preventDefault(); graph.click(null); settleNow(); dirty = true;
      }
    });

    // ------------------------------------------------------------------------------------- the loop

    function frame(now) {
      if (!running) return;
      requestAnimationFrame(frame);
      acc += Math.min(250, now - (lastTime || now));
      lastTime = now;
      for (var steps = 0; acc >= 1000 / 60; steps++) {
        acc -= 1000 / 60;
        if (steps > 8) { acc = 0; break; }
        if (graph && graph.moving()) { graph.tick(); dirty = true; }
        if (st.flies.length) {
          st.flies.forEach(function (f) { f.t += 1 / M.fly; });
          st.flies = st.flies.filter(function (f) { return f.t < 1; });
          dirty = true;
        }
        if (st.note && --st.note.ttl <= 0) { st.note = null; dirty = true; }
      }
      if (dirty) {
        if (graph && graph.moving() && st.mouse) hover();
        draw();
      }
    }

    if (window.ResizeObserver) new ResizeObserver(function () { if (running) layout(); }).observe(frameEl);
    window.addEventListener('resize', function () { if (running) layout(); });

    // --------------------------------------------------------------------------- page controls (not in game)

    var tabs = root.querySelector('.ws-tabs'), levelInput = root.querySelector('.ws-level input');
    var levelOut = root.querySelector('.ws-level output'), invEl = root.querySelector('.ws-inv');
    var fullButton = root.querySelector('.ws-full'), tableEl = (root.closest('section') || root).querySelector('.ws-table');

    payload.stations.forEach(function (s, i) {
      var b = document.createElement('button');
      b.type = 'button';
      b.setAttribute('role', 'tab');
      if (s.sigil) { var img = document.createElement('img'); img.src = s.sigil; img.alt = ''; b.appendChild(img); }
      b.appendChild(document.createTextNode(s.title));
      if (!s.data) { var small = document.createElement('small'); small.textContent = ' pending'; b.appendChild(small); }
      b.addEventListener('click', function () { useStation(i); });
      tabs.appendChild(b);
    });

    function useStation(i) {
      st.station = i;
      st.amount = 1;
      st.note = null;
      st.flies = [];
      graph = station().data ? new G.Graph(station().data, style) : null;
      if (graph) graph.level = st.level;
      loadFrame();
      Array.prototype.forEach.call(tabs.children, function (b, j) { b.setAttribute('aria-selected', i === j ? 'true' : 'false'); });
      if (running) layout();
      dirty = true;
    }

    function loadFrame() {
      var meta = frameMeta();
      crest = null;
      if (meta) {
        crest = new Image();
        crest.onload = function () { dirty = true; };
        crest.src = meta.image;
      }
    }

    function pressed(group, value) {
      root.querySelectorAll('[data-control="' + group + '"] button').forEach(function (b) {
        b.setAttribute('aria-pressed', b.dataset.value === value ? 'true' : 'false');
      });
    }

    function preset(name) {
      st.preset = name;
      st.inv = {};
      var p = payload.presets[name] || {};
      for (var id in p) st.inv[id] = p[id];
      pressed('preset', name);
      inventory();
      dirty = true;
    }

    function level(value) {
      st.level = value;
      levelInput.value = value;
      levelOut.textContent = value;
      if (graph) graph.level = value;
      dirty = true;
    }

    function icon(id) {
      var cell = meta(id).icon || 0, cols = payload.atlas.cols;
      return '<i class="ico" style="background-position:' + (-(cell % cols) * 24) + 'px ' + (-Math.floor(cell / cols) * 24) + 'px"></i>';
    }

    /** The player's inventory under the frame; the item just changed flashes. */
    function inventory(changed) {
      var ids = Object.keys(st.inv).filter(function (id) { return st.inv[id] > 0; });
      if (!ids.length) { invEl.textContent = 'Inventory empty.'; return; }
      invEl.innerHTML = ids.map(function (id) {
        return '<span title="' + esc(meta(id).name) + '"' + (id === changed ? ' class="flash"' : '') + '>' + icon(id) + st.inv[id] + '</span>';
      }).join('');
    }

    function reset() {
      level(8);
      preset('starter');
      useStation(st.station);
    }

    function table() {
      if (!tableEl) return;
      var rows = [], armoury = payload.stations[0].data;
      armoury.categories.forEach(function (c) {
        rows.push('<tr class="ws-cat"><th colspan="4">' + esc(c.title) + ' <span class="muted">level ' + c.level +
                  (c.planned ? ', planned' : '') + '</span></th></tr>');
        c.items.forEach(function (family) {
          var v = family.variants[0];
          var cost = Object.keys(v.cost).map(function (id) {
            return '<span><b>' + v.cost[id] + '</b>' + icon(id) + esc(meta(id).name) + '</span>';
          }).join('');
          rows.push('<tr><td>' + icon(v.item) + esc(meta(v.item).name) + ((v.count || 1) > 1 ? ' <span class="muted">x' + v.count + '</span>' : '') +
                    '</td><td>' + G.levelOf(c, family, null) + '</td><td><span class="ws-cost">' + cost + '</span></td><td>' +
                    Object.keys(v.cost).reduce(function (sum, id) { return sum + v.cost[id]; }, 0) + '</td></tr>');
        });
      });
      tableEl.querySelector('tbody').innerHTML = rows.join('');
    }
    function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }

    levelInput.addEventListener('input', function () { level(+levelInput.value); });
    root.querySelectorAll('[data-control="preset"] button').forEach(function (b) { b.addEventListener('click', function () { preset(b.dataset.value); }); });
    root.querySelector('.ws-reset').addEventListener('click', reset);
    fullButton.addEventListener('click', function () {
      if (document.fullscreenElement) document.exitFullscreen();
      else if (frameEl.requestFullscreen) frameEl.requestFullscreen();
    });
    document.addEventListener('fullscreenchange', function () {
      fullButton.textContent = document.fullscreenElement === frameEl ? 'Exit full screen' : 'Full screen (1:1)';
      layout();
    });

    // The atlas also paints the page's own icons (inventory, tables, review list) through one CSS rule.
    var rule = document.createElement('style');
    rule.textContent = '.ico{background-image:url(' + payload.atlas.image + ');background-size:' + payload.atlas.cols * 24 + 'px auto}';
    document.head.appendChild(rule);

    table();
    preset('starter');
    level(8);
    useStation(0);

    return {
      show: function (on) {
        if (on && !running) { running = true; lastTime = 0; layout(); requestAnimationFrame(frame); }
        if (!on) running = false;
      },
      icon: icon,
      /** For checks: the graph and the preview state. */
      state: function () { return { graph: graph, st: st, view: view }; }
    };
  }

  return { mount: mount };
})();
