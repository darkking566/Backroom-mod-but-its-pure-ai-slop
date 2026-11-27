package dev.noclip.backrooms.level3;

import static dev.noclip.backrooms.level3.Level3Plan.CELL;
import static dev.noclip.backrooms.level3.Level3Plan.N;
import static dev.noclip.backrooms.level3.Level3Plan.SIZE;

/**
 * Where Base Gamma itself stands (Wikidot: "the largest known man-made structure"): one region in every 4x4 block of
 * regions, picked by the seed, holds it in the middle of its maze. It takes 10x8 cells there (60x48 blocks: the base,
 * and its storage facility on the east side), with one way in, from the maze cell north of it.
 */
final class GammaPlan {
	/** Regions per side of the block of regions that holds one Gamma. */
	static final int SPREAD = 4;
	/** The cells Gamma takes in its region. */
	static final int I = 3, J = 4, W = 10, H = 8;
	/** The maze cell in front of the gate, on Gamma's north side; it is always a wide passage. */
	static final int FRONT_I = 7, FRONT_J = J - 1;
	/** The three locked side-door approaches (west, east, south); each maze cell in front is kept wide. */
	static final int SIDE_WEST_I = I - 1, SIDE_WEST_J = J + 2;
	static final int SIDE_EAST_I = I + W, SIDE_EAST_J = J + 2;
	static final int SIDE_SOUTH_I = FRONT_I, SIDE_SOUTH_J = J + H;
	private static final int SALT = 309;

	private GammaPlan() {
	}

	/** Whether this region holds Base Gamma. A pure hash: regions without it are planned exactly as before. */
	static boolean in(long seed, int rx, int rz) {
		int bx = Math.floorDiv(rx, SPREAD), bz = Math.floorDiv(rz, SPREAD);
		int pick = Math.floorMod(Level3Layout.hash(seed, bx, bz, SALT), SPREAD * SPREAD);
		return Math.floorMod(rx, SPREAD) == pick % SPREAD && Math.floorMod(rz, SPREAD) == pick / SPREAD;
	}

	/** Whether a maze cell (in cells over the whole level) is the one in front of a Gamma's gate. */
	static boolean front(long seed, int gi, int gj) {
		return Math.floorMod(gi, N) == FRONT_I && Math.floorMod(gj, N) == FRONT_J && in(seed, Math.floorDiv(gi, N), Math.floorDiv(gj, N));
	}

	/** Whether a maze cell is immediately outside one of Gamma's three locked side doors. */
	static boolean sideFront(long seed, int gi, int gj) {
		int i = Math.floorMod(gi, N), j = Math.floorMod(gj, N);
		boolean match = (i == SIDE_WEST_I && j == SIDE_WEST_J)
				|| (i == SIDE_EAST_I && j == SIDE_EAST_J)
				|| (i == SIDE_SOUTH_I && j == SIDE_SOUTH_J);
		return match && in(seed, Math.floorDiv(gi, N), Math.floorDiv(gj, N));
	}

	/** The world x, z of the north-west corner of the Gamma in a region that holds one. */
	static int x0(int rx) {
		return rx * SIZE + I * CELL;
	}

	static int z0(int rz) {
		return rz * SIZE + J * CELL;
	}

	/** The world x, z of the gate of the nearest Gamma to (x, z), or null if none is within reach. */
	static int[] nearestGate(long seed, int x, int z) {
		int rx0 = Math.floorDiv(x, SIZE), rz0 = Math.floorDiv(z, SIZE);
		int[] best = null;
		long bestDistance = Long.MAX_VALUE;
		int reach = 2 * SPREAD;
		for (int rx = rx0 - reach; rx <= rx0 + reach; rx++) {
			for (int rz = rz0 - reach; rz <= rz0 + reach; rz++) {
				if (!in(seed, rx, rz)) continue;
				int gx = x0(rx) + GammaLayout.GATE_U, gz = z0(rz);
				long distance = (long) (gx - x) * (gx - x) + (long) (gz - z) * (gz - z);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = new int[] {gx, gz};
				}
			}
		}
		return best;
	}
}
