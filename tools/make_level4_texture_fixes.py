"""Targeted Level 4 stain & repetition texture fixes (Batch 1 + Batch 2).

Replaces stamp-like damp carpet blobs, geometric drywall marks/polka-dot dents,
and symmetric carpet dents (Batch 1), plus adds aged/water-stained acoustic
ceiling variants, modular Omega Base panels without loud stripes, subtle
pillar/radiator wear variants, and phase-shifted rain windows (Batch 2), without
touching make_level4.main() (which would overwrite hand-edited POI assets).

    python tools/make_level4_texture_fixes.py
    python tools/make_level4_realistic.py --only office_damp_carpet office_drywall office_carpet_dent office_acoustic_ceiling omega_wall office_pillar office_radiator_wall
"""

import json
import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.dont_write_bytecode = True
TOOLS = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS))

import make_textures as mt  # noqa: E402
import make_level4 as l4  # noqa: E402
from make_textures import SIZE, X, Y, clouds, flat, inside, noise, paint, step, tint  # noqa: E402

ASSETS = l4.ASSETS
TEXTURES = l4.TEXTURES
MODELS = l4.MODELS
STATES = ASSETS / "blockstates"
NS = l4.NS


def base_carpet(seed):
    """Base dark blue-grey commercial short-pile carpet matching make_level4.carpet."""
    m = flat((74, 80, 88), smooth=0.05, f0=8, porosity=210)
    weave = noise(seed, 64) * 0.05 + noise(seed + 1, 128) * 0.07
    m.color *= (1 + weave)[..., None]
    m.height += noise(seed + 2, 128) * 0.35
    speck = step(0.75, 0.95, noise(seed + 3, 128))
    tint(m, speck * 0.35, (0.82, 0.85, 0.90))
    patch = step(0.1, 0.7, clouds(seed + 4, 3)) * 0.14
    tint(m, patch, (0.90, 0.92, 0.94))
    return m


def apply_damp_carpet(m, variant):
    """Apply 6 distinct, seamless/edge-flowing dampness patterns so tiled wet carpet reads continuously."""
    if variant == 0:
        # Broad seamless damp sheet
        sheet = clouds(4120, 2) * 0.65 + clouds(4121, 5) * 0.35
        mask = step(0.08, 0.58, sheet)
        tint(m, mask * 0.72, (0.73, 0.76, 0.81))
        paint(m, mask, smooth=0.45, f0=14, porosity=60)
    elif variant == 1:
        # Broad damp base + off-center pooled wet core near (44, 78) softened by clouds
        base = step(0.02, 0.55, clouds(4122, 2))
        tint(m, base * 0.45, (0.78, 0.81, 0.85))
        dist = np.hypot(X - 44, Y - 78) + clouds(4123, 4) * 26.0
        pool = (1 - step(14, 42, dist)) * inside()
        tint(m, pool * 0.80, (0.64, 0.68, 0.74))
        paint(m, np.clip(base * 0.4 + pool, 0, 1), height=-0.3, smooth=0.72, f0=18, porosity=15)
    elif variant == 2:
        # Edge-creeping moisture seepage across one side/corner so rotations merge across tiles
        flow = (1 - step(18, 96, X * 0.72 + Y * 0.42 + clouds(4124, 3) * 34.0))
        mottle = step(-0.1, 0.5, clouds(4125, 5))
        mask = np.clip(flow * (0.55 + 0.45 * mottle), 0, 1)
        tint(m, mask * 0.72, (0.68, 0.72, 0.78))
        paint(m, mask, smooth=0.58, f0=16, porosity=32)
    elif variant == 3:
        # Diffuse mottled mild dampness
        sheet = clouds(4126, 2) * 0.55 + clouds(4127, 4) * 0.45
        mask = step(-0.02, 0.52, sheet)
        tint(m, mask * 0.65, (0.77, 0.80, 0.85))
        paint(m, mask, smooth=0.36, f0=12, porosity=85)
    elif variant == 4:
        # Soft stretched damp smear / wet footpath streak (diagonal noise-warped band)
        band_dist = np.abs((X - 64) * 0.62 - (Y - 64) * 0.78 + clouds(4128, 3) * 24.0)
        band = 1 - step(10, 38, band_dist)
        feather = step(-0.15, 0.5, clouds(4129, 5))
        mask = np.clip(band * (0.5 + 0.5 * feather), 0, 1)
        tint(m, mask * 0.76, (0.67, 0.71, 0.77))
        paint(m, mask, height=-0.18, smooth=0.64, f0=16, porosity=25)
    else:
        # Subtle light-moisture sheen
        mask = step(0.0, 0.62, clouds(4130, 2) * 0.7 + noise(4131, 6) * 0.3)
        tint(m, mask * 0.55, (0.81, 0.84, 0.88))
        paint(m, mask, smooth=0.28, f0=11, porosity=110)
    return m


def base_drywall(seed, baseboard=True, roller_shift=0.0):
    """Matte cool off-white office drywall with optional subtle roller-tone shift."""
    m = flat((216, 219, 215), smooth=0.14, f0=10, porosity=45)
    grain = noise(seed, 64) * 0.018 + clouds(seed + 1, 4) * 0.025
    roller = clouds(seed + 7, 2) * 0.014 + roller_shift
    m.color *= (1 + grain + roller)[..., None]
    m.height += noise(seed + 2, 128) * 0.15
    if baseboard:
        bb = step(112, 114, Y)
        paint(m, bb, (232, 234, 232), height=0.8, smooth=0.35, porosity=15)
        groove = mt.box(-2, 112, 130, 114, 0.6)
        paint(m, groove, (172, 176, 174), height=-0.4)
    return m


def wear_drywall(seed, baseboard=True, variant=0):
    """Apply 1..9 unified subtle organic wear variants without geometric dots/bars/boxes."""
    m = base_drywall(seed, baseboard=baseboard)
    edge = inside()
    top_clear = step(4, 14, Y)
    wall_cap = (1 - step(100, 111, Y)) if baseboard else (1 - step(114, 126, Y))
    vband = top_clear * wall_cap

    if variant == 1:  # organic water stain (mid-wall on lower, higher/ceiling-seep on upper)
        cy = 58.0 if baseboard else 26.0
        cx = 58.0 if baseboard else 74.0
        wobble = clouds(seed + 10, 4) * 18.0 + noise(seed + 11, 16) * 4.5
        d = np.hypot((X - cx) * 0.85, (Y - cy) * 1.15) + wobble
        fill = (1 - step(18, 34, d)) * 0.35
        ring = (1 - step(3, 9, np.abs(d - 22))) * 0.45
        mask = np.clip(fill + ring, 0, 1) * edge * vband
        tint(m, mask * 0.55, (0.90, 0.88, 0.83))
        paint(m, mask * 0.3, smooth=0.10, porosity=70)

    elif variant == 2:  # asymmetric organic tapering trickles (no straight parallel bars)
        specs = ((29, 26, 56), (78, 34, 62), (99, 18, 40)) if baseboard else ((41, 8, 42), (86, 12, 34))
        trickles = np.zeros((SIZE, SIZE))
        for idx, (cx, y0, y1) in enumerate(specs):
            meander = clouds(seed + 12 + idx * 3, 6) * 2.8 + noise(seed + 13 + idx * 3, 24) * 1.1
            width = 1.1 + 0.5 * noise(seed + 14 + idx * 3, 12)
            line = 1 - step(width * 0.4, width * 1.3, np.abs(X - (cx + meander)))
            fade = step(y0 - 3, y0 + 5, Y) * (1 - step(y1 - 10, y1, Y))
            broken = 0.65 + 0.35 * step(-0.2, 0.4, noise(seed + 15 + idx * 3, 32))
            trickles = np.maximum(trickles, line * fade * broken)
        mask = trickles * edge * wall_cap
        tint(m, mask * 0.34, (0.83, 0.82, 0.78))

    elif variant == 3:  # organic meandering long vertical leak streak
        cx = 46.0 if baseboard else 79.0
        y0, y1 = (12, 102) if baseboard else (4, 108)
        meander = clouds(seed + 20, 4) * 4.5 + noise(seed + 21, 16) * 1.6
        w = 1.6 + 0.9 * clouds(seed + 22, 8)
        streak = 1 - step(w * 0.5, w * 1.5, np.abs(X - (cx + meander)))
        vfade = step(y0 - 4, y0 + 10, Y) * (1 - step(y1 - 16, y1, Y))
        gap = 0.5 + 0.5 * step(-0.25, 0.35, clouds(seed + 23, 10))
        mask = streak * vfade * gap * edge * wall_cap
        tint(m, mask * 0.32, (0.82, 0.81, 0.78))

    elif variant == 4:  # irregular soft-feathered smudge (light-switch height vs upper vent/dust)
        cx, cy = (56.0, 62.0) if baseboard else (72.0, 26.0)
        d = np.hypot((X - cx) * 0.78, (Y - cy) * 1.08) + clouds(seed + 30, 5) * 10.0
        sm = (1 - step(10, 24, d)) * (0.55 + 0.45 * clouds(seed + 31, 8))
        mask = np.clip(sm, 0, 1) * edge * vband
        tint(m, mask * 0.38, (0.85, 0.85, 0.83))
        paint(m, mask * 0.3, smooth=0.09)

    elif variant == 5:  # hand-troweled feathered spackle patch (organic boundary)
        cx, cy = (54.0, 56.0) if baseboard else (68.0, 64.0)
        d = np.hypot((X - cx) * 0.82, (Y - cy) * 1.05) + clouds(seed + 40, 4) * 9.5
        patch = (1 - step(13, 23, d)) * edge * vband
        paint(m, patch * 0.75, (223, 225, 221), height=0.28, smooth=0.07, porosity=65)

    elif variant == 6:  # organic oval paint blister
        cx, cy = (66.0, 52.0) if baseboard else (48.0, 58.0)
        d = np.hypot((X - cx) * 0.9, (Y - cy) * 1.2) + clouds(seed + 50, 6) * 4.0
        blister = (1 - step(7, 13, d)) * edge * vband
        rim = (1 - step(1.5, 4.0, np.abs(d - 10))) * edge * vband
        paint(m, blister, (223, 225, 221), height=0.85, smooth=0.20)
        tint(m, rim * 0.35, (0.91, 0.91, 0.89))

    elif variant == 7:  # subtle irregular chipped drywall scuff + faint hairline crack (NO black circle)
        cx, cy = (42.0, 68.0) if baseboard else (84.0, 44.0)
        chip_d = np.hypot((X - cx) * 0.95, (Y - cy) * 1.45) + noise(seed + 60, 32) * 1.8 + clouds(seed + 61, 8) * 1.6
        chip = (1 - step(2.0, 3.6, chip_d)) * edge * vband
        halo = (1 - step(3.2, 6.2, chip_d)) * (1 - chip) * edge * vband
        # Faint diagonal hairline crack ~18px long passing through the chip
        crack_pos = (X - cx) * 0.55 - (Y - cy) * 0.83 + noise(seed + 62, 48) * 1.1
        crack_len = np.hypot(X - cx, Y - cy)
        crack = (1 - step(0.45, 1.1, np.abs(crack_pos))) * (1 - step(7, 11, crack_len)) * edge * vband
        paint(m, halo * 0.45, (226, 227, 223), height=0.05, smooth=0.09, porosity=75)
        paint(m, chip * 0.82, (166, 168, 164), height=-0.75, smooth=0.04, porosity=110)
        tint(m, crack * 0.42, (0.76, 0.77, 0.75))
        paint(m, crack * 0.5, height=-0.25)

    elif variant == 8:  # mottled damp mildew (near baseboard on lower, near ceiling on upper)
        zone = (step(86, 98, Y) * (1 - step(108, 112, Y))) if baseboard else (step(0, 3, Y) * (1 - step(16, 28, Y)))
        mildew = step(0.18, 0.62, clouds(seed + 70, 5) * 0.75 + noise(seed + 71, 24) * 0.25) * zone * edge
        tint(m, mildew * 0.52, (0.78, 0.80, 0.75))
        paint(m, mildew * 0.4, smooth=0.08, porosity=85)

    elif variant == 9:  # horizontal furniture-rub scuff / faint dust flecks
        if baseboard:
            rub_y = 1 - step(4, 12, np.abs(Y - (86 + clouds(seed + 80, 4) * 5.0)))
            rub_x = step(14, 28, X) * (1 - step(98, 112, X))
            rub = rub_y * rub_x * (0.45 + 0.55 * step(-0.2, 0.5, noise(seed + 81, 32)))
            tint(m, rub * 0.36, (0.83, 0.83, 0.82))
        else:
            fleck = step(0.78, 0.94, noise(seed + 82, 48)) * edge * vband
            tint(m, fleck * 0.32, (0.84, 0.84, 0.83))
    return m


def carpet_dent(seed, variant):
    """Subtle, asymmetric office chair / furniture compression wear (replaces dice-dot circles)."""
    m = base_carpet(seed)
    edge = inside()
    if variant == 1:
        # Curved swivel-chair caster wear arc
        r = np.hypot(X - 58, Y - 70) + clouds(seed + 20, 6) * 3.0
        arc = (1 - step(3.5, 9.5, np.abs(r - 28))) * step(18, 34, X) * (1 - step(84, 102, X)) * (1 - step(68, 92, Y))
        mask = arc * edge * 0.65
    elif variant == 2:
        # Two soft asymmetric furniture-leg dents + faint crushed nap smear
        d1 = 1 - step(5, 13, np.hypot((X - 38) * 0.9, (Y - 46) * 1.1) + clouds(seed + 21, 6) * 2.5)
        d2 = 1 - step(6, 15, np.hypot((X - 82) * 1.1, (Y - 78) * 0.85) + clouds(seed + 22, 6) * 3.0)
        mask = np.clip(np.maximum(d1, d2 * 0.85), 0, 1) * edge * 0.6
    elif variant == 3:
        # Oblique dragged-furniture / traffic crease
        band = 1 - step(4, 14, np.abs((X - 64) * 0.72 + (Y - 64) * 0.69 + clouds(seed + 23, 5) * 6.0))
        reach = 1 - step(24, 44, np.hypot(X - 64, Y - 64))
        mask = band * reach * edge * 0.58
    else:
        # Broad subtle flattened-pile chair zone
        zone = (1 - step(18, 40, np.hypot((X - 68) * 0.85, (Y - 60) * 1.1) + clouds(seed + 24, 4) * 9.0))
        mask = zone * edge * 0.45

    tint(m, mask, (0.84, 0.86, 0.89))
    paint(m, mask, height=-0.55, smooth=0.04)
    return m


def acoustic_ceiling_base(seed):
    """4-tile suspended office acoustic ceiling grid + panel interior mask."""
    m = flat((194, 198, 195), smooth=0.10, f0=10, porosity=90)
    stip = step(0.55, 0.9, noise(seed, 128))
    tint(m, stip * 0.35, (0.82, 0.84, 0.82))
    m.height -= stip * 0.4
    gx = np.minimum(X % 64, 64 - X % 64)
    gy = np.minimum(Y % 64, 64 - Y % 64)
    grid = np.maximum(1 - step(1.2, 2.2, gx), 1 - step(1.2, 2.2, gy))
    paint(m, grid, (218, 222, 220), height=0.7, smooth=0.45, f0=40, porosity=10)
    patch = step(0.2, 0.7, clouds(seed + 3, 4)) * 0.12
    tint(m, patch, (0.90, 0.89, 0.86))
    panel_mask = 1.0 - np.clip(grid, 0.0, 1.0)
    return m, panel_mask


def acoustic_ceiling_variant(seed, variant):
    """Aged, yellowed, corner-damp, replacement-mismatch, and tea-ring leak ceiling variants."""
    m, panel_mask = acoustic_ceiling_base(seed)
    quad_tl = (1 - step(60, 63, X)) * (1 - step(60, 63, Y)) * panel_mask
    quad_br = step(65, 68, X) * step(65, 68, Y) * panel_mask

    if variant == 3:  # 1 quadrant lightly yellowed / aged
        mottle = 0.55 + 0.45 * step(-0.2, 0.6, clouds(seed + 10, 4))
        tint(m, quad_tl * mottle, (0.94, 0.90, 0.80))
    elif variant == 4:  # Classic ceiling leak tea-ring water stain
        wobble = clouds(seed + 11, 5) * 9.0 + noise(seed + 12, 24) * 2.5
        d = np.hypot(X - 34, Y - 38) + wobble
        inner = (1 - step(12, 22, d)) * 0.55
        ring = (1 - step(2.0, 5.5, np.abs(d - 18))) * 0.75
        spill = np.clip(inner + ring, 0, 1) * panel_mask * (1 - step(70, 88, X)) * (1 - step(70, 88, Y))
        tint(m, spill, (0.84, 0.78, 0.66))
        paint(m, spill * 0.3, smooth=0.07, porosity=120)
    elif variant == 5:  # Subtle cool-grey/olive damp shadow in corner of 1 quadrant
        corner_d = np.hypot(X - 10, Y - 12) + clouds(seed + 13, 5) * 8.0
        damp_corner = (1 - step(18, 34, corner_d)) * quad_tl
        tint(m, damp_corner * 0.65, (0.84, 0.86, 0.80))
        paint(m, damp_corner * 0.35, smooth=0.14, porosity=110)
    else:  # Variant 6: subtle replacement tone mismatch (1 brighter, 1 dusty)
        paint(m, quad_tl * 0.65, (205, 208, 206), smooth=0.12)
        tint(m, quad_br * 0.7, (0.93, 0.93, 0.91))
    return m


def omega_wall_panel(seed, variant):
    """Modular M.E.G. bulkhead panels without loud wall-to-wall stripes."""
    m = flat((156, 162, 170), smooth=0.30, f0=85, porosity=20)
    # Vertical brushed steel grain + subtle rolling mottling
    grain = noise(seed, 96, 16) * 0.025 + clouds(seed + 1, 4) * 0.022
    m.color *= (1 + grain)[..., None]
    m.height += noise(seed + 2, 96, 16) * 0.18

    # Vertical panel seam at X=0,64 & subtle horizontal top/bottom flange seam
    rivet_x = np.minimum(X % 64, 64 - X % 64)
    edge_y = np.minimum(Y, SIZE - Y)
    seam = np.maximum(1 - step(0.8, 1.8, rivet_x), 1 - step(0.8, 1.8, edge_y))
    paint(m, seam, (108, 114, 122), height=-0.7, smooth=0.22, f0=60)

    # Subtle corner rivets on each 64x128 panel
    for rx in (6, 58, 70, 122):
        for ry in (7, 121):
            rivet = 1 - step(1.0, 2.0, np.hypot(X - rx, Y - ry))
            paint(m, rivet * 0.7, (176, 182, 188), height=0.5, smooth=0.45, f0=110)

    if variant == 1:  # Weathered / scuffed panel
        seam_grime = (1 - step(4, 16, edge_y)) * step(-0.1, 0.6, clouds(seed + 10, 5))
        scuff = step(0.32, 0.72, noise(seed + 11, 24, 8)) * inside()
        tint(m, seam_grime * 0.45, (0.86, 0.87, 0.89))
        tint(m, scuff * 0.35, (0.88, 0.89, 0.90))
        paint(m, seam_grime * 0.25, smooth=0.18, porosity=40)
    elif variant == 2:  # Recessed service access plate & neutral stencil bar
        plate = mt.box(22, 34, 106, 94, 0.8)
        rim = mt.box(20, 32, 108, 96, 0.8) - plate
        paint(m, plate * 0.45, (146, 152, 160), height=-0.35, smooth=0.32)
        paint(m, rim * 0.8, (104, 110, 118), height=-0.65)
        bar = mt.box(28, 42, 68, 46, 0.4)
        tint(m, bar * 0.5, (0.76, 0.79, 0.84))
    elif variant == 3:  # Muted desaturated M.E.G. steel-blue accent band (sparse variant)
        band = mt.box(-2, 50, 130, 57, 0.5)
        wear = 0.78 + 0.22 * step(-0.2, 0.4, clouds(seed + 15, 6))
        paint(m, band * wear, (62, 94, 132), height=0.22, smooth=0.42, f0=60)
    return m


def pillar_variant(seed, baseboard=True, variant=1):
    """Subtle wear variants for office structural pillars."""
    m = base_drywall(seed + 30, baseboard=baseboard, roller_shift=(-0.010 if variant == 1 else 0.009))
    edge = np.maximum(1 - step(1, 3, X), 1 - step(1, 3, SIZE - 1 - X))
    tint(m, edge * 0.45, (0.86, 0.88, 0.86))
    paint(m, edge, height=0.4)

    if baseboard:
        if variant == 1:
            # Faint corner rubbing & mid-lower wear along left/right bevels
            side_wear = np.maximum(1 - step(6, 22, X), 1 - step(6, 22, SIZE - X))
            vzone = step(56, 70, Y) * (1 - step(104, 112, Y))
            tint(m, side_wear * vzone * step(-0.1, 0.6, clouds(seed + 40, 5)) * 0.35, (0.85, 0.86, 0.84))
        else:
            # Subtle mop / shoe wear just above baseboard
            mop = step(94, 102, Y) * (1 - step(109, 112, Y)) * step(0.0, 0.65, clouds(seed + 41, 6))
            tint(m, mop * 0.34, (0.84, 0.85, 0.82))
    else:
        if variant == 1:
            # Very faint dust smudge near ceiling top
            dust = (1 - step(14, 42, Y)) * step(0.05, 0.65, clouds(seed + 42, 5)) * inside()
            tint(m, dust * 0.32, (0.88, 0.88, 0.86))
        else:
            # Very faint vertical edge paint scuff
            side_wear = np.maximum(1 - step(5, 18, X), 1 - step(5, 18, SIZE - X))
            tint(m, side_wear * step(0.1, 0.7, clouds(seed + 43, 6)) * 0.30, (0.88, 0.88, 0.86))
    return m


def radiator_variant(seed, variant):
    """Subtle convection-dust and aged-casing variants of office_radiator_wall."""
    m = l4.radiator_wall(seed)
    if variant == 1:
        # Warm rising convection dust plume on drywall above radiator top lip (Y=4..20)
        plume = step(3, 8, Y) * (1 - step(17, 23, Y)) * mt.box(14, 0, 114, 24, 4)
        mottle = step(-0.1, 0.55, clouds(seed + 20, 6))
        tint(m, plume * mottle * 0.42, (0.84, 0.83, 0.79))
        # Subtle shading variation across slats
        slat_var = mt.box(18, 28, 110, 96, 1) * step(0.15, 0.7, clouds(seed + 21, 5))
        tint(m, slat_var * 0.25, (0.89, 0.90, 0.90))
    else:
        # Lightly aged ivory radiator casing + faint dust/rust specks near bottom lip
        casing = mt.box(14, 22, 114, 102, 1.5)
        tint(m, casing * 0.38, (0.95, 0.93, 0.88))
        specks = step(0.68, 0.92, noise(seed + 22, 48)) * mt.box(16, 90, 112, 108, 2)
        tint(m, specks * 0.55, (0.72, 0.66, 0.58))
    return m


def generate_rain_window_variants():
    """Create 2 phase-shifted & spatially varied animated rain-window variants (128x4096 RGBA)."""
    frames = 32
    fsize = SIZE
    src_base = Image.open(TEXTURES / "office_rain_window.png").convert("RGBA")
    src_n = Image.open(TEXTURES / "office_rain_window_n.png").convert("RGBA")
    src_s = Image.open(TEXTURES / "office_rain_window_s.png").convert("RGBA")
    mcmeta = (TEXTURES / "office_rain_window.png.mcmeta").read_text(encoding="utf8")

    arr_c = np.asarray(src_base, dtype=np.uint8).reshape(frames, fsize, fsize, 4)
    arr_n = np.asarray(src_n, dtype=np.uint8).reshape(frames, fsize, fsize, 4)
    arr_s = np.asarray(src_s, dtype=np.uint8).reshape(frames, fsize, fsize, 4)

    # Variant 1: +11 frame phase shift + horizontal mirror on glass interior (5:123), flip normal X
    v1_c = np.roll(arr_c, -11, axis=0).copy()
    v1_n = np.roll(arr_n, -11, axis=0).copy()
    v1_s = np.roll(arr_s, -11, axis=0).copy()
    v1_c[:, 5:123, 5:123, :] = v1_c[:, 5:123, 122:4:-1, :]
    v1_s[:, 5:123, 5:123, :] = v1_s[:, 5:123, 122:4:-1, :]
    v1_n[:, 5:123, 5:123, :] = v1_n[:, 5:123, 122:4:-1, :]
    v1_n[:, 5:123, 5:123, 0] = 255 - v1_n[:, 5:123, 5:123, 0]

    # Variant 2: +21 frame phase shift + horizontal roll (+44px) on glass interior (5:123)
    v2_c = np.roll(arr_c, -21, axis=0).copy()
    v2_n = np.roll(arr_n, -21, axis=0).copy()
    v2_s = np.roll(arr_s, -21, axis=0).copy()
    v2_c[:, 5:123, 5:123, :] = np.roll(v2_c[:, 5:123, 5:123, :], 44, axis=2)
    v2_n[:, 5:123, 5:123, :] = np.roll(v2_n[:, 5:123, 5:123, :], 44, axis=2)
    v2_s[:, 5:123, 5:123, :] = np.roll(v2_s[:, 5:123, 5:123, :], 44, axis=2)

    for suffix_tag, (c_arr, n_arr, s_arr) in (
        ("office_rain_window_1", (v1_c, v1_n, v1_s)),
        ("office_rain_window_2", (v2_c, v2_n, v2_s)),
    ):
        for map_suf, data in (("", c_arr), ("_n", n_arr), ("_s", s_arr)):
            Image.fromarray(data.reshape(frames * fsize, fsize, 4), "RGBA").save(
                TEXTURES / f"{suffix_tag}{map_suf}.png", optimize=True
            )
        (TEXTURES / f"{suffix_tag}.png.mcmeta").write_text(mcmeta, encoding="utf8")


def main():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    mt.OUT = TEXTURES

    textures = {}
    # Batch 1.1: Damp carpet (6 natural variants)
    for k in range(6):
        name = "office_damp_carpet" if k == 0 else f"office_damp_carpet_{k}"
        textures[name] = apply_damp_carpet(base_carpet(4120 + k * 7), k)

    # Batch 1.2: Drywall subtle clean tones + 1..9 organic wear variants (lower + upper)
    for prefix, seed_base, bb in (("office_drywall", 4200, True), ("office_drywall_upper", 4210, False)):
        textures[f"{prefix}_clean_1"] = base_drywall(seed_base + 50, baseboard=bb, roller_shift=-0.012)
        textures[f"{prefix}_clean_2"] = base_drywall(seed_base + 55, baseboard=bb, roller_shift=0.010)
        for v in range(1, 10):
            textures[f"{prefix}_{v}"] = wear_drywall(seed_base + v, baseboard=bb, variant=v)

    # Batch 1.3: Carpet dent (4 natural variants)
    for k in range(1, 5):
        textures[f"office_carpet_dent_{k}"] = carpet_dent(4110 + k, k)

    # Batch 2.1: Acoustic ceiling aged/water-stained variants (3..6)
    for k in (3, 4, 5, 6):
        textures[f"office_acoustic_ceiling_{k}"] = acoustic_ceiling_variant(4300 + k * 5, k)

    # Batch 2.2: Modular Omega bulkhead wall (base without loud blue stripe + 3 variants)
    for k in range(4):
        name = "omega_wall" if k == 0 else f"omega_wall_{k}"
        textures[name] = omega_wall_panel(4400 + k * 9, k)

    # Batch 2.3: Pillars subtle wear variants (lower + upper)
    for k in (1, 2):
        textures[f"office_pillar_{k}"] = pillar_variant(4220 + k * 5, baseboard=True, variant=k)
        textures[f"office_pillar_upper_{k}"] = pillar_variant(4240 + k * 5, baseboard=False, variant=k)

    # Batch 2.4: Radiator wall weathered variants
    for k in (1, 2):
        textures[f"office_radiator_wall_{k}"] = radiator_variant(4230 + k * 7, k)

    for name, material in textures.items():
        mt.save(name, material)

    # Batch 2.5: Rain window animated phase-shifted variants
    generate_rain_window_variants()

    # Write cube_all models for new variants (keep existing drywall, carpet_dent, omega_wall.json intact)
    new_cube_models = (
        ["office_damp_carpet"]
        + [f"office_damp_carpet_{k}" for k in range(1, 6)]
        + ["office_drywall_clean_1", "office_drywall_clean_2",
           "office_drywall_upper_clean_1", "office_drywall_upper_clean_2"]
        + [f"office_acoustic_ceiling_{k}" for k in (3, 4, 5, 6)]
        + ["omega_wall_1", "omega_wall_2", "omega_wall_3",
           "office_pillar_1", "office_pillar_2",
           "office_pillar_upper_1", "office_pillar_upper_2",
           "office_rain_window_1", "office_rain_window_2"]
    )
    for mname in new_cube_models:
        l4.write_json(MODELS / f"{mname}.json", l4.cube(mname))

    # Write orientable models for radiator wall variants
    for k in (1, 2):
        l4.write_json(MODELS / f"office_radiator_wall_{k}.json", {
            "parent": "minecraft:block/orientable",
            "textures": {
                "front": NS + f"office_radiator_wall_{k}",
                "side": NS + "office_drywall",
                "top": NS + "office_drywall",
            }
        })

    # Update Batch 1 blockstates
    damp_weights = [(0, 3), (1, 2), (2, 2), (3, 3), (4, 2), (5, 2)]
    damp_entries = []
    for idx, w in damp_weights:
        mname = "office_damp_carpet" if idx == 0 else f"office_damp_carpet_{idx}"
        for y in (0, 90, 180, 270):
            e = {"model": NS + mname, "weight": w}
            if y:
                e["y"] = y
            damp_entries.append(e)
    l4.write_json(STATES / "office_damp_carpet.json", {"variants": {"": damp_entries}})

    wear_weights = [(1, 2), (2, 2), (3, 1), (4, 2), (5, 2), (6, 1), (7, 1), (8, 1), (9, 2)]
    for prefix in ("office_drywall", "office_drywall_upper"):
        entries = [
            {"model": NS + prefix, "weight": 28},
            {"model": NS + f"{prefix}_clean_1", "weight": 16},
            {"model": NS + f"{prefix}_clean_2", "weight": 16},
        ] + [{"model": NS + f"{prefix}_{v}", "weight": w} for v, w in wear_weights]
        l4.write_json(STATES / f"{prefix}.json", {"variants": {"": entries}})

    # Update Batch 2 blockstates
    ceil_weights = [
        ("office_acoustic_ceiling", 10),
        ("office_acoustic_ceiling_1", 10),
        ("office_acoustic_ceiling_2", 10),
        ("office_acoustic_ceiling_6", 4),
        ("office_acoustic_ceiling_3", 2),
        ("office_acoustic_ceiling_4", 2),
        ("office_acoustic_ceiling_5", 1),
    ]
    ceil_entries = []
    for mname, w in ceil_weights:
        for y in (0, 90, 180, 270):
            e = {"model": NS + mname, "weight": w}
            if y:
                e["y"] = y
            ceil_entries.append(e)
    l4.write_json(STATES / "office_acoustic_ceiling.json", {"variants": {"": ceil_entries}})

    l4.write_json(STATES / "omega_wall.json", {"variants": {"": [
        {"model": NS + "omega_wall", "weight": 24},
        {"model": NS + "omega_wall_1", "weight": 10},
        {"model": NS + "omega_wall_2", "weight": 6},
        {"model": NS + "omega_wall_3", "weight": 6},
    ]}})

    for pbase in ("office_pillar", "office_pillar_upper"):
        l4.write_json(STATES / f"{pbase}.json", {"variants": {"": [
            {"model": NS + pbase, "weight": 10},
            {"model": NS + f"{pbase}_1", "weight": 3},
            {"model": NS + f"{pbase}_2", "weight": 2},
        ]}})

    rad_variants = {}
    for f, y in l4.FACING_Y.items():
        rad_variants[f"facing={f}"] = [
            {"model": NS + mname, **({"y": y} if y else {}), "weight": w}
            for mname, w in (("office_radiator_wall", 8), ("office_radiator_wall_1", 3), ("office_radiator_wall_2", 2))
        ]
    l4.write_json(STATES / "office_radiator_wall.json", {"variants": rad_variants})

    l4.write_json(STATES / "office_rain_window.json", {"variants": {"": [
        {"model": NS + "office_rain_window"},
        {"model": NS + "office_rain_window_1"},
        {"model": NS + "office_rain_window_2"},
    ]}})

    # Verify every referenced model and texture (+ _n/_s)
    checked_states = (
        "office_damp_carpet", "office_drywall", "office_drywall_upper", "office_carpet_dent",
        "office_acoustic_ceiling", "omega_wall", "office_pillar", "office_pillar_upper",
        "office_radiator_wall", "office_rain_window",
    )
    prefix = "noclip_backrooms:block/"
    for bs_name in checked_states:
        state = json.loads((STATES / f"{bs_name}.json").read_text(encoding="utf8"))
        for entry in state["variants"].values():
            for v in (entry if isinstance(entry, list) else [entry]):
                mpath = ASSETS / f"models/block/{v['model'][len(prefix):]}.json"
                assert mpath.is_file(), f"Missing model: {mpath}"
                mdata = json.loads(mpath.read_text(encoding="utf8"))
                for tval in mdata.get("textures", {}).values():
                    if tval.startswith(NS):
                        stem = tval[len(NS):]
                        for suf in ("", "_n", "_s"):
                            tpath = TEXTURES / f"{stem}{suf}.png"
                            assert tpath.is_file(), f"Missing texture: {tpath}"
                        if stem.startswith("office_rain_window"):
                            assert (TEXTURES / f"{stem}.png.mcmeta").is_file(), f"Missing mcmeta: {stem}"

    print(f"Level 4 texture fixes: {len(textures)} static + 2 animated textures, {len(new_cube_models) + 2} models, {len(checked_states)} blockstates verified.")


if __name__ == "__main__":
    main()
