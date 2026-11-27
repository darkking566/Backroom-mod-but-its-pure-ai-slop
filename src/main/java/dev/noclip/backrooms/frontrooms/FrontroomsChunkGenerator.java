package dev.noclip.backrooms.frontrooms;

import com.mojang.serialization.MapCodec;
import dev.noclip.backrooms.frontrooms.InstituteLayout.Cell;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.level0p1.ZenithBlocks;
import dev.noclip.backrooms.props.BackroomsProps;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
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
 * The Frontrooms: the M.E.G. institute (InstituteLayout) and its research grounds (FrontroomsCampus) in the middle of a
 * patch of ordinary country (FrontroomsTerrain), with a round quarantine wall around it and nothing beyond. Trees,
 * flowers, ore and animals come from the biome, as in the Overworld; none grow on the institute or the grounds' works.
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
		FrontroomsTown.Piece piece = FrontroomsTown.piece(x, y, z);
		if (piece != null) return town(piece);
		FrontroomsCampus.Part part = FrontroomsCampus.part(x, y, z);
		if (part != FrontroomsCampus.Part.NONE) return campus(part);
		if (FrontroomsTerrain.wall(x, z)) return wall(x, y, z);
		Cell cell = InstituteLayout.cell(x, y, z);
		if (cell.kind() != InstituteLayout.Kind.NONE) return building(cell, x, y, z);
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

	/** What the grounds' parts are made of. */
	private static BlockState campus(FrontroomsCampus.Part part) {
		return switch (part) {
			case NONE, AIR -> AIR;
			case ASPHALT -> FrontroomsBlocks.CAMPUS_ASPHALT.defaultBlockState();
			case LINE -> FrontroomsBlocks.CAMPUS_LINE.defaultBlockState();
			case SIDEWALK -> FrontroomsBlocks.CAMPUS_SIDEWALK.defaultBlockState();
			case PRECAST -> FrontroomsBlocks.PRECAST_PANEL.defaultBlockState();
			case RIBBON -> FrontroomsBlocks.RIBBON_WINDOW.defaultBlockState();
			case STEEL -> FrontroomsBlocks.STEEL_FRAME.defaultBlockState();
			case SIGN -> FrontroomsBlocks.FACADE_SIGN.defaultBlockState();
			case BAND -> FrontroomsBlocks.QUARANTINE_BAND.defaultBlockState();
			case CONCRETE -> FrontroomsBlocks.QUARANTINE_WALL.defaultBlockState();
			case WATER -> Blocks.WATER.defaultBlockState();
			case LIGHT -> FrontroomsBlocks.INSTITUTE_LIGHT.defaultBlockState();
			case SODIUM -> FrontroomsBlocks.SODIUM_LAMP.defaultBlockState();
			case FLOODLIGHT -> FrontroomsBlocks.QUARANTINE_FLOODLIGHT.defaultBlockState();
			case RED_LIGHT -> FrontroomsBlocks.EMERGENCY_LIGHT.defaultBlockState();
			case BARS -> Blocks.IRON_BARS.defaultBlockState();
			case POLE -> FrontroomsBlocks.STEEL_POLE.defaultBlockState();
		};
	}

	/** What the town's pieces are made of. */
	private static BlockState town(FrontroomsTown.Piece piece) {
		Direction facing = Direction.from2DDataValue(piece.facing());
		return switch (piece.kind()) {
			case AIR -> AIR;
			case SIDING -> (piece.variant() == 0 ? FrontroomsBlocks.TOWN_SIDING_GRAY : piece.variant() == 1 ? FrontroomsBlocks.TOWN_SIDING_BLUE
					: FrontroomsBlocks.TOWN_SIDING_CREAM).defaultBlockState();
			case TRIM -> FrontroomsBlocks.TOWN_TRIM.defaultBlockState();
			case FOUNDATION -> FrontroomsBlocks.QUARANTINE_WALL.defaultBlockState();
			case CARPET -> Level0.CARPET;
			case WALLPAPER -> Level0.WALLPAPER;
			case PLASTER -> FrontroomsBlocks.INSTITUTE_WALL.defaultBlockState();
			case WINDOW -> FrontroomsBlocks.TOWN_WINDOW.defaultBlockState();
			case DOOR -> Blocks.BIRCH_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing)
					.setValue(DoorBlock.HALF, piece.variant() == 1 ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER)
					.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, false);
			case GARAGE -> FrontroomsBlocks.TOWN_GARAGE_DOOR.defaultBlockState();
			case GARAGE_TOP -> FrontroomsBlocks.TOWN_GARAGE_DOOR_TOP.defaultBlockState();
			case SHINGLES -> FrontroomsBlocks.TOWN_SHINGLES.defaultBlockState();
			case ROOF_STAIR -> FrontroomsBlocks.TOWN_SHINGLE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing)
					.setValue(StairBlock.HALF, Half.BOTTOM).setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
			case FLAT_ROOF -> FrontroomsBlocks.INSTITUTE_CONCRETE.defaultBlockState();
			case STORE_WALL -> FrontroomsBlocks.PRECAST_PANEL.defaultBlockState();
			case STORE_FLOOR -> FrontroomsBlocks.INSTITUTE_FLOOR.defaultBlockState();
			case STORE_CEILING -> FrontroomsBlocks.INSTITUTE_CEILING.defaultBlockState();
			case STORE_LIGHT -> FrontroomsBlocks.INSTITUTE_LIGHT.defaultBlockState();
			case COUNTER -> FrontroomsBlocks.INSTITUTE_COUNTER.defaultBlockState();
			case ASPHALT -> FrontroomsBlocks.CAMPUS_ASPHALT.defaultBlockState();
			case SIDEWALK -> FrontroomsBlocks.CAMPUS_SIDEWALK.defaultBlockState();
			case POLE -> FrontroomsBlocks.STEEL_POLE.defaultBlockState();
			case SODIUM -> FrontroomsBlocks.SODIUM_LAMP.defaultBlockState();
			case LOG -> Blocks.OAK_LOG.defaultBlockState();
			case LEAVES -> Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
			case PROP -> {
				FrontroomsTown.Place place = FrontroomsTown.places().get(piece.variant());
				yield BuiltInRegistries.BLOCK.getValue(BackroomsMod.id(place.id())).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
			}
			case PROP_PART -> BackroomsProps.PROP_PART.defaultBlockState();
		};
	}

	/** Level 0's own blocks, for the piece of it in the specimen chamber (looked up by name: they are Level 0's). */
	private static final class Level0 {
		static final BlockState CARPET = block("carpet"), WALLPAPER = block("wallpaper"), CEILING = block("ceiling_tile"),
				LIGHT = block("fluorescent_light");

		private static BlockState block(String name) {
			return BuiltInRegistries.BLOCK.getValue(BackroomsMod.id(name)).defaultBlockState();
		}
	}

	/** A railing joins the railings and walls beside it. */
	private static BlockState rail(int x, int y, int z) {
		BlockState state = Blocks.IRON_BARS.defaultBlockState();
		for (Direction side : Direction.Plane.HORIZONTAL) {
			InstituteLayout.Kind next = InstituteLayout.cell(x + side.getStepX(), y, z + side.getStepZ()).kind();
			boolean joins = switch (next) {
				case RAIL, WALL, PILLAR, WAINSCOT, MINT_WALL, MINT_WAINSCOT, GLASS, OBSERVATION -> true;
				default -> false;
			};
			state = state.setValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(side), joins);
		}
		return state;
	}

	private static BlockState building(Cell cell, int x, int y, int z) {
		Direction facing = Direction.from2DDataValue(cell.facing());
		return switch (cell.kind()) {
			case NONE, AIR -> AIR;
			case SOLID, ROOF -> FrontroomsBlocks.INSTITUTE_CONCRETE.defaultBlockState();
			case WALL, PILLAR -> FrontroomsBlocks.INSTITUTE_WALL.defaultBlockState();
			case WAINSCOT -> FrontroomsBlocks.INSTITUTE_WAINSCOT.defaultBlockState();
			case MINT_WALL -> FrontroomsBlocks.MINT_WALL.defaultBlockState();
			case MINT_WAINSCOT -> FrontroomsBlocks.MINT_WAINSCOT.defaultBlockState();
			case GLASS -> Blocks.GLASS.defaultBlockState();
			case OBSERVATION -> FrontroomsBlocks.OBSERVATION_WINDOW.defaultBlockState();
			case LOBBY_FLOOR -> FrontroomsBlocks.LOBBY_TERRAZZO.defaultBlockState();
			case RAIL -> rail(x, y, z);
			case PRECAST -> FrontroomsBlocks.PRECAST_PANEL.defaultBlockState();
			case RIBBON -> FrontroomsBlocks.RIBBON_WINDOW.defaultBlockState();
			case STEEL -> FrontroomsBlocks.STEEL_FRAME.defaultBlockState();
			case SIGN -> FrontroomsBlocks.FACADE_SIGN.defaultBlockState();
			case CONTAINMENT -> FrontroomsBlocks.CONTAINMENT_GLASS.defaultBlockState();
			case BAND -> FrontroomsBlocks.QUARANTINE_BAND.defaultBlockState();
			case LEVEL0_CARPET -> Level0.CARPET;
			case LEVEL0_WALL -> Level0.WALLPAPER;
			case LEVEL0_CEILING -> Level0.CEILING;
			case LEVEL0_LIGHT -> Level0.LIGHT;
			case BARS -> Blocks.IRON_BARS.defaultBlockState();
			case COIL -> ZenithBlocks.BRIDGE_COIL.defaultBlockState();
			case ASPHALT -> FrontroomsBlocks.CAMPUS_ASPHALT.defaultBlockState();
			case PAINT -> FrontroomsBlocks.INSTITUTE_WALL.defaultBlockState();
			case PROP -> {
				InstituteFurnishing.Place place = InstituteFurnishing.places().get(cell.requires());
				yield InstituteProps.block(place.type().id()).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
			}
			case PROP_PART -> BackroomsProps.PROP_PART.defaultBlockState();
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
			case STAIR -> Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
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
			case 'V' -> BackroomsProps.WORKSHOP_BENCH;
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
				int top = Math.min(HEIGHT - 1, columnTop(wx, wz));
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

	/** The highest block that may be anything but air in a column. */
	private static int columnTop(int x, int z) {
		int top = Math.max(FrontroomsTerrain.WALL_TOP + 2, Math.max(FrontroomsCampus.top(x, z), FrontroomsTown.top(x, z)));
		return InstituteLayout.inside(x, z) ? Math.max(top, InstituteLayout.TOP) : top;
	}

	@Override
	public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
		ChunkPos pos = chunk.getPos();
		int x0 = pos.getMinBlockX(), z0 = pos.getMinBlockZ(), x1 = pos.getMaxBlockX(), z1 = pos.getMaxBlockZ();
		boolean nearBuilding = x1 >= InstituteLayout.MIN_X - CLEAR && x0 <= InstituteLayout.MAX_X + CLEAR
				&& z1 >= InstituteLayout.MIN_Z - CLEAR && z0 <= InstituteLayout.MAX_Z + CLEAR || FrontroomsCampus.busy(x0, z0, x1, z1)
				|| FrontroomsTown.busy(x0, z0, x1, z1);
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
		for (int y = Math.max(FrontroomsCampus.top(x, z), FrontroomsTown.top(x, z)); y > FrontroomsTerrain.GROUND; y--) {
			if (FrontroomsCampus.structure(x, y, z) || FrontroomsTown.structure(x, y, z)) return y + 1;
		}
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
