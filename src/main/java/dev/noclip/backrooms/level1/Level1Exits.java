package dev.noclip.backrooms.level1;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The blocks of the exits in the halls (HallPlan has where they are). Each method gives the block at one
 * height of a footprint column, or null where the ordinary hall shows through.
 */
final class Level1Exits {
	private static final int FLOOR = Level1ChunkGenerator.FLOOR_Y, CEILING = Level1ChunkGenerator.CEILING_Y;
	/** The roof over the landing at the top of the stairs up, above the ceiling. */
	private static final int ROOF = CEILING + 3;

	private Level1Exits() {
	}

	static BlockState block(HallPlan.Cell cell, int x, int y, int z) {
		HallPlan.Placed placed = cell.placed();
		return switch (placed.exit().shape) {
			case CORE_DOOR, CORE_PAINTING, CORE_OUTLETS -> core(placed, x, y, z);
			case PILLAR -> y >= FLOOR + 1 && y <= FLOOR + 3 ? state(Level1Blocks.CRACKED_PILLAR) : null;
			case HATCH -> y == FLOOR ? state(Level1Blocks.CELLAR_HATCH) : null;
			case FRAME -> y == FLOOR + 1 ? state(Level1Blocks.DOOR_FRAME) : y == FLOOR + 2 ? state(Level1Blocks.DOOR_FRAME_TOP) : null;
			case STAIRS_DOWN -> stairsDown(cell.bx() - 3, cell.bz() - 2, y);
			case STAIRS_UP -> stairsUp(cell.bx() - 3, cell.bz() - 2, y);
		};
	}

	/** A concrete service room, floor to ceiling, with the exit set into the middle of one face. */
	private static BlockState core(HallPlan.Placed placed, int x, int y, int z) {
		if (y == FLOOR) return state(Level1Blocks.GARAGE_FLOOR);
		if (y <= FLOOR || y >= CEILING) return null;
		int[] spot = placed.spot();
		if (x == spot[0] && z == spot[1]) {
			// North and south faces run along X.
			Direction.Axis axis = placed.face() % 2 == 0 ? Direction.Axis.X : Direction.Axis.Z;
			BlockState exit = switch (placed.exit()) {
				case WHITE_DOOR -> door(Level1Blocks.WHITE_DOOR, Level1Blocks.WHITE_DOOR_TOP, y, axis);
				case REINFORCED_DOOR -> door(Level1Blocks.REINFORCED_DOOR, Level1Blocks.REINFORCED_DOOR_TOP, y, axis);
				case AUTOMATIC_DOOR -> door(Level1Blocks.AUTOMATIC_DOOR, Level1Blocks.AUTOMATIC_DOOR_TOP, y, axis);
				case PICKAXE_DOOR -> door(Level1Blocks.PICKAXE_DOOR, Level1Blocks.PICKAXE_DOOR_TOP, y, axis);
				case PAINTING -> y == FLOOR + 2 ? state(Level1Blocks.PALACE_PAINTING) : null;
				case OUTLETS -> y == FLOOR + 1 ? state(Level1Blocks.OUTLET_WALL) : null;
				default -> null;
			};
			if (exit != null) return exit;
		}
		return state(Level1Blocks.GARAGE_WALL);
	}

	private static BlockState door(Block bottom, Block top, int y, Direction.Axis axis) {
		if (y == FLOOR + 1) return bottom.defaultBlockState().setValue(AxisExitBlock.AXIS, axis);
		if (y == FLOOR + 2) return top.defaultBlockState().setValue(AxisExitBlock.AXIS, axis);
		return null;
	}

	/**
	 * Down along +Z: four steps from the floor through a hole, then a low passage under the floor that ends in darkness.
	 * u, v are offsets in the footprint (4 x 9); the shaft is u = 1..2, v = 1..7, walled all round below the floor.
	 */
	private static BlockState stairsDown(int u, int v, int y) {
		if (y > FLOOR || y < FLOOR - 5) return null;
		boolean shaft = u >= 1 && u <= 2 && v >= 1 && v <= 7;
		if (!shaft) return y == FLOOR ? null : state(Level1Blocks.GARAGE_WALL);
		if (v <= 4) {
			int step = FLOOR + 1 - v;
			if (y == step) return stairs(Direction.NORTH);
			return y > step ? air() : null;
		}
		if (y == FLOOR) return null; // the hall floor roofs the passage
		if (y == FLOOR - 4) return state(Level1Blocks.CORRIDOR_FLOOR);
		if (y < FLOOR - 4) return null;
		return v == 7 && y <= FLOOR - 2 ? state(Level1Blocks.DARKNESS_BELOW) : air();
	}

	/**
	 * Up along +Z: five steps on a solid base rise through a hole in the ceiling to a landing above it,
	 * which ends in darkness. Walled all round above the ceiling.
	 */
	private static BlockState stairsUp(int u, int v, int y) {
		boolean shaft = u >= 1 && u <= 2 && v >= 1 && v <= 7;
		if (!shaft) {
			if (y > CEILING && y <= ROOF) return state(Level1Blocks.GARAGE_WALL);
			return null;
		}
		if (y <= FLOOR || y > ROOF) return null;
		if (y == ROOF) return state(Level1Blocks.GARAGE_CEILING);
		if (v <= 5) {
			int step = FLOOR + v;
			if (y < step) return state(Level1Blocks.GARAGE_WALL);
			if (y == step) return stairs(Direction.SOUTH);
			// Open overhead from the third step on, so a player can climb through.
			if (y >= CEILING && v < 3) return state(y == CEILING ? Level1Blocks.GARAGE_CEILING : Level1Blocks.GARAGE_WALL);
			return air();
		}
		if (y < CEILING - 1) return state(Level1Blocks.GARAGE_WALL);
		if (y == CEILING - 1) return state(Level1Blocks.CORRIDOR_FLOOR);
		return v == 7 && y <= CEILING + 1 ? state(Level1Blocks.DARKNESS_ABOVE) : air();
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
