package dev.noclip.backrooms.level1;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** A sign set into a wall; AXIS is the direction the wall runs, and the sign shows on both faces across it. */
final class AxisSignBlock extends Block {
	static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

	AxisSignBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// Face whoever places it.
		Direction.Axis looking = context.getHorizontalDirection().getAxis();
		return defaultBlockState().setValue(AXIS, looking == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}
}
