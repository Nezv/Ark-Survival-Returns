"""Lives the sample regions of the existence model through and prints the verdict of each, without the game.

The same model as the showcase's Behaviour page (tools/existence_model.js, run with Node) on the same data
(tools/showcase_existence.py): the wildlife register of one biome region beyond the loaded land, under the proposed
bounded model and under the rules the game runs today. For each run it says whether the register stays bounded,
keeps every class it has room for and settles, how many animals it starts and ends with, the pressure on what each
kind eats, and which species are gone.

Run from Ark:
  python tools/existence_check.py                         every sample region, both rule sets
  python tools/existence_check.py --region plains desert --cells 64 256 1024 --days 1440
  python tools/existence_check.py --rules bounded --set kill=0.03 fecundity=5 --species
  python tools/existence_check.py --rules bounded --strict   exit 1 unless the proposal is bounded, alive and settled everywhere
"""
import argparse
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import showcase_existence

HERE = Path(__file__).resolve().parent
KINDS = {'GRAZER': 'plant eaters', 'HUNTER': 'hunters', 'APEX': 'giants', 'FLYER': 'flyers', 'SEA': 'sea animals'}
STARTS = ('settled', 'empty', 'crowded', 'no_hunters', 'hunters')


def setups(args, data):
    weights = {}
    for pair in args.set:
        name, _, value = pair.partition('=')
        if name not in {w['id'] for w in data['model']['weights']}:
            raise SystemExit(f'--set {name}: no such weight; there are {", ".join(w["id"] for w in data["model"]["weights"])}')
        weights[name] = float(value)
    regions = args.region or [region['id'] for region in data['regions']]
    unknown = set(regions) - {region['id'] for region in data['regions']}
    if unknown:
        raise SystemExit(f'--region {" ".join(sorted(unknown))}: not a sample region of design/existence/model.json')
    out = []
    for region in regions:
        for cells in args.cells:
            for rules in args.rules:
                # The proposal has arrivals everywhere; the game today only where a player is near.
                for arrivals in ((True,) if rules == 'bounded' else (False, True)) if args.arrivals == 'both' else (args.arrivals == 'on',):
                    out.append({'region': region, 'cells': cells, 'zone': args.zone, 'rules': rules, 'arrivals': arrivals, 'start': args.start,
                                'days': args.days, 'pace': args.pace, 'seed': args.seed, 'runs': args.runs, 'weights': weights})
    return out


def yes(flag):
    return 'yes' if flag else 'NO '


def report(result, species):
    setup, verdict = result['setup'], result['verdict']
    classes = [c for c in result['classes'] if c['room'] > 0 or c['start'] > 0]
    line = (f'{setup["region"]:<13}{setup["cells"]:>5} {setup["rules"]:<8}{"on" if setup["arrivals"] else "off":<4}'
            f'{yes(verdict["bounded"])}  {yes(verdict["alive"])}  {yes(verdict["settled"])}     '
            f'{result["start"]:>5.1f} -> {verdict["tail"]:>5.1f}  ')
    line += '  '.join(f'{KINDS[c["kind"]]} {c["animals"]:.1f} ({c["there"] * 100:.0f}%'
                      + (f', gone day {c["lost"]:.0f}' if c['lost'] is not None and c['there'] < 0.9 else '') + ')' for c in classes)
    print(line)
    pressure = '  '.join(f'{role} {value:.2f}' for role, value in result['pressure'].items() if value > 0)
    deaths = '  '.join(f'{cause} {count}' for cause, count in sorted(result['deaths'].items(), key=lambda item: -item[1]))
    print(f'{"":<33}pressure: {pressure or "none"};  ends: {deaths or "none"};  level {verdict["level"][0]:.0f} -> {verdict["level"][1]:.0f}')
    if verdict['gone']:
        print(f'{"":<33}gone: {", ".join(verdict["gone"])}')
    if species:
        for s in result['species']:
            mark = 'never came' if s['never'] else f'{s["there"] * 100:>3.0f}% there'
            print(f'{"":<35}{s["name"]:<18}{s["start"]:>5.1f} -> {s["animals"]:>5.1f}  in {s["groups"]:.1f} groups, {mark}')


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--region', nargs='*', help='sample regions (default: all)')
    parser.add_argument('--rules', nargs='*', default=['bounded', 'coded'], choices=['bounded', 'coded'])
    parser.add_argument('--arrivals', default='both', choices=['both', 'on', 'off'],
                        help='both: on for the bounded model, off and on for the coded rules')
    parser.add_argument('--cells', nargs='*', type=int, default=[256], help='chunks of the region, 16 to 1024')
    parser.add_argument('--zone', type=int, default=3, choices=[1, 2, 3])
    parser.add_argument('--start', default='settled', choices=STARTS)
    parser.add_argument('--days', type=int, default=720)
    parser.add_argument('--pace', type=int, default=1, help='rounds a game day')
    parser.add_argument('--runs', type=int, default=24)
    parser.add_argument('--seed', type=int, default=1)
    parser.add_argument('--set', nargs='*', default=[], metavar='WEIGHT=VALUE', help='weights of the bounded model to try')
    parser.add_argument('--species', action='store_true', help='list every species of each run')
    parser.add_argument('--strict', action='store_true', help='exit 1 unless every bounded run with arrivals is bounded, alive and settled')
    args = parser.parse_args()
    node = shutil.which('node')
    if not node:
        raise SystemExit('Node is not installed: the model is tools/existence_model.js, the same file the page runs')
    data = showcase_existence.data()
    with tempfile.TemporaryDirectory() as folder:
        files = [Path(folder) / 'data.json', Path(folder) / 'setups.json']
        files[0].write_text(json.dumps(data), encoding='utf-8')
        files[1].write_text(json.dumps(setups(args, data)), encoding='utf-8')
        done = subprocess.run([node, str(HERE / 'existence_model.js'), *map(str, files)], capture_output=True, text=True, encoding='utf-8')
    if done.returncode:
        raise SystemExit(done.stderr)
    results = json.loads(done.stdout)
    print(f'{"region":<13}{"cells":>5} {"rules":<8}{"arr":<4}{"bnd":<5}{"live":<5}{"settled":<8}{"animals":<16}classes in the last third (share of its days they are there)')
    for result in results:
        report(result, args.species)
    failed = [r for r in results if r['setup']['rules'] == 'bounded' and r['setup']['arrivals']
              and not (r['verdict']['bounded'] and r['verdict']['alive'] and r['verdict']['settled'])]
    if args.strict and failed:
        raise SystemExit(f'{len(failed)} run(s) of the bounded model are not bounded, alive and settled')


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
