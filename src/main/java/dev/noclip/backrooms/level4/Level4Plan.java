package dev.noclip.backrooms.level4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure deterministic layout plan for one 112x112 region of Level 4 ("Abandoned Office"):
 * 16x16 cells of 7x7 blocks, merging open-plan office bays with structural columns, glass-partitioned suites,
 * double-height rainy atriums with Almond Water fountains, dark wings, and three faction bases
 * (M.E.G. Base Omega, Amor Incrementum, and T.B.D.).
 */
final class Level4Plan {
	static final int N = 16, CELL = 7, SIZE = N * CELL;
	static final int NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3;
	static final int[] DX = {0, 1, 0, -1}, DZ = {-1, 0, 1, 0};
	private static final int PLAN_SALT = 401, EDGE_SALT = 402, OMEGA_SALT = 409;
	private static final int OMEGA_SPREAD = 2;
	private static final ConcurrentHashMap<Key, Level4Plan> CACHE = new ConcurrentHashMap<>();

	enum ZoneKind {
		/** Wide open-plan office bay with regular structural pillars. */
		OPEN_OFFICE,
		/** Partitioned offices, meeting rooms, breakrooms, and copy rooms. */
		SUITE,
		/** Double-height rainy atrium with a central Almond Water fountain. */
		ATRIUM,
		/** Unlit/flickering abandoned wing where rare entities lurk. */
		DARK_WING,
		/** M.E.G. Base Omega safe stronghold (with permanent Level 5 & 6 stairwells). */
		OMEGA_BASE,
		/** Amor Incrementum fungal greenhouse (hostile unless holding a religious item). */
		AMOR_CAMP,
		/** T.B.D. cluttered storage room (sells useless curios). */
		TBD_ROOM,
		/** Solid structural drywall core (houses elevators, exits, and windowed perimeter bays). */
		SOLID_CORE
	}

	enum Edge {
		WALL,
		GLASS_WALL,
		OPEN,
		DOOR
	}

	record RoomBox(int i, int j, int w, int h, ZoneKind kind) {
		int cx() {
			return i + w / 2;
		}

		int cz() {
			return j + h / 2;
		}
	}

	record PlacedExit(int cell, int dir, Level4Exit exit, boolean permanent) {
	}

	private record Key(long seed, int rx, int rz) {
	}

	final ZoneKind[] zones = new ZoneKind[N * N];
	final int[] roomId = new int[N * N];
	final Edge[] edges = new Edge[N * N * 4];
	final List<RoomBox> rooms = new ArrayList<>();
	final List<PlacedExit> exits = new ArrayList<>();
	final boolean hasOmega;
	final boolean hasAmor;
	final boolean hasTbd;
	final boolean hasAtrium;

	static Level4Plan of(long seed, int rx, int rz) {
		if (CACHE.size() > 512) CACHE.clear();
		return CACHE.computeIfAbsent(new Key(seed, rx, rz), k -> new Level4Plan(seed, rx, rz));
	}

	private Level4Plan(long seed, int rx, int rz) {
		SplittableRandom rng = new SplittableRandom(Level4Layout.hash(seed, rx, rz, PLAN_SALT));
		Arrays.fill(zones, ZoneKind.OPEN_OFFICE);
		Arrays.fill(roomId, -1);
		Arrays.fill(edges, Edge.OPEN);

		this.hasOmega = isOmegaRegion(seed, rx, rz);
		boolean omegaPlaced = hasOmega && placeBox(rng, 3, 3, ZoneKind.OMEGA_BASE, 5, 5);
		this.hasAtrium = rng.nextInt(100) < 55 && placeBox(rng, 3, 3, ZoneKind.ATRIUM, -1, -1);
		this.hasAmor = !hasOmega && rng.nextInt(100) < 34 && placeBox(rng, 2, 2, ZoneKind.AMOR_CAMP, -1, -1);
		this.hasTbd = rng.nextInt(100) < 34 && placeBox(rng, 2, 2, ZoneKind.TBD_ROOM, -1, -1);
		if (rng.nextInt(100) < 42) placeBox(rng, 3, 3, ZoneKind.DARK_WING, -1, -1);

		// Add 1..2 structural solid cores inside the open office for elevators, exits, and window walls
		int cores = 2 + rng.nextInt(2);
		for (int k = 0; k < cores; k++) placeBox(rng, 1 + rng.nextInt(2), 1, ZoneKind.SOLID_CORE, -1, -1);

		// Carve 3..5 enclosed office suites / meeting rooms / breakrooms
		int suites = 3 + rng.nextInt(3);
		for (int k = 0; k < suites; k++) {
			int w = 1 + rng.nextInt(2), h = 1 + rng.nextInt(2);
			placeBox(rng, w, h, ZoneKind.SUITE, -1, -1);
		}

		buildEdges(rng, seed, rx, rz);
		ensureConnected(rng);
		placeExits(rng, omegaPlaced);
	}

	static boolean isOmegaRegion(long seed, int rx, int rz) {
		int bx = Math.floorDiv(rx, OMEGA_SPREAD), bz = Math.floorDiv(rz, OMEGA_SPREAD);
		int pick = Math.floorMod(Level4Layout.hash(seed, bx, bz, OMEGA_SALT), OMEGA_SPREAD * OMEGA_SPREAD);
		return Math.floorMod(rx, OMEGA_SPREAD) == pick % OMEGA_SPREAD
				&& Math.floorMod(rz, OMEGA_SPREAD) == pick / OMEGA_SPREAD;
	}

	static int cell(int i, int j) {
		return j * N + i;
	}

	static int opposite(int d) {
		return (d + 2) & 3;
	}

	static boolean inside(int i, int j) {
		return i >= 0 && j >= 0 && i < N && j < N;
	}

	boolean walkable(int i, int j) {
		return inside(i, j) && zones[cell(i, j)] != ZoneKind.SOLID_CORE;
	}

	Edge edge(int c, int d) {
		return edges[c * 4 + d];
	}

	private void setEdge(int c, int d, Edge e) {
		edges[c * 4 + d] = e;
		int i = c % N + DX[d], j = c / N + DZ[d];
		if (inside(i, j)) edges[cell(i, j) * 4 + opposite(d)] = e;
	}

	private boolean placeBox(SplittableRandom rng, int w, int h, ZoneKind kind, int fixedI, int fixedJ) {
		int tries = fixedI >= 0 ? 1 : 40;
		for (int attempt = 0; attempt < tries; attempt++) {
			int i = fixedI >= 0 ? fixedI : 1 + rng.nextInt(N - 1 - w);
			int j = fixedJ >= 0 ? fixedJ : 1 + rng.nextInt(N - 1 - h);
			if (!free(i - 1, j - 1, w + 2, h + 2)) continue;
			int id = rooms.size();
			rooms.add(new RoomBox(i, j, w, h, kind));
			for (int a = i; a < i + w; a++) {
				for (int b = j; b < j + h; b++) {
					int c = cell(a, b);
					zones[c] = kind;
					roomId[c] = id;
				}
			}
			return true;
		}
		return false;
	}

	private boolean free(int i, int j, int w, int h) {
		for (int a = i; a < i + w; a++) {
			for (int b = j; b < j + h; b++) {
				if (!inside(a, b)) return false;
				if (zones[cell(a, b)] != ZoneKind.OPEN_OFFICE) return false;
			}
		}
		return true;
	}

	private void buildEdges(SplittableRandom rng, long seed, int rx, int rz) {
		// Region outer boundaries are walls except for 2 deterministic open corridors per side
		for (int i = 0; i < N; i++) {
			edges[cell(i, 0) * 4 + NORTH] = Edge.WALL;
			edges[cell(i, N - 1) * 4 + SOUTH] = Edge.WALL;
			edges[cell(0, i) * 4 + WEST] = Edge.WALL;
			edges[cell(N - 1, i) * 4 + EAST] = Edge.WALL;
		}
		for (int k : edgeOpenings(seed, rx, rz, EAST)) edges[cell(N - 1, k) * 4 + EAST] = Edge.OPEN;
		for (int k : edgeOpenings(seed, rx - 1, rz, EAST)) edges[cell(0, k) * 4 + WEST] = Edge.OPEN;
		for (int k : edgeOpenings(seed, rx, rz, SOUTH)) edges[cell(k, N - 1) * 4 + SOUTH] = Edge.OPEN;
		for (int k : edgeOpenings(seed, rx, rz - 1, SOUTH)) edges[cell(k, 0) * 4 + NORTH] = Edge.OPEN;

		// Walls around rooms, cores, and factions
		for (int c = 0; c < N * N; c++) {
			int i = c % N, j = c / N;
			ZoneKind z = zones[c];
			for (int d : new int[] {EAST, SOUTH}) {
				int a = i + DX[d], b = j + DZ[d];
				if (!inside(a, b)) continue;
				int nc = cell(a, b);
				ZoneKind nz = zones[nc];
				if (z == ZoneKind.SOLID_CORE || nz == ZoneKind.SOLID_CORE) {
					setEdge(c, d, Edge.WALL);
				} else if (roomId[c] != roomId[nc]) {
					boolean glass = (z == ZoneKind.SUITE || nz == ZoneKind.SUITE) && rng.nextInt(100) < 45;
					setEdge(c, d, glass ? Edge.GLASS_WALL : Edge.WALL);
				} else if (z == ZoneKind.OPEN_OFFICE && rng.nextInt(100) < 6) {
					// Occasional partial drywall/glass divider in open office
					setEdge(c, d, rng.nextBoolean() ? Edge.GLASS_WALL : Edge.WALL);
				}
			}
		}

		// Open doorways into each placed room/atrium/faction
		for (RoomBox room : rooms) {
			if (room.kind() == ZoneKind.SOLID_CORE) continue;
			if (room.kind() == ZoneKind.OMEGA_BASE) {
				// 4 cardinal entrances into Omega Base
				setEdge(cell(room.cx(), room.j()), NORTH, Edge.OPEN);
				setEdge(cell(room.cx(), room.j() + room.h() - 1), SOUTH, Edge.OPEN);
				setEdge(cell(room.i(), room.cz()), WEST, Edge.OPEN);
				setEdge(cell(room.i() + room.w() - 1, room.cz()), EAST, Edge.OPEN);
				continue;
			}
			if (room.kind() == ZoneKind.ATRIUM) {
				// Wide open archways into the atrium
				for (int a = room.i(); a < room.i() + room.w(); a++) {
					if (a == room.cx()) {
						setEdge(cell(a, room.j()), NORTH, Edge.OPEN);
						setEdge(cell(a, room.j() + room.h() - 1), SOUTH, Edge.OPEN);
					}
				}
				for (int b = room.j(); b < room.j() + room.h(); b++) {
					if (b == room.cz()) {
						setEdge(cell(room.i(), b), WEST, Edge.OPEN);
						setEdge(cell(room.i() + room.w() - 1, b), EAST, Edge.OPEN);
					}
				}
				continue;
			}
			if (room.kind() == ZoneKind.DARK_WING) {
				setEdge(cell(room.cx(), room.j()), NORTH, Edge.OPEN);
				setEdge(cell(room.i(), room.cz()), WEST, Edge.OPEN);
				continue;
			}
			// Suites, Amor Camp, T.B.D. Room get 1..2 fire doors
			List<int[]> sides = new ArrayList<>();
			for (int a = room.i(); a < room.i() + room.w(); a++) {
				if (walkable(a, room.j() - 1)) sides.add(new int[] {cell(a, room.j()), NORTH});
				if (walkable(a, room.j() + room.h())) sides.add(new int[] {cell(a, room.j() + room.h() - 1), SOUTH});
			}
			for (int b = room.j(); b < room.j() + room.h(); b++) {
				if (walkable(room.i() - 1, b)) sides.add(new int[] {cell(room.i(), b), WEST});
				if (walkable(room.i() + room.w(), b)) sides.add(new int[] {cell(room.i() + room.w() - 1, b), EAST});
			}
			int doors = (room.kind() == ZoneKind.SUITE && sides.size() > 1 && rng.nextBoolean()) ? 2 : 1;
			for (int n = 0; n < doors && !sides.isEmpty(); n++) {
				int[] pick = sides.remove(rng.nextInt(sides.size()));
				setEdge(pick[0], pick[1], Edge.DOOR);
			}
		}
	}

	private static int[] edgeOpenings(long seed, int rx, int rz, int side) {
		SplittableRandom rng = new SplittableRandom(Level4Layout.hash(seed, rx, rz, EDGE_SALT + side));
		int a = 1 + rng.nextInt(5);
		int b = 9 + rng.nextInt(5);
		return new int[] {a, b};
	}

	/** Ensures every walkable cell in the region reaches every other walkable cell. */
	private void ensureConnected(SplittableRandom rng) {
		int[] comp = new int[N * N];
		int[] queue = new int[N * N];
		while (true) {
			Arrays.fill(comp, -1);
			int nextComp = 0;
			for (int start = 0; start < N * N; start++) {
				if (zones[start] == ZoneKind.SOLID_CORE || comp[start] >= 0) continue;
				int head = 0, tail = 0;
				queue[tail++] = start;
				comp[start] = nextComp;
				while (head < tail) {
					int c = queue[head++];
					int i = c % N, j = c / N;
					for (int d = 0; d < 4; d++) {
						Edge e = edge(c, d);
						if (e != Edge.OPEN && e != Edge.DOOR) continue;
						int a = i + DX[d], b = j + DZ[d];
						if (!walkable(a, b)) continue;
						int nc = cell(a, b);
						if (comp[nc] < 0) {
							comp[nc] = nextComp;
							queue[tail++] = nc;
						}
					}
				}
				nextComp++;
			}
			if (nextComp <= 1) break;
			List<int[]> bridges = new ArrayList<>();
			for (int c = 0; c < N * N; c++) {
				if (comp[c] < 0) continue;
				int i = c % N, j = c / N;
				for (int d : new int[] {EAST, SOUTH}) {
					int a = i + DX[d], b = j + DZ[d];
					if (!walkable(a, b)) continue;
					int nc = cell(a, b);
					if (comp[nc] != comp[c]) bridges.add(new int[] {c, d});
				}
			}
			if (bridges.isEmpty()) break;
			int[] b = bridges.get(rng.nextInt(bridges.size()));
			setEdge(b[0], b[1], Edge.DOOR);
		}
	}

	private void placeExits(SplittableRandom rng, boolean omegaPlaced) {
		if (omegaPlaced) {
			for (RoomBox room : rooms) {
				if (room.kind() != ZoneKind.OMEGA_BASE) continue;
				// Permanent Level 5 & Level 6 stairwells inside Omega Base's corners
				exits.add(new PlacedExit(cell(room.i(), room.j()), NORTH, Level4Exit.OMEGA_STAIRS_5, true));
				exits.add(new PlacedExit(cell(room.i() + room.w() - 1, room.j()), NORTH, Level4Exit.OMEGA_STAIRS_6, true));
			}
		}

		List<int[]> wallSpots = new ArrayList<>();
		List<Integer> floorSpots = new ArrayList<>();
		for (int i = 1; i < N - 1; i++) {
			for (int j = 1; j < N - 1; j++) {
				int c = cell(i, j);
				ZoneKind z = zones[c];
				if (z != ZoneKind.OPEN_OFFICE && z != ZoneKind.SUITE && z != ZoneKind.DARK_WING) continue;
				floorSpots.add(c);
				for (int d = 0; d < 4; d++) {
					if (edge(c, d) == Edge.WALL) wallSpots.add(new int[] {c, d});
				}
			}
		}
		if (wallSpots.isEmpty()) return;

		for (Level4Exit ex : Level4Exit.values()) {
			if (ex == Level4Exit.OMEGA_STAIRS_5 || ex == Level4Exit.OMEGA_STAIRS_6) continue;
			if (rng.nextDouble() >= ex.chance) continue;
			if (ex == Level4Exit.HATCH_2) {
				if (!floorSpots.isEmpty()) {
					int c = floorSpots.remove(rng.nextInt(floorSpots.size()));
					exits.add(new PlacedExit(c, NORTH, ex, false));
				}
			} else if (!wallSpots.isEmpty()) {
				int[] spot = wallSpots.remove(rng.nextInt(wallSpots.size()));
				exits.add(new PlacedExit(spot[0], spot[1], ex, !ex.vanishing));
			}
		}
	}
}
