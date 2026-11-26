package dev.noclip.backrooms.level3;

import com.mojang.serialization.MapCodec;
import dev.noclip.backrooms.level3.Level3Halls.Piece;
import dev.noclip.backrooms.level3.Level3Layout.Kind;
import dev.noclip.backrooms.level3.Level3Layout.Tile;
import dev.noclip.backrooms.props.BackroomsProps;
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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Turns the Level 3 floor plan into blocks: brick passages of every height under a dark ceiling, conduits and
 * black sludge pipes along the walls, electrical boxes, sparking outlets, live wires and sludge underfoot, bars
 * you cannot pass, and halls six blocks high.
 */
final class Level3ChunkGenerator extends ChunkGenerator {
	static final MapCodec<Level3ChunkGenerator> CODEC = BiomeSource.CODEC.fieldOf("biome_source")
			.xmap(Level3ChunkGenerator::new, generator -> generator.biomeSource);

	static final int FLOOR_Y = 32;
	/** The top of the solid; halls are open up to just under it. */
	static final int ROOF_Y = FLOOR_Y + Level3Halls.HEIGHT + 1;
	// Must match dimension_type/level_3.json
	static final int HEIGHT = 48;

	private Level3ChunkGenerator(BiomeSource biomeSource) {
		super(biomeSource);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	// RandomState is derived from the world seed, which fillFromNoise is not given directly.
	static long planSeed(RandomState randomState) {
		return randomState.getOrCreateRandomFactory(Level3.id("level_3_plan")).at(0, 0, 0).nextLong();
	}

	static BlockState blockAt(long seed, int x, int y, int z) {
		return new Column(seed, x, z).block(y);
	}

	/** Everything about one column that does not depend on the height. */
	private static final class Column {
		final long seed;
		final int x, z;
		final Tile tile;
		/** The side of a wall this floor column is against (or -1), for things mounted on walls. */
		final int against;
		final boolean gap;

		Column(long seed, int x, int z) {
			this.seed = seed;
			this.x = x;
			this.z = z;
			this.tile = Level3Layout.tile(seed, x, z);
			int side = -1;
			if (tile.kind() == Kind.FLOOR) {
				for (int d = 0; d < 4 && side < 0; d++) {
					if (Level3Layout.tile(seed, x + Level3Plan.DX[d], z + Level3Plan.DZ[d]).kind() == Kind.WALL) side = d;
				}
			}
			this.against = side;
			this.gap = tile.kind() == Kind.HALL && Level3Layout.nearGap(seed, tile.hall(), x, z);
		}

		BlockState block(int y) {
			if (tile.hall() != null && tile.hall().kind() == Level3Plan.HallKind.GAMMA) return gamma(y);
			// Bedrock under the floor and over the roof, so nothing gets out underneath or lands on top.
			if (y == 0 || y > ROOF_Y) return Blocks.BEDROCK.defaultBlockState();
			if (y < FLOOR_Y) return Blocks.CONCRETE.gray().defaultBlockState();
			if (y == FLOOR_Y) return floor();
			int h = y - FLOOR_Y;
			return switch (tile.kind()) {
				case WALL -> wall(h);
				case FLOOR -> passage(h);
				case BARS -> bars(h, false);
				case GATE -> bars(h, true);
				case EXIT -> exit(h);
				case HALL -> hall(h);
				case HALL_WALL -> hallWall(h);
				case HALL_GAP -> h <= tile.clearance() || tile.clearance() == 1 && h == 2 ? gapFill(h) : hallWall(h);
			};
		}

		private BlockState floor() {
			if (tile.kind() == Kind.HALL) {
				return switch (tile.hall().kind()) {
					case BASE, BNTG_STORAGE -> state(Level3Blocks.MEG_WALL);
					case SCYLLA_COLONY -> state(Level3Blocks.CINDER_BLOCK_WALL);
					default -> state(Level3Blocks.DUSTY_FLOOR);
				};
			}
			return state(Level3Blocks.DUSTY_FLOOR);
		}

		private BlockState wall(int h) {
			if (h == 1 && outletHere()) return state(Level3Blocks.SPARKING_OUTLET);
			if (Level3Layout.purpified(seed, x, h, z)) return state(Level3Blocks.PURPIFIED_BRICK);
			return switch (Level3Layout.finish(seed, x, z)) {
				case RED_BRICK -> state(Level3Blocks.RED_BRICK_WALL);
				case BROWN_BRICK -> state(Level3Blocks.BROWN_BRICK_WALL);
				case CINDER_BLOCK -> state(Level3Blocks.CINDER_BLOCK_WALL);
				case GLAZED_TILE -> state(h == 1 ? Level3Blocks.GLAZED_TILE_WAINSCOT : Level3Blocks.GLAZED_TILE_WALL);
			};
		}

		/** Now and then a wall block beside a roomy passage has a warm, sparking outlet in it. */
		private boolean outletHere() {
			if (Level3Layout.roll(seed, x, 77, z) >= 3) return false;
			for (int d = 0; d < 4; d++) {
				Tile next = Level3Layout.tile(seed, x + Level3Plan.DX[d], z + Level3Plan.DZ[d]);
				if (next.kind() == Kind.FLOOR && next.clearance() >= 3) return true;
			}
			return false;
		}

		/** Ceiling, then solid wall up to the roof. */
		private BlockState above(int h, int clearance) {
			if (clearance == 1 && h == 2) return state(Level3Blocks.LOW_CEILING);
			if (h == Math.max(clearance, 1) + 1 + (clearance == 1 ? 1 : 0)) return state(Level3Blocks.STATION_CEILING);
			return wall(h);
		}

		private BlockState passage(int h) {
			int clearance = tile.clearance();
			if (h > clearance) return above(h, clearance);
			if (h == 1) {
				switch (Level3Layout.hazard(seed, x, z, tile)) {
					case WIRE -> {
						return state(Level3Blocks.LIVE_WIRE);
					}
					case SLUDGE -> {
						return state(Level3Blocks.SLUDGE_PUDDLE);
					}
					case NONE -> {
					}
				}
			}
			if (h == clearance) {
				BlockState light = light();
				if (light != null) return light;
				if (against >= 0 && clearance >= 3) {
					int roll = Level3Layout.roll(seed, x, h, z);
					if (roll < 40) return mounted(roll < 6 ? Level3Blocks.LEAKING_SLUDGE_PIPE : roll < 16 ? Level3Blocks.SLUDGE_PIPE : Level3Blocks.WALL_CONDUIT);
				}
			}
			if (h == 2) {
				Level3Layout.Sign sign = Level3Layout.sign(seed, x, z);
				if (sign != null) {
					return (sign.warning() ? Level3Blocks.MEG_WARNING_SIGN : Level3Blocks.MEG_ARROW_SIGN).defaultBlockState()
							.setValue(Level3SignBlock.FACING, Direction.from2DDataValue(horizontal(sign.facing())))
							.setValue(Level3SignBlock.RIGHT, sign.right());
				}
			}
			if (h == 2 && against >= 0 && clearance >= 3 && Level3Layout.roll(seed, x, 2, z) < 5) return mounted(Level3Blocks.ELECTRICAL_BOX);
			return air();
		}

		private BlockState mounted(Block block) {
			Direction out = Direction.from2DDataValue(horizontal(Level3Plan.opposite(against)));
			return block.defaultBlockState().setValue(Level3FacingBlock.FACING, out);
		}

		private BlockState light() {
			Block block = switch (Level3Layout.light(seed, x, z)) {
				case NONE -> null;
				case FLUORESCENT -> Level3Blocks.STATION_LIGHT;
				case DIM_FLUORESCENT -> Level3Blocks.DIM_STATION_LIGHT;
				case FLICKERING -> Level3Blocks.FLICKERING_STATION_LIGHT;
				case BROKEN -> Level3Blocks.BROKEN_STATION_LIGHT;
				case BULB -> Level3Blocks.CAGE_BULB;
				case DEAD_BULB -> Level3Blocks.DEAD_CAGE_BULB;
			};
			if (block == null) return null;
			return block.defaultBlockState().setValue(Level3AxisBlock.AXIS, runsAlongZ() ? Direction.Axis.Z : Direction.Axis.X);
		}

		private boolean runsAlongZ() {
			Level3Plan plan = Level3Layout.plan(seed, x, z);
			int lx = Math.floorMod(x, Level3Plan.SIZE), lz = Math.floorMod(z, Level3Plan.SIZE);
			return plan.straightAxis(Level3Plan.cell(lx / Level3Plan.CELL, lz / Level3Plan.CELL)) == 1;
		}

		private BlockState bars(int h, boolean gate) {
			int clearance = tile.clearance();
			if (h > Math.max(clearance, 1)) return above(h, clearance);
			Direction.Axis axis = tile.dir() == Level3Plan.NORTH || tile.dir() == Level3Plan.SOUTH ? Direction.Axis.X : Direction.Axis.Z;
			if (gate && h <= 2) {
				return Level3Blocks.CAGE_GATE.defaultBlockState().setValue(Level3GateBlock.AXIS, axis)
						.setValue(Level3GateBlock.HALF, h == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
			}
			Block bars = switch (Level3Layout.finish(seed, x, z)) {
				case GLAZED_TILE, CINDER_BLOCK -> Level3Blocks.WHITE_BARS;
				default -> Level3Blocks.BLACK_BARS;
			};
			return bars.defaultBlockState().setValue(Level3AxisBlock.AXIS, axis);
		}

		private BlockState exit(int h) {
			if (h > 2) return tile.hall() != null ? hallWall(h) : wall(h);
			Block block = tile.exit() == Level3Exit.ELEVATOR ? Level3Blocks.ELEVATOR : Level3Blocks.BASE_GAMMA_DOOR;
			return block.defaultBlockState().setValue(Level3PartBlock.PART, h - 1);
		}

		private BlockState hallWall(int h) {
			if (h == Level3Halls.HEIGHT + 1) return state(Level3Blocks.STATION_CEILING);
			return switch (tile.hall().kind()) {
				case SANCTUM -> sanctumWall(h);
				case BASE -> h == 3 && along() % 4 == 2 ? state(Level3Blocks.MEG_SIGN) : state(Level3Blocks.MEG_WALL);
				case BNTG_STORAGE -> state(Level3Blocks.MEG_WALL);
				case BLUE_SALVATION -> state(Level3Blocks.FOJ_WHITE_WALL);
				case GENERATOR, BOILER, ASSEMBLY, SCYLLA_COLONY -> state(Level3Blocks.CINDER_BLOCK_WALL);
				case CAR -> state(Level3Blocks.RED_BRICK_WALL);
				case GAMMA -> state(Level3Blocks.MEG_WALL);
			};
		}

		/** Base Gamma, after GammaLayout's template; tile.hx and hz are the position inside it. */
		private BlockState gamma(int y) {
			GammaLayout.Bit bit = GammaLayout.at(tile.hx(), y, tile.hz());
			Direction facing = Direction.from2DDataValue(horizontal(bit.dir()));
			Direction.Axis along = bit.dir() == 1 ? Direction.Axis.Z : Direction.Axis.X;
			return switch (bit.thing()) {
				case AIR -> air();
				case BEDROCK -> Blocks.BEDROCK.defaultBlockState();
				case BRICK -> state(Level3Blocks.RED_BRICK_WALL);
				case WALL -> state(Level3Blocks.MEG_WALL);
				case FLOOR -> state(Level3Blocks.DUSTY_FLOOR);
				case MALL_FLOOR -> state(Level3Blocks.GLAZED_TILE_WALL);
				case CEILING -> state(Level3Blocks.STATION_CEILING);
				case LIGHT -> axis(Level3Blocks.STATION_LIGHT);
				case BULB -> axis(Level3Blocks.CAGE_BULB);
				case DAYLIGHT -> state(Level3Blocks.DAYLIGHT_PANEL);
				case RAIL -> Level3Blocks.BLACK_BARS.defaultBlockState().setValue(Level3AxisBlock.AXIS, along);
				case STAIRS -> Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
				case GLASS -> Blocks.GLASS.defaultBlockState();
				case WATER -> Blocks.WATER.defaultBlockState();
				case RIM -> state(Level3Blocks.CINDER_BLOCK_WALL);
				case CHECKPOINT -> part(Level3Blocks.CHECKPOINT_DOOR, bit.part(), facing);
				case LOCKER -> Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, facing);
				case COT -> Level3Blocks.COT.defaultBlockState().setValue(Level3FacingBlock.FACING, facing);
				case TABLE -> state(Level3Blocks.TABLE);
				case SUPPLY_CRATE -> state(Level3Blocks.SUPPLY_CRATE);
				case CRATE -> state(Level3Blocks.PARTS_CRATE);
				case CAMERA -> Level3Blocks.SECURITY_CAMERA.defaultBlockState().setValue(Level3FacingBlock.FACING, facing);
				case MEG_SIGN -> state(Level3Blocks.MEG_SIGN);
				case LECTERN -> Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
				case IRON_DOOR -> Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing)
						.setValue(DoorBlock.HALF, bit.part() == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
				case CAGE_GATE -> Level3Blocks.CAGE_GATE.defaultBlockState().setValue(Level3GateBlock.AXIS, along)
						.setValue(Level3GateBlock.HALF, bit.part() == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
				case EXIT_DOOR -> part(Level3Blocks.BASE_GAMMA_DOOR, bit.part(), facing);
				case ELEVATOR -> part(Level3Blocks.ELEVATOR, bit.part(), facing);
				case PLANKS -> Blocks.SPRUCE_PLANKS.defaultBlockState();
				case PATCH -> Blocks.OAK_PLANKS.defaultBlockState();
				case COBWEB -> Blocks.COBWEB.defaultBlockState();
				case CANOPY_RED -> Blocks.WOOL.red().defaultBlockState();
				case CANOPY_YELLOW -> Blocks.WOOL.yellow().defaultBlockState();
				case CANOPY_BLUE -> Blocks.WOOL.blue().defaultBlockState();
				case SERVER -> part(Level3Blocks.SWITCHGEAR, bit.part(), facing);
				case ARCADE -> Blocks.CONCRETE.black().defaultBlockState();
				case SCREEN -> Blocks.SEA_LANTERN.defaultBlockState();
				case JUKEBOX -> Blocks.JUKEBOX.defaultBlockState();
				case SMOKER -> Blocks.SMOKER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
			};
		}

		/** Brown brick with tall stained-glass windows. */
		private BlockState sanctumWall(int h) {
			int along = along();
			if ((h == 3 || h == 4) && along % 5 == 2) return state(Level3Blocks.STAINED_GLASS);
			return state(Level3Blocks.BROWN_BRICK_WALL);
		}

		private int along() {
			return tile.dir() == Level3Plan.NORTH || tile.dir() == Level3Plan.SOUTH ? Math.floorMod(x, Level3Plan.SIZE) : Math.floorMod(z, Level3Plan.SIZE);
		}

		private BlockState gapFill(int h) {
			if (tile.clearance() == 1 && h == 2) return state(Level3Blocks.LOW_CEILING);
			return air();
		}

		private BlockState hall(int h) {
			if (h == Level3Halls.HEIGHT + 1) return state(Level3Blocks.STATION_CEILING);
			Piece piece = Level3Halls.at(seed, tile.hall(), tile.hx(), tile.hz(), h, x, z, gap);
			Direction facing = Direction.from2DDataValue(horizontal(piece.dir()));
			return switch (piece.thing()) {
				case AIR -> air();
				case LIGHT -> axis(Level3Blocks.STATION_LIGHT);
				case FLICKERING_LIGHT -> axis(Level3Blocks.FLICKERING_STATION_LIGHT);
				case BULB -> axis(Level3Blocks.CAGE_BULB);
				case BRIGHT_LIGHT -> axis(Level3Blocks.FLOODLIGHT);
				case BLUE_LIGHT -> axis(Level3Blocks.BLUE_STATION_LIGHT);
				case DEAD_LIGHT -> axis(Level3Blocks.BROKEN_STATION_LIGHT);
				case CABLE_TRAY -> axis(Level3Blocks.CABLE_TRAY);
				case SLUDGE_PIPE -> axis(Level3Blocks.CEILING_SLUDGE_PIPE);
				case WIRE -> state(Level3Blocks.LIVE_WIRE);
				case SLUDGE -> state(Level3Blocks.SLUDGE_PUDDLE);
				case GENERATOR -> part(Level3Blocks.GENERATOR, piece.part(), Direction.SOUTH);
				case SWITCHGEAR -> part(Level3Blocks.SWITCHGEAR, piece.part(), facing);
				case BOILER -> part(Level3Blocks.BOILER, piece.part(), Direction.SOUTH);
				case BOILER_VALVE -> Level3Blocks.BOILER_VALVE.defaultBlockState().setValue(Level3FacingBlock.FACING, Direction.SOUTH);
				case CONVEYOR -> Level3Blocks.CONVEYOR.defaultBlockState().setValue(Level3FacingBlock.FACING, facing);
				case CRATE -> state(Level3Blocks.PARTS_CRATE);
				case PRESS -> part(Level3Blocks.PRESS, piece.part(), facing);
				case CHAIN -> axis(Level3Blocks.HOIST_CHAIN);
				case HOOK -> axis(Level3Blocks.HOIST_HOOK);
				case PILLAR_BASE -> state(Level3Blocks.PILLAR_BASE);
				case PILLAR -> state(Level3Blocks.PILLAR);
				case PILLAR_TOP -> state(Level3Blocks.PILLAR_TOP);
				case PEDESTAL -> state(Level3Blocks.PEDESTAL);
				case ANGEL -> part(Level3Blocks.ANGEL, piece.part(), facing);
				case CAR -> part(Level3Blocks.CAR, piece.part(), facing);
				case COT -> Level3Blocks.COT.defaultBlockState().setValue(Level3FacingBlock.FACING, facing);
				case SUPPLY_CRATE -> state(Level3Blocks.SUPPLY_CRATE);
				case TABLE -> state(Level3Blocks.TABLE);
				case CAMERA -> Level3Blocks.SECURITY_CAMERA.defaultBlockState().setValue(Level3FacingBlock.FACING, facing);
				case JERRY_MURAL -> Level3Blocks.JERRY_MURAL.defaultBlockState().setValue(Level3FacingBlock.FACING, facing);
				case UEC_APPARATUS -> Level3Blocks.UEC_APPARATUS.defaultBlockState().setValue(Level3FacingBlock.FACING, facing);
				case SALVAGE_BENCH -> BackroomsProps.SALVAGE_BENCH.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing);
				case WORKSHOP_BENCH -> BackroomsProps.WORKSHOP_BENCH.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing);
				case ARMORY_CRATE -> BackroomsProps.LOCKED_ARMORY_CRATE.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing);
				case VAULT_CHEST -> BackroomsProps.ANOMALY_VAULT_CHEST.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing);
				case LECTERN -> Blocks.LECTERN.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing);
				case BARS -> axis(Level3Blocks.WHITE_BARS);
			};
		}

		private BlockState axis(Block block) {
			return block.defaultBlockState().setValue(Level3AxisBlock.AXIS, Direction.Axis.X);
		}

		private BlockState part(Block block, int part, Direction facing) {
			return block.defaultBlockState().setValue(Level3PartBlock.PART, part).setValue(Level3PartBlock.FACING, facing);
		}
	}

	/** Plan directions (north, east, south, west) to Minecraft's 2D data values (south, west, north, east). */
	static int horizontal(int dir) {
		return switch (dir) {
			case Level3Plan.NORTH -> 2;
			case Level3Plan.EAST -> 3;
			case Level3Plan.SOUTH -> 0;
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
				Column column = new Column(seed, minX + x, minZ + z);
				for (int y = 0; y < HEIGHT; y++) {
					BlockState state = column.block(y);
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
		Column column = new Column(planSeed(randomState), x, z);
		BlockState[] states = new BlockState[HEIGHT];
		for (int y = 0; y < HEIGHT; y++) states[y] = column.block(y);
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
