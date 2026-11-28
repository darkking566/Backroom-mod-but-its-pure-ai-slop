package dev.noclip.backrooms.level1;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** BlockEntity marker for full-scale Level 1 vehicle master blocks rendered by {@code Level1VehicleRenderer}. */
public final class Level1VehicleBlockEntity extends BlockEntity {
	public Level1VehicleBlockEntity(BlockPos pos, BlockState state) {
		super(Level1Blocks.VEHICLE_BLOCK_ENTITY, pos, state);
	}
}
