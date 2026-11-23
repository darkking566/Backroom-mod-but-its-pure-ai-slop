package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.BasePlan.Piece;
import dev.noclip.backrooms.level1.Level1Layout.Base;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The blocks of the settlements (BasePlan has where they stand); null where the ordinary hall shows through. */
final class Level1Bases {
	private static final int FLOOR = Level1ChunkGenerator.FLOOR_Y;
	private static final int TENT_ROOF = FLOOR + 3;

	private Level1Bases() {
	}

	static BlockState block(Base base, Piece piece, int y) {
		return switch (piece) {
			case BARRIER_X, BARRIER_Z -> y == FLOOR + 1 ? barrier(piece == Piece.BARRIER_X) : null;
			case SIGN_X, SIGN_Z -> {
				if (y == FLOOR + 1) yield barrier(piece == Piece.SIGN_X);
				if (y == FLOOR + 2) yield state(base == Base.FIRST_LIGHT ? Level1Blocks.FIRST_LIGHT_SIGN : Level1Blocks.BLINK_SIGN);
				yield null;
			}
			case FLOODLIGHT -> y == FLOOR + 1 ? state(Level1Blocks.FLOODLIGHT) : null;
			case COT -> y == FLOOR + 1 ? state(Level1Blocks.COT) : null;
			case LOCKER -> y == FLOOR + 1 ? state(Level1Blocks.SUPPLY_LOCKER) : null;
			case DESK -> y == FLOOR + 1 ? state(Level1Blocks.OFFICE_DESK) : null;
			case COMPUTER_DESK -> {
				if (y == FLOOR + 1) yield state(Level1Blocks.OFFICE_DESK);
				if (y == FLOOR + 2) yield state(Level1Blocks.OLD_COMPUTER).setValue(OldComputerBlock.FACING, Direction.SOUTH);
				yield null;
			}
			case FIRE -> y == FLOOR + 1 ? state(Level1Blocks.BARREL_FIRE) : null;
			case BEDROLL -> y == FLOOR + 1 ? state(Level1Blocks.BEDROLL) : null;
			case TENT_WALL -> y > FLOOR && y <= TENT_ROOF ? state(Level1Blocks.TARP) : null;
			case TENT_FLOOR -> y == TENT_ROOF ? state(Level1Blocks.TARP) : null;
			case TENT_BEDROLL -> y == FLOOR + 1 ? state(Level1Blocks.BEDROLL) : y == TENT_ROOF ? state(Level1Blocks.TARP) : null;
		};
	}

	private static BlockState barrier(boolean alongX) {
		return state(Level1Blocks.JERSEY_BARRIER).setValue(AxisShapedBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z);
	}

	private static BlockState state(Block block) {
		return block.defaultBlockState();
	}
}
