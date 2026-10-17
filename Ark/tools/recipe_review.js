/*
 * Recipe control (P14): every recipe in the game (vanilla, Ark, the benches' own designs and the pack's mods) with its
 * fate in the crafting rework (go, stay, change or decide) and the bench it goes to, as tools/workstation_plan.py
 * planned it. A family (the twelve wooden stairs) is one row that opens into its recipes; a change gets its new cost
 * and count in an editor under its row. Changes made here stay in this browser until Save decisions writes
 * design/workstations/vanilla_fates.json: Chrome and Edge save straight to the file you pick, other browsers download
 * it. The game build (tools/build_workstation_data.py) and the next showcase build read it.
 *
 * ArkRecipeReview.mount(root, payload, icon) wires the list; icon(id) draws an atlas icon (tools/workstation_ui.js).
 */
window.ArkRecipeReview = (function () {
  'use strict';
  var FATES = ['go', 'stay', 'change', 'decide'];
  var LABEL = { go: 'Go', stay: 'Stay', change: 'Change', decide: '?' };
  var ORDER = ['inventory', 'armoury', 'working_station', 'saddlery', 'campfire', 'stonecutter', 'mortar_and_pestle', 'primitive_forge',
               'medicine_bench', 'smithing_table', 'mechanical_press', 'milling_machine', 'cutting_machine', ''];
  var STORE = 'ark-p14-recipe-edits';
  var PAGE = 120;

  function esc(s) {
    return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; });
  }

  function mount(root, payload, icon) {
    var review = payload.review, items = payload.items;
    var list = root.querySelector('.rv-list'), summary = root.querySelector('.rv-summary'), more = root.querySelector('.rv-more');
    var statusEl = root.querySelector('.rv-status');
    var edits = {};
    try { edits = JSON.parse(localStorage.getItem(STORE) || '{}') || {}; } catch (err) { edits = {}; }
    var filters = { q: '', fate: 'all', bench: 'all', source: 'all', kind: 'all', edited: false, nocost: false };
    var expanded = {}, editing = {}, limit = PAGE;
    var SOURCE = { vanilla: 'vanilla', ark: 'Ark', mod: 'mod' };

    function name(id) { return (items[id] && items[id].name) || id; }
    function store() { try { localStorage.setItem(STORE, JSON.stringify(edits)); } catch (err) { /* private window */ } }
    function benchTitle(b) { return b ? review.benches[b] || b : 'None'; }
    function placeTitle(b, p) { return p && review.places[b] && review.places[b][p] ? review.places[b][p] : ''; }

    /**
     * A record's fate, bench, note and new cost after this browser's edits (a recipe's own edit beats its family's). The
     * new cost is the user's, else the design's (nc), else none: a change without one still needs it.
     */
    function effective(r) {
      var f = edits['family:' + r.f] || {}, own = edits[r.i] || {};
      var fate = own.fate || f.fate || r.fate;
      return { fate: fate, bench: own.bench != null ? own.bench : f.bench != null ? f.bench : (r.b || ''),
               note: own.note != null ? own.note : f.note != null ? f.note : (r.note || ''),
               cost: own.cost || r.nc || null, count: own.count || r.nn || r.n || 1,
               edited: !!(edits[r.i] || edits['family:' + r.f]) };
    }
    function needsCost(r, e) { return e.fate === 'change' && !e.cost && !r.sp; }
    function needsBench(r, e) { return (e.fate === 'stay' || e.fate === 'change') && !e.bench && !r.sp; }
    /** What a kept recipe still lacks before the build can make it: a bench, and for a change its new cost. */
    function incomplete(r, e) { return needsCost(r, e) || needsBench(r, e); }

    function kindOf(r) {
      var t = r.t || 'crafting_shaped';
      return t === 'bench' ? 'bench' : t.indexOf('crafting') === 0 || t === 'bound_shaped' ? 'crafting' : 'other';
    }

    function families() {
      var byKey = {}, out = [];
      review.records.forEach(function (r) {
        var source = r.s || 'vanilla';
        if (filters.source !== 'all' && source !== filters.source) return;
        if (filters.kind !== 'all' && kindOf(r) !== filters.kind) return;
        var key = source + '|' + r.f;
        if (!byKey[key]) { byKey[key] = { key: key, family: r.f, members: [] }; out.push(byKey[key]); }
        byKey[key].members.push(r);
      });
      out.forEach(function (fam) {
        var first = fam.members[0], e = effective(first);
        fam.title = fam.members.length > 1 ? title(fam.family) : name(first.r) || first.i;
        fam.bench = e.bench;
        fam.place = first.p || '';
      });
      out.sort(function (a, b) {
        return (ORDER.indexOf(a.bench) - ORDER.indexOf(b.bench)) || a.place.localeCompare(b.place) || a.title.localeCompare(b.title);
      });
      return out;
    }

    function title(family) {
      return family.replace(/_/g, ' ').replace(/\b\w/g, function (c) { return c.toUpperCase(); });
    }

    function matches(fam) {
      var q = filters.q.trim().toLowerCase();
      return fam.members.some(function (r) {
        var e = effective(r);
        if (filters.fate !== 'all' && e.fate !== filters.fate) return false;
        if (filters.bench !== 'all' && e.bench !== filters.bench) return false;
        if (filters.edited && !e.edited) return false;
        if (filters.nocost && !incomplete(r, e)) return false;
        if (!q) return true;
        return (fam.title + ' ' + r.i + ' ' + name(r.r) + ' ' + e.note).toLowerCase().indexOf(q) >= 0;
      });
    }

    function costHtml(cost, count, result) {
      var parts = Object.keys(cost || {}).map(function (id) {
        return '<span title="' + esc(name(id)) + '"><b>' + cost[id] + '</b>' + icon(id) + '</span>';
      });
      return parts.join('') + (result ? '<i class="rv-arrow">→</i><span title="' + esc(name(result)) + '">' +
                               (count > 1 ? '<b>' + count + '</b>' : '') + icon(result) + '</span>' : '');
    }

    /** The cost cell: the cost as it is and, for a change, the new one, or a call for it; an editor button beside. */
    function costCell(r, e, key) {
      if (r.sp) return costHtml(r.c, r.n || 1, r.r);
      // only the recipe as it is: the new cost once a change has one
      var current = e.fate === 'change' && e.cost ? costHtml(e.cost, e.count, r.r) : costHtml(r.c, r.n || 1, r.r);
      return current + (e.fate === 'change' && !e.cost ? '<span class="rv-need">needs a cost</span>' : '') +
        (e.fate === 'change' ? '<button type="button" class="rv-ed-open" data-key="' + esc(key) + '" aria-expanded="' + !!editing[key] +
                                '" title="Edit the new cost">' + (editing[key] ? 'Done' : 'Edit cost') + '</button>' : '');
    }

    /** The editor of a change's new cost: an amount per ingredient, an ingredient to add, the count it makes. */
    function editor(r, e, key) {
      var cost = e.cost || r.c;
      return '<div class="rv-editor" data-key="' + esc(key) + '"><span class="label">New cost</span><span class="rv-ed-cost">' +
        Object.keys(cost).map(function (id) {
          return '<span class="rv-ed-item">' + icon(id) + '<input type="number" min="0" class="rv-ed-n" data-item="' + esc(id) + '" value="' +
                 cost[id] + '" aria-label="' + esc(name(id)) + '"><small>' + esc(name(id)) + '</small><button type="button" class="rv-ed-del" data-item="' +
                 esc(id) + '" aria-label="Remove ' + esc(name(id)) + '">×</button></span>';
        }).join('') + '</span>' +
        '<input class="rv-ed-add" list="rv-items" placeholder="Add an ingredient: item or tag" aria-label="Add an ingredient">' +
        '<span class="label">Makes</span><span class="rv-ed-item"><input type="number" min="1" class="rv-ed-count" value="' + e.count +
        '" aria-label="Count made">' + icon(r.r) + '<small>' + esc(name(r.r)) + '</small></span>' +
        '<button type="button" class="rv-ed-reset">Back to the plan</button></div>';
    }

    function fateButtons(value, key) {
      return '<span class="rv-fate" role="group" aria-label="Fate">' + FATES.map(function (f) {
        return '<button type="button" data-fate="' + f + '" data-key="' + esc(key) + '" class="f-' + f + '" aria-pressed="' +
               (value === f) + '" title="' + { go: 'Remove it', stay: 'Keep the grid cost', change: 'Keep with a new cost', decide: 'Still to decide' }[f] +
               '">' + LABEL[f] + '</button>';
      }).join('') + '</span>';
    }

    function benchSelect(value, key) {
      return '<select class="rv-bench" data-key="' + esc(key) + '" aria-label="Bench">' + ORDER.map(function (b) {
        return '<option value="' + b + '"' + (b === value ? ' selected' : '') + '>' + esc(benchTitle(b)) + '</option>';
      }).join('') + '</select>';
    }

    function byLabel(r, e) {
      if (e.edited) return '<span class="rv-by edited" title="Changed here, not saved yet">edited</span>';
      return '<span class="rv-by ' + (r.by || 'rule') + '">' + ({ rule: 'rule', design: 'design', user: 'yours' }[r.by || 'rule']) + '</span>';
    }

    function row(fam) {
      var first = fam.members[0], states = fam.members.map(effective), fates = {};
      states.forEach(function (s) { fates[s.fate] = (fates[s.fate] || 0) + 1; });
      var keys = Object.keys(fates), fate = keys.length === 1 ? keys[0] : null, many = fam.members.length > 1;
      var e = states[0], key = many ? 'family:' + fam.family : first.i, open = !!expanded[fam.key];
      var cost = many ? costHtml(first.c, first.n || 1, first.r) : costCell(first, e, key);
      var where = placeTitle(e.bench, first.p);
      var html = '<div class="rv-row' + (many ? ' rv-fam' : '') + '" data-fam="' + esc(fam.key) + '">' +
        (many ? '<button type="button" class="rv-exp" aria-expanded="' + open + '" data-fam="' + esc(fam.key) + '" aria-label="Show the ' +
                fam.members.length + ' recipes">' + (open ? '▾' : '▸') + '</button>' : '<span class="rv-exp"></span>') +
        '<span class="rv-name">' + icon(first.r || Object.keys(first.c)[0]) + '<span><b>' + esc(fam.title) + '</b>' +
          (many ? ' <small>' + fam.members.length + ' recipes' + (fate ? '' : ', mixed') + '</small>' : '') +
          '<small class="rv-id">' + esc(many ? fam.family : first.i) + (first.sp ? ' · special' : '') + '</small></span></span>' +
        '<span class="rv-cost">' + cost + '</span>' +
        fateButtons(fate, key) +
        '<span class="rv-where">' + benchSelect(e.bench, key) + (!many && needsBench(first, e) ? '<small class="rv-need">needs a bench</small>'
                                                                 : where ? '<small>' + esc(where) + '</small>' : '') + '</span>' +
        '<input class="rv-note" data-key="' + esc(key) + '" value="' + esc(many && !fate ? '' : e.note) + '" placeholder="Note" aria-label="Note">' +
        byLabel(first, e) + '</div>' + (!many && editing[key] ? editor(first, e, key) : '');
      if (many && open) {
        html += '<div class="rv-members">' + fam.members.map(function (r) {
          var s = effective(r);
          return '<div class="rv-row rv-member">' + '<span class="rv-exp"></span>' +
            '<span class="rv-name">' + icon(r.r || Object.keys(r.c)[0]) + '<span><b>' + esc(name(r.r) || r.i) + '</b><small class="rv-id">' +
            esc(r.i) + '</small></span></span>' +
            '<span class="rv-cost">' + costCell(r, s, r.i) + '</span>' +
            fateButtons(s.fate, r.i) + '<span class="rv-where">' + benchSelect(s.bench, r.i) +
            (needsBench(r, s) ? '<small class="rv-need">needs a bench</small>' : '') + '</span>' +
            '<input class="rv-note" data-key="' + esc(r.i) + '" value="' + esc(s.note) + '" placeholder="Note" aria-label="Note">' +
            byLabel(r, s) + '</div>' + (editing[r.i] ? editor(r, s, r.i) : '');
        }).join('') + '</div>';
      }
      return html;
    }

    function counts() {
      var c = { go: 0, stay: 0, change: 0, decide: 0 }, total = 0, by = {}, nocost = 0;
      review.records.forEach(function (r) {
        var source = r.s || 'vanilla', e;
        if (filters.source !== 'all' && source !== filters.source) return;
        e = effective(r);
        c[e.fate]++;
        by[source] = (by[source] || 0) + 1;
        if (incomplete(r, e)) nocost++;
        total++;
      });
      return { c: c, total: total, by: by, nocost: nocost };
    }

    function render() {
      var fams = families().filter(matches), n = counts(), editsCount = Object.keys(edits).length;
      summary.innerHTML = '<b>' + n.total + '</b> ' + (filters.source === 'all' ? '' : SOURCE[filters.source] + ' ') + 'recipes: ' +
        FATES.map(function (f) { return '<span class="rv-count f-' + f + '">' + n.c[f] + ' ' + (f === 'decide' ? 'to decide' : f) + '</span>'; }).join(' ') +
        (n.nocost ? ' <span class="rv-count rv-need" title="A kept recipe on no bench, or a change without a new cost">' + n.nocost +
                    ' incomplete</span>' : '') +
        (filters.source === 'all' ? ' <span class="muted">' + Object.keys(SOURCE).filter(function (k) { return n.by[k]; })
          .map(function (k) { return n.by[k] + ' ' + SOURCE[k]; }).join(', ') + '</span>' : '') +
        (editsCount ? ' <span class="rv-edits">' + editsCount + ' unsaved ' + (editsCount === 1 ? 'change' : 'changes') + '</span>' : '');
      list.innerHTML = fams.slice(0, limit).map(row).join('') || '<p class="muted">No recipe matches.</p>';
      more.hidden = fams.length <= limit;
      more.textContent = 'Show ' + Math.min(PAGE, fams.length - limit) + ' more of ' + (fams.length - limit);
    }

    /** Sets one field for a recipe or a family; an edit that matches the plan again is dropped. */
    function edit(key, field, value) {
      var entry = edits[key] || {};
      entry[field] = value;
      var records = key.indexOf('family:') === 0 ? review.records.filter(function (r) { return 'family:' + r.f === key; })
                                                 : review.records.filter(function (r) { return r.i === key; });
      function planned(r, k) {
        return k === 'bench' ? r.b || '' : k === 'note' ? r.note || '' : k === 'cost' ? same(r.nc || null) : k === 'count' ? r.nn || r.n || 1 : r.fate;
      }
      Object.keys(entry).forEach(function (k) {
        var value = k === 'cost' ? same(entry[k]) : entry[k];
        if (records.length && records.every(function (r) { return planned(r, k) === value; })) delete entry[k];
      });
      if (key.indexOf('family:') === 0) {
        records.forEach(function (r) {  // the family's new value replaces the same field on its recipes
          if (!edits[r.i]) return;
          delete edits[r.i][field];
          if (!Object.keys(edits[r.i]).length) delete edits[r.i];
        });
      }
      if (Object.keys(entry).length) edits[key] = entry; else delete edits[key];
      store();
      render();
    }

    /** A cost as a comparable string: its ingredients in order. */
    function same(cost) {
      return cost ? JSON.stringify(Object.keys(cost).sort().map(function (k) { return [k, cost[k]]; })) : 'null';
    }

    /** The new cost of a change, edited: one ingredient's amount (0 removes it), or a new ingredient. */
    function editCost(key, item, amount) {
      var r = review.records.filter(function (x) { return x.i === key; })[0];
      if (!r) return;
      var e = effective(r), cost = Object.assign({}, e.cost || r.c);
      if (amount > 0) cost[item] = amount; else delete cost[item];
      if (!Object.keys(cost).length) return;  // a recipe costs something: the last ingredient stays
      if (!(edits[key] || {}).count && !r.nn) edit(key, 'count', e.count);
      edit(key, 'cost', cost);
    }

    function decisions() {
      var out = JSON.parse(JSON.stringify(review.decisions || {}));
      Object.keys(edits).forEach(function (key) {
        if (key.indexOf('family:') === 0) {
          review.records.forEach(function (r) {  // saved recipe decisions give way on the fields the family now sets
            if ('family:' + r.f !== key || !out[r.i]) return;
            Object.keys(edits[key]).forEach(function (field) { delete out[r.i][field]; });
            if (!Object.keys(out[r.i]).length) delete out[r.i];
          });
        }
        out[key] = Object.assign({}, out[key] || {}, edits[key]);
        if (edits[key].bench !== undefined) delete out[key].place;  // the plan finds a spot in the new bench
      });
      return { about: "The user's decisions on the recipe plan (P14) for every recipe in the game, read by " +
                      "tools/workstation_plan.py after its rules and the bench designs. A key is a recipe id, family:<name> for " +
                      "every recipe of a family, or design:<bench>:<item> for a recipe only a bench design holds. A value may set " +
                      "fate (go, stay, change, decide), bench, place and note, and for a change its new cost ({item or #tag: " +
                      "amount}) and count. The showcase Control page saves this file (Save decisions); put it at " +
                      "Ark/design/workstations/vanilla_fates.json, then ./gradlew build (tools/build_workstation_data.py " +
                      "applies it to the benches in the game); python Ark/tools/workstation_plan.py lists the edits of a bench " +
                      "design file that a decision on a designed item needs. Rebuild the showcase with " +
                      "python Ark/tools/build_showcase.py.", decisions: out };
    }

    function status(message) { statusEl.textContent = message; }

    function save() {
      var text = JSON.stringify(decisions(), null, 2) + '\n';
      if (window.showSaveFilePicker) {
        window.showSaveFilePicker({ suggestedName: 'vanilla_fates.json', types: [{ description: 'Recipe decisions', accept: { 'application/json': ['.json'] } }] })
          .then(function (handle) {
            return handle.createWritable().then(function (w) { return w.write(text).then(function () { return w.close(); }); })
              .then(function () { status('Saved ' + handle.name + '. Put it at Ark/design/workstations/vanilla_fates.json and rebuild the showcase.'); });
          })
          .catch(function (err) { if (err && err.name !== 'AbortError') download(text); });
        return;
      }
      download(text);
    }

    function download(text) {
      var a = document.createElement('a');
      a.href = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
      a.download = 'vanilla_fates.json';
      a.click();
      setTimeout(function () { URL.revokeObjectURL(a.href); }, 1000);
      status('Downloaded vanilla_fates.json. Put it at Ark/design/workstations/ and rebuild the showcase.');
    }

    // ------------------------------------------------------------------------------------------ events

    list.addEventListener('click', function (ev) {
      var b = ev.target.closest('button');
      if (!b) return;
      var panel = b.closest('.rv-editor'), key = panel && panel.dataset.key;
      if (b.classList.contains('rv-exp')) { expanded[b.dataset.fam] = !expanded[b.dataset.fam]; render(); return; }
      if (b.classList.contains('rv-ed-open')) { editing[b.dataset.key] = !editing[b.dataset.key]; render(); return; }
      if (b.classList.contains('rv-ed-del')) { editCost(key, b.dataset.item, 0); return; }
      if (b.classList.contains('rv-ed-reset')) {
        if (edits[key]) { delete edits[key].cost; delete edits[key].count; if (!Object.keys(edits[key]).length) delete edits[key]; }
        store(); render(); return;
      }
      if (b.dataset.fate) {
        var single = b.dataset.key.indexOf('family:') !== 0;
        if (single && b.dataset.fate === 'change') editing[b.dataset.key] = true;  // a change asks for its cost at once
        edit(b.dataset.key, 'fate', b.dataset.fate);
      }
    });
    list.addEventListener('change', function (ev) {
      var t = ev.target, panel = t.closest('.rv-editor'), key = panel && panel.dataset.key;
      if (t.classList.contains('rv-bench')) edit(t.dataset.key, 'bench', t.value);
      if (t.classList.contains('rv-note')) edit(t.dataset.key, 'note', t.value);
      if (t.classList.contains('rv-ed-n')) editCost(key, t.dataset.item, Math.max(0, Math.round(+t.value || 0)));
      if (t.classList.contains('rv-ed-count')) edit(key, 'count', Math.max(1, Math.round(+t.value || 1)));
      if (t.classList.contains('rv-ed-add')) {
        var typed = t.value.trim(), id = (items[typed] || typed.charAt(0) === '#') ? typed : byName[typed.toLowerCase()];
        if (id) editCost(key, id, 1); else status('No item or tag is called "' + typed + '": pick one from the list.');
      }
    });
    // Every item and tag the page knows, for the editor's ingredient field: by id and by name.
    var byName = {}, datalist = document.createElement('datalist');
    datalist.id = 'rv-items';
    datalist.innerHTML = Object.keys(items).sort(function (a, b) { return name(a).localeCompare(name(b)); }).map(function (id) {
      byName[name(id).toLowerCase()] = byName[name(id).toLowerCase()] || id;
      return '<option value="' + esc(id) + '">' + esc(name(id)) + '</option>';
    }).join('');
    root.appendChild(datalist);
    root.querySelector('.rv-search').addEventListener('input', function (ev) { filters.q = ev.target.value; limit = PAGE; render(); });
    root.querySelectorAll('[data-filter]').forEach(function (el) {
      el.addEventListener(el.tagName === 'SELECT' ? 'change' : 'click', function () {
        var key = el.dataset.filter;
        if (el.tagName === 'SELECT') filters[key] = el.value;
        else if (key === 'edited' || key === 'nocost') { filters[key] = !filters[key]; el.setAttribute('aria-pressed', filters[key]); }
        else {
          filters[key] = el.dataset.value;
          root.querySelectorAll('[data-filter="' + key + '"]').forEach(function (o) { o.setAttribute('aria-pressed', o === el); });
        }
        limit = PAGE;
        render();
      });
    });
    var benchFilter = root.querySelector('select[data-filter="bench"]');
    benchFilter.innerHTML = '<option value="all">Every bench</option>' + ORDER.map(function (b) {
      return '<option value="' + b + '">' + esc(benchTitle(b)) + '</option>';
    }).join('');
    more.addEventListener('click', function () { limit += PAGE; render(); });
    root.querySelector('.rv-save').addEventListener('click', save);
    root.querySelector('.rv-copy').addEventListener('click', function () {
      var text = JSON.stringify(decisions(), null, 2);
      if (navigator.clipboard) navigator.clipboard.writeText(text).then(function () { status('Copied the decisions as JSON.'); }, function () { status('Copy failed: use Save decisions.'); });
    });
    root.querySelector('.rv-discard').addEventListener('click', function () {
      if (!Object.keys(edits).length || !window.confirm('Discard every unsaved change in this browser?')) return;
      edits = {};
      store();
      render();
      status('Unsaved changes discarded.');
    });

    render();
    return { render: render, decisions: decisions, edits: function () { return edits; } };
  }

  return { mount: mount };
})();
