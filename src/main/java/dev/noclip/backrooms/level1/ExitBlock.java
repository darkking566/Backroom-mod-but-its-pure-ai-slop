package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.core.LevelExits;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Something you use with a right-click to go to another level: a door, a painting, a crack, a hatch. */
class ExitBlock extends Block {
	final String level;

	ExitBlock(Properties properties, String level) {
		super(properties);
		this.level = level;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) LevelExits.use(serverPlayer, level);
		return InteractionResult.SUCCESS;
	}
}
