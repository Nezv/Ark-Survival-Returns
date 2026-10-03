"""Biomes page of the showcase: a picture of every biome the pack's Overworld generates and a summary of what spawns there.

The biome list and the pictures come from design/showcase/biomes.json and design/showcase/biomes/, which
tools/biome_pictures.py fetches from the Minecraft Wiki and the Stardust Labs (Terralith) wiki; every card
credits its picture. Who spawns where is read from the generated biome tags the spawner itself uses
(tags/worldgen/biome/spawns/<species>.json and the habitat tags they point to), so the page follows the code.
build_showcase.py calls section().
"""
import base64
import json
from collections import Counter
from pathlib import Path

ARK = Path(__file__).resolve().parents[1]
INDEX = ARK / 'design/showcase/biomes.json'
PICTURES = ARK / 'design/showcase/biomes'
TAGS = ARK / 'src/generated/resources/data/arksurvivalreturns/tags/worldgen/biome'
SOURCES = {'minecraft': 'Minecraft', 'terralith': 'Terralith'}
# Filter buttons after the habitats, in page order: key, label.
PLACES = [('underground', 'Underground'), ('none', 'No Ark creatures'), ('minecraft', 'Minecraft'), ('terralith', 'Terralith')]
EMPTY = {key: '' for key in ('BIOMES', 'BIOMES_FILTERS', 'BIOMES_SPECIES', 'BIOMES_RANKS', 'BIOMES_EMPTY')}


def members(tag):
    """Biome ids of an Ark biome tag, through its references to other Ark tags; optional entries count."""
    out = set()
    for value in json.loads((TAGS / f'{tag}.json').read_text(encoding='utf-8'))['values']:
        value = value['id'] if isinstance(value, dict) else value
        if value.startswith('#arksurvivalreturns:'):
            out |= members(value.split(':', 1)[1])
        elif value.startswith('#'):
            raise SystemExit(f'{tag}: {value} is not an Ark tag; showcase_biomes.py cannot resolve it')
        else:
            out.add(value)
    return out


def listed(words):
    return ' and '.join(words) if len(words) < 3 else ', '.join(words[:-1]) + ' and ' + words[-1]


def section(species, e, rank_colors, rank_names, habitats):
    if not INDEX.is_file():
        note = '<p class="muted">No biome pictures yet: run <code>python Ark/tools/biome_pictures.py</code>.</p>'
        return {**EMPTY, 'BIOMES_COUNT': 0, 'BIOMES_NOTE': note}
    atlas = json.loads(INDEX.read_text(encoding='utf-8'))
    spawns = {s['id']: members(f'spawns/{s["id"]}') for s in species}
    titles = {key: (title, color) for key, title, color in habitats}
    cards, counts, bare, credits = [], Counter(), [], {}
    for biome in atlas['biomes']:
        ident, name = biome['biome'], biome['name']
        namespace = ident.split(':')[0]
        here = sorted((s for s in species if ident in spawns[s['id']]), key=lambda s: (s['danger'], s['name']))
        communities = [key for key, _, _ in habitats if any(s['habitat'] == key for s in here)]
        tags = [key for key in communities if key != 'sky'] + [namespace]
        if not here:
            tags.append('none')
        if 'cave' in ident or ident == 'minecraft:deep_dark':
            tags.append('underground')
        elif not here:
            bare.append(name)
        counts.update(tags)
        counts['all'] += 1
        picture = PICTURES / biome.get('picture', '-')
        credit = biome.get('credit')
        if picture.is_file() and credit:
            data = base64.b64encode(picture.read_bytes()).decode()
            credits[credit['site']] = credit['licence']
            shot = (f'<a href="{e(credit["url"])}" target="_blank" rel="noopener" title="Picture: {e(credit["file"])}, '
                    f'{e(credit["site"])}, {e(credit["licence"])}"><img loading="lazy" decoding="async" '
                    f'src="data:image/webp;base64,{data}" alt="{e(name)}"></a>')
        else:
            shot = '<span>No picture</span>'
        # The summary under the picture: how many spawn, split by community; the names open on demand.
        if here:
            split = ''.join(f'<span><i style="background:{titles[key][1]}"></i>'
                            f'{sum(1 for s in here if s["habitat"] == key)} {e(titles[key][0].lower())}</span>' for key in communities)
            names = ''.join(f'<span class="res" data-s="{s["id"]}" title="From danger rank {s["danger"]}, {rank_names[s["danger"] - 1]}">'
                            f'<i style="background:{rank_colors[s["danger"] - 1]}"></i>{e(s["name"])}</span>' for s in here)
            summary = (f'<div class="spawns">{split}</div>'
                       f'<details><summary>{len(here)} creatures</summary><div class="res-list">{names}</div></details>')
        else:
            summary = '<p class="muted">No Ark creatures</p>'
        cards.append(f'''<article class="biome" data-tags="{' '.join(tags)}" data-species="{' '.join(s['id'] for s in here)}">
  <figure class="biome-shot">{shot}</figure>
  <div class="biome-body">
    <h3 title="{e(ident)}">{e(name)}</h3>
    <span class="biome-source">{e(SOURCES.get(namespace, namespace.title()))}</span>
    {summary}
  </div>
</article>''')
    buttons = [('all', 'All')] + [(key, title) for key, title, _ in habitats if key != 'sky'] + PLACES
    filters = ''.join(f'<button type="button" data-biome="{key}" aria-pressed="{"true" if key == "all" else "false"}">'
                      f'{e(label)} <small>{counts[key]}</small></button>' for key, label in buttons if counts[key])
    options = ''.join(f'<option value="{s["id"]}">{e(s["name"])}</option>' for s in sorted(species, key=lambda s: s['name']))
    ranks = ''.join(f'<span><i style="background:{color}"></i>{i + 1} {e(rank_names[i])}</span>' for i, color in enumerate(rank_colors))
    licences = listed([f'the {e(site)} ({e(licence)})' for site, licence in credits.items()])
    note = (f'<p class="muted">Pictures: {licences}, fetched {e(atlas["fetched"])}; each one links to its source. They show the biome '
            f'as those wikis photographed it, on vanilla terrain.</p>') if credits else ''
    # Which biomes the spawn tags leave out, said from the data so the sentence cannot go stale.
    parts = [f'the {counts["underground"]} underground biomes'] if counts['underground'] else []
    if bare:
        parts.append('on the surface ' + listed(bare))
    empty = f' {counts["none"]} biomes have no Ark wildlife: {", and ".join(parts)}.' if counts['none'] else ''
    return {'BIOMES': '\n'.join(cards), 'BIOMES_FILTERS': filters, 'BIOMES_SPECIES': options, 'BIOMES_COUNT': counts['all'],
            'BIOMES_NOTE': note, 'BIOMES_RANKS': ranks, 'BIOMES_EMPTY': e(empty)}
