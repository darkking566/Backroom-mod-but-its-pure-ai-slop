package dev.noclip.backrooms.level0p1;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** One ring of the glowing disc round the singularity: 0 is the whitest, nearest the middle. */
final class ZenithPlasmaBlock extends Block {
	static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, 3);

	ZenithPlasmaBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(HEAT, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HEAT);
	}
}
