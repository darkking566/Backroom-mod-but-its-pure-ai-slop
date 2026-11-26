package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level0.Level0;
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
 * A heavy push-bar door in the corridors: a way out M.E.G. watches. Using it calls them for a pickup (hold out beside
 * it), and they take the player to the institute in the Frontrooms, alive and with their items.
 */
final class Level1DoorBlock extends AxisPlacedBlock {
	Level1DoorBlock(Properties properties) {
		super(properties, net.minecraft.world.phys.shapes.Shapes.block(), false);
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

		// A pickup point, like every way out M.E.G. knows of (core.trip.Extraction): hold out here, then the institute.
		dev.noclip.backrooms.core.trip.Extraction.start(player, player.position(), 6.0);
	}
}
