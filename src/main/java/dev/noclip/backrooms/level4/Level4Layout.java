package dev.noclip.backrooms.level4;

import static dev.noclip.backrooms.level4.Level4Plan.CELL;
import static dev.noclip.backrooms.level4.Level4Plan.N;
import static dev.noclip.backrooms.level4.Level4Plan.SIZE;

import dev.noclip.backrooms.level4.Level4Plan.Edge;
import dev.noclip.backrooms.level4.Level4Plan.PlacedExit;
import dev.noclip.backrooms.level4.Level4Plan.RoomBox;
import dev.noclip.backrooms.level4.Level4Plan.ZoneKind;

/**
 * Pure block-less coordinate queries for Level 4. Given (seed, x, z), returns what kind of column stands there,
 * which direction it faces, ceiling height, and safe landing points for {@link Level4Travel} and unit tests.
 */
final class Level4Layout {
	static final int FLOOR_Y = 32;
	static final int OPEN_HEIGHT = 4;
	static final int ATRIUM_HEIGHT = 8;

	enum Kind {
		AIR,
		OUTSIDE_AIR,
		ATRIUM_ARCH,
		PILLAR,
		DRYWALL,
		GLASS_WALL,
		OMEGA_WALL,
		OMEGA_SIGN,
		WINDOW_BLACKED,
		WINDOW_TRAP,
		WINDOW_RAIN,
		FIRE_DOOR,
		EXIT_DOOR,
		EXIT_STRUCTURE,
		EXIT_HATCH,
		WATER_COOLER,
		FOUNTAIN_BASIN,
		FOUNTAIN_POOL,
		VENDING,
		OMEGA_COT,
		OMEGA_LOCKER,
		OMEGA_BARRICADE,
		AMOR_PLANTER,
		AMOR_PLANTER_L1,
		AMOR_PLANTER_VEIL,
		TBD_SHELF,
		TBD_HOARD,
		BREAKER,
		PHOTOCOPIER,
		DESK_PHONE
	}

	record ExitStruct(Level4Exit exit, ZoneKind zone, int dir, int depth, int along) {
	}

	record Tile(Kind kind, ZoneKind zone, int dir, int ceilingHeight, Level4Exit exit, boolean exitSign) {
		boolean walkable() {
			return kind == Kind.AIR || kind == Kind.ATRIUM_ARCH || kind == Kind.FIRE_DOOR || kind == Kind.EXIT_HATCH;
		}
	}

	private Level4Layout() {
	}

	static long hash(long seed, int a, int b, int salt) {
		long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xBF58476D1CE4E5B9L) ^ (salt * 0x94D049BB133111EBL);
		h ^= h >>> 30;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 27;
		h *= 0x94D049BB133111EBL;
		return h ^ (h >>> 31);
	}

	static Level4Plan plan(long seed, int x, int z) {
		return Level4Plan.of(seed, Math.floorDiv(x, SIZE), Math.floorDiv(z, SIZE));
	}

	static ExitStruct exitStructure(long seed, int x, int z) {
		int rx = Math.floorDiv(x, SIZE), rz = Math.floorDiv(z, SIZE);
		Level4Plan plan = Level4Plan.of(seed, rx, rz);
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		int i = lx / CELL, j = lz / CELL;
		int cx = lx % CELL, cz = lz % CELL;
		int c = Level4Plan.cell(i, j);
		for (PlacedExit pe : plan.exits) {
			if (pe.cell() != c || !pe.exit().hasStructure()) continue;
			int depth = switch (pe.dir()) {
				case Level4Plan.NORTH -> cz;
				case Level4Plan.SOUTH -> (CELL - 1) - cz;
				case Level4Plan.WEST -> cx;
				default -> (CELL - 1) - cx;
			};
			int along = (pe.dir() == Level4Plan.NORTH || pe.dir() == Level4Plan.SOUTH) ? cx : cz;
			if (pe.exit() == Level4Exit.ELEVATOR_3) {
				if (along >= 2 && along <= 4 && depth >= 0 && depth <= 1) {
					return new ExitStruct(pe.exit(), plan.zones[c], pe.dir(), depth, along);
				}
			} else {
				boolean inFootprint = (along >= 2 && along <= 4 && depth >= 0 && depth <= 2)
						|| (along == 3 && depth == 3);
				if (inFootprint) {
					return new ExitStruct(pe.exit(), plan.zones[c], pe.dir(), depth, along);
				}
			}
		}
		return null;
	}

	static Tile tile(long seed, int x, int z) {
		int rx = Math.floorDiv(x, SIZE), rz = Math.floorDiv(z, SIZE);
		Level4Plan plan = Level4Plan.of(seed, rx, rz);
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		int i = lx / CELL, j = lz / CELL;
		int cx = lx % CELL, cz = lz % CELL;
		int c = Level4Plan.cell(i, j);
		ZoneKind zone = plan.zones[c];
		int ceilH = zone == ZoneKind.ATRIUM ? ATRIUM_HEIGHT : OPEN_HEIGHT;

		if (zone == ZoneKind.SOLID_CORE) {
			return new Tile(Kind.DRYWALL, zone, Level4Plan.NORTH, ceilH, null, false);
		}
		if (zone == ZoneKind.OUTSIDE) {
			return outsideTile(seed, plan, i, j, cx, cz, x, z);
		}

		ExitStruct es = exitStructure(seed, x, z);
		if (es != null) {
			int doorDepth = (es.exit() == Level4Exit.ELEVATOR_3 || es.exit() == Level4Exit.BASEMENT_71) ? 0 : 1;
			Kind k = (es.along() == 3 && es.depth() == doorDepth) ? Kind.EXIT_DOOR : Kind.EXIT_STRUCTURE;
			return new Tile(k, zone, Level4Plan.opposite(es.dir()), ceilH, es.exit(), false);
		}

		// Exits attached to cell walls or floor
		for (PlacedExit pe : plan.exits) {
			if (pe.cell() != c) continue;
			if (pe.exit() == Level4Exit.HATCH_2 && cx == 3 && cz == 3) {
				return new Tile(Kind.EXIT_HATCH, zone, Level4Plan.NORTH, ceilH, pe.exit(), false);
			}
			if (pe.exit() != Level4Exit.HATCH_2 && onEdgeCenter(cx, cz, pe.dir())) {
				return new Tile(Kind.EXIT_DOOR, zone, Level4Plan.opposite(pe.dir()), ceilH, pe.exit(), false);
			}
		}

		// Corner column at (0, 0) of each 7x7 cell where walls don't already cover it
		if (cx == 0 && cz == 0) {
			return cornerTile(seed, plan, i, j, zone, ceilH);
		}

		// North wall band (cz == 0, cx in 1..6)
		if (cz == 0) {
			return edgeTile(seed, plan, i, j, Level4Plan.NORTH, cx, zone, ceilH);
		}
		// West wall band (cx == 0, cz in 1..6)
		if (cx == 0) {
			return edgeTile(seed, plan, i, j, Level4Plan.WEST, cz, zone, ceilH);
		}

		// Interior fixtures by zone
		return interiorTile(seed, plan, i, j, cx, cz, zone, ceilH);
	}

	private static Tile outsideTile(long seed, Level4Plan plan, int i, int j, int cx, int cz, int x, int z) {
		boolean northIndoor = j > 0 && plan.zones[Level4Plan.cell(i, j - 1)] != ZoneKind.OUTSIDE;
		boolean westIndoor = i > 0 && plan.zones[Level4Plan.cell(i - 1, j)] != ZoneKind.OUTSIDE;
		boolean nwIndoor = i > 0 && j > 0 && plan.zones[Level4Plan.cell(i - 1, j - 1)] != ZoneKind.OUTSIDE;
		if (cx == 0 && cz == 0 && (northIndoor || westIndoor || nwIndoor)) {
			boolean nearAtrium = (northIndoor && plan.zones[Level4Plan.cell(i, j - 1)] == ZoneKind.ATRIUM)
					|| (westIndoor && plan.zones[Level4Plan.cell(i - 1, j)] == ZoneKind.ATRIUM)
					|| (nwIndoor && plan.zones[Level4Plan.cell(i - 1, j - 1)] == ZoneKind.ATRIUM);
			return new Tile(Kind.PILLAR, ZoneKind.OUTSIDE, Level4Plan.NORTH, nearAtrium ? ATRIUM_HEIGHT : OPEN_HEIGHT, null, false);
		}
		if (cz == 0 && northIndoor) {
			ZoneKind nz = plan.zones[Level4Plan.cell(i, j - 1)];
			int ceil = nz == ZoneKind.ATRIUM ? ATRIUM_HEIGHT : OPEN_HEIGHT;
			Kind wk = (cx >= 1 && cx <= 5) ? exteriorWindowKind(seed, plan, i, j - 1, x, z) : Kind.DRYWALL;
			return new Tile(wk, ZoneKind.OUTSIDE, Level4Plan.NORTH, ceil, null, false);
		}
		if (cx == 0 && westIndoor) {
			ZoneKind wz = plan.zones[Level4Plan.cell(i - 1, j)];
			int ceil = wz == ZoneKind.ATRIUM ? ATRIUM_HEIGHT : OPEN_HEIGHT;
			Kind wk = (cz >= 1 && cz <= 5) ? exteriorWindowKind(seed, plan, i - 1, j, x, z) : Kind.DRYWALL;
			return new Tile(wk, ZoneKind.OUTSIDE, Level4Plan.WEST, ceil, null, false);
		}
		return new Tile(Kind.OUTSIDE_AIR, ZoneKind.OUTSIDE, Level4Plan.NORTH, OPEN_HEIGHT, null, false);
	}

	private static boolean onEdgeCenter(int cx, int cz, int d) {
		return switch (d) {
			case Level4Plan.NORTH -> cz == 0 && cx == 3;
			case Level4Plan.WEST -> cx == 0 && cz == 3;
			case Level4Plan.SOUTH -> cz == CELL - 1 && cx == 3;
			default -> cx == CELL - 1 && cz == 3;
		};
	}

	private static boolean isAtrium(Level4Plan plan, int i, int j) {
		return Level4Plan.inside(i, j) && plan.zones[Level4Plan.cell(i, j)] == ZoneKind.ATRIUM;
	}

	private static Tile cornerTile(long seed, Level4Plan plan, int i, int j, ZoneKind zone, int ceilH) {
		boolean nearAtrium = zone == ZoneKind.ATRIUM || isAtrium(plan, i - 1, j)
				|| isAtrium(plan, i, j - 1) || isAtrium(plan, i - 1, j - 1);
		int cornerCeil = nearAtrium ? ATRIUM_HEIGHT : ceilH;
		if (zone == ZoneKind.OMEGA_BASE) {
			RoomBox room = plan.rooms.get(plan.roomId[Level4Plan.cell(i, j)]);
			boolean outer = i == room.i() || j == room.j();
			if (outer) return new Tile(Kind.OMEGA_WALL, zone, Level4Plan.NORTH, cornerCeil, null, false);
		}
		// Keep open doorway corners clear on region edges
		if (i == 0 && plan.edge(Level4Plan.cell(0, j), Level4Plan.WEST) == Edge.OPEN) {
			return new Tile(Kind.AIR, zone, Level4Plan.NORTH, cornerCeil, null, false);
		}
		if (j == 0 && plan.edge(Level4Plan.cell(i, 0), Level4Plan.NORTH) == Edge.OPEN) {
			return new Tile(Kind.AIR, zone, Level4Plan.NORTH, cornerCeil, null, false);
		}
		return new Tile(Kind.PILLAR, zone, Level4Plan.NORTH, cornerCeil, null, false);
	}

	private static Tile edgeTile(long seed, Level4Plan plan, int i, int j, int dir, int along, ZoneKind zone, int ceilH) {
		int c = Level4Plan.cell(i, j);
		Edge e = plan.edge(c, dir);
		int ni = i + Level4Plan.DX[dir], nj = j + Level4Plan.DZ[dir];
		ZoneKind neighbor = Level4Plan.inside(ni, nj) ? plan.zones[Level4Plan.cell(ni, nj)] : ZoneKind.OPEN_OFFICE;
		boolean omegaWall = zone == ZoneKind.OMEGA_BASE || neighbor == ZoneKind.OMEGA_BASE;
		boolean atriumBorder = zone == ZoneKind.ATRIUM || neighbor == ZoneKind.ATRIUM;
		int edgeCeil = atriumBorder ? ATRIUM_HEIGHT : ceilH;
		int facingInto = dir == Level4Plan.NORTH ? Level4Plan.SOUTH : Level4Plan.EAST;
		if (omegaWall && zone != ZoneKind.OMEGA_BASE) facingInto = Level4Plan.opposite(facingInto);

		if (e == Edge.OPEN) {
			if (atriumBorder && zone != neighbor) {
				return new Tile(Kind.ATRIUM_ARCH, zone, facingInto, edgeCeil, null, false);
			}
			return new Tile(Kind.AIR, zone, facingInto, edgeCeil, null, false);
		}
		if (e == Edge.DOOR) {
			if (along == 3) {
				return new Tile(Kind.FIRE_DOOR, zone, facingInto, edgeCeil, null, true);
			}
			return new Tile(omegaWall ? Kind.OMEGA_WALL : Kind.DRYWALL, zone, facingInto, edgeCeil, null, false);
		}
		if (e == Edge.GLASS_WALL) {
			return new Tile(Kind.GLASS_WALL, zone, facingInto, edgeCeil, null, false);
		}
		// Solid wall edge: Omega sign/wall, or real exterior windows facing the misty rain canyon
		if (omegaWall) {
			boolean sign = (along == 2 || along == 4) && isOmegaGateAdjacent(plan, i, j, dir);
			return new Tile(sign ? Kind.OMEGA_SIGN : Kind.OMEGA_WALL, zone, facingInto, edgeCeil, null, false);
		}
		if (neighbor == ZoneKind.OUTSIDE) {
			if (along >= 1 && along <= 5) {
				int wx = dir == Level4Plan.NORTH ? i * CELL + along : i * CELL;
				int wz = dir == Level4Plan.NORTH ? j * CELL : j * CELL + along;
				return new Tile(exteriorWindowKind(seed, plan, i, j, wx, wz), zone, facingInto, edgeCeil, null, false);
			}
			return new Tile(Kind.DRYWALL, zone, facingInto, edgeCeil, null, false);
		}
		return new Tile(Kind.DRYWALL, zone, facingInto, edgeCeil, null, false);
	}

	private static boolean isOmegaGateAdjacent(Level4Plan plan, int i, int j, int dir) {
		int c = Level4Plan.cell(i, j);
		return plan.zones[c] == ZoneKind.OMEGA_BASE || (Level4Plan.inside(i + Level4Plan.DX[dir], j + Level4Plan.DZ[dir])
				&& plan.zones[Level4Plan.cell(i + Level4Plan.DX[dir], j + Level4Plan.DZ[dir])] == ZoneKind.OMEGA_BASE);
	}

	private static Kind exteriorWindowKind(long seed, Level4Plan plan, int roomI, int roomJ, int x, int z) {
		if (plan.nearHumanBase(roomI, roomJ)) return Kind.WINDOW_BLACKED;
		int r = Math.floorMod(hash(seed, x, z, 421), 100);
		return r < 18 ? Kind.WINDOW_TRAP : Kind.WINDOW_RAIN;
	}

	/** Whether (x, z) lies on the outer facade skin of the main 12x12 skyscraper (rendered vertically). */
	static boolean isExteriorSkin(long seed, int x, int z) {
		int rx = Math.floorDiv(x, SIZE), rz = Math.floorDiv(z, SIZE);
		Level4Plan plan = Level4Plan.of(seed, rx, rz);
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		int i = lx / CELL, j = lz / CELL;
		int cx = lx % CELL, cz = lz % CELL;
		if (Level4Plan.isSkybridgeCell(i, j)) return false;
		if (i == Level4Plan.MARGIN && cx == 0) return true;
		if (j == Level4Plan.MARGIN && cz == 0) return true;
		if (i == N - Level4Plan.MARGIN && cx == 0 && j >= Level4Plan.MARGIN && j < N - Level4Plan.MARGIN) return true;
		if (j == N - Level4Plan.MARGIN && cz == 0 && i >= Level4Plan.MARGIN && i < N - Level4Plan.MARGIN) return true;
		return false;
	}

	/** Whether (x, z) is part of a skybridge spanning the canyon between towers. */
	static boolean isSkybridge(long seed, int x, int z) {
		int rx = Math.floorDiv(x, SIZE), rz = Math.floorDiv(z, SIZE);
		Level4Plan plan = Level4Plan.of(seed, rx, rz);
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		int i = lx / CELL, j = lz / CELL;
		int cx = lx % CELL, cz = lz % CELL;
		if (Level4Plan.isSkybridgeCell(i, j) && plan.zones[Level4Plan.cell(i, j)] != ZoneKind.OUTSIDE) return true;
		if (cz == 0 && j > 0 && Level4Plan.isSkybridgeCell(i, j - 1)
				&& plan.zones[Level4Plan.cell(i, j - 1)] != ZoneKind.OUTSIDE && Level4Plan.isSkybridgeCell(i, j)) {
			return true;
		}
		if (cx == 0 && i > 0 && Level4Plan.isSkybridgeCell(i - 1, j)
				&& plan.zones[Level4Plan.cell(i - 1, j)] != ZoneKind.OUTSIDE && Level4Plan.isSkybridgeCell(i, j)) {
			return true;
		}
		return false;
	}

	private static Tile interiorTile(long seed, Level4Plan plan, int i, int j, int cx, int cz, ZoneKind zone, int ceilH) {
		int c = Level4Plan.cell(i, j);
		RoomBox room = plan.roomId[c] >= 0 ? plan.rooms.get(plan.roomId[c]) : null;

		if (zone == ZoneKind.ATRIUM && room != null && i == room.cx() && j == room.cz()) {
			// Central 3x3 Almond Water Fountain inside the Atrium
			if (cx >= 2 && cx <= 4 && cz >= 2 && cz <= 4) {
				if (cx == 3 && cz == 3) return new Tile(Kind.FOUNTAIN_POOL, zone, Level4Plan.NORTH, ceilH, null, false);
				if ((cx == 3 || cz == 3)) return new Tile(Kind.FOUNTAIN_POOL, zone, Level4Plan.NORTH, ceilH, null, false);
				return new Tile(Kind.FOUNTAIN_BASIN, zone, Level4Plan.NORTH, ceilH, null, false);
			}
		}

		if (zone == ZoneKind.OMEGA_BASE && room != null) {
			int i0 = room.i(), j0 = room.j(), i1 = i0 + room.w() - 1, j1 = j0 + room.h() - 1;
			// 4 checkpoint gate barricades inside cardinal doorways
			if (i == room.cx() && j == j0 && cz == 2 && (cx == 2 || cx == 4)) {
				return new Tile(Kind.OMEGA_BARRICADE, zone, Level4Plan.NORTH, ceilH, null, false);
			}
			if (i == room.cx() && j == j1 && cz == 4 && (cx == 2 || cx == 4)) {
				return new Tile(Kind.OMEGA_BARRICADE, zone, Level4Plan.SOUTH, ceilH, null, false);
			}
			if (i == i0 && j == room.cz() && cx == 2 && (cz == 2 || cz == 4)) {
				return new Tile(Kind.OMEGA_BARRICADE, zone, Level4Plan.WEST, ceilH, null, false);
			}
			if (i == i1 && j == room.cz() && cx == 4 && (cz == 2 || cz == 4)) {
				return new Tile(Kind.OMEGA_BARRICADE, zone, Level4Plan.EAST, ceilH, null, false);
			}
			// West barracks (6 squad cots)
			if (i == i0 && j >= j0 + 1 && j <= j0 + 3 && cx == 2 && (cz == 2 || cz == 4)) {
				return new Tile(Kind.OMEGA_COT, zone, Level4Plan.EAST, ceilH, null, false);
			}
			// South medical bay (2 medical cots + water cooler)
			if (j == j1 && (i == i0 + 1 || i == i0 + 3) && cx == 3 && cz == 4) {
				return new Tile(Kind.OMEGA_COT, zone, Level4Plan.NORTH, ceilH, null, false);
			}
			if (j == j1 && i == i0 + 1 && cx == 5 && cz == 4) {
				return new Tile(Kind.WATER_COOLER, zone, Level4Plan.NORTH, ceilH, null, false);
			}
			// East armory & supply wing (6 supply lockers)
			if (i == i1 && j >= j0 + 1 && j <= j0 + 3 && cx == 5 && (cz == 2 || cz == 4)) {
				return new Tile(Kind.OMEGA_LOCKER, zone, Level4Plan.WEST, ceilH, null, false);
			}
			// Central command & hub
			if (i == room.cx() && j == room.cz()) {
				if (cx == 2 && cz == 2) return new Tile(Kind.WATER_COOLER, zone, Level4Plan.SOUTH, ceilH, null, false);
				if (cx == 5 && cz == 2) return new Tile(Kind.VENDING, zone, Level4Plan.WEST, ceilH, null, false);
				if (cx == 2 && cz == 5) return new Tile(Kind.DESK_PHONE, zone, Level4Plan.NORTH, ceilH, null, false);
				if (cx == 5 && cz == 5) return new Tile(Kind.VENDING, zone, Level4Plan.WEST, ceilH, null, false);
			}
		}

		if (zone == ZoneKind.AMOR_CAMP && room != null) {
			if (i == room.cx() && j == room.cz()) {
				if (cx == 3 && cz == 3) {
					return new Tile(Kind.AMOR_PLANTER_VEIL, zone, Level4Plan.NORTH, ceilH, null, false);
				}
			} else if ((cx == 2 && cz == 2) || (cx == 5 && cz == 5)) {
				int mod = Math.floorMod(i + j, 3);
				Kind planter = switch (mod) {
					case 1 -> Kind.AMOR_PLANTER_L1;
					case 2 -> Kind.AMOR_PLANTER_VEIL;
					default -> Kind.AMOR_PLANTER;
				};
				return new Tile(planter, zone, Level4Plan.NORTH, ceilH, null, false);
			}
		}

		if (zone == ZoneKind.TBD_ROOM && room != null) {
			int i0 = room.i(), j0 = room.j();
			if (cx == 3 && cz == 2 && ((i == i0 && j == j0) || (i == i0 + 1 && j == j0 + 1) || (i == i0 && j == j0 + 2))) {
				return new Tile(Kind.TBD_SHELF, zone, Level4Plan.SOUTH, ceilH, null, false);
			}
			if ((cx == 1 || cx == 5) && (cz == 1 || cz == 5)) {
				int dir = cx == 1 ? Level4Plan.EAST : Level4Plan.WEST;
				return new Tile(Kind.TBD_HOARD, zone, dir, ceilH, null, false);
			}
		}

		if (zone == ZoneKind.DARK_WING && room != null && i == room.i() && j == room.j() && cx == 1 && cz == 2) {
			return new Tile(Kind.BREAKER, zone, Level4Plan.EAST, ceilH, null, false);
		}

		if (zone == ZoneKind.SUITE && room != null) {
			int roll = Math.floorMod(hash(seed, i, j, 433), 100);
			if (cx == 2 && cz == 2) {
				if (roll < 35) return new Tile(Kind.WATER_COOLER, zone, Level4Plan.SOUTH, ceilH, null, false);
				if (roll < 58) return new Tile(Kind.VENDING, zone, Level4Plan.SOUTH, ceilH, null, false);
				if (roll < 78) return new Tile(Kind.PHOTOCOPIER, zone, Level4Plan.SOUTH, ceilH, null, false);
				return new Tile(Kind.DESK_PHONE, zone, Level4Plan.SOUTH, ceilH, null, false);
			}
		}

		// Occasional water cooler / vending machine along open office pillars
		if (zone == ZoneKind.OPEN_OFFICE && cx == 1 && cz == 1) {
			int roll = Math.floorMod(hash(seed, i, j, 444), 100);
			if (roll < 9) return new Tile(Kind.WATER_COOLER, zone, Level4Plan.SOUTH, ceilH, null, false);
			if (roll < 15) return new Tile(Kind.VENDING, zone, Level4Plan.SOUTH, ceilH, null, false);
		}

		return new Tile(Kind.AIR, zone, Level4Plan.NORTH, ceilH, null, false);
	}

	/** Whether this floor cell has carpet indentations from long-vanished desks/chairs. */
	static boolean hasCarpetDent(long seed, int x, int z) {
		return Math.floorMod(hash(seed, x, z, 451), 100) < 11;
	}

	/** Whether this floor cell is damp (near fountains/coolers/rainy windows). */
	static boolean isDampCarpet(long seed, int x, int z, ZoneKind zone) {
		if (zone == ZoneKind.AMOR_CAMP) return Math.floorMod(hash(seed, x, z, 452), 100) < 35;
		if (zone == ZoneKind.ATRIUM) return Math.floorMod(hash(seed, x, z, 452), 100) < 20;
		return Math.floorMod(hash(seed, x, z, 452), 100) < 3;
	}

	/** Safe arrival coordinate near (targetX, targetZ), inside an open walkable bay away from traps/exits. */
	static int[] landing(long seed, int targetX, int targetZ) {
		int baseRx = Math.floorDiv(targetX, SIZE);
		int baseRz = Math.floorDiv(targetZ, SIZE);
		for (int radius = 0; radius <= 1; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					Level4Plan plan = Level4Plan.of(seed, baseRx + dx, baseRz + dz);
					for (int i = 1; i < N - 1; i++) {
						for (int j = 1; j < N - 1; j++) {
							int c = Level4Plan.cell(i, j);
							if (plan.zones[c] != ZoneKind.OPEN_OFFICE && plan.zones[c] != ZoneKind.SUITE) continue;
							int wx = (baseRx + dx) * SIZE + i * CELL + 3;
							int wz = (baseRz + dz) * SIZE + j * CELL + 3;
							if (tile(seed, wx, wz).kind() == Kind.AIR) return new int[] {wx, wz};
						}
					}
				}
			}
		}
		return new int[] {baseRx * SIZE + 3, baseRz * SIZE + 3};
	}

	/** Returns whether (x, z) is inside M.E.G. Base Omega (used to block monster spawns). */
	static boolean inOmegaBase(long seed, int x, int z) {
		return tile(seed, x, z).zone() == ZoneKind.OMEGA_BASE;
	}
}
