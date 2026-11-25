package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.props.BackroomsProps;
import dev.noclip.backrooms.props.BlenderSinglePropBlock;
import dev.noclip.backrooms.props.MultiPartPropBlock;
import dev.noclip.backrooms.props.PropBlockEntity;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Populates Level 0 ("The Lobby") with both scattered lone objects and movie-style concentrated
 * furniture/appliance clusters (mixing intact props and half-sliced noclipped variants).
 *
 * Placement runs cleanly on first chunk generation (never cross-chunk, never touching the [-32..31]
 * landing/test zone, Manila rooms, staircase complexes, red rooms, chasms, or pits) so Level 0's
 * core maze generation and tests remain completely undisturbed.
 */
final class Level0Props {
	private static final Direction[] HORIZONTALS = {
			Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
	};

	enum ClusterTheme {
		CHAIR_MOUNTAIN,     // Movie-style towering wood chair pile + scattered lone wooden chairs + noclipped chair
		OFFICE_CLUSTER,     // Glass partitions + folding tables + intact/damaged/noclipped PCs + office chairs + cabinet
		LOUNGE_APPLIANCES,  // Intact + noclipped sofas + floor lamp + water cooler + fridge/vending/TV
		STORAGE_HOARD,      // Cardboard pile + curtain heap + standing mirror + fan + plant + CRT TV
		WANDERER_CAMP       // Rare wanderer remains + lamp + chair/table + first-aid kit + occasional locked armory crate
	}

	private Level0Props() {
	}

	static void register() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (!generated || level.dimension() != Level0.LEVEL_0) return;
			long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
			populateChunk(chunk, seed);
		});
	}

	private static void populateChunk(LevelChunk chunk, long seed) {
		int chunkX = chunk.getPos().x();
		int chunkZ = chunk.getPos().z();
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		int centerX = minX + 8;
		int centerZ = minZ + 8;

		// Preserve origin [-32..31] x [-32..31] (landing zone & baseline generator checks) and far test point (701, -700)
		if (isProtectedRegion(minX, minZ)) return;

		Maze.Zone zone = Maze.zoneAt(seed, centerX, centerZ);
		if (zone == Maze.Zone.RED_ROOM || zone == Maze.Zone.CHASM || zone == Maze.Zone.PITS) return;

		// Distance-scaled noclip ratio: ~25% near origin, scaling up to ~50% deep in Level 0 (55% in Darkened zones)
		double dist = Math.hypot(centerX, centerZ);
		int noclipPercent = zone == Maze.Zone.DARKENED
				? 55
				: Math.min(50, 25 + (int) (dist / 28.0));

		long h = hashChunk(seed, chunkX, chunkZ, 0x4C305052L);
		int roll = Math.floorMod((int) h, 100);

		// ~16% of chunks form a movie-style concentrated furniture cluster (higher in Megahalls / Cubicles / Arches)
		int clusterChance = switch (zone) {
			case MEGATHALL -> 26;
			case CUBICLES, ARCHES -> 20;
			case PILLARS, CLASSIC -> 16;
			default -> 12;
		};

		if (roll < clusterChance) {
			ClusterTheme theme = chooseClusterTheme(zone, h >>> 8);
			if (placeCluster(chunk, seed, minX, minZ, zone, theme, noclipPercent, h)) {
				chunk.markUnsaved();
			}
			return;
		}

		// ~48% of remaining chunks get 1..3 eerie lone scattered objects (like lone chairs/PCs/sofas/TVs)
		int scatterChance = switch (zone) {
			case MEGATHALL, CUBICLES -> 62;
			case CLASSIC, ARCHES, PILLARS -> 50;
			default -> 38;
		};
		if (roll < clusterChance + scatterChance) {
			int count = 1 + Math.floorMod((int) (h >>> 16), zone == Maze.Zone.MEGATHALL ? 3 : 2);
			boolean placedAny = false;
			for (int i = 0; i < count; i++) {
				long sub = mix(h ^ (i * 0x9E3779B97F4A7C15L));
				boolean noclipped = Math.floorMod((int) sub, 100) < noclipPercent;
				Block prop = pickScatteredProp(zone, noclipped, sub >>> 12);
				placedAny |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, prop, sub >>> 24);
			}
			if (placedAny) {
				chunk.markUnsaved();
			}
		}
	}

	static boolean isProtectedRegion(int chunkMinX, int chunkMinZ) {
		int chunkMaxX = chunkMinX + 15;
		int chunkMaxZ = chunkMinZ + 15;
		// Keep [-32..31] x [-32..31] completely untouched (landing cells, EdgeShift test ring, generator checks)
		if (chunkMaxX >= -32 && chunkMinX <= 31 && chunkMaxZ >= -32 && chunkMinZ <= 31) {
			return true;
		}
		// Keep far client test teleport point (701, -700) clear
		return chunkMaxX >= 694 && chunkMinX <= 708 && chunkMaxZ >= -708 && chunkMinZ <= -692;
	}

	static ClusterTheme chooseClusterTheme(Maze.Zone zone, long h) {
		int r = Math.floorMod((int) h, 100);
		if (r < 6) return ClusterTheme.WANDERER_CAMP;
		return switch (zone) {
			case CUBICLES -> r < 60 ? ClusterTheme.OFFICE_CLUSTER
					: (r < 82 ? ClusterTheme.CHAIR_MOUNTAIN : ClusterTheme.STORAGE_HOARD);
			case MEGATHALL -> r < 46 ? ClusterTheme.CHAIR_MOUNTAIN
					: (r < 80 ? ClusterTheme.LOUNGE_APPLIANCES : ClusterTheme.STORAGE_HOARD);
			case ARCHES -> r < 48 ? ClusterTheme.LOUNGE_APPLIANCES
					: (r < 80 ? ClusterTheme.CHAIR_MOUNTAIN : ClusterTheme.OFFICE_CLUSTER);
			case PILLARS -> r < 42 ? ClusterTheme.CHAIR_MOUNTAIN
					: (r < 72 ? ClusterTheme.STORAGE_HOARD : ClusterTheme.LOUNGE_APPLIANCES);
			default -> r < 38 ? ClusterTheme.CHAIR_MOUNTAIN
					: (r < 62 ? ClusterTheme.OFFICE_CLUSTER
					: (r < 82 ? ClusterTheme.LOUNGE_APPLIANCES : ClusterTheme.STORAGE_HOARD));
		};
	}

	private static boolean placeCluster(LevelChunk chunk, long seed, int minX, int minZ,
			Maze.Zone zone, ClusterTheme theme, int noclipPercent, long h) {
		long s = mix(h ^ 0x517CC1B727220A95L);
		boolean placed = false;
		switch (theme) {
			case CHAIR_MOUNTAIN -> {
				// Centerpiece: towering 2x2x2 stacked wooden chair mountain (or folding chair pile)
				Block centerpiece = Math.floorMod((int) s, 10) < 7
						? BackroomsProps.PROP_WOOD_CHAIR_PILE
						: BackroomsProps.PROP_FOLDING_CHAIR_PILE;
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, centerpiece, s);
				// Surrounding eerie lone intact wooden chairs facing random angles (just like the Backrooms film!)
				int intactChairs = 3 + Math.floorMod((int) (s >>> 8), 3);
				for (int i = 0; i < intactChairs; i++) {
					s = mix(s + 0x9E37L);
					placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, BackroomsProps.PROP_WOOD_CHAIR, s);
				}
				// Mix 1..2 half-sliced noclipped chairs right in the same cluster!
				int ncChairs = 1 + (Math.floorMod((int) (s >>> 16), 100) < noclipPercent ? 1 : 0);
				for (int i = 0; i < ncChairs; i++) {
					s = mix(s + 0x7F4AL);
					Block nc = (i == 0) ? BackroomsProps.NOCLIPPED_WOOD_CHAIR : BackroomsProps.NOCLIPPED_OFFICE_CHAIR;
					placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, nc, s);
				}
			}
			case OFFICE_CLUSTER -> {
				// Mix intact & broken glass partitions + folding table + computers (intact/damaged/noclipped) + chairs + cabinet
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ,
						(s & 1) == 0 ? BackroomsProps.PROP_GLASS_PARTITION : BackroomsProps.PROP_GLASS_PARTITION_BROKEN, s);
				s = mix(s);
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ,
						Math.floorMod((int) s, 100) < noclipPercent
								? BackroomsProps.NOCLIPPED_FOLDING_TABLE : BackroomsProps.PROP_FOLDING_TABLE, s);
				s = mix(s);
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ,
						Math.floorMod((int) s, 100) < noclipPercent
								? BackroomsProps.NOCLIPPED_FILE_CABINET : BackroomsProps.PROP_FILE_CABINET, s);
				// Computers: intact + damaged + noclipped mix
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, BackroomsProps.PROP_DESKTOP_COMPUTER, mix(s + 1));
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ,
						Math.floorMod((int) (s >>> 10), 100) < noclipPercent
								? BackroomsProps.PROP_DESKTOP_COMPUTER_NOCLIPPED
								: BackroomsProps.PROP_DESKTOP_COMPUTER_DAMAGED, mix(s + 2));
				// Office chairs: intact + noclipped mix
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, BackroomsProps.PROP_OFFICE_CHAIR, mix(s + 3));
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, BackroomsProps.NOCLIPPED_OFFICE_CHAIR, mix(s + 4));
				if (Math.floorMod((int) (s >>> 20), 100) < 45) {
					Block phone = switch (Math.floorMod((int) (s >>> 28), 3)) {
						case 0 -> BackroomsProps.PROP_PHONE;
						case 1 -> BackroomsProps.PROP_PHONE_CRACKED;
						default -> BackroomsProps.PROP_PHONE_NOCLIPPED;
					};
					placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, phone, mix(s + 5));
				}
			}
			case LOUNGE_APPLIANCES -> {
				// Intact sofa + half-sliced noclipped sofa + floor lamp + water cooler + big appliance (fridge/vending/TV)
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, BackroomsProps.PROP_SOFA, s);
				s = mix(s);
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ,
						Math.floorMod((int) s, 100) < Math.max(45, noclipPercent)
								? BackroomsProps.PROP_SOFA_NOCLIPPED : BackroomsProps.PROP_SOFA, s);
				s = mix(s);
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ,
						Math.floorMod((int) s, 100) < noclipPercent
								? BackroomsProps.NOCLIPPED_WATER_COOLER : BackroomsProps.PROP_WATER_COOLER, s);
				s = mix(s);
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, BackroomsProps.PROP_FLOOR_LAMP, s);
				s = mix(s);
				boolean ncBig = Math.floorMod((int) s, 100) < noclipPercent;
				Block big = switch (Math.floorMod((int) (s >>> 8), 4)) {
					case 0 -> ncBig ? BackroomsProps.NOCLIPPED_FRIDGE : BackroomsProps.PROP_FRIDGE;
					case 1 -> ncBig ? BackroomsProps.NOCLIPPED_VENDING_MACHINE : BackroomsProps.PROP_VENDING_MACHINE;
					case 2 -> ncBig ? BackroomsProps.NOCLIPPED_CRT_TV : BackroomsProps.PROP_CRT_TV;
					default -> BackroomsProps.PROP_MICROWAVE;
				};
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, big, mix(s + 7));
			}
			case STORAGE_HOARD -> {
				// Cardboard box mountain + curtain heap + standing mirror + fan + plant + TV + chair
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, BackroomsProps.PROP_CARDBOARD_PILE, s);
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, BackroomsProps.PROP_CURTAIN_HEAP, mix(s + 1));
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, BackroomsProps.PROP_STANDING_MIRROR, mix(s + 2));
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, BackroomsProps.PROP_ELECTRIC_FAN, mix(s + 3));
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, BackroomsProps.PROP_POTTED_PLANT_PLASTIC, mix(s + 4));
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ,
						Math.floorMod((int) (s >>> 12), 100) < noclipPercent
								? BackroomsProps.NOCLIPPED_CRT_TV : BackroomsProps.PROP_CRT_TV, mix(s + 5));
			}
			case WANDERER_CAMP -> {
				// Rare wanderer remains + lamp + chair + folding table + first-aid kit + occasional locked armory crate
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, BackroomsProps.WANDERER_REMAINS, s);
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, BackroomsProps.PROP_FLOOR_LAMP, mix(s + 1));
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, BackroomsProps.PROP_WOOD_CHAIR, mix(s + 2));
				placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, BackroomsProps.PROP_WALL_FIRST_AID_KIT, mix(s + 3));
				if (Math.floorMod((int) (s >>> 10), 2) == 0) {
					placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, BackroomsProps.LOCKED_ARMORY_CRATE, mix(s + 4));
				} else {
					placed |= tryPlaceAnywhereInChunk(chunk, seed, minX, minZ, BackroomsProps.PROP_FOLDING_TABLE, mix(s + 4));
				}
			}
		}
		return placed;
	}

	private static Block pickScatteredProp(Maze.Zone zone, boolean noclipped, long h) {
		int r = Math.floorMod((int) h, 100);
		if (r < 2) return BackroomsProps.WANDERER_REMAINS;
		if (r < 6) {
			if (noclipped) return BackroomsProps.PROP_PHONE_NOCLIPPED;
			return (h & 1) == 0 ? BackroomsProps.PROP_PHONE : BackroomsProps.PROP_PHONE_CRACKED;
		}
		if (zone == Maze.Zone.CUBICLES) {
			if (r < 32) return noclipped ? BackroomsProps.PROP_DESKTOP_COMPUTER_NOCLIPPED
					: ((h & 1) == 0 ? BackroomsProps.PROP_DESKTOP_COMPUTER : BackroomsProps.PROP_DESKTOP_COMPUTER_DAMAGED);
			if (r < 54) return noclipped ? BackroomsProps.NOCLIPPED_OFFICE_CHAIR : BackroomsProps.PROP_OFFICE_CHAIR;
			if (r < 72) return (h & 1) == 0 ? BackroomsProps.PROP_GLASS_PARTITION : BackroomsProps.PROP_GLASS_PARTITION_BROKEN;
			if (r < 86) return noclipped ? BackroomsProps.NOCLIPPED_FILE_CABINET : BackroomsProps.PROP_FILE_CABINET;
			return BackroomsProps.PROP_POTTED_PLANT_PLASTIC;
		}
		if (zone == Maze.Zone.MEGATHALL || zone == Maze.Zone.ARCHES) {
			if (r < 24) return noclipped ? BackroomsProps.PROP_SOFA_NOCLIPPED : BackroomsProps.PROP_SOFA;
			if (r < 46) return noclipped ? BackroomsProps.NOCLIPPED_WOOD_CHAIR : BackroomsProps.PROP_WOOD_CHAIR;
			if (r < 58) return noclipped ? BackroomsProps.NOCLIPPED_WATER_COOLER : BackroomsProps.PROP_WATER_COOLER;
			if (r < 68) return BackroomsProps.PROP_FLOOR_LAMP;
			if (r < 78) return noclipped ? BackroomsProps.NOCLIPPED_CRT_TV : BackroomsProps.PROP_CRT_TV;
			if (r < 86) return noclipped ? BackroomsProps.NOCLIPPED_VENDING_MACHINE : BackroomsProps.PROP_VENDING_MACHINE;
			if (r < 93) return noclipped ? BackroomsProps.NOCLIPPED_FRIDGE : BackroomsProps.PROP_FRIDGE;
			return BackroomsProps.PROP_STANDING_MIRROR;
		}
		// CLASSIC, PILLARS, CORRIDORS, DARKENED
		if (r < 24) return noclipped ? BackroomsProps.NOCLIPPED_WOOD_CHAIR : BackroomsProps.PROP_WOOD_CHAIR;
		if (r < 38) return noclipped ? BackroomsProps.NOCLIPPED_OFFICE_CHAIR : BackroomsProps.PROP_OFFICE_CHAIR;
		if (r < 50) return noclipped ? BackroomsProps.PROP_DESKTOP_COMPUTER_NOCLIPPED
				: ((h & 1) == 0 ? BackroomsProps.PROP_DESKTOP_COMPUTER : BackroomsProps.PROP_DESKTOP_COMPUTER_DAMAGED);
		if (r < 60) return noclipped ? BackroomsProps.NOCLIPPED_FOLDING_TABLE : BackroomsProps.PROP_FOLDING_TABLE;
		if (r < 68) return noclipped ? BackroomsProps.NOCLIPPED_FILE_CABINET : BackroomsProps.PROP_FILE_CABINET;
		if (r < 76) return noclipped ? BackroomsProps.NOCLIPPED_CRT_TV : BackroomsProps.PROP_CRT_TV;
		if (r < 82) return noclipped ? BackroomsProps.NOCLIPPED_WATER_COOLER : BackroomsProps.PROP_WATER_COOLER;
		if (r < 88) return BackroomsProps.PROP_ELECTRIC_FAN;
		if (r < 92) return BackroomsProps.PROP_POTTED_PLANT_PLASTIC;
		if (r < 96) return BackroomsProps.PROP_FLOOR_LAMP;
		return BackroomsProps.PROP_MICROWAVE;
	}

	private static boolean tryPlaceAnywhereInChunk(LevelChunk chunk, long seed, int minX, int minZ, Block block, long h) {
		int startIdx = Math.floorMod((int) h, 196);
		int facingBase = Math.floorMod((int) (h >>> 12), 4);
		for (int attempt = 0; attempt < 40; attempt++) {
			int cell = (startIdx + attempt * 11) % 196;
			int x = minX + 1 + (cell % 14);
			int z = minZ + 1 + (cell / 14);
			if (ManilaRoomPlan.roomAtBlock(seed, x, z) != null) continue;
			if (StaircasePlan.complexAtBlock(seed, x, z) != null) continue;
			if (ZonePoiPlan.isAnyPoiBlock(seed, x, z)) continue;

			int floorY = findCarpetFloorY(chunk, seed, x, z);
			if (floorY == Integer.MIN_VALUE) continue;
			BlockPos masterPos = new BlockPos(x, floorY + 1, z);
			Direction facing = HORIZONTALS[(facingBase + attempt) & 3];

			if (canPlacePropInChunk(chunk, seed, minX, minZ, masterPos, block, facing)) {
				placePropInChunk(chunk, masterPos, block, facing);
				return true;
			}
		}
		return false;
	}

	private static int findCarpetFloorY(LevelChunk chunk, long seed, int x, int z) {
		Maze.Zone zone = Maze.zoneAt(seed, x, z);
		int y = (zone == Maze.Zone.MEGATHALL)
				? MegahallPlan.walkableFloorY(seed, x, z)
				: Level0ChunkGenerator.FLOOR_Y;
		if (y <= Level0ChunkGenerator.MIN_Y + 2 || y >= Level0ChunkGenerator.MAX_Y - 4) {
			return Integer.MIN_VALUE;
		}
		BlockState floor = chunk.getBlockState(new BlockPos(x, y, z));
		return isValidFloor(floor) ? y : Integer.MIN_VALUE;
	}

	private static boolean isValidFloor(BlockState state) {
		return state.is(ModBlocks.CARPET) || state.is(ModBlocks.DRY_CARPET) || state.is(ModBlocks.THICK_CARPET);
	}

	private static boolean canPlacePropInChunk(LevelChunk chunk, long seed, int minX, int minZ, BlockPos masterPos, Block block, Direction facing) {
		int maxX = minX + 15;
		int maxZ = minZ + 15;
		if (block instanceof MultiPartPropBlock multi) {
			List<BlockPos> cells = multi.occupiedPositions(masterPos, facing);
			for (BlockPos p : cells) {
				// Keep every part strictly inside [minX+1 .. maxX-1] x [minZ+1 .. maxZ-1] (zero cross-chunk locks!)
				if (p.getX() < minX + 1 || p.getX() > maxX - 1 || p.getZ() < minZ + 1 || p.getZ() > maxZ - 1) {
					return false;
				}
				if (ZonePoiPlan.isAnyPoiBlock(seed, p.getX(), p.getZ())) return false;
				if (p.getY() >= Level0ChunkGenerator.MAX_Y - 1) return false;
				if (!chunk.getBlockState(p).isAir()) return false;
				if (p.getY() == masterPos.getY()) {
					if (!isValidFloor(chunk.getBlockState(p.below()))) return false;
				}
			}
			// Ensure at least one open tile in front so hallways stay passable
			BlockPos front = masterPos.relative(facing);
			if (front.getX() >= minX && front.getX() <= maxX && front.getZ() >= minZ && front.getZ() <= maxZ) {
				if (!chunk.getBlockState(front).isAir()) return false;
			}
			return true;
		}
		// Single-block prop
		if (masterPos.getX() < minX + 1 || masterPos.getX() > maxX - 1
				|| masterPos.getZ() < minZ + 1 || masterPos.getZ() > maxZ - 1) {
			return false;
		}
		if (!chunk.getBlockState(masterPos).isAir() || !chunk.getBlockState(masterPos.above()).isAir()) {
			return false;
		}
		if (!isValidFloor(chunk.getBlockState(masterPos.below()))) {
			return false;
		}
		// Keep at least 2 adjacent horizontal tiles open so single props never choke narrow doorways
		int openSides = 0;
		for (Direction d : HORIZONTALS) {
			BlockPos adj = masterPos.relative(d);
			if (chunk.getBlockState(adj).isAir()) openSides++;
		}
		return openSides >= 2;
	}

	private static void placePropInChunk(LevelChunk chunk, BlockPos masterPos, Block block, Direction facing) {
		BlockState masterState = block.defaultBlockState();
		if (masterState.hasProperty(HorizontalDirectionalBlock.FACING)) {
			masterState = masterState.setValue(HorizontalDirectionalBlock.FACING, facing);
		}
		chunk.setBlockState(masterPos, masterState, 0);
		if (block instanceof MultiPartPropBlock multi) {
			chunk.setBlockEntity(new PropBlockEntity(masterPos, masterState));
			BlockState partState = BackroomsProps.PROP_PART.defaultBlockState();
			for (BlockPos cell : multi.occupiedPositions(masterPos, facing)) {
				if (cell.equals(masterPos)) continue;
				chunk.setBlockState(cell, partState, 0);
				PropBlockEntity partBe = new PropBlockEntity(cell, partState);
				partBe.setMasterPos(masterPos);
				chunk.setBlockEntity(partBe);
			}
		} else if (block instanceof BlenderSinglePropBlock) {
			chunk.setBlockEntity(new PropBlockEntity(masterPos, masterState));
		}
	}

	private static long hashChunk(long seed, int cx, int cz, long salt) {
		return mix(seed ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xBF58476D1CE4E5B9L) ^ salt);
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
