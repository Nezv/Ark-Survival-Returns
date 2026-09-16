"""Plot the production danger-region formula and its discrete area shares."""
from pathlib import Path
import json
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.colors import ListedColormap, BoundaryNorm

ROOT = Path(__file__).resolve().parents[1]

def danger(x, z, width=256):
    radius, period = 2 * width, 4 * width
    u = x + np.floor(radius * .2 * np.sin(2 * np.pi * (z % period) / period) + .5)
    v = z + np.floor(radius * .2 * np.sin(2 * np.pi * (u % period) / period) + .5)
    d = np.maximum(np.abs((u + radius) % period - radius), np.abs((v + radius) % period - radius))
    return 1 + np.minimum(4, np.floor(5 * (d / radius) ** 2)).astype(int)

def main():
    period = 1024
    z, x = np.mgrid[0:period, 0:period]
    ranks = danger(x, z)
    shares = {str(i): float(np.mean(ranks == i) * 100) for i in range(1, 6)}
    (ROOT/'docs/difficulty-area-shares.json').write_text(json.dumps(shares, indent=2))
    z, x = np.mgrid[-1024:1024:4, -1024:1024:4]
    colors = ['#217a63', '#8dbd68', '#ecd06b', '#dc8359', '#853958']
    fig, ax = plt.subplots(figsize=(9, 8), layout='constrained', facecolor='#f8f6ef')
    ax.imshow(danger(x, z), origin='lower', extent=(-1024,1024,-1024,1024), cmap=ListedColormap(colors), norm=BoundaryNorm(np.arange(.5,6),5), interpolation='nearest')
    ax.scatter([0], [0], marker='*', s=160, color='white', edgecolors='#172a25', linewidths=1)
    ax.annotate('Initial spawn', (0,0), (18,18), textcoords='offset points', color='white', fontsize=10, weight='bold')
    ax.set_title('Recurring danger regions', loc='left', fontsize=20, weight='bold', pad=35)
    ax.text(0, 1.02, 'Gradual borders · all five levels recur · existing terrain retained', transform=ax.transAxes, fontsize=10)
    ax.set_xlabel('X distance from initial spawn (blocks)'); ax.set_ylabel('Z distance from initial spawn (blocks)')
    ax.set_xticks(np.arange(-1024,1025,256)); ax.set_yticks(np.arange(-1024,1025,256)); ax.grid(alpha=.15,color='white')
    from matplotlib.patches import Patch
    fig.legend([Patch(facecolor=c) for c in colors], [f'Level {i} — {shares[str(i)]:.2f}%' for i in range(1,6)], loc='outside lower center', ncol=3, frameon=False)
    fig.savefig(ROOT/'docs/difficulty-map.png', dpi=150)
    plt.close(fig)
    print(shares)

if __name__ == '__main__': main()
