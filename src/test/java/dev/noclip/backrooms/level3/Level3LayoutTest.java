package dev.noclip.backrooms.level3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.level3.Level3Layout.Kind;
import dev.noclip.backrooms.level3.Level3Layout.Tile;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Level3LayoutTest {
	private static final long SEED = 12345;
	private static final int SIZE = Level3Plan.SIZE;
	private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/** Every tunnel and every hall can be walked to (picking a lock or two on the way), across region edges. */
	@Test
	void everyWalkableBlockIsReachable() {
		int min = -2 * SIZE, size = 5 * SIZE;
		Tile[][] tiles = new Tile[size][size];
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) tiles[i][j] = Level3Layout.tile(SEED, min + i, min + j);
		}
		boolean[][] seen = new boolean[size][size];
		int[] start = Level3Layout.landing(SEED, 0, 0);
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[] {start[0] - min, start[1] - min});
		seen[start[0] - min][start[1] - min] = true;
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			for (int[] step : STEPS) {
				int a = at[0] + step[0], b = at[1] + step[1];
				if (a < 0 || b < 0 || a >= size || b >= size || seen[a][b] || !walkable(tiles[a][b])) continue;
				seen[a][b] = true;
				queue.add(new int[] {a, b});
			}
		}
		for (int i = SIZE; i < 4 * SIZE; i++) {
			for (int j = SIZE; j < 4 * SIZE; j++) {
				if (walkable(tiles[i][j])) assertTrue(seen[i][j], "unreachable " + tiles[i][j].kind() + " at " + (min + i) + ", " + (min + j));
			}
		}
	}

	/** Hall floor under things you cannot walk through does not count; the rest of a hall must be reachable. */
	private static boolean walkable(Tile tile) {
		return tile.kind().walkable();
	}

	/** Every passage leaves room to stand: at least a block wide, and crouch height at the lowest. */
	@Test
	void passagesFitAPlayer() {
		for (int x = -SIZE; x < SIZE; x++) {
			for (int z = -SIZE; z < SIZE; z++) {
				Tile tile = Level3Layout.tile(SEED, x, z);
				if (tile.kind() == Kind.FLOOR || tile.kind() == Kind.HALL_GAP) {
					assertTrue(tile.clearance() >= 1 && tile.clearance() <= 4, "headroom " + tile.clearance() + " at " + x + ", " + z);
				}
				if (tile.kind() == Kind.GATE) assertTrue(tile.clearance() >= 2, "a gate under a crouch-height ceiling at " + x + ", " + z);
			}
		}
	}

	/** All three ways out turn up in a few regions, and each opens onto floor. */
	@Test
	void everyExitTurnsUp() {
		Set<Level3Exit> found = EnumSet.noneOf(Level3Exit.class);
		int span = 10 * SIZE;
		for (int x = -span; x < span; x++) {
			for (int z = -span; z < span; z++) {
				Tile tile = Level3Layout.tile(SEED, x, z);
				if (tile.kind() == Kind.EXIT) {
					found.add(tile.exit());
					Tile front = Level3Layout.tile(SEED, x + Level3Plan.DX[tile.dir()], z + Level3Plan.DZ[tile.dir()]);
					assertTrue(front.kind() == Kind.FLOOR || front.kind() == Kind.HALL, tile.exit() + " at " + x + ", " + z + " faces " + front.kind());
					if (front.kind() == Kind.FLOOR) assertTrue(front.clearance() >= 3, "an elevator in a low passage at " + x + ", " + z);
				} else if (tile.kind() == Kind.HALL && tile.hall().kind() == Level3Plan.HallKind.CAR) {
					found.add(Level3Exit.CAR);
				}
			}
		}
		assertEquals(EnumSet.allOf(Level3Exit.class), found, "missing exits");
	}

	/** Bars only ever shut off extra connections: they never stand where the maze needs a way through. */
	@Test
	void barsHaveAWayRound() {
		// Covered by reachability with bars counted as walls; here, that bars and gates exist at all.
		int bars = 0, gates = 0;
		for (int x = -SIZE; x < SIZE; x++) {
			for (int z = -SIZE; z < SIZE; z++) {
				Kind kind = Level3Layout.tile(SEED, x, z).kind();
				if (kind == Kind.BARS) bars++;
				if (kind == Kind.GATE) gates++;
			}
		}
		assertTrue(bars > 20, "hardly any bars: " + bars);
		assertTrue(gates > 2, "hardly any gates: " + gates);
	}

	/** The M.E.G.'s arrows lead to a way into Base Gamma; its signs hang on walls; warnings stand before hotspots. */
	@Test
	void signsLeadToBaseGamma() {
		for (int rx = -3; rx <= 3; rx++) {
			for (int rz = -3; rz <= 3; rz++) {
				Level3Plan plan = Level3Plan.of(SEED, rx, rz);
				// In Gamma's own region the signs lead there from anywhere in the region.
				int depth = GammaPlan.in(SEED, rx, rz) ? Level3Plan.N * Level3Plan.N : Level3Plan.SIGN_DEPTH;
				for (int c = 0; c < Level3Plan.N * Level3Plan.N; c++) {
					if (plan.baseTo[c] < 0) continue;
					int at = c, steps = 0;
					while (true) {
						int d = plan.baseTo[at];
						assertTrue(d >= 0 && steps++ <= depth, "signs lose the way in region " + rx + ", " + rz);
						if (plan.edge(at, d) == Level3Plan.Edge.HALL) break;
						at = Level3Plan.cell(at % Level3Plan.N + Level3Plan.DX[d], at / Level3Plan.N + Level3Plan.DZ[d]);
					}
				}
			}
		}
		int arrows = 0, warnings = 0;
		for (int x = -3 * SIZE; x < 3 * SIZE; x++) {
			for (int z = -3 * SIZE; z < 3 * SIZE; z++) {
				Level3Layout.Sign sign = Level3Layout.sign(SEED, x, z);
				if (sign == null) continue;
				if (sign.warning()) warnings++;
				else arrows++;
				assertEquals(Kind.FLOOR, Level3Layout.tile(SEED, x, z).kind(), "a sign off the floor at " + x + ", " + z);
				Kind behind = Level3Layout.tile(SEED, x - Level3Plan.DX[sign.facing()], z - Level3Plan.DZ[sign.facing()]).kind();
				assertTrue(behind == Kind.WALL || behind == Kind.EXIT, "a sign with no wall behind it at " + x + ", " + z + ": " + behind);
			}
		}
		assertTrue(arrows > 5 && warnings > 5, "arrows " + arrows + ", warnings " + warnings);
	}

	/** The paranoia hotspots are a minority of the level, and arriving never puts you in one. */
	@Test
	void hotspotsArePatchy() {
		int hot = 0, floor = 0;
		for (int x = -2 * SIZE; x < 2 * SIZE; x += 2) {
			for (int z = -2 * SIZE; z < 2 * SIZE; z += 2) {
				if (Level3Layout.tile(SEED, x, z).kind() != Kind.FLOOR) continue;
				floor++;
				if (Level3Layout.hotspot(SEED, x, z)) hot++;
			}
		}
		assertTrue(hot > 0 && hot < floor / 4, "hotspots " + hot + " of " + floor);
		for (int x = -400; x <= 400; x += 37) {
			int[] spot = Level3Layout.landing(SEED, x, -x);
			Tile tile = Level3Layout.tile(SEED, spot[0], spot[1]);
			assertEquals(Kind.FLOOR, tile.kind());
			assertTrue(tile.clearance() >= 3 && !Level3Layout.hotspot(SEED, spot[0], spot[1]));
		}
	}

	/** All four faction outposts (Base Gamma, B.N.T.G. Storage, Blue Salvation, Scylla Colony) turn up. */
	@Test
	void allFourFactionBasesTurnUp() {
		Set<Level3Plan.HallKind> found = EnumSet.noneOf(Level3Plan.HallKind.class);
		for (int rx = -5; rx <= 5; rx++) {
			for (int rz = -5; rz <= 5; rz++) {
				for (Level3Plan.Hall hall : Level3Plan.of(SEED, rx, rz).halls) found.add(hall.kind());
			}
		}
		assertEquals(EnumSet.allOf(Level3Plan.HallKind.class), found);
	}
}
