package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Layout.Base;
import dev.noclip.backrooms.level1.Level1Layout.Kind;
import dev.noclip.backrooms.level1.Level1Layout.Sector;
import dev.noclip.backrooms.level1.Level1Layout.Tile;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dramatic per-sector architectural variants for Level 1 halls (3 distinct variants per sector = 18 total).
 * Layers cleanly on top of {@link Level1Elevation} so every sector has unmistakable structural geometry,
 * not just a texture reskin, while keeping district borders, settlements, exits and props unobstructed.
 */
public final class Level1SectorArchitecture {
	private static final int D = Level1Layout.DISTRICT;
	private static final int FLOOR = Level1ChunkGenerator.FLOOR_Y;
	private static final int VARIANT_SALT = 67;
	private static final int GILD_PILLAR_COUNT = 6;
	private static final int NEON_COUNT = 3;
	private static final int GARDEN_PLANT_COUNT = 5;

	private static Block gildPillar(int index) {
		return switch (Math.floorMod(index, GILD_PILLAR_COUNT)) {
			case 0 -> Level1Blocks.GILD_PILLAR_0;
			case 1 -> Level1Blocks.GILD_PILLAR_1;
			case 2 -> Level1Blocks.GILD_PILLAR_2;
			case 3 -> Level1Blocks.GILD_PILLAR_3;
			case 4 -> Level1Blocks.GILD_PILLAR_4;
			default -> Level1Blocks.GILD_PILLAR_5;
		};
	}

	private static Block neon(int index) {
		return switch (Math.floorMod(index, NEON_COUNT)) {
			case 0 -> Level1Blocks.NEON_CYAN;
			case 1 -> Level1Blocks.NEON_MAGENTA;
			default -> Level1Blocks.NEON_AMBER;
		};
	}

	private static Block gardenPlant(int index) {
		return switch (Math.floorMod(index, GARDEN_PLANT_COUNT)) {
			case 0 -> Level1Blocks.GARDEN_GRASS;
			case 1 -> Level1Blocks.GARDEN_FERN;
			case 2 -> Level1Blocks.GARDEN_FLOWER_BLUE;
			case 3 -> Level1Blocks.GARDEN_FLOWER_RED;
			default -> Level1Blocks.GARDEN_FLOWER_YELLOW;
		};
	}

	public enum Variant {
		NONE,
		// Talon (concrete industrial parking)
		TALON_SPLIT_RAMPS,
		TALON_PIPE_GANTRY,
		TALON_FLOODED_TRENCH,
		// Gild (bright warehouse & trade)
		GILD_MEGA_RACKS,
		GILD_LOADING_DOCKS,
		GILD_PRISM_GALLERY,
		// Gothic (marble columns & vaulted stone)
		GOTHIC_CATHEDRAL_ARCHES,
		GOTHIC_SUNKEN_CLOISTER,
		GOTHIC_CRYPT_CATACOMB,
		// Garden (overgrown concrete ruins)
		GARDEN_OVERGROWN_TERRACES,
		GARDEN_SUNKEN_POND,
		GARDEN_RUINED_ARBORS,
		// Legend (crimson lacquer & cyber-neon)
		LEGEND_NEON_ATRIUM,
		LEGEND_CRIMSON_PAVILIONS,
		LEGEND_LANTERN_BRIDGES,
		// Tailchase (endless construction site)
		TAILCHASE_SCAFFOLD_TOWERS,
		TAILCHASE_EXCAVATION_PITS,
		TAILCHASE_UNFINISHED_BEAMS
	}

	private Level1SectorArchitecture() {
	}

	/** Picks one of the 3 dramatic variants for this district's sector, or NONE for origin/corridors/bases/landmarks. */
	public static Variant variant(long seed, int dx, int dz) {
		if (dx == 0 && dz == 0) return Variant.NONE;
		if (Level1Layout.kind(seed, dx, dz) == Kind.CORRIDORS) return Variant.NONE;
		if (Level1Layout.base(seed, dx, dz) != Base.NONE) return Variant.NONE;
		if (Level1Landmarks.landmark(seed, dx, dz) != Level1Landmarks.Landmark.NONE) return Variant.NONE;
		Sector sector = Level1Layout.sector(seed, dx * D + D / 2, dz * D + D / 2);
		int pick = Math.floorMod(Level1Layout.hash(seed, dx, dz, VARIANT_SALT), 3);
		return switch (sector) {
			case TALON -> switch (pick) {
				case 0 -> Variant.TALON_SPLIT_RAMPS;
				case 1 -> Variant.TALON_PIPE_GANTRY;
				default -> Variant.TALON_FLOODED_TRENCH;
			};
			case GILD -> switch (pick) {
				case 0 -> Variant.GILD_MEGA_RACKS;
				case 1 -> Variant.GILD_LOADING_DOCKS;
				default -> Variant.GILD_PRISM_GALLERY;
			};
			case GOTHIC -> switch (pick) {
				case 0 -> Variant.GOTHIC_CATHEDRAL_ARCHES;
				case 1 -> Variant.GOTHIC_SUNKEN_CLOISTER;
				default -> Variant.GOTHIC_CRYPT_CATACOMB;
			};
			case GARDEN -> switch (pick) {
				case 0 -> Variant.GARDEN_OVERGROWN_TERRACES;
				case 1 -> Variant.GARDEN_SUNKEN_POND;
				default -> Variant.GARDEN_RUINED_ARBORS;
			};
			case LEGEND -> switch (pick) {
				case 0 -> Variant.LEGEND_NEON_ATRIUM;
				case 1 -> Variant.LEGEND_CRIMSON_PAVILIONS;
				default -> Variant.LEGEND_LANTERN_BRIDGES;
			};
			case TAILCHASE -> switch (pick) {
				case 0 -> Variant.TAILCHASE_SCAFFOLD_TOWERS;
				case 1 -> Variant.TAILCHASE_EXCAVATION_PITS;
				default -> Variant.TAILCHASE_UNFINISHED_BEAMS;
			};
		};
	}

	/**
	 * Applies sector-specific architectural geometry on top of the dressed hall block.
	 * Never touches district borders, settlements, exits, props, or bedrock.
	 */
	static BlockState apply(long seed, Tile tile, BlockState current, int x, int y, int z) {
		return apply(seed, tile, current, x, y, z, true);
	}

	/** {@code fit = false} is what the sector would build ignoring the hall's profile (for tests). */
	static BlockState apply(long seed, Tile tile, BlockState current, int x, int y, int z, boolean fit) {
		if (y <= 1 || y >= Level1ChunkGenerator.HEIGHT - 2) return current;
		int dx = Math.floorDiv(x, D), dz = Math.floorDiv(z, D);
		Variant v = variant(seed, dx, dz);
		if (v == Variant.NONE) return current;

		int lx = Math.floorMod(x, D), lz = Math.floorMod(z, D);
		// Keep a 5-block clear border around the district so inter-district travel stays clean.
		if (lx < 5 || lx > D - 6 || lz < 5 || lz > D - 6) return current;
		// Never overwrite exits, props, walls, or pillars' core columns.
		if (tile == Tile.HALL_WALL || tile == Tile.PERIMETER || tile == Tile.EXIT_CORE
				|| tile == Tile.STAIRWELL || tile == Tile.HATCH || tile == Tile.FRAME
				|| tile == Tile.BOOTH || tile == Tile.MACHINE || tile == Tile.CRATE) {
			return current;
		}
		if (Level1Elevation.isNearExitOrProp(seed, dx, dz, x, z)) return current;

		int topCeil = Level1Elevation.ceilingY(seed, dx, dz);
		Level1Elevation.Profile profile = Level1Elevation.profile(seed, dx, dz);
		// Avoid carving into deep sunken basins or multi-deck stair columns.
		boolean hasMainFloor = profile != Level1Elevation.Profile.SUNKEN_BASIN
				|| (lx < 11 || lx > 52 || lz < 11 || lz > 52);

		BlockState prop = prop(seed, tile, current, x, y, z, hasMainFloor);
		if (prop != null) {
			// Only on the main floor where it really is, and not where this sector builds something of its own.
			boolean clear = structure(v, seed, tile, current, x, FLOOR, z, lx, lz, topCeil, hasMainFloor, profile) == null
					&& structure(v, seed, tile, current, x, FLOOR + 1, z, lx, lz, topCeil, hasMainFloor, profile) == null;
			if (!fit || clear && allowed(seed, profile, topCeil, x, y, z)) return prop;
		}
		BlockState override = structure(v, seed, tile, current, x, y, z, lx, lz, topCeil, hasMainFloor, profile);
		if (override == null || override == current) return current;
		if (!fit) return override;
		int[] span = span(v, lx, lz, y, topCeil);
		if (span != null) return whole(seed, dx, dz, span) ? override : current;
		return allowed(seed, profile, topCeil, x, y, z) ? override : current;
	}

	/**
	 * {lx0, lx1, lz0, lz1, y0, y1} of the bridge, beam or pipe run this piece belongs to, or null. Such a run goes in whole
	 * or not at all: one cell left out of a walkway is a hole to fall through.
	 */
	static int[] span(Variant v, int lx, int lz, int y, int topCeil) {
		return switch (v) {
			case LEGEND_LANTERN_BRIDGES -> topCeil >= FLOOR + 9 && lz >= 28 && lz <= 36 && (y == FLOOR + 5 || y == FLOOR + 6)
					? new int[] {5, D - 6, 28, 36, FLOOR + 5, FLOOR + 6} : null;
			case TAILCHASE_UNFINISHED_BEAMS -> (lz == 22 || lz == 42) && y >= FLOOR + 2 && y <= FLOOR + 4 && lx % 9 < 6
					? new int[] {Math.max(5, lx - lx % 9), Math.min(D - 6, lx - lx % 9 + 5), lz, lz, FLOOR + 2, FLOOR + 4} : null;
			case TALON_PIPE_GANTRY -> y == topCeil - 1 && lz % 12 == 0 ? new int[] {5, D - 6, lz, lz, y, y} : null;
			default -> null;
		};
	}

	private static final ThreadLocal<java.util.Map<String, Boolean>> SPANS = ThreadLocal.withInitial(java.util.HashMap::new);

	/** Whether every cell of the run that would be solid over open air really gets placed. */
	private static boolean whole(long seed, int dx, int dz, int[] span) {
		java.util.Map<String, Boolean> cache = SPANS.get();
		if (cache.size() > 4096) cache.clear();
		String key = seed + ":" + dx + ":" + dz + ":" + span[0] + ":" + span[2] + ":" + span[4];
		Boolean known = cache.get(key);
		if (known != null) return known;
		boolean ok = true;
		for (int lx = span[0]; lx <= span[1] && ok; lx++) {
			for (int lz = span[2]; lz <= span[3] && ok; lz++) {
				int x = dx * D + lx, z = dz * D + lz;
				Tile tile = Level1Layout.tile(seed, x, z);
				for (int y = span[4]; y <= span[5] && ok; y++) {
					BlockState base = Level1ChunkGenerator.baseAt(seed, x, y, z);
					if (!base.isAir()) continue;  // a wall or pillar already fills it
					BlockState wanted = apply(seed, tile, base, x, y, z, false);
					if (wanted == base || wanted.isAir()) continue;
					ok = cell(seed, tile, base, x, y, z) != base;
				}
			}
		}
		cache.put(key, ok);
		return ok;
	}

	/** One cell judged on its own (the per-column checks of {@link #apply}, without the run check). */
	private static BlockState cell(long seed, Tile tile, BlockState current, int x, int y, int z) {
		int dx = Math.floorDiv(x, D), dz = Math.floorDiv(z, D);
		int lx = Math.floorMod(x, D), lz = Math.floorMod(z, D);
		if (tile == Tile.HALL_WALL || tile == Tile.PERIMETER || tile == Tile.EXIT_CORE
				|| tile == Tile.STAIRWELL || tile == Tile.HATCH || tile == Tile.FRAME
				|| tile == Tile.BOOTH || tile == Tile.MACHINE || tile == Tile.CRATE) {
			return current;
		}
		if (Level1Elevation.isNearExitOrProp(seed, dx, dz, x, z)) return current;
		Variant v = variant(seed, dx, dz);
		int topCeil = Level1Elevation.ceilingY(seed, dx, dz);
		Level1Elevation.Profile profile = Level1Elevation.profile(seed, dx, dz);
		boolean hasMainFloor = profile != Level1Elevation.Profile.SUNKEN_BASIN || (lx < 11 || lx > 52 || lz < 11 || lz > 52);
		BlockState override = structure(v, seed, tile, current, x, y, z, lx, lz, topCeil, hasMainFloor, profile);
		if (override == null || override == current) return current;
		return allowed(seed, profile, topCeil, x, y, z) ? override : current;
	}

	/** The cars, vending machines and pallet stacks a sector leaves about its main floor, or null. */
	private static BlockState prop(long seed, Tile tile, BlockState current, int x, int y, int z, boolean hasMainFloor) {
		// Occasional abandoned 90s sedan or cargo van parked neatly inside parking stalls (never blocking aisles)
		if (hasMainFloor && y == FLOOR + 1 && current.isAir() && tile == Tile.FLOOR
				&& Level1Layout.kindAt(seed, x, z) == Kind.PARKING) {
			int bx = Math.floorMod(x, Level1Layout.BAY_X), bz = Math.floorMod(z, Level1Layout.BAY_Z);
			if ((bx == 3 || bx == 6) && (bz == 4 || bz == 13)
					&& Math.floorMod(Level1Layout.hash(seed, x, z, 163), 100) < 10) {
				Direction dir = Math.floorMod(Level1Layout.hash(seed, x, z, 167), 2) == 0 ? Direction.NORTH : Direction.SOUTH;
				Block vehicle = Math.floorMod(Level1Layout.hash(seed, x, z, 173), 3) == 0
						? Level1Blocks.ABANDONED_VAN : Level1Blocks.ABANDONED_CAR;
				return vehicle.defaultBlockState().setValue(OldComputerBlock.FACING, dir);
			}
			// Rare softly glowing retro vending machine tucked beside a pillar
			if (bx == 2 && bz == 1 && Math.floorMod(Level1Layout.hash(seed, x, z, 179), 100) < 6) {
				return Level1Blocks.VENDING_MACHINE.defaultBlockState().setValue(OldComputerBlock.FACING, Direction.EAST);
			}
		}
		// Occasional pallet stack in warehouse bays
		if (hasMainFloor && y == FLOOR + 1 && current.isAir() && tile == Tile.FLOOR
				&& Level1Layout.kindAt(seed, x, z) == Kind.WAREHOUSE) {
			int bx = Math.floorMod(x, Level1Layout.BAY_X), bz = Math.floorMod(z, Level1Layout.BAY_Z);
			if (bx == 2 && bz == 7 && Math.floorMod(Level1Layout.hash(seed, x, z, 181), 100) < 18) {
				Direction dir = Math.floorMod(Level1Layout.hash(seed, x, z, 191), 2) == 0 ? Direction.EAST : Direction.SOUTH;
				return Level1Blocks.PALLET_STACK.defaultBlockState().setValue(OldComputerBlock.FACING, dir);
			}
		}
		return null;
	}

	private static BlockState structure(Variant v, long seed, Tile tile, BlockState current, int x, int y, int z, int lx, int lz,
			int topCeil, boolean hasMainFloor, Level1Elevation.Profile profile) {
		return switch (v) {
			case TALON_SPLIT_RAMPS -> talonSplitRamps(tile, current, lx, lz, y, hasMainFloor);
			case TALON_PIPE_GANTRY -> talonPipeGantry(seed, tile, current, x, y, z, lx, lz, topCeil);
			case TALON_FLOODED_TRENCH -> talonFloodedTrench(tile, current, lx, lz, y, hasMainFloor, profile);
			case GILD_MEGA_RACKS -> gildMegaRacks(seed, tile, current, x, y, z, lx, lz, topCeil);
			case GILD_LOADING_DOCKS -> gildLoadingDocks(tile, current, lx, lz, y, hasMainFloor);
			case GILD_PRISM_GALLERY -> gildPrismGallery(seed, tile, current, x, y, z, lx, lz, topCeil, hasMainFloor);
			case GOTHIC_CATHEDRAL_ARCHES -> gothicCathedralArches(tile, current, x, y, z, topCeil);
			case GOTHIC_SUNKEN_CLOISTER -> gothicSunkenCloister(tile, current, lx, lz, y, hasMainFloor, profile);
			case GOTHIC_CRYPT_CATACOMB -> gothicCryptCatacomb(tile, current, lx, lz, y, topCeil, hasMainFloor);
			case GARDEN_OVERGROWN_TERRACES -> gardenOvergrownTerraces(seed, tile, current, x, y, z, lx, lz, hasMainFloor);
			case GARDEN_SUNKEN_POND -> gardenSunkenPond(seed, tile, current, x, y, z, lx, lz, topCeil, hasMainFloor, profile);
			case GARDEN_RUINED_ARBORS -> gardenRuinedArbors(seed, tile, current, x, y, z, lx, lz, topCeil);
			case LEGEND_NEON_ATRIUM -> legendNeonAtrium(seed, tile, current, x, y, z, lx, lz, topCeil);
			case LEGEND_CRIMSON_PAVILIONS -> legendCrimsonPavilions(seed, tile, current, x, y, z, lx, lz, hasMainFloor);
			case LEGEND_LANTERN_BRIDGES -> legendLanternBridges(tile, current, lx, lz, y, topCeil, hasMainFloor);
			case TAILCHASE_SCAFFOLD_TOWERS -> tailchaseScaffoldTowers(seed, tile, current, x, y, z, lx, lz, topCeil);
			case TAILCHASE_EXCAVATION_PITS -> tailchaseExcavationPits(seed, tile, current, x, y, z, lx, lz, hasMainFloor, profile);
			case TAILCHASE_UNFINISHED_BEAMS -> tailchaseUnfinishedBeams(seed, tile, current, x, y, z, lx, lz, topCeil);
			case NONE -> null;
		};
	}

	// ==================== Fitting in with the hall's profile ====================

	/**
	 * Whether a sector piece may go here, given what the district's vertical profile or landmark already built:
	 * above the main floor only where that floor is really there and open, never above an upper deck, a skywalk or a
	 * beam (so racks, arches and neon stop beneath them); nothing on or beside a staircase or a stairwell opening; and
	 * pits and trenches only cut where there is plain ground beneath (not into the decks or tunnels below).
	 */
	static boolean allowed(long seed, Level1Elevation.Profile profile, int topCeil, int x, int y, int z) {
		int[] here = column(seed, x, z, topCeil);
		if (here[1] == 1) return false;
		// The whole column, not just its foot: a rack or arch cut off at the bottom would hang in the air.
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (column(seed, x + d.getStepX(), z + d.getStepZ(), topCeil)[1] == 1) return false;
		}
		if (here[0] == 0) return false;
		if (y <= FLOOR) {
			return here[3] == 1 && (profile == Level1Elevation.Profile.STANDARD || profile == Level1Elevation.Profile.VAULTED);
		}
		if (y < here[2]) return true;
		return y == topCeil - 1 && here[2] >= topCeil - 1;
	}

	private static final ThreadLocal<java.util.Map<Long, int[]>> COLUMNS = ThreadLocal.withInitial(java.util.HashMap::new);
	private static final ThreadLocal<long[]> COLUMN_SEED = ThreadLocal.withInitial(() -> new long[1]);

	/** {main floor solid, stairs or an opening here, first y from FLOOR + 3 up that is already built, nothing standing on the floor}. */
	private static int[] column(long seed, int x, int z, int topCeil) {
		java.util.Map<Long, int[]> cache = COLUMNS.get();
		long[] cachedSeed = COLUMN_SEED.get();
		if (cachedSeed[0] != seed || cache.size() > 16_384) {
			cache.clear();
			cachedSeed[0] = seed;
		}
		return cache.computeIfAbsent(((long) x << 32) ^ (z & 0xffffffffL), key -> {
			BlockState floor = Level1ChunkGenerator.baseAt(seed, x, FLOOR, z);
			boolean solid = !floor.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).isEmpty();
			boolean stairs = floor.isAir() || floor.getBlock() instanceof StairBlock;
			for (int y = FLOOR + 1; y <= FLOOR + 2 && !stairs; y++) {
				stairs = Level1ChunkGenerator.baseAt(seed, x, y, z).getBlock() instanceof StairBlock;
			}
			int limit = topCeil;
			for (int y = FLOOR + 3; y < topCeil; y++) {
				if (!Level1ChunkGenerator.baseAt(seed, x, y, z).isAir()) {
					limit = y;
					break;
				}
			}
			boolean bare = Level1ChunkGenerator.baseAt(seed, x, FLOOR + 1, z).isAir();
			return new int[] {solid ? 1 : 0, stairs ? 1 : 0, limit, bare ? 1 : 0};
		});
	}

	// ==================== TALON (3 variants) ====================

	/** Raised concrete split-level parking islands (y=34) with concrete stairs, barriers and toll arms. */
	private static BlockState talonSplitRamps(Tile tile, BlockState cur, int lx, int lz, int y, boolean hasMainFloor) {
		if (!hasMainFloor || tile == Tile.PILLAR) return null;
		// Two big elevated parking decks at (10..26, 12..28) and (36..52, 34..50)
		boolean deckA = lx >= 10 && lx <= 26 && lz >= 12 && lz <= 28;
		boolean deckB = lx >= 36 && lx <= 52 && lz >= 34 && lz <= 50;
		if (!deckA && !deckB) return null;
		int rx = deckA ? lx - 10 : lx - 36;
		int rz = deckA ? lz - 12 : lz - 34;
		// Ramp stairs on the north edge (rz = 0..1, rx = 6..10) and south edge (rz = 15..16, rx = 6..10)
		if (rx >= 6 && rx <= 10) {
			if (rz == 0 && y == FLOOR + 1) return stairs(Direction.SOUTH);
			if (rz == 1 && y == FLOOR + 1) return state(Level1Blocks.GARAGE_WALL);
			if (rz == 1 && y == FLOOR + 2) return stairs(Direction.SOUTH);
			if (rz == 16 && y == FLOOR + 1) return stairs(Direction.NORTH);
			if (rz == 15 && y == FLOOR + 1) return state(Level1Blocks.GARAGE_WALL);
			if (rz == 15 && y == FLOOR + 2) return stairs(Direction.NORTH);
		}
		if (rz >= 2 && rz <= 14) {
			if (y == FLOOR + 1) return state(Level1Blocks.GARAGE_WALL);
			if (y == FLOOR + 2) return (rx == 4 || rx == 12) ? state(Level1Blocks.GARAGE_FLOOR_LINE) : state(Level1Blocks.GARAGE_FLOOR);
			boolean edge = rx == 0 || rx == 16 || ((rz == 2 || rz == 14) && (rx < 6 || rx > 10));
			if (y == FLOOR + 3 && edge && cur.isAir()) {
				return barrier((rz == 2 || rz == 14) ? Direction.Axis.X : Direction.Axis.Z);
			}
		}
		return null;
	}

	/** Heavy overhead copper pipe gantries (X-aligned so pipes connect cleanly), ceiling-mounted caged lamps, and drums. */
	private static BlockState talonPipeGantry(long seed, Tile tile, BlockState cur, int x, int y, int z, int lx, int lz, int topCeil) {
		if (!cur.isAir()) return null;
		// CEILING_PIPE only connects along the X axis (fixed Z, varying X), and must sit right under the ceiling.
		if (y == topCeil - 1) {
			if (lz % 12 == 0) return state(Level1Blocks.CEILING_PIPE);
			if (lx % 12 == 6 && lz % 12 == 6) return state(Level1Blocks.CAGED_LAMP);
		}
		// Full-height vertical copper pipe risers (floor to ceiling, never floating mid-air)
		if (lx % 12 == 0 && lz % 12 == 0 && y > FLOOR && y < topCeil) {
			return state(Level1Blocks.COPPER_PILLAR);
		}
		// Occasional red fuel drums clustered near the risers on the main floor
		if (y == FLOOR + 1 && tile == Tile.FLOOR && Math.abs(lx % 12) == 1 && Math.abs(lz % 12) == 1) {
			if (Math.floorMod(Level1Layout.hash(seed, x, z, 71), 3) == 0) return state(Level1Blocks.FUEL_DRUM);
		}
		return null;
	}

	/** Parallel 2-block-deep flooded drainage trenches carved under the parking aisles with concrete footbridges. */
	private static BlockState talonFloodedTrench(Tile tile, BlockState cur, int lx, int lz, int y, boolean hasMainFloor,
			Level1Elevation.Profile profile) {
		if (!hasMainFloor || tile == Tile.PILLAR || profile == Level1Elevation.Profile.MULTI_DECK) return null;
		// Two parallel trenches running along Z at lx in [17..20] and [43..46], with bridges every 12 blocks.
		boolean inTrench = ((lx >= 17 && lx <= 20) || (lx >= 43 && lx <= 46)) && lz >= 8 && lz <= 55;
		if (!inTrench) return null;
		boolean bridge = lz % 12 >= 4 && lz % 12 <= 7;
		if (bridge) {
			if (y == FLOOR) return state(Level1Blocks.GARAGE_FLOOR);
			if (y == FLOOR + 1 && (lz % 12 == 4 || lz % 12 == 7) && cur.isAir()) return barrier(Direction.Axis.X);
			return null;
		}
		if (y == FLOOR) return air();
		if (y == FLOOR - 1) return Blocks.WATER.defaultBlockState();
		if (y == FLOOR - 2) return state(Level1Blocks.PUDDLED_GARAGE_FLOOR);
		return null;
	}

	// ==================== GILD (3 variants) ====================

	/** Towering warehouse mega-racks stacked up to 8-10 blocks high with colorful endcaps and floodlights. */
	private static BlockState gildMegaRacks(long seed, Tile tile, BlockState cur, int x, int y, int z, int lx, int lz, int topCeil) {
		if (tile == Tile.PILLAR || !cur.isAir()) return null;
		int maxRackY = Math.min(topCeil - 2, FLOOR + 9);
		int bz = Math.floorMod(z, Level1Layout.BAY_Z);
		int bx = Math.floorMod(x, Level1Layout.BAY_X);
		boolean rackRow = bx >= 3 && bx <= 7 && (bz == 4 || bz == 12);
		if (rackRow && y > FLOOR && y <= maxRackY) {
			// Leave walking gaps at ground level every other bay so navigation stays fluid.
			if (y <= FLOOR + 2 && bx == 5) return null;
			if (bx == 3 || bx == 7) {
				int color = Math.floorMod(Level1Layout.hash(seed, lx / 9, lz / 16, 73), GILD_PILLAR_COUNT);
				return state(gildPillar(color));
			}
			return state(Level1Blocks.METAL_SHELF);
		}
		return null;
	}

	/** Raised colorful loading docks with recessed truck bays, stairs, and stacked crate clusters. */
	private static BlockState gildLoadingDocks(Tile tile, BlockState cur, int lx, int lz, int y, boolean hasMainFloor) {
		if (!hasMainFloor || tile == Tile.PILLAR) return null;
		// Alternating 12-wide raised docks at lz in [10..20] and [42..52]
		boolean dock = (lz >= 10 && lz <= 20) || (lz >= 42 && lz <= 52);
		if (!dock || lx < 8 || lx > 55) return null;
		boolean stepBand = lx % 16 >= 6 && lx % 16 <= 9;
		boolean frontEdge = lz == 10 || lz == 20 || lz == 42 || lz == 52;
		if (frontEdge && stepBand) {
			if (y == FLOOR + 1) return stairs((lz == 10 || lz == 42) ? Direction.SOUTH : Direction.NORTH);
			return null;
		}
		if (y == FLOOR + 1) return state(Level1Blocks.GILD_WALL);
		if (y == FLOOR + 2) return state(Level1Blocks.GILD_FLOOR);
		if (y == FLOOR + 3 && cur.isAir() && frontEdge && !stepBand && lx % 8 == 0) {
			return state(Level1Blocks.FLOODLIGHT);
		}
		return null;
	}

	/** Bold rainbow colonnades with stepped display plinths and vibrant colored overhead lintels. */
	private static BlockState gildPrismGallery(long seed, Tile tile, BlockState cur, int x, int y, int z, int lx, int lz, int topCeil,
			boolean hasMainFloor) {
		if (tile == Tile.PILLAR || !cur.isAir()) return null;
		// Extra decorative colorful mini-columns along a 10x10 grid with bright overhead lintels.
		boolean col = lx % 10 == 5 && lz % 10 == 5;
		int color = Math.floorMod(Level1Layout.hash(seed, lx / 10, lz / 10, 79), GILD_PILLAR_COUNT);
		if (col && y > FLOOR && y < topCeil) {
			return state(gildPillar(color));
		}
		if (y == Math.min(topCeil - 2, FLOOR + 5) && (lx % 10 == 5 || lz % 10 == 5)) {
			return state(gildPillar(color));
		}
		if (hasMainFloor && y == FLOOR + 1 && Math.abs(lx - 32) <= 4 && Math.abs(lz - 32) <= 4) {
			return state(Level1Blocks.GILD_FLOOR);
		}
		return null;
	}

	// ==================== GOTHIC (3 variants) ====================

	/** Grand vaulted Gothic stone rib-arches spanning between marble columns with suspended caged chandeliers. */
	private static BlockState gothicCathedralArches(Tile tile, BlockState cur, int x, int y, int z, int topCeil) {
		if (tile == Tile.PILLAR || !cur.isAir()) return null;
		int bx = Math.floorMod(x, Level1Layout.BAY_X);
		int bz = Math.floorMod(z, Level1Layout.BAY_Z);
		int archBase = FLOOR + 4;
		// Ribbed arches curving upward from pillars along both X (bz < 2) and Z (bx < 2)
		if (bz < 2) {
			int dist = Math.min(bx, Level1Layout.BAY_X - bx); // 0..4
			int archY = archBase + Math.min(dist, 4);
			if (y >= archY && y <= Math.min(topCeil - 1, archY + 2)) return state(Level1Blocks.GOTHIC_BRICK);
		}
		if (bx < 2) {
			int dist = Math.min(bz, Level1Layout.BAY_Z - bz); // 0..8
			int archY = archBase + Math.min(dist / 2, 4);
			if (y >= archY && y <= Math.min(topCeil - 1, archY + 2)) return state(Level1Blocks.GOTHIC_BRICK);
		}
		// Hanging caged chandelier mounted directly under the ceiling in the center of each bay
		if (bx == 5 && bz == 8 && y == topCeil - 1) {
			return state(Level1Blocks.CAGED_LAMP);
		}
		return null;
	}

	/** Sunken stone cloister courtyard in the center of the district with marble colonnades and grand steps. */
	private static BlockState gothicSunkenCloister(Tile tile, BlockState cur, int lx, int lz, int y, boolean hasMainFloor,
			Level1Elevation.Profile profile) {
		if (!hasMainFloor || profile == Level1Elevation.Profile.MULTI_DECK) return null;
		int dx = Math.abs(lx - 32), dz = Math.abs(lz - 32);
		int ring = Math.max(dx, dz);
		if (ring > 16) return null;
		// Outer marble colonnade wall with arched openings on the 4 cardinal axes
		if (ring == 16) {
			if (dx <= 2 || dz <= 2) return null; // open doorways
			if (y >= FLOOR + 1 && y <= FLOOR + 5) {
				if ((lx + lz) % 4 == 0) return state(Level1Blocks.GOTHIC_COLUMN);
				if (y == FLOOR + 1 || y >= FLOOR + 4) return state(Level1Blocks.GOTHIC_BRICK);
			}
			return null;
		}
		if (tile == Tile.PILLAR) return null;
		// Stepped descent down 3 blocks into the sunken stone court (y = 29)
		if (ring == 14 && y == FLOOR) return air();
		if (ring == 14 && y == FLOOR - 1) return state(Level1Blocks.GOTHIC_FLOOR);
		if (ring == 13 && y >= FLOOR - 1 && y <= FLOOR) return air();
		if (ring == 13 && y == FLOOR - 2) return state(Level1Blocks.GOTHIC_FLOOR);
		if (ring <= 12) {
			if (y >= FLOOR - 2 && y <= FLOOR) return air();
			if (y == FLOOR - 3) return state(Level1Blocks.GOTHIC_FLOOR);
			// Central stone altar/plinth with glowing caged lamp
			if (ring <= 2 && y == FLOOR - 2) return state(Level1Blocks.GOTHIC_BRICK);
			if (ring == 0 && y == FLOOR - 1) return state(Level1Blocks.GOTHIC_COLUMN);
			if (ring == 0 && y == FLOOR) return state(Level1Blocks.CAGED_LAMP);
		}
		return null;
	}

	/** Symmetrical Gothic stone crypt chambers and alcoves with arched portals and warm caged lamps. */
	private static BlockState gothicCryptCatacomb(Tile tile, BlockState cur, int lx, int lz, int y, int topCeil, boolean hasMainFloor) {
		if (!hasMainFloor || tile == Tile.PILLAR || !cur.isAir()) return null;
		int cx = lx % 16, cz = lz % 16;
		boolean wall = cx == 0 || cz == 0;
		boolean doorway = (cx == 0 && cz >= 6 && cz <= 10) || (cz == 0 && cx >= 6 && cx <= 10);
		if (wall && y > FLOOR && y <= Math.min(topCeil - 1, FLOOR + 6)) {
			if (doorway && y <= FLOOR + 3) return null;
			return state(Level1Blocks.GOTHIC_BRICK);
		}
		if (cx == 8 && cz == 8 && y == topCeil - 1) return state(Level1Blocks.CAGED_LAMP);
		return null;
	}

	// ==================== GARDEN (3 variants) ====================

	/** Undulating mossy garden terraces (1-3 blocks high) blanketed in ferns and tri-color wildflowers. */
	private static BlockState gardenOvergrownTerraces(long seed, Tile tile, BlockState cur, int x, int y, int z, int lx, int lz,
			boolean hasMainFloor) {
		if (!hasMainFloor || tile == Tile.PILLAR) return null;
		// Smooth concentric terrace height from 0 to 3 blocks using simple integer wave math
		double wave = Math.sin(lx * 0.14) + Math.cos(lz * 0.14);
		int terrace = wave > 1.1 ? 3 : wave > 0.35 ? 2 : wave > -0.25 ? 1 : 0;
		if (terrace == 0) return null;
		if (y > FLOOR && y <= FLOOR + terrace) {
			return state(y == FLOOR + terrace ? Level1Blocks.GARDEN_GRASS_FLOOR : Level1Blocks.GARDEN_MOSS);
		}
		if (y == FLOOR + terrace + 1 && cur.isAir()) {
			int roll = Math.floorMod(Level1Layout.hash(seed, x, z, 83), 100);
			if (roll < 35) return state(gardenPlant(roll));
		}
		return null;
	}

	/** Sunken mossy water pool with stepping stones, drowned ruins, and hanging moss stalactites overhead. */
	private static BlockState gardenSunkenPond(long seed, Tile tile, BlockState cur, int x, int y, int z, int lx, int lz, int topCeil,
			boolean hasMainFloor, Level1Elevation.Profile profile) {
		// Hanging moss stalactites from the ceiling
		if (cur.isAir() && y >= topCeil - 5 && y < topCeil) {
			int drop = Math.floorMod(Level1Layout.hash(seed, x, z, 89), 18);
			if (drop < 4 && y >= topCeil - 1 - drop) return state(Level1Blocks.GARDEN_CEILING);
		}
		if (!hasMainFloor || tile == Tile.PILLAR || profile == Level1Elevation.Profile.MULTI_DECK) return null;
		double dist = Math.hypot(lx - 32, lz - 32);
		if (dist > 18) return null;
		// Stepping-stone path crossing the pond along lx == 32 or lz == 32
		boolean steppingStone = (Math.abs(lx - 32) <= 1 && lz % 3 != 0) || (Math.abs(lz - 32) <= 1 && lx % 3 != 0);
		if (dist <= 15) {
			if (y == FLOOR) return steppingStone ? state(Level1Blocks.GARDEN_MOSS) : Blocks.WATER.defaultBlockState();
			if (y == FLOOR - 1) return state(Level1Blocks.GARDEN_MOSS);
			if (y == FLOOR + 1 && steppingStone && cur.isAir() && Math.floorMod(Level1Layout.hash(seed, x, z, 97), 5) == 0) {
				return state(Level1Blocks.GARDEN_FLOWER_BLUE);
			}
		} else if (y == FLOOR + 1 && cur.isAir()) {
			int roll = Math.floorMod(Level1Layout.hash(seed, x, z, 101), 10);
			if (roll < 5) return state(gardenPlant(roll));
		}
		return null;
	}

	/** Overgrown ruined concrete arbors with shattered mossy walls and dense hanging canopy tendrils. */
	private static BlockState gardenRuinedArbors(long seed, Tile tile, BlockState cur, int x, int y, int z, int lx, int lz, int topCeil) {
		if (tile == Tile.PILLAR || !cur.isAir()) return null;
		int cx = lx % 14, cz = lz % 14;
		int wallHash = Math.floorMod(Level1Layout.hash(seed, lx / 14, lz / 14, 103), 100);
		// Broken mossy ruin walls along cell edges with jagged heights
		if ((cx == 0 || cz == 0) && (cx != 7 && cz != 7)) {
			int maxH = 1 + Math.floorMod(Level1Layout.hash(seed, x, z, 107), 4);
			if (wallHash < 65 && y > FLOOR && y <= FLOOR + maxH) {
				return state((x + y + z) % 3 == 0 ? Level1Blocks.GARDEN_MOSS : Level1Blocks.GARDEN_WALL);
			}
		}
		// Overhead mossy pergola beams & hanging vines
		if (y == Math.min(topCeil - 2, FLOOR + 5) && (cx == 0 || cz == 0)) {
			return state(Level1Blocks.GARDEN_PILLAR);
		}
		if (y >= FLOOR + 3 && y < topCeil - 1 && (cx == 0 || cz == 0)
				&& Math.floorMod(Level1Layout.hash(seed, x, z, 109), 7) == 0) {
			return state(Level1Blocks.GARDEN_CEILING);
		}
		return null;
	}

	// ==================== LEGEND (3 variants) ====================

	/** Towering cyan/magenta/amber neon light-columns and suspended neon ribbons that burn through blackouts. */
	private static BlockState legendNeonAtrium(long seed, Tile tile, BlockState cur, int x, int y, int z, int lx, int lz, int topCeil) {
		if (tile == Tile.PILLAR || !cur.isAir()) return null;
		int color = Math.floorMod(Level1Layout.hash(seed, lx / 12, lz / 12, 113), NEON_COUNT);
		// Glowing neon drop-columns hanging from the ceiling down to head height
		if (lx % 12 == 6 && lz % 12 == 6 && y >= FLOOR + 3 && y < topCeil) {
			return state(neon(color)).setValue(NeonBlock.UPRIGHT, true);
		}
		// Horizontal neon light-ribbons crisscrossing overhead
		if ((y == FLOOR + 4 || (topCeil >= 46 && y == FLOOR + 9)) && (lx % 12 == 6 || lz % 12 == 6)) {
			return state(neon(color + (y == FLOOR + 4 ? 0 : 1)));
		}
		return null;
	}

	/** Elevated crimson lacquer pavilions with gilded pillars, neon eaves, stairs, and glowing barrel braziers. */
	private static BlockState legendCrimsonPavilions(long seed, Tile tile, BlockState cur, int x, int y, int z, int lx, int lz,
			boolean hasMainFloor) {
		if (!hasMainFloor || tile == Tile.PILLAR) return null;
		// Centered crimson pavilion at lx in [20..44], lz in [20..44]
		if (lx < 20 || lx > 44 || lz < 20 || lz > 44) return null;
		int rx = lx - 20, rz = lz - 20; // 0..24
		// Steps on all 4 cardinal sides
		boolean onStep = ((rx == 0 || rx == 24) && rz >= 10 && rz <= 14) || ((rz == 0 || rz == 24) && rx >= 10 && rx <= 14);
		if (onStep) {
			if (y == FLOOR + 1) {
				Direction dir = rx == 0 ? Direction.EAST : rx == 24 ? Direction.WEST : rz == 0 ? Direction.SOUTH : Direction.NORTH;
				return stairs(dir);
			}
			return null;
		}
		if (rx >= 1 && rx <= 23 && rz >= 1 && rz <= 23) {
			if (y == FLOOR + 1) return state(Level1Blocks.LEGEND_WALL);
			if (y == FLOOR + 2) return state((rx == 12 || rz == 12) ? Level1Blocks.LEGEND_FLOOR_LINE : Level1Blocks.LEGEND_FLOOR);
			// Corner and mid-span crimson pillars rising to pavilion roof at y = FLOOR + 7
			boolean post = (rx == 2 || rx == 12 || rx == 22) && (rz == 2 || rz == 12 || rz == 22) && !(rx == 12 && rz == 12);
			if (post && y >= FLOOR + 3 && y <= FLOOR + 6) {
				return state(y == FLOOR + 3 ? Level1Blocks.LEGEND_BASE : Level1Blocks.LEGEND_PILLAR);
			}
			// Roof canopy with glowing neon trim
			if (y == FLOOR + 7 && cur.isAir()) {
				boolean rim = rx == 1 || rx == 23 || rz == 1 || rz == 23;
				if (rim) {
					int c = Math.floorMod(Level1Layout.hash(seed, rx / 4, rz / 4, 127), NEON_COUNT);
					return state(neon(c));
				}
				return state(Level1Blocks.LEGEND_CEILING);
			}
			// Center warm brazier
			if (rx == 12 && rz == 12 && y == FLOOR + 3 && cur.isAir()) return state(Level1Blocks.BARREL_FIRE);
		}
		return null;
	}

	/** Crimson lacquer screen walls and elevated neon-trimmed wooden bridges crisscrossing the hall. */
	private static BlockState legendLanternBridges(Tile tile, BlockState cur, int lx, int lz, int y, int topCeil, boolean hasMainFloor) {
		if (tile == Tile.PILLAR || !cur.isAir()) return null;
		// Crimson divider screens with wide openings
		if (hasMainFloor && (lx == 20 || lx == 44) && (lz % 12 < 7) && y >= FLOOR + 1 && y <= FLOOR + 3) {
			return state(y == FLOOR + 3 ? Level1Blocks.NEON_AMBER : Level1Blocks.LEGEND_WALL);
		}
		// High neon-edged bridge at y = FLOOR + 5 when ceiling allows
		if (topCeil >= FLOOR + 9 && lz >= 28 && lz <= 36) {
			if (y == FLOOR + 5) return state(Level1Blocks.LEGEND_FLOOR);
			if (y == FLOOR + 6 && (lz == 28 || lz == 36)) {
				return state(lx % 4 == 0 ? Level1Blocks.NEON_CYAN : Level1Blocks.LEGEND_WALL);
			}
		}
		return null;
	}

	// ==================== TAILCHASE (3 variants) ====================

	/** Multi-story steel scaffolding towers rising to the ceiling with work platforms, cones, machines and caged lamps. */
	private static BlockState tailchaseScaffoldTowers(long seed, Tile tile, BlockState cur, int x, int y, int z, int lx, int lz, int topCeil) {
		if (tile == Tile.PILLAR || !cur.isAir()) return null;
		int cx = lx % 16, cz = lz % 16;
		// 5x5 scaffold towers centered at (6..10, 6..10) in each 16x16 cell
		if (cx < 6 || cx > 10 || cz < 6 || cz > 10) return null;
		int towerRoll = Math.floorMod(Level1Layout.hash(seed, lx / 16, lz / 16, 131), 10);
		if (towerRoll >= 6) return null;
		boolean corner = (cx == 6 || cx == 10) && (cz == 6 || cz == 10);
		if (corner && y > FLOOR && y < topCeil) return state(Level1Blocks.SCAFFOLD_POLE);
		// Work platforms every 4 blocks vertically
		if (y > FLOOR + 1 && y < topCeil - 2 && (y - FLOOR) % 4 == 0) {
			return state(Level1Blocks.GARAGE_FLOOR);
		}
		if (y > FLOOR + 1 && y < topCeil - 2 && (y - FLOOR) % 4 == 1) {
			if (cx == 8 && cz == 8) return state(Level1Blocks.SITE_MACHINE);
			if (cx == 7 && cz == 7) return state(Level1Blocks.CONSTRUCTION_CONE);
		}
		if (cx == 8 && cz == 8 && y == topCeil - 1) return state(Level1Blocks.CAGED_LAMP);
		return null;
	}

	/** Deep rectangular excavation pits (3 blocks deep) ringed by Jersey barriers, cones, drums and access stairs. */
	private static BlockState tailchaseExcavationPits(long seed, Tile tile, BlockState cur, int x, int y, int z, int lx, int lz,
			boolean hasMainFloor, Level1Elevation.Profile profile) {
		if (!hasMainFloor || tile == Tile.PILLAR || profile == Level1Elevation.Profile.MULTI_DECK) return null;
		// Two excavation pits at (12..26, 14..28) and (36..50, 34..48)
		boolean pitA = lx >= 12 && lx <= 26 && lz >= 14 && lz <= 28;
		boolean pitB = lx >= 36 && lx <= 50 && lz >= 34 && lz <= 48;
		if (!pitA && !pitB) return null;
		int rx = pitA ? lx - 12 : lx - 36;
		int rz = pitA ? lz - 14 : lz - 34;
		boolean rim = rx == 0 || rx == 14 || rz == 0 || rz == 14;
		if (rim) {
			// Leave an entrance gap on the north side (rz == 0, rx == 6..8)
			if (rz == 0 && rx >= 6 && rx <= 8) return null;
			if (y == FLOOR + 1 && cur.isAir()) {
				if ((rx + rz) % 3 == 0) return state(Level1Blocks.CONSTRUCTION_CONE);
				return barrier((rz == 0 || rz == 14) ? Direction.Axis.X : Direction.Axis.Z);
			}
			return null;
		}
		// Stairs descending from north entrance (rx == 6..8, rz == 1..3)
		if (rx >= 6 && rx <= 8 && rz >= 1 && rz <= 3) {
			int stepY = FLOOR - rz + 1; // 32, 31, 30
			if (y == stepY) return stairs(Direction.NORTH);
			if (y > stepY && y <= FLOOR) return air();
			if (y < stepY && y >= FLOOR - 3) return state(Level1Blocks.GARAGE_WALL);
			return null;
		}
		if (y >= FLOOR - 2 && y <= FLOOR) {
			if (y == FLOOR - 2 && (rx == 4 || rx == 10) && rz == 10) return state(Level1Blocks.FUEL_DRUM);
			if (y == FLOOR - 2 && rx == 7 && rz == 8) return state(Level1Blocks.SITE_MACHINE);
			return air();
		}
		if (y == FLOOR - 3) return state(Level1Blocks.DAMP_GARAGE_FLOOR);
		return null;
	}

	/** Half-built suspended concrete beams, hanging scaffold poles, and blue tarp construction curtains. */
	private static BlockState tailchaseUnfinishedBeams(long seed, Tile tile, BlockState cur, int x, int y, int z, int lx, int lz, int topCeil) {
		if (tile == Tile.PILLAR || !cur.isAir()) return null;
		// Blue tarp partitions stretching vertically in construction zones (with open walkways every 12 blocks)
		if ((lx == 18 || lx == 46) && lz % 12 < 7 && y >= FLOOR + 1 && y <= Math.min(topCeil - 2, FLOOR + 4)) {
			return state(Level1Blocks.TARP);
		}
		// Half-built concrete crossbeams hanging mid-air at y = FLOOR + 4
		if (y == FLOOR + 4 && (lz == 22 || lz == 42) && lx % 9 < 6) {
			return state(Level1Blocks.GARAGE_CEILING);
		}
		// Hanging scaffold poles under the unfinished beams
		if (y >= FLOOR + 2 && y <= FLOOR + 3 && (lz == 22 || lz == 42) && lx % 9 == 0) {
			return state(Level1Blocks.SCAFFOLD_POLE);
		}
		// Scattered warning cones on the floor underneath
		if (y == FLOOR + 1 && (lz == 21 || lz == 43) && Math.floorMod(Level1Layout.hash(seed, x, z, 137), 5) == 0) {
			return state(Level1Blocks.CONSTRUCTION_CONE);
		}
		return null;
	}

	private static BlockState barrier(Direction.Axis axis) {
		return Level1Blocks.JERSEY_BARRIER.defaultBlockState().setValue(AxisShapedBlock.AXIS, axis);
	}

	private static BlockState stairs(Direction facing) {
		return Level1Blocks.CONCRETE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
	}

	private static BlockState state(Block block) {
		return block.defaultBlockState();
	}

	private static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}
}
