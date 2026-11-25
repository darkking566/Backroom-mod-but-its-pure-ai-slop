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
 * A beige box with a dead screen, facing FACING; and the other Level 1 fixtures with a front (chairs, lockers, locks, desks,
 * beds). North is how the model is drawn, so what the world generator places unturned looks as it always did.
 */
class OldComputerBlock extends ShapedBlock {
	static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** Quarter turns clockwise from the way the player looks to the FACING a placed block gets (2: facing the player). */
	private final int turns;

	OldComputerBlock(Properties properties, VoxelShape shape) {
		this(properties, shape, 2);
	}

	OldComputerBlock(Properties properties, VoxelShape shape, int turns) {
		super(properties, shape);
		this.turns = turns;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getHorizontalDirection();
		for (int i = 0; i < turns; i++) facing = facing.getClockWise();
		return defaultBlockState().setValue(FACING, facing);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}
}
