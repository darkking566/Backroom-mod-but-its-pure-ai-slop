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
	static final int MEZZANINE_HEIGHT = 10;

	enum Kind {
		AIR,
		OUTSIDE_AIR,
		ATRIUM_ARCH,
		PILLAR,
		DRYWALL,
		GLASS_WALL,
		CUBICLE_WALL,
		CUBICLE_GLASS,
		UNFINISHED_STUB,
		UNFINISHED_PILLAR,
		BALCONY_RAIL,
		BALCONY_FLOOR,
		BALCONY_CANOPY,
		ELEVATOR_SHAFT,
		ELEVATOR_CABLE,
		ELEVATOR_SHAFT_WALL,
		ELEVATOR_THRESHOLD,
		OMEGA_WALL,
		OMEGA_SIGN,
		WINDOW_BLACKED,
		WINDOW_TRAP,
		WINDOW_RAIN,
		WINDOW_DARK_MIRROR,
		WINDOW_BREACHED,
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
		DESK_PHONE,
		RELAY_BOX
	}

	record ExitStruct(Level4Exit exit, ZoneKind zone, int dir, int depth, int along) {
	}

	record Tile(Kind kind, ZoneKind zone, int dir, int ceilingHeight, Level4Exit exit, boolean exitSign) {
		boolean walkable() {
			return kind == Kind.AIR || kind == Kind.ATRIUM_ARCH || kind == Kind.FIRE_DOOR || kind == Kind.EXIT_HATCH
					|| kind == Kind.BALCONY_FLOOR || kind == Kind.BALCONY_CANOPY;
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
		int ceilH = zoneCeilHeight(zone);

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

		// Check balcony overrides before cell corners/edges/interiors so cheek walls stay seamless
		Tile balcony = balconyTile(seed, plan, i, j, cx, cz, zone, ceilH);
		if (balcony != null) return balcony;

		// Corner column at (0, 0) of each 7x7 cell where walls don't already cover it
		if (cx == 0 && cz == 0) {
			return cornerTile(seed, plan, i, j, zone, ceilH);
		}

		// North wall band (cz == 0, cx in 1..6)
		if (cz == 0) {
			return edgeTile(seed, rx, rz, plan, i, j, Level4Plan.NORTH, cx, zone, ceilH);
		}
		// West wall band (cx == 0, cz in 1..6)
		if (cx == 0) {
			return edgeTile(seed, rx, rz, plan, i, j, Level4Plan.WEST, cz, zone, ceilH);
		}

		// Interior fixtures by zone
		return interiorTile(seed, plan, Level4PoiPlan.of(seed, rx, rz), i, j, cx, cz, zone, ceilH);
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
			if (plan.balconyDir[Level4Plan.cell(i, j - 1)] == Level4Plan.SOUTH) {
				Kind wk = (cx >= 1 && cx <= 5) ? Kind.BALCONY_RAIL : Kind.DRYWALL;
				return new Tile(wk, ZoneKind.OUTSIDE, Level4Plan.NORTH, OPEN_HEIGHT, null, false);
			}
			Kind wk = (cx >= 1 && cx <= 5) ? exteriorWindowKind(seed, plan, i, j - 1, Level4Plan.SOUTH, cx, x, z) : Kind.DRYWALL;
			return new Tile(wk, ZoneKind.OUTSIDE, Level4Plan.NORTH, ceil, null, false);
		}
		if (cx == 0 && westIndoor) {
			ZoneKind wz = plan.zones[Level4Plan.cell(i - 1, j)];
			int ceil = wz == ZoneKind.ATRIUM ? ATRIUM_HEIGHT : OPEN_HEIGHT;
			if (plan.balconyDir[Level4Plan.cell(i - 1, j)] == Level4Plan.EAST) {
				Kind wk = (cz >= 1 && cz <= 5) ? Kind.BALCONY_RAIL : Kind.DRYWALL;
				return new Tile(wk, ZoneKind.OUTSIDE, Level4Plan.WEST, OPEN_HEIGHT, null, false);
			}
			Kind wk = (cz >= 1 && cz <= 5) ? exteriorWindowKind(seed, plan, i - 1, j, Level4Plan.EAST, cz, x, z) : Kind.DRYWALL;
			return new Tile(wk, ZoneKind.OUTSIDE, Level4Plan.WEST, ceil, null, false);
		}
		return new Tile(Kind.OUTSIDE_AIR, ZoneKind.OUTSIDE, Level4Plan.NORTH, OPEN_HEIGHT, null, false);
	}

	private static Tile balconyTile(long seed, Level4Plan plan, int i, int j, int cx, int cz, ZoneKind zone, int ceilH) {
		int bdir = plan.balconyDir[Level4Plan.cell(i, j)];
		if (bdir < 0) return null;

		if (bdir == Level4Plan.NORTH || bdir == Level4Plan.SOUTH) {
			boolean north = bdir == Level4Plan.NORTH;
			int depth = north ? cz : (CELL - cz);
			if (depth < 0 || depth > 3) return null;
			if (cx == 0 && cz == 0) return new Tile(Kind.PILLAR, zone, Level4Plan.NORTH, OPEN_HEIGHT, null, false);
			if (cx == 0) return new Tile(Kind.DRYWALL, zone, Level4Plan.WEST, OPEN_HEIGHT, null, false);
			if (depth == 0) {
				Kind k = (cx >= 1 && cx <= 5) ? Kind.BALCONY_RAIL : Kind.DRYWALL;
				return new Tile(k, zone, Level4Plan.SOUTH, OPEN_HEIGHT, null, false);
			}
			if (depth == 1) return new Tile(cx <= 5 ? Kind.BALCONY_FLOOR : Kind.DRYWALL, zone, Level4Plan.NORTH, OPEN_HEIGHT, null, false);
			if (depth == 2) return new Tile(cx <= 5 ? Kind.BALCONY_CANOPY : Kind.DRYWALL, zone, Level4Plan.NORTH, OPEN_HEIGHT, null, false);
			int doorFace = north ? Level4Plan.SOUTH : Level4Plan.NORTH;
			if (cx == 3) return new Tile(Kind.FIRE_DOOR, zone, doorFace, OPEN_HEIGHT, null, false);
			if (cx == 2 || cx == 4) return new Tile(Kind.WINDOW_RAIN, zone, doorFace, OPEN_HEIGHT, null, false);
			return new Tile(Kind.DRYWALL, zone, doorFace, OPEN_HEIGHT, null, false);
		}

		boolean west = bdir == Level4Plan.WEST;
		int depth = west ? cx : (CELL - cx);
		if (depth < 0 || depth > 3) return null;
		if (cx == 0 && cz == 0) return new Tile(Kind.PILLAR, zone, Level4Plan.NORTH, OPEN_HEIGHT, null, false);
		if (cz == 0) return new Tile(Kind.DRYWALL, zone, Level4Plan.NORTH, OPEN_HEIGHT, null, false);
		if (depth == 0) {
			Kind k = (cz >= 1 && cz <= 5) ? Kind.BALCONY_RAIL : Kind.DRYWALL;
			return new Tile(k, zone, Level4Plan.EAST, OPEN_HEIGHT, null, false);
		}
		if (depth == 1) return new Tile(cz <= 5 ? Kind.BALCONY_FLOOR : Kind.DRYWALL, zone, Level4Plan.NORTH, OPEN_HEIGHT, null, false);
		if (depth == 2) return new Tile(cz <= 5 ? Kind.BALCONY_CANOPY : Kind.DRYWALL, zone, Level4Plan.NORTH, OPEN_HEIGHT, null, false);
		int doorFace = west ? Level4Plan.EAST : Level4Plan.WEST;
		if (cz == 3) return new Tile(Kind.FIRE_DOOR, zone, doorFace, OPEN_HEIGHT, null, false);
		if (cz == 2 || cz == 4) return new Tile(Kind.WINDOW_RAIN, zone, doorFace, OPEN_HEIGHT, null, false);
		return new Tile(Kind.DRYWALL, zone, doorFace, OPEN_HEIGHT, null, false);
	}

	/** Whether (x, z) lies inside the open-sky central shaft of a tower {@link ZoneKind#LIGHTWELL}. */
	static boolean isLightwellShaft(long seed, int x, int z) {
		int rx = Math.floorDiv(x, SIZE), rz = Math.floorDiv(z, SIZE);
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		int i = lx / CELL, j = lz / CELL;
		if (Level4Plan.isSkybridgeCell(i, j)) return false;
		Level4Plan plan = Level4Plan.of(seed, rx, rz);
		return plan.zones[Level4Plan.cell(i, j)] == ZoneKind.OUTSIDE && tile(seed, x, z).kind() == Kind.OUTSIDE_AIR;
	}

	private static boolean onEdgeCenter(int cx, int cz, int d) {
		return switch (d) {
			case Level4Plan.NORTH -> cz == 0 && cx == 3;
			case Level4Plan.WEST -> cx == 0 && cz == 3;
			case Level4Plan.SOUTH -> cz == CELL - 1 && cx == 3;
			default -> cx == CELL - 1 && cz == 3;
		};
	}

	private static int zoneCeilHeight(ZoneKind zone) {
		return switch (zone) {
			case MEZZANINE -> MEZZANINE_HEIGHT;
			case ATRIUM, AUDITORIUM -> ATRIUM_HEIGHT;
			default -> OPEN_HEIGHT;
		};
	}

	private static boolean isTallZone(ZoneKind zone) {
		return zone == ZoneKind.ATRIUM || zone == ZoneKind.AUDITORIUM || zone == ZoneKind.MEZZANINE;
	}

	private static int tallBorderHeight(Level4Plan plan, int i, int j) {
		return Level4Plan.inside(i, j) ? zoneCeilHeight(plan.zones[Level4Plan.cell(i, j)]) : OPEN_HEIGHT;
	}

	private static Tile cornerTile(long seed, Level4Plan plan, int i, int j, ZoneKind zone, int ceilH) {
		if (zone == ZoneKind.ATRIUM_ZERO && i > Level4Plan.MARGIN && j > Level4Plan.MARGIN) {
			return new Tile(Kind.AIR, zone, Level4Plan.NORTH, OPEN_HEIGHT, null, false);
		}
		int cornerCeil = Math.max(ceilH, Math.max(tallBorderHeight(plan, i - 1, j),
				Math.max(tallBorderHeight(plan, i, j - 1), tallBorderHeight(plan, i - 1, j - 1))));
		if (zone == ZoneKind.OMEGA_BASE) {
			RoomBox room = plan.rooms.get(plan.roomId[Level4Plan.cell(i, j)]);
			boolean outer = i == room.i() || j == room.j();
			if (outer) return new Tile(Kind.OMEGA_WALL, zone, Level4Plan.NORTH, cornerCeil, null, false);
		}
		if (zone == ZoneKind.AUDITORIUM) {
			RoomBox room = plan.rooms.get(plan.roomId[Level4Plan.cell(i, j)]);
			if (i > room.i() && j > room.j()) {
				return new Tile(Kind.AIR, zone, Level4Plan.NORTH, ATRIUM_HEIGHT, null, false);
			}
		}
		return new Tile(Kind.PILLAR, zone, Level4Plan.NORTH, cornerCeil, null, false);
	}

	private static Tile edgeTile(long seed, int rx, int rz, Level4Plan plan, int i, int j, int dir, int along, ZoneKind zone, int ceilH) {
		int c = Level4Plan.cell(i, j);
		Edge e = plan.edge(c, dir);
		int ni = i + Level4Plan.DX[dir], nj = j + Level4Plan.DZ[dir];
		ZoneKind neighbor = Level4Plan.inside(ni, nj) ? plan.zones[Level4Plan.cell(ni, nj)] : ZoneKind.OPEN_OFFICE;
		boolean omegaWall = zone == ZoneKind.OMEGA_BASE || neighbor == ZoneKind.OMEGA_BASE;
		boolean atriumBorder = isTallZone(zone) || isTallZone(neighbor);
		int edgeCeil = Math.max(ceilH, zoneCeilHeight(neighbor));
		int facingInto = dir == Level4Plan.NORTH ? Level4Plan.SOUTH : Level4Plan.EAST;
		if (omegaWall && zone != ZoneKind.OMEGA_BASE) facingInto = Level4Plan.opposite(facingInto);

		if (zone == ZoneKind.ELEVATOR_LOBBY && dir == Level4Plan.NORTH && along >= 1 && along <= 5) {
			return new Tile(Kind.ELEVATOR_SHAFT_WALL, zone, facingInto, edgeCeil, null, false);
		}

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
				int wx = rx * SIZE + (dir == Level4Plan.NORTH ? i * CELL + along : i * CELL);
				int wz = rz * SIZE + (dir == Level4Plan.NORTH ? j * CELL : j * CELL + along);
				return new Tile(exteriorWindowKind(seed, plan, i, j, dir, along, wx, wz), zone, facingInto, edgeCeil, null, false);
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

	static boolean isTrapBay(long seed, int rx, int rz, int roomI, int roomJ, int dir) {
		return Math.floorMod(hash(seed, rx * 31 + roomI, rz * 31 + roomJ, 421 + dir), 100) < 10;
	}

	private static Kind exteriorWindowKind(long seed, Level4Plan plan, int roomI, int roomJ, int dir, int along, int x, int z) {
		if (plan.hasAtriumZero) return Kind.WINDOW_RAIN;
		if (plan.nearHumanBase(roomI, roomJ)) return Kind.WINDOW_BLACKED;
		int rx = Math.floorDiv(x, SIZE), rz = Math.floorDiv(z, SIZE);
		Level4PoiPlan.PlacedPoi poi = Level4PoiPlan.of(seed, rx, rz).atCellIndex(Level4Plan.cell(roomI, roomJ));
		if (poi != null && poi.kind() == Level4PoiPlan.PoiKind.EVACUATED_OUTPOST) return Kind.WINDOW_BLACKED;
		if (poi != null && poi.kind() == Level4PoiPlan.PoiKind.STORM_BREACH_CORRIDOR && poi.dir() == Level4Plan.opposite(dir)) {
			return (along >= 2 && along <= 4) ? Kind.WINDOW_BREACHED : Kind.WINDOW_TRAP;
		}
		if (poi != null && poi.kind() == Level4PoiPlan.PoiKind.DELAYED_REFLECTION_MIRROR
				&& !poi.variantFlag() && poi.dir() == Level4Plan.opposite(dir) && along >= 2 && along <= 4) {
			return Kind.WINDOW_DARK_MIRROR;
		}
		if (poi != null && poi.kind() == Level4PoiPlan.PoiKind.WINDOW_PAINT_CART && poi.dir() == Level4Plan.opposite(dir)) {
			if (along <= 2) return Kind.WINDOW_BLACKED;
			if (along <= 4) return Kind.WINDOW_TRAP;
			return Kind.WINDOW_RAIN;
		}
		if (isTrapBay(seed, rx, rz, roomI, roomJ, dir) && along >= 2 && along <= 4) return Kind.WINDOW_TRAP;
		return Kind.WINDOW_RAIN;
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

	record BridgeInfo(boolean eastWest, boolean elevated, int spanPos, int perpPos) {
	}

	record AuditoriumInfo(int floorY, boolean stair, int stairDir, boolean stage, boolean screenWall, int rx, int rz) {
	}

	record MezzanineInfo(int stairStep, boolean stairRail, boolean overlookOpen, boolean overlookRail, boolean upperGlassWall) {
	}

	record PlenumInfo(boolean wallShell, int ladderDir, boolean ventGrate, boolean duct, int rx, int rz) {
	}

	record SubTunnelInfo(boolean stair, int stepY, int stairDir, boolean stairRail, boolean corridor, boolean wallSide, boolean light, boolean pipeAllowed) {
	}

	/** Returns two-story mezzanine geometry inside {@link ZoneKind#MEZZANINE}, or null elsewhere. */
	static MezzanineInfo mezzanineInfo(long seed, int x, int z) {
		int rx0 = Math.floorDiv(x, SIZE), rz0 = Math.floorDiv(z, SIZE);
		Level4Plan plan = Level4Plan.of(seed, rx0, rz0);
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		int i = lx / CELL, j = lz / CELL;
		int c = Level4Plan.cell(i, j);
		if (plan.zones[c] != ZoneKind.MEZZANINE || plan.roomId[c] < 0) return null;
		RoomBox room = plan.rooms.get(plan.roomId[c]);
		int rx = lx - room.i() * CELL;
		int rz = lz - room.j() * CELL;
		int stairStep = ((rz == 3 || rz == 4) && rx >= 1 && rx <= 6) ? rx : 0;
		boolean stairRail = (rz == 2 || rz == 5) && rx >= 1 && rx <= 6;
		boolean overlookOpen = rx >= 14 && rx <= 18 && rz >= 8 && rz <= 12;
		boolean overlookRail = !overlookOpen && (rx >= 13 && rx <= 19 && rz >= 7 && rz <= 13);
		boolean upperGlassWall = (rx == 12 && rz >= 2 && rz <= 6 && rz != 4);
		return new MezzanineInfo(stairStep, stairRail, overlookOpen, overlookRail, upperGlassWall);
	}

	/** Returns ceiling plenum / HVAC crawlway geometry above {@link ZoneKind#DARK_WING}, or null elsewhere. */
	static PlenumInfo plenumInfo(long seed, int x, int z) {
		int rx0 = Math.floorDiv(x, SIZE), rz0 = Math.floorDiv(z, SIZE);
		Level4Plan plan = Level4Plan.of(seed, rx0, rz0);
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		int i = lx / CELL, j = lz / CELL;
		int c = Level4Plan.cell(i, j);
		if (plan.zones[c] != ZoneKind.DARK_WING || plan.roomId[c] < 0) return null;
		RoomBox room = plan.rooms.get(plan.roomId[c]);
		int rx = lx - room.i() * CELL;
		int rz = lz - room.j() * CELL;
		boolean wallShell = (rx == 0 || rx == 20 || rz == 0 || rz == 20);
		int ladderDir = -1;
		if (rx == 4 && rz == 1 && !hasExitInCell(plan, Level4Plan.cell(room.i(), room.j()))) {
			ladderDir = Level4Plan.SOUTH;
		} else if (rx == 18 && rz == 20 && !hasExitInCell(plan, Level4Plan.cell(room.i() + 2, room.j() + 2))) {
			ladderDir = Level4Plan.NORTH;
		}
		boolean ventGrate = (rx == 10 && rz == 10);
		boolean duct = !wallShell && ladderDir < 0
				&& (((rx == 7 || rx == 14) && rz >= 2 && rz <= 18)
						|| ((rz == 7 || rz == 14) && rx >= 2 && rx <= 18 && rx != 4 && rx != 18));
		return new PlenumInfo(wallShell, ladderDir, ventGrate, duct, rx, rz);
	}

	/** Returns sub-level MEP chiller corridor & stairwell geometry, or null outside tunnel cells. */
	static SubTunnelInfo subTunnelInfo(long seed, int x, int z) {
		int rx0 = Math.floorDiv(x, SIZE), rz0 = Math.floorDiv(z, SIZE);
		Level4Plan plan = Level4Plan.of(seed, rx0, rz0);
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		int i = lx / CELL, j = lz / CELL;
		int cx = lx % CELL, cz = lz % CELL;
		if (!plan.isTunnelCell(i, j) || cz < 1 || cz > 5) return null;

		boolean wallSide = (cz == 1 || cz == 5);
		if (i == plan.tunnelI0 && cx >= 1) {
			if (cz == 3) {
				int stepY = FLOOR_Y + 1 - cx;
				return new SubTunnelInfo(true, stepY, Level4Plan.WEST, false, false, false, false, false);
			}
			return new SubTunnelInfo(false, 0, Level4Plan.WEST, cx >= 1 && (cz == 2 || cz == 4), false, false, false, false);
		}
		if (i == plan.tunnelI1 && cx >= 1) {
			if (cz == 3) {
				int stepY = FLOOR_Y - 6 + cx;
				return new SubTunnelInfo(true, stepY, Level4Plan.EAST, false, false, false, false, false);
			}
			return new SubTunnelInfo(false, 0, Level4Plan.EAST, cx >= 1 && (cz == 2 || cz == 4), false, false, false, false);
		}
		if (i > plan.tunnelI0 && (i < plan.tunnelI1 || cx == 0)) {
			boolean corridor = cz >= 2 && cz <= 4;
			boolean light = corridor && cz == 3 && cx == 3;
			boolean pipeAllowed = corridor
					&& !(i == plan.tunnelI0 + 1 && cx <= 1)
					&& !(i == plan.tunnelI1 && cx == 0)
					&& !(i == plan.tunnelI1 - 1 && cx == 6);
			return new SubTunnelInfo(false, 0, Level4Plan.EAST, false, corridor, wallSide, light, pipeAllowed);
		}
		return null;
	}

	/** Returns tiered seating/stage geometry inside a {@link ZoneKind#AUDITORIUM} room, or null elsewhere. */
	static AuditoriumInfo auditoriumInfo(long seed, int x, int z) {
		int rx0 = Math.floorDiv(x, SIZE), rz0 = Math.floorDiv(z, SIZE);
		Level4Plan plan = Level4Plan.of(seed, rx0, rz0);
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		int i = lx / CELL, j = lz / CELL;
		int c = Level4Plan.cell(i, j);
		if (plan.zones[c] != ZoneKind.AUDITORIUM || plan.roomId[c] < 0) return null;
		RoomBox room = plan.rooms.get(plan.roomId[c]);
		int rx = lx - room.i() * CELL;
		int rz = lz - room.j() * CELL;
		boolean screen = (rz == 19 && rx >= 5 && rx <= 15);
		if (rz <= 3) return new AuditoriumInfo(FLOOR_Y, false, Level4Plan.NORTH, false, screen, rx, rz);
		if (rz <= 6) return new AuditoriumInfo(FLOOR_Y - 1, rz == 4 && rx >= 2 && rx <= 18, Level4Plan.NORTH, false, screen, rx, rz);
		if (rz <= 9) return new AuditoriumInfo(FLOOR_Y - 2, rz == 7 && rx >= 2 && rx <= 18, Level4Plan.NORTH, false, screen, rx, rz);
		if (rz <= 12) return new AuditoriumInfo(FLOOR_Y - 3, rz == 10 && rx >= 2 && rx <= 18, Level4Plan.NORTH, false, screen, rx, rz);
		if (rz <= 15) return new AuditoriumInfo(FLOOR_Y - 3, false, Level4Plan.NORTH, false, screen, rx, rz);
		if (rz == 16 && rx >= 8 && rx <= 12) {
			return new AuditoriumInfo(FLOOR_Y - 3, true, Level4Plan.SOUTH, true, screen, rx, rz);
		}
		return new AuditoriumInfo(FLOOR_Y - 2, false, Level4Plan.SOUTH, true, screen, rx, rz);
	}

	/** Returns bridge geometry metadata if (x, z) lies on or forms a wall of an exterior skybridge. */
	static BridgeInfo skybridgeInfo(long seed, int x, int z) {
		int rx = Math.floorDiv(x, SIZE), rz = Math.floorDiv(z, SIZE);
		Level4Plan plan = Level4Plan.of(seed, rx, rz);
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		int i = lx / CELL, j = lz / CELL;
		int cx = lx % CELL, cz = lz % CELL;

		// Check if (i, j) or its N/W border cell belongs to an E-W bridge
		int bridgeJ = j;
		boolean ewFloor = (i >= N - Level4Plan.MARGIN || i < Level4Plan.MARGIN)
				&& plan.zones[Level4Plan.cell(i, j)] != ZoneKind.OUTSIDE;
		boolean ewSouthWall = !ewFloor && cz == 0 && j > 0
				&& (i >= N - Level4Plan.MARGIN || i < Level4Plan.MARGIN)
				&& plan.zones[Level4Plan.cell(i, j - 1)] != ZoneKind.OUTSIDE;
		if (ewFloor || ewSouthWall) {
			if (ewSouthWall) bridgeJ = j - 1;
			boolean firstHalf = i >= N - Level4Plan.MARGIN;
			int brx = firstHalf ? rx : rx - 1;
			int spanPos = firstHalf ? lx - (N - Level4Plan.MARGIN) * CELL : Level4Plan.MARGIN * CELL + lx;
			int perpPos = ewSouthWall ? CELL : cz;
			boolean elevated = Math.floorMod(hash(seed, brx, rz, 471 + bridgeJ), 2) == 0;
			return new BridgeInfo(true, elevated, spanPos, perpPos);
		}

		// Check if (i, j) or its W border cell belongs to a N-S bridge
		int bridgeI = i;
		boolean nsFloor = (j >= N - Level4Plan.MARGIN || j < Level4Plan.MARGIN)
				&& plan.zones[Level4Plan.cell(i, j)] != ZoneKind.OUTSIDE;
		boolean nsEastWall = !nsFloor && cx == 0 && i > 0
				&& (j >= N - Level4Plan.MARGIN || j < Level4Plan.MARGIN)
				&& plan.zones[Level4Plan.cell(i - 1, j)] != ZoneKind.OUTSIDE;
		if (nsFloor || nsEastWall) {
			if (nsEastWall) bridgeI = i - 1;
			boolean firstHalf = j >= N - Level4Plan.MARGIN;
			int brz = firstHalf ? rz : rz - 1;
			int spanPos = firstHalf ? lz - (N - Level4Plan.MARGIN) * CELL : Level4Plan.MARGIN * CELL + lz;
			int perpPos = nsEastWall ? CELL : cx;
			boolean elevated = Math.floorMod(hash(seed, rx, brz, 473 + bridgeI), 2) == 0;
			return new BridgeInfo(false, elevated, spanPos, perpPos);
		}

		return null;
	}

	/** Whether (x, z) is part of a skybridge spanning the canyon between towers. */
	static boolean isSkybridge(long seed, int x, int z) {
		return skybridgeInfo(seed, x, z) != null;
	}

	private static Tile interiorTile(long seed, Level4Plan plan, Level4PoiPlan poiPlan, int i, int j, int cx, int cz, ZoneKind zone, int ceilH) {
		int c = Level4Plan.cell(i, j);
		RoomBox room = plan.roomId[c] >= 0 ? plan.rooms.get(plan.roomId[c]) : null;
		boolean hasPoi = poiPlan.atCellIndex(c) != null;

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
				if (cx == 5 && cz == 5) return new Tile(Kind.RELAY_BOX, zone, Level4Plan.NORTH, ceilH, null, false);
			}
			// Interior glass partitions dividing barracks, armory/supply, and medical bay
			if (!hasExitInCell(plan, c)) {
				int di = i - i0, dj = j - j0;
				if (di == 1 && dj >= 1 && dj <= 3 && cx == 1 && (cz == 1 || cz == 2 || cz == 4 || cz == 5)) {
					return new Tile(Kind.CUBICLE_GLASS, zone, Level4Plan.EAST, ceilH, null, false);
				}
				if (di == 3 && dj >= 1 && dj <= 3 && cx == 5 && (cz == 1 || cz == 2 || cz == 4 || cz == 5)) {
					return new Tile(Kind.CUBICLE_GLASS, zone, Level4Plan.WEST, ceilH, null, false);
				}
				if (dj == 3 && (di == 1 || di == 3) && cz == 5 && (cx == 1 || cx == 2 || cx == 4 || cx == 5)) {
					return new Tile(Kind.CUBICLE_GLASS, zone, Level4Plan.SOUTH, ceilH, null, false);
				}
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

		if (zone == ZoneKind.ELEVATOR_LOBBY && room != null) {
			if (cz == 0 && cx >= 1 && cx <= 5) {
				return new Tile(Kind.ELEVATOR_SHAFT_WALL, zone, Level4Plan.SOUTH, ceilH, null, false);
			}
			if (cz == 1) {
				if (cx == 1 || cx == 5) return new Tile(Kind.ELEVATOR_SHAFT_WALL, zone, Level4Plan.SOUTH, ceilH, null, false);
				if (cx == 3) return new Tile(Kind.ELEVATOR_CABLE, zone, Level4Plan.SOUTH, ceilH, null, false);
				if (cx == 2 || cx == 4) return new Tile(Kind.ELEVATOR_SHAFT, zone, Level4Plan.SOUTH, ceilH, null, false);
			}
			if (cz == 2 && cx >= 1 && cx <= 5) {
				return new Tile(Kind.ELEVATOR_THRESHOLD, zone, Level4Plan.SOUTH, ceilH, null, false);
			}
		}

		if (zone == ZoneKind.UNFINISHED_WING && room != null && !hasPoi) {
			if ((cx == 3 && (cz == 2 || cz == 5)) || (cz == 3 && cx == 2 && (i + j) % 2 == 0)) {
				return new Tile(Kind.UNFINISHED_PILLAR, zone, Level4Plan.NORTH, ceilH, null, false);
			}
			if ((cx == 2 && cz == 4) || (cx == 4 && cz == 2) || (cx == 5 && cz == 4 && (i + j) % 2 == 1)) {
				return new Tile(Kind.UNFINISHED_STUB, zone, Level4Plan.NORTH, ceilH, null, false);
			}
		}

		if (zone == ZoneKind.SUITE && room != null && !hasPoi) {
			int roll = Math.floorMod(hash(seed, i, j, 433), 100);
			if (cx == 2 && cz == 2 && roll < 14) {
				if (roll < 5) return new Tile(Kind.WATER_COOLER, zone, Level4Plan.SOUTH, ceilH, null, false);
				if (roll < 7) return new Tile(Kind.VENDING, zone, Level4Plan.SOUTH, ceilH, null, false);
				if (roll < 11) return new Tile(Kind.PHOTOCOPIER, zone, Level4Plan.SOUTH, ceilH, null, false);
				return new Tile(Kind.DESK_PHONE, zone, Level4Plan.SOUTH, ceilH, null, false);
			}
		}

		// Very rare water cooler / vending machine along open office pillars, plus half-height cubicle mazes
		if (zone == ZoneKind.OPEN_OFFICE && !Level4Plan.isSkybridgeCell(i, j) && !hasPoi) {
			int roll = Math.floorMod(hash(seed, i, j, 444), 100);
			if (cx == 1 && cz == 1) {
				if (roll < 2) return new Tile(Kind.WATER_COOLER, zone, Level4Plan.SOUTH, ceilH, null, false);
				if (roll == 2) return new Tile(Kind.VENDING, zone, Level4Plan.SOUTH, ceilH, null, false);
			}
			if (roll >= 15 && roll < 52 && !hasExitInCell(plan, c) && !plan.isTunnelCell(i, j)) {
				Kind cubicle = cubicleTile(cx, cz, Math.floorMod(roll, 4));
				if (cubicle != null) return new Tile(cubicle, zone, Level4Plan.NORTH, ceilH, null, false);
			}
		}

		return new Tile(Kind.AIR, zone, Level4Plan.NORTH, ceilH, null, false);
	}

	private static boolean hasExitInCell(Level4Plan plan, int c) {
		for (PlacedExit pe : plan.exits) {
			if (pe.cell() == c) return true;
		}
		return false;
	}

	private static Kind cubicleTile(int cx, int cz, int variant) {
		return switch (variant) {
			case 0 -> {
				if (cx == 3 && cz == 3) yield Kind.CUBICLE_GLASS;
				if ((cx == 2 || cx == 4) && cz >= 2 && cz <= 4) yield Kind.CUBICLE_WALL;
				yield null;
			}
			case 1 -> {
				if ((cz == 2 && cx == 3) || (cz == 4 && cx == 4)) yield Kind.CUBICLE_GLASS;
				if ((cz == 2 && cx >= 2 && cx <= 4) || (cz == 4 && cx >= 3 && cx <= 5)) yield Kind.CUBICLE_WALL;
				yield null;
			}
			case 2 -> {
				if (cz == 3 && (cx == 2 || cx == 4)) yield Kind.CUBICLE_GLASS;
				if (cx == 3 && (cz == 2 || cz == 4)) yield Kind.CUBICLE_WALL;
				yield null;
			}
			default -> {
				if ((cx == 2 || cx == 5) && cz == 3) yield Kind.CUBICLE_GLASS;
				if ((cx == 2 || cx == 5) && (cz == 2 || cz == 5)) yield Kind.CUBICLE_WALL;
				yield null;
			}
		};
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
							if (plan.isTunnelCell(i, j)) continue;
							int wx = (baseRx + dx) * SIZE + i * CELL + 3;
							int wz = (baseRz + dz) * SIZE + j * CELL + 3;
							if (tile(seed, wx, wz).kind() == Kind.AIR && Level4PoiPlan.atCell(seed, wx, wz) == null) return new int[] {wx, wz};
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
