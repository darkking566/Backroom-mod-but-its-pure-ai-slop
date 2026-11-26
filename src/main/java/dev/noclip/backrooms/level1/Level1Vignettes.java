package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Elevation.Profile;
import dev.noclip.backrooms.level1.Level1Layout.Base;
import dev.noclip.backrooms.props.BackroomsProps;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sparse micro-vignettes for Level 1 corridors (dead-end cells) and standard/elevated halls.
 * Corridor vignettes occupy 3x3 dead-end interiors (y = 32..35); hall vignettes occupy a
 * pillar-free 5x5 bay (lx 29..33, lz 5..9, y = 32..35) clear of Batch 1 & Batch 2 POIs.
 */
public final class Level1Vignettes {
	private static final int D = Level1Layout.DISTRICT;
	private static final int CELL = Level1Layout.CELL;
	private static final int FLOOR = Level1ChunkGenerator.FLOOR_Y;
	private static final int CORRIDOR_CHANCE = 65;
	private static final int HALL_CHANCE = 50;
	private static final int SALT = 173;
	private static final int PICK_SALT = 179;
	private static final ConcurrentHashMap<Key, Optional<Vignette>> CACHE = new ConcurrentHashMap<>();

	public enum Kind {
		NONE,
		CORRIDOR_SECURITY_NOOK,
		CORRIDOR_SHELTER_CACHE,
		CORRIDOR_VENDING_ALCOVE,
		HALL_BREAKDOWN_SPOT,
		HALL_VENDING_BREAK_CORNER
	}

	public record Vignette(
			Kind kind,
			int dx,
			int dz,
			int minLx,
			int maxLx,
			int minLz,
			int maxLz,
			Direction openDir,
			int keyX,
			int keyY,
			int keyZ) {
	}

	private record Key(long seed, int dx, int dz) {
	}

	private Level1Vignettes() {
	}

	/** Returns the micro-vignette in district (dx, dz), or null if none spawns here. */
	public static Vignette vignette(long seed, int dx, int dz) {
		if (CACHE.size() > 1024) CACHE.clear();
		return CACHE.computeIfAbsent(new Key(seed, dx, dz), key -> Optional.ofNullable(compute(key.seed(), key.dx(), key.dz())))
				.orElse(null);
	}

	/** Reserves the 5x5 hall vignette + 1-block halo from Level1SectorArchitecture overrides. */
	static boolean isReserved(long seed, int dx, int dz, int lx, int lz) {
		Vignette v = vignette(seed, dx, dz);
		if (v == null || (v.kind() != Kind.HALL_BREAKDOWN_SPOT && v.kind() != Kind.HALL_VENDING_BREAK_CORNER)) {
			return false;
		}
		return lx >= v.minLx() - 1 && lx <= v.maxLx() + 1 && lz >= v.minLz() - 1 && lz <= v.maxLz() + 1;
	}

	private static Vignette compute(long seed, int dx, int dz) {
		if (Level1Layout.kind(seed, dx, dz) == Level1Layout.Kind.CORRIDORS) {
			return computeCorridor(seed, dx, dz);
		}
		return computeHall(seed, dx, dz);
	}

	private static Vignette computeCorridor(long seed, int dx, int dz) {
		if (Math.floorMod(Level1Layout.hash(seed, dx, dz, SALT), 100) >= CORRIDOR_CHANCE) return null;
		CorridorPlan plan = CorridorPlan.of(seed, dx, dz);
		int start = Math.floorMod(Level1Layout.hash(seed, dx, dz, PICK_SALT + 3), 144);
		int x0 = dx * D, z0 = dz * D;

		for (int k = 0; k < 144; k++) {
			int idx = (start + k) % 144;
			int ci = 2 + idx % 12;
			int cj = 2 + idx / 12;
			int midLx = ci * CELL + 2, midLz = cj * CELL + 2;
			if (plan.roomAt(midLx, midLz) != null) continue;
			int mask = plan.openMask(ci, cj);
			if (Integer.bitCount(mask) != 1) continue;
			if (Math.abs(midLx - plan.doorX()) <= 6 && Math.abs(midLz - plan.doorZ()) <= 6) continue;
			if (Level1Layout.glitchZone(seed, x0 + midLx, z0 + midLz) >= 0) continue;

			Direction openDir = switch (mask) {
				case 1 -> Direction.NORTH;
				case 2 -> Direction.EAST;
				case 4 -> Direction.SOUTH;
				default -> Direction.WEST;
			};
			int pick = Math.floorMod(Level1Layout.hash(seed, dx, dz, PICK_SALT), 3);
			Kind kind = switch (pick) {
				case 0 -> Kind.CORRIDOR_SECURITY_NOOK;
				case 1 -> Kind.CORRIDOR_SHELTER_CACHE;
				default -> Kind.CORRIDOR_VENDING_ALCOVE;
			};

			int minLx = ci * CELL + 1, maxLx = ci * CELL + 3;
			int minLz = cj * CELL + 1, maxLz = cj * CELL + 3;
			int cx = x0 + minLx + 1, cz = z0 + minLz + 1;
			Direction back = openDir.getOpposite();
			Direction left = back.getCounterClockWise();

			int keyX = cx + back.getStepX() + (kind == Kind.CORRIDOR_SECURITY_NOOK ? left.getStepX() : 0);
			int keyZ = cz + back.getStepZ() + (kind == Kind.CORRIDOR_SECURITY_NOOK ? left.getStepZ() : 0);
			return new Vignette(kind, dx, dz, minLx, maxLx, minLz, maxLz, openDir, keyX, FLOOR + 1, keyZ);
		}
		return null;
	}

	private static Vignette computeHall(long seed, int dx, int dz) {
		if (dx == 0 && dz == 0) return null;
		if (Level1Layout.base(seed, dx, dz) != Base.NONE) return null;
		if (Level1Landmarks.landmark(seed, dx, dz) != Level1Landmarks.Landmark.NONE) return null;
		if (Level1Elevation.profile(seed, dx, dz) == Profile.SUNKEN_BASIN) return null;
		if (Math.floorMod(Level1Layout.hash(seed, dx, dz, SALT), 100) >= HALL_CHANCE) return null;

		int minLx = 29, maxLx = 33, minLz = 5, maxLz = 9;
		if (anyNearFeature(seed, dx, dz, minLx - 1, maxLx + 1, minLz - 1, maxLz + 1)) return null;
		int x0 = dx * D, z0 = dz * D;
		double pipe = Level1Layout.pipeZone(seed, x0 + minLx + 2, z0 + minLz + 2);
		if (pipe >= 0 && pipe <= 0.15) return null;

		boolean breakdown = Math.floorMod(Level1Layout.hash(seed, dx, dz, PICK_SALT), 2) == 0;
		Kind kind = breakdown ? Kind.HALL_BREAKDOWN_SPOT : Kind.HALL_VENDING_BREAK_CORNER;
		int keyX = x0 + 31;
		int keyZ = breakdown ? z0 + 7 : z0 + 8;
		return new Vignette(kind, dx, dz, minLx, maxLx, minLz, maxLz, Direction.SOUTH, keyX, FLOOR + 1, keyZ);
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

	/** Generates the micro-vignette block at (x, y, z) on y = 32..35, or null outside. */
	static BlockState block(long seed, int x, int y, int z) {
		if (y < FLOOR || y > FLOOR + 3) return null;
		int dx = Math.floorDiv(x, D), dz = Math.floorDiv(z, D);
		Vignette v = vignette(seed, dx, dz);
		if (v == null) return null;

		int lx = Math.floorMod(x, D), lz = Math.floorMod(z, D);
		if (lx < v.minLx() || lx > v.maxLx() || lz < v.minLz() || lz > v.maxLz()) return null;

		return switch (v.kind()) {
			case CORRIDOR_SECURITY_NOOK, CORRIDOR_SHELTER_CACHE, CORRIDOR_VENDING_ALCOVE ->
					corridorBlock(v, lx, lz, y);
			case HALL_BREAKDOWN_SPOT -> hallBreakdown(dx, dz, lx - v.minLx(), lz - v.minLz(), y);
			case HALL_VENDING_BREAK_CORNER -> hallVendingCorner(lx - v.minLx(), lz - v.minLz(), y);
			case NONE -> null;
		};
	}

	private static BlockState corridorBlock(Vignette v, int lx, int lz, int y) {
		int cx = v.minLx() + 1, cz = v.minLz() + 1;
		int rx = lx - cx, rz = lz - cz;
		Direction open = v.openDir();
		Direction back = open.getOpposite();
		Direction right = back.getClockWise();
		Direction left = back.getCounterClockWise();

		boolean isCenter = rx == 0 && rz == 0;
		boolean isBack = rx == back.getStepX() && rz == back.getStepZ();
		boolean isBackLeft = rx == back.getStepX() + left.getStepX() && rz == back.getStepZ() + left.getStepZ();
		boolean isBackRight = rx == back.getStepX() + right.getStepX() && rz == back.getStepZ() + right.getStepZ();
		boolean isLeft = rx == left.getStepX() && rz == left.getStepZ();
		boolean isRight = rx == right.getStepX() && rz == right.getStepZ();

		return switch (v.kind()) {
			case CORRIDOR_SECURITY_NOOK -> {
				if (y == 32) yield null;
				if (y == 35) yield isCenter ? state(Level1Blocks.BROKEN_BARE_BULB) : air();
				if (y == 33) {
					if (isBack) yield facing(Level1Blocks.OFFICE_DESK, open);
					if (isBackLeft) yield facing(BackroomsProps.INDUSTRIAL_FUSE_BOX, open);
					if (isBackRight) yield state(Level1Blocks.SUPPLY_CRATE);
					if (isLeft) yield facing(BackroomsProps.PROP_OFFICE_CHAIR, back);
					if (isRight) yield state(Level1Blocks.WASTEBASKET);
				}
				if (y == 34 && isBack) yield facing(Level1Blocks.OLD_COMPUTER, open);
				yield air();
			}
			case CORRIDOR_SHELTER_CACHE -> {
				if (y == 32) yield state(Level1Blocks.DAMP_GARAGE_FLOOR);
				if (y == 35) yield isCenter ? state(Level1Blocks.CAGED_LAMP) : air();
				if (y == 33) {
					if (isBack) yield facing(BackroomsProps.LOCKED_ARMORY_CRATE, open);
					if (isBackLeft) yield state(Level1Blocks.SUPPLY_CRATE);
					if (isBackRight) yield facing(BackroomsProps.WANDERER_REMAINS, open);
					if (isLeft) yield facing(BackroomsProps.PROP_WOOD_CHAIR, right);
					if (isRight) yield state(Level1Blocks.DEBRIS_PILE);
				}
				yield air();
			}
			case CORRIDOR_VENDING_ALCOVE -> {
				if (y == 32) yield null;
				if (y == 35) yield isCenter ? Level1Blocks.BARE_BULB.defaultBlockState().setValue(Level1LampBlock.LIT, true) : air();
				if (y == 33) {
					if (isBack) yield facing(Level1Blocks.VENDING_MACHINE, open);
					if (isBackLeft) yield facing(Level1Blocks.OFFICE_DESK, open);
					if (isBackRight) yield state(Level1Blocks.WASTEBASKET);
					if (isRight) yield state(Level1Blocks.SUPPLY_CRATE);
				}
				if (y == 34 && isBackLeft) yield facing(BackroomsProps.PROP_CRT_TV, open);
				yield air();
			}
			default -> null;
		};
	}

	private static BlockState hallBreakdown(int dx, int dz, int u, int v, int y) {
		if (y == 32) {
			if (u == 0 || u == 4) return state(Level1Blocks.GARAGE_FLOOR_LINE);
			if (u >= 1 && u <= 3 && v >= 1 && v <= 3) return state(Level1Blocks.DAMP_GARAGE_FLOOR);
			return state(Level1Blocks.GARAGE_FLOOR);
		}
		if (y == 33) {
			if (u == 2 && v == 2) {
				Block vehicle = ((dx + dz) & 1) == 0 ? Level1Blocks.ABANDONED_CAR : Level1Blocks.ABANDONED_VAN;
				return facing(vehicle, Direction.EAST);
			}
			if ((u == 0 && v == 1) || (u == 4 && v == 3)) return state(Level1Blocks.CONSTRUCTION_CONE);
			if (u == 4 && v == 1) return state(Level1Blocks.FUEL_DRUM);
			if (u == 0 && v == 3) return state(Level1Blocks.DEBRIS_PILE);
			if (u == 2 && v == 4) return facing(BackroomsProps.WANDERER_REMAINS, Direction.NORTH);
			if (u == 3 && v == 4) return state(Level1Blocks.SUPPLY_CRATE);
			if (u == 1 && v == 4) return state(Level1Blocks.FLOODLIGHT);
		}
		return air();
	}

	private static BlockState hallVendingCorner(int u, int v, int y) {
		boolean inner = u >= 1 && u <= 3 && v >= 1 && v <= 3;
		if (y == 32) return state(inner ? Level1Blocks.CORRIDOR_FLOOR : Level1Blocks.GARAGE_FLOOR);
		if (u == 4 && v == 3 && y >= 33 && y <= 35) return state(Level1Blocks.GARAGE_WALL);
		if (y == 33) {
			if (u == 2 && v == 3) return facing(Level1Blocks.VENDING_MACHINE, Direction.NORTH);
			if (u == 1 && v == 3) return facing(Level1Blocks.OFFICE_DESK, Direction.NORTH);
			if (u == 3 && v == 3) return state(Level1Blocks.WASTEBASKET);
			if (u == 2 && v == 1) return facing(BackroomsProps.PROP_WOOD_CHAIR, Direction.SOUTH);
			if (u == 4 && v == 2) return facing(BackroomsProps.INDUSTRIAL_FUSE_BOX, Direction.WEST);
			if (u == 0 && v == 2) return state(Level1Blocks.SUPPLY_CRATE);
		}
		if (y == 34 && u == 1 && v == 3) return facing(BackroomsProps.PROP_CRT_TV, Direction.NORTH);
		return air();
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
