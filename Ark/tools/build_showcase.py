"""Build the project showcase: one self-contained HTML page at the repository root.

Everything is read from the live project, so rebuilding keeps the page honest:
creature facts (design/showcase/species.json, exported by runData), the tech tree (tree.json), the journal
chapters, item sprites and names, block renders, the Ark UI previews and Dashboard.csv for the roadmap.
Images are embedded as data URIs, so the file opens in Chrome from anywhere. Fonts and the Mermaid
renderer load from Google Fonts / jsDelivr when online and fall back gracefully offline.

Run from Ark after runData: python tools/build_showcase.py
"""
import base64
import csv
import datetime
import html
import io
import json
import math
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
    with zipfile.ZipFile(JAR) as jar:
        return Image.open(io.BytesIO(jar.read(f'assets/minecraft/textures/{path}.png'))).convert('RGBA')


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


def biome_tiers():
    blocks = []
    for index, tier in enumerate(('easy', 'moderate', 'hard', 'extreme', 'severe')):
        path = GENERATED / f'data/arksurvivalreturns/tags/worldgen/biome/difficulty/{tier}.json'
        if not path.is_file():
            continue
        values = load_json(path)['values']
        names = [v.split(':')[1].replace('_', ' ') for v in values if isinstance(v, str)]
        extra = sum(1 for v in values if isinstance(v, dict))  # optional Terralith biomes (I08)
        color = RANK_COLORS[min(index, 4)] if tier != 'severe' else RANK_COLORS[4]
        more = f' <span class="chip">+{extra} Terralith</span>' if extra else ''
        blocks.append(f'<div class="tier"><h4><i style="background:{color}"></i>{tier.title()}</h4><p>{e(", ".join(names))}</p>{more}</div>')
    return '\n'.join(blocks)


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
        svg = [f'<svg viewBox="0 0 {width} 268" role="img" aria-label="{e(age["title"])} technology tree">',
               f'<rect x="0" y="0" width="{width}" height="268" rx="10" fill="{age["color"]}" opacity=".32"/>']
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
    ('Prehistoric', ['rock', 'stone_knife', 'stone_hatchet', 'fire_starter', 'plant_fiber', 'flint_knife', 'spear']),
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
    out = []
    for title, ids in groups:
        tiles = []
        for item_id in ids:
            sprite = item_sprite(item_id)
            if not sprite:
                continue
            name = names.get(f'item.arksurvivalreturns.{item_id}', item_id.replace('_', ' ').title())
            tiles.append(f'<figure class="item"><img src="{sprite}" alt=""><figcaption>{e(name)}</figcaption></figure>')
        out.append(f'<div class="item-group"><h4>{e(title)}</h4><div class="item-grid">{"".join(tiles)}</div></div>')
    return '\n'.join(out)


def blocks_section():
    ortho = ARK / 'design/prehistoric-camp/orthographic'
    renders = [('stone_fire_lit', 'Stone Fire, lit', 'In game'), ('stone_fire_cooked', 'Stone Fire, meat on the spit', 'In game'),
               ('primitive_forge_lit_front', 'Primitive Forge, two blocks tall', 'In game'), ('primitive_bedroll', 'Primitive Bedroll, two blocks long', 'In game'),
               ('pot_on_fire_stew', 'Clay pot on the fire', 'Design'), ('mortar_berry_whole', 'Mortar and pestle', 'Design (B02)')]
    figs = []
    for key, caption, status in renders:
        path = ortho / f'{key}.png'
        if path.is_file():
            image = Image.open(path).convert('RGBA')
            image = image.crop(image.getbbox())
            image.thumbnail((360, 260), Image.Resampling.LANCZOS)
            figs.append(f'<figure class="render"><img loading="lazy" src="{uri(image)}" alt="{e(caption)}"><figcaption>{e(caption)}'
                        f'<span class="chip {"ok" if status == "In game" else "idea"}">{e(status)}</span></figcaption></figure>')
    camp = uri(ARK / 'docs/camp-assets.png', width=1160, quality=80)
    return '\n'.join(figs), camp


def ui_section():
    title = Image.open(ASSETS / 'textures/gui/title/background.png').convert('RGBA')
    logo = Image.open(ASSETS / 'textures/gui/title/logo.png')
    scale = 0.36
    logo_small = logo.resize((int(logo.width * scale * 1.6), int(logo.height * scale * 1.6)), Image.Resampling.LANCZOS)
    title.alpha_composite(logo_small, ((title.width - logo_small.width) // 2, 60))
    buttons = Image.open(ARK / 'src/main/resources/resourcepacks/ark_ui/assets/minecraft/textures/gui/sprites/widget/button.png')
    from PIL import ImageDraw, ImageFont
    font = ImageFont.truetype(str(ARK / 'tools/fonts/Bitter.ttf'), 26)
    font.set_variation_by_axes([600])
    draw = ImageDraw.Draw(title)
    for i, label in enumerate(('Singleplayer', 'Multiplayer', 'Options...')):
        b = buttons.resize((600, 60), Image.Resampling.NEAREST)
        top = 560 + i * 80
        title.alpha_composite(b, ((title.width - 600) // 2, top))
        draw.text((title.width // 2, top + 30), label, font=font, fill=(236, 238, 230), anchor='mm')
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
    renders, camp = blocks_section()
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
        'TIERS': biome_tiers(),
        'TREE': tree_section(),
        'FLOW': e((ARK / 'design/showcase/prehistoric-gates.mmd').read_text(encoding='utf-8')),
        'JOURNAL': journal_section(),
        'RENDERS': renders, 'CAMP': camp,
        'ITEMS': items_section(names),
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
