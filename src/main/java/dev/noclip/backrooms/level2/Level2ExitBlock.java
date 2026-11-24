package dev.noclip.backrooms.level2;

import dev.noclip.backrooms.core.LevelExits;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A door to another level, set flush into a tunnel wall. PART counts up from the floor (0 is the bottom block);
 * the oversized floor-to-ceiling doors use all four. Right-click any part to go through.
 */
final class Level2ExitBlock extends Block {
	static final IntegerProperty PART = IntegerProperty.create("part", 0, 3);
	final Level2Exit exit;

	Level2ExitBlock(Properties properties, Level2Exit exit) {
		super(properties);
		this.exit = exit;
		registerDefaultState(stateDefinition.any().setValue(PART, 0));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) LevelExits.use(serverPlayer, exit.level);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PART);
	}
}
