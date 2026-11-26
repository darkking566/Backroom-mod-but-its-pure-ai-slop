package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.level1.Level1Blocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Deterministic placement of Level 0's multi-form non-door anomalous exits inside every 5x5 Stage (300x300 blocks).
 *
 * Every Stage guarantees:
 * - 1 physical Level 1 oil vent hatch (in addition to flickering walls)
 * - 1 Level 2 Pipe Dreams steam grate in the floor
 * - 1 Level 161 1980 Appalachia fading mountain-lodge poster (tear or break to cross)
 * - 5 rotating branch exits drawn from the remaining 10 forms (Level 188 wall window, Level 27 peeling carpet,
 *   Level 194 hollow plaster wall, Level 0.5 giant mirror, Level 499 neon bowling door, Level 0.2 remodel door,
 *   Level 193 floating mid-air door, Level 13 cracked glass door, Level 68 echoing diner door, Level ! red fake EXIT).
 */
final class AnomalyExitPlan {
	private static final int EXIT_SALT = 331;

	enum EntryKind {
		OIL_VENT_1,
		STEAM_GRATE_2,
		POSTER_161,
		WINDOW_188,
		CARPET_27,
		PLASTER_194,
		MIRROR_0P5,
		DOOR_BOWLING_499,
		DOOR_REMODEL_0P2,
		DOOR_FLOATING_193,
		DOOR_GLASS_13,
		DOOR_DINER_68,
		DOOR_FAKE_EXIT_RUN
	}

	private static final EntryKind[] ROTATING_POOL = {
			EntryKind.WINDOW_188,
			EntryKind.CARPET_27,
			EntryKind.PLASTER_194,
			EntryKind.MIRROR_0P5,
			EntryKind.DOOR_BOWLING_499,
			EntryKind.DOOR_REMODEL_0P2,
			EntryKind.DOOR_FLOATING_193,
			EntryKind.DOOR_GLASS_13,
			EntryKind.DOOR_DINER_68,
			EntryKind.DOOR_FAKE_EXIT_RUN
	};

	record Placement(int cx, int cz, EntryKind kind) {
	}

	private AnomalyExitPlan() {
	}

	static List<Placement> placementsInStage(long seed, int stx, int stz) {
		return CACHE.get().getOrCompute(seed, stx, stz);
	}

	static Placement exitAtCell(long seed, int cx, int cz) {
		int sx = Math.floorDiv(cx, Maze.SECTOR);
		int sz = Math.floorDiv(cz, Maze.SECTOR);
		int stx = Maze.stageCoord(sx);
		int stz = Maze.stageCoord(sz);
		for (Placement p : placementsInStage(seed, stx, stz)) {
			if (p.cx() == cx && p.cz() == cz) return p;
		}
		return null;
	}

	static BlockState blockAt(Placement p, int y, int ix, int iz) {
		int c = Maze.CENTER; // 5
		int fy = Level0ChunkGenerator.FLOOR_Y;
		int cy = Level0ChunkGenerator.CEILING_Y;

		return switch (p.kind()) {
			case OIL_VENT_1 -> {
				if (y == fy && ix == c && iz == c) yield Level1Blocks.DAMP_GARAGE_FLOOR.defaultBlockState();
				if (iz == c - 1 && ix >= c - 1 && ix <= c + 1 && y >= fy + 1 && y < cy) {
					yield (y == fy + 1 ? ModBlocks.ROUGH_WALLPAPER_BASEBOARD : ModBlocks.ROUGH_WALLPAPER).defaultBlockState();
				}
				if (ix == c && iz == c && y == fy + 1) {
					yield ModBlocks.OIL_VENT_HATCH.defaultBlockState().setValue(AnomalyExitBlock.FACING, Direction.SOUTH);
				}
				yield null;
			}
			case STEAM_GRATE_2 -> {
				if (y == fy && ix >= c - 1 && ix <= c + 1 && iz >= c - 1 && iz <= c + 1) {
					if (ix == c && iz == c) {
						yield ModBlocks.STEAM_GRATE.defaultBlockState().setValue(AnomalyExitBlock.FACING, Direction.SOUTH);
					}
					yield Level1Blocks.GARAGE_FLOOR.defaultBlockState();
				}
				yield null;
			}
			case POSTER_161 -> {
				// Feature wall backing + fading 1980 mountain lodge poster mounted at eye level (fy + 2)
				if (iz == c - 1 && ix >= c - 1 && ix <= c + 1 && y >= fy + 1 && y < cy) {
					yield (y == fy + 1 ? ModBlocks.WALLPAPER_BASEBOARD : ModBlocks.WALLPAPER).defaultBlockState();
				}
				if (ix == c && iz == c && y == fy + 2) {
					yield ModBlocks.ANOMALY_POSTER.defaultBlockState().setValue(AnomalyExitBlock.FACING, Direction.SOUTH);
				}
				yield null;
			}
			case WINDOW_188 -> {
				if (iz == c - 1 && ix >= c - 1 && ix <= c + 1 && y >= fy + 1 && y < cy) {
					if (ix == c && y == fy + 2) {
						yield ModBlocks.ANOMALY_WINDOW.defaultBlockState().setValue(AnomalyExitBlock.FACING, Direction.SOUTH);
					}
					yield (y == fy + 1 ? ModBlocks.PALE_WALLPAPER_BASEBOARD : ModBlocks.PALE_WALLPAPER).defaultBlockState();
				}
				yield null;
			}
			case CARPET_27 -> {
				if (y == fy && ix >= c - 1 && ix <= c + 1 && iz >= c - 1 && iz <= c + 1) {
					if (ix == c && iz == c) {
						yield ModBlocks.PEELING_CARPET_EXIT.defaultBlockState().setValue(AnomalyExitBlock.FACING, Direction.SOUTH);
					}
					yield ModBlocks.DRY_CARPET.defaultBlockState();
				}
				yield null;
			}
			case PLASTER_194 -> {
				if (iz == c - 1 && ix >= c - 1 && ix <= c + 1 && y >= fy + 1 && y < cy) {
					if (ix == c && (y == fy + 1 || y == fy + 2)) {
						yield ModBlocks.HOLLOW_PLASTER_EXIT.defaultBlockState().setValue(AnomalyExitBlock.FACING, Direction.SOUTH);
					}
					yield (y == fy + 1 ? ModBlocks.ROUGH_WALLPAPER_BASEBOARD : ModBlocks.ROUGH_WALLPAPER).defaultBlockState();
				}
				yield null;
			}
			case MIRROR_0P5 -> {
				if (iz == c - 1 && ix >= c - 1 && ix <= c + 1 && y >= fy + 1 && y < cy) {
					yield (y == fy + 1 ? ModBlocks.PALE_WALLPAPER_BASEBOARD : ModBlocks.PALE_WALLPAPER).defaultBlockState();
				}
				if (ix == c && iz == c) {
					if (y == fy + 1) {
						yield ModBlocks.GIANT_MIRROR_EXIT.defaultBlockState()
								.setValue(AnomalyExitBlock.FACING, Direction.SOUTH)
								.setValue(AnomalyExitBlock.HALF, DoubleBlockHalf.LOWER);
					}
					if (y == fy + 2) {
						yield ModBlocks.GIANT_MIRROR_EXIT.defaultBlockState()
								.setValue(AnomalyExitBlock.FACING, Direction.SOUTH)
								.setValue(AnomalyExitBlock.HALF, DoubleBlockHalf.UPPER);
					}
				}
				yield null;
			}
			case DOOR_BOWLING_499 -> portalDoorFrame(y, ix, iz, c, fy, cy, AnomalyPortalDoorBlock.Kind.BOWLING, false);
			case DOOR_REMODEL_0P2 -> portalDoorFrame(y, ix, iz, c, fy, cy, AnomalyPortalDoorBlock.Kind.REMODEL, false);
			case DOOR_FLOATING_193 -> portalDoorFrame(y, ix, iz, c, fy, cy, AnomalyPortalDoorBlock.Kind.FLOATING, true);
			case DOOR_GLASS_13 -> portalDoorFrame(y, ix, iz, c, fy, cy, AnomalyPortalDoorBlock.Kind.GLASS, false);
			case DOOR_DINER_68 -> portalDoorFrame(y, ix, iz, c, fy, cy, AnomalyPortalDoorBlock.Kind.DINER, false);
			case DOOR_FAKE_EXIT_RUN -> portalDoorFrame(y, ix, iz, c, fy, cy, AnomalyPortalDoorBlock.Kind.FAKE_EXIT, false);
		};
	}

	private static BlockState portalDoorFrame(int y, int ix, int iz, int c, int fy, int cy,
			AnomalyPortalDoorBlock.Kind kind, boolean floating) {
		if (kind == AnomalyPortalDoorBlock.Kind.BOWLING && y == fy && ix >= c - 1 && ix <= c + 1 && iz == c) {
			return (ix == c ? Level1Blocks.CORRIDOR_FLOOR : Level1Blocks.NEON_MAGENTA).defaultBlockState();
		}
		if (iz != c - 1 || ix < c - 1 || ix > c + 1) return null;
		int doorLowY = floating ? fy + 3 : fy + 1;
		int doorHighY = doorLowY + 1;

		if (ix == c - 1 || ix == c + 1) {
			if (y == fy + 1) return ModBlocks.PALE_WALLPAPER_BASEBOARD.defaultBlockState();
			if (y >= fy + 2 && y < cy) return ModBlocks.PALE_WALLPAPER.defaultBlockState();
			return null;
		}
		if (y == doorLowY) {
			return ModBlocks.ANOMALY_PORTAL_DOOR.defaultBlockState()
					.setValue(AnomalyPortalDoorBlock.FACING, Direction.SOUTH)
					.setValue(AnomalyPortalDoorBlock.HALF, DoubleBlockHalf.LOWER)
					.setValue(AnomalyPortalDoorBlock.KIND, kind);
		}
		if (y == doorHighY) {
			return ModBlocks.ANOMALY_PORTAL_DOOR.defaultBlockState()
					.setValue(AnomalyPortalDoorBlock.FACING, Direction.SOUTH)
					.setValue(AnomalyPortalDoorBlock.HALF, DoubleBlockHalf.UPPER)
					.setValue(AnomalyPortalDoorBlock.KIND, kind);
		}
		if (y >= fy + 1 && y < cy) {
			return (y == fy + 1 ? ModBlocks.PALE_WALLPAPER_BASEBOARD : ModBlocks.PALE_WALLPAPER).defaultBlockState();
		}
		return null;
	}

	private static List<Placement> computeStageExits(long seed, int stx, int stz) {
		int baseSx = stx * Maze.STAGE_SECTORS;
		int baseSz = stz * Maze.STAGE_SECTORS;

		// Build list of 8 target exit kinds: 3 guaranteed + 5 deterministically sampled from rotating pool.
		List<EntryKind> wanted = new ArrayList<>(8);
		wanted.add(EntryKind.OIL_VENT_1);
		wanted.add(EntryKind.STEAM_GRATE_2);
		wanted.add(EntryKind.POSTER_161);

		EntryKind[] pool = ROTATING_POOL.clone();
		long rng = hash(seed, stx, stz, EXIT_SALT);
		for (int i = pool.length - 1; i > 0; i--) {
			rng = mix(rng);
			int j = Math.floorMod(rng, i + 1);
			EntryKind tmp = pool[i];
			pool[i] = pool[j];
			pool[j] = tmp;
		}
		for (int i = 0; i < 5; i++) {
			wanted.add(pool[i]);
		}

		// Collect eligible sectors inside this Stage (standard walkable zones, skipping (0,0) only for cell (0,0)).
		List<int[]> sectors = new ArrayList<>();
		for (int lx = 0; lx < Maze.STAGE_SECTORS; lx++) {
			for (int lz = 0; lz < Maze.STAGE_SECTORS; lz++) {
				int sx = baseSx + lx, sz = baseSz + lz;
				if (sx == 0 && sz == 0) continue;
				Maze.Zone z = Maze.baseZoneForSector(seed, sx, sz);
				if (z == Maze.Zone.CLASSIC || z == Maze.Zone.CUBICLES || z == Maze.Zone.CORRIDORS
						|| z == Maze.Zone.ARCHES || z == Maze.Zone.PILLARS) {
					sectors.add(new int[] {sx, sz});
				}
			}
		}

		List<Placement> result = new ArrayList<>();
		int sectorIdx = 0;
		for (EntryKind kind : wanted) {
			if (sectors.isEmpty()) break;
			for (int tries = 0; tries < sectors.size(); tries++) {
				int[] s = sectors.get((sectorIdx + tries) % sectors.size());
				int sx = s[0], sz = s[1];
				Placement chosen = pickCellInSector(seed, sx, sz, kind, result);
				if (chosen != null) {
					result.add(chosen);
					sectorIdx = (sectorIdx + tries + 1) % sectors.size();
					break;
				}
			}
		}
		return List.copyOf(result);
	}

	private static Placement pickCellInSector(long seed, int sx, int sz, EntryKind kind, List<Placement> existing) {
		long h = hash(seed, sx, sz, EXIT_SALT + kind.ordinal() + 17);
		int start = Math.floorMod(h, 16);
		for (int i = 0; i < 16; i++) {
			int pick = (start + i) % 16;
			int cx = sx * Maze.SECTOR + 1 + (pick & 3);
			int cz = sz * Maze.SECTOR + 1 + (pick >> 2);
			if (cx == 0 && cz == 0) continue;
			if (ManilaRoomPlan.isProtectedCell(seed, cx, cz)) continue;
			if (StaircasePlan.isProtectedCell(seed, cx, cz)) continue;
			if (Maze.isAtriumCell(seed, cx, cz)) continue;
			boolean clash = false;
			for (Placement e : existing) {
				if (e.cx() == cx && e.cz() == cz) {
					clash = true;
					break;
				}
			}
			if (!clash) return new Placement(cx, cz, kind);
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

	private static final ThreadLocal<StageExitCache> CACHE = ThreadLocal.withInitial(StageExitCache::new);

	private static final class StageExitCache {
		private static final int MASK = 127;
		private final long[] seeds = new long[MASK + 1];
		private final int[] sxs = new int[MASK + 1];
		private final int[] szs = new int[MASK + 1];
		private final List<Placement>[] values = new List[MASK + 1];
		private final boolean[] valid = new boolean[MASK + 1];

		List<Placement> getOrCompute(long seed, int stx, int stz) {
			int idx = (int) ((seed ^ (stx * 0x9E3779B97F4A7C15L) ^ (stz * 0xC2B2AE3D27D4EB4FL)) >>> 32) & MASK;
			if (valid[idx] && seeds[idx] == seed && sxs[idx] == stx && szs[idx] == stz) {
				return values[idx];
			}
			List<Placement> computed = computeStageExits(seed, stx, stz);
			seeds[idx] = seed;
			sxs[idx] = stx;
			szs[idx] = stz;
			values[idx] = computed;
			valid[idx] = true;
			return computed;
		}
	}
}
