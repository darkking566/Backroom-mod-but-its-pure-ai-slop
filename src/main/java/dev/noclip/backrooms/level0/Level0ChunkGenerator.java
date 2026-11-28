package dev.noclip.backrooms.level0;

import com.mojang.serialization.MapCodec;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.level1.Level1Blocks;
import dev.noclip.backrooms.level1.PipeBlock;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
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
	/** 5-block clear headroom (y = 33, 34, 35, 36, 37) per 2026-11-26 cinematic scale spec. */
	static final int CEILING_Y = 38;
	// Must match dimension_type/level_0.json
	static final int MIN_Y = -32;
	static final int HEIGHT = 128;
	static final int MAX_Y = MIN_Y + HEIGHT - 1;

	private Level0ChunkGenerator(BiomeSource biomeSource) {
		super(biomeSource);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	static long mazeSeed(RandomState randomState) {
		return randomState.getOrCreateRandomFactory(BackroomsMod.id("maze")).at(0, 0, 0).nextLong();
	}

	static BlockState blockAt(long seed, int x, int y, int z) {
		return blockAt(seed, x, y, z, Maze.EpochSource.ZERO);
	}

	/**
	 * What the world holds at a block: Level 0 is a torus, every Stage of a region a copy of its centre Stage
	 * (Level0Space). The plan (blockAt) stays unbounded; only what is written into the world repeats.
	 */
	static BlockState worldBlockAt(long seed, int x, int y, int z) {
		return blockAt(seed, Level0Space.template(x), y, Level0Space.template(z));
	}

	static BlockState blockAt(long seed, int x, int y, int z, Maze.EpochSource epochs) {
		if (y == MIN_Y) return Blocks.BEDROCK.defaultBlockState();
		int cx = Math.floorDiv(x, Maze.CELL), cz = Math.floorDiv(z, Maze.CELL);
		int ix = Math.floorMod(x, Maze.CELL), iz = Math.floorMod(z, Maze.CELL);
		Maze.Zone zone = Maze.zoneAt(seed, x, z);

		if (zone == Maze.Zone.CHASM) return chasmBlockAt(seed, x, y, z, cx, cz, ix, iz, epochs);
		if (zone == Maze.Zone.MEGATHALL) return megahallBlockAt(seed, x, y, z, cx, cz, ix, iz, epochs);
		ManilaRoomPlan.Room manila = ManilaRoomPlan.roomAtBlock(seed, x, z);
		if (manila != null) return manilaBlockAt(manila, x, y, z);
		StaircasePlan.Complex staircase = StaircasePlan.complexAtBlock(seed, x, z);
		if (staircase != null) {
			BlockState state = StaircasePlan.blockAt(staircase, x, y, z);
			if (state != null) return state;
		}
		if (zone == Maze.Zone.PITS) {
			BlockState pitSub = PitfallsPlan.blockAt(seed, x, y, z, cx, cz, ix, iz);
			if (pitSub != null) return pitSub;
		}

		// Ceiling crawlspace upper cavity (y in 38..42) for CEILING_CRAWLSPACE vignettes
		if (y >= CEILING_Y && y <= CEILING_Y + 4) {
			BlockState crawl = ZonePoiPlan.ceilingCrawlspaceBlockAt(seed, y, cx, cz, ix, iz);
			if (crawl != null) return crawl;
		}

		if (y < FLOOR_Y) {
			if (Maze.isPit(seed, x, z)) {
				return Blocks.AIR.defaultBlockState();
			}
			if (y == FLOOR_Y - 1 && ix >= 3 && ix <= 7 && iz >= 3 && iz <= 7 && Maze.isSunkenCell(seed, cx, cz)) {
				if (isSunkenStep(seed, cx, cz, ix, iz)) return Blocks.CONCRETE.black().defaultBlockState();
				return (zone == Maze.Zone.ARCHES ? ModBlocks.THICK_CARPET : ModBlocks.DRY_CARPET).defaultBlockState();
			}
			if (y == FLOOR_Y - 1 && Maze.isPuddle(seed, x, z)) {
				return ModBlocks.CARPET.defaultBlockState();
			}
			return Blocks.CONCRETE.black().defaultBlockState();
		}
		if (y == FLOOR_Y) {
			if (Maze.isPit(seed, x, z)) return Blocks.AIR.defaultBlockState();
			if (Maze.isPuddle(seed, x, z)) return Blocks.WATER.defaultBlockState();
			if (ix >= 3 && ix <= 7 && iz >= 3 && iz <= 7 && Maze.isSunkenCell(seed, cx, cz)) {
				if (isSunkenStep(seed, cx, cz, ix, iz)) {
					return Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState()
							.setValue(StairBlock.FACING, sunkenStepFacing(seed, cx, cz));
				}
				return Blocks.AIR.defaultBlockState();
			}
			if (ix >= 2 && ix <= 8 && iz >= 2 && iz <= 8) {
				BlockState poi = ZonePoiPlan.standardZoneBlockAt(seed, y, cx, cz, ix, iz, zone);
				if (poi != null) return poi;
			}
			if (Maze.isThickCarpet(seed, x, z)) {
				return ModBlocks.THICK_CARPET.defaultBlockState();
			}
			if (zone == Maze.Zone.PILLARS) return ModBlocks.DRY_CARPET.defaultBlockState();
			if (ix >= 3 && ix <= 7 && iz >= 3 && iz <= 7) {
				Maze.FloorAccent accent = Maze.floorAccent(seed, cx, cz);
				if (accent == Maze.FloorAccent.DRY_RUG) return ModBlocks.DRY_CARPET.defaultBlockState();
				if (accent == Maze.FloorAccent.THICK_RUG) return ModBlocks.THICK_CARPET.defaultBlockState();
			}
			return ModBlocks.CARPET.defaultBlockState();
		}

		int ceilY = Maze.cellCeilingY(seed, cx, cz);
		Maze.CeilingStyle ceiling = Maze.ceilingStyle(seed, cx, cz);
		boolean inCofferCore = ceiling == Maze.CeilingStyle.COFFERED && ix >= 3 && ix <= 7 && iz >= 3 && iz <= 7;
		boolean inDamagedCore = ceiling == Maze.CeilingStyle.DAMAGED_TILES && ix >= 3 && ix <= 7 && iz >= 3 && iz <= 7;
		if (inCofferCore) {
			if (y > ceilY + 1) return Blocks.BEDROCK.defaultBlockState();
			if (y == ceilY + 1) return ceilingOrLampBlockAt(seed, x, z, cx, cz, ix, iz, zone, epochs);
			if (y == ceilY) {
				if (Maze.isWall(seed, x, z, true, epochs)) {
					return wallBlockAt(seed, x, z, cx, cz, ix, iz, zone, false, y, epochs);
				}
				return Blocks.AIR.defaultBlockState();
			}
		} else if (inDamagedCore) {
			if (y > ceilY + 3) return Blocks.BEDROCK.defaultBlockState();
			if (y == ceilY + 3) return Blocks.CONCRETE.black().defaultBlockState();
			if (y == ceilY + 2) {
				if (iz == 5) return Level1Blocks.CEILING_PIPE.defaultBlockState().setValue(PipeBlock.AXIS, Direction.Axis.X);
				return Blocks.AIR.defaultBlockState();
			}
			if (y == ceilY + 1) return Blocks.AIR.defaultBlockState();
			if (y == ceilY) {
				if (Maze.isLamp(seed, x, z, epochs)) {
					return ceilingOrLampBlockAt(seed, x, z, cx, cz, ix, iz, zone, epochs);
				}
				if ((ix == 4 || ix == 6) && (iz == 4 || iz == 5)) {
					return Blocks.AIR.defaultBlockState(); // missing drop-ceiling tile exposing dark plenum & pipes
				}
				return ModBlocks.CEILING_TILE.defaultBlockState();
			}
		} else {
			if (y > ceilY) return Blocks.BEDROCK.defaultBlockState();
			if (y == ceilY) return ceilingOrLampBlockAt(seed, x, z, cx, cz, ix, iz, zone, epochs);
		}

		// Two-story atrium upper indoor balcony & glowing fake window panels (at y = FLOOR_Y + 6..8)
		if (Maze.isAtriumCell(seed, cx, cz) && y >= FLOOR_Y + 6 && y < ceilY) {
			BlockState balcony = atriumUpperBlockAt(cx, cz, ix, iz, y);
			if (balcony != null) return balcony;
		}

		boolean topLayer = y == ceilY - 1;
		boolean baseboard = y == FLOOR_Y + 1;
		if (Exits.isDoor(seed, x, z) && y <= FLOOR_Y + 3 && Maze.isWall(seed, x, z, y == FLOOR_Y + 3, epochs)) {
			if (baseboard) return ModBlocks.EXIT_DOOR.defaultBlockState();
			return (y == FLOOR_Y + 2 ? ModBlocks.EXIT_DOOR_TOP : ModBlocks.EXIT_SIGN).defaultBlockState();
		}
		if (Maze.isWall(seed, x, z, topLayer, epochs)) {
			return wallBlockAt(seed, x, z, cx, cz, ix, iz, zone, baseboard, y, epochs);
		}
		if (y >= FLOOR_Y + 1 && y < ceilY) {
			BlockState poi = ZonePoiPlan.standardZoneBlockAt(seed, y, cx, cz, ix, iz, zone);
			if (poi != null) return poi;
		}
		if (topLayer && isDroppedCeilingTile(seed, x, z, ix, iz, ceiling, epochs)) {
			return ModBlocks.CEILING_TILE.defaultBlockState();
		}
		return Blocks.AIR.defaultBlockState();
	}

	/** Upper indoor balcony ledge + glowing fake wall windows lining 2x2 Atriums. */
	private static BlockState atriumUpperBlockAt(int cx, int cz, int ix, int iz, int y) {
		int ax = Math.floorMod(cx, 2), az = Math.floorMod(cz, 2);
		boolean westEdge = ax == 0 && ix == 1;
		boolean eastEdge = ax == 1 && ix == Maze.CELL - 1;
		boolean northEdge = az == 0 && iz == 1;
		boolean southEdge = az == 1 && iz == Maze.CELL - 1;
		if (westEdge || eastEdge || northEdge || southEdge) {
			if (y == FLOOR_Y + 6) return Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState();
			if (y == FLOOR_Y + 7) return Blocks.IRON_BARS.defaultBlockState();
		}
		boolean wallFace = (ax == 0 && ix == 0 && (iz == 4 || iz == 5))
				|| (az == 0 && iz == 0 && (ix == 4 || ix == 5));
		if (wallFace && (y == FLOOR_Y + 8 || y == FLOOR_Y + 9)) {
			return ModBlocks.MANILA_LIGHT.defaultBlockState(); // eerie glowing fake upper window
		}
		return null;
	}

	private static BlockState chasmBlockAt(long seed, int x, int y, int z, int cx, int cz, int ix, int iz,
			Maze.EpochSource epochs) {
		int floorY = ChasmPlan.floorYAt(seed, cx, cz, ix, iz);
		int ceilY = ChasmPlan.cellCeilingY(seed, cx, cz);

		if (floorY == Integer.MIN_VALUE) {
			if (y == ChasmPlan.ABYSS_FLOOR_Y) return Blocks.CONCRETE.black().defaultBlockState();
			if (y < ceilY) return Blocks.AIR.defaultBlockState();
			if (y == ceilY) return ceilingOrLampBlockAt(seed, x, z, cx, cz, ix, iz, Maze.Zone.CHASM, epochs);
			return Blocks.BEDROCK.defaultBlockState();
		}

		if (y < floorY) {
			return cliffStrataBlock(y, false);
		}
		BlockState poi = ZonePoiPlan.chasmBlockAt(seed, y, cx, cz, ix, iz, floorY, ceilY);
		if (poi != null) return poi;
		if (y == floorY) {
			Direction stair = ChasmPlan.stairFacingAt(seed, x, z);
			if (stair != null) {
				return Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, stair);
			}
			if (floorY <= 12) return ModBlocks.THICK_CARPET.defaultBlockState();
			return ModBlocks.CARPET.defaultBlockState();
		}
		if (y > ceilY) {
			if (ceilY <= FLOOR_Y + 6 && y <= 64) {
				return cliffStrataBlock(y, false);
			}
			return Blocks.BEDROCK.defaultBlockState();
		}
		if (y == ceilY) {
			return ceilingOrLampBlockAt(seed, x, z, cx, cz, ix, iz, Maze.Zone.CHASM, epochs);
		}

		boolean baseboard = y == floorY + 1;
		boolean topLayer = y == ceilY - 1;
		if (ChasmPlan.isFlatRimCell(seed, cx, cz) && Exits.isDoor(seed, x, z) && y <= FLOOR_Y + 3
				&& Maze.isWall(seed, x, z, y == FLOOR_Y + 3, epochs)) {
			if (baseboard) return ModBlocks.EXIT_DOOR.defaultBlockState();
			return (y == FLOOR_Y + 2 ? ModBlocks.EXIT_DOOR_TOP : ModBlocks.EXIT_SIGN).defaultBlockState();
		}
		if (ChasmPlan.isWallAt(seed, x, z, topLayer)) {
			boolean openCavern = ceilY >= 58;
			if (openCavern && ix != 0 && iz != 0 && y > floorY + 3) {
				return Blocks.AIR.defaultBlockState();
			}
			boolean isCornerPier = (ix == 0 && (iz == 1 || iz == Maze.CELL - 1))
					|| (iz == 0 && (ix == 1 || ix == Maze.CELL - 1))
					|| (ix == 0 && iz == 0);
			if (openCavern && !isCornerPier && y > floorY + 5) {
				return Blocks.AIR.defaultBlockState();
			}
			return cliffStrataBlock(y, baseboard);
		}
		return Blocks.AIR.defaultBlockState();
	}

	private static BlockState megahallBlockAt(long seed, int x, int y, int z, int cx, int cz, int ix, int iz,
			Maze.EpochSource epochs) {
		int floorY = MegahallPlan.walkableFloorY(seed, x, z);
		int ceilY = MegahallPlan.cellCeilingY(seed, cx, cz);

		BlockState poi = ZonePoiPlan.megahallBlockAt(seed, y, cx, cz, ix, iz, floorY, ceilY);
		if (poi != null) return poi;

		if (y < floorY) return Blocks.CONCRETE.black().defaultBlockState();
		if (y == floorY) {
			Direction stair = MegahallPlan.stairFacingAt(seed, x, z);
			if (stair != null) {
				return Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, stair);
			}
			if (floorY < FLOOR_Y) return ModBlocks.THICK_CARPET.defaultBlockState();
			if (floorY > FLOOR_Y) return ModBlocks.DRY_CARPET.defaultBlockState();
			if (((cx + cz) & 1) == 0 && ix >= 3 && ix <= 7 && iz >= 3 && iz <= 7) {
				return ModBlocks.DRY_CARPET.defaultBlockState();
			}
			return ModBlocks.CARPET.defaultBlockState();
		}
		if (y > ceilY) return Blocks.BEDROCK.defaultBlockState();
		if (y == ceilY) return ceilingOrLampBlockAt(seed, x, z, cx, cz, ix, iz, Maze.Zone.MEGATHALL, epochs);

		boolean baseboard = y == floorY + 1;
		boolean topLayer = y == ceilY - 1;
		if (MegahallPlan.isRimCell(cx, cz) && Exits.isDoor(seed, x, z) && y <= FLOOR_Y + 3
				&& Maze.isWall(seed, x, z, y == FLOOR_Y + 3, epochs)) {
			if (baseboard) return ModBlocks.EXIT_DOOR.defaultBlockState();
			return (y == FLOOR_Y + 2 ? ModBlocks.EXIT_DOOR_TOP : ModBlocks.EXIT_SIGN).defaultBlockState();
		}
		if (MegahallPlan.isWallAt(seed, x, z, topLayer)) {
			if (y == FLOOR_Y + 14 || y == FLOOR_Y + 28) {
				return ModBlocks.CEILING_TILE.defaultBlockState();
			}
			boolean paleColumn = ix != 0 && iz != 0 && Math.floorMod(cx + cz, 2) == 0;
			if (paleColumn) {
				return (baseboard ? ModBlocks.PALE_WALLPAPER_BASEBOARD : ModBlocks.PALE_WALLPAPER).defaultBlockState();
			}
			return (baseboard ? ModBlocks.WALLPAPER_BASEBOARD : ModBlocks.WALLPAPER).defaultBlockState();
		}
		if (MegahallPlan.isHighBeam(seed, x, y, z, ceilY)) {
			return ModBlocks.CEILING_TILE.defaultBlockState();
		}
		return Blocks.AIR.defaultBlockState();
	}

	private static BlockState manilaBlockAt(ManilaRoomPlan.Room room, int x, int y, int z) {
		int dx = x - room.baseX(), dz = z - room.baseZ();
		int max = ManilaRoomPlan.SPAN_BLOCKS; // 20
		int da = ManilaRoomPlan.DOOR_A, db = ManilaRoomPlan.DOOR_B; // 9, 10
		if (y < FLOOR_Y) return Blocks.CONCRETE.black().defaultBlockState();
		if (y == FLOOR_Y) {
			if ((dx >= 2 && dx <= max - 3 && dz >= 2 && dz <= max - 3) || room.isDoorwayTunnel(dx, dz)) {
				return ModBlocks.MANILA_WOOD_FLOOR.defaultBlockState();
			}
			return Blocks.CONCRETE.black().defaultBlockState();
		}
		if (y > CEILING_Y) return Blocks.BEDROCK.defaultBlockState();
		if (y == CEILING_Y) {
			if ((dx == da || dx == db) && (dz == da || dz == db)) {
				return ModBlocks.MANILA_LIGHT.defaultBlockState();
			}
			return ModBlocks.CEILING_TILE.defaultBlockState();
		}

		if (dx <= 2 || dx >= max - 3 || dz <= 2 || dz >= max - 3) {
			if (room.isDoorwayTunnel(dx, dz)) {
				if (y >= FLOOR_Y + 3) return ModBlocks.MANILA_WALLPAPER.defaultBlockState();
				Direction doorFacing = ManilaRoomPlan.doorFacing(room, dx, dz);
				if (doorFacing != null) {
					BlockState door = (y == FLOOR_Y + 1 ? ModBlocks.MANILA_DOOR : ModBlocks.MANILA_DOOR_TOP).defaultBlockState();
					return door.setValue(ManilaDoorBlock.FACING, doorFacing).setValue(ManilaDoorBlock.OPEN, true);
				}
				return Blocks.AIR.defaultBlockState();
			}
			if (room.isCoreVoidLayer(dx, dz)) return ModBlocks.VOID_STONE.defaultBlockState();
			return (y == FLOOR_Y + 1 ? ModBlocks.MANILA_WALLPAPER_BASEBOARD : ModBlocks.MANILA_WALLPAPER).defaultBlockState();
		}

		if (y == FLOOR_Y + 1) {
			if (dx == da && dz == da) return ModBlocks.MANILA_TABLE.defaultBlockState().setValue(ManilaFurnitureBlock.FACING, Direction.NORTH);
			if (dx == db && dz == da) return ModBlocks.MANILA_TABLE.defaultBlockState().setValue(ManilaFurnitureBlock.FACING, Direction.EAST);
			if (dx == db && dz == db) return ModBlocks.MANILA_CABINET.defaultBlockState()
					.setValue(ManilaCabinetBlock.FACING, Direction.SOUTH)
					.setValue(ManilaCabinetBlock.OPENED, false);
			if (dx == da && dz == db) return ModBlocks.MANILA_TABLE.defaultBlockState().setValue(ManilaFurnitureBlock.FACING, Direction.WEST);
			if (dx == da - 1 && dz == da) return ModBlocks.MANILA_CHAIR.defaultBlockState().setValue(ManilaFurnitureBlock.FACING, Direction.EAST);
			if (dx == db + 1 && dz == db) return ModBlocks.MANILA_CHAIR.defaultBlockState().setValue(ManilaFurnitureBlock.FACING, Direction.WEST);
			// Interactive cot/bed in the quiet northeast corner: sleeping offers a random jump to Level 1 or Level 2
			if (dx == 15 && dz == 4) {
				return Blocks.BED.brown().defaultBlockState()
						.setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH)
						.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD);
			}
			if (dx == 15 && dz == 5) {
				return Blocks.BED.brown().defaultBlockState()
						.setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH)
						.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT);
			}
		}
		if (y == FLOOR_Y + 2 && dx == da && dz == da) {
			return ModBlocks.MANILA_NOTE.defaultBlockState().setValue(ManilaNoteBlock.FACING, Direction.NORTH);
		}
		return Blocks.AIR.defaultBlockState();
	}

	private static BlockState cliffStrataBlock(int y, boolean baseboard) {
		if (baseboard) return ModBlocks.ROUGH_WALLPAPER_BASEBOARD.defaultBlockState();
		int band = Math.floorMod(y, 10);
		if (band == 0) return Blocks.CONCRETE.black().defaultBlockState();
		if (band <= 4) return ModBlocks.ROUGH_WALLPAPER.defaultBlockState();
		if (band <= 7) return ModBlocks.WALLPAPER.defaultBlockState();
		return ModBlocks.PALE_WALLPAPER.defaultBlockState();
	}

	private static BlockState ceilingOrLampBlockAt(long seed, int x, int z, int cx, int cz, int ix, int iz,
			Maze.Zone zone, Maze.EpochSource epochs) {
		if (!Maze.isLamp(seed, x, z, epochs)) {
			// Micro-event #1: occasional sagging loose ceiling tile ready to search
			if (!(cx == 0 && cz == 0) && ix == 3 && iz == 7
					&& zone != Maze.Zone.RED_ROOM && zone != Maze.Zone.CHASM && zone != Maze.Zone.MEGATHALL
					&& !ZonePoiPlan.isStandardPoiCell(seed, cx, cz)
					&& Math.floorMod(Maze.hash(seed, cx, cz, 401), 6) == 0) {
				return ModBlocks.LOOSE_CEILING_TILE.defaultBlockState();
			}
			return ModBlocks.CEILING_TILE.defaultBlockState();
		}
		if (Exits.lampPointsToExit(seed, x, z)) return ModBlocks.EXIT_LIGHT.defaultBlockState();
		if (!Maze.lampWorks(seed, x, z, epochs)) return ModBlocks.BROKEN_FLUORESCENT_LIGHT.defaultBlockState();
		if (ZonePoiPlan.isFunDoorCell(seed, x, z)) return ModBlocks.MANILA_LIGHT.defaultBlockState();
		return (zone == Maze.Zone.RED_ROOM ? ModBlocks.RED_FLUORESCENT_LIGHT : ModBlocks.FLUORESCENT_LIGHT).defaultBlockState();
	}

	private static boolean isDroppedCeilingTile(long seed, int x, int z, int ix, int iz,
			Maze.CeilingStyle ceiling, Maze.EpochSource epochs) {
		if (ix == 0 || iz == 0) return false;
		return switch (ceiling) {
			case BEAMED_X -> ix == 3 || ix == 7;
			case BEAMED_Z -> iz == 3 || iz == 7;
			case LOW_SOFFIT -> !Maze.isLamp(seed, x, z, epochs);
			case FLAT, COFFERED, DAMAGED_TILES -> false;
		};
	}

	private static BlockState wallBlockAt(long seed, int x, int z, int cx, int cz, int ix, int iz,
			Maze.Zone zone, boolean baseboard, int y, Maze.EpochSource epochs) {
		if (zone == Maze.Zone.ARCHES) {
			return (baseboard ? ModBlocks.PALE_WALLPAPER_BASEBOARD : ModBlocks.PALE_WALLPAPER).defaultBlockState();
		}
		if (zone == Maze.Zone.RED_ROOM) {
			return (baseboard ? ModBlocks.RED_WALLPAPER_BASEBOARD : ModBlocks.RED_WALLPAPER).defaultBlockState();
		}
		if (zone == Maze.Zone.DARKENED) {
			return (baseboard ? ModBlocks.ROUGH_WALLPAPER_BASEBOARD : ModBlocks.ROUGH_WALLPAPER).defaultBlockState();
		}

		// Eye-level micro-event & environmental storytelling wall blocks (skipping origin cell (0,0) and protected cells)
		if (!baseboard && y == FLOOR_Y + 2 && !(cx == 0 && cz == 0)
				&& !Exits.isProtectedCell(seed, cx, cz) && !ZonePoiPlan.isStandardPoiCell(seed, cx, cz)) {
			// Cruciform pillar code signage (A-14) on PILLARS zone cross arms
			if (zone == Maze.Zone.PILLARS && ix == 0 && iz == 1 && Math.floorMod(Maze.hash(seed, cx, cz, 405), 3) == 0) {
				return ModBlocks.GRAFFITI_WALL.defaultBlockState().setValue(Level0EventBlocks.GraffitiWallBlock.VARIANT, 0);
			}
			// Wanderer graffiti arrows & warnings on standard walls
			if ((ix == 0 && iz == 5) || (iz == 0 && ix == 5)) {
				int gRoll = Math.floorMod(Maze.hash(seed, cx, cz, 409), 18);
				if (gRoll == 0) {
					return ModBlocks.GRAFFITI_WALL.defaultBlockState().setValue(Level0EventBlocks.GraffitiWallBlock.VARIANT, 1);
				}
				if (gRoll == 1) {
					return ModBlocks.GRAFFITI_WALL.defaultBlockState().setValue(Level0EventBlocks.GraffitiWallBlock.VARIANT, 2);
				}
				// Micro-event #2: bulging hollow wallpaper wall ready to knock
				if (gRoll == 2 || gRoll == 3) {
					return ModBlocks.HOLLOW_WALL.defaultBlockState();
				}
			}
		}

		if (ix != 0 && iz != 0 && Maze.useAccentWall(seed, cx, cz, epochs)) {
			boolean pale = zone == Maze.Zone.CUBICLES || Math.floorMod(cx + cz, 2) == 0;
			if (pale) {
				return (baseboard ? ModBlocks.PALE_WALLPAPER_BASEBOARD : ModBlocks.PALE_WALLPAPER).defaultBlockState();
			}
			return (baseboard ? ModBlocks.ROUGH_WALLPAPER_BASEBOARD : ModBlocks.ROUGH_WALLPAPER).defaultBlockState();
		}
		if (!baseboard && Maze.isRedWarningWall(seed, x, z, epochs)) {
			return ModBlocks.PEELING_RED_WALLPAPER.defaultBlockState();
		}
		return (baseboard ? ModBlocks.WALLPAPER_BASEBOARD : ModBlocks.WALLPAPER).defaultBlockState();
	}

	private static boolean isSunkenStep(long seed, int cx, int cz, int ix, int iz) {
		return switch (Maze.sunkenEntryDir(seed, cx, cz)) {
			case 0 -> ix == 3 && iz >= 4 && iz <= 6;
			case 1 -> ix == 7 && iz >= 4 && iz <= 6;
			case 2 -> iz == 3 && ix >= 4 && ix <= 6;
			default -> iz == 7 && ix >= 4 && ix <= 6;
		};
	}

	private static Direction sunkenStepFacing(long seed, int cx, int cz) {
		return switch (Maze.sunkenEntryDir(seed, cx, cz)) {
			case 0 -> Direction.WEST;
			case 1 -> Direction.EAST;
			case 2 -> Direction.NORTH;
			default -> Direction.SOUTH;
		};
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
			StructureManager structureManager, ChunkAccess chunk) {
		long seed = mazeSeed(randomState);
		Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
		Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState[] col = new BlockState[HEIGHT];
		int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int wx = minX + x, wz = minZ + z;
				fillColumnStates(seed, wx, wz, col);
				for (int i = 0; i < HEIGHT; i++) {
					int y = MIN_Y + i;
					BlockState state = col[i];
					chunk.setBlockState(pos.set(x, y, z), state);
					if (state.getBlock() instanceof EntityBlock entityBlock) {
						BlockPos worldPos = new BlockPos(wx, y, wz);
						BlockEntity be = entityBlock.newBlockEntity(worldPos, state);
						if (be != null) {
							chunk.setBlockEntity(be);
						}
					}
					oceanFloor.update(x, y, z, state);
					surface.update(x, y, z, state);
				}
			}
		}
		return CompletableFuture.completedFuture(chunk);
	}

	/** Computes one full column (MIN_Y..MAX_Y) identically to {@link #worldBlockAt}, fast-filling uniform bedrock and subfloor bands. */
	static void fillColumnStates(long seed, int worldX, int worldZ, BlockState[] out) {
		int x = Level0Space.template(worldX), z = Level0Space.template(worldZ);
		BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
		out[0] = bedrock;
		int cx = Math.floorDiv(x, Maze.CELL), cz = Math.floorDiv(z, Maze.CELL);
		Maze.Zone zone = Maze.zoneAt(seed, x, z);
		boolean special = zone == Maze.Zone.CHASM || zone == Maze.Zone.MEGATHALL || zone == Maze.Zone.PITS
				|| ManilaRoomPlan.roomAtBlock(seed, x, z) != null
				|| StaircasePlan.complexAtBlock(seed, x, z) != null;
		if (special) {
			for (int i = 1; i < HEIGHT; i++) {
				out[i] = blockAt(seed, x, MIN_Y + i, z, Maze.EpochSource.ZERO);
			}
			return;
		}
		BlockState subfloor = Blocks.CONCRETE.black().defaultBlockState();
		int subEnd = FLOOR_Y - 2;
		for (int y = MIN_Y + 1; y <= subEnd; y++) {
			out[y - MIN_Y] = subfloor;
		}
		int ceilY = Maze.cellCeilingY(seed, cx, cz);
		int maxActiveY = Math.min(MAX_Y, Math.max(CEILING_Y + 4, ceilY + 3));
		for (int y = FLOOR_Y - 1; y <= maxActiveY; y++) {
			out[y - MIN_Y] = blockAt(seed, x, y, z, Maze.EpochSource.ZERO);
		}
		for (int y = maxActiveY + 1; y <= MAX_Y; y++) {
			out[y - MIN_Y] = bedrock;
		}
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
		long seed = mazeSeed(randomState);
		BlockState[] column = new BlockState[HEIGHT];
		for (int i = 0; i < HEIGHT; i++) column[i] = worldBlockAt(seed, x, MIN_Y + i, z);
		return new NoiseColumn(MIN_Y, column);
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
		return MAX_Y + 1;
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
