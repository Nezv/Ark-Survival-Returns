"""Source-only creature batch. No runtime entity, behavior or spawn registration."""

MODELS = {
    'Direwolf': dict(asset='Direwolf_New', directory='PrimalEarth/Dinos/Direwolf', mesh='Direwolf_new/Direwolf_New', skeleton='Direwolf_Skeleton'),
    'Megalocerus': dict(asset='Stag'),
    'Megapithecus': dict(asset='Gorilla', extras=['Gorilla_fur']),
    'Mammoth': dict(asset='SK_Mammoth_new', directory='PrimalEarth/Dinos/Mammoth', mesh='Mammoth_new/SK_Mammoth_new', skeleton='Mammoth_Skeleton'),
    'Unicorn': dict(asset='Equus', extras=['SM_Unicorn_Horn_01']),
    'Sabertooth': dict(asset='Saber'),
    'Quetzal': dict(asset='Quetzalcoatlus'),
    'Archaeopteryx': dict(asset='Archaeopteryx'),
    'Paraceratherium': dict(asset='Paraceratherium'),
    'Terrorbird': dict(asset='TerrorBird'),
    'Ravager': dict(asset='CaveWolf', directory='Aberration/Dinos/CaveWolf'),
    'Tusoteuthis': dict(asset='Tusoteuthis'),
    'Cnidaria': dict(asset='Cnidaria'),
    'Mosasaurus': dict(asset='Mosasaurus'),
    'Megalodon': dict(asset='Megalodon'),
    'Plesiosaur': dict(asset='Plesiosaur'),
    'Liopleurodon': dict(asset='Liopleurodon'),
    'Kaprosuchus': dict(asset='Kaprosuchus'),
    'Deinosuchus': dict(asset='Deinosuchus_TLC_Rig', animation_dir='Animation'),
    'Sarco': dict(asset='Sarco-New', directory='PrimalEarth/Dinos/Sarco', mesh='Sarco_New/Sarco-New', skeleton='Sarco_Skeleton'),
    'Titanoboa': dict(asset='BoaFrill'),
    'Dragon': dict(asset='Dragon', directory='PrimalEarth/Dinos/Dragon'),
}

NATIVE_ASSETS = {m['asset'] for m in MODELS.values()}

# Body, highlights, markings, underside, hard parts, dark details, eyes, mouth.
PALETTES = {
    'Direwolf': ['#879397','#c9ceca','#4b5864','#e0ddd0','#efe5ce','#222d34','#e9bf5b','#906269'],
    'Megalocerus': ['#796c55','#ae9572','#504e40','#cbb58f','#ead6af','#262e29','#d9a847','#9b6256'],
    'Megapithecus': ['#bdc5c1','#e4e6da','#6f7d83','#8c9693','#e7dfc9','#283741','#f0ae62','#ac686b'],
    'Mammoth': ['#67554a','#967b60','#443f39','#b9a282','#eedfba','#242925','#d0a13e','#a16b65'],
    'Unicorn': ['#d0d5d1','#edf0e4','#99afb4','#e3dccc','#ead6a0','#4a626d','#62bad0','#bc8c90'],
    'Sabertooth': ['#a2987d','#d0c5a6','#5a625b','#e0d5b5','#f0e3c5','#283630','#dfb34c','#a16b69'],
    'Quetzal': ['#815e56','#b88968','#414d4e','#ceb49b','#e3cfa1','#26343b','#e0ad49','#a66569'],
    'Archaeopteryx': ['#9b6c4c','#cf9861','#4d5a58','#d0b17b','#e5d4a8','#28373a','#dbb847','#b86f59'],
    'Paraceratherium': ['#868478','#b3b09c','#555e59','#cebfaa','#e8d8b5','#2d3733','#d4a44c','#a7716a'],
    'Terrorbird': ['#785a4c','#b18b60','#435353','#c4aa87','#dfc78f','#273333','#e0ac39','#ac6057'],
    'Ravager': ['#6a686e','#9e9995','#444957','#b09c8d','#e2d0ad','#222933','#d7b852','#a3636c'],
    'Tusoteuthis': ['#844c5e','#b77983','#4d3d57','#d49c99','#eed0b2','#292c40','#78c8c2','#b85e6b'],
    'Cnidaria': ['#748dc0','#b4d8e7','#52679a','#93c3cf','#d0edf1','#354767','#c8c2f2','#a59de0'],
    'Mosasaurus': ['#4f7278','#85a0a3','#304f59','#b1c0b8','#e3dfc0','#1e3039','#d5bc55','#9d6b73'],
    'Megalodon': ['#647887','#98aab0','#354f62','#c8d0c9','#ece4ca','#202e3a','#deb45e','#a56e73'],
    'Plesiosaur': ['#657b73','#97ab94','#3d5b59','#c7c4a5','#e1dabb','#263c3d','#d5bb57','#a66f72'],
    'Liopleurodon': ['#536f76','#8fa6a1','#334853','#c0c4ae','#e6ddbc','#1c2d39','#ddb952','#a1666e'],
    'Kaprosuchus': ['#60715a','#96a176','#354d45','#b4ad85','#dcd3af','#24362f','#d7b74a','#ac6b61'],
    'Deinosuchus': ['#5e6950','#939271','#394638','#b3a17b','#dfd0a6','#263027','#d1ae45','#9b625a'],
    'Sarco': ['#586d59','#92a07a','#324c42','#b4ae84','#dfd4ac','#1e3029','#dabc47','#a16959'],
    'Titanoboa': ['#677a53','#a0ad76','#3f5440','#c6bc85','#e0d2a6','#253329','#d6b23d','#ad6d58'],
    'Dragon': ['#6f3f32','#a66a42','#3f5541','#c5a36a','#e1d1a1','#252d28','#e0a63e','#a94d42'],
}
