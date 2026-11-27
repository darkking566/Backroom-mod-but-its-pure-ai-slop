package dev.noclip.backrooms.level4;

import com.mojang.serialization.MapCodec;
import dev.noclip.backrooms.level1.Level1Blocks;
import dev.noclip.backrooms.level4.Level4Layout.Kind;
import dev.noclip.backrooms.level4.Level4Layout.Tile;
import dev.noclip.backrooms.level4.Level4Plan.ZoneKind;
import dev.noclip.backrooms.level4.Level4PoiPlan.PlacedPoi;
import dev.noclip.backrooms.level4.Level4PoiPlan.PoiKind;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
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
				if (Level4PoiPlan.isInvertedLightwell(seed, x, z)) {
					int cx = Math.floorMod(x, Level4Plan.CELL), cz = Math.floorMod(z, Level4Plan.CELL);
					boolean rim = (cx == 1 || cx == 5 || cz == 1 || cz == 5) && cx >= 1 && cx <= 5 && cz >= 1 && cz <= 5;
					if (rim) {
						boolean corner = (cx == 1 || cx == 5) && (cz == 1 || cz == 5);
						if (y == FLOOR_Y + 7) {
							if (corner) return Level4Blocks.OFFICE_PILLAR.defaultBlockState();
							if (cx == 3 || cz == 3) {
								return Level4Blocks.OFFICE_TROFFER_LIT.defaultBlockState()
										.setValue(Level4AxisBlock.AXIS, cx == 3 ? Direction.Axis.Z : Direction.Axis.X);
							}
							return Blocks.AIR.defaultBlockState();
						}
						if (y == FLOOR_Y + 8) {
							return (corner ? Level4Blocks.OFFICE_PILLAR : Level4Blocks.OFFICE_DARK_MIRROR).defaultBlockState();
						}
						if (y == FLOOR_Y + 9 || y == FLOOR_Y + 10) {
							return (corner ? Level4Blocks.OFFICE_PILLAR_UPPER : Level4Blocks.OFFICE_RAIN_WINDOW).defaultBlockState();
						}
						if (y == FLOOR_Y + 11) {
							return ((cx == 3 || cz == 3)
									? Level4Blocks.OFFICE_TROFFER_LIT.defaultBlockState().setValue(Level4AxisBlock.AXIS,
											cx == 3 ? Direction.Axis.Z : Direction.Axis.X)
									: Level4Blocks.OFFICE_ACOUSTIC_CEILING.defaultBlockState());
						}
						if (y == FLOOR_Y + 12) return Level4Blocks.OFFICE_DARK_MIRROR.defaultBlockState();
						if (y == FLOOR_Y + 13) return Level4Blocks.OFFICE_CARPET.defaultBlockState();
					}
				}
				if (y < FLOOR_Y) return Blocks.CONCRETE.gray().defaultBlockState();
				if (y == FLOOR_Y) return Blocks.WATER.defaultBlockState();
			}
			return Blocks.AIR.defaultBlockState();
		}
		PlacedPoi poi = es == null ? Level4PoiPlan.atCell(seed, x, z) : null;
		if (tile.kind() == Kind.BALCONY_RAIL || tile.kind() == Kind.BALCONY_FLOOR || tile.kind() == Kind.BALCONY_CANOPY) {
			if (poi != null && poi.kind() == PoiKind.MISSING_WANDERER_CAMP) {
				BlockState p = poiBlock(x, y, z, tile, poi);
				if (p != null) return p;
			}
			return balconyColumn(y, tile.kind(), exterior);
		}
		if (y >= MAX_Y) return Blocks.BEDROCK.defaultBlockState();

		if (bridge != null && !exterior) {
			return bridgeColumn(seed, x, y, z, tile, bridge);
		}

		if (tile.kind() == Kind.ELEVATOR_SHAFT || tile.kind() == Kind.ELEVATOR_CABLE || tile.kind() == Kind.ELEVATOR_SHAFT_WALL) {
			return elevatorShaftColumn(x, y, topY, tile.kind(), poi);
		}

		if (es != null && y >= FLOOR_Y - 3 && y <= FLOOR_Y + 6) {
			return exitStructureBlock(es, y);
		}

		if (y > topY + 1) return Blocks.AIR.defaultBlockState();
		if (y == topY + 1) return exterior ? Blocks.CONCRETE.lightGray().defaultBlockState() : Blocks.AIR.defaultBlockState();
		if (y == topY) return Blocks.CONCRETE.gray().defaultBlockState();

		int roofY = FLOOR_Y + tile.ceilingHeight() + 1;
		PlacedPoi abyssPoi = es == null ? Level4PoiPlan.subtunnelAbyssAt(seed, x, z) : null;
		if (abyssPoi != null && y >= FLOOR_Y - 18 && y <= FLOOR_Y - 2) {
			BlockState abyss = subtunnelAbyssBlock(x, y, z, abyssPoi);
			if (abyss != null) return abyss;
		}
		Level4Layout.SubTunnelInfo sub = es == null ? Level4Layout.subTunnelInfo(seed, x, z) : null;
		if (sub != null) {
			BlockState st = subTunnelBlock(x, y, z, sub);
			if (st != null) return st;
		}
		Level4Layout.PlenumInfo plenum = tile.zone() == ZoneKind.DARK_WING && es == null ? Level4Layout.plenumInfo(seed, x, z) : null;
		if (plenum != null) {
			BlockState pl = plenumBlock(x, y, z, plenum, poi);
			if (pl != null) return pl;
		}
		Level4Layout.MezzanineInfo mezz = tile.kind() == Kind.AIR ? Level4Layout.mezzanineInfo(seed, x, z) : null;
		if (mezz != null && y >= FLOOR_Y && y <= roofY) {
			if (poi != null && (poi.kind() == PoiKind.MISSING_WANDERER_CAMP || poi.kind() == PoiKind.DELAYED_REFLECTION_MIRROR)
					&& poi.baseY() == FLOOR_Y + 6) {
				BlockState p = poiBlock(x, y, z, tile, poi);
				if (p != null) return p;
			}
			return mezzanineColumn(seed, x, y, z, tile, mezz, roofY);
		}
		Level4Layout.AuditoriumInfo aud = tile.kind() == Kind.AIR ? Level4Layout.auditoriumInfo(seed, x, z) : null;
		if (aud != null) {
			return auditoriumColumn(seed, x, y, z, tile, aud, roofY);
		}

		if (poi != null && poi.kind() == PoiKind.INVERTED_MIRROR_LIGHTWELL && y >= FLOOR_Y && y <= FLOOR_Y + 10) {
			return invertedLightwellColumn(seed, x, y, z, tile, poi);
		}
		if (y >= FLOOR_Y && y <= roofY) {
			int execEdge = Level4PoiPlan.executiveAuditoriumEdgeKind(seed, x, z);
			if (execEdge > 0 && (tile.kind() == Kind.DRYWALL || tile.kind() == Kind.GLASS_WALL)) {
				if (y == FLOOR_Y + 1 || y == FLOOR_Y + 4) return Blocks.DARK_OAK_PLANKS.defaultBlockState();
				if (y == FLOOR_Y + 2 || y == FLOOR_Y + 3) {
					return (execEdge == 2 ? Level4Blocks.OFFICE_GLASS_WALL : Blocks.DARK_OAK_PLANKS).defaultBlockState();
				}
			}
			if (poi != null && poi.kind() == PoiKind.CORRUPTED_RED_ATRIUM) {
				if (y == roofY) {
					BlockState normalCeil = ceiling(seed, x, z, tile);
					if (normalCeil.is(Level4Blocks.OFFICE_TROFFER_LIT)
							|| normalCeil.is(Level4Blocks.OFFICE_TROFFER_DIM)
							|| normalCeil.is(Level4Blocks.OFFICE_TROFFER_FLICKER)) {
						return Level4Blocks.OFFICE_TROFFER_BROKEN.defaultBlockState()
								.setValue(Level4AxisBlock.AXIS, normalCeil.getValue(Level4AxisBlock.AXIS));
					}
				}
				if (tile.kind() == Kind.FOUNTAIN_POOL && y == FLOOR_Y + 1) {
					return Level4Blocks.CORRUPTED_FOUNTAIN_POOL.defaultBlockState();
				}
				if (tile.kind() == Kind.FOUNTAIN_BASIN) {
					if (y == FLOOR_Y + 1) return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
					if (y == FLOOR_Y + 2) return Blocks.POTTED_DEAD_BUSH.defaultBlockState();
				}
				if (y == FLOOR_Y && tile.kind() == Kind.AIR) {
					int cx = Math.floorMod(x, Level4Plan.CELL), cz = Math.floorMod(z, Level4Plan.CELL);
					if (cx >= 1 && cx <= 5 && cz >= 1 && cz <= 5) {
						return Level4Blocks.OFFICE_DAMP_CARPET.defaultBlockState();
					}
				}
			}
			if (poi != null && poi.kind() == PoiKind.FROZEN_FRIDAY_1998) {
				if (y == roofY) {
					BlockState normalCeil = ceiling(seed, x, z, tile);
					if (normalCeil.is(Level4Blocks.OFFICE_TROFFER_BROKEN)
							|| normalCeil.is(Level4Blocks.OFFICE_TROFFER_DIM)
							|| normalCeil.is(Level4Blocks.OFFICE_TROFFER_FLICKER)) {
						return Level4Blocks.OFFICE_TROFFER_LIT.defaultBlockState()
								.setValue(Level4AxisBlock.AXIS, normalCeil.getValue(Level4AxisBlock.AXIS));
					}
				}
				if (y == FLOOR_Y && tile.walkable()) {
					return Level4Blocks.OFFICE_CARPET.defaultBlockState();
				}
			}
			if (poi != null && poi.kind() == PoiKind.EXECUTIVE_BROADCAST_SUITE) {
				BlockState exec = executiveSuiteBlock(seed, x, y, z, roofY, tile, poi);
				if (exec != null) return exec;
			}
			if (poi != null) {
				BlockState p = poiBlock(x, y, z, tile, poi);
				if (p != null) return p;
			}
			if (tile.kind() == Kind.FIRE_DOOR && (y == FLOOR_Y + 1 || y == FLOOR_Y + 2)) {
				if (isSealedMeetingDoor(seed, x, z)) {
					return Level4Blocks.CAUTION_TAPE_BARRIER.defaultBlockState()
							.setValue(Level4FacingBlock.FACING, dirFrom(tile.dir()));
				}
				if (isPbxRoomEdge(seed, x, z)) {
					return Blocks.IRON_DOOR.defaultBlockState()
							.setValue(DoorBlock.FACING, dirFrom(tile.dir()))
							.setValue(DoorBlock.HALF, y == FLOOR_Y + 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
							.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
							.setValue(DoorBlock.OPEN, false)
							.setValue(DoorBlock.POWERED, false);
				}
			}
			if (tile.kind() == Kind.GLASS_WALL && (y == FLOOR_Y + 2 || y == FLOOR_Y + 3) && isPbxRoomEdge(seed, x, z)) {
				return Blocks.IRON_BARS.defaultBlockState();
			}
			if (y == FLOOR_Y) return floor(seed, x, z, tile);
			if (y == roofY) return ceiling(seed, x, z, tile);
			if (tile.kind() == Kind.ELEVATOR_THRESHOLD) {
				return elevatorThresholdBlock(x, y - FLOOR_Y, poi);
			}
			if (tile.kind() == Kind.WINDOW_DARK_MIRROR) {
				return darkMirrorWindowColumn(x, z, y - FLOOR_Y, dirFrom(tile.dir()));
			}
			return columnBlock(tile, y - FLOOR_Y);
		}
		if (poi != null && poi.kind() == PoiKind.INVERTED_MIRROR_LIGHTWELL && y >= FLOOR_Y + 11 && y <= FLOOR_Y + 21) {
			int mh = (y - (FLOOR_Y + 11)) % 5;
			int cx = Math.floorMod(x, Level4Plan.CELL), cz = Math.floorMod(z, Level4Plan.CELL);
			if (tile.walkable()) {
				if (mh == 0) {
					return (cx == 3 && cz >= 2 && cz <= 4)
							? Level4Blocks.OFFICE_TROFFER_LIT.defaultBlockState().setValue(Level4AxisBlock.AXIS, Direction.Axis.Z)
							: Level4Blocks.OFFICE_ACOUSTIC_CEILING.defaultBlockState();
				}
				if (mh == 4) return Level4Blocks.OFFICE_CARPET.defaultBlockState();
				return Blocks.AIR.defaultBlockState();
			} else if (tile.kind() == Kind.WINDOW_RAIN || tile.kind() == Kind.WINDOW_BLACKED || tile.kind() == Kind.WINDOW_TRAP) {
				if (mh == 1 || mh == 2) return Level4Blocks.OFFICE_RAIN_WINDOW.defaultBlockState();
				if (mh == 3) return Level4Blocks.OFFICE_RADIATOR_WALL.defaultBlockState().setValue(Level4FacingBlock.FACING, dirFrom(tile.dir()));
				return Level4Blocks.OFFICE_DRYWALL_UPPER.defaultBlockState();
			}
		}
		if (poi != null && poi.kind() == PoiKind.CEILING_DRIP_SPOT && !poi.variantFlag()
				&& y == FLOOR_Y + 6 && Math.floorMod(x, Level4Plan.CELL) == 3 && Math.floorMod(z, Level4Plan.CELL) == 3) {
			return Blocks.IRON_BARS.defaultBlockState();
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

	private static BlockState mezzanineColumn(long seed, int x, int y, int z, Tile tile,
			Level4Layout.MezzanineInfo mezz, int roofY) {
		if (y == FLOOR_Y) return floor(seed, x, z, tile);
		if (y == roofY) return ceiling(seed, x, z, tile);
		if (mezz.stairStep() > 0) {
			int topStair = FLOOR_Y + mezz.stairStep();
			if (y < topStair) return Blocks.POLISHED_ANDESITE.defaultBlockState();
			if (y == topStair) return Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
			return Blocks.AIR.defaultBlockState();
		}
		if (mezz.overlookOpen()) return Blocks.AIR.defaultBlockState();
		if (y <= FLOOR_Y + 4) return Blocks.AIR.defaultBlockState();
		if (y == FLOOR_Y + 5) return ceiling(seed, x, z, tile);
		if (y == FLOOR_Y + 6) return Level4Blocks.OFFICE_CARPET.defaultBlockState();
		if ((mezz.overlookRail() || mezz.stairRail()) && y == FLOOR_Y + 7) {
			return Level4Blocks.OFFICE_GLASS_WALL.defaultBlockState();
		}
		if (mezz.upperGlassWall() && y <= FLOOR_Y + 9) {
			return Level4Blocks.OFFICE_GLASS_WALL.defaultBlockState();
		}
		return Blocks.AIR.defaultBlockState();
	}

	private static BlockState plenumBlock(int x, int y, int z, Level4Layout.PlenumInfo plenum, PlacedPoi poi) {
		if (plenum.ladderDir() >= 0 && y >= FLOOR_Y + 1 && y <= FLOOR_Y + 7) {
			return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, dirFrom(plenum.ladderDir()));
		}
		if (poi != null && poi.kind() == PoiKind.PLENUM_MAINTENANCE_HUB) {
			int cx = Math.floorMod(x, Level4Plan.CELL), cz = Math.floorMod(z, Level4Plan.CELL);
			if (cx >= 2 && cx <= 4 && cz >= 2 && cz <= 4) {
				if (y == FLOOR_Y + 5) {
					return (cx == 3 && cz == 3 ? Blocks.IRON_BARS : Blocks.POLISHED_ANDESITE).defaultBlockState();
				}
				if (y == FLOOR_Y + 6 && cx == 2 && cz == 2) {
					return Level4Blocks.INDUSTRIAL_TOOLBOX.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.SOUTH);
				}
				if (y == FLOOR_Y + 8 && cx == 4 && cz == 4) {
					return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
				}
			}
		}
		if (plenum.ventGrate() && y == FLOOR_Y + 5) {
			return Blocks.IRON_BARS.defaultBlockState();
		}
		if (y >= FLOOR_Y + 6 && y <= FLOOR_Y + 9) {
			if (y == FLOOR_Y + 9) {
				if (!plenum.wallShell() && Math.floorMod(x, Level4Plan.CELL) == 3 && Math.floorMod(z, Level4Plan.CELL) == 3) {
					return Level4Blocks.OFFICE_TROFFER_DIM.defaultBlockState().setValue(Level4AxisBlock.AXIS, Direction.Axis.Z);
				}
				return Blocks.CONCRETE.gray().defaultBlockState();
			}
			if (plenum.wallShell()) return Blocks.CONCRETE.gray().defaultBlockState();
			if (plenum.duct() && y == FLOOR_Y + 8) return Blocks.CONCRETE.lightGray().defaultBlockState();
			return Blocks.AIR.defaultBlockState();
		}
		return null;
	}

	private static BlockState subTunnelBlock(int x, int y, int z, Level4Layout.SubTunnelInfo sub) {
		if (sub.stair() && y >= FLOOR_Y - 7 && y <= FLOOR_Y + 4) {
			if (y == FLOOR_Y - 7) return Blocks.CONCRETE.gray().defaultBlockState();
			if (y < sub.stepY()) return Blocks.STONE_BRICKS.defaultBlockState();
			if (y == sub.stepY()) {
				return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, dirFrom(sub.stairDir()));
			}
			return Blocks.AIR.defaultBlockState();
		}
		if (sub.stairRail()) {
			if (y == FLOOR_Y + 1) return Blocks.IRON_BARS.defaultBlockState();
			if (y >= FLOOR_Y - 7 && y < FLOOR_Y) return Blocks.CONCRETE.gray().defaultBlockState();
			return null;
		}
		if (y < FLOOR_Y - 7 || y >= FLOOR_Y) return null;
		if (sub.wallSide()) {
			if (y == FLOOR_Y - 4 && Math.floorMod(x, 2) == 0) return Blocks.IRON_BARS.defaultBlockState();
			return Blocks.CONCRETE.lightGray().defaultBlockState();
		}
		if (sub.corridor()) {
			if (y == FLOOR_Y - 7 || y == FLOOR_Y - 1) return Blocks.CONCRETE.gray().defaultBlockState();
			if (y == FLOOR_Y - 6) {
				return (Math.floorMod(z, Level4Plan.CELL) == 3 ? Level4Blocks.OFFICE_DAMP_CARPET : Blocks.CONCRETE.gray()).defaultBlockState();
			}
			if (y == FLOOR_Y - 2) {
				return sub.light()
						? Level4Blocks.OFFICE_TROFFER_DIM.defaultBlockState().setValue(Level4AxisBlock.AXIS, Direction.Axis.X)
						: Blocks.CONCRETE.gray().defaultBlockState();
			}
			return Blocks.AIR.defaultBlockState();
		}
		return null;
	}

	private static BlockState subtunnelAbyssBlock(int x, int y, int z, PlacedPoi poi) {
		int cx = Math.floorMod(x, Level4Plan.CELL);
		int dz = Math.floorMod(z, Level4Plan.SIZE) - poi.cellJ() * Level4Plan.CELL;
		if (dz == 4) {
			if (cx == 2 && y == FLOOR_Y - 5) {
				return Level4Blocks.INDUSTRIAL_TOOLBOX.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.NORTH);
			}
			return null;
		}
		if (dz == 5) {
			if ((cx == 1 || cx == 5) && (y == FLOOR_Y - 5 || y == FLOOR_Y - 4)) {
				return Level4Blocks.OFFICE_ELEVATOR_FRAME.defaultBlockState();
			}
			if ((cx == 1 || cx == 5) && y == FLOOR_Y - 3) {
				return Blocks.IRON_BARS.defaultBlockState();
			}
			if ((cx == 2 || cx == 4) && (y == FLOOR_Y - 5 || y == FLOOR_Y - 4)) {
				return ironBars(false, true, false, false);
			}
			if (cx == 3) {
				if (y == FLOOR_Y - 6) return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
				if (y == FLOOR_Y - 5 || y == FLOOR_Y - 4 || y == FLOOR_Y - 3) return Blocks.AIR.defaultBlockState();
			}
			return null;
		}
		if (dz < 6 || dz > 12) return null;
		if (y == FLOOR_Y - 2) return Blocks.CONCRETE.gray().defaultBlockState();
		if (cx == 0 || cx == 6) return Blocks.CONCRETE.gray().defaultBlockState();
		if (dz == 12) {
			if (cx == 3 && (y == FLOOR_Y - 11 || y == FLOOR_Y - 10)) {
				return Level4Blocks.OFFICE_BASEMENT_71.defaultBlockState()
						.setValue(Level4ExitBlock.PART, y - (FLOOR_Y - 11))
						.setValue(Level4ExitBlock.FACING, Direction.NORTH);
			}
			return Blocks.CONCRETE.gray().defaultBlockState();
		}
		if (y <= FLOOR_Y - 16) return Blocks.CONCRETE.black().defaultBlockState();
		if (cx == 1 || cx == 5) {
			int railY = (dz == 11) ? FLOOR_Y - 11 : (FLOOR_Y - 6 - (dz - 5) + 1);
			if (y == railY) return ironBars(true, dz < 11, false, false);
			return Blocks.AIR.defaultBlockState();
		}
		if (dz == 11) {
			if (y == FLOOR_Y - 12) return Blocks.STONE_BRICKS.defaultBlockState();
			if (y == FLOOR_Y - 7 && cx == 3) return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
			return Blocks.AIR.defaultBlockState();
		}
		int stepY = FLOOR_Y - 6 - (dz - 5);
		if (y == stepY) {
			return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
		}
		if (y == stepY - 1) return Blocks.STONE_BRICKS.defaultBlockState();
		if (y == FLOOR_Y - 3 && cx == 3 && dz == 8) {
			return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
		}
		return Blocks.AIR.defaultBlockState();
	}

	private static BlockState elevatorShaftColumn(int x, int y, int topY, Kind kind, PlacedPoi poi) {
		int minShaft = Math.max(MIN_Y + 2, FLOOR_Y - 22);
		int maxShaft = Math.min(topY - 1, FLOOR_Y + 28);
		if (y < minShaft || y > maxShaft) {
			return (y > topY) ? Blocks.AIR.defaultBlockState() : Blocks.CONCRETE.gray().defaultBlockState();
		}
		if (kind == Kind.ELEVATOR_SHAFT_WALL) {
			if (poi != null && poi.kind() == PoiKind.STALLED_FREIGHT_ELEVATOR) {
				return (Math.floorMod(y, 3) == 0 ? Level4Blocks.OFFICE_ELEVATOR_FRAME : Blocks.CRACKED_STONE_BRICKS).defaultBlockState();
			}
			return Blocks.CONCRETE.gray().defaultBlockState();
		}
		if (poi != null && poi.kind() == PoiKind.STALLED_FREIGHT_ELEVATOR) {
			int cx = Math.floorMod(x, Level4Plan.CELL);
			if (y == FLOOR_Y - 4) return Level4Blocks.OFFICE_ELEVATOR_FRAME.defaultBlockState();
			if (y == FLOOR_Y - 3) {
				if (cx == 2) return Blocks.REDSTONE_TORCH.defaultBlockState();
				if (cx == 3) return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
				if (cx == 4) return Level4Blocks.MEG_SUPPLY_CRATE.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.WEST);
			}
			if (y == FLOOR_Y - 2 || y == FLOOR_Y - 1) {
				return cx == 3
						? Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH)
						: Blocks.AIR.defaultBlockState();
			}
			if (y == FLOOR_Y) {
				if (cx == 3) {
					return Blocks.IRON_TRAPDOOR.defaultBlockState()
							.setValue(TrapDoorBlock.FACING, Direction.NORTH)
							.setValue(TrapDoorBlock.OPEN, true)
							.setValue(TrapDoorBlock.HALF, Half.TOP);
				}
				return Level4Blocks.OFFICE_ELEVATOR_FRAME.defaultBlockState();
			}
			if (y == FLOOR_Y + 1 || y == FLOOR_Y + 2) return Blocks.AIR.defaultBlockState();
			if (y == FLOOR_Y + 3) {
				if (cx == 2) return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
				if (cx == 4) return Blocks.IRON_BARS.defaultBlockState();
				return Blocks.AIR.defaultBlockState();
			}
			if (y >= FLOOR_Y + 4 && y <= FLOOR_Y + 12 && (cx == 2 || cx == 4)) {
				return Blocks.IRON_BARS.defaultBlockState();
			}
		}
		if (kind == Kind.ELEVATOR_CABLE) return Blocks.IRON_BARS.defaultBlockState();
		return Blocks.AIR.defaultBlockState();
	}

	private static BlockState elevatorThresholdBlock(int x, int h, PlacedPoi poi) {
		int cx = Math.floorMod(x, Level4Plan.CELL);
		if (cx == 1 || cx == 5 || cx == 2 || h >= 3) return Level4Blocks.OFFICE_ELEVATOR_FRAME.defaultBlockState();
		if (poi != null && poi.kind() == PoiKind.STALLED_FREIGHT_ELEVATOR) {
			if (cx == 3 && (h == 1 || h == 2)) return Blocks.AIR.defaultBlockState();
			if (cx == 4 && h == 1) return ironBars(false, false, true, false);
			if (cx == 4 && h == 2) {
				return Level4Blocks.CAUTION_TAPE_BARRIER.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.SOUTH);
			}
			return Blocks.AIR.defaultBlockState();
		}
		if (h == 1) return ironBars(false, false, true, true);
		return Blocks.AIR.defaultBlockState();
	}

	private static BlockState auditoriumColumn(long seed, int x, int y, int z, Tile tile,
			Level4Layout.AuditoriumInfo aud, int roofY) {
		if (y > roofY) return Blocks.CONCRETE.gray().defaultBlockState();
		if (y == roofY) return ceiling(seed, x, z, tile);
		if (aud.screenWall() && y >= FLOOR_Y - 1 && y <= FLOOR_Y + 3) {
			boolean border = aud.rx() == 5 || aud.rx() == 15 || y == FLOOR_Y - 1 || y == FLOOR_Y + 3;
			return (border ? Blocks.CONCRETE.gray() : Blocks.CONCRETE.lightGray()).defaultBlockState();
		}
		if (y < aud.floorY()) return Blocks.CONCRETE.gray().defaultBlockState();
		if (y == aud.floorY()) {
			return (aud.stage() ? Blocks.POLISHED_ANDESITE : Level4Blocks.OFFICE_CARPET).defaultBlockState();
		}
		if (aud.stair() && y == aud.floorY() + 1) {
			return Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, dirFrom(aud.stairDir()));
		}
		return Blocks.AIR.defaultBlockState();
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
		if (zone == ZoneKind.AUDITORIUM) {
			return (r < 42 ? Level4Blocks.OFFICE_TROFFER_DIM : Level4Blocks.OFFICE_TROFFER_BROKEN).defaultBlockState();
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
			case WINDOW_DARK_MIRROR -> windowColumn(Level4Blocks.OFFICE_DARK_MIRROR, h, dir);
			case WINDOW_BREACHED -> {
				if (h == 1) yield Level4Blocks.OFFICE_RADIATOR_WALL.defaultBlockState().setValue(Level4FacingBlock.FACING, dir);
				if (h == 2 || h == 3) yield Blocks.AIR.defaultBlockState();
				yield Level4Blocks.OFFICE_DRYWALL_UPPER.defaultBlockState();
			}
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
			case EXIT_STRUCTURE, EXIT_HATCH, BALCONY_FLOOR, BALCONY_CANOPY, BALCONY_RAIL,
					ELEVATOR_SHAFT, ELEVATOR_CABLE, ELEVATOR_SHAFT_WALL, ELEVATOR_THRESHOLD -> Blocks.AIR.defaultBlockState();
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
				return Level4Blocks.OFFICE_ELEVATOR_FRAME.defaultBlockState();
			}
			// depth == 1: walk-in elevator cabin
			if (y == FLOOR_Y) return Blocks.POLISHED_ANDESITE.defaultBlockState();
			if (a == 2 || a == 4) {
				return (y == FLOOR_Y + 4 ? wallUp : Level4Blocks.OFFICE_ELEVATOR_FRAME).defaultBlockState();
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

	private static BlockState darkMirrorWindowColumn(int x, int z, int h, Direction facing) {
		if (h == 1) return Level4Blocks.OFFICE_RADIATOR_WALL.defaultBlockState().setValue(Level4FacingBlock.FACING, facing);
		if (h == 2 || h == 3) {
			boolean center = Math.floorMod(x, Level4Plan.CELL) == 3 || Math.floorMod(z, Level4Plan.CELL) == 3;
			int sil = center ? (h == 2 ? 1 : 2) : 0;
			return Level4Blocks.OFFICE_DARK_MIRROR.defaultBlockState()
					.setValue(Level4Blocks.OfficeDarkMirrorBlock.SILHOUETTE, sil);
		}
		if (h == 6 || h == 7) return Level4Blocks.OFFICE_DARK_MIRROR.defaultBlockState();
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

	private static int[] uvFromLocal(int dirIntoRoom, int cx, int cz) {
		return switch (dirIntoRoom) {
			case Level4Plan.SOUTH -> new int[] {cx, cz};
			case Level4Plan.NORTH -> new int[] {cx, Level4Plan.CELL - cz};
			case Level4Plan.EAST -> new int[] {cz, cx};
			default -> new int[] {cz, Level4Plan.CELL - cx};
		};
	}

	private static boolean isSealedMeetingDoor(long seed, int x, int z) {
		int rx = Math.floorDiv(x, Level4Plan.SIZE), rz = Math.floorDiv(z, Level4Plan.SIZE);
		int lx = Math.floorMod(x, Level4Plan.SIZE), lz = Math.floorMod(z, Level4Plan.SIZE);
		int i = lx / Level4Plan.CELL, j = lz / Level4Plan.CELL;
		int cx = lx % Level4Plan.CELL, cz = lz % Level4Plan.CELL;
		Level4Plan plan = Level4Plan.of(seed, rx, rz);
		Level4PoiPlan poiPlan = Level4PoiPlan.of(seed, rx, rz);
		if (poiPlan.isSealedSuiteRoom(plan.roomId[Level4Plan.cell(i, j)])) return true;
		if (cz == 0 && j > 0 && poiPlan.isSealedSuiteRoom(plan.roomId[Level4Plan.cell(i, j - 1)])) return true;
		if (cx == 0 && i > 0 && poiPlan.isSealedSuiteRoom(plan.roomId[Level4Plan.cell(i - 1, j)])) return true;
		return false;
	}

	private static boolean isPbxRoomEdge(long seed, int x, int z) {
		int rx = Math.floorDiv(x, Level4Plan.SIZE), rz = Math.floorDiv(z, Level4Plan.SIZE);
		int lx = Math.floorMod(x, Level4Plan.SIZE), lz = Math.floorMod(z, Level4Plan.SIZE);
		int i = lx / Level4Plan.CELL, j = lz / Level4Plan.CELL;
		int cx = lx % Level4Plan.CELL, cz = lz % Level4Plan.CELL;
		Level4Plan plan = Level4Plan.of(seed, rx, rz);
		Level4PoiPlan poiPlan = Level4PoiPlan.of(seed, rx, rz);
		if (poiPlan.isPbxSuiteRoom(plan.roomId[Level4Plan.cell(i, j)])) return true;
		if (cz == 0 && j > 0 && poiPlan.isPbxSuiteRoom(plan.roomId[Level4Plan.cell(i, j - 1)])) return true;
		if (cx == 0 && i > 0 && poiPlan.isPbxSuiteRoom(plan.roomId[Level4Plan.cell(i - 1, j)])) return true;
		return false;
	}

	private static BlockState ironBars(boolean n, boolean s, boolean e, boolean w) {
		return Blocks.IRON_BARS.defaultBlockState()
				.setValue(BlockStateProperties.NORTH, n)
				.setValue(BlockStateProperties.SOUTH, s)
				.setValue(BlockStateProperties.EAST, e)
				.setValue(BlockStateProperties.WEST, w);
	}

	private static BlockState greenCarpet() {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("green_carpet")).defaultBlockState();
	}

	private static BlockState redCarpet() {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("red_carpet")).defaultBlockState();
	}

	private static BlockState invertedLightwellColumn(long seed, int x, int y, int z, Tile tile, PlacedPoi poi) {
		int h = y - FLOOR_Y;
		int cx = Math.floorMod(x, Level4Plan.CELL);
		int cz = Math.floorMod(z, Level4Plan.CELL);
		if (h <= 4) {
			BlockState p = poiBlock(x, y, z, tile, poi);
			if (p != null) return p;
		}
		if (tile.walkable()) {
			if (h == 0) return Level4Blocks.OFFICE_CARPET.defaultBlockState();
			if (h == 9 && cx == 3 && cz >= 2 && cz <= 4) {
				return Level4Blocks.OFFICE_TROFFER_LIT.defaultBlockState().setValue(Level4AxisBlock.AXIS, Direction.Axis.Z);
			}
			if (h == 10) return Level4Blocks.OFFICE_CARPET.defaultBlockState();
			return Blocks.AIR.defaultBlockState();
		}
		if (tile.kind() == Kind.WINDOW_RAIN || tile.kind() == Kind.WINDOW_BLACKED || tile.kind() == Kind.WINDOW_TRAP) {
			if (h == 0) return Blocks.CONCRETE.gray().defaultBlockState();
			if (h == 1 || h == 9) return Level4Blocks.OFFICE_RADIATOR_WALL.defaultBlockState().setValue(Level4FacingBlock.FACING, dirFrom(tile.dir()));
			if (h == 5) return Level4Blocks.OFFICE_DARK_MIRROR.defaultBlockState();
			if (h == 10) return Level4Blocks.OFFICE_DRYWALL.defaultBlockState();
			return Level4Blocks.OFFICE_RAIN_WINDOW.defaultBlockState();
		}
		if (tile.kind() == Kind.PILLAR) {
			if (h == 0) return Level4Blocks.OFFICE_CARPET.defaultBlockState();
			return (h == 1 || h == 9 || h == 10 ? Level4Blocks.OFFICE_PILLAR : Level4Blocks.OFFICE_PILLAR_UPPER).defaultBlockState();
		}
		if (tile.kind() == Kind.DRYWALL) {
			if (h == 0) return Level4Blocks.OFFICE_CARPET.defaultBlockState();
			if (h == 1 || h == 9 || h == 10) return Level4Blocks.OFFICE_DRYWALL.defaultBlockState();
			if (h == 5) return Level4Blocks.OFFICE_DARK_MIRROR.defaultBlockState();
			return Level4Blocks.OFFICE_DRYWALL_UPPER.defaultBlockState();
		}
		if (h == 0) return floor(seed, x, z, tile);
		if (h <= 4) return columnBlock(tile, h);
		if (h == 5) return Level4Blocks.OFFICE_DARK_MIRROR.defaultBlockState();
		if (h == 9 || h == 10) return Level4Blocks.OFFICE_DRYWALL.defaultBlockState();
		return Level4Blocks.OFFICE_DRYWALL_UPPER.defaultBlockState();
	}

	private static BlockState executiveSuiteBlock(long seed, int x, int y, int z, int roofY, Tile tile, PlacedPoi poi) {
		int cx = Math.floorMod(x, Level4Plan.CELL);
		int cz = Math.floorMod(z, Level4Plan.CELL);
		if (y == FLOOR_Y) {
			return tile.walkable() ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : null;
		}
		if (y == roofY) {
			if (cx == 3 && cz >= 2 && cz <= 4) {
				return Level4Blocks.OFFICE_TROFFER_LIT.defaultBlockState().setValue(Level4AxisBlock.AXIS, Direction.Axis.Z);
			}
			return Blocks.DARK_OAK_PLANKS.defaultBlockState();
		}
		if (tile.kind() == Kind.FIRE_DOOR || tile.kind() == Kind.EXIT_DOOR) return null;
		if (Level4PoiPlan.executiveAuditoriumEdgeKind(seed, x, z) > 0) return null;
		if ((cx == 0 || cx == 6) && (cz == 0 || cz == 6)) {
			return Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
		}
		int audDir = Level4Plan.opposite(poi.dir());
		if (cx == 6 && cz >= 1 && cz <= 5) {
			if (audDir == Level4Plan.EAST) {
				if (cz >= 2 && cz <= 4 && (y == FLOOR_Y + 2 || y == FLOOR_Y + 3)) return Blocks.AIR.defaultBlockState();
				return Blocks.DARK_OAK_PLANKS.defaultBlockState();
			}
			if (cz == 3 && tile.walkable() && y <= FLOOR_Y + 2) return null;
			if ((y == FLOOR_Y + 2 || y == FLOOR_Y + 3) && (cz == 2 || cz == 4 || (cz == 3 && !tile.walkable()))) {
				return Level4Blocks.EXECUTIVE_BROADCAST_SCREEN.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.WEST);
			}
			return Blocks.DARK_OAK_PLANKS.defaultBlockState();
		}
		if (cz == 6 && cx >= 1 && cx <= 5) {
			if (audDir == Level4Plan.SOUTH) {
				if (cx >= 2 && cx <= 4 && (y == FLOOR_Y + 2 || y == FLOOR_Y + 3)) return Blocks.AIR.defaultBlockState();
				return Blocks.DARK_OAK_PLANKS.defaultBlockState();
			}
			if (cx == 3 && tile.walkable() && y <= FLOOR_Y + 2) return null;
			if ((y == FLOOR_Y + 2 || y == FLOOR_Y + 3) && (cx == 2 || cx == 4 || (cx == 3 && !tile.walkable()))) {
				return Level4Blocks.EXECUTIVE_BROADCAST_SCREEN.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.NORTH);
			}
			return Blocks.DARK_OAK_PLANKS.defaultBlockState();
		}
		if (cx == 0 && cz >= 1 && cz <= 5) {
			if (cz == 3 && tile.walkable() && y <= FLOOR_Y + 2) return null;
			return Blocks.DARK_OAK_PLANKS.defaultBlockState();
		}
		if (cz == 0 && cx >= 1 && cx <= 5) {
			if (cx == 3 && tile.walkable() && y <= FLOOR_Y + 2) return null;
			return Blocks.DARK_OAK_PLANKS.defaultBlockState();
		}
		return null;
	}

	private static BlockState poiBlock(int x, int y, int z, Tile tile, PlacedPoi poi) {
		if (!tile.walkable()) return null;
		int cx = Math.floorMod(x, Level4Plan.CELL);
		int cz = Math.floorMod(z, Level4Plan.CELL);
		return switch (poi.kind()) {
			case BREAKROOM_CORNER -> {
				int[] uv = uvFromLocal(poi.dir(), cx, cz);
				if (y == FLOOR_Y && uv[0] >= 1 && uv[0] <= 3 && uv[1] >= 1 && uv[1] <= 2) {
					yield Level4Blocks.OFFICE_DAMP_CARPET.defaultBlockState();
				}
				if (y == FLOOR_Y + 1 && uv[1] == 1) {
					if (uv[0] == 1 || uv[0] == 2) {
						yield Blocks.QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
					}
					if (uv[0] == 3) {
						yield Blocks.CAULDRON.defaultBlockState();
					}
				}
				yield null;
			}
			case MEG_CHALK_WALL -> {
				if (y != FLOOR_Y + 2) yield null;
				if (poi.roomId() == -2) {
					if (cx == 1 && cz == 0) {
						yield Level4Blocks.MEG_CHALK_SIGN.defaultBlockState()
								.setValue(Level4FacingBlock.FACING, Direction.EAST)
								.setValue(Level4Blocks.MegChalkSignBlock.SLASHED, poi.variantFlag());
					}
					yield null;
				}
				int[] uv = uvFromLocal(poi.dir(), cx, cz);
				if (uv[0] == 2 && uv[1] == 1) {
					yield Level4Blocks.MEG_CHALK_SIGN.defaultBlockState()
							.setValue(Level4FacingBlock.FACING, dirFrom(poi.dir()))
							.setValue(Level4Blocks.MegChalkSignBlock.SLASHED, poi.variantFlag());
				}
				yield null;
			}
			case CEILING_DRIP_SPOT -> {
				if (y == FLOOR_Y && cx >= 2 && cx <= 4 && cz >= 2 && cz <= 4) {
					yield Level4Blocks.OFFICE_DAMP_CARPET.defaultBlockState();
				}
				if (cx == 3 && cz == 3) {
					if (y == FLOOR_Y + 1) {
						yield Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2);
					}
					if (y == FLOOR_Y + 5) {
						yield Blocks.AIR.defaultBlockState();
					}
				}
				yield null;
			}
			case WINDOW_PAINT_CART -> {
				int[] uv = uvFromLocal(poi.dir(), cx, cz);
				if (y == FLOOR_Y && uv[0] >= 2 && uv[0] <= 4 && uv[1] >= 1 && uv[1] <= 2) {
					yield Level4Blocks.OFFICE_DAMP_CARPET.defaultBlockState();
				}
				if (y == FLOOR_Y + 1 && uv[1] == 1) {
					if (uv[0] == 2) yield Blocks.CAULDRON.defaultBlockState();
					if (uv[0] == 3) yield Level4Blocks.INDUSTRIAL_TOOLBOX.defaultBlockState().setValue(Level4FacingBlock.FACING, dirFrom(poi.dir()));
				}
				yield null;
			}
			case MISSING_WANDERER_CAMP -> {
				if (y != poi.baseY() + 1) yield null;
				if (tile.kind() == Kind.BALCONY_FLOOR || tile.kind() == Kind.BALCONY_CANOPY) {
					int along = (poi.dir() == Level4Plan.NORTH || poi.dir() == Level4Plan.SOUTH) ? cx : cz;
					int depth = switch (poi.dir()) {
						case Level4Plan.NORTH -> cz;
						case Level4Plan.SOUTH -> Level4Plan.CELL - cz;
						case Level4Plan.WEST -> cx;
						default -> Level4Plan.CELL - cx;
					};
					if (depth == 1 && along == 1) yield greenCarpet();
					if (depth == 2 && along == 1) yield Level4Blocks.MEG_SUPPLY_CRATE.defaultBlockState().setValue(Level4FacingBlock.FACING, dirFrom(poi.dir()));
					if (depth == 2 && along == 2) yield Blocks.LANTERN.defaultBlockState();
					if (depth == 1 && along == 5) yield Level4Blocks.ALMOND_BOTTLE_CLUSTER.defaultBlockState();
					yield null;
				}
				if (poi.variantFlag() && poi.baseY() == FLOOR_Y + 6) {
					if ((cx == 3 || cx == 4) && cz == 2) yield greenCarpet();
					if (cx == 5 && cz == 2) yield Level4Blocks.MEG_SUPPLY_CRATE.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.SOUTH);
					if (cx == 5 && cz == 3) yield Blocks.LANTERN.defaultBlockState();
					if (cx == 3 && cz == 3) yield Level4Blocks.ALMOND_BOTTLE_CLUSTER.defaultBlockState();
					yield null;
				}
				if ((cx == 1 || cx == 2) && cz == 1) yield greenCarpet();
				if (cx == 1 && cz == 2) yield Level4Blocks.MEG_SUPPLY_CRATE.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.SOUTH);
				if (cx == 2 && cz == 2) yield Blocks.LANTERN.defaultBlockState();
				if (cx == 1 && cz == 3) yield Level4Blocks.ALMOND_BOTTLE_CLUSTER.defaultBlockState();
				yield null;
			}
			case AMOR_FIELD_SHRINE -> {
				if ((cx == 3 || cx == 4) && cz == 3) {
					if (y == FLOOR_Y) yield Level4Blocks.AMOR_PLANTER.defaultBlockState();
					if (y == FLOOR_Y + 1) {
						yield (cx == 3 ? Level4Blocks.AMOR_MUSHROOM : Level4Blocks.AMOR_MUSHROOM_VEIL).defaultBlockState();
					}
				}
				if (y == FLOOR_Y && cx >= 2 && cx <= 4 && cz >= 2 && cz <= 4) {
					yield Level4Blocks.OFFICE_DAMP_CARPET.defaultBlockState();
				}
				yield null;
			}
			case LOCKED_PBX_ROOM -> {
				int[] uv = uvFromLocal(poi.dir(), cx, cz);
				Direction backFace = dirFrom(Level4Plan.opposite(poi.dir()));
				if (uv[0] == 1 && uv[1] == 5) {
					if (y == FLOOR_Y + 1 || y == FLOOR_Y + 2) yield Blocks.IRON_BLOCK.defaultBlockState();
					if (y == FLOOR_Y + 3) yield Blocks.REDSTONE_TORCH.defaultBlockState();
				}
				if (uv[0] == 2 && uv[1] == 5 && (y == FLOOR_Y + 1 || y == FLOOR_Y + 2)) {
					yield Blocks.IRON_BLOCK.defaultBlockState();
				}
				if (uv[0] == 4 && uv[1] == 5 && y == FLOOR_Y + 1) {
					yield Level4Blocks.OFFICE_DESK_PHONE.defaultBlockState().setValue(Level4FacingBlock.FACING, backFace);
				}
				if (uv[0] == 5 && uv[1] == 5 && y == FLOOR_Y + 2) {
					yield Level4Blocks.OFFICE_BREAKER.defaultBlockState().setValue(Level4FacingBlock.FACING, backFace);
				}
				if (uv[0] == 5 && uv[1] == 2 && y == FLOOR_Y + 1) {
					yield Level4Blocks.INDUSTRIAL_TOOLBOX.defaultBlockState().setValue(Level4FacingBlock.FACING, dirFrom(poi.dir()));
				}
				yield null;
			}
			case DELAYED_REFLECTION_MIRROR -> {
				if (!poi.variantFlag()) yield null;
				int[] uv = uvFromLocal(poi.dir(), cx, cz);
				if (uv[0] == 2 && uv[1] == 1 && (y == poi.baseY() + 1 || y == poi.baseY() + 2)) {
					int sil = (y == poi.baseY() + 1) ? 1 : 2;
					yield Level4Blocks.OFFICE_DARK_MIRROR.defaultBlockState()
							.setValue(Level4Blocks.OfficeDarkMirrorBlock.SILHOUETTE, sil);
				}
				if (uv[0] == 3 && uv[1] == 1 && (y == poi.baseY() + 1 || y == poi.baseY() + 2)) {
					yield Level4Blocks.OFFICE_DARK_MIRROR.defaultBlockState();
				}
				yield null;
			}
			case ACTIVE_TERMINAL_ISLAND -> {
				if (y != FLOOR_Y + 1) yield null;
				if (poi.dir() == Level4Plan.SOUTH) {
					if (cx == 2 && (cz == 2 || cz == 3)) {
						yield Level1Blocks.OFFICE_DESK.defaultBlockState()
								.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
					}
					if (cx == 3 && (cz == 2 || cz == 3)) {
						yield Level1Blocks.OFFICE_DESK.defaultBlockState()
								.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
					}
				} else {
					if ((cx == 2 || cx == 3) && cz == 2) {
						yield Level1Blocks.OFFICE_DESK.defaultBlockState()
								.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
					}
					if ((cx == 2 || cx == 3) && cz == 3) {
						yield Level1Blocks.OFFICE_DESK.defaultBlockState()
								.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
					}
				}
				yield null;
			}
			case TBD_OVERFLOW_CAGE -> {
				if (y == FLOOR_Y + 1 || y == FLOOR_Y + 2) {
					if (cx == 4 && cz == 4) yield ironBars(false, true, true, false);
					if (cx == 5 && cz == 4) yield ironBars(false, false, true, true);
					if (cx == 6 && cz == 4) yield ironBars(false, true, false, true);
					if (cx == 4 && cz == 5) yield ironBars(true, false, false, false);
				}
				if (y == FLOOR_Y + 3) {
					if (cx == 4 && cz == 4) yield ironBars(false, true, true, false);
					if (cx == 5 && cz == 4) yield ironBars(false, true, true, true);
					if (cx == 6 && cz == 4) yield ironBars(false, true, false, true);
					if (cx == 4 && cz == 5) yield ironBars(true, true, true, false);
					if (cx == 4 && cz == 6) yield ironBars(true, false, true, false);
					if (cx == 5 && cz == 5) yield ironBars(true, true, true, true);
					if (cx == 6 && cz == 5) yield ironBars(true, true, false, true);
					if (cx == 5 && cz == 6) yield ironBars(true, false, true, true);
					if (cx == 6 && cz == 6) yield ironBars(true, false, false, true);
				}
				if (cx == 5 && cz == 5 && (y == FLOOR_Y + 1 || y == FLOOR_Y + 2)) {
					yield Level4Blocks.TBD_HOARD_CRATE.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.NORTH);
				}
				if (y == FLOOR_Y + 1) {
					if (cx == 6 && cz == 5) {
						yield Level4Blocks.TBD_CURIO_SHELF.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.WEST);
					}
					if (cx == 5 && cz == 6) {
						yield Level4Blocks.MEG_SUPPLY_CRATE.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.WEST);
					}
					if (cx == 6 && cz == 6) {
						yield Level4Blocks.OFFICE_STEEL_SAFE.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.WEST);
					}
				}
				yield null;
			}
			case EVACUATED_OUTPOST -> {
				int ci = Math.floorMod(Math.floorDiv(x, Level4Plan.CELL), Level4Plan.N);
				int cj = Math.floorMod(Math.floorDiv(z, Level4Plan.CELL), Level4Plan.N);
				int di = ci - poi.cellI(), dj = cj - poi.cellJ();
				if (di == 0 && dj == 0) {
					if (y == FLOOR_Y + 1) {
						if (cx == 2 && cz == 2) yield Level4Blocks.OMEGA_COT.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.EAST);
						if (cx == 5 && cz == 2) yield Level4Blocks.OMEGA_LOCKER.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.SOUTH);
						if (cx == 1 && cz == 4) yield Level4Blocks.OMEGA_BARRICADE.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.WEST);
					}
					if (y == FLOOR_Y + 2 && cx == 1 && cz == 0) {
						yield Level4Blocks.MEG_CHALK_SIGN.defaultBlockState()
								.setValue(Level4FacingBlock.FACING, Direction.EAST)
								.setValue(Level4Blocks.MegChalkSignBlock.SLASHED, true);
					}
				} else if (di == 1 && dj == 0 && y == FLOOR_Y + 1 && cx == 5 && cz == 1) {
					yield Level4Blocks.OMEGA_BARRICADE.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.NORTH);
				} else if (di == 0 && dj == 1 && y == FLOOR_Y + 1) {
					if (cx == 2 && cz == 5) yield Level4Blocks.OMEGA_BARRICADE.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.SOUTH);
					if (cx == 4 && cz == 5) yield Level4Blocks.ALMOND_BOTTLE_CLUSTER.defaultBlockState();
				} else if (di == 1 && dj == 1 && y == FLOOR_Y + 1) {
					if (cx == 5 && cz == 4) yield Level4Blocks.OMEGA_BARRICADE.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.EAST);
					if (cx == 2 && cz == 2) yield Blocks.LANTERN.defaultBlockState();
				}
				yield null;
			}
			case STORM_BREACH_CORRIDOR -> {
				if (y == FLOOR_Y && cx >= 1 && cx <= 5 && cz >= 1 && cz <= 5) {
					yield Level4Blocks.OFFICE_DAMP_CARPET.defaultBlockState();
				}
				if (y == FLOOR_Y + 5 && cx == 3 && cz >= 2 && cz <= 4) {
					yield Level4Blocks.OFFICE_TROFFER_BROKEN.defaultBlockState().setValue(Level4AxisBlock.AXIS, Direction.Axis.Z);
				}
				if (y == FLOOR_Y + 1) {
					int[] uv = uvFromLocal(poi.dir(), cx, cz);
					if (uv[0] == 2 && uv[1] == 2) yield Level4Blocks.ALMOND_BOTTLE_CLUSTER.defaultBlockState();
					if (uv[0] == 4 && uv[1] == 2) yield Level4Blocks.MEG_SUPPLY_CRATE.defaultBlockState().setValue(Level4FacingBlock.FACING, dirFrom(poi.dir()));
				}
				yield null;
			}
			case FROZEN_FRIDAY_1998 -> {
				int ci = Math.floorMod(Math.floorDiv(x, Level4Plan.CELL), Level4Plan.N);
				int cj = Math.floorMod(Math.floorDiv(z, Level4Plan.CELL), Level4Plan.N);
				if (y != FLOOR_Y + 1) yield null;
				if ((cx == 2 || cx == 3) && cz == 4) {
					yield Level1Blocks.OFFICE_DESK.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
				}
				if (ci == poi.cellI() && cj == poi.cellJ()) {
					if (cx == 2 && cz == 2) yield Level4Blocks.ALMOND_BOTTLE_CLUSTER.defaultBlockState();
					if (cx == 5 && cz == 2) yield Level4Blocks.OFFICE_STEEL_SAFE.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.WEST);
					if (cx == 5 && cz == 5) {
						yield Level4Blocks.OFFICE_WATER_COOLER.defaultBlockState()
								.setValue(Level4WaterSourceBlock.FACING, Direction.NORTH)
								.setValue(Level4WaterSourceBlock.BOTTLES, 2);
					}
				} else if (cx == 5 && cz == 2) {
					yield Level4Blocks.ALMOND_BOTTLE_CLUSTER.defaultBlockState();
				}
				yield null;
			}
			case INVERTED_MIRROR_LIGHTWELL -> {
				int ci = Math.floorMod(Math.floorDiv(x, Level4Plan.CELL), Level4Plan.N);
				int cj = Math.floorMod(Math.floorDiv(z, Level4Plan.CELL), Level4Plan.N);
				if (ci == poi.cellI() && cj == poi.cellJ()) {
					if (cx == 2 && cz == 1 && (y == FLOOR_Y + 1 || y == FLOOR_Y + 2)) {
						yield Level4Blocks.OFFICE_DOOR_13.defaultBlockState()
								.setValue(Level4ExitBlock.PART, y - (FLOOR_Y + 1))
								.setValue(Level4ExitBlock.FACING, Direction.SOUTH);
					}
					if (((cx == 1 || cx == 3) && cz == 1 && y >= FLOOR_Y + 1 && y <= FLOOR_Y + 3)
							|| (cx == 2 && cz == 1 && y == FLOOR_Y + 3)) {
						yield Level4Blocks.OFFICE_DARK_MIRROR.defaultBlockState();
					}
					if (cx == 5 && cz == 1 && y == FLOOR_Y + 1) {
						yield Level4Blocks.MEG_SUPPLY_CRATE.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.SOUTH);
					}
				}
				yield null;
			}
			case EXECUTIVE_BROADCAST_SUITE -> {
				if (y == FLOOR_Y + 1) {
					if (cx == 3 && cz == 3) {
						yield Level4Blocks.EXECUTIVE_RED_PHONE.defaultBlockState().setValue(Level4FacingBlock.FACING, dirFrom(poi.dir()));
					}
					if (cx == 2 && cz == 3) {
						yield Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.FACING, Direction.WEST);
					}
					if (cx == 4 && cz == 3) {
						yield Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.FACING, Direction.EAST);
					}
					if (cx >= 2 && cx <= 4 && cz == 4) yield redCarpet();
					if (cx == 5 && cz == 5) yield Level4Blocks.OFFICE_STEEL_SAFE.defaultBlockState().setValue(Level4FacingBlock.FACING, Direction.WEST);
				}
				yield null;
			}
			default -> null;
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
