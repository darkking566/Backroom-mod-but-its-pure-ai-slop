package dev.noclip.backrooms.level1;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A block whose outline matches its model, for fixtures that are smaller than a whole block. {@code shape} is the north
 * facing; a block with a horizontal facing turns it, as its blockstate turns the model.
 */
class ShapedBlock extends Block {
	private final VoxelShape shape;
	private final Map<Direction, VoxelShape> turned;

	ShapedBlock(Properties properties, VoxelShape shape) {
		super(properties);
		this.shape = shape;
		this.turned = Shapes.rotateHorizontal(shape);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? turned.get(state.getValue(BlockStateProperties.HORIZONTAL_FACING)) : shape;
	}
}
