package dev.noclip.backrooms;

import dev.noclip.backrooms.levels.LevelExits;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Hidden Void Stone core inside the Manila Room's thick walls; touching it attempts to reach Level 287. */
final class VoidStoneBlock extends Block {
	VoidStoneBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			LevelExits.use(serverPlayer, "287");
		}
		return InteractionResult.SUCCESS;
	}
}
