package dev.noclip.backrooms.level4;

import dev.noclip.backrooms.level1.Level1Blocks;
import dev.noclip.backrooms.level4.Level4Layout.Kind;
import dev.noclip.backrooms.level4.Level4Layout.Tile;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Pure block-less geometry classifier and Minecraft block mapper for Level 4's ultra-rare
 * full-skyscraper megastructure, "Atrium Zero".
 */
final class Level4AtriumZero {
	static final int CENTER = 56;
	static final int FLOOR_Y = Level4Layout.FLOOR_Y;
	static final int UPPER_Y = 48;
	static final int LOWER_Y = 18;
	static final int MIN_Y = -64;
	static final int MAX_Y = 127;

	private record ElevatorSpec(int ex, int ey, int ez, Part light) {
	}

	private static final ElevatorSpec[] ELEVATORS = {
			new ElevatorSpec(34, 54, 34, Part.TROFFER_DIM),
			new ElevatorSpec(78, 43, 34, Part.TROFFER_FLICKER),
			new ElevatorSpec(34, 19, 78, Part.TROFFER_DIM),
			new ElevatorSpec(78, 66, 78, Part.TROFFER_LIT)
	};

	enum Part {
		AIR,
		BEDROCK,
		ABYSS_WATER,
		CARPET,
		DAMP_CARPET,
		ACOUSTIC_CEILING,
		TROFFER_LIT,
		TROFFER_DIM,
		TROFFER_FLICKER,
		TROFFER_BROKEN,
		PILLAR,
		PILLAR_UPPER,
		DRYWALL_UPPER,
		RADIATOR_WALL,
		CONCRETE_GRAY,
		CONCRETE_LIGHT_GRAY,
		POLISHED_ANDESITE,
		RAIN_WINDOW,
		GLASS_WALL,
		ELEVATOR_FRAME,
		ALMOND_BASIN,
		ALMOND_POOL,
		WATERFALL,
		CAGED_LAMP,
		STAIR_NORTH,
		STAIR_SOUTH,
		STAIR_EAST,
		STAIR_WEST,
		IRON_BARS,
		IRON_BARS_ES,
		IRON_BARS_WS,
		IRON_BARS_NS,
		LADDER_NORTH,
		STEEL_SAFE_SOUTH,
		STEEL_SAFE_WEST,
		SUPPLY_CRATE_SOUTH,
		SUPPLY_CRATE_EAST,
		SLEEPING_BAG_WEST,
		ALMOND_BOTTLES,
		EXIT_DOOR_11_LOWER,
		EXIT_DOOR_11_UPPER,
		EXIT_SIGN_SOUTH,
		ATRIUM_PLAQUE
	}

	private Level4AtriumZero() {
	}

	static BlockState blockAt(long seed, int x, int y, int z, Tile tile, boolean exterior) {
		int lx = Math.floorMod(x, Level4Plan.SIZE);
		int lz = Math.floorMod(z, Level4Plan.SIZE);
		Part part = partAt(seed, lx, y, lz, tile);
		Direction.Axis trofferAxis = Math.abs(lx - CENTER) >= 35 ? Direction.Axis.Z : Direction.Axis.X;
		return toBlockState(part, tile, trofferAxis);
	}

	static Part partAt(long seed, int lx, int y, int lz, Tile tile) {
		if (y <= MIN_Y) return Part.BEDROCK;
		int ax = Math.abs(lx - CENTER);
		int az = Math.abs(lz - CENTER);
		int cheb = Math.max(ax, az);
		int manh = ax + az;

		if (cheb == 42) {
			return exteriorSkinPart(seed, lx, y, lz, tile);
		}
		if (cheb >= 35) {
			return perimeterRingPart(seed, lx, y, lz, ax, az, cheb);
		}
		return shaftPart(lx, y, lz, ax, az, cheb, manh);
	}

	private static Part exteriorSkinPart(long seed, int lx, int y, int lz, Tile tile) {
		if (y >= MAX_Y) return Part.AIR;
		if (y >= FLOOR_Y && y <= FLOOR_Y + 5) {
			if (tile.kind() == Kind.AIR) {
				if (y == FLOOR_Y) return Part.CARPET;
				if (y == FLOOR_Y + 5) return Part.ACOUSTIC_CEILING;
				return Part.AIR;
			}
			if (tile.kind() == Kind.PILLAR) {
				if (y == FLOOR_Y) return Part.CARPET;
				if (y == FLOOR_Y + 1) return Part.PILLAR;
				return Part.PILLAR_UPPER;
			}
			if (tile.kind() == Kind.WINDOW_RAIN) {
				if (y == FLOOR_Y) return Part.CARPET;
				if (y == FLOOR_Y + 1) return Part.RADIATOR_WALL;
				if (y == FLOOR_Y + 2 || y == FLOOR_Y + 3) return Part.RAIN_WINDOW;
				return Part.DRYWALL_UPPER;
			}
			if (y == FLOOR_Y) return Part.CARPET;
			return Part.DRYWALL_UPPER;
		}
		if (tile.kind() == Kind.PILLAR) return Part.CONCRETE_LIGHT_GRAY;
		int story = Math.floorDiv(y - FLOOR_Y, 5);
		int sh = Math.floorMod(y - FLOOR_Y, 5);
		if (sh == 0 || sh == 4) return Part.CONCRETE_GRAY;
		if (sh == 1) return Part.CONCRETE_LIGHT_GRAY;
		int cellX = Math.floorDiv(lx, Level4Plan.CELL);
		int cellZ = Math.floorDiv(lz, Level4Plan.CELL);
		boolean lit = Math.floorMod(Level4Layout.hash(seed, cellX + story * 31, cellZ - story * 17, 497), 100) < 14;
		return lit ? Part.TROFFER_DIM : Part.RAIN_WINDOW;
	}

	private static Part perimeterRingPart(long seed, int lx, int y, int lz, int ax, int az, int cheb) {
		if (y >= MAX_Y) return Part.AIR;
		if (y == MAX_Y - 1) return Part.CONCRETE_GRAY;

		// Diagonal NW & SE 5x5 glass spiral staircases (y = 32 -> 48)
		Part stair = spiralStairPart(lx, y, lz);
		if (stair != null) return stair;

		if (y >= FLOOR_Y && y <= FLOOR_Y + 5) {
			return ringFloorPart(seed, lx, y, lz, ax, az, cheb, FLOOR_Y, true);
		}

		// Upper observation gallery ring (y = 48..53)
		if (y >= UPPER_Y && y <= UPPER_Y + 5) {
			if (y == UPPER_Y + 1) {
				if (lx == 95 && lz == 16) return Part.SUPPLY_CRATE_SOUTH;
				if (lx == 96 && lz == 16) return Part.STEEL_SAFE_SOUTH;
				if (lx == 96 && lz == 18) return Part.SLEEPING_BAG_WEST;
				if (lx == 94 && lz == 16) return Part.ALMOND_BOTTLES;
				if (lx == 93 && lz == 18) return Part.CAGED_LAMP;
			}
			return ringFloorPart(seed, lx, y, lz, ax, az, cheb, UPPER_Y, false);
		}

		// 30+ story ghost office curtain wall & hollow bay shells above and below galleries
		int story = Math.floorDiv(y - FLOOR_Y, 5);
		int sh = Math.floorMod(y - FLOOR_Y, 5);
		if (cheb == 35) {
			if (sh == 0) return Part.CONCRETE_GRAY;
			if (sh == 4) return Part.CONCRETE_LIGHT_GRAY;
			if ((ax == 35 && Math.floorMod(lz, 7) == 0) || (az == 35 && Math.floorMod(lx, 7) == 0)) {
				return Part.PILLAR_UPPER;
			}
			return Part.RAIN_WINDOW;
		}
		if (cheb >= 36 && cheb <= 38) {
			if (sh == 0) return Part.CARPET;
			if (sh == 4) {
				boolean centerLight = cheb == 37
						&& ((ax == 37 && Math.floorMod(lz, 7) == 3) || (az == 37 && Math.floorMod(lx, 7) == 3));
				if (centerLight) {
					int bay = (ax == 37) ? Math.floorDiv(lz, 7) : Math.floorDiv(lx, 7);
					int side = (ax == 37) ? (lx < CENTER ? 1 : 2) : (lz < CENTER ? 3 : 4);
					int roll = Math.floorMod(Level4Layout.hash(seed, story * 13 + side, bay, 463), 100);
					if (roll < 22) return Part.TROFFER_LIT;
					if (roll < 36) return Part.TROFFER_DIM;
					if (roll < 42) return Part.TROFFER_FLICKER;
					return Part.TROFFER_BROKEN;
				}
				return Part.ACOUSTIC_CEILING;
			}
			return Part.AIR;
		}
		if (cheb == 39 && sh >= 1 && sh <= 3) {
			return Part.DRYWALL_UPPER;
		}
		return Part.CONCRETE_GRAY;
	}

	private static Part ringFloorPart(long seed, int lx, int y, int lz, int ax, int az, int cheb, int baseY, boolean openPortals) {
		if (cheb == 35) {
			if (openPortals && Math.min(ax, az) <= 1) {
				if (y == baseY) return Part.POLISHED_ANDESITE;
				if (y == baseY + 5) return Part.ACOUSTIC_CEILING;
				return Part.AIR;
			}
			boolean pillar = (openPortals && Math.min(ax, az) == 2)
					|| (ax == 35 && Math.floorMod(lz, 7) == 0)
					|| (az == 35 && Math.floorMod(lx, 7) == 0);
			if (pillar) {
				if (y == baseY) return Part.POLISHED_ANDESITE;
				if (y == baseY + 1) return Part.PILLAR;
				return Part.PILLAR_UPPER;
			}
			if (y == baseY) return Part.POLISHED_ANDESITE;
			if (y == baseY + 1 || y == baseY + 2) return Part.GLASS_WALL;
			if (y == baseY + 5) return Part.CONCRETE_LIGHT_GRAY;
			return Part.AIR;
		}
		if (y == baseY) {
			if (cheb == 36 && Math.floorMod(Level4Layout.hash(seed, lx, lz, 452), 100) < 35) {
				return Part.DAMP_CARPET;
			}
			return Part.CARPET;
		}
		if (y == baseY + 5) {
			if ((ax == 38 || az == 38) && Math.floorMod(lx + lz, 7) == 3) {
				return Part.TROFFER_LIT;
			}
			return Part.ACOUSTIC_CEILING;
		}
		return Part.AIR;
	}

	private static Part spiralStairPart(int lx, int y, int lz) {
		if (y < FLOOR_Y || y > UPPER_Y + 5) return null;
		boolean nw = lx >= 15 && lx <= 19 && lz >= 15 && lz <= 19;
		boolean se = lx >= 93 && lx <= 97 && lz >= 93 && lz <= 97;
		if (!nw && !se) return null;

		int dx = nw ? lx - 15 : 97 - lx;
		int dz = nw ? lz - 15 : 97 - lz;

		if (y == UPPER_Y + 5) return Part.ACOUSTIC_CEILING;

		// Inner 3x3 glass & pillar core
		if (dx >= 1 && dx <= 3 && dz >= 1 && dz <= 3) {
			if (y == FLOOR_Y) return Part.POLISHED_ANDESITE;
			if (dx == 2 && dz == 2) return y == FLOOR_Y + 1 ? Part.PILLAR : Part.PILLAR_UPPER;
			return y <= UPPER_Y + 3 ? Part.GLASS_WALL : Part.AIR;
		}

		// Entry / top exit column at (0, 4)
		if (dx == 0 && dz == 4) {
			if (y == FLOOR_Y || y == UPPER_Y - 1 || y == UPPER_Y) return Part.POLISHED_ANDESITE;
			return Part.AIR;
		}

		int stepY;
		Part stairPart;
		if (dz == 4 && dx >= 1 && dx <= 3) {
			stepY = FLOOR_Y + dx;
			stairPart = nw ? Part.STAIR_EAST : Part.STAIR_WEST;
		} else if (dx == 4 && dz == 4) {
			stepY = FLOOR_Y + 4;
			stairPart = Part.POLISHED_ANDESITE;
		} else if (dx == 4 && dz >= 1 && dz <= 3) {
			stepY = FLOOR_Y + 4 + (4 - dz);
			stairPart = nw ? Part.STAIR_NORTH : Part.STAIR_SOUTH;
		} else if (dx == 4 && dz == 0) {
			stepY = FLOOR_Y + 8;
			stairPart = Part.POLISHED_ANDESITE;
		} else if (dz == 0 && dx >= 1 && dx <= 3) {
			stepY = FLOOR_Y + 8 + (4 - dx);
			stairPart = nw ? Part.STAIR_WEST : Part.STAIR_EAST;
		} else if (dx == 0 && dz == 0) {
			stepY = FLOOR_Y + 12;
			stairPart = Part.POLISHED_ANDESITE;
		} else {
			// dx == 0 && dz >= 1 && dz <= 3
			stepY = FLOOR_Y + 12 + dz;
			stairPart = nw ? Part.STAIR_SOUTH : Part.STAIR_NORTH;
		}

		if (y == FLOOR_Y) return Part.CARPET;
		if (y == stepY) return stairPart;
		if (y == stepY - 1) return Part.POLISHED_ANDESITE;
		return Part.AIR;
	}

	private static Part shaftPart(int lx, int y, int lz, int ax, int az, int cheb, int manh) {
		if (y >= MAX_Y) return Part.AIR;
		if (y == MIN_Y + 1) return Part.ABYSS_WATER;

		Part elevator = suspendedElevatorPart(lx, y, lz);
		if (elevator != null) return elevator;

		Part catwalk = lowerCatwalkPart(lx, y, lz);
		if (catwalk != null) return catwalk;

		// Central skyfall waterfall tower (y = 33..126)
		if (cheb <= 5 && manh <= 8 && y >= FLOOR_Y + 1 && y <= MAX_Y - 1) {
			if (y == FLOOR_Y + 1 && ax == 0 && az == 0) return Part.ATRIUM_PLAQUE;
			boolean arch = Math.min(ax, az) <= 1 && y <= FLOOR_Y + 5;
			boolean steelCorner = (cheb == 5 && manh == 8) || (cheb == 4 && manh == 8);
			boolean ring = (y - FLOOR_Y) % 12 == 0 && (cheb == 5 || (cheb == 4 && manh == 8));
			if (steelCorner) return Part.ELEVATOR_FRAME;
			if (arch) return Part.AIR;
			if (cheb == 5 && y == FLOOR_Y + 6 && Math.min(ax, az) <= 1) return Part.ELEVATOR_FRAME;
			if (ring) {
				return (cheb == 5 && Math.min(ax, az) == 0) ? Part.TROFFER_LIT : Part.ELEVATOR_FRAME;
			}
			if (cheb == 5) return Part.WATERFALL;
			if (cheb == 4) return Part.GLASS_WALL;
			return Part.AIR;
		}

		// Central floating octagonal basin (y = 29..33)
		if (cheb <= 12 && manh <= 18) {
			if (y == FLOOR_Y) {
				if (cheb <= 5 && manh <= 8) {
					return (cheb == 5 && Math.min(ax, az) >= 2 && manh <= 7) ? Part.ALMOND_POOL : Part.POLISHED_ANDESITE;
				}
				if (cheb >= 6 && cheb <= 9 && manh <= 14) {
					if (Math.min(ax, az) <= 1) return Part.POLISHED_ANDESITE;
					if (cheb == 9 || manh == 14) return Part.ALMOND_BASIN;
					return Part.ALMOND_POOL;
				}
				if (manh == 18 && ax >= 6 && az >= 6) return Part.WATERFALL;
				return Part.POLISHED_ANDESITE;
			}
			if (y == FLOOR_Y + 1 && cheb == 12 && Math.min(ax, az) >= 2 && manh < 18) {
				return Part.GLASS_WALL;
			}
			if (y == FLOOR_Y - 1) {
				boolean underPool = cheb >= 5 && cheb <= 8 && manh <= 13 && Math.min(ax, az) >= 2;
				return underPool ? Part.ALMOND_BASIN : Part.POLISHED_ANDESITE;
			}
			if (y == FLOOR_Y - 2 && cheb <= 10 && manh <= 15) return Part.CONCRETE_GRAY;
			if (y == FLOOR_Y - 3 && cheb <= 8 && manh <= 12) return Part.CONCRETE_GRAY;
		}

		// Four diagonal abyssal waterfall curtains falling from the basin into the void
		if (manh == 19 && ax >= 6 && az >= 6 && cheb <= 13 && y >= MIN_Y + 2 && y <= FLOOR_Y) {
			return Part.WATERFALL;
		}

		// Four roofless glass rain bridges connecting perimeter ring to the octagonal basin
		if ((ax >= 13 && ax <= 34 && az <= 2) || (az >= 13 && az <= 34 && ax <= 2)) {
			int along = ax >= 13 ? ax : az;
			int perp = ax >= 13 ? az : ax;
			boolean rib = along % 6 == 0;
			if (y == FLOOR_Y - 1 && (perp == 2 || rib)) return Part.POLISHED_ANDESITE;
			if (y == FLOOR_Y) return (perp == 2 || rib) ? Part.POLISHED_ANDESITE : Part.GLASS_WALL;
			if (y == FLOOR_Y + 1 && perp == 2) return rib ? Part.PILLAR : Part.GLASS_WALL;
			if (y == FLOOR_Y + 2 && perp == 2 && rib) return Part.CAGED_LAMP;
		}

		return Part.AIR;
	}

	private static Part suspendedElevatorPart(int lx, int y, int lz) {
		if (y < MIN_Y + 2 || y > MAX_Y - 1) return null;
		for (ElevatorSpec spec : ELEVATORS) {
			int dx = lx - spec.ex();
			int dz = lz - spec.ez();
			if (Math.abs(dx) > 2 || Math.abs(dz) > 2) continue;
			int dy = y - spec.ey();
			boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
			boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
			if (dy >= 0 && dy <= 4) {
				if (dy == 0) return edge ? Part.ELEVATOR_FRAME : Part.POLISHED_ANDESITE;
				if (dy == 4) {
					if (dx == 0 && dz == 0) return spec.light();
					return edge ? Part.ELEVATOR_FRAME : Part.ACOUSTIC_CEILING;
				}
				if (corner) return Part.ELEVATOR_FRAME;
				if (edge) return Part.GLASS_WALL;
				return Part.AIR;
			}
			if ((dx == 0 && dz == 0) || corner) {
				return Part.IRON_BARS;
			}
			return null;
		}
		return null;
	}

	private static Part lowerCatwalkPart(int lx, int y, int lz) {
		// Vertical access ladder at (60, 19..32, 67) backed by frame at (60, 18..32, 68)
		if (lx == 60 && lz == 68 && y >= LOWER_Y && y <= FLOOR_Y) {
			return Part.ELEVATOR_FRAME;
		}
		if (lx == 60 && lz == 67 && y >= LOWER_Y + 1 && y <= FLOOR_Y) {
			return Part.LADDER_NORTH;
		}
		if (lz == 67 && (lx == 59 || lx == 61) && y == FLOOR_Y + 1) {
			return Part.IRON_BARS;
		}
		if (lx == 60 && lz == 67 && y == LOWER_Y) {
			return Part.POLISHED_ANDESITE;
		}

		// Connecting catwalk segment at y = 18..19 (lx in 56..60, lz in 66..67)
		if (lx >= 56 && lx <= 60 && lz >= 66 && lz <= 67) {
			if (y == LOWER_Y) {
				return (lx == 56 || lx == 60 || lz == 66) ? Part.POLISHED_ANDESITE : Part.ELEVATOR_FRAME;
			}
			if (y == LOWER_Y + 1 && lz == 67 && (lx == 56 || lx == 58)) {
				return Part.IRON_BARS;
			}
		}

		// Narrow suspended steel spine catwalk (lx in 55..57, lz in 49..65)
		if (lx >= 55 && lx <= 57 && lz >= 49 && lz <= 65) {
			if ((lx == 55 || lx == 57) && (lz == 52 || lz == 62) && y >= LOWER_Y + 1 && y <= FLOOR_Y - 4) {
				return Part.IRON_BARS;
			}
			if (lx == 56 && (lz == 54 || lz == 62) && y == LOWER_Y + 5) {
				return Part.CAGED_LAMP;
			}
			if (y == LOWER_Y) {
				if (lx == 56) return (lz % 2 == 0) ? Part.POLISHED_ANDESITE : Part.ELEVATOR_FRAME;
				if (lz % 2 == 0) return Part.ELEVATOR_FRAME;
			}
			if (y == LOWER_Y + 1 && (lx == 55 || lx == 57) && lz % 4 == 0) {
				return Part.IRON_BARS;
			}
		}

		// North reward platform & permanent City Door (Level 11) exit (lx in 54..58, lz in 44..48)
		if (lx >= 54 && lx <= 58 && lz >= 44 && lz <= 48 && y >= LOWER_Y && y <= LOWER_Y + 3) {
			boolean rim = lx == 54 || lx == 58 || lz == 44 || lz == 48;
			if (y == LOWER_Y) {
				return rim ? Part.ELEVATOR_FRAME : Part.POLISHED_ANDESITE;
			}
			if (lz == 44) {
				if (lx == 55 || lx == 57) return Part.ELEVATOR_FRAME;
				if (lx == 56) {
					if (y == LOWER_Y + 1) return Part.EXIT_DOOR_11_LOWER;
					if (y == LOWER_Y + 2) return Part.EXIT_DOOR_11_UPPER;
					if (y == LOWER_Y + 3) return Part.ELEVATOR_FRAME;
				}
				if (lx == 54 && y == LOWER_Y + 1) return Part.IRON_BARS_ES;
				if (lx == 58 && y == LOWER_Y + 1) return Part.IRON_BARS_WS;
			}
			if (y == LOWER_Y + 1) {
				if (lx == 54 && lz == 46) return Part.SUPPLY_CRATE_EAST;
				if (lx == 58 && lz == 46) return Part.STEEL_SAFE_WEST;
				if ((lx == 54 || lx == 58) && (lz == 45 || lz == 47)) return Part.IRON_BARS_NS;
				if (lz == 48 && (lx == 55 || lx == 57)) return Part.IRON_BARS;
			}
			if (y == LOWER_Y + 3 && lx == 56 && lz == 45) {
				return Part.EXIT_SIGN_SOUTH;
			}
		}
		return null;
	}

	private static BlockState toBlockState(Part part, Tile tile, Direction.Axis trofferAxis) {
		return switch (part) {
			case AIR -> Blocks.AIR.defaultBlockState();
			case BEDROCK -> Blocks.BEDROCK.defaultBlockState();
			case ABYSS_WATER, WATERFALL -> Blocks.WATER.defaultBlockState();
			case CARPET -> Level4Blocks.OFFICE_CARPET.defaultBlockState();
			case DAMP_CARPET -> Level4Blocks.OFFICE_DAMP_CARPET.defaultBlockState();
			case ACOUSTIC_CEILING -> Level4Blocks.OFFICE_ACOUSTIC_CEILING.defaultBlockState();
			case TROFFER_LIT -> Level4Blocks.OFFICE_TROFFER_LIT.defaultBlockState().setValue(Level4AxisBlock.AXIS, trofferAxis);
			case TROFFER_DIM -> Level4Blocks.OFFICE_TROFFER_DIM.defaultBlockState().setValue(Level4AxisBlock.AXIS, trofferAxis);
			case TROFFER_FLICKER -> Level4Blocks.OFFICE_TROFFER_FLICKER.defaultBlockState().setValue(Level4AxisBlock.AXIS, trofferAxis);
			case TROFFER_BROKEN -> Level4Blocks.OFFICE_TROFFER_BROKEN.defaultBlockState().setValue(Level4AxisBlock.AXIS, trofferAxis);
			case PILLAR -> Level4Blocks.OFFICE_PILLAR.defaultBlockState();
			case PILLAR_UPPER -> Level4Blocks.OFFICE_PILLAR_UPPER.defaultBlockState();
			case DRYWALL_UPPER -> Level4Blocks.OFFICE_DRYWALL_UPPER.defaultBlockState();
			case RADIATOR_WALL -> Level4Blocks.OFFICE_RADIATOR_WALL.defaultBlockState().setValue(Level4FacingBlock.FACING, dirFrom(tile.dir()));
			case CONCRETE_GRAY -> Blocks.CONCRETE.gray().defaultBlockState();
			case CONCRETE_LIGHT_GRAY -> Blocks.CONCRETE.lightGray().defaultBlockState();
			case POLISHED_ANDESITE -> Blocks.POLISHED_ANDESITE.defaultBlockState();
			case RAIN_WINDOW -> Level4Blocks.OFFICE_RAIN_WINDOW.defaultBlockState();
			case GLASS_WALL -> Level4Blocks.OFFICE_GLASS_WALL.defaultBlockState();
			case ELEVATOR_FRAME -> Level4Blocks.OFFICE_ELEVATOR_FRAME.defaultBlockState();
			case ALMOND_BASIN -> Level4Blocks.ALMOND_FOUNTAIN_BASIN.defaultBlockState();
			case ALMOND_POOL -> Level4Blocks.ALMOND_FOUNTAIN_POOL.defaultBlockState().setValue(Level4WaterSourceBlock.BOTTLES, 2);
			case CAGED_LAMP -> Level1Blocks.CAGED_LAMP.defaultBlockState();
			case STAIR_NORTH -> Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
			case STAIR_SOUTH -> Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
			case STAIR_EAST -> Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
			case STAIR_WEST -> Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
			case IRON_BARS -> Blocks.IRON_BARS.defaultBlockState();
			case IRON_BARS_ES -> Blocks.IRON_BARS.defaultBlockState()
					.setValue(BlockStateProperties.EAST, true)
					.setValue(BlockStateProperties.SOUTH, true);
			case IRON_BARS_WS -> Blocks.IRON_BARS.defaultBlockState()
					.setValue(BlockStateProperties.WEST, true)
					.setValue(BlockStateProperties.SOUTH, true);
			case IRON_BARS_NS -> Blocks.IRON_BARS.defaultBlockState()
					.setValue(BlockStateProperties.NORTH, true)
					.setValue(BlockStateProperties.SOUTH, true);
			case LADDER_NORTH -> Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
			case STEEL_SAFE_SOUTH -> Level4Blocks.OFFICE_STEEL_SAFE.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.SOUTH);
			case STEEL_SAFE_WEST -> Level4Blocks.OFFICE_STEEL_SAFE.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.WEST);
			case SUPPLY_CRATE_SOUTH -> Level4Blocks.MEG_SUPPLY_CRATE.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.SOUTH);
			case SUPPLY_CRATE_EAST -> Level4Blocks.MEG_SUPPLY_CRATE.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.EAST);
			case SLEEPING_BAG_WEST -> Level4Blocks.CAMP_SLEEPING_BAG.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.WEST);
			case ALMOND_BOTTLES -> Level4Blocks.ALMOND_BOTTLE_CLUSTER.defaultBlockState();
			case EXIT_DOOR_11_LOWER -> Level4Blocks.OFFICE_DOOR_11.defaultBlockState()
					.setValue(Level4ExitBlock.PART, 0)
					.setValue(Level4ExitBlock.FACING, Direction.SOUTH);
			case EXIT_DOOR_11_UPPER -> Level4Blocks.OFFICE_DOOR_11.defaultBlockState()
					.setValue(Level4ExitBlock.PART, 1)
					.setValue(Level4ExitBlock.FACING, Direction.SOUTH);
			case EXIT_SIGN_SOUTH -> Level4Blocks.OFFICE_EXIT_SIGN.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.SOUTH);
			case ATRIUM_PLAQUE -> Level4Blocks.ATRIUM_ZERO_PLAQUE.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.SOUTH);
		};
	}

	private static Direction dirFrom(int dir) {
		return switch (dir) {
			case Level4Plan.NORTH -> Direction.NORTH;
			case Level4Plan.EAST -> Direction.EAST;
			case Level4Plan.SOUTH -> Direction.SOUTH;
			default -> Direction.WEST;
		};
	}
}
