package dev.noclip.backrooms.level0;

import net.minecraft.core.Direction;

/**
 * Spatial layout for Level 0's Manila Room scaled to CELL=10 & 5-block ceiling height:
 * a 2x2-cell (20x20-block) thick-walled sanctuary with a spacious 14x14 wooden-floored interior,
 * four recessed double wooden doors, hidden Void Stone inside the walls, a central octagonal
 * table with two chairs, an under-table supply cabinet, and a Manila Mary Foundation guide note.
 *
 * Plan C rarity: aligned with 5x5 Stages (300x300 blocks) with RARITY_MOD = 2 (~50% of Stages
 * contain a Manila Room so players realistically discover it during an expedition).
 */
final class ManilaRoomPlan {
	static final int REGION_SECTORS = Maze.STAGE_SECTORS; // 5x5 sectors = 300x300 blocks (1 Stage)
	static final int SPAN_CELLS = 2;
	static final int SPAN_BLOCKS = SPAN_CELLS * Maze.CELL; // 20 blocks
	static final int DOOR_A = 9;
	static final int DOOR_B = 10;
	private static final int MANILA_SALT = 87;
	private static final int RARITY_MOD = 2; // ~50% of Stages contain a Manila Room

	record Room(int cx0, int cz0) {
		int baseX() {
			return cx0 * Maze.CELL;
		}

		int baseZ() {
			return cz0 * Maze.CELL;
		}

		int centerX() {
			return baseX() + SPAN_BLOCKS / 2;
		}

		int centerZ() {
			return baseZ() + SPAN_BLOCKS / 2;
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
			return dx >= 3 && dx <= SPAN_BLOCKS - 4 && dz >= 3 && dz <= SPAN_BLOCKS - 4;
		}

		boolean isDoorwayTunnel(int dx, int dz) {
			return ((dx >= 0 && dx <= 2 || dx >= SPAN_BLOCKS - 3 && dx < SPAN_BLOCKS) && (dz == DOOR_A || dz == DOOR_B))
					|| ((dz >= 0 && dz <= 2 || dz >= SPAN_BLOCKS - 3 && dz < SPAN_BLOCKS) && (dx == DOOR_A || dx == DOOR_B));
		}

		boolean isNeighborDoorClear(int x, int z) {
			int dx = x - baseX(), dz = z - baseZ();
			return (dx == SPAN_BLOCKS && (dz == DOOR_A || dz == DOOR_B))
					|| (dz == SPAN_BLOCKS && (dx == DOOR_A || dx == DOOR_B));
		}

		boolean isWall(int x, int z, boolean topLayer) {
			int dx = x - baseX(), dz = z - baseZ();
			if (dx < 0 || dx >= SPAN_BLOCKS || dz < 0 || dz >= SPAN_BLOCKS) return false;
			if (dx <= 2 || dx >= SPAN_BLOCKS - 3 || dz <= 2 || dz >= SPAN_BLOCKS - 3) {
				if (isDoorwayTunnel(dx, dz)) return topLayer;
				return true;
			}
			return false;
		}

		boolean isCoreVoidLayer(int dx, int dz) {
			if (isDoorwayTunnel(dx, dz)) return false;
			boolean inXWall = dx == 1 || dx == SPAN_BLOCKS - 2;
			boolean inZWall = dz == 1 || dz == SPAN_BLOCKS - 2;
			boolean insideShell = dx >= 1 && dx <= SPAN_BLOCKS - 2 && dz >= 1 && dz <= SPAN_BLOCKS - 2;
			return insideShell && (inXWall || inZWall);
		}
	}

	private ManilaRoomPlan() {
	}

	static Room roomForCell(long seed, int cx, int cz) {
		int sx = Math.floorDiv(cx, Maze.SECTOR);
		int sz = Math.floorDiv(cz, Maze.SECTOR);
		int rx = Math.floorDiv(sx, REGION_SECTORS);
		int rz = Math.floorDiv(sz, REGION_SECTORS);
		Room room = roomInRegion(seed, rx, rz);
		if (room != null && room.isProtectedCell(cx, cz)) return room;
		return null;
	}

	static Room roomAtBlock(long seed, int x, int z) {
		int cx = Math.floorDiv(x, Maze.CELL);
		int cz = Math.floorDiv(z, Maze.CELL);
		Room room = roomForCell(seed, cx, cz);
		if (room != null && room.containsBlock(x, z)) return room;
		return null;
	}

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
			if (sx == 0 && sz == 0) continue;

			Maze.Zone zone = Maze.baseZoneForSector(seed, sx, sz);
			if (zone != Maze.Zone.CLASSIC && zone != Maze.Zone.CUBICLES && zone != Maze.Zone.CORRIDORS) {
				continue;
			}
			int lx = 2 + Math.floorMod(hash(seed, sx, sz, MANILA_SALT + 2), 2);
			int lz = 2 + Math.floorMod(hash(seed, sx, sz, MANILA_SALT + 3), 2);
			return new Room(sx * Maze.SECTOR + lx, sz * Maze.SECTOR + lz);
		}
		return null;
	}

	static Direction doorFacing(Room room, int dx, int dz) {
		if ((dz == 1 || dz == SPAN_BLOCKS - 2) && (dx == DOOR_A || dx == DOOR_B)) {
			return dx == DOOR_A ? Direction.NORTH : Direction.SOUTH;
		}
		if ((dx == 1 || dx == SPAN_BLOCKS - 2) && (dz == DOOR_A || dz == DOOR_B)) {
			return dz == DOOR_A ? Direction.EAST : Direction.WEST;
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
