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
import net.minecraft.world.level.block.StairBlock;
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
	static final int MIN_Y = -64;
	static final int HEIGHT = 192;
	static final int MAX_Y = MIN_Y + HEIGHT - 1;

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

	private static int towerTopY(long seed, int x, int z) {
		int rx = Math.floorDiv(x, Level4Plan.SIZE);
		int rz = Math.floorDiv(z, Level4Plan.SIZE);
		return 82 + Math.floorMod((int) Level4Layout.hash(seed, rx, rz, 499), 34);
	}

	static BlockState blockAt(long seed, int x, int y, int z) {
		Level4Layout.ExitStruct es = Level4Layout.exitStructure(seed, x, z);
		Tile tile = Level4Layout.tile(seed, x, z);
		Level4Layout.BridgeInfo bridge = Level4Layout.skybridgeInfo(seed, x, z);
		boolean exterior = Level4Layout.isExteriorSkin(seed, x, z);
		int topY = towerTopY(seed, x, z);
		return blockAtColumn(seed, x, y, z, tile, es, bridge, exterior, topY);
	}

	private static BlockState blockAtColumn(long seed, int x, int y, int z, Tile tile,
			Level4Layout.ExitStruct es, Level4Layout.BridgeInfo bridge, boolean exterior, int topY) {
		if (y <= MIN_Y) return Blocks.BEDROCK.defaultBlockState();
		if (tile.kind() == Kind.OUTSIDE_AIR) {
			if (Level4Layout.isLightwellShaft(seed, x, z)) {
				if (y < FLOOR_Y) return Blocks.CONCRETE.gray().defaultBlockState();
				if (y == FLOOR_Y) return Blocks.WATER.defaultBlockState();
			}
			return Blocks.AIR.defaultBlockState();
		}
		if (tile.kind() == Kind.BALCONY_RAIL || tile.kind() == Kind.BALCONY_FLOOR || tile.kind() == Kind.BALCONY_CANOPY) {
			return balconyColumn(y, tile.kind(), exterior);
		}
		if (y >= MAX_Y) return Blocks.BEDROCK.defaultBlockState();

		if (bridge != null && !exterior) {
			return bridgeColumn(seed, x, y, z, tile, bridge);
		}

		if (es != null && y >= FLOOR_Y - 3 && y <= FLOOR_Y + 6) {
			return exitStructureBlock(es, y);
		}

		if (y > topY + 1) return Blocks.AIR.defaultBlockState();
		if (y == topY + 1) return exterior ? Blocks.CONCRETE.lightGray().defaultBlockState() : Blocks.AIR.defaultBlockState();
		if (y == topY) return Blocks.CONCRETE.gray().defaultBlockState();

		int roofY = FLOOR_Y + tile.ceilingHeight() + 1;
		if (y >= FLOOR_Y && y <= roofY) {
			if (y == FLOOR_Y) return floor(seed, x, z, tile);
			if (y == roofY) return ceiling(seed, x, z, tile);
			return columnBlock(tile, y - FLOOR_Y);
		}

		if (exterior) {
			int story = Math.floorDiv(y - FLOOR_Y, 5);
			int sh = Math.floorMod(y - FLOOR_Y, 5);
			if (tile.kind() == Kind.PILLAR) return Blocks.CONCRETE.lightGray().defaultBlockState();
			if (sh == 0 || sh == 4) return Blocks.CONCRETE.gray().defaultBlockState();
			if (sh == 1) return Blocks.CONCRETE.lightGray().defaultBlockState();
			int cellX = Math.floorDiv(x, Level4Plan.CELL);
			int cellZ = Math.floorDiv(z, Level4Plan.CELL);
			boolean lit = Math.floorMod(Level4Layout.hash(seed, cellX + story * 31, cellZ - story * 17, 497), 100) < 14;
			return (lit ? Level4Blocks.OFFICE_TROFFER_DIM : Level4Blocks.OFFICE_BLACKED_WINDOW).defaultBlockState();
		}
		return Blocks.CONCRETE.gray().defaultBlockState();
	}

	private static BlockState balconyColumn(int y, Kind kind, boolean exterior) {
		if (y < FLOOR_Y) {
			if (exterior) {
				int sh = Math.floorMod(y - FLOOR_Y, 5);
				if (sh == 0 || sh == 4) return Blocks.CONCRETE.gray().defaultBlockState();
				return Blocks.CONCRETE.lightGray().defaultBlockState();
			}
			return Blocks.CONCRETE.gray().defaultBlockState();
		}
		if (y == FLOOR_Y) return Blocks.CONCRETE.gray().defaultBlockState();
		if (kind == Kind.BALCONY_RAIL) {
			if (y == FLOOR_Y + 1) return Blocks.CONCRETE.lightGray().defaultBlockState();
			if (y == FLOOR_Y + 2) return Blocks.IRON_BARS.defaultBlockState();
			return Blocks.AIR.defaultBlockState();
		}
		if (kind == Kind.BALCONY_CANOPY && y == FLOOR_Y + 5) {
			return Blocks.CONCRETE.lightGray().defaultBlockState();
		}
		return Blocks.AIR.defaultBlockState();
	}

	private static BlockState bridgeColumn(long seed, int x, int y, int z, Tile tile, Level4Layout.BridgeInfo bridge) {
		int s = bridge.spanPos();
		if (!bridge.elevated()) {
			int roofY = FLOOR_Y + tile.ceilingHeight() + 1;
			if (y == FLOOR_Y - 1 || y == roofY + 1) return Blocks.CONCRETE.gray().defaultBlockState();
			if (y < FLOOR_Y || y > roofY + 1) return Blocks.AIR.defaultBlockState();
			if (y == FLOOR_Y) return floor(seed, x, z, tile);
			if (y == roofY) return ceiling(seed, x, z, tile);
			int h = y - FLOOR_Y;
			if (s >= 11 && s <= 17) {
				if (tile.kind() == Kind.WINDOW_RAIN || tile.kind() == Kind.WINDOW_TRAP || tile.kind() == Kind.WINDOW_BLACKED) {
					if (h == 4) {
						Block win = switch (tile.kind()) {
							case WINDOW_BLACKED -> Level4Blocks.OFFICE_BLACKED_WINDOW;
							case WINDOW_TRAP -> Level4Blocks.OFFICE_TRAP_WINDOW;
							default -> Level4Blocks.OFFICE_RAIN_WINDOW;
						};
						return win.defaultBlockState();
					}
				}
				if (s == 14 && bridge.perpPos() == 3 && tile.kind() == Kind.AIR) {
					return (h <= 3 ? Level4Blocks.OFFICE_GLASS_WALL : Level4Blocks.OFFICE_PILLAR_UPPER).defaultBlockState();
				}
			}
			return columnBlock(tile, h);
		}

		// Elevated +2 skybridge with ascending/descending stairs at ends and panoramic canyon glass
		int roofY = FLOOR_Y + 7;
		if (y == roofY + 1) return Blocks.CONCRETE.gray().defaultBlockState();
		if (y == roofY) return ceiling(seed, x, z, tile);
		if (y > roofY + 1) return Blocks.AIR.defaultBlockState();

		boolean midDeck = s >= 3 && s <= 25;
		if (midDeck) {
			if (y <= FLOOR_Y) return Blocks.AIR.defaultBlockState();
			if (y == FLOOR_Y + 1) return Blocks.CONCRETE.gray().defaultBlockState();
			int eh = y - (FLOOR_Y + 2);
			if (eh == 0) {
				if (tile.walkable()) return floor(seed, x, z, tile);
				if (tile.kind() == Kind.PILLAR) return Level4Blocks.OFFICE_PILLAR.defaultBlockState();
				if (tile.kind() == Kind.DRYWALL) return Level4Blocks.OFFICE_DRYWALL.defaultBlockState();
				return Level4Blocks.OFFICE_RADIATOR_WALL.defaultBlockState().setValue(Level4FacingBlock.FACING, dirFrom(tile.dir()));
			}
			if (tile.walkable()) return Blocks.AIR.defaultBlockState();
			return switch (tile.kind()) {
				case PILLAR -> Level4Blocks.OFFICE_PILLAR_UPPER.defaultBlockState();
				case DRYWALL -> Level4Blocks.OFFICE_DRYWALL_UPPER.defaultBlockState();
				case WINDOW_BLACKED -> Level4Blocks.OFFICE_BLACKED_WINDOW.defaultBlockState();
				case WINDOW_TRAP -> Level4Blocks.OFFICE_TRAP_WINDOW.defaultBlockState();
				case WINDOW_RAIN -> Level4Blocks.OFFICE_RAIN_WINDOW.defaultBlockState();
				default -> columnBlock(tile, eh);
			};
		}

		// End ramp spans (s = 1..2 ascending, s = 26..27 descending)
		if (y < FLOOR_Y - 1) return Blocks.AIR.defaultBlockState();
		if (y == FLOOR_Y - 1) return Blocks.CONCRETE.gray().defaultBlockState();
		if (!tile.walkable()) {
			int h = y - FLOOR_Y;
			if (h == 0) return Blocks.CONCRETE.gray().defaultBlockState();
			if (h <= 4) return columnBlock(tile, h);
			if (tile.kind() == Kind.PILLAR) return Level4Blocks.OFFICE_PILLAR_UPPER.defaultBlockState();
			if (tile.kind() == Kind.DRYWALL || h == 6) return Level4Blocks.OFFICE_DRYWALL_UPPER.defaultBlockState();
			return switch (tile.kind()) {
				case WINDOW_BLACKED -> Level4Blocks.OFFICE_BLACKED_WINDOW.defaultBlockState();
				case WINDOW_TRAP -> Level4Blocks.OFFICE_TRAP_WINDOW.defaultBlockState();
				case WINDOW_RAIN -> Level4Blocks.OFFICE_RAIN_WINDOW.defaultBlockState();
				default -> Level4Blocks.OFFICE_DRYWALL_UPPER.defaultBlockState();
			};
		}
		Direction climb = bridge.eastWest()
				? (s <= 2 ? Direction.EAST : Direction.WEST)
				: (s <= 2 ? Direction.SOUTH : Direction.NORTH);
		int stepH = (s == 1 || s == 27) ? 1 : 2;
		int dy = y - FLOOR_Y;
		if (dy < stepH) return Blocks.POLISHED_ANDESITE.defaultBlockState();
		if (dy == stepH) return Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, climb);
		return Blocks.AIR.defaultBlockState();
	}

	private static BlockState floor(long seed, int x, int z, Tile tile) {
		if (tile.zone() == ZoneKind.UNFINISHED_WING) {
			return (Math.floorMod(Level4Layout.hash(seed, x, z, 481), 5) == 0
					? Blocks.CONCRETE.lightGray()
					: Blocks.CONCRETE.gray()).defaultBlockState();
		}
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
		if (!tile.walkable() && tile.kind() != Kind.PILLAR && tile.kind() != Kind.GLASS_WALL
				&& tile.kind() != Kind.CUBICLE_WALL && tile.kind() != Kind.CUBICLE_GLASS
				&& tile.kind() != Kind.UNFINISHED_STUB && tile.kind() != Kind.UNFINISHED_PILLAR
				&& tile.kind() != Kind.FOUNTAIN_POOL
				&& tile.kind() != Kind.FOUNTAIN_BASIN && tile.kind() != Kind.WATER_COOLER && tile.kind() != Kind.VENDING
				&& tile.kind() != Kind.OMEGA_COT && tile.kind() != Kind.OMEGA_LOCKER && tile.kind() != Kind.OMEGA_BARRICADE
				&& tile.kind() != Kind.AMOR_PLANTER && tile.kind() != Kind.AMOR_PLANTER_L1 && tile.kind() != Kind.AMOR_PLANTER_VEIL
				&& tile.kind() != Kind.TBD_SHELF && tile.kind() != Kind.TBD_HOARD && tile.kind() != Kind.BREAKER
				&& tile.kind() != Kind.PHOTOCOPIER && tile.kind() != Kind.DESK_PHONE) {
			return (tile.zone() == ZoneKind.UNFINISHED_WING ? Blocks.CONCRETE.gray() : Level4Blocks.OFFICE_ACOUSTIC_CEILING).defaultBlockState();
		}
		int lx = Math.floorMod(x, Level4Plan.CELL);
		int lz = Math.floorMod(z, Level4Plan.CELL);
		// Troffer strip lights centered in every cell (cx=3, cz=2..4)
		if (lx == 3 && lz >= 2 && lz <= 4) {
			return trofferState(seed, x, z, tile.zone()).setValue(Level4AxisBlock.AXIS, Direction.Axis.Z);
		}
		if (tile.zone() == ZoneKind.UNFINISHED_WING) {
			return ((lx == 0 || lz == 0) ? Blocks.CONCRETE.gray() : Blocks.CONCRETE.lightGray()).defaultBlockState();
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
		if (zone == ZoneKind.UNFINISHED_WING) {
			if (r < 50) return Level4Blocks.OFFICE_TROFFER_BROKEN.defaultBlockState();
			if (r < 80) return Level4Blocks.OFFICE_TROFFER_DIM.defaultBlockState();
			return Level4Blocks.OFFICE_TROFFER_LIT.defaultBlockState();
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
			case AIR, OUTSIDE_AIR -> Blocks.AIR.defaultBlockState();
			case ATRIUM_ARCH -> h <= Level4Layout.OPEN_HEIGHT
					? Blocks.AIR.defaultBlockState()
					: Level4Blocks.OFFICE_DRYWALL_UPPER.defaultBlockState();
			case PILLAR -> (h == 1 ? Level4Blocks.OFFICE_PILLAR : Level4Blocks.OFFICE_PILLAR_UPPER).defaultBlockState();
			case DRYWALL -> (h == 1 ? Level4Blocks.OFFICE_DRYWALL : Level4Blocks.OFFICE_DRYWALL_UPPER).defaultBlockState();
			case GLASS_WALL -> (h <= 3 ? Level4Blocks.OFFICE_GLASS_WALL : Level4Blocks.OFFICE_DRYWALL_UPPER).defaultBlockState();
			case CUBICLE_WALL -> h == 1
					? Level4Blocks.OFFICE_DRYWALL.defaultBlockState()
					: (h == 2 ? Level4Blocks.OFFICE_DRYWALL_UPPER.defaultBlockState() : Blocks.AIR.defaultBlockState());
			case CUBICLE_GLASS -> h == 1
					? Level4Blocks.OFFICE_DRYWALL.defaultBlockState()
					: (h == 2 ? Level4Blocks.OFFICE_GLASS_WALL.defaultBlockState() : Blocks.AIR.defaultBlockState());
			case UNFINISHED_PILLAR -> Blocks.CONCRETE.lightGray().defaultBlockState();
			case UNFINISHED_STUB -> h == 1
					? Blocks.CONCRETE.lightGray().defaultBlockState()
					: (h == 2 ? Blocks.IRON_BARS.defaultBlockState() : Blocks.AIR.defaultBlockState());
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
			case EXIT_STRUCTURE, EXIT_HATCH, BALCONY_FLOOR, BALCONY_CANOPY, BALCONY_RAIL -> Blocks.AIR.defaultBlockState();
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
			case AMOR_PLANTER_L1 -> {
				if (h == 1) yield Level4Blocks.AMOR_PLANTER_L1.defaultBlockState();
				if (h == 2) yield Level4Blocks.AMOR_MUSHROOM_MOSS.defaultBlockState();
				yield Blocks.AIR.defaultBlockState();
			}
			case AMOR_PLANTER_VEIL -> {
				if (h == 1) yield Level4Blocks.AMOR_PLANTER.defaultBlockState();
				if (h == 2) yield Level4Blocks.AMOR_MUSHROOM_VEIL.defaultBlockState();
				yield Blocks.AIR.defaultBlockState();
			}
			case TBD_SHELF -> h == 1
					? Level4Blocks.TBD_CURIO_SHELF.defaultBlockState().setValue(Level4FacingBlock.FACING, dir)
					: Blocks.AIR.defaultBlockState();
			case TBD_HOARD -> (h == 1 || h == 2)
					? Level4Blocks.TBD_HOARD_CRATE.defaultBlockState().setValue(Level4FacingBlock.FACING, dir)
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

	private static BlockState exitStructureBlock(Level4Layout.ExitStruct es, int y) {
		Direction intoWall = dirFrom(es.dir());
		Direction intoRoom = intoWall.getOpposite();
		Block wallLow = es.zone() == ZoneKind.OMEGA_BASE ? Level4Blocks.OMEGA_WALL : Level4Blocks.OFFICE_DRYWALL;
		Block wallUp = es.zone() == ZoneKind.OMEGA_BASE ? Level4Blocks.OMEGA_WALL : Level4Blocks.OFFICE_DRYWALL_UPPER;
		int d = es.depth(), a = es.along();

		if (es.exit() == Level4Exit.ELEVATOR_3) {
			if (y < FLOOR_Y || y > FLOOR_Y + Level4Layout.OPEN_HEIGHT + 1) return Blocks.CONCRETE.gray().defaultBlockState();
			if (y == FLOOR_Y + Level4Layout.OPEN_HEIGHT + 1) return Level4Blocks.OFFICE_ACOUSTIC_CEILING.defaultBlockState();
			if (d == 0) {
				if (y == FLOOR_Y) return Blocks.POLISHED_ANDESITE.defaultBlockState();
				if (a == 3 && (y == FLOOR_Y + 1 || y == FLOOR_Y + 2)) {
					return Level4Blocks.OFFICE_ELEVATOR.defaultBlockState()
							.setValue(Level4ExitBlock.PART, y - (FLOOR_Y + 1))
							.setValue(Level4ExitBlock.FACING, intoRoom);
				}
				return Level4Blocks.OMEGA_WALL.defaultBlockState();
			}
			// depth == 1: walk-in elevator cabin
			if (y == FLOOR_Y) return Blocks.POLISHED_ANDESITE.defaultBlockState();
			if (a == 2 || a == 4) {
				return (y == FLOOR_Y + 4 ? wallUp : Level4Blocks.OMEGA_WALL).defaultBlockState();
			}
			if (y == FLOOR_Y + 3) {
				return Level4Blocks.OFFICE_TROFFER_LIT.defaultBlockState().setValue(Level4AxisBlock.AXIS, intoWall.getAxis());
			}
			if (y == FLOOR_Y + 4) return wallUp.defaultBlockState();
			return Blocks.AIR.defaultBlockState();
		}

		if (es.exit() == Level4Exit.BASEMENT_71) {
			if (y < FLOOR_Y - 3 || y > FLOOR_Y + Level4Layout.OPEN_HEIGHT + 1) return Blocks.CONCRETE.gray().defaultBlockState();
			if (y == FLOOR_Y + Level4Layout.OPEN_HEIGHT + 1) return Level4Blocks.OFFICE_ACOUSTIC_CEILING.defaultBlockState();
			if (d == 0) {
				if (a == 3 && (y == FLOOR_Y - 1 || y == FLOOR_Y)) {
					return Level4Blocks.OFFICE_BASEMENT_71.defaultBlockState()
							.setValue(Level4ExitBlock.PART, y - (FLOOR_Y - 1))
							.setValue(Level4ExitBlock.FACING, intoRoom);
				}
				if (y <= FLOOR_Y) return Blocks.STONE_BRICKS.defaultBlockState();
				return (y == FLOOR_Y + 1 ? wallLow : wallUp).defaultBlockState();
			}
			if (d == 1) {
				if (a == 2 || a == 4) {
					if (y <= FLOOR_Y) return Blocks.STONE_BRICKS.defaultBlockState();
					return (y == FLOOR_Y + 1 ? wallLow : wallUp).defaultBlockState();
				}
				if (y <= FLOOR_Y - 2) return Blocks.STONE_BRICKS.defaultBlockState();
				if (y == FLOOR_Y + 3) {
					return Level4Blocks.OFFICE_TROFFER_DIM.defaultBlockState().setValue(Level4AxisBlock.AXIS, intoWall.getAxis());
				}
				if (y == FLOOR_Y + 4) return wallUp.defaultBlockState();
				return Blocks.AIR.defaultBlockState();
			}
			if (d == 2) {
				if (a == 2 || a == 4) {
					if (y <= FLOOR_Y) return Blocks.STONE_BRICKS.defaultBlockState();
					return (y == FLOOR_Y + 1 ? wallLow : wallUp).defaultBlockState();
				}
				if (y <= FLOOR_Y - 2) return Blocks.STONE_BRICKS.defaultBlockState();
				if (y == FLOOR_Y - 1) return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, intoRoom);
				return Blocks.AIR.defaultBlockState();
			}
			// d == 3, a == 3: descending entry step
			if (y < FLOOR_Y) return Blocks.CONCRETE.gray().defaultBlockState();
			if (y == FLOOR_Y) return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, intoRoom);
			return Blocks.AIR.defaultBlockState();
		}

		// Ascending office stairwell (Level 5 / Level 6)
		if (y < FLOOR_Y || y > FLOOR_Y + 6) return Blocks.CONCRETE.gray().defaultBlockState();
		if (d == 0) {
			if (y == FLOOR_Y) return Blocks.POLISHED_ANDESITE.defaultBlockState();
			return (y == FLOOR_Y + 1 ? wallLow : wallUp).defaultBlockState();
		}
		if (d == 1) {
			if (y == FLOOR_Y) return Blocks.POLISHED_ANDESITE.defaultBlockState();
			if (a == 2 || a == 4) return (y == FLOOR_Y + 1 ? wallLow : wallUp).defaultBlockState();
			if (y == FLOOR_Y + 1 || y == FLOOR_Y + 2) return Blocks.POLISHED_ANDESITE.defaultBlockState();
			if (y == FLOOR_Y + 3 || y == FLOOR_Y + 4) {
				return exitBlock(es.exit()).defaultBlockState()
						.setValue(Level4ExitBlock.PART, y - (FLOOR_Y + 3))
						.setValue(Level4ExitBlock.FACING, intoRoom);
			}
			return wallUp.defaultBlockState();
		}
		if (d == 2) {
			if (y == FLOOR_Y) return Blocks.POLISHED_ANDESITE.defaultBlockState();
			if (a == 2 || a == 4) return (y == FLOOR_Y + 1 ? wallLow : wallUp).defaultBlockState();
			if (y == FLOOR_Y + 1) return Blocks.POLISHED_ANDESITE.defaultBlockState();
			if (y == FLOOR_Y + 2) return Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, intoWall);
			if (y == FLOOR_Y + 5) {
				return Level4Blocks.OFFICE_TROFFER_LIT.defaultBlockState().setValue(Level4AxisBlock.AXIS, intoWall.getAxis());
			}
			if (y == FLOOR_Y + 6) return Level4Blocks.OFFICE_ACOUSTIC_CEILING.defaultBlockState();
			return Blocks.AIR.defaultBlockState();
		}
		// d == 3, a == 3: ascending entry step
		if (y == FLOOR_Y) return Level4Blocks.OFFICE_CARPET.defaultBlockState();
		if (y == FLOOR_Y + 1) return Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, intoWall);
		if (y == FLOOR_Y + 5) return Level4Blocks.OFFICE_ACOUSTIC_CEILING.defaultBlockState();
		if (y == FLOOR_Y + 6) return Blocks.CONCRETE.gray().defaultBlockState();
		return Blocks.AIR.defaultBlockState();
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
			int wx = minX + x;
			for (int z = 0; z < 16; z++) {
				int wz = minZ + z;
				Level4Layout.ExitStruct es = Level4Layout.exitStructure(seed, wx, wz);
				Tile tile = Level4Layout.tile(seed, wx, wz);
				Level4Layout.BridgeInfo bridge = Level4Layout.skybridgeInfo(seed, wx, wz);
				boolean exterior = Level4Layout.isExteriorSkin(seed, wx, wz);
				int topY = towerTopY(seed, wx, wz);
				for (int y = MIN_Y; y <= MAX_Y; y++) {
					BlockState state = blockAtColumn(seed, wx, y, wz, tile, es, bridge, exterior, topY);
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
		Level4Layout.ExitStruct es = Level4Layout.exitStructure(seed, x, z);
		Tile tile = Level4Layout.tile(seed, x, z);
		Level4Layout.BridgeInfo bridge = Level4Layout.skybridgeInfo(seed, x, z);
		boolean exterior = Level4Layout.isExteriorSkin(seed, x, z);
		int topY = towerTopY(seed, x, z);
		for (int i = 0; i < HEIGHT; i++) {
			states[i] = blockAtColumn(seed, x, MIN_Y + i, z, tile, es, bridge, exterior, topY);
		}
		return new NoiseColumn(MIN_Y, states);
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
		return FLOOR_Y + Level4Layout.OPEN_HEIGHT + 2;
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
