package dev.noclip.backrooms.level3;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Something that runs one way: a strip light, a cable tray, a set of bars across a passage. */
class Level3AxisBlock extends Block {
	static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	private final VoxelShape[] shapes;

	Level3AxisBlock(Properties properties, VoxelShape[] shapes) {
		super(properties);
		this.shapes = shapes;
		registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes[state.getValue(AXIS) == Direction.Axis.X ? 0 : 1];
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction.Axis looking = context.getHorizontalDirection().getAxis();
		return defaultBlockState().setValue(AXIS, looking == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}
}
