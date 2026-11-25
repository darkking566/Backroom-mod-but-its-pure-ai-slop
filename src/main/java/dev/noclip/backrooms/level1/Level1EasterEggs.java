package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Elevation.Profile;
import dev.noclip.backrooms.level1.Level1Layout.Base;
import dev.noclip.backrooms.props.BackroomsProps;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Rare region-scoped foreshadowing and easter-egg POIs placed on the main floor (y = 32..36)
 * at most once per 4x4 district macro-region in a pillar-free, span-free pocket (lx 28..34, lz 51..57).
 */
public final class Level1EasterEggs {
	private static final int D = Level1Layout.DISTRICT;
	private static final int FLOOR = Level1ChunkGenerator.FLOOR_Y;
	private static final int REGION = 4;
	private static final int REGION_SALT = 191;
	private static final int KIND_SALT = 193;
	private static final int MIN_LX = 28;
	private static final int MAX_LX = 34;
	private static final int MIN_LZ = 51;
	private static final int MAX_LZ = 57;
	private static final ConcurrentHashMap<Key, Optional<Egg>> CACHE = new ConcurrentHashMap<>();

	public enum Kind {
		NONE,
		PARTYGOER_DOOR_EGG,
		ANOMALY_RIFT_ARCHIVE,
		VOID_RELAY_POST
	}

	public record Egg(
			Kind kind,
			int dx,
			int dz,
			int minLx,
			int maxLx,
			int minLz,
			int maxLz,
			int focalX,
			int focalY,
			int focalZ,
			int lootX,
			int lootY,
			int lootZ) {
	}

	private record Key(long seed, int dx, int dz) {
	}

	private Level1EasterEggs() {
	}

	/** Returns the rare easter-egg POI in district (dx, dz), or null if none spawns here. */
	public static Egg egg(long seed, int dx, int dz) {
		if (CACHE.size() > 1024) CACHE.clear();
		return CACHE.computeIfAbsent(new Key(seed, dx, dz), key -> Optional.ofNullable(compute(key.seed(), key.dx(), key.dz())))
				.orElse(null);
	}

	/** Whether (lx, lz) lies within the easter-egg's 7x7 footprint + 1-block reserved halo. */
	static boolean isReserved(long seed, int dx, int dz, int lx, int lz) {
		Egg e = egg(seed, dx, dz);
		return e != null && lx >= e.minLx() - 1 && lx <= e.maxLx() + 1 && lz >= e.minLz() - 1 && lz <= e.maxLz() + 1;
	}

	private static Egg compute(long seed, int dx, int dz) {
		int rx = Math.floorDiv(dx, REGION);
		int rz = Math.floorDiv(dz, REGION);
		int total = REGION * REGION;
		int start = Math.floorMod(Level1Layout.hash(seed, rx, rz, REGION_SALT), total);

		int chosenDx = Integer.MIN_VALUE;
		int chosenDz = Integer.MIN_VALUE;
		for (int i = 0; i < total; i++) {
			int idx = (start + i) % total;
			int cdx = rx * REGION + (idx % REGION);
			int cdz = rz * REGION + (idx / REGION);
			if (!isEligibleDistrict(seed, cdx, cdz)) continue;
			if (!candidateValid(seed, cdx, cdz, cdx * D, cdz * D, MIN_LX, MAX_LX, MIN_LZ, MAX_LZ)) continue;
			chosenDx = cdx;
			chosenDz = cdz;
			break;
		}
		if (chosenDx != dx || chosenDz != dz) return null;

		Kind kind = switch (Math.floorMod(Level1Layout.hash(seed, rx, rz, KIND_SALT), 3)) {
			case 0 -> Kind.PARTYGOER_DOOR_EGG;
			case 1 -> Kind.ANOMALY_RIFT_ARCHIVE;
			default -> Kind.VOID_RELAY_POST;
		};

		int bx = dx * D + MIN_LX;
		int bz = dz * D + MIN_LZ;
		return switch (kind) {
			case PARTYGOER_DOOR_EGG -> new Egg(kind, dx, dz, MIN_LX, MAX_LX, MIN_LZ, MAX_LZ,
					bx + 3, 33, bz + 6, bx + 5, 33, bz + 5);
			case ANOMALY_RIFT_ARCHIVE -> new Egg(kind, dx, dz, MIN_LX, MAX_LX, MIN_LZ, MAX_LZ,
					bx + 3, 33, bz + 4, bx + 1, 33, bz + 5);
			case VOID_RELAY_POST -> new Egg(kind, dx, dz, MIN_LX, MAX_LX, MIN_LZ, MAX_LZ,
					bx + 3, 33, bz + 6, bx + 5, 33, bz + 5);
			case NONE -> null;
		};
	}

	private static boolean isEligibleDistrict(long seed, int dx, int dz) {
		if (dx == 0 && dz == 0) return false;
		if (Level1Layout.kind(seed, dx, dz) == Level1Layout.Kind.CORRIDORS) return false;
		if (Level1Layout.base(seed, dx, dz) != Base.NONE) return false;
		if (Level1Landmarks.landmark(seed, dx, dz) != Level1Landmarks.Landmark.NONE) return false;
		return Level1Elevation.profile(seed, dx, dz) != Profile.SUNKEN_BASIN;
	}

	private static boolean candidateValid(long seed, int dx, int dz, int x0, int z0, int minLx, int maxLx, int minLz, int maxLz) {
		for (int lx = minLx - 1; lx <= maxLx + 1; lx++) {
			for (int lz = minLz - 1; lz <= maxLz + 1; lz++) {
				if (Level1Elevation.isNearExitOrProp(seed, dx, dz, x0 + lx, z0 + lz)) return false;
			}
		}
		double zone = Level1Layout.pipeZone(seed, x0 + minLx + 3, z0 + minLz + 3);
		return !(zone >= 0 && zone <= 0.15);
	}

	/**
	 * Generates the easter-egg block at (x, y, z) on y = 32..36, or null outside.
	 */
	static BlockState block(long seed, int x, int y, int z) {
		if (y < FLOOR || y > FLOOR + 4) return null;
		int dx = Math.floorDiv(x, D), dz = Math.floorDiv(z, D);
		Egg e = egg(seed, dx, dz);
		if (e == null) return null;

		int lx = Math.floorMod(x, D), lz = Math.floorMod(z, D);
		if (lx < e.minLx() || lx > e.maxLx() || lz < e.minLz() || lz > e.maxLz()) return null;

		int u = lx - e.minLx();
		int v = lz - e.minLz();
		return switch (e.kind()) {
			case PARTYGOER_DOOR_EGG -> partygoerDoorEgg(u, v, y);
			case ANOMALY_RIFT_ARCHIVE -> anomalyRiftArchive(u, v, y);
			case VOID_RELAY_POST -> voidRelayPost(u, v, y);
			case NONE -> null;
		};
	}

	private static BlockState partygoerDoorEgg(int u, int v, int y) {
		if (y == FLOOR) {
			if (u == 1 && v == 1) return Level1Blocks.NEON_MAGENTA.defaultBlockState();
			if (u == 5 && v == 1) return Level1Blocks.NEON_AMBER.defaultBlockState();
			return ((u + v) & 1) == 0
					? Level1Blocks.PAINTED_CONCRETE_WALL.defaultBlockState()
					: Level1Blocks.CORRIDOR_FLOOR.defaultBlockState();
		}
		if (y == FLOOR + 4) {
			return v >= 2 ? Level1Blocks.PAINTED_CONCRETE_WALL.defaultBlockState() : null;
		}
		if (isAlcoveWall(u, v)) {
			if (u == 3 && v == 6) {
				if (y == FLOOR + 1) return Shared.FUN_DOOR;
				if (y == FLOOR + 2) return Shared.FUN_DOOR_TOP;
				return Level1Blocks.NEON_AMBER.defaultBlockState();
			}
			if ((u == 2 && v == 6) || (u == 0 && v == 2)) {
				return Level1Blocks.NEON_MAGENTA.defaultBlockState().setValue(NeonBlock.UPRIGHT, true);
			}
			if ((u == 4 && v == 6) || (u == 6 && v == 2)) {
				return Level1Blocks.NEON_AMBER.defaultBlockState().setValue(NeonBlock.UPRIGHT, true);
			}
			return Level1Blocks.PAINTED_CONCRETE_WALL.defaultBlockState();
		}
		if (y == FLOOR + 3 && u == 3 && (v == 3 || v == 5)) {
			return Level1Blocks.GARAGE_LIGHT.defaultBlockState().setValue(AxisPlacedBlock.AXIS, Direction.Axis.X);
		}
		if (u == 1 && v == 5) {
			if (y == FLOOR + 1) return Level1Blocks.OFFICE_DESK.defaultBlockState().setValue(OldComputerBlock.FACING, Direction.EAST);
			if (y == FLOOR + 2) return BackroomsProps.PROP_CRT_TV.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST);
		}
		if (y == FLOOR + 1) {
			if (u == 5 && v == 5) return facing(BackroomsProps.ANOMALY_VAULT_CHEST, Direction.WEST);
			if (u == 1 && v == 3) return Level1Blocks.SUPPLY_CRATE.defaultBlockState();
			if (u == 5 && v == 3) return facing(BackroomsProps.WANDERER_REMAINS, Direction.NORTH);
			if ((u == 2 || u == 4) && v == 1) return Level1Blocks.CONSTRUCTION_CONE.defaultBlockState();
		}
		return air();
	}

	private static BlockState anomalyRiftArchive(int u, int v, int y) {
		if (y == FLOOR) {
			if (u >= 2 && u <= 4 && v >= 3 && v <= 5) return Shared.VOID_STONE;
			if (u == 3 && v == 2) return facing(BackroomsProps.GLITCHED_WALL_SEAM, Direction.NORTH);
			return Level1Blocks.GARAGE_FLOOR.defaultBlockState();
		}
		if (y == FLOOR + 4) {
			return v >= 2 ? Level1Blocks.GARAGE_CEILING.defaultBlockState() : null;
		}
		if (isAlcoveWall(u, v)) {
			if (u == 3 && v == 6) {
				return y <= FLOOR + 2 ? Shared.VOID_STONE : facing(BackroomsProps.SEALED_VAULT_WALL, Direction.NORTH);
			}
			if ((u == 2 || u == 4) && v == 6) {
				return facing(BackroomsProps.GLITCHED_WALL_SEAM, Direction.NORTH);
			}
			if (v == 6 || (v == 2 && (u == 0 || u == 6))) {
				return facing(BackroomsProps.SEALED_VAULT_WALL, Direction.NORTH);
			}
			return Level1Blocks.GARAGE_WALL.defaultBlockState();
		}
		if (y == FLOOR + 3 && v == 3 && (u == 2 || u == 4)) {
			return Level1Blocks.CAGED_LAMP.defaultBlockState();
		}
		if (u == 5 && v == 5) {
			if (y == FLOOR + 1) return Level1Blocks.OFFICE_DESK.defaultBlockState().setValue(OldComputerBlock.FACING, Direction.WEST);
			if (y == FLOOR + 2) return Level1Blocks.OLD_COMPUTER.defaultBlockState().setValue(OldComputerBlock.FACING, Direction.WEST);
		}
		if (y == FLOOR + 1) {
			if (u == 3 && v == 5) return facing(BackroomsProps.CRYSTAL_VEIN_NODE, Direction.NORTH);
			if (u == 3 && v == 4) return facing(BackroomsProps.ANOMALY_ALTAR, Direction.NORTH);
			if (u == 1 && v == 5) return facing(BackroomsProps.ANOMALY_VAULT_CHEST, Direction.EAST);
			if (u == 5 && v == 3) return facing(BackroomsProps.INDUSTRIAL_FUSE_BOX, Direction.WEST);
			if (u == 1 && v == 3) return facing(BackroomsProps.WANDERER_REMAINS, Direction.EAST);
			if ((u == 1 || u == 5) && v == 1) return Level1Blocks.JERSEY_BARRIER.defaultBlockState().setValue(AxisShapedBlock.AXIS, Direction.Axis.X);
		}
		return air();
	}

	private static BlockState voidRelayPost(int u, int v, int y) {
		if (y == FLOOR) {
			if (u >= 2 && u <= 4 && v >= 1 && v <= 5) return Shared.VOID_STONE;
			return Level1Blocks.DAMP_GARAGE_FLOOR.defaultBlockState();
		}
		if (y == FLOOR + 4) {
			return v >= 2 ? Level1Blocks.GARAGE_CEILING.defaultBlockState() : null;
		}
		if (isAlcoveWall(u, v)) {
			if (u == 3 && v == 6) {
				if (y == FLOOR + 1) return Shared.HUB_DOOR;
				if (y == FLOOR + 2) return Shared.HUB_DOOR_TOP;
				return Level1Blocks.NEON_CYAN.defaultBlockState();
			}
			if ((u == 2 || u == 4) && v == 6) {
				return y <= FLOOR + 2
						? Level1Blocks.NEON_CYAN.defaultBlockState().setValue(NeonBlock.UPRIGHT, true)
						: facing(BackroomsProps.SEALED_VAULT_WALL, Direction.NORTH);
			}
			if (v == 6) {
				return facing(BackroomsProps.SEALED_VAULT_WALL, Direction.NORTH);
			}
			if ((u == 0 || u == 6) && v == 2 && y == FLOOR + 2) {
				return Level1Blocks.NEON_CYAN.defaultBlockState().setValue(NeonBlock.UPRIGHT, true);
			}
			return Level1Blocks.GARAGE_WALL.defaultBlockState();
		}
		if (y == FLOOR + 3 && v == 3 && (u == 2 || u == 4)) {
			return Level1Blocks.CAGED_LAMP.defaultBlockState();
		}
		if (y == FLOOR + 1) {
			if (u == 1 && v == 5) return facing(BackroomsProps.RELAY_DROPOFF_BOX, Direction.EAST);
			if (u == 5 && v == 5) return facing(BackroomsProps.LOCKED_ARMORY_CRATE, Direction.WEST);
			if (u == 1 && v == 3) return facing(BackroomsProps.RED_ANOMALY_CLUSTER, Direction.EAST);
			if (u == 5 && v == 3) return facing(BackroomsProps.WANDERER_REMAINS, Direction.WEST);
			if ((u == 1 || u == 5) && v == 1) return Level1Blocks.FUEL_DRUM.defaultBlockState();
		}
		return air();
	}

	private static boolean isAlcoveWall(int u, int v) {
		return v == 6 || (v >= 2 && (u == 0 || u == 6));
	}

	private static BlockState facing(Block block, Direction dir) {
		return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, dir);
	}

	private static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}

	/** Lazy lookup for cross-package blocks so pure unit tests never touch registries. */
	private static final class Shared {
		static final BlockState FUN_DOOR = BuiltInRegistries.BLOCK.getValue(Level1.id("fun_door"))
				.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.Z);
		static final BlockState FUN_DOOR_TOP = BuiltInRegistries.BLOCK.getValue(Level1.id("fun_door_top"))
				.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.Z);
		static final BlockState HUB_DOOR = BuiltInRegistries.BLOCK.getValue(Level1.id("hub_door"))
				.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.Z);
		static final BlockState HUB_DOOR_TOP = BuiltInRegistries.BLOCK.getValue(Level1.id("hub_door_top"))
				.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.Z);
		static final BlockState VOID_STONE = BuiltInRegistries.BLOCK.getValue(Level1.id("void_stone"))
				.defaultBlockState();
	}
}
