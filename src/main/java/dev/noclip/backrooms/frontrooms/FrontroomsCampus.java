package dev.noclip.backrooms.frontrooms;

/**
 * The research grounds around the institute (docs/project/1-gameplay/README.md, "研究所改版"), as plain numbers: a
 * forecourt with the M.E.G. emblem and flagpoles under the entrance canopy, an avenue with sodium lamps down to the
 * welded-shut gate in the quarantine wall, a car park, a power plant with its stack and two cooling towers, a water
 * tower, storage silos, a radio mast, and guard towers along the wall. A real research site of the eighties, only too
 * big and too empty. Nothing here touches Minecraft's blocks; the generator picks them.
 */
public final class FrontroomsCampus {
	/** What a block of the grounds is. */
	public enum Part {
		NONE, AIR, ASPHALT, LINE, SIDEWALK, PRECAST, RIBBON, STEEL, SIGN, BAND, CONCRETE, WATER, LIGHT, SODIUM, FLOODLIGHT,
		RED_LIGHT, BARS, POLE
	}

	private static final int G = FrontroomsTerrain.GROUND;
	/** Cooling towers: centre x, z, radius at the foot. */
	private static final int[][] TOWERS = {{-92, -84, 14}, {-126, -52, 12}};
	private static final int TOWER_HEIGHT = 50;
	private static final int[][] SILOS = {{120, -14}, {120, 2}, {120, 18}};
	private static final int MAST_X = 88, MAST_Z = 104, MAST_TOP = 166;
	private static final int WATER_X = 104, WATER_Z = -76;
	private static final int STACK_X = -102, STACK_Z = -14;
	/** Guard towers stand just inside the wall, between the main directions. */
	private static final int[][] GUARD = new int[8][];

	static {
		for (int i = 0; i < 8; i++) {
			double a = Math.toRadians(22.5 + 45 * i);
			double r = FrontroomsTerrain.WALL_IN - 4.5;
			GUARD[i] = new int[] {(int) Math.round(Math.cos(a) * r), (int) Math.round(Math.sin(a) * r)};
		}
	}

	private FrontroomsCampus() {
	}

	/** The highest block the grounds put in a column (the ground's own height when there is nothing). */
	public static int top(int x, int z) {
		if (Math.abs(x - MAST_X) <= 3 && Math.abs(z - MAST_Z) <= 3) return MAST_TOP;
		for (int[] t : TOWERS) if (Math.hypot(x - t[0], z - t[1]) <= t[2] + 1) return G + TOWER_HEIGHT;
		if (Math.hypot(x - STACK_X, z - STACK_Z) <= 3) return G + 62;
		if (Math.abs(x - WATER_X) <= 8 && Math.abs(z - WATER_Z) <= 8) return G + 47;
		for (int[] s : SILOS) if (Math.hypot(x - s[0], z - s[1]) <= 6) return G + 27;
		for (int[] g : GUARD) if (Math.abs(x - g[0]) <= 3 && Math.abs(z - g[1]) <= 3) return FrontroomsTerrain.WALL_TOP + 7;
		if (gate(x, z)) return G + 22;
		return G + 14;
	}

	/** Whether something of the grounds (not only paving) stands in or near a block area: no trees grow there. */
	public static boolean busy(int x0, int z0, int x1, int z1) {
		int pad = 6;
		if (overlaps(x0, z0, x1, z1, -30, 36, 29, 60, pad)) return true;                 // forecourt
		if (overlaps(x0, z0, x1, z1, -9, 57, 8, FrontroomsTerrain.WALL_IN, pad)) return true;  // avenue
		if (overlaps(x0, z0, x1, z1, 5, 60, 52, 93, pad)) return true;                   // car park
		if (overlaps(x0, z0, x1, z1, -123, -31, -95, -7, pad)) return true;              // power plant
		for (int[] t : TOWERS) if (overlaps(x0, z0, x1, z1, t[0] - t[2], t[1] - t[2], t[0] + t[2], t[1] + t[2], pad)) return true;
		for (int[] s : SILOS) if (overlaps(x0, z0, x1, z1, s[0] - 6, s[1] - 6, s[0] + 6, s[1] + 6, pad)) return true;
		if (overlaps(x0, z0, x1, z1, WATER_X - 8, WATER_Z - 8, WATER_X + 8, WATER_Z + 8, pad)) return true;
		if (overlaps(x0, z0, x1, z1, MAST_X - 4, MAST_Z - 4, MAST_X + 4, MAST_Z + 4, pad)) return true;
		for (int[] g : GUARD) if (overlaps(x0, z0, x1, z1, g[0] - 3, g[1] - 3, g[0] + 3, g[1] + 3, pad)) return true;
		return false;
	}

	private static boolean overlaps(int x0, int z0, int x1, int z1, int ax0, int az0, int ax1, int az1, int pad) {
		return x1 >= ax0 - pad && x0 <= ax1 + pad && z1 >= az0 - pad && z0 <= az1 + pad;
	}

	/** Whether a block is part of something built on the grounds (which nobody may break): not the paving's soil. */
	public static boolean structure(int x, int y, int z) {
		Part part = part(x, y, z);
		return part != Part.NONE && part != Part.AIR && part != Part.WATER;
	}

	/** The south gate: the stretch of the quarantine wall the avenue runs into, welded shut. */
	private static boolean gate(int x, int z) {
		return Math.abs(x) <= 9 && z > 0 && FrontroomsTerrain.wall(x, z);
	}

	/** What the grounds put at a block, or {@link Part#NONE} where it is up to the terrain (or the wall, or the building). */
	public static Part part(int x, int y, int z) {
		if (y < G) return Part.NONE;
		if (gate(x, z)) return gateBlock(x, y);
		Part part;
		if ((part = canopy(x, y, z)) != null) return part;
		if ((part = forecourt(x, y, z)) != null) return part;
		if ((part = avenue(x, y, z)) != null) return part;
		if ((part = carPark(x, y, z)) != null) return part;
		if ((part = plant(x, y, z)) != null) return part;
		for (int[] t : TOWERS) if ((part = coolingTower(t, x, y, z)) != null) return part;
		if ((part = waterTower(x, y, z)) != null) return part;
		for (int[] s : SILOS) if ((part = silo(s, x, y, z)) != null) return part;
		if ((part = mast(x, y, z)) != null) return part;
		for (int[] g : GUARD) if ((part = guardTower(g, x, y, z)) != null) return part;
		return Part.NONE;
	}

	/** The gate: steel leaves with hazard bands, as tall as two floors, a floodlight on each side. */
	private static Part gateBlock(int x, int y) {
		int h = y - G;
		if (h == 0) return Part.ASPHALT;
		if (h > 20) return Part.NONE;
		if (Math.abs(x) == 9) return h == 20 ? Part.FLOODLIGHT : Part.CONCRETE;
		if (x == 0 || x == -1) return Part.STEEL;
		return h % 5 == 0 ? Part.BAND : Part.STEEL;
	}

	/** The entrance canopy: a precast slab on two steel columns, lit underneath. */
	private static Part canopy(int x, int y, int z) {
		if (x < -8 || x > 7 || z < 36 || z > 41) return null;
		if (y == InstituteLayout.CEILING) return Math.floorMod(x, 4) == 1 && z == 39 ? Part.LIGHT : Part.PRECAST;
		if ((x == -8 || x == 7) && z == 41 && y > G && y < InstituteLayout.CEILING) return Part.STEEL;
		if (y == G) return Part.SIDEWALK;
		return null;
	}

	/** The forecourt: paving slabs, the emblem set into them, flagpoles at the far edge, lamps at the corners. */
	private static Part forecourt(int x, int y, int z) {
		if (x < -26 || x > 25 || z < 36 || z > 56) return null;
		int h = y - G;
		double r = Math.hypot(x + 0.5, z - 46.5);
		if (h == 0) {
			if (r <= 6.2) return r >= 4.6 ? Part.STEEL : r >= 1.6 && r <= 2.6 ? Part.PRECAST : Part.SIGN;
			return Part.SIDEWALK;
		}
		for (int pole : new int[] {-20, -16, 15, 19}) {
			if (x == pole && z == 54) return h <= 12 ? Part.POLE : null;
			if (z == 54 && x > pole && x <= pole + 3 && h >= 10 && h <= 12) return h == 11 ? Part.PRECAST : Part.SIGN;
		}
		for (int[] lamp : new int[][] {{-26, 36}, {25, 36}, {-26, 56}, {25, 56}}) {
			if (x == lamp[0] && z == lamp[1]) return h <= 6 ? Part.POLE : h == 7 ? Part.SODIUM : null;
		}
		return null;
	}

	/** The avenue: two lanes and a dashed line, kerbs, sodium lamps every sixteen blocks. */
	private static Part avenue(int x, int y, int z) {
		if (z < 57 || x < -8 || x > 7 || !FrontroomsTerrain.grounds(x, z)) return null;
		int h = y - G;
		if (h == 0) {
			if (x <= -6 || x >= 5) return Part.SIDEWALK;
			if ((x == -1 || x == 0) && Math.floorMod(z, 8) < 4) return Part.LINE;
			return Part.ASPHALT;
		}
		if ((x == -8 || x == 7) && Math.floorMod(z, 16) == 0) return h <= 6 ? Part.POLE : h == 7 ? Part.SODIUM : null;
		return null;
	}

	/** The car park east of the avenue, two rows of bays, and the drive into it. */
	private static Part carPark(int x, int y, int z) {
		boolean lot = x >= 12 && x <= 52 && z >= 60 && z <= 92;
		boolean drive = x >= 8 && x <= 11 && z >= 72 && z <= 77;
		if (!lot && !drive) return null;
		int h = y - G;
		if (h == 0) {
			boolean bays = z >= 61 && z <= 66 || z >= 86 && z <= 91;
			return lot && bays && Math.floorMod(x, 4) == 0 ? Part.LINE : Part.ASPHALT;
		}
		if (lot && (x == 22 || x == 42) && z == 76) return h <= 6 ? Part.POLE : h == 7 ? Part.SODIUM : null;
		return null;
	}

	/** The power plant: a long precast hall with a ribbon of windows, a stack beside it. */
	private static Part plant(int x, int y, int z) {
		int h = y - G;
		double stack = Math.hypot(x - STACK_X, z - STACK_Z);
		if (stack <= 2.6 && h >= 15 && h <= 61) {
			if (stack < 1.6) return Part.AIR;
			if (h == 61) return Part.STEEL;
			return h >= 54 && h <= 55 ? Part.BAND : Part.CONCRETE;
		}
		if (x < -122 || x > -96 || z < -30 || z > -8) return null;
		if (h > 15) return null;
		boolean wall = x == -122 || x == -96 || z == -30 || z == -8;
		if (h == 0) return Part.CONCRETE;
		if (h == 15) return Part.CONCRETE;
		if (!wall) return Part.AIR;
		int along = x == -122 || x == -96 ? z : x;
		if (h >= 10 && h <= 11 && Math.floorMod(along, 6) != 0) return Part.RIBBON;
		return Math.floorMod(along, 6) == 0 ? Part.STEEL : Part.PRECAST;
	}

	/** A cooling tower: a concrete shell, wide at the foot, narrow at the waist, a little wider at the lip. */
	private static Part coolingTower(int[] t, int x, int y, int z) {
		double d = Math.hypot(x - t[0], z - t[1]);
		if (d > t[2] + 1) return null;
		int h = y - G;
		if (h > TOWER_HEIGHT) return null;
		if (h == 0) return d < t[2] - 1 ? Part.WATER : Part.CONCRETE;
		double s = (double) h / TOWER_HEIGHT;
		double waist = t[2] * 0.64, lip = t[2] * 0.72;
		double r = s <= 0.75 ? waist + (t[2] - waist) * Math.pow((0.75 - s) / 0.75, 2) : waist + (lip - waist) * Math.pow((s - 0.75) / 0.25, 2);
		if (Math.abs(d - r) > 0.75) return d < r ? Part.AIR : null;
		if (h <= 3) {
			// The legs: the shell stands on slanted columns, open between them.
			double angle = Math.atan2(z - t[1], x - t[0]);
			return Math.floorMod((int) Math.floor((angle + Math.PI) / (2 * Math.PI) * 24), 2) == 0 ? Part.CONCRETE : Part.AIR;
		}
		return h == TOWER_HEIGHT ? Part.STEEL : Part.CONCRETE;
	}

	/** A water tower: four legs with rings of bracing, a round tank, a cone roof, a red lamp on top. */
	private static Part waterTower(int x, int y, int z) {
		int dx = x - WATER_X, dz = z - WATER_Z, h = y - G;
		if (Math.abs(dx) > 8 || Math.abs(dz) > 8 || h < 1) return null;
		boolean leg = (dx == -4 || dx == 4) && (dz == -4 || dz == 4);
		if (h <= 32) {
			if (leg) return Part.STEEL;
			boolean brace = h % 8 == 0 && (Math.abs(dx) == 4 && Math.abs(dz) <= 4 || Math.abs(dz) == 4 && Math.abs(dx) <= 4);
			return brace ? Part.STEEL : null;
		}
		double d = Math.hypot(dx, dz);
		if (h <= 42) {
			if (h == 33) return d <= 6.5 ? Part.STEEL : null;
			if (d > 6.5) return null;
			if (d < 5.6) return Part.AIR;
			return h == 37 || h == 38 ? Part.BAND : Part.CONCRETE;
		}
		if (h <= 46) {
			double r = 6.5 - (h - 42) * 1.5;
			if (d <= r && d > r - 1.2) return Part.STEEL;
			return d <= r ? Part.AIR : null;
		}
		return h == 47 && dx == 0 && dz == 0 ? Part.RED_LIGHT : null;
	}

	/** A storage silo: a concrete drum with hazard bands and a steel cap. */
	private static Part silo(int[] s, int x, int y, int z) {
		double d = Math.hypot(x - s[0], z - s[1]);
		int h = y - G;
		if (d > 5.6 || h < 1 || h > 26) return null;
		if (h >= 23) {
			double r = 5.6 - (h - 22) * 1.3;
			if (d <= r) return d > r - 1.3 ? Part.STEEL : Part.AIR;
			return null;
		}
		if (d < 4.6) return Part.AIR;
		return h == 6 || h == 20 ? Part.BAND : Part.CONCRETE;
	}

	/** The radio mast: a lattice of steel a hundred blocks tall on a small hut, red lamps on the way up. */
	private static Part mast(int x, int y, int z) {
		int dx = x - MAST_X, dz = z - MAST_Z, h = y - G;
		if (Math.abs(dx) > 3 || Math.abs(dz) > 3 || h < 1 || y > MAST_TOP) return null;
		if (h <= 4) {
			boolean wall = Math.abs(dx) == 3 || Math.abs(dz) == 3;
			if (h == 4) return Part.PRECAST;
			return wall ? (h == 2 && dz == 3 && dx == 0 ? Part.RIBBON : Part.PRECAST) : Part.AIR;
		}
		if (Math.abs(dx) > 1 || Math.abs(dz) > 1) return null;
		boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
		if (y == MAST_TOP) return dx == 0 && dz == 0 ? Part.RED_LIGHT : null;
		if (corner) return h % 32 == 0 ? Part.RED_LIGHT : h % 8 == 0 ? Part.STEEL : Part.POLE;
		if (dx == 0 && dz == 0) return null;
		return h % 8 == 0 ? Part.STEEL : null;
	}

	/** A guard tower: a concrete shaft against the wall up to its top, a glazed booth and a floodlight over it. */
	private static Part guardTower(int[] g, int x, int y, int z) {
		int dx = x - g[0], dz = z - g[1];
		if (Math.abs(dx) > 3 || Math.abs(dz) > 3 || y <= G) return null;
		int top = FrontroomsTerrain.WALL_TOP;
		boolean wall = Math.abs(dx) == 3 || Math.abs(dz) == 3;
		boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
		if (y <= top) return wall ? Part.CONCRETE : Part.AIR;
		if (y <= top + 4) {
			if (y == top + 1) return Part.CONCRETE;
			return wall ? corner ? Part.STEEL : Part.RIBBON : Part.AIR;
		}
		if (y == top + 5) return Part.CONCRETE;
		if (y == top + 6 && dx == 0 && dz == 0) return Part.FLOODLIGHT;
		return null;
	}
}
