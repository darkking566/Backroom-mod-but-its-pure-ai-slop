package dev.noclip.backrooms.level3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The plan of one 96x96 region of Level 3: a 16x16 grid of 6-block cells carved into a maze, with big halls set
 * into it (generator rooms, boiler rooms, assembly lines, sanctums, the odd car room, a Base Gamma outpost).
 * Many of the maze's extra connections are shut off by bars you can see through but never pass; some of its
 * necessary ones have a gate in the bars whose lock can be picked. Regions join through three open passages
 * on every edge, worked out the same way from both sides.
 */
final class Level3Plan {
	static final int N = 16, CELL = 6, SIZE = N * CELL;
	static final int NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3;
	static final int[] DX = {0, 1, 0, -1}, DZ = {-1, 0, 1, 0};
	private static final int EDGE_OPENINGS = 3;
	private static final int PLAN = 301, EDGE = 302;
	private static final ConcurrentHashMap<Key, Level3Plan> CACHE = new ConcurrentHashMap<>();

	enum HallKind {
		/** Lots of electrical equipment, and the generator itself. */
		GENERATOR,
		/** Boilers, the source of the black sludge in the pipes. */
		BOILER,
		/** A factory floor of conveyor belts. */
		ASSEMBLY,
		/** An empty, church-like room with pillars and an angel statue: the one place entities stay away from. */
		SANCTUM,
		/** A room with a car in it: get in, and it takes you to Level 69. */
		CAR,
		/** An M.E.G. outpost of Base Gamma, with the door back to Level 11. */
		BASE,
		/** B.N.T.G. Storage Facility: supply warehouse, pawnshop counter, and rare-item showcase. */
		BNTG_STORAGE,
		/** Followers of Jerry - Blue Salvation: converted generator room with white drywall, blue lights, and murals. */
		BLUE_SALVATION,
		/** U.E.C. Scylla Colony: dark, hostile research outpost with apparatus and locked crates. */
		SCYLLA_COLONY,
		/** Base Gamma itself: a whole walled town in the middle of its region, over every floor of the level (GammaLayout). */
		GAMMA
	}

	/** What joins a cell to the next one on one side. */
	enum Edge {
		/** Wall. */
		NONE,
		OPEN,
		/** Bars across the passage: you can see through, never pass. */
		BARS,
		/** Bars with a locked gate that can be picked. */
		GATE,
		/** An opening into a hall. */
		HALL
	}

	record Hall(int i, int j, int w, int h, HallKind kind) {
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

	final Edge[] edges = new Edge[N * N * 4];
	final int[] hallOf = new int[N * N];
	final List<Hall> halls = new ArrayList<>();
	/** Elevators set into a cell's wall on one side: index cell * 4 + direction. */
	final Level3Exit[] exits = new Level3Exit[N * N * 4];
	/**
	 * The M.E.G. signs towards Base Gamma (Wikidot: "numerous signs ... with the hopes of helping wanderers find
	 * their way to the base"): in cells within reach of a Base Gamma outpost, the way to go, or -1.
	 */
	final byte[] baseTo = new byte[N * N];
	static final int SIGN_DEPTH = 14;
	private final long seed;
	private final int rx, rz;

	static Level3Plan of(long seed, int rx, int rz) {
		// Chunks are generated on several threads; plans are cheap, so a full cache is simply dropped.
		if (CACHE.size() > 512) CACHE.clear();
		return CACHE.computeIfAbsent(new Key(seed, rx, rz), key -> new Level3Plan(seed, rx, rz));
	}

	private Level3Plan(long seed, int rx, int rz) {
		this.seed = seed;
		this.rx = rx;
		this.rz = rz;
		SplittableRandom random = new SplittableRandom(Level3Layout.hash(seed, rx, rz, PLAN));
		Arrays.fill(edges, Edge.NONE);
		Arrays.fill(hallOf, -1);
		placeHalls(random);
		carveMaze(random);
		addLoops(random);
		openEdges();
		openHalls(random);
		placeElevators(random);
		placeBaseSigns();
	}

	static int cell(int i, int j) {
		return j * N + i;
	}

	static int opposite(int d) {
		return (d + 2) & 3;
	}

	Edge edge(int cell, int d) {
		return edges[cell * 4 + d];
	}

	/** Whether a side has any passage through it (open, barred, gated, or into a hall). */
	boolean passage(int cell, int d) {
		return edges[cell * 4 + d] != Edge.NONE;
	}

	/** -1 unless the cell is a straight run: 0 along X, 1 along Z. */
	int straightAxis(int cell) {
		boolean n = passage(cell, NORTH), e = passage(cell, EAST), s = passage(cell, SOUTH), w = passage(cell, WEST);
		if (e && w && !n && !s) return 0;
		if (n && s && !e && !w) return 1;
		return -1;
	}

	Level3Layout.Profile profile(int i, int j) {
		return Level3Layout.profile(seed, rx * N + i, rz * N + j);
	}

	private boolean inside(int i, int j) {
		return i >= 0 && j >= 0 && i < N && j < N;
	}

	private boolean mazeCell(int i, int j) {
		return inside(i, j) && hallOf[cell(i, j)] < 0;
	}

	private void setEdge(int c, int d, Edge edge) {
		int i = c % N, j = c / N;
		edges[c * 4 + d] = edge;
		int a = i + Level3Plan.DX[d], b = j + Level3Plan.DZ[d];
		if (inside(a, b)) edges[cell(a, b) * 4 + opposite(d)] = edge;
	}

	// Halls stay off the edge rows (the edges carry the openings to the next region) and apart from each other.
	private void placeHalls(SplittableRandom random) {
		List<HallKind> wanted = new ArrayList<>();
		if (random.nextDouble() < Level3Exit.BASE_GAMMA_DOOR.chance) wanted.add(HallKind.BASE);
		if (random.nextDouble() < Level3Exit.CAR.chance) wanted.add(HallKind.CAR);
		if (random.nextDouble() < 0.18) wanted.add(HallKind.BNTG_STORAGE);
		if (random.nextDouble() < 0.14) wanted.add(HallKind.BLUE_SALVATION);
		if (random.nextDouble() < 0.14) wanted.add(HallKind.SCYLLA_COLONY);
		int ordinary = 2 + random.nextInt(2);
		for (int n = 0; n < ordinary; n++) {
			int roll = random.nextInt(100);
			wanted.add(roll < 30 ? HallKind.GENERATOR : roll < 55 ? HallKind.BOILER : roll < 82 ? HallKind.ASSEMBLY : HallKind.SANCTUM);
		}
		// Gamma goes in first, without touching the random draws: regions without it come out exactly as before.
		if (GammaPlan.in(seed, rx, rz)) {
			halls.add(new Hall(GammaPlan.I, GammaPlan.J, GammaPlan.W, GammaPlan.H, HallKind.GAMMA));
			for (int a = GammaPlan.I; a < GammaPlan.I + GammaPlan.W; a++) {
				for (int b = GammaPlan.J; b < GammaPlan.J + GammaPlan.H; b++) hallOf[cell(a, b)] = 0;
			}
		}
		for (HallKind kind : wanted) {
			int w, h;
			switch (kind) {
				case CAR -> {
					w = 1;
					h = 2;
				}
				case GENERATOR, BOILER, BLUE_SALVATION -> {
					w = 2 + (kind == HallKind.BLUE_SALVATION ? 0 : random.nextInt(2));
					h = 2;
				}
				case BNTG_STORAGE, SCYLLA_COLONY -> {
					w = 3;
					h = 2;
				}
				default -> {
					w = 3;
					h = 3;
				}
			}
			if (kind == HallKind.CAR && random.nextBoolean()) {
				int t = w;
				w = h;
				h = t;
			}
			for (int attempt = 0; attempt < 40; attempt++) {
				int i = 1 + random.nextInt(N - 1 - w), j = 1 + random.nextInt(N - 1 - h);
				if (!free(i - 1, j - 1, w + 2, h + 2)) continue;
				halls.add(new Hall(i, j, w, h, kind));
				for (int a = i; a < i + w; a++) {
					for (int b = j; b < j + h; b++) hallOf[cell(a, b)] = halls.size() - 1;
				}
				break;
			}
		}
	}

	private boolean free(int i, int j, int w, int h) {
		for (int a = i; a < i + w; a++) {
			for (int b = j; b < j + h; b++) {
				if (inside(a, b) && hallOf[cell(a, b)] >= 0) return false;
			}
		}
		return true;
	}

	/** Gates need room for a 2-high door: not in the crouch-height passages. */
	private boolean gateFits(int c, int d) {
		int i = c % N, j = c / N;
		return profile(i, j) != Level3Layout.Profile.LOW && profile(i + DX[d], j + DZ[d]) != Level3Layout.Profile.LOW;
	}

	private void carveMaze(SplittableRandom random) {
		boolean[] seen = new boolean[N * N];
		int[] came = new int[N * N];
		int[] stack = new int[N * N];
		int start;
		do {
			start = random.nextInt(N * N);
		} while (hallOf[start] >= 0);
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
			int d = ahead && random.nextInt(100) < 60 ? came[c] : choices[random.nextInt(count)];
			// Some of the passages the maze needs have bars with a gate in them; the lock can be picked.
			setEdge(c, d, random.nextInt(100) < 7 && gateFits(c, d) ? Edge.GATE : Edge.OPEN);
			int next = cell(i + DX[d], j + DZ[d]);
			seen[next] = true;
			came[next] = d;
			stack[top++] = next;
		}
	}

	// Extra connections, for loops and junctions; half of them are barred off for good.
	private void addLoops(SplittableRandom random) {
		for (int c = 0; c < N * N; c++) {
			int i = c % N, j = c / N;
			if (hallOf[c] >= 0) continue;
			for (int d : new int[] {EAST, SOUTH}) {
				if (!mazeCell(i + DX[d], j + DZ[d]) || passage(c, d)) continue;
				if (random.nextInt(100) < 14) setEdge(c, d, random.nextInt(100) < 50 ? Edge.BARS : Edge.OPEN);
			}
		}
	}

	private void openEdges() {
		for (int k : edgeOpenings(seed, rx, rz, EAST)) edges[cell(N - 1, k) * 4 + EAST] = Edge.OPEN;
		for (int k : edgeOpenings(seed, rx - 1, rz, EAST)) edges[cell(0, k) * 4 + WEST] = Edge.OPEN;
		for (int k : edgeOpenings(seed, rx, rz, SOUTH)) edges[cell(k, N - 1) * 4 + SOUTH] = Edge.OPEN;
		for (int k : edgeOpenings(seed, rx, rz - 1, SOUTH)) edges[cell(k, 0) * 4 + NORTH] = Edge.OPEN;
	}

	/** Where the east (or south) edge of a region opens into the next one; both sides ask the same question. */
	private static int[] edgeOpenings(long seed, int rx, int rz, int side) {
		SplittableRandom random = new SplittableRandom(Level3Layout.hash(seed, rx, rz, EDGE + side));
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

	// Every hall opens onto one or more maze cells around it, through a gap in its wall.
	private void openHalls(SplittableRandom random) {
		for (int h = 0; h < halls.size(); h++) {
			Hall hall = halls.get(h);
			// Gamma has its main gate on the north and three locked side doors on west, east and south.
			if (hall.kind() == HallKind.GAMMA) {
				edges[cell(GammaPlan.FRONT_I, GammaPlan.FRONT_J) * 4 + SOUTH] = Edge.HALL;
				edges[cell(GammaPlan.SIDE_WEST_I, GammaPlan.SIDE_WEST_J) * 4 + EAST] = Edge.HALL;
				edges[cell(GammaPlan.SIDE_EAST_I, GammaPlan.SIDE_EAST_J) * 4 + WEST] = Edge.HALL;
				edges[cell(GammaPlan.SIDE_SOUTH_I, GammaPlan.SIDE_SOUTH_J) * 4 + NORTH] = Edge.HALL;
				continue;
			}
			List<int[]> sides = new ArrayList<>();
			for (int a = hall.i(); a < hall.i() + hall.w(); a++) {
				for (int b = hall.j(); b < hall.j() + hall.h(); b++) {
					for (int d = 0; d < 4; d++) {
						int ma = a - DX[d], mb = b - DZ[d];
						if (mazeCell(ma, mb)) sides.add(new int[] {cell(ma, mb), d});
					}
				}
			}
			int openings = hall.kind() == HallKind.CAR ? 1 : 1 + random.nextInt(hall.w() * hall.h() >= 9 ? 3 : 2);
			for (int n = 0; n < openings && !sides.isEmpty(); n++) {
				int[] side = sides.remove(random.nextInt(sides.size()));
				edges[side[0] * 4 + side[1]] = Edge.HALL;
			}
		}
	}

	/**
	 * From the ways into each Base Gamma outpost, a walk back along passable passages, marking which way it lies. In the
	 * region that holds Gamma itself, the signs lead there instead, from anywhere in the region.
	 */
	private void placeBaseSigns() {
		boolean gamma = !halls.isEmpty() && halls.get(0).kind() == HallKind.GAMMA;
		HallKind target = gamma ? HallKind.GAMMA : HallKind.BASE;
		int depth = gamma ? N * N : SIGN_DEPTH;
		Arrays.fill(baseTo, (byte) -1);
		int[] seen = new int[N * N];
		Arrays.fill(seen, -1);
		int[] queue = new int[N * N];
		int head = 0, tail = 0;
		for (int c = 0; c < N * N; c++) {
			if (hallOf[c] >= 0) continue;
			for (int d = 0; d < 4; d++) {
				if (edges[c * 4 + d] != Edge.HALL) continue;
				int i = c % N + DX[d], j = c / N + DZ[d];
				if (!inside(i, j) || halls.get(hallOf[cell(i, j)]).kind() != target || seen[c] >= 0) continue;
				// In Gamma's region, signs always lead to the north main gate (side doors are locked from outside).
				if (gamma && (c % N != GammaPlan.FRONT_I || c / N != GammaPlan.FRONT_J)) continue;
				seen[c] = 0;
				baseTo[c] = (byte) d;
				queue[tail++] = c;
			}
		}
		while (head < tail) {
			int at = queue[head++];
			if (seen[at] >= depth) continue;
			int i = at % N, j = at / N;
			for (int way = 0; way < 4; way++) {
				Edge edge = edges[at * 4 + way];
				int a = i + DX[way], b = j + DZ[way];
				if (edge != Edge.OPEN && edge != Edge.GATE || !mazeCell(a, b)) continue;
				int next = cell(a, b);
				if (seen[next] >= 0) continue;
				seen[next] = seen[at] + 1;
				baseTo[next] = (byte) opposite(way);
				queue[tail++] = next;
			}
		}
	}

	private void placeElevators(SplittableRandom random) {
		if (random.nextDouble() >= Level3Exit.ELEVATOR.chance) return;
		for (int attempt = 0; attempt < 80; attempt++) {
			int c = random.nextInt(N * N);
			int axis = straightAxis(c);
			if (hallOf[c] >= 0 || axis < 0) continue;
			Level3Layout.Profile profile = profile(c % N, c / N);
			if (profile.clearance < 3) continue;
			int d = axis == 0 ? (random.nextBoolean() ? NORTH : SOUTH) : (random.nextBoolean() ? EAST : WEST);
			exits[c * 4 + d] = Level3Exit.ELEVATOR;
			return;
		}
	}
}
