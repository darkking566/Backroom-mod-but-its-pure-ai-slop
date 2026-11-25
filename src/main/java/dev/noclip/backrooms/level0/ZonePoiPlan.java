package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.level1.Level1Blocks;
import dev.noclip.backrooms.level1.PipeBlock;
import dev.noclip.backrooms.props.BackroomsLootChestBlock;
import dev.noclip.backrooms.props.BackroomsProps;
import dev.noclip.backrooms.props.NoClipPropBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Deterministic points of interest inside Level 0:
 * 1. Special macro-zones:
 *    - {@link Maze.Zone#CHASM}: a lit deep-tier maintenance outpost with an anomaly vault or supply crate.
 *    - {@link Maze.Zone#MEGATHALL}: a suspended low-ceiling lightwell lounge stranded in the vast dark hall.
 *    - {@link Maze.Zone#RED_ROOM}: a crimson anomaly shrine with crystal cluster, vault chest, and remains.
 *    - {@link Maze.Zone#DARKENED}: a dead-lamp CRT static signal beside wanderer remains and a supply crate.
 * 2. Sparse sector micro-vignettes across standard maze zones:
 *    - {@link VignetteType#GLASS_OFFICE}: smoked-glass corner office with desk, computer, cabinet, chair, wastebasket.
 *    - {@link VignetteType#WANDERER_SITE}: damp carpet under a broken lamp with wanderer remains, chair, crate, note.
 *    - {@link VignetteType#NOCLIP_ALCOVE}: surreal reality-clipped vending machine, water cooler, cabinet, office chair.
 */
final class ZonePoiPlan {
	private static final int POI_SALT = 149;
	private static final int VIGNETTE_SALT = 211;
	private static final int MYSTERY_SALT = 277;
	static final int MYSTERY_REGION_SECTORS = 8;

	enum VignetteType {
		GLASS_OFFICE,
		WANDERER_SITE,
		NOCLIP_ALCOVE,
		BREAKER_NICHE,
		PEEK_CHAMBER
	}

	enum MysteryKind {
		HUB,
		FUN
	}

	record ChasmOutpost(int cx, int cz, int tier, int floorY, int ceilY) {
	}

	record MegahallLounge(int cx, int cz, int floorY) {
	}

	record Vignette(int cx, int cz, VignetteType type) {
	}

	record MysteryExit(int cx, int cz, MysteryKind kind) {
	}

	private ZonePoiPlan() {
	}

	static ChasmOutpost chasmOutpostInMacro(long seed, int baseCx, int baseCz) {
		return CHASM_CACHE.get().getOrCompute(seed, baseCx, baseCz);
	}

	static MegahallLounge megahallLoungeInMacro(long seed, int baseCx, int baseCz) {
		return MEGA_CACHE.get().getOrCompute(seed, baseCx, baseCz);
	}

	static Vignette vignetteInSector(long seed, int sx, int sz) {
		return VIGNETTE_CACHE.get().getOrCompute(seed, sx, sz);
	}

	static Vignette vignetteAtCell(long seed, int cx, int cz) {
		int sx = Math.floorDiv(cx, Maze.SECTOR);
		int sz = Math.floorDiv(cz, Maze.SECTOR);
		Vignette v = vignetteInSector(seed, sx, sz);
		return v != null && v.cx() == cx && v.cz() == cz ? v : null;
	}

	static MysteryExit mysteryExitInRegion(long seed, int rx, int rz) {
		return MYSTERY_CACHE.get().getOrCompute(seed, rx, rz);
	}

	static MysteryExit mysteryExitAtCell(long seed, int cx, int cz) {
		int rx = Math.floorDiv(Math.floorDiv(cx, Maze.SECTOR), MYSTERY_REGION_SECTORS);
		int rz = Math.floorDiv(Math.floorDiv(cz, Maze.SECTOR), MYSTERY_REGION_SECTORS);
		MysteryExit exit = mysteryExitInRegion(seed, rx, rz);
		return exit != null && exit.cx() == cx && exit.cz() == cz ? exit : null;
	}

	static boolean isFunDoorCell(long seed, int x, int z) {
		MysteryExit exit = mysteryExitAtCell(seed, Math.floorDiv(x, Maze.CELL), Math.floorDiv(z, Maze.CELL));
		return exit != null && exit.kind() == MysteryKind.FUN;
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
		if (mysteryExitAtCell(seed, cx, cz) != null) return true;
		if (vignetteAtCell(seed, cx, cz) != null) return true;
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

	static boolean isAnyPoiBlock(long seed, int x, int z) {
		int cx = Math.floorDiv(x, Maze.CELL);
		int cz = Math.floorDiv(z, Maze.CELL);
		if (isProtectedCell(seed, cx, cz)) return true;
		int chasmBaseCx = Math.floorDiv(cx, ChasmPlan.MACRO_CELLS) * ChasmPlan.MACRO_CELLS;
		int chasmBaseCz = Math.floorDiv(cz, ChasmPlan.MACRO_CELLS) * ChasmPlan.MACRO_CELLS;
		ChasmOutpost outpost = chasmOutpostInMacro(seed, chasmBaseCx, chasmBaseCz);
		if (outpost != null && outpost.cx() == cx && outpost.cz() == cz) return true;
		int megaBaseCx = Math.floorDiv(cx, MegahallPlan.MACRO_CELLS) * MegahallPlan.MACRO_CELLS;
		int megaBaseCz = Math.floorDiv(cz, MegahallPlan.MACRO_CELLS) * MegahallPlan.MACRO_CELLS;
		MegahallLounge lounge = megahallLoungeInMacro(seed, megaBaseCx, megaBaseCz);
		return lounge != null && lounge.cx() == cx && lounge.cz() == cz;
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
			return null;
		}
		MysteryExit mystery = mysteryExitAtCell(seed, cx, cz);
		if (mystery != null) {
			return mysteryBlockAt(mystery.kind(), y, ix, iz);
		}
		Vignette v = vignetteAtCell(seed, cx, cz);
		if (v == null) return null;
		return vignetteBlockAt(v.type(), y, ix, iz);
	}

	private static BlockState mysteryBlockAt(MysteryKind kind, int y, int ix, int iz) {
		if (kind == MysteryKind.FUN && y == Level0ChunkGenerator.FLOOR_Y && ix >= 2 && ix <= 4 && iz >= 2 && iz <= 4) {
			return ModBlocks.DRY_CARPET.defaultBlockState();
		}
		if (iz != 3 || ix < 2 || ix > 4) return null;
		boolean hub = kind == MysteryKind.HUB;
		Block wall = hub ? ModBlocks.ROUGH_WALLPAPER : ModBlocks.PALE_WALLPAPER;
		Block base = hub ? ModBlocks.ROUGH_WALLPAPER_BASEBOARD : ModBlocks.PALE_WALLPAPER_BASEBOARD;
		if (ix == 2 || ix == 4) {
			if (y == Level0ChunkGenerator.FLOOR_Y + 1) return base.defaultBlockState();
			if (y == Level0ChunkGenerator.FLOOR_Y + 2 || y == Level0ChunkGenerator.FLOOR_Y + 3) return wall.defaultBlockState();
			return null;
		}
		if (y == Level0ChunkGenerator.FLOOR_Y + 1) {
			return (hub ? ModBlocks.HUB_DOOR : ModBlocks.FUN_DOOR).defaultBlockState()
					.setValue(MysteryExitBlock.AXIS, Direction.Axis.Z);
		}
		if (y == Level0ChunkGenerator.FLOOR_Y + 2) {
			return (hub ? ModBlocks.HUB_DOOR_TOP : ModBlocks.FUN_DOOR_TOP).defaultBlockState()
					.setValue(MysteryExitBlock.AXIS, Direction.Axis.Z);
		}
		if (y == Level0ChunkGenerator.FLOOR_Y + 3) {
			return wall.defaultBlockState();
		}
		return null;
	}

	private static BlockState vignetteBlockAt(VignetteType type, int y, int ix, int iz) {
		return switch (type) {
			case GLASS_OFFICE -> {
				if (y == Level0ChunkGenerator.FLOOR_Y + 1) {
					if ((ix == 2 && (iz == 2 || iz == 3)) || (ix == 3 && iz == 2)) {
						yield Blocks.STAINED_GLASS.gray().defaultBlockState();
					}
					if (ix == 3 && iz == 3) {
						yield Level1Blocks.OFFICE_DESK.defaultBlockState()
								.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
					}
					if (ix == 4 && iz == 3) {
						yield BackroomsProps.NOCLIPPED_FILE_CABINET.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
					}
					if (ix == 3 && iz == 4) {
						yield BackroomsProps.NOCLIPPED_OFFICE_CHAIR.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.NORTH);
					}
					if (ix == 4 && iz == 4) {
						yield Level1Blocks.WASTEBASKET.defaultBlockState();
					}
				} else if (y == Level0ChunkGenerator.FLOOR_Y + 2) {
					if ((ix == 2 && (iz == 2 || iz == 3)) || (ix == 3 && iz == 2)) {
						yield Blocks.STAINED_GLASS.gray().defaultBlockState();
					}
					if (ix == 3 && iz == 3) {
						yield Level1Blocks.OLD_COMPUTER.defaultBlockState()
								.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
					}
				}
				yield null;
			}
			case WANDERER_SITE -> {
				if (y == Level0ChunkGenerator.FLOOR_Y && (ix == 3 || ix == 4) && iz == 3) {
					yield ModBlocks.THICK_CARPET.defaultBlockState();
				}
				if (y == Level0ChunkGenerator.FLOOR_Y + 1) {
					if (ix == 3 && iz == 3) {
						yield BackroomsProps.WANDERER_REMAINS.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.EAST);
					}
					if (ix == 4 && iz == 3) {
						yield BackroomsProps.NOCLIPPED_WOOD_CHAIR.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.WEST);
					}
					if (ix == 2 && iz == 3) {
						yield Level1Blocks.SUPPLY_CRATE.defaultBlockState();
					}
					if (ix == 4 && iz == 4) {
						yield ModBlocks.FIELD_NOTE.defaultBlockState()
								.setValue(FieldNoteBlock.FACING, Direction.NORTH)
								.setValue(FieldNoteBlock.VARIANT, 2);
					}
				}
				yield null;
			}
			case NOCLIP_ALCOVE -> {
				if (y == Level0ChunkGenerator.FLOOR_Y + 1) {
					if (ix == 3 && iz == 3) {
						yield BackroomsProps.NOCLIPPED_VENDING_MACHINE.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
					}
					if (ix == 4 && iz == 3) {
						yield BackroomsProps.NOCLIPPED_WATER_COOLER.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
					}
					if (ix == 2 && iz == 3) {
						yield BackroomsProps.NOCLIPPED_FILE_CABINET.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
					}
					if (ix == 4 && iz == 4) {
						yield BackroomsProps.NOCLIPPED_OFFICE_CHAIR.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.WEST);
					}
				}
				yield null;
			}
			case BREAKER_NICHE -> {
				if (y == Level0ChunkGenerator.FLOOR_Y + 1) {
					if (ix == 3 && iz == 2) {
						yield ModBlocks.ROUGH_WALLPAPER_BASEBOARD.defaultBlockState();
					}
					if (ix == 3 && iz == 3) {
						yield BackroomsProps.INDUSTRIAL_FUSE_BOX.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
					}
					if (ix == 4 && iz == 3) {
						yield Level1Blocks.SUPPLY_CRATE.defaultBlockState();
					}
				} else if (y == Level0ChunkGenerator.FLOOR_Y + 2) {
					if (ix == 3 && iz == 2) {
						yield ModBlocks.ROUGH_WALLPAPER.defaultBlockState();
					}
				} else if (y == Level0ChunkGenerator.FLOOR_Y + 3) {
					if (ix == 3 && iz == 2) {
						yield ModBlocks.ROUGH_WALLPAPER.defaultBlockState();
					}
					if ((ix == 2 || ix == 4) && iz == 3) {
						yield Level1Blocks.CEILING_PIPE.defaultBlockState()
								.setValue(PipeBlock.AXIS, Direction.Axis.X);
					}
				}
				yield null;
			}
			case PEEK_CHAMBER -> {
				if (y == Level0ChunkGenerator.FLOOR_Y && ix == 3 && iz == 3) {
					yield ModBlocks.THICK_CARPET.defaultBlockState();
				}
				boolean sideWall = (ix == 2 || ix == 4) && iz >= 2 && iz <= 4;
				boolean windowCol = ix == 3 && iz == 4;
				if (y == Level0ChunkGenerator.FLOOR_Y + 1) {
					if (sideWall) yield ModBlocks.PALE_WALLPAPER_BASEBOARD.defaultBlockState();
					if (windowCol) yield Blocks.GLASS.defaultBlockState();
					if (ix == 3 && iz == 3) {
						yield BackroomsProps.NOCLIPPED_WOOD_CHAIR.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
					}
				} else if (y == Level0ChunkGenerator.FLOOR_Y + 2) {
					if (sideWall) yield ModBlocks.PALE_WALLPAPER.defaultBlockState();
					if (windowCol) yield Blocks.GLASS.defaultBlockState();
				} else if (y == Level0ChunkGenerator.FLOOR_Y + 3) {
					if (sideWall || windowCol) yield ModBlocks.PALE_WALLPAPER.defaultBlockState();
				}
				yield null;
			}
		};
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

	private static Vignette computeVignette(long seed, int sx, int sz) {
		if (sx == 0 && sz == 0) return null;
		Maze.Zone zone = Maze.baseZoneForSector(seed, sx, sz);
		if (zone != Maze.Zone.CLASSIC && zone != Maze.Zone.CUBICLES && zone != Maze.Zone.CORRIDORS
				&& zone != Maze.Zone.ARCHES && zone != Maze.Zone.PILLARS) {
			return null;
		}
		int spawnRoll = Math.floorMod(hash(seed, sx, sz, VIGNETTE_SALT), 100);
		if (spawnRoll >= 75) return null;

		int start = Math.floorMod(hash(seed, sx, sz, VIGNETTE_SALT + 1), 16);
		for (int i = 0; i < 16; i++) {
			int pick = (start + i) % 16;
			int cx = sx * Maze.SECTOR + 2 + (pick & 3);
			int cz = sz * Maze.SECTOR + 2 + (pick >> 2);
			if (ManilaRoomPlan.isProtectedCell(seed, cx, cz)) continue;
			if (StaircasePlan.isProtectedCell(seed, cx, cz)) continue;
			if (Maze.isAtriumCell(seed, cx, cz)) continue;
			if (mysteryExitAtCell(seed, cx, cz) != null) continue;

			int typeRoll = Math.floorMod(hash(seed, cx, cz, VIGNETTE_SALT + 2), 100);
			VignetteType type = switch (zone) {
				case CUBICLES -> typeRoll < 50 ? VignetteType.GLASS_OFFICE
						: (typeRoll < 70 ? VignetteType.BREAKER_NICHE
						: (typeRoll < 85 ? VignetteType.NOCLIP_ALCOVE : VignetteType.PEEK_CHAMBER));
				case ARCHES -> typeRoll < 35 ? VignetteType.GLASS_OFFICE
						: (typeRoll < 65 ? VignetteType.PEEK_CHAMBER
						: (typeRoll < 85 ? VignetteType.WANDERER_SITE : VignetteType.BREAKER_NICHE));
				case CORRIDORS -> typeRoll < 40 ? VignetteType.NOCLIP_ALCOVE
						: (typeRoll < 65 ? VignetteType.BREAKER_NICHE
						: (typeRoll < 85 ? VignetteType.PEEK_CHAMBER : VignetteType.WANDERER_SITE));
				case PILLARS, CLASSIC -> typeRoll < 30 ? VignetteType.WANDERER_SITE
						: (typeRoll < 55 ? VignetteType.NOCLIP_ALCOVE
						: (typeRoll < 80 ? VignetteType.BREAKER_NICHE : VignetteType.PEEK_CHAMBER));
				default -> VignetteType.WANDERER_SITE;
			};
			return new Vignette(cx, cz, type);
		}
		return null;
	}

	private static MysteryExit computeMysteryExit(long seed, int rx, int rz) {
		if (rx == 0 && rz == 0) return null;
		int total = MYSTERY_REGION_SECTORS * MYSTERY_REGION_SECTORS;
		int start = Math.floorMod(hash(seed, rx, rz, MYSTERY_SALT), total);
		MysteryKind kind = (hash(seed, rx, rz, MYSTERY_SALT + 1) & 1L) == 0 ? MysteryKind.HUB : MysteryKind.FUN;

		for (int i = 0; i < total; i++) {
			int pick = (start + i) % total;
			int sx = rx * MYSTERY_REGION_SECTORS + (pick % MYSTERY_REGION_SECTORS);
			int sz = rz * MYSTERY_REGION_SECTORS + (pick / MYSTERY_REGION_SECTORS);
			if (sx == 0 && sz == 0) continue;

			Maze.Zone zone = Maze.baseZoneForSector(seed, sx, sz);
			if (zone != Maze.Zone.CLASSIC && zone != Maze.Zone.CORRIDORS
					&& zone != Maze.Zone.ARCHES && zone != Maze.Zone.CUBICLES) {
				continue;
			}
			int lx = 2 + Math.floorMod(hash(seed, sx, sz, MYSTERY_SALT + 2), 4);
			int lz = 2 + Math.floorMod(hash(seed, sx, sz, MYSTERY_SALT + 3), 4);
			int cx = sx * Maze.SECTOR + lx;
			int cz = sz * Maze.SECTOR + lz;
			if (ManilaRoomPlan.isProtectedCell(seed, cx, cz)) continue;
			if (StaircasePlan.isProtectedCell(seed, cx, cz)) continue;
			if (Maze.isAtriumCell(seed, cx, cz)) continue;
			return new MysteryExit(cx, cz, kind);
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
	private static final ThreadLocal<MacroCache<Vignette>> VIGNETTE_CACHE =
			ThreadLocal.withInitial(() -> new MacroCache<>(ZonePoiPlan::computeVignette));
	private static final ThreadLocal<MacroCache<MysteryExit>> MYSTERY_CACHE =
			ThreadLocal.withInitial(() -> new MacroCache<>(ZonePoiPlan::computeMysteryExit));

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
