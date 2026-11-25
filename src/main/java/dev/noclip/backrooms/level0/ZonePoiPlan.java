package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.level1.Level1Blocks;
import dev.noclip.backrooms.props.BackroomsLootChestBlock;
import dev.noclip.backrooms.props.BackroomsProps;
import dev.noclip.backrooms.props.NoClipPropBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Deterministic points of interest inside Level 0's special macro-zones:
 * - {@link Maze.Zone#CHASM}: a lit deep-tier maintenance outpost with an anomaly vault or supply crate.
 * - {@link Maze.Zone#MEGATHALL}: a suspended low-ceiling lightwell lounge stranded in the vast dark hall.
 * - {@link Maze.Zone#RED_ROOM}: a crimson anomaly shrine with crystal cluster, vault chest, and remains.
 * - {@link Maze.Zone#DARKENED}: a dead-lamp CRT static signal beside wanderer remains and a supply crate.
 */
final class ZonePoiPlan {
	private static final int POI_SALT = 149;

	record ChasmOutpost(int cx, int cz, int tier, int floorY, int ceilY) {
	}

	record MegahallLounge(int cx, int cz, int floorY) {
	}

	private ZonePoiPlan() {
	}

	static ChasmOutpost chasmOutpostInMacro(long seed, int baseCx, int baseCz) {
		return CHASM_CACHE.get().getOrCompute(seed, baseCx, baseCz);
	}

	static MegahallLounge megahallLoungeInMacro(long seed, int baseCx, int baseCz) {
		return MEGA_CACHE.get().getOrCompute(seed, baseCx, baseCz);
	}

	static boolean isRedRoomPoiCell(long seed, int cx, int cz) {
		int sx = Math.floorDiv(cx, Maze.SECTOR);
		int sz = Math.floorDiv(cz, Maze.SECTOR);
		int osx = Maze.largeZoneOriginSector(sx);
		int osz = Maze.largeZoneOriginSector(sz);
		if (osx == 0 && osz == 0) return false;
		if (cx != osx * Maze.SECTOR + 12 || cz != osz * Maze.SECTOR + 12) return false;
		return Maze.baseZoneForSector(seed, osx, osz) == Maze.Zone.RED_ROOM;
	}

	static boolean isDarkenedPoiCell(long seed, int cx, int cz) {
		int sx = Math.floorDiv(cx, Maze.SECTOR);
		int sz = Math.floorDiv(cz, Maze.SECTOR);
		int osx = Maze.largeZoneOriginSector(sx);
		int osz = Maze.largeZoneOriginSector(sz);
		if (osx == 0 && osz == 0) return false;
		if (cx != osx * Maze.SECTOR + 12 || cz != osz * Maze.SECTOR + 12) return false;
		return Maze.baseZoneForSector(seed, osx, osz) == Maze.Zone.DARKENED;
	}

	static boolean isStandardPoiCell(long seed, int cx, int cz) {
		int sx = Math.floorDiv(cx, Maze.SECTOR);
		int sz = Math.floorDiv(cz, Maze.SECTOR);
		int osx = Maze.largeZoneOriginSector(sx);
		int osz = Maze.largeZoneOriginSector(sz);
		if (osx == 0 && osz == 0) return false;
		if (cx != osx * Maze.SECTOR + 12 || cz != osz * Maze.SECTOR + 12) return false;
		Maze.Zone zone = Maze.baseZoneForSector(seed, osx, osz);
		return zone == Maze.Zone.RED_ROOM || zone == Maze.Zone.DARKENED;
	}

	static boolean isProtectedCell(long seed, int cx, int cz) {
		return isStandardPoiCell(seed, cx, cz);
	}

	static BlockState chasmBlockAt(long seed, int y, int cx, int cz, int ix, int iz, int floorY, int ceilY) {
		if (floorY == Integer.MIN_VALUE || y < floorY || y > ceilY) return null;
		int baseCx = Math.floorDiv(cx, ChasmPlan.MACRO_CELLS) * ChasmPlan.MACRO_CELLS;
		int baseCz = Math.floorDiv(cz, ChasmPlan.MACRO_CELLS) * ChasmPlan.MACRO_CELLS;
		ChasmOutpost outpost = chasmOutpostInMacro(seed, baseCx, baseCz);
		if (outpost == null || outpost.cx() != cx || outpost.cz() != cz) return null;

		if (y == floorY) {
			if (ix >= 2 && ix <= 5 && iz >= 2 && iz <= 5) {
				return Level1Blocks.GARAGE_FLOOR.defaultBlockState();
			}
			return null;
		}

		boolean openCavern = ceilY > floorY + 8;
		if (y == ceilY) {
			if (!openCavern && (ix == 3 || ix == 4) && iz == 3) {
				return ModBlocks.FLUORESCENT_LIGHT.defaultBlockState();
			}
			return null;
		}

		if (openCavern) {
			if (y == floorY + 4 && ix >= 2 && ix <= 5 && iz >= 2 && iz <= 5) {
				return ((ix == 3 || ix == 4) && iz == 3)
						? ModBlocks.FLUORESCENT_LIGHT.defaultBlockState()
						: ModBlocks.CEILING_TILE.defaultBlockState();
			}
			if (((ix == 2 && iz == 2) || (ix == 5 && iz == 5)) && y >= floorY + 1 && y <= floorY + 3) {
				return (y == floorY + 1 ? ModBlocks.ROUGH_WALLPAPER_BASEBOARD : ModBlocks.ROUGH_WALLPAPER)
						.defaultBlockState();
			}
		}

		if (y == floorY + 1) {
			if (ix == 3 && iz == 3) {
				return outpost.tier() >= 4
						? BackroomsProps.ANOMALY_VAULT_CHEST.defaultBlockState()
								.setValue(BackroomsLootChestBlock.FACING, Direction.SOUTH)
						: Level1Blocks.SUPPLY_CRATE.defaultBlockState();
			}
			if (ix == 4 && iz == 3) {
				return BackroomsProps.NOCLIPPED_FOLDING_TABLE.defaultBlockState()
						.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
			}
			if (ix == 3 && iz == 4) {
				return BackroomsProps.WANDERER_REMAINS.defaultBlockState()
						.setValue(NoClipPropBlock.FACING, Direction.EAST);
			}
			if (ix == 2 && iz == 4) {
				return BackroomsProps.INDUSTRIAL_FUSE_BOX.defaultBlockState()
						.setValue(NoClipPropBlock.FACING, Direction.EAST);
			}
		}

		if (y == floorY + 2 && ix == 4 && iz == 3 && y < ceilY) {
			return Level1Blocks.OLD_COMPUTER.defaultBlockState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
		}

		if ((ix == 3 || ix == 4) && (iz == 3 || iz == 4) && y > floorY && y < ceilY) {
			return Blocks.AIR.defaultBlockState();
		}
		return null;
	}

	static BlockState megahallBlockAt(long seed, int y, int cx, int cz, int ix, int iz, int floorY, int ceilY) {
		if (y < floorY || y >= ceilY) return null;
		int baseCx = Math.floorDiv(cx, MegahallPlan.MACRO_CELLS) * MegahallPlan.MACRO_CELLS;
		int baseCz = Math.floorDiv(cz, MegahallPlan.MACRO_CELLS) * MegahallPlan.MACRO_CELLS;
		MegahallLounge lounge = megahallLoungeInMacro(seed, baseCx, baseCz);
		if (lounge == null || lounge.cx() != cx || lounge.cz() != cz) return null;

		if (y == floorY) {
			if (ix >= 3 && ix <= 4 && iz >= 3 && iz <= 4) {
				return ModBlocks.MANILA_WOOD_FLOOR.defaultBlockState();
			}
			if (ix >= 2 && ix <= 5 && iz >= 2 && iz <= 5) {
				return ModBlocks.THICK_CARPET.defaultBlockState();
			}
			return null;
		}

		if (((ix == 2 && iz == 2) || (ix == 5 && iz == 5)) && y >= floorY + 1 && y <= floorY + 4) {
			return (y == floorY + 1 ? ModBlocks.PALE_WALLPAPER_BASEBOARD : ModBlocks.PALE_WALLPAPER)
					.defaultBlockState();
		}

		if (y == floorY + 5 && ix >= 2 && ix <= 5 && iz >= 2 && iz <= 5) {
			if (((ix == 3 || ix == 4) && iz == 3) || (ix == 3 && iz == 4)) {
				return ModBlocks.FLUORESCENT_LIGHT.defaultBlockState();
			}
			if (ix == 4 && iz == 4) {
				return ModBlocks.BROKEN_FLUORESCENT_LIGHT.defaultBlockState();
			}
			return ModBlocks.CEILING_TILE.defaultBlockState();
		}

		if (y == floorY + 1) {
			if (ix == 3 && iz == 3) {
				return BackroomsProps.NOCLIPPED_FOLDING_TABLE.defaultBlockState()
						.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
			}
			if (ix == 4 && iz == 3) {
				return BackroomsProps.NOCLIPPED_CRT_TV.defaultBlockState()
						.setValue(NoClipPropBlock.FACING, Direction.WEST);
			}
			if (ix == 3 && iz == 4) {
				return BackroomsProps.NOCLIPPED_OFFICE_CHAIR.defaultBlockState()
						.setValue(NoClipPropBlock.FACING, Direction.NORTH);
			}
			if (ix == 4 && iz == 4) {
				return BackroomsProps.NOCLIPPED_WOOD_CHAIR.defaultBlockState()
						.setValue(NoClipPropBlock.FACING, Direction.NORTH);
			}
			if (ix == 2 && iz == 4) {
				return BackroomsProps.NOCLIPPED_WATER_COOLER.defaultBlockState()
						.setValue(NoClipPropBlock.FACING, Direction.EAST);
			}
			if (ix == 5 && iz == 3) {
				return Level1Blocks.SUPPLY_CRATE.defaultBlockState();
			}
		}
		return null;
	}

	static BlockState standardZoneBlockAt(long seed, int y, int cx, int cz, int ix, int iz, Maze.Zone zone) {
		if (zone == Maze.Zone.RED_ROOM && isRedRoomPoiCell(seed, cx, cz)) {
			if (y == Level0ChunkGenerator.FLOOR_Y && ix == 3 && iz == 3) {
				return ModBlocks.VOID_STONE.defaultBlockState();
			}
			if (y == Level0ChunkGenerator.FLOOR_Y + 1) {
				if (ix == 3 && iz == 3) {
					return BackroomsProps.RED_ANOMALY_CLUSTER.defaultBlockState()
							.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
				}
				if (ix == 4 && iz == 3) {
					return BackroomsProps.ANOMALY_VAULT_CHEST.defaultBlockState()
							.setValue(BackroomsLootChestBlock.FACING, Direction.WEST);
				}
				if (ix == 2 && iz == 4) {
					return BackroomsProps.WANDERER_REMAINS.defaultBlockState()
							.setValue(NoClipPropBlock.FACING, Direction.EAST);
				}
			}
			return null;
		}
		if (zone == Maze.Zone.DARKENED && isDarkenedPoiCell(seed, cx, cz)) {
			if (y == Level0ChunkGenerator.FLOOR_Y + 1) {
				if (ix == 3 && iz == 3) {
					return BackroomsProps.NOCLIPPED_CRT_TV.defaultBlockState()
							.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
				}
				if (ix == 4 && iz == 3) {
					return BackroomsProps.WANDERER_REMAINS.defaultBlockState()
							.setValue(NoClipPropBlock.FACING, Direction.WEST);
				}
				if (ix == 2 && iz == 4) {
					return Level1Blocks.SUPPLY_CRATE.defaultBlockState();
				}
			}
		}
		return null;
	}

	private static ChasmOutpost computeChasmOutpost(long seed, int baseCx, int baseCz) {
		int bestPx = -1, bestPz = -1, bestTier = 0;
		long bestTie = Long.MIN_VALUE;
		for (int px = 1; px <= ChasmPlan.PADS - 2; px++) {
			for (int pz = 1; pz <= ChasmPlan.PADS - 2; pz++) {
				int tier = ChasmPlan.padTier(seed, baseCx, baseCz, px, pz);
				if (tier < 2) continue;
				long tie = hash(seed, baseCx + px, baseCz + pz, POI_SALT);
				if (tier > bestTier || (tier == bestTier && tie > bestTie)) {
					bestTier = tier;
					bestPx = px;
					bestPz = pz;
					bestTie = tie;
				}
			}
		}
		if (bestPx < 0) return null;
		int cx = baseCx + bestPx * ChasmPlan.PAD_CELLS + 1;
		int cz = baseCz + bestPz * ChasmPlan.PAD_CELLS + 1;
		return new ChasmOutpost(cx, cz, bestTier, ChasmPlan.tierFloorY(bestTier), ChasmPlan.cellCeilingY(seed, cx, cz));
	}

	private static MegahallLounge computeMegahallLounge(long seed, int baseCx, int baseCz) {
		MegahallPlan.Style style = MegahallPlan.styleAt(seed, baseCx, baseCz);
		if (style == MegahallPlan.Style.TERRACED) {
			return new MegahallLounge(baseCx + 7, baseCz + 7, Level0ChunkGenerator.FLOOR_Y - 3);
		}
		int[][] candidates = {{1, 1}, {2, 2}, {1, 2}, {2, 1}, {0, 1}, {1, 0}, {2, 0}, {0, 2}, {0, 0}, {3, 3}};
		int offset = Math.floorMod(hash(seed, baseCx, baseCz, POI_SALT + 1), candidates.length);
		for (int i = 0; i < candidates.length; i++) {
			int[] b = candidates[(offset + i) % candidates.length];
			if (MegahallPlan.bayFloor(seed, baseCx, baseCz, b[0], b[1]) == MegahallPlan.BayFloor.FLAT) {
				return new MegahallLounge(
						baseCx + MegahallPlan.bayCenter(b[0]),
						baseCz + MegahallPlan.bayCenter(b[1]),
						Level0ChunkGenerator.FLOOR_Y);
			}
		}
		return null;
	}

	private static long hash(long seed, int x, int z, int salt) {
		long h = mix(seed + salt);
		h = mix(h ^ x);
		return mix(h ^ z);
	}

	private static long mix(long v) {
		v += 0x9E3779B97F4A7C15L;
		v = (v ^ (v >>> 30)) * 0xBF58476D1CE4E5B9L;
		v = (v ^ (v >>> 27)) * 0x94D049BB133111EBL;
		return v ^ (v >>> 31);
	}

	private static final ThreadLocal<MacroCache<ChasmOutpost>> CHASM_CACHE =
			ThreadLocal.withInitial(() -> new MacroCache<>(ZonePoiPlan::computeChasmOutpost));
	private static final ThreadLocal<MacroCache<MegahallLounge>> MEGA_CACHE =
			ThreadLocal.withInitial(() -> new MacroCache<>(ZonePoiPlan::computeMegahallLounge));

	@FunctionalInterface
	private interface MacroComputer<T> {
		T compute(long seed, int baseCx, int baseCz);
	}

	private static final class MacroCache<T> {
		private static final int MASK = 127;
		private final long[] seeds = new long[MASK + 1];
		private final int[] bxs = new int[MASK + 1];
		private final int[] bzs = new int[MASK + 1];
		@SuppressWarnings("unchecked")
		private final T[] values = (T[]) new Object[MASK + 1];
		private final boolean[] valid = new boolean[MASK + 1];
		private final MacroComputer<T> computer;

		MacroCache(MacroComputer<T> computer) {
			this.computer = computer;
		}

		T getOrCompute(long seed, int baseCx, int baseCz) {
			int idx = (int) ((seed ^ (baseCx * 0x9E3779B97F4A7C15L) ^ (baseCz * 0xC2B2AE3D27D4EB4FL)) >>> 32) & MASK;
			if (valid[idx] && seeds[idx] == seed && bxs[idx] == baseCx && bzs[idx] == baseCz) {
				return values[idx];
			}
			T computed = computer.compute(seed, baseCx, baseCz);
			seeds[idx] = seed;
			bxs[idx] = baseCx;
			bzs[idx] = baseCz;
			values[idx] = computed;
			valid[idx] = true;
			return computed;
		}
	}
}
