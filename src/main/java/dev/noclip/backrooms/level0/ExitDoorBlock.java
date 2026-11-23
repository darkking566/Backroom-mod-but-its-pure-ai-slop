package dev.noclip.backrooms.level0;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Using either half of an exit door in Level 0 sends the player to their respawn point, alive and with their items. */
final class ExitDoorBlock extends Block {
	ExitDoorBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.dimension() != Level0.LEVEL_0) return InteractionResult.PASS;

		if (player instanceof ServerPlayer serverPlayer) {
			MinecraftServer server = serverPlayer.level().getServer();
			// Not mid-interaction, for the same reason as Noclip.
			server.schedule(new TickTask(server.getTickCount(), () -> leave(serverPlayer)));
		}
		return InteractionResult.SUCCESS;
	}

	private static void leave(ServerPlayer player) {
		if (player.isRemoved() || player.hasDisconnected() || !player.isAlive()) return;
		// A double click queues this twice.
		if (player.level().dimension() != Level0.LEVEL_0) return;

		// false: do not spend a respawn anchor charge.
		TeleportTransition destination = player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.PLAY_PORTAL_SOUND);
		if (destination.newLevel().dimension() == Level0.LEVEL_0) {
			// A spawn point set in here by command would make every door lead straight back in.
			ServerLevel overworld = player.level().getServer().overworld();
			Vec3 spawn = Vec3.atBottomCenterOf(player.adjustSpawnLocation(overworld, overworld.getRespawnData().pos()));
			destination = new TeleportTransition(overworld, spawn, Vec3.ZERO, 0, 0, TeleportTransition.PLAY_PORTAL_SOUND);
		}

		player.stopRiding();
		player.resetFallDistance();
		player.teleport(destination);
	}
}
