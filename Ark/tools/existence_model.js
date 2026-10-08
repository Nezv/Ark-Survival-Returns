/*
 * The existence model: the wildlife register of one biome region beyond the loaded land, lived through in rounds.
 *
 * Two rule sets run on the same records (groups of animals, each with a level, the day it appeared, the day it
 * dies of age and a hunger from 0 to 1):
 *   coded    SilentLife.java as it was read at the commit named in design/existence/model.json: a round of rules
 *            for the region, its odds fixed per round;
 *   bounded  the proposal: every daily odd is a clamped linear function of the pressure on what the animal eats
 *            (its appetite over its food), so a region can neither empty its land nor outgrow it.
 * Space inside the region is left out: only who lives, in which group, counts.
 *
 * One file for the showcase page (window.ArkExistence, tools/existence_ui.js) and for Node
 * (tools/existence_check.py), so both show the same numbers. `data` is what tools/showcase_existence.py
 * assembles: the species, the sample regions with who lives there, the weights and the coded constants.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.ArkExistence = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var CLASSES = ['GRAZER', 'HUNTER', 'APEX', 'FLYER', 'SEA'];
  var ROLES = ['grazer', 'hunter', 'apex', 'flyer'];
  // What a record is between two rounds, by its hunger, and how its life as a record can end.
  var FED = 0, HUNGRY = 1, STARVING = 2;
  var STATES = ['fed', 'hungry', 'starving'];
  var ENDS = ['age', 'starved', 'hunted', 'struck'];

  function clamp(x) { return x < 0 ? 0 : x > 1 ? 1 : x; }
  function band(hunger) { return hunger < 1 / 3 ? FED : hunger < 2 / 3 ? HUNGRY : STARVING; }

  /** mulberry32: the same run for the same seed, in the browser and in Node. */
  function random(seed) {
    var a = seed >>> 0;
    return function () {
      a = (a + 0x6D2B79F5) | 0;
      var t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  /** WildClass.of: the class whose quota of groups a species counts against. */
  function classOf(s) { return s.realm === 'water' ? 'SEA' : s.realm === 'air' ? 'FLYER' : s.apex ? 'APEX' : s.predator ? 'HUNTER' : 'GRAZER'; }
  /** What a species eats in the bounded model: the land, or those who do. */
  function roleOf(s) { return s.predator ? (s.apex ? 'apex' : 'hunter') : s.realm === 'air' ? 'flyer' : 'grazer'; }

  function weights(data, overrides) {
    var w = {};
    data.model.weights.forEach(function (entry) { w[entry.id] = entry.value; });
    Object.keys(data.model.fixed).forEach(function (key) { w[key] = data.model.fixed[key]; });
    Object.keys(overrides || {}).forEach(function (key) { w[key] = overrides[key]; });
    return w;
  }

  /** The species as the rules see them: the class, the role, the span, the appetite and the tempo of each. */
  function prepare(data, w) {
    var days = data.model.coded.config.wildLifespanDays, byId = {};
    data.species.forEach(function (s) {
      var span = 0.5 + s.health / 100;
      byId[s.id] = {
        id: s.id, name: s.name, health: s.health, damage: s.damage, groupMin: s.groupMin, groupMax: s.groupMax,
        weight: s.weight, predator: s.predator, apex: s.apex, realm: s.realm, cold: s.cold, timid: s.timid, danger: s.danger,
        kind: classOf(s), role: roleOf(s),
        lifespan: days * span,                               // SilentLife.lifespan
        tempo: 1 / span,                                     // how fast it lives: 1 for an animal of 50 HP
        appetite: Math.pow(s.health / 50, w.sizeExponent)    // what it eats a day, in appetites of a 50 HP animal
      };
    });
    return byId;
  }

  /** What each role eats in a region of the land or of the sea: a share of the supply, and what its prey carries. */
  function diets(w, sea) {
    return sea ? {
      grazer: { supply: w.sea.grazer, carry: 0, prey: [] },
      hunter: { supply: w.sea.hunter, carry: w.carryHunter, prey: ['grazer'] },
      apex: { supply: w.sea.apex, carry: w.carryApex, prey: ['grazer', 'hunter'] },
      flyer: { supply: 0, carry: 0, prey: [] }
    } : {
      grazer: { supply: 1, carry: 0, prey: [] },
      hunter: { supply: 0, carry: w.carryHunter, prey: ['grazer'] },
      apex: { supply: 0, carry: w.carryApex, prey: ['grazer', 'hunter'] },
      flyer: { supply: w.flyerShare, carry: 0, prey: [] }
    };
  }

  /**
   * One region and its register.
   * options: region (an entry of data.regions), cells, zone (1..3), water, rules ('bounded' | 'coded'), pace (rounds
   * a game day), arrivals (bool), start ('settled' | 'empty' | 'crowded' | 'no_hunters' | 'hunters'), seed,
   * weights (overrides by id).
   */
  function Sim(data, options) {
    var coded = data.model.coded, region = options.region;
    this.coded = coded;
    this.rules = options.rules || 'bounded';
    this.w = weights(data, options.weights);
    this.species = prepare(data, this.w);
    this.rand = random(options.seed == null ? 1 : options.seed);
    this.cells = options.cells || 256;
    this.zone = options.zone || 3;
    this.water = options.water == null ? region.water : options.water;
    this.sea = region.type === 'OCEAN' || region.type === 'RIVER';
    this.fertility = options.fertility == null ? region.fertility : options.fertility;
    // What the land grows, against the richest: the bounded model's measure of a region's resources.
    this.richness = this.rules === 'bounded' ? this.fertility * (this.water ? 1 : this.w.dry) : 1;
    this.arrivals = options.arrivals !== false;
    this.dt = 1 / (options.pace || 1);
    this.levels = coded.levels[this.zone];
    this.diet = diets(this.w, this.sea);
    var self = this;
    this.pool = region.pool.map(function (id) { return self.species[id]; });
    this.eligible = {};
    this.quota = {};
    this.room = {};
    var groupsPerChunk = coded.config.wildGroupsPerPlayer / (Math.PI * coded.config.populationRadius * coded.config.populationRadius / 256);
    CLASSES.forEach(function (kind) {
      var lives = self.pool.filter(function (s) { return s.kind === kind; });
      self.eligible[kind] = lives.filter(function (s) { return s.weight > 0 && s.danger <= self.zone; });
      // LandRegister.quota: a class that does not live in the biome gets none; the fraction is rounded by the region itself.
      // The bounded model gives poorer land a smaller quota: the same share of what it grows.
      var groups = (self.sea === (kind === 'SEA')) && lives.length ? self.cells * groupsPerChunk * coded.share[kind] * self.richness : 0;
      // What the zone keeps out cannot come: a class with a quota and nobody to fill it stays empty.
      self.room[kind] = self.eligible[kind].length ? groups : 0;
      self.quota[kind] = Math.floor(groups) + (self.rand() < groups - Math.floor(groups) ? 1 : 0);
    });
    this.day = 0;
    this.groups = [];
    this.due = { GRAZER: 0, HUNTER: 0, APEX: 0, FLYER: 0, SEA: 0 };
    this.tally = {};       // per species: record-days in each state, moves between them, ends, young, arrivals
    this.deaths = {};      // ends by cause
    this.start(options.start || 'settled');
  }

  Sim.prototype.tallyOf = function (species) {
    var t = this.tally[species.id];
    if (!t) {
      t = this.tally[species.id] = { days: [0, 0, 0], moves: [[0, 0, 0], [0, 0, 0], [0, 0, 0]], ends: [[0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0]],
        young: [0, 0, 0], arrived: 0, born: 0 };
    }
    return t;
  };

  Sim.prototype.level = function () { return this.levels[0] + Math.floor(this.rand() * (this.levels[1] - this.levels[0] + 1)); };

  /** SilentLife.lastDay: a quarter to five quarters of the species' span after it appeared. */
  Sim.prototype.life = function (species, level, hunger) {
    return { level: level, born: this.day, last: this.day + species.lifespan * (0.25 + this.rand()), hunger: hunger };
  };

  /** NaturalPopulations.pickOfClass: by weight among the species of the class the zone admits. */
  Sim.prototype.pick = function (kind, filter) {
    var list = this.eligible[kind], total = 0, i;
    if (filter) list = list.filter(filter);
    for (i = 0; i < list.length; i++) total += list[i].weight;
    if (!total) return null;
    var roll = this.rand() * total;
    for (i = 0; i < list.length; i++) { roll -= list[i].weight; if (roll < 0) return list[i]; }
    return list[list.length - 1];
  };

  Sim.prototype.place = function (species, size, arrived) {
    var group = { species: species, members: [], hunger: this.coded.hunger };
    for (var i = 0; i < size; i++) group.members.push(this.life(species, this.level(), this.coded.hunger));
    this.groups.push(group);
    if (arrived) this.tallyOf(species).arrived += size;
    return group;
  };

  Sim.prototype.size = function (species) { return species.groupMin + Math.floor(this.rand() * (species.groupMax - species.groupMin + 1)); };

  Sim.prototype.count = function (kind) {
    var n = 0;
    for (var i = 0; i < this.groups.length; i++) if (this.groups[i].species.kind === kind && this.groups[i].members.length) n++;
    return n;
  };

  /**
   * The region as the budget settles it (every class at its quota), or one of the disturbed starts. Under the bounded
   * model the budget too places a group only where the land can feed it, plant eaters first.
   */
  Sim.prototype.start = function (how) {
    var self = this;
    if (how === 'empty') return;
    ['GRAZER', 'FLYER', 'SEA', 'HUNTER', 'APEX'].forEach(function (kind) {
      var groups = self.quota[kind] * (how === 'crowded' ? 2 : 1);
      for (var i = 0; i < groups; i++) {
        var species = self.pick(kind, how === 'no_hunters' ? function (s) { return !s.predator; }
          : how === 'hunters' ? function (s) { return s.predator; } : null);
        if (!species) continue;
        var size = self.size(species);
        if (self.rules === 'bounded' && how === 'settled') size = self.fed(species, size, self.census());
        if (size > 0) self.place(species, size, false);
      }
    });
  };

  /** How many of the group the land can feed as well: as many as keep the pressure on their food at or below 1. */
  Sim.prototype.fed = function (species, size, now) {
    var spare = now.food[species.role] - now.demand[species.role];
    return Math.max(0, Math.min(size, Math.floor(spare / species.appetite + 1e-9)));
  };

  // ------------------------------------------------------------------------------------------ coded

  function scaledHealth(base, level, growth) { return Math.min(1024, base * (1 + growth * Math.pow(Math.max(1, Math.min(100, level)) - 1, 0.85))); }
  function scaledDamage(base, level, growth) { return base * (1 + growth * Math.sqrt(Math.max(1, Math.min(100, level)) - 1)); }

  /** SilentLife.power: the health times the damage of each of its animals at its level. */
  Sim.prototype.power = function (herd) {
    var c = this.coded.config, sum = 0;
    for (var i = 0; i < herd.members.length; i++) {
      sum += scaledHealth(herd.species.health, herd.members[i].level, c.healthGrowth) * scaledDamage(herd.species.damage, herd.members[i].level, c.damageGrowth);
    }
    return sum;
  };

  function preys(hunter, prey) {
    return (hunter.realm === 'water') === (prey.realm === 'water') && prey.realm !== 'air' && (!prey.predator || (hunter.apex && !prey.apex));
  }

  /** Ends the life of the lowest level in the herd. */
  Sim.prototype.weakest = function (herd, cause, from) {
    var pick = 0;
    for (var i = 1; i < herd.members.length; i++) if (herd.members[i].level < herd.members[pick].level) pick = i;
    var life = herd.members.splice(pick, 1)[0];
    this.end(herd.species, life, cause, from ? from.get(life) : band(life.hunger));
  };

  Sim.prototype.end = function (species, life, cause, state) {
    this.tallyOf(species).ends[state][ENDS.indexOf(cause)]++;
    this.deaths[cause] = (this.deaths[cause] || 0) + 1;
  };

  Sim.prototype.hunt = function (pack, herds, from) {
    var k = this.coded.silent, might = this.power(pack), total = 0, prey = [], odds = [], i;
    for (i = 0; i < herds.length; i++) {
      var herd = herds[i];
      if (herd === pack || !herd.members.length || !preys(pack.species, herd.species)) continue;
      var chance = might / (might + this.power(herd));
      prey.push(herd); odds.push(chance); total += chance;
    }
    if (prey.length) {
      var roll = this.rand() * total, pick = 0;
      while (pick < prey.length - 1 && (roll -= odds[pick]) >= 0) pick++;
      if (this.rand() < odds[pick]) {
        this.weakest(prey[pick], 'hunted', from);
        pack.hunger = k.FED;
      } else if (!prey[pick].species.timid && this.rand() < k.STRIKES_BACK * (1 - odds[pick])) this.weakest(pack, 'struck', from);
    }
    if (pack.hunger >= 1 && pack.members.length && this.rand() < k.STARVES) this.weakest(pack, 'starved', from);
  };

  /** SilentLife.round, without the shift inside the chunk. */
  Sim.prototype.roundCoded = function () {
    var k = this.coded.silent, herds = this.groups, today = this.day, groups = 0, hunters = 0, i, j, herd;
    var from = new Map();
    for (i = 0; i < herds.length; i++) {
      herd = herds[i];
      if (!herd.members.length) continue;
      groups++;
      if (herd.species.predator) hunters++;
      for (j = 0; j < herd.members.length; j++) {
        var state = band(herd.members[j].hunger);
        from.set(herd.members[j], state);
        this.tallyOf(herd.species).days[state] += this.dt;
      }
    }
    for (i = 0; i < herds.length; i++) {
      herd = herds[i];
      herd.hunger = 0;
      for (j = herd.members.length - 1; j >= 0; j--) {
        var old = herd.members[j];
        if (today >= old.last) { herd.members.splice(j, 1); this.end(herd.species, old, 'age', from.get(old)); }
        else herd.hunger += old.hunger;
      }
      if (herd.members.length) herd.hunger /= herd.members.length;
    }
    for (i = 0; i < herds.length; i++) {
      herd = herds[i];
      if (!herd.members.length) continue;
      if (!herd.species.predator) herd.hunger = k.GRAZED;
      else {
        herd.hunger = Math.min(1, herd.hunger + k.APPETITE);
        if (herd.hunger >= k.HUNTS_FROM) this.hunt(herd, herds, from);
      }
    }
    for (i = 0; i < herds.length; i++) {
      herd = herds[i];
      var size = herd.members.length, s = herd.species;
      if (size >= 2 && size < s.groupMax && herd.hunger < k.BREEDS_BELOW) {
        var odds = k.BIRTH * (this.water || s.realm === 'water' || s.cold ? 1 : k.DRY) * (s.predator || groups === 0 ? 1 : 1 - hunters / groups);
        if (this.rand() < odds) {
          var parent = herd.members[Math.floor(this.rand() * size)];
          herd.members.push(this.life(s, parent.level, herd.hunger));
          var t = this.tallyOf(s);
          t.young[from.get(parent)]++;
          t.born++;
        }
      }
      for (j = 0; j < herd.members.length; j++) {
        var life = herd.members[j], was = from.get(life);
        life.hunger = herd.hunger;
        if (was !== undefined) this.tallyOf(s).moves[was][band(life.hunger)]++;
      }
    }
    this.sweep();
    if (this.arrivals) this.arriveCoded();
  };

  /** LandRegister.grow and the budget's arrivals: the whole quota in populationRefillDays, never beyond what is missing. */
  Sim.prototype.arriveCoded = function () {
    var self = this, days = this.coded.config.populationRefillDays;
    CLASSES.forEach(function (kind) {
      var quota = self.quota[kind], count = self.count(kind);
      self.due[kind] = Math.min(Math.max(0, quota - count), self.due[kind] + quota * self.dt / days);
      while (self.due[kind] >= 1 && count < quota) {
        var species = self.pick(kind);
        if (!species) { self.due[kind] = Math.min(self.due[kind], 1); break; }   // LandRegister.full
        self.place(species, self.size(species), true);
        self.due[kind] -= 1;
        count++;
      }
    });
  };

  Sim.prototype.sweep = function () {
    var kept = [];
    for (var i = 0; i < this.groups.length; i++) if (this.groups[i].members.length) kept.push(this.groups[i]);
    this.groups = kept;
  };

  // ---------------------------------------------------------------------------------------- bounded

  /**
   * The pressures of the region today, from a count of the register: for each role its appetite, its food (a share
   * of what the land grows, and what its prey carries) and the one over the other; and from them the three bounded
   * functions every odd is made of: the room left (1 - p), the food found (2 - p), and the hunters at the heels
   * of each role (the pressure of those who eat it), each clamped to 0..1.
   */
  Sim.prototype.census = function () {
    var w = this.w, diet = this.diet, demand = { grazer: 0, hunter: 0, apex: 0, flyer: 0 }, alive = {}, i;
    for (i = 0; i < this.groups.length; i++) {
      var g = this.groups[i];
      demand[g.species.role] += g.members.length * g.species.appetite;
      alive[g.species.id] = (alive[g.species.id] || 0) + g.members.length;
    }
    var supply = this.cells * w.supply * this.richness;
    var food = {}, pressure = {}, room = {}, found = {}, hunted = { grazer: 0, hunter: 0, apex: 0, flyer: 0 };
    ROLES.forEach(function (role) {
      var d = diet[role], prey = 0;
      d.prey.forEach(function (other) { prey += demand[other]; });
      food[role] = d.supply * supply + d.carry * prey;
      pressure[role] = food[role] > 0 ? demand[role] / food[role] : demand[role] > 0 ? 2 : 0;
      room[role] = clamp(1 - pressure[role]);
      found[role] = clamp(2 - pressure[role]);
    });
    ROLES.forEach(function (role) {
      if (!demand[role] || !diet[role].carry) return;
      diet[role].prey.forEach(function (other) { hunted[other] += clamp(pressure[role]); });
    });
    return { supply: supply, demand: demand, food: food, pressure: pressure, room: room, found: found, hunted: hunted, alive: alive };
  };

  Sim.prototype.roundBounded = function () {
    var w = this.w, dt = this.dt, now = this.census(), born = [], i, j;
    var meal = Math.min(1, dt), starve = 1 - Math.pow(1 - w.starve, dt);
    for (i = 0; i < this.groups.length; i++) {
      var g = this.groups[i], s = g.species, role = s.role, t = this.tallyOf(s);
      var hunted = 1 - Math.pow(1 - Math.min(1, w.kill * s.tempo * now.hunted[role]), dt);
      var young = 1 - Math.pow(1 - Math.min(1, w.fecundity / s.lifespan * now.room[role]), dt);
      var mate = now.alive[s.id] >= 2;
      for (j = g.members.length - 1; j >= 0; j--) {
        var life = g.members[j], was = band(life.hunger);
        t.days[was] += dt;
        if (this.day >= life.last) { g.members.splice(j, 1); this.end(s, life, 'age', was); continue; }
        if (this.rand() < hunted) { g.members.splice(j, 1); this.end(s, life, 'hunted', was); continue; }
        // A meal a day: found, the animal is a step better fed; missed, a step hungrier.
        if (this.rand() < meal) life.hunger = this.rand() < now.found[role] ? Math.max(0, life.hunger - 0.5) : Math.min(1, life.hunger + 0.5);
        if (life.hunger >= 1 && this.rand() < starve) { g.members.splice(j, 1); this.end(s, life, 'starved', was); continue; }
        t.moves[was][band(life.hunger)]++;
        if (life.hunger <= 0 && mate && this.rand() < young) born.push({ group: g, species: s, was: was });
      }
    }
    // A young is born only where the land can feed it as well. It stays with its group while there is room in it;
    // from a full group it joins another of its kind that has room, and where every group is full it founds one of
    // its own. Every species so gets every young it is due.
    for (i = 0; i < born.length; i++) {
      var b = born[i];
      if (!this.fed(b.species, 1, now)) continue;
      now.demand[b.species.role] += b.species.appetite;
      var child = this.life(b.species, this.level(), 0.5), tb = this.tallyOf(b.species), home = null;
      if (b.group.members.length && b.group.members.length < b.species.groupMax) home = b.group;
      for (j = 0; !home && j < this.groups.length; j++) {
        var other = this.groups[j];
        if (other.species === b.species && other.members.length && other.members.length < b.species.groupMax) home = other;
      }
      if (home) home.members.push(child);
      else this.groups.push({ species: b.species, members: [child], hunger: 0.5 });
      tb.young[b.was]++;
      tb.born++;
    }
    this.sweep();
    if (this.arrivals) this.arriveBounded(now);
  };

  /**
   * Arrivals as the game has them (LandRegister.grow), for the land nobody is near as well: a class below its quota
   * of groups is allowed its whole quota in `refill` days, never more than it is short of. A class at its quota
   * still meets wanderers, its quota of them in `wander` days, so a species the region has lost comes back. Of
   * either group only as many come as the land can feed, that is as keep the pressure on their food at or below 1.
   */
  Sim.prototype.arriveBounded = function (now) {
    var self = this, w = this.w;
    function come(kind) {
      var species = self.pick(kind), size = species ? self.fed(species, self.size(species), now) : 0;
      if (!size) return false;
      self.place(species, size, true);
      now.demand[species.role] += size * species.appetite;
      return true;
    }
    CLASSES.forEach(function (kind) {
      var quota = self.quota[kind], count = self.count(kind);
      self.due[kind] = Math.min(Math.max(0, quota - count), self.due[kind] + quota * self.dt / w.refill);
      while (self.due[kind] >= 1 && count < quota) {
        if (!come(kind)) { self.due[kind] = Math.min(self.due[kind], 1); break; }
        self.due[kind] -= 1;
        count++;
      }
      if (!(w.wander > 0) || !self.room[kind]) return;
      // Wanderers pass by chance, the quota of them in `wander` days on average.
      for (var passing = self.room[kind] * self.dt / w.wander; passing > 0; passing -= 1) if (self.rand() < passing) come(kind);
    });
  };

  // ------------------------------------------------------------------------------------------- runs

  Sim.prototype.round = function () {
    if (this.rules === 'coded') this.roundCoded(); else this.roundBounded();
    this.day += this.dt;
  };

  /** Who lives now: animals and groups by class and by species, the mean level, and the pressures of the bounded model. */
  Sim.prototype.snapshot = function () {
    var animals = { GRAZER: 0, HUNTER: 0, APEX: 0, FLYER: 0, SEA: 0 }, groups = { GRAZER: 0, HUNTER: 0, APEX: 0, FLYER: 0, SEA: 0 };
    var species = {}, levels = 0, total = 0;
    for (var i = 0; i < this.groups.length; i++) {
      var g = this.groups[i], n = g.members.length;
      if (!n) continue;
      animals[g.species.kind] += n;
      groups[g.species.kind]++;
      var s = species[g.species.id] || (species[g.species.id] = { animals: 0, groups: 0 });
      s.animals += n;
      s.groups++;
      for (var j = 0; j < n; j++) levels += g.members[j].level;
      total += n;
    }
    var now = this.census();
    return { day: this.day, animals: animals, groups: groups, species: species, total: total, level: total ? levels / total : 0,
      pressure: now.pressure, demand: now.demand, food: now.food, supply: now.supply };
  };

  /**
   * Lives the region through `days` game days and keeps a snapshot every `every` days.
   * Returns the series, the tally of every move between states, and the day each species and class was last seen.
   */
  function run(data, options) {
    var sim = new Sim(data, options), days = options.days || 720, every = options.every || Math.max(1, Math.round(days / 240));
    var series = [sim.snapshot()], next = every, lost = {}, seen = {};
    function watch(shot) {
      CLASSES.concat(Object.keys(sim.species)).forEach(function (key) {
        var n = shot.animals[key] !== undefined ? shot.animals[key] : shot.species[key] ? shot.species[key].animals : 0;
        if (n > 0) { seen[key] = true; delete lost[key]; } else if (seen[key] && lost[key] === undefined) lost[key] = shot.day;
      });
    }
    watch(series[0]);
    while (sim.day < days - 1e-9) {
      sim.round();
      var shot = null;
      if (sim.day >= next - 1e-9) { shot = sim.snapshot(); series.push(shot); next += every; }
      watch(shot || sim.snapshot());
    }
    return { sim: sim, series: series, tally: sim.tally, deaths: sim.deaths, lost: lost, seen: seen, quota: sim.quota, room: sim.room };
  }

  function quantile(sorted, q) {
    if (!sorted.length) return 0;
    var at = (sorted.length - 1) * q, low = Math.floor(at), high = Math.ceil(at);
    return sorted[low] + (sorted[high] - sorted[low]) * (at - low);
  }

  /**
   * The same region lived through `runs` times from different seeds: for each class the mean number of animals and
   * the band most runs stay in, day by day; how often each class and species was still there at the end; the summed
   * tally; and a verdict on the three things a register has to do: stay bounded, stay alive and settle.
   */
  function ensemble(data, options, runs) {
    runs = runs || 24;
    var all = [], i, k;
    for (i = 0; i < runs; i++) {
      var o = {};
      for (k in options) o[k] = options[k];
      o.seed = (options.seed == null ? 1 : options.seed) * 7919 + i * 104729;
      all.push(run(data, o));
    }
    var first = all[0], steps = first.series.length, days = first.series.map(function (s) { return s.day; });
    var kinds = CLASSES.filter(function (kind) { return all.some(function (r) { return r.seen[kind] || r.room[kind] > 0; }); });
    var mean = {}, low = {}, high = {}, groups = {}, pressure = { grazer: [], hunter: [], apex: [], flyer: [] }, level = [], total = [];
    var demand = { grazer: 0, hunter: 0, apex: 0, flyer: 0 }, food = { grazer: 0, hunter: 0, apex: 0, flyer: 0 }, tailFrom = Math.floor(steps * 2 / 3);
    kinds.forEach(function (kind) { mean[kind] = []; low[kind] = []; high[kind] = []; groups[kind] = []; });
    for (var t = 0; t < steps; t++) {
      kinds.forEach(function (kind) {
        var values = all.map(function (r) { return r.series[t].animals[kind]; }).sort(function (a, b) { return a - b; });
        mean[kind].push(values.reduce(function (a, b) { return a + b; }, 0) / runs);
        low[kind].push(quantile(values, 0.1));
        high[kind].push(quantile(values, 0.9));
        groups[kind].push(all.reduce(function (a, r) { return a + r.series[t].groups[kind]; }, 0) / runs);
      });
      ROLES.forEach(function (role) {
        pressure[role].push(all.reduce(function (a, r) { return a + Math.min(3, r.series[t].pressure[role]); }, 0) / runs);
        if (t >= tailFrom) {
          demand[role] += all.reduce(function (a, r) { return a + r.series[t].demand[role]; }, 0) / runs / (steps - tailFrom);
          food[role] += all.reduce(function (a, r) { return a + r.series[t].food[role]; }, 0) / runs / (steps - tailFrom);
        }
      });
      // The mean level of the runs that still hold animals.
      var peopled = all.filter(function (r) { return r.series[t].total > 0; });
      level.push(peopled.length ? peopled.reduce(function (a, r) { return a + r.series[t].level; }, 0) / peopled.length : 0);
      total.push(all.reduce(function (a, r) { return a + r.series[t].total; }, 0) / runs);
    }
    // Species: how many at the start and over the last third, in how many runs it is there at the end, and when it went.
    var species = [], third = Math.floor(steps * 2 / 3), ids = {};
    all.forEach(function (r) { Object.keys(r.seen).forEach(function (key) { if (CLASSES.indexOf(key) < 0) ids[key] = true; }); });
    first.sim.pool.forEach(function (s) { if (s.danger <= first.sim.zone) ids[s.id] = true; });
    Object.keys(ids).forEach(function (id) {
      var s = first.sim.species[id], startN = 0, endN = 0, endG = 0, there = 0, gone = [];
      all.forEach(function (r) {
        var a = r.series[0].species[id];
        startN += a ? a.animals : 0;
        for (var t = third; t < steps; t++) {
          var b = r.series[t].species[id];
          if (b) { endN += b.animals; endG += b.groups; }
        }
        for (var u = third; u < steps; u++) if (r.series[u].species[id]) there += 1 / (steps - third);
        if (!r.series[steps - 1].species[id] && r.lost[id] !== undefined) gone.push(r.lost[id]);
      });
      gone.sort(function (a, b) { return a - b; });
      species.push({ id: id, name: s.name, kind: s.kind, role: s.role, start: startN / runs, animals: endN / runs / (steps - third),
        groups: endG / runs / (steps - third), there: there / runs, lost: gone.length ? quantile(gone, 0.5) : null, never: !all.some(function (r) { return r.seen[id]; }) });
    });
    species.sort(function (a, b) { return CLASSES.indexOf(a.kind) - CLASSES.indexOf(b.kind) || b.animals - a.animals || a.name.localeCompare(b.name); });
    // Classes: there at the end, and the day the median run lost it.
    var classes = kinds.map(function (kind) {
      var there = 0, due = 0, gone = [];
      all.forEach(function (r) {
        // A region whose share of the class rounds to no group is not missing it.
        if (!(r.quota[kind] > 0) && !(r.series[0].animals[kind] > 0)) return;
        due++;
        for (var t = third; t < steps; t++) if (r.series[t].animals[kind] > 0) there += 1 / (steps - third);
        if (!(r.series[steps - 1].animals[kind] > 0) && r.lost[kind] !== undefined) gone.push(r.lost[kind]);
      });
      gone.sort(function (a, b) { return a - b; });
      var tail = mean[kind].slice(third), middle = mean[kind].slice(Math.floor(steps / 3), third);
      var avg = function (list) { return list.length ? list.reduce(function (a, b) { return a + b; }, 0) / list.length : 0; };
      var m = avg(tail), width = avg(high[kind].slice(third).map(function (v, i) { return (v - low[kind][third + i]) / 2; }));
      return { kind: kind, quota: first.quota[kind], room: first.room[kind], start: mean[kind][0], animals: m, groups: avg(groups[kind].slice(third)),
        before: avg(middle), band: m ? width / m : 0, there: due ? there / due : 0, due: due / runs, lost: gone.length ? quantile(gone, 0.5) : null,
        peak: Math.max.apply(null, high[kind]) };
    });
    var tally = {}, deaths = {};
    all.forEach(function (r) {
      Object.keys(r.tally).forEach(function (id) {
        var from = r.tally[id], to = tally[id] || (tally[id] = { days: [0, 0, 0], moves: [[0, 0, 0], [0, 0, 0], [0, 0, 0]],
          ends: [[0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0]], young: [0, 0, 0], arrived: 0, born: 0 });
        for (var a = 0; a < 3; a++) {
          to.days[a] += from.days[a];
          to.young[a] += from.young[a];
          for (var b = 0; b < 3; b++) to.moves[a][b] += from.moves[a][b];
          for (var c = 0; c < 4; c++) to.ends[a][c] += from.ends[a][c];
        }
        to.arrived += from.arrived;
        to.born += from.born;
      });
      Object.keys(r.deaths).forEach(function (cause) { deaths[cause] = (deaths[cause] || 0) + r.deaths[cause]; });
    });
    return { runs: runs, days: days, kinds: kinds, mean: mean, low: low, high: high, groups: groups, pressure: pressure, demand: demand, food: food,
      supply: first.series[0].supply, level: level, total: total,
      species: species, classes: classes, tally: tally, deaths: deaths, last: first.sim, verdict: verdict(classes, species, total, level, days) };
  }

  /**
   * Bounded: never far above where it started or settled. Alive: every class the region has room for at least a
   * group of is there nine days in ten of the last third. Settled: the last third is where the middle third was
   * (within a tenth, or 2.5 animals); `since` is the day from which the mean run, smoothed over a sixteenth of the
   * span, stays within 15 % of the last third. A species is gone when it lived in the region and is there less than
   * one day in twenty of the last third.
   */
  function verdict(classes, species, total, level, days) {
    var steps = total.length, third = Math.floor(steps * 2 / 3);
    var avg = function (list) { return list.length ? list.reduce(function (a, b) { return a + b; }, 0) / list.length : 0; };
    var tail = avg(total.slice(third)), middle = avg(total.slice(Math.floor(steps / 3), third)), peak = Math.max.apply(null, total);
    var weakest = classes.reduce(function (a, c) { return c.room >= 1 ? Math.min(a, c.there) : a; }, 1);
    var near = Math.max(2.5, 0.15 * tail), half = Math.max(1, Math.round(steps / 32)), since = 0;
    for (var i = 0; i < steps; i++) {
      if (Math.abs(avg(total.slice(Math.max(0, i - half), i + half + 1)) - tail) > near) since = i + 1;
    }
    return {
      bounded: peak <= Math.max(total[0], tail) * 1.5 + 4,
      alive: weakest >= 0.9,
      settled: Math.abs(tail - middle) <= Math.max(2.5, 0.1 * middle),
      since: since < steps ? days[since] : null,
      peak: peak, tail: tail, middle: middle, weakest: weakest,
      gone: species.filter(function (s) { return !s.never && s.there < 0.05; }).map(function (s) { return s.id; }),
      passing: species.filter(function (s) { return !s.never && s.there >= 0.05 && s.there < 0.5; }).map(function (s) { return s.id; }),
      level: [level[0], level[steps - 1]]
    };
  }

  /**
   * The edges of the existence diagram for a class (or one species): per animal and day, how often a record in each
   * state moved to each other, ended in each way or had a young, counted over the runs.
   */
  function edges(result, key) {
    var sim = result.last, sum = { days: [0, 0, 0], moves: [[0, 0, 0], [0, 0, 0], [0, 0, 0]], ends: [[0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0]], young: [0, 0, 0], arrived: 0, born: 0 };
    Object.keys(result.tally).forEach(function (id) {
      var s = sim.species[id];
      if (key !== 'ALL' && s.kind !== key && s.id !== key) return;
      var t = result.tally[id];
      for (var a = 0; a < 3; a++) {
        sum.days[a] += t.days[a];
        sum.young[a] += t.young[a];
        for (var b = 0; b < 3; b++) sum.moves[a][b] += t.moves[a][b];
        for (var c = 0; c < 4; c++) sum.ends[a][c] += t.ends[a][c];
      }
      sum.arrived += t.arrived;
      sum.born += t.born;
    });
    var days = sum.days[0] + sum.days[1] + sum.days[2];
    var rate = function (count, from) { return sum.days[from] > 0 ? count / sum.days[from] : 0; };
    var out = { days: days, share: sum.days.map(function (d) { return days ? d / days : 0; }), moves: [], ends: [], young: [], arrived: days ? sum.arrived / days : 0,
      born: days ? sum.born / days : 0, counts: sum, span: days && (sum.born + sum.arrived) ? days / (sum.born + sum.arrived) : 0 };
    for (var a = 0; a < 3; a++) {
      out.moves.push([0, 1, 2].map(function (b) { return rate(sum.moves[a][b], a); }));
      out.ends.push([0, 1, 2, 3].map(function (c) { return rate(sum.ends[a][c], a); }));
      out.young.push(rate(sum.young[a], a));
    }
    out.deaths = [0, 1, 2, 3].map(function (c) { return days ? (sum.ends[0][c] + sum.ends[1][c] + sum.ends[2][c]) / days : 0; });
    return out;
  }

  return { CLASSES: CLASSES, ROLES: ROLES, STATES: STATES, ENDS: ENDS, Sim: Sim, run: run, ensemble: ensemble, edges: edges, weights: weights,
    classOf: classOf, roleOf: roleOf, clamp: clamp };
});

// Node: `node existence_model.js data.json setups.json` lives each setup through and prints what the page would show
// of it as JSON, for tools/existence_check.py. A setup names its region and may give any option of ensemble().
if (typeof module === 'object' && module.exports && typeof require === 'function' && require.main === module) {
  (function () {
    var fs = require('fs'), model = module.exports;
    var data = JSON.parse(fs.readFileSync(process.argv[2], 'utf8')), setups = JSON.parse(fs.readFileSync(process.argv[3], 'utf8'));
    process.stdout.write(JSON.stringify(setups.map(function (setup) {
      var options = {}, key;
      for (key in setup) options[key] = setup[key];
      options.region = data.regions.filter(function (region) { return region.id === setup.region; })[0];
      var result = model.ensemble(data, options, setup.runs || 24), tail = Math.floor(result.days.length * 2 / 3), pressure = {};
      model.ROLES.forEach(function (role) {
        var list = result.pressure[role].slice(tail);
        pressure[role] = list.reduce(function (a, b) { return a + b; }, 0) / list.length;
      });
      return { setup: setup, verdict: result.verdict, classes: result.classes, species: result.species, deaths: result.deaths, pressure: pressure,
        start: result.total[0] };
    })));
  })();
}
