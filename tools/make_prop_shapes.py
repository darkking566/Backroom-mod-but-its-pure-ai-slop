"""Generate accurate, model-derived VoxelShape bounding boxes for all 135 props.

Reads:
  - BackroomsProps.java (67 blocks: station, chest, multiProp, blenderProp, prop)
  - FilmProps.java (68 blocks: single, multi)
  - models/block/crafting/<id>.json
  - models/blender_props/<id>.obj
  - models/film/<id>.obj

Outputs:
  - src/main/resources/data/noclip_backrooms/prop_shapes.json
"""

from __future__ import annotations

import json
import math
import os
import re
from collections import deque
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BACKROOMS_PROPS_JAVA = ROOT / "src/main/java/dev/noclip/backrooms/props/BackroomsProps.java"
FILM_PROPS_JAVA = ROOT / "src/main/java/dev/noclip/backrooms/level0/film/FilmProps.java"
ASSETS = ROOT / "src/main/resources/assets/noclip_backrooms"
OUT_JSON = ROOT / "src/main/resources/data/noclip_backrooms/prop_shapes.json"

MIN_THICK = 1.0 / 16.0
MAX_BOXES = 48


def snap(val: float, grid: float = 1.0 / 32.0) -> float:
    return round(round(val / grid) * grid, 4)


def quantize_box_min_thick(x0: float, y0: float, z0: float, x1: float, y1: float, z1: float, grid: float = 1.0 / 32.0) -> tuple[float, float, float, float, float, float]:
    def fix_axis(a: float, b: float) -> tuple[float, float]:
        sa = snap(a, grid)
        sb = snap(b, grid)
        if sb < sa:
            sa, sb = sb, sa
        if sb - sa < MIN_THICK - 1e-6:
            mid = 0.5 * (sa + sb)
            sa = snap(mid - 0.5 * MIN_THICK, grid)
            sb = snap(sa + MIN_THICK, grid)
            if sb - sa < MIN_THICK - 1e-6:
                sb = round(sa + MIN_THICK, 4)
        return sa, sb

    nx0, nx1 = fix_axis(x0, x1)
    ny0, ny1 = fix_axis(y0, y1)
    nz0, nz1 = fix_axis(z0, z1)
    return (nx0, ny0, nz0, nx1, ny1, nz1)


def rotate_point(pt: tuple[float, float, float], origin: tuple[float, float, float], axis: str, angle_deg: float) -> tuple[float, float, float]:
    rad = math.radians(angle_deg)
    c = math.cos(rad)
    s = math.sin(rad)
    dx = pt[0] - origin[0]
    dy = pt[1] - origin[1]
    dz = pt[2] - origin[2]
    if axis == "x":
        rx, ry, rz = dx, dy * c - dz * s, dy * s + dz * c
    elif axis == "y":
        rx, ry, rz = dx * c + dz * s, dy, -dx * s + dz * c
    elif axis == "z":
        rx, ry, rz = dx * c - dy * s, dx * s + dy * c, dz
    else:
        rx, ry, rz = dx, dy, dz
    return (origin[0] + rx, origin[1] + ry, origin[2] + rz)


def boxes_from_json_model(path: Path, prop_id: str) -> list[list[float]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    elems = data.get("elements", [])
    raw_boxes: list[tuple[float, float, float, float, float, float]] = []
    for elem in elems:
        f = [v / 16.0 for v in elem["from"]]
        t = [v / 16.0 for v in elem["to"]]
        x0, y0, z0 = min(f[0], t[0]), min(f[1], t[1]), min(f[2], t[2])
        x1, y1, z1 = max(f[0], t[0]), max(f[1], t[1]), max(f[2], t[2])
        rot = elem.get("rotation")
        if rot and rot.get("angle", 0) != 0:
            origin = tuple(v / 16.0 for v in rot["origin"])
            axis = rot["axis"]
            angle = float(rot["angle"])
            corners = [
                rotate_point((x, y, z), origin, axis, angle)
                for x in (x0, x1)
                for y in (y0, y1)
                for z in (z0, z1)
            ]
            x0 = min(p[0] for p in corners)
            y0 = min(p[1] for p in corners)
            z0 = min(p[2] for p in corners)
            x1 = max(p[0] for p in corners)
            y1 = max(p[1] for p in corners)
            z1 = max(p[2] for p in corners)

        if prop_id in ("sealed_vault_wall", "glitched_wall_seam"):
            if -0.05 <= x0 <= 0.05:
                x0 = 0.0
            if 0.95 <= x1 <= 1.05:
                x1 = 1.0
            if -0.05 <= y0 <= 0.05:
                y0 = 0.0
            if 0.95 <= y1 <= 1.05:
                y1 = 1.0
            if -0.05 <= z0 <= 0.05:
                z0 = 0.0
            if 0.95 <= z1 <= 1.05:
                z1 = 1.0

        raw_boxes.append(quantize_box_min_thick(x0, y0, z0, x1, y1, z1))

    # Deduplicate and drop strictly contained boxes
    unique = list(dict.fromkeys(raw_boxes))
    filtered: list[tuple[float, float, float, float, float, float]] = []
    for i, b in enumerate(unique):
        contained = False
        for j, other in enumerate(unique):
            if i == j:
                continue
            if (
                other[0] <= b[0] + 1e-6
                and other[1] <= b[1] + 1e-6
                and other[2] <= b[2] + 1e-6
                and other[3] >= b[3] - 1e-6
                and other[4] >= b[4] - 1e-6
                and other[5] >= b[5] - 1e-6
            ):
                contained = True
                break
        if not contained:
            filtered.append(b)

    if len(filtered) > MAX_BOXES:
        mn_x = min(b[0] for b in filtered)
        mn_y = min(b[1] for b in filtered)
        mn_z = min(b[2] for b in filtered)
        mx_x = max(b[3] for b in filtered)
        mx_y = max(b[4] for b in filtered)
        mx_z = max(b[5] for b in filtered)
        filtered = [quantize_box_min_thick(mn_x, mn_y, mn_z, mx_x, mx_y, mx_z)]

    return [[round(v, 4) for v in b] for b in filtered]


def load_obj_tris(path: Path) -> tuple[list[tuple[float, float, float]], list[tuple[tuple[float, float, float], tuple[float, float, float], tuple[float, float, float]]]]:
    verts: list[tuple[float, float, float]] = []
    tris = []
    for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        if line.startswith("v "):
            p = line.split()
            verts.append((float(p[1]) + 0.5, float(p[2]), float(p[3]) + 0.5))
        elif line.startswith("f "):
            parts = line.split()[1:]
            idx = [int(x.split("/")[0]) - 1 for x in parts]
            for i in range(1, len(idx) - 1):
                tris.append((verts[idx[0]], verts[idx[i]], verts[idx[i + 1]]))
    return verts, tris


def fill_interior(occ: set[tuple[int, int, int]]) -> set[tuple[int, int, int]]:
    if not occ:
        return occ
    xs = [v[0] for v in occ]
    ys = [v[1] for v in occ]
    zs = [v[2] for v in occ]
    x0, x1 = min(xs) - 1, max(xs) + 1
    y0, y1 = min(ys) - 1, max(ys) + 1
    z0, z1 = min(zs) - 1, max(zs) + 1
    ext = set()
    q = deque([(x0, y0, z0)])
    ext.add((x0, y0, z0))
    while q:
        x, y, z = q.popleft()
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            nx, ny, nz = x + dx, y + dy, z + dz
            if x0 <= nx <= x1 and y0 <= ny <= y1 and z0 <= nz <= z1:
                pt = (nx, ny, nz)
                if pt not in ext and pt not in occ:
                    ext.add(pt)
                    q.append(pt)
    filled = set(occ)
    for x in range(min(xs), max(xs) + 1):
        for y in range(min(ys), max(ys) + 1):
            for z in range(min(zs), max(zs) + 1):
                if (x, y, z) not in ext:
                    filled.add((x, y, z))
    return filled


def voxelize_tris(tris, step: float) -> set[tuple[int, int, int]]:
    occ: set[tuple[int, int, int]] = set()
    for v0, v1, v2 in tris:
        l1 = math.dist(v0, v1)
        l2 = math.dist(v0, v2)
        l3 = math.dist(v1, v2)
        n = min(24, max(1, int(math.ceil(max(l1, l2, l3) / (step * 0.45)))))
        for i in range(n + 1):
            u = i / n
            for j in range(n + 1 - i):
                v = j / n
                w = 1.0 - u - v
                px = w * v0[0] + u * v1[0] + v * v2[0]
                py = w * v0[1] + u * v1[1] + v * v2[1]
                pz = w * v0[2] + u * v1[2] + v * v2[2]
                occ.add((int(math.floor(px / step)), int(math.floor(py / step)), int(math.floor(pz / step))))
    return fill_interior(occ)


def greedy_merge(occ: set[tuple[int, int, int]]) -> list[tuple[int, int, int, int, int, int]]:
    rem = set(occ)
    boxes = []
    while rem:
        x0, y0, z0 = min(rem)
        x1 = x0 + 1
        while (x1, y0, z0) in rem:
            x1 += 1
        z1 = z0 + 1
        while all((x, y0, z1) in rem for x in range(x0, x1)):
            z1 += 1
        y1 = y0 + 1
        while all((x, y1, z) in rem for x in range(x0, x1) for z in range(z0, z1)):
            y1 += 1
        for x in range(x0, x1):
            for y in range(y0, y1):
                for z in range(z0, z1):
                    rem.remove((x, y, z))
        boxes.append((x0, y0, z0, x1, y1, z1))
    return boxes


def boxes_from_obj_mesh(path: Path, width: int, height: int, depth: int) -> list[list[float]]:
    verts, tris = load_obj_tris(path)
    if not verts or not tris:
        return [[0.0, 0.0, 0.0, 1.0, 1.0, 1.0]]

    mesh_min = (
        min(v[0] for v in verts),
        min(v[1] for v in verts),
        min(v[2] for v in verts),
    )
    mesh_max = (
        max(v[0] for v in verts),
        max(v[1] for v in verts),
        max(v[2] for v in verts),
    )
    q_min_x, q_min_y, q_min_z, q_max_x, q_max_y, q_max_z = quantize_box_min_thick(
        mesh_min[0], mesh_min[1], mesh_min[2], mesh_max[0], mesh_max[1], mesh_max[2], grid=MIN_THICK
    )

    vol = width * height * depth
    if vol <= 2:
        step = 0.125
    elif vol <= 8:
        step = 0.25
    else:
        step = 0.5

    merged: list[tuple[int, int, int, int, int, int]] = []
    while True:
        occ = voxelize_tris(tris, step)
        merged = greedy_merge(occ)
        if len(merged) <= MAX_BOXES or step >= 2.0:
            break
        step *= 2.0

    result: list[list[float]] = []
    for gx0, gy0, gz0, gx1, gy1, gz1 in merged:
        bx0 = max(q_min_x, snap(gx0 * step, MIN_THICK))
        by0 = max(q_min_y, snap(gy0 * step, MIN_THICK))
        bz0 = max(q_min_z, snap(gz0 * step, MIN_THICK))
        bx1 = min(q_max_x, snap(gx1 * step, MIN_THICK))
        by1 = min(q_max_y, snap(gy1 * step, MIN_THICK))
        bz1 = min(q_max_z, snap(gz1 * step, MIN_THICK))
        if bx1 - bx0 >= MIN_THICK - 1e-6 and by1 - by0 >= MIN_THICK - 1e-6 and bz1 - bz0 >= MIN_THICK - 1e-6:
            result.append([round(bx0, 4), round(by0, 4), round(bz0, 4), round(bx1, 4), round(by1, 4), round(bz1, 4)])

    if not result or len(result) > MAX_BOXES:
        result = [[round(q_min_x, 4), round(q_min_y, 4), round(q_min_z, 4), round(q_max_x, 4), round(q_max_y, 4), round(q_max_z, 4)]]

    return result


def main() -> None:
    bp_txt = BACKROOMS_PROPS_JAVA.read_text(encoding="utf-8")
    fp_txt = FILM_PROPS_JAVA.read_text(encoding="utf-8")

    entries: list[tuple[str, str, int, int, int]] = []
    for fn in ("station", "chest", "multiProp", "blenderProp", "prop"):
        for m in re.finditer(fn + r'\("([^"]+)"(?:,\s*(\d+),\s*(\d+),\s*(\d+))?', bp_txt):
            entries.append((fn, m.group(1), int(m.group(2) or 1), int(m.group(3) or 1), int(m.group(4) or 1)))

    for m in re.finditer(r'(single|multi)\("([^"]+)"(?:,\s*(\d+),\s*(\d+),\s*(\d+))?', fp_txt):
        entries.append(("film_" + m.group(1), m.group(2), int(m.group(3) or 1), int(m.group(4) or 1), int(m.group(5) or 1)))

    assert len(entries) == 135, f"Expected 135 prop entries, found {len(entries)}"

    shapes: dict[str, list[list[float]]] = {}
    for kind, pid, w, h, d in entries:
        if kind in ("station", "chest", "prop"):
            src = ASSETS / "models/block/crafting" / f"{pid}.json"
            assert src.exists(), f"Missing JSON model: {src}"
            boxes = boxes_from_json_model(src, pid)
        elif kind in ("blenderProp", "multiProp"):
            src = ASSETS / "models/blender_props" / f"{pid}.obj"
            assert src.exists(), f"Missing OBJ model: {src}"
            boxes = boxes_from_obj_mesh(src, w, h, d)
        else:
            src = ASSETS / "models/film" / f"{pid}.obj"
            assert src.exists(), f"Missing film OBJ model: {src}"
            boxes = boxes_from_obj_mesh(src, w, h, d)

        assert 1 <= len(boxes) <= MAX_BOXES, f"{pid} produced {len(boxes)} boxes"
        shapes[pid] = boxes

    OUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    ordered = {k: shapes[k] for k in sorted(shapes.keys())}
    OUT_JSON.write_text(json.dumps(ordered, separators=(",", ":"), indent=None) + "\n", encoding="utf-8")

    counts = [len(v) for v in ordered.values()]
    print(f"Generated {len(ordered)} prop shapes -> {OUT_JSON.relative_to(ROOT)}")
    print(f"Box counts: min={min(counts)}, max={max(counts)}, avg={sum(counts)/len(counts):.2f}")
    samples = [
        "prop_wood_chair", "prop_floor_lamp", "prop_sofa", "prop_desktop_computer",
        "salvage_bench", "locked_armory_crate", "film_sofa_tan", "film_torchiere",
        "film_mannequin", "film_watercolor_print", "film_furniture_pile",
    ]
    for s in samples:
        b = ordered[s]
        mn = [min(x[i] for x in b) for i in range(3)]
        mx = [max(x[i + 3] for x in b) for i in range(3)]
        print(f"  {s:24s}: {len(b):2d} boxes, bounds ({mn[0]:.3f},{mn[1]:.3f},{mn[2]:.3f})..({mx[0]:.3f},{mx[1]:.3f},{mx[2]:.3f})")


if __name__ == "__main__":
    main()
