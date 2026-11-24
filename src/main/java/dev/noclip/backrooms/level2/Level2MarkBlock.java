package dev.noclip.backrooms.level2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Spray paint on a tunnel wall left by earlier wanderers: the number of the level a door leads to, and an arrow
 * the way to it. FACING points out from the wall; LEVEL is 0, 1, 2 for the doors to 1, 3, 4; RIGHT, which way
 * the arrow points as you face the wall.
 */
final class Level2MarkBlock extends Block {
	static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 2);
	static final BooleanProperty RIGHT = BooleanProperty.create("right");
	private static final java.util.Map<Direction, VoxelShape> SHAPES = java.util.Map.of(
			Direction.SOUTH, Block.box(0, 0, 0, 16, 16, 0.5), Direction.NORTH, Block.box(0, 0, 15.5, 16, 16, 16),
			Direction.EAST, Block.box(0, 0, 0, 0.5, 16, 16), Direction.WEST, Block.box(15.5, 0, 0, 16, 16, 16));

	Level2MarkBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(LEVEL, 0).setValue(RIGHT, true));
	}

	static int level(Level2Exit exit) {
		return exit == Level2Exit.MARKED_1 ? 0 : exit == Level2Exit.MARKED_3 ? 1 : 2;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LEVEL, RIGHT);
	}
}
