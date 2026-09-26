"""Build the project showcase: one self-contained HTML page at the repository root.

Everything is read from the live project, so rebuilding keeps the page honest:
creature facts (design/showcase/species.json) and the behaviour models with their transition matrices
(design/showcase/behavior.json), both exported by runData, the habitat tags, the tech tree (tree.json), the
journal chapters, item sprites and names, block renders (render_blocks.py, from the shipped models), the recipe-gate
spine (build_item_flow.py, drawn by spine.js), the Ark UI previews and Dashboard.csv for the roadmap.
Images are embedded as data URIs, so the file opens in Chrome from anywhere. Fonts and the Mermaid
renderer load from Google Fonts / jsDelivr when online and fall back gracefully offline. Each section is its own page:
the nav, the home index and the pager switch between them.

Vanilla item sprites come from the Minecraft sources jar that a Gradle build unpacks. Without it (a fresh
checkout), the sprites the current page already shows are carried over instead of dropped.

Run from Ark after runData: python tools/build_showcase.py
"""
import base64
import csv
import datetime
import html
import io
import json
import math
import re
import zipfile
from pathlib import Path
from PIL import Image

ARK = Path(__file__).resolve().parents[1]
REPO = ARK.parent
OUT = REPO / 'Ark-Survival-Returns.html'
JAR = ARK / 'build/moddev/artifacts/minecraft-patched-26.1.2.109-sources.jar'
ASSETS = ARK / 'src/main/resources/assets/arksurvivalreturns'
GENERATED = ARK / 'src/generated/resources'
PREVIEW_BG = (19, 30, 39)
RANK_COLORS = ['#4bae66', '#a8ca53', '#e4c653', '#e98646', '#994eb3']
RANK_NAMES = ['Easy', 'Moderate', 'Hard', 'Severe', 'Extreme']
MAP_HEIGHT = 344  # TechScreen.MAP_HEIGHT: four lanes around the gate row
FOLDERS = {'pteranodon': 'Piterodon', 'therizinosaurus': 'Therezinosaur', 'brontosaurus': 'Brontosaur',
           'tyrannosaurus': 'Tyranosaur', 'giganotosaurus': 'Giganotosaur', 'acrocanthosaurus': 'Acrochantosaur'}
e = html.escape


# ------------------------------------------------------------------------------------------ images

def uri(image, fmt='WEBP', quality=82, width=None):
    if isinstance(image, (str, Path)):
        image = Image.open(image)
    if width and image.width > width:
        image = image.resize((width, round(image.height * width / image.width)), Image.Resampling.LANCZOS)
    buffer = io.BytesIO()
    if fmt == 'PNG':
        image.save(buffer, 'PNG', optimize=True)
    else:
        image.save(buffer, fmt, quality=quality, method=6)
    return f'data:image/{fmt.lower()};base64,' + base64.b64encode(buffer.getvalue()).decode()


def vanilla_texture(path):
    if not JAR.is_file():
        return None
    with zipfile.ZipFile(JAR) as jar:
        return Image.open(io.BytesIO(jar.read(f'assets/minecraft/textures/{path}.png'))).convert('RGBA')


def published_sprites():
    """Item sprites of the page as last built, by caption: the fallback when the Minecraft jar is missing."""
    if not OUT.is_file():
        return {}
    page = OUT.read_text(encoding='utf-8')
    return {html.unescape(name): src for src, name in
            re.findall(r'<figure class="item"><img src="([^"]+)" alt=""><figcaption>(.*?)</figcaption></figure>', page)}


def cutout(path):
    """A creature preview render with its flat backdrop, caption and ground line removed."""
    image = Image.open(path).convert('RGB').crop((20, 100, 1080, 720))
    rgba = Image.new('RGBA', image.size)
    src, dst = image.load(), rgba.load()
    for y in range(image.height):
        diffs = [sum(abs(src[x, y][i] - PREVIEW_BG[i]) for i in range(3)) for x in range(image.width)]
        if sum(d > 30 for d in diffs) > image.width * 0.8:
            continue
        for x, d in enumerate(diffs):
            if d > 18:
                dst[x, y] = (*src[x, y], 255 if d > 40 else int(255 * (d - 18) / 22))
    box = rgba.getbbox()
    rgba = rgba.crop(box) if box else rgba
    rgba.thumbnail((420, 250), Image.Resampling.LANCZOS)
    return rgba


def creature_image(species_id):
    folder = REPO / 'Creatures' / FOLDERS.get(species_id, species_id.capitalize())
    for candidate in (folder / 'skin/previews/three_quarter.png', folder / 'textures/previews/three_quarter.png',
                      folder / 'previews/model.png'):
        if candidate.is_file():
            return uri(cutout(candidate), quality=80), 'textured' if 'three_quarter' in candidate.name else 'model'
    return '', 'none'


# -------------------------------------------------------------------------------------------- data

def load_json(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def lang():
    return load_json(GENERATED / 'assets/arksurvivalreturns/lang/en_us.json')


def dashboard():
    rows = []
    for name in ('Dashboard.csv', 'Dashboard.additions.csv'):
        path = REPO / name
        if not path.is_file():
            continue
        with path.open(newline='', encoding='utf-8') as handle:
            for row in csv.DictReader(handle):
                key = (row.get('ID', '').strip(), row.get('Item', '').strip())
                if key[0] and key not in {(r['ID'].strip(), r['Item'].strip()) for r in rows}:
                    rows.append(row)
    return rows


def status_class(status):
    s = status.lower()
    if s.startswith('done') and 'unverified' not in s or s.startswith('verified') or s.startswith('implemented') or s.startswith('published'):
        return 'ok'
    if 'unverified' in s or 'pending' in s:
        return 'check'
    if 'discussion' in s or 'planned' in s:
        return 'idea'
    return 'todo'


# ---------------------------------------------------------------------------------------- sections

def creatures_section(species):
    cards = []
    for s in sorted(species, key=lambda s: (s['danger'], s['name'])):
        image, kind = creature_image(s['id'])
        pips = ''.join(f'<i style="background:{RANK_COLORS[i] if i < s["danger"] else "var(--line)"}"></i>' for i in range(5))
        group = f'{s["groupMin"]}' if s['groupMin'] == s['groupMax'] else f'{s["groupMin"]}-{s["groupMax"]}'
        tags = [s['realm']] + (['predator'] if s['predator'] else ['herbivore']) + (['apex'] if s['apex'] else []) + (['cold'] if s['cold'] else [])
        filters = ' '.join(tags)
        cards.append(f'''<article class="dino" data-tags="{filters}">
  <div class="dino-art">{f'<img loading="lazy" src="{image}" alt="{e(s["name"])} model">' if image else ''}{'<span class="art-tag">textured</span>' if kind == 'textured' else ''}</div>
  <div class="dino-body">
    <div class="dino-head"><h3>{e(s["name"])}</h3><span class="pips" title="Appears from danger rank {s["danger"]}">{pips}</span></div>
    <div class="chips">{''.join(f'<span class="chip">{t}</span>' for t in tags)}</div>
    <dl><div><dt>Health</dt><dd>{s["health"]:g}</dd></div><div><dt>Damage</dt><dd>{s["damage"]:g}</dd></div>
    <div><dt>Group</dt><dd>{group}</dd></div><div><dt>Taming</dt><dd>{s["taming"]}</dd></div><div><dt>Meat</dt><dd>{s["meat"]}</dd></div></dl>
  </div>
</article>''')
    return '\n'.join(cards)


HABITATS = [('temperate', 'Temperate land', '#6f9a4f'), ('wetland', 'Wetlands', '#3f8f80'),
            ('cold', 'Snow and ice', '#86b3d3'), ('sea', 'Sea', '#3f6fa3'), ('sky', 'Sky', '#c49a45')]


def habitats_section(species):
    """The five habitat tags (generated from Species.habitat) with their biomes and residents."""
    lists, blocks = {}, []
    for key, title, color in HABITATS:
        values = load_json(GENERATED / f'data/arksurvivalreturns/tags/worldgen/biome/habitat/{key}.json')['values']
        lists[key] = [v.split(':')[1].replace('_', ' ') for v in values if isinstance(v, str)]
        extra = sum(1 for v in values if isinstance(v, dict))  # optional Terralith biomes (I08)
        if key == 'sky' and set(lists[key]) == set(lists['temperate']) | set(lists['cold']):
            biomes = '<p class="plain">Every temperate and snowy land biome.</p>'
        else:
            biomes = f'<p>{e(", ".join(lists[key]))}</p>'
        residents = ''.join(f'<span class="chip">{e(s["name"])}</span>'
                            for s in sorted(species, key=lambda s: s['name']) if s.get('habitat') == key)
        more = f'<span class="chip">+{extra} Terralith</span>' if extra else ''
        blocks.append(f'<div class="tier"><h4><i style="background:{color}"></i>{e(title)}</h4>{biomes}'
                      f'<div class="chips">{residents}{more}</div></div>')
    return '\n'.join(blocks)


# ---------------------------------------------------------------------------------------- behaviour

TIER_STYLE = {'FULL': ('Full detail', '#4bae66'), 'AMBIENT': ('Ambient', '#e4c653'), 'DORMANT': ('Dormant', '#8a8f86')}
PHASE_STYLE = {'HUNT': ('Hunt', 'p-hunt'), 'SLEEP': ('Sleep', 'p-sleep'), 'ROAM': ('Roam', 'p-roam')}
LAP_TITLES = {'LOOP': 'Wide wobbling loop', 'FIGURE_EIGHT': 'Figure eight over the nest',
              'THERMAL': 'Thermal circle, gaining height'}


def state_name(state):
    return state.replace('_', ' ').capitalize()


def tier_cards(data):
    cards, inner = [], 0
    for tier in data['tiers']:
        title, color = TIER_STYLE[tier['id']]
        span = f'Within {tier["radius"]} blocks' if inner == 0 else f'{inner} to {tier["radius"]} blocks'
        cards.append(f'<div class="tier"><h4><i style="background:{color}"></i>{e(title)}</h4>'
                     f'<b class="range">{span}</b><p class="plain">{e(tier["summary"])}</p></div>')
        inner = tier['radius']
    return '\n'.join(cards)


def day_schedule(schedule):
    """Carnivore and herbivore days as bars over the 24 in-game hours (tick 0 is 06:00)."""
    def hour(tick):
        return (tick + 6000) % 24000 / 1000

    def bar(segments):
        spans = []
        for start, end, phase in segments:
            end = end if end > start else end + 24
            for a, b in ((start, min(end, 24)), (0, end - 24)):
                if b > a:
                    label, css = PHASE_STYLE[phase]
                    spans.append(f'<span class="{css}" style="left:{a / 24 * 100:.3f}%;width:{(b - a) / 24 * 100:.3f}%" '
                                 f'title="{label}">{label if b - a >= 2.5 else ""}</span>')
        return ''.join(spans)

    dusk, dawn = hour(schedule['nightStart']), hour(schedule['nightEnd'])
    daylight = (schedule['nightStart'] - schedule['nightEnd']) % 24000
    wake = hour(schedule['nightEnd'] + schedule['carnivoreDaySleep'] * daylight)
    rows = [('Carnivores', [(dawn, wake, 'SLEEP'), (wake, dusk, 'ROAM'), (dusk, dawn, 'HUNT')], schedule['carnivore']),
            ('Herbivores', [(dawn, dusk, 'ROAM'), (dusk, dawn, 'SLEEP')], schedule['herbivore'])]
    body = ''.join(f'<div class="day-row"><b>{name}</b><div class="day-bar">{bar(segments)}</div>'
                   f'<small class="muted">{e(text)}</small></div>' for name, segments, text in rows)
    axis = ''.join(f'<span>{h:02d}:00</span>' for h in range(0, 25, 6))
    return f'<div class="day">{body}<div class="day-axis"><span></span><div>{axis}</div></div></div>'


def matrix_table(tier):
    """Transition matrix: rows are the current state, columns the next; a filled cell lists its rules."""
    states, cells = tier['states'], tier['matrix']
    head = ''.join(f'<th scope="col"><span>{e(state_name(s))}</span></th>' for s in states)
    rows = []
    for a in states:
        tds = []
        for b in states:
            why = cells.get(a, {}).get(b)
            css = 'hit diag' if why and a == b else 'hit' if why else 'diag' if a == b else ''
            if why:
                mark = '&#9679;' if len(why) == 1 else f'&#9679;<sup>{len(why)}</sup>'
                tds.append(f'<td class="{css}" tabindex="0" data-from="{e(state_name(a))}" data-to="{e(state_name(b))}" '
                           f'data-why="{e(" | ".join(why))}" title="{e("; ".join(why))}">{mark}</td>')
            else:
                tds.append(f'<td class="{css}"></td>' if css else '<td></td>')
        rows.append(f'<tr><th scope="row">{e(state_name(a))}</th>{"".join(tds)}</tr>')
    return (f'<table class="matrix"><thead><tr><th class="corner" scope="col">from / to</th>{head}</tr></thead>'
            f'<tbody>{"".join(rows)}</tbody></table>')


def short_reason(reason):
    """First clause of a rule, for diagram labels: 'attacked: retaliate' -> 'attacked'."""
    clause = reason.split(':')[0].split(',')[0].strip()
    return re.sub(r'[;#{}<>"]', '', clause)


def state_diagram(tier):
    """
    Mermaid source for one tier. A rule that fires from most states (being attacked, nothing pressing...) is
    drawn once from an "Any state" node instead of from every state, so the diagram stays readable; the
    matrix beside it still lists every source.
    """
    states, matrix = tier['states'], tier['matrix']
    sources = {}
    for a, row in matrix.items():
        for b, why in row.items():
            for reason in why:
                sources.setdefault((b, reason), set()).add(a)
    common = {key for key, found in sources.items() if len(found) >= max(4, (len(states) - 1) / 2)}
    edges = []
    for target in states:
        reasons = [reason for (b, reason) in sorted(common) if b == target]
        if reasons:
            edges.append(('any_state', target.lower(), reasons))
    for a, row in matrix.items():
        for b, why in row.items():
            rest = [reason for reason in why if (b, reason) not in common]
            if rest:
                edges.append((a.lower(), b.lower(), rest))
    labelled = len(edges) <= 40
    # A hub fans out best left to right; a chain of phases (take off, roam, land, perch) reads top to bottom.
    direction = 'LR' if sum(1 for edge in edges if edge[0] == 'any_state') >= 4 else 'TB'
    lines = ['stateDiagram-v2', f'  direction {direction}',
             '  classDef anyState fill:transparent,stroke:#8a8f86,stroke-width:1.5px,stroke-dasharray:4 3']
    if common:
        lines.append('  state "Any state" as any_state')
    lines += [f'  state "{state_name(s)}" as {s.lower()}' for s in states]
    for a, b, why in edges:
        label = ' / '.join(dict.fromkeys(short_reason(r) for r in why)) if labelled else ''
        lines.append(f'  {a} --> {b}' + (f': {label}' if label else ''))
    if common:
        lines.append('  class any_state anyState')
    return '\n'.join(lines)


def bridges_list(model, actions, names, species_names):
    """Level 2: the animation-timed actions a creature plays while it changes state."""
    motion = {a['id']: a['motion'].lower() for a in actions}
    rows = []
    for bridge in model['bridges']:
        beats = []
        for beat in bridge['beats']:
            label = names.get(f'action.arksurvivalreturns.{beat["action"].lower()}', state_name(beat['action']))
            length = f'{beat["ticks"] / 20:.1f} s' if beat['ticks'] else 'held'
            beats.append(f'<span class="beat m-{motion.get(beat["action"], "hold")}">{e(label)}<small>{length}</small></span>')
        who = species_names.get(bridge['species'], bridge['species'].title())
        reflex = ' &middot; reflex, no display' if bridge['urgent'] else ''
        rows.append(f'<li><div class="bridge-head"><b>{e(state_name(bridge["from"]))} &rarr; {e(state_name(bridge["to"]))}</b>'
                    f'<span class="muted">{e(bridge["when"])} &middot; {e(who)}{reflex}</span></div>'
                    f'<div class="beats">{"<i>&rarr;</i>".join(beats)}</div></li>')
    return f'<ol class="bridges">{"".join(rows)}</ol>'


def lap_figures(model):
    """Sample laps seen from above with the nest in the middle, and the height over one lap underneath."""
    figures = []
    for curve in model.get('curves', []):
        points, title = curve['points'], LAP_TITLES.get(curve['kind'], state_name(curve['kind']))
        reach = max(max(abs(p[0]), abs(p[2])) for p in points) * 1.12 or 1
        route = ' '.join(f'{50 + p[0] / reach * 46:.1f},{50 + p[2] / reach * 46:.1f}' for p in points)
        low, high = min(p[1] for p in points), max(p[1] for p in points)
        span = max(high - low, 1)
        height = ' '.join(f'{i / (len(points) - 1) * 100:.1f},{22 - (p[1] - low) / span * 18:.1f}' for i, p in enumerate(points))
        figures.append(f'<figure class="lap"><svg viewBox="0 0 100 100" role="img" aria-label="{e(title)}">'
                       f'<circle cx="50" cy="50" r="2.4" class="nest"/><polyline points="{route}" class="route"/></svg>'
                       f'<svg viewBox="0 0 100 24" class="profile" aria-hidden="true"><polyline points="{height}"/></svg>'
                       f'<figcaption>{e(title)}<small>{low:.0f} to {high:.0f} blocks above the nest</small></figcaption></figure>')
    return f'<div class="laps">{"".join(figures)}</div>' if figures else ''


def model_panel(model, actions, names, species_names):
    tier_tabs, tier_panels = [], []
    for index, tier in enumerate(model['tiers']):
        title = TIER_STYLE[tier['tier']][0]
        selected = 'true' if index == 0 else 'false'
        tier_tabs.append(f'<button type="button" role="tab" data-tier="{tier["tier"]}" aria-selected="{selected}">'
                         f'Tier {index + 1}: {e(title)}</button>')
        count = sum(len(row) for row in tier['matrix'].values())
        diagram = state_diagram(tier)
        legend = ('<p class="muted diagram-note">In the diagram, the dashed "Any state" stands for a rule that fires from '
                  'most states; the matrix lists every source.</p>' if 'any_state' in diagram else '')
        tier_panels.append(
            f'<div class="tier-panel" data-tier="{tier["tier"]}" role="tabpanel">'
            f'<p class="muted">{e(tier["note"])} <span class="chip">{len(tier["states"])} states, {count} transitions</span></p>'
            f'<div class="machine"><div class="matrix-wrap">{matrix_table(tier)}</div>'
            f'<div class="diagram"><div class="diagram-svg"></div><pre class="diagram-src">{e(diagram)}</pre></div></div>'
            f'{legend}</div>')
    chips = ''.join(f'<span class="chip">{e(r)}</span>' for r in model['realms'].split())
    chips += '<span class="chip">predator</span>' if model['predator'] else ''
    extra = ''
    if model['bridges']:
        extra += ('<h4>Level 2: the actions inside a change of state</h4><p class="muted">Every change of state first plays a '
                  'short bridge timed to the rig\'s own clips (lengths below are the named species\'). A hit or a threat at the '
                  'body is a reflex and skips the display.</p>' + bridges_list(model, actions, names, species_names))
    if model.get('curves'):
        extra += ('<h4>Flight curves</h4><p class="muted">One sample lap of each shape from the flight code. Each lap may '
                  'pick a new shape, and the bird follows a point that slides along it, so it banks and climbs smoothly.</p>'
                  + lap_figures(model))
    return (f'<div class="model" data-model="{model["id"]}" role="tabpanel">'
            f'<div class="model-head"><h3>{e(model["title"])}</h3>{chips}</div>'
            f'<p class="lede">{e(model["summary"])}</p>'
            f'<div class="tabs tier-tabs" role="tablist" aria-label="Distance tier">{"".join(tier_tabs)}</div>'
            f'{"".join(tier_panels)}'
            f'<div class="node-info cell-info" aria-live="polite"><b>Pick a filled cell</b>'
            f'<span class="muted">The rules behind that transition appear here.</span></div>'
            f'{extra}</div>')


def behavior_section(names, species):
    data = load_json(ARK / 'design/showcase/behavior.json')
    species_names = {s['id']: s['name'] for s in species}
    tabs = ''.join(f'<button type="button" role="tab" data-model="{m["id"]}" aria-selected="{"true" if i == 0 else "false"}">'
                   f'{e(m["title"])}</button>' for i, m in enumerate(data['models']))
    panels = '\n'.join(model_panel(m, data['actions'], names, species_names) for m in data['models'])
    motions = {}
    for action in data['actions']:
        label = names.get(f'action.arksurvivalreturns.{action["id"].lower()}', state_name(action['id']))
        cue = '<small>+ clip</small>' if action['cue'] else ''
        motions.setdefault(action['motion'].lower(), []).append(f'<span class="beat m-{action["motion"].lower()}">{e(label)}{cue}</span>')
    legend = ''.join(f'<div class="motion"><b>{e(motion.title())}</b>{"".join(chips)}</div>' for motion, chips in motions.items())
    margin = data['margin']
    return {
        'BEHAVIORTIERS': tier_cards(data),
        'BEHAVIORMARGIN': f'{margin} block{"s" if margin != 1 else ""}',
        'SCHEDULE': day_schedule(data['schedule']),
        'MODELTABS': tabs,
        'MODELS': panels,
        'ACTIONS': legend,
    }


def starfish_path(cx, cy, radius):
    from ark_shapes import star_path
    return star_path(cx, cy, radius * 0.96)


def trigger_text(trigger):
    kind = trigger.get('type')
    items = ', '.join(i.split(':')[1].replace('_', ' ') for i in trigger.get('items', trigger.get('blocks', [])))
    return {
        'collect': f'Have: {items}' + (' (all of them)' if trigger.get('distinct') else ''),
        'craft': f'Craft: {items}',
        'consume': f'Eat: {items}' + (f' (tier {trigger["dryness"]})' if trigger.get('dryness') else ''),
        'place_block': f'Light: {items}',
        'event': {'damage_creature': 'Damage a dinosaur', 'tame_work': 'A tame completes a work job',
                  'light_torch': 'Light a torch in the stone fire'}.get(trigger.get('kind'), trigger.get('kind', '')),
        'tame': 'Tame any dinosaur',
    }.get(kind, 'Planned: trigger not wired yet')


def tree_section():
    tree = load_json(ASSETS.parent.parent / 'data/arksurvivalreturns/tech_tree/tree.json')
    icons = {}
    for n in tree['nodes']:
        icon = ASSETS / f'textures/gui/tech/icons/{n["id"]}.png'
        if icon.is_file():
            icons[n['id']] = uri(Image.open(icon), 'PNG')
    by_id = {n['id']: n for n in tree['nodes']}
    parts = []
    for age in tree['ages']:
        nodes = [n for n in tree['nodes'] if n['age'] == age['id']]
        xs = [n['box'][0] for n in nodes]
        left, right = min(xs) - 60, max(xs) + 60
        width = right - left
        wired = sum(n['trigger']['type'] != 'future' for n in nodes)
        svg = [f'<svg viewBox="0 0 {width} {MAP_HEIGHT}" role="img" aria-label="{e(age["title"])} technology tree">',
               f'<rect x="0" y="0" width="{width}" height="{MAP_HEIGHT}" rx="10" fill="{age["color"]}" opacity=".32"/>']
        for n in nodes:
            if n['kind'] == 'side':
                continue
            for dep in n['requires']:
                a = by_id.get(dep)
                if not a or a['age'] != n['age']:
                    continue
                ax, ay = a['box'][0] - left, a['box'][1]
                nx, ny = n['box'][0] - left, n['box'][1]
                mid = nx - 44 if len(n['requires']) >= 3 else ax + 55
                svg.append(f'<path class="edge" d="M{ax + 23},{ay} H{mid} V{ny} H{nx - 23}"/>')
        for n in nodes:
            x, y = n['box'][0] - left, n['box'][1]
            state = 'secret' if n['kind'] == 'side' else ('wired' if n['trigger']['type'] != 'future' else 'planned')
            title = '???' if state == 'secret' else n['title']
            task = 'Secret bonus food. Revealed when a tribe completes it.' if state == 'secret' else n['task']
            how = '' if state == 'secret' else trigger_text(n['trigger'])
            svg.append(f'<g class="node {state}" tabindex="0" data-title="{e(title)}" data-task="{e(task)}" data-how="{e(how)}" '
                       f'data-kind="{e(n["kind"])}"><path d="{starfish_path(x, y, 27)}"/>')
            if state != 'secret' and n['id'] in icons:
                svg.append(f'<image href="{icons[n["id"]]}" x="{x - 17}" y="{y - 17}" width="34" height="34"/>')
            elif state == 'secret':
                svg.append(f'<text x="{x}" y="{y + 5}" class="secret-mark">???</text>')
            svg.append(f'<text x="{x}" y="{y + 42}" class="node-label">{e(title)}</text></g>')
        svg.append('</svg>')
        parts.append(f'''<div class="age">
  <div class="age-head"><h3>{e(age["title"])}</h3><span class="chip {'ok' if wired == len(nodes) else 'todo'}">{wired} of {len(nodes)} nodes wired</span>
    <span class="lanes">{' · '.join(e(lane['title']) for lane in age['lanes'])}</span></div>
  <div class="tree-scroll">{''.join(svg)}</div>
</div>''')
    return '\n'.join(parts)


def journal_section():
    cards = []
    for name in ('journal', 'camp', 'work', 'homestead', 'guardian'):
        data = load_json(ARK / f'config/ftbquests/quests/lang/en_us/{name}.json5')
        title = next(v for k, v in data.items() if k.startswith('chapter.') and k.endswith('.title'))
        subtitle = next((v[0] for k, v in data.items() if k.endswith('chapter_subtitle')), '')
        quests = [v for k, v in data.items() if k.startswith('quest.') and k.endswith('.title')]
        cards.append(f'<div class="card"><h4>{e(title)}</h4><p class="muted">{e(subtitle)}</p>'
                     f'<ol class="quests">{"".join(f"<li>{e(q)}</li>" for q in quests)}</ol></div>')
    return '\n'.join(cards)


ITEM_GROUPS = [
    ('Prehistoric', ['rock', 'sharp_rock', 'stone_knife', 'stone_hatchet', 'fire_starter', 'plant_fiber', 'flint_knife']),
    ('Keratin tier', ['keratin', 'keratin_spear', 'keratin_helmet', 'keratin_chestplate', 'keratin_leggings', 'keratin_boots']),
    ('Food and meat', ['dried_meat', 'dried_ration', 'hearty_stew', 'trail_mix', 'tintoberry', 'amarberry', 'azulberry', 'narcoberry']),
    ('Taming and tribe', ['tranquilizer_arrow', 'improved_tranquilizer_arrow', 'concentrated_sedative', 'companion_whistle',
                          'field_journal', 'pack_harness', 'reinforced_harness', 'fiber_bandage']),
    ('Guardian', ['allosaur_heart', 'workshop_schematic', 'guardian_trophy']),
]


def item_sprite(item_id):
    model = GENERATED / f'assets/arksurvivalreturns/models/item/{item_id}.json'
    if not model.is_file():
        return None
    layer = load_json(model).get('textures', {}).get('layer0')
    if not layer:
        return None
    namespace, path = layer.split(':') if ':' in layer else ('minecraft', layer)
    if namespace == 'arksurvivalreturns':
        file = ASSETS / f'textures/{path}.png'
        image = Image.open(file).convert('RGBA') if file.is_file() else None
    else:
        image = vanilla_texture(path)
    if image is None:
        return None
    image = image.crop((0, 0, image.width, image.width))  # animated strips: first frame
    return uri(image.resize((32, 32), Image.Resampling.NEAREST), 'PNG')


def items_section(names):
    meats = [p.stem for p in sorted((GENERATED / 'assets/arksurvivalreturns/models/item').glob('*_meat.json'))
             if p.stem.startswith(('raw_', 'cooked_'))]
    groups = ITEM_GROUPS[:2] + [('Dinosaur meat', meats)] + ITEM_GROUPS[2:]
    published = {} if JAR.is_file() else published_sprites()
    out = []
    for title, ids in groups:
        tiles = []
        for item_id in ids:
            name = names.get(f'item.arksurvivalreturns.{item_id}', item_id.replace('_', ' ').title())
            sprite = item_sprite(item_id) or published.get(name)
            if not sprite:
                continue
            tiles.append(f'<figure class="item"><img src="{sprite}" alt=""><figcaption>{e(name)}</figcaption></figure>')
        out.append(f'<div class="item-group"><h4>{e(title)}</h4><div class="item-grid">{"".join(tiles)}</div></div>')
    return '\n'.join(out)


def blocks_section():
    """One card per Ark block, each rendered the same way from the model the game ships."""
    import render_blocks
    cards = []
    for (key, name, status, size, recipe, text, _), tile in render_blocks.renders():
        css = 'ok' if status == 'In game' else 'idea'
        cards.append(f'''<article class="dino block-card">
  <div class="dino-art"><img loading="lazy" src="{uri(tile)}" alt="{e(name)} model"></div>
  <div class="dino-body">
    <div class="dino-head"><h3>{e(name)}</h3><span class="chip {css}">{e(status)}</span></div>
    <p class="muted">{e(text)}</p>
    <dl><div><dt>Size</dt><dd>{e(size)}</dd></div><div><dt>Made from</dt><dd>{e(recipe)}</dd></div></dl>
  </div>
</article>''')
    return '\n'.join(cards)


def spine_section():
    """The recipe-gate spine from the Recipe Gates chart, with its notes and open decisions."""
    import build_item_flow as flow
    data, notes = flow.spine_payload()
    css, js = flow.spine_assets()
    return {
        'SPINECSS': css, 'SPINEJS': js,
        'SPINEDATA': json.dumps(data, ensure_ascii=False, separators=(',', ':')),
        'SPINELEGEND': flow.legend_html(), 'SPINENOTES': flow.notes_html(notes), 'SPINENOTECOUNT': len(notes),
        'SPINEDECISIONS': flow.decisions_html(),
    }


def ui_section():
    # The title screen as tools/preview_title_scene.py renders it: both GLSL layers, the creature and the layout.
    title = Image.open(ARK / 'docs/title-scene.jpg').convert('RGB')
    parchment = Image.new('RGBA', (4 * 150, 150), (240, 226, 196, 255))
    for i, state in enumerate(('locked', 'ready', 'complete', 'hover')):
        node = Image.open(ASSETS / f'textures/gui/tech/node_{state}.png').resize((128, 128), Image.Resampling.LANCZOS)
        parchment.alpha_composite(node, (i * 150 + 11, 11))
    return uri(title, width=1160, quality=80), uri(ARK / 'design/ui-rework/ui-preview.png', 'PNG'), uri(parchment, 'PNG')


STATUS_LABEL = {'integrated': ('ok', 'Integrated'), 'pinned': ('check', 'Pinned, tuning later'),
                'planned-layout': ('check', 'Fork built, layout in test'), 'blocked': ('todo', 'Blocked')}
BUILD_LABEL = {'release': 'Official release, Ark-branded', 'source': 'Built from source, Ark fork',
               'embedded': 'Embedded in the Ark jar', 'shader': 'Shader pack, unmodified', 'blocked': 'No 26.1 build yet'}


def integrations_section():
    manifest = load_json(ARK / 'config/integrations.json')
    cards = []
    for integ in manifest['integrations']:
        css, label = STATUS_LABEL.get(integ['status'], ('idea', integ['status']))
        mods = []
        for mod in integ['mods']:
            source = mod.get('source', '')
            link = f'<a href="{e(source)}" rel="noopener" target="_blank">source</a>' if source.startswith('http') else 'closed source'
            mods.append(f'<li><b>{e(mod["arkName"])}</b><span class="muted">{e(BUILD_LABEL.get(mod.get("build", "release"), ""))}'
                        f' · {e(mod.get("license", ""))} · {link}</span></li>')
        cards.append(f'''<article class="integration">
  <div class="integration-head"><code>{e(integ["id"])}</code><h3>{e(integ["title"])}</h3>
    <span class="chip {css}">{e(label)}</span><span class="chip">{e(integ["side"])}</span></div>
  <ul class="mods">{''.join(mods)}</ul>
  <dl class="spec"><dt>Uses</dt><dd>{e(integ["uses"])}</dd><dt>Changed</dt><dd>{e(integ["changed"])}</dd>
  <dt>Next</dt><dd>{e(integ["next"])}</dd></dl>
</article>''')
    count = sum(1 for i in manifest['integrations'] for m in i['mods'] if m.get('build') in ('release', 'source'))
    return '\n'.join(cards), count


def roadmap_section(rows):
    body = []
    for row in rows:
        status = row.get('Status', '').strip().split('\n')[0].split(';')[0].strip()
        status = status if len(status) <= 70 else status[:67].rstrip() + '...'
        body.append(f'<tr><td>{e(row.get("Type", "").strip())}</td><td><code>{e(row.get("ID", "").strip())}</code></td>'
                    f'<td>{e(row.get("Item", "").strip())}</td><td>{e(row.get("Executed by", "").strip().split(chr(10))[0])}</td>'
                    f'<td><span class="chip {status_class(status)}">{e(status or "Not set")}</span></td></tr>')
    return '\n'.join(body)


# -------------------------------------------------------------------------------------------- page

def build():
    species = load_json(ARK / 'design/showcase/species.json')['species']
    names = lang()
    rows = dashboard()
    hero = uri(ASSETS / 'textures/gui/title/background.png', width=1600, quality=78)
    logo = uri(ASSETS / 'textures/gui/title/logo.png', 'PNG', width=900)
    title_shot, ui_sheet, nodes = ui_section()
    tree = load_json(ASSETS.parent.parent / 'data/arksurvivalreturns/tech_tree/tree.json')
    item_count = len([p for p in (GENERATED / 'assets/arksurvivalreturns/items').glob('*.json') if not p.stem.endswith('spawn_egg')])
    integrations, integrated = integrations_section()
    stats = [(len(species), 'creatures'), (5, 'danger ranks'), (len(tree['nodes']), 'tech nodes'), (integrated, 'integrated mods'),
             (item_count, 'items and blocks')]
    page = TEMPLATE
    replacements = {
        'HERO': hero, 'LOGO': logo,
        'STATS': ''.join(f'<div class="stat"><b>{n}</b><span>{e(label)}</span></div>' for n, label in stats),
        'CREATURES': creatures_section(species),
        'HABITATS': habitats_section(species),
        **behavior_section(names, species),
        'TREE': tree_section(),
        **spine_section(),
        'JOURNAL': journal_section(),
        'BLOCKS': blocks_section(),
        'ITEMS': items_section(names),
        **__import__('showcase_accessories').section(names, uri, e),
        'TITLESHOT': title_shot, 'UISHEET': ui_sheet, 'NODES': nodes,
        'ROADMAP': roadmap_section(rows),
        'INTEGRATIONS': integrations,
        'ARKINVENTORY': uri(ARK / 'design/ui-rework/ark-inventory.png', 'PNG'),
        'DATE': datetime.date.today().isoformat(),
        'RANKCOLORS': json.dumps(RANK_COLORS), 'RANKNAMES': json.dumps(RANK_NAMES),
        'STARPATH': starfish_path(30, 30, 27),
    }
    for key, value in replacements.items():
        page = page.replace('%%' + key + '%%', str(value))
    OUT.write_text(page, encoding='utf-8')
    print(f'Wrote {OUT} ({OUT.stat().st_size / 1e6:.1f} MB, {len(species)} creatures, {len(rows)} roadmap rows)')


TEMPLATE = (Path(__file__).with_name('showcase_template.html')).read_text(encoding='utf-8')

if __name__ == '__main__':
    build()
