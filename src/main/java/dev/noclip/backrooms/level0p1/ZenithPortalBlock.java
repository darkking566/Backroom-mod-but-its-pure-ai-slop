package dev.noclip.backrooms.level0p1;

import dev.noclip.backrooms.core.LevelExitBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** A doorway of yellow wallpaper standing alone in an airlock: right-click it and you are back in Level 0. */
class ZenithPortalBlock extends LevelExitBlock {
	static final IntegerProperty PART = IntegerProperty.create("part", 0, 1);

	ZenithPortalBlock(Properties properties, Use use) {
		super(properties, use);
		registerDefaultState(stateDefinition.any().setValue(PART, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PART);
	}
}
