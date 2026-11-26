package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.level1.Level1Blocks;
import dev.noclip.backrooms.level1.PipeBlock;
import dev.noclip.backrooms.props.BackroomsLootChestBlock;
import dev.noclip.backrooms.props.BackroomsProps;
import dev.noclip.backrooms.props.NoClipPropBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Escapable lower sub-level (Room 14D / maintenance undercroft) beneath every {@link Maze.Zone#PITS} sector.
 *
 * Falling through a 3x3, 4x4, or 5x3 floor pit no longer kills the player in the void; instead they land
 * in a dim, damp subterranean corridor network (floor y = 14, ceiling y = 20, 5-block headroom) marked with
 * black escape arrows leading to a lit ladder shaft in the sector's center-south cell (lsx=3, lsz=3)
 * that climbs cleanly back to the upper Level 0 walkway (y = 32), plus a risky loot cache in the corner.
 */
final class PitfallsPlan {
	static final int LOWER_FLOOR_Y = 14;
	static final int LOWER_CEIL_Y = 20;
	static final int SHAFT_LX = 3;
	static final int SHAFT_LZ = 3;
	static final int CACHE_LX = 1;
	static final int CACHE_LZ = 1;

	private PitfallsPlan() {
	}

	static boolean isEscapeShaftCell(int cx, int cz) {
		return Math.floorMod(cx, Maze.SECTOR) == SHAFT_LX && Math.floorMod(cz, Maze.SECTOR) == SHAFT_LZ;
	}

	/** Opening at upper FLOOR_Y for the 2x2 ladder escape shaft (ix in 4..5, iz in 4..5, ladder on ix=4, iz=4..5 facing EAST). */
	static boolean isEscapeShaftOpening(int cx, int cz, int ix, int iz) {
		return isEscapeShaftCell(cx, cz) && ix >= 4 && ix <= 5 && iz >= 4 && iz <= 5;
	}

	/** Pure-geometry check for whether (y, ix, iz) inside the escape shaft cell holds a climbable escape ladder rung. */
	static boolean isEscapeLadderAt(int y, int ix, int iz) {
		return ix == 4 && iz >= 4 && iz <= 5 && y >= LOWER_FLOOR_Y + 1 && y <= Level0ChunkGenerator.FLOOR_Y;
	}

	/**
	 * Generates subterranean Room 14D / Pitfalls lower blocks (y < FLOOR_Y) and the upper-level escape ladder shaft
	 * inside a PITS sector. Returns null when standard upper-floor generation should run.
	 */
	static BlockState blockAt(long seed, int x, int y, int z, int cx, int cz, int ix, int iz) {
		int lx = Math.floorMod(cx, Maze.SECTOR);
		int lz = Math.floorMod(cz, Maze.SECTOR);

		// Escape ladder shaft connecting lower floor (y=14) all the way up to upper walkway (y=32).
		if (lx == SHAFT_LX && lz == SHAFT_LZ) {
			BlockState shaft = escapeShaftBlockAt(y, ix, iz);
			if (shaft != null) return shaft;
		}

		// Upper floor and above handled by normal Level0ChunkGenerator (including open pit mouths).
		if (y >= Level0ChunkGenerator.FLOOR_Y) {
			return null;
		}

		// Vertical drop shaft beneath an open pit (y in 20..31): open air with concrete walls surrounding it.
		if (y >= LOWER_CEIL_Y) {
			if (Maze.isPit(seed, x, z)) {
				return Blocks.AIR.defaultBlockState();
			}
			// Rim blocks directly adjacent to a pit shaft show rough dark concrete/wallpaper shaft liners.
			if (y == LOWER_CEIL_Y) {
				if ((ix == 5 && iz == 5) && ((lx + lz) & 1) == 0) {
					return (lx == SHAFT_LX || lz == SHAFT_LZ)
							? Level1Blocks.BARE_BULB.defaultBlockState()
							: Level1Blocks.BROKEN_BARE_BULB.defaultBlockState();
				}
				return Level1Blocks.GARAGE_CEILING.defaultBlockState();
			}
			return Blocks.CONCRETE.black().defaultBlockState();
		}

		if (y < LOWER_FLOOR_Y) {
			return Blocks.CONCRETE.black().defaultBlockState();
		}

		// Sector perimeter walls seal the undercroft cleanly inside the 60x60 PITS sector.
		boolean outerBorder = (lx == 0 && ix == 0) || (lz == 0 && iz == 0)
				|| (lx == Maze.SECTOR - 1 && ix == Maze.CELL - 1)
				|| (lz == Maze.SECTOR - 1 && iz == Maze.CELL - 1);
		if (outerBorder) {
			if (y == LOWER_FLOOR_Y) return Level1Blocks.GARAGE_FLOOR.defaultBlockState();
			return (y == LOWER_FLOOR_Y + 2 && (ix == 5 || iz == 5))
					? ModBlocks.GRAFFITI_WALL.defaultBlockState().setValue(Level0EventBlocks.GraffitiWallBlock.VARIANT, 3)
					: Level1Blocks.GARAGE_WALL.defaultBlockState();
		}

		if (y == LOWER_FLOOR_Y) {
			if (Maze.isPit(seed, x, z)) return Level1Blocks.DAMP_GARAGE_FLOOR.defaultBlockState();
			return ((lx + lz) & 1) == 0
					? Level1Blocks.GARAGE_FLOOR.defaultBlockState()
					: Level1Blocks.DAMP_GARAGE_FLOOR.defaultBlockState();
		}

		// Sparse structural columns & arrow-marked partition walls in the undercroft (always keeping 4-block open lanes!).
		boolean lowerPillar = (ix == 0 || ix == 1) && (iz == 0 || iz == 1);
		if (lowerPillar) {
			if (y == LOWER_FLOOR_Y + 2 && (lx == SHAFT_LX || lz == SHAFT_LZ)) {
				return ModBlocks.GRAFFITI_WALL.defaultBlockState().setValue(Level0EventBlocks.GraffitiWallBlock.VARIANT, 3);
			}
			return (y == LOWER_FLOOR_Y + 1 ? ModBlocks.ROUGH_WALLPAPER_BASEBOARD : ModBlocks.ROUGH_WALLPAPER).defaultBlockState();
		}

		// Ceiling pipes running through the undercroft at y = LOWER_CEIL_Y - 1 (19)
		if (y == LOWER_CEIL_Y - 1 && iz == 2 && !Maze.isPit(seed, x, z)) {
			return ((lx + ix) % 7 == 0 ? Level1Blocks.LEAKING_CEILING_PIPE : Level1Blocks.CEILING_PIPE)
					.defaultBlockState().setValue(PipeBlock.AXIS, Direction.Axis.X);
		}

		// Risky reward cache in lower corner cell (CACHE_LX, CACHE_LZ)
		if (lx == CACHE_LX && lz == CACHE_LZ && y == LOWER_FLOOR_Y + 1) {
			if (ix == 5 && iz == 5) {
				return BackroomsProps.ANOMALY_VAULT_CHEST.defaultBlockState()
						.setValue(BackroomsLootChestBlock.FACING, Direction.SOUTH);
			}
			if (ix == 6 && iz == 5) {
				return BackroomsProps.WANDERER_REMAINS.defaultBlockState()
						.setValue(NoClipPropBlock.FACING, Direction.WEST);
			}
			if (ix == 4 && iz == 5) {
				return Level1Blocks.SUPPLY_CRATE.defaultBlockState();
			}
		}

		return Blocks.AIR.defaultBlockState();
	}

	private static BlockState escapeShaftBlockAt(int y, int ix, int iz) {
		// Ladder shaft interior: ix in 4..5, iz in 4..5; backing wall on ix=3, iz in 3..6 so ladders attach solidly.
		boolean inShaft = ix >= 4 && ix <= 5 && iz >= 4 && iz <= 5;
		boolean backWall = ix == 3 && iz >= 4 && iz <= 5;

		if (y > Level0ChunkGenerator.FLOOR_Y && y < Level0ChunkGenerator.CEILING_Y) {
			// Ground-level safety railing/marker column beside shaft on ix=3 so players spot it easily.
			if (backWall) {
				if (y == Level0ChunkGenerator.FLOOR_Y + 2) {
					return ModBlocks.GRAFFITI_WALL.defaultBlockState().setValue(Level0EventBlocks.GraffitiWallBlock.VARIANT, 3);
				}
				return (y == Level0ChunkGenerator.FLOOR_Y + 1
						? ModBlocks.ROUGH_WALLPAPER_BASEBOARD
						: ModBlocks.ROUGH_WALLPAPER).defaultBlockState();
			}
			return null;
		}
		if (y == Level0ChunkGenerator.CEILING_Y && inShaft) {
			return ModBlocks.FLUORESCENT_LIGHT.defaultBlockState();
		}
		if (y > Level0ChunkGenerator.FLOOR_Y) return null;

		if (y < LOWER_FLOOR_Y) return Blocks.CONCRETE.black().defaultBlockState();
		if (y == LOWER_FLOOR_Y) {
			return Level1Blocks.GARAGE_FLOOR.defaultBlockState();
		}

		if (backWall && y >= LOWER_FLOOR_Y + 1 && y <= Level0ChunkGenerator.FLOOR_Y) {
			return Level1Blocks.GARAGE_WALL.defaultBlockState();
		}
		if (inShaft && y >= LOWER_FLOOR_Y + 1 && y <= Level0ChunkGenerator.FLOOR_Y) {
			if (isEscapeLadderAt(y, ix, iz)) {
				return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
			}
			return Blocks.AIR.defaultBlockState();
		}
		if (y >= LOWER_CEIL_Y && y < Level0ChunkGenerator.FLOOR_Y) {
			if (y == LOWER_CEIL_Y && ix == 6 && iz == 5) {
				return Level1Blocks.BARE_BULB.defaultBlockState();
			}
			return Blocks.CONCRETE.black().defaultBlockState();
		}
		return null;
	}
}
