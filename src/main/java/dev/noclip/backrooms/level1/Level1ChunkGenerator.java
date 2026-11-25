package dev.noclip.backrooms.level1;

import com.mojang.serialization.MapCodec;
import dev.noclip.backrooms.level1.Level1Layout.Ceiling;
import dev.noclip.backrooms.level1.Level1Layout.Kind;
import dev.noclip.backrooms.level1.Level1Layout.Tile;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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

/**
 * Turns the Level 1 floor plan into blocks. Halls have a five-block ceiling with beams, lights and pipes;
 * corridors have a three-block ceiling with bare bulbs, and are walled off by full-height white walls.
 */
final class Level1ChunkGenerator extends ChunkGenerator {
	static final MapCodec<Level1ChunkGenerator> CODEC = BiomeSource.CODEC.fieldOf("biome_source")
			.xmap(Level1ChunkGenerator::new, generator -> generator.biomeSource);

	static final int FLOOR_Y = 32;
	/** Corridors and doorways stop under this ceiling. */
	static final int LOW_CEILING_Y = 36;
	static final int CEILING_Y = 38;
	// Must match dimension_type/level_1.json
	static final int HEIGHT = 96;
	private static final int HALL_TOP = CEILING_Y - 1, LOW_TOP = LOW_CEILING_Y - 1;

	private Level1ChunkGenerator(BiomeSource biomeSource) {
		super(biomeSource);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	// RandomState is derived from the world seed, which fillFromNoise is not given directly.
	static long planSeed(RandomState randomState) {
		return randomState.getOrCreateRandomFactory(Level1.id("level_1_plan")).at(0, 0, 0).nextLong();
	}

	static BlockState blockAt(long seed, int x, int y, int z) {
		return block(seed, Level1Layout.tile(seed, x, z), Level1Layout.ceiling(seed, x, z), Level1Layout.exitCell(seed, x, z),
				Level1Layout.propCell(seed, x, z), Level1Layout.basePiece(seed, x, z), x, y, z);
	}

	private static BlockState block(long seed, Tile tile, Ceiling ceiling, HallPlan.Cell exit, HallPlan.PropCell prop,
			BasePlan.Piece piece, int x, int y, int z) {
		BlockState state = plainBlock(seed, tile, ceiling, exit, prop, piece, x, y, z);
		BlockState dressed = Level1Sectors.dress(Level1Layout.sector(seed, x, z), tile, state, seed, x, y, z);
		return Level1SectorArchitecture.apply(seed, tile, dressed, x, y, z);
	}

	private static BlockState plainBlock(long seed, Tile tile, Ceiling ceiling, HallPlan.Cell exit, HallPlan.PropCell prop,
			BasePlan.Piece piece, int x, int y, int z) {
		if (piece != null && tile != Tile.PILLAR) {
			Level1Layout.Base base = Level1Layout.base(seed, Math.floorDiv(x, Level1Layout.DISTRICT), Math.floorDiv(z, Level1Layout.DISTRICT));
			BlockState state = Level1Bases.block(base, piece, y);
			if (state != null) return state;
		}
		if (exit != null) {
			BlockState state = Level1Exits.block(exit, x, y, z);
			if (state != null) return state;
		}
		if (prop != null) {
			BlockState state = Level1Props.block(prop, x, y, z);
			if (state != null) return state;
		}
		// Bedrock under the floor and over the roof, so nothing gets out underneath or lands on top.
		if (y == 0 || y >= HEIGHT - 1) return Blocks.BEDROCK.defaultBlockState();
		BlockState landmark = Level1Landmarks.block(seed, tile, ceiling, x, y, z);
		if (landmark != null) return landmark;
		BlockState elevated = Level1Elevation.block(seed, tile, ceiling, x, y, z);
		if (elevated != null) return elevated;
		if (y > CEILING_Y) return Blocks.CONCRETE.gray().defaultBlockState();
		if (y < FLOOR_Y) return Blocks.CONCRETE.gray().defaultBlockState();
		if (y == CEILING_Y) return state(Level1Blocks.GARAGE_CEILING);
		if (y == FLOOR_Y) return floor(tile);
		return switch (tile) {
			case FLOOR, LINE, DAMP, PUDDLE, HATCH, FRAME, STAIRWELL, BOOTH, ROOM, MACHINE, CRATE, BARRIER, FURNITURE ->
					y == HALL_TOP ? hallFixture(ceiling) : air();
			case EXIT_CORE -> state(Level1Blocks.GARAGE_WALL);
			case SHELF -> y <= FLOOR_Y + 2 ? state(Level1Blocks.METAL_SHELF) : y == HALL_TOP ? hallFixture(ceiling) : air();
			case PILLAR -> pillar(seed, x, y, z);
			case HALL_WALL -> state(Level1Blocks.GARAGE_WALL);
			case PERIMETER -> state(Level1Blocks.PAINTED_CONCRETE_WALL);
			case OPENING -> y <= LOW_TOP ? air() : state(Level1Blocks.PAINTED_CONCRETE_WALL);
			case OPENING_SIGN -> y <= LOW_TOP ? air() : y == LOW_CEILING_Y ? sign(x) : state(Level1Blocks.PAINTED_CONCRETE_WALL);
			case CORRIDOR -> corridor(ceiling, y);
			case DESK -> y == FLOOR_Y + 1 ? state(Level1Blocks.OFFICE_DESK) : y == FLOOR_Y + 2 ? deskTop(seed, x, z) : corridor(ceiling, y);
			case BIN -> y == FLOOR_Y + 1 ? state(Level1Blocks.WASTEBASKET) : corridor(ceiling, y);
			case CHAIR -> y == FLOOR_Y + 1 ? state(Level1Blocks.SUNKEN_CHAIR).setValue(OldComputerBlock.FACING, chairFacing(seed, x, z)) : corridor(ceiling, y);
			case FLOOR_PAINTING -> y == FLOOR_Y + 1 ? state(Level1Blocks.FLOOR_PAINTING) : corridor(ceiling, y);
			case BED -> y == FLOOR_Y + 1 ? state(Level1Blocks.HOSPITAL_BED) : corridor(ceiling, y);
			case TABLE -> y == FLOOR_Y + 1 ? state(Level1Blocks.OFFICE_DESK) : corridor(ceiling, y);
			case GALLERY -> y <= LOW_CEILING_Y ? state(Level1Blocks.GALLERY_WALL) : state(Level1Blocks.GARAGE_WALL);
			case BOOKSHELF -> y <= FLOOR_Y + 2 ? state(Level1Blocks.BOOK_SHELF) : corridor(ceiling, y);
			case PARTITION -> y <= FLOOR_Y + 1 ? panel(seed, x, z) : corridor(ceiling, y);
			case ROOM_SHELF -> y <= FLOOR_Y + 2 ? state(Level1Blocks.METAL_SHELF) : corridor(ceiling, y);
			case WALL -> y <= LOW_CEILING_Y ? state(Level1Blocks.PAINTED_CONCRETE_WALL) : state(Level1Blocks.GARAGE_WALL);
			case BRICK -> y <= LOW_CEILING_Y ? state(Level1Blocks.OLD_BRICK) : state(Level1Blocks.GARAGE_WALL);
			case PADDED -> y <= LOW_TOP ? state(Level1Blocks.PADDED_WALL)
					: y == LOW_CEILING_Y ? state(Level1Blocks.PAINTED_CONCRETE_WALL) : state(Level1Blocks.GARAGE_WALL);
			case DOOR -> door(y);
		};
	}

	private static BlockState floor(Tile tile) {
		return switch (tile) {
			case FLOOR, PILLAR, SHELF, HALL_WALL, EXIT_CORE, HATCH, FRAME, STAIRWELL, BOOTH, ROOM, MACHINE, CRATE, BARRIER, FURNITURE ->
					state(Level1Blocks.GARAGE_FLOOR);
			case LINE -> state(Level1Blocks.GARAGE_FLOOR_LINE);
			case DAMP -> state(Level1Blocks.DAMP_GARAGE_FLOOR);
			case PUDDLE -> state(Level1Blocks.PUDDLED_GARAGE_FLOOR);
			default -> state(Level1Blocks.CORRIDOR_FLOOR);
		};
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

	private static BlockState corridor(Ceiling ceiling, int y) {
		if (y < LOW_TOP) return air();
		if (y == LOW_TOP) {
			return switch (ceiling) {
				case BULB -> state(Level1Blocks.BARE_BULB);
				case DEAD_BULB -> state(Level1Blocks.BROKEN_BARE_BULB);
				case EXIT_BULB -> state(Level1Blocks.GREEN_BARE_BULB);
				default -> air();
			};
		}
		return state(y == LOW_CEILING_Y ? Level1Blocks.GARAGE_CEILING : Level1Blocks.GARAGE_WALL);
	}

	private static BlockState pillar(long seed, int x, int y, int z) {
		if (y == FLOOR_Y + 1) return state(Level1Blocks.GARAGE_PILLAR_BASE);
		if (y == FLOOR_Y + 2 && Level1Layout.kindAt(seed, x, z) == Kind.PARKING) {
			// The two corner blocks of a 2x2 pillar that between them face all four ways carry the level number.
			int bx = Math.floorMod(x, Level1Layout.BAY_X), bz = Math.floorMod(z, Level1Layout.BAY_Z);
			if (bx != bz) return state(Level1Blocks.GARAGE_PILLAR_MARKED);
		}
		return state(Level1Blocks.GARAGE_PILLAR);
	}

	private static BlockState sign(int x) {
		boolean alongZ = Math.floorMod(x, Level1Layout.DISTRICT) == 0;
		return state(Level1Blocks.EMERGENCY_SIGN).setValue(AxisSignBlock.AXIS, alongZ ? Direction.Axis.Z : Direction.Axis.X);
	}

	/** A cubicle panel with arms toward the panels next to it and a look picked from where it stands. */
	private static BlockState panel(long seed, int x, int z) {
		int[] weights = {0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 3};
		int style = weights[Math.floorMod(Level1Layout.hash(seed, x, z, 17), weights.length)];
		return CubiclePanelBlock.with(isPanel(seed, x, z - 1), isPanel(seed, x + 1, z), isPanel(seed, x, z + 1), isPanel(seed, x - 1, z), style);
	}

	private static boolean isPanel(long seed, int x, int z) {
		return Level1Layout.tile(seed, x, z) == Tile.PARTITION;
	}

	/** What sits on an office desk: mostly a computer, otherwise a dead plant, a fern in a pot, or nothing. */
	private static BlockState deskTop(long seed, int x, int z) {
		int roll = Math.floorMod(Level1Layout.hash(seed, x, z, 13), 100);
		if (roll < 62) return computer(seed, x, z);
		if (roll < 76) return state(Level1Blocks.DESK_PLANT_DEAD);
		if (roll < 86) return state(Level1Blocks.DESK_PLANT_LEAFY);
		return Blocks.AIR.defaultBlockState();
	}

	private static BlockState computer(long seed, int x, int z) {
		// The screen faces whoever sits at the desk; with no chair, it faces out into the room.
		Direction facing = toward(seed, x, z, Tile.CHAIR);
		if (facing == null) facing = awayFromWall(seed, x, z);
		if (facing == null) facing = Direction.from2DDataValue(Math.floorMod(Level1Layout.hash(seed, x, z, 11), 4));
		return state(Level1Blocks.OLD_COMPUTER).setValue(OldComputerBlock.FACING, facing);
	}

	/** A chair faces its desk or table; one set against a wall faces out into the room. */
	static Direction chairFacing(long seed, int x, int z) {
		Direction facing = toward(seed, x, z, Tile.DESK);
		if (facing == null) facing = toward(seed, x, z, Tile.TABLE);
		if (facing == null) facing = awayFromWall(seed, x, z);
		return facing == null ? Direction.NORTH : facing;
	}

	private static Direction toward(long seed, int x, int z, Tile tile) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (Level1Layout.tile(seed, x + d.getStepX(), z + d.getStepZ()) == tile) return d;
		}
		return null;
	}

	private static Direction awayFromWall(long seed, int x, int z) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			boolean back = Level1Layout.tile(seed, x - d.getStepX(), z - d.getStepZ()).walkable();
			if (!back && Level1Layout.tile(seed, x + d.getStepX(), z + d.getStepZ()).walkable()) return d;
		}
		return null;
	}

	private static BlockState door(int y) {
		if (y == FLOOR_Y + 1) return state(Level1Blocks.METAL_EXIT_DOOR);
		if (y == FLOOR_Y + 2) return state(Level1Blocks.METAL_EXIT_DOOR_TOP);
		if (y == FLOOR_Y + 3) return Shared.EXIT_SIGN;
		return state(y == LOW_CEILING_Y ? Level1Blocks.PAINTED_CONCRETE_WALL : Level1Blocks.GARAGE_WALL);
	}

	private static BlockState state(net.minecraft.world.level.block.Block block) {
		return block.defaultBlockState();
	}

	private static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}

	/** Blocks from the rest of the mod, looked up by id once everything is registered. */
	private static final class Shared {
		static final BlockState EXIT_SIGN = BuiltInRegistries.BLOCK.getValue(Level1.id("exit_sign")).defaultBlockState();
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
				int wx = minX + x, wz = minZ + z;
				Tile tile = Level1Layout.tile(seed, wx, wz);
				Ceiling ceiling = Level1Layout.ceiling(seed, wx, wz);
				HallPlan.Cell exit = Level1Layout.exitCell(seed, wx, wz);
				HallPlan.PropCell prop = Level1Layout.propCell(seed, wx, wz);
				BasePlan.Piece piece = Level1Layout.basePiece(seed, wx, wz);
				for (int y = 0; y < HEIGHT; y++) {
					BlockState state = block(seed, tile, ceiling, exit, prop, piece, wx, y, wz);
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
