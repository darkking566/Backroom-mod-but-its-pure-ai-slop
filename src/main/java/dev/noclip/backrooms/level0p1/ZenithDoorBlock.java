package dev.noclip.backrooms.level0p1;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** A sliding door of the station: ZenithDoors opens it when you come near, and it closes itself when nobody is. */
final class ZenithDoorBlock extends DoorBlock {
	private static final int CHECK = 24;

	ZenithDoorBlock(BlockSetType type, Properties properties) {
		super(type, properties);
	}

	@Override
	public void setOpen(Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
		super.setOpen(entity, level, state, pos, open);
		if (open && level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, this, CHECK);
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
