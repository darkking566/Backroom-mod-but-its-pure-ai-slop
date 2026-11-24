package dev.noclip.backrooms.level2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The plan of one 80x80 region of tunnels: a 16x16 grid of 5-block cells carved into a maze that likes to keep
 * going straight, a few rooms set into it, and what is set into the tunnel walls (room doors, exits, windows).
 * Regions join up through three openings on every edge, worked out the same way from both sides.
 */
final class Level2Plan {
	static final int N = 16, CELL = 5, SIZE = N * CELL;
	/** Directions: index into dx/dz, and bit (1 << d) in open[]. */
	static final int NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3;
	static final int[] DX = {0, 1, 0, -1}, DZ = {-1, 0, 1, 0};
	static final int HOT_RADIUS = 14;
	private static final int EDGE_OPENINGS = 3;
	private static final int PLAN = 201, EDGE = 202;
	private static final ConcurrentHashMap<Key, Level2Plan> CACHE = new ConcurrentHashMap<>();

	enum RoomKind {
		/** Bare four walls, usually dark. */
		EMPTY,
		/** Crates, oil drums, dismantled parts. */
		STORAGE,
		/** Rows of machine cabinets. */
		MACHINES,
		/** No light, no sound: the doorway is the only light there is. */
		VOID
	}

	enum Feature {
		NONE, DOOR, LOCKED_DOOR, EXIT, WINDOW
	}

	record Room(int i, int j, int w, int h, RoomKind kind, boolean lit) {
		int x0() {
			return i * CELL;
		}

		int z0() {
			return j * CELL;
		}

		int x1() {
			return (i + w) * CELL - 1;
		}

		int z1() {
			return (j + h) * CELL - 1;
		}
	}

	private record Key(long seed, int rx, int rz) {
	}

	/** Open sides of each cell, as bits 1 << direction. */
	final byte[] open = new byte[N * N];
	final int[] roomOf = new int[N * N];
	final List<Room> rooms = new ArrayList<>();
	/** What is set into each cell's wall on each side: index cell * 4 + direction. */
	final Feature[] features = new Feature[N * N * 4];
	final Level2Exit[] exits = new Level2Exit[N * N * 4];
	/** Local block positions (z * SIZE + x) of the gaps in room walls behind room doors. */
	final List<Integer> doorways = new ArrayList<>();
	/** Local block position of the middle of the hot zone, or -1. */
	int hotX = -1, hotZ = -1;
	/** The cell whose wall holds the door to 811, or -1. */
	int bladeCell = -1;
	/**
	 * Earlier wanderers' marks (Wikidot: the standard exits are "often marked by earlier wanderers' signs"): in cells
	 * near a door marked 1, 3 or 4, the way to go towards it (a direction), or -1; and which door.
	 */
	final byte[] markTo = new byte[N * N];
	final Level2Exit[] markFor = new Level2Exit[N * N];
	static final int MARK_DEPTH = 7;

	static Level2Plan of(long seed, int rx, int rz) {
		// Chunks are generated on several threads; plans are cheap, so a full cache is simply dropped.
		if (CACHE.size() > 512) CACHE.clear();
		return CACHE.computeIfAbsent(new Key(seed, rx, rz), key -> new Level2Plan(seed, rx, rz));
	}

	private Level2Plan(long seed, int rx, int rz) {
		SplittableRandom random = new SplittableRandom(Level2Layout.hash(seed, rx, rz, PLAN));
		Arrays.fill(roomOf, -1);
		Arrays.fill(features, Feature.NONE);
		placeRooms(random);
		carveMaze(random);
		addLoops(random);
		openEdges(seed, rx, rz);
		placeRoomDoors(random);
		placeHotZone(random);
		placeExits(random);
		placeWindows(random);
		placeMarks();
	}

	static int cell(int i, int j) {
		return j * N + i;
	}

	static int opposite(int d) {
		return (d + 2) & 3;
	}

	boolean isOpen(int cell, int d) {
		return (open[cell] & (1 << d)) != 0;
	}

	int openings(int cell) {
		return Integer.bitCount(open[cell]);
	}

	/** -1 unless the cell is a straight run: 0 along X (open east and west), 1 along Z. */
	int straightAxis(int cell) {
		int o = open[cell];
		if (o == ((1 << EAST) | (1 << WEST))) return 0;
		if (o == ((1 << NORTH) | (1 << SOUTH))) return 1;
		return -1;
	}

	Feature feature(int cell, int d) {
		return features[cell * 4 + d];
	}

	private boolean inside(int i, int j) {
		return i >= 0 && j >= 0 && i < N && j < N;
	}

	// Rooms stay off the edge rows (the edges carry the openings to the next region) and apart from each other,
	// so the maze around them stays in one piece.
	private void placeRooms(SplittableRandom random) {
		int wanted = 2 + random.nextInt(3);
		for (int attempt = 0; attempt < 40 && rooms.size() < wanted; attempt++) {
			int w = 2 + random.nextInt(2), h = 2 + random.nextInt(2);
			int i = 1 + random.nextInt(N - 1 - w), j = 1 + random.nextInt(N - 1 - h);
			if (!free(i - 1, j - 1, w + 2, h + 2)) continue;
			int roll = random.nextInt(100);
			RoomKind kind = roll < 35 ? RoomKind.EMPTY : roll < 70 ? RoomKind.STORAGE : roll < 90 ? RoomKind.MACHINES : RoomKind.VOID;
			rooms.add(new Room(i, j, w, h, kind, kind != RoomKind.VOID && random.nextInt(100) < 45));
			for (int a = i; a < i + w; a++) {
				for (int b = j; b < j + h; b++) roomOf[cell(a, b)] = rooms.size() - 1;
			}
		}
	}

	private boolean free(int i, int j, int w, int h) {
		for (int a = i; a < i + w; a++) {
			for (int b = j; b < j + h; b++) {
				if (inside(a, b) && roomOf[cell(a, b)] >= 0) return false;
			}
		}
		return true;
	}

	private void connect(int c, int d) {
		int i = c % N, j = c / N;
		open[c] |= (byte) (1 << d);
		open[cell(i + DX[d], j + DZ[d])] |= (byte) (1 << opposite(d));
	}

	private boolean mazeCell(int i, int j) {
		return inside(i, j) && roomOf[cell(i, j)] < 0;
	}

	// A depth-first maze that keeps the way it was going most of the time, for the long runs of tunnel.
	private void carveMaze(SplittableRandom random) {
		boolean[] seen = new boolean[N * N];
		int[] came = new int[N * N];
		int[] stack = new int[N * N];
		int start;
		do {
			start = random.nextInt(N * N);
		} while (roomOf[start] >= 0);
		int top = 0;
		stack[top++] = start;
		seen[start] = true;
		came[start] = -1;
		int[] choices = new int[4];
		while (top > 0) {
			int c = stack[top - 1];
			int i = c % N, j = c / N, count = 0;
			boolean ahead = false;
			for (int d = 0; d < 4; d++) {
				int a = i + DX[d], b = j + DZ[d];
				if (!mazeCell(a, b) || seen[cell(a, b)]) continue;
				choices[count++] = d;
				if (d == came[c]) ahead = true;
			}
			if (count == 0) {
				top--;
				continue;
			}
			int d = ahead && random.nextInt(100) < 72 ? came[c] : choices[random.nextInt(count)];
			connect(c, d);
			int next = cell(i + DX[d], j + DZ[d]);
			seen[next] = true;
			came[next] = d;
			stack[top++] = next;
		}
	}

	// A few extra openings, for the intersections.
	private void addLoops(SplittableRandom random) {
		for (int c = 0; c < N * N; c++) {
			int i = c % N, j = c / N;
			if (roomOf[c] >= 0) continue;
			for (int d : new int[] {EAST, SOUTH}) {
				if (!mazeCell(i + DX[d], j + DZ[d]) || isOpen(c, d)) continue;
				if (random.nextInt(100) < 7) connect(c, d);
			}
		}
	}

	private void openEdges(long seed, int rx, int rz) {
		for (int k : edgeOpenings(seed, rx, rz, EAST)) open[cell(N - 1, k)] |= 1 << EAST;
		for (int k : edgeOpenings(seed, rx - 1, rz, EAST)) open[cell(0, k)] |= 1 << WEST;
		for (int k : edgeOpenings(seed, rx, rz, SOUTH)) open[cell(k, N - 1)] |= 1 << SOUTH;
		for (int k : edgeOpenings(seed, rx, rz - 1, SOUTH)) open[cell(k, 0)] |= 1 << NORTH;
	}

	/** Where the east (or south) edge of a region opens into the next one; both sides ask the same question. */
	private static int[] edgeOpenings(long seed, int rx, int rz, int side) {
		SplittableRandom random = new SplittableRandom(Level2Layout.hash(seed, rx, rz, EDGE + side));
		int[] picked = new int[EDGE_OPENINGS];
		int count = 0;
		while (count < EDGE_OPENINGS) {
			int k = 1 + random.nextInt(N - 2);
			boolean taken = false;
			for (int n = 0; n < count; n++) taken |= picked[n] == k;
			if (!taken) picked[count++] = k;
		}
		return picked;
	}

	private void placeRoomDoors(SplittableRandom random) {
		for (int r = 0; r < rooms.size(); r++) {
			Room room = rooms.get(r);
			List<int[]> walls = new ArrayList<>();
			for (int a = room.i(); a < room.i() + room.w(); a++) {
				for (int b = room.j(); b < room.j() + room.h(); b++) {
					for (int d = 0; d < 4; d++) {
						int ma = a - DX[d], mb = b - DZ[d];
						// From maze cell (ma, mb), looking d, is room cell (a, b).
						if (mazeCell(ma, mb)) walls.add(new int[] {cell(ma, mb), d, a, b});
					}
				}
			}
			int doors = random.nextInt(100) < 30 ? 2 : 1;
			for (int n = 0; n < doors && !walls.isEmpty(); n++) {
				int[] wall = walls.remove(random.nextInt(walls.size()));
				int c = wall[0], d = wall[1];
				if (features[c * 4 + d] != Feature.NONE) continue;
				features[c * 4 + d] = random.nextInt(100) < 55 ? Feature.DOOR : Feature.LOCKED_DOOR;
				int i = c % N, j = c / N;
				// The middle of the cell's wall on side d, then one step on into the room's own wall.
				int x = i * CELL + 2 + DX[d] * 3, z = j * CELL + 2 + DZ[d] * 3;
				doorways.add(z * SIZE + x);
			}
		}
	}

	private void placeHotZone(SplittableRandom random) {
		if (random.nextInt(100) >= 35) return;
		int c;
		do {
			c = random.nextInt(N * N);
		} while (roomOf[c] >= 0);
		hotX = c % N * CELL + 2;
		hotZ = c / N * CELL + 2;
	}

	private void placeExits(SplittableRandom random) {
		for (Level2Exit exit : Level2Exit.values()) {
			if (random.nextDouble() >= exit.chance) continue;
			if (exit.place == Level2Exit.Place.HOT && hotX < 0) continue;
			for (int attempt = 0; attempt < 60; attempt++) {
				int c = random.nextInt(N * N);
				int axis = straightAxis(c);
				if (roomOf[c] >= 0 || axis < 0) continue;
				if (exit.place == Level2Exit.Place.HOT) {
					int x = c % N * CELL + 2, z = c / N * CELL + 2;
					if (Math.abs(x - hotX) + Math.abs(z - hotZ) > HOT_RADIUS - 4) continue;
				}
				int d = axis == 0 ? (random.nextBoolean() ? NORTH : SOUTH) : (random.nextBoolean() ? EAST : WEST);
				if (features[c * 4 + d] != Feature.NONE) d = opposite(d);
				if (features[c * 4 + d] != Feature.NONE) continue;
				features[c * 4 + d] = Feature.EXIT;
				exits[c * 4 + d] = exit;
				if (exit == Level2Exit.BLADE) bladeCell = c;
				break;
			}
		}
	}

	/** From each standard exit, a short walk back along the tunnels, marking which way it lies. */
	private void placeMarks() {
		Arrays.fill(markTo, (byte) -1);
		int[] depth = new int[N * N];
		Arrays.fill(depth, Integer.MAX_VALUE);
		int[] queue = new int[N * N];
		for (int c = 0; c < N * N; c++) {
			for (int d = 0; d < 4; d++) {
				Level2Exit exit = exits[c * 4 + d];
				if (exit != Level2Exit.MARKED_1 && exit != Level2Exit.MARKED_3 && exit != Level2Exit.MARKED_4) continue;
				int head = 0, tail = 0;
				queue[tail++] = c;
				int[] seen = new int[N * N];
				Arrays.fill(seen, -1);
				seen[c] = 0;
				while (head < tail) {
					int at = queue[head++];
					if (seen[at] >= MARK_DEPTH) continue;
					int i = at % N, j = at / N;
					for (int way = 0; way < 4; way++) {
						int a = i + DX[way], b = j + DZ[way];
						if (!isOpen(at, way) || !mazeCell(a, b)) continue;
						int next = cell(a, b);
						if (seen[next] >= 0) continue;
						seen[next] = seen[at] + 1;
						queue[tail++] = next;
						// Nearer exits win where their marks would meet.
						if (seen[next] < depth[next]) {
							depth[next] = seen[next];
							markTo[next] = (byte) opposite(way);
							markFor[next] = exit;
						}
					}
				}
			}
		}
	}

	// Rare windows at the ends of dead-end tunnels, on the far wall.
	private void placeWindows(SplittableRandom random) {
		for (int c = 0; c < N * N; c++) {
			if (roomOf[c] >= 0 || openings(c) != 1 || random.nextInt(100) >= 15) continue;
			int way = Integer.numberOfTrailingZeros(open[c]);
			int d = opposite(way);
			if (features[c * 4 + d] == Feature.NONE) features[c * 4 + d] = Feature.WINDOW;
		}
	}
}
