package dev.noclip.backrooms.level1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
	void lowerLevelsAreHarderToFind() {
		Level1Exit[] exits = Level1Exit.values();
		for (int i = 1; i < exits.length; i++) {
			assertTrue(Double.parseDouble(exits[i].level) > Double.parseDouble(exits[i - 1].level), "exits must be listed by level");
			assertTrue(exits[i].chance > exits[i - 1].chance, exits[i] + " should be easier to find than " + exits[i - 1]);
		}

		// What actually generates follows the chances.
		int[] counts = new int[exits.length];
		int parking = 0, warehouse = 0;
		for (long seed = 0; seed < 6; seed++) {
			for (int dx = -20; dx < 20; dx++) {
				for (int dz = -20; dz < 20; dz++) {
					// Settlements take most of a district's bays, so they are left out of the count.
					if (Level1Layout.base(seed, dx, dz) != Level1Layout.Base.NONE) continue;
					Kind kind = Level1Layout.kind(seed, dx, dz);
					if (kind == Kind.PARKING) parking++;
					if (kind == Kind.WAREHOUSE) warehouse++;
					for (HallPlan.Placed placed : Level1Layout.exits(seed, dx, dz)) counts[placed.exit().ordinal()]++;
				}
			}
		}
		for (Level1Exit exit : exits) {
			int districts = parking + (exit.shape.fitsWarehouse() ? warehouse : 0);
			double expected = districts * exit.chance;
			double spread = 4 * Math.sqrt(expected);
			assertTrue(Math.abs(counts[exit.ordinal()] - expected) < spread,
					exit + ": " + counts[exit.ordinal()] + " placed, expected about " + (int) expected);
		}
		assertTrue(counts[Level1Exit.STAIRS_UP.ordinal()] * 4 < counts[Level1Exit.CRACKED_PILLAR.ordinal()]);
	}

	@Test
	void everyExitCanBeReachedAndUsed() {
		int checked = 0;
		for (long seed = 0; seed < 8; seed++) {
			for (int dx = -10; dx < 10; dx++) {
				for (int dz = -10; dz < 10; dz++) {
					for (HallPlan.Placed placed : Level1Layout.exits(seed, dx, dz)) {
						checked++;
						int[] spot = placed.spot();
						int x = spot[0], z = spot[1];
						Tile at = Level1Layout.tile(seed, x, z);
						String where = placed + ", seed " + seed;
						switch (placed.exit().shape) {
							case CORE_DOOR, CORE_PAINTING, CORE_OUTLETS -> {
								assertEquals(Tile.EXIT_CORE, at, where);
								int[] out = HallPlan.FACES[placed.face()];
								assertTrue(Level1Layout.tile(seed, x + out[0], z + out[1]).walkable(), "blocked exit " + where);
							}
							case PILLAR -> {
								assertEquals(Tile.PILLAR, at, where);
								assertTrue(Level1Layout.tile(seed, x - 1, z).walkable() || Level1Layout.tile(seed, x, z + 1).walkable(), where);
							}
							case HATCH -> assertEquals(Tile.HATCH, at, where);
							case FRAME -> {
								assertEquals(Tile.FRAME, at, where);
								assertTrue(Level1Layout.tile(seed, x, z - 1).walkable() && Level1Layout.tile(seed, x, z + 1).walkable(), where);
							}
							case STAIRS_DOWN, STAIRS_UP -> {
								assertEquals(Tile.STAIRWELL, at, where);
								// The top (or bottom) step is reached from the open floor before it.
								assertTrue(Level1Layout.tile(seed, x, z - 1).walkable() && Level1Layout.tile(seed, x + 1, z - 1).walkable(), where);
							}
						}
						assertEquals(placed.exit().shape == Level1Exit.Shape.PILLAR ? null : placed,
								placed.exit().shape == Level1Exit.Shape.PILLAR ? null : Level1Layout.exitCell(seed, x, z).placed(), where);
					}
				}
			}
		}
		assertTrue(checked > 1000, "only " + checked + " exits");
	}

	@Test
	void propsStandWhereTheyCanBeUsed() {
		int[] counts = new int[HallPlan.Prop.values().length];
		for (long seed = 0; seed < 8; seed++) {
			for (int dx = -10; dx < 10; dx++) {
				for (int dz = -10; dz < 10; dz++) {
					for (HallPlan.PlacedProp prop : Level1Layout.props(seed, dx, dz)) {
						counts[prop.prop().ordinal()]++;
						int[] spot = prop.spot();
						String where = prop + ", seed " + seed;
						switch (prop.prop()) {
							case TOLL_BOOTH -> {
								assertEquals(Tile.BOOTH, Level1Layout.tile(seed, spot[0], spot[1]), where);
								// The drawer is used from inside the booth, which is entered from behind.
								assertTrue(Level1Layout.tile(seed, spot[0], spot[1] + 1).walkable(), where);
								assertTrue(Level1Layout.tile(seed, spot[0], spot[1] + 3).walkable(), where);
							}
							case GENERATOR_ROOM -> {
								assertEquals(Tile.MACHINE, Level1Layout.tile(seed, spot[0], spot[1]), where);
								int[] door = prop.door();
								int[] out = HallPlan.FACES[prop.face()];
								assertEquals(Tile.ROOM, Level1Layout.tile(seed, door[0], door[1]), where);
								assertEquals(Tile.ROOM, Level1Layout.tile(seed, door[0] - out[0], door[1] - out[1]), where);
								assertTrue(Level1Layout.tile(seed, door[0] + out[0], door[1] + out[1]).walkable(), where);
							}
							case EYED_CRATE -> {
								assertEquals(Tile.CRATE, Level1Layout.tile(seed, spot[0], spot[1]), where);
								assertTrue(Level1Layout.tile(seed, spot[0] - 2, spot[1]).walkable(), where);
							}
						}
					}
				}
			}
		}
		for (HallPlan.Prop prop : HallPlan.Prop.values()) assertTrue(counts[prop.ordinal()] > 100, prop + ": " + counts[prop.ordinal()]);
	}

	@Test
	void settlementsStandInHallsWithAWayIn() {
		int outposts = 0, camps = 0;
		for (long seed = 0; seed < 10; seed++) {
			assertEquals(Level1Layout.Base.NONE, Level1Layout.base(seed, 0, 0));
			for (int dx = -15; dx < 15; dx++) {
				for (int dz = -15; dz < 15; dz++) {
					Level1Layout.Base base = Level1Layout.base(seed, dx, dz);
					if (base == Level1Layout.Base.NONE) continue;
					Kind kind = Level1Layout.kind(seed, dx, dz);
					if (base == Level1Layout.Base.FIRST_LIGHT) assertEquals(Kind.PARKING, kind);
					if (base == Level1Layout.Base.BLINK) assertEquals(Kind.WAREHOUSE, kind);
					if (base == Level1Layout.Base.FIRST_LIGHT) outposts++;
					else camps++;
					// No exit or prop inside or right around the ring.
					for (HallPlan.Placed exit : Level1Layout.exits(seed, dx, dz)) {
						int lx = exit.spot()[0] - dx * D, lz = exit.spot()[1] - dz * D;
						assertFalse(lx >= BasePlan.MIN_X - 2 && lx <= BasePlan.MAX_X + 2 && lz >= BasePlan.MIN_Z - 2 && lz <= BasePlan.MAX_Z + 2);
					}
					// Each side's way in is open floor.
					int x0 = dx * D, z0 = dz * D;
					boolean north = false, west = false;
					for (int along = 30; along <= 32; along++) {
						north |= Level1Layout.tile(seed, x0 + along, z0 + BasePlan.MIN_Z).walkable();
						west |= Level1Layout.tile(seed, x0 + BasePlan.MIN_X, z0 + along).walkable();
					}
					assertTrue(north && west, "no way in, seed " + seed + ", district " + dx + ", " + dz);
				}
			}
		}
		assertTrue(outposts > 20 && camps > 10, outposts + " outposts, " + camps + " camps");
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
