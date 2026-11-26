package dev.noclip.backrooms.level4;

import com.mojang.serialization.MapCodec;
import dev.noclip.backrooms.level4.Level4Layout.Kind;
import dev.noclip.backrooms.level4.Level4Layout.Tile;
import dev.noclip.backrooms.level4.Level4Plan.ZoneKind;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Turns {@link Level4Layout} into blocks: charcoal commercial carpet with desk/chair indentations,
 * cool matte drywall with single-height baseboards, 4-tile acoustic ceilings with 4-state louvered troffers,
 * blacked-out vs Entity 2 trap windows, rainy double-height Almond Water atriums, and three faction bases.
 */
final class Level4ChunkGenerator extends ChunkGenerator {
	static final MapCodec<Level4ChunkGenerator> CODEC = BiomeSource.CODEC.fieldOf("biome_source")
			.xmap(Level4ChunkGenerator::new, generator -> generator.biomeSource);

	static final int FLOOR_Y = Level4Layout.FLOOR_Y;
	static final int HEIGHT = 48;

	private Level4ChunkGenerator(BiomeSource biomeSource) {
		super(biomeSource);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	static long planSeed(RandomState randomState) {
		return randomState.getOrCreateRandomFactory(Level4.id("level_4_plan")).at(0, 0, 0).nextLong();
	}

	static BlockState blockAt(long seed, int x, int y, int z) {
		if (y <= 0 || y >= HEIGHT - 2) return Blocks.BEDROCK.defaultBlockState();
		if (y < FLOOR_Y) return Blocks.CONCRETE.gray().defaultBlockState();
		Tile tile = Level4Layout.tile(seed, x, z);
		int roofY = FLOOR_Y + tile.ceilingHeight() + 1;
		if (y > roofY) return Blocks.CONCRETE.gray().defaultBlockState();
		if (y == FLOOR_Y) return floor(seed, x, z, tile);
		if (y == roofY) return ceiling(seed, x, z, tile);
		int h = y - FLOOR_Y;
		return columnBlock(tile, h);
	}

	private static BlockState floor(long seed, int x, int z, Tile tile) {
		if (tile.kind() == Kind.FOUNTAIN_BASIN || tile.kind() == Kind.FOUNTAIN_POOL) {
			return Level4Blocks.ALMOND_FOUNTAIN_BASIN.defaultBlockState();
		}
		if (tile.kind() == Kind.EXIT_HATCH) {
			return Level4Blocks.OFFICE_HATCH_2.defaultBlockState();
		}
		if (Level4Layout.isDampCarpet(seed, x, z, tile.zone())) {
			return Level4Blocks.OFFICE_DAMP_CARPET.defaultBlockState();
		}
		if (Level4Layout.hasCarpetDent(seed, x, z)) {
			return Level4Blocks.OFFICE_CARPET_DENT.defaultBlockState();
		}
		return Level4Blocks.OFFICE_CARPET.defaultBlockState();
	}

	private static BlockState ceiling(long seed, int x, int z, Tile tile) {
		if (!tile.walkable() && tile.kind() != Kind.PILLAR && tile.kind() != Kind.GLASS_WALL && tile.kind() != Kind.FOUNTAIN_POOL
				&& tile.kind() != Kind.FOUNTAIN_BASIN && tile.kind() != Kind.WATER_COOLER && tile.kind() != Kind.VENDING
				&& tile.kind() != Kind.OMEGA_COT && tile.kind() != Kind.OMEGA_LOCKER && tile.kind() != Kind.OMEGA_BARRICADE
				&& tile.kind() != Kind.AMOR_PLANTER && tile.kind() != Kind.TBD_SHELF && tile.kind() != Kind.BREAKER
				&& tile.kind() != Kind.PHOTOCOPIER && tile.kind() != Kind.DESK_PHONE) {
			return Level4Blocks.OFFICE_ACOUSTIC_CEILING.defaultBlockState();
		}
		int lx = Math.floorMod(x, Level4Plan.CELL);
		int lz = Math.floorMod(z, Level4Plan.CELL);
		// Troffer strip lights centered in every cell (cx=3, cz=2..4)
		if (lx == 3 && lz >= 2 && lz <= 4) {
			return trofferState(seed, x, z, tile.zone()).setValue(Level4AxisBlock.AXIS, Direction.Axis.Z);
		}
		if (lx == 1 && lz == 5 && Math.floorMod(Level4Layout.hash(seed, x, z, 461), 4) == 0) {
			return Level4Blocks.OFFICE_CEILING_VENT.defaultBlockState();
		}
		return Level4Blocks.OFFICE_ACOUSTIC_CEILING.defaultBlockState();
	}

	private static BlockState trofferState(long seed, int x, int z, ZoneKind zone) {
		int cellX = Math.floorDiv(x, Level4Plan.CELL);
		int cellZ = Math.floorDiv(z, Level4Plan.CELL);
		int r = Math.floorMod(Level4Layout.hash(seed, cellX, cellZ, 462), 100);
		if (zone == ZoneKind.DARK_WING) {
			if (r < 68) return Level4Blocks.OFFICE_TROFFER_BROKEN.defaultBlockState();
			if (r < 88) return Level4Blocks.OFFICE_TROFFER_FLICKER.defaultBlockState();
			return Level4Blocks.OFFICE_TROFFER_DIM.defaultBlockState();
		}
		if (zone == ZoneKind.OMEGA_BASE) {
			return Level4Blocks.OFFICE_TROFFER_LIT.defaultBlockState();
		}
		if (r < 64) return Level4Blocks.OFFICE_TROFFER_LIT.defaultBlockState();
		if (r < 78) return Level4Blocks.OFFICE_TROFFER_DIM.defaultBlockState();
		if (r < 88) return Level4Blocks.OFFICE_TROFFER_FLICKER.defaultBlockState();
		return Level4Blocks.OFFICE_TROFFER_BROKEN.defaultBlockState();
	}

	private static BlockState columnBlock(Tile tile, int h) {
		Direction dir = dirFrom(tile.dir());
		return switch (tile.kind()) {
			case AIR -> Blocks.AIR.defaultBlockState();
			case ATRIUM_ARCH -> h <= Level4Layout.OPEN_HEIGHT
					? Blocks.AIR.defaultBlockState()
					: Level4Blocks.OFFICE_DRYWALL_UPPER.defaultBlockState();
			case PILLAR -> (h == 1 ? Level4Blocks.OFFICE_PILLAR : Level4Blocks.OFFICE_PILLAR_UPPER).defaultBlockState();
			case DRYWALL -> (h == 1 ? Level4Blocks.OFFICE_DRYWALL : Level4Blocks.OFFICE_DRYWALL_UPPER).defaultBlockState();
			case GLASS_WALL -> (h <= 3 ? Level4Blocks.OFFICE_GLASS_WALL : Level4Blocks.OFFICE_DRYWALL_UPPER).defaultBlockState();
			case OMEGA_WALL -> Level4Blocks.OMEGA_WALL.defaultBlockState();
			case OMEGA_SIGN -> (h == 2 ? Level4Blocks.OMEGA_SIGN : Level4Blocks.OMEGA_WALL).defaultBlockState();
			case WINDOW_BLACKED -> windowColumn(Level4Blocks.OFFICE_BLACKED_WINDOW, h, dir);
			case WINDOW_TRAP -> windowColumn(Level4Blocks.OFFICE_TRAP_WINDOW, h, dir);
			case WINDOW_RAIN -> windowColumn(Level4Blocks.OFFICE_RAIN_WINDOW, h, dir);
			case FIRE_DOOR -> {
				if (h == 1 || h == 2) {
					yield Level4Blocks.OFFICE_FIRE_DOOR.defaultBlockState()
							.setValue(DoorBlock.FACING, dir)
							.setValue(DoorBlock.HALF, h == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
							.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
							.setValue(DoorBlock.OPEN, false)
							.setValue(DoorBlock.POWERED, false);
				}
				if (h == 3 && tile.exitSign()) {
					yield Level4Blocks.OFFICE_EXIT_SIGN.defaultBlockState().setValue(Level4FacingBlock.FACING, dir);
				}
				yield Level4Blocks.OFFICE_DRYWALL_UPPER.defaultBlockState();
			}
			case EXIT_DOOR -> {
				if (h == 1 || h == 2) {
					yield exitBlock(tile.exit()).defaultBlockState()
							.setValue(Level4ExitBlock.PART, h - 1)
							.setValue(Level4ExitBlock.FACING, dir);
				}
				yield (tile.zone() == ZoneKind.OMEGA_BASE ? Level4Blocks.OMEGA_WALL : Level4Blocks.OFFICE_DRYWALL_UPPER).defaultBlockState();
			}
			case EXIT_HATCH -> Blocks.AIR.defaultBlockState();
			case WATER_COOLER -> h == 1
					? Level4Blocks.OFFICE_WATER_COOLER.defaultBlockState().setValue(Level4WaterSourceBlock.FACING, dir).setValue(Level4WaterSourceBlock.BOTTLES, 2)
					: Blocks.AIR.defaultBlockState();
			case FOUNTAIN_BASIN -> h == 1 ? Level4Blocks.ALMOND_FOUNTAIN_BASIN.defaultBlockState() : Blocks.AIR.defaultBlockState();
			case FOUNTAIN_POOL -> h == 1
					? Level4Blocks.ALMOND_FOUNTAIN_POOL.defaultBlockState().setValue(Level4WaterSourceBlock.BOTTLES, 2)
					: Blocks.AIR.defaultBlockState();
			case VENDING -> h == 1
					? Level4Blocks.OFFICE_VENDING.defaultBlockState().setValue(Level4FacingBlock.FACING, dir)
					: Blocks.AIR.defaultBlockState();
			case OMEGA_COT -> h == 1
					? Level4Blocks.OMEGA_COT.defaultBlockState().setValue(Level4FacingBlock.FACING, dir)
					: Blocks.AIR.defaultBlockState();
			case OMEGA_LOCKER -> h == 1
					? Level4Blocks.OMEGA_LOCKER.defaultBlockState().setValue(Level4FacingBlock.FACING, dir)
					: Blocks.AIR.defaultBlockState();
			case OMEGA_BARRICADE -> h == 1
					? Level4Blocks.OMEGA_BARRICADE.defaultBlockState().setValue(Level4FacingBlock.FACING, dir)
					: Blocks.AIR.defaultBlockState();
			case AMOR_PLANTER -> {
				if (h == 1) yield Level4Blocks.AMOR_PLANTER.defaultBlockState();
				if (h == 2) yield Level4Blocks.AMOR_MUSHROOM.defaultBlockState();
				yield Blocks.AIR.defaultBlockState();
			}
			case TBD_SHELF -> h == 1
					? Level4Blocks.TBD_CURIO_SHELF.defaultBlockState().setValue(Level4FacingBlock.FACING, dir)
					: Blocks.AIR.defaultBlockState();
			case BREAKER -> h == 2
					? Level4Blocks.OFFICE_BREAKER.defaultBlockState().setValue(Level4FacingBlock.FACING, dir)
					: Blocks.AIR.defaultBlockState();
			case PHOTOCOPIER -> h == 1
					? Level4Blocks.OFFICE_PHOTOCOPIER.defaultBlockState().setValue(Level4FacingBlock.FACING, dir)
					: Blocks.AIR.defaultBlockState();
			case DESK_PHONE -> h == 1
					? Level4Blocks.OFFICE_DESK_PHONE.defaultBlockState().setValue(Level4FacingBlock.FACING, dir)
					: Blocks.AIR.defaultBlockState();
		};
	}

	private static BlockState windowColumn(Block window, int h, Direction facing) {
		if (h == 1) return Level4Blocks.OFFICE_RADIATOR_WALL.defaultBlockState().setValue(Level4FacingBlock.FACING, facing);
		if (h == 2 || h == 3 || h == 6 || h == 7) return window.defaultBlockState();
		return Level4Blocks.OFFICE_DRYWALL_UPPER.defaultBlockState();
	}

	private static Block exitBlock(Level4Exit exit) {
		return switch (exit) {
			case ELEVATOR_3 -> Level4Blocks.OFFICE_ELEVATOR;
			case OMEGA_STAIRS_5, STAIRS_5 -> Level4Blocks.OFFICE_STAIRWELL_5;
			case OMEGA_STAIRS_6, STAIRS_6 -> Level4Blocks.OFFICE_STAIRWELL_6;
			case BASEMENT_71 -> Level4Blocks.OFFICE_BASEMENT_71;
			case WHITE_DOOR_4P3 -> Level4Blocks.OFFICE_DOOR_4P3;
			case CITY_DOOR_11 -> Level4Blocks.OFFICE_DOOR_11;
			case HATCH_2 -> Level4Blocks.OFFICE_HATCH_2;
			case MIRROR_13 -> Level4Blocks.OFFICE_DOOR_13;
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

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
			StructureManager structureManager, ChunkAccess chunk) {
		long seed = planSeed(randomState);
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
		long seed = planSeed(randomState);
		BlockState[] states = new BlockState[HEIGHT];
		for (int y = 0; y < HEIGHT; y++) states[y] = blockAt(seed, x, y, z);
		return new NoiseColumn(0, states);
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
