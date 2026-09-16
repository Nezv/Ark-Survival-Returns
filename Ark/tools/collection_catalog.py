"""Asset/controller contracts for the ice, flying, aquatic and swamp collection.

Companion to `expansion_catalog.py`. Every entry has a realm, a habitat family and
the runtime role clips selected from that creature's own converted clip library.

realm
    LAND        terrestrial; uses the saved land-habitat system
    AMPHIBIOUS  bank dweller that also swims; land habitat with a swim clip set
    WATER       water-bound; uses the saved aquatic-habitat system
    AIR         flying; uses the saved nest-colony system

`family` names refer to `LandFamily` profiles (realm LAND/AMPHIBIOUS) or to a
`FlyerProfile` (realm AIR). `food` and `warning` may be None when the source
library has no matching clip; the runtime then falls back to the idle clip.
`swim` supplies AMPHIBIOUS/ WATER water clips. `fly` supplies the AIR clip roles.
`width`/`height` are the final collision dimensions in blocks and must match the
`Species` entry; `height` here is the final body height used by the importer.
"""
COLLECTION = [
    # ---------------------------------------------------------------- aquatic
    dict(folder='Cnidaria', id='cnidaria', height=.6, width=.35, size=1,
         realm='WATER', family='AQUATIC', timid=True, predator=False, danger=1, weight=8,
         idle='Cnidaria-Idle', walk='Cnidaria-Idle', attack='Cnidaria-Shock',
         run='Cnidaria-Idle', food=None, warning='Cnidaria-Wide-Idle', sleep=False),
    dict(folder='Plesiosaur', id='plesiosaur', height=3.5, width=1.7, size=1,
         realm='WATER', family='AQUATIC', timid=False, predator=True, danger=2, weight=8,
         idle='Plesiosaur-Idle', walk='Plesiosaur-Move-Fwd', attack='Plesiosaur-Attack-Bite',
         run='Plesiosaur-Move-Fwd', food='Plesiosaur-Eat', warning=None, sleep=False),
    dict(folder='Megalodon', id='megalodon', height=3.0, width=1.6, size=1,
         realm='WATER', family='AQUATIC', timid=False, predator=True, danger=3, weight=7,
         idle='Megalodon-Idle', walk='Megalodon-Swim-Fwd', attack='Megalodon-Attack-Bite',
         run='Megalodon-Swim-Fwd', food='Megalodon-Eat', warning=None, sleep=False),
    dict(folder='Liopleurodon', id='liopleurodon', height=2.5, width=1.3, size=1,
         realm='WATER', family='AQUATIC', timid=False, predator=True, danger=3, weight=5,
         idle='Liopleurodon-Idle', walk='Liopleurodon-Swim-Fwd', attack='Liopleurodon-Attack-Chomp',
         run='Liopleurodon-Swim-Charge-Fwd', food='Liopleurodon-Torpid-Eat',
         warning='Liopleurodon-Spin', sleep=False),
    dict(folder='Mosasaurus', id='mosasaurus', height=5.0, width=2.6, size=1,
         realm='WATER', family='AQUATIC', timid=False, predator=True, danger=4, weight=3,
         idle='Mosasaurus-Idle', walk='Mosasaurus-Swim-Fwd', attack='Mosasaurus-Attack-Bite',
         run='Mosasaurus-Charge-Fwd', food='Mosasaurus-Eat', warning=None, sleep=False),
    dict(folder='Tusoteuthis', id='tusoteuthis', height=3.5, width=2.0, size=1,
         realm='WATER', family='AQUATIC', timid=False, predator=True, danger=4, weight=3,
         idle='Tusoteuthis-Idle', walk='Tusoteuthis-Swim-Fwd', attack='Tusoteuthis-Attack-Bite',
         run='Tusoteuthis-Swim-Fwd', food='Tusoteuthis-Eat',
         warning='Tusoteuthis-Attack-Crush-Loop', sleep=False),
    # --------------------------------------------------------- semi-aquatic
    dict(folder='Kaprosuchus', id='kaprosuchus', height=2.0, width=.95, size=1,
         realm='AMPHIBIOUS', family='AMPHIBIOUS', timid=False, predator=True, danger=2, weight=7,
         idle='Kaprosuchus-Idle', walk='Kaprosuchus-Move-Fwd', attack='Kaprosuchus-Attack-Bite',
         run='Kaprosuchus-Charge-Fwd', food='Kaprosuchus-Eat', warning='Kaprosuchus-Startle',
         swim=dict(idle='Kaprosuchus-Swim-Idle', walk='Kaprosuchus-Swim-Fwd', run='Kaprosuchus-Swim-Fwd')),
    dict(folder='Sarco', id='sarco', height=2.5, width=1.2, size=1,
         realm='AMPHIBIOUS', family='SWAMP_PACK', timid=False, predator=True, danger=2, weight=8,
         idle='Sarco-Ground-Idle', walk='Sarco-Ground-Move-Fwd', attack='Sarco-Ground-Attack-Bite',
         run='Sarco-Ground-Charge-Fwd', food='Sarco-Ground-Eat-Additive',
         warning='Sarco-Ground-Attack-Lunge',
         swim=dict(idle='Sarco-Swim-Idle', walk='Sarco-Swim-Fwd', run='Sarco-Swim-Charge-Fwd')),
    dict(folder='Deinosuchus', id='deinosuchus', height=3.5, width=1.8, size=1,
         realm='AMPHIBIOUS', family='AMPHIBIOUS', timid=False, predator=True, danger=3, weight=4,
         idle='Deinosuchus_Idle', walk='Deinosuchus_Move_FWD', attack='Deinosuchus_Attack_Bite',
         run='Deinosuchus_Charge_FWD', food='Deinosuchus_Eat', warning='Deinosuchus_Attack_Hiss',
         swim=dict(idle='Deinosuchus_Swim_Idle', walk='Deinosuchus_Swim_Move_FWD', run='Deinosuchus_Swim_Charge_FWD')),
    dict(folder='Titanoboa', id='titanoboa', height=1.2, width=.8, size=1,
         realm='AMPHIBIOUS', family='AMPHIBIOUS', timid=False, predator=True, danger=3, weight=6,
         idle='BoaFrill-Idle', walk='BoaFrill-Move-Fwd', attack='BoaFrill-Attack-Lunge',
         run='BoaFrill-Charge-Fwd', food='BoaFrill-Eat-Additive', warning='BoaFrill-Startled'),
    # ------------------------------------------------------------------ cold
    dict(folder='Megalocerus', id='megalocerus', height=2.2, width=1.0, size=1,
         realm='LAND', family='COLD_GRAZER', timid=True, predator=False, danger=1, weight=10,
         idle='Stag-Idle', walk='Stag-Move-Fwd', attack='Stag-Attack-Gore',
         run='Stag-Charge-Fwd', food='Stag-Graze', warning='Stag-Startled'),
    dict(folder='Unicorn', id='unicorn', height=2.0, width=.95, size=1,
         realm='LAND', family='RARE_GRAZER', timid=True, predator=False, danger=1, weight=3,
         idle='Equus-Idle2', walk='Equus-Move-Fwd', attack='Equus-Attack-Buck',
         run='Equus-Charge-Fwd', food='Equus-Eat', warning='Equus-Roar'),
    dict(folder='Mammoth', id='mammoth', height=4.0, width=1.9, size=1,
         realm='LAND', family='COLD_BROWSER', timid=False, predator=False, danger=2, weight=7,
         idle='Mammoth-Idle', walk='Mammoth-Move-Fwd', attack='Mammoth-Attack-Tusk-Jab',
         run='Mammoth-Charge-Fwd', food='Mammoth-Graze', warning='Mammoth_new_Call'),
    dict(folder='Direwolf', id='direwolf', height=1.6, width=.8, size=1,
         realm='LAND', family='COLD_PREDATOR', timid=False, predator=True, danger=2, weight=9,
         idle='Direwolf-Idle', walk='Direwolf-Move-Fwd', attack='Direwolf-Attack-Bite',
         run='Direwolf-Charge-Fwd', food='Direwolf-Eat', warning='Direwolf-Howl'),
    dict(folder='Sabertooth', id='sabertooth', height=1.6, width=.8, size=1,
         realm='LAND', family='COLD_STALKER', timid=False, predator=True, danger=3, weight=6,
         idle='Saber-Idle', walk='Saber-Move-Fwd', attack='Saber-Attack-Bite',
         run='Saber-Charge-Fwd', food='Saber-Eat', warning='Saber-Startled'),
    dict(folder='Megapithecus', id='megapithecus', height=3.5, width=1.7, size=1,
         realm='LAND', family='GUARDIAN', timid=False, predator=True, danger=5, weight=1,
         idle='Gorilla-Idle', walk='Gorilla-Move-Fwd', attack='Gorilla-Attack-Pound',
         run='Gorilla-Charge-Fwd', food=None, warning='Gorilla_Chest_Pounding'),
    # ------------------------------------------------- land, warm collection
    dict(folder='Paraceratherium', id='paraceratherium', height=4.5, width=2.0, size=1,
         realm='LAND', family='BIG_HERBIVORE', timid=False, predator=False, danger=3, weight=6,
         idle='Paraceratherium-Idle', walk='Paraceratherium-Move-Fwd',
         attack='Paraceratherium-Attack-Footstomp', run='Paraceratherium-Charge-Fwd',
         food='Paraceratherium-Eat', warning='Paraceratherium-Startled'),
    dict(folder='Terrorbird', id='terrorbird', height=2.0, width=.9, size=1,
         realm='LAND', family='SMALL_CARNIVORE', timid=False, predator=True, danger=2, weight=8,
         idle='TerrorBird-Idle', walk='TerrorBird-Move-Fwd', attack='TerrorBird-Attack-Bite',
         run='TerrorBird-Charge-Fwd', food='TerrorBird-Eat', warning='TerrorBird-Startled'),
    dict(folder='Ravager', id='ravager', height=1.8, width=.9, size=1,
         realm='LAND', family='SMALL_CARNIVORE', timid=False, predator=True, danger=3, weight=5,
         idle='CaveWolf-Idle', walk='CaveWolf-Walk-Fwd', attack='CaveWolf-Attack-Bite',
         run='CaveWolf-Charge-Fwd', food='CaveWolf-Eat', warning='CaveWolf-Howl'),
    # ---------------------------------------------------------------- flying
    dict(folder='Archaeopteryx', id='archaeopteryx', height=.5, width=.4, size=1,
         realm='AIR', family='GLIDER', timid=True, predator=False, danger=1, weight=8,
         idle='Archaeopteryx-Idle', walk='Archaeopteryx-Move-Fwd', attack='Archaeopteryx-Attack-Bite',
         run='Archaeopteryx-Charge-Fwd', food='Archaeopteryx-Eat', warning='Archaeopteryx-Startle',
         sleep=False, perch='Archaeopteryx-Perched',
         fly=dict(move='Archaeopteryx-Fly', hover='Archaeopteryx-Glide',
                  land='Archaeopteryx-StartPerch', takeoff='Archaeopteryx-Fly-Startle',
                  attack='Archaeopteryx-Attack-Bite')),
    dict(folder='Quetzal', id='quetzal', height=5.0, width=2.4, size=1,
         realm='AIR', family='GIANT_FLYER', timid=False, predator=False, danger=4, weight=2,
         idle='Quetzalcoatlus-Ground-Idle', walk='Quetzalcoatlus-Ground-Platform-Move-Fwd',
         attack='Quetzalcoatlus-Fly-Attack-Bite', run='Quetzalcoatlus-Ground-Platform-Move-Fwd',
         food='Quetzalcoatlus-Ground-Eat', warning='Quetzalcoatlus-Ground-Startled',
         sleep=False,
         fly=dict(move='Quetzalcoatlus-Fly-Fwd', hover='Quetzalcoatlus-Fly-Flap-Fwd',
                  land='Quetzalcoatlus-Land-Platform', takeoff='Quetzalcoatlus-Take-Off-Platform',
                  attack='Quetzalcoatlus-Fly-Attack-Bite')),
    dict(folder='Dragon', id='dragon', height=4.5, width=2.2, size=1,
         realm='AIR', family='APEX_FLYER', timid=False, predator=True, danger=5, weight=1,
         idle='Dragon-Ground-Idle', walk='Dragon-Ground-Move-Fwd', attack='Dragon-Ground-Attack-Bite',
         run='Dragon-Ground-Charge-Fwd', food=None, warning='Dragon-Ground-Attack-Fire',
         sleep=False,
         fly=dict(move='Dragon-Fly-Fwd', hover='Dragon-Fly-Idle', land='Dragon-Land',
                  takeoff='Dragon-Take-Off', swoopOut='Dragon-Fly-Attack-Swoop-Out',
                  attack='Dragon-Fly-Attack-Fire')),
]

ROLES = ('idle', 'walk', 'attack', 'run', 'food', 'warning')
SWIM_ROLES = ('idle', 'walk', 'run')
FLY_ROLES = ('move', 'hover', 'land', 'takeoff', 'swoopLoop', 'swoopOut', 'attack')


def import_clips(entry):
    """Ordered clip names the importer must copy. Index 0 is idle, 2 is the attack pose."""
    names = [entry[k] for k in ROLES if entry.get(k)]
    names += [entry['swim'][k] for k in SWIM_ROLES if (entry.get('swim') or {}).get(k)]
    names += [entry['fly'][k] for k in FLY_ROLES if (entry.get('fly') or {}).get(k)]
    if entry.get('perch'):
        names.append(entry['perch'])
    if entry.get('sleep', True):
        names.append('Ark-Sleep')
    ordered = []
    for name in names:
        if name not in ordered:
            ordered.append(name)
    return ordered
