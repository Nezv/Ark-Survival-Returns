"""Accessories page of the showcase: every accessory worn on a player, its slot, effect, bonuses and recipe.

Facts come from design/showcase/accessories.json (written by runData, AccessoryData.java) and the language
file; the renders come from the worn models themselves (build_accessories.py + accessory_art.py), with the
camera framed on the slot's body part. build_showcase.py calls section().
"""
import json
from pathlib import Path

import accessory_art as A
import build_accessories as B

ARK = Path(__file__).resolve().parents[1]
FACTS = ARK / 'design/showcase/accessories.json'

# camera per slot: (yaw, pitch, model-space centre y, scale)
FRAMING = {'head': (-32, 12, -7, 9.0), 'necklace': (-28, 10, 3, 8.0), 'body': (-30, 10, 5, 6.8),
           'back': (150, 18, 4, 6.8), 'belt': (-35, 12, 10, 7.5), 'charm': (-50, 10, 12, 11.0),
           'legs': (-30, 10, 16, 6.5), 'feet': (-35, 30, 20, 8.0), 'hands': (-40, 12, 10, 8.0),
           'bracelet': (-40, 12, 9, 8.5), 'ring': (-40, 12, 10, 9.0)}
SLOT_NAMES = {'head': 'Head', 'necklace': 'Necklace', 'back': 'Back', 'body': 'Body', 'bracelet': 'Bracelet',
              'hands': 'Hands', 'ring': 'Ring', 'belt': 'Belt', 'legs': 'Legs', 'feet': 'Feet', 'charm': 'Charm'}
ATTRIBUTE_NAMES = {'minecraft:armor': 'Armor', 'minecraft:knockback_resistance': 'Knockback resistance',
                   'minecraft:movement_speed': 'Speed', 'minecraft:water_movement_efficiency': 'Water movement',
                   'minecraft:movement_efficiency': 'Movement efficiency', 'minecraft:sneaking_speed': 'Sneaking speed',
                   'minecraft:oxygen_bonus': 'Oxygen bonus', 'minecraft:safe_fall_distance': 'Safe fall distance',
                   'minecraft:burning_time': 'Burning time', 'minecraft:step_height': 'Step height',
                   'minecraft:attack_damage': 'Attack damage', 'neoforge:swim_speed': 'Swim speed',
                   'arksurvivalreturns:visibility': 'Visibility to creatures', 'arksurvivalreturns:noise': 'Noise',
                   'arksurvivalreturns:scent': 'Scent', 'arksurvivalreturns:carry_capacity': 'Carry capacity'}


def bonus_text(bonus):
    amount = bonus['amount']
    name = ATTRIBUTE_NAMES.get(bonus['attribute'], bonus['attribute'])
    if bonus['operation'] == 'add_value':
        return f'{amount:+g} {name}'
    return f'{amount * 100:+.0f}% {name}'


def ingredient_text(ingredients, names):
    counts = {}
    for ingredient in ingredients:
        counts[ingredient] = counts.get(ingredient, 0) + 1
    parts = []
    for ingredient, count in counts.items():
        if ingredient.startswith('#'):
            label = ingredient.split(':')[-1].replace('_', ' ').title()
        else:
            ns, path = ingredient.split(':')
            label = names.get(f'item.{ns}.{path}') or names.get(f'block.{ns}.{path}') or path.replace('_', ' ').title()
        parts.append(f'{count} {label}' if count > 1 else label)
    return ' + '.join(parts)


def render(ident, slot):
    worn, tex, icon = B.build(ident)
    yaw, pitch, centre_y, scale = FRAMING[slot]
    pose = {'spread': 1.0} if ident == 'membrane_glider' else None
    frame = A.preview(worn, tex, slim=False, side='right' if slot in ('ring', 'bracelet') else None, pose=pose,
                      views=((yaw, pitch),), size=(300, 230), scale=scale, centre=(0, centre_y, 0), background=(0, 0, 0, 0))
    return frame, icon


def section(names, uri, e):
    facts = json.loads(FACTS.read_text(encoding='utf-8'))
    groups = {'primitive': [], 'relic': []}
    for accessory in facts['accessories']:
        ident, slot = accessory['id'], accessory['slot']
        frame, icon = render(ident, slot)
        name = names.get(f'item.arksurvivalreturns.{ident}', ident)
        effect = names.get(f'accessory.arksurvivalreturns.{ident}.effect', '')
        lore = names.get(f'accessory.arksurvivalreturns.{ident}.lore', '')
        bonuses = ''.join(f'<li>{e(bonus_text(b))}</li>' for b in accessory['bonuses'])
        made = ingredient_text(accessory['ingredients'], names)
        groups[accessory['group']].append(f'''<article class="dino block-card accessory-card">
  <div class="dino-art"><img loading="lazy" src="{uri(frame, 'PNG')}" alt="{e(name)} worn"><img class="pixel accessory-icon" src="{uri(icon.resize((48, 48), 0), 'PNG')}" alt=""></div>
  <div class="dino-body">
    <div class="dino-head"><h3>{e(name)}</h3><span class="chip ok">{e(SLOT_NAMES.get(slot, slot))}</span></div>
    <p>{e(effect)}</p>
    {f'<ul class="bonuses">{bonuses}</ul>' if bonuses else ''}
    <p class="muted"><i>{e(lore)}</i></p>
    <dl><div><dt>Made from</dt><dd>{e(made)}</dd></div></dl>
  </div>
</article>''')
    rows = []
    for trophy, sources in sorted(facts['trophies'].items()):
        icon = B.A.icon(*B.TROPHIES[trophy])
        label = names.get(f'item.arksurvivalreturns.{trophy}', trophy)
        where = ', '.join(('dug gravel or clay' if s['species'] == 'digging' else
                           names.get(f'entity.arksurvivalreturns.{s["species"]}', s['species'].title()))
                          + f' ({s["chance"] * 100:.3g}%)' for s in sources)
        rows.append(f'<tr><td><img class="pixel" src="{uri(icon.resize((32, 32), 0), "PNG")}" alt=""> {e(label)}</td><td>{e(where)}</td></tr>')
    rows.append('<tr><td>' + e(names.get('item.arksurvivalreturns.red_ochre', 'Red Ochre')) +
                '</td><td>Crafted: clay ball, rock and red dye. The binder of every relic.</td></tr>')
    return {
        'ACC_PRIMITIVE': '\n'.join(groups['primitive']),
        'ACC_RELIC': '\n'.join(groups['relic']),
        'ACC_TROPHIES': '\n'.join(rows),
        'ACC_COUNT': str(len(facts['accessories'])),
    }
