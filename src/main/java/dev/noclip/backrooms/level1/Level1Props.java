package dev.noclip.backrooms.level1;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The blocks of the props in the halls (HallPlan has where they are); null where the ordinary hall shows through. */
final class Level1Props {
	private static final int FLOOR = Level1ChunkGenerator.FLOOR_Y;
	/** Booths and generator rooms have their own lower roof. */
	private static final int BOOTH_ROOF = FLOOR + 3, ROOM_CEILING = FLOOR + 5;

	private Level1Props() {
	}

	static BlockState block(HallPlan.PropCell cell, int x, int y, int z) {
		return switch (cell.prop().prop()) {
			case TOLL_BOOTH -> booth(cell.bx(), cell.bz(), y);
			case GENERATOR_ROOM -> generatorRoom(cell.prop(), cell.bx(), cell.bz(), x, y, z);
			case EYED_CRATE -> y == FLOOR + 1 ? state(Level1Blocks.EYED_CRATE) : null;
		};
	}

	/**
	 * A 3x3 booth: a post at each corner, waist-high panels with open windows above on three sides,
	 * the drawer under the front window, a doorway at the back, a flat roof; then the barrier across the aisle.
	 */
	private static BlockState booth(int bx, int bz, int y) {
		if (bx >= 5) {
			if (bx == 5) return y == FLOOR + 1 ? state(Level1Blocks.BARRIER_BASE) : null;
			return y == FLOOR + 2 ? state(Level1Blocks.BARRIER_ARM) : null;
		}
		if (y == BOOTH_ROOF) return state(Level1Blocks.BOOTH_ROOF);
		if (y <= FLOOR || y > BOOTH_ROOF) return null;
		boolean corner = (bx == 2 || bx == 4) && (bz == 7 || bz == 9);
		if (corner) return state(Level1Blocks.BOOTH_WALL);
		if (bx == 3 && bz >= 8) return air(); // inside, and the doorway
		if (y == FLOOR + 1) return bx == 3 ? state(Level1Blocks.BOOTH_DRAWER) : state(Level1Blocks.BOOTH_WALL);
		return air(); // the windows
	}

	/** A 5x5 concrete room, floor to ceiling outside, with a lower ceiling inside, a doorway, the generator and a drum. */
	private static BlockState generatorRoom(HallPlan.PlacedProp prop, int bx, int bz, int x, int y, int z) {
		if (y <= FLOOR || y >= Level1ChunkGenerator.CEILING_Y) return null;
		int[] door = prop.door();
		boolean doorway = x == door[0] && z == door[1];
		boolean wall = !doorway && (bx == 3 || bx == 7 || bz == 6 || bz == 10);
		if (wall || y > ROOM_CEILING) return state(Level1Blocks.GARAGE_WALL);
		if (y == ROOM_CEILING) return doorway ? state(Level1Blocks.GARAGE_WALL) : state(Level1Blocks.GARAGE_CEILING);
		if (doorway) return y <= FLOOR + 2 ? air() : state(Level1Blocks.GARAGE_WALL);
		if (bx == 5 && bz == 8) {
			if (y == FLOOR + 1) {
				// The control side faces the doorway.
				Direction facing = Direction.from2DDataValue(new int[] {2, 3, 0, 1}[prop.face()]);
				return state(Level1Blocks.GENERATOR).setValue(GeneratorBlock.FACING, facing);
			}
			if (y == ROOM_CEILING - 1) return state(Level1Blocks.CAGED_LAMP);
			return air();
		}
		int[] drum = Level1Layout.generatorDrum(prop);
		if (bx == drum[0] && bz == drum[1] && y == FLOOR + 1) return state(Level1Blocks.FUEL_DRUM);
		return air();
	}

	private static BlockState state(Block block) {
		return block.defaultBlockState();
	}

	private static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}
}
