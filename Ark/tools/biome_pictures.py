"""Fetches a picture of every biome the pack's Overworld generates, for the Biomes page of the showcase.

    python tools/biome_pictures.py [--refresh]

The biome list is read from the pack itself: Terralith's Overworld dimension, which names the vanilla biomes it
keeps and its own. A vanilla biome takes the picture the Minecraft Wiki shows for it in its biome table
(https://minecraft.wiki/w/Biome, CC BY-NC-SA 3.0); a Terralith biome takes the first gallery picture of its page
on the Stardust Labs wiki (https://stardustlabs.miraheze.org, CC BY-SA 4.0). Each becomes a 576x324 WebP under
design/showcase/biomes, and design/showcase/biomes.json records the name, the file, the page it came from and
its licence, which the showcase credits on every card. Pictures already there are kept unless --refresh is
given. Needs internet; without the Terralith jar in shared-mods only the vanilla Overworld biomes are listed.
"""
from __future__ import annotations

import argparse
import datetime
import html
import io
import json
import re
import sys
import time
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

from PIL import Image

ARK = Path(__file__).resolve().parents[1]
OUT = ARK / "design/showcase/biomes"
INDEX = ARK / "design/showcase/biomes.json"
PICTURE = (576, 324)
QUALITY = 74
AGENT = "ArkSurvivalReturns-showcase/1.0 (private mod project page; fetches one picture per biome)"
SITES = {
    "minecraft": {"site": "Minecraft Wiki", "api": "https://minecraft.wiki/api.php", "page": "https://minecraft.wiki/w/",
                  "licence": "CC BY-NC-SA 3.0"},
    "terralith": {"site": "Stardust Labs wiki", "api": "https://stardustlabs.miraheze.org/w/api.php",
                  "page": "https://stardustlabs.miraheze.org/wiki/", "licence": "CC BY-SA 4.0"},
}
# Vanilla Overworld biomes, for a checkout without the Terralith jar.
VANILLA = """badlands bamboo_jungle beach birch_forest cherry_grove cold_ocean dark_forest deep_cold_ocean deep_dark
deep_frozen_ocean deep_lukewarm_ocean deep_ocean desert dripstone_caves eroded_badlands flower_forest forest frozen_ocean
frozen_peaks frozen_river grove ice_spikes jagged_peaks jungle lukewarm_ocean lush_caves mangrove_swamp meadow
mushroom_fields ocean old_growth_birch_forest old_growth_pine_taiga old_growth_spruce_taiga pale_garden plains river
savanna savanna_plateau snowy_beach snowy_plains snowy_slopes snowy_taiga sparse_jungle stony_peaks stony_shore
sunflower_plains swamp taiga warm_ocean windswept_forest windswept_gravelly_hills windswept_hills windswept_savanna
wooded_badlands""".split()


def api(site: str, **params) -> dict:
    params.update(format="json", formatversion="2")
    request = urllib.request.Request(SITES[site]["api"] + "?" + urllib.parse.urlencode(params), headers={"User-Agent": AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def jars() -> list[Path]:
    return sorted((ARK / "build/moddev/artifacts").glob("minecraft-patched-*-merged.jar")) + sorted((ARK / "shared-mods").glob("*.jar"))


def biomes() -> list[str]:
    """Ids of the biomes the pack's Overworld generates."""
    for jar in sorted((ARK / "shared-mods").glob("Terralith_*.jar")):
        with zipfile.ZipFile(jar) as archive:
            source = json.loads(archive.read("data/minecraft/dimension/overworld.json"))["generator"]["biome_source"]
            return sorted({entry["biome"] for entry in source["biomes"]})
    return [f"minecraft:{name}" for name in VANILLA]


def names() -> dict[str, str]:
    """English biome names from the game's and the terrain mods' language files."""
    found = {}
    for jar in jars():
        with zipfile.ZipFile(jar) as archive:
            for entry in archive.namelist():
                parts = entry.split("/")
                if len(parts) == 4 and parts[0] == "assets" and parts[2] == "lang" and parts[3] == "en_us.json":
                    for key, value in json.loads(archive.read(entry).decode("utf-8-sig")).items():
                        if key.startswith("biome.") and key.count(".") >= 2:
                            namespace, path = key.split(".", 2)[1:]
                            found[f"{namespace}:{path}"] = value
    return found


def title(biome: str, known: dict[str, str]) -> str:
    # Language keys write a folder as a dot: terralith:cave/deep_caves is biome.terralith.cave.deep_caves.
    return known.get(biome.replace("/", ".")) or biome.split(":")[1].split("/")[-1].replace("_", " ").title()


def pack() -> dict:
    """The terrain mods and their versions, from the jar names."""
    versions = {}
    for jar in sorted((ARK / "shared-mods").glob("*.jar")):
        for mod in ("terralith", "tectonic"):
            version = re.search(r"(\d+\.\d+\.\d+)", jar.name.split("26.1")[-1] if mod == "terralith" else jar.name)
            if jar.name.lower().startswith(mod) and version:
                versions[mod] = version.group(1)
    return versions


def vanilla_files() -> dict[str, str]:
    """Biome id path to the file the Minecraft Wiki's biome table shows for it."""
    page = api("minecraft", action="parse", page="Biome", prop="text")["parse"]["text"]
    files = {}
    for row in re.findall(r"<tr[^>]*>((?:(?!</tr>).)*?)</tr>", page, re.S):
        name = re.search(r"<td[^>]*>\s*<b>\s*<a [^>]*>([^<]+)</a>", row)
        picture = re.search(r'<a href="/w/(File:[^"]+)"[^>]*class="mw-file-description"', row)
        if name and picture:
            key = html.unescape(name.group(1)).strip().lower().replace(" ", "_")
            files.setdefault(key, urllib.parse.unquote(html.unescape(picture.group(1))).replace("_", " "))
    return files


def terralith_file(name: str) -> tuple[str, str] | None:
    """The page of a Terralith biome and the first full-size picture of its gallery."""
    # Pages are in sentence case: "Skylands (autumn)", "Alpha islands"; redirects cover most other spellings.
    for page in dict.fromkeys((name, name[0] + name[1:].lower())):
        data = api("terralith", action="parse", page=page, prop="text", redirects="1")
        if "error" in data:
            continue
        text = data["parse"]["text"]
        for tag in re.finditer(r'<a href="/wiki/(File:[^"?]+)[^"]*"[^>]*class="mw-file-description"[^>]*><img[^>]*data-file-width="(\d+)"', text):
            if int(tag.group(2)) >= 800:
                return data["parse"]["title"], urllib.parse.unquote(html.unescape(tag.group(1))).replace("_", " ")
    return None


def thumbs(site: str, files: list[str]) -> dict[str, str]:
    """A 640-pixel-wide rendition of each file, as the wiki scales it."""
    urls = {}
    for start in range(0, len(files), 40):
        data = api(site, action="query", titles="|".join(files[start:start + 40]), prop="imageinfo", iiprop="url", iiurlwidth="640")
        renamed = {entry["to"]: entry["from"] for entry in data["query"].get("normalized", [])}
        for page in data["query"]["pages"]:
            info = (page.get("imageinfo") or [{}])[0]
            if info.get("thumburl") or info.get("url"):
                urls[renamed.get(page["title"], page["title"])] = info.get("thumburl") or info["url"]
    return urls


def save(url: str, target: Path):
    """Downloads a picture and stores it cropped to 16:9 at the showcase's size."""
    request = urllib.request.Request(url if url.startswith("http") else "https:" + url, headers={"User-Agent": AGENT})
    with urllib.request.urlopen(request, timeout=120) as response:
        picture = Image.open(io.BytesIO(response.read())).convert("RGB")
    width, height = picture.size
    wanted = PICTURE[0] / PICTURE[1]
    if width / height > wanted:
        crop = round(height * wanted)
        picture = picture.crop(((width - crop) // 2, 0, (width - crop) // 2 + crop, height))
    else:
        crop = round(width / wanted)
        picture = picture.crop((0, (height - crop) // 2, width, (height - crop) // 2 + crop))
    picture.resize(PICTURE, Image.Resampling.LANCZOS).save(target, "WEBP", quality=QUALITY, method=6)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--refresh", action="store_true", help="fetch every picture again instead of keeping the ones already there")
    arguments = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    known = names()
    ids = biomes()
    before = {entry["biome"]: entry for entry in json.loads(INDEX.read_text(encoding="utf-8")).get("biomes", [])} if INDEX.is_file() else {}
    table = vanilla_files()
    wanted = {}  # biome -> (site key, page title, file title)
    for biome in ids:
        namespace, path = biome.split(":")
        if namespace == "minecraft" and path in table:
            wanted[biome] = ("minecraft", "Biome", table[path])
        elif namespace == "terralith":
            found = terralith_file(title(biome, known))
            # Without a page of its own it may be a vanilla biome Terralith brings back (Deep Warm Ocean): the Minecraft Wiki's file.
            wanted[biome] = ("terralith", *found) if found else ("minecraft", "Biome", f"File:{title(biome, known)}.png")
            time.sleep(0.2)
    urls = {site: thumbs(site, sorted({file for where, _, file in wanted.values() if where == site})) for site in SITES}
    entries, fetched, total = [], 0, 0
    for biome in ids:
        entry = {"biome": biome, "name": title(biome, known)}
        name = biome.replace(":", ".").replace("/", ".") + ".webp"
        if biome in wanted:
            site, page, file = wanted[biome]
            credit = {"site": SITES[site]["site"], "licence": SITES[site]["licence"], "file": file,
                      "url": SITES[site]["page"] + urllib.parse.quote(file.replace(" ", "_"))}
            kept = not arguments.refresh and (OUT / name).is_file() and before.get(biome, {}).get("credit", {}).get("file") == file
            if not kept and file in urls[site]:
                try:
                    save(urls[site][file], OUT / name)
                    fetched += 1
                    time.sleep(0.2)
                except Exception as error:  # one broken picture must not stop the rest
                    print(f"{biome}: {file} could not be fetched ({error})")
            if (OUT / name).is_file():
                entry["picture"] = name
                entry["credit"] = credit
                total += (OUT / name).stat().st_size
        entries.append(entry)
    for stale in set(path.name for path in OUT.glob("*.webp")) - {entry.get("picture") for entry in entries}:
        (OUT / stale).unlink()
    INDEX.write_text(json.dumps({
        "about": "Written by tools/biome_pictures.py; do not edit. Pictures belong to their wikis' contributors under the licence given.",
        "fetched": datetime.date.today().isoformat(), "pack": pack(), "biomes": entries}, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    pictured = sum(1 for entry in entries if "picture" in entry)
    print(f"{pictured} pictures for {len(entries)} biomes ({fetched} fetched now, {total / 1e6:.1f} MB) in {OUT}")
    missing = [entry["biome"] for entry in entries if "picture" not in entry]
    if missing:
        print("no picture: " + ", ".join(missing))
    return 0


if __name__ == "__main__":
    sys.exit(main())
