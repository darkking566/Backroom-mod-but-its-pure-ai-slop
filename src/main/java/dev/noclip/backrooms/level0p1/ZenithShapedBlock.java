package dev.noclip.backrooms.level0p1;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A block smaller than a cube that needs no facing: a table, a bench, a hanging lamp. */
final class ZenithShapedBlock extends Block {
	private final VoxelShape shape;
	private final boolean solid;

	ZenithShapedBlock(Properties properties, VoxelShape shape, boolean solid) {
		super(properties);
		this.shape = shape;
		this.solid = solid;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return solid ? shape : net.minecraft.world.phys.shapes.Shapes.empty();
	}
}
