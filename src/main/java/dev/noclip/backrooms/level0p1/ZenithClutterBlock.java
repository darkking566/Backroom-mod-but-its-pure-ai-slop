package dev.noclip.backrooms.level0p1;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Small things left on a surface (a mug and a book, a stack of plates). On a table or desk, whose top is 3 pixels
 * short of a block, they sit down on it ({@link #ON_TABLE}); anywhere else they stand on the block below.
 */
final class ZenithClutterBlock extends Block {
	static final BooleanProperty ON_TABLE = BooleanProperty.create("on_table");
	private static final VoxelShape ON_FLOOR = Block.box(2, 0, 2, 14, 4, 14), SUNK = Block.box(2, -3, 2, 14, 1, 14);

	ZenithClutterBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(ON_TABLE, false));
	}

	static boolean isTable(BlockState below) {
		return below.is(ZenithBlocks.CREW_TABLE) || below.is(ZenithBlocks.CREW_DESK);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(ON_TABLE, isTable(context.getLevel().getBlockState(context.getClickedPos().below())));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(ON_TABLE) ? SUNK : ON_FLOOR;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ON_TABLE);
	}
}
