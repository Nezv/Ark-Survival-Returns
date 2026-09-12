"""Asset/controller contracts for the September 12 creature expansion.

Family names refer to existing gameplay profiles, not anatomical size classes.
Allosaurus uses the existing pack-predator profile; Pegomastax uses timid foraging.
"""
EXPANSION = [
    dict(folder='Spinosaurus',id='spinosaurus',height=4.5,size=3,family='BIG_CARNIVORE',timid=False,
         idle='Spino-Idle',walk='Spino-Move-Fwd',attack='Spino-Attack-Bite',run='Spino-Charge-Fwd',food='Spino-Eat',warning='Spino-Roar'),
    dict(folder='Parasaur',id='parasaur',height=2.0,size=2,family='SMALL_HERBIVORE',timid=True,
         idle='Para-Idle',walk='Para-Move-Fwd',attack='Para-Attack-Bite',run='Para-Charge-Fwd',food='Para-Graze',warning='Para-Roar-Alert'),
    dict(folder='Ceratosaurus',id='ceratosaurus',height=2.8,size=2,family='BIG_CARNIVORE',timid=False,
         idle='Ceratosaurus_Idle',walk='Ceratosaurus_MoveFWD',attack='Cerato_Attack_Bite1',run='Ceratosaurus_ChargeFWD_NOBOOST',food='Ceratosaurus_Eat',warning='Ceratosaurus_Roar1'),
    dict(folder='Dilophosaur',id='dilophosaur',height=.95,size=2,family='SMALL_CARNIVORE',timid=False,
         idle='Dilo-Idle',walk='Dilo-Move-Fwd',attack='Dilo-Attack-Bite',run='Dilo-Charge-Fwd',food='Dilo-Eat',warning='Dilo-Startled'),
    dict(folder='Acrochantosaur',id='acrocanthosaurus',height=4.8,size=3,family='BIG_CARNIVORE',timid=False,
         idle='Acro_Idle',walk='Acro_Move_Walk_FWD',attack='Acro_Attack_Bite',run='Acro_Move_Charge_FWD',food='Acro_Eat',warning='Acro_Attack_Roar'),
    dict(folder='Allosaurus',id='allosaurus',height=3.0,size=2,family='SMALL_CARNIVORE',timid=False,
         idle='Allosaurus-Idle',walk='Allosaurus-Move-Fwd',attack='Allosaurus-Attack-Bite',run='Allosaurus-Charge-Fwd',food='Allosaurus-Eat-Additive',warning='Allosaurus-Roar_Anim'),
    dict(folder='Ankylosaurus',id='ankylosaurus',height=2.0,size=2,family='BIG_HERBIVORE',timid=False,
         idle='Ankylo-Idle',walk='Ankylo-Move-Fwd',attack='Ankylo-Attack-Tail-Sweep',run='Ankylo-Charge-Fwd',food='Ankylo-Graze',warning='Ankylo-Startled'),
    dict(folder='Carnotaurus',id='carnotaurus',height=3.0,size=2,family='BIG_CARNIVORE',timid=False,
         idle='Carno-Idle',walk='Carno-Move-Fwd',attack='Carno-Attack-Bite',run='Carno-Charge-Fwd',food='Carno-Eat',warning='Carno-Startled'),
    dict(folder='Pegomastax',id='pegomastax',height=.65,size=2,family='SMALL_HERBIVORE',timid=True,
         idle='Pegomastax-Idle',walk='Pegomastax-Move-Fwd',attack='Pegomastax-Attack-Bite',run='Pegomastax-Biped-Charge-Fwd',food='Pegomastax-Eat',warning='Pegomastax-Roar'),
    dict(folder='Lystrosaurus',id='lystrosaurus',height=.5,size=2,family='SMALL_HERBIVORE',timid=True,
         idle='Lystrosaurus-Idle',walk='Lystrosaurus-Move-Fwd',attack='Lystrosaurus-Attack-Bite',run='Lystrosaurus-Charge-Fwd',food='Lystrosaurus-Eat',warning='Lystrosaurus-Startled'),
]
