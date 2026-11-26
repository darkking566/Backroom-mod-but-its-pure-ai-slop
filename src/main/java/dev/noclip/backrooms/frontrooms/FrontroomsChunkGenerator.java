package dev.noclip.backrooms.frontrooms;

import com.mojang.serialization.MapCodec;
import dev.noclip.backrooms.frontrooms.InstituteLayout.Cell;
import dev.noclip.backrooms.props.BackroomsProps;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * The Frontrooms: the M.E.G. institute (InstituteLayout) in the middle of a patch of ordinary country (FrontroomsTerrain)
 * with a round quarantine wall around it and nothing beyond. Trees, flowers, ore and animals come from the biome, as in
 * the Overworld; none grow on the institute or its forecourt.
 */
final class FrontroomsChunkGenerator extends ChunkGenerator {
	static final MapCodec<FrontroomsChunkGenerator> CODEC = BiomeSource.CODEC.fieldOf("biome_source")
			.xmap(FrontroomsChunkGenerator::new, generator -> generator.biomeSource);
	/** Must match dimension_type/frontrooms.json. */
	static final int HEIGHT = 256;
	/** Around the building the biome's trees and flowers are left out. */
	private static final int CLEAR = 10;

	private FrontroomsChunkGenerator(BiomeSource biomeSource) {
		super(biomeSource);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	static long seed(RandomState randomState) {
		return randomState.getOrCreateRandomFactory(Frontrooms.id("frontrooms_terrain")).at(0, 0, 0).nextLong();
	}

	static BlockState blockAt(long seed, int x, int y, int z) {
		if (FrontroomsTerrain.beyond(x, z)) return AIR;
		if (FrontroomsTerrain.wall(x, z)) return wall(x, y, z);
		Cell cell = InstituteLayout.cell(x, y, z);
		if (cell.kind() != InstituteLayout.Kind.NONE) return building(cell);
		int h = FrontroomsTerrain.height(seed, x, z);
		if (y == 0) return Blocks.BEDROCK.defaultBlockState();
		boolean shore = h <= FrontroomsTerrain.WATER + 1;
		if (y < h - 3) return Blocks.STONE.defaultBlockState();
		if (y < h) return shore ? Blocks.SAND.defaultBlockState() : Blocks.DIRT.defaultBlockState();
		if (y == h) {
			if (h <= FrontroomsTerrain.WATER - 1) return h % 2 == 0 ? Blocks.CLAY.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
			return shore ? Blocks.SAND.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
		}
		if (y <= FrontroomsTerrain.WATER) return Blocks.WATER.defaultBlockState();
		if (y == h + 2) {
			BlockState sign = sign(x, z);
			if (sign != null) return sign;
		}
		return AIR;
	}

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	/** The quarantine wall: concrete with a hazard band at head height, a live fence on top, floodlights now and then. */
	private static BlockState wall(int x, int y, int z) {
		if (y == 0) return Blocks.BEDROCK.defaultBlockState();
		if (y <= FrontroomsTerrain.WALL_TOP) {
			return y == FrontroomsTerrain.GROUND + 4 || y == FrontroomsTerrain.GROUND + 24
					? FrontroomsBlocks.QUARANTINE_BAND.defaultBlockState() : FrontroomsBlocks.QUARANTINE_WALL.defaultBlockState();
		}
		if (y <= FrontroomsTerrain.WALL_TOP + 2) {
			if (y == FrontroomsTerrain.WALL_TOP + 1 && FrontroomsTerrain.hash(x, z, 19) == 0) return FrontroomsBlocks.QUARANTINE_FLOODLIGHT.defaultBlockState();
			return FrontroomsBlocks.ELECTRIC_FENCE.defaultBlockState();
		}
		return AIR;
	}

	/** Now and then a warning sign on the inside of the wall, looking into the grounds. */
	private static BlockState sign(int x, int z) {
		double r = FrontroomsTerrain.radius(x, z);
		if (r < FrontroomsTerrain.WALL_IN - 1 || FrontroomsTerrain.hash(x, z, 9) != 0) return null;
		Direction facing = Math.abs(x) > Math.abs(z) ? (x > 0 ? Direction.WEST : Direction.EAST) : (z > 0 ? Direction.NORTH : Direction.SOUTH);
		Direction back = facing.getOpposite();
		if (!FrontroomsTerrain.wall(x + back.getStepX(), z + back.getStepZ())) return null;
		return FrontroomsBlocks.WARNING_SIGN.defaultBlockState().setValue(InstituteBlocks.Facing.FACING, facing);
	}

	private static BlockState building(Cell cell) {
		Direction facing = Direction.from2DDataValue(cell.facing());
		return switch (cell.kind()) {
			case NONE, AIR -> AIR;
			case SOLID, ROOF -> FrontroomsBlocks.INSTITUTE_CONCRETE.defaultBlockState();
			case WALL, PILLAR -> FrontroomsBlocks.INSTITUTE_WALL.defaultBlockState();
			case WAINSCOT -> FrontroomsBlocks.INSTITUTE_WAINSCOT.defaultBlockState();
			case GLASS -> Blocks.GLASS.defaultBlockState();
			case FLOOR -> FrontroomsBlocks.INSTITUTE_FLOOR.defaultBlockState();
			case WORK_FLOOR -> FrontroomsBlocks.INSTITUTE_WORK_FLOOR.defaultBlockState();
			case CEILING -> FrontroomsBlocks.INSTITUTE_CEILING.defaultBlockState();
			case LIGHT -> FrontroomsBlocks.INSTITUTE_LIGHT.defaultBlockState();
			case EMERGENCY_LIGHT -> FrontroomsBlocks.EMERGENCY_LIGHT.defaultBlockState();
			case DOOR -> FrontroomsBlocks.BLAST_DOOR.defaultBlockState()
					.setValue(BlastDoorBlock.HALF, cell.upper() ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER)
					.setValue(BlastDoorBlock.AXIS, cell.facing() == 0 ? Direction.Axis.X : Direction.Axis.Z)
					.setValue(BlastDoorBlock.REQUIRES, cell.requires());
			case DOOR_LAMP -> FrontroomsBlocks.DOOR_LAMP.defaultBlockState().setValue(DoorLampBlock.REQUIRES, cell.requires());
			case STAIR -> Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
			case LADDER -> Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
			case HATCH -> FrontroomsBlocks.ARCHIVE_HATCH.defaultBlockState();
			case COUNTER -> FrontroomsBlocks.INSTITUTE_COUNTER.defaultBlockState();
			case LOCKER -> FrontroomsBlocks.MEG_LOCKER.defaultBlockState().setValue(InstituteBlocks.Facing.FACING, facing);
			case WEAPON_LOCKER -> FrontroomsBlocks.WEAPON_LOCKER.defaultBlockState().setValue(InstituteBlocks.Facing.FACING, facing);
			case BED_HEAD, BED_FOOT -> Blocks.BED.white().defaultBlockState().setValue(BedBlock.FACING, facing)
					.setValue(BedBlock.PART, cell.kind() == InstituteLayout.Kind.BED_HEAD ? BedPart.HEAD : BedPart.FOOT);
			case STATION -> station(cell.code(), facing);
			case PAD -> FrontroomsBlocks.TRANSPORT_PAD.defaultBlockState();
			case SHELF -> Blocks.BOOKSHELF.defaultBlockState();
			case FILE -> FrontroomsBlocks.ARCHIVE_FILE.defaultBlockState();
		};
	}

	private static BlockState station(char code, Direction facing) {
		Block block = switch (code) {
			case 'K' -> BackroomsProps.SALVAGE_BENCH;
			case 'W' -> BackroomsProps.WORKSHOP_BENCH;
			case 'A' -> BackroomsProps.ALMOND_DISTILLER;
			case 'R' -> BackroomsProps.ANOMALY_ALTAR;
			case 'X' -> BackroomsProps.SCRAP_RECYCLER;
			case 'N' -> BackroomsProps.SPIDERWEB_CRAFTING_TABLE;
			case 'Y' -> BackroomsProps.ANOMALY_DISTILLERY_BARREL;
			case 'U' -> BackroomsProps.ANOMALY_RESEARCH_TERMINAL;
			case 'Z' -> BackroomsProps.CRYSTAL_REPLICATOR;
			default -> Blocks.CRAFTING_TABLE;
		};
		BlockState state = block.defaultBlockState();
		return state.hasProperty(HorizontalDirectionalBlock.FACING) ? state.setValue(HorizontalDirectionalBlock.FACING, facing) : state;
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
		long seed = seed(randomState);
		Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
		Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int wx = minX + x, wz = minZ + z;
				if (FrontroomsTerrain.beyond(wx, wz)) continue;
				int top = Math.min(HEIGHT - 1, Math.max(InstituteLayout.TOP, FrontroomsTerrain.WALL_TOP + 2));
				for (int y = 0; y <= top; y++) {
					BlockState state = blockAt(seed, wx, y, wz);
					if (state.isAir()) continue;
					chunk.setBlockState(pos.set(x, y, z), state);
					oceanFloor.update(x, y, z, state);
					surface.update(x, y, z, state);
				}
			}
		}
		return CompletableFuture.completedFuture(chunk);
	}

	@Override
	public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
		ChunkPos pos = chunk.getPos();
		int x0 = pos.getMinBlockX(), z0 = pos.getMinBlockZ(), x1 = pos.getMaxBlockX(), z1 = pos.getMaxBlockZ();
		boolean nearBuilding = x1 >= InstituteLayout.MIN_X - CLEAR && x0 <= InstituteLayout.MAX_X + CLEAR
				&& z1 >= InstituteLayout.MIN_Z - CLEAR && z0 <= InstituteLayout.YARD_MAX_Z + CLEAR;
		boolean allBeyond = FrontroomsTerrain.beyond(x0, z0) && FrontroomsTerrain.beyond(x1, z0) && FrontroomsTerrain.beyond(x0, z1)
				&& FrontroomsTerrain.beyond(x1, z1) && Math.min(Math.abs(x0), Math.abs(x1)) > 0 && Math.min(Math.abs(z0), Math.abs(z1)) > 0;
		if (nearBuilding || allBeyond) return;
		super.applyBiomeDecoration(level, chunk, structureManager);
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
		long seed = seed(randomState);
		BlockState[] column = new BlockState[HEIGHT];
		for (int y = 0; y < HEIGHT; y++) column[y] = blockAt(seed, x, y, z);
		return new NoiseColumn(0, column);
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
		if (FrontroomsTerrain.beyond(x, z)) return 0;
		if (FrontroomsTerrain.wall(x, z)) return FrontroomsTerrain.WALL_TOP + 3;
		if (InstituteLayout.inside(x, z)) return InstituteLayout.ROOF + 1;
		int h = FrontroomsTerrain.height(seed(randomState), x, z);
		boolean water = type == Heightmap.Types.WORLD_SURFACE_WG || type == Heightmap.Types.WORLD_SURFACE || type == Heightmap.Types.MOTION_BLOCKING;
		return (water ? Math.max(h, FrontroomsTerrain.WATER) : h) + 1;
	}

	@Override
	public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {
	}

	@Override
	public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
	}

	/** Animals at first, as the Overworld has them. */
	@Override
	public void spawnOriginalMobs(WorldGenRegion region) {
		ChunkPos pos = region.getCenter();
		if (!FrontroomsTerrain.grounds(pos.getMiddleBlockX(), pos.getMiddleBlockZ())) return;
		Holder<Biome> biome = region.getBiome(pos.getWorldPosition().atY(region.getMaxY()));
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
		random.setDecorationSeed(region.getSeed(), pos.getMinBlockX(), pos.getMinBlockZ());
		NaturalSpawner.spawnMobsForChunkGeneration(region, biome, pos, random);
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
		return FrontroomsTerrain.WATER;
	}
}
