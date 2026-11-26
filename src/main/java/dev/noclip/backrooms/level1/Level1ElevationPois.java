package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Elevation.Profile;
import dev.noclip.backrooms.level1.Level1Layout.Base;
import dev.noclip.backrooms.level1.Level1Layout.Ceiling;
import dev.noclip.backrooms.level1.Level1Layout.Tile;
import dev.noclip.backrooms.props.BackroomsProps;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Deterministic vertical-space Points of Interest (POIs) placed inside non-standard
 * {@link Level1Elevation} districts (Vaulted skywalks, Multi-Deck B1 garages, Sunken Basins,
 * and Sublevel Tunnels). All footprints stay strictly off the y=32 main floor and inside
 * pillar-free Z bands so main-floor connectivity, sector architecture, and pipeFit stay intact.
 */
public final class Level1ElevationPois {
	private static final int D = Level1Layout.DISTRICT;
	private static final int CHANCE_SALT = 131;
	private static final int CHANCE_PERCENT = 65;
	private static final ConcurrentHashMap<Key, Optional<Poi>> CACHE = new ConcurrentHashMap<>();

	public enum Kind {
		NONE,
		SKYWALK_WATCHPOST,
		B1_GARAGE_CHECKPOINT,
		BASIN_PUMP_STATION,
		TUNNEL_SERVICE_CACHE
	}

	public record Poi(
			Kind kind,
			int dx,
			int dz,
			int minLx,
			int maxLx,
			int minLz,
			int maxLz,
			int floorY,
			int fuseX,
			int fuseY,
			int fuseZ,
			int lampX,
			int lampY,
			int lampZ,
			int chestX,
			int chestY,
			int chestZ,
			boolean deep) {
	}

	private record Key(long seed, int dx, int dz) {
	}

	private Level1ElevationPois() {
	}

	/** Returns the vertical-space POI in district (dx, dz), or null if none spawns here. */
	public static Poi poi(long seed, int dx, int dz) {
		if (CACHE.size() > 1024) CACHE.clear();
		return CACHE.computeIfAbsent(new Key(seed, dx, dz), key -> Optional.ofNullable(compute(key.seed(), key.dx(), key.dz())))
				.orElse(null);
	}

	private static Poi compute(long seed, int dx, int dz) {
		if (dx == 0 && dz == 0) return null;
		if (Level1Layout.kind(seed, dx, dz) == Level1Layout.Kind.CORRIDORS) return null;
		if (Level1Layout.base(seed, dx, dz) != Base.NONE) return null;
		if (Level1Landmarks.landmark(seed, dx, dz) != Level1Landmarks.Landmark.NONE) return null;
		Profile profile = Level1Elevation.profile(seed, dx, dz);
		if (profile == Profile.STANDARD) return null;
		if (Math.floorMod(Level1Layout.hash(seed, dx, dz, CHANCE_SALT), 100) >= CHANCE_PERCENT) return null;

		boolean deep = Level1Elevation.extraTier(seed, dx, dz);
		int x0 = dx * D, z0 = dz * D;

		return switch (profile) {
			case VAULTED -> {
				int minLx = 29, maxLx = 34, minLz = 5, maxLz = 13;
				if (anyNearFeature(seed, dx, dz, 28, 35, 4, 14)) yield null;
				yield new Poi(
						Kind.SKYWALK_WATCHPOST, dx, dz, minLx, maxLx, minLz, maxLz, 40,
						x0 + minLx + 0, 41, z0 + minLz + 2,
						x0 + minLx + 2, 44, z0 + minLz + 1,
						x0 + minLx + 4, 41, z0 + minLz + 0,
						deep);
			}
			case MULTI_DECK -> {
				int minLx = 27, maxLx = 36, minLz = 36, maxLz = 44;
				if (anyNearFeature(seed, dx, dz, minLx, maxLx, minLz, maxLz)) {
					minLz = 20;
					maxLz = 28;
					if (anyNearFeature(seed, dx, dz, minLx, maxLx, minLz, maxLz)) yield null;
				}
				yield new Poi(
						Kind.B1_GARAGE_CHECKPOINT, dx, dz, minLx, maxLx, minLz, maxLz, 24,
						x0 + minLx + 5, 25, z0 + minLz + 2,
						x0 + minLx + 5, 30, z0 + minLz + 4,
						x0 + minLx + 8, 25, z0 + minLz + 6,
						deep);
			}
			case SUNKEN_BASIN -> {
				int minLx = 17, maxLx = 25, minLz = 22, maxLz = 30;
				if (anyNearFeature(seed, dx, dz, minLx - 1, maxLx + 1, minLz - 1, maxLz + 1)) {
					minLx = 38;
					maxLx = 46;
					if (anyNearFeature(seed, dx, dz, minLx - 1, maxLx + 1, minLz - 1, maxLz + 1)) yield null;
				}
				int basinY = deep ? 18 : 24;
				int platY = basinY + 1;
				yield new Poi(
						Kind.BASIN_PUMP_STATION, dx, dz, minLx, maxLx, minLz, maxLz, platY,
						x0 + minLx + 4, platY + 1, z0 + minLz + 1,
						x0 + minLx + 4, basinY + 5, z0 + minLz + 4,
						x0 + minLx + 4, platY + 1, z0 + minLz + 4,
						deep);
			}
			case SUBLEVEL_TUNNELS -> {
				int minLx = deep ? 22 : 28;
				int maxLx = deep ? 28 : 34;
				int minLz = deep ? 22 : 15;
				int maxLz = deep ? 28 : 21;
				if (anyNearFeature(seed, dx, dz, minLx, maxLx, minLz, maxLz)) yield null;
				int floorY = deep ? 14 : 22;
				yield new Poi(
						Kind.TUNNEL_SERVICE_CACHE, dx, dz, minLx, maxLx, minLz, maxLz, floorY,
						deep ? x0 + minLx + 3 : x0 + minLx + 5,
						floorY + 1,
						deep ? z0 + minLz + 1 : z0 + minLz + 3,
						x0 + minLx + 3, deep ? floorY + 4 : floorY + 3, z0 + minLz + 3,
						deep ? x0 + minLx + 1 : x0 + minLx + 1,
						floorY + 1,
						deep ? z0 + minLz + 3 : z0 + minLz + 4,
						deep);
			}
			case STANDARD -> null;
		};
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
	 * Generates an elevation POI block at (x, y, z), or null to fall through to {@link Level1Elevation}.
	 */
	static BlockState block(long seed, Tile tile, Ceiling ceiling, int x, int y, int z) {
		if (y <= 0 || y >= Level1ChunkGenerator.HEIGHT - 1) return null;
		int dx = Math.floorDiv(x, D), dz = Math.floorDiv(z, D);
		Poi p = poi(seed, dx, dz);
		if (p == null) return null;

		int lx = Math.floorMod(x, D), lz = Math.floorMod(z, D);
		if (lx < p.minLx() || lx > p.maxLx() || lz < p.minLz() || lz > p.maxLz()) return null;

		int u = lx - p.minLx();
		int v = lz - p.minLz();
		return switch (p.kind()) {
			case SKYWALK_WATCHPOST -> skywalkWatchpost(u, v, y);
			case B1_GARAGE_CHECKPOINT -> b1Checkpoint(u, v, y);
			case BASIN_PUMP_STATION -> basinPumpStation(p, u, v, y);
			case TUNNEL_SERVICE_CACHE -> p.deep() ? deepSumpVault(u, v, y) : sublevel1Alcove(u, v, y);
			case NONE -> null;
		};
	}

	private static BlockState skywalkWatchpost(int u, int v, int y) {
		if (y < 40 || y > 45) return null;
		if (y == 40) return state(Level1Blocks.CORRIDOR_FLOOR);
		if (y == 45) return v <= 6 ? state(Level1Blocks.TARP) : null;

		if ((u == 0 || u == 5) && (v == 0 || v == 6)) {
			return state(Level1Blocks.SCAFFOLD_POLE);
		}
		if (y == 44 && v <= 6) {
			if ((u == 1 || u == 4) && v == 3) return state(Level1Blocks.CAGED_LAMP);
			if (u == 2 && v == 1) return brokenLightX();
		}
		if (y == 41) {
			if ((u == 1 || u == 2) && v == 0) return facing(Level1Blocks.OFFICE_DESK, Direction.SOUTH);
			if (u == 0 && v == 2) return facing(BackroomsProps.INDUSTRIAL_FUSE_BOX, Direction.EAST);
			if (u == 4 && v == 0) return facing(BackroomsProps.LOCKED_ARMORY_CRATE, Direction.SOUTH);
			if (u == 5 && v == 1) return state(Level1Blocks.SUPPLY_CRATE);
			if (u == 2 && v == 1) return facing(BackroomsProps.PROP_OFFICE_CHAIR, Direction.NORTH);
			if (u == 5 && v == 4) return facing(BackroomsProps.WANDERER_REMAINS, Direction.WEST);
			if (u == 0 && v == 4) return state(Level1Blocks.FUEL_DRUM);
			if (u == 5 && v == 7) return state(Level1Blocks.FLOODLIGHT);
			if (u == 0 && v == 7) return state(Level1Blocks.CONSTRUCTION_CONE);
		}
		if (y == 42) {
			if (u == 2 && v == 0) return facing(Level1Blocks.OLD_COMPUTER, Direction.SOUTH);
			if (u == 1 && v == 0) return state(Level1Blocks.DESK_PLANT_DEAD);
		}
		return air();
	}

	private static BlockState b1Checkpoint(int u, int v, int y) {
		if (y < 24 || y > 30) return null;
		if (y == 24) {
			return state((u == 0 || u == 9) ? Level1Blocks.GARAGE_FLOOR_LINE : Level1Blocks.CORRIDOR_FLOOR);
		}
		if (u == 5 && v == 1) {
			return state(Level1Blocks.GARAGE_WALL);
		}
		if (y == 25) {
			if ((v == 0 || v == 8) && (u <= 2 || u >= 7)) return barrierX();
			if ((v == 0 || v == 8) && (u == 3 || u == 6)) return state(Level1Blocks.CONSTRUCTION_CONE);
			if (u == 2 && v == 4) return facing(Level1Blocks.ABANDONED_VAN, Direction.EAST);
			if (u == 5 && v == 2) return facing(BackroomsProps.INDUSTRIAL_FUSE_BOX, Direction.SOUTH);
			if (u == 8 && v == 2) return facing(Level1Blocks.PALLET_STACK, Direction.WEST);
			if (u == 8 && v == 3) return state(Level1Blocks.FUEL_DRUM);
			if (u == 8 && v == 5) return facing(BackroomsProps.SALVAGE_BENCH, Direction.WEST);
			if (u == 8 && v == 6) return facing(BackroomsProps.LOCKED_ARMORY_CRATE, Direction.WEST);
			if (u == 7 && v == 7) return state(Level1Blocks.SUPPLY_CRATE);
			if (u == 2 && v == 7) return facing(BackroomsProps.WANDERER_REMAINS, Direction.NORTH);
			if (u == 1 && v == 6) return state(Level1Blocks.DEBRIS_PILE);
			if (u == 1 && v == 2) return state(Level1Blocks.FLOODLIGHT);
		}
		if (y == 30) {
			if ((u == 3 || u == 7) && v == 4) return state(Level1Blocks.CAGED_LAMP);
			if (u == 5 && v == 4) return brokenLightX();
		}
		return air();
	}

	private static BlockState basinPumpStation(Poi p, int u, int v, int y) {
		int platY = p.floorY();
		int basinY = platY - 1;
		int canopyY = basinY + 6;
		if (y < basinY || y > canopyY) return null;

		boolean westSide = p.minLx() == 17;
		boolean outerRim = u == 0 || u == 8 || v == 0 || v == 8;

		if (y == basinY) return state(Level1Blocks.DAMP_GARAGE_FLOOR);
		if (y == platY) {
			if (!outerRim) return state(Level1Blocks.CORRIDOR_FLOOR);
			boolean onStairEdge = westSide ? u == 8 : u == 0;
			if (onStairEdge && v >= 3 && v <= 5) {
				return Level1Blocks.CONCRETE_STAIRS.defaultBlockState()
						.setValue(StairBlock.FACING, westSide ? Direction.WEST : Direction.EAST);
			}
			return air();
		}
		if (y == canopyY) {
			return outerRim ? null : state(Level1Blocks.GARAGE_CEILING);
		}
		if (outerRim) return air();

		if ((u == 1 || u == 7) && (v == 1 || v == 7)) {
			return state(Level1Blocks.COPPER_PILLAR);
		}
		if (y == canopyY - 1) {
			if (u >= 2 && u <= 6 && v == 2) {
				return Level1Blocks.CEILING_PIPE.defaultBlockState().setValue(PipeBlock.AXIS, Direction.Axis.X);
			}
			if ((u == 2 || u == 6) && v == 4) return state(Level1Blocks.CAGED_LAMP);
			if (u == 4 && v == 4) return brokenLightX();
		}
		if (y == platY + 1) {
			if (u == 2 && v == 1) return facing(Level1Blocks.ELECTRICAL_CABINET, Direction.SOUTH);
			if (u == 4 && v == 1) return facing(BackroomsProps.INDUSTRIAL_FUSE_BOX, Direction.SOUTH);
			if (u == 6 && v == 1) return state(Level1Blocks.FUEL_DRUM);
			if (u == 2 && v == 7) return facing(BackroomsProps.ALMOND_DISTILLER, Direction.NORTH);
			if (u == 4 && v == 4) {
				Block chest = p.deep() ? BackroomsProps.ANOMALY_VAULT_CHEST : BackroomsProps.LOCKED_ARMORY_CRATE;
				return facing(chest, westSide ? Direction.EAST : Direction.WEST);
			}
			if (u == 6 && v == 6) return state(Level1Blocks.SUPPLY_CRATE);
			if (u == 5 && v == 7) return facing(BackroomsProps.WANDERER_REMAINS, Direction.NORTH);
			if (u == 3 && v == 7) return facing(BackroomsProps.PROP_CRT_TV, Direction.NORTH);
		}
		return air();
	}

	private static BlockState deepSumpVault(int u, int v, int y) {
		if (y < 14 || y > 19) return null;
		if (y == 14) return state(Level1Blocks.CORRIDOR_FLOOR);
		if (y == 19) return state(Level1Blocks.GARAGE_CEILING);

		boolean perimeter = u == 0 || u == 6 || v == 0 || v == 6;
		if (perimeter) {
			if ((u == 0 || u == 6) && (v == 0 || v == 6)) return state(Level1Blocks.COPPER_PILLAR);
			if (u == 6 && v >= 2 && v <= 4 && y <= 17) return air();
			return state(Level1Blocks.GARAGE_WALL);
		}
		if (y == 18) {
			if ((u == 2 && v == 2) || (u == 4 && v == 4)) return state(Level1Blocks.CAGED_LAMP);
			if (u == 3 && v == 3) return brokenLightX();
		}
		if (y == 15) {
			if (u == 1 && v == 3) return facing(BackroomsProps.ANOMALY_VAULT_CHEST, Direction.EAST);
			if (u == 1 && v == 1) return facing(BackroomsProps.WORKSHOP_BENCH, Direction.EAST);
			if (u == 3 && v == 1) return facing(BackroomsProps.INDUSTRIAL_FUSE_BOX, Direction.SOUTH);
			if (u == 5 && v == 1) return state(Level1Blocks.FUEL_DRUM);
			if (u == 1 && v == 5) return facing(Level1Blocks.GENERATOR, Direction.EAST);
			if (u == 3 && v == 5) return state(Level1Blocks.SUPPLY_CRATE);
			if (u == 5 && v == 5) return facing(BackroomsProps.WANDERER_REMAINS, Direction.NORTH);
		}
		return air();
	}

	private static BlockState sublevel1Alcove(int u, int v, int y) {
		if (y < 22 || y > 26) return null;
		if (y == 22) return state(Level1Blocks.CORRIDOR_FLOOR);
		if (y == 26) return state(Level1Blocks.GARAGE_CEILING);

		boolean perimeter = u == 0 || u == 6 || v == 0 || v == 6;
		if (perimeter) {
			if (v == 6 && u >= 2 && u <= 4 && y <= 24) return air();
			return state(Level1Blocks.GARAGE_WALL);
		}
		if (y == 25) {
			if ((u == 2 && v == 2) || (u == 4 && v == 4)) return state(Level1Blocks.CAGED_LAMP);
			if (u == 3 && v == 3) return brokenLightX();
		}
		if (y == 23) {
			if ((u == 1 || u == 2) && v == 1) return facing(Level1Blocks.OFFICE_DESK, Direction.SOUTH);
			if (u == 2 && v == 2) return facing(BackroomsProps.PROP_OFFICE_CHAIR, Direction.NORTH);
			if (u == 4 && v == 1) return state(Level1Blocks.FUEL_DRUM);
			if (u == 5 && v == 1) return facing(Level1Blocks.ELECTRICAL_CABINET, Direction.SOUTH);
			if (u == 5 && v == 3) return facing(BackroomsProps.INDUSTRIAL_FUSE_BOX, Direction.WEST);
			if (u == 1 && v == 4) return facing(BackroomsProps.LOCKED_ARMORY_CRATE, Direction.EAST);
			if (u == 1 && v == 5) return state(Level1Blocks.SUPPLY_CRATE);
			if (u == 5 && v == 5) return facing(BackroomsProps.WANDERER_REMAINS, Direction.WEST);
		}
		if (y == 24) {
			if (u == 2 && v == 1) return facing(Level1Blocks.OLD_COMPUTER, Direction.SOUTH);
			if (u == 1 && v == 1) return state(Level1Blocks.DESK_PLANT_DEAD);
		}
		return air();
	}

	private static BlockState brokenLightX() {
		return Level1Blocks.BROKEN_GARAGE_LIGHT.defaultBlockState().setValue(AxisPlacedBlock.AXIS, Direction.Axis.X);
	}

	private static BlockState barrierX() {
		return Level1Blocks.JERSEY_BARRIER.defaultBlockState().setValue(AxisShapedBlock.AXIS, Direction.Axis.X);
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
