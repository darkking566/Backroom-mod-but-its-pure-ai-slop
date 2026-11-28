package dev.noclip.backrooms.level4;

import static dev.noclip.backrooms.level4.Level4Plan.CELL;
import static dev.noclip.backrooms.level4.Level4Plan.DX;
import static dev.noclip.backrooms.level4.Level4Plan.DZ;
import static dev.noclip.backrooms.level4.Level4Plan.EAST;
import static dev.noclip.backrooms.level4.Level4Plan.MARGIN;
import static dev.noclip.backrooms.level4.Level4Plan.N;
import static dev.noclip.backrooms.level4.Level4Plan.NORTH;
import static dev.noclip.backrooms.level4.Level4Plan.SIZE;
import static dev.noclip.backrooms.level4.Level4Plan.SOUTH;
import static dev.noclip.backrooms.level4.Level4Plan.WEST;

import dev.noclip.backrooms.level4.Level4Plan.Edge;
import dev.noclip.backrooms.level4.Level4Plan.PlacedExit;
import dev.noclip.backrooms.level4.Level4Plan.RoomBox;
import dev.noclip.backrooms.level4.Level4Plan.ZoneKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure deterministic POI planner for Level 4 ("Abandoned Office").
 * Computes all POI placements per 112x112 region from {@link Level4Plan} without touching world state
 * or breaking connectivity, faction footprints, or exit structures.
 */
final class Level4PoiPlan {
	private static final int POI_SALT = 455;
	private static final ConcurrentHashMap<Key, Level4PoiPlan> CACHE = new ConcurrentHashMap<>();

	enum PoiRarity {
		COMMON,
		UNCOMMON,
		RARE,
		ULTRA_RARE
	}

	enum PoiKind {
		// Batch 1 (Common x6 + Uncommon x2)
		BREAKROOM_CORNER(PoiRarity.COMMON, 1),
		MEG_CHALK_WALL(PoiRarity.COMMON, 1),
		SEALED_MEETING_ROOM(PoiRarity.COMMON, 1),
		CEILING_DRIP_SPOT(PoiRarity.COMMON, 1),
		ACTIVE_TERMINAL_ISLAND(PoiRarity.COMMON, 1),
		WINDOW_PAINT_CART(PoiRarity.COMMON, 1),
		MISSING_WANDERER_CAMP(PoiRarity.UNCOMMON, 1),
		AMOR_FIELD_SHRINE(PoiRarity.UNCOMMON, 1),

		// Batch 2 (Uncommon x4 + Rare x2)
		LOCKED_PBX_ROOM(PoiRarity.UNCOMMON, 2),
		DELAYED_REFLECTION_MIRROR(PoiRarity.UNCOMMON, 2),
		TBD_OVERFLOW_CAGE(PoiRarity.UNCOMMON, 2),
		PLENUM_MAINTENANCE_HUB(PoiRarity.UNCOMMON, 2),
		STALLED_FREIGHT_ELEVATOR(PoiRarity.RARE, 2),
		EVACUATED_OUTPOST(PoiRarity.RARE, 2),

		// Batch 3 (Rare x3)
		STORM_BREACH_CORRIDOR(PoiRarity.RARE, 3),
		CORRUPTED_RED_ATRIUM(PoiRarity.RARE, 3),
		SUBTUNNEL_ABYSS_DOOR(PoiRarity.RARE, 3),

		// Batch 4 (Ultra-Rare x3)
		FROZEN_FRIDAY_1998(PoiRarity.ULTRA_RARE, 4),
		INVERTED_MIRROR_LIGHTWELL(PoiRarity.ULTRA_RARE, 4),
		EXECUTIVE_BROADCAST_SUITE(PoiRarity.ULTRA_RARE, 4);

		final PoiRarity rarity;
		final int batch;

		PoiKind(PoiRarity rarity, int batch) {
			this.rarity = rarity;
			this.batch = batch;
		}
	}

	/**
	 * @param dir facing direction INTO the room from the wall/anchor (NORTH/EAST/SOUTH/WEST)
	 * @param baseY floor block Y (32 normally, 38 on mezzanine upper deck)
	 * @param variantFlag POI-specific flag (MEG_CHALK_WALL: slashed; CEILING_DRIP_SPOT: opens to plenum;
	 *                    MEG_CHALK_WALL pillar variant if roomId == -2)
	 * @param roomId room index in {@link Level4Plan#rooms} when anchored to a room, else -1
	 */
	record PlacedPoi(PoiKind kind, int cellI, int cellJ, int dir, int baseY, boolean variantFlag, int roomId) {
		int cell() {
			return Level4Plan.cell(cellI, cellJ);
		}
	}

	private record Key(long seed, int rx, int rz) {
	}

	final List<PlacedPoi> pois = new ArrayList<>();
	private final Map<Integer, PlacedPoi> byCell = new HashMap<>();
	private final Set<Integer> sealedRoomIds = new HashSet<>();
	private final Set<Integer> pbxRoomIds = new HashSet<>();

	static Level4PoiPlan of(long seed, int rx, int rz) {
		if (CACHE.size() > 512) CACHE.clear();
		return CACHE.computeIfAbsent(new Key(seed, rx, rz), k -> new Level4PoiPlan(seed, rx, rz));
	}

	static PlacedPoi atCell(long seed, int x, int z) {
		int rx = Math.floorDiv(x, SIZE), rz = Math.floorDiv(z, SIZE);
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		int i = lx / CELL, j = lz / CELL;
		return of(seed, rx, rz).byCell.get(Level4Plan.cell(i, j));
	}

	PlacedPoi atCellIndex(int cell) {
		return byCell.get(cell);
	}

	boolean isSealedSuiteRoom(int roomId) {
		return roomId >= 0 && sealedRoomIds.contains(roomId);
	}

	boolean isPbxSuiteRoom(int roomId) {
		return roomId >= 0 && pbxRoomIds.contains(roomId);
	}

	static boolean isFrozenFriday(long seed, int x, int z) {
		PlacedPoi poi = atCell(seed, x, z);
		return poi != null && poi.kind() == PoiKind.FROZEN_FRIDAY_1998;
	}

	static boolean isInvertedLightwell(long seed, int x, int z) {
		PlacedPoi poi = atCell(seed, x, z);
		return poi != null && poi.kind() == PoiKind.INVERTED_MIRROR_LIGHTWELL;
	}

	static PlacedPoi subtunnelAbyssAt(long seed, int x, int z) {
		PlacedPoi here = atCell(seed, x, z);
		if (here != null && here.kind() == PoiKind.SUBTUNNEL_ABYSS_DOOR) return here;
		PlacedPoi north = atCell(seed, x, z - CELL);
		if (north != null && north.kind() == PoiKind.SUBTUNNEL_ABYSS_DOOR) return north;
		return null;
	}

	/**
	 * Returns whether (x, z) lies on a shared wall between an EXECUTIVE_BROADCAST_SUITE and an AUDITORIUM:
	 * 0 = none, 1 = mahogany frame border, 2 = 3-wide panoramic glass overlook window.
	 */
	static int executiveAuditoriumEdgeKind(long seed, int x, int z) {
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		int cx = lx % CELL, cz = lz % CELL;
		if (cz == 0 && cx >= 1 && cx <= 5) {
			PlacedPoi here = atCell(seed, x, z);
			if (here != null && here.kind() == PoiKind.EXECUTIVE_BROADCAST_SUITE
					&& Level4Plan.opposite(here.dir()) == NORTH) {
				return (cx >= 2 && cx <= 4) ? 2 : 1;
			}
			PlacedPoi north = atCell(seed, x, z - CELL);
			if (north != null && north.kind() == PoiKind.EXECUTIVE_BROADCAST_SUITE
					&& Level4Plan.opposite(north.dir()) == SOUTH) {
				return (cx >= 2 && cx <= 4) ? 2 : 1;
			}
		}
		if (cx == 0 && cz >= 1 && cz <= 5) {
			PlacedPoi here = atCell(seed, x, z);
			if (here != null && here.kind() == PoiKind.EXECUTIVE_BROADCAST_SUITE
					&& Level4Plan.opposite(here.dir()) == WEST) {
				return (cz >= 2 && cz <= 4) ? 2 : 1;
			}
			PlacedPoi west = atCell(seed, x - CELL, z);
			if (west != null && west.kind() == PoiKind.EXECUTIVE_BROADCAST_SUITE
					&& Level4Plan.opposite(west.dir()) == EAST) {
				return (cz >= 2 && cz <= 4) ? 2 : 1;
			}
		}
		return 0;
	}

	private Level4PoiPlan(long seed, int rx, int rz) {
		Level4Plan plan = Level4Plan.of(seed, rx, rz);
		if (plan.hasAtriumZero) return;
		SplittableRandom rng = new SplittableRandom(Level4Layout.hash(seed, rx, rz, POI_SALT));
		Set<Integer> usedCells = new HashSet<>();

		// Pre-block factions, solid cores, outside, exits, and tunnel stairs
		for (int i = 0; i < N; i++) {
			for (int j = 0; j < N; j++) {
				int c = Level4Plan.cell(i, j);
				ZoneKind z = plan.zones[c];
				if (z == ZoneKind.OMEGA_BASE || z == ZoneKind.AMOR_CAMP || z == ZoneKind.TBD_ROOM
						|| z == ZoneKind.SOLID_CORE || z == ZoneKind.OUTSIDE) {
					usedCells.add(c);
				}
				if (plan.isTunnelCell(i, j)) {
					usedCells.add(c);
				}
			}
		}
		for (PlacedExit pe : plan.exits) {
			usedCells.add(pe.cell());
		}

		// Batch 4: Ultra-Rare landmark POIs (non-origin regions only; rolled before normal POIs for clean footprints)
		if (!(rx == 0 && rz == 0)) {
			SplittableRandom landmarkRng = new SplittableRandom(Level4Layout.hash(seed, rx, rz, POI_SALT + 4));
			if (landmarkRng.nextInt(100) < 18) {
				placeInvertedMirrorLightwell(plan, usedCells);
			}
			if (landmarkRng.nextInt(100) < 24) {
				placeExecutiveBroadcastSuite(plan, landmarkRng, usedCells);
			}
			if (landmarkRng.nextInt(100) < 8) {
				placeFrozenFriday1998(plan, landmarkRng, usedCells);
			}
		}

		// #3 SEALED_MEETING_ROOM (Common, ~70% per region on a 1x1 or 2x1 suite)
		if (rng.nextInt(100) < 70) {
			placeSealedMeetingRoom(plan, rng, usedCells);
		}

		// #6 WINDOW_PAINT_CART (Common, ~70% per region on exterior perimeter cell away from bases)
		if (rng.nextInt(100) < 70) {
			placeWindowPaintCart(plan, rng, usedCells);
		}

		// #1 BREAKROOM_CORNER (Common, 1..2 per region)
		int breakrooms = 1 + (rng.nextInt(100) < 45 ? 1 : 0);
		for (int k = 0; k < breakrooms; k++) {
			placeBreakroomCorner(plan, rng, usedCells);
		}

		// #4 CEILING_DRIP_SPOT (Common, ~75% per region; prefers DARK_WING under plenum)
		if (rng.nextInt(100) < 75) {
			placeCeilingDripSpot(plan, rng, usedCells);
		}

		// #5 ACTIVE_TERMINAL_ISLAND (Common, ~70% per region)
		if (rng.nextInt(100) < 70) {
			placeActiveTerminalIsland(plan, rng, usedCells);
		}

		// #2 MEG_CHALK_WALL (Common, 1..2 per region)
		int chalks = 1 + (rng.nextInt(100) < 55 ? 1 : 0);
		for (int k = 0; k < chalks; k++) {
			placeMegChalkWall(seed, rx, rz, plan, rng, usedCells);
		}

		// #7 MISSING_WANDERER_CAMP (Uncommon, ~50% per region on BALCONY, MEZZANINE 2F, or UNFINISHED_WING)
		if (rng.nextInt(100) < 50) {
			placeMissingWandererCamp(plan, rng, usedCells);
		}

		// #10 AMOR_FIELD_SHRINE (Uncommon, ~45% per region in DARK_WING or isolated SUITE)
		if (rng.nextInt(100) < 45) {
			placeAmorFieldShrine(plan, rng, usedCells);
		}

		// Batch 2 POIs
		// #8 LOCKED_PBX_ROOM (Uncommon, ~48% per region on a 1x1 SUITE)
		if (rng.nextInt(100) < 48) {
			placeLockedPbxRoom(plan, rng, usedCells);
		}

		// #9 DELAYED_REFLECTION_MIRROR (Uncommon, ~58% per region on LIGHTWELL shaft window, MEZZANINE 2F, or OPEN_OFFICE wall)
		if (rng.nextInt(100) < 58) {
			placeDelayedReflectionMirror(plan, rng, usedCells);
		}

		// #11 TBD_OVERFLOW_CAGE (Uncommon, ~52% per region in UNFINISHED_WING or ELEVATOR_LOBBY corner)
		if (rng.nextInt(100) < 52) {
			placeTbdOverflowCage(plan, rng, usedCells);
		}

		// #12 PLENUM_MAINTENANCE_HUB (Uncommon, ~70% when DARK_WING exists, centered in overhead plenum)
		if (rng.nextInt(100) < 70) {
			placePlenumMaintenanceHub(plan, usedCells);
		}

		// #14 STALLED_FREIGHT_ELEVATOR (Rare, ~65% when ELEVATOR_LOBBY exists)
		if (rng.nextInt(100) < 65) {
			placeStalledFreightElevator(plan, usedCells);
		}

		// #15 EVACUATED_OUTPOST (Rare, ~42% when !plan.hasOmega on a 2x2 open bay)
		if (!plan.hasOmega && rng.nextInt(100) < 42) {
			placeEvacuatedOutpost(plan, rng, usedCells);
		}

		// Batch 3 POIs
		// #13 STORM_BREACH_CORRIDOR (Rare, ~38% per region on OPEN_OFFICE perimeter facing OUTSIDE)
		if (rng.nextInt(100) < 38) {
			placeStormBreachCorridor(plan, rng, usedCells);
		}

		// #16 CORRUPTED_RED_ATRIUM (Rare, ~26% replacing ATRIUM away from origin (0, 0))
		if (plan.hasAtrium && !(rx == 0 && rz == 0) && rng.nextInt(100) < 26) {
			placeCorruptedRedAtrium(plan, usedCells);
		}

		// #17 SUBTUNNEL_ABYSS_DOOR (Rare, ~52% when MEP subTunnel exists)
		if (plan.tunnelJ >= 0 && rng.nextInt(100) < 52) {
			placeSubtunnelAbyssDoor(plan, rng);
		}
	}

	private void add(PlacedPoi poi, Set<Integer> usedCells) {
		pois.add(poi);
		byCell.put(poi.cell(), poi);
		usedCells.add(poi.cell());
	}

	private void placeSealedMeetingRoom(Level4Plan plan, SplittableRandom rng, Set<Integer> usedCells) {
		List<Integer> candidates = new ArrayList<>();
		for (int id = 0; id < plan.rooms.size(); id++) {
			RoomBox room = plan.rooms.get(id);
			if (room.kind() != ZoneKind.SUITE || room.w() > 2 || room.h() > 2) continue;
			boolean free = true;
			int doorDir = -1;
			for (int a = room.i(); a < room.i() + room.w() && free; a++) {
				for (int b = room.j(); b < room.j() + room.h() && free; b++) {
					int c = Level4Plan.cell(a, b);
					if (usedCells.contains(c) || plan.balconyDir[c] >= 0) {
						free = false;
						break;
					}
					if (a == room.i() && b == room.j()) {
						for (int d = 0; d < 4; d++) {
							if (plan.edge(c, d) == Edge.DOOR) {
								doorDir = d;
								break;
							}
						}
					}
				}
			}
			if (free && doorDir >= 0) candidates.add(id);
		}
		if (candidates.isEmpty()) return;
		int pickedId = candidates.get(rng.nextInt(candidates.size()));
		RoomBox room = plan.rooms.get(pickedId);
		int anchorCell = Level4Plan.cell(room.i(), room.j());
		int doorDir = NORTH;
		for (int d = 0; d < 4; d++) {
			if (plan.edge(anchorCell, d) == Edge.DOOR) {
				doorDir = d;
				break;
			}
		}
		sealedRoomIds.add(pickedId);
		PlacedPoi anchor = new PlacedPoi(PoiKind.SEALED_MEETING_ROOM, room.i(), room.j(),
				Level4Plan.opposite(doorDir), Level4Layout.FLOOR_Y, false, pickedId);
		pois.add(anchor);
		for (int a = room.i(); a < room.i() + room.w(); a++) {
			for (int b = room.j(); b < room.j() + room.h(); b++) {
				int c = Level4Plan.cell(a, b);
				byCell.put(c, anchor);
				usedCells.add(c);
			}
		}
	}

	private void placeWindowPaintCart(Level4Plan plan, SplittableRandom rng, Set<Integer> usedCells) {
		List<int[]> candidates = new ArrayList<>();
		for (int i = MARGIN; i < N - MARGIN; i++) {
			for (int j = MARGIN; j < N - MARGIN; j++) {
				int c = Level4Plan.cell(i, j);
				if (usedCells.contains(c) || plan.balconyDir[c] >= 0 || plan.nearHumanBase(i, j)) continue;
				ZoneKind z = plan.zones[c];
				if (z != ZoneKind.OPEN_OFFICE && z != ZoneKind.LIGHTWELL) continue;
				for (int d = 0; d < 4; d++) {
					int ni = i + DX[d], nj = j + DZ[d];
					if (Level4Plan.inside(ni, nj) && plan.zones[Level4Plan.cell(ni, nj)] == ZoneKind.OUTSIDE
							&& plan.edge(c, d) == Edge.WALL) {
						candidates.add(new int[] {i, j, Level4Plan.opposite(d)});
					}
				}
			}
		}
		if (candidates.isEmpty()) return;
		int[] pick = candidates.get(rng.nextInt(candidates.size()));
		add(new PlacedPoi(PoiKind.WINDOW_PAINT_CART, pick[0], pick[1], pick[2], Level4Layout.FLOOR_Y, false, -1), usedCells);
	}

	private void placeBreakroomCorner(Level4Plan plan, SplittableRandom rng, Set<Integer> usedCells) {
		List<int[]> candidates = new ArrayList<>();
		for (int i = MARGIN; i < N - MARGIN; i++) {
			for (int j = MARGIN; j < N - MARGIN; j++) {
				int c = Level4Plan.cell(i, j);
				if (usedCells.contains(c) || plan.balconyDir[c] >= 0 || Level4Plan.isSkybridgeCell(i, j)) continue;
				ZoneKind z = plan.zones[c];
				if (z != ZoneKind.OPEN_OFFICE && z != ZoneKind.SUITE) continue;
				for (int d = 0; d < 4; d++) {
					if (plan.edge(c, d) != Edge.WALL) continue;
					int ni = i + DX[d], nj = j + DZ[d];
					if (!Level4Plan.inside(ni, nj)) continue;
					ZoneKind nz = plan.zones[Level4Plan.cell(ni, nj)];
					if (nz == ZoneKind.OUTSIDE || nz == ZoneKind.OMEGA_BASE) continue;
					candidates.add(new int[] {i, j, Level4Plan.opposite(d)});
				}
			}
		}
		if (candidates.isEmpty()) return;
		int[] pick = candidates.get(rng.nextInt(candidates.size()));
		add(new PlacedPoi(PoiKind.BREAKROOM_CORNER, pick[0], pick[1], pick[2], Level4Layout.FLOOR_Y, false, -1), usedCells);
	}

	private void placeCeilingDripSpot(Level4Plan plan, SplittableRandom rng, Set<Integer> usedCells) {
		List<int[]> plenumSpots = new ArrayList<>();
		List<int[]> officeSpots = new ArrayList<>();
		for (RoomBox room : plan.rooms) {
			if (room.kind() != ZoneKind.DARK_WING) continue;
			// Cells (i+1, j) and (i, j+1) sit cleanly under open plenum air (avoiding ladder/vent/duct lines)
			int[][] safePlenumCells = {{room.i() + 1, room.j()}, {room.i(), room.j() + 1}, {room.i() + 2, room.j() + 1}};
			for (int[] ij : safePlenumCells) {
				int c = Level4Plan.cell(ij[0], ij[1]);
				if (!usedCells.contains(c) && plan.zones[c] == ZoneKind.DARK_WING) {
					plenumSpots.add(ij);
				}
			}
		}
		if (!plenumSpots.isEmpty()) {
			int[] pick = plenumSpots.get(rng.nextInt(plenumSpots.size()));
			add(new PlacedPoi(PoiKind.CEILING_DRIP_SPOT, pick[0], pick[1], SOUTH, Level4Layout.FLOOR_Y, true, -1), usedCells);
			return;
		}
		for (int i = MARGIN + 1; i < N - MARGIN - 1; i++) {
			for (int j = MARGIN + 1; j < N - MARGIN - 1; j++) {
				int c = Level4Plan.cell(i, j);
				if (usedCells.contains(c) || plan.balconyDir[c] >= 0) continue;
				if (plan.zones[c] == ZoneKind.OPEN_OFFICE) officeSpots.add(new int[] {i, j});
			}
		}
		if (officeSpots.isEmpty()) return;
		int[] pick = officeSpots.get(rng.nextInt(officeSpots.size()));
		add(new PlacedPoi(PoiKind.CEILING_DRIP_SPOT, pick[0], pick[1], SOUTH, Level4Layout.FLOOR_Y, false, -1), usedCells);
	}

	private void placeActiveTerminalIsland(Level4Plan plan, SplittableRandom rng, Set<Integer> usedCells) {
		List<int[]> candidates = new ArrayList<>();
		for (int i = MARGIN + 1; i < N - MARGIN - 1; i++) {
			for (int j = MARGIN + 1; j < N - MARGIN - 1; j++) {
				int c = Level4Plan.cell(i, j);
				if (usedCells.contains(c) || plan.balconyDir[c] >= 0) continue;
				if (plan.zones[c] == ZoneKind.OPEN_OFFICE) candidates.add(new int[] {i, j});
			}
		}
		if (candidates.isEmpty()) return;
		int[] pick = candidates.get(rng.nextInt(candidates.size()));
		add(new PlacedPoi(PoiKind.ACTIVE_TERMINAL_ISLAND, pick[0], pick[1],
				rng.nextBoolean() ? EAST : SOUTH, Level4Layout.FLOOR_Y, false, -1), usedCells);
	}

	private void placeMegChalkWall(long seed, int rx, int rz, Level4Plan plan, SplittableRandom rng, Set<Integer> usedCells) {
		List<int[]> wallSpots = new ArrayList<>();
		List<int[]> pillarSpots = new ArrayList<>();
		for (int i = 0; i < N; i++) {
			for (int j = 0; j < N; j++) {
				int c = Level4Plan.cell(i, j);
				if (usedCells.contains(c) || plan.balconyDir[c] >= 0 || plan.zones[c] != ZoneKind.OPEN_OFFICE) continue;
				if (Level4Plan.isSkybridgeCell(i, j)) continue;
				for (int d = 0; d < 4; d++) {
					if (plan.edge(c, d) != Edge.WALL) continue;
					int ni = i + DX[d], nj = j + DZ[d];
					if (!Level4Plan.inside(ni, nj)) continue;
					ZoneKind nz = plan.zones[Level4Plan.cell(ni, nj)];
					if (nz != ZoneKind.OUTSIDE && nz != ZoneKind.OMEGA_BASE && nz != ZoneKind.ELEVATOR_LOBBY) {
						wallSpots.add(new int[] {i, j, Level4Plan.opposite(d), 0});
					}
				}
				// Open-office corner pillar option (backing pillar at cx=0, cz=0)
				if (i > MARGIN && j > MARGIN && i < N - MARGIN && j < N - MARGIN
						&& plan.edge(c, NORTH) == Edge.OPEN
						&& plan.zones[Level4Plan.cell(i - 1, j)] == ZoneKind.OPEN_OFFICE
						&& plan.zones[Level4Plan.cell(i, j - 1)] == ZoneKind.OPEN_OFFICE
						&& plan.zones[Level4Plan.cell(i - 1, j - 1)] == ZoneKind.OPEN_OFFICE) {
					pillarSpots.add(new int[] {i, j, EAST, 1});
				}
			}
		}
		List<int[]> pool = !pillarSpots.isEmpty() && rng.nextInt(100) < 55 ? pillarSpots
				: (!wallSpots.isEmpty() ? wallSpots : pillarSpots);
		if (pool.isEmpty()) return;
		int[] pick = pool.get(rng.nextInt(pool.size()));
		boolean slashed = isNearDanger(seed, rx, rz, plan, pick[0], pick[1]);
		add(new PlacedPoi(PoiKind.MEG_CHALK_WALL, pick[0], pick[1], pick[2], Level4Layout.FLOOR_Y, slashed,
				pick[3] == 1 ? -2 : -1), usedCells);
	}

	private static boolean isNearDanger(long seed, int rx, int rz, Level4Plan plan, int ci, int cj) {
		for (int i = Math.max(0, ci - 2); i <= Math.min(N - 1, ci + 2); i++) {
			for (int j = Math.max(0, cj - 2); j <= Math.min(N - 1, cj + 2); j++) {
				int c = Level4Plan.cell(i, j);
				if (plan.zones[c] == ZoneKind.DARK_WING) return true;
				for (int d = 0; d < 4; d++) {
					int ni = i + DX[d], nj = j + DZ[d];
					if (Level4Plan.inside(ni, nj) && plan.zones[Level4Plan.cell(ni, nj)] == ZoneKind.OUTSIDE
							&& !plan.nearHumanBase(i, j)
							&& Level4Layout.isTrapBay(seed, rx, rz, i, j, d)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private void placeMissingWandererCamp(Level4Plan plan, SplittableRandom rng, Set<Integer> usedCells) {
		List<int[]> balconies = new ArrayList<>();
		for (int i = MARGIN; i < N - MARGIN; i++) {
			for (int j = MARGIN; j < N - MARGIN; j++) {
				int c = Level4Plan.cell(i, j);
				if (!usedCells.contains(c) && plan.balconyDir[c] >= 0) {
					balconies.add(new int[] {i, j, plan.balconyDir[c], Level4Layout.FLOOR_Y});
				}
			}
		}
		if (!balconies.isEmpty()) {
			int[] pick = balconies.get(rng.nextInt(balconies.size()));
			add(new PlacedPoi(PoiKind.MISSING_WANDERER_CAMP, pick[0], pick[1], pick[2], pick[3], false, -1), usedCells);
			return;
		}
		for (RoomBox room : plan.rooms) {
			if (room.kind() == ZoneKind.MEZZANINE) {
				int i = room.i() + 2, j = room.j();
				int c = Level4Plan.cell(i, j);
				if (!usedCells.contains(c)) {
					add(new PlacedPoi(PoiKind.MISSING_WANDERER_CAMP, i, j, SOUTH, Level4Layout.FLOOR_Y + 6, true, -1), usedCells);
					return;
				}
			}
		}
		for (RoomBox room : plan.rooms) {
			if (room.kind() == ZoneKind.UNFINISHED_WING) {
				int i = room.i(), j = room.j();
				int c = Level4Plan.cell(i, j);
				if (!usedCells.contains(c)) {
					add(new PlacedPoi(PoiKind.MISSING_WANDERER_CAMP, i, j, SOUTH, Level4Layout.FLOOR_Y, false, -1), usedCells);
					return;
				}
			}
		}
	}

	private void placeAmorFieldShrine(Level4Plan plan, SplittableRandom rng, Set<Integer> usedCells) {
		List<int[]> candidates = new ArrayList<>();
		for (RoomBox room : plan.rooms) {
			if (room.kind() == ZoneKind.DARK_WING) {
				int[][] corners = {{room.i() + 2, room.j()}, {room.i(), room.j() + 2}, {room.i() + 2, room.j() + 1}};
				for (int[] ij : corners) {
					int c = Level4Plan.cell(ij[0], ij[1]);
					if (!usedCells.contains(c) && plan.zones[c] == ZoneKind.DARK_WING) {
						candidates.add(ij);
					}
				}
			}
		}
		if (candidates.isEmpty()) {
			for (RoomBox room : plan.rooms) {
				if (room.kind() != ZoneKind.SUITE) continue;
				int c = Level4Plan.cell(room.i(), room.j());
				if (!usedCells.contains(c)) {
					candidates.add(new int[] {room.i(), room.j()});
				}
			}
		}
		if (candidates.isEmpty()) return;
		int[] pick = candidates.get(rng.nextInt(candidates.size()));
		add(new PlacedPoi(PoiKind.AMOR_FIELD_SHRINE, pick[0], pick[1], NORTH, Level4Layout.FLOOR_Y, false, -1), usedCells);
	}

	private void placeLockedPbxRoom(Level4Plan plan, SplittableRandom rng, Set<Integer> usedCells) {
		List<Integer> candidates = new ArrayList<>();
		for (int id = 0; id < plan.rooms.size(); id++) {
			RoomBox room = plan.rooms.get(id);
			if (room.kind() != ZoneKind.SUITE || room.w() != 1 || room.h() != 1) continue;
			int c = Level4Plan.cell(room.i(), room.j());
			if (usedCells.contains(c) || plan.balconyDir[c] >= 0) continue;
			for (int d = 0; d < 4; d++) {
				if (plan.edge(c, d) == Edge.DOOR) {
					candidates.add(id);
					break;
				}
			}
		}
		if (candidates.isEmpty()) return;
		int pickedId = candidates.get(rng.nextInt(candidates.size()));
		RoomBox room = plan.rooms.get(pickedId);
		int c = Level4Plan.cell(room.i(), room.j());
		int doorDir = NORTH;
		for (int d = 0; d < 4; d++) {
			if (plan.edge(c, d) == Edge.DOOR) {
				doorDir = d;
				break;
			}
		}
		pbxRoomIds.add(pickedId);
		add(new PlacedPoi(PoiKind.LOCKED_PBX_ROOM, room.i(), room.j(), Level4Plan.opposite(doorDir),
				Level4Layout.FLOOR_Y, false, pickedId), usedCells);
	}

	private void placeDelayedReflectionMirror(Level4Plan plan, SplittableRandom rng, Set<Integer> usedCells) {
		List<int[]> lightwellSpots = new ArrayList<>();
		for (int i = MARGIN; i < N - MARGIN; i++) {
			for (int j = MARGIN; j < N - MARGIN; j++) {
				int c = Level4Plan.cell(i, j);
				if (usedCells.contains(c) || plan.balconyDir[c] >= 0 || plan.nearHumanBase(i, j)) continue;
				if (plan.zones[c] != ZoneKind.LIGHTWELL) continue;
				for (int d = 0; d < 4; d++) {
					int ni = i + DX[d], nj = j + DZ[d];
					if (Level4Plan.inside(ni, nj) && plan.zones[Level4Plan.cell(ni, nj)] == ZoneKind.OUTSIDE
							&& plan.edge(c, d) == Edge.WALL) {
						lightwellSpots.add(new int[] {i, j, Level4Plan.opposite(d), Level4Layout.FLOOR_Y, 0});
					}
				}
			}
		}
		if (!lightwellSpots.isEmpty()) {
			int[] pick = lightwellSpots.get(rng.nextInt(lightwellSpots.size()));
			add(new PlacedPoi(PoiKind.DELAYED_REFLECTION_MIRROR, pick[0], pick[1], pick[2], pick[3], false, -1), usedCells);
			return;
		}

		List<int[]> wallSpots = new ArrayList<>();
		for (RoomBox room : plan.rooms) {
			if (room.kind() != ZoneKind.MEZZANINE) continue;
			for (int a = room.i() + 1; a < room.i() + room.w(); a++) {
				for (int b = room.j(); b < room.j() + room.h(); b++) {
					int c = Level4Plan.cell(a, b);
					if (usedCells.contains(c) || plan.balconyDir[c] >= 0) continue;
					for (int d = 0; d < 4; d++) {
						if (plan.edge(c, d) != Edge.WALL) continue;
						int ni = a + DX[d], nj = b + DZ[d];
						if (!Level4Plan.inside(ni, nj)) continue;
						ZoneKind nz = plan.zones[Level4Plan.cell(ni, nj)];
						if (nz != ZoneKind.OUTSIDE && nz != ZoneKind.MEZZANINE) {
							wallSpots.add(new int[] {a, b, Level4Plan.opposite(d), Level4Layout.FLOOR_Y + 6});
						}
					}
				}
			}
		}
		if (wallSpots.isEmpty()) {
			for (int i = MARGIN + 1; i < N - MARGIN - 1; i++) {
				for (int j = MARGIN + 1; j < N - MARGIN - 1; j++) {
					int c = Level4Plan.cell(i, j);
					if (usedCells.contains(c) || plan.balconyDir[c] >= 0 || plan.zones[c] != ZoneKind.OPEN_OFFICE) continue;
					if (Level4Plan.isSkybridgeCell(i, j)) continue;
					for (int d = 0; d < 4; d++) {
						if (plan.edge(c, d) != Edge.WALL) continue;
						int ni = i + DX[d], nj = j + DZ[d];
						if (!Level4Plan.inside(ni, nj)) continue;
						ZoneKind nz = plan.zones[Level4Plan.cell(ni, nj)];
						if (nz == ZoneKind.OPEN_OFFICE || nz == ZoneKind.SUITE || nz == ZoneKind.SOLID_CORE || nz == ZoneKind.DARK_WING) {
							wallSpots.add(new int[] {i, j, Level4Plan.opposite(d), Level4Layout.FLOOR_Y});
						}
					}
				}
			}
		}
		if (wallSpots.isEmpty()) return;
		int[] pick = wallSpots.get(rng.nextInt(wallSpots.size()));
		add(new PlacedPoi(PoiKind.DELAYED_REFLECTION_MIRROR, pick[0], pick[1], pick[2], pick[3], true, -1), usedCells);
	}

	private void placeTbdOverflowCage(Level4Plan plan, SplittableRandom rng, Set<Integer> usedCells) {
		List<int[]> candidates = new ArrayList<>();
		for (RoomBox room : plan.rooms) {
			if (room.kind() != ZoneKind.UNFINISHED_WING) continue;
			for (int a = room.i(); a < room.i() + room.w(); a++) {
				for (int b = room.j(); b < room.j() + room.h(); b++) {
					if (a == room.i() && b == room.j()) continue; // keep corner (i, j) available for #7 camp
					int c = Level4Plan.cell(a, b);
					if (!usedCells.contains(c) && plan.balconyDir[c] < 0) {
						candidates.add(new int[] {a, b});
					}
				}
			}
		}
		if (candidates.isEmpty()) {
			for (RoomBox room : plan.rooms) {
				if (room.kind() != ZoneKind.ELEVATOR_LOBBY) continue;
				int c = Level4Plan.cell(room.i(), room.j());
				if (!usedCells.contains(c)) {
					candidates.add(new int[] {room.i(), room.j()});
				}
			}
		}
		if (candidates.isEmpty()) return;
		int[] pick = candidates.get(rng.nextInt(candidates.size()));
		add(new PlacedPoi(PoiKind.TBD_OVERFLOW_CAGE, pick[0], pick[1], NORTH, Level4Layout.FLOOR_Y, false, -1), usedCells);
	}

	private void placePlenumMaintenanceHub(Level4Plan plan, Set<Integer> usedCells) {
		for (int id = 0; id < plan.rooms.size(); id++) {
			RoomBox room = plan.rooms.get(id);
			if (room.kind() != ZoneKind.DARK_WING || room.w() < 3 || room.h() < 3) continue;
			int ci = room.i() + 1, cj = room.j() + 1;
			int c = Level4Plan.cell(ci, cj);
			if (usedCells.contains(c)) continue;
			add(new PlacedPoi(PoiKind.PLENUM_MAINTENANCE_HUB, ci, cj, NORTH, Level4Layout.FLOOR_Y + 5, false, id), usedCells);
			return;
		}
	}

	private void placeStalledFreightElevator(Level4Plan plan, Set<Integer> usedCells) {
		for (int id = 0; id < plan.rooms.size(); id++) {
			RoomBox room = plan.rooms.get(id);
			if (room.kind() != ZoneKind.ELEVATOR_LOBBY) continue;
			int ci = room.i() + (room.w() > 1 ? 1 : 0);
			int cj = room.j();
			int c = Level4Plan.cell(ci, cj);
			if (usedCells.contains(c)) {
				ci = room.i();
				c = Level4Plan.cell(ci, cj);
				if (usedCells.contains(c)) continue;
			}
			add(new PlacedPoi(PoiKind.STALLED_FREIGHT_ELEVATOR, ci, cj, SOUTH, Level4Layout.FLOOR_Y - 4, false, id), usedCells);
			return;
		}
	}

	private void placeEvacuatedOutpost(Level4Plan plan, SplittableRandom rng, Set<Integer> usedCells) {
		List<int[]> preferred = new ArrayList<>();
		List<int[]> fallback = new ArrayList<>();
		for (int i0 = MARGIN; i0 <= N - MARGIN - 2; i0++) {
			for (int j0 = MARGIN; j0 <= N - MARGIN - 2; j0++) {
				boolean ok = true;
				boolean nearAud = false;
				for (int a = i0; a <= i0 + 1 && ok; a++) {
					for (int b = j0; b <= j0 + 1 && ok; b++) {
						int c = Level4Plan.cell(a, b);
						if (usedCells.contains(c) || plan.balconyDir[c] >= 0 || plan.nearHumanBase(a, b)
								|| plan.isTunnelCell(a, b) || plan.zones[c] != ZoneKind.OPEN_OFFICE) {
							ok = false;
							break;
						}
						for (int d = 0; d < 4; d++) {
							int na = a + DX[d], nb = b + DZ[d];
							if (Level4Plan.inside(na, nb) && plan.zones[Level4Plan.cell(na, nb)] == ZoneKind.AUDITORIUM) {
								nearAud = true;
							}
						}
					}
				}
				if (!ok) continue;
				if (nearAud) preferred.add(new int[] {i0, j0});
				else fallback.add(new int[] {i0, j0});
			}
		}
		List<int[]> pool = !preferred.isEmpty() ? preferred : fallback;
		if (pool.isEmpty()) return;
		int[] pick = pool.get(rng.nextInt(pool.size()));
		PlacedPoi anchor = new PlacedPoi(PoiKind.EVACUATED_OUTPOST, pick[0], pick[1], SOUTH, Level4Layout.FLOOR_Y, false, -1);
		pois.add(anchor);
		for (int a = pick[0]; a <= pick[0] + 1; a++) {
			for (int b = pick[1]; b <= pick[1] + 1; b++) {
				int c = Level4Plan.cell(a, b);
				byCell.put(c, anchor);
				usedCells.add(c);
			}
		}
	}

	private void placeStormBreachCorridor(Level4Plan plan, SplittableRandom rng, Set<Integer> usedCells) {
		List<int[]> candidates = new ArrayList<>();
		for (int i = MARGIN; i < N - MARGIN; i++) {
			for (int j = MARGIN; j < N - MARGIN; j++) {
				int c = Level4Plan.cell(i, j);
				if (usedCells.contains(c) || plan.balconyDir[c] >= 0 || plan.nearHumanBase(i, j)
						|| Level4Plan.isSkybridgeCell(i, j) || plan.zones[c] != ZoneKind.OPEN_OFFICE) {
					continue;
				}
				for (int d = 0; d < 4; d++) {
					int ni = i + DX[d], nj = j + DZ[d];
					if (Level4Plan.inside(ni, nj) && plan.zones[Level4Plan.cell(ni, nj)] == ZoneKind.OUTSIDE
							&& plan.edge(c, d) == Edge.WALL) {
						candidates.add(new int[] {i, j, Level4Plan.opposite(d)});
					}
				}
			}
		}
		if (candidates.isEmpty()) return;
		int[] pick = candidates.get(rng.nextInt(candidates.size()));
		add(new PlacedPoi(PoiKind.STORM_BREACH_CORRIDOR, pick[0], pick[1], pick[2], Level4Layout.FLOOR_Y, false, -1), usedCells);
	}

	private void placeCorruptedRedAtrium(Level4Plan plan, Set<Integer> usedCells) {
		for (int id = 0; id < plan.rooms.size(); id++) {
			RoomBox room = plan.rooms.get(id);
			if (room.kind() != ZoneKind.ATRIUM) continue;
			for (int a = room.i(); a < room.i() + room.w(); a++) {
				for (int b = room.j(); b < room.j() + room.h(); b++) {
					if (usedCells.contains(Level4Plan.cell(a, b))) return;
				}
			}
			PlacedPoi anchor = new PlacedPoi(PoiKind.CORRUPTED_RED_ATRIUM, room.cx(), room.cz(), SOUTH,
					Level4Layout.FLOOR_Y, false, id);
			pois.add(anchor);
			for (int a = room.i(); a < room.i() + room.w(); a++) {
				for (int b = room.j(); b < room.j() + room.h(); b++) {
					int c = Level4Plan.cell(a, b);
					byCell.put(c, anchor);
					usedCells.add(c);
				}
			}
			return;
		}
	}

	private void placeSubtunnelAbyssDoor(Level4Plan plan, SplittableRandom rng) {
		if (plan.tunnelJ + 1 >= N) return;
		List<Integer> candidates = new ArrayList<>();
		for (int ci = plan.tunnelI0 + 1; ci <= plan.tunnelI1 - 1; ci++) {
			int c = Level4Plan.cell(ci, plan.tunnelJ);
			if (byCell.containsKey(c)) continue;
			ZoneKind southZone = plan.zones[Level4Plan.cell(ci, plan.tunnelJ + 1)];
			if (southZone == ZoneKind.OUTSIDE || southZone == ZoneKind.ELEVATOR_LOBBY
					|| southZone == ZoneKind.AUDITORIUM || southZone == ZoneKind.OMEGA_BASE) {
				continue;
			}
			candidates.add(ci);
		}
		if (candidates.isEmpty()) return;
		int ci = candidates.get(rng.nextInt(candidates.size()));
		PlacedPoi poi = new PlacedPoi(PoiKind.SUBTUNNEL_ABYSS_DOOR, ci, plan.tunnelJ, SOUTH,
				Level4Layout.FLOOR_Y - 6, false, -1);
		pois.add(poi);
		byCell.put(poi.cell(), poi);
	}

	private void placeInvertedMirrorLightwell(Level4Plan plan, Set<Integer> usedCells) {
		for (int id = 0; id < plan.rooms.size(); id++) {
			RoomBox room = plan.rooms.get(id);
			if (room.kind() != ZoneKind.LIGHTWELL) continue;
			for (int a = room.i(); a < room.i() + room.w(); a++) {
				for (int b = room.j(); b < room.j() + room.h(); b++) {
					if (a == room.cx() && b == room.cz()) continue;
					if (usedCells.contains(Level4Plan.cell(a, b))) return;
				}
			}
			PlacedPoi anchor = new PlacedPoi(PoiKind.INVERTED_MIRROR_LIGHTWELL, room.i(), room.j(), SOUTH,
					Level4Layout.FLOOR_Y, false, id);
			pois.add(anchor);
			for (int a = room.i(); a < room.i() + room.w(); a++) {
				for (int b = room.j(); b < room.j() + room.h(); b++) {
					int c = Level4Plan.cell(a, b);
					byCell.put(c, anchor);
					usedCells.add(c);
				}
			}
			return;
		}
	}

	private void placeExecutiveBroadcastSuite(Level4Plan plan, SplittableRandom rng, Set<Integer> usedCells) {
		List<int[]> candidates = new ArrayList<>();
		for (int id = 0; id < plan.rooms.size(); id++) {
			RoomBox room = plan.rooms.get(id);
			if (room.kind() != ZoneKind.AUDITORIUM) continue;
			int[][] adj = {
					{room.cx(), room.j() - 1, NORTH, id},
					{room.cx(), room.j() + room.h(), SOUTH, id},
					{room.i() - 1, room.cz(), WEST, id},
					{room.i() + room.w(), room.cz(), EAST, id}
			};
			for (int[] cand : adj) {
				int a = cand[0], b = cand[1];
				if (a < MARGIN || a >= N - MARGIN || b < MARGIN || b >= N - MARGIN) continue;
				int c = Level4Plan.cell(a, b);
				if (plan.zones[c] != ZoneKind.OPEN_OFFICE || usedCells.contains(c)
						|| plan.balconyDir[c] >= 0 || plan.nearHumanBase(a, b) || Level4Plan.isSkybridgeCell(a, b)) {
					continue;
				}
				candidates.add(cand);
			}
		}
		if (candidates.isEmpty()) return;
		int[] pick = candidates.get(rng.nextInt(candidates.size()));
		add(new PlacedPoi(PoiKind.EXECUTIVE_BROADCAST_SUITE, pick[0], pick[1], pick[2],
				Level4Layout.FLOOR_Y, false, pick[3]), usedCells);
	}

	private void placeFrozenFriday1998(Level4Plan plan, SplittableRandom rng, Set<Integer> usedCells) {
		List<Integer> candidates = new ArrayList<>();
		for (int id = 0; id < plan.rooms.size(); id++) {
			RoomBox room = plan.rooms.get(id);
			if (room.kind() != ZoneKind.SUITE || room.w() > 2 || room.h() > 2) continue;
			boolean free = true;
			int doorDir = -1;
			for (int a = room.i(); a < room.i() + room.w() && free; a++) {
				for (int b = room.j(); b < room.j() + room.h() && free; b++) {
					int c = Level4Plan.cell(a, b);
					if (usedCells.contains(c) || plan.balconyDir[c] >= 0 || plan.nearHumanBase(a, b)) {
						free = false;
						break;
					}
					if (a == room.i() && b == room.j()) {
						for (int d = 0; d < 4; d++) {
							if (plan.edge(c, d) == Edge.DOOR) {
								doorDir = d;
								break;
							}
						}
					}
				}
			}
			if (free && doorDir >= 0) candidates.add(id);
		}
		if (candidates.isEmpty()) return;
		int pickedId = candidates.get(rng.nextInt(candidates.size()));
		RoomBox room = plan.rooms.get(pickedId);
		int anchorCell = Level4Plan.cell(room.i(), room.j());
		int doorDir = NORTH;
		for (int d = 0; d < 4; d++) {
			if (plan.edge(anchorCell, d) == Edge.DOOR) {
				doorDir = d;
				break;
			}
		}
		PlacedPoi anchor = new PlacedPoi(PoiKind.FROZEN_FRIDAY_1998, room.i(), room.j(),
				Level4Plan.opposite(doorDir), Level4Layout.FLOOR_Y, false, pickedId);
		pois.add(anchor);
		for (int a = room.i(); a < room.i() + room.w(); a++) {
			for (int b = room.j(); b < room.j() + room.h(); b++) {
				int c = Level4Plan.cell(a, b);
				byCell.put(c, anchor);
				usedCells.add(c);
			}
		}
		int doorI = room.i() + DX[doorDir], doorJ = room.j() + DZ[doorDir];
		if (Level4Plan.inside(doorI, doorJ)) {
			int dc = Level4Plan.cell(doorI, doorJ);
			if (plan.zones[dc] == ZoneKind.OPEN_OFFICE && !usedCells.contains(dc) && plan.balconyDir[dc] < 0
					&& !Level4Plan.isSkybridgeCell(doorI, doorJ) && !plan.nearHumanBase(doorI, doorJ)) {
				byCell.put(dc, anchor);
				usedCells.add(dc);
			}
		}
	}

	/** Maps wall-relative (u in 1..5 along wall, v in 0..6 depth into cell from wall) to cell-local (cx, cz). */
	static int[] uvToLocal(int dirIntoRoom, int u, int v) {
		return switch (dirIntoRoom) {
			case SOUTH -> new int[] {u, v};
			case NORTH -> new int[] {u, CELL - v};
			case EAST -> new int[] {v, u};
			default -> new int[] {CELL - v, u};
		};
	}
}
