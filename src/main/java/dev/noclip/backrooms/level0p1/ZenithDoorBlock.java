package dev.noclip.backrooms.level0p1;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** A sliding door of the station: ZenithDoors opens it when you come near, and it closes itself when nobody is. */
final class ZenithDoorBlock extends DoorBlock {
	private static final int CHECK = 24;

	ZenithDoorBlock(BlockSetType type, Properties properties) {
		super(type, properties);
	}

	@Override
	public void setOpen(Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
		if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
			pos = pos.below();
			state = level.getBlockState(pos);
			if (!state.is(this)) return;
		}
		super.setOpen(entity, level, state, pos, open);
		if (open && level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, this, CHECK);
		// The other leaf of a pair moves with this one (quietly: one sound for the pair). The upper half follows by itself.
		Direction facing = state.getValue(FACING);
		for (Direction side : new Direction[] {facing.getClockWise(), facing.getCounterClockWise()}) {
			BlockPos other = pos.relative(side);
			BlockState leaf = level.getBlockState(other);
			if (leaf.is(this) && leaf.getValue(HALF) == DoubleBlockHalf.LOWER && leaf.getValue(FACING) == facing
					&& leaf.getValue(HINGE) != state.getValue(HINGE) && leaf.getValue(OPEN) != open) {
				level.setBlock(other, leaf.setValue(OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
				if (open && level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(other, this, CHECK);
			}
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(OPEN)) return;
		boolean near = false;
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5) < 4.0 * 4.0) near = true;
		}
		if (near) {
			level.scheduleTick(pos, this, CHECK);
			return;
		}
		super.setOpen(null, level, state, pos, false);
	}
}
