package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.level1.Level1Blocks;
import dev.noclip.backrooms.level1.PipeBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Multi-level staircase complexes replacing dead-end bedrock-walled stairs in Level 0.
 *
 * Each complex fits inside a 3x3-cell footprint (21x21 blocks) strictly buffered within an
 * eligible CLASSIC or CORRIDORS sector. Ground-floor stair shafts sit inside the 4x4 interior core
 * (ix, iz in 2..5) so every cell's 1-block perimeter walkway stays 100% open to surrounding maze
 * doorways, while upper rooms (floor y = 37, ceiling y = 42) and basements (floor y = 24, ceiling
 * y = 29) stay completely sealed from bedrock.
 */
final class StaircasePlan {
	enum Type {
		ATTIC_SHORTCUT,
		UTILITY_LOFT,
		OBSERVATION_OVERLOOK,
		FLOODED_BASEMENT,
		PARADOX_LOOP
	}

	static final int SPAN_CELLS = 3;
	static final int SPAN_BLOCKS = SPAN_CELLS * Maze.CELL; // 21 blocks
	static final int UPPER_FLOOR_Y = Level0ChunkGenerator.CEILING_Y + 1; // 37
	static final int UPPER_CEIL_Y = UPPER_FLOOR_Y + 5; // 42
	static final int BASEMENT_FLOOR_Y = 24;
	static final int BASEMENT_CEIL_Y = 29;
	private static final int SALT = 93;

	record Complex(int cx0, int cz0, int rot, Type type) {
		int baseX() {
			return cx0 * Maze.CELL;
		}

		int baseZ() {
			return cz0 * Maze.CELL;
		}

		boolean containsCell(int cx, int cz) {
			return cx >= cx0 && cx < cx0 + SPAN_CELLS && cz >= cz0 && cz < cz0 + SPAN_CELLS;
		}

		boolean isProtectedCell(int cx, int cz) {
			return cx >= cx0 - 1 && cx <= cx0 + SPAN_CELLS && cz >= cz0 - 1 && cz <= cz0 + SPAN_CELLS;
		}

		boolean containsBlock(int x, int z) {
			int dx = x - baseX(), dz = z - baseZ();
			return dx >= 0 && dx < SPAN_BLOCKS && dz >= 0 && dz < SPAN_BLOCKS;
		}

		int localU(int dx, int dz) {
			return switch (rot) {
				case 0 -> dx;
				case 1 -> dz;
				case 2 -> SPAN_BLOCKS - dx;
				default -> SPAN_BLOCKS - dz;
			};
		}

		int localV(int dx, int dz) {
			return switch (rot) {
				case 0 -> dz;
				case 1 -> SPAN_BLOCKS - dx;
				case 2 -> SPAN_BLOCKS - dz;
				default -> dx;
			};
		}

		int worldX(int lu, int lv) {
			int dx = switch (rot) {
				case 0 -> lu;
				case 1 -> SPAN_BLOCKS - lv;
				case 2 -> SPAN_BLOCKS - lu;
				default -> lv;
			};
			return baseX() + dx;
		}

		int worldZ(int lu, int lv) {
			int dz = switch (rot) {
				case 0 -> lv;
				case 1 -> lu;
				case 2 -> SPAN_BLOCKS - lv;
				default -> SPAN_BLOCKS - lu;
			};
			return baseZ() + dz;
		}

		/** Maps a local cell (cu, cv) in 0..2 to world cell coordinates. */
		int worldCellX(int cu, int cv) {
			int lu = cu * Maze.CELL + Maze.CENTER;
			int lv = cv * Maze.CELL + Maze.CENTER;
			return Math.floorDiv(worldX(lu, lv), Maze.CELL);
		}

		int worldCellZ(int cu, int cv) {
			int lu = cu * Maze.CELL + Maze.CENTER;
			int lv = cv * Maze.CELL + Maze.CENTER;
			return Math.floorDiv(worldZ(lu, lv), Maze.CELL);
		}

		Direction worldDir(Direction local) {
			Direction dir = local;
			for (int i = 0; i < rot; i++) {
				dir = dir.getClockWise();
			}
			return dir;
		}

		boolean isEntryCell(int cx, int cz) {
			return cx == worldCellX(0, 1) && cz == worldCellZ(0, 1);
		}

		boolean isExitStairCell(int cx, int cz) {
			return (type == Type.ATTIC_SHORTCUT || type == Type.PARADOX_LOOP)
					&& cx == worldCellX(2, 1) && cz == worldCellZ(2, 1);
		}
	}

	private StaircasePlan() {
	}

	static Complex complexForCell(long seed, int cx, int cz) {
		int sx = Math.floorDiv(cx, Maze.SECTOR);
		int sz = Math.floorDiv(cz, Maze.SECTOR);
		Complex complex = complexInSector(seed, sx, sz);
		if (complex != null && complex.containsCell(cx, cz)) return complex;
		return null;
	}

	static boolean isComplexCell(long seed, int cx, int cz) {
		return complexForCell(seed, cx, cz) != null;
	}

	static boolean isProtectedCell(long seed, int cx, int cz) {
		int sx = Math.floorDiv(cx, Maze.SECTOR);
		int sz = Math.floorDiv(cz, Maze.SECTOR);
		Complex complex = complexInSector(seed, sx, sz);
		return complex != null && complex.isProtectedCell(cx, cz);
	}

	static Complex complexAtBlock(long seed, int x, int z) {
		int cx = Math.floorDiv(x, Maze.CELL), cz = Math.floorDiv(z, Maze.CELL);
		Complex complex = complexForCell(seed, cx, cz);
		return complex != null && complex.containsBlock(x, z) ? complex : null;
	}

	static Complex complexInSector(long seed, int sx, int sz) {
		return CACHE.get().getOrCompute(seed, sx, sz);
	}

	private static Complex computeInSector(long seed, int sx, int sz) {
		if (sx == 0 && sz == 0) return null;
		Maze.Zone zone = Maze.baseZoneForSector(seed, sx, sz);
		if (zone != Maze.Zone.CLASSIC && zone != Maze.Zone.CORRIDORS) return null;
		if (ManilaRoomPlan.roomForCell(seed, sx * Maze.SECTOR + 3, sz * Maze.SECTOR + 3) != null) return null;

		long h = hash(seed, sx, sz, SALT);
		if (Math.floorMod(h, 5) >= 2) return null; // 40% of eligible CLASSIC/CORRIDORS sectors

		int lx0 = 2 + Math.floorMod(h >>> 4, 2);
		int lz0 = 2 + Math.floorMod(h >>> 8, 2);
		int rot = Math.floorMod(h >>> 12, 4);
		int roll = Math.floorMod(h >>> 16, 100);
		Type type;
		if (roll < 25) {
			type = Type.ATTIC_SHORTCUT;
		} else if (roll < 50) {
			type = Type.UTILITY_LOFT;
		} else if (roll < 70) {
			type = Type.OBSERVATION_OVERLOOK;
		} else if (roll < 90) {
			type = Type.FLOODED_BASEMENT;
		} else {
			type = Type.PARADOX_LOOP;
		}
		return new Complex(sx * Maze.SECTOR + lx0, sz * Maze.SECTOR + lz0, rot, type);
	}

	/**
	 * Whether (x, z) is blocked by a ground-floor stairwell enclosure wall (kept inside ix, iz in 2..5
	 * so every cell's outer 1-block perimeter remains open for Maze doorways).
	 */
	static boolean isGroundWall(Complex c, int x, int z) {
		int lu = c.localU(x - c.baseX(), z - c.baseZ());
		int lv = c.localV(x - c.baseX(), z - c.baseZ());
		if (c.type() == Type.FLOODED_BASEMENT) {
			return isDownShaftWall(lu, lv);
		}
		if (isUpShaftWall(lu, lv)) return true;
		if ((c.type() == Type.ATTIC_SHORTCUT || c.type() == Type.PARADOX_LOOP)
				&& isUpShaftWall(SPAN_BLOCKS - lu, lv)) {
			return true;
		}
		return false;
	}

	/**
	 * Primary ground-floor walkable Y for a block inside a Complex (used by MazeTest's 2D BFS;
	 * upper rooms and underground tunnels are additionally verified in 3D against blockAt).
	 */
	static int groundWalkableY(Complex c, int x, int z) {
		int lu = c.localU(x - c.baseX(), z - c.baseZ());
		int lv = c.localV(x - c.baseX(), z - c.baseZ());
		if (c.type() == Type.FLOODED_BASEMENT) {
			int down = downShaftOpenStepY(lu, lv);
			if (down != Integer.MIN_VALUE) return down;
			return Level0ChunkGenerator.FLOOR_Y;
		}
		int up = upShaftStepY(lu, lv);
		if (up != Integer.MIN_VALUE) return up;
		if (c.type() == Type.ATTIC_SHORTCUT || c.type() == Type.PARADOX_LOOP) {
			int upExit = upShaftStepY(SPAN_BLOCKS - lu, lv);
			if (upExit != Integer.MIN_VALUE) return upExit;
		}
		return Level0ChunkGenerator.FLOOR_Y;
	}

	/**
	 * Returns a generated BlockState override inside the staircase complex, or null where normal
	 * ground-level Level 0 maze generation should apply (such as the ground floor above a basement
	 * or underneath a loft/attic).
	 */
	static BlockState blockAt(Complex c, int x, int y, int z) {
		int lu = c.localU(x - c.baseX(), z - c.baseZ());
		int lv = c.localV(x - c.baseX(), z - c.baseZ());
		if (lu < 1 || lu >= SPAN_BLOCKS || lv < 1 || lv >= SPAN_BLOCKS) return null;
		if (c.type() == Type.FLOODED_BASEMENT) {
			return basementBlockAt(c, lu, y, lv);
		}
		return upperComplexBlockAt(c, lu, y, lv);
	}

	private static BlockState upperComplexBlockAt(Complex c, int lu, int y, int lv) {
		if (y > UPPER_CEIL_Y) return Blocks.BEDROCK.defaultBlockState();

		// Stair shaft(s) inside entry cell (0,1) and optional mirrored exit cell (2,1).
		BlockState shaft = upShaftBlockAt(c, lu, y, lv, false);
		if (shaft != null) return shaft;
		if (c.type() == Type.ATTIC_SHORTCUT || c.type() == Type.PARADOX_LOOP) {
			BlockState exitShaft = upShaftBlockAt(c, SPAN_BLOCKS - lu, y, lv, true);
			if (exitShaft != null) return exitShaft;
		}

		// Upper room floor/walls/ceiling (y = 37..42), plus ceiling peek-windows at y = 36 where noted.
		return switch (c.type()) {
			case ATTIC_SHORTCUT -> atticBlockAt(c, lu, y, lv);
			case UTILITY_LOFT -> utilityLoftBlockAt(c, lu, y, lv);
			case OBSERVATION_OVERLOOK -> overlookBlockAt(c, lu, y, lv);
			case PARADOX_LOOP -> paradoxLoopBlockAt(c, lu, y, lv);
			case FLOODED_BASEMENT -> null;
		};
	}

	/**
	 * Builds a straight 2-wide upward stairwell in local cell (0, 1) (lu in 2..5, lv in 10..11),
	 * enclosed on lv=9 and lv=12, with doorway header above lu=1 and threshold bridge at lu=6.
	 */
	private static BlockState upShaftBlockAt(Complex c, int lu, int y, int lv, boolean mirrored) {
		// Doorway header above ground entrance (lu=1, lv in 10..11) so looking back up-stairs never sees bedrock.
		if (lu == 1 && (lv == 10 || lv == 11) && y >= UPPER_FLOOR_Y) {
			return y == UPPER_CEIL_Y ? ModBlocks.CEILING_TILE.defaultBlockState() : ModBlocks.WALLPAPER.defaultBlockState();
		}

		// Upper bridge threshold at lu=6 crossing cleanly over ground walkway (y=32..35) + ground ceiling (y=36).
		if (lu == 6 && lv >= 9 && lv <= 12 && y >= UPPER_FLOOR_Y) {
			if (y == UPPER_CEIL_Y) return ModBlocks.CEILING_TILE.defaultBlockState();
			if (lv == 9 || lv == 12) return ModBlocks.WALLPAPER.defaultBlockState();
			if (y == UPPER_FLOOR_Y) return Blocks.SMOOTH_SANDSTONE.defaultBlockState();
			return Blocks.AIR.defaultBlockState();
		}

		// Interior stair shaft enclosure & steps in lu in 2..5, lv in 9..12.
		if (lu < 2 || lu > 5 || lv < 9 || lv > 12) return null;

		if (isUpShaftWall(lu, lv)) {
			if (y < Level0ChunkGenerator.FLOOR_Y) return Blocks.CONCRETE.black().defaultBlockState();
			if (y == Level0ChunkGenerator.FLOOR_Y) return ModBlocks.CARPET.defaultBlockState();
			if (y == Level0ChunkGenerator.FLOOR_Y + 1) return ModBlocks.WALLPAPER_BASEBOARD.defaultBlockState();
			if (y < UPPER_CEIL_Y) return ModBlocks.WALLPAPER.defaultBlockState();
			return ModBlocks.CEILING_TILE.defaultBlockState();
		}

		int stepY = upShaftStepY(lu, lv);
		if (stepY == Integer.MIN_VALUE) return null;

		if (y < Level0ChunkGenerator.FLOOR_Y) return Blocks.CONCRETE.black().defaultBlockState();
		if (y < stepY) {
			// Ground-side east face under top step (lu=5) shows wallpaper/baseboard from the walkway behind it.
			if (lu == 5 && y >= Level0ChunkGenerator.FLOOR_Y + 1) {
				return (y == Level0ChunkGenerator.FLOOR_Y + 1
						? ModBlocks.WALLPAPER_BASEBOARD : ModBlocks.WALLPAPER).defaultBlockState();
			}
			return Blocks.SMOOTH_SANDSTONE.defaultBlockState();
		}
		if (y == stepY) {
			Direction localDir = mirrored ? Direction.WEST : Direction.EAST;
			return Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState()
					.setValue(StairBlock.FACING, c.worldDir(localDir));
		}
		if (y < UPPER_CEIL_Y) return Blocks.AIR.defaultBlockState();
		if (lu == 3 && lv == 10) return ModBlocks.BROKEN_FLUORESCENT_LIGHT.defaultBlockState();
		return ModBlocks.CEILING_TILE.defaultBlockState();
	}

	private static boolean isUpShaftWall(int lu, int lv) {
		return lu >= 2 && lu <= 5 && (lv == 9 || lv == 12);
	}

	private static int upShaftStepY(int lu, int lv) {
		if ((lv == 10 || lv == 11) && lu >= 2 && lu <= 5) {
			return Level0ChunkGenerator.FLOOR_Y + (lu - 1); // 33, 34, 35, 36 -> steps straight onto 37 at lu=6
		}
		return Integer.MIN_VALUE;
	}

	private static BlockState atticBlockAt(Complex c, int lu, int y, int lv) {
		// Attic hallway covers lu in 7..14, lv in 8..12 (with recessed supply alcoves at (10,8) & (10,12)).
		if (lu < 7 || lu > 14 || lv < 7 || lv > 13) return null;
		boolean inOuterWallBox = lv >= 8 && lv <= 12;
		boolean alcoveBehind = lu == 10 && (lv == 7 || lv == 13);
		if (!inOuterWallBox && !alcoveBehind) return null;

		boolean inMainCorridor = lv >= 9 && lv <= 11;
		boolean inAlcove = lu == 10 && (lv == 8 || lv == 12);
		boolean peekWindow = lv == 10 && (lu == 9 || lu == 11);

		if (y == Level0ChunkGenerator.CEILING_Y && peekWindow) {
			return Blocks.AIR.defaultBlockState();
		}
		if (y < UPPER_FLOOR_Y) return null;

		boolean isWall = !inMainCorridor && !inAlcove;
		if (isWall) {
			if (y == UPPER_CEIL_Y) return ModBlocks.CEILING_TILE.defaultBlockState();
			if (y == UPPER_FLOOR_Y + 1) return ModBlocks.ROUGH_WALLPAPER_BASEBOARD.defaultBlockState();
			return ModBlocks.ROUGH_WALLPAPER.defaultBlockState();
		}
		if (y == UPPER_FLOOR_Y) {
			return peekWindow ? Blocks.STAINED_GLASS.gray().defaultBlockState() : Level1Blocks.GARAGE_FLOOR.defaultBlockState();
		}
		if (y == UPPER_CEIL_Y) {
			if (lu == 8 && lv == 10) return Level1Blocks.BARE_BULB.defaultBlockState();
			if (lu == 12 && lv == 10) return Level1Blocks.BROKEN_BARE_BULB.defaultBlockState();
			return ModBlocks.CEILING_TILE.defaultBlockState();
		}
		if (y == UPPER_CEIL_Y - 1 && lv == 9) {
			return pipe(c, lu == 10, true);
		}
		if (y == UPPER_FLOOR_Y + 1 && inAlcove) {
			if (lv == 8) return Level1Blocks.SUPPLY_CRATE.defaultBlockState();
			return ModBlocks.MANILA_CABINET.defaultBlockState()
					.setValue(ManilaCabinetBlock.FACING, c.worldDir(Direction.NORTH))
					.setValue(ManilaCabinetBlock.OPENED, false);
		}
		return Blocks.AIR.defaultBlockState();
	}

	/** A ceiling pipe running along local U (alongU) or local V, turned to match the complex's rotation. */
	private static BlockState pipe(Complex c, boolean leaking, boolean alongU) {
		boolean alongX = alongU == (c.rot() % 2 == 0);
		return (leaking ? Level1Blocks.LEAKING_CEILING_PIPE : Level1Blocks.CEILING_PIPE).defaultBlockState()
				.setValue(PipeBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z);
	}

	private static BlockState utilityLoftBlockAt(Complex c, int lu, int y, int lv) {
		if (lu < 7 || lu > 17 || lv < 4 || lv > 16) return null;
		if (y < UPPER_FLOOR_Y) return null;

		boolean wall = lu == 7 || lu == 17 || lv == 4 || lv == 16;
		boolean doorway = lu == 7 && (lv == 10 || lv == 11);
		if (wall && !doorway) {
			if (y == UPPER_CEIL_Y) return ModBlocks.CEILING_TILE.defaultBlockState();
			if (y == UPPER_FLOOR_Y + 1) return ModBlocks.ROUGH_WALLPAPER_BASEBOARD.defaultBlockState();
			return ModBlocks.ROUGH_WALLPAPER.defaultBlockState();
		}
		if (y == UPPER_FLOOR_Y) {
			return (lv >= 9 && lv <= 11 && lu <= 15)
					? ModBlocks.DRY_CARPET.defaultBlockState()
					: Level1Blocks.GARAGE_FLOOR.defaultBlockState();
		}
		if (y == UPPER_CEIL_Y) {
			if ((lu == 10 || lu == 14) && (lv == 7 || lv == 13)) {
				return (lu == 14 && lv == 7)
						? ModBlocks.BROKEN_FLUORESCENT_LIGHT.defaultBlockState()
						: Level1Blocks.GARAGE_LIGHT.defaultBlockState();
			}
			return ModBlocks.CEILING_TILE.defaultBlockState();
		}
		if (doorway) return Blocks.AIR.defaultBlockState();

		if (y == UPPER_CEIL_Y - 1 && (lu == 9 || lu == 15) && lv >= 5 && lv <= 15) {
			return pipe(c, lu == 9 && lv == 13, false);
		}

		// Electrical breaker assembly against east wall (lu=16, lv=10).
		if (lu == 16 && lv == 10) {
			if (y == UPPER_FLOOR_Y + 1) return Blocks.IRON_BLOCK.defaultBlockState();
			if (y == UPPER_FLOOR_Y + 2) return Blocks.REDSTONE_LAMP.defaultBlockState();
		}
		if (lu == 15 && lv == 10 && y == UPPER_FLOOR_Y + 2) {
			return Blocks.LEVER.defaultBlockState()
					.setValue(LeverBlock.FACE, AttachFace.WALL)
					.setValue(LeverBlock.FACING, c.worldDir(Direction.WEST))
					.setValue(LeverBlock.POWERED, false);
		}
		if (lu == 16 && (lv == 8 || lv == 12) && y <= UPPER_FLOOR_Y + 2) {
			return Level1Blocks.METAL_SHELF.defaultBlockState();
		}

		// Electrician desk, computer, chair, and log note.
		if (y == UPPER_FLOOR_Y + 1) {
			if ((lu == 11 || lu == 12) && lv == 6) return Level1Blocks.OFFICE_DESK.defaultBlockState();
			if (lu == 11 && lv == 7) {
				return ModBlocks.MANILA_CHAIR.defaultBlockState()
						.setValue(ManilaFurnitureBlock.FACING, c.worldDir(Direction.NORTH));
			}
			if (lu == 11 && lv == 14) {
				return ModBlocks.MANILA_CABINET.defaultBlockState()
						.setValue(ManilaCabinetBlock.FACING, c.worldDir(Direction.NORTH))
						.setValue(ManilaCabinetBlock.OPENED, false);
			}
			if (lu == 12 && lv == 14) return Level1Blocks.SUPPLY_CRATE.defaultBlockState();
		}
		if (y == UPPER_FLOOR_Y + 2) {
			if (lu == 11 && lv == 6) {
				return Level1Blocks.OLD_COMPUTER.defaultBlockState()
						.setValue(BlockStateProperties.HORIZONTAL_FACING, c.worldDir(Direction.SOUTH));
			}
			if (lu == 12 && lv == 6) {
				return ModBlocks.FIELD_NOTE.defaultBlockState()
						.setValue(FieldNoteBlock.FACING, c.worldDir(Direction.SOUTH))
						.setValue(FieldNoteBlock.VARIANT, 0);
			}
		}
		return Blocks.AIR.defaultBlockState();
	}

	private static BlockState overlookBlockAt(Complex c, int lu, int y, int lv) {
		if (lu < 7 || lu > 17 || lv < 4 || lv > 16) return null;

		boolean glassWindow = lu >= 10 && lu <= 14 && lv >= 9 && lv <= 12;
		if (y == Level0ChunkGenerator.CEILING_Y && glassWindow) {
			return Blocks.AIR.defaultBlockState();
		}
		if (y < UPPER_FLOOR_Y) return null;

		boolean wall = lu == 7 || lu == 17 || lv == 4 || lv == 16;
		boolean doorway = lu == 7 && (lv == 10 || lv == 11);
		if (wall && !doorway) {
			if (y == UPPER_CEIL_Y) return ModBlocks.CEILING_TILE.defaultBlockState();
			if (y == UPPER_FLOOR_Y + 1) return ModBlocks.PALE_WALLPAPER_BASEBOARD.defaultBlockState();
			return ModBlocks.PALE_WALLPAPER.defaultBlockState();
		}
		if (y == UPPER_FLOOR_Y) {
			return glassWindow ? Blocks.STAINED_GLASS.gray().defaultBlockState() : ModBlocks.THICK_CARPET.defaultBlockState();
		}
		if (y == UPPER_CEIL_Y) {
			if (lu == 9 && lv == 11) return ModBlocks.RED_FLUORESCENT_LIGHT.defaultBlockState();
			if (lu == 15 && lv == 6) return ModBlocks.MANILA_LIGHT.defaultBlockState();
			if (lu == 15 && lv == 14) return ModBlocks.BROKEN_FLUORESCENT_LIGHT.defaultBlockState();
			return ModBlocks.CEILING_TILE.defaultBlockState();
		}
		if (doorway) return Blocks.AIR.defaultBlockState();

		// Desk overlooking the glass window + supplies.
		if (y == UPPER_FLOOR_Y + 1) {
			if (lu == 15 && lv == 6) {
				return ModBlocks.MANILA_TABLE.defaultBlockState()
						.setValue(ManilaFurnitureBlock.FACING, c.worldDir(Direction.WEST));
			}
			if (lu == 14 && lv == 6) {
				return ModBlocks.MANILA_CHAIR.defaultBlockState()
						.setValue(ManilaFurnitureBlock.FACING, c.worldDir(Direction.EAST));
			}
			if (lu == 15 && lv == 13) {
				return ModBlocks.MANILA_CABINET.defaultBlockState()
						.setValue(ManilaCabinetBlock.FACING, c.worldDir(Direction.WEST))
						.setValue(ManilaCabinetBlock.OPENED, false);
			}
			if (lu == 15 && lv == 14) return Level1Blocks.SUPPLY_CRATE.defaultBlockState();
		}
		if (y == UPPER_FLOOR_Y + 2 && lu == 15 && lv == 6) {
			return ModBlocks.FIELD_NOTE.defaultBlockState()
					.setValue(FieldNoteBlock.FACING, c.worldDir(Direction.WEST))
					.setValue(FieldNoteBlock.VARIANT, 1);
		}
		return Blocks.AIR.defaultBlockState();
	}

	private static BlockState paradoxLoopBlockAt(Complex c, int lu, int y, int lv) {
		// Upper corridor lu in 7..14, lv in 9..13, plus sealed door alcove lu in 9..11, lv in 5..8.
		boolean inHall = lu >= 7 && lu <= 14 && lv >= 9 && lv <= 13;
		boolean inAlcove = lu >= 9 && lu <= 11 && lv >= 5 && lv <= 8;
		if (!inHall && !inAlcove) return null;
		if (y < UPPER_FLOOR_Y) return null;

		boolean isOpenHall = lu >= 7 && lu <= 14 && lv >= 10 && lv <= 12;
		boolean isAlcoveCorridor = lu == 10 && lv >= 7 && lv <= 9;
		boolean isWall = !isOpenHall && !isAlcoveCorridor;

		if (isWall) {
			if (y == UPPER_CEIL_Y) return ModBlocks.CEILING_TILE.defaultBlockState();
			if (lu >= 9 && lu <= 11 && lv <= 9) return ModBlocks.PEELING_RED_WALLPAPER.defaultBlockState();
			if (y == UPPER_FLOOR_Y + 1) return ModBlocks.WALLPAPER_BASEBOARD.defaultBlockState();
			return ModBlocks.WALLPAPER.defaultBlockState();
		}
		if (y == UPPER_FLOOR_Y) {
			return isAlcoveCorridor ? ModBlocks.MANILA_WOOD_FLOOR.defaultBlockState() : ModBlocks.THICK_CARPET.defaultBlockState();
		}
		if (y == UPPER_CEIL_Y) {
			if (lu == 10 && lv == 11) return ModBlocks.RED_FLUORESCENT_LIGHT.defaultBlockState();
			if (lu == 10 && lv == 8) return ModBlocks.BROKEN_FLUORESCENT_LIGHT.defaultBlockState();
			return ModBlocks.CEILING_TILE.defaultBlockState();
		}

		// Sealed door backed immediately by Void Stone inside the north alcove.
		if (lu == 10 && lv == 7) {
			if (y == UPPER_FLOOR_Y + 1) {
				return ModBlocks.MANILA_DOOR.defaultBlockState()
						.setValue(ManilaDoorBlock.FACING, c.worldDir(Direction.NORTH))
						.setValue(ManilaDoorBlock.OPEN, false);
			}
			if (y == UPPER_FLOOR_Y + 2) {
				return ModBlocks.MANILA_DOOR_TOP.defaultBlockState()
						.setValue(ManilaDoorBlock.FACING, c.worldDir(Direction.NORTH))
						.setValue(ManilaDoorBlock.OPEN, false);
			}
			if (y == UPPER_FLOOR_Y + 3) return ModBlocks.PEELING_RED_WALLPAPER.defaultBlockState();
			return ModBlocks.VOID_STONE.defaultBlockState();
		}
		// Keep Void Stone immediately behind the closed door too (covered by alcove wall lu=10, lv=6!).
		if (lu == 9 && lv == 12 && y == UPPER_FLOOR_Y + 1) {
			return ModBlocks.MANILA_TABLE.defaultBlockState()
					.setValue(ManilaFurnitureBlock.FACING, c.worldDir(Direction.NORTH));
		}
		if (lu == 9 && lv == 12 && y == UPPER_FLOOR_Y + 2) {
			return ModBlocks.FIELD_NOTE.defaultBlockState()
					.setValue(FieldNoteBlock.FACING, c.worldDir(Direction.NORTH))
					.setValue(FieldNoteBlock.VARIANT, 2);
		}
		if (lu == 11 && lv == 12 && y == UPPER_FLOOR_Y + 1) {
			return Level1Blocks.SUPPLY_CRATE.defaultBlockState();
		}
		return Blocks.AIR.defaultBlockState();
	}

	private static BlockState basementBlockAt(Complex c, int lu, int y, int lv) {
		if (y == Level0ChunkGenerator.MIN_Y) return Blocks.BEDROCK.defaultBlockState();

		// 2-wide straight stairwell descending lu=2..8, lv=10..11 from y=32 down to y=24.
		BlockState shaft = downShaftBlockAt(c, lu, y, lv);
		if (shaft != null) return shaft;

		// Basement chamber below ground maze: lu in 9..19, lv in 4..16, y in 23..31 (y >= 32 stays normal ground!).
		if (lu < 9 || lu > 19 || lv < 4 || lv > 16 || y > Level0ChunkGenerator.FLOOR_Y - 1) return null;

		boolean wall = lu == 9 || lu == 19 || lv == 4 || lv == 16;
		boolean doorway = lu == 9 && (lv == 10 || lv == 11);
		boolean floodedSump = lu >= 12 && lu <= 14 && lv >= 6 && lv <= 14;
		int dryFloor = BASEMENT_FLOOR_Y;

		if (y < BASEMENT_FLOOR_Y - 1) return Blocks.CONCRETE.black().defaultBlockState();
		if (wall && !doorway) {
			if (y >= BASEMENT_CEIL_Y + 1) return Blocks.CONCRETE.black().defaultBlockState();
			if (y == BASEMENT_CEIL_Y) return Level1Blocks.GARAGE_CEILING.defaultBlockState();
			return (y <= BASEMENT_FLOOR_Y + 2 ? Level1Blocks.GARAGE_WALL : ModBlocks.ROUGH_WALLPAPER).defaultBlockState();
		}
		if (y >= BASEMENT_CEIL_Y + 1) {
			return (y == BASEMENT_CEIL_Y + 1 ? Level1Blocks.GARAGE_CEILING : Blocks.CONCRETE.black()).defaultBlockState();
		}
		if (y == BASEMENT_CEIL_Y) {
			if (doorway) return Level1Blocks.GARAGE_CEILING.defaultBlockState();
			if (lu == 11 && lv == 10) return Level1Blocks.BARE_BULB.defaultBlockState();
			if (lu == 17 && lv == 10) return Level1Blocks.BARE_BULB.defaultBlockState();
			if (lu == 14 && lv == 7) return Level1Blocks.BROKEN_BARE_BULB.defaultBlockState();
			if (lu == 14 && lv == 13) return Level1Blocks.BROKEN_BARE_BULB.defaultBlockState();
			return Level1Blocks.GARAGE_CEILING.defaultBlockState();
		}

		if (y == dryFloor - 1) {
			return floodedSump ? Level1Blocks.PUDDLED_GARAGE_FLOOR.defaultBlockState() : Blocks.CONCRETE.black().defaultBlockState();
		}
		if (y == dryFloor) {
			if (floodedSump) return Blocks.WATER.defaultBlockState();
			return (lu >= 15 ? Level1Blocks.GARAGE_FLOOR : Level1Blocks.DAMP_GARAGE_FLOOR).defaultBlockState();
		}
		if (doorway) return Blocks.AIR.defaultBlockState();

		if (y == BASEMENT_CEIL_Y - 1 && (lu == 13 || lu == 16) && lv >= 5 && lv <= 15) {
			return pipe(c, lu == 13 && lv == 10, false);
		}
		if (y == dryFloor + 1) {
			if (lu == 17 && lv == 9) return Level1Blocks.SUPPLY_CRATE.defaultBlockState();
			if (lu == 17 && lv == 11) {
				return ModBlocks.MANILA_CABINET.defaultBlockState()
						.setValue(ManilaCabinetBlock.FACING, c.worldDir(Direction.WEST))
						.setValue(ManilaCabinetBlock.OPENED, false);
			}
			if (lu == 17 && lv == 10) {
				return ModBlocks.MANILA_TABLE.defaultBlockState()
						.setValue(ManilaFurnitureBlock.FACING, c.worldDir(Direction.WEST));
			}
		}
		if (y == dryFloor + 2 && lu == 17 && lv == 10) {
			return ModBlocks.FIELD_NOTE.defaultBlockState()
					.setValue(FieldNoteBlock.FACING, c.worldDir(Direction.WEST))
					.setValue(FieldNoteBlock.VARIANT, 2);
		}
		return Blocks.AIR.defaultBlockState();
	}

	/**
	 * Builds a straight 2-wide concrete staircase descending lu=2..8, lv=10..11 from y=31 down to y=25
	 * into the y=24 flooded basement chamber, keeping all cell perimeter walkways clear.
	 */
	private static BlockState downShaftBlockAt(Complex c, int lu, int y, int lv) {
		// Sub-level enclosure walls along tunnel flanks (lu in 6..8, lv in 9 & 12).
		if (lu >= 6 && lu <= 8 && (lv == 9 || lv == 12) && y <= Level0ChunkGenerator.FLOOR_Y - 1) {
			if (y < BASEMENT_FLOOR_Y) return Blocks.CONCRETE.black().defaultBlockState();
			return Level1Blocks.GARAGE_WALL.defaultBlockState();
		}

		// Basement steps: lu=2..8 at lv=10..11 step cleanly 31, 30, 29, 28, 27, 26, 25 -> floor 24 at lu=9.
		int stepY = downShaftStepY(lu, lv);
		if (stepY != Integer.MIN_VALUE) {
			boolean underGroundWalkway = lu >= 6; // stepY <= 27 -> 3+ blocks headroom & solid y=32 carpet above
			if (underGroundWalkway && y > Level0ChunkGenerator.FLOOR_Y - 1) {
				return null; // Ground floor y=32 carpet and y=33..36 hallway stay untouched above tunnel.
			}
			if (y < stepY) return Blocks.CONCRETE.black().defaultBlockState();
			if (y == stepY) {
				return Level1Blocks.CONCRETE_STAIRS.defaultBlockState()
						.setValue(StairBlock.FACING, c.worldDir(Direction.WEST));
			}

			// Safety wallpaper barrier at lu=5 on ground level (y=33..35) so walkway pedestrians don't fall in.
			if (lu == 5 && y >= Level0ChunkGenerator.FLOOR_Y + 1) {
				if (y > Level0ChunkGenerator.CEILING_Y) return Blocks.BEDROCK.defaultBlockState();
				if (y == Level0ChunkGenerator.CEILING_Y) return ModBlocks.CEILING_TILE.defaultBlockState();
				return (y == Level0ChunkGenerator.FLOOR_Y + 1
						? ModBlocks.WALLPAPER_BASEBOARD : ModBlocks.WALLPAPER).defaultBlockState();
			}

			int ceilY = underGroundWalkway ? Math.min(Level0ChunkGenerator.FLOOR_Y - 1, stepY + 4) : Level0ChunkGenerator.CEILING_Y;
			if (y < ceilY) return Blocks.AIR.defaultBlockState();
			if (y == ceilY) {
				if (underGroundWalkway) return Level1Blocks.GARAGE_CEILING.defaultBlockState();
				return (lu == 3 && lv == 10)
						? ModBlocks.BROKEN_FLUORESCENT_LIGHT.defaultBlockState()
						: ModBlocks.CEILING_TILE.defaultBlockState();
			}
			if (underGroundWalkway) return Blocks.CONCRETE.black().defaultBlockState();
			return Blocks.BEDROCK.defaultBlockState();
		}

		// Ground-floor outer walls enclosing the 4x4 stairwell opening at lu in 2..5, lv in 9..12.
		if (isDownShaftWall(lu, lv)) {
			if (y > Level0ChunkGenerator.CEILING_Y) return Blocks.BEDROCK.defaultBlockState();
			if (y == Level0ChunkGenerator.CEILING_Y) return ModBlocks.CEILING_TILE.defaultBlockState();
			if (y >= Level0ChunkGenerator.FLOOR_Y + 1) {
				return (y == Level0ChunkGenerator.FLOOR_Y + 1
						? ModBlocks.WALLPAPER_BASEBOARD : ModBlocks.WALLPAPER).defaultBlockState();
			}
			if (y >= BASEMENT_FLOOR_Y) return Level1Blocks.GARAGE_WALL.defaultBlockState();
			return Blocks.CONCRETE.black().defaultBlockState();
		}
		return null;
	}

	private static boolean isDownShaftWall(int lu, int lv) {
		return lu >= 2 && lu <= 5 && (lv == 9 || lv == 12);
	}

	private static int downShaftOpenStepY(int lu, int lv) {
		if ((lv == 10 || lv == 11) && lu >= 2 && lu <= 5) return Level0ChunkGenerator.FLOOR_Y - (lu - 1); // 31..28
		return Integer.MIN_VALUE;
	}

	private static int downShaftStepY(int lu, int lv) {
		if ((lv == 10 || lv == 11) && lu >= 2 && lu <= 8) return Level0ChunkGenerator.FLOOR_Y - (lu - 1); // 31..25
		return Integer.MIN_VALUE;
	}

	private static long hash(long seed, int x, int z, int salt) {
		long h = mix(seed + salt);
		h = mix(h ^ x);
		return mix(h ^ z);
	}

	private static long mix(long v) {
		v += 0x9E3779B97F4A7C15L;
		v = (v ^ (v >>> 30)) * 0xBF58476D1CE4E5B9L;
		v = (v ^ (v >>> 27)) * 0x94D049BB133111EBL;
		return v ^ (v >>> 31);
	}

	private static final ThreadLocal<SectorCache> CACHE = ThreadLocal.withInitial(SectorCache::new);

	private static final class SectorCache {
		private static final int MASK = 255;
		private final long[] seeds = new long[MASK + 1];
		private final int[] sxs = new int[MASK + 1];
		private final int[] szs = new int[MASK + 1];
		private final Complex[] complexes = new Complex[MASK + 1];
		private final boolean[] valid = new boolean[MASK + 1];

		Complex getOrCompute(long seed, int sx, int sz) {
			int idx = (int) ((seed ^ (sx * 0x9E3779B97F4A7C15L) ^ (sz * 0xC2B2AE3D27D4EB4FL)) >>> 32) & MASK;
			if (valid[idx] && seeds[idx] == seed && sxs[idx] == sx && szs[idx] == sz) {
				return complexes[idx];
			}
			Complex computed = computeInSector(seed, sx, sz);
			seeds[idx] = seed;
			sxs[idx] = sx;
			szs[idx] = sz;
			complexes[idx] = computed;
			valid[idx] = true;
			return computed;
		}
	}
}
