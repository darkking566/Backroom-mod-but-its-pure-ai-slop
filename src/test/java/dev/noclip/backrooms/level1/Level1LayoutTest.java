package dev.noclip.backrooms.level1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.level1.Level1Layout.Ceiling;
import dev.noclip.backrooms.level1.Level1Layout.Kind;
import dev.noclip.backrooms.level1.Level1Layout.Tile;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Random;
import org.junit.jupiter.api.Test;

class Level1LayoutTest {
	private static final int D = Level1Layout.DISTRICT;
	private static final int AREA = D * 3;

	@Test
	void everyWalkableBlockIsReachable() {
		Random random = new Random(1);
		// Includes negative coordinates and districts near the world border.
		int[] origins = {0, -D * 3, -29_999_872 / D * D, 29_000_000 / D * D};
		for (int run = 0; run < 160; run++) {
			long seed = random.nextLong();
			int origin = origins[run % origins.length] + D * (random.nextInt(9) - 4);
			boolean[] unvisited = new boolean[AREA * AREA];
			int open = 0, start = -1;
			for (int i = 0; i < unvisited.length; i++) {
				unvisited[i] = Level1Layout.tile(seed, origin + i % AREA, origin + i / AREA).walkable();
				if (unvisited[i]) {
					open++;
					if (start < 0) start = i;
				}
			}
			assertTrue(open > AREA * AREA / 3, "mostly solid, seed " + seed);

			ArrayDeque<Integer> queue = new ArrayDeque<>();
			unvisited[start] = false;
			queue.add(start);
			int reached = 0;
			while (!queue.isEmpty()) {
				int at = queue.poll(), x = at % AREA, z = at / AREA;
				reached++;
				int[] next = {x > 0 ? at - 1 : -1, x < AREA - 1 ? at + 1 : -1, z > 0 ? at - AREA : -1, z < AREA - 1 ? at + AREA : -1};
				for (int n : next) {
					if (n >= 0 && unvisited[n]) {
						unvisited[n] = false;
						queue.add(n);
					}
				}
			}
			assertEquals(open, reached, "unreachable area, seed " + seed + ", origin " + origin);
		}
	}

	@Test
	void everyKindAndTileGenerates() {
		EnumSet<Kind> kinds = EnumSet.noneOf(Kind.class);
		EnumSet<Tile> tiles = EnumSet.noneOf(Tile.class);
		EnumSet<Ceiling> ceilings = EnumSet.noneOf(Ceiling.class);
		for (int x = -D * 6; x < D * 6; x++) {
			for (int z = -D * 6; z < D * 6; z++) {
				tiles.add(Level1Layout.tile(7, x, z));
				ceilings.add(Level1Layout.ceiling(7, x, z));
			}
		}
		for (int dx = -6; dx < 6; dx++) {
			for (int dz = -6; dz < 6; dz++) kinds.add(Level1Layout.kind(7, dx, dz));
		}
		assertEquals(EnumSet.allOf(Kind.class), kinds);
		assertEquals(EnumSet.allOf(Tile.class), tiles);
		assertEquals(EnumSet.allOf(Ceiling.class), ceilings);
	}

	@Test
	void arrivalsLandOnAParkingAisle() {
		Random random = new Random(2);
		for (int i = 0; i < 20_000; i++) {
			long seed = random.nextLong();
			int x = random.nextInt(2_000_000) - 1_000_000, z = random.nextInt(2_000_000) - 1_000_000;
			int[] spot = Level1Layout.landing(seed, x, z);
			assertTrue(Level1Layout.tile(seed, spot[0], spot[1]).walkable(), "blocked landing, seed " + seed);
			assertEquals(Ceiling.NONE, Level1Layout.ceiling(seed, spot[0], spot[1]));
			assertEquals(Kind.PARKING, Level1Layout.kindAt(seed, spot[0], spot[1]));
			assertTrue(Math.abs(spot[0] - x) < D * 9 && Math.abs(spot[1] - z) < D * 9, "landed far away");
		}
		assertEquals(Kind.PARKING, Level1Layout.kind(5, 0, 0));
	}

	@Test
	void everyCorridorBlockHasADoorWithRoomOnBothSides() {
		int corridorBlocks = 0;
		for (long seed = 0; seed < 20; seed++) {
			for (int dx = -8; dx < 8; dx++) {
				for (int dz = -8; dz < 8; dz++) {
					Level1Layout.Door door = Level1Layout.door(seed, dx, dz);
					if (Level1Layout.kind(seed, dx, dz) != Kind.CORRIDORS) {
						assertEquals(null, door);
						continue;
					}
					corridorBlocks++;
					assertNotNull(door);
					assertEquals(dx, Math.floorDiv(door.x(), D));
					assertEquals(dz, Math.floorDiv(door.z(), D));
					assertEquals(Tile.DOOR, Level1Layout.tile(seed, door.x(), door.z()));
					assertEquals(Tile.WALL, Level1Layout.tile(seed, door.x(), door.z() - 1));
					assertEquals(Tile.WALL, Level1Layout.tile(seed, door.x(), door.z() + 1));
					assertEquals(Tile.CORRIDOR, Level1Layout.tile(seed, door.x() - 1, door.z()));
					assertEquals(Tile.CORRIDOR, Level1Layout.tile(seed, door.x() + 1, door.z()));
					assertEquals(Ceiling.EXIT_BULB, Level1Layout.ceiling(seed, door.x() + 2, door.z()));
				}
			}
		}
		assertTrue(corridorBlocks > 20 * 16 * 16 / 5, "too few corridor blocks: " + corridorBlocks);
	}

	@Test
	void exactlyOneDoorPerCorridorBlock() {
		long seed = 11;
		for (int dx = -3; dx < 3; dx++) {
			for (int dz = -3; dz < 3; dz++) {
				int doors = 0;
				for (int x = dx * D; x < dx * D + D; x++) {
					for (int z = dz * D; z < dz * D + D; z++) {
						if (Level1Layout.tile(seed, x, z) == Tile.DOOR) doors++;
					}
				}
				assertEquals(Level1Layout.door(seed, dx, dz) == null ? 0 : 1, doors);
			}
		}
	}

	@Test
	void seedChangesTheFloorPlan() {
		int different = 0;
		for (int x = 0; x < AREA * 2; x++) {
			for (int z = 0; z < AREA * 2; z++) {
				if (Level1Layout.tile(1, x, z) != Level1Layout.tile(2, x, z)) different++;
			}
		}
		assertTrue(different > AREA);
		assertNotNull(Level1Layout.tile(1, 0, 0));
	}
}
