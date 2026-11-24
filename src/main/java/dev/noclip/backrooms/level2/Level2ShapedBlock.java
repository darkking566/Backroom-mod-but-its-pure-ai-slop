package dev.noclip.backrooms.level2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A block smaller than a full cube: an oil drum, a heap of scrap, a hanging light. */
class Level2ShapedBlock extends Block {
	private final VoxelShape shape;

	Level2ShapedBlock(Properties properties, VoxelShape shape) {
		super(properties);
		this.shape = shape;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape;
	}
}
