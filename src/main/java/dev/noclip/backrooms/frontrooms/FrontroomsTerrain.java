package dev.noclip.backrooms.frontrooms;

/**
 * The ground of the Frontrooms, as plain numbers: gentle hills and a few ponds inside a round quarantine wall, level
 * with the institute's ground floor around the building. Past the wall there is nothing.
 */
public final class FrontroomsTerrain {
	/** The grass around the institute is level with its ground floor. */
	public static final int GROUND = InstituteLayout.FLOOR;
	/** Ponds fill up to here. */
	public static final int WATER = GROUND - 2;
	/** The quarantine wall stands between these distances from the middle; nothing is past it. */
	public static final int WALL_IN = 100, WALL_OUT = 103;
	/** The wall's top course; the fence and floodlights stand on it. */
	public static final int WALL_TOP = 100;
	/** How far around the building the ground is kept flat, and how far it takes to rise into the hills. */
	private static final int FLAT = 8, BLEND = 20;

	private FrontroomsTerrain() {
	}

	/** Distance of a block column's middle from the middle of the grounds. */
	public static double radius(int x, int z) {
		return Math.hypot(x + 0.5, z + 0.5);
	}

	public static boolean wall(int x, int z) {
		double r = radius(x, z);
		return r >= WALL_IN && r < WALL_OUT + 1;
	}

	public static boolean beyond(int x, int z) {
		return radius(x, z) >= WALL_OUT + 1;
	}

	/** Whether a block column is part of the grounds someone can walk on (inside the wall). */
	public static boolean grounds(int x, int z) {
		return radius(x, z) < WALL_IN;
	}

	/** The top block of the ground (grass, sand, or a pond's bed). */
	public static int height(long seed, int x, int z) {
		double n = 4.6 * noise(seed, x / 52.0, z / 52.0) + 1.8 * noise(seed ^ 0x5DEECE66DL, x / 17.0, z / 17.0);
		int dx = Math.max(Math.max(InstituteLayout.MIN_X - FLAT - x, x - InstituteLayout.MAX_X - FLAT), 0);
		int dz = Math.max(Math.max(InstituteLayout.MIN_Z - FLAT - z, z - InstituteLayout.YARD_MAX_Z - FLAT), 0);
		double d = Math.hypot(dx, dz);
		double t = Math.clamp(d / BLEND, 0.0, 1.0);
		t = t * t * (3 - 2 * t);
		return GROUND + (int) Math.round(n * t);
	}

	/** Smooth value noise in -1..1. */
	static double noise(long seed, double x, double z) {
		int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
		double tx = x - x0, tz = z - z0;
		tx = tx * tx * (3 - 2 * tx);
		tz = tz * tz * (3 - 2 * tz);
		double a = lattice(seed, x0, z0), b = lattice(seed, x0 + 1, z0), c = lattice(seed, x0, z0 + 1), d = lattice(seed, x0 + 1, z0 + 1);
		return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
	}

	private static double lattice(long seed, int x, int z) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		h ^= h >>> 31;
		return (h >>> 11) * 0x1.0p-53 * 2 - 1;
	}

	/** A number from 0 to {@code bound - 1} for a block column, the same every time. */
	static int hash(int x, int z, int bound) {
		long h = x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL;
		h = (h ^ (h >>> 29)) * 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (int) Math.floorMod(h, (long) bound);
	}
}
