package dev.noclip.backrooms.level1;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** A neon tube: lying (the default, for ribbons and trim) or upright, so a stacked column reads as one long tube. */
final class NeonBlock extends Block {
	static final BooleanProperty UPRIGHT = BooleanProperty.create("upright");

	NeonBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(UPRIGHT, false));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(UPRIGHT, context.getClickedFace().getAxis().isVertical());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(UPRIGHT);
	}
}
