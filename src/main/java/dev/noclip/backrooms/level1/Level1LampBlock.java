package dev.noclip.backrooms.level1;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A working light. Blackouts switch it off and on through LIT; broken lights are separate blocks. */
final class Level1LampBlock extends ShapedBlock {
	static final BooleanProperty LIT = BlockStateProperties.LIT;

	Level1LampBlock(Properties properties, VoxelShape shape) {
		super(properties, shape);
		registerDefaultState(stateDefinition.any().setValue(LIT, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}
}
