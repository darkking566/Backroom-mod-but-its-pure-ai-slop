package dev.noclip.backrooms.level0p1;

import com.mojang.serialization.MapCodec;
import dev.noclip.backrooms.level0p1.ZenithLayout.Kind;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Turns the Zenith Station plan into blocks. Only the blocks inside the sphere are visited; the rest stays air. */
final class ZenithChunkGenerator extends ChunkGenerator {
	static final MapCodec<ZenithChunkGenerator> CODEC = BiomeSource.CODEC.fieldOf("biome_source")
			.xmap(ZenithChunkGenerator::new, generator -> generator.biomeSource);

	// Must match dimension_type/level_0.1.json
	static final int MIN_Y = -512;
	static final int HEIGHT = 1024;
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private ZenithChunkGenerator(BiomeSource biomeSource) {
		super(biomeSource);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	static long planSeed(RandomState randomState) {
		return randomState.getOrCreateRandomFactory(Level0p1.id("level_0_1_plan")).at(0, 0, 0).nextLong();
	}

	static BlockState blockAt(long seed, int x, int y, int z) {
		int cell = ZenithLayout.cell(seed, x, y, z);
		BlockState state = state(cell);
		Kind kind = ZenithLayout.kind(cell);
		// Rails are fences: they only join up if their arms are set here, since world generation never updates neighbours.
		if (kind == Kind.RAIL || kind == Kind.RAIL_LAMP) {
			state = state.setValue(FenceBlock.NORTH, rail(seed, x, y, z - 1)).setValue(FenceBlock.SOUTH, rail(seed, x, y, z + 1))
					.setValue(FenceBlock.WEST, rail(seed, x - 1, y, z)).setValue(FenceBlock.EAST, rail(seed, x + 1, y, z));
		}
		return state;
	}

	private static boolean rail(long seed, int x, int y, int z) {
		Kind kind = ZenithLayout.kind(ZenithLayout.cell(seed, x, y, z));
		return kind == Kind.RAIL || kind == Kind.RAIL_LAMP;
	}

	// The sea outside is Level 0's own blocks (the lamps still burn), looked up by name so Level 0 keeps them private.
	private static final BlockState SEA_WALL = level0("wallpaper"), SEA_CARPET = level0("carpet"), SEA_LIGHT = level0("fluorescent_light");

	private static BlockState level0(String name) {
		return BuiltInRegistries.BLOCK.getValue(Level0p1.id(name)).defaultBlockState();
	}

	static BlockState state(int cell) {
		int dir = ZenithLayout.dir(cell), extra = ZenithLayout.extra(cell);
		Kind kind = ZenithLayout.kind(cell);
		return switch (kind) {
			case AIR -> AIR;
			case HULL -> ZenithBlocks.HULL_PLATE.defaultBlockState();
			case HULL_SEAM -> ZenithBlocks.HULL_SEAM.defaultBlockState();
			case SKIN_PLATE -> ZenithBlocks.INNER_HULL_PLATE.defaultBlockState();
			case WINDOW -> ZenithBlocks.OBSERVATION_GLASS.defaultBlockState();
			case BULKHEAD -> ZenithBlocks.BULKHEAD.defaultBlockState();
			case FLOOR -> ZenithBlocks.DECK_FLOOR.defaultBlockState();
			case FLOOR_CORRIDOR -> ZenithBlocks.CORRIDOR_FLOOR.defaultBlockState();
			case FLOOR_STRIPE -> ZenithBlocks.CORRIDOR_FLOOR_STRIPE.defaultBlockState();
			case FLOOR_QUARTERS -> ZenithBlocks.QUARTERS_CARPET.defaultBlockState();
			case FLOOR_KITCHEN -> ZenithBlocks.KITCHEN_TILE.defaultBlockState();
			case FLOOR_GYM -> ZenithBlocks.GYM_MAT.defaultBlockState();
			case FLOOR_GARDEN -> ZenithBlocks.GARDEN_FLOOR.defaultBlockState();
			case FLOOR_WORN -> ZenithBlocks.WORN_FLOOR.defaultBlockState();
			case FLOOR_HAZARD -> ZenithBlocks.HAZARD_FLOOR.defaultBlockState();
			case CEILING -> ZenithBlocks.DECK_CEILING.defaultBlockState();
			case LIGHT -> ZenithBlocks.CEILING_LIGHT.defaultBlockState();
			case DEAD_LIGHT -> ZenithBlocks.DEAD_CEILING_LIGHT.defaultBlockState();
			case WALL -> ZenithBlocks.WALL_PANEL.defaultBlockState();
			case WALL_QUARTERS -> ZenithBlocks.QUARTERS_WALL.defaultBlockState();
			case WALL_KITCHEN -> ZenithBlocks.KITCHEN_WALL.defaultBlockState();
			case WALL_GYM -> ZenithBlocks.GYM_WALL.defaultBlockState();
			case WALL_DAMAGED -> ZenithBlocks.DAMAGED_WALL.defaultBlockState();
			case CEILING_FROST -> ZenithBlocks.FROST_CEILING.defaultBlockState();
			case CEILING_SCORCH -> ZenithBlocks.SCORCHED_CEILING.defaultBlockState();
			case FLOOR_FROST -> ZenithBlocks.FROST_FLOOR.defaultBlockState();
			case FLOOR_SCORCH -> ZenithBlocks.SCORCHED_FLOOR.defaultBlockState();
			case WALL_FROST -> ZenithBlocks.FROST_WALL.defaultBlockState();
			case WALL_SCORCH -> ZenithBlocks.SCORCHED_WALL.defaultBlockState();
			case DOOR -> door(dir, extra);
			case STAIR -> ZenithBlocks.STATION_STAIRS.defaultBlockState().setValue(StairBlock.FACING, direction(dir));
			case STAIR_CORE -> (extra == 1 ? ZenithBlocks.DEAD_STAIR_CORE : ZenithBlocks.STAIR_CORE).defaultBlockState();
			case RAIL -> ZenithBlocks.SAFETY_RAIL.defaultBlockState();
			case RAIL_LAMP -> ZenithBlocks.RAIL_LAMP.defaultBlockState();
			case BED -> facing(ZenithBlocks.CREW_BED, dir);
			case LOCKER -> facing(ZenithBlocks.CREW_LOCKER, dir);
			case COUNTER -> facing(ZenithBlocks.KITCHEN_COUNTER, dir);
			case STOVE -> facing(ZenithBlocks.STOVE, dir);
			case TREADMILL -> facing(ZenithBlocks.TREADMILL_BLOCK, dir);
			case WEIGHTS -> facing(ZenithBlocks.WEIGHT_RACK, dir);
			case PLANTER -> ZenithBlocks.HYDROPONIC_PLANTER.defaultBlockState();
			case CONSOLE -> facing(ZenithBlocks.CONSOLE, dir);
			case BROKEN_CONSOLE -> facing(ZenithBlocks.BROKEN_CONSOLE, dir);
			case SERVER -> facing(ZenithBlocks.SERVER_RACK, dir);
			case CRATE -> ZenithBlocks.SUPPLY_CRATE.defaultBlockState();
			case CABLES -> ZenithBlocks.HANGING_CABLES.defaultBlockState();
			case TABLE -> ZenithBlocks.CREW_TABLE.defaultBlockState();
			case DESK -> facing(ZenithBlocks.CREW_DESK, dir);
			case WARDROBE -> facing(ZenithBlocks.OPEN_WARDROBE, dir);
			case BUNK_BED -> facing(ZenithBlocks.BUNK_BED, dir);
			case FRAME -> facing(ZenithBlocks.WALL_FRAME, dir);
			case ITEMS -> ZenithBlocks.PERSONAL_ITEMS.defaultBlockState();
			case SINK -> facing(ZenithBlocks.KITCHEN_SINK, dir);
			case FRIDGE -> facing(ZenithBlocks.FRIDGE, dir);
			case POTS -> facing(ZenithBlocks.HANGING_POTS, dir);
			case DISHES -> ZenithBlocks.DISH_STACK.defaultBlockState();
			case BENCH_PRESS -> facing(ZenithBlocks.BENCH_PRESS, dir);
			case BAG -> ZenithBlocks.PUNCHING_BAG.defaultBlockState();
			case MIRROR -> facing(ZenithBlocks.WALL_MIRROR, dir);
			case DISPENSER -> facing(ZenithBlocks.WATER_DISPENSER, dir);
			case MATS -> ZenithBlocks.MAT_STACK.defaultBlockState();
			case RACK -> ZenithBlocks.HYDROPONIC_RACK.defaultBlockState();
			case GROW_LIGHT -> ZenithBlocks.GROW_LIGHT.defaultBlockState();
			case TANK -> facing(ZenithBlocks.WATER_TANK, dir);
			case SPEAKER -> ZenithBlocks.CEILING_SPEAKER.defaultBlockState();
			case ICICLES -> ZenithBlocks.ICICLES.defaultBlockState();
			case DRIFT -> ZenithBlocks.ICE_DRIFT.defaultBlockState();
			case SLAG -> ZenithBlocks.SLAG_PILE.defaultBlockState();
			case VENT -> ZenithBlocks.HEAT_VENT.defaultBlockState();
			case SCREEN -> facing(ZenithBlocks.COUNTDOWN_SCREEN, dir);
			case COIL -> ZenithBlocks.BRIDGE_COIL.defaultBlockState();
			case NOTES -> ZenithBlocks.RESEARCH_NOTES.defaultBlockState();
			case COAT -> facing(ZenithBlocks.LAB_COAT, dir);
			case SEA_WALL -> SEA_WALL;
			case SEA_CARPET -> SEA_CARPET;
			case SEA_LIGHT -> SEA_LIGHT;
			case VINES -> ZenithBlocks.HANGING_VINES.defaultBlockState();
			case BENCH -> ZenithBlocks.STATION_BENCH.defaultBlockState();
			case PORTAL -> ZenithBlocks.YELLOW_DOORWAY.defaultBlockState().setValue(ZenithPortalBlock.PART, extra);
			case PORTAL_FRAME -> ZenithBlocks.DOORWAY_FRAME.defaultBlockState();
			case STATIC_TEAR -> ZenithBlocks.STATIC_TEAR.defaultBlockState();
			case LEAVES -> Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
			case LOG -> Blocks.MANGROVE_LOG.defaultBlockState();
			case WATER -> Blocks.WATER.defaultBlockState();
			case ORB -> ZenithBlocks.GLOW_ORB.defaultBlockState();
			case CATWALK -> ZenithBlocks.CATWALK_GRATING.defaultBlockState();
			case TRUSS -> ZenithBlocks.TRUSS.defaultBlockState();
			case TETHER -> ZenithBlocks.TETHER_CABLE.defaultBlockState();
			case GIRDER -> ZenithBlocks.GIRDER.defaultBlockState();
			case GIRDER_LIGHT -> ZenithBlocks.GIRDER_LAMP.defaultBlockState();
			case SINGULARITY_CORE -> ZenithBlocks.SINGULARITY_CORE.defaultBlockState();
			case SINGULARITY_SHELL -> ZenithBlocks.SINGULARITY_SHELL.defaultBlockState();
			case PLASMA -> ZenithBlocks.PLASMA.defaultBlockState().setValue(ZenithPlasmaBlock.HEAT, extra);
		};
	}

	/** The layout's directions (north, east, south, west) as Minecraft's. */
	private static Direction direction(int dir) {
		return switch (dir) {
			case 0 -> Direction.NORTH;
			case 1 -> Direction.EAST;
			case 2 -> Direction.SOUTH;
			default -> Direction.WEST;
		};
	}

	private static BlockState facing(Block block, int dir) {
		return block.defaultBlockState().setValue(ZenithFacingBlock.FACING, direction(dir));
	}

	private static BlockState door(int dir, int extra) {
		return ZenithBlocks.STATION_DOOR_BLOCK.defaultBlockState().setValue(DoorBlock.FACING, direction(dir))
				.setValue(DoorBlock.HALF, (extra & 2) == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
				.setValue(DoorBlock.HINGE, (extra & 1) == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT)
				.setValue(DoorBlock.OPEN, false).setValue(DoorBlock.POWERED, false);
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
			StructureManager structureManager, ChunkAccess chunk) {
		int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
		if (ZenithLayout.emptyBox(minX, minX + 15, minZ, minZ + 15)) return CompletableFuture.completedFuture(chunk);
		long seed = planSeed(randomState);
		Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
		Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		long outer = (long) (ZenithLayout.RO + ZenithLayout.SEA) * (ZenithLayout.RO + ZenithLayout.SEA);
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int wx = minX + x, wz = minZ + z;
				long left = outer - (long) wx * wx - (long) wz * wz;
				if (left < 0) continue;
				int reach = Math.min((int) Math.sqrt(left), MIN_Y + HEIGHT - 1);
				for (int y = Math.max(-reach, MIN_Y); y <= reach; y++) {
					BlockState state = blockAt(seed, wx, y, wz);
					if (state == AIR) continue;
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
		BlockState[] column = new BlockState[HEIGHT];
		for (int i = 0; i < HEIGHT; i++) column[i] = blockAt(seed, x, MIN_Y + i, z);
		return new NoiseColumn(MIN_Y, column);
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
		long seed = planSeed(randomState);
		for (int y = MIN_Y + HEIGHT - 1; y >= MIN_Y; y--) {
			if (blockAt(seed, x, y, z) != AIR) return y + 1;
		}
		return MIN_Y;
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
		return MIN_Y;
	}
}
