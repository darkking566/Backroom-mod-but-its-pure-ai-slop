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
	static final int MARGIN = 2;
	static final int NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3;
	static final int[] DX = {0, 1, 0, -1}, DZ = {-1, 0, 1, 0};
	private static final int PLAN_SALT = 401, EDGE_SALT = 402, OMEGA_SALT = 409;
	private static final int OMEGA_SPREAD = 3;
	private static final ConcurrentHashMap<Key, Level4Plan> CACHE = new ConcurrentHashMap<>();

	enum ZoneKind {
		/** Wide open-plan office bay with regular structural pillars. */
		OPEN_OFFICE,
		/** Partitioned offices, meeting rooms, breakrooms, and copy rooms. */
		SUITE,
		/** Double-height rainy atrium with a central Almond Water fountain. */
		ATRIUM,
		/** Open-sky central rain shaft ringed by glass perimeter corridors. */
		LIGHTWELL,
		/** Unlit/flickering abandoned wing where rare entities lurk. */
		DARK_WING,
		/** Half-built bare-concrete office wing abandoned mid-renovation. */
		UNFINISHED_WING,
		/** M.E.G. Base Omega safe stronghold (with permanent Level 5 & 6 stairwells). */
		OMEGA_BASE,
		/** Amor Incrementum fungal greenhouse (hostile unless holding a religious item). */
		AMOR_CAMP,
		/** T.B.D. cluttered storage room (sells useless curios). */
		TBD_ROOM,
		/** Solid structural drywall core (houses elevators, exits, and windowed perimeter bays). */
		SOLID_CORE,
		/** Open outdoor misty rain canyon separating office skyscrapers. */
		OUTSIDE
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
	final int[] balconyDir = new int[N * N];
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
		Arrays.fill(balconyDir, -1);
		Arrays.fill(edges, Edge.OPEN);

		// Carve a 2-cell outdoor misty canyon around the 12x12 skyscraper core, keeping 2 skybridges per side
		for (int i = 0; i < N; i++) {
			for (int j = 0; j < N; j++) {
				if (i < MARGIN || i >= N - MARGIN || j < MARGIN || j >= N - MARGIN) {
					zones[cell(i, j)] = ZoneKind.OUTSIDE;
				}
			}
		}
		for (int k : edgeOpenings(seed, rx, rz, EAST)) {
			zones[cell(N - 2, k)] = ZoneKind.OPEN_OFFICE;
			zones[cell(N - 1, k)] = ZoneKind.OPEN_OFFICE;
		}
		for (int k : edgeOpenings(seed, rx - 1, rz, EAST)) {
			zones[cell(0, k)] = ZoneKind.OPEN_OFFICE;
			zones[cell(1, k)] = ZoneKind.OPEN_OFFICE;
		}
		for (int k : edgeOpenings(seed, rx, rz, SOUTH)) {
			zones[cell(k, N - 2)] = ZoneKind.OPEN_OFFICE;
			zones[cell(k, N - 1)] = ZoneKind.OPEN_OFFICE;
		}
		for (int k : edgeOpenings(seed, rx, rz - 1, SOUTH)) {
			zones[cell(k, 0)] = ZoneKind.OPEN_OFFICE;
			zones[cell(k, 1)] = ZoneKind.OPEN_OFFICE;
		}

		this.hasOmega = isOmegaRegion(seed, rx, rz);
		boolean omegaPlaced = hasOmega && placeBox(rng, 5, 5, ZoneKind.OMEGA_BASE, 5, 5);
		this.hasAtrium = rng.nextInt(100) < 55 && placeExteriorAtrium(rng);
		if (rng.nextInt(100) < 42) placeLightwell(rng);
		this.hasAmor = !hasOmega && rng.nextInt(100) < 24 && placeBox(rng, 3, 3, ZoneKind.AMOR_CAMP, -1, -1);
		this.hasTbd = rng.nextInt(100) < 28 && placeBox(rng, 2, 3, ZoneKind.TBD_ROOM, -1, -1);
		if (rng.nextInt(100) < 42) placeBox(rng, 3, 3, ZoneKind.DARK_WING, -1, -1);
		if (rng.nextInt(100) < 38) placeBox(rng, rng.nextBoolean() ? 3 : 2, 2, ZoneKind.UNFINISHED_WING, -1, -1);

		// Add 2 structural solid cores inside the open office tower
		for (int k = 0; k < 2; k++) placeBox(rng, 1 + rng.nextInt(2), 1, ZoneKind.SOLID_CORE, -1, -1);

		// Carve 3..4 enclosed office suites / meeting rooms / breakrooms
		int suites = 3 + rng.nextInt(2);
		for (int k = 0; k < suites; k++) {
			int w = 1 + rng.nextInt(2), h = 1 + rng.nextInt(2);
			placeBox(rng, w, h, ZoneKind.SUITE, -1, -1);
		}

		buildEdges(rng, seed, rx, rz);
		ensureConnected(rng);
		placeExits(rng, omegaPlaced);
		placeBalconies(rng);
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
		return inside(i, j) && zones[cell(i, j)] != ZoneKind.SOLID_CORE && zones[cell(i, j)] != ZoneKind.OUTSIDE;
	}

	static boolean isSkybridgeCell(int i, int j) {
		return i < MARGIN || i >= N - MARGIN || j < MARGIN || j >= N - MARGIN;
	}

	boolean nearHumanBase(int i, int j) {
		for (RoomBox room : rooms) {
			int pad = switch (room.kind()) {
				case OMEGA_BASE -> 3;
				case AMOR_CAMP, TBD_ROOM -> 2;
				default -> -1;
			};
			if (pad < 0) continue;
			if (i >= room.i() - pad && i < room.i() + room.w() + pad
					&& j >= room.j() - pad && j < room.j() + room.h() + pad) {
				return true;
			}
		}
		return false;
	}

	Edge edge(int c, int d) {
		return edges[c * 4 + d];
	}

	private void setEdge(int c, int d, Edge e) {
		edges[c * 4 + d] = e;
		int i = c % N + DX[d], j = c / N + DZ[d];
		if (inside(i, j)) edges[cell(i, j) * 4 + opposite(d)] = e;
	}

	private boolean placeLightwell(SplittableRandom rng) {
		for (int attempt = 0; attempt < 30; attempt++) {
			int i = (MARGIN + 1) + rng.nextInt((N - MARGIN - 1) - (MARGIN + 1) - 3 + 1);
			int j = (MARGIN + 1) + rng.nextInt((N - MARGIN - 1) - (MARGIN + 1) - 3 + 1);
			if (!free(i - 1, j - 1, 5, 5)) continue;
			int id = rooms.size();
			rooms.add(new RoomBox(i, j, 3, 3, ZoneKind.LIGHTWELL));
			for (int a = i; a < i + 3; a++) {
				for (int b = j; b < j + 3; b++) {
					int c = cell(a, b);
					if (a == i + 1 && b == j + 1) {
						zones[c] = ZoneKind.OUTSIDE;
						roomId[c] = -1;
					} else {
						zones[c] = ZoneKind.LIGHTWELL;
						roomId[c] = id;
					}
				}
			}
			return true;
		}
		return false;
	}

	private void placeBalconies(SplittableRandom rng) {
		for (int i = MARGIN; i < N - MARGIN; i++) {
			for (int j = MARGIN; j < N - MARGIN; j++) {
				int dir = -1;
				if (i == MARGIN) dir = WEST;
				else if (i == N - MARGIN - 1) dir = EAST;
				else if (j == MARGIN) dir = NORTH;
				else if (j == N - MARGIN - 1) dir = SOUTH;
				else continue;

				int c = cell(i, j);
				if (zones[c] != ZoneKind.OPEN_OFFICE || nearHumanBase(i, j)) continue;
				int ni = i + DX[dir], nj = j + DZ[dir];
				if (!inside(ni, nj) || zones[cell(ni, nj)] != ZoneKind.OUTSIDE) continue;
				boolean hasExit = false;
				for (PlacedExit pe : exits) {
					if (pe.cell() == c) {
						hasExit = true;
						break;
					}
				}
				if (hasExit) continue;

				boolean openSides = true;
				for (int d = 0; d < 4; d++) {
					if (d == dir) continue;
					if (edge(c, d) != Edge.OPEN) {
						openSides = false;
						break;
					}
					int ai = i + DX[d], aj = j + DZ[d];
					if (inside(ai, aj) && balconyDir[cell(ai, aj)] >= 0) {
						openSides = false;
						break;
					}
				}
				if (!openSides) continue;
				if (rng.nextInt(100) < 22) balconyDir[c] = dir;
			}
		}
	}

	private boolean placeExteriorAtrium(SplittableRandom rng) {
		int[][] candidates = {
				{MARGIN, MARGIN + 1}, {MARGIN, N - MARGIN - 4},
				{N - MARGIN - 3, MARGIN + 1}, {N - MARGIN - 3, N - MARGIN - 4},
				{MARGIN + 1, MARGIN}, {N - MARGIN - 4, MARGIN},
				{MARGIN + 1, N - MARGIN - 3}, {N - MARGIN - 4, N - MARGIN - 3}
		};
		for (int attempt = 0; attempt < 8; attempt++) {
			int[] pick = candidates[rng.nextInt(candidates.length)];
			int i = pick[0], j = pick[1];
			if (!freeIndoor(i, j, 3, 3)) continue;
			int id = rooms.size();
			rooms.add(new RoomBox(i, j, 3, 3, ZoneKind.ATRIUM));
			for (int a = i; a < i + 3; a++) {
				for (int b = j; b < j + 3; b++) {
					int c = cell(a, b);
					zones[c] = ZoneKind.ATRIUM;
					roomId[c] = id;
				}
			}
			return true;
		}
		return placeBox(rng, 3, 3, ZoneKind.ATRIUM, -1, -1);
	}

	private boolean freeIndoor(int i, int j, int w, int h) {
		for (int a = i - 1; a <= i + w; a++) {
			for (int b = j - 1; b <= j + h; b++) {
				if (!inside(a, b)) return false;
				ZoneKind z = zones[cell(a, b)];
				if (a >= i && a < i + w && b >= j && b < j + h) {
					if (z != ZoneKind.OPEN_OFFICE || isSkybridgeCell(a, b)) return false;
				} else if (z != ZoneKind.OPEN_OFFICE && z != ZoneKind.OUTSIDE) {
					return false;
				}
			}
		}
		return true;
	}

	private boolean placeBox(SplittableRandom rng, int w, int h, ZoneKind kind, int fixedI, int fixedJ) {
		int tries = fixedI >= 0 ? 1 : 40;
		for (int attempt = 0; attempt < tries; attempt++) {
			int i = fixedI >= 0 ? fixedI : (MARGIN + 1) + rng.nextInt((N - MARGIN - 1) - (MARGIN + 1) - w + 1);
			int j = fixedJ >= 0 ? fixedJ : (MARGIN + 1) + rng.nextInt((N - MARGIN - 1) - (MARGIN + 1) - h + 1);
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
		// Region outer boundaries are walls except for the 2 skybridges per side
		for (int i = 0; i < N; i++) {
			edges[cell(i, 0) * 4 + NORTH] = zones[cell(i, 0)] == ZoneKind.OUTSIDE ? Edge.WALL : Edge.OPEN;
			edges[cell(i, N - 1) * 4 + SOUTH] = zones[cell(i, N - 1)] == ZoneKind.OUTSIDE ? Edge.WALL : Edge.OPEN;
			edges[cell(0, i) * 4 + WEST] = zones[cell(0, i)] == ZoneKind.OUTSIDE ? Edge.WALL : Edge.OPEN;
			edges[cell(N - 1, i) * 4 + EAST] = zones[cell(N - 1, i)] == ZoneKind.OUTSIDE ? Edge.WALL : Edge.OPEN;
		}

		// Walls around rooms, cores, factions, and outdoor canyon perimeter
		for (int c = 0; c < N * N; c++) {
			int i = c % N, j = c / N;
			ZoneKind z = zones[c];
			for (int d : new int[] {EAST, SOUTH}) {
				int a = i + DX[d], b = j + DZ[d];
				if (!inside(a, b)) continue;
				int nc = cell(a, b);
				ZoneKind nz = zones[nc];
				if (z == ZoneKind.OUTSIDE && nz == ZoneKind.OUTSIDE) {
					setEdge(c, d, Edge.OPEN);
				} else if (z == ZoneKind.OUTSIDE || nz == ZoneKind.OUTSIDE) {
					setEdge(c, d, Edge.WALL);
				} else if (z == ZoneKind.SOLID_CORE || nz == ZoneKind.SOLID_CORE) {
					setEdge(c, d, Edge.WALL);
				} else if (roomId[c] != roomId[nc]) {
					boolean glass = (z == ZoneKind.SUITE || nz == ZoneKind.SUITE) && rng.nextInt(100) < 45;
					setEdge(c, d, glass ? Edge.GLASS_WALL : Edge.WALL);
				} else if (z == ZoneKind.OPEN_OFFICE && !isSkybridgeCell(i, j) && !isSkybridgeCell(a, b) && rng.nextInt(100) < 6) {
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
			if (room.kind() == ZoneKind.ATRIUM || room.kind() == ZoneKind.LIGHTWELL) {
				// Wide open archways into the atrium or lightwell ring (only toward indoor walkable neighbors)
				if (walkable(room.cx(), room.j() - 1)) setEdge(cell(room.cx(), room.j()), NORTH, Edge.OPEN);
				if (walkable(room.cx(), room.j() + room.h())) setEdge(cell(room.cx(), room.j() + room.h() - 1), SOUTH, Edge.OPEN);
				if (walkable(room.i() - 1, room.cz())) setEdge(cell(room.i(), room.cz()), WEST, Edge.OPEN);
				if (walkable(room.i() + room.w(), room.cz())) setEdge(cell(room.i() + room.w() - 1, room.cz()), EAST, Edge.OPEN);
				continue;
			}
			if (room.kind() == ZoneKind.DARK_WING || room.kind() == ZoneKind.UNFINISHED_WING) {
				if (walkable(room.cx(), room.j() - 1)) setEdge(cell(room.cx(), room.j()), NORTH, Edge.OPEN);
				if (walkable(room.i() - 1, room.cz())) setEdge(cell(room.i(), room.cz()), WEST, Edge.OPEN);
				if (room.kind() == ZoneKind.UNFINISHED_WING && walkable(room.i() + room.w(), room.cz())) {
					setEdge(cell(room.i() + room.w() - 1, room.cz()), EAST, Edge.OPEN);
				}
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
		int a = 3 + rng.nextInt(4);
		int b = 9 + rng.nextInt(4);
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
				if (zones[start] == ZoneKind.SOLID_CORE || zones[start] == ZoneKind.OUTSIDE || comp[start] >= 0) continue;
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
		java.util.Set<Integer> usedCells = new java.util.HashSet<>();
		if (omegaPlaced) {
			for (RoomBox room : rooms) {
				if (room.kind() != ZoneKind.OMEGA_BASE) continue;
				// Permanent Level 5 & Level 6 stairwells inside Omega Base's corners
				int c5 = cell(room.i(), room.j());
				int c6 = cell(room.i() + room.w() - 1, room.j());
				exits.add(new PlacedExit(c5, NORTH, Level4Exit.OMEGA_STAIRS_5, true));
				exits.add(new PlacedExit(c6, NORTH, Level4Exit.OMEGA_STAIRS_6, true));
				usedCells.add(c5);
				usedCells.add(c6);
			}
		}

		List<int[]> wallSpots = new ArrayList<>();
		List<Integer> floorSpots = new ArrayList<>();
		for (int i = MARGIN + 1; i < N - MARGIN - 1; i++) {
			for (int j = MARGIN + 1; j < N - MARGIN - 1; j++) {
				int c = cell(i, j);
				ZoneKind z = zones[c];
				if (z != ZoneKind.OPEN_OFFICE && z != ZoneKind.SUITE && z != ZoneKind.DARK_WING) continue;
				floorSpots.add(c);
				for (int d = 0; d < 4; d++) {
					int ni = i + DX[d], nj = j + DZ[d];
					if (inside(ni, nj) && zones[cell(ni, nj)] == ZoneKind.OUTSIDE) continue;
					if (edge(c, d) == Edge.WALL) wallSpots.add(new int[] {c, d});
				}
			}
		}
		if (wallSpots.isEmpty()) return;

		for (Level4Exit ex : Level4Exit.values()) {
			if (ex == Level4Exit.OMEGA_STAIRS_5 || ex == Level4Exit.OMEGA_STAIRS_6) continue;
			if (rng.nextDouble() >= ex.chance) continue;
			if (ex == Level4Exit.HATCH_2) {
				while (!floorSpots.isEmpty()) {
					int c = floorSpots.remove(rng.nextInt(floorSpots.size()));
					if (usedCells.add(c)) {
						exits.add(new PlacedExit(c, NORTH, ex, false));
						break;
					}
				}
			} else {
				while (!wallSpots.isEmpty()) {
					int[] spot = wallSpots.remove(rng.nextInt(wallSpots.size()));
					if (usedCells.add(spot[0])) {
						exits.add(new PlacedExit(spot[0], spot[1], ex, !ex.vanishing));
						break;
					}
				}
			}
		}
	}
}
