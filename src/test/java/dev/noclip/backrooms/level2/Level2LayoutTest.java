package dev.noclip.backrooms.level2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.level2.Level2Layout.Kind;
import dev.noclip.backrooms.level2.Level2Layout.Tile;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Level2LayoutTest {
	private static final long SEED = 12345;
	private static final int SIZE = Level2Plan.SIZE;
	private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/** Every tunnel and every room with an unlocked door can be walked to, across region edges. */
	@Test
	void everyWalkableBlockIsReachable() {
		// Search a 5x5-region window and check the middle 3x3 regions, so nothing depends on the window's edge.
		int min = -2 * SIZE, size = 5 * SIZE;
		Kind[][] kinds = new Kind[size][size];
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) kinds[i][j] = Level2Layout.tile(SEED, min + i, min + j).kind();
		}
		boolean[][] seen = new boolean[size][size];
		int[] start = Level2Layout.landing(SEED, 0, 0);
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[] {start[0] - min, start[1] - min});
		seen[start[0] - min][start[1] - min] = true;
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			for (int[] step : STEPS) {
				int a = at[0] + step[0], b = at[1] + step[1];
				if (a < 0 || b < 0 || a >= size || b >= size || seen[a][b] || !kinds[a][b].walkable()) continue;
				seen[a][b] = true;
				queue.add(new int[] {a, b});
			}
		}
		int lockedRooms = 0;
		for (int i = SIZE; i < 4 * SIZE; i++) {
			for (int j = SIZE; j < 4 * SIZE; j++) {
				Kind kind = kinds[i][j];
				if (kind == Kind.FLOOR || kind == Kind.DOOR) {
					assertTrue(seen[i][j], "unreachable tunnel at " + (min + i) + ", " + (min + j));
				} else if ((kind == Kind.ROOM || kind == Kind.VOID) && !seen[i][j]) {
					lockedRooms++;
				}
			}
		}
		// Rooms behind nothing but locked doors exist; they must not be most of them.
		assertTrue(lockedRooms < 9 * SIZE * SIZE / 20, "too much of the level is shut away: " + lockedRooms);
	}

	/** Two of the three blocks across a straight tunnel stay clear of pipes. */
	@Test
	void tunnelsStayWalkable() {
		for (int x = -SIZE; x < SIZE; x++) {
			for (int z = -SIZE; z < SIZE; z++) {
				if (Level2Layout.tile(SEED, x, z).kind() != Kind.BANK) continue;
				Tile tile = Level2Layout.tile(SEED, x, z);
				// Out from a bank block, the way it faces, there are two blocks of floor.
				int clear = 0;
				for (int k = 1; k <= 2; k++) {
					Kind out = Level2Layout.tile(SEED, x + Level2Plan.DX[tile.dir()] * k, z + Level2Plan.DZ[tile.dir()] * k).kind();
					if (out == Kind.FLOOR) clear++;
				}
				assertTrue(clear >= 2, "pipes block the tunnel at " + x + ", " + z);
			}
		}
	}

	/** Every kind of exit turns up within a few regions, and each one opens onto the tunnel it faces. */
	@Test
	void everyExitTurnsUpFacingATunnel() {
		Set<Level2Exit> found = EnumSet.noneOf(Level2Exit.class);
		int span = 10 * SIZE;
		for (int x = -span; x < span; x++) {
			for (int z = -span; z < span; z++) {
				Tile tile = Level2Layout.tile(SEED, x, z);
				if (tile.kind() != Kind.EXIT) continue;
				found.add(tile.exit());
				int fx = x + Level2Plan.DX[tile.dir()], fz = z + Level2Plan.DZ[tile.dir()];
				assertEquals(Kind.FLOOR, Level2Layout.tile(SEED, fx, fz).kind(), "exit " + tile.exit() + " at " + x + ", " + z + " faces a wall");
				if (tile.exit().place == Level2Exit.Place.HOT) assertTrue(Level2Layout.hot(SEED, x, z), "boiler hatch outside the heat");
			}
		}
		assertEquals(EnumSet.allOf(Level2Exit.class), found, "missing exits");
	}

	/** Lower levels are rarer: in a big area, the way back up to Level 1 is the rarest door of all. */
	@Test
	void lowerLevelsAreHarderToFind() {
		int[] counts = new int[Level2Exit.values().length];
		int span = 12 * SIZE;
		for (int x = -span; x < span; x++) {
			for (int z = -span; z < span; z++) {
				Tile tile = Level2Layout.tile(SEED, x, z);
				if (tile.kind() == Kind.EXIT) counts[tile.exit().ordinal()]++;
			}
		}
		int up = counts[Level2Exit.MARKED_1.ordinal()];
		assertTrue(up > 0, "no way back up at all");
		for (Level2Exit exit : Level2Exit.values()) {
			if (exit.place == Level2Exit.Place.WALL && exit.level.length() >= 3) {
				assertTrue(counts[exit.ordinal()] > up, exit + " is rarer than the way up to Level 1");
			}
		}
	}

	/** Doors, windows and machines face open floor, and room doors have a gap behind them. */
	@Test
	void doorsAndWindowsFaceTheTunnel() {
		for (int x = -2 * SIZE; x < 2 * SIZE; x++) {
			for (int z = -2 * SIZE; z < 2 * SIZE; z++) {
				Tile tile = Level2Layout.tile(SEED, x, z);
				switch (tile.kind()) {
					case DOOR, LOCKED_DOOR, WINDOW, MACHINE, BANK -> {
						int fx = x + Level2Plan.DX[tile.dir()], fz = z + Level2Plan.DZ[tile.dir()];
						assertTrue(Level2Layout.tile(SEED, fx, fz).kind().walkable(), tile.kind() + " at " + x + ", " + z + " faces no floor");
						if (tile.kind() == Kind.DOOR || tile.kind() == Kind.LOCKED_DOOR) {
							int bx = x - Level2Plan.DX[tile.dir()], bz = z - Level2Plan.DZ[tile.dir()];
							Kind behind = Level2Layout.tile(SEED, bx, bz).kind();
							assertTrue(behind == Kind.ROOM || behind == Kind.VOID, "door at " + x + ", " + z + " opens onto " + behind);
						}
					}
					default -> {
					}
				}
			}
		}
	}

	/** Earlier wanderers' marks lead to the door they name, and are painted on a wall. */
	@Test
	void marksLeadToTheirDoors() {
		int marks = 0;
		for (int rx = -3; rx <= 3; rx++) {
			for (int rz = -3; rz <= 3; rz++) {
				Level2Plan plan = Level2Plan.of(SEED, rx, rz);
				for (int c = 0; c < Level2Plan.N * Level2Plan.N; c++) {
					if (plan.markTo[c] < 0) continue;
					int at = c, steps = 0;
					while (!hasExit(plan, at, plan.markFor[c])) {
						assertTrue(plan.markTo[at] >= 0 && steps++ <= Level2Plan.MARK_DEPTH, "marks lose the way in region " + rx + ", " + rz);
						int d = plan.markTo[at];
						at = Level2Plan.cell(at % Level2Plan.N + Level2Plan.DX[d], at / Level2Plan.N + Level2Plan.DZ[d]);
					}
				}
			}
		}
		for (int x = -2 * SIZE; x < 2 * SIZE; x++) {
			for (int z = -2 * SIZE; z < 2 * SIZE; z++) {
				Level2Layout.Mark mark = Level2Layout.mark(SEED, x, z);
				if (mark == null) continue;
				marks++;
				assertEquals(Kind.FLOOR, Level2Layout.tile(SEED, x, z).kind(), "a mark floating off the floor at " + x + ", " + z);
				int wx = x - Level2Plan.DX[mark.facing()], wz = z - Level2Plan.DZ[mark.facing()];
				assertEquals(Kind.WALL, Level2Layout.tile(SEED, wx, wz).kind(), "a mark with no wall behind it at " + x + ", " + z);
			}
		}
		assertTrue(marks > 5, "hardly any marks: " + marks);
	}

	private static boolean hasExit(Level2Plan plan, int cell, Level2Exit exit) {
		for (int d = 0; d < 4; d++) {
			if (plan.exits[cell * 4 + d] == exit) return true;
		}
		return false;
	}

	/** Some of the level is hot, most of it is not, and arriving never puts you in the heat. */
	@Test
	void heatIsPatchy() {
		int hot = 0, total = 0;
		for (int x = -4 * SIZE; x < 4 * SIZE; x += 3) {
			for (int z = -4 * SIZE; z < 4 * SIZE; z += 3) {
				total++;
				if (Level2Layout.hot(SEED, x, z)) hot++;
			}
		}
		assertTrue(hot > 0 && hot < total / 8, "hot " + hot + " of " + total);
		for (int x = -400; x <= 400; x += 37) {
			int[] spot = Level2Layout.landing(SEED, x, -x);
			assertEquals(Kind.FLOOR, Level2Layout.tile(SEED, spot[0], spot[1]).kind());
			assertFalse(Level2Layout.hot(SEED, spot[0], spot[1]));
			assertFalse(Level2Layout.cold(SEED, spot[0], spot[1]));
			assertFalse(Level2Layout.gas(SEED, spot[0], spot[1]));
		}
	}

	/** Cold zones, gas leak zones, and bare office corridors appear; office corridors have no pipes. */
	@Test
	void anomaliesAndOfficeCorridorsAppear() {
		int cold = 0, gas = 0, office = 0;
		for (int x = -6 * SIZE; x < 6 * SIZE; x += 5) {
			for (int z = -6 * SIZE; z < 6 * SIZE; z += 5) {
				if (Level2Layout.cold(SEED, x, z)) cold++;
				if (Level2Layout.gas(SEED, x, z)) gas++;
				if (Level2Layout.isOfficePatch(SEED, x, z)) {
					office++;
					assertFalse(Level2Layout.tile(SEED, x + 1, z + 1).kind() == Kind.BANK, "office corridor has pipes");
				}
			}
		}
		assertTrue(cold > 0, "no cold zones appeared");
		assertTrue(gas > 0, "no gas zones appeared");
		assertTrue(office > 0, "no office corridors appeared");
	}

	/** EL3A outposts, Macchina ruins, and narrow-gauge rail lines appear; EL3A is never hot. */
	@Test
	void facilitiesAndRailsAppear() {
		int el3a = 0, macchina = 0, rails = 0;
		for (int x = -6 * SIZE; x < 6 * SIZE; x += 5) {
			for (int z = -6 * SIZE; z < 6 * SIZE; z += 5) {
				Level2Plan.RoomKind kind = Level2Layout.roomKindAt(SEED, x, z);
				if (kind == Level2Plan.RoomKind.EL3A) {
					el3a++;
					assertFalse(Level2Layout.hot(SEED, x, z), "EL3A should not be hot");
				} else if (kind == Level2Plan.RoomKind.MACCHINA) {
					macchina++;
				}
				if (Level2Layout.railAxis(SEED, x + 2, z + 2) >= 0) rails++;
			}
		}
		assertTrue(el3a > 0, "no EL3A rooms appeared");
		assertTrue(macchina > 0, "no Macchina rooms appeared");
		assertTrue(rails > 0, "no railway tracks appeared");
	}
}
