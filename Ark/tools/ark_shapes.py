"""Shared vector shapes for the game textures and the showcase, so both always draw the same silhouette.

rounded_star: the tech-tree node badge, an eight-point star whose tips and valleys are both rounded
with quadratic curves (user reference, F01). Coordinates are unit-radius around the origin.
"""
import math

POINTS = 8
INNER = 0.76      # valley radius relative to the tips
ROUNDING = 0.24   # how far along each edge the corner curve starts (0..0.5)


def _vertices(points=POINTS, inner=INNER):
    out = []
    for i in range(points * 2):
        angle = -math.pi / 2 + i * math.pi / points  # first tip points straight up
        radius = 1.0 if i % 2 == 0 else inner
        out.append((radius * math.cos(angle), radius * math.sin(angle)))
    return out


def _corners(points=POINTS, inner=INNER, rounding=ROUNDING):
    """For each vertex: (curve start, control = vertex, curve end)."""
    vs = _vertices(points, inner)
    corners = []
    for i, v in enumerate(vs):
        prev, nxt = vs[i - 1], vs[(i + 1) % len(vs)]
        start = (v[0] + (prev[0] - v[0]) * rounding, v[1] + (prev[1] - v[1]) * rounding)
        end = (v[0] + (nxt[0] - v[0]) * rounding, v[1] + (nxt[1] - v[1]) * rounding)
        corners.append((start, v, end))
    return corners


def star_polygon(cx, cy, radius, samples=10, **shape):
    """Sampled outline for raster drawing (PIL)."""
    pts = []
    for start, control, end in _corners(**shape):
        for k in range(samples + 1):
            t = k / samples
            x = (1 - t) ** 2 * start[0] + 2 * (1 - t) * t * control[0] + t ** 2 * end[0]
            y = (1 - t) ** 2 * start[1] + 2 * (1 - t) * t * control[1] + t ** 2 * end[1]
            pts.append((cx + x * radius, cy + y * radius))
    return pts


def star_path(cx, cy, radius, **shape):
    """SVG path with one quadratic curve per corner and straight edges between them."""
    corners = _corners(**shape)
    f = lambda p: f'{cx + p[0] * radius:.2f},{cy + p[1] * radius:.2f}'
    d = [f'M{f(corners[0][0])}']
    for i, (start, control, end) in enumerate(corners):
        if i:
            d.append(f'L{f(start)}')
        d.append(f'Q{f(control)} {f(end)}')
    d.append('Z')
    return ' '.join(d)
