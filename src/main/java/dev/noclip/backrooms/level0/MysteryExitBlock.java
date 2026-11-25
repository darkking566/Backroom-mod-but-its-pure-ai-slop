package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.core.LevelExitBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Unbreakable foreshadowing exit door in Level 0 pointing to another Backrooms level key.
 * Until the target level registers with {@code LevelExits.open(...)}, using either half tells the player it is shut.
 */
final class MysteryExitBlock extends LevelExitBlock {
	static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

	MysteryExitBlock(Properties properties, String level) {
		super(properties, LevelExitBlock.to(level));
		registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
	}
}
