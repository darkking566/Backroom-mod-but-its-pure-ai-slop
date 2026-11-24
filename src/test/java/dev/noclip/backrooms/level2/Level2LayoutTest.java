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
		}
	}
}
