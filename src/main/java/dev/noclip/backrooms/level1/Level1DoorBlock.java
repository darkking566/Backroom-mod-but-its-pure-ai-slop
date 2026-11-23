package dev.noclip.backrooms.level1;

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

/**
 * A heavy push-bar door in the corridors. Until Level 2 exists it leads home, like the Level 0 exits:
 * to the player's respawn point, alive and with their items.
 */
final class Level1DoorBlock extends Block {
	Level1DoorBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.dimension() != Level1.LEVEL_1) return InteractionResult.PASS;

		if (player instanceof ServerPlayer serverPlayer) {
			MinecraftServer server = serverPlayer.level().getServer();
			// Changing dimension in the middle of an interaction is unsafe.
			server.schedule(new TickTask(server.getTickCount(), () -> leave(serverPlayer)));
		}
		return InteractionResult.SUCCESS;
	}

	private static void leave(ServerPlayer player) {
		if (player.isRemoved() || player.hasDisconnected() || !player.isAlive()) return;
		// A double click queues this twice.
		if (player.level().dimension() != Level1.LEVEL_1) return;

		// false: do not spend a respawn anchor charge.
		TeleportTransition destination = player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.PLAY_PORTAL_SOUND);
		var target = destination.newLevel().dimension();
		if (target == Level1.LEVEL_0) {
			// A spawn point in Level 0 would make the way out lead straight back in. One in Level 1 is a settlement
			// someone chose to call home, so the door takes them there.
			ServerLevel overworld = player.level().getServer().overworld();
			Vec3 spawn = Vec3.atBottomCenterOf(player.adjustSpawnLocation(overworld, overworld.getRespawnData().pos()));
			destination = new TeleportTransition(overworld, spawn, Vec3.ZERO, 0, 0, TeleportTransition.PLAY_PORTAL_SOUND);
		}

		player.stopRiding();
		player.resetFallDistance();
		player.teleport(destination);
	}
}
