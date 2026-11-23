package dev.noclip.backrooms;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

final class Level0ChunkGenerator extends ChunkGenerator {
	static final MapCodec<Level0ChunkGenerator> CODEC = BiomeSource.CODEC.fieldOf("biome_source")
			.xmap(Level0ChunkGenerator::new, generator -> generator.biomeSource);

	static final int FLOOR_Y = 32;
	static final int CEILING_Y = 36;
	// Must match dimension_type/level_0.json
	static final int MIN_Y = -32;
	static final int HEIGHT = 128;
	static final int MAX_Y = MIN_Y + HEIGHT - 1;

	private Level0ChunkGenerator(BiomeSource biomeSource) {
		super(biomeSource);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	// RandomState is derived from the world seed, which fillFromNoise is not given directly.
	static long mazeSeed(RandomState randomState) {
		return randomState.getOrCreateRandomFactory(BackroomsMod.id("maze")).at(0, 0, 0).nextLong();
	}

	static BlockState blockAt(long seed, int x, int y, int z) {
		return blockAt(seed, x, y, z, Maze.EpochSource.ZERO);
	}

	static BlockState blockAt(long seed, int x, int y, int z, Maze.EpochSource epochs) {
		// Solid bedrock at min_y and above the ceiling so nothing can escape underneath or land on the roof.
		if (y == MIN_Y) return Blocks.BEDROCK.defaultBlockState();
		int cx = Math.floorDiv(x, Maze.CELL), cz = Math.floorDiv(z, Maze.CELL);
		int ix = Math.floorMod(x, Maze.CELL), iz = Math.floorMod(z, Maze.CELL);
		Maze.Zone zone = Maze.zoneAt(seed, x, z);

		if (zone == Maze.Zone.CHASM) return chasmBlockAt(seed, x, y, z, cx, cz, ix, iz, epochs);
		if (zone == Maze.Zone.MEGATHALL) return megahallBlockAt(seed, x, y, z, cx, cz, ix, iz, epochs);

		if (y < FLOOR_Y) {
			if (Maze.isPit(seed, x, z)) {
				return Blocks.AIR.defaultBlockState();
			}
			if (y == FLOOR_Y - 1 && ix >= 2 && ix <= 5 && iz >= 2 && iz <= 5 && Maze.isSunkenCell(seed, cx, cz)) {
				if (isSunkenStep(seed, cx, cz, ix, iz)) return Blocks.CONCRETE.black().defaultBlockState();
				return (zone == Maze.Zone.ARCHES ? ModBlocks.THICK_CARPET : ModBlocks.DRY_CARPET).defaultBlockState();
			}
			if (y == FLOOR_Y - 1 && Maze.isPuddle(seed, x, z)) {
				return ModBlocks.CARPET.defaultBlockState();
			}
			return Blocks.CONCRETE.black().defaultBlockState();
		}
		if (y == FLOOR_Y) {
			if (Maze.isPit(seed, x, z)) return Blocks.AIR.defaultBlockState();
			if (Maze.isPuddle(seed, x, z)) return Blocks.WATER.defaultBlockState();
			if (ix >= 2 && ix <= 5 && iz >= 2 && iz <= 5 && Maze.isSunkenCell(seed, cx, cz)) {
				if (isSunkenStep(seed, cx, cz, ix, iz)) {
					return Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState()
							.setValue(StairBlock.FACING, sunkenStepFacing(seed, cx, cz));
				}
				return Blocks.AIR.defaultBlockState();
			}
			if (Maze.isThickCarpet(seed, x, z)) return ModBlocks.THICK_CARPET.defaultBlockState();
			if (zone == Maze.Zone.PILLARS) return ModBlocks.DRY_CARPET.defaultBlockState();
			if (ix >= 2 && ix <= 5 && iz >= 2 && iz <= 5) {
				Maze.FloorAccent accent = Maze.floorAccent(seed, cx, cz);
				if (accent == Maze.FloorAccent.DRY_RUG) return ModBlocks.DRY_CARPET.defaultBlockState();
				if (accent == Maze.FloorAccent.THICK_RUG) return ModBlocks.THICK_CARPET.defaultBlockState();
			}
			return ModBlocks.CARPET.defaultBlockState();
		}

		BlockState stairwell = stairwellBlockAt(seed, x, y, z);
		if (stairwell != null) return stairwell;

		int ceilY = Maze.cellCeilingY(seed, cx, cz);
		Maze.CeilingStyle ceiling = Maze.ceilingStyle(seed, cx, cz);
		boolean inCofferCore = ceiling == Maze.CeilingStyle.COFFERED && ix >= 2 && ix <= 5 && iz >= 2 && iz <= 5;
		if (inCofferCore) {
			if (y > ceilY + 1) return Blocks.BEDROCK.defaultBlockState();
			if (y == ceilY + 1) return ceilingOrLampBlockAt(seed, x, z, zone, epochs);
			if (y == ceilY) {
				if (Maze.isWall(seed, x, z, true, epochs)) {
					return wallBlockAt(seed, x, z, cx, cz, ix, iz, zone, false, epochs);
				}
				return Blocks.AIR.defaultBlockState();
			}
		} else {
			if (y > ceilY) return Blocks.BEDROCK.defaultBlockState();
			if (y == ceilY) return ceilingOrLampBlockAt(seed, x, z, zone, epochs);
		}

		boolean topLayer = y == ceilY - 1;
		boolean baseboard = y == FLOOR_Y + 1;
		if (Exits.isDoor(seed, x, z) && y <= FLOOR_Y + 3 && Maze.isWall(seed, x, z, y == FLOOR_Y + 3, epochs)) {
			if (baseboard) return ModBlocks.EXIT_DOOR.defaultBlockState();
			return (y == FLOOR_Y + 2 ? ModBlocks.EXIT_DOOR_TOP : ModBlocks.EXIT_SIGN).defaultBlockState();
		}
		if (Maze.isWall(seed, x, z, topLayer, epochs)) {
			return wallBlockAt(seed, x, z, cx, cz, ix, iz, zone, baseboard, epochs);
		}
		if (topLayer && isDroppedCeilingTile(seed, x, z, ix, iz, ceiling, epochs)) {
			return ModBlocks.CEILING_TILE.defaultBlockState();
		}
		return Blocks.AIR.defaultBlockState();
	}

	private static BlockState chasmBlockAt(long seed, int x, int y, int z, int cx, int cz, int ix, int iz,
			Maze.EpochSource epochs) {
		int floorY = ChasmPlan.floorYAt(seed, cx, cz, ix, iz);
		int ceilY = ChasmPlan.cellCeilingY(seed, cx, cz);

		if (floorY == Integer.MIN_VALUE) {
			if (y == ChasmPlan.ABYSS_FLOOR_Y) return Blocks.CONCRETE.black().defaultBlockState();
			if (y < ceilY) return Blocks.AIR.defaultBlockState();
			if (y == ceilY) return ceilingOrLampBlockAt(seed, x, z, Maze.Zone.CHASM, epochs);
			return Blocks.BEDROCK.defaultBlockState();
		}

		if (y < floorY) {
			return cliffStrataBlock(y, false);
		}
		if (y == floorY) {
			Direction stair = ChasmPlan.stairFacingAt(seed, x, z);
			if (stair != null) {
				return Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, stair);
			}
			if (floorY <= 12) return ModBlocks.THICK_CARPET.defaultBlockState();
			return ModBlocks.CARPET.defaultBlockState();
		}
		if (y > ceilY) {
			if (ceilY <= FLOOR_Y + 6 && y <= 64) {
				return cliffStrataBlock(y, false);
			}
			return Blocks.BEDROCK.defaultBlockState();
		}
		if (y == ceilY) {
			return ceilingOrLampBlockAt(seed, x, z, Maze.Zone.CHASM, epochs);
		}

		boolean baseboard = y == floorY + 1;
		boolean topLayer = y == ceilY - 1;
		if (ChasmPlan.isFlatRimCell(seed, cx, cz) && Exits.isDoor(seed, x, z) && y <= FLOOR_Y + 3
				&& Maze.isWall(seed, x, z, y == FLOOR_Y + 3, epochs)) {
			if (baseboard) return ModBlocks.EXIT_DOOR.defaultBlockState();
			return (y == FLOOR_Y + 2 ? ModBlocks.EXIT_DOOR_TOP : ModBlocks.EXIT_SIGN).defaultBlockState();
		}
		if (ChasmPlan.isWallAt(seed, x, z, topLayer)) {
			boolean openCavern = ceilY >= 58;
			if (openCavern && ix != 0 && iz != 0 && y > floorY + 2) {
				return Blocks.AIR.defaultBlockState();
			}
			boolean isCornerPier = (ix == 0 && (iz == 1 || iz == Maze.CELL - 1))
					|| (iz == 0 && (ix == 1 || ix == Maze.CELL - 1))
					|| (ix == 0 && iz == 0);
			if (openCavern && !isCornerPier && y > floorY + 4) {
				return Blocks.AIR.defaultBlockState();
			}
			return cliffStrataBlock(y, baseboard);
		}
		return Blocks.AIR.defaultBlockState();
	}

	private static BlockState megahallBlockAt(long seed, int x, int y, int z, int cx, int cz, int ix, int iz,
			Maze.EpochSource epochs) {
		int floorY = MegahallPlan.walkableFloorY(seed, x, z);
		int ceilY = MegahallPlan.cellCeilingY(seed, cx, cz);

		if (y < floorY) return Blocks.CONCRETE.black().defaultBlockState();
		if (y == floorY) {
			Direction stair = MegahallPlan.stairFacingAt(seed, x, z);
			if (stair != null) {
				return Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, stair);
			}
			if (floorY < FLOOR_Y) return ModBlocks.THICK_CARPET.defaultBlockState();
			if (floorY > FLOOR_Y) return ModBlocks.DRY_CARPET.defaultBlockState();
			if (((cx + cz) & 1) == 0 && ix >= 2 && ix <= 5 && iz >= 2 && iz <= 5) {
				return ModBlocks.DRY_CARPET.defaultBlockState();
			}
			return ModBlocks.CARPET.defaultBlockState();
		}
		if (y > ceilY) return Blocks.BEDROCK.defaultBlockState();
		if (y == ceilY) return ceilingOrLampBlockAt(seed, x, z, Maze.Zone.MEGATHALL, epochs);

		boolean baseboard = y == floorY + 1;
		boolean topLayer = y == ceilY - 1;
		if (MegahallPlan.isRimCell(cx, cz) && Exits.isDoor(seed, x, z) && y <= FLOOR_Y + 3
				&& Maze.isWall(seed, x, z, y == FLOOR_Y + 3, epochs)) {
			if (baseboard) return ModBlocks.EXIT_DOOR.defaultBlockState();
			return (y == FLOOR_Y + 2 ? ModBlocks.EXIT_DOOR_TOP : ModBlocks.EXIT_SIGN).defaultBlockState();
		}
		if (MegahallPlan.isWallAt(seed, x, z, topLayer)) {
			if (y == FLOOR_Y + 14 || y == FLOOR_Y + 28) {
				return ModBlocks.CEILING_TILE.defaultBlockState();
			}
			boolean paleColumn = ix != 0 && iz != 0 && Math.floorMod(cx + cz, 2) == 0;
			if (paleColumn) {
				return (baseboard ? ModBlocks.PALE_WALLPAPER_BASEBOARD : ModBlocks.PALE_WALLPAPER).defaultBlockState();
			}
			return (baseboard ? ModBlocks.WALLPAPER_BASEBOARD : ModBlocks.WALLPAPER).defaultBlockState();
		}
		if (MegahallPlan.isHighBeam(seed, x, y, z, ceilY)) {
			return ModBlocks.CEILING_TILE.defaultBlockState();
		}
		return Blocks.AIR.defaultBlockState();
	}

	private static BlockState cliffStrataBlock(int y, boolean baseboard) {
		if (baseboard) return ModBlocks.ROUGH_WALLPAPER_BASEBOARD.defaultBlockState();
		int band = Math.floorMod(y, 10);
		if (band == 0) return Blocks.CONCRETE.black().defaultBlockState();
		if (band <= 4) return ModBlocks.ROUGH_WALLPAPER.defaultBlockState();
		if (band <= 7) return ModBlocks.WALLPAPER.defaultBlockState();
		return ModBlocks.PALE_WALLPAPER.defaultBlockState();
	}

	private static BlockState ceilingOrLampBlockAt(long seed, int x, int z, Maze.Zone zone, Maze.EpochSource epochs) {
		if (!Maze.isLamp(seed, x, z, epochs)) return ModBlocks.CEILING_TILE.defaultBlockState();
		if (Exits.lampPointsToExit(seed, x, z)) return ModBlocks.EXIT_LIGHT.defaultBlockState();
		if (!Maze.lampWorks(seed, x, z, epochs)) return ModBlocks.BROKEN_FLUORESCENT_LIGHT.defaultBlockState();
		return (zone == Maze.Zone.RED_ROOM ? ModBlocks.RED_FLUORESCENT_LIGHT : ModBlocks.FLUORESCENT_LIGHT).defaultBlockState();
	}

	private static boolean isDroppedCeilingTile(long seed, int x, int z, int ix, int iz,
			Maze.CeilingStyle ceiling, Maze.EpochSource epochs) {
		if (ix == 0 || iz == 0) return false;
		return switch (ceiling) {
			case BEAMED_X -> ix == 2 || ix == 5;
			case BEAMED_Z -> iz == 2 || iz == 5;
			case LOW_SOFFIT -> !Maze.isLamp(seed, x, z, epochs);
			case FLAT, COFFERED -> false;
		};
	}

	private static BlockState wallBlockAt(long seed, int x, int z, int cx, int cz, int ix, int iz,
			Maze.Zone zone, boolean baseboard, Maze.EpochSource epochs) {
		if (zone == Maze.Zone.ARCHES) {
			return (baseboard ? ModBlocks.PALE_WALLPAPER_BASEBOARD : ModBlocks.PALE_WALLPAPER).defaultBlockState();
		}
		if (zone == Maze.Zone.RED_ROOM) {
			return (baseboard ? ModBlocks.RED_WALLPAPER_BASEBOARD : ModBlocks.RED_WALLPAPER).defaultBlockState();
		}
		if (zone == Maze.Zone.DARKENED) {
			return (baseboard ? ModBlocks.ROUGH_WALLPAPER_BASEBOARD : ModBlocks.ROUGH_WALLPAPER).defaultBlockState();
		}
		if (ix != 0 && iz != 0 && Maze.useAccentWall(seed, cx, cz, epochs)) {
			boolean pale = zone == Maze.Zone.CUBICLES || Math.floorMod(cx + cz, 2) == 0;
			if (pale) {
				return (baseboard ? ModBlocks.PALE_WALLPAPER_BASEBOARD : ModBlocks.PALE_WALLPAPER).defaultBlockState();
			}
			return (baseboard ? ModBlocks.ROUGH_WALLPAPER_BASEBOARD : ModBlocks.ROUGH_WALLPAPER).defaultBlockState();
		}
		if (!baseboard && Maze.isRedWarningWall(seed, x, z, epochs)) {
			return ModBlocks.PEELING_RED_WALLPAPER.defaultBlockState();
		}
		return (baseboard ? ModBlocks.WALLPAPER_BASEBOARD : ModBlocks.WALLPAPER).defaultBlockState();
	}

	private static boolean isSunkenStep(long seed, int cx, int cz, int ix, int iz) {
		return switch (Maze.sunkenEntryDir(seed, cx, cz)) {
			case 0 -> ix == 2 && (iz == 3 || iz == 4);
			case 1 -> ix == 5 && (iz == 3 || iz == 4);
			case 2 -> iz == 2 && (ix == 3 || ix == 4);
			default -> iz == 5 && (ix == 3 || ix == 4);
		};
	}

	private static Direction sunkenStepFacing(long seed, int cx, int cz) {
		return switch (Maze.sunkenEntryDir(seed, cx, cz)) {
			case 0 -> Direction.WEST;
			case 1 -> Direction.EAST;
			case 2 -> Direction.NORTH;
			default -> Direction.SOUTH;
		};
	}

	private static BlockState stairwellBlockAt(long seed, int x, int y, int z) {
		int cx = Math.floorDiv(x, Maze.CELL), cz = Math.floorDiv(z, Maze.CELL);
		int dir = Maze.stairwellDir(seed, cx, cz);
		if (dir < 0) return null;
		int ix = Math.floorMod(x, Maze.CELL), iz = Math.floorMod(z, Maze.CELL);

		boolean isWall = switch (dir) {
			case 0 -> (iz == 5 && ix >= 2 && ix <= 5) || (ix == 5 && iz == 4);
			case 1 -> (iz == 5 && ix >= 2 && ix <= 5) || (ix == 2 && iz == 4);
			case 2 -> (ix == 5 && iz >= 2 && iz <= 5) || (iz == 5 && ix == 4);
			default -> (ix == 5 && iz >= 2 && iz <= 5) || (iz == 2 && ix == 4);
		};
		if (isWall) {
			if (y == FLOOR_Y + 1) return ModBlocks.WALLPAPER_BASEBOARD.defaultBlockState();
			if (y <= FLOOR_Y + 7) return ModBlocks.WALLPAPER.defaultBlockState();
			return Blocks.BEDROCK.defaultBlockState();
		}

		int step = switch (dir) {
			case 0 -> (iz == 4 && ix >= 2 && ix <= 4) ? ix - 2 : -1;
			case 1 -> (iz == 4 && ix >= 3 && ix <= 5) ? 5 - ix : -1;
			case 2 -> (ix == 4 && iz >= 2 && iz <= 4) ? iz - 2 : -1;
			default -> (ix == 4 && iz >= 3 && iz <= 5) ? 5 - iz : -1;
		};
		if (step >= 0) {
			int relY = y - FLOOR_Y;
			if (relY <= step) return Blocks.SMOOTH_SANDSTONE.defaultBlockState();
			if (relY == step + 1) {
				Direction facing = switch (dir) {
					case 0 -> Direction.EAST;
					case 1 -> Direction.WEST;
					case 2 -> Direction.SOUTH;
					default -> Direction.NORTH;
				};
				return step < 2
						? Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing)
						: Blocks.SMOOTH_SANDSTONE.defaultBlockState();
			}
			if (relY <= 6) return Blocks.AIR.defaultBlockState();
			if (relY == 7) {
				return (step == 1 ? ModBlocks.BROKEN_FLUORESCENT_LIGHT : ModBlocks.CEILING_TILE).defaultBlockState();
			}
			return Blocks.BEDROCK.defaultBlockState();
		}
		return null;
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
			StructureManager structureManager, ChunkAccess chunk) {
		long seed = mazeSeed(randomState);
		Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
		Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = MIN_Y; y <= MAX_Y; y++) {
					BlockState state = blockAt(seed, minX + x, y, minZ + z);
					chunk.setBlockState(pos.set(x, y, z), state);
					oceanFloor.update(x, y, z, state);
					surface.update(x, y, z, state);
				}
			}
		}
		return CompletableFuture.completedFuture(chunk);
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
		long seed = mazeSeed(randomState);
		BlockState[] column = new BlockState[HEIGHT];
		for (int i = 0; i < HEIGHT; i++) column[i] = blockAt(seed, x, MIN_Y + i, z);
		return new NoiseColumn(MIN_Y, column);
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
		return MAX_Y + 1;
	}

	@Override
	public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager,
			StructureManager structureManager, ChunkAccess chunk) {
	}

	@Override
	public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion region) {
	}

	@Override
	public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos) {
	}

	@Override
	public int getMinY() {
		return MIN_Y;
	}

	@Override
	public int getGenDepth() {
		return HEIGHT;
	}

	@Override
	public int getSeaLevel() {
		return 0;
	}
}
