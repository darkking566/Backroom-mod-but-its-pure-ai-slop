package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Layout.Base;
import dev.noclip.backrooms.level1.Level1Layout.Ceiling;
import dev.noclip.backrooms.level1.Level1Layout.Kind;
import dev.noclip.backrooms.level1.Level1Layout.Tile;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rare monumental landmarks scattered across Level 1 hall districts (~7% of eligible halls).
 * Each landmark occupies a clean 32x32 central monument footprint (lx, lz in 16..47) with dramatic
 * multi-story verticality, while keeping district borders, settlements, exits and props unobstructed.
 */
public final class Level1Landmarks {
	private static final int D = Level1Layout.DISTRICT;
	private static final int FLOOR = Level1ChunkGenerator.FLOOR_Y;
	private static final int SALT = 89;
	private static final int PICK_SALT = 91;
	/** Percent chance an eligible hall district hosts a rare landmark. */
	private static final int CHANCE_PERCENT = 8;

	public enum Landmark {
		NONE,
		/** Two-story underground power substation with transformer floor, control catwalk and caged work lights. */
		ABANDONED_SUBSTATION,
		/** 40-block-tall flooded central elevator shaft with a stuck car and stacked observation galleries. */
		FLOODED_ELEVATOR_SHAFT,
		/** Collapsed multi-level concrete spiral parking ramp with broken slabs, rebar and lootable debris. */
		COLLAPSED_PARKING_SPIRAL,
		/** Towering 16-block-high lost-and-found warehouse packed with mega-shelves, crates and a catwalk. */
		LOST_AND_FOUND_VAULT,
		/** Fortified two-tier abandoned wanderer outpost with tarps, cots, lockers and a basement bunker. */
		ABANDONED_OUTPOST_RUIN,
		/** Surreal indoor two-tier highway overpass interchange crisscrossing inside a vaulted hall. */
		OVERPASS_INTERCHANGE
	}

	private Level1Landmarks() {
	}

	/** Which rare landmark stands in this 64x64 district, or NONE. */
	public static Landmark landmark(long seed, int dx, int dz) {
		if (dx == 0 && dz == 0) return Landmark.NONE;
		if (Level1Layout.kind(seed, dx, dz) == Kind.CORRIDORS) return Landmark.NONE;
		if (Level1Layout.base(seed, dx, dz) != Base.NONE) return Landmark.NONE;
		int roll = Math.floorMod(Level1Layout.hash(seed, dx, dz, SALT), 100);
		if (roll >= CHANCE_PERCENT) return Landmark.NONE;
		// Skip if an exit or prop sits right inside the central 32x32 monument zone.
		if (!isCenterClear(seed, dx, dz)) return Landmark.NONE;
		int pick = Math.floorMod(Level1Layout.hash(seed, dx, dz, PICK_SALT), 6);
		return switch (pick) {
			case 0 -> Landmark.ABANDONED_SUBSTATION;
			case 1 -> Landmark.FLOODED_ELEVATOR_SHAFT;
			case 2 -> Landmark.COLLAPSED_PARKING_SPIRAL;
			case 3 -> Landmark.LOST_AND_FOUND_VAULT;
			case 4 -> Landmark.ABANDONED_OUTPOST_RUIN;
			default -> Landmark.OVERPASS_INTERCHANGE;
		};
	}

	/** Ceiling Y for a landmark district. */
	public static int ceilingY(Landmark landmark) {
		return switch (landmark) {
			case ABANDONED_SUBSTATION -> 50;
			case FLOODED_ELEVATOR_SHAFT -> 56;
			case COLLAPSED_PARKING_SPIRAL -> 54;
			case LOST_AND_FOUND_VAULT -> 48;
			case ABANDONED_OUTPOST_RUIN -> 44;
			case OVERPASS_INTERCHANGE -> 54;
			case NONE -> Level1ChunkGenerator.CEILING_Y;
		};
	}

	private static boolean isCenterClear(long seed, int dx, int dz) {
		HallPlan plan = HallPlan.of(seed, dx, dz);
		int x0 = dx * D, z0 = dz * D;
		for (HallPlan.Placed placed : plan.placed()) {
			int lx = placed.spot()[0] - x0, lz = placed.spot()[1] - z0;
			if (lx >= 12 && lx <= 51 && lz >= 12 && lz <= 51) return false;
		}
		for (HallPlan.PlacedProp prop : plan.props()) {
			int lx = prop.spot()[0] - x0, lz = prop.spot()[1] - z0;
			if (lx >= 12 && lx <= 51 && lz >= 12 && lz <= 51) return false;
		}
		return true;
	}

	/**
	 * Generates the block for a rare landmark district, or null to fall back to the standard hall.
	 * Called after settlements/exits/props and before standard elevation.
	 */
	static BlockState block(long seed, Tile tile, Ceiling ceiling, int x, int y, int z) {
		if (y <= 0 || y >= Level1ChunkGenerator.HEIGHT - 1) return null;
		int dx = Math.floorDiv(x, D), dz = Math.floorDiv(z, D);
		Landmark lm = landmark(seed, dx, dz);
		if (lm == Landmark.NONE) return null;

		int topCeil = ceilingY(lm);
		int lx = Math.floorMod(x, D), lz = Math.floorMod(z, D);

		if (y > topCeil) return solidConcrete();
		if (y == topCeil) return state(Level1Blocks.GARAGE_CEILING);

		// Keep district perimeter walls solid up to the tall landmark ceiling.
		if (tile == Tile.HALL_WALL || tile == Tile.PERIMETER) {
			return y >= FLOOR ? state(Level1Blocks.GARAGE_WALL) : solidConcrete();
		}

		// Outer approach ring (outside 14..49): vaulted hall matching topCeil so the monument feels grand.
		boolean inMonument = lx >= 14 && lx <= 49 && lz >= 14 && lz <= 49;
		if (!inMonument) {
			if (y < FLOOR) return solidConcrete();
			if (y == FLOOR) return null;
			if (tile == Tile.PILLAR) return pillarAt(seed, x, y, z, FLOOR);
			if (tile == Tile.SHELF && y <= FLOOR + 2) return null;
			if (y == topCeil - 1) return hallFixture(ceiling);
			return air();
		}

		int u = lx - 16, v = lz - 16; // -2..33 (0..31 is the core 32x32 monument)
		return switch (lm) {
			case ABANDONED_SUBSTATION -> substation(seed, ceiling, x, y, z, u, v, topCeil);
			case FLOODED_ELEVATOR_SHAFT -> elevatorShaft(ceiling, y, u, v, topCeil);
			case COLLAPSED_PARKING_SPIRAL -> collapsedSpiral(seed, ceiling, x, y, z, u, v, topCeil);
			case LOST_AND_FOUND_VAULT -> lostAndFound(seed, ceiling, x, y, z, u, v, topCeil);
			case ABANDONED_OUTPOST_RUIN -> outpostRuin(seed, ceiling, x, y, z, u, v, topCeil);
			case OVERPASS_INTERCHANGE -> overpass(ceiling, y, u, v, topCeil);
			case NONE -> null;
		};
	}

	// ==================== 1. ABANDONED SUBSTATION ====================

	/** Two-story underground power substation: sunken generator hall (y=26), main ring (y=32), control catwalk (y=38). */
	private static BlockState substation(long seed, Ceiling ceiling, int x, int y, int z, int u, int v, int topCeil) {
		int lowerY = 26;
		if (y < lowerY) return solidConcrete();
		// Outer rim (u or v outside 0..31) stays at y = 32 with protective barriers.
		if (u < 0 || u > 31 || v < 0 || v > 31) {
			if (y < FLOOR) return solidConcrete();
			if (y == FLOOR) return state(Level1Blocks.GARAGE_FLOOR);
			if (y == topCeil - 1) return hallFixture(ceiling);
			return air();
		}

		// Descending stair on North side (u = 14..17, v = 0..5) from y=32 down to y=26
		if (u >= 14 && u <= 17 && v >= 0 && v <= 5) {
			int stepY = FLOOR - 1 - v; // 31..26
			if (y == stepY) return stairs(Direction.NORTH);
			if (y < stepY) return state(Level1Blocks.GARAGE_WALL);
			return y == topCeil - 1 ? hallFixture(ceiling) : air();
		}
		// Ascending stair on South side (u = 14..17, v = 25..30) from y=32 up to control catwalk y=38
		if (u >= 14 && u <= 17 && v >= 25 && v <= 30) {
			int stepY = FLOOR + 1 + (30 - v); // 33..38
			if (y == stepY) return stairs(Direction.NORTH);
			if (y < stepY && y >= FLOOR) return state(Level1Blocks.GARAGE_WALL);
			if (y < FLOOR) return solidConcrete();
			return y == topCeil - 1 ? hallFixture(ceiling) : air();
		}

		// Upper control catwalk at y = 38 along the east/west/south perimeter (u <= 4 || u >= 27 || v >= 27)
		boolean onCatwalk = u <= 4 || u >= 27 || v >= 27;
		boolean onCatwalkEdge = onCatwalk && (u == 4 || u == 27 || v == 27);
		if (onCatwalk && y >= 38) {
			if (y == 38) return state(Level1Blocks.GARAGE_FLOOR);
			if (y == 39 && onCatwalkEdge) return barrier(v == 27 ? Direction.Axis.X : Direction.Axis.Z);
			// Control consoles along the east and west catwalks
			if (y == 39 && (u == 1 || u == 30) && v >= 8 && v <= 24 && v % 4 == 0) return state(Level1Blocks.OFFICE_DESK);
			if (y == 40 && (u == 1 || u == 30) && v >= 8 && v <= 24 && v % 4 == 0) {
				return Level1Blocks.OLD_COMPUTER.defaultBlockState()
						.setValue(OldComputerBlock.FACING, u == 1 ? Direction.EAST : Direction.WEST);
			}
			if (y == topCeil - 1 && (u == 2 || u == 29) && v % 8 == 4) return state(Level1Blocks.CAGED_LAMP);
			return y == topCeil - 1 ? hallFixture(ceiling) : air();
		}

		// Lower transformer floor at y = 26
		if (y == lowerY) {
			return state((u % 6 == 0 || v % 6 == 0) ? Level1Blocks.DAMP_GARAGE_FLOOR : Level1Blocks.GARAGE_FLOOR);
		}
		// Giant rows of full-height copper cooling towers & generator banks on lower floor
		if ((u == 9 || u == 22) && (v == 10 || v == 16 || v == 22)) {
			if (y > lowerY && y < topCeil) return state(Level1Blocks.COPPER_PILLAR);
		}
		if ((u == 11 || u == 20) && (v == 10 || v == 16 || v == 22) && y == lowerY + 1) {
			return state(Level1Blocks.GENERATOR);
		}
		if ((u == 12 || u == 19) && (v == 11 || v == 17) && y == lowerY + 1) {
			return state(Level1Blocks.FUEL_DRUM);
		}
		if ((u == 6 || u == 25) && (v == 10 || v == 18) && y == lowerY + 1) {
			return Level1Blocks.ELECTRICAL_CABINET.defaultBlockState()
					.setValue(OldComputerBlock.FACING, u == 6 ? Direction.EAST : Direction.WEST);
		}
		// Overhead X-aligned pipe grid mounted directly under the ceiling
		if (y == topCeil - 1 && v % 6 == 0) return state(Level1Blocks.CEILING_PIPE);
		if (y == topCeil - 1) return hallFixture(ceiling);
		return air();
	}

	// ==================== 2. FLOODED ELEVATOR SHAFT ====================

	/** 40-block-tall open concrete shaft plunging from y=56 to flooded y=16, with a stuck elevator car and 4 observation decks. */
	private static BlockState elevatorShaft(Ceiling ceiling, int y, int u, int v, int topCeil) {
		int pitFloor = 16;
		if (y < pitFloor) return solidConcrete();
		if (u < 0 || u > 31 || v < 0 || v > 31) {
			if (y < FLOOR) return solidConcrete();
			if (y == FLOOR) return state(Level1Blocks.GARAGE_FLOOR);
			if (y == topCeil - 1) return hallFixture(ceiling);
			return air();
		}

		// Central open vertical shaft: u in [9..22], v in [9..22] (14x14 sheer drop!)
		boolean inShaft = u >= 9 && u <= 22 && v >= 9 && v <= 22;
		boolean onShaftRim = u >= 8 && u <= 23 && v >= 8 && v <= 23 && !inShaft;

		if (inShaft) {
			if (y == pitFloor) return state(Level1Blocks.PUDDLED_GARAGE_FLOOR);
			if (y == pitFloor + 1 || y == pitFloor + 2) return Blocks.WATER.defaultBlockState();
			// 4 vertical elevator guide rails running from flooded bottom to top ceiling
			if ((u == 10 || u == 21) && (v == 10 || v == 21)) return state(Level1Blocks.COPPER_PILLAR);
			// Stuck elevator car suspended mid-shaft at y = 35..40, u = 13..18, v = 13..18
			boolean inCar = u >= 13 && u <= 18 && v >= 13 && v <= 18 && y >= 35 && y <= 40;
			if (inCar) {
				boolean carWall = u == 13 || u == 18 || v == 13 || v == 18;
				if (y == 35 || y == 40) return state(Level1Blocks.GARAGE_WALL);
				if (carWall) {
					// Open doorway facing north so you can see inside the stuck elevator!
					if (v == 13 && (u == 15 || u == 16) && y <= 38) return air();
					return state(Level1Blocks.GARAGE_WALL);
				}
				if (y == 39 && u == 15 && v == 15) return state(Level1Blocks.CAGED_LAMP);
				return air();
			}
			// Suspension cables rising from the stuck elevator car to y = 55
			if (y > 40 && (u == 14 || u == 17) && (v == 14 || v == 17)) return state(Level1Blocks.SCAFFOLD_POLE);
			return y == topCeil - 1 ? hallFixture(ceiling) : air();
		}

		// Stacked observation galleries wrapping the shaft at y = 24, 32, 40, 48!
		// Connecting switchback staircases on the West gallery (u = 2..5)
		// Stair down 32 -> 24 along v = 10..17
		if (u >= 2 && u <= 4 && v >= 10 && v <= 17 && y >= 24 && y <= 35) {
			int stepY = FLOOR - 1 - (v - 10); // 31..24
			if (y == stepY) return stairs(Direction.NORTH);
			if (y < stepY) return state(Level1Blocks.GARAGE_WALL);
			return air();
		}
		// Stair up 32 -> 40 along East gallery (u = 27..29, v = 10..17)
		if (u >= 27 && u <= 29 && v >= 10 && v <= 17 && y >= 32 && y <= 43) {
			int stepY = FLOOR + 1 + (v - 10); // 33..40
			if (y == stepY) return stairs(Direction.SOUTH);
			if (y < stepY) return state(Level1Blocks.GARAGE_WALL);
			return air();
		}
		// Stair up 40 -> 48 along North gallery (v = 2..4, u = 12..19)
		if (v >= 2 && v <= 4 && u >= 12 && u <= 19 && y >= 40 && y <= 51) {
			int stepY = 41 + (u - 12); // 41..48
			if (y == stepY) return stairs(Direction.EAST);
			if (y < stepY) return state(Level1Blocks.GARAGE_WALL);
			return air();
		}

		// Outer concrete walls enclosing lower gallery (y < 32) and upper galleries (y > 38)
		if ((u == 0 || u == 31 || v == 0 || v == 31) && (y < FLOOR || y > 38)) {
			return state(Level1Blocks.GARAGE_WALL);
		}
		// Gallery floors at y = 24, 32, 40, 48 with safety railings around the shaft
		if (y == 24 || y == 32 || y == 40 || y == 48) {
			return state(Level1Blocks.GARAGE_FLOOR);
		}
		if (onShaftRim && (y == 25 || y == 33 || y == 41 || y == 49)) {
			return barrier((v == 8 || v == 23) ? Direction.Axis.X : Direction.Axis.Z);
		}
		if ((y == 31 || y == 39 || y == 47 || y == 55) && (u == 5 || u == 26) && (v == 5 || v == 26)) {
			return state(Level1Blocks.CAGED_LAMP);
		}
		if (y < 24) return solidConcrete();
		return y == topCeil - 1 ? hallFixture(ceiling) : air();
	}

	// ==================== 3. COLLAPSED PARKING SPIRAL ====================

	/** Broken multi-level concrete spiral parking ramp looping around a central core with debris and floodlights. */
	private static BlockState collapsedSpiral(long seed, Ceiling ceiling, int x, int y, int z, int u, int v, int topCeil) {
		int baseY = 26;
		if (y < baseY) return solidConcrete();
		if (u < 0 || u > 31 || v < 0 || v > 31) {
			if (y < FLOOR) return solidConcrete();
			if (y == FLOOR) return state(Level1Blocks.GARAGE_FLOOR);
			if (y == topCeil - 1) return hallFixture(ceiling);
			return air();
		}

		double dist = Math.hypot(u - 15.5, v - 15.5);
		// Central massive concrete spiral core
		if (dist <= 5.5) {
			if (y <= 50) return state(Level1Blocks.GARAGE_PILLAR);
			return y == topCeil - 1 ? hallFixture(ceiling) : air();
		}

		// Spiral ramp ring (dist 5.5..14.5) climbing 16 blocks per full 360-degree turn!
		if (dist <= 14.5) {
			double angle = Math.atan2(v - 15.5, u - 15.5); // -PI..PI
			double norm = (angle + Math.PI) / (2.0 * Math.PI); // 0..1
			// Two stacked spiral turns: lower turn (y = 26..42) and upper broken turn (y = 42..50)
			int ramp1Y = baseY + (int) Math.round(norm * 16.0);
			int ramp2Y = ramp1Y + 12;
			int crumble = Math.floorMod(Level1Layout.hash(seed, x, z, 149), 100);

			// Lower spiral deck (mostly intact so players can actually walk up it!)
			if (y == ramp1Y) {
				if (dist > 13.2) return state(Level1Blocks.GARAGE_WALL);
				return state(Math.abs(dist - 10.0) < 0.7 ? Level1Blocks.GARAGE_FLOOR_LINE : Level1Blocks.GARAGE_FLOOR);
			}
			if (y == ramp1Y + 1 && dist > 13.2) return barrier(Math.abs(u - 15.5) > Math.abs(v - 15.5) ? Direction.Axis.Z : Direction.Axis.X);
			if (y == ramp1Y + 1 && crumble < 3 && dist > 7 && dist < 12) return state(Level1Blocks.DEBRIS_PILE);
			if (y == ramp1Y + 1 && crumble == 4 && dist > 7 && dist < 12) return state(Level1Blocks.CONSTRUCTION_CONE);

			// Upper collapsed turn: jagged broken slabs and hanging scaffold rebar
			if (ramp2Y <= 50 && norm < 0.72) {
				if (y == ramp2Y && crumble < 78) return state(Level1Blocks.GARAGE_FLOOR);
				if (y < ramp2Y && y >= ramp2Y - 3 && crumble < 12) return state(Level1Blocks.SCAFFOLD_POLE);
			}
		}

		if (y == baseY) return state(Level1Blocks.DAMP_GARAGE_FLOOR);
		if (y == baseY + 1 && (u == 2 || u == 29) && (v == 2 || v == 29)) return state(Level1Blocks.FLOODLIGHT);
		return y == topCeil - 1 ? hallFixture(ceiling) : air();
	}

	// ==================== 4. LOST AND FOUND VAULT ====================

	/** Towering 16-block-high storage cavern packed with mega-shelves, lootable crates/debris, and a high catwalk. */
	private static BlockState lostAndFound(long seed, Ceiling ceiling, int x, int y, int z, int u, int v, int topCeil) {
		if (y < FLOOR) return solidConcrete();
		if (u < 0 || u > 31 || v < 0 || v > 31) {
			if (y == FLOOR) return state(Level1Blocks.GARAGE_FLOOR);
			if (y == topCeil - 1) return hallFixture(ceiling);
			return air();
		}
		if (y == FLOOR) return state(Level1Blocks.GARAGE_FLOOR);

		// Staircase on west side (u = 2..3, v = 10..15) climbing to the y=38 catwalk
		if ((u == 2 || u == 3) && v >= 10 && v <= 15) {
			int stepY = FLOOR + 1 + (v - 10); // 33..38
			if (y == stepY) return stairs(Direction.SOUTH);
			if (y < stepY) return state(Level1Blocks.GARAGE_WALL);
			return air();
		}

		// Elevated inspection catwalk around the perimeter at y = 38
		boolean catwalk = u <= 3 || u >= 28 || v <= 3 || v >= 28;
		if (catwalk) {
			if (y == 38) return state(Level1Blocks.GARAGE_FLOOR);
			boolean rim = u == 3 || u == 28 || v == 3 || v == 28;
			if (y == 39 && rim) return barrier((v == 3 || v == 28) ? Direction.Axis.X : Direction.Axis.Z);
			if (y == 39 && (u == 1 || u == 30) && v % 10 == 5) return state(Level1Blocks.FLOODLIGHT);
		}

		// Massive 7-block-tall shelf aisles inside (u = 7..24, v = 6..25)
		if (u >= 7 && u <= 24 && v >= 6 && v <= 25) {
			boolean shelfRow = (u == 8 || u == 13 || u == 18 || u == 23) && (v != 15 && v != 16);
			if (shelfRow && y >= FLOOR + 1 && y <= FLOOR + 7) {
				return state(Level1Blocks.METAL_SHELF);
			}
			// Scattered lootable crates, debris piles, old computers and strange lost furniture in the aisles
			if (!shelfRow && y == FLOOR + 1) {
				int r = Math.floorMod(Level1Layout.hash(seed, x, z, 157), 60);
				if (r == 0) return state(Level1Blocks.SUPPLY_CRATE);
				if (r == 1 || r == 2) return state(Level1Blocks.DEBRIS_PILE);
				if (r == 3) return state(Level1Blocks.SUNKEN_CHAIR);
				if (r == 4) return state(Level1Blocks.FLOOR_PAINTING);
			}
		}
		return y == topCeil - 1 ? hallFixture(ceiling) : air();
	}

	// ==================== 5. ABANDONED OUTPOST RUIN ====================

	/** Fortified two-tier abandoned camp with Jersey walls, blue tarps, cots, lockers, fire barrel and basement bunker. */
	private static BlockState outpostRuin(long seed, Ceiling ceiling, int x, int y, int z, int u, int v, int topCeil) {
		int bunkerY = 26;
		if (y < bunkerY) return solidConcrete();
		if (u < 0 || u > 31 || v < 0 || v > 31) {
			if (y < FLOOR) return solidConcrete();
			if (y == FLOOR) return state(Level1Blocks.GARAGE_FLOOR);
			if (y == topCeil - 1) return hallFixture(ceiling);
			return air();
		}

		// Underground concrete bunker under the center (u = 10..21, v = 10..21, y = 26..30)
		// Descending stairs from main floor at u = 11..12, v = 4..9
		if ((u == 11 || u == 12) && v >= 4 && v <= 9 && y >= bunkerY && y <= FLOOR) {
			int stepY = FLOOR - (v - 4); // 32 down to 27
			if (y == stepY) return stairs(Direction.NORTH);
			if (y > stepY) return air();
			return state(Level1Blocks.GARAGE_WALL);
		}
		if (y < FLOOR) {
			boolean inBunker = u >= 10 && u <= 21 && v >= 10 && v <= 21;
			if (!inBunker) return solidConcrete();
			if (y == bunkerY) return state(Level1Blocks.CORRIDOR_FLOOR);
			if (y == FLOOR - 1) return state(Level1Blocks.GARAGE_CEILING);
			if (y == bunkerY + 1 && (u == 11 || u == 20) && (v == 12 || v == 15 || v == 18)) {
				return state(Level1Blocks.SUPPLY_LOCKER);
			}
			if (y == bunkerY + 1 && u == 16 && v == 16) return state(Level1Blocks.OFFICE_DESK);
			if (y == bunkerY + 2 && u == 16 && v == 16) return state(Level1Blocks.OLD_COMPUTER);
			if (y == FLOOR - 2 && u == 15 && v == 15) return state(Level1Blocks.CAGED_LAMP);
			return air();
		}

		if (y == FLOOR) return state(Level1Blocks.GARAGE_FLOOR);

		// Outer Jersey barrier defense wall (with wide gates on N/S/E/W)
		boolean ring = u == 2 || u == 29 || v == 2 || v == 29;
		boolean gate = (u >= 14 && u <= 17) || (v >= 14 && v <= 17);
		if (ring && !gate && u >= 2 && u <= 29 && v >= 2 && v <= 29) {
			if (y == FLOOR + 1) return barrier((v == 2 || v == 29) ? Direction.Axis.X : Direction.Axis.Z);
			if (y == FLOOR + 2 && (u == 2 || u == 29) && (v == 2 || v == 29)) return state(Level1Blocks.FLOODLIGHT);
		}

		// Blue tarp tent roofs overhead at y = FLOOR + 4 on East and West flanks
		boolean tentW = u >= 5 && u <= 9 && v >= 12 && v <= 22;
		boolean tentE = u >= 22 && u <= 26 && v >= 12 && v <= 22;
		if ((tentW || tentE) && y == FLOOR + 4) return state(Level1Blocks.TARP);
		if ((tentW || tentE) && y >= FLOOR + 1 && y <= FLOOR + 3 && (v == 12 || v == 22) && (u == 5 || u == 9 || u == 22 || u == 26)) {
			return state(Level1Blocks.SCAFFOLD_POLE);
		}
		// Abandoned cots & bedrolls under the tarps
		if (y == FLOOR + 1 && tentW && u == 7 && v % 3 == 0) return state(Level1Blocks.COT);
		if (y == FLOOR + 1 && tentE && u == 24 && v % 3 == 0) return state(Level1Blocks.BEDROLL);
		// Central still-burning barrel fire + debris
		if (y == FLOOR + 1 && u == 16 && v == 16) return state(Level1Blocks.BARREL_FIRE);
		if (y == FLOOR + 1 && (u == 14 || u == 18) && v == 20) return state(Level1Blocks.DEBRIS_PILE);
		return y == topCeil - 1 ? hallFixture(ceiling) : air();
	}

	// ==================== 6. OVERPASS INTERCHANGE ====================

	/** Two surreal multi-level concrete highway overpasses crossing inside the vaulted hall at y=39 and y=46. */
	private static BlockState overpass(Ceiling ceiling, int y, int u, int v, int topCeil) {
		if (y < FLOOR) return solidConcrete();
		if (u < 0 || u > 31 || v < 0 || v > 31) {
			if (y == FLOOR) return state(Level1Blocks.GARAGE_FLOOR);
			if (y == topCeil - 1) return hallFixture(ceiling);
			return air();
		}
		if (y == FLOOR) return state(Level1Blocks.GARAGE_FLOOR);

		// Highway 1 (East-West) at y = 39 along v in [12..19]
		boolean hwy1 = v >= 12 && v <= 19;
		// Highway 2 (North-South) at y = 46 along u in [12..19]
		boolean hwy2 = u >= 12 && u <= 19;

		// Giant concrete highway piers supporting both overpasses
		boolean pier = (u == 4 || u == 27) && (v == 13 || v == 18)
				|| (v == 4 || v == 27) && (u == 13 || u == 18);
		if (pier && y > FLOOR && y < 46) return state(Level1Blocks.GARAGE_PILLAR);

		// Sweeping staircase up to Highway 1 (y = 32 -> 39) at u = 5..11, v = 9..11
		if (v >= 9 && v <= 11 && u >= 5 && u <= 11) {
			int stepY = FLOOR + 1 + (u - 5); // 33..39
			if (y == stepY) return stairs(Direction.EAST);
			if (y < stepY) return state(Level1Blocks.GARAGE_WALL);
			return air();
		}
		// Staircase from Highway 1 (39) up to Highway 2 (46) at u = 20..22, v = 13..19
		if (u >= 20 && u <= 22 && v >= 13 && v <= 19 && y >= 39 && y <= 49) {
			int stepY = 40 + (v - 13); // 40..46
			if (y == stepY) return stairs(Direction.SOUTH);
			if (y < stepY && y > 39) return state(Level1Blocks.GARAGE_WALL);
			if (y > stepY) return air();
		}

		// Highway 1 deck (y = 39)
		if (hwy1) {
			if (y == 39) return state((v == 15 || v == 16) ? Level1Blocks.GARAGE_FLOOR_LINE : Level1Blocks.GARAGE_FLOOR);
			if (y == 40 && (v == 12 || v == 19)) return barrier(Direction.Axis.X);
			if (y == 40 && u == 7 && v == 14) return Level1Blocks.ABANDONED_CAR.defaultBlockState().setValue(OldComputerBlock.FACING, Direction.EAST);
			if (y == 40 && u == 23 && v == 17) return Level1Blocks.ABANDONED_VAN.defaultBlockState().setValue(OldComputerBlock.FACING, Direction.WEST);
			if (y == 38 && u % 8 == 4 && v == 15) return state(Level1Blocks.CAGED_LAMP);
		}
		// Highway 2 deck (y = 46)
		if (hwy2) {
			if (y == 46) return state((u == 15 || u == 16) ? Level1Blocks.GARAGE_FLOOR_LINE : Level1Blocks.GARAGE_FLOOR);
			if (y == 47 && (u == 12 || u == 19)) return barrier(Direction.Axis.Z);
			if (y == 47 && u == 14 && v == 8) return Level1Blocks.ABANDONED_CAR.defaultBlockState().setValue(OldComputerBlock.FACING, Direction.SOUTH);
			if (y == 45 && v % 8 == 4 && u == 15) return state(Level1Blocks.CAGED_LAMP);
		}
		return y == topCeil - 1 ? hallFixture(ceiling) : air();
	}

	private static BlockState pillarAt(long seed, int x, int y, int z, int deckFloor) {
		if (y == deckFloor + 1) return state(Level1Blocks.GARAGE_PILLAR_BASE);
		if (y == deckFloor + 2 && Level1Layout.kindAt(seed, x, z) == Kind.PARKING) {
			int bx = Math.floorMod(x, Level1Layout.BAY_X), bz = Math.floorMod(z, Level1Layout.BAY_Z);
			if (bx != bz) return state(Level1Blocks.GARAGE_PILLAR_MARKED);
		}
		return state(Level1Blocks.GARAGE_PILLAR);
	}

	private static BlockState hallFixture(Ceiling ceiling) {
		return switch (ceiling) {
			case BEAM -> state(Level1Blocks.GARAGE_CEILING);
			case LAMP -> state(Level1Blocks.GARAGE_LIGHT);
			case DEAD_LAMP -> state(Level1Blocks.BROKEN_GARAGE_LIGHT);
			case PIPE -> state(Level1Blocks.CEILING_PIPE);
			case LEAK -> state(Level1Blocks.LEAKING_CEILING_PIPE);
			default -> air();
		};
	}

	private static BlockState barrier(Direction.Axis axis) {
		return Level1Blocks.JERSEY_BARRIER.defaultBlockState().setValue(AxisShapedBlock.AXIS, axis);
	}

	private static BlockState stairs(Direction facing) {
		return Level1Blocks.CONCRETE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
	}

	private static BlockState solidConcrete() {
		return Blocks.CONCRETE.gray().defaultBlockState();
	}

	private static BlockState state(Block block) {
		return block.defaultBlockState();
	}

	private static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}
}
