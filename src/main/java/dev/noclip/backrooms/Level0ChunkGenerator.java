package dev.noclip.backrooms;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
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
	static final int HEIGHT = 48;

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
		// Solid bedrock at y=0 and above the ceiling so nothing can escape underneath or land on the roof.
		if (y == 0 || y > CEILING_Y) return Blocks.BEDROCK.defaultBlockState();
		if (y < FLOOR_Y) {
			if (Maze.isPit(seed, x, z)) {
				return (y == 1 ? Blocks.CONCRETE.black() : Blocks.AIR).defaultBlockState();
			}
			if (y == FLOOR_Y - 1 && Maze.isPuddle(seed, x, z)) {
				return ModBlocks.CARPET.defaultBlockState();
			}
			return Blocks.CONCRETE.black().defaultBlockState();
		}
		if (y == FLOOR_Y) {
			if (Maze.isPit(seed, x, z)) return Blocks.AIR.defaultBlockState();
			if (Maze.isPuddle(seed, x, z)) return Blocks.WATER.defaultBlockState();
			return (Maze.isThickCarpet(seed, x, z) ? ModBlocks.THICK_CARPET : ModBlocks.CARPET).defaultBlockState();
		}
		Maze.Zone zone = Maze.zoneAt(seed, x, z);
		if (y == CEILING_Y) {
			if (!Maze.isLamp(x, z)) return ModBlocks.CEILING_TILE.defaultBlockState();
			if (Exits.lampPointsToExit(seed, x, z)) return ModBlocks.EXIT_LIGHT.defaultBlockState();
			if (!Maze.lampWorks(seed, x, z)) return ModBlocks.BROKEN_FLUORESCENT_LIGHT.defaultBlockState();
			return (zone == Maze.Zone.RED_ROOM ? ModBlocks.RED_FLUORESCENT_LIGHT : ModBlocks.FLUORESCENT_LIGHT).defaultBlockState();
		}
		if (!Maze.isWall(seed, x, z, y == CEILING_Y - 1)) return Blocks.AIR.defaultBlockState();
		boolean baseboard = y == FLOOR_Y + 1;
		if (Exits.isDoor(seed, x, z)) {
			if (baseboard) return ModBlocks.EXIT_DOOR.defaultBlockState();
			return (y == FLOOR_Y + 2 ? ModBlocks.EXIT_DOOR_TOP : ModBlocks.EXIT_SIGN).defaultBlockState();
		}
		if (zone == Maze.Zone.ARCHES) {
			return (baseboard ? ModBlocks.PALE_WALLPAPER_BASEBOARD : ModBlocks.PALE_WALLPAPER).defaultBlockState();
		}
		if (zone == Maze.Zone.RED_ROOM) {
			return (baseboard ? ModBlocks.RED_WALLPAPER_BASEBOARD : ModBlocks.RED_WALLPAPER).defaultBlockState();
		}
		if (!baseboard && Maze.isRedWarningWall(seed, x, z)) {
			return ModBlocks.PEELING_RED_WALLPAPER.defaultBlockState();
		}
		return (baseboard ? ModBlocks.WALLPAPER_BASEBOARD : ModBlocks.WALLPAPER).defaultBlockState();
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
				for (int y = 0; y < HEIGHT; y++) {
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
		for (int y = 0; y < HEIGHT; y++) column[y] = blockAt(seed, x, y, z);
		return new NoiseColumn(0, column);
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
		return HEIGHT;
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
		return 0;
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
