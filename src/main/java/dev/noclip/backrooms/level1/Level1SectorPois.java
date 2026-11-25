package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Elevation.Profile;
import dev.noclip.backrooms.level1.Level1Layout.Base;
import dev.noclip.backrooms.level1.Level1Layout.Sector;
import dev.noclip.backrooms.props.BackroomsProps;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Deterministic 7x7 sector-exclusive micro-POIs placed on the main floor (y = 32..36)
 * inside a pillar-free, span-free corner bay of eligible hall districts (~45% chance).
 */
public final class Level1SectorPois {
	private static final int D = Level1Layout.DISTRICT;
	private static final int FLOOR = Level1ChunkGenerator.FLOOR_Y;
	private static final int CHANCE_SALT = 149;
	private static final int CHANCE_PERCENT = 45;
	private static final ConcurrentHashMap<Key, Optional<Poi>> CACHE = new ConcurrentHashMap<>();

	public enum Kind {
		NONE,
		TALON_CRASH_SITE,
		GILD_TRADE_STALL,
		GOTHIC_RELIQUARY_SHRINE,
		GARDEN_OVERGROWN_CAMP,
		LEGEND_NEON_ARCADE,
		TAILCHASE_WORKSITE_SHACK
	}

	public record Poi(
			Kind kind,
			Sector sector,
			int dx,
			int dz,
			int minLx,
			int maxLx,
			int minLz,
			int maxLz,
			int lootX,
			int lootY,
			int lootZ,
			int fuseX,
			int fuseY,
			int fuseZ,
			boolean hasFuse) {
	}

	private record Key(long seed, int dx, int dz) {
	}

	private Level1SectorPois() {
	}

	/** Returns the sector micro-POI in district (dx, dz), or null if none spawns here. */
	public static Poi poi(long seed, int dx, int dz) {
		if (CACHE.size() > 1024) CACHE.clear();
		return CACHE.computeIfAbsent(new Key(seed, dx, dz), key -> Optional.ofNullable(compute(key.seed(), key.dx(), key.dz())))
				.orElse(null);
	}

	/** Whether (lx, lz) lies within the POI's 7x7 footprint + 1-block reserved halo. */
	static boolean isReserved(long seed, int dx, int dz, int lx, int lz) {
		Poi p = poi(seed, dx, dz);
		return p != null && lx >= p.minLx() - 1 && lx <= p.maxLx() + 1 && lz >= p.minLz() - 1 && lz <= p.maxLz() + 1;
	}

	private static Poi compute(long seed, int dx, int dz) {
		if (dx == 0 && dz == 0) return null;
		if (Level1Layout.kind(seed, dx, dz) == Level1Layout.Kind.CORRIDORS) return null;
		if (Level1Layout.base(seed, dx, dz) != Base.NONE) return null;
		if (Level1Landmarks.landmark(seed, dx, dz) != Level1Landmarks.Landmark.NONE) return null;
		if (Level1Elevation.profile(seed, dx, dz) == Profile.SUNKEN_BASIN) return null;
		if (Math.floorMod(Level1Layout.hash(seed, dx, dz, CHANCE_SALT), 100) >= CHANCE_PERCENT) return null;

		int x0 = dx * D, z0 = dz * D;
		int minLx = 49, maxLx = 55, minLz = 51, maxLz = 57;
		if (!candidateValid(seed, dx, dz, x0, z0, minLx, maxLx, minLz, maxLz)) {
			minLx = 6;
			maxLx = 12;
			if (!candidateValid(seed, dx, dz, x0, z0, minLx, maxLx, minLz, maxLz)) return null;
		}

		Sector sector = Level1Layout.sector(seed, dx * D + D / 2, dz * D + D / 2);
		Kind kind = switch (sector) {
			case TALON -> Kind.TALON_CRASH_SITE;
			case GILD -> Kind.GILD_TRADE_STALL;
			case GOTHIC -> Kind.GOTHIC_RELIQUARY_SHRINE;
			case GARDEN -> Kind.GARDEN_OVERGROWN_CAMP;
			case LEGEND -> Kind.LEGEND_NEON_ARCADE;
			case TAILCHASE -> Kind.TAILCHASE_WORKSITE_SHACK;
		};

		int bx = x0 + minLx, bz = z0 + minLz;
		return switch (kind) {
			case TALON_CRASH_SITE -> new Poi(kind, sector, dx, dz, minLx, maxLx, minLz, maxLz,
					bx + 5, 33, bz + 4, bx + 5, 33, bz + 1, true);
			case GILD_TRADE_STALL -> new Poi(kind, sector, dx, dz, minLx, maxLx, minLz, maxLz,
					bx + 2, 33, bz + 4, 0, 0, 0, false);
			case GOTHIC_RELIQUARY_SHRINE -> new Poi(kind, sector, dx, dz, minLx, maxLx, minLz, maxLz,
					bx + 3, 34, bz + 4, 0, 0, 0, false);
			case GARDEN_OVERGROWN_CAMP -> new Poi(kind, sector, dx, dz, minLx, maxLx, minLz, maxLz,
					bx + 5, 33, bz + 2, 0, 0, 0, false);
			case LEGEND_NEON_ARCADE -> new Poi(kind, sector, dx, dz, minLx, maxLx, minLz, maxLz,
					bx + 4, 33, bz + 4, bx + 5, 33, bz + 3, true);
			case TAILCHASE_WORKSITE_SHACK -> new Poi(kind, sector, dx, dz, minLx, maxLx, minLz, maxLz,
					bx + 2, 33, bz + 5, bx + 5, 33, bz + 3, true);
			case NONE -> null;
		};
	}

	private static boolean candidateValid(long seed, int dx, int dz, int x0, int z0, int minLx, int maxLx, int minLz, int maxLz) {
		if (anyNearFeature(seed, dx, dz, minLx - 1, maxLx + 1, minLz - 1, maxLz + 1)) return false;
		return !nearPipeThreshold(seed, x0 + minLx + 3, z0 + minLz + 3);
	}

	private static boolean nearPipeThreshold(long seed, int cx, int cz) {
		double zone = Level1Layout.pipeZone(seed, cx, cz);
		return zone >= 0 && zone <= 0.15;
	}

	private static boolean anyNearFeature(long seed, int dx, int dz, int minLx, int maxLx, int minLz, int maxLz) {
		HallPlan plan = HallPlan.of(seed, dx, dz);
		int x0 = dx * D, z0 = dz * D;
		int minX = x0 + minLx - 5, maxX = x0 + maxLx + 5;
		int minZ = z0 + minLz - 6, maxZ = z0 + maxLz + 6;
		for (HallPlan.Placed placed : plan.placed()) {
			int[] spot = placed.spot();
			if (spot[0] >= minX && spot[0] <= maxX && spot[1] >= minZ && spot[1] <= maxZ) return true;
		}
		for (HallPlan.PlacedProp prop : plan.props()) {
			int[] spot = prop.spot();
			if (spot[0] >= minX && spot[0] <= maxX && spot[1] >= minZ && spot[1] <= maxZ) return true;
		}
		return false;
	}

	/**
	 * Generates the sector micro-POI block at (x, y, z) on y = 32..36, or null outside.
	 */
	static BlockState block(long seed, int x, int y, int z) {
		if (y < FLOOR || y > FLOOR + 4) return null;
		int dx = Math.floorDiv(x, D), dz = Math.floorDiv(z, D);
		Poi p = poi(seed, dx, dz);
		if (p == null) return null;

		int lx = Math.floorMod(x, D), lz = Math.floorMod(z, D);
		if (lx < p.minLx() || lx > p.maxLx() || lz < p.minLz() || lz > p.maxLz()) return null;

		int u = lx - p.minLx();
		int v = lz - p.minLz();
		return switch (p.kind()) {
			case TALON_CRASH_SITE -> talonCrashSite(u, v, y);
			case GILD_TRADE_STALL -> gildTradeStall(u, v, y);
			case GOTHIC_RELIQUARY_SHRINE -> gothicReliquaryShrine(u, v, y);
			case GARDEN_OVERGROWN_CAMP -> gardenOvergrownCamp(u, v, y);
			case LEGEND_NEON_ARCADE -> legendNeonArcade(u, v, y);
			case TAILCHASE_WORKSITE_SHACK -> tailchaseWorksiteShack(u, v, y);
			case NONE -> null;
		};
	}

	private static BlockState talonCrashSite(int u, int v, int y) {
		if (y == 32) {
			if (u == 0 || u == 6) return state(Level1Blocks.GARAGE_FLOOR_LINE);
			if (u >= 1 && u <= 5 && v >= 1 && v <= 5) return state(Level1Blocks.DAMP_GARAGE_FLOOR);
			return state(Level1Blocks.GARAGE_FLOOR);
		}
		if (u == 5 && v == 0 && y >= 33 && y <= 35) {
			return state(Level1Blocks.GARAGE_WALL);
		}
		if (y == 33) {
			if (((u == 0 || u == 1) && v == 0) || ((u == 5 || u == 6) && v == 6)) return barrier(Direction.Axis.X);
			if ((u == 0 && v == 1) || (u == 6 && v == 5)) return barrier(Direction.Axis.Z);
			if ((u == 2 && v == 0) || (u == 4 && v == 6)) return state(Level1Blocks.CONSTRUCTION_CONE);
			if (u == 2 && v == 3) return facing(Level1Blocks.ABANDONED_CAR, Direction.EAST);
			if (u == 5 && v == 1) return facing(BackroomsProps.INDUSTRIAL_FUSE_BOX, Direction.SOUTH);
			if (u == 5 && v == 4) return facing(BackroomsProps.LOCKED_ARMORY_CRATE, Direction.WEST);
			if (u == 4 && v == 5) return state(Level1Blocks.SUPPLY_CRATE);
			if (u == 1 && v == 5) return facing(BackroomsProps.WANDERER_REMAINS, Direction.EAST);
			if (u == 2 && v == 5) return state(Level1Blocks.DEBRIS_PILE);
			if (u == 0 && v == 4) return state(Level1Blocks.FLOODLIGHT);
			if (u == 6 && v == 2) return state(Level1Blocks.FUEL_DRUM);
		}
		return air();
	}

	private static BlockState gildTradeStall(int u, int v, int y) {
		if (y == 32) return state(Level1Blocks.GILD_FLOOR);
		boolean inner = u >= 1 && u <= 5 && v >= 1 && v <= 5;
		if (y == 36) return inner ? state(Level1Blocks.TARP) : air();
		if (u == 1 && v == 1) return state(Level1Blocks.GILD_PILLAR_0);
		if (u == 5 && v == 1) return state(Level1Blocks.GILD_PILLAR_2);
		if (u == 1 && v == 5) return state(Level1Blocks.GILD_PILLAR_4);
		if (u == 5 && v == 5) return state(Level1Blocks.GILD_PILLAR_3);
		if (y == 35 && (u == 2 || u == 4) && v == 3) return state(Level1Blocks.CAGED_LAMP);
		if (y == 33) {
			if ((u == 2 || u == 3 || u == 4) && v == 5) return facing(Level1Blocks.OFFICE_DESK, Direction.NORTH);
			if (u == 1 && v == 3) return facing(Level1Blocks.VENDING_MACHINE, Direction.EAST);
			if (u == 5 && v == 3) return facing(Level1Blocks.PALLET_STACK, Direction.WEST);
			if (u == 5 && v == 2) return facing(BackroomsProps.SALVAGE_BENCH, Direction.WEST);
			if (u == 2 && v == 4) return facing(BackroomsProps.LOCKED_ARMORY_CRATE, Direction.NORTH);
			if (u == 4 && v == 4) return state(Level1Blocks.SUPPLY_CRATE);
			if (u == 1 && v == 2) return facing(BackroomsProps.PROP_WOOD_CHAIR, Direction.EAST);
			if (u == 4 && v == 2) return facing(BackroomsProps.WANDERER_REMAINS, Direction.WEST);
		}
		if (y == 34) {
			if (u == 3 && v == 5) return facing(Level1Blocks.OLD_COMPUTER, Direction.NORTH);
			if (u == 2 && v == 5) return state(Level1Blocks.DESK_PLANT_LEAFY);
		}
		return air();
	}

	private static BlockState gothicReliquaryShrine(int u, int v, int y) {
		if (y == 32) return state(Level1Blocks.GOTHIC_FLOOR);
		boolean inner = u >= 1 && u <= 5 && v >= 1 && v <= 5;
		if (y == 36) return inner ? state(Level1Blocks.GOTHIC_CEILING) : air();
		if ((u == 1 || u == 5) && (v == 1 || v == 5)) {
			return state(y == 33 ? Level1Blocks.GOTHIC_BASE : Level1Blocks.GOTHIC_COLUMN);
		}
		if (v == 5 && u >= 2 && u <= 4) return state(Level1Blocks.GOTHIC_BRICK);
		if (y == 35) {
			if (u == 3 && v == 3) return facing(BackroomsProps.VINTAGE_CHANDELIER, Direction.NORTH);
			if ((u == 2 || u == 4) && v == 2) return state(Level1Blocks.CAGED_LAMP);
		}
		if ((y == 33 || y == 34) && (u == 1 || u == 5) && v == 4) {
			return state(Level1Blocks.BOOK_SHELF);
		}
		if (y == 33) {
			if (u == 3 && v == 4) return state(Level1Blocks.GOTHIC_BRICK);
			if (u == 2 && v == 4) return facing(BackroomsProps.ALMOND_DISTILLER, Direction.NORTH);
			if (u == 4 && v == 4) return state(Level1Blocks.SUPPLY_CRATE);
			if (u == 1 && v == 3) return facing(BackroomsProps.WANDERER_REMAINS, Direction.EAST);
		}
		if (y == 34 && u == 3 && v == 4) {
			return facing(BackroomsProps.ANOMALY_VAULT_CHEST, Direction.NORTH);
		}
		return air();
	}

	private static BlockState gardenOvergrownCamp(int u, int v, int y) {
		boolean inner = u >= 1 && u <= 5 && v >= 1 && v <= 5;
		if (y == 32) return state(inner ? Level1Blocks.GARDEN_MOSS : Level1Blocks.GARDEN_GRASS_FLOOR);
		if (y == 36) {
			boolean ring = inner && (u == 1 || u == 5 || v == 1 || v == 5);
			return ring ? state(Level1Blocks.GARDEN_CEILING) : air();
		}
		if ((u == 1 || u == 5) && (v == 1 || v == 5)) {
			return state(Level1Blocks.GARDEN_PILLAR);
		}
		if (y == 35 && (u == 1 || u == 5) && v == 3) return state(Level1Blocks.CAGED_LAMP);
		if (y == 33) {
			if (u == 2 && v == 3) return facing(Level1Blocks.ABANDONED_CAR, Direction.NORTH);
			if (u == 4 && v == 3) return state(Level1Blocks.BARREL_FIRE);
			if (u == 5 && v == 2) return facing(BackroomsProps.LOCKED_ARMORY_CRATE, Direction.WEST);
			if (u == 5 && v == 4) return state(Level1Blocks.SUPPLY_CRATE);
			if (u == 4 && v == 5) return facing(BackroomsProps.WANDERER_REMAINS, Direction.NORTH);
			if (u == 3 && v == 5) return facing(BackroomsProps.PROP_WOOD_CHAIR, Direction.NORTH);
			if (u == 1 && v == 4) return facing(BackroomsProps.PROP_POTTED_PLANT_PLASTIC, Direction.EAST);
			if (u == 0 && v == 1) return state(Level1Blocks.GARDEN_FERN);
			if (u == 6 && v == 1) return state(Level1Blocks.GARDEN_FLOWER_BLUE);
			if (u == 0 && v == 5) return state(Level1Blocks.GARDEN_FLOWER_YELLOW);
			if (u == 6 && v == 5) return state(Level1Blocks.GARDEN_FLOWER_RED);
			if (u == 2 && v == 1) return state(Level1Blocks.GARDEN_GRASS);
		}
		return air();
	}

	private static BlockState legendNeonArcade(int u, int v, int y) {
		if (y == 32) {
			return state((u == 3 || v == 3) ? Level1Blocks.LEGEND_FLOOR_LINE : Level1Blocks.LEGEND_FLOOR);
		}
		if (y == 36) {
			if (u >= 2 && u <= 4 && v >= 2 && v <= 4) return state(Level1Blocks.LEGEND_CEILING);
			if (u >= 1 && u <= 5 && v >= 1 && v <= 5) {
				return state((u == 1 || u == 5) ? Level1Blocks.NEON_CYAN : Level1Blocks.NEON_MAGENTA);
			}
			return air();
		}
		if ((u == 1 || u == 5) && (v == 1 || v == 5)) {
			return state(y == 33 ? Level1Blocks.LEGEND_BASE : Level1Blocks.LEGEND_PILLAR);
		}
		if (v == 5 && u >= 2 && u <= 4) {
			return state(y <= 34 ? Level1Blocks.LEGEND_WALL : Level1Blocks.NEON_AMBER);
		}
		if (y == 33) {
			if (u == 2 && v == 4) return facing(Level1Blocks.VENDING_MACHINE, Direction.NORTH);
			if (u == 4 && v == 4) return facing(BackroomsProps.ANOMALY_VAULT_CHEST, Direction.NORTH);
			if (u == 5 && v == 3) return facing(BackroomsProps.INDUSTRIAL_FUSE_BOX, Direction.WEST);
			if (u == 1 && v == 3) return facing(Level1Blocks.OFFICE_DESK, Direction.EAST);
			if (u == 2 && v == 2) return facing(BackroomsProps.PROP_OFFICE_CHAIR, Direction.WEST);
			if (u == 4 && v == 2) return facing(BackroomsProps.WANDERER_REMAINS, Direction.NORTH);
			if (u == 5 && v == 2) return state(Level1Blocks.SUPPLY_CRATE);
		}
		if (y == 34 && u == 1 && v == 3) {
			return facing(BackroomsProps.PROP_CRT_TV, Direction.EAST);
		}
		return air();
	}

	private static BlockState tailchaseWorksiteShack(int u, int v, int y) {
		boolean inner = u >= 1 && u <= 5 && v >= 1 && v <= 5;
		if (y == 32) return state(inner ? Level1Blocks.CORRIDOR_FLOOR : Level1Blocks.GARAGE_FLOOR);
		if (y == 36) return inner ? state(Level1Blocks.TARP) : air();
		if ((u == 1 || u == 5) && (v == 1 || v == 5)) return state(Level1Blocks.SCAFFOLD_POLE);
		if (y == 35) {
			if ((u == 2 || u == 4) && v == 3) return state(Level1Blocks.CAGED_LAMP);
			if (u == 3 && v == 2) {
				return Level1Blocks.BROKEN_GARAGE_LIGHT.defaultBlockState().setValue(AxisPlacedBlock.AXIS, Direction.Axis.X);
			}
		}
		if (y == 33) {
			if ((u == 0 || u == 6) && (v == 0 || v == 6)) return state(Level1Blocks.CONSTRUCTION_CONE);
			if (u == 1 && v == 4) return facing(BackroomsProps.WORKSHOP_BENCH, Direction.EAST);
			if (u == 2 && v == 5) return facing(BackroomsProps.LOCKED_ARMORY_CRATE, Direction.NORTH);
			if (u == 3 && v == 5) return state(Level1Blocks.SUPPLY_CRATE);
			if (u == 4 && v == 5) return state(Level1Blocks.FUEL_DRUM);
			if (u == 5 && v == 3) return facing(BackroomsProps.INDUSTRIAL_FUSE_BOX, Direction.WEST);
			if (u == 5 && v == 4) return facing(Level1Blocks.ELECTRICAL_CABINET, Direction.WEST);
			if (u == 1 && v == 2) return state(Level1Blocks.SITE_MACHINE);
			if (u == 4 && v == 2) return state(Level1Blocks.DEBRIS_PILE);
			if (u == 3 && v == 4) return facing(BackroomsProps.WANDERER_REMAINS, Direction.NORTH);
			if (u == 0 && v == 3) return state(Level1Blocks.FLOODLIGHT);
		}
		return air();
	}

	private static BlockState barrier(Direction.Axis axis) {
		return Level1Blocks.JERSEY_BARRIER.defaultBlockState().setValue(AxisShapedBlock.AXIS, axis);
	}

	private static BlockState facing(Block block, Direction dir) {
		return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, dir);
	}

	private static BlockState state(Block block) {
		return block.defaultBlockState();
	}

	private static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}
}
