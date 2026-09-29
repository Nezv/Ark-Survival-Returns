/*
 * Workstation graph (P14): the model and physics of a workstation screen, with no DOM or drawing code.
 *
 * It reads a station file (design/workstations/armoury.json) and the shared style (graph_style.json). The showcase
 * draws it with tools/workstation_ui.js; the mod will port this file line by line to a Java WorkstationGraph that
 * reads the same two files. The simulation is deterministic (fixed 1/60 s tick, no randomness, no trigonometry),
 * so the port can be checked against `node tools/workstation_graph.js <station> <style> [clicks...]`, which prints
 * the settled layout.
 *
 * Materials (categories) are linked in a chain by 'after'. Opening one grows its items around it; selecting an
 * item grows its ingredients, and an ingredient that is another item of the same material (arrows for the
 * tranquilizer arrow) links to that item instead of growing a copy. Forces follow d3-force: many-body charge,
 * springs to a rest length, a pull toward the centre (stronger on the selected item, else the open material, so the rest
 * is pushed aside),
 * collision, then velocity decay and the walls of the graph area.
 */
(function (root) {
  'use strict';

  /** 16 unit vectors, 22.5 degrees apart, clockwise from +x (y points down). */
  var DIRS = [
    [1, 0], [0.92388, 0.38268], [0.70711, 0.70711], [0.38268, 0.92388],
    [0, 1], [-0.38268, 0.92388], [-0.70711, 0.70711], [-0.92388, 0.38268],
    [-1, 0], [-0.92388, -0.38268], [-0.70711, -0.70711], [-0.38268, -0.92388],
    [0, -1], [0.38268, -0.92388], [0.70711, -0.70711], [0.92388, -0.38268]];

  /** The graph well inside the panel, in panel units; the craft bar sits under it. */
  function graphArea(style) {
    var p = style.panel;
    return { x: p.inset, y: p.title, w: p.width - 2 * p.inset, h: p.height - p.title - p.bar - p.inset };
  }

  function levelOf(category, item) {
    return item && item.level != null ? item.level : category.level;
  }

  /** How many times the inventory (id -> count) pays for the item's cost. */
  function maxCrafts(item, have) {
    var best = -1;
    for (var id in item.cost) {
      var times = Math.floor((have[id] || 0) / item.cost[id]);
      best = best < 0 ? times : Math.min(best, times);
    }
    return Math.max(0, best);
  }

  /** Takes the cost of `times` crafts out of the inventory. The caller checks maxCrafts first. */
  function pay(item, times, have) {
    for (var id in item.cost) have[id] = (have[id] || 0) - item.cost[id] * times;
  }

  function Graph(station, style) {
    this.station = station;
    this.style = style;
    this.area = graphArea(style);
    this.nodes = [];
    this.links = [];
    this.level = 0;
    this.open = null;
    this.selected = null;
    this.alpha = 1;
    this.alphaTarget = 0;
    this.ticks = 0;
    var cats = station.categories, byId = {}, cx = this.area.x + this.area.w / 2, cy = this.area.y + this.area.h / 2;
    for (var i = 0; i < cats.length; i++) {
      // A loose spiral to start from; settle() below finds the resting shape.
      var d = DIRS[(i * 7) % 16], reach = 10 + 9 * i;
      var node = this.add('category', 'c:' + cats[i].id, cats[i], null, cx + d[0] * reach * 1.8, cy + d[1] * reach * 0.8);
      node.grow = 1;
      byId[cats[i].id] = node;
    }
    for (i = 0; i < cats.length; i++) {
      if (cats[i].after && byId[cats[i].after]) this.link(byId[cats[i].after], byId[cats[i].id], 'chain');
    }
    this.settle(600);
  }

  Graph.prototype.add = function (kind, key, category, parent, x, y) {
    var node = { kind: kind, key: key, cat: category, item: null, ingredient: null, need: 0, parent: parent,
                 r: this.style.node[kind], x: x, y: y, vx: 0, vy: 0, grow: 0, dying: false, fixed: false,
                 shake: 0, pulse: 0 };
    this.nodes.push(node);
    return node;
  };

  Graph.prototype.link = function (a, b, kind) {
    this.links.push({ a: a, b: b, kind: kind });
  };

  Graph.prototype.find = function (key) {
    for (var i = 0; i < this.nodes.length; i++) if (this.nodes[i].key === key && !this.nodes[i].dying) return this.nodes[i];
    return null;
  };

  Graph.prototype.unlocked = function (node) {
    if (node.kind === 'ingredient') return true;
    if (this.level < node.cat.level) return false;
    return node.kind === 'category' || this.level >= levelOf(node.cat, node.item);
  };

  /** Index of the direction closest to the line from `from` to `node`. */
  Graph.prototype.outward = function (node, fx, fy) {
    var ox = node.x - fx, oy = node.y - fy, best = 0, dot = -Infinity;
    for (var i = 0; i < 16; i++) {
      var v = DIRS[i][0] * ox + DIRS[i][1] * oy;
      if (v > dot) { dot = v; best = i; }
    }
    return best;
  };

  Graph.prototype.reheat = function (alpha) {
    this.alpha = Math.max(this.alpha, alpha == null ? this.style.force.reheat : alpha);
  };

  Graph.prototype.openCategory = function (node) {
    if (this.open === node) return;
    this.closeCategory();
    this.open = node;
    var items = node.cat.items, n = items.length;
    var base = this.outward(node, this.area.x + this.area.w / 2, this.area.y + this.area.h / 2);
    for (var i = 0; i < n; i++) {
      var d = DIRS[(base + Math.round((i - (n - 1) / 2) * 14 / Math.max(n, 4)) + 32) % 16];
      var child = this.add('item', 'i:' + items[i].item, node.cat, node, node.x + d[0] * 2, node.y + d[1] * 2);
      child.item = items[i];
      child.vx = d[0] * 2;
      child.vy = d[1] * 2;
      this.link(node, child, 'item');
    }
    this.reheat();
  };

  Graph.prototype.closeCategory = function () {
    var open = this.open;
    if (!open) return;
    this.clearSelection();
    this.open = null;
    for (var i = 0; i < this.nodes.length; i++) if (this.nodes[i].parent === open) this.nodes[i].dying = true;
    this.reheat();
  };

  Graph.prototype.selectItem = function (node) {
    if (this.selected === node) return;
    this.clearSelection();
    this.selected = node;
    var cost = node.item.cost, keys = Object.keys(cost), n = keys.length, base = this.outward(node, node.parent.x, node.parent.y);
    for (var i = 0; i < n; i++) {
      var sibling = this.find('i:' + keys[i]);
      if (sibling && sibling.parent === node.parent) {
        sibling.need = cost[keys[i]];
        this.link(node, sibling, 'reuse');
        continue;
      }
      var d = DIRS[(base + Math.round((i - (n - 1) / 2) * 2.5) + 32) % 16];
      var g = this.add('ingredient', 'g:' + node.item.item + '>' + keys[i], node.cat, node, node.x + d[0] * 2, node.y + d[1] * 2);
      g.item = node.item;
      g.ingredient = keys[i];
      g.need = cost[keys[i]];
      g.vx = d[0] * 2;
      g.vy = d[1] * 2;
      this.link(node, g, 'ingredient');
    }
    this.reheat(0.6);
  };

  Graph.prototype.clearSelection = function () {
    var selected = this.selected;
    if (!selected) return;
    this.selected = null;
    for (var i = 0; i < this.nodes.length; i++) {
      var n = this.nodes[i];
      if (n.kind === 'ingredient' && n.parent === selected) n.dying = true;
      if (n.kind === 'item') n.need = 0;
    }
    var kept = [];
    for (i = 0; i < this.links.length; i++) if (this.links[i].kind !== 'reuse') kept.push(this.links[i]);
    this.links = kept;
    this.reheat(0.5);
  };

  /**
   * One click, as the screen reports it. Returns what happened: 'open', 'close', 'select', 'craft' (the selected
   * item again: the screen crafts), 'locked', 'back' (empty space: drop the selection, else close the material)
   * or '' (an ingredient: its tooltip says it all).
   */
  Graph.prototype.click = function (node) {
    if (!node) {
      if (this.selected) { this.clearSelection(); return 'back'; }
      if (this.open) { this.closeCategory(); return 'back'; }
      return '';
    }
    if (!this.unlocked(node)) {
      node.shake = this.style.motion.shake;
      return 'locked';
    }
    if (node.kind === 'category') {
      if (this.open === node) { this.closeCategory(); return 'close'; }
      this.openCategory(node);
      return 'open';
    }
    if (node.kind === 'item') {
      if (this.selected === node) return 'craft';
      this.selectItem(node);
      return 'select';
    }
    return '';
  };

  Graph.prototype.charge = function (node) {
    var f = this.style.force;
    var base = node.kind === 'category' ? f.chargeCategory : node.kind === 'item' ? f.chargeItem : f.chargeIngredient;
    return base * node.grow;
  };

  Graph.prototype.rest = function (link) {
    var l = this.style.link;
    return link.kind === 'chain' ? l.chain : link.kind === 'item' ? l.item : link.kind === 'reuse' ? l.item : l.ingredient;
  };

  Graph.prototype.tick = function () {
    var f = this.style.force, nodes = this.nodes, n = nodes.length, i, j, a, b, dx, dy, l;
    this.alpha += (this.alphaTarget - this.alpha) * f.alphaDecay;
    var alpha = this.alpha;

    // Many-body charge: every pair pushes apart, weaker with distance (d3 forceManyBody, no Barnes-Hut).
    for (i = 0; i < n; i++) {
      a = nodes[i];
      for (j = i + 1; j < n; j++) {
        b = nodes[j];
        dx = b.x - a.x; dy = b.y - a.y; l = dx * dx + dy * dy;
        if (l < 1e-6) { dx = (i % 2) ? 0.3 : -0.3; dy = (j % 2) ? 0.3 : -0.3; l = 0.18; }
        var qa = this.charge(a) * alpha / l, qb = this.charge(b) * alpha / l;
        a.vx += dx * qb; a.vy += dy * qb;
        b.vx -= dx * qa; b.vy -= dy * qa;
      }
    }

    // Springs: a grown child rests at its link length; while growing or dying the length follows it.
    for (i = 0; i < this.links.length; i++) {
      var link = this.links[i];
      a = link.a; b = link.b;
      dx = b.x + b.vx - a.x - a.vx; dy = b.y + b.vy - a.y - a.vy;
      var d = Math.sqrt(dx * dx + dy * dy) || 1e-6;
      var stiff = link.kind === 'chain' ? 0.5 : link.kind === 'reuse' ? 0.25 : 1;
      var k = (d - this.rest(link) * Math.min(a.grow, b.grow)) / d * alpha * stiff;
      var w = link.kind === 'chain' || link.kind === 'reuse' ? 0.5 : this.style.link.parentWeight;
      dx *= k; dy *= k;
      b.vx -= dx * (1 - w); b.vy -= dy * (1 - w);
      a.vx += dx * w; a.vy += dy * w;
    }

    // Centring, stretched to the wide graph area. The focus (the selected item, else the open material) is pulled to
    // the middle, so whatever it grows has room and the rest is pushed aside.
    var cx = this.area.x + this.area.w / 2, cy = this.area.y + this.area.h / 2, aspect = this.area.w / this.area.h;
    var focus = this.selected || this.open;
    for (i = 0; i < n; i++) {
      a = nodes[i];
      var s = a === focus ? f.focus : a.kind === 'category' ? f.centre : f.centre * 0.3;
      a.vx += (cx - a.x) * s * alpha;
      a.vy += (cy - a.y) * s * aspect * alpha;
    }

    // Collision on the predicted positions (d3 forceCollide, equal masses).
    var gap = this.style.node.gap;
    for (i = 0; i < n; i++) {
      a = nodes[i];
      for (j = i + 1; j < n; j++) {
        b = nodes[j];
        var min = a.r * a.grow + b.r * b.grow + gap;
        dx = b.x + b.vx - a.x - a.vx; dy = b.y + b.vy - a.y - a.vy; l = dx * dx + dy * dy;
        if (l >= min * min) continue;
        l = Math.sqrt(l) || 1e-6;
        var push = (min - l) / l * f.collide * 0.5;
        dx *= push; dy *= push;
        b.vx += dx; b.vy += dy;
        a.vx -= dx; a.vy -= dy;
      }
    }

    // Integrate, then keep every node inside the graph area.
    var x0 = this.area.x, y0 = this.area.y, x1 = x0 + this.area.w, y1 = y0 + this.area.h;
    for (i = 0; i < n; i++) {
      a = nodes[i];
      if (a.fixed) { a.vx = 0; a.vy = 0; continue; }
      a.vx *= 1 - f.velocityDecay; a.vy *= 1 - f.velocityDecay;
      var speed = Math.sqrt(a.vx * a.vx + a.vy * a.vy);
      if (speed > f.maxSpeed) { a.vx *= f.maxSpeed / speed; a.vy *= f.maxSpeed / speed; }
      a.x += a.vx; a.y += a.vy;
      var m = a.r + 1;
      if (a.x < x0 + m) { a.x = x0 + m; a.vx = 0; } else if (a.x > x1 - m) { a.x = x1 - m; a.vx = 0; }
      if (a.y < y0 + m) { a.y = y0 + m; a.vy = 0; } else if (a.y > y1 - m) { a.y = y1 - m; a.vy = 0; }
    }

    // Grow, shrink and drop the nodes that finished dying, with their links.
    var motion = this.style.motion, alive = [];
    for (i = 0; i < n; i++) {
      a = nodes[i];
      if (a.dying) a.grow -= 1 / motion.shrink;
      else if (a.grow < 1) a.grow = Math.min(1, a.grow + 1 / motion.grow);
      if (a.shake > 0) a.shake--;
      if (a.pulse > 0) a.pulse--;
      if (a.grow > 0) alive.push(a);
    }
    if (alive.length !== n) {
      this.nodes = alive;
      var links = [];
      for (i = 0; i < this.links.length; i++) {
        if (this.links[i].a.grow > 0 && this.links[i].b.grow > 0) links.push(this.links[i]);
      }
      this.links = links;
    }
    this.ticks++;
  };

  /** True while anything still moves or animates; the screen can skip ticks and redraws otherwise. */
  Graph.prototype.moving = function () {
    if (this.alpha > this.style.force.alphaMin) return true;
    for (var i = 0; i < this.nodes.length; i++) {
      var a = this.nodes[i];
      if (a.dying || a.grow < 1 || a.shake > 0 || a.pulse > 0) return true;
    }
    return false;
  };

  Graph.prototype.settle = function (max) {
    for (var i = 0; i < max && this.moving(); i++) this.tick();
  };

  /** The node under a point, topmost first; growing and dying nodes are not clickable. */
  Graph.prototype.nodeAt = function (x, y) {
    for (var i = this.nodes.length - 1; i >= 0; i--) {
      var a = this.nodes[i];
      if (a.dying || a.grow < 0.5) continue;
      var dx = x - a.x, dy = y - a.y;
      if (dx * dx + dy * dy <= (a.r + 1) * (a.r + 1)) return a;
    }
    return null;
  };

  Graph.prototype.grab = function (node) {
    node.fixed = true;
    this.alphaTarget = this.style.force.dragTarget;
    this.reheat(this.style.force.dragTarget);
  };

  Graph.prototype.moveTo = function (node, x, y) {
    var m = node.r + 1;
    node.x = Math.min(this.area.x + this.area.w - m, Math.max(this.area.x + m, x));
    node.y = Math.min(this.area.y + this.area.h - m, Math.max(this.area.y + m, y));
  };

  Graph.prototype.drop = function (node) {
    node.fixed = false;
    this.alphaTarget = 0;
  };

  var api = { Graph: Graph, DIRS: DIRS, graphArea: graphArea, levelOf: levelOf, maxCrafts: maxCrafts, pay: pay };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.ArkWorkstationGraph = api;

  // node tools/workstation_graph.js <station.json> <style.json> [node keys to click...]: the settled layout as JSON.
  if (typeof require !== 'undefined' && typeof module !== 'undefined' && require.main === module) {
    var fs = require('fs');
    var graph = new Graph(JSON.parse(fs.readFileSync(process.argv[2], 'utf8')), JSON.parse(fs.readFileSync(process.argv[3], 'utf8')));
    graph.level = 99;
    process.argv.slice(4).forEach(function (key) { graph.click(graph.find(key)); graph.settle(900); });
    console.log(JSON.stringify({ ticks: graph.ticks, nodes: graph.nodes.map(function (a) {
      return { key: a.key, x: Math.round(a.x * 100) / 100, y: Math.round(a.y * 100) / 100 };
    }) }));
  }
})(this);
