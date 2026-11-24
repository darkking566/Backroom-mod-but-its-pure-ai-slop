package dev.noclip.backrooms.level2;

import com.mojang.serialization.MapCodec;
import dev.noclip.backrooms.level2.Level2Layout.Kind;
import dev.noclip.backrooms.level2.Level2Layout.Tile;
import dev.noclip.backrooms.level2.Level2Plan.Room;
import dev.noclip.backrooms.level2.Level2Plan.RoomKind;
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
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Turns the Level 2 floor plan into blocks: tunnels four blocks high under a cable-strung ceiling, banks of pipes
 * floor to ceiling along one wall (fat pipe at the bottom, steel pipes, a cable tray, thin pipes at the top),
 * a light in the middle of each cell, and doors, exits and windows set into the walls.
 */
final class Level2ChunkGenerator extends ChunkGenerator {
	static final MapCodec<Level2ChunkGenerator> CODEC = BiomeSource.CODEC.fieldOf("biome_source")
			.xmap(Level2ChunkGenerator::new, generator -> generator.biomeSource);

	static final int FLOOR_Y = 32;
	static final int CEILING_Y = 37;
	static final int TOP = CEILING_Y - 1;
	// Must match dimension_type/level_2.json
	static final int HEIGHT = 48;

	private Level2ChunkGenerator(BiomeSource biomeSource) {
		super(biomeSource);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	// RandomState is derived from the world seed, which fillFromNoise is not given directly.
	static long planSeed(RandomState randomState) {
		return randomState.getOrCreateRandomFactory(Level2.id("level_2_plan")).at(0, 0, 0).nextLong();
	}

	static BlockState blockAt(long seed, int x, int y, int z) {
		return block(seed, Level2Layout.tile(seed, x, z), x, y, z);
	}

	private static BlockState block(long seed, Tile tile, int x, int y, int z) {
		// Bedrock under the floor and over the roof, so nothing gets out underneath or lands on top.
		if (y == 0 || y > CEILING_Y) return Blocks.BEDROCK.defaultBlockState();
		boolean dark = tile.kind() == Kind.VOID || tile.kind() == Kind.VOID_WALL;
		if (y < FLOOR_Y) return Blocks.CONCRETE.gray().defaultBlockState();
		if (y == CEILING_Y) return state(dark ? Level2Blocks.VOID_CONCRETE : Level2Blocks.UTILITY_CEILING);
		if (y == FLOOR_Y) {
			if (dark) return state(Level2Blocks.VOID_CONCRETE);
			if (Level2Layout.railAxis(seed, x, z) >= 0 && Math.floorMod(x + z, 8) == 0) {
				return Blocks.REDSTONE_BLOCK.defaultBlockState();
			}
			return floor(seed, tile, x, z);
		}
		int h = y - FLOOR_Y; // 1..4
		return switch (tile.kind()) {
			case WALL -> wall(seed, x, z);
			case VOID_WALL -> state(Level2Blocks.VOID_CONCRETE);
			case FLOOR -> floorBlock(seed, x, z, h);
			case ROOM -> roomBlock(seed, x, z, h);
			case VOID -> air();
			case BANK -> bank(seed, x, y, z, tile.dir());
			case MACHINE -> machine(seed, tile, x, y, z, h);
			case CABINET -> h == 1 ? facing(Level2Blocks.MACHINE_CABINET, tile.dir()) : h == 2 ? facing(Level2Blocks.MACHINE_PANEL, tile.dir())
					: h == 4 ? light(seed, x, z) : air();
			case CRATE -> h == 1 || h == 2 && Level2Layout.bankRoll(seed, x, y, z) < 45 ? state(Level2Blocks.WOODEN_CRATE) : roomAir(seed, x, z, h);
			case DRUM -> h == 1 ? state(Level2Blocks.OIL_DRUM) : roomAir(seed, x, z, h);
			case SCRAP -> h == 1 ? state(Level2Blocks.SCRAP_HEAP) : roomAir(seed, x, z, h);
			case DOOR, LOCKED_DOOR -> h <= 2 ? door(tile, h) : wall(seed, x, z);
			case EXIT -> h <= tile.exit().height
					? Level2Blocks.EXITS.get(tile.exit()).defaultBlockState().setValue(Level2ExitBlock.PART, h - 1) : wall(seed, x, z);
			case WINDOW -> h == 2 ? state(Level2Blocks.TUNNEL_WINDOW) : wall(seed, x, z);
		};
	}

	/** An earlier wanderer's spray-painted mark on the wall beside this column, if there is one here. */
	private static BlockState mark(long seed, int x, int z) {
		Level2Layout.Mark mark = Level2Layout.mark(seed, x, z);
		if (mark == null) return air();
		return Level2Blocks.WANDERER_MARK.defaultBlockState()
				.setValue(Level2MarkBlock.FACING, Direction.from2DDataValue(horizontal(mark.facing())))
				.setValue(Level2MarkBlock.LEVEL, Level2MarkBlock.level(mark.exit())).setValue(Level2MarkBlock.RIGHT, mark.right());
	}

	private static BlockState floorBlock(long seed, int x, int z, int h) {
		if (h == 4) return light(seed, x, z);
		if (h == 2) return mark(seed, x, z);
		if (h == 1) {
			int rail = Level2Layout.railAxis(seed, x, z);
			if (rail >= 0) {
				RailShape shape = rail == 0 ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
				if (Math.floorMod(x + z, 8) == 0) {
					return Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shape).setValue(PoweredRailBlock.POWERED, true);
				}
				return Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape);
			}
		}
		return air();
	}

	private static BlockState roomBlock(long seed, int x, int z, int h) {
		if (h == 4) return light(seed, x, z);
		int lx = Math.floorMod(x, Level2Plan.SIZE), lz = Math.floorMod(z, Level2Plan.SIZE);
		Level2Plan plan = Level2Layout.plan(seed, x, z);
		int r = plan.roomOf[Level2Plan.cell(lx / Level2Plan.CELL, lz / Level2Plan.CELL)];
		if (r >= 0) {
			Room room = plan.rooms.get(r);
			int cx = (room.x0() + room.x1()) / 2, cz = (room.z0() + room.z1()) / 2;
			if (room.kind() == RoomKind.EL3A) {
				if (lx == cx && lz == cz) return h == 1 ? Blocks.CRAFTING_TABLE.defaultBlockState() : h == 2 ? Blocks.LANTERN.defaultBlockState() : air();
				if (lx == cx + 1 && lz == cz && h == 1) return Blocks.CHEST.defaultBlockState();
			} else if (room.kind() == RoomKind.MACCHINA) {
				if (lx == cx && lz == cz) return h == 1 ? state(Level2Blocks.MACHINE_CABINET) : h == 2 ? Blocks.SOUL_LANTERN.defaultBlockState() : air();
				if (lx == cx + 1 && lz == cz && h == 1) return Blocks.LECTERN.defaultBlockState();
			}
		}
		return air();
	}

	private static BlockState roomAir(long seed, int x, int z, int h) {
		return h == 4 ? light(seed, x, z) : air();
	}

	private static BlockState floor(long seed, Tile tile, int x, int z) {
		if (tile.kind() != Kind.FLOOR && tile.kind() != Kind.ROOM) return state(Level2Blocks.UTILITY_FLOOR);
		RoomKind kind = Level2Layout.roomKindAt(seed, x, z);
		if (kind == RoomKind.EL3A) return state(Level2Blocks.UTILITY_FLOOR);
		if (kind == RoomKind.MACCHINA) return state(Level2Blocks.BLACK_BRICK_WALL);
		return switch (Level2Layout.ground(seed, x, z)) {
			case DEBRIS -> state(Level2Blocks.DEBRIS_UTILITY_FLOOR);
			case DAMP -> state(Level2Blocks.DAMP_UTILITY_FLOOR);
			case PLAIN -> state(Level2Blocks.UTILITY_FLOOR);
		};
	}

	private static BlockState wall(long seed, int x, int z) {
		return switch (Level2Layout.finish(seed, x, z)) {
			case CONCRETE -> state(Level2Blocks.UTILITY_WALL);
			case STAINED -> state(Level2Blocks.STAINED_UTILITY_WALL);
			case MUDDY_BRICK -> state(Level2Blocks.MUDDY_BRICK_WALL);
			case BLACK_BRICK -> state(Level2Blocks.BLACK_BRICK_WALL);
			case PAINTED -> state(Level2Blocks.PAINTED_BRICK_WALL);
		};
	}

	/** One bank of pipes: the fat pipe at the bottom, steel pipes at chest height, a cable tray, thin pipes on top. */
	private static BlockState bank(long seed, int x, int y, int z, int dir) {
		int axis = dir == Level2Plan.NORTH || dir == Level2Plan.SOUTH ? 0 : 1;
		Block block = switch (y - FLOOR_Y) {
			case 1 -> Level2Layout.palette(seed, x, z, axis) == Level2Layout.Palette.GREEN ? Level2Blocks.GREEN_PIPE : Level2Blocks.INSULATED_PIPE;
			case 2 -> middlePipes(seed, x, y, z);
			case 3 -> Level2Blocks.CONDUIT_TRAY;
			default -> Level2Blocks.SMALL_PIPES;
		};
		return block.defaultBlockState().setValue(Level2BankBlock.FACING, Direction.from2DDataValue(horizontal(dir)));
	}

	private static Block middlePipes(long seed, int x, int y, int z) {
		if (Level2Layout.bladeTrail(seed, x, z)) return Level2Blocks.BLUE_LIGHT_PIPES;
		int roll = Level2Layout.bankRoll(seed, x, y, z);
		if (Level2Layout.hot(seed, x, z) && roll < 30) return Level2Blocks.STEAM_PIPES;
		if (roll < 4) return Level2Blocks.LEAKING_PIPES;
		if (roll < 12) return Level2Blocks.VALVE_PIPES;
		return Level2Blocks.STEEL_PIPES;
	}

	/** Where a bank of pipes ends, a machine cabinet stands in its place, the pipes going on above it. */
	private static BlockState machine(long seed, Tile tile, int x, int y, int z, int h) {
		if (h == 1) return facing(Level2Blocks.MACHINE_CABINET, tile.dir());
		if (h == 2) return facing(Level2Blocks.MACHINE_PANEL, tile.dir());
		return bank(seed, x, y, z, tile.dir());
	}

	private static BlockState light(long seed, int x, int z) {
		Block block = switch (Level2Layout.light(seed, x, z)) {
			case NONE -> null;
			case WHITE -> Level2Blocks.UTILITY_LIGHT;
			case WARM -> Level2Blocks.WARM_UTILITY_LIGHT;
			case DEAD -> Level2Blocks.BROKEN_UTILITY_LIGHT;
			case VIOLET -> Level2Blocks.EMERGENCY_LIGHT;
			case BULB -> Level2Blocks.CAGED_BULB;
			case DEAD_BULB -> Level2Blocks.BROKEN_CAGED_BULB;
		};
		if (block == null) return air();
		return block.defaultBlockState().setValue(Level2AxisBlock.AXIS, Level2Layout.lightAxis(seed, x, z) == 0 ? Direction.Axis.X : Direction.Axis.Z);
	}

	/**
	 * A steel door, closed, its panel flush with the tunnel side of the wall. The plan's dir points into the tunnel;
	 * a vanilla door faces the way you look when you place it, which is into the room.
	 */
	private static BlockState door(Tile tile, int h) {
		Block block = tile.kind() == Kind.DOOR ? Level2Blocks.STEEL_DOOR : Level2Blocks.LOCKED_STEEL_DOOR;
		Direction into = Direction.from2DDataValue(horizontal(tile.dir())).getOpposite();
		return block.defaultBlockState().setValue(DoorBlock.FACING, into)
				.setValue(DoorBlock.HALF, h == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
				.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, false).setValue(DoorBlock.POWERED, false);
	}

	private static BlockState facing(Block block, int dir) {
		return block.defaultBlockState().setValue(Level2FacingBlock.FACING, Direction.from2DDataValue(horizontal(dir)));
	}

	/** Plan directions (north, east, south, west) to Minecraft's 2D data values (south, west, north, east). */
	static int horizontal(int dir) {
		return switch (dir) {
			case Level2Plan.NORTH -> 2;
			case Level2Plan.EAST -> 3;
			case Level2Plan.SOUTH -> 0;
			default -> 1;
		};
	}

	private static BlockState state(Block block) {
		return block.defaultBlockState();
	}

	private static BlockState air() {
		return Blocks.AIR.defaultBlockState();
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
				Tile tile = Level2Layout.tile(seed, wx, wz);
				for (int y = 0; y < HEIGHT; y++) {
					BlockState state = block(seed, tile, wx, y, wz);
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
