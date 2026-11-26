package dev.noclip.backrooms.frontrooms;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** The lamp over a blast door: red while it is locked, green once the team's quotas have opened it. */
public final class DoorLampBlock extends Block {
	public static final IntegerProperty REQUIRES = BlastDoorBlock.REQUIRES;
	public static final BooleanProperty UNLOCKED = BooleanProperty.create("unlocked");

	DoorLampBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(REQUIRES, 1).setValue(UNLOCKED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(REQUIRES, UNLOCKED);
	}
}
