package dev.noclip.backrooms.level4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.level4.Level4Layout.Kind;
import dev.noclip.backrooms.level4.Level4Plan.PlacedExit;
import dev.noclip.backrooms.level4.Level4Plan.ZoneKind;
import dev.noclip.backrooms.level4.Level4PoiPlan.PlacedPoi;
import dev.noclip.backrooms.level4.Level4PoiPlan.PoiKind;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class Level4PoiPlanTest {
	@Test
	void batch1PoisAppearAndAvoidProtectedZonesAndExits() {
		Set<PoiKind> seen = EnumSet.noneOf(PoiKind.class);
		boolean sawNormalChalk = false, sawSlashedChalk = false;
		boolean sawPlenumDrip = false, sawOfficeDrip = false;
		boolean sawPaintBayWithBlackedAndTrap = false;

		for (long seed = 1; seed <= 16; seed++) {
			for (int rx = -1; rx <= 1; rx++) {
				for (int rz = -1; rz <= 1; rz++) {
					Level4Plan plan = Level4Plan.of(seed, rx, rz);
					Level4PoiPlan poiPlan = Level4PoiPlan.of(seed, rx, rz);

					Set<Integer> exitCells = new HashSet<>();
					for (PlacedExit pe : plan.exits) exitCells.add(pe.cell());

					for (PlacedPoi poi : poiPlan.pois) {
						seen.add(poi.kind());
						int c = poi.cell();
						ZoneKind z = plan.zones[c];
						assertNotEquals(ZoneKind.OMEGA_BASE, z);
						assertNotEquals(ZoneKind.AMOR_CAMP, z);
						assertNotEquals(ZoneKind.TBD_ROOM, z);
						assertNotEquals(ZoneKind.SOLID_CORE, z);
						assertNotEquals(ZoneKind.OUTSIDE, z);
						assertFalse(exitCells.contains(c), "POI overlapped exit cell in " + rx + "," + rz);

						if (poi.kind() == PoiKind.MEG_CHALK_WALL) {
							if (poi.variantFlag()) sawSlashedChalk = true;
							else sawNormalChalk = true;
						} else if (poi.kind() == PoiKind.CEILING_DRIP_SPOT) {
							if (poi.variantFlag()) sawPlenumDrip = true;
							else sawOfficeDrip = true;
						} else if (poi.kind() == PoiKind.WINDOW_PAINT_CART) {
							int wallDir = Level4Plan.opposite(poi.dir());
							int wi = poi.cellI() + (wallDir == Level4Plan.EAST ? 1 : 0);
							int wj = poi.cellJ() + (wallDir == Level4Plan.SOUTH ? 1 : 0);
							boolean hasBlacked = false, hasTrap = false;
							StringBuilder sb = new StringBuilder();
							for (int along = 1; along <= 5; along++) {
								int wx = rx * Level4Plan.SIZE + wi * Level4Plan.CELL
										+ (wallDir == Level4Plan.NORTH || wallDir == Level4Plan.SOUTH ? along : 0);
								int wz = rz * Level4Plan.SIZE + wj * Level4Plan.CELL
										+ (wallDir == Level4Plan.NORTH || wallDir == Level4Plan.SOUTH ? 0 : along);
								Kind wk = Level4Layout.tile(seed, wx, wz).kind();
								sb.append(wk).append(',');
								if (wk == Kind.WINDOW_BLACKED) hasBlacked = true;
								if (wk == Kind.WINDOW_TRAP) hasTrap = true;
							}
							assertTrue(hasBlacked && hasTrap,
									"Paint cart bay must have both blacked and trap windows at seed=" + seed
											+ " rx=" + rx + " rz=" + rz + " poi=" + poi
											+ " zone=" + z + " kinds=" + sb);
							sawPaintBayWithBlackedAndTrap = true;
						}
					}
				}
			}
		}

		for (PoiKind kind : PoiKind.values()) {
			if (kind.batch == 1) {
				assertTrue(seen.contains(kind), "Missing Batch 1 POI: " + kind);
			}
		}
		assertTrue(sawNormalChalk && sawSlashedChalk, "Expected both normal and slashed chalk walls");
		assertTrue(sawPlenumDrip && sawOfficeDrip, "Expected both plenum-linked and open-office drip spots");
		assertTrue(sawPaintBayWithBlackedAndTrap, "Expected half-painted window cart bays");
	}

	@Test
	void batch2PoisAppearWithValidStructuresAndAvoidProtectedZones() {
		Set<PoiKind> seen = EnumSet.noneOf(PoiKind.class);
		boolean sawLightwellMirror = false;

		for (long seed = 1; seed <= 24; seed++) {
			for (int rx = -1; rx <= 1; rx++) {
				for (int rz = -1; rz <= 1; rz++) {
					Level4Plan plan = Level4Plan.of(seed, rx, rz);
					Level4PoiPlan poiPlan = Level4PoiPlan.of(seed, rx, rz);

					Set<Integer> exitCells = new HashSet<>();
					for (PlacedExit pe : plan.exits) exitCells.add(pe.cell());

					for (PlacedPoi poi : poiPlan.pois) {
						if (poi.kind().batch != 2) continue;
						seen.add(poi.kind());
						int c = poi.cell();
						ZoneKind z = plan.zones[c];
						assertNotEquals(ZoneKind.OMEGA_BASE, z);
						assertNotEquals(ZoneKind.AMOR_CAMP, z);
						assertNotEquals(ZoneKind.TBD_ROOM, z);
						assertNotEquals(ZoneKind.SOLID_CORE, z);
						assertNotEquals(ZoneKind.OUTSIDE, z);
						assertFalse(exitCells.contains(c), "Batch 2 POI overlapped exit cell in " + rx + "," + rz);

						switch (poi.kind()) {
							case LOCKED_PBX_ROOM -> {
								assertEquals(ZoneKind.SUITE, z);
								assertTrue(poiPlan.isPbxSuiteRoom(poi.roomId()), "PBX suite must register pbxRoomId");
							}
							case DELAYED_REFLECTION_MIRROR -> {
								if (!poi.variantFlag()) {
									assertEquals(ZoneKind.LIGHTWELL, z);
									int wallDir = Level4Plan.opposite(poi.dir());
									int wi = poi.cellI() + (wallDir == Level4Plan.EAST ? 1 : 0);
									int wj = poi.cellJ() + (wallDir == Level4Plan.SOUTH ? 1 : 0);
									int wx = rx * Level4Plan.SIZE + wi * Level4Plan.CELL
											+ (wallDir == Level4Plan.NORTH || wallDir == Level4Plan.SOUTH ? 3 : 0);
									int wz = rz * Level4Plan.SIZE + wj * Level4Plan.CELL
											+ (wallDir == Level4Plan.NORTH || wallDir == Level4Plan.SOUTH ? 0 : 3);
									assertEquals(Kind.WINDOW_DARK_MIRROR, Level4Layout.tile(seed, wx, wz).kind(),
											"Expected dark mirror window on LIGHTWELL shaft edge");
									sawLightwellMirror = true;
								}
							}
							case PLENUM_MAINTENANCE_HUB -> {
								assertEquals(ZoneKind.DARK_WING, z);
								int cx = rx * Level4Plan.SIZE + poi.cellI() * Level4Plan.CELL + 3;
								int cz = rz * Level4Plan.SIZE + poi.cellJ() * Level4Plan.CELL + 3;
								Level4Layout.PlenumInfo pi = Level4Layout.plenumInfo(seed, cx, cz);
								assertTrue(pi != null && pi.ventGrate(), "Plenum hub must sit on DARK_WING center vent grate");
							}
							case STALLED_FREIGHT_ELEVATOR -> assertEquals(ZoneKind.ELEVATOR_LOBBY, z);
							case EVACUATED_OUTPOST -> {
								assertFalse(plan.hasOmega, "Evacuated outpost must not spawn in Omega region");
								for (int a = poi.cellI(); a <= poi.cellI() + 1; a++) {
									for (int b = poi.cellJ(); b <= poi.cellJ() + 1; b++) {
										int subC = Level4Plan.cell(a, b);
										assertEquals(ZoneKind.OPEN_OFFICE, plan.zones[subC]);
										assertFalse(plan.nearHumanBase(a, b));
										assertFalse(exitCells.contains(subC));
									}
								}
							}
							default -> {
							}
						}
					}
				}
			}
		}

		for (PoiKind kind : PoiKind.values()) {
			if (kind.batch == 2) {
				assertTrue(seen.contains(kind), "Missing Batch 2 POI: " + kind);
			}
		}
		assertTrue(sawLightwellMirror, "Expected at least one LIGHTWELL shaft DELAYED_REFLECTION_MIRROR");
	}

	@Test
	void batch3PoisAppearWithValidStructuresAndAvoidProtectedZones() {
		Set<PoiKind> seen = EnumSet.noneOf(PoiKind.class);

		for (long seed = 1; seed <= 24; seed++) {
			for (int rx = -1; rx <= 1; rx++) {
				for (int rz = -1; rz <= 1; rz++) {
					Level4Plan plan = Level4Plan.of(seed, rx, rz);
					Level4PoiPlan poiPlan = Level4PoiPlan.of(seed, rx, rz);

					Set<Integer> exitCells = new HashSet<>();
					for (PlacedExit pe : plan.exits) exitCells.add(pe.cell());

					for (PlacedPoi poi : poiPlan.pois) {
						if (poi.kind().batch != 3) continue;
						seen.add(poi.kind());
						int c = poi.cell();
						ZoneKind z = plan.zones[c];
						assertNotEquals(ZoneKind.OMEGA_BASE, z);
						assertNotEquals(ZoneKind.AMOR_CAMP, z);
						assertNotEquals(ZoneKind.TBD_ROOM, z);
						assertNotEquals(ZoneKind.SOLID_CORE, z);
						assertNotEquals(ZoneKind.OUTSIDE, z);
						assertFalse(exitCells.contains(c), "Batch 3 POI overlapped exit cell in " + rx + "," + rz);

						switch (poi.kind()) {
							case STORM_BREACH_CORRIDOR -> {
								assertEquals(ZoneKind.OPEN_OFFICE, z);
								int wallDir = Level4Plan.opposite(poi.dir());
								int wi = poi.cellI() + (wallDir == Level4Plan.EAST ? 1 : 0);
								int wj = poi.cellJ() + (wallDir == Level4Plan.SOUTH ? 1 : 0);
								int midX = rx * Level4Plan.SIZE + wi * Level4Plan.CELL
										+ (wallDir == Level4Plan.NORTH || wallDir == Level4Plan.SOUTH ? 3 : 0);
								int midZ = rz * Level4Plan.SIZE + wj * Level4Plan.CELL
										+ (wallDir == Level4Plan.NORTH || wallDir == Level4Plan.SOUTH ? 0 : 3);
								assertEquals(Kind.WINDOW_BREACHED, Level4Layout.tile(seed, midX, midZ).kind(),
										"Expected WINDOW_BREACHED center gap on storm breach corridor");
								int sideX = rx * Level4Plan.SIZE + wi * Level4Plan.CELL
										+ (wallDir == Level4Plan.NORTH || wallDir == Level4Plan.SOUTH ? 1 : 0);
								int sideZ = rz * Level4Plan.SIZE + wj * Level4Plan.CELL
										+ (wallDir == Level4Plan.NORTH || wallDir == Level4Plan.SOUTH ? 0 : 1);
								assertEquals(Kind.WINDOW_TRAP, Level4Layout.tile(seed, sideX, sideZ).kind(),
										"Expected WINDOW_TRAP flanking panes on storm breach corridor");
							}
							case CORRUPTED_RED_ATRIUM -> {
								assertEquals(ZoneKind.ATRIUM, z);
								assertFalse(rx == 0 && rz == 0, "Corrupted red atrium must not spawn in origin region");
							}
							case SUBTUNNEL_ABYSS_DOOR -> {
								assertTrue(plan.isTunnelCell(poi.cellI(), poi.cellJ()),
										"Abyss door must sit inside MEP sub-tunnel");
								assertTrue(poi.cellI() > plan.tunnelI0 && poi.cellI() < plan.tunnelI1,
										"Abyss door must sit between tunnel stairwells");
								ZoneKind southZone = plan.zones[Level4Plan.cell(poi.cellI(), poi.cellJ() + 1)];
								assertNotEquals(ZoneKind.OUTSIDE, southZone);
								assertNotEquals(ZoneKind.ELEVATOR_LOBBY, southZone);
								assertNotEquals(ZoneKind.AUDITORIUM, southZone);
							}
							default -> {
							}
						}
					}
				}
			}
		}

		for (PoiKind kind : PoiKind.values()) {
			if (kind.batch == 3) {
				assertTrue(seen.contains(kind), "Missing Batch 3 POI: " + kind);
			}
		}
	}

	@Test
	void megChalkSignHasValidBackingBlockAndAirCell() {
		for (long seed = 1; seed <= 8; seed++) {
			for (int rx = -1; rx <= 1; rx++) {
				for (int rz = -1; rz <= 1; rz++) {
					Level4PoiPlan poiPlan = Level4PoiPlan.of(seed, rx, rz);
					for (PlacedPoi poi : poiPlan.pois) {
						if (poi.kind() != PoiKind.MEG_CHALK_WALL) continue;
						int bx = rx * Level4Plan.SIZE + poi.cellI() * Level4Plan.CELL;
						int bz = rz * Level4Plan.SIZE + poi.cellJ() * Level4Plan.CELL;
						int sx, sz, wallDx, wallDz;
						if (poi.roomId() == -2) {
							sx = bx + 1;
							sz = bz;
							wallDx = -1;
							wallDz = 0;
						} else {
							int[] uv = Level4PoiPlan.uvToLocal(poi.dir(), 2, 1);
							sx = bx + uv[0];
							sz = bz + uv[1];
							wallDx = -Level4Plan.DX[poi.dir()];
							wallDz = -Level4Plan.DZ[poi.dir()];
						}
						assertTrue(Level4Layout.tile(seed, sx, sz).walkable(), "Sign cell not walkable at " + sx + "," + sz);
						Kind back = Level4Layout.tile(seed, sx + wallDx, sz + wallDz).kind();
						assertTrue(back == Kind.DRYWALL || back == Kind.PILLAR,
								"Expected solid drywall/pillar behind chalk sign, got " + back);
					}
				}
			}
		}
	}
}
