/*
 * The existence inspector's page: tools/session_inspect.py puts the recordings it read into window.INSPECTOR and this
 * script draws them. Two runs stand side by side, "then" and "now"; the ledger below them holds every run entered.
 * Nothing here runs the game or the model: the curves are what the recordings hold, the bands what the model made of
 * the same animals when the page was built.
 */
(function () {
  'use strict';
  var D = window.INSPECTOR, app = document.getElementById('app');
  if (!D) { app.textContent = 'This page was opened without its data: build it with python tools/session_inspect.py.'; return; }
  var CLASSES = D.classes, ROLES = D.roles;
  var COLOR = { GRAZER: 'var(--s1)', HUNTER: 'var(--s2)', APEX: 'var(--s3)', FLYER: 'var(--s4)', SEA: 'var(--s5)', prey: 'var(--s1)',
    predators: 'var(--s2)', grazer: 'var(--s1)', hunter: 'var(--s2)', apex: 'var(--s3)', flyer: 'var(--s4)', then: 'var(--neutral)', now: 'var(--s1)',
    born: 'var(--s3)', arrived: 'var(--s1)', age: 'var(--s4)', starved: 'var(--s2)', hunted: 'var(--s5)', other: 'var(--neutral)' };
  var NAME = { GRAZER: 'Plant eaters', HUNTER: 'Hunters', APEX: 'Giants', FLYER: 'Flyers', SEA: 'Sea animals', prey: 'Prey', predators: 'Predators',
    grazer: 'Plant eaters', hunter: 'Hunters', apex: 'Giants', flyer: 'Flyers', born: 'Born as a record', arrived: 'Arrived as a record',
    age: 'Of age', starved: 'Starved', hunted: 'Hunted', other: 'Other ends' };
  var STATUS = { pass: ['✓', 'Holds'], fail: ['✕', 'Fails'], review: ['!', 'Look'], not_exercised: ['–', 'Not tested'] };
  var state = { then: D.runs.length > 1 ? D.runs.length - 2 : -1, now: D.runs.length - 1, scope: null, group: 'prey', lives: { cls: '', species: '', fate: '', sort: 'class' } };

  // ------------------------------------------------------------------------------------------------ small things

  function el(tag, attrs, kids) {
    var node = document.createElement(tag), key;
    for (key in attrs || {}) {
      if (key === 'text') node.textContent = attrs[key];
      else if (key === 'html') node.innerHTML = attrs[key];
      else if (key.slice(0, 2) === 'on') node.addEventListener(key.slice(2), attrs[key]);
      else if (attrs[key] != null) node.setAttribute(key, attrs[key]);
    }
    (kids || []).forEach(function (kid) { if (kid) node.appendChild(typeof kid === 'string' ? document.createTextNode(kid) : kid); });
    return node;
  }
  function sv(tag, attrs) {
    var node = document.createElementNS('http://www.w3.org/2000/svg', tag), key;
    for (key in attrs || {}) if (attrs[key] != null) node.setAttribute(key, attrs[key]);
    return node;
  }
  function num(value, digits) {
    if (value == null || isNaN(value)) return '–';
    return Number(value).toLocaleString('en-US', { maximumFractionDigits: digits == null ? (Math.abs(value) >= 100 ? 0 : Math.abs(value) >= 10 ? 1 : 2) : digits });
  }
  function sum(list) { return list.reduce(function (a, b) { return a + b; }, 0); }
  function ticksFor(low, high, count) {
    var span = high - low || 1, step = Math.pow(10, Math.floor(Math.log10(span / count))), error = span / count / step;
    step *= error >= 7.5 ? 10 : error >= 3.5 ? 5 : error >= 1.5 ? 2 : 1;
    var out = [], value = Math.ceil(low / step - 1e-9) * step;
    for (; value <= high + step * 1e-6; value += step) out.push(Math.abs(value) < step * 1e-6 ? 0 : value);
    return out;
  }
  function regionName(run, key) {
    var region = run.regions[key];
    if (!region) return key === '-' ? 'Land not divided yet' : 'Region ' + key;
    return String(region.biome).replace(/^minecraft:/, '').replace(/_/g, ' ') + ' · ' + region.cells + ' chunks · ' + key;
  }
  function status(result) {
    var mark = STATUS[result] || STATUS.not_exercised;
    return el('span', { class: 'status ' + (STATUS[result] ? result : 'not_exercised') }, [el('span', { 'aria-hidden': 'true', text: mark[0] }), mark[1]]);
  }

  // ------------------------------------------------------------------------------------------------ the scope

  /** What a scope holds of a run: the counted series of its regions added up, and the model's band where there is one. */
  function scoped(run, scope) {
    var series = run.series, keys, band = null, from = null, to = null, label;
    if (scope === 'all') { keys = null; label = 'All the land on the register'; }
    else if (scope === 'alone') {
      if (!run.pool) return null;
      keys = run.pool.keys; band = { days: run.pool.days, sides: run.pool.sides }; from = run.pool.from; to = run.pool.to;
      label = 'The land left alone (' + keys.length + ' region' + (keys.length === 1 ? '' : 's') + ')';
    } else {
      if (!series.regions[scope]) return null;
      keys = [scope]; label = regionName(run, scope);
      var mine = run.model && run.model.regions && run.model.regions[scope];
      if (mine) { band = { days: mine.days, sides: mine.sides, classes: mine.classes, pressure: mine.pressure }; from = mine.from; to = mine.to; }
    }
    function rows(group, kind) {
      if (!keys) return series.all[group][kind];
      return series.day.map(function (_, i) { return sum(keys.map(function (key) { return series.regions[key][group][kind][i]; })); });
    }
    function both(group) { var w = rows(group, 'world'), r = rows(group, 'record'); return w.map(function (v, i) { return v + r[i]; }); }
    return { keys: keys || Object.keys(series.regions), label: label, band: band, from: from, to: to, rows: rows, both: both, day: series.day };
  }

  function scopes() {
    var list = [['all', 'All the land on the register'], ['alone', 'The land left alone: no animal of it in the world']], seen = {};
    picked().forEach(function (run) {
      Object.keys(run.series.regions).forEach(function (key) {
        if (seen[key] || key === '-') return;
        seen[key] = true;
        list.push([key, regionName(run, key) + (run.model && run.model.regions && run.model.regions[key] ? ' · with the model' : '')]);
      });
    });
    return list;
  }
  function picked() { return [state.then, state.now].filter(function (i) { return i >= 0; }).map(function (i) { return D.runs[i]; }); }

  // ------------------------------------------------------------------------------------------------ charts

  var W = 560;

  function frame(host, opt) {
    host.innerHTML = '';
    var height = opt.height || 240, pad = { l: 40, r: opt.right || 14, t: 24, b: 30 };
    var svg = sv('svg', { viewBox: '0 0 ' + W + ' ' + height, role: 'img', 'aria-label': opt.title || '' });
    var x0 = opt.xMin, x1 = opt.xMax, y0 = opt.yMin == null ? 0 : opt.yMin, y1 = opt.yMax;
    if (!(x1 > x0)) x1 = x0 + 1;
    if (!(y1 > y0)) y1 = y0 + 1;
    var yt = ticksFor(y0, y1, 4), xt = opt.xTicks || ticksFor(x0, x1, 6);
    y1 = Math.max(y1, yt[yt.length - 1]);
    var sx = function (v) { return pad.l + (v - x0) / (x1 - x0) * (W - pad.l - pad.r); };
    var sy = function (v) { return height - pad.b - (v - y0) / (y1 - y0) * (height - pad.t - pad.b); };
    yt.forEach(function (t) {
      svg.appendChild(sv('line', { x1: pad.l, x2: W - pad.r, y1: sy(t), y2: sy(t), style: 'stroke:var(--grid)', 'stroke-width': 1 }));
      var label = sv('text', { x: pad.l - 6, y: sy(t) + 4, 'text-anchor': 'end' }); label.textContent = num(t); svg.appendChild(label);
    });
    xt.forEach(function (t) {
      if (t < x0 - 1e-9 || t > x1 + 1e-9) return;
      var label = sv('text', { x: sx(t), y: height - pad.b + 14, 'text-anchor': 'middle' }); label.textContent = opt.xFormat ? opt.xFormat(t) : num(t); svg.appendChild(label);
    });
    svg.appendChild(sv('line', { x1: pad.l, x2: W - pad.r, y1: sy(y0), y2: sy(y0), style: 'stroke:var(--rule)', 'stroke-width': 1 }));
    if (opt.xLabel) { var xl = sv('text', { x: W - pad.r, y: height - 3, 'text-anchor': 'end' }); xl.textContent = opt.xLabel; svg.appendChild(xl); }
    if (opt.yLabel) { var yl = sv('text', { x: 2, y: 10 }); yl.textContent = opt.yLabel; svg.appendChild(yl); }
    (opt.marks || []).forEach(function (mark, i) {
      if (mark.x < x0 || mark.x > x1) return;
      svg.appendChild(sv('line', { x1: sx(mark.x), x2: sx(mark.x), y1: pad.t, y2: sy(y0), style: 'stroke:var(--ink-3)', 'stroke-width': 1, 'stroke-dasharray': '2 3', opacity: 0.7 }));
      var label = sv('text', { x: sx(mark.x) + 3, y: pad.t + 8 + (i % 2) * 11 }); label.textContent = mark.label; svg.appendChild(label);
    });
    (opt.refs || []).forEach(function (ref) {
      if (ref.y < y0 || ref.y > y1) return;
      svg.appendChild(sv('line', { x1: pad.l, x2: W - pad.r, y1: sy(ref.y), y2: sy(ref.y), style: 'stroke:var(--ink-2)', 'stroke-width': 1, 'stroke-dasharray': '5 4' }));
      var label = sv('text', { x: W - pad.r - 2, y: sy(ref.y) - 4, 'text-anchor': 'end', class: 'lab' }); label.textContent = ref.label; svg.appendChild(label);
    });
    host.appendChild(svg);
    var tip = el('div', { class: 'tip' });
    host.appendChild(tip);
    return { svg: svg, sx: sx, sy: sy, pad: pad, height: height, tip: tip, x0: x0, x1: x1, y0: y0, y1: y1 };
  }

  function pathOf(xs, ys, f) {
    var d = '';
    for (var i = 0; i < xs.length; i++) if (ys[i] != null) d += (d ? 'L' : 'M') + f.sx(xs[i]).toFixed(1) + ' ' + f.sy(ys[i]).toFixed(1);
    return d;
  }

  function hover(host, f, xsOf, rowsAt, opt) {
    var guide = sv('line', { y1: f.pad.t, y2: f.sy(f.y0), style: 'stroke:var(--ink-3)', 'stroke-width': 1, opacity: 0 });
    f.svg.appendChild(guide);
    var hit = sv('rect', { x: f.pad.l, y: f.pad.t, width: W - f.pad.l - f.pad.r, height: f.sy(f.y0) - f.pad.t, fill: 'transparent' });
    f.svg.appendChild(hit);
    function move(event) {
      var box = f.svg.getBoundingClientRect(), px = (event.clientX - box.left) / box.width * W;
      var value = f.x0 + (px - f.pad.l) / (W - f.pad.l - f.pad.r) * (f.x1 - f.x0), xs = xsOf(), best = 0;
      for (var i = 1; i < xs.length; i++) if (Math.abs(xs[i] - value) < Math.abs(xs[best] - value)) best = i;
      if (!xs.length) return;
      guide.setAttribute('x1', f.sx(xs[best])); guide.setAttribute('x2', f.sx(xs[best])); guide.setAttribute('opacity', 0.6);
      var rows = rowsAt(xs[best], best);
      f.tip.innerHTML = '<b>' + (opt.head ? opt.head(xs[best]) : num(xs[best])) + '</b>' + rows.map(function (row) {
        return '<br><i style="background:' + row.color + '"></i>' + row.name + ': <b>' + row.value + '</b>';
      }).join('');
      f.tip.style.display = 'block';
      var left = (event.clientX - box.left) + 14;
      if (left + f.tip.offsetWidth > box.width) left = (event.clientX - box.left) - f.tip.offsetWidth - 14;
      f.tip.style.left = Math.max(0, left) + 'px';
      f.tip.style.top = Math.max(0, (event.clientY - box.top) - 10) + 'px';
    }
    hit.addEventListener('mousemove', move);
    hit.addEventListener('touchstart', function (event) { move(event.touches[0]); }, { passive: true });
    hit.addEventListener('mouseleave', function () { f.tip.style.display = 'none'; guide.setAttribute('opacity', 0); });
  }

  /** Lines over a shared x: each series {name, color, x, y, dash, width, band: {x, low, high}, end: label at its end}. */
  function lines(host, opt) {
    var all = opt.series.filter(function (s) { return s.x.length; });
    if (!all.length) { host.innerHTML = '<div class="empty">Nothing recorded for this.</div>'; return; }
    var xs = [].concat.apply([], all.map(function (s) { return s.x; })), ys = [];
    all.forEach(function (s) { ys = ys.concat(s.y.filter(function (v) { return v != null; })); if (s.band) ys = ys.concat(s.band.high); });
    (opt.refs || []).forEach(function (ref) { if (ref.show) ys.push(ref.y); });
    var f = frame(host, { title: opt.title, height: opt.height, xMin: opt.xMin != null ? opt.xMin : Math.min.apply(null, xs), xMax: opt.xMax != null ? opt.xMax : Math.max.apply(null, xs),
      yMin: opt.yMin, yMax: opt.yMax != null ? opt.yMax : Math.max.apply(null, ys) * 1.06, xLabel: opt.xLabel, yLabel: opt.yLabel, marks: opt.marks, refs: opt.refs,
      right: opt.right, xFormat: opt.xFormat });
    all.forEach(function (s) {
      if (!s.band) return;
      var d = '';
      s.band.x.forEach(function (x, i) { d += (i ? 'L' : 'M') + f.sx(x).toFixed(1) + ' ' + f.sy(s.band.high[i]).toFixed(1); });
      for (var i = s.band.x.length - 1; i >= 0; i--) d += 'L' + f.sx(s.band.x[i]).toFixed(1) + ' ' + f.sy(s.band.low[i]).toFixed(1);
      f.svg.appendChild(sv('path', { d: d + 'Z', style: 'fill:' + s.color, opacity: 0.16 }));
    });
    all.forEach(function (s) {
      f.svg.appendChild(sv('path', { d: pathOf(s.x, s.y, f), fill: 'none', style: 'stroke:' + s.color, 'stroke-width': s.width || 2, 'stroke-linejoin': 'round',
        'stroke-linecap': 'round', 'stroke-dasharray': s.dash || null, opacity: s.opacity || 1 }));
      if (s.dots) s.x.forEach(function (x, i) { if (s.y[i] != null) f.svg.appendChild(sv('circle', { cx: f.sx(x), cy: f.sy(s.y[i]), r: 2.2, style: 'fill:' + s.color })); });
    });
    // The names at the ends of the lines, moved apart where the lines end together.
    var ends = all.filter(function (s) { return s.end; }).map(function (s) {
      var last = s.y.length - 1;
      return { text: s.end, x: f.sx(s.x[last]) + 5, y: f.sy(s.y[last]) + 4 };
    }).sort(function (a, b) { return a.y - b.y; });
    for (var e = 1; e < ends.length; e++) if (ends[e].y - ends[e - 1].y < 12) ends[e].y = ends[e - 1].y + 12;
    var over = ends.length ? ends[ends.length - 1].y - (f.height - f.pad.b) : 0;
    ends.forEach(function (end) {
      var label = sv('text', { x: end.x, y: end.y - Math.max(0, over), class: 'lab' }); label.textContent = end.text; f.svg.appendChild(label);
    });
    var lead = all[0];
    hover(host, f, function () { return lead.x; }, function (x) {
      return all.filter(function (s) { return !s.quiet; }).map(function (s) {
        var best = 0;
        for (var i = 1; i < s.x.length; i++) if (Math.abs(s.x[i] - x) < Math.abs(s.x[best] - x)) best = i;
        var text = num(s.y[best]);
        if (s.band) {
          var b = 0;
          for (var j = 1; j < s.band.x.length; j++) if (Math.abs(s.band.x[j] - x) < Math.abs(s.band.x[b] - x)) b = j;
          text += ' <span style="color:var(--ink-3)">(model ' + num(s.band.low[b], 0) + '–' + num(s.band.high[b], 0) + ')</span>';
        }
        return { name: s.name, color: s.color, value: text };
      });
    }, { head: opt.head });
  }

  /** Layers stacked over x: bodies in the world below, records above them. */
  function stacked(host, opt) {
    var total = opt.x.map(function (_, i) { return sum(opt.layers.map(function (l) { return l.y[i]; })); });
    var f = frame(host, { title: opt.title, height: opt.height, xMin: opt.x[0], xMax: opt.x[opt.x.length - 1], yMax: Math.max.apply(null, total.concat([1])) * 1.06,
      xLabel: opt.xLabel, yLabel: opt.yLabel, marks: opt.marks });
    var base = opt.x.map(function () { return 0; });
    opt.layers.forEach(function (layer) {
      var top = base.map(function (b, i) { return b + layer.y[i]; }), d = '';
      opt.x.forEach(function (x, i) { d += (i ? 'L' : 'M') + f.sx(x).toFixed(1) + ' ' + f.sy(top[i]).toFixed(1); });
      for (var i = opt.x.length - 1; i >= 0; i--) d += 'L' + f.sx(opt.x[i]).toFixed(1) + ' ' + f.sy(base[i]).toFixed(1);
      f.svg.appendChild(sv('path', { d: d + 'Z', style: 'fill:' + layer.color + ';stroke:var(--panel)', 'stroke-width': 1.5, opacity: layer.opacity || 1 }));
      base = top;
    });
    hover(host, f, function () { return opt.x; }, function (x, i) {
      return opt.layers.map(function (l) { return { name: l.name, color: l.color, value: num(l.y[i]) }; }).concat([{ name: 'Together', color: 'transparent', value: num(total[i]) }]);
    }, { head: opt.head });
  }

  /** Bars stacked per bin of the calendar. */
  function bars(host, opt) {
    var totals = opt.bins.map(function (_, i) { return sum(opt.layers.map(function (l) { return l.y[i]; })); });
    if (!sum(totals)) { host.innerHTML = '<div class="empty">None in this run.</div>'; return; }
    var width = opt.bins.length > 1 ? opt.bins[1] - opt.bins[0] : 1;
    var f = frame(host, { title: opt.title, height: opt.height || 170, xMin: opt.xMin, xMax: opt.xMax, yMax: Math.max.apply(null, totals.concat([opt.yMax || 1])), xLabel: opt.xLabel,
      yLabel: opt.yLabel, marks: opt.marks });
    var px = Math.max(1.5, (f.sx(opt.bins[0] + width) - f.sx(opt.bins[0])) - 2);
    opt.bins.forEach(function (bin, i) {
      var base = 0;
      opt.layers.forEach(function (layer) {
        if (!layer.y[i]) return;
        var top = f.sy(base + layer.y[i]), bottom = f.sy(base);
        f.svg.appendChild(sv('rect', { x: f.sx(bin) + 1, y: top, width: px, height: Math.max(0.5, bottom - top - (base ? 1 : 0)), rx: Math.min(2, px / 2), style: 'fill:' + layer.color }));
        base += layer.y[i];
      });
    });
    hover(host, f, function () { return opt.bins.map(function (b) { return b + width / 2; }); }, function (x, i) {
      return opt.layers.filter(function (l) { return l.y[i]; }).map(function (l) { return { name: l.name, color: l.color, value: num(l.y[i]) }; });
    }, { head: function (x) { return 'Days ' + num(x - width / 2, 1) + ' to ' + num(x + width / 2, 1); } });
  }

  /** Predators against prey: the course of the run as one line, with the model's mean course beside it. */
  function phase(host, opt) {
    var xs = opt.x.concat(opt.model ? opt.model.x : []), ys = opt.y.concat(opt.model ? opt.model.y : []);
    var f = frame(host, { title: opt.title, height: 260, xMin: 0, xMax: Math.max.apply(null, xs) * 1.08 + 1, yMin: 0,
      yMax: Math.max.apply(null, ys) * 1.15 + 1, xLabel: 'Prey, animals', yLabel: 'Predators, animals', right: 70 });
    if (opt.model) f.svg.appendChild(sv('path', { d: pathOf(opt.model.x, opt.model.y, f), fill: 'none', style: 'stroke:var(--ink-3)', 'stroke-width': 1.5, 'stroke-dasharray': '4 4' }));
    for (var i = 1; i < opt.x.length; i++) {
      f.svg.appendChild(sv('line', { x1: f.sx(opt.x[i - 1]), y1: f.sy(opt.y[i - 1]), x2: f.sx(opt.x[i]), y2: f.sy(opt.y[i]), style: 'stroke:var(--s1)', 'stroke-width': 2,
        'stroke-linecap': 'round', opacity: 0.3 + 0.7 * i / opt.x.length }));
    }
    var last = opt.x.length - 1;
    f.svg.appendChild(sv('circle', { cx: f.sx(opt.x[0]), cy: f.sy(opt.y[0]), r: 4.5, style: 'fill:var(--panel);stroke:var(--s1)', 'stroke-width': 2 }));
    f.svg.appendChild(sv('circle', { cx: f.sx(opt.x[last]), cy: f.sy(opt.y[last]), r: 4.5, style: 'fill:var(--s1);stroke:var(--panel)', 'stroke-width': 2 }));
    [['start', 0], ['end', last]].forEach(function (pair) {
      var label = sv('text', { x: f.sx(opt.x[pair[1]]) + 8, y: f.sy(opt.y[pair[1]]) - 8, class: 'lab' }); label.textContent = pair[0] + ', day ' + num(opt.day[pair[1]], 0); f.svg.appendChild(label);
    });
    var dots = sv('g', {});
    f.svg.appendChild(dots);
    opt.x.forEach(function (x, i) {
      var dot = sv('circle', { cx: f.sx(x), cy: f.sy(opt.y[i]), r: 7, fill: 'transparent' });
      dot.addEventListener('mouseenter', function () {
        f.tip.innerHTML = '<b>Day ' + num(opt.day[i], 1) + '</b><br>Prey: <b>' + x + '</b><br>Predators: <b>' + opt.y[i] + '</b>';
        f.tip.style.display = 'block'; f.tip.style.left = (f.sx(x) / W * 100) + '%'; f.tip.style.top = (f.sy(opt.y[i]) / f.height * 100) + '%';
      });
      dot.addEventListener('mouseleave', function () { f.tip.style.display = 'none'; });
      dots.appendChild(dot);
    });
  }

  function legend(items) {
    return el('div', { class: 'legend' }, items.map(function (item) {
      return el('span', {}, [el('i', { class: item.kind || '', style: 'background:' + item.color + ';color:' + item.color + (item.opacity ? ';opacity:' + item.opacity : '') }), item.name]);
    }));
  }

  // ------------------------------------------------------------------------------------------------ sections

  function runCard(run, tag, body) {
    return el('div', { class: 'card' }, [el('span', { class: 'run-tag', text: tag }), el('h3', { text: runTitle(run) }),
      el('p', { class: 'sub', text: runSub(run) })].concat(body));
  }
  function runTitle(run) {
    return (run.rules === 'BOUNDED' ? 'Bounded rules' : 'First rules (odds)') + (run.label ? ' · ' + run.label : '');
  }
  function runSub(run) {
    return 'Build ' + run.build.commit + (run.build.dirty ? ' with uncommitted changes' : '') + ' · session ' + run.session + ' · ' + num(run.days, 1) + ' days of the register in '
      + num(run.seconds / 60, 1) + ' min' + (run.speed > 1 ? ' (calendar ×' + num(run.speed, 0) + ')' : '') + ' · a round every ' + num(run.round_days) + ' day';
  }
  function tagOf(index) { return index === state.now ? 'Now' : 'Then'; }

  function each(make) {
    var list = [state.then, state.now].filter(function (i) { return i >= 0; });
    return el('div', { class: 'pair' }, list.map(function (i) { return make(D.runs[i], tagOf(i)); }));
  }

  function header() {
    var options = function (selected, none) {
      return (none ? [el('option', { value: -1, text: 'None' })] : []).concat(D.runs.map(function (run, i) {
        return el('option', { value: i, selected: i === selected ? '' : null, text: run.session + ' · ' + (run.rules === 'BOUNDED' ? 'bounded' : 'odds') + (run.label ? ' · ' + run.label : '') });
      }));
    };
    return el('header', {}, [
      el('h1', { text: 'Existence Inspector' }),
      el('p', { class: 'lede', text: 'Does the wildlife register live the way the existence model says? Each recording is followed animal by animal, in the world and beyond it, '
        + 'and set against the model started from the same animals and against the run before it.' }),
      el('p', { class: 'note', text: 'A validation page, apart from the game: built ' + D.built.replace('T', ' ').slice(0, 16) + ' by tools/session_inspect.py from recordings of '
        + 'tools/session_bench.py --path return --record. Days are days of the register\'s calendar.' }),
      D.runs.length ? el('div', { class: 'bar' }, [
        el('label', { class: 'field' }, ['Then', el('select', { onchange: function (e) { state.then = +e.target.value; render(); } }, options(state.then, true))]),
        el('label', { class: 'field' }, ['Now', el('select', { onchange: function (e) { state.now = +e.target.value; render(); } }, options(state.now, false))])
      ]) : null
    ]);
  }

  function checksSection() {
    return el('section', {}, [el('h2', { text: 'What the runs have to show' }),
      el('p', { text: 'Each check is a statement about the register that a recording either supports or does not. “Look” means the numbers are outside what was expected and want a reading, not that a rule was broken.' }),
      each(function (run, tag) {
        return runCard(run, tag, [el('ul', { class: 'checks' }, run.checks.map(function (check) {
          return el('li', {}, [status(check.result), el('div', {}, [el('b', { text: check.what }), el('span', { text: check.found })])]);
        }))]);
      })]);
  }

  function compareSection() {
    var runs = picked();
    if (!runs.length) return null;
    var groups = [];
    function row(name, get, digits, better) {
      var values = runs.map(function (run) { try { return get(run.summary, run); } catch (e) { return null; } });
      var cells = [el('td', { text: name })].concat(values.map(function (v) {
        return el('td', { class: 'n' + (typeof v === 'string' && v.length > 28 ? ' wrap' : ''), text: typeof v === 'string' ? v : num(v, digits) });
      }));
      if (runs.length > 1) {
        var a = values[0], b = values[1], text = '';
        if (typeof a === 'number' && typeof b === 'number' && isFinite(a) && isFinite(b)) {
          text = (b - a >= 0 ? '+' : '−') + num(Math.abs(b - a), digits) + (a ? ' (' + (b >= a ? '+' : '−') + num(Math.abs(b / a - 1) * 100, 0) + ' %)' : '');
        }
        var cls = 'n';
        if (better && text && b !== a) cls += (b > a) === (better === 'up') ? ' delta-up' : ' delta-down';
        cells.push(el('td', { class: cls, text: text }));
      }
      return el('tr', {}, cells);
    }
    function group(title, rows) { groups.push(el('tr', { class: 'group' }, [el('td', { colspan: runs.length + 2, text: title })])); rows.forEach(function (r) { groups.push(r); }); }
    var pop = function (s) { return s.population; }, perf = function (s) { return s.performance; }, rate = function (s) { return s.per_100_animal_days; };
    group('The run', [
      row('Rules beyond the loaded land', function (s) { return s.rules === 'BOUNDED' ? 'bounded' : 'first (odds)'; }),
      row('Build', function (s) { return s.commit; }),
      row('Days of the register lived', function (s) { return s.days; }, 1),
      row('Rounds lived, all regions', function (s) { return s.rounds; }, 0),
      row('Regions with animals', function (s) { return s.regions; }, 0)
    ]);
    group('Who lives, bodies and records together', [
      row('Animals at the start of the recording', function (s) { return pop(s).total_start; }, 0),
      row('Animals when the place was left', function (s) { return pop(s).total_depart; }, 0),
      row('Animals at the end', function (s) { return pop(s).total_end; }, 0),
      row('The end, % of the leaving', function (s) { return pop(s).total_end / pop(s).total_depart * 100; }, 0),
      row('Fewest and most at any count', function (s) { return pop(s).total_min + ' to ' + pop(s).total_max; })
    ].concat(CLASSES.map(function (kind) {
      return row(NAME[kind] + ', leaving to end', function (s) { return pop(s).depart[kind] + ' → ' + pop(s).end[kind]; });
    })).concat([
      row('Species at the start and at the end', function (s) { return pop(s).species_start + ' → ' + pop(s).species_end; }),
      row('Species lost', function (s) { return pop(s).lost.length ? pop(s).lost.join(', ') : 'none'; })
    ]));
    group('What happened, per 100 animal-days', ['ended', 'age', 'starved', 'hunted', 'struck', 'removed', 'born', 'arrived'].filter(function (key) {
      return runs.some(function (run) { return run.summary.per_100_animal_days[key]; });
    }).map(function (key) {
      var names = { ended: 'Ended, all causes', age: 'Died of age', starved: 'Starved', hunted: 'Hunted', struck: 'Hunters killed by prey that stood', removed: 'Taken off without dying',
        born: 'Born as records', arrived: 'Arrived as records' };
      return row(names[key], function (s) { return rate(s)[key] || 0; }, 2);
    }));
    if (runs.some(function (run) { return run.summary.pressure; })) {
      group('Pressure on what each role eats, all the land, last third of the run', ROLES.map(function (role) {
        return row(NAME[role], function (s) { return s.pressure ? s.pressure[role] : null; }, 2);
      }));
    }
    group('The place left and come back to', [
      row('Animals there at the leaving', function (s) { return s.place && s.place.cohort; }, 0),
      row('Of them without a body in between', function (s) { return s.place && s.place.without_body; }, 0),
      row('Back with their body', function (s) { return s.place && s.place.fates ? (s.place.fates.back || 0) + (s.place.fates.stayed || 0) : null; }, 0),
      row('Ended as records, on record', function (s) { return s.place && s.place.fates ? s.place.fates.died_as_record || 0 : null; }, 0),
      row('Verdict', function (s) { return s.place ? String(s.place.result).replace('_', ' ') : '–'; })
    ]);
    var legs = ['arrive', 'out', 'away', 'back', 'home'];
    group('What it cost', legs.filter(function (leg) { return runs.some(function (run) { return perf(run.summary).fps && perf(run.summary).fps[leg] != null; }); }).map(function (leg) {
      return row('Frames a second, ' + leg, function (s) { return perf(s).fps ? perf(s).fps[leg] : null; }, 1, 'up');
    }).concat([
      row('Server tick, mean ms', function (s) { return perf(s).tick_ms; }, 2, 'down'),
      row('Server tick, 95th percentile of the seconds, ms', function (s) { return perf(s).tick_ms_p95; }, 2, 'down'),
      row('Server tick, longest, ms', function (s) { return perf(s).tick_ms_max; }, 0, 'down'),
      row('Budget pass, median ms', function (s) { return perf(s).pass_ms_median; }, 2, 'down'),
      row('Budget pass, longest, ms', function (s) { return perf(s).pass_ms_max; }, 0, 'down'),
      row('Rounds of one pass, mean ms', function (s) { return perf(s).rounds_ms_mean; }, 2),
      row('Rounds of one pass, longest, ms', function (s) { return perf(s).rounds_ms_max; }, 2),
      row('Records on the register, most', function (s) { return perf(s).records_max; }, 0),
      row('Graphics card, °C and watts', function (s) { return perf(s).gpu ? perf(s).gpu.temperature + ' °C, ' + perf(s).gpu.watts + ' W' : '–'; })
    ]));
    var head = [el('th', { text: '' })].concat(runs.map(function (run, i) { return el('th', { class: 'n', text: runs.length > 1 ? (i ? 'Now' : 'Then') : 'Now' }); }));
    if (runs.length > 1) head.push(el('th', { class: 'n', text: 'Now against then' }));
    return el('section', {}, [el('h2', { text: 'Now and then' }),
      el('p', { text: 'The same flight over the same world under each build: out of the place, away while its animals live as records, and back. '
        + 'A frame rate from one run each is good to about a tenth; the card runs hotter in the second of two runs.' }),
      el('div', { class: 'scroll' }, [el('table', { class: 'compare' }, [el('thead', {}, [el('tr', {}, head)]), el('tbody', {}, groups)])])]);
  }

  function marksOf(run) { return run.marks.map(function (m) { return { x: m.day, label: m.name }; }); }
  function marksT(run) { return run.marks.map(function (m) { return { x: m.t, label: m.name }; }); }

  function curvesSection() {
    var list = scopes();
    if (!state.scope || !list.some(function (s) { return s[0] === state.scope; })) {
      state.scope = picked().some(function (run) { return run.pool; }) ? 'alone' : 'all';
    }
    var groupNames = ['prey', 'predators'].concat(CLASSES);
    var controls = el('div', { class: 'bar' }, [
      el('label', { class: 'field' }, ['Land', el('select', { onchange: function (e) { state.scope = e.target.value; render(); } }, list.map(function (s) {
        return el('option', { value: s[0], selected: s[0] === state.scope ? '' : null, text: s[1] });
      }))]),
      el('label', { class: 'field' }, ['In the world and beyond it: who', el('select', { onchange: function (e) { state.group = e.target.value; render(); } }, groupNames.map(function (g) {
        return el('option', { value: g, selected: g === state.group ? '' : null, text: NAME[g] });
      }))])
    ]);
    function panel(title, sub, make) {
      return el('div', {}, [el('h3', { text: title, style: 'margin-top:26px' }), el('p', { class: 'note', text: sub }), each(function (run, tag) {
        var host = el('div', { class: 'chart' }), extra = [];
        var card = runCard(run, tag, [host]);
        var scope = scoped(run, state.scope);
        if (!scope) { host.appendChild(el('div', { class: 'empty', text: state.scope === 'alone' ? (run.model && run.model.why ? 'No band: ' + run.model.why + '.' : 'No region of this run was left alone.') : 'This run has no animals there.' })); return card; }
        var out = make(host, run, scope);
        (out || []).forEach(function (node) { card.appendChild(node); });
        return card;
      })]);
    }
    return el('section', {}, [el('h2', { text: 'Predators and prey' }),
      el('p', { text: 'Every animal on the register counts, whether it has a body in the world or is a record beyond the loaded land. The band is where four in five of the model\'s runs stay when they '
        + 'start from the very animals the recording holds; it exists for the days a region had no animal in the world, when the rules alone moved it.' }),
      controls,
      panel('The curve', 'Animals by day. Solid: counted in the game. Dashed with a band: the model.', function (host, run, scope) {
        var series = ['prey', 'predators'].map(function (side) {
          var s = { name: NAME[side], color: COLOR[side], x: scope.day, y: scope.both(side), end: NAME[side] };
          if (scope.band) s.band = { x: scope.band.days, low: scope.band.sides[side].low, high: scope.band.sides[side].high };
          return s;
        });
        if (scope.band) ['prey', 'predators'].forEach(function (side) {
          series.push({ name: NAME[side] + ', model mean', color: COLOR[side], x: scope.band.days, y: scope.band.sides[side].mean, dash: '5 4', width: 1.5, quiet: true });
        });
        lines(host, { title: 'Prey and predators by day', series: series, xLabel: 'day', yLabel: 'animals', marks: marksOf(run), xMin: 0, xMax: run.days, right: 62,
          head: function (x) { return 'Day ' + num(x, 1); } });
        return [legend([{ name: 'Prey', color: COLOR.prey }, { name: 'Predators', color: COLOR.predators }].concat(scope.band ? [{ name: 'Model: mean and the band of four runs in five', color: 'var(--ink-3)', kind: 'dash' }] : []))];
      }),
      panel('Predators against prey', 'The same days as one line through the plane of the two. A balance is a knot; a spiral outward or a run into an axis is not.', function (host, run, scope) {
        var prey = scope.both('prey'), predators = scope.both('predators');
        phase(host, { title: 'Predators against prey', x: prey, y: predators, day: scope.day,
          model: scope.band ? { x: scope.band.sides.prey.mean, y: scope.band.sides.predators.mean } : null });
        return [legend([{ name: 'The game, start to end (fainter earlier)', color: COLOR.prey }].concat(scope.band ? [{ name: 'Model, mean course over days ' + num(scope.from, 0) + ' to ' + num(scope.to, 0), color: 'var(--ink-3)', kind: 'dash' }] : []))];
      }),
      panel('By class', 'Plant eaters, hunters, giants, flyers and the animals of the sea: the classes a region keeps quotas of.', function (host, run, scope) {
        var kinds = CLASSES.filter(function (kind) { return Math.max.apply(null, scope.both(kind)) > 0; });
        lines(host, { title: 'Animals of each class by day', series: kinds.map(function (kind) {
          var s = { name: NAME[kind], color: COLOR[kind], x: scope.day, y: scope.both(kind), end: kinds.length <= 4 ? NAME[kind] : null };
          if (scope.band && scope.band.classes && scope.band.classes[kind]) s.band = { x: scope.band.days, low: scope.band.classes[kind].low, high: scope.band.classes[kind].high };
          return s;
        }), xLabel: 'day', yLabel: 'animals', marks: marksOf(run), xMin: 0, xMax: run.days, right: 76, head: function (x) { return 'Day ' + num(x, 1); } });
        return [legend(kinds.map(function (kind) { return { name: NAME[kind], color: COLOR[kind] }; }))];
      }),
      panel('In the world and beyond it', 'Of the animals chosen above: how many had a body in the loaded world and how many were records only.', function (host, run, scope) {
        var color = COLOR[state.group];
        stacked(host, { title: NAME[state.group] + ' in the world and as records', x: scope.day, layers: [
          { name: 'With a body in the world', color: color, y: scope.rows(state.group, 'world') },
          { name: 'Records beyond the loaded land', color: color, opacity: 0.4, y: scope.rows(state.group, 'record') }],
          xLabel: 'day', yLabel: 'animals', marks: marksOf(run), head: function (x) { return 'Day ' + num(x, 1); } });
        return [legend([{ name: NAME[state.group] + ' with a body in the world', color: color, kind: 'box' }, { name: 'records beyond the loaded land', color: color, kind: 'box', opacity: 0.4 }])];
      }),
      panel('Pressure', 'The appetite of a role over its food, counted from the animals of the land chosen. At 1 the land feeds no more; the dashed line is where births and deaths of the plant eaters meet.', function (host, run, scope) {
        var counted = run.pressure.counted, keys = scope.keys.filter(function (key) { return counted[key]; });
        if (!keys.length) { host.appendChild(el('div', { class: 'empty', text: 'The recording does not describe these regions (a build before schema 3).' })); return []; }
        var roles = ROLES.filter(function (role) { return keys.some(function (key) { return Math.max.apply(null, counted[key].demand[role]) > 0; }); });
        var balance = 1 - 4 / (3 * run.weights.fecundity);
        lines(host, { title: 'Pressure by day', series: roles.map(function (role) {
          return { name: NAME[role], color: COLOR[role], x: scope.day, end: NAME[role], y: scope.day.map(function (_, i) {
            var demand = sum(keys.map(function (key) { return counted[key].demand[role][i]; })), food = sum(keys.map(function (key) { return counted[key].food[role][i]; }));
            return food > 0 ? Math.min(3, demand / food) : demand > 0 ? 2 : 0;
          }) };
        }), xLabel: 'day', yLabel: 'appetite over food', marks: marksOf(run), xMin: 0, xMax: run.days, right: 76, yMax: 1.6,
          refs: [{ y: 1, label: 'the land feeds no more', show: true }, { y: balance, label: 'balance of the plant eaters, ' + num(balance, 2) }], head: function (x) { return 'Day ' + num(x, 1); } });
        return [legend(roles.map(function (role) { return { name: NAME[role], color: COLOR[role] }; }))];
      }),
      panel('Entered and ended', 'Records born and arrived beyond the loaded land, and every end by its cause, in the land chosen.', function (host, run, scope) {
        var width = Math.max(1, Math.ceil(run.days / 40)), count = Math.max(1, Math.ceil(run.days / width)), bins = [];
        for (var i = 0; i < count; i++) bins.push(i * width);
        var inScope = function (event) { return state.scope === 'all' || scope.keys.indexOf(event[3]) >= 0; };
        function layer(kinds, name, color) {
          var y = bins.map(function () { return 0; });
          run.events.forEach(function (event) { if (kinds.indexOf(event[1]) >= 0 && inScope(event)) y[Math.min(count - 1, Math.max(0, Math.floor(event[0] / width)))]++; });
          return { name: name, color: color, y: y };
        }
        var entered = [layer(['born'], NAME.born, COLOR.born), layer(['arrived'], NAME.arrived, COLOR.arrived)];
        var ended = [layer(['age'], NAME.age, COLOR.age), layer(['starved'], NAME.starved, COLOR.starved), layer(['hunted', 'struck'], NAME.hunted, COLOR.hunted),
          layer(['killed', 'player', 'removed', 'tamed', 'other'], NAME.other, COLOR.other)];
        var top = Math.max.apply(null, bins.map(function (_, i) { return Math.max(sum(entered.map(function (l) { return l.y[i]; })), sum(ended.map(function (l) { return l.y[i]; }))); }));
        var second = el('div', { class: 'chart' });
        bars(host, { title: 'Records entered', bins: bins, layers: entered, xMin: 0, xMax: count * width, yMax: top, xLabel: 'day', yLabel: 'entered, per ' + width + ' day' + (width > 1 ? 's' : ''), marks: marksOf(run) });
        bars(second, { title: 'Lives ended', bins: bins, layers: ended, xMin: 0, xMax: count * width, yMax: top, xLabel: 'day', yLabel: 'ended, per ' + width + ' day' + (width > 1 ? 's' : ''), marks: marksOf(run) });
        return [legend(entered.map(function (l) { return { name: l.name, color: l.color, kind: 'box' }; })), second, legend(ended.map(function (l) { return { name: l.name, color: l.color, kind: 'box' }; }))];
      })
    ]);
  }

  // ---- individuals

  function css(name) { return getComputedStyle(document.documentElement).getPropertyValue(name).trim(); }

  function livesSection() {
    var f = state.lives, run = D.runs[state.now];
    if (!run) return null;
    var species = Object.keys(run.kinds).sort();
    var rows = run.individuals.filter(function (p) {
      return (!f.cls || p.c === f.cls) && (!f.species || p.s === f.species) && (!f.fate || (f.fate === 'alive' ? !p.e : f.fate === 'record' ? p.a === 'born' || p.a === 'arrived' : p.e === f.fate));
    });
    if (f.sort === 'start') rows = rows.slice().sort(function (a, b) { return a.d0 - b.d0; });
    if (f.sort === 'end') rows = rows.slice().sort(function (a, b) { return a.d1 - b.d1; });
    var pick = function (key, label, options) {
      return el('label', { class: 'field' }, [label, el('select', { onchange: function (e) { f[key] = e.target.value; render('lives'); } }, options.map(function (o) {
        return el('option', { value: o[0], selected: o[0] === f[key] ? '' : null, text: o[1] });
      }))]);
    };
    var controls = el('div', { class: 'bar' }, [
      pick('cls', 'Class', [['', 'All classes']].concat(CLASSES.map(function (c) { return [c, NAME[c]]; }))),
      pick('species', 'Species', [['', 'All species']].concat(species.map(function (s) { return [s, s]; }))),
      pick('fate', 'Which', [['', 'Everyone'], ['alive', 'Alive at the end'], ['record', 'Born or arrived as a record'], ['age', 'Died of age'], ['starved', 'Starved'], ['hunted', 'Hunted'],
        ['removed', 'Taken off without dying']]),
      pick('sort', 'Order', [['class', 'By class and species'], ['start', 'By first day'], ['end', 'By last day']])
    ]);
    var rowH = 7, left = 6, width = 1180, height = rows.length * rowH + 22;
    var canvas = el('canvas', {}), box = el('div', { class: 'lives' }, [canvas]), tip = el('div', { class: 'tip' });
    var ratio = window.devicePixelRatio || 1;
    canvas.width = width * ratio; canvas.height = height * ratio; canvas.style.width = '100%'; canvas.style.minWidth = '640px';
    var ctx = canvas.getContext('2d'), days = Math.max(1, run.days), sx = function (d) { return left + Math.max(0, Math.min(days, d)) / days * (width - left * 2); };
    ctx.scale(ratio, ratio);
    ctx.font = '10px system-ui, sans-serif';
    ctx.fillStyle = css('--ink-3');
    ticksFor(0, days, 10).forEach(function (t) { ctx.fillText('day ' + num(t, 0), sx(t) + 2, 10); ctx.fillRect(sx(t), 12, 0.5, height); });
    run.marks.forEach(function (m) { ctx.fillStyle = css('--ink-2'); ctx.fillRect(sx(m.day), 12, 1, height); });
    var colors = {}; CLASSES.forEach(function (c, i) { colors[c] = css('--s' + (i + 1)); });
    var endColor = { age: css('--s4'), starved: css('--s2'), hunted: css('--s5'), struck: css('--s5') }, ink = css('--ink');
    rows.forEach(function (p, i) {
      var y = 16 + i * rowH;
      p.sp.forEach(function (span) {
        ctx.globalAlpha = span[2] === 'w' ? 1 : 0.38;
        ctx.fillStyle = colors[p.c];
        ctx.fillRect(sx(span[0]), y + (span[2] === 'w' ? 0 : 1.5), Math.max(1.5, sx(span[1]) - sx(span[0])), span[2] === 'w' ? 5 : 2);
      });
      ctx.globalAlpha = 1;
      if (p.e) { ctx.fillStyle = endColor[p.e] || ink; ctx.fillRect(sx(p.d1) - 1, y - 1, 3, 7); }
      if (p.a === 'born' || p.a === 'arrived') { ctx.fillStyle = p.a === 'born' ? css('--s3') : css('--s1'); ctx.beginPath(); ctx.arc(sx(p.d0), y + 2.5, 2.2, 0, 7); ctx.fill(); }
    });
    box.appendChild(tip);
    canvas.addEventListener('mousemove', function (event) {
      var rect = canvas.getBoundingClientRect(), index = Math.floor(((event.clientY - rect.top) / rect.height * height - 16) / rowH), p = rows[index];
      if (!p) { tip.style.display = 'none'; return; }
      var world = sum(p.sp.filter(function (s) { return s[2] === 'w'; }).map(function (s) { return s[1] - s[0]; })), record = sum(p.sp.filter(function (s) { return s[2] === 'r'; }).map(function (s) { return s[1] - s[0]; }));
      tip.innerHTML = '<b>' + p.s + '</b> level ' + p.l + ' · ' + p.u + '<br>' + ({ there: 'On the register at the start', born: 'Born as a record', arrived: 'Arrived as a record', placed: 'Placed with a body',
        unexplained: 'Origin not recorded' })[p.a] + ', day ' + num(p.d0, 1) + '<br>' + num(world, 1) + ' days with a body, ' + num(record, 1) + ' as a record'
        + (p.h != null ? '<br>Hunger at the last roll call ' + num(p.h, 2) : '')
        + '<br>' + (p.e ? 'Ended day ' + num(p.d1, 1) + ': ' + p.ec + (p.es ? ', as a record' : ', in the world') + (p.lv != null ? ', after ' + num(p.lv, 1) + ' days' : '') : 'Alive at the end');
      tip.style.display = 'block';
      tip.style.left = Math.min(box.clientWidth - tip.offsetWidth - 8, Math.max(4, event.clientX - rect.left + box.scrollLeft + 12)) + 'px';
      tip.style.top = (event.clientY - rect.top + 14) + 'px';
    });
    canvas.addEventListener('mouseleave', function () { tip.style.display = 'none'; });
    var shown = rows.slice(0, 300);
    var table = el('details', {}, [el('summary', { text: 'The same as a table (' + (rows.length > 300 ? 'first 300 of ' : '') + rows.length + ')' }), el('div', { class: 'scroll' }, [el('table', {}, [
      el('thead', {}, [el('tr', {}, ['Species', 'Level', 'Id', 'Group', 'Region', 'Entered', 'First day', 'Last day', 'With a body, days', 'As a record, days', 'End'].map(function (h, i) {
        return el('th', { class: i === 1 || i > 5 && i < 10 ? 'n' : '', text: h });
      }))]),
      el('tbody', {}, shown.map(function (p) {
        var world = sum(p.sp.filter(function (s) { return s[2] === 'w'; }).map(function (s) { return s[1] - s[0]; })), record = sum(p.sp.filter(function (s) { return s[2] === 'r'; }).map(function (s) { return s[1] - s[0]; }));
        return el('tr', {}, [el('td', { text: p.s }), el('td', { class: 'n', text: p.l }), el('td', { text: p.u }), el('td', { text: p.p }), el('td', { text: p.r || '' }), el('td', { text: p.a }),
          el('td', { class: 'n', text: num(p.d0, 1) }), el('td', { class: 'n', text: num(p.d1, 1) }), el('td', { class: 'n', text: num(world, 1) }), el('td', { class: 'n', text: num(record, 1) }),
          el('td', { text: p.e ? p.ec + (p.es ? ' (record)' : '') : 'alive' })]);
      }))])])]);
    var counts = { alive: rows.filter(function (p) { return !p.e; }).length, born: rows.filter(function (p) { return p.a === 'born'; }).length, arrived: rows.filter(function (p) { return p.a === 'arrived'; }).length };
    return el('section', { id: 'lives' }, [el('h2', { text: 'One by one' }),
      el('p', { text: 'Every animal the “now” run had on its register, one line each: thick while it had a body in the world, thin while it was a record. A dot is where it was born or arrived as a record; '
        + 'a tick at the end is its death, coloured by cause.' }),
      controls,
      el('p', { class: 'note', text: rows.length + ' animals: ' + counts.alive + ' alive at the end, ' + counts.born + ' born and ' + counts.arrived + ' arrived as records.' }),
      box,
      legend(CLASSES.map(function (c) { return { name: NAME[c], color: COLOR[c], kind: 'box' }; }).concat([{ name: 'born', color: 'var(--s3)', kind: 'box' }, { name: 'arrived', color: 'var(--s1)', kind: 'box' },
        { name: 'end: age', color: COLOR.age, kind: 'box' }, { name: 'starved', color: COLOR.starved, kind: 'box' }, { name: 'hunted', color: COLOR.hunted, kind: 'box' }, { name: 'other', color: 'var(--ink)', kind: 'box' }])),
      table]);
  }

  // ---- what it cost

  function costSection() {
    var runs = picked();
    if (!runs.length) return null;
    function shared(get) { return Math.max.apply(null, runs.map(function (run) { var v = get(run); return v.length ? Math.max.apply(null, v) : 0; })); }
    function panel(title, sub, make) {
      return el('div', {}, [el('h3', { text: title, style: 'margin-top:26px' }), el('p', { class: 'note', text: sub }), each(function (run, tag) {
        var host = el('div', { class: 'chart' }), card = runCard(run, tag, [host]);
        (make(host, run) || []).forEach(function (node) { card.appendChild(node); });
        return card;
      })]);
    }
    // The passes that settle new land are a hundred times the others: the axis follows the nine in ten that are not.
    function ninth(run) { var v = run.perf.pass.map(function (p) { return p[1]; }).sort(function (a, b) { return a - b; }); return v.length ? [v[Math.floor(v.length * 0.9)] * 2.5] : []; }
    var tickTop = Math.min(120, shared(function (run) { return run.perf.tick_ms; }) * 1.1), passTop = Math.max(8, shared(ninth));
    var fpsTop = shared(function (run) { return run.fps && run.fps.fps ? run.fps.fps : []; }) * 1.05;
    return el('section', {}, [el('h2', { text: 'What it cost, second by second' }),
      el('p', { text: 'The server runs the rules; the frame rate is what the player sees. Both axes are the same for the two runs.' }),
      panel('Server tick', 'Mean length of the server\'s ticks in each second; a tick has 50 ms.', function (host, run) {
        lines(host, { title: 'Server tick by second', series: [{ name: 'Mean of the second', color: 'var(--s1)', x: run.perf.t, y: run.perf.tick_ms.map(function (v) { return Math.min(v, tickTop); }) }],
          xLabel: 'seconds', yLabel: 'ms', marks: marksT(run), yMax: tickTop, refs: tickTop >= 50 ? [{ y: 50, label: 'one tick' }] : [], head: function (x) { return num(x, 0) + ' s'; } });
        var late = run.perf.tick_max.filter(function (v) { return v > 50; }).length;
        return [el('p', { class: 'note', text: 'Longest single tick ' + num(Math.max.apply(null, run.perf.tick_max), 0) + ' ms; ' + late + ' seconds held a tick over 50 ms.' })];
      }),
      panel('The budget\'s pass and the rounds', 'Each pass of the population budget (every five seconds) and, within it, the rounds of the rules beyond the loaded land. A pass that settles new land runs off the top; its length is in the line below.', function (host, run) {
        var series = [{ name: 'Budget pass', color: 'var(--s1)', x: run.perf.pass.map(function (p) { return p[0]; }), y: run.perf.pass.map(function (p) { return Math.min(p[1], passTop); }) }];
        if (run.perf.rounds.length) series.push({ name: 'Rounds within it', color: 'var(--s2)', x: run.perf.rounds.map(function (p) { return p[0]; }), y: run.perf.rounds.map(function (p) { return p[1]; }) });
        lines(host, { title: 'Budget pass by time', series: series, xLabel: 'seconds', yLabel: 'ms', marks: marksT(run), yMax: passTop, head: function (x) { return num(x, 0) + ' s'; } });
        var most = run.perf.pass.length ? Math.max.apply(null, run.perf.pass.map(function (p) { return p[1]; })) : 0;
        return [legend(series.map(function (s) { return { name: s.name, color: s.color }; })), el('p', { class: 'note', text: 'Longest pass ' + num(most, 0) + ' ms; up to '
          + num(run.perf.pass.length ? Math.max.apply(null, run.perf.pass.map(function (p) { return p[2]; })) : 0, 0) + ' records on the register.' })];
      }),
      panel('Frames a second', 'The client, second by second of the flight; the letters are its legs.', function (host, run) {
        if (!run.fps || !run.fps.t) { host.appendChild(el('div', { class: 'empty', text: 'No frame data was read for this run (no --bench folder).' })); return []; }
        var marks = [], last = null;
        run.fps.phase.forEach(function (name, i) { if (name !== last) { marks.push({ x: run.fps.t[i], label: name }); last = name; } });
        lines(host, { title: 'Frames a second', series: [{ name: 'Frames a second', color: 'var(--s1)', x: run.fps.t, y: run.fps.fps }], xLabel: 'seconds of the flight', yLabel: 'fps', marks: marks, yMax: fpsTop,
          head: function (x) { return num(x, 0) + ' s'; } });
        return [el('p', { class: 'note', text: run.frames.map(function (leg) { return leg.leg + ' ' + num(leg.fps, 1); }).join(' · ') + (run.gpu && run.gpu.temperature ? ' · card ' + run.gpu.temperature + ' °C, ' + run.gpu.watts + ' W' : '') })];
      })]);
  }

  // ---- the ledger

  function ledgerSection() {
    var book = D.ledger;
    if (!book.length) return null;
    var names = ['Started', 'Build', 'Rules', 'Calendar', 'A round every', 'Days', 'Animals, leaving → end', 'Species lost', 'Ended', 'Born', 'Arrived', 'Tick ms', 'Longest tick', 'Pass ms', 'Fps away', 'Checks'];
    var body = book.map(function (s) {
      var checks = s.checks || {}, bad = Object.keys(checks).filter(function (k) { return checks[k] === 'fail'; }), look = Object.keys(checks).filter(function (k) { return checks[k] === 'review'; });
      var rate = s.per_100_animal_days || {}, perf = s.performance || {};
      return el('tr', {}, [
        el('td', { text: String(s.started || '').replace('T', ' ').slice(0, 16) }), el('td', { text: s.commit }), el('td', { text: (s.rules === 'BOUNDED' ? 'bounded' : 'odds') + (s.label ? ' · ' + s.label : '') }),
        el('td', { class: 'n', text: '×' + num(s.calendar_speed, 0) }), el('td', { class: 'n', text: num(s.round_days) + ' d' }), el('td', { class: 'n', text: num(s.days, 1) }),
        el('td', { class: 'n', text: (s.population.total_depart != null ? s.population.total_depart : s.population.total_start) + ' → ' + s.population.total_end }), el('td', { class: 'n', text: s.population.lost.length }),
        el('td', { class: 'n', text: num(rate.ended, 2) }), el('td', { class: 'n', text: num(rate.born, 2) }), el('td', { class: 'n', text: num(rate.arrived, 2) }),
        el('td', { class: 'n', text: num(perf.tick_ms, 2) }), el('td', { class: 'n', text: num(perf.tick_ms_max, 0) }), el('td', { class: 'n', text: num(perf.pass_ms_median, 2) }),
        el('td', { class: 'n', text: perf.fps && perf.fps.away != null ? num(perf.fps.away, 1) : '–' }),
        el('td', {}, [status(bad.length ? 'fail' : look.length ? 'review' : Object.keys(checks).length ? 'pass' : 'not_exercised'), bad.concat(look).length ? ' ' + bad.concat(look).join(', ') : ''])
      ]);
    });
    var x = book.map(function (_, i) { return i + 1; });
    function trend(title, unit, get) {
      var host = el('div', { class: 'chart' });
      var y = book.map(get);
      if (y.some(function (v) { return v != null; })) {
        lines(host, { title: title, height: 170, series: [{ name: title, color: 'var(--s1)', x: x, y: y, dots: true }], xMin: 0.5, xMax: Math.max(book.length + 0.5, 2), yLabel: unit, xLabel: 'run, oldest first',
          xFormat: function (t) { return Number.isInteger(t) ? String(t) : ''; }, head: function (i) { var s = book[Math.round(i) - 1]; return s ? s.commit + ' · ' + (s.rules === 'BOUNDED' ? 'bounded' : 'odds') : ''; } });
      } else host.appendChild(el('div', { class: 'empty', text: 'Not measured.' }));
      return el('div', { class: 'card' }, [el('h3', { text: title }), host]);
    }
    return el('section', {}, [el('h2', { text: 'Across time: the ledger' }),
      el('p', { text: 'Every run entered so far, oldest first (design/existence/runs.json). Ended, born and arrived are per 100 animal-days of the register\'s calendar, so runs of different length can be set side by side; '
        + 'under the first rules a round is a day\'s worth whatever its pace, so the runs with a round every 0.05 day lived twenty of them a day.' }),
      el('div', { class: 'scroll' }, [el('table', {}, [el('thead', {}, [el('tr', {}, names.map(function (n, i) { return el('th', { class: i > 2 && i < 15 ? 'n' : '', text: n }); }))]), el('tbody', {}, body)])]),
      el('div', { class: 'pair', style: 'margin-top:16px' }, [
        trend('Animals at the end, % of the leaving', '%', function (s) { var from = s.population.total_depart || s.population.total_start; return from ? Math.round(s.population.total_end / from * 100) : null; }),
        trend('Server tick, mean', 'ms', function (s) { return s.performance ? s.performance.tick_ms : null; }),
        trend('Frames a second while away', 'fps', function (s) { return s.performance && s.performance.fps ? s.performance.fps.away : null; })
      ])]);
  }

  function tilesSection() {
    var run = D.runs[state.now];
    if (!run) return null;
    var s = run.summary, held = run.checks.filter(function (c) { return c.result === 'pass'; }).length, failed = run.checks.filter(function (c) { return c.result === 'fail'; }).length;
    function tile(value, name, small) { return el('div', { class: 'tile' }, [el('span', { text: name }), el('b', { text: value }), small ? el('small', { text: small }) : null]); }
    return el('div', { class: 'tiles' }, [
      tile(held + ' of ' + run.checks.length, 'Checks that hold, now', failed ? failed + ' fail' : 'none fails'),
      tile(s.population.total_depart + ' → ' + s.population.total_end, 'Animals, leaving to end', 'of ' + num(s.days, 0) + ' days in all'),
      tile(String(s.population.lost.length), 'Species lost', s.population.lost.join(', ') || 'none'),
      tile(num(s.per_100_animal_days.ended, 2) + ' / ' + num((s.per_100_animal_days.born || 0) + (s.per_100_animal_days.arrived || 0), 2), 'Ended / entered', 'per 100 animal-days'),
      tile(num(s.performance.tick_ms, 1) + ' ms', 'Server tick, mean', 'longest ' + num(s.performance.tick_ms_max, 0) + ' ms'),
      tile(s.performance.fps && s.performance.fps.away != null ? num(s.performance.fps.away, 1) : '–', 'Frames a second, away', s.performance.gpu ? s.performance.gpu.temperature + ' °C' : '')
    ]);
  }

  function render(focus) {
    var top = window.scrollY;
    app.innerHTML = '';
    app.appendChild(header());
    if (!D.runs.length) {
      app.appendChild(el('p', { text: 'No recording was read for this build of the page; the ledger below holds the runs entered before.' }));
    } else {
      [tilesSection(), checksSection(), compareSection(), curvesSection(), livesSection(), costSection()].forEach(function (node) { if (node) app.appendChild(node); });
    }
    var book = ledgerSection();
    if (book) app.appendChild(book);
    if (focus) window.scrollTo(0, top);
  }
  render();
})();
