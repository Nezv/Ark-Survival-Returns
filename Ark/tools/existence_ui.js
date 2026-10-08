/*
 * The existence simulator of the showcase's Existence Model page: one biome region's wildlife register lived through
 * beyond the loaded land, without the game. It runs tools/existence_model.js on what
 * tools/showcase_existence.py assembled (the species, who lives in each sample biome, the weights) and draws
 * the life of a record with the weight of every connection, who lives in the region day by day, the pressure
 * on what each kind eats, and a verdict: does the register stay bounded, alive and settled.
 * Weights changed here stay in this browser until Save weights writes design/existence/weights.json.
 *
 * ArkExistenceUI.mount(root, data) wires it; show(visible) runs it the first time its page is opened.
 */
window.ArkExistenceUI = (function () {
  'use strict';
  var M = window.ArkExistence;
  var NS = 'http://www.w3.org/2000/svg';
  var STORE = 'ark-existence-weights';
  // A class keeps its colour whatever else is on the chart; the five were checked for colour-blind separation on both themes.
  var KIND = {
    GRAZER: { label: 'Plant eaters', color: 'var(--s1)' },
    HUNTER: { label: 'Hunters', color: 'var(--s2)' },
    APEX: { label: 'Giants', color: 'var(--s3)' },
    FLYER: { label: 'Flyers', color: 'var(--s4)' },
    SEA: { label: 'Sea animals', color: 'var(--s5)' }
  };
  // What each kind of eater lives on, on land and in the sea (the diets of tools/existence_model.js).
  var ROLE = {
    grazer: { label: 'Plant eaters', eats: 'what the land grows', sea: 'a share of what the sea grows', color: 'var(--s1)' },
    hunter: { label: 'Hunters', eats: 'what the plant eaters carry', sea: 'a share of the sea, and what plant eaters carry', color: 'var(--s2)' },
    apex: { label: 'Giants', eats: 'what plant eaters and hunters carry', sea: 'a share of the sea, and what the others carry', color: 'var(--s3)' },
    flyer: { label: 'Flyers', eats: 'a share of what the land grows', sea: '', color: 'var(--s4)' }
  };
  var GROUPS = { land: 'The land', life: 'A life', hunt: 'Hunters' };

  function el(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text != null) node.textContent = text;
    return node;
  }
  function svg(tag, attrs, text) {
    var node = document.createElementNS(NS, tag);
    for (var key in attrs) node.setAttribute(key, attrs[key]);
    if (text != null) node.textContent = text;
    return node;
  }
  function num(x) { return x >= 100 ? String(Math.round(x)) : x >= 10 ? x.toFixed(0) : x.toFixed(1); }
  function pct(x) { return Math.round(x * 100) + '%'; }
  /** A daily odd as a reader says it: a share of the days, or a count a day once it passes one. */
  function rate(r) {
    if (!(r > 0)) return 'never';
    if (r >= 1) return r.toFixed(1) + ' a day';
    if (r >= 0.0995) return Math.round(r * 100) + '% a day';
    if (r >= 0.00095) return (r * 100).toFixed(1) + '% a day';
    return '<0.1% a day';
  }
  function avg(list, from) {
    var sum = 0, n = 0;
    for (var i = from || 0; i < list.length; i++) { sum += list[i]; n++; }
    return n ? sum / n : 0;
  }

  /** Round ticks for an axis that starts at zero. */
  function ticks(top) {
    var raw = (top || 1) / 4, power = Math.pow(10, Math.floor(Math.log(raw) / Math.LN10)), unit = raw / power;
    var step = (unit <= 1 ? 1 : unit <= 2 ? 2 : unit <= 2.5 ? 2.5 : unit <= 5 ? 5 : 10) * power, out = [];
    for (var v = 0; v < top + step * 0.999; v += step) out.push(+v.toFixed(6));
    return out;
  }

  /**
   * A line a series over the days, with the band most runs stay in, values at the line ends, a crosshair that reads
   * every series at a day (pointer or arrow keys), and an optional line for a bound.
   * spec: days, series [{label, color, values, low, high}], label, bound {y, label}, digits.
   */
  function lineChart(host, spec) {
    if (!host.clientWidth) return;  // its panel is not the one on show: drawn once it is
    host.textContent = '';
    var width = Math.max(280, host.clientWidth), height = spec.height || 220;
    var longest = spec.series.reduce(function (n, s) { return Math.max(n, s.label.length); }, spec.bound ? spec.bound.label.length - 4 : 0);
    var pad = { l: 34, r: 46 + longest * 6.4, t: 10, b: 24 }, w = width - pad.l - pad.r, h = height - pad.t - pad.b;
    var days = spec.days, last = days.length - 1, span = days[last] || 1, top = spec.bound ? spec.bound.y * 1.2 : 0;
    spec.series.forEach(function (s) { (s.high || s.values).forEach(function (v) { if (v > top) top = v; }); });
    var ys = ticks(top || 1), ymax = ys[ys.length - 1];
    function X(day) { return pad.l + day / span * w; }
    function Y(value) { return pad.t + h - value / ymax * h; }
    var fmt = function (v) { return spec.digits ? v.toFixed(spec.digits) : num(v); };
    var root = svg('svg', { viewBox: '0 0 ' + width + ' ' + height, width: width, height: height, role: 'img', tabindex: 0,
      'aria-label': spec.label + '. Arrow keys read it day by day; the table below holds the same numbers.' });
    ys.forEach(function (v) {
      root.appendChild(svg('line', { x1: pad.l, x2: pad.l + w, y1: Y(v), y2: Y(v), 'class': v === 0 ? 'ex-axis' : 'ex-grid' }));
      root.appendChild(svg('text', { x: pad.l - 6, y: Y(v) + 4, 'text-anchor': 'end', 'class': 'ex-tick' }, spec.digits ? v.toFixed(1) : String(v)));
    });
    var every = [10, 30, 60, 90, 120, 180, 360, 720, 1440].filter(function (s) { return span / s <= 6; })[0] || 2880;
    for (var d = 0; d <= span + 0.5; d += every) {
      root.appendChild(svg('text', { x: X(d), y: height - 6, 'text-anchor': d === 0 ? 'start' : 'middle', 'class': 'ex-tick' }, d === 0 ? 'day 0' : String(d)));
    }
    if (spec.bound) {
      root.appendChild(svg('line', { x1: pad.l, x2: pad.l + w, y1: Y(spec.bound.y), y2: Y(spec.bound.y), 'class': 'ex-bound' }));
      root.appendChild(svg('text', { x: pad.l + w + 8, y: Y(spec.bound.y) + 4, 'class': 'ex-tick' }, spec.bound.label));
    }
    spec.series.forEach(function (s) {
      if (s.low && s.high) {
        var path = 'M' + s.high.map(function (v, i) { return X(days[i]).toFixed(1) + ' ' + Y(v).toFixed(1); }).join('L');
        for (var i = last; i >= 0; i--) path += 'L' + X(days[i]).toFixed(1) + ' ' + Y(s.low[i]).toFixed(1);
        root.appendChild(svg('path', { d: path + 'Z', fill: s.color, 'class': 'ex-band' }));
      }
    });
    spec.series.forEach(function (s) {
      root.appendChild(svg('polyline', { points: s.values.map(function (v, i) { return X(days[i]).toFixed(1) + ',' + Y(v).toFixed(1); }).join(' '),
        stroke: s.color, 'class': 'ex-line' }));
    });
    // The value each line ends at, beside it; labels that would touch are moved apart and tied back with a thin lead.
    var ends = spec.series.map(function (s) { return { s: s, at: Y(s.values[last]), y: Y(s.values[last]) }; })
      .sort(function (a, b) { return a.at - b.at; });
    for (var k = 1; k < ends.length; k++) if (ends[k].y < ends[k - 1].y + 15) ends[k].y = ends[k - 1].y + 15;
    var over = ends.length ? ends[ends.length - 1].y - (pad.t + h) : 0;
    if (over > 0) ends.forEach(function (e) { e.y -= over; });
    for (k = ends.length - 2; k >= 0; k--) if (ends[k].y > ends[k + 1].y - 15) ends[k].y = ends[k + 1].y - 15;
    ends.forEach(function (e) {
      if (Math.abs(e.y - e.at) > 2) root.appendChild(svg('line', { x1: pad.l + w + 1, y1: e.at, x2: pad.l + w + 7, y2: e.y, 'class': 'ex-lead' }));
      var text = svg('text', { x: pad.l + w + 9, y: e.y + 4, 'class': 'ex-end' });
      text.appendChild(svg('tspan', { 'class': 'ex-end-value' }, fmt(e.s.values[last])));
      text.appendChild(svg('tspan', { dx: 4 }, e.s.label));
      root.appendChild(text);
    });
    var hair = svg('line', { y1: pad.t, y2: pad.t + h, 'class': 'ex-hair', visibility: 'hidden' });
    root.appendChild(hair);
    var dots = spec.series.map(function (s) {
      var dot = svg('circle', { r: 4, fill: s.color, 'class': 'ex-dot', visibility: 'hidden' });
      root.appendChild(dot);
      return dot;
    });
    host.appendChild(root);
    var tip = el('div', 'ex-tip');
    tip.hidden = true;
    host.appendChild(tip);
    var at = -1;
    function read(index) {
      at = Math.max(0, Math.min(last, index));
      hair.setAttribute('x1', X(days[at])); hair.setAttribute('x2', X(days[at])); hair.setAttribute('visibility', 'visible');
      tip.textContent = '';
      tip.appendChild(el('b', '', 'Day ' + Math.round(days[at])));
      spec.series.forEach(function (s, i) {
        dots[i].setAttribute('cx', X(days[at])); dots[i].setAttribute('cy', Y(s.values[at])); dots[i].setAttribute('visibility', 'visible');
        var row = el('span', 'ex-tip-row');
        var key = el('i'); key.style.background = s.color; row.appendChild(key);
        row.appendChild(el('strong', '', fmt(s.values[at])));
        row.appendChild(el('span', '', s.label + (s.low ? ' (most runs ' + fmt(s.low[at]) + ' to ' + fmt(s.high[at]) + ')' : '')));
        tip.appendChild(row);
      });
      tip.hidden = false;
      var x = X(days[at]), room = width - x;
      tip.style.left = room > 230 ? (x + 12) + 'px' : '';
      tip.style.right = room > 230 ? '' : (width - x + 12) + 'px';
    }
    function leave() {
      at = -1;
      hair.setAttribute('visibility', 'hidden');
      dots.forEach(function (dot) { dot.setAttribute('visibility', 'hidden'); });
      tip.hidden = true;
    }
    root.addEventListener('pointermove', function (ev) {
      var box = root.getBoundingClientRect(), day = (ev.clientX - box.left - pad.l) / w * span, best = 0;
      for (var i = 1; i <= last; i++) if (Math.abs(days[i] - day) < Math.abs(days[best] - day)) best = i;
      read(best);
    });
    root.addEventListener('pointerleave', leave);
    root.addEventListener('blur', leave);
    root.addEventListener('focus', function () { read(last); });
    root.addEventListener('keydown', function (ev) {
      if (ev.key !== 'ArrowLeft' && ev.key !== 'ArrowRight') return;
      ev.preventDefault();
      read((at < 0 ? last : at) + (ev.key === 'ArrowLeft' ? -1 : 1));
    });
  }

  function legend(host, series) {
    host.textContent = '';
    if (series.length < 2) return;
    series.forEach(function (s) {
      var item = el('span');
      var key = el('i'); key.style.background = s.color;
      item.appendChild(key);
      item.appendChild(document.createTextNode(s.label));
      host.appendChild(item);
    });
  }

  // What sets each connection, under the proposal and under the rules the game runs today.
  var CAPTION = {
    bounded: { worse: '1 \u2212 meal', better: 'meal', arrives: 'refill, wanderers', born: 'fecundity \u00f7 span \u00d7 room', hunted: 'kill \u00d7 tempo \u00d7 \u03a3 p',
      age: 'its span', starves: 'starve' },
    coded: { worse: 'appetite', better: 'a kill', arrives: 'refill', born: 'birth \u00d7 water \u00d7 (1 \u2212 hunters)', hunted: 'odds of strength',
      age: 'its span', starves: 'starves', struck: 'prey strikes back' }
  };

  /**
   * The existence state machine: what a record is between two rounds (fed, hungry, starving), how it comes to the
   * region and how it ends. Each connection carries how often it was taken per animal and day in the runs, and under
   * it what sets it.
   */
  function diagram(host, edges, rules) {
    host.textContent = '';
    var caption = CAPTION[rules];
    var root = svg('svg', { viewBox: '0 0 720 316', role: 'img', 'aria-label': 'The existence state machine with the weight of every connection; the list below it reads the same numbers.' });
    var states = [{ x: 80, name: 'Fed' }, { x: 294, name: 'Hungry' }, { x: 508, name: 'Starving' }];
    function thick(r) { return r > 0 ? 1.25 + 2.75 * Math.max(0, Math.min(1, (Math.log(r) / Math.LN10 + 4) / 4)) : 1; }
    function arrow(x1, y1, x2, y2, r, both) {
      var dx = x2 - x1, dy = y2 - y1, len = Math.sqrt(dx * dx + dy * dy), ux = dx / len, uy = dy / len, t = thick(r), head = 5 + t;
      var g = svg('g', { 'class': 'ex-edge' + (r > 0 || r === null ? '' : ' off') });
      g.appendChild(svg('line', { x1: x1 + (both ? ux * head : 0), y1: y1 + (both ? uy * head : 0), x2: x2 - ux * head, y2: y2 - uy * head, 'stroke-width': t }));
      function tip(x, y, sx, sy) {
        g.appendChild(svg('polygon', { points: [x, y, x - sx * head * 1.5 - sy * head * 0.7, y - sy * head * 1.5 + sx * head * 0.7,
          x - sx * head * 1.5 + sy * head * 0.7, y - sy * head * 1.5 - sx * head * 0.7].map(function (v) { return v.toFixed(1); }).join(' ') }));
      }
      tip(x2, y2, ux, uy);
      if (both) tip(x1, y1, -ux, -uy);
      root.appendChild(g);
    }
    function label(x, y, text, anchor, cls) { root.appendChild(svg('text', { x: x, y: y, 'text-anchor': anchor || 'start', 'class': cls || 'ex-weight' }, text)); }
    function pill(cx, y, text, cls) {
      var w = Math.max(96, text.length * 7.4 + 26);
      root.appendChild(svg('rect', { x: cx - w / 2, y: y, width: w, height: 28, rx: 14, 'class': 'ex-pill ' + (cls || '') }));
      label(cx, y + 18.5, text, 'middle', 'ex-pill-text');
    }
    root.appendChild(svg('rect', { x: 60, y: 84, width: 600, height: 134, rx: 14, 'class': 'ex-frame' }));
    label(74, 102, 'A RECORD BEYOND THE LOADED LAND', 'start', 'ex-frame-label');
    // Where a record comes from: its body unloads, a group arrives, a young is born.
    pill(137, 14, 'Body near a player');
    arrow(137, 42, 137, 84, null, true);
    label(147, 60, 'the chunk unloads');
    label(147, 75, 'and loads again', 'start', 'ex-caption');
    pill(382, 14, 'Arrives');
    arrow(382, 42, 382, 84, edges.arrived);
    label(392, 60, rate(edges.arrived));
    label(392, 75, caption.arrives, 'start', 'ex-caption');
    pill(574, 14, 'Is born');
    arrow(574, 42, 574, 84, edges.born);
    label(584, 60, rate(edges.born));
    label(584, 75, caption.born, 'start', 'ex-caption');
    states.forEach(function (s, i) {
      root.appendChild(svg('rect', { x: s.x, y: 128, width: 132, height: 64, rx: 10, 'class': 'ex-state' }));
      label(s.x + 66, 156, s.name, 'middle', 'ex-state-name');
      label(s.x + 66, 175, edges.days ? pct(edges.share[i]) + ' of its days' : 'no days', 'middle', 'ex-state-share');
    });
    // A step hungrier along the top, a step better fed along the bottom.
    [[0, 1], [1, 2]].forEach(function (pair) {
      var a = states[pair[0]], b = states[pair[1]], mid = (a.x + 132 + b.x) / 2;
      label(mid, 123, caption.worse, 'middle', 'ex-caption');
      label(mid, 140, rate(edges.moves[pair[0]][pair[1]]), 'middle');
      arrow(a.x + 132, 149, b.x, 149, edges.moves[pair[0]][pair[1]]);
      arrow(b.x, 171, a.x + 132, 171, edges.moves[pair[1]][pair[0]]);
      label(mid, 188, rate(edges.moves[pair[1]][pair[0]]), 'middle');
      label(mid, 203, caption.better, 'middle', 'ex-caption');
    });
    // The coded rules move a pack from starving to fed in one kill.
    if (edges.moves[2][0] > 0 || edges.moves[0][2] > 0) {
      label(360, 212, 'starving to fed at one kill: ' + rate(edges.moves[2][0]), 'middle', 'ex-caption');
    }
    // How a life as a record ends.
    var ends = [{ cx: 137, name: 'Hunted', r: edges.deaths[2], by: caption.hunted }, { cx: 300, name: 'Dies of age', r: edges.deaths[0], by: caption.age }];
    if (caption.struck) ends.push({ cx: 440, name: 'Struck down', r: edges.deaths[3], by: caption.struck });
    ends.push({ cx: 574, name: 'Starves', r: edges.deaths[1], by: caption.starves, from: 192 });
    ends.forEach(function (end) {
      arrow(end.cx, end.from || 218, end.cx, 274, end.r);
      label(end.cx + 10, 242, rate(end.r));
      label(end.cx + 10, 257, end.by, 'start', 'ex-caption');
      pill(end.cx, 274, end.name, 'end');
    });
    host.appendChild(root);
  }

  function mount(root, data) {
    var q = function (sel) { return root.querySelector(sel); };
    var weights = data.model.weights, defaults = {}, overrides = {};
    weights.forEach(function (w) { defaults[w.id] = w.value; });
    try { overrides = JSON.parse(localStorage.getItem(STORE) || '{}') || {}; } catch (err) { overrides = {}; }
    Object.keys(overrides).forEach(function (id) { if (defaults[id] === undefined || overrides[id] === defaults[id]) delete overrides[id]; });
    var state = { region: data.regions[0].id, cells: 256, zone: 3, water: null, fertility: null, start: 'settled', days: 720, pace: 1, rules: 'bounded',
      arrivals: true, seed: 1, focus: null };
    var result = null, ran = false, visible = false, timer = 0;

    function region() { return data.regions.filter(function (r) { return r.id === state.region; })[0]; }
    function store() { try { localStorage.setItem(STORE, JSON.stringify(overrides)); } catch (err) { /* private window */ } }
    function options(extra) {
      var r = region(), o = { region: r, cells: state.cells, zone: state.zone, water: state.water == null ? r.water : state.water,
        fertility: state.fertility == null ? r.fertility : state.fertility, start: state.start, days: state.days, pace: state.pace, rules: state.rules,
        arrivals: state.arrivals, seed: state.seed, weights: overrides };
      for (var key in extra || {}) o[key] = extra[key];
      return o;
    }
    /** Fewer runs for a large region over a long span, so a change still answers at once. */
    function runsFor(o) { return Math.max(8, Math.min(24, Math.round(24 * 256 * 720 / (o.cells * o.days)))); }

    // ------------------------------------------------------------------------------------ controls
    var selects = {};
    Array.prototype.forEach.call(root.querySelectorAll('select[data-ex]'), function (select) { selects[select.dataset.ex] = select; });
    data.regions.forEach(function (r) {
      var option = el('option', '', r.name);
      option.value = r.id;
      selects.region.appendChild(option);
    });
    var fertility = q('input[data-ex="fertility"]'), fertilityOut = q('output[data-ex="fertility"]');
    function syncRegion() {
      var r = region();
      selects.water.value = (state.water == null ? r.water : state.water) ? '1' : '0';
      fertility.value = state.fertility == null ? r.fertility : state.fertility;
      fertilityOut.textContent = (+fertility.value).toFixed(2);
    }
    Object.keys(selects).forEach(function (key) {
      selects[key].addEventListener('change', function () {
        var value = selects[key].value;
        if (key === 'region') { state.region = value; state.water = null; state.fertility = null; state.focus = null; syncRegion(); }
        else if (key === 'water') state.water = value === '1';
        else state[key] = key === 'start' ? value : +value;
        run();
      });
    });
    fertility.addEventListener('input', function () {
      state.fertility = +fertility.value;
      fertilityOut.textContent = state.fertility.toFixed(2);
      run(160);
    });
    Array.prototype.forEach.call(root.querySelectorAll('[data-ex-seg]'), function (seg) {
      var key = seg.dataset.exSeg;
      Array.prototype.forEach.call(seg.querySelectorAll('button'), function (button) {
        button.addEventListener('click', function () {
          if (key === 'rules') {
            state.rules = button.dataset.value;
            // Each rule set starts as it stands in the game or in the proposal: today nothing arrives where nobody is near.
            state.arrivals = state.rules === 'bounded';
          } else state.arrivals = button.dataset.value === '1';
          press();
          run();
        });
      });
    });
    function press() {
      Array.prototype.forEach.call(root.querySelectorAll('[data-ex-seg] button'), function (button) {
        var key = button.parentElement.dataset.exSeg;
        button.setAttribute('aria-pressed', (key === 'rules' ? state.rules === button.dataset.value : state.arrivals === (button.dataset.value === '1')) ? 'true' : 'false');
      });
      q('.ex-weights').classList.toggle('idle', state.rules !== 'bounded');
      q('.ex-idle').hidden = state.rules === 'bounded';
    }
    q('[data-ex="draw"]').addEventListener('click', function () { state.seed++; run(); });
    // Statistics shows one of its panels at a time; a chart takes the width its panel has once it is on show.
    var stats = root.querySelectorAll('.ex-stat-tabs button');
    Array.prototype.forEach.call(stats, function (button) {
      button.addEventListener('click', function () {
        Array.prototype.forEach.call(stats, function (b) { b.setAttribute('aria-selected', b === button ? 'true' : 'false'); });
        Array.prototype.forEach.call(root.querySelectorAll('.ex-stat'), function (panel) { panel.hidden = panel.dataset.stat !== button.dataset.stat; });
        if (result) paintCharts();
      });
    });

    // ------------------------------------------------------------------------------------- weights
    var sliders = {};
    (function () {
      var host = q('.ex-sliders');
      Object.keys(GROUPS).forEach(function (group) {
        var box = el('div', 'ex-group');
        box.appendChild(el('span', 'label', GROUPS[group]));
        weights.filter(function (w) { return w.group === group; }).forEach(function (w) {
          var row = el('label', 'ex-weight-row');
          row.title = w.note;
          var head = el('span', 'ex-weight-head');
          head.appendChild(el('b', '', w.label));
          var out = el('output');
          head.appendChild(out);
          head.appendChild(el('small', 'muted', w.unit));
          var input = el('input');
          input.type = 'range'; input.min = w.min; input.max = w.max; input.step = w.step;
          row.appendChild(head);
          row.appendChild(input);
          row.appendChild(el('small', 'muted ex-weight-note', w.note));
          box.appendChild(row);
          sliders[w.id] = { input: input, out: out, row: row, weight: w };
          input.addEventListener('input', function () {
            var value = +input.value;
            if (value === w.value) delete overrides[w.id]; else overrides[w.id] = value;
            store();
            paintWeights();
            run(160);
          });
        });
        host.appendChild(box);
      });
    })();
    function paintWeights() {
      var changed = 0;
      Object.keys(sliders).forEach(function (id) {
        var s = sliders[id], value = overrides[id] === undefined ? s.weight.value : overrides[id];
        s.input.value = value;
        s.out.textContent = String(value);
        s.row.classList.toggle('changed', overrides[id] !== undefined);
        if (overrides[id] !== undefined) changed++;
      });
      q('.ex-changed').textContent = changed ? changed + ' changed here, kept in this browser' : 'As in design/existence/model.json';
      paintBalance();
    }
    /**
     * What the weights imply before any run. An animal lives three quarters of its span on average and has
     * fecundity x room young over a whole span, so a kind left alone replaces itself where 0.75 x fecundity x room = 1.
     * Its hunters take kill x tempo a day for each kind of them at its limit, and span x tempo is the same for every
     * species (the span of a 50 HP animal), so prey outbreeds two kinds of hunters at their limit while
     * kill < (fecundity - 4/3) / (2 x that span).
     */
    function paintBalance() {
      var value = function (id) { return overrides[id] === undefined ? defaults[id] : overrides[id]; };
      var span = data.model.coded.config.wildLifespanDays, fecundity = value('fecundity'), kill = value('kill'), list = q('.ex-balance');
      var alone = 1 - 4 / (3 * fecundity), safe = (fecundity - 4 / 3) / (2 * span);
      list.textContent = '';
      function item(ok, name, text) {
        var li = el('li');
        li.appendChild(el('b', '', (ok ? '✓ ' : '✗ ') + name));
        li.appendChild(document.createTextNode(' ' + text));
        list.appendChild(li);
      }
      item(alone > 0, 'A kind replaces itself', alone > 0
        ? 'and, left alone, settles at the pressure 1 \u2212 4 \u00f7 (3 \u00d7 fecundity) = ' + alone.toFixed(2) + '; hunters and wanderers move it from there.'
        : 'only with a fecundity above 4/3: an animal lives three quarters of its span on average.');
      item(kill < safe, 'Prey outbreeds its hunters', 'while kill is under (fecundity \u2212 4/3) \u00f7 ' + (2 * span) + ' = ' + safe.toFixed(3)
        + ', hunters and giants both at their limit; kill is ' + kill + (kill < safe ? '.' : ': prey that is cornered so is kept by arrivals alone.'));
    }
    function weightsJson() {
      var out = {};
      weights.forEach(function (w) { out[w.id] = overrides[w.id] === undefined ? w.value : overrides[w.id]; });
      return JSON.stringify({ about: 'Weights of the existence model as tuned on the showcase; tools/showcase_existence.py reads them over design/existence/model.json.',
        weights: out }, null, 2) + '\n';
    }
    function say(text) { q('.ex-saved').textContent = text; }
    function download(text) {
      var link = el('a');
      link.href = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
      link.download = 'weights.json';
      document.body.appendChild(link);
      link.click();
      link.remove();
      say('Downloaded weights.json. Put it at Ark/design/existence/weights.json and rebuild the showcase.');
    }
    q('.ex-reset').addEventListener('click', function () { overrides = {}; store(); paintWeights(); say(''); run(); });
    q('.ex-copy').addEventListener('click', function () {
      if (navigator.clipboard) navigator.clipboard.writeText(weightsJson()).then(function () { say('Copied the weights as JSON.'); }, function () { say('Copy failed: use Save weights.'); });
    });
    q('.ex-save').addEventListener('click', function () {
      var text = weightsJson();
      if (!window.showSaveFilePicker) return download(text);
      window.showSaveFilePicker({ suggestedName: 'weights.json', types: [{ description: 'Existence weights', accept: { 'application/json': ['.json'] } }] })
        .then(function (handle) {
          return handle.createWritable().then(function (w) { return w.write(text).then(function () { return w.close(); }); })
            .then(function () { say('Saved ' + handle.name + '. Put it at Ark/design/existence/weights.json and rebuild the showcase.'); });
        })
        .catch(function (err) { if (err && err.name !== 'AbortError') download(text); });
    });

    // ----------------------------------------------------------------------------------------- run
    function run(wait) {
      if (!visible) { ran = false; return; }
      clearTimeout(timer);
      q('.ex-status').textContent = 'Living it through...';
      root.classList.add('busy');
      timer = setTimeout(function () {
        var o = options(), began = Date.now();
        result = M.ensemble(data, o, runsFor(o));
        ran = true;
        root.classList.remove('busy');
        q('.ex-status').textContent = result.runs + ' runs of ' + o.days + ' game days in ' + (Date.now() - began) + ' ms';
        paint();
      }, wait || 20);
    }

    function chip(ok, yes, no) { return el('span', 'chip ' + (ok ? 'ok' : 'todo'), (ok ? '✓ ' : '✗ ') + (ok ? yes : no)); }

    function paintVerdict() {
      var v = result.verdict, host = q('.ex-verdict');
      host.textContent = '';
      function row(ok, yes, no, text) {
        var item = el('div', 'ex-verdict-row');
        item.appendChild(chip(ok, yes, no));
        item.appendChild(el('span', '', text));
        host.appendChild(item);
      }
      row(v.bounded, 'Bounded', 'Not bounded', 'It starts with ' + num(result.total[0]) + ' animals, never holds more than ' + num(v.peak)
        + ' in the mean run, and ends at ' + num(v.tail) + '.');
      var missing = result.classes.filter(function (c) { return c.room >= 1 && c.there < 0.9; });
      var marginal = result.classes.filter(function (c) { return c.room > 0 && c.room < 1; });
      var text = missing.length ? missing.map(function (c) {
        return KIND[c.kind].label + ' are there ' + pct(c.there) + ' of the last third' + (c.lost != null ? ', gone by day ' + Math.round(c.lost) + ' in the median run that lost them' : '');
      }).join('; ') + '.' : 'Every class the region has room for is there at least nine days in ten of the last third.';
      if (marginal.length) text += ' It has room for less than one group of ' + marginal.map(function (c) { return KIND[c.kind].label.toLowerCase(); }).join(' and of ')
        + ', so they are not counted here.';
      row(v.alive, 'Alive', 'Classes lost', text);
      row(v.settled, 'Settled', 'Still moving', 'The middle third holds ' + num(v.middle) + ' animals, the last third ' + num(v.tail)
        + (v.since == null ? '; it never comes within 15% of that for good.' : v.since > 0 ? '; from day ' + Math.round(v.since) + ' on it stays within 15% of that.'
          : '; it is within 15% of that from the first day.'));
      var name = function (id) { return result.species.filter(function (s) { return s.id === id; })[0].name; };
      var stay = result.species.filter(function (s) { return s.there >= 0.5; }).length;
      row(!v.gone.length, 'No species gone', v.gone.length + ' species gone', (v.gone.length ? 'Gone from the region: ' + v.gone.map(name).join(', ') + '. ' : '')
        + stay + ' of ' + result.species.filter(function (s) { return !s.never; }).length + ' species are there more than half of the last third'
        + (v.passing.length ? '; ' + v.passing.map(name).join(', ') + ' come and go.' : '.'));
      var drift = v.level[1] - v.level[0];
      if (Math.abs(drift) >= 2 && result.total[result.total.length - 1] > 0) {
        row(false, '', 'Levels drift', 'The mean level goes from ' + num(v.level[0]) + ' to ' + num(v.level[1]) + ': the weakest is taken first and a young has its parent\'s level.');
      }
    }

    function paintCharts() {
      var third = Math.floor(result.days.length * 2 / 3);
      var series = result.kinds.map(function (kind) {
        return { label: KIND[kind].label, color: KIND[kind].color, values: result.mean[kind], low: result.low[kind], high: result.high[kind] };
      });
      legend(q('[data-legend="animals"]'), series);
      lineChart(q('[data-chart="animals"]'), { days: result.days, series: series, label: 'Animals of each class in the region over ' + state.days + ' game days' });
      var roles = M.ROLES.filter(function (role) { return result.pressure[role].some(function (p) { return p > 0; }); });
      var lines = roles.map(function (role) { return { label: ROLE[role].label, color: ROLE[role].color, values: result.pressure[role].map(function (p) { return Math.min(p, 2); }) }; });
      legend(q('[data-legend="pressure"]'), lines);
      lineChart(q('[data-chart="pressure"]'), { days: result.days, series: lines, digits: 2, height: 190, bound: { y: 1, label: 'all food taken' },
        label: 'Pressure on the food of each kind of eater' });
      var body = q('.ex-pressures tbody');
      body.textContent = '';
      roles.forEach(function (role) {
        var p = avg(result.pressure[role], third), tr = el('tr');
        [ROLE[role].label, result.last.sea ? ROLE[role].sea : ROLE[role].eats, num(result.demand[role]), num(result.food[role]), p.toFixed(2),
          M.clamp(1 - p).toFixed(2), M.clamp(2 - p).toFixed(2)]
          .forEach(function (text, i) { tr.appendChild(el(i ? 'td' : 'th', '', text)); });
        body.appendChild(tr);
      });
      q('.ex-yardstick').hidden = state.rules === 'bounded';
    }

    function paintClasses() {
      var body = q('.ex-classes tbody');
      body.textContent = '';
      result.classes.forEach(function (c) {
        var tr = el('tr');
        [KIND[c.kind].label, c.room ? c.room.toFixed(1) : 'none', num(c.start), num(c.animals), num(c.groups), c.room > 0 || c.start > 0 ? pct(c.there) : '',
          c.animals ? '±' + pct(c.band) : ''].forEach(function (text, i) { tr.appendChild(el(i ? 'td' : 'th', '', text)); });
        body.appendChild(tr);
      });
    }

    function paintSpecies() {
      var body = q('.ex-species tbody');
      body.textContent = '';
      result.species.forEach(function (s) {
        var tr = el('tr');
        tr.appendChild(el('th', '', s.name));
        tr.appendChild(el('td', '', KIND[s.kind].label));
        tr.appendChild(el('td', '', num(s.start)));
        tr.appendChild(el('td', '', num(s.animals)));
        tr.appendChild(el('td', '', num(s.groups)));
        var there = el('td');
        if (s.never) there.appendChild(el('span', 'muted', 'never came'));
        else there.appendChild(el('span', s.there < 0.05 ? 'ex-lost' : '', (s.there < 0.05 ? '✗ ' : '') + pct(s.there)));
        tr.appendChild(there);
        tr.appendChild(el('td', '', s.lost != null && s.there < 0.5 ? 'day ' + Math.round(s.lost) : ''));
        body.appendChild(tr);
      });
    }

    function paintDiagram() {
      var tabs = q('.ex-focus'), keys = ['ALL'].concat(result.kinds);
      if (keys.indexOf(state.focus) < 0) state.focus = result.kinds[0] || 'ALL';
      tabs.textContent = '';
      keys.forEach(function (key) {
        var button = el('button', '', key === 'ALL' ? 'Every animal' : KIND[key].label);
        button.type = 'button';
        button.setAttribute('role', 'tab');
        button.setAttribute('aria-selected', key === state.focus ? 'true' : 'false');
        button.addEventListener('click', function () { state.focus = key; paintDiagram(); });
        tabs.appendChild(button);
      });
      var edges = M.edges(result, state.focus), who = state.focus === 'ALL' ? 'an animal' : 'one of the ' + KIND[state.focus].label.toLowerCase();
      diagram(q('.ex-diagram'), edges, state.rules);
      var list = q('.ex-edges');
      list.textContent = '';
      function item(name, text) {
        var li = el('li');
        li.appendChild(el('b', '', name));
        li.appendChild(document.createTextNode(' ' + text));
        list.appendChild(li);
      }
      if (!edges.days) { item('Nothing to count:', 'no animal of this kind lived in the region.'); return; }
      var life = edges.deaths[0] + edges.deaths[1] + edges.deaths[2] + edges.deaths[3];
      item('A life', 'as a record lasts ' + (life > 0 ? num(1 / life) + ' game days on average' : 'the whole span') + ', ' + pct(edges.share[0]) + ' of them fed.');
      if (life > 0) item('It ends', ['of age ' + pct(edges.deaths[0] / life), 'hunted ' + pct(edges.deaths[2] / life), 'starved ' + pct(edges.deaths[1] / life)]
        .concat(state.rules === 'coded' ? ['struck down by prey ' + pct(edges.deaths[3] / life)] : []).join(', ') + '.');
      item('It is replaced', 'by young ' + rate(edges.born) + ' and arrivals ' + rate(edges.arrived) + ' for each animal living, against ' + rate(life) + ' lost.');
      q('.ex-diagram-note').textContent = 'Counted over ' + Math.round(edges.days).toLocaleString('en-US') + ' animal-days in ' + result.runs
        + ' runs: how often ' + who + ' took each connection, per animal and day, and under it what sets it. A thicker line is taken more often.';
    }

    function paint() {
      if (!result) return;
      paintVerdict();
      paintCharts();
      paintClasses();
      paintSpecies();
      paintDiagram();
    }

    // -------------------------------------------------------------------------------------- survey
    var surveying = false;
    q('.ex-survey-run').addEventListener('click', function () {
      if (surveying) return;
      surveying = true;
      var body = q('.ex-survey tbody'), index = 0;
      body.textContent = '';
      q('.ex-survey-wrap').hidden = false;
      var setups = [{ rules: 'bounded', arrivals: true }, { rules: 'coded', arrivals: false }, { rules: 'coded', arrivals: true }];
      (function next() {
        if (index >= data.regions.length) { surveying = false; q('.ex-survey-status').textContent = 'Surveyed with the size, zone, start, span and weights set on this page.'; return; }
        var r = data.regions[index++], tr = el('tr');
        q('.ex-survey-status').textContent = 'Living through ' + r.name + '...';
        tr.appendChild(el('th', '', r.name));
        setups.forEach(function (setup) {
          var o = options({ region: r, water: r.water, fertility: r.fertility, rules: setup.rules, arrivals: setup.arrivals });
          var out = M.ensemble(data, o, Math.max(6, Math.round(runsFor(o) / 2))), v = out.verdict, td = el('td');
          var marks = el('div', 'chips');
          marks.appendChild(chip(v.bounded, 'bounded', 'unbounded'));
          marks.appendChild(chip(v.alive, 'alive', 'classes lost'));
          marks.appendChild(chip(v.settled, 'settled', 'moving'));
          td.appendChild(marks);
          var gone = out.classes.filter(function (c) { return c.room >= 1 && c.there < 0.9; }).sort(function (a, b) { return (a.lost == null ? 1e9 : a.lost) - (b.lost == null ? 1e9 : b.lost); });
          td.appendChild(el('small', 'muted', num(out.total[0]) + ' animals, then ' + num(v.tail)
            + (gone.length ? '; ' + gone.map(function (c) { return KIND[c.kind].label.toLowerCase() + (c.lost != null ? ' gone by day ' + Math.round(c.lost) : ' ' + pct(c.there)); }).join(', ') : '')
            + (v.gone.length ? '; ' + v.gone.length + ' species gone' : '')));
          tr.appendChild(td);
        });
        body.appendChild(tr);
        setTimeout(next, 10);
      })();
    });

    var resize = 0;
    window.addEventListener('resize', function () {
      clearTimeout(resize);
      resize = setTimeout(function () { if (visible && result) paintCharts(); }, 120);
    });

    syncRegion();
    paintWeights();
    press();
    return {
      /** Runs the region the first time its page is opened, and redraws the charts to the width they now have. */
      show: function (now) {
        visible = now;
        if (!visible) return;
        if (!ran) run(); else paintCharts();
      }
    };
  }

  return { mount: mount };
})();
