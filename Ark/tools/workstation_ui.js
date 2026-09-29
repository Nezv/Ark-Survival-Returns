/*
 * Workstation screen preview (P14). Draws a tools/workstation_graph.js graph the way the mod's WorkstationScreen
 * will: laid out in design units (the panel is graph_style.panel.width x height), with the whole screen scaled so
 * the panel spans panel.widthFraction of the window. The canvas renders a whole number of device pixels per unit
 * and the browser only scales down, so item sprites and the vanilla bitmap font stay crisp. Drawing goes through a
 * GuiGraphics-sized set of calls (fill, item, text, tooltip), so the Java screen can port it call for call.
 *
 * ArkWorkstationUI.mount(root, payload) wires one preview: the canvas, the station tabs, the preview controls
 * (level, inventory, where crafted items go) and the recipe table. The payload comes from tools/showcase_recipes.py.
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

  // ------------------------------------------------------------------------------------------------ mount

  function mount(root, payload) {
    var style = payload.style, P = style.panel, pal = style.palette, M = style.motion, A = G.graphArea(style);
    var frameEl = root.querySelector('.ws-frame'), canvas = root.querySelector('.ws-canvas'), ctx = canvas.getContext('2d');
    var off = document.createElement('canvas'), g = off.getContext('2d');
    var font = new Font(payload.font), images = {}, world = document.querySelector('.hero > img.bg');
    var backdrop = document.createElement('canvas'), backdropKey = '';
    var view = null, running = false, dirty = true, lastTime = 0, acc = 0, widgets = [];
    var graph = null;
    font.image.onload = function () { dirty = true; };
    if (world && !world.complete) world.addEventListener('load', function () { backdropKey = ''; dirty = true; });
    var st = { station: 0, level: 8, preset: 'starter', inv: {}, mode: 'direct', tray: [null, null, null, null], amount: 1,
               mouse: null, hover: null, widget: null, press: null, dragging: null, flies: [], note: null };

    function station() { return payload.stations[st.station]; }
    function meta(id) { return payload.items[id] || { name: id, stack: 1 }; }
    function have(id) { return st.inv[id] || 0; }
    function nodeId(n) { return n.kind === 'category' ? n.cat.icon : n.kind === 'item' ? n.item.item : n.ingredient; }
    function enough(n) { return have(nodeId(n)) >= n.need * st.amount; }

    function image(id) {
      if (!images[id]) {
        images[id] = new Image();
        images[id].onload = function () { dirty = true; };
        images[id].src = meta(id).sprite || '';
      }
      return images[id];
    }

    // ------------------------------------------------------------------ GuiGraphics-sized drawing calls

    function fill(x0, y0, x1, y1, hex, alpha) {
      g.fillStyle = css(hex, alpha);
      g.fillRect(x0, y0, x1 - x0, y1 - y0);
    }
    function text(s, x, y, hex, shadow) { font.draw(g, s, x, y, hex, shadow); }
    function item(id, x, y) {
      var img = image(id);
      if (img.complete && img.naturalWidth) { g.drawImage(img, x, y, 16, 16); return; }
      fill(x, y, x + 16, y + 16, '#000000');                    // the missing-texture checker
      fill(x, y, x + 8, y + 8, '#f800f8'); fill(x + 8, y + 8, x + 16, y + 16, '#f800f8');
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

    function layout() {
      var rect = canvas.getBoundingClientRect(), dpr = window.devicePixelRatio || 1;
      if (!rect.width || !rect.height) return false;
      var panel = Math.min(rect.width * P.widthFraction, rect.height * P.maxHeightFraction * P.width / P.height);
      var scale = panel / P.width, k = Math.max(1, Math.min(8, Math.ceil(scale * dpr - 0.01)));
      var uw = Math.ceil(rect.width / scale), uh = Math.ceil(rect.height / scale);
      view = { dpr: dpr, scale: scale, k: k, uw: uw, uh: uh,
               px: Math.floor((uw - P.width) / 2), py: Math.floor((uh - P.height) / 2) };
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
      text(s.title, 8, 4, pal.title, false);
      var level = 'Level ' + st.level;
      text(level, w - 8 - font.width(level) + 1, 4, pal.level, true);
      // The graph well, sunk into the face.
      fill(A.x - 1, A.y - 1, A.x + A.w + 1, A.y + A.h + 1, pal.bevelLight);
      fill(A.x - 1, A.y - 1, A.x + A.w, A.y + A.h, pal.bevelDark);
      fill(A.x, A.y, A.x + A.w, A.y + A.h, pal.well);
      for (var x = A.x + 6; x < A.x + A.w - 2; x += 12) {
        for (var y = A.y + 6; y < A.y + A.h - 2; y += 12) fill(x, y, x + 1, y + 1, pal.wellDot);
      }
    }

    function ringColour(n, locked) {
      if (locked) return pal.faint;
      if (n === graph.selected) return pal.select;
      if (n.kind === 'category') return n.cat.color;
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
        else if (l.kind === 'item') { color = a.cat.color; alpha = 0.4; }
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
      var emphasis = n === graph.selected ? pal.select : n === graph.open ? n.cat.color : null;
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
      item(nodeId(n), -8, -8);
      if (locked) g.drawImage(shape(r - 1, 0, pal.dim), -r + 1, -r + 1);
      if (n.need) count(n.need * st.amount, -8, -8, enough(n) ? WHITE : pal.missing);
      g.restore();
    }

    /** Where everything in the craft bar sits, right to left; drawBar and the craft animation share it. */
    function bar() {
      var y = A.y + A.h, right = P.width - 8, o = { y: y, slot: { x: 8, y: y + 6 }, tray: [] };
      if (st.mode === 'tray') {
        for (var i = 0; i < 4; i++) o.tray.push({ x: right - 18 * (4 - i), y: y + 6, w: 18, h: 18 });
        right -= 4 * 18 + 6;
      }
      o.craft = { x: right - 34, y: y + 7, w: 34, h: 16 }; right -= 34 + 6;
      o.max = { x: right - 20, y: y + 9, w: 20, h: 12 }; right -= 21;
      o.plus = { x: right - 11, y: y + 9, w: 11, h: 12 }; right -= 12;
      o.count = { x: right - 18, y: y + 9, w: 18, h: 12 }; right -= 19;
      o.minus = { x: right - 11, y: y + 9, w: 11, h: 12 }; right -= 11;
      o.text = { x: 32, w: right - 6 - 32 };
      return o;
    }

    /** Why the current craft cannot happen, or '' when it can. */
    function blocker(times) {
      var sel = graph.selected;
      if (!graph.unlocked(sel)) return 'Needs level ' + G.levelOf(sel.cat, sel.item);
      for (var id in sel.item.cost) {
        var short = sel.item.cost[id] * times - have(id);
        if (short > 0) return 'Missing ' + short + ' ' + meta(id).name;
      }
      if (st.mode === 'tray' && !trayAdd(sel.item.item, (sel.item.count || 1) * times, true)) return 'Output tray is full';
      return '';
    }

    function drawBar() {
      widgets = [];
      var o = bar(), sel = graph.selected;
      slot(o.slot.x, o.slot.y);
      o.tray.forEach(function (t, i) {
        slot(t.x, t.y);
        var stack = st.tray[i];
        if (stack) { item(stack.id, t.x + 1, t.y + 1); if (stack.count > 1) count(stack.count, t.x + 1, t.y + 1, WHITE); }
        widgets.push({ id: 'tray' + i, x: t.x, y: t.y, w: 18, h: 18, enabled: !!stack });
      });
      if (!sel) {
        var hint = graph.open ? 'Pick an item to see its cost' : 'Pick a material to see its items';
        text(font.fit(hint, P.width - 40 - (o.tray.length ? 78 : 0)), 32, o.y + 8, pal.muted, false);
        text(font.fit('Names show on hover; dimmed needs a higher level', P.width - 40 - (o.tray.length ? 78 : 0)),
             32, o.y + 18, pal.faint, false);
        return;
      }
      var entry = sel.item, info = meta(entry.item), locked = !graph.unlocked(sel), max = G.maxCrafts(entry, st.inv);
      item(entry.item, o.slot.x + 1, o.slot.y + 1);
      text(font.fit(info.name, o.text.w), o.text.x, o.y + 7, locked ? pal.muted : pal.title, false);
      var sub = locked ? ['Needs level ' + G.levelOf(sel.cat, entry), pal.missing]
          : info.planned ? ['Planned item', pal.planned]
          : [((entry.count || 1) > 1 ? 'Makes ' + entry.count + ' · have ' : 'Have ') + have(entry.item), pal.muted];
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
      button('craft', o.craft, 'Craft', !blocker(st.amount));
    }

    function drawPending() {
      widgets = [];
      var s = station(), cx = P.width / 2, lines = font.wrap(s.about, 220);
      text('Design pending', Math.round(cx - (font.width('Design pending') - 1) / 2), A.y + 52, pal.title, false);
      lines.forEach(function (line, i) {
        text(line, Math.round(cx - (font.width(line) - 1) / 2), A.y + 70 + i * 10, pal.muted, false);
      });
      text('Its recipes stay on the grid until then.', 32, A.y + A.h + 12, pal.faint, false);
    }

    function drawFlies() {
      st.flies.forEach(function (f) {
        var t = ease(f.t), x = f.x0 + (f.x1 - f.x0) * t, y = f.y0 + (f.y1 - f.y0) * t - 14 * Math.sin(Math.PI * f.t);
        g.save();
        g.globalAlpha = f.fade ? Math.max(0, 1 - f.t * f.t) : 1;
        item(f.id, x - 8, y - 8);
        g.restore();
      });
    }

    function tooltipLines() {
      var h = st.hover;
      if (!h || st.dragging) return null;
      if (h.node) {
        var n = h.node;
        if (n.kind === 'category') {
          var lines = [[n.cat.title, WHITE]];
          lines.push(graph.unlocked(n) ? [n.cat.items.length + ' recipes', GRAY] : ['Requires level ' + n.cat.level, pal.missing]);
          if (n.cat.planned) lines.push(['Planned: Iron Age', pal.planned]);
          return lines;
        }
        var id = nodeId(n), info = meta(id), out = [[info.name, WHITE]];
        if (n.kind === 'item') {
          if (!graph.unlocked(n)) out.push(['Requires level ' + G.levelOf(n.cat, n.item), pal.missing]);
          else if (n === graph.selected) out.push(['Click again to craft', GRAY]);
          if ((n.item.count || 1) > 1) out.push(['Makes ' + n.item.count, GRAY]);
          if (info.planned) out.push(['Planned item', pal.planned]);
        }
        if (n.need) out.push(['Have ' + have(id) + ' / ' + n.need * st.amount, enough(n) ? GRAY : pal.missing]);
        return out;
      }
      var sel = graph && graph.selected;
      if (/^tray/.test(h.widget)) {
        var stack = st.tray[+h.widget.slice(4)];
        return stack ? [[meta(stack.id).name + (stack.count > 1 ? ' x' + stack.count : ''), WHITE], ['Click to take', GRAY]] : null;
      }
      if (!sel) return null;
      if (h.widget === 'craft') {
        var why = blocker(st.amount), made = (sel.item.count || 1) * st.amount;
        return [['Craft ' + (made > 1 ? made + ' ' : '') + meta(sel.item.item).name, WHITE]].concat(why ? [[why, pal.missing]] : [['Shift: all you can', GRAY]]);
      }
      if (h.widget === 'max') return [['As many as you can make', WHITE], [String(G.maxCrafts(sel.item, st.inv)), GRAY]];
      if (h.widget === 'count') return [['Amount', WHITE], ['Scroll or use - and +', GRAY]];
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

    /** Puts `amount` of an item into the output tray (stacks first, then empty slots); dry runs only check. */
    function trayAdd(id, amount, dry) {
      var stack = meta(id).stack || 1, tray = dry ? st.tray.map(function (s) { return s && { id: s.id, count: s.count }; }) : st.tray;
      for (var i = 0; i < tray.length && amount > 0; i++) {
        if (tray[i] && tray[i].id === id && tray[i].count < stack) {
          var move = Math.min(stack - tray[i].count, amount);
          tray[i].count += move; amount -= move;
        }
      }
      for (i = 0; i < tray.length && amount > 0; i++) {
        if (!tray[i]) { tray[i] = { id: id, count: Math.min(stack, amount) }; amount -= tray[i].count; }
      }
      return amount <= 0;
    }

    function take(i) {
      var stack = st.tray[i];
      if (!stack) return;
      st.inv[stack.id] = have(stack.id) + stack.count;
      st.tray[i] = null;
      inventory(stack.id);
    }

    function note(message, color) { st.note = { text: message, color: color || pal.missing, ttl: 90 }; }

    function craft(times) {
      var sel = graph.selected;
      if (!sel) return;
      var entry = sel.item, max = G.maxCrafts(entry, st.inv);
      if (times < 0) times = Math.max(1, max);
      var why = blocker(times);
      if (why) { sel.shake = M.shake; note(why); return; }
      var made = (entry.count || 1) * times, o = bar(), target;
      G.pay(entry, times, st.inv);
      if (st.mode === 'tray') {
        trayAdd(entry.item, made, false);
        for (var i = 0; i < 4; i++) if (st.tray[i] && st.tray[i].id === entry.item) target = o.tray[i];
      } else {
        st.inv[entry.item] = have(entry.item) + made;
      }
      graph.nodes.forEach(function (n) { if (n.need) n.pulse = M.pulse; });
      sel.pulse = M.pulse;
      note('Crafted ' + (made > 1 ? made + ' ' : '') + meta(entry.item).name, pal.craftable);
      if (!REDUCED) {
        st.flies.push({ id: entry.item, x0: sel.x, y0: sel.y, t: 0,
                        x1: target ? target.x + 9 : P.width - 12, y1: target ? target.y + 9 : P.height - 10, fade: !target });
      }
      inventory(entry.item);
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
      if (result === 'locked') note(node.kind === 'category' ? 'Requires level ' + node.cat.level : 'Requires level ' + G.levelOf(node.cat, node.item));
      settleNow();
    }

    function clickWidget(id, shift) {
      var sel = graph.selected;
      if (/^tray/.test(id)) {
        if (shift) for (var i = 0; i < 4; i++) take(i); else take(+id.slice(4));
        return;
      }
      if (!sel) return;
      if (id === 'minus') st.amount = Math.max(1, st.amount - (shift ? 10 : 1));
      if (id === 'plus') st.amount = Math.min(99, st.amount + (shift ? 10 : 1));
      if (id === 'max') st.amount = Math.max(1, Math.min(99, G.maxCrafts(sel.item, st.inv)));
      if (id === 'craft') craft(shift ? -1 : st.amount);
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
      if (!graph || !graph.selected || !st.hover || st.hover.widget !== 'count') return;
      ev.preventDefault();
      st.amount = Math.max(1, Math.min(99, st.amount + (ev.deltaY < 0 ? 1 : -1)));
      dirty = true;
    }, { passive: false });
    canvas.addEventListener('keydown', function (ev) {
      if (ev.key === 'Escape' && graph && (graph.selected || graph.open)) { ev.preventDefault(); graph.click(null); settleNow(); dirty = true; }
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
      b.textContent = s.title;
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
      Array.prototype.forEach.call(tabs.children, function (b, j) { b.setAttribute('aria-selected', i === j ? 'true' : 'false'); });
      dirty = true;
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

    function mode(value) {
      if (value === 'direct') for (var i = 0; i < 4; i++) take(i);
      st.mode = value;
      pressed('mode', value);
      dirty = true;
    }

    function level(value) {
      st.level = value;
      levelInput.value = value;
      levelOut.textContent = value;
      if (graph) graph.level = value;
      dirty = true;
    }

    /** The player's inventory under the frame; the item just changed flashes. */
    function inventory(changed) {
      invEl.innerHTML = '';
      var ids = Object.keys(st.inv).filter(function (id) { return st.inv[id] > 0; });
      if (!ids.length) { invEl.textContent = 'Inventory empty.'; return; }
      ids.forEach(function (id) {
        var chip = document.createElement('span'), img = document.createElement('img');
        img.src = meta(id).sprite || '';
        img.alt = '';
        chip.title = meta(id).name;
        chip.appendChild(img);
        chip.appendChild(document.createTextNode(st.inv[id]));
        if (id === changed) chip.className = 'flash';
        invEl.appendChild(chip);
      });
    }

    function reset() {
      level(8);
      mode('direct');
      st.tray = [null, null, null, null];
      preset('starter');
      useStation(st.station);
    }

    function table() {
      if (!tableEl) return;
      var rows = [];
      payload.stations.forEach(function (s) {
        if (!s.data) return;
        s.data.categories.forEach(function (c) {
          rows.push('<tr class="ws-cat"><th colspan="4">' + esc(c.title) + ' <span class="muted">level ' + c.level +
                    (c.planned ? ', planned' : '') + '</span></th></tr>');
          c.items.forEach(function (it) {
            var cost = Object.keys(it.cost).map(function (id) {
              return '<span><b>' + it.cost[id] + '</b>' + icon(id) + esc(meta(id).name) + '</span>';
            }).join('');
            rows.push('<tr><td>' + icon(it.item) + esc(meta(it.item).name) + ((it.count || 1) > 1 ? ' <span class="muted">x' + it.count + '</span>' : '') +
                      '</td><td>' + G.levelOf(c, it) + '</td><td><span class="ws-cost">' + cost + '</span></td><td>' +
                      Object.keys(it.cost).reduce(function (sum, id) { return sum + it.cost[id]; }, 0) + '</td></tr>');
          });
        });
      });
      tableEl.querySelector('tbody').innerHTML = rows.join('');
    }
    function icon(id) { return '<img src="' + (meta(id).sprite || '') + '" alt="">'; }
    function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }

    levelInput.addEventListener('input', function () { level(+levelInput.value); });
    root.querySelectorAll('[data-control="preset"] button').forEach(function (b) { b.addEventListener('click', function () { preset(b.dataset.value); }); });
    root.querySelectorAll('[data-control="mode"] button').forEach(function (b) { b.addEventListener('click', function () { mode(b.dataset.value); }); });
    root.querySelector('.ws-reset').addEventListener('click', reset);
    fullButton.addEventListener('click', function () {
      if (document.fullscreenElement) document.exitFullscreen();
      else if (frameEl.requestFullscreen) frameEl.requestFullscreen();
    });
    document.addEventListener('fullscreenchange', function () {
      fullButton.textContent = document.fullscreenElement === frameEl ? 'Exit full screen' : 'Full screen (1:1)';
      layout();
    });

    table();
    preset('starter');
    level(8);
    useStation(0);

    return {
      show: function (on) {
        if (on && !running) { running = true; lastTime = 0; layout(); requestAnimationFrame(frame); }
        if (!on) running = false;
      },
      /** For checks: the graph and the preview state. */
      state: function () { return { graph: graph, st: st, view: view }; }
    };
  }

  return { mount: mount };
})();
