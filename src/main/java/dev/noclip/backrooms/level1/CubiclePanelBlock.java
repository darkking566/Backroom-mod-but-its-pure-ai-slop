package dev.noclip.backrooms.level1;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A thin office partition: a two-pixel post that reaches an arm toward each neighbouring panel, with one of four things pinned to it. */
final class CubiclePanelBlock extends Block {
	static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST,
			SOUTH = BlockStateProperties.SOUTH, WEST = BlockStateProperties.WEST;
	static final IntegerProperty STYLE = IntegerProperty.create("style", 0, 3);
	private static final VoxelShape POST = Block.box(7, 0, 7, 9, 16, 9);
	private static final VoxelShape[] ARMS = {
			Block.box(7, 0, 0, 9, 16, 8), Block.box(8, 0, 7, 16, 16, 9), Block.box(7, 0, 8, 9, 16, 16), Block.box(0, 0, 7, 8, 16, 9)};

	CubiclePanelBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false).setValue(STYLE, 0));
	}

	static BooleanProperty side(Direction direction) {
		return switch (direction) {
			case NORTH -> NORTH;
			case EAST -> EAST;
			case SOUTH -> SOUTH;
			default -> WEST;
		};
	}

	/** The panel with arms toward the given neighbours (north, east, south, west). */
	static BlockState with(boolean north, boolean east, boolean south, boolean west, int style) {
		return Level1Blocks.CUBICLE_PANEL.defaultBlockState().setValue(NORTH, north).setValue(EAST, east).setValue(SOUTH, south).setValue(WEST, west).setValue(STYLE, style);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState().setValue(STYLE, Math.floorMod(context.getClickedPos().hashCode(), 4));
		for (Direction d : Direction.Plane.HORIZONTAL) {
			state = state.setValue(side(d), context.getLevel().getBlockState(context.getClickedPos().relative(d)).is(this));
		}
		return state;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		VoxelShape shape = POST;
		Direction[] order = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
		for (int i = 0; i < 4; i++) {
			if (state.getValue(side(order[i]))) shape = Shapes.or(shape, ARMS[i]);
		}
		return shape;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, STYLE);
	}
}
