# Knight
    I've designed it as a "Raptor-Knight" set: late-medieval plate where the dinosaur motifs are built into the armour's shapes rather than painted on. It stays in blocky Minecraft style but uses 62 cubes instead of vanilla's handful.

    - **Helmet:** morion-style helm with a houndskull snout visor that reads as a raptor muzzle (eye slit, breathing holes). It has three stegosaur plates along the crest, a nape ridge, cheek guards and a sallet-tail neck guard.
    - **Chestplate:** keeled breastplate with three raptor claw gashes, a mail-sided torso, gorget, plackart, leather fauld with buckle and dorsal spine plates. Each layered pauldron has a hooked sickle claw, and there are strapped cuffs and elbow couters.
    - **Leggings:** mail chausses with a leather belt, two-lame tassets, hip plates, and knee cops with a small horn.
    - **Boots:** greaves with a flared cuff and ankle strap, and sabatons with three claw toes and a heel dewclaw spur.

    **Palette:** there are exactly five colours: outline, dark steel, steel, polished steel and oiled leather. Every texel in the worn texture and the icons comes from that set, and the script checks this. If you want it closer to your Create look, change index 4 from leather to brass.

    ```python
    #!/usr/bin/env python3
    """
    Raptor-Knight steel armor set: medieval plate with dinosaur motifs.

    Writes under ./out/:
    assets/<MODID>/geo/item/armor/steel_armor.geo.json              GeckoLib/Bedrock model (opens in Blockbench)
    assets/<MODID>/animations/item/armor/steel_armor.animation.json empty stub GeckoLib expects
    assets/<MODID>/textures/item/armor/steel_armor.png              worn texture (box UV, auto-packed)
    assets/<MODID>/textures/item/steel_{helmet,chestplate,leggings,boots}.png  16x16 icons
    assets/<MODID>/models/item/steel_*.json                         item/generated models
    preview/steel_armor_preview.png                                 3/4 front + back render + icons
    Requires Pillow:  pip install pillow
    """
    import json
    import math
    import random
    from pathlib import Path

    from PIL import Image, ImageDraw

    MODID = "dinosurvival"  # <- your mod id
    NAME = "steel_armor"
    OUT = Path("out")
    ASSETS = OUT / "assets" / MODID

    # ------------------------------------------------------------------ palette (exactly 5)
    PAL = [
        (28, 30, 38),     # 0 outline, gashes, deep shadow
        (72, 80, 94),     # 1 dark steel
        (122, 131, 144),  # 2 steel
        (188, 196, 204),  # 3 polished edge, claws, rivets
        (112, 74, 44),    # 4 oiled leather (swap for brass if you want Create vibes)
    ]

    # ------------------------------------------------------------------ geometry
    # Bedrock/GeckoLib units: 16 = 1 block, y up, front = -z, wearer's RIGHT = +x
    # (GeckoLib mirrors X on load, same convention as its armor template).
    def C(name, origin, size, style="steel", inflate=0.0):
        return {"name": name, "origin": list(origin), "size": list(size),
                "style": style, "inflate": inflate}

    TORSO = {"north": "steel", "south": "steel", "up": "steel", "down": "steel",
            "east": "mail", "west": "mail"}

    HEAD = [
        C("helm",        (-4, 24, -4),     (8, 8, 8), inflate=1.0),
        C("visor",       (-4.5, 24, -6),   (9, 6, 1)),          # eye slit painted on
        C("snout",       (-3, 24, -8),     (6, 3, 2)),          # houndskull muzzle
        C("snout_tip",   (-1, 24.5, -9),   (2, 2, 1), "claw"),
        C("brow",        (-5.5, 30, -6),   (11, 1, 1)),
        C("crest_front", (-0.5, 33, -3.5), (1, 2, 2), "claw"),  # stegosaur plates
        C("crest_mid",   (-0.5, 33, -1),   (1, 3, 2), "claw"),
        C("crest_back",  (-0.5, 33, 1.5),  (1, 2, 2), "claw"),
        C("nape_ridge",  (-0.5, 28, 5),    (1, 4, 1), "claw"),
        C("cheek_r",     (5, 23, -4),      (1, 4, 6)),
        C("cheek_l",     (-6, 23, -4),     (1, 4, 6)),
        C("neck_guard",  (-4.5, 22, 5),    (9, 3, 1), "dark"),
    ]
    BODY = [
        C("torso",       (-4, 12, -2),     (8, 12, 4), TORSO, 1.01),
        C("breastplate", (-4.5, 17, -4),   (9, 7, 1)),          # claw gashes painted on
        C("keel",        (-0.5, 16, -5),   (1, 7, 1), "claw"),
        C("plackart",    (-3.5, 13, -4),   (7, 3, 1), "dark"),
        C("gorget",      (-4.5, 24, -3.5), (9, 2, 7)),
        C("spine_upper", (-0.5, 19, 3),    (1, 4, 3), "claw"),
        C("spine_lower", (-0.5, 14, 3),    (1, 3, 2), "claw"),
        C("fauld",       (-5.5, 11, -4.5), (11, 2, 8), "leather"),
    ]
    R_ARM = [
        C("arm",           (4, 12, -2),     (4, 12, 4), "mail", 1.0),
        C("pauldron",      (2, 23, -3.5),   (8, 3, 7)),
        C("pauldron_lame", (9, 19, -3),     (2, 4, 6), "dark"),
        C("claw_base",     (7, 26, -1),     (2, 2, 2), "claw"),  # sickle claw
        C("claw_mid",      (7.5, 28, -0.5), (1, 2, 1), "claw"),
        C("claw_tip",      (8, 30, -0.5),   (1, 1, 1), "claw"),
        C("cuff",          (2.5, 12, -3.5), (7, 3, 7)),
        C("couter",        (4.5, 16, 3),    (3, 3, 1)),
    ]
    R_LEG = [
        C("leg",         (0, 0, -2),      (4, 12, 4), "mail", 0.5),
        C("tasset",      (-0.5, 9, -3.5), (5, 4, 1)),
        C("tasset_lame", (0, 6, -3.5),    (4, 3, 1), "dark"),
        C("hip_plate",   (4.5, 7, -2),    (1, 5, 4)),
        C("poleyn",      (0.5, 3, -4),    (3, 3, 2)),
        C("knee_spike",  (1.5, 4, -5),    (1, 1, 1), "claw"),
    ]
    R_BOOT = [
        C("boot",      (0, 0, -2),      (4, 5, 4), inflate=1.0),
        C("toe_cap",   (-0.5, -1, -5),  (5, 2, 2), "dark"),
        C("toe_a",     (-0.5, -1, -6),  (1, 1, 1), "claw"),     # three-toed sabaton
        C("toe_b",     (1.5, -1, -6),   (1, 1, 1), "claw"),
        C("toe_c",     (3.5, -1, -6),   (1, 1, 1), "claw"),
        C("heel_spur", (1.5, 0, 3),     (1, 1, 2), "claw"),
        C("boot_cuff", (-1.5, 5, -3.5), (7, 2, 7)),
    ]

    def mirror(cubes):
        return [dict(c, origin=[-(c["origin"][0] + c["size"][0])] + c["origin"][1:])
                for c in cubes]

    PARENTS = {"bipedHead": [0, 24, 0], "bipedBody": [0, 24, 0],
            "bipedRightArm": [5, 22, 0], "bipedLeftArm": [-5, 22, 0],
            "bipedRightLeg": [2, 12, 0], "bipedLeftLeg": [-2, 12, 0]}
    BONES = [  # GeckoLib 4 armor bone names
        ("armorHead", "bipedHead", HEAD),
        ("armorBody", "bipedBody", BODY),
        ("armorRightArm", "bipedRightArm", R_ARM),
        ("armorLeftArm", "bipedLeftArm", mirror(R_ARM)),
        ("armorRightLeg", "bipedRightLeg", R_LEG),
        ("armorLeftLeg", "bipedLeftLeg", mirror(R_LEG)),
        ("armorRightBoot", "bipedRightLeg", R_BOOT),
        ("armorLeftBoot", "bipedLeftLeg", mirror(R_BOOT)),
    ]
    ALL = []
    for _bone, _, _cubes in BONES:
        for _c in _cubes:
            _c["bone"] = _bone
            ALL.append(_c)

    # ------------------------------------------------------------------ box UV
    def dims(c):
        return tuple(int(math.ceil(s)) for s in c["size"])

    def face_rects(c):
        w, h, d = dims(c)
        u, v = c["uv"]
        return {"up": (u + d, v, w, d), "down": (u + d + w, v, w, d),
                "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
                "west": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h)}

    def pack(cubes, width=128):
        def fp(c):
            w, h, d = dims(c)
            return 2 * (w + d), d + h
        x = y = row_h = 0
        for c in sorted(cubes, key=lambda c: (fp(c)[1], fp(c)[0]), reverse=True):
            fw, fh = fp(c)
            if x + fw > width:
                x, y, row_h = 0, y + row_h, 0
            c["uv"] = [x, y]
            x += fw
            row_h = max(row_h, fh)
        tex_h = 16
        while tex_h < y + row_h:
            tex_h *= 2
        return width, tex_h

    # ------------------------------------------------------------------ texture painting
    SIDES = ("north", "south", "east", "west")
    DOWN = {"steel": 1, "dark": 0, "mail": 0, "leather": 4, "claw": 1}

    def bevel(w, h, base, hi, lo):
        g = [[base] * w for _ in range(h)]
        if h >= 2:
            g[0] = [hi] * w
        if h >= 3:
            g[-1] = [lo] * w
            if w >= 3:
                for row in g[1:-1]:
                    row[0], row[-1] = hi, lo
        return g

    def dents(g, rng):
        h, w = len(g), len(g[0])
        if w < 4 or h < 4:
            return
        for _ in range(max(1, (w - 2) * (h - 2) // 16)):
            x, y = rng.randint(2, w - 2), rng.randint(2, h - 2)
            g[y][x], g[y - 1][x - 1] = 1, 3

    def rivets(g):
        h, w = len(g), len(g[0])
        if w < 6 or h < 6:
            return
        for x, y in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
            g[y][x] = 3
            if y + 1 < h - 1:
                g[y + 1][x] = 1

    def paint(style, face, w, h, rng):
        if face == "down":
            return [[DOWN[style]] * w for _ in range(h)]
        if style == "steel":
            g = bevel(w, h, 2, 3, 1)
            dents(g, rng)
            if face != "up":
                rivets(g)
            return g
        if style == "dark":
            return bevel(w, h, 1, 2, 0)
        if style == "mail":
            if face == "up":
                return [[1] * w for _ in range(h)]
            return [[2 if (x + y) % 2 == 0 else 1 for x in range(w)] for y in range(h)]
        if style == "leather":
            g = [[4] * w for _ in range(h)]
            if h >= 3 and face != "up":
                g[-1] = [0] * w
            return g
        if style == "claw":
            g = [[3] * w for _ in range(h)]
            if face != "up":
                if w >= 2:
                    for row in g:
                        row[-1] = 2
                if h >= 2:
                    g[-1] = [1 if h >= 3 else 2] * w
            return g
        raise ValueError(style)

    def decorate(name, face, g):
        h, w = len(g), len(g[0])
        if name == "visor" and face in SIDES:                 # eye slit + nasal bar
            for x in range(w):
                if face != "north" or (0 < x < w - 1 and x != w // 2):
                    g[2][x] = 0
        elif name == "snout" and face == "north":             # breathing holes
            for x in range(1, w - 1):
                if min(x, w - 1 - x) % 2 == 1:
                    g[1][x] = 0
        elif name == "breastplate" and face == "north":       # three raptor gashes
            for sx, sy, n in ((6, 1, 2), (8, 1, 4), (8, 3, 3)):
                for t in range(n):
                    g[sy + t][sx - t] = 0
        elif name == "fauld" and face == "north":             # buckle
            c = w // 2
            g[0][c - 1:c + 2] = [3, 3, 3]
            g[1][c - 1:c + 2] = [3, 0, 3]
        elif name == "cuff" and face in SIDES:                # wrist strap
            g[1] = [4] * w
            if face == "north":
                g[1][w // 2] = 3
        elif name == "boot" and face in SIDES:                # ankle strap
            g[1] = [4] * w
        elif name == "leg" and face in SIDES:                 # belt
            g[0] = [4] * w
            g[1] = [4] * w

    def build_atlas(tw, th):
        img = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        for c in ALL:
            for face, (fx, fy, fw, fh) in face_rects(c).items():
                style = c["style"][face] if isinstance(c["style"], dict) else c["style"]
                rng = random.Random(f'{c["bone"]}/{c["name"]}/{face}')
                g = paint(style, face, fw, fh, rng)
                decorate(c["name"], face, g)
                for y in range(fh):
                    for x in range(fw):
                        img.putpixel((fx + x, fy + y), PAL[g[y][x]] + (255,))
        return img

    # ------------------------------------------------------------------ item sprites
    SPRITES = {
        "helmet": [
            "................",
            "........0.......",
            "....0..030..0...",
            "...0300320.020..",
            "...03322222210..",
            "..0332222222210.",
            ".032222222222110",
            ".000000000222110",
            "0333333322222110",
            ".032020222222110",
            "..01111222222210",
            "...0000111023310",
            ".......000022210",
            "..........011110",
            "...........00000",
            "................",
        ],
        "chestplate": [
            "..0..........0..",
            ".030........030.",
            "033200....002210",
            "0322030000102210",
            "0111033222101110",
            "0322032020102210",
            ".00003020200000.",
            "....00202010....",
            "....03220210....",
            "....03222210....",
            "....01111110....",
            "....03222210....",
            "....04433440....",
            "...0322222210...",
            "...0000000000...",
            "................",
        ],
        "leggings": [
            "................",
            "...0000000000...",
            "...0444334440...",
            "..033220032210..",
            "..032220032210..",
            "..011110011110..",
            "...0321003210...",
            "...0212002120...",
            "...0121001210...",
            "...0332003320...",
            "...0232002320...",
            "...0111001110...",
            "...0212002120...",
            "...0321003210...",
            "...0000000000...",
            "................",
        ],
        "boots": [
            "................",
            "................",
            "................",
            "................",
            "..........00000.",
            "..00000...033210",
            "..033210...0440.",
            "...0440....0320.",
            "...0320...03220.",
            "..03220..0322210",
            ".032221003222210",
            "03222210.0000000",
            ".0000000........",
            "................",
            "................",
            "................",
        ],
    }

    def sprite(rows):
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), rows
        im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != ".":
                    im.putpixel((x, y), PAL[int(ch)] + (255,))
        return im

    # ------------------------------------------------------------------ preview render
    NORMAL = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0),
            "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
    SHADE = {"up": 1.0, "north": 0.85, "south": 0.85, "east": 0.7, "west": 0.7, "down": 0.5}
    BG = (58, 66, 52)

    def face_point(face, fs, ft, b):
        x0, y0, z0, x1, y1, z1 = b
        y = y1 - ft * (y1 - y0)
        if face == "north":
            return (x1 - fs * (x1 - x0), y, z0)
        if face == "south":
            return (x0 + fs * (x1 - x0), y, z1)
        if face == "east":
            return (x1, y, z1 - fs * (z1 - z0))
        if face == "west":
            return (x0, y, z0 + fs * (z1 - z0))
        if face == "up":
            return (x1 - fs * (x1 - x0), y1, z1 - ft * (z1 - z0))
        return (x1 - fs * (x1 - x0), y0, z0 + ft * (z1 - z0))

    def render(atlas, yaw, pitch=22, px=12):
        cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
        cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

        def proj(p):
            a, b, c = -p[0], p[1], p[2]
            a, c = a * cy - c * sy, a * sy + c * cy
            b, c = b * cp + c * sp, -b * sp + c * cp
            return a * px, -b * px, c

        quads = []
        for cube in ALL:
            (ox, oy, oz), (w, h, d), k = cube["origin"], cube["size"], cube["inflate"]
            box = (ox - k, oy - k, oz - k, ox + w + k, oy + h + k, oz + d + k)
            for face, (fx, fy, fw, fh) in face_rects(cube).items():
                if proj(NORMAL[face])[2] >= 0:
                    continue
                s = SHADE[face]
                for j in range(fh):
                    for i in range(fw):
                        r, g, bl, a = atlas.getpixel((fx + i, fy + j))
                        if not a:
                            continue
                        pts = [proj(face_point(face, (i + di) / fw, (j + dj) / fh, box))
                            for di, dj in ((0, 0), (1, 0), (1, 1), (0, 1))]
                        quads.append((sum(p[2] for p in pts) / 4,
                                    [(p[0], p[1]) for p in pts],
                                    (int(r * s), int(g * s), int(bl * s))))
        xs = [x for _, pts, _ in quads for x, _ in pts]
        ys = [y for _, pts, _ in quads for _, y in pts]
        pad = 20
        img = Image.new("RGB", (int(max(xs) - min(xs)) + 2 * pad,
                                int(max(ys) - min(ys)) + 2 * pad), BG)
        draw = ImageDraw.Draw(img)
        dx, dy = pad - min(xs), pad - min(ys)
        for _, pts, col in sorted(quads, key=lambda q: q[0], reverse=True):
            poly = [(x + dx, y + dy) for x, y in pts]
            draw.polygon(poly, fill=col, outline=col)
        return img

    def preview(atlas, icons):
        views = [render(atlas, 35), render(atlas, 215)]
        nearest = getattr(Image, "Resampling", Image).NEAREST
        row = [ic.resize((128, 128), nearest) for ic in icons.values()]
        gap = 24
        top_h = max(v.height for v in views)
        sheet = Image.new("RGB", (max(sum(v.width for v in views) + gap, len(row) * (128 + gap)),
                                top_h + gap + 128 + gap), BG)
        x = 0
        for v in views:
            sheet.paste(v, (x, 0))
            x += v.width + gap
        x = gap // 2
        for ic in row:
            sheet.paste(ic, (x, top_h + gap), ic)
            x += 128 + gap
        return sheet

    # ------------------------------------------------------------------ output
    def cube_json(c):
        j = {"origin": c["origin"], "size": c["size"], "uv": c["uv"]}
        if c["inflate"]:
            j["inflate"] = c["inflate"]
        return j

    def geo_json(tw, th):
        bones = []
        for parent, pivot in PARENTS.items():
            bones.append({"name": parent, "pivot": pivot})
            for bone, par, cubes in BONES:
                if par == parent:
                    bones.append({"name": bone, "parent": parent, "pivot": pivot,
                                "cubes": [cube_json(c) for c in cubes]})
        return {"format_version": "1.12.0",
                "minecraft:geometry": [{
                    "description": {"identifier": f"geometry.{NAME}",
                                    "texture_width": tw, "texture_height": th,
                                    "visible_bounds_width": 3, "visible_bounds_height": 3.5,
                                    "visible_bounds_offset": [0, 1.25, 0]},
                    "bones": bones}]}

    def write(path, data):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=2))

    def save(img, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)

    def check_palette(images):
        allowed = {p + (255,) for p in PAL}
        for im in images:
            used = {col for _, col in im.getcolors(1 << 16) if col[3]}
            assert used <= allowed, f"off-palette colours: {used - allowed}"

    def main():
        tw, th = pack(ALL)
        atlas = build_atlas(tw, th)
        write(ASSETS / "geo/item/armor" / f"{NAME}.geo.json", geo_json(tw, th))
        write(ASSETS / "animations/item/armor" / f"{NAME}.animation.json",
            {"format_version": "1.8.0", "animations": {}})
        save(atlas, ASSETS / "textures/item/armor" / f"{NAME}.png")
        icons = {}
        for piece, rows in SPRITES.items():
            item = f"steel_{piece}"
            icons[piece] = sprite(rows)
            save(icons[piece], ASSETS / "textures/item" / f"{item}.png")
            write(ASSETS / "models/item" / f"{item}.json",
                {"parent": "minecraft:item/generated",
                "textures": {"layer0": f"{MODID}:item/{item}"}})
        check_palette([atlas, *icons.values()])
        save(preview(atlas, icons), OUT / "preview" / f"{NAME}_preview.png")
        print(f"done: {len(ALL)} cubes, texture {tw}x{th}, files in {OUT.resolve()}")

    if __name__ == "__main__":
        main()
    ```

    **Notes for you and your agent:**

    - **Which file is which:** I treated "3D asset (block)" as the worn model. The `.geo.json` is a GeckoLib armor model, and it opens directly in Blockbench. If you meant an actual placeable block, such as an armor display stand, tell me and I'll add one.
    - **Wiring it up:** all four items share one model and one texture. The agent points a `GeoArmorRenderer` at `DefaultedItemGeoModel(MODID, "armor/steel_armor")`, and GeckoLib shows only the bones for whichever slot is equipped.
    - **One thing to check:** I placed the wearer's right side at +x, which follows GeckoLib's armor template. Load the file next to Blockbench's GeckoLib armor template once. If an arm's armour swings around the wrong shoulder, swap the Right and Left bone names.
    - **Adjusting the design:** every cube is one line in the `HEAD`/`BODY`/`R_ARM`/`R_LEG`/`R_BOOT` lists. The left side is mirrored automatically, and the UV map and texture regenerate on every run.
    - **Not yet run:** I couldn't run the script here. If it throws an error, paste the traceback. `preview/steel_armor_preview.png` gives you a quick front and back look without opening Blockbench.

# Tech
    Here's a live, orbitable preview built from the same geometry and animation data as the script below.The three machines share one 1×2×1 chassis and differ only in the upper mechanism, so they read as a family and chain into a single line.

    **Shared chassis (lower block)**
    - An andesite casing with a wooden rim, the Create look.
    - A short belt with spinning rollers, a brass intake arch on the input side and a copper exit arch on the output side. The belts sit at the same height, so adjacent machines hand items off to each other.
    - A copper heat port on the back, and a brass heat gauge on the front whose needle shows the heat level.
    - There is no external drive. The heat level sets the internal speed: 0, 32, or 64 RPM, like Create's speed tiers.

    **The three machines**
    - **Mechanical press.** Four wooden posts hold a casing housing with a cogwheel on top that always spins while heated. The ram slams down 6 px, holds, then eases back up. A copper heat riser runs up the back.
    - **Milling machine.** A column at the back carries a brass spindle head with a copper heat-motor drum on top. The spindle idles slowly, spins 4× faster while working, and the quill plunges 2.5 px into the workpiece.
    - **Cutting machine.** A hinged chop-saw arm comes out of a back tower that has a horizontal motor drum. The guarded blade spins nonstop while heated, and the arm swings from 25° raised down to 0° to cut.

    **Side convention**
    - The model faces north, like a furnace. Input comes in on the east side, which is your left when you face the front. Output leaves on the west side. Heat comes in from the south, at the back.
    - The blockstate rotates all of this with the block's facing.

    ### Asset generator (Python + Pillow)

    ```python
    #!/usr/bin/env python3
    """
    Create-style heat-driven line: Mechanical Press -> Milling Machine -> Cutting Machine.
    Single source of truth for the 3D assets. Writes (under --out):
    assets/<MOD_ID>/models/block/<machine>/base.json    static body, 1x2x1 (y = 0..32 px)
    assets/<MOD_ID>/models/block/<machine>/<part>.json  moving partials (rest pose, block space)
    assets/<MOD_ID>/models/block/common/<part>.json     shared partials (rollers, gauge needle)
    assets/<MOD_ID>/models/block/machine_ghost.json     invisible upper block
    assets/<MOD_ID>/blockstates/<machine>.json          horizontal FACING variants
    assets/<MOD_ID>/textures/block/machines/*.png       16x16 placeholder textures
    anim/<machine>.anim.json                            animation spec for the block-entity renderer
    preview/*.png, preview/*.gif                        renders (front, back, line, animated)
    Conventions: front=north(z=0) input=east(x=16, LEFT when facing front) output=west(x=0)
    heat=south(z=16). Units: px (1/16 block), degrees, ticks. Right-hand-rule rotations
    (same as Minecraft element rotations / Blockbench).
    Requires Pillow:  pip install pillow      Usage: python create_machines.py [--out build]
    """
    import argparse, json, math, os
    from PIL import Image, ImageDraw

    MOD_ID = "yourmod"
    RPM = {"none": 0, "heated": 32, "superheated": 64}  # heat level -> internal drive speed

    PALETTE = {
        "casing": (148, 150, 144), "frame": (160, 118, 74), "alloy": (180, 186, 178),
        "brass": (222, 172, 64), "copper": (198, 110, 72), "iron": (78, 82, 90),
        "belt": (54, 50, 48), "blade": (216, 222, 228), "dial": (236, 228, 206),
        "needle": (180, 40, 32),
    }

    # Box: [x1, y1, z1, x2, y2, z2, material, optional ["axis", angle, ox, oy, oz]]
    COMMON = {
        "static": [
            [0, 0, 0, 16, 6, 16, "casing"],                  # chassis
            [1.5, 6, 2, 14.5, 9, 4, "frame"],                # belt rails
            [1.5, 6, 12, 14.5, 9, 14, "frame"],
            [1.5, 5, 4, 14.5, 8, 12, "belt"],                # belt, top at y=8
            [14.5, 6, 2, 16, 12.5, 4, "brass"],              # intake arch (east / input)
            [14.5, 6, 12, 16, 12.5, 14, "brass"],
            [14.5, 11, 4, 16, 12.5, 12, "brass"],
            [0, 6, 2, 1.5, 12.5, 4, "copper"],               # exit arch (west / output)
            [0, 6, 12, 1.5, 12.5, 14, "copper"],
            [0, 11, 4, 1.5, 12.5, 12, "copper"],
            [4, 1, 15.5, 12, 5, 16.25, "copper"],            # heat port (south / back)
            [5, 2, 16.25, 11, 4, 16.5, "iron"],
            [10, 0.5, -0.5, 15, 5.5, 0, "brass"],            # heat gauge (front)
            [10.5, 1, -0.75, 14.5, 5, -0.5, "dial"],
            [12.1, 2.6, -1.1, 12.9, 3.4, -0.9, "brass"],
        ],
        "parts": {
            "roller_in": {"boxes": [[13.25, 5.25, 4, 15.75, 7.75, 12, "alloy"],
                                    [13.25, 5.25, 4.1, 15.75, 7.75, 11.9, "alloy", ["z", 45, 14.5, 6.5, 8]]],
                        "mode": "rotate", "axis": "z", "pivot": [14.5, 6.5, 8],
                        "drive": "spin", "spin_idle": 1, "spin_proc": 1},
            "roller_out": {"boxes": [[0.25, 5.25, 4, 2.75, 7.75, 12, "alloy"],
                                    [0.25, 5.25, 4.1, 2.75, 7.75, 11.9, "alloy", ["z", 45, 1.5, 6.5, 8]]],
                        "mode": "rotate", "axis": "z", "pivot": [1.5, 6.5, 8],
                        "drive": "spin", "spin_idle": 1, "spin_proc": 1},
            "gauge_needle": {"boxes": [[12.25, 3, -1, 12.75, 4.75, -0.75, "needle"]],
                            "mode": "rotate", "axis": "z", "pivot": [12.5, 3, -0.875], "drive": "heat",
                            "heat_angles": {"none": -70, "heated": 10, "superheated": 70}, "jitter": 3},
        },
    }

    MACHINES = {
        "mechanical_press": {
            "static": [
                [0, 6, 0, 2, 22, 2, "frame"], [14, 6, 0, 16, 22, 2, "frame"],       # corner posts
                [0, 6, 14, 2, 22, 16, "frame"], [14, 6, 14, 16, 22, 16, "frame"],
                [0, 22, 0, 16, 30, 16, "casing"],                                   # press housing
                [5, 23.5, -0.5, 11, 28.5, 0, "brass"],                              # name plate
                [6.5, 6, 14, 9.5, 22, 16, "copper"],                                # heat riser
                [6, 12, 13.5, 10, 13.5, 16.25, "copper"],
            ],
            "parts": {
                "press_head": {"boxes": [[6.75, 20, 6.75, 9.25, 30, 9.25, "alloy"],  # ram
                                        [5, 19, 5, 11, 20, 11, "brass"],
                                        [3, 16, 3, 13, 19, 13, "iron"],
                                        [4, 15, 4, 12, 16, 12, "alloy"]],           # die face
                            "mode": "translate", "axis": "y", "pivot": [8, 0, 8],
                            "drive": "cycle", "cycle_ticks": 20,
                            "keys": [[0, 0, "in"], [0.3, -6, "hold"], [0.45, -6, "out"], [1, 0, "hold"]]},
                "drive_cog": {"boxes": [[5, 30, 5, 11, 31.5, 11, "frame"],
                                        [5, 30, 5, 11, 31.25, 11, "frame", ["y", 45, 8, 30, 8]],
                                        [2.75, 30.25, 7, 13.25, 31, 9, "frame"],
                                        [7, 30.25, 2.75, 9, 31, 13.25, "frame"],
                                        [2.75, 30.25, 7, 13.25, 31, 9, "frame", ["y", 45, 8, 30, 8]],
                                        [7, 30.25, 2.75, 9, 31, 13.25, "frame", ["y", 45, 8, 30, 8]],
                                        [7, 30, 7, 9, 32, 9, "alloy"]],
                            "mode": "rotate", "axis": "y", "pivot": [8, 30, 8],
                            "drive": "spin", "spin_idle": 1, "spin_proc": 1},
            },
        },
        "milling_machine": {
            "static": [
                [2, 6, 12.5, 14, 28, 16, "casing"],                                 # column
                [1.5, 28, 12, 14.5, 29.5, 16, "frame"],
                [5, 9, 12, 11, 20, 12.5, "iron"],                                   # slide ways
                [3.5, 20, 3, 12.5, 27, 12.5, "brass"],                              # spindle head
                [5, 27, 5, 11, 30.5, 11, "copper"],                                 # heat motor drum
                [5, 27, 5, 11, 30.25, 11, "copper", ["y", 45, 8, 27, 8]],
                [6.5, 30.5, 6.5, 9.5, 31.5, 9.5, "iron"],
                [10.5, 17, 4, 11.5, 20, 5, "copper"],                               # coolant nozzle
            ],
            "parts": {
                "quill": {"boxes": [[6.5, 18, 6.5, 9.5, 26, 9.5, "alloy"], [6, 17, 6, 10, 18, 10, "brass"]],
                        "mode": "translate", "axis": "y", "pivot": [8, 0, 8],
                        "drive": "cycle", "cycle_ticks": 40,
                        "keys": [[0, 0, "inout"], [0.2, -2.5, "hold"], [0.8, -2.5, "inout"], [1, 0, "hold"]]},
                "spindle": {"boxes": [[6.25, 15, 6.25, 9.75, 17, 9.75, "iron"],
                                    [6.25, 15.25, 6.25, 9.75, 17, 9.75, "iron", ["y", 45, 8, 16, 8]],
                                    [7.25, 12, 7.25, 8.75, 15, 8.75, "blade"],
                                    [6.5, 12.25, 7.75, 9.5, 14.5, 8.25, "blade", ["y", 22.5, 8, 13, 8]],
                                    [7.75, 12.25, 6.5, 8.25, 14.5, 9.5, "blade", ["y", 22.5, 8, 13, 8]]],
                            "parent": "quill", "mode": "rotate", "axis": "y", "pivot": [8, 0, 8],
                            "drive": "spin", "spin_idle": 1, "spin_proc": 4},
            },
        },
        "cutting_machine": {
            "static": [
                [3, 6, 12, 13, 26, 16, "casing"],                                   # saw tower
                [8, 17, 11.5, 11.5, 23, 12, "brass"],                               # arm slot plate
                [4, 26, 12.5, 12, 29, 15.5, "copper"],                              # heat motor drum
                [4.25, 26, 12.5, 11.75, 29, 15.5, "copper", ["x", 45, 8, 27.5, 14]],
                [3, 26.5, 13, 4, 28.5, 15, "brass"], [12, 26.5, 13, 13, 28.5, 15, "brass"],
            ],
            "parts": {
                "arm": {"boxes": [[9, 19, 3.69, 10.5, 21, 16, "iron", ["x", -45, 8, 20, 15]],
                                [6.75, 16, 3, 9.25, 17, 11, "brass"],               # blade guard
                                [8.75, 12, 3, 9.25, 16, 11, "brass"]],
                        "mode": "rotate", "axis": "x", "pivot": [8, 20, 15],
                        "drive": "cycle", "cycle_ticks": 40,
                        "keys": [[0, 25, "inout"], [0.45, 0, "hold"], [0.6, 0, "inout"], [1, 25, "hold"]]},
                "blade": {"boxes": [[7.5, 9, 4, 8.5, 15, 10, "blade"],
                                    [7.6, 9, 4, 8.4, 15, 10, "blade", ["x", 22.5, 8, 12, 7]],
                                    [7.7, 9, 4, 8.3, 15, 10, "blade", ["x", 45, 8, 12, 7]],
                                    [7.8, 9, 4, 8.2, 15, 10, "blade", ["x", -22.5, 8, 12, 7]],
                                    [7, 11, 6, 9, 13, 8, "alloy"],
                                    [7.1, 11, 6, 8.9, 13, 8, "alloy", ["x", 45, 8, 12, 7]]],
                        "parent": "arm", "mode": "rotate", "axis": "x", "pivot": [8, 12, 7],
                        "drive": "spin", "spin_idle": -3, "spin_proc": -3},
            },
        },
    }

    PREVIEW_ITEMS = {  # preview only: the workpiece under each tool (not exported)
        "mechanical_press": [[4.5, 8, 5, 11.5, 9, 11, "brass"]],
        "milling_machine": [[5, 8, 5.5, 11, 9.5, 10.5, "brass"]],
        "cutting_machine": [[3, 8, 7, 13, 9.5, 9, "brass"]],
    }
    LINE = [("mechanical_press", 16), ("milling_machine", 0), ("cutting_machine", -16)]  # left -> right

    # ---------------- math ----------------
    def add(a, b): return (a[0] + b[0], a[1] + b[1], a[2] + b[2])
    def sub(a, b): return (a[0] - b[0], a[1] - b[1], a[2] - b[2])
    def dot(a, b): return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    def cross(a, b): return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    def unit(a):
        n = math.sqrt(dot(a, a)); return (a[0] / n, a[1] / n, a[2] / n)

    def rot(p, axis, deg, o):
        c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
        x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
        if axis == "x": x, y, z = x, y * c - z * s, y * s + z * c
        elif axis == "y": x, y, z = x * c + z * s, y, -x * s + z * c
        else: x, y, z = x * c - y * s, x * s + y * c, z
        return (x + o[0], y + o[1], z + o[2])

    def apply(p, ops):
        for op in ops:
            p = add(p, op[1]) if op[0] == "t" else rot(p, op[1], op[2], op[3])
        return p

    # ---------------- animation ----------------
    EASE = {"linear": lambda s: s, "in": lambda s: s * s, "out": lambda s: 1 - (1 - s) ** 2,
            "inout": lambda s: s * s * (3 - 2 * s), "hold": lambda s: 0.0}

    def keyframe(keys, u):
        for (t0, v0, ease), (t1, v1, _) in zip(keys, keys[1:]):
            if u <= t1:
                s = (u - t0) / (t1 - t0) if t1 > t0 else 1.0
                return v0 + (v1 - v0) * EASE[ease](min(1.0, max(0.0, s)))
        return keys[-1][1]

    def part_value(p, t, heat, proc):
        rpm = RPM[heat]
        if p["drive"] == "spin":
            return rpm * 0.3 * (p["spin_proc"] if proc else p["spin_idle"]) * t
        if p["drive"] == "cycle":
            if not proc or rpm == 0: return p["keys"][0][1]
            period = p["cycle_ticks"] * 32 / rpm
            return keyframe(p["keys"], (t % period) / period)
        return p["heat_angles"][heat] + (p["jitter"] * math.sin(1.7 * t) if rpm else 0)

    def part_ops(parts, name, t, heat, proc):
        p = parts[name]; v = part_value(p, t, heat, proc)
        ops = ([("t", tuple(v if a == p["axis"] else 0 for a in "xyz"))] if p["mode"] == "translate"
            else [("r", p["axis"], v, p["pivot"])])
        return ops + (part_ops(parts, p["parent"], t, heat, proc) if "parent" in p else [])

    def scene(name, t=0.0, heat="heated", proc=True, dx=0, items=True):
        m = MACHINES[name]; parts = {**COMMON["parts"], **m["parts"]}
        shift = [("t", (dx, 0, 0))] if dx else []
        out = [(b, shift) for b in COMMON["static"] + m["static"] + (PREVIEW_ITEMS[name] if items else [])]
        for pname, p in parts.items():
            ops = part_ops(parts, pname, t, heat, proc) + shift
            out += [(b, ops) for b in p["boxes"]]
        return out

    # ---------------- preview renderer (painter's algorithm, Create-style rims) ----------------
    LIGHT = unit((-0.3, 1.0, -0.55))
    def shade(c, k): return tuple(max(0, min(255, int(v * k))) for v in c[:3])

    def faces(b):  # (corner, edge U, edge V) with U x V = outward normal
        x1, y1, z1, x2, y2, z2 = b[:6]; dx, dy, dz = x2 - x1, y2 - y1, z2 - z1
        return (((x2, y1, z2), (0, 0, -dz), (0, dy, 0)), ((x1, y1, z1), (0, 0, dz), (0, dy, 0)),
                ((x1, y2, z2), (dx, 0, 0), (0, 0, -dz)), ((x1, y1, z1), (dx, 0, 0), (0, 0, dz)),
                ((x1, y1, z2), (dx, 0, 0), (0, dy, 0)), ((x2, y1, z1), (-dx, 0, 0), (0, dy, 0)))

    def camera(yaw, pitch):
        a, b = math.radians(yaw), math.radians(pitch)
        d = (-math.cos(b) * math.sin(a), -math.sin(b), math.cos(b) * math.cos(a))
        r = unit(cross(d, (0, 1, 0)))
        return d, r, cross(r, d)

    def project(boxes, yaw, pitch, cell):
        d, r, u = camera(yaw, pitch)
        P = lambda v: (dot(v, r), -dot(v, u), dot(v, d))
        polys = []
        for b, ops in boxes:
            ops = ([("r", b[7][0], b[7][1], b[7][2:5])] if len(b) > 7 else []) + ops
            mat = b[6]
            for p0, U, V in faces(b):
                q0 = apply(p0, ops)
                eu, ev = sub(apply(add(p0, U), ops), q0), sub(apply(add(p0, V), ops), q0)
                n = cross(eu, ev); ln = math.sqrt(dot(n, n))
                if ln < 1e-9 or dot(n, d) >= 0: continue
                lit = 0.52 + 0.48 * max(0.0, dot(n, LIGHT) / ln)
                lu, lv = math.sqrt(dot(eu, eu)), math.sqrt(dot(ev, ev)); mn = min(lu, lv)
                nu, nv = max(1, math.ceil(lu / cell - 1e-6)), max(1, math.ceil(lv / cell - 1e-6))
                rw = 2 if mat == "casing" and mn >= 6 else (1 if mn >= 2.5 else 0)
                base = shade(PALETTE[mat], lit)
                rim = shade(PALETTE["frame"] if rw == 2 else shade(PALETTE[mat], 0.8), lit)
                s0, su, sv = P(q0), P(eu), P(ev)
                for i in range(nu):
                    a0, a1 = i / nu, (i + 1) / nu
                    edge_u = a0 * lu < rw - 1e-6 or a1 * lu > lu - rw + 1e-6
                    for j in range(nv):
                        c0, c1 = j / nv, (j + 1) / nv
                        edge = edge_u or c0 * lv < rw - 1e-6 or c1 * lv > lv - rw + 1e-6
                        pts = [(s0[0] + su[0] * a + sv[0] * c, s0[1] + su[1] * a + sv[1] * c)
                            for a, c in ((a0, c0), (a1, c0), (a1, c1), (a0, c1))]
                        depth = s0[2] + su[2] * (a0 + a1) / 2 + sv[2] * (c0 + c1) / 2
                        polys.append((depth, pts, rim if edge else base))
        polys.sort(key=lambda q: -q[0])
        return polys

    def render(boxes, yaw=-28, pitch=26, size=640, bg=(0, 0, 0, 0), fit=None, cell=1.0, ss=2):
        polys = project(boxes, yaw, pitch, cell)
        if fit is None:
            xs = [x for _, pts, _ in polys for x, _ in pts]; ys = [y for _, pts, _ in polys for _, y in pts]
            k = 0.86 * size / max(max(xs) - min(xs), max(ys) - min(ys))
            fit = (k, size / 2 - k * (max(xs) + min(xs)) / 2, size / 2 - k * (max(ys) + min(ys)) / 2)
        k, ox, oy = fit
        img = Image.new("RGBA", (size * ss, size * ss), bg); draw = ImageDraw.Draw(img)
        for _, pts, col in polys:
            draw.polygon([((x * k + ox) * ss, (y * k + oy) * ss) for x, y in pts],
                        fill=col + (255,), outline=col + (255,))
        return img.resize((size, size), Image.LANCZOS), fit

    def gif(path, scene_at, ticks, size, step=0.5):
        frames, fit = [], None
        for i in range(max(1, round(ticks / step))):
            img, fit = render(scene_at(i * step), size=size, bg=(34, 36, 40, 255), fit=fit, cell=1.5)
            frames.append(img.convert("RGB"))
        frames[0].save(path, save_all=True, append_images=frames[1:], duration=40, loop=0)

    # ---------------- Minecraft export ----------------
    def fit_uv(a, b):
        if b - a >= 16: return 0, 16
        k = math.floor(a / 16) * 16; a, b = a - k, b - k
        if b > 16: a, b = 16 - (b - a), 16
        return round(a, 3), round(b, 3)

    def element(b):
        x1, y1, z1, x2, y2, z2, mat = b[:7]
        el = {"from": [x1, y1, z1], "to": [x2, y2, z2], "faces": {}}
        if len(b) > 7:
            el["rotation"] = {"origin": b[7][2:5], "axis": b[7][0], "angle": b[7][1]}
        uv = {"north": (16 - x2, 16 - y2, 16 - x1, 16 - y1), "south": (x1, 16 - y2, x2, 16 - y1),
            "west": (z1, 16 - y2, z2, 16 - y1), "east": (16 - z2, 16 - y2, 16 - z1, 16 - y1),
            "up": (x1, z1, x2, z2), "down": (x1, 16 - z2, x2, 16 - z1)}
        for face, (u1, v1, u2, v2) in uv.items():
            (u1, u2), (v1, v2) = fit_uv(u1, u2), fit_uv(v1, v2)
            el["faces"][face] = {"uv": [u1, v1, u2, v2], "texture": "#" + mat}
        return el

    def tex(mat): return f"{MOD_ID}:block/machines/{mat}"

    def model_json(boxes):
        textures = {m: tex(m) for m in sorted({b[6] for b in boxes})}
        textures["particle"] = tex("casing")
        return {"parent": "block/block", "textures": textures, "elements": [element(b) for b in boxes]}

    def blockstate(name):
        return {"variants": {f"facing={f}": {"model": f"{MOD_ID}:block/{name}/base", **({"y": y} if y else {})}
                            for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}}

    def anim_spec(name):
        parts = []
        for group, src in (("common", COMMON["parts"]), (name, MACHINES[name]["parts"])):
            for pname, p in src.items():
                spec = {k: v for k, v in p.items() if k != "boxes"}
                spec.update(name=pname, model=f"{MOD_ID}:block/{group}/{pname}")
                parts.append(spec)
        return {
            "machine": name, "model": f"{MOD_ID}:block/{name}/base",
            "footprint": {"blocks": [1, 2, 1], "upper": f"{MOD_ID}:block/machine_ghost"},
            "sides": {"front": "north", "input": "east", "output": "west", "heat_in": "south"},
            "rpm_by_heat": RPM,
            "rules": {
                "units": "px (1/16 block), degrees, ticks; right-hand-rule rotations",
                "spin": "angle = rpm * 0.3 * (spin_proc if processing else spin_idle) * ticks",
                "cycle": "only while processing: period = cycle_ticks * 32 / rpm, value = keys at "
                        "u = (t % period) / period; otherwise the first key (rest pose)",
                "heat": "angle = heat_angles[heat] + jitter * sin(1.7 * ticks)",
                "keys": "[u, value, ease_to_next], ease in linear|in|out|inout|hold",
                "transform": "translate along axis by value, or rotate about pivot by value; apply the "
                            "part's own transform, then its parent's, then FACING rotation about (8,8,8)",
            },
            "parts": parts,
        }

    def texture(mat):
        img = Image.new("RGBA", (16, 16)); px = img.load(); base = PALETTE[mat]
        for x in range(16):
            for y in range(16):
                edge = min(x, y, 15 - x, 15 - y)
                if mat == "casing" and edge < 2: c = shade(PALETTE["frame"], 0.8 if edge == 0 else 1.0)
                elif mat == "belt": c = shade(base, 0.65 if x % 4 == 0 else 1.0)
                else: c = shade(base, 0.8 if edge == 0 else 1.0)
                n = ((x * 73 + y * 151) ^ (x * y * 7)) % 7 - 3
                px[x, y] = tuple(max(0, min(255, v + 3 * n)) for v in c) + (255,)
        return img

    def main():
        ap = argparse.ArgumentParser(description="Create-style machine asset generator")
        ap.add_argument("--out", default="build")
        ap.add_argument("--heat", default="heated", choices=list(RPM))
        ap.add_argument("--no-gif", action="store_true")
        args = ap.parse_args()
        root, assets = args.out, os.path.join(args.out, "assets", MOD_ID)

        def dump(path, obj):
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "w") as f: json.dump(obj, f, indent=1)

        for pname, p in COMMON["parts"].items():
            dump(f"{assets}/models/block/common/{pname}.json", model_json(p["boxes"]))
        for name, m in MACHINES.items():
            dump(f"{assets}/models/block/{name}/base.json", model_json(COMMON["static"] + m["static"]))
            for pname, p in m["parts"].items():
                dump(f"{assets}/models/block/{name}/{pname}.json", model_json(p["boxes"]))
            dump(f"{assets}/blockstates/{name}.json", blockstate(name))
            dump(f"{root}/anim/{name}.anim.json", anim_spec(name))
        dump(f"{assets}/models/block/machine_ghost.json", {"textures": {"particle": tex("casing")}})
        os.makedirs(f"{assets}/textures/block/machines", exist_ok=True)
        for mat in PALETTE:
            texture(mat).save(f"{assets}/textures/block/machines/{mat}.png")

        prev = os.path.join(root, "preview"); os.makedirs(prev, exist_ok=True)
        for name in MACHINES:
            idle = scene(name, 0, args.heat, proc=False)
            render(idle)[0].save(f"{prev}/{name}_front.png")
            render(idle, yaw=150)[0].save(f"{prev}/{name}_back.png")
        line = lambda t: [x for n, dx in LINE for x in scene(n, t, args.heat, dx=dx)]
        render(line(6), size=960)[0].save(f"{prev}/line.png")
        rpm = RPM[args.heat]
        if rpm and not args.no_gif:
            for name, m in MACHINES.items():
                ticks = max(p.get("cycle_ticks", 0) for p in m["parts"].values()) * 32 / rpm
                gif(f"{prev}/{name}.gif", lambda t, n=name: scene(n, t, args.heat), ticks, 420)
            gif(f"{prev}/line.gif", line, 40 * 32 / rpm, 720)
        print("done ->", os.path.abspath(root))

    if __name__ == "__main__":
        main()
    ```

    ### Notes for your local agent

    - **Blocks.** Each `base.json` is 32 px tall, so render it from the lower block. On placement, put an invisible "ghost" block above it that has collision and forwards interactions to the base. Breaking either block breaks both. Mark both blocks as non-occluding.
    - **Moving parts.** Register the partial models as extra or standalone models and draw them in a block-entity renderer, following each `.anim.json`.
    - **Transform order.** For each part, apply the block's facing rotation first, then the parent's transform, then the part's own transform: translate to the pivot, rotate, translate back.
    - **Smooth animation.** Use game time plus the partial tick for `t`.
    - **Heat tiers.** The heat reaching the back face picks the tier (none, heated, or superheated). That tier sets the RPM, which scales both the spin speed and the processing cycle, so superheating doubles throughput.
    - **Z-fighting.** Where two elements overlap on purpose (octagon drums, the blade star), one is slightly inset, so there's no flicker in-game. Keep that inset if you edit them in Blockbench.
    - **Textures.** The textures are flat placeholders with Create-style rims. The UVs are simple per-face projections, so artists can repaint the files directly. The blade and cog can later be swapped for cutout-texture planes if you want rounder silhouettes.   

    Here's the repainted preview. It uses the same textures the script now writes, sampled texel by texel.The geometry and animation are unchanged. Only the palette, the texture generator and the renderer changed.

**Materials**
- The casing is riveted soot-iron plate with a 2 px walnut rim and brass corner rivets.
- The walnut frame has grain running along its length.
- Aged brass has tarnish pits, and copper has verdigris specks.
- Iron is blackened and scratched, the belt is oiled leather with stitching, and the blade is tool steel.

**How the pixel variation works**
- Each material has a ramp of adjacent shades set in `VARY`: 5 for wood, 4 for casing, brass and copper, and down to 1 for the needle.
- Every texel picks a step on its ramp from clustered noise, so neighbouring pixels drift by one or two shades, the way vanilla blocks do.
- The noise is a deterministic integer hash, so every run produces identical textures, and the web preview matches them exactly.

**Preview**
- The renderer now samples the real 16×16 textures through each face's in-game UVs, one cell per texel. The PNGs and GIFs show exactly what Minecraft will draw.
- Faces smaller than 16 px show only part of the texture, just as in-game. That's why the rim and rivets appear on full-size casing faces but not on thin ones.

### Patch for `create_machines.py`

**1. Replace `PALETTE` with this block:**

```python
# ---- steampunk palette: base colour + number of adjacent shades each texel may use (1-5) ----
PALETTE = {
    "casing": (96, 88, 80),     # riveted soot-iron plate
    "frame":  (104, 66, 40),    # dark walnut
    "alloy":  (150, 146, 134),  # gunmetal
    "brass":  (200, 152, 64),   # aged brass
    "copper": (182, 98, 58),    # copper
    "iron":   (54, 52, 54),     # blackened iron
    "belt":   (96, 60, 38),     # oiled leather
    "blade":  (204, 202, 194),  # tool steel
    "dial":   (228, 214, 178),  # aged ivory
    "needle": (152, 36, 28),    # oxblood
}
VARY = {"casing": 4, "frame": 5, "alloy": 3, "brass": 4, "copper": 4,
        "iron": 3, "belt": 3, "blade": 2, "dial": 2, "needle": 1}
SHADE_STEP = 0.055                 # brightness gap between adjacent shades
PATINA, RIVET, STITCH = (86, 148, 124), (214, 170, 84), (196, 164, 112)
```

**2. Replace `texture()` with these functions** (keep `shade()` where it is):

```python
def hash2(x, y, s):  # deterministic integer hash (mirrored bit-for-bit in the web preview)
    h = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0

def texel(mat, x, y):
    s, n = sum(map(ord, mat)), VARY[mat]
    mid = hash2(x >> 2, y, s + 1) if mat == "frame" else hash2(x >> 1, y >> 1, s + 1)
    v = 0.5 * hash2(x, y, s) + 0.3 * mid + 0.2 * hash2(x >> 2, y >> 2, s + 2)
    k = 1 + (min(n - 1, int(v * n)) - (n - 1) / 2) * SHADE_STEP      # step on the shade ramp
    base, edge = PALETTE[mat], min(x, y, 15 - x, 15 - y)
    if mat == "casing":
        if edge < 2: return shade(texel("frame", x, y), 0.8 if edge == 0 else 1.0)  # walnut rim
        if x in (2, 13) and y in (2, 13): return shade(RIVET, k)                  # rivets
        if x == 2 or y == 2: k *= 1.08                                            # plate bevel
        if x == 13 or y == 13: k *= 0.9
    if mat == "copper" and edge > 0 and hash2(x, y, s + 9) > 0.88: base = PATINA   # verdigris
    if mat == "brass" and hash2(x, y, s + 9) > 0.93: k *= 0.8                      # tarnish pits
    if mat == "iron" and (x + y) % 7 == 0 and hash2(x, y, s + 5) > 0.4: k *= 1.12  # scratches
    if mat == "belt":
        if x % 4 == 0: k *= 0.8                                                    # grooves
        if y in (4, 11) and x % 2 == 0: base = STITCH                              # stitching
    if edge == 0 and mat not in ("dial", "needle", "belt"): k *= 0.8               # vanilla-style border
    return shade(base, k)

def texture(mat):
    img = Image.new("RGBA", (16, 16)); px = img.load()
    for x in range(16):
        for y in range(16):
            px[x, y] = texel(mat, x, y) + (255,)
    return img

TEX = {}
def tex_px(mat, u, v):
    if mat not in TEX: TEX[mat] = [[texel(mat, x, y) for y in range(16)] for x in range(16)]
    return TEX[mat][min(15, max(0, int(u)))][min(15, max(0, int(v)))]
```

**3. Replace `element()` with these two functions.** The renderer now shares the game's UVs:

```python
def face_uvs(b):  # order matches faces(): east, west, up, down, south, north
    x1, y1, z1, x2, y2, z2 = b[:6]
    raw = {"east": (16 - z2, 16 - y2, 16 - z1, 16 - y1), "west": (z1, 16 - y2, z2, 16 - y1),
           "up": (x1, z1, x2, z2), "down": (x1, 16 - z2, x2, 16 - z1),
           "south": (x1, 16 - y2, x2, 16 - y1), "north": (16 - x2, 16 - y2, 16 - x1, 16 - y1)}
    out = {}
    for f, (u1, v1, u2, v2) in raw.items():
        (u1, u2), (v1, v2) = fit_uv(u1, u2), fit_uv(v1, v2)
        out[f] = (u1, v1, u2, v2)
    return out

def element(b):
    el = {"from": list(b[0:3]), "to": list(b[3:6]),
          "faces": {f: {"uv": list(uv), "texture": "#" + b[6]} for f, uv in face_uvs(b).items()}}
    if len(b) > 7:
        el["rotation"] = {"origin": b[7][2:5], "axis": b[7][0], "angle": b[7][1]}
    return el
```

**4. Replace `project()`** so it samples the textures:

```python
def project(boxes, yaw, pitch, cell=1.0):
    """Painter's algorithm on 1-texel cells, sampling the same textures the game uses."""
    d, r, u = camera(yaw, pitch)
    P = lambda v: (dot(v, r), -dot(v, u), dot(v, d))
    polys = []
    for b, ops in boxes:
        ops = ([("r", b[7][0], b[7][1], b[7][2:5])] if len(b) > 7 else []) + ops
        mat = b[6]
        for (p0, U, V), (u1, v1, u2, v2) in zip(faces(b), face_uvs(b).values()):
            q0 = apply(p0, ops)
            eu, ev = sub(apply(add(p0, U), ops), q0), sub(apply(add(p0, V), ops), q0)
            n = cross(eu, ev); ln = math.sqrt(dot(n, n))
            if ln < 1e-9 or dot(n, d) >= 0: continue
            lit = 0.52 + 0.48 * max(0.0, dot(n, LIGHT) / ln)
            lu, lv = math.sqrt(dot(eu, eu)), math.sqrt(dot(ev, ev))
            nu, nv = max(1, math.ceil(lu / cell - 1e-6)), max(1, math.ceil(lv / cell - 1e-6))
            s0, su, sv = P(q0), P(eu), P(ev)
            for i in range(nu):
                a0, a1 = i / nu, (i + 1) / nu; am = (a0 + a1) / 2
                tu = u1 + am * (u2 - u1)
                for j in range(nv):
                    c0, c1 = j / nv, (j + 1) / nv; cm = (c0 + c1) / 2
                    col = shade(tex_px(mat, tu, v2 - cm * (v2 - v1)), lit)
                    pts = [(s0[0] + su[0] * a + sv[0] * c, s0[1] + su[1] * a + sv[1] * c)
                           for a, c in ((a0, c0), (a1, c0), (a1, c1), (a0, c1))]
                    polys.append((s0[2] + su[2] * am + sv[2] * cm, pts, col))
    polys.sort(key=lambda q: -q[0])
    return polys
```

**5. In `gif()`, drop `cell=1.5`** from the `render(...)` call. The GIFs then show every texel, and they take somewhat longer to generate.

To make the look noisier or calmer, raise or lower `SHADE_STEP`, or change the per-material counts in `VARY`.
# Saddlery
    A crafting station one block wide and two tall, 100 cuboids, front to the north like a furnace. It is the bench where tack is made: the saddle, leads and the pack harnesses. The Chronicle node "We should ride them" asks for it.

    **Lower block: the saddler's bench**
    - Log legs, a plank top, aprons and a drawer with a bronze pull.
    - On the shelf: the hide blanket rolled in its fur fringe, a saddlebag, a bedroll in two straps and a box of buckles.
    - A girth with its buckle hangs on the west edge, a coiled lead on the east one.
    - On the top: a round knife, a mallet, a strap being cut with its buckle, a spool of red thread with a needle, and an awl.

    **Upper block: saddle horse and tack rack**
    - A log on two trestles carries the bronze saddle from the saddle concept (`tools/preview_saddles.py`): a red blanket with a dark border and a gold edge, a leather seat with cantle, pommel and bronze horn, a fender, a stirrup on its leather and a stitched girth with a buckle.
    - Behind it, a rack of two posts and two rails: a rope coil and shears on pegs, and the steel blanket (blue, riveted plate edge) folded over the top rail.

    **Palette**
    - The three saddle tiers keep the colours of the concept: hide (tan, cream fur), bronze (red, gold, bronze fittings) and steel (blue, plate). Wood, log and rope are the station materials the other benches use.

    **In the mod**
    - Generator: `Ark/tools/build_station_assets.py`, `saddlery()`. It writes `models/block/station/saddlery_lower.json`, `saddlery_upper.json`, `saddlery_item.json` (both halves stacked, for the inventory) and fourteen `saddle_*` textures.
    - Block: `feature/station/SaddleryBlock.java`, two halves like the Primitive Forge; the blockstate turns both with the facing.
    - Showcase: Models page, as a block card and as a live 3D card under Workstations.
