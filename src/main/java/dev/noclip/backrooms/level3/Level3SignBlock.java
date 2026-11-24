package dev.noclip.backrooms.level3;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A sign plate on a wall. FACING points out from the wall; RIGHT, which way an arrow on it points as you face it. */
final class Level3SignBlock extends Block {
	static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	static final BooleanProperty RIGHT = BooleanProperty.create("right");
	private static final Map<Direction, VoxelShape> SHAPES = Level3Shapes.facing(2, 3, 0, 14, 13, 1);

	Level3SignBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(RIGHT, true));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction clicked = context.getClickedFace();
		return defaultBlockState().setValue(FACING, clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, RIGHT);
	}
}
