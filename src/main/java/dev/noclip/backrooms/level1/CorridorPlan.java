package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Layout.Ceiling;
import dev.noclip.backrooms.level1.Level1Layout.Tile;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The inside of one corridor district: a 16x16 grid of 4-block cells (three blocks of floor and one of wall),
 * carved into a winding maze with a few loops, then a few groups of cells merged into rooms.
 * Every cell is reachable from every other, and furniture only stands in the middle of a room,
 * so a free ring always runs around it.
 */
final class CorridorPlan {
	private static final int N = Level1Layout.CELLS;
	private static final int CELL = Level1Layout.CELL;
	private static final int SIZE = Level1Layout.DISTRICT;
	private static final int EXIT_GLOW_CELLS = 5;
	private static final ConcurrentHashMap<Key, CorridorPlan> CACHE = new ConcurrentHashMap<>();

	enum Room {
		OFFICE, STORAGE, BRICK, PADDED, EMPTY,
		/** A bed in the middle with tables round it. */
		MEDICAL,
		/** Pictures on every wall. */
		GALLERY,
		/** Rows of desks and chairs. */
		CLASSROOM,
		/** Columns of bookcases with a reading table. */
		LIBRARY,
		/** Rows of beds. */
		DORM,
		/** Long tables with chairs. */
		CANTEEN,
		/** An open office of partitioned desks. */
		CUBICLES,
		/** A grid of bare columns. */
		PILLARED,
		/** Chairs lined up along the walls. */
		WAITING
	}

	private record Key(long seed, int dx, int dz) {
	}

	private record Box(int i, int j, int w, int h, Room type) {
	}

	/** A room's way in and out: lockX, lockZ is where a natural lock hangs; tiles are every doorway block of that room. */
	record Doorway(int lockX, int lockZ, int[][] tiles) {
	}

	private final List<Doorway> doorways = new ArrayList<>();

	/** east[c]: open between cell c and the cell east of it. south[c]: open to the cell south of it. */
	private final boolean[] east = new boolean[N * N];
	private final boolean[] south = new boolean[N * N];
	private final int[] roomOf = new int[N * N];
	private final Room[] roomTypes = new Room[8];
	private int rooms;
	private final Ceiling[] bulbs = new Ceiling[N * N];
	/** 0 nothing, 1 desk, 2 shelf, 3 bed, 4 table, 5 chair, 6 floor painting, 7 bookcase, 8 partition, 9 pillar; by local block position. */
	private final byte[] furniture = new byte[SIZE * SIZE];
	/** A second stream for the looks of rooms, so choosing furniture never shifts the maze or where rooms stand. */
	private SplittableRandom variety;
	private int doorX = -1, doorZ = -1;

	static CorridorPlan of(long seed, int dx, int dz) {
		// Chunks are generated on several threads; plans are cheap, so a full cache is simply dropped.
		if (CACHE.size() > 512) CACHE.clear();
		return CACHE.computeIfAbsent(new Key(seed, dx, dz), key -> new CorridorPlan(seed, dx, dz));
	}

	private CorridorPlan(long seed, int dx, int dz) {
		SplittableRandom random = new SplittableRandom(Level1Layout.hash(seed, dx, dz, Level1Layout.PLAN));
		Arrays.fill(roomOf, -1);
		variety = new SplittableRandom(Level1Layout.hash(seed, dx, dz, Level1Layout.PLAN + 7919));
		carveMaze(random);
		addLoops(random);
		addRooms(random);
		findDoorways();
		placeDoor(random);
		lightCells(random);
	}

	List<Doorway> doorways() {
		return doorways;
	}

	/** Each room's doorways: the open walls between one of its cells and a cell outside it, three blocks wide. */
	private void findDoorways() {
		for (int room = 0; room < rooms; room++) {
			List<int[]> tiles = new ArrayList<>();
			int[] mid = null;
			for (int cell = 0; cell < N * N; cell++) {
				if (roomOf[cell] != room) continue;
				int ci = cell % N, cj = cell / N;
				int[][] edges = new int[4][];
				if (ci < N - 1 && east[cell] && roomOf[cell + 1] != room) edges[0] = new int[] {(ci + 1) * CELL, cj * CELL + 1, 1, 0};
				if (ci > 0 && east[cell - 1] && roomOf[cell - 1] != room) edges[1] = new int[] {ci * CELL, cj * CELL + 1, 1, 0};
				if (cj < N - 1 && south[cell] && roomOf[cell + N] != room) edges[2] = new int[] {ci * CELL + 1, (cj + 1) * CELL, 0, 1};
				if (cj > 0 && south[cell - N] && roomOf[cell - N] != room) edges[3] = new int[] {ci * CELL + 1, cj * CELL, 0, 1};
				for (int[] edge : edges) {
					// Walls on the district's own edge lines belong to Level1Layout, not to this plan.
					if (edge == null || (edge[2] == 1 ? edge[0] : edge[1]) == 0) continue;
					for (int k = 0; k < 3; k++) {
						int[] tile = {edge[0] + (edge[2] == 0 ? k : 0), edge[1] + (edge[3] == 0 ? k : 0)};
						tiles.add(tile);
						if (mid == null && k == 1) mid = tile;
					}
				}
			}
			if (mid != null) doorways.add(new Doorway(mid[0], mid[1], tiles.toArray(new int[0][])));
		}
	}

	/** The kind of room whose cells cover this local block, or null. */
	Room roomAt(int lx, int lz) {
		int cell = lx / CELL + lz / CELL * N;
		return roomOf[cell] < 0 ? null : roomTypes[roomOf[cell]];
	}

	int doorX() {
		return doorX;
	}

	int doorZ() {
		return doorZ;
	}

	Ceiling bulb(int ci, int cj) {
		return bulbs[ci + cj * N];
	}

	/** Never called for the district's own edge lines (lx == 0 or lz == 0); Level1Layout handles those. */
	Tile tile(int lx, int lz) {
		int item = furniture[lx + lz * SIZE];
		if (item == 1) return Tile.DESK;
		if (item == 2) return Tile.ROOM_SHELF;
		if (item == 3) return Tile.BED;
		if (item == 5) return Tile.CHAIR;
		if (item == 6) return Tile.FLOOR_PAINTING;
		if (item == 4) return Tile.TABLE;
		if (item == 7) return Tile.BOOKSHELF;
		if (item == 8) return Tile.PARTITION;
		if (item == 9) return Tile.WALL;
		if (item == 10) return Tile.BIN;

		int ci = lx / CELL, cj = lz / CELL, px = lx % CELL, pz = lz % CELL;
		if (px != 0 && pz != 0) return Tile.CORRIDOR;
		if (pz != 0) {
			if (lx == doorX && lz == doorZ) return Tile.DOOR;
			int west = ci - 1 + cj * N;
			return east[west] ? Tile.CORRIDOR : wall(west, west + 1);
		}
		if (px != 0) {
			int north = ci + (cj - 1) * N;
			return south[north] ? Tile.CORRIDOR : wall(north, north + N);
		}
		int nw = ci - 1 + (cj - 1) * N, ne = nw + 1, sw = nw + N, se = sw + 1;
		if (roomOf[nw] >= 0 && roomOf[nw] == roomOf[ne] && roomOf[nw] == roomOf[sw] && roomOf[nw] == roomOf[se]) {
			return Tile.CORRIDOR;
		}
		return wall(nw, ne, sw, se);
	}

	private Tile wall(int... cells) {
		Tile tile = Tile.WALL;
		for (int cell : cells) {
			int room = roomOf[cell];
			if (room < 0) continue;
			if (roomTypes[room] == Room.BRICK) return Tile.BRICK;
			if (roomTypes[room] == Room.PADDED) tile = Tile.PADDED;
			else if (roomTypes[room] == Room.GALLERY && tile == Tile.WALL) tile = Tile.GALLERY;
		}
		return tile;
	}

	/** A depth-first maze makes long winding corridors rather than many short stubs. */
	private void carveMaze(SplittableRandom random) {
		boolean[] seen = new boolean[N * N];
		int[] stack = new int[N * N];
		int top = 0;
		int start = random.nextInt(N * N);
		seen[start] = true;
		stack[top++] = start;
		int[] options = new int[4];
		while (top > 0) {
			int cell = stack[top - 1], ci = cell % N, cj = cell / N;
			int count = 0;
			if (ci > 0 && !seen[cell - 1]) options[count++] = cell - 1;
			if (ci < N - 1 && !seen[cell + 1]) options[count++] = cell + 1;
			if (cj > 0 && !seen[cell - N]) options[count++] = cell - N;
			if (cj < N - 1 && !seen[cell + N]) options[count++] = cell + N;
			if (count == 0) {
				top--;
				continue;
			}
			int next = options[random.nextInt(count)];
			open(cell, next);
			seen[next] = true;
			stack[top++] = next;
		}
	}

	private void addLoops(SplittableRandom random) {
		for (int cell = 0; cell < N * N; cell++) {
			if (cell % N < N - 1 && random.nextInt(100) < 10) east[cell] = true;
			if (cell / N < N - 1 && random.nextInt(100) < 10) south[cell] = true;
		}
	}

	private void addRooms(SplittableRandom random) {
		for (int attempt = 0; attempt < 14 && rooms < 4; attempt++) {
			int roll = random.nextInt(100), w, h;
			Room type;
			if (roll < 30) {
				// Tight rooms: brick, or padded.
				boolean wide = random.nextBoolean();
				w = wide ? 2 : 1;
				h = wide ? 1 : 2;
				type = random.nextInt(100) < 60 ? Room.BRICK : Room.PADDED;
			} else if (roll < 85) {
				w = 2;
				h = 2;
				int pick = random.nextInt(100);
				type = pick < 20 ? Room.OFFICE : pick < 34 ? Room.STORAGE : pick < 40 ? Room.EMPTY : pick < 50 ? Room.MEDICAL : pick < 60 ? Room.GALLERY
						: pick < 68 ? Room.CLASSROOM : pick < 76 ? Room.LIBRARY : pick < 84 ? Room.DORM : pick < 90 ? Room.CANTEEN
						: pick < 96 ? Room.CUBICLES : pick < 98 ? Room.PILLARED : Room.WAITING;
			} else {
				boolean wide = random.nextBoolean();
				w = wide ? 3 : 2;
				h = wide ? 2 : 3;
				type = random.nextBoolean() ? Room.STORAGE : Room.OFFICE;
				// The big rooms take their looks from the second stream.
				int look = variety.nextInt(100);
				if (type == Room.OFFICE) type = look < 40 ? Room.OFFICE : look < 75 ? Room.CUBICLES : Room.CLASSROOM;
				else type = look < 40 ? Room.STORAGE : look < 75 ? Room.LIBRARY : Room.CANTEEN;
			}
			int i0 = random.nextInt(N - w + 1), j0 = random.nextInt(N - h + 1);
			if (taken(i0, j0, w, h)) continue;

			int id = rooms++;
			roomTypes[id] = type;
			for (int i = i0; i < i0 + w; i++) {
				for (int j = j0; j < j0 + h; j++) {
					int cell = i + j * N;
					roomOf[cell] = id;
					if (i < i0 + w - 1) east[cell] = true;
					if (j < j0 + h - 1) south[cell] = true;
				}
			}
			furnish(new Box(i0, j0, w, h, type));
		}
	}

	private boolean taken(int i0, int j0, int w, int h) {
		for (int i = i0; i < i0 + w; i++) {
			for (int j = j0; j < j0 + h; j++) {
				if (roomOf[i + j * N] >= 0) return true;
			}
		}
		return false;
	}

	/** Furniture stays one block clear of the room's walls, so the ring around it joins every doorway. */
	private void furnish(Box room) {
		int x0 = room.i() * CELL + 2, x1 = (room.i() + room.w()) * CELL - 2;
		int z0 = room.j() * CELL + 2, z1 = (room.j() + room.h()) * CELL - 2;
		if (room.type() == Room.PADDED) furniture[room.i() * CELL + 2 + (room.j() * CELL + 2) * SIZE] = 5;
		if (x1 - x0 < 4 || z1 - z0 < 4) return;
		switch (room.type()) {
			case OFFICE -> {
				int z = (z0 + z1) / 2;
				for (int x = x0 + 1; x < x1; x += 2) furniture[x + z * SIZE] = 1;
			}
			case GALLERY -> {
				furniture[x0 + 1 + z0 * SIZE] = 6;
				furniture[x1 - 2 + (z1 - 1) * SIZE] = 6;
				furniture[x0 + (z0 + z1) / 2 * SIZE] = 6;
			}
			case MEDICAL -> {
				furniture[(x0 + x1) / 2 + (z0 + z1) / 2 * SIZE] = 3;
				for (int x : new int[] {x0, x1 - 1}) {
					for (int z : new int[] {z0, z1 - 1}) furniture[x + z * SIZE] = 4;
				}
			}
			case STORAGE -> {
				for (int z = z0 + 1; z < z1; z += 2) {
					for (int x = x0; x < x1; x++) furniture[x + z * SIZE] = 2;
				}
			}
			case CLASSROOM -> {
				// Pairs of desks in rows with a chair behind each, an aisle after every pair, and a free lane between rows.
				for (int z = z0; z + 1 < z1; z += 3) {
					for (int x = x0; x < x1; x++) {
						if ((x - x0) % 3 == 2) continue;
						furniture[x + z * SIZE] = 1;
						furniture[x + (z + 1) * SIZE] = 5;
					}
				}
			}
			case CANTEEN -> {
				// Long tables with a chair here and there down both sides, broken by gaps to cross through.
				for (int z = z0 + 1; z + 1 < z1; z += 5) {
					for (int x = x0; x < x1; x++) {
						if ((x - x0) % 7 == 6) continue;
						furniture[x + z * SIZE] = 4;
						if (variety.nextInt(100) < 55) furniture[x + (z - 1) * SIZE] = 5;
						if (variety.nextInt(100) < 55) furniture[x + (z + 1) * SIZE] = 5;
					}
				}
			}
			case DORM -> {
				// Beds with a gap between each, in rows with room to walk between.
				for (int z = z0; z < z1; z += 3) {
					for (int x = x0; x < x1; x += 2) furniture[x + z * SIZE] = 3;
				}
			}
			case LIBRARY -> {
				// Stacks: columns of bookcases five long, a gap in each to slip through, two lanes between columns.
				for (int x = x0; x < x1; x += 3) {
					for (int z = z0; z < z1; z++) {
						if ((z - z0) % 6 == 5) continue;
						furniture[x + z * SIZE] = 7;
					}
				}
				// A reading table with a chair in a corner of the stacks.
				furniture[x1 - 1 + (z1 - 1) * SIZE] = 4;
				furniture[x1 - 2 + (z1 - 1) * SIZE] = 5;
			}
			case CUBICLES -> {
				// Each cubicle is an L of partition panels round a desk and chair, open to the aisles on its other two sides.
				for (int z = z0; z + 2 < z1; z += 4) {
					for (int x = x0; x + 2 < x1; x += 4) {
						for (int k = 0; k < 3; k++) {
							furniture[x + k + z * SIZE] = 8;
							furniture[x + (z + k) * SIZE] = 8;
						}
						// The chair in front of the desk, on the open side; the bin beside the desk.
						furniture[x + 1 + (z + 1) * SIZE] = 1;
						furniture[x + 1 + (z + 2) * SIZE] = 5;
						if (variety.nextInt(100) < 40) furniture[x + 2 + (z + 1) * SIZE] = 10;
					}
				}
			}
			case PILLARED -> {
				for (int z = z0 + 1; z < z1; z += 4) {
					for (int x = x0 + 1; x < x1; x += 4) furniture[x + z * SIZE] = 9;
				}
			}
			case WAITING -> {
				// Chairs set in rows along two walls, and a low table between.
				for (int x = x0; x < x1; x += 2) {
					furniture[x + z0 * SIZE] = 5;
					furniture[x + (z1 - 1) * SIZE] = 5;
				}
				furniture[(x0 + x1) / 2 + (z0 + z1) / 2 * SIZE] = 4;
			}
			default -> {
			}
		}
	}

	/** The way out goes in a closed wall between two plain corridor cells, facing along X. */
	private void placeDoor(SplittableRandom random) {
		int first = random.nextInt((N - 1) * N);
		for (int k = 0; k < (N - 1) * N; k++) {
			int index = (first + k) % ((N - 1) * N);
			int i = 1 + index % (N - 1), j = index / (N - 1);
			int west = i - 1 + j * N;
			if (east[west] || roomOf[west] >= 0 || roomOf[west + 1] >= 0) continue;
			doorX = i * CELL;
			doorZ = j * CELL + CELL / 2;
			return;
		}
	}

	private void lightCells(SplittableRandom random) {
		int[] toExit = doorX < 0 ? null : distances(doorX / CELL - 1 + doorZ / CELL * N, doorX / CELL + doorZ / CELL * N);
		for (int cell = 0; cell < N * N; cell++) {
			if (toExit != null && toExit[cell] <= EXIT_GLOW_CELLS) {
				bulbs[cell] = Ceiling.EXIT_BULB;
				continue;
			}
			int roll = random.nextInt(100);
			if (roomOf[cell] >= 0) {
				bulbs[cell] = roll < 75 ? Ceiling.BULB : roll < 85 ? Ceiling.DEAD_BULB : Ceiling.NONE;
			} else {
				bulbs[cell] = roll < 30 ? Ceiling.BULB : roll < 55 ? Ceiling.DEAD_BULB : Ceiling.NONE;
			}
		}
	}

	/** Steps from the given cells to every cell, through open walls. */
	private int[] distances(int... from) {
		int[] distance = new int[N * N];
		Arrays.fill(distance, Integer.MAX_VALUE);
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		for (int cell : from) {
			distance[cell] = 0;
			queue.add(cell);
		}
		while (!queue.isEmpty()) {
			int cell = queue.poll(), ci = cell % N, cj = cell / N;
			int[] next = {
					ci > 0 && east[cell - 1] ? cell - 1 : -1,
					ci < N - 1 && east[cell] ? cell + 1 : -1,
					cj > 0 && south[cell - N] ? cell - N : -1,
					cj < N - 1 && south[cell] ? cell + N : -1};
			for (int n : next) {
				if (n >= 0 && distance[n] == Integer.MAX_VALUE) {
					distance[n] = distance[cell] + 1;
					queue.add(n);
				}
			}
		}
		return distance;
	}

	private void open(int a, int b) {
		int low = Math.min(a, b), high = Math.max(a, b);
		if (high - low == 1) east[low] = true;
		else south[low] = true;
	}
}
