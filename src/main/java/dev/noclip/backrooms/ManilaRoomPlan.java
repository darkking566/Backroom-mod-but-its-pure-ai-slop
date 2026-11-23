package dev.noclip.backrooms;

import net.minecraft.core.Direction;

/**
 * Spatial layout for Level 0's ultra-rare Manila Room:
 * a 2x2-cell (14x14-block) thick-walled sanctuary with an 8x8 wooden-floored interior,
 * four recessed double wooden doors, hidden Void Stone inside the walls, a central octagonal
 * table with two chairs, an under-table supply cabinet, and a Manila Mary Foundation guide note.
 */
final class ManilaRoomPlan {
	static final int REGION_SECTORS = 16; // 16x16 sectors = 896x896 blocks
	static final int SPAN_CELLS = 2;
	static final int SPAN_BLOCKS = SPAN_CELLS * Maze.CELL; // 14 blocks
	private static final int MANILA_SALT = 87;
	private static final int RARITY_MOD = 4; // 1 in 4 regions (~1 per 1800x1800 blocks)

	record Room(int cx0, int cz0) {
		int baseX() {
			return cx0 * Maze.CELL;
		}

		int baseZ() {
			return cz0 * Maze.CELL;
		}

		int centerX() {
			return baseX() + 7;
		}

		int centerZ() {
			return baseZ() + 7;
		}

		boolean containsCell(int cx, int cz) {
			return cx >= cx0 && cx <= cx0 + 1 && cz >= cz0 && cz <= cz0 + 1;
		}

		boolean isProtectedCell(int cx, int cz) {
			return cx >= cx0 - 1 && cx <= cx0 + 2 && cz >= cz0 - 1 && cz <= cz0 + 2;
		}

		boolean containsBlock(int x, int z) {
			int dx = x - baseX(), dz = z - baseZ();
			return dx >= 0 && dx < SPAN_BLOCKS && dz >= 0 && dz < SPAN_BLOCKS;
		}

		boolean isInteriorBlock(int x, int z) {
			int dx = x - baseX(), dz = z - baseZ();
			return dx >= 3 && dx <= 10 && dz >= 3 && dz <= 10;
		}

		boolean isDoorwayTunnel(int dx, int dz) {
			return ((dx >= 0 && dx <= 2 || dx >= 11 && dx <= 13) && (dz == 6 || dz == 7))
					|| ((dz >= 0 && dz <= 2 || dz >= 11 && dz <= 13) && (dx == 6 || dx == 7));
		}

		boolean isNeighborDoorClear(int x, int z) {
			int dx = x - baseX(), dz = z - baseZ();
			return (dx == SPAN_BLOCKS && (dz == 6 || dz == 7))
					|| (dz == SPAN_BLOCKS && (dx == 6 || dx == 7));
		}

		boolean isWall(int x, int z, boolean topLayer) {
			int dx = x - baseX(), dz = z - baseZ();
			if (dx < 0 || dx >= SPAN_BLOCKS || dz < 0 || dz >= SPAN_BLOCKS) return false;
			if (dx <= 2 || dx >= 11 || dz <= 2 || dz >= 11) {
				if (isDoorwayTunnel(dx, dz)) return topLayer;
				return true;
			}
			return false;
		}

		boolean isCoreVoidLayer(int dx, int dz) {
			if (isDoorwayTunnel(dx, dz)) return false;
			boolean inXWall = dx == 1 || dx == 12;
			boolean inZWall = dz == 1 || dz == 12;
			boolean insideShell = dx >= 1 && dx <= 12 && dz >= 1 && dz <= 12;
			return insideShell && (inXWall || inZWall);
		}
	}

	private ManilaRoomPlan() {
	}

	/** Returns the Manila Room covering or neighboring (cx, cz), or null if none in its region. */
	static Room roomForCell(long seed, int cx, int cz) {
		int sx = Math.floorDiv(cx, Maze.SECTOR);
		int sz = Math.floorDiv(cz, Maze.SECTOR);
		int rx = Math.floorDiv(sx, REGION_SECTORS);
		int rz = Math.floorDiv(sz, REGION_SECTORS);
		Room room = roomInRegion(seed, rx, rz);
		if (room != null && room.isProtectedCell(cx, cz)) return room;
		return null;
	}

	/** Returns the Manila Room whose 14x14 footprint contains (x, z), or null. */
	static Room roomAtBlock(long seed, int x, int z) {
		int cx = Math.floorDiv(x, Maze.CELL);
		int cz = Math.floorDiv(z, Maze.CELL);
		Room room = roomForCell(seed, cx, cz);
		if (room != null && room.containsBlock(x, z)) return room;
		return null;
	}

	/** Returns the Manila Room in the region of (x, z) if (x, z) is within radiusBlocks of its center, or null. */
	static Room nearbyRoom(long seed, int x, int z, int radiusBlocks) {
		int sx = Math.floorDiv(Math.floorDiv(x, Maze.CELL), Maze.SECTOR);
		int sz = Math.floorDiv(Math.floorDiv(z, Maze.CELL), Maze.SECTOR);
		int rx = Math.floorDiv(sx, REGION_SECTORS);
		int rz = Math.floorDiv(sz, REGION_SECTORS);
		for (int drx = -1; drx <= 1; drx++) {
			for (int drz = -1; drz <= 1; drz++) {
				Room room = roomInRegion(seed, rx + drx, rz + drz);
				if (room == null) continue;
				int dx = x - room.centerX();
				int dz = z - room.centerZ();
				if (dx * dx + dz * dz <= radiusBlocks * radiusBlocks) {
					return room;
				}
			}
		}
		return null;
	}

	static boolean isRoomCell(long seed, int cx, int cz) {
		Room room = roomForCell(seed, cx, cz);
		return room != null && room.containsCell(cx, cz);
	}

	static boolean isProtectedCell(long seed, int cx, int cz) {
		return roomForCell(seed, cx, cz) != null;
	}

	static Room roomInRegion(long seed, int rx, int rz) {
		return CACHE.get().getOrCompute(seed, rx, rz);
	}

	private static Room computeRoomInRegion(long seed, int rx, int rz) {
		if (Math.floorMod(hash(seed, rx, rz, MANILA_SALT), RARITY_MOD) != 0) {
			return null;
		}
		int total = REGION_SECTORS * REGION_SECTORS;
		int start = Math.floorMod(hash(seed, rx, rz, MANILA_SALT + 1), total);
		for (int i = 0; i < total; i++) {
			int pick = (start + i) % total;
			int sx = rx * REGION_SECTORS + (pick % REGION_SECTORS);
			int sz = rz * REGION_SECTORS + (pick / REGION_SECTORS);
			if (Math.abs(sx) <= 2 && Math.abs(sz) <= 2) continue;

			Maze.Zone zone = Maze.baseZoneForSector(seed, sx, sz);
			if (zone != Maze.Zone.CLASSIC && zone != Maze.Zone.CUBICLES && zone != Maze.Zone.CORRIDORS) {
				continue;
			}
			int lx = 2 + Math.floorMod(hash(seed, sx, sz, MANILA_SALT + 2), 3);
			int lz = 2 + Math.floorMod(hash(seed, sx, sz, MANILA_SALT + 3), 3);
			return new Room(sx * Maze.SECTOR + lx, sz * Maze.SECTOR + lz);
		}
		return null;
	}

	/** Door leaf direction in one of the four recessed doorways, or null if not a door block. */
	static Direction doorFacing(Room room, int dx, int dz) {
		// North & South doorways (dz == 1, dz == 12): left leaf (dx==6) faces NORTH, right leaf (dx==7) faces SOUTH.
		if ((dz == 1 || dz == 12) && (dx == 6 || dx == 7)) {
			return dx == 6 ? Direction.NORTH : Direction.SOUTH;
		}
		// West & East doorways (dx == 1, dx == 12): top leaf (dz==6) faces EAST, bottom leaf (dz==7) faces WEST.
		if ((dx == 1 || dx == 12) && (dz == 6 || dz == 7)) {
			return dz == 6 ? Direction.EAST : Direction.WEST;
		}
		return null;
	}

	private static long hash(long seed, int x, int z, int salt) {
		long h = mix(seed + salt);
		h = mix(h ^ x);
		return mix(h ^ z);
	}

	private static long mix(long v) {
		v += 0x9E3779B97F4A7C15L;
		v = (v ^ (v >>> 30)) * 0xBF58476D1CE4E5B9L;
		v = (v ^ (v >>> 27)) * 0x94D049BB133111EBL;
		return v ^ (v >>> 31);
	}

	private static final ThreadLocal<RoomCache> CACHE = ThreadLocal.withInitial(RoomCache::new);

	private static final class RoomCache {
		private static final int MASK = 255;
		private final long[] seeds = new long[MASK + 1];
		private final int[] rxs = new int[MASK + 1];
		private final int[] rzs = new int[MASK + 1];
		private final Room[] rooms = new Room[MASK + 1];
		private final boolean[] valid = new boolean[MASK + 1];

		Room getOrCompute(long seed, int rx, int rz) {
			int idx = (int) ((seed ^ (rx * 0x9E3779B97F4A7C15L) ^ (rz * 0xC2B2AE3D27D4EB4FL)) >>> 32) & MASK;
			if (valid[idx] && seeds[idx] == seed && rxs[idx] == rx && rzs[idx] == rz) {
				return rooms[idx];
			}
			Room computed = computeRoomInRegion(seed, rx, rz);
			seeds[idx] = seed;
			rxs[idx] = rx;
			rzs[idx] = rz;
			rooms[idx] = computed;
			valid[idx] = true;
			return computed;
		}
	}
}
