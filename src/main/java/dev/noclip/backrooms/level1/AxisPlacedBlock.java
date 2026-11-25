package dev.noclip.backrooms.level1;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A fixture that is long one way and the same from both sides (counters, door frames, doors, lights, barrier arms), lying
 * along AXIS. X is how the model is drawn, so what the world generator places unturned looks as it always did.
 */
class AxisPlacedBlock extends ShapedBlock {
	static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	/** true: a player lays it along the way they look (ceiling lights); false: across it, like a wall in front of them. */
	private final boolean along;

	AxisPlacedBlock(Properties properties, VoxelShape shape, boolean along) {
		super(properties, shape);
		this.along = along;
		registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		boolean lookingAlongX = context.getHorizontalDirection().getAxis() == Direction.Axis.X;
		return defaultBlockState().setValue(AXIS, lookingAlongX == along ? Direction.Axis.X : Direction.Axis.Z);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}
}
