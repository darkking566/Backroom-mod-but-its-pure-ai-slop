package dev.noclip.backrooms.level0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ExitsTest {
	/** The longest walk allowed from any walkable block to the nearest exit. */
	private static final int MAX_WALK = Exits.REGION * 3;
	private static final int BORDER_REGION = 29_900_000 / Exits.REGION;
	private static final int[] REGIONS = {0, -1, 7, -BORDER_REGION, BORDER_REGION};

	/** Doors are rare (about one region in Exits.RARITY, 2026-11-26); each one sits in a wall with room on both sides. */
	@Test
	void rareDoorsSitInAWallWithRoomOnBothSides() {
		Random random = new Random(1);
		int regions = 0, withDoor = 0;
		for (int run = 0; run < 500; run++) {
			long seed = random.nextLong();
			for (int origin : REGIONS) {
				for (int rx = origin - 1; rx <= origin + 1; rx++) {
					for (int rz = origin - 1; rz <= origin + 1; rz++) {
						regions++;
						Exits.Door door = Exits.doorIn(seed, rx, rz);
						if (door == null) continue;
						withDoor++;
						int x = door.x(), z = door.z();
						assertTrue(Exits.isDoor(seed, x, z));
						assertEquals(rx, Math.floorDiv(x, Exits.REGION));
						assertEquals(rz, Math.floorDiv(z, Exits.REGION));
						assertNotEquals(Maze.Zone.RED_ROOM, Maze.zoneAt(seed, x, z), "door placed inside a red room");
						assertNotEquals(Maze.Zone.RED_ROOM, Maze.zoneAt(seed, x - 1, z), "door borders a red room");
						for (int dz = -1; dz <= 1; dz++) {
							assertTrue(Maze.isWall(seed, x, z + dz, false) && Maze.isWall(seed, x, z + dz, true), "door not set in a wall");
						}
						for (int side : new int[] {x - 1, x + 1}) {
							assertFalse(Maze.isWall(seed, side, z, true), "door blocked");
							assertFalse(Maze.isPit(seed, side, z), "door opens over a pit");
							assertFalse(Maze.isPuddle(seed, side, z), "door opens into a puddle");
						}
					}
				}
			}
		}
		double share = withDoor / (double) regions;
		assertTrue(share > 0.5 / Exits.RARITY && share < 1.5 / Exits.RARITY, "share of regions with a door: " + share);
	}

	/** A region near {@code region} that has a door. */
	private static int regionWithDoor(long seed, int region) {
		for (int r = region; ; r++) {
			if (Exits.doorIn(seed, r, r) != null) return r;
		}
	}

	/** Every walkable block of a region with a door can walk to it (and, the way the maze is built, not too far). */
	@Test
	void anExitIsWalkableFromEverywhere() {
		Random random = new Random(2);
		int size = Exits.REGION * 3;
		int[] queue = new int[size * size], walked = new int[size * size];

		for (int run = 0; run < 40; run++) {
			long seed = random.nextLong();
			int region = regionWithDoor(seed, REGIONS[run % REGIONS.length]);
			int origin = (region - 1) * Exits.REGION;
			Arrays.fill(walked, Integer.MAX_VALUE);
			byte[] floorY = new byte[size * size];
			for (int i = 0; i < floorY.length; i++) {
				int x = origin + i % size, z = origin + i / size;
				int fy = Maze.walkableFloorY(seed, x, z, Maze.EpochSource.ZERO);
				floorY[i] = fy == Integer.MIN_VALUE ? Byte.MIN_VALUE : (byte) fy;
			}

			int tail = 0;
			for (int rx = region - 1; rx <= region + 1; rx++) {
				for (int rz = region - 1; rz <= region + 1; rz++) {
					Exits.Door door = Exits.doorIn(seed, rx, rz);
					if (door == null) continue;
					for (int x : new int[] {door.x() - 1, door.x() + 1}) {
						if (x < origin || x >= origin + size) continue;
						int at = x - origin + (door.z() - origin) * size;
						walked[at] = 0;
						queue[tail++] = at;
					}
				}
			}

			for (int head = 0; head < tail; head++) {
				int at = queue[head], x = at % size, z = at / size;
				byte curY = floorY[at];
				int[] next = {x > 0 ? at - 1 : -1, x < size - 1 ? at + 1 : -1, z > 0 ? at - size : -1, z < size - 1 ? at + size : -1};
				for (int n : next) {
					if (n >= 0 && floorY[n] != Byte.MIN_VALUE && Math.abs(floorY[n] - curY) <= 1 && walked[n] == Integer.MAX_VALUE) {
						walked[n] = walked[at] + 1;
						queue[tail++] = n;
					}
				}
			}

			// Only the middle region, which has a door: the others may be nearer to a door outside the window.
			for (int x = Exits.REGION; x < Exits.REGION * 2; x++) {
				for (int z = Exits.REGION; z < Exits.REGION * 2; z++) {
					int at = x + z * size;
					if (floorY[at] != Byte.MIN_VALUE) {
						int wx = origin + x, wz = origin + z;
						assertTrue(walked[at] <= MAX_WALK, "too far from an exit: " + walked[at] + " blocks at ("
								+ wx + ", " + wz + ") zone=" + Maze.zoneAt(seed, wx, wz) + " floorY=" + floorY[at] + ", seed " + seed);
					}
				}
			}
		}
	}

	@Test
	void lampsNearAnExitPointToIt() {
		Random random = new Random(3);
		for (int run = 0; run < 500; run++) {
			long seed = random.nextLong();
			int region = regionWithDoor(seed, run % 5 - 2);
			Exits.Door door = Exits.doorIn(seed, region, region);
			assertTrue(Exits.lampPointsToExit(seed, door.x() + Maze.CENTER, door.z()));
			assertTrue(Exits.lampPointsToExit(seed, door.x() - Maze.CELL + Maze.CENTER, door.z()));
			assertTrue(Exits.lampPointsToExit(seed, door.x() + Exits.HINT_CELLS * Maze.CELL + Maze.CENTER, door.z()));
			assertTrue(Exits.lampPointsToExit(seed, door.x() - (Exits.HINT_CELLS + 1) * Maze.CELL + Maze.CENTER, door.z()));
			int doorCx = Math.floorDiv(door.x(), Maze.CELL);
			int doorCz = Math.floorDiv(door.z(), Maze.CELL);
			assertTrue(Exits.isProtectedCell(seed, doorCx, doorCz));
			assertTrue(Exits.isProtectedCell(seed, doorCx - 1, doorCz));
		}
	}
}
