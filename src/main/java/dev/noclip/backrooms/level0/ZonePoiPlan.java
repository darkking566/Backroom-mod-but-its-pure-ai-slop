package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.level1.Level1Blocks;
import dev.noclip.backrooms.level1.PipeBlock;
import dev.noclip.backrooms.props.BackroomsLootChestBlock;
import dev.noclip.backrooms.props.BackroomsProps;
import dev.noclip.backrooms.props.NoClipPropBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Deterministic points of interest inside Level 0 (scaled cleanly to CELL=10, CENTER=5, 5-block ceiling height):
 * 1. Macro-zone centerpieces: CHASM deep outpost, MEGATHALL skylight lounge, RED_ROOM shrine, DARKENED CRT signal.
 * 2. Rich sector micro-vignettes:
 *    - Classic 5: GLASS_OFFICE, WANDERER_SITE, NOCLIP_ALCOVE, BREAKER_NICHE, PEEK_CHAMBER
 *    - Overhaul 4: MEG_ISOLATION_POST, FAKE_DOOR_HALL, CEILING_CRAWLSPACE, LOCKED_UTILITY_ROOM
 * 3. Rare foreshadowing Mystery Exits (HUB_DOOR, FUN_DOOR).
 */
final class ZonePoiPlan {
	private static final int POI_SALT = 149;
	private static final int VIGNETTE_SALT = 211;
	private static final int MYSTERY_SALT = 277;
	static final int MYSTERY_REGION_SECTORS = 5; // 1 per Stage (300x300 blocks) so HUB / FUN doors are discoverable

	enum VignetteType {
		GLASS_OFFICE,
		WANDERER_SITE,
		NOCLIP_ALCOVE,
		BREAKER_NICHE,
		PEEK_CHAMBER,
		MEG_ISOLATION_POST,
		FAKE_DOOR_HALL,
		CEILING_CRAWLSPACE,
		LOCKED_UTILITY_ROOM
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
		if (cx != osx * Maze.SECTOR + 3 || cz != osz * Maze.SECTOR + 3) return false;
		return Maze.baseZoneForSector(seed, osx, osz) == Maze.Zone.RED_ROOM;
	}

	static boolean isDarkenedPoiCell(long seed, int cx, int cz) {
		int sx = Math.floorDiv(cx, Maze.SECTOR);
		int sz = Math.floorDiv(cz, Maze.SECTOR);
		int osx = Maze.largeZoneOriginSector(sx);
		int osz = Maze.largeZoneOriginSector(sz);
		if (osx == 0 && osz == 0) return false;
		if (cx != osx * Maze.SECTOR + 3 || cz != osz * Maze.SECTOR + 3) return false;
		return Maze.baseZoneForSector(seed, osx, osz) == Maze.Zone.DARKENED;
	}

	static boolean isStandardPoiCell(long seed, int cx, int cz) {
		if (mysteryExitAtCell(seed, cx, cz) != null) return true;
		if (vignetteAtCell(seed, cx, cz) != null) return true;
		if (AnomalyExitPlan.exitAtCell(seed, cx, cz) != null) return true;
		int sx = Math.floorDiv(cx, Maze.SECTOR);
		int sz = Math.floorDiv(cz, Maze.SECTOR);
		int osx = Maze.largeZoneOriginSector(sx);
		int osz = Maze.largeZoneOriginSector(sz);
		if (osx == 0 && osz == 0) return false;
		if (cx != osx * Maze.SECTOR + 3 || cz != osz * Maze.SECTOR + 3) return false;
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

	/** Optional upper ceiling crawlspace cavity for CEILING_CRAWLSPACE vignettes (y in 38..42). */
	static BlockState ceilingCrawlspaceBlockAt(long seed, int y, int cx, int cz, int ix, int iz) {
		Vignette v = vignetteAtCell(seed, cx, cz);
		if (v == null || v.type() != VignetteType.CEILING_CRAWLSPACE) return null;
		if (y < Level0ChunkGenerator.CEILING_Y || y > Level0ChunkGenerator.CEILING_Y + 4) return null;
		if (ix < 2 || ix > 8 || iz < 2 || iz > 8) return null;

		boolean border = ix == 2 || ix == 8 || iz == 2 || iz == 8;
		boolean hatch = ix == 4 && iz == 4;
		if (y == Level0ChunkGenerator.CEILING_Y) {
			if (hatch) return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
			return ModBlocks.CEILING_TILE.defaultBlockState();
		}
		if (y == Level0ChunkGenerator.CEILING_Y + 4) {
			return Blocks.CONCRETE.black().defaultBlockState();
		}
		if (border) {
			return Level1Blocks.GARAGE_WALL.defaultBlockState();
		}
		if (y == Level0ChunkGenerator.CEILING_Y + 1) {
			if (ix == 6 && iz == 6) return Level1Blocks.SUPPLY_CRATE.defaultBlockState();
			if (ix == 7 && iz == 5) return BackroomsProps.WANDERER_REMAINS.defaultBlockState().setValue(NoClipPropBlock.FACING, Direction.WEST);
			if (hatch) return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
			return Blocks.AIR.defaultBlockState();
		}
		if (y == Level0ChunkGenerator.CEILING_Y + 3 && iz == 5) {
			return Level1Blocks.CEILING_PIPE.defaultBlockState().setValue(PipeBlock.AXIS, Direction.Axis.X);
		}
		if (y == Level0ChunkGenerator.CEILING_Y + 3 && ix == 5 && iz == 6) {
			return Level1Blocks.BARE_BULB.defaultBlockState();
		}
		return Blocks.AIR.defaultBlockState();
	}

	static BlockState chasmBlockAt(long seed, int y, int cx, int cz, int ix, int iz, int floorY, int ceilY) {
		if (floorY == Integer.MIN_VALUE || y < floorY || y > ceilY) return null;
		int baseCx = Math.floorDiv(cx, ChasmPlan.MACRO_CELLS) * ChasmPlan.MACRO_CELLS;
		int baseCz = Math.floorDiv(cz, ChasmPlan.MACRO_CELLS) * ChasmPlan.MACRO_CELLS;
		ChasmOutpost outpost = chasmOutpostInMacro(seed, baseCx, baseCz);
		if (outpost == null || outpost.cx() != cx || outpost.cz() != cz) return null;

		int c = Maze.CENTER;
		if (y == floorY) {
			if (ix >= c - 2 && ix <= c + 2 && iz >= c - 2 && iz <= c + 2) {
				return Level1Blocks.GARAGE_FLOOR.defaultBlockState();
			}
			return null;
		}

		boolean openCavern = ceilY > floorY + 8;
		if (y == ceilY) {
			if (!openCavern && (ix == c || ix == c + 1) && iz == c) {
				return ModBlocks.FLUORESCENT_LIGHT.defaultBlockState();
			}
			return null;
		}

		if (openCavern) {
			if (y == floorY + 5 && ix >= c - 2 && ix <= c + 2 && iz >= c - 2 && iz <= c + 2) {
				return ((ix == c || ix == c + 1) && iz == c)
						? ModBlocks.FLUORESCENT_LIGHT.defaultBlockState()
						: ModBlocks.CEILING_TILE.defaultBlockState();
			}
			if (((ix == c - 2 && iz == c - 2) || (ix == c + 2 && iz == c + 2)) && y >= floorY + 1 && y <= floorY + 4) {
				return (y == floorY + 1 ? ModBlocks.ROUGH_WALLPAPER_BASEBOARD : ModBlocks.ROUGH_WALLPAPER)
						.defaultBlockState();
			}
		}

		if (y == floorY + 1) {
			if (ix == c && iz == c) {
				return outpost.tier() >= 4
						? BackroomsProps.ANOMALY_VAULT_CHEST.defaultBlockState()
								.setValue(BackroomsLootChestBlock.FACING, Direction.SOUTH)
						: Level1Blocks.SUPPLY_CRATE.defaultBlockState();
			}
			if (ix == c + 1 && iz == c) {
				return Level1Blocks.OFFICE_DESK.defaultBlockState();
			}
			if (ix == c && iz == c + 1) {
				return BackroomsProps.WANDERER_REMAINS.defaultBlockState()
						.setValue(NoClipPropBlock.FACING, Direction.EAST);
			}
			if (ix == c - 1 && iz == c + 1) {
				return BackroomsProps.INDUSTRIAL_FUSE_BOX.defaultBlockState()
						.setValue(NoClipPropBlock.FACING, Direction.EAST);
			}
		}

		if (y == floorY + 2 && ix == c + 1 && iz == c && y < ceilY) {
			return Level1Blocks.OLD_COMPUTER.defaultBlockState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
		}

		if ((ix == c || ix == c + 1) && (iz == c || iz == c + 1) && y > floorY && y < ceilY) {
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

		int c = Maze.CENTER;
		if (y == floorY) {
			if (ix >= c && ix <= c + 1 && iz >= c && iz <= c + 1) {
				return ModBlocks.MANILA_WOOD_FLOOR.defaultBlockState();
			}
			if (ix >= c - 1 && ix <= c + 2 && iz >= c - 1 && iz <= c + 2) {
				return ModBlocks.THICK_CARPET.defaultBlockState();
			}
			return null;
		}

		if (((ix == c - 1 && iz == c - 1) || (ix == c + 2 && iz == c + 2)) && y >= floorY + 1 && y <= floorY + 5) {
			return (y == floorY + 1 ? ModBlocks.PALE_WALLPAPER_BASEBOARD : ModBlocks.PALE_WALLPAPER)
					.defaultBlockState();
		}

		if (y == floorY + 6 && ix >= c - 1 && ix <= c + 2 && iz >= c - 1 && iz <= c + 2) {
			if (((ix == c || ix == c + 1) && iz == c) || (ix == c && iz == c + 1)) {
				return ModBlocks.FLUORESCENT_LIGHT.defaultBlockState();
			}
			if (ix == c + 1 && iz == c + 1) {
				return ModBlocks.BROKEN_FLUORESCENT_LIGHT.defaultBlockState();
			}
			return ModBlocks.CEILING_TILE.defaultBlockState();
		}

		if (y == floorY + 1) {
			if (ix == c && iz == c) {
				return BackroomsProps.NOCLIPPED_FOLDING_TABLE.defaultBlockState()
						.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
			}
			if (ix == c + 1 && iz == c) {
				return BackroomsProps.NOCLIPPED_CRT_TV.defaultBlockState()
						.setValue(NoClipPropBlock.FACING, Direction.WEST);
			}
			if (ix == c && iz == c + 1) {
				return BackroomsProps.NOCLIPPED_OFFICE_CHAIR.defaultBlockState()
						.setValue(NoClipPropBlock.FACING, Direction.NORTH);
			}
			if (ix == c + 1 && iz == c + 1) {
				return BackroomsProps.NOCLIPPED_WOOD_CHAIR.defaultBlockState()
						.setValue(NoClipPropBlock.FACING, Direction.NORTH);
			}
			if (ix == c - 1 && iz == c + 1) {
				return BackroomsProps.NOCLIPPED_WATER_COOLER.defaultBlockState()
						.setValue(NoClipPropBlock.FACING, Direction.EAST);
			}
			if (ix == c + 2 && iz == c) {
				return Level1Blocks.SUPPLY_CRATE.defaultBlockState();
			}
		}
		return null;
	}

	static BlockState standardZoneBlockAt(long seed, int y, int cx, int cz, int ix, int iz, Maze.Zone zone) {
		int c = Maze.CENTER; // 5
		if (zone == Maze.Zone.RED_ROOM && isRedRoomPoiCell(seed, cx, cz)) {
			if (y == Level0ChunkGenerator.FLOOR_Y && ix == c && iz == c) {
				return ModBlocks.VOID_STONE.defaultBlockState();
			}
			if (y == Level0ChunkGenerator.FLOOR_Y + 1) {
				if (ix == c && iz == c) {
					return BackroomsProps.RED_ANOMALY_CLUSTER.defaultBlockState()
							.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
				}
				if (ix == c + 1 && iz == c) {
					return BackroomsProps.ANOMALY_VAULT_CHEST.defaultBlockState()
							.setValue(BackroomsLootChestBlock.FACING, Direction.WEST);
				}
				if (ix == c - 1 && iz == c + 1) {
					return BackroomsProps.WANDERER_REMAINS.defaultBlockState()
							.setValue(NoClipPropBlock.FACING, Direction.EAST);
				}
			}
			return null;
		}
		if (zone == Maze.Zone.DARKENED && isDarkenedPoiCell(seed, cx, cz)) {
			if (y == Level0ChunkGenerator.FLOOR_Y + 1) {
				if (ix == c && iz == c) {
					return BackroomsProps.NOCLIPPED_CRT_TV.defaultBlockState()
							.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
				}
				if (ix == c + 1 && iz == c) {
					return BackroomsProps.WANDERER_REMAINS.defaultBlockState()
							.setValue(NoClipPropBlock.FACING, Direction.WEST);
				}
				if (ix == c - 1 && iz == c + 1) {
					return Level1Blocks.SUPPLY_CRATE.defaultBlockState();
				}
			}
			return null;
		}

		AnomalyExitPlan.Placement anomaly = AnomalyExitPlan.exitAtCell(seed, cx, cz);
		if (anomaly != null) {
			return AnomalyExitPlan.blockAt(anomaly, y, ix, iz);
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
		int c = Maze.CENTER;
		if (kind == MysteryKind.FUN && y == Level0ChunkGenerator.FLOOR_Y && ix >= c - 1 && ix <= c + 1 && iz >= c - 1 && iz <= c + 1) {
			return ModBlocks.DRY_CARPET.defaultBlockState();
		}
		if (iz != c || ix < c - 1 || ix > c + 1) return null;
		boolean hub = kind == MysteryKind.HUB;
		Block wall = hub ? ModBlocks.ROUGH_WALLPAPER : ModBlocks.PALE_WALLPAPER;
		Block base = hub ? ModBlocks.ROUGH_WALLPAPER_BASEBOARD : ModBlocks.PALE_WALLPAPER_BASEBOARD;
		if (ix == c - 1 || ix == c + 1) {
			if (y == Level0ChunkGenerator.FLOOR_Y + 1) return base.defaultBlockState();
			if (y >= Level0ChunkGenerator.FLOOR_Y + 2 && y < Level0ChunkGenerator.CEILING_Y) return wall.defaultBlockState();
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
		if (y >= Level0ChunkGenerator.FLOOR_Y + 3 && y < Level0ChunkGenerator.CEILING_Y) {
			return wall.defaultBlockState();
		}
		return null;
	}

	private static BlockState vignetteBlockAt(VignetteType type, int y, int ix, int iz) {
		int c = Maze.CENTER; // 5
		int fy = Level0ChunkGenerator.FLOOR_Y;
		int cy = Level0ChunkGenerator.CEILING_Y;
		return switch (type) {
			case GLASS_OFFICE -> {
				if (y == fy + 1) {
					if ((ix == c - 1 && (iz == c - 1 || iz == c)) || (ix == c && iz == c - 1)) {
						yield Blocks.STAINED_GLASS.gray().defaultBlockState();
					}
					if (ix == c && iz == c) {
						yield Level1Blocks.OFFICE_DESK.defaultBlockState()
								.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
					}
					if (ix == c + 1 && iz == c) {
						yield BackroomsProps.NOCLIPPED_FILE_CABINET.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
					}
					if (ix == c && iz == c + 1) {
						yield BackroomsProps.NOCLIPPED_OFFICE_CHAIR.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.NORTH);
					}
					if (ix == c + 1 && iz == c + 1) {
						yield Level1Blocks.WASTEBASKET.defaultBlockState();
					}
				} else if (y == fy + 2) {
					if ((ix == c - 1 && (iz == c - 1 || iz == c)) || (ix == c && iz == c - 1)) {
						yield Blocks.STAINED_GLASS.gray().defaultBlockState();
					}
					if (ix == c && iz == c) {
						yield Level1Blocks.OLD_COMPUTER.defaultBlockState()
								.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
					}
				} else if (y == fy + 3) {
					if ((ix == c - 1 && (iz == c - 1 || iz == c)) || (ix == c && iz == c - 1)) {
						yield Blocks.STAINED_GLASS.gray().defaultBlockState();
					}
				}
				yield null;
			}
			case WANDERER_SITE -> {
				if (y == fy && (ix == c || ix == c + 1) && iz == c) {
					yield ModBlocks.THICK_CARPET.defaultBlockState();
				}
				if (y == fy + 1) {
					if (ix == c && iz == c) {
						yield BackroomsProps.WANDERER_REMAINS.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.EAST);
					}
					if (ix == c + 1 && iz == c) {
						yield BackroomsProps.NOCLIPPED_WOOD_CHAIR.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.WEST);
					}
					if (ix == c - 1 && iz == c) {
						yield Level1Blocks.SUPPLY_CRATE.defaultBlockState();
					}
					if (ix == c + 1 && iz == c + 1) {
						yield ModBlocks.FIELD_NOTE.defaultBlockState()
								.setValue(FieldNoteBlock.FACING, Direction.NORTH)
								.setValue(FieldNoteBlock.VARIANT, 2);
					}
				}
				yield null;
			}
			case NOCLIP_ALCOVE -> {
				if (y == fy + 1) {
					if (ix == c && iz == c) {
						yield BackroomsProps.NOCLIPPED_VENDING_MACHINE.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
					}
					if (ix == c + 1 && iz == c) {
						yield BackroomsProps.NOCLIPPED_WATER_COOLER.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
					}
					if (ix == c - 1 && iz == c) {
						yield BackroomsProps.NOCLIPPED_FILE_CABINET.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
					}
					if (ix == c + 1 && iz == c + 1) {
						yield BackroomsProps.NOCLIPPED_OFFICE_CHAIR.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.WEST);
					}
				}
				yield null;
			}
			case BREAKER_NICHE -> {
				if (y == fy + 1) {
					if (ix == c && iz == c - 1) yield ModBlocks.ROUGH_WALLPAPER_BASEBOARD.defaultBlockState();
					if (ix == c && iz == c) {
						yield BackroomsProps.INDUSTRIAL_FUSE_BOX.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
					}
					if (ix == c + 1 && iz == c) yield Level1Blocks.SUPPLY_CRATE.defaultBlockState();
				} else if (y >= fy + 2 && y < cy) {
					if (ix == c && iz == c - 1) yield ModBlocks.ROUGH_WALLPAPER.defaultBlockState();
					if (y == fy + 4 && (ix == c - 1 || ix == c + 1) && iz == c) {
						yield Level1Blocks.CEILING_PIPE.defaultBlockState().setValue(PipeBlock.AXIS, Direction.Axis.X);
					}
				}
				yield null;
			}
			case PEEK_CHAMBER -> {
				if (y == fy && ix == c && iz == c) yield ModBlocks.THICK_CARPET.defaultBlockState();
				boolean sideWall = (ix == c - 1 || ix == c + 1) && iz >= c - 1 && iz <= c + 1;
				boolean windowCol = ix == c && iz == c + 1;
				if (y == fy + 1) {
					if (sideWall) yield ModBlocks.PALE_WALLPAPER_BASEBOARD.defaultBlockState();
					if (windowCol) yield Blocks.GLASS.defaultBlockState();
					if (ix == c && iz == c) {
						yield BackroomsProps.NOCLIPPED_WOOD_CHAIR.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
					}
				} else if (y == fy + 2 || y == fy + 3) {
					if (sideWall) yield ModBlocks.PALE_WALLPAPER.defaultBlockState();
					if (windowCol) yield Blocks.GLASS.defaultBlockState();
				} else if (y >= fy + 4 && y < cy) {
					if (sideWall || windowCol) yield ModBlocks.PALE_WALLPAPER.defaultBlockState();
				}
				yield null;
			}
			case MEG_ISOLATION_POST -> {
				// M.E.G. temporary plastic-curtain field outpost
				if (y == fy && ix >= c - 2 && ix <= c + 2 && iz >= c - 2 && iz <= c + 2) {
					yield Level1Blocks.GARAGE_FLOOR.defaultBlockState();
				}
				boolean curtain = ((ix == c - 2 || ix == c + 2) && iz >= c - 2 && iz <= c + 1)
						|| (iz == c - 2 && ix >= c - 2 && ix <= c + 2);
				if (curtain && y >= fy + 1 && y <= fy + 4) {
					yield Blocks.STAINED_GLASS_PANE.lightBlue().defaultBlockState();
				}
				if (y == fy + 1) {
					if (ix == c && iz == c - 1) yield Level1Blocks.OFFICE_DESK.defaultBlockState();
					if (ix == c - 1 && iz == c - 1) yield Level1Blocks.OFFICE_DESK.defaultBlockState();
					if (ix == c + 1 && iz == c - 1) yield Level1Blocks.SUPPLY_CRATE.defaultBlockState();
					if (ix == c + 1 && iz == c) {
						yield BackroomsProps.NOCLIPPED_FILE_CABINET.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.WEST);
					}
					if (ix == c && iz == c) {
						yield BackroomsProps.NOCLIPPED_OFFICE_CHAIR.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.NORTH);
					}
				} else if (y == fy + 2) {
					if (ix == c && iz == c - 1) {
						yield Level1Blocks.OLD_COMPUTER.defaultBlockState()
								.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
					}
					if (ix == c - 1 && iz == c - 1) {
						yield ModBlocks.FIELD_NOTE.defaultBlockState()
								.setValue(FieldNoteBlock.FACING, Direction.SOUTH)
								.setValue(FieldNoteBlock.VARIANT, 0);
					}
				}
				yield null;
			}
			case FAKE_DOOR_HALL -> {
				// Row of 3 identical wooden doors backed immediately by solid wallpaper/rough wall
				if (iz == c - 1 && ix >= c - 2 && ix <= c + 2 && y >= fy + 1 && y < cy) {
					yield (y == fy + 1 ? ModBlocks.WALLPAPER_BASEBOARD : ModBlocks.WALLPAPER).defaultBlockState();
				}
				if (iz == c && (ix == c - 2 || ix == c || ix == c + 2)) {
					if (y == fy + 1) {
						yield ModBlocks.MANILA_DOOR.defaultBlockState()
								.setValue(ManilaDoorBlock.FACING, Direction.SOUTH)
								.setValue(ManilaDoorBlock.OPEN, false);
					}
					if (y == fy + 2) {
						yield ModBlocks.MANILA_DOOR_TOP.defaultBlockState()
								.setValue(ManilaDoorBlock.FACING, Direction.SOUTH)
								.setValue(ManilaDoorBlock.OPEN, false);
					}
				}
				yield null;
			}
			case CEILING_CRAWLSPACE -> {
				// Ladder column on ix=4, iz=4 backed by rough column at ix=4, iz=3 leading up into ceiling cavity
				if (ix == 4 && iz == 3 && y >= fy + 1 && y < cy) {
					yield (y == fy + 1 ? ModBlocks.ROUGH_WALLPAPER_BASEBOARD : ModBlocks.ROUGH_WALLPAPER).defaultBlockState();
				}
				if (ix == 4 && iz == 4 && y >= fy + 1 && y < cy) {
					yield Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
				}
				if (ix == 6 && iz == 4 && y == fy + 1) {
					yield BackroomsProps.NOCLIPPED_WOOD_CHAIR.defaultBlockState().setValue(NoClipPropBlock.FACING, Direction.WEST);
				}
				yield null;
			}
			case LOCKED_UTILITY_ROOM -> {
				// Compact locked 3x3 closet (ix 4..6, iz 3..5) with locked shutter door at (5,5) and fuse box outside at (7,5)
				boolean closetWall = ((ix == 3 || ix == 7) && iz >= 3 && iz <= 5)
						|| (iz == 2 && ix >= 3 && ix <= 7)
						|| (iz == 5 && (ix == 4 || ix == 6));
				if (closetWall && y >= fy + 1 && y < cy) {
					yield (y == fy + 1 ? ModBlocks.ROUGH_WALLPAPER_BASEBOARD : ModBlocks.ROUGH_WALLPAPER).defaultBlockState();
				}
				if (ix == 5 && iz == 5) {
					if (y == fy + 1) {
						yield ModBlocks.LOCKED_UTILITY_DOOR.defaultBlockState()
								.setValue(Level0EventBlocks.LockedUtilityDoorBlock.FACING, Direction.SOUTH)
								.setValue(Level0EventBlocks.LockedUtilityDoorBlock.HALF, DoubleBlockHalf.LOWER);
					}
					if (y == fy + 2) {
						yield ModBlocks.LOCKED_UTILITY_DOOR.defaultBlockState()
								.setValue(Level0EventBlocks.LockedUtilityDoorBlock.FACING, Direction.SOUTH)
								.setValue(Level0EventBlocks.LockedUtilityDoorBlock.HALF, DoubleBlockHalf.UPPER);
					}
					if (y >= fy + 3 && y < cy) yield ModBlocks.ROUGH_WALLPAPER.defaultBlockState();
				}
				if (y == fy + 1) {
					if (ix == 5 && iz == 3) {
						yield BackroomsProps.ANOMALY_VAULT_CHEST.defaultBlockState()
								.setValue(BackroomsLootChestBlock.FACING, Direction.SOUTH);
					}
					if (ix == 4 && iz == 3) yield Level1Blocks.SUPPLY_CRATE.defaultBlockState();
					if (ix == 7 && iz == 6) {
						yield BackroomsProps.INDUSTRIAL_FUSE_BOX.defaultBlockState()
								.setValue(NoClipPropBlock.FACING, Direction.SOUTH);
					}
				}
				yield null;
			}
		};
	}

	private static ChasmOutpost computeChasmOutpost(long seed, int baseCx, int baseCz) {
		// ChasmPlan guarantees pad (2, 2) is always a flat deep non-stair pad with tier >= 2.
		int px = 2, pz = 2;
		int tier = ChasmPlan.padTier(seed, baseCx, baseCz, px, pz);
		if (tier < 2) return null;
		int cx = baseCx + px;
		int cz = baseCz + pz;
		return new ChasmOutpost(cx, cz, tier, ChasmPlan.tierFloorY(tier), ChasmPlan.cellCeilingY(seed, cx, cz));
	}

	private static MegahallLounge computeMegahallLounge(long seed, int baseCx, int baseCz) {
		MegahallPlan.Style style = MegahallPlan.styleAt(seed, baseCx, baseCz);
		if (style == MegahallPlan.Style.TERRACED) {
			return new MegahallLounge(baseCx + 2, baseCz + 2, Level0ChunkGenerator.FLOOR_Y - 1);
		}
		int[][] candidates = {{0, 0}, {1, 1}, {0, 1}, {1, 0}};
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
		if (spawnRoll >= 88) return null; // High vignette density: ~88% of eligible sectors

		int start = Math.floorMod(hash(seed, sx, sz, VIGNETTE_SALT + 1), 16);
		for (int i = 0; i < 16; i++) {
			int pick = (start + i) % 16;
			int cx = sx * Maze.SECTOR + 1 + (pick & 3);
			int cz = sz * Maze.SECTOR + 1 + (pick >> 2);
			if (ManilaRoomPlan.isProtectedCell(seed, cx, cz)) continue;
			if (StaircasePlan.isProtectedCell(seed, cx, cz)) continue;
			if (Maze.isAtriumCell(seed, cx, cz)) continue;
			if (mysteryExitAtCell(seed, cx, cz) != null) continue;
			if (AnomalyExitPlan.exitAtCell(seed, cx, cz) != null) continue;

			int typeRoll = Math.floorMod(hash(seed, cx, cz, VIGNETTE_SALT + 2), 100);
			VignetteType type = switch (zone) {
				case CUBICLES -> typeRoll < 30 ? VignetteType.GLASS_OFFICE
						: (typeRoll < 48 ? VignetteType.MEG_ISOLATION_POST
						: (typeRoll < 65 ? VignetteType.LOCKED_UTILITY_ROOM
						: (typeRoll < 80 ? VignetteType.BREAKER_NICHE : VignetteType.NOCLIP_ALCOVE)));
				case ARCHES -> typeRoll < 28 ? VignetteType.PEEK_CHAMBER
						: (typeRoll < 52 ? VignetteType.GLASS_OFFICE
						: (typeRoll < 74 ? VignetteType.WANDERER_SITE : VignetteType.CEILING_CRAWLSPACE));
				case CORRIDORS -> typeRoll < 28 ? VignetteType.FAKE_DOOR_HALL
						: (typeRoll < 50 ? VignetteType.NOCLIP_ALCOVE
						: (typeRoll < 70 ? VignetteType.BREAKER_NICHE
						: (typeRoll < 86 ? VignetteType.LOCKED_UTILITY_ROOM : VignetteType.PEEK_CHAMBER)));
				case PILLARS, CLASSIC -> typeRoll < 18 ? VignetteType.MEG_ISOLATION_POST
						: (typeRoll < 36 ? VignetteType.WANDERER_SITE
						: (typeRoll < 52 ? VignetteType.CEILING_CRAWLSPACE
						: (typeRoll < 68 ? VignetteType.NOCLIP_ALCOVE
						: (typeRoll < 84 ? VignetteType.BREAKER_NICHE : VignetteType.PEEK_CHAMBER))));
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
			int lx = 1 + Math.floorMod(hash(seed, sx, sz, MYSTERY_SALT + 2), 4);
			int lz = 1 + Math.floorMod(hash(seed, sx, sz, MYSTERY_SALT + 3), 4);
			int cx = sx * Maze.SECTOR + lx;
			int cz = sz * Maze.SECTOR + lz;
			if (ManilaRoomPlan.isProtectedCell(seed, cx, cz)) continue;
			if (StaircasePlan.isProtectedCell(seed, cx, cz)) continue;
			if (Maze.isAtriumCell(seed, cx, cz)) continue;
			if (AnomalyExitPlan.exitAtCell(seed, cx, cz) != null) continue;
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
