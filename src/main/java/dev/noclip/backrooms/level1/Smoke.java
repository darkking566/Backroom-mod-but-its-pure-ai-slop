package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Layout.Kind;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "Not every room in the corridors has a vent, and you can still be choked by the smoke of your own camp": a lit
 * campfire near you in the corridors fills the air, and the longer you stay by it the worse you cough.
 */
final class Smoke {
	private static final int RANGE = 5;

	private Smoke() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Smoke::tick);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		if (level == null || server.getTickCount() % 40 != 0) return;
		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative()) continue;
			if (Level1Layout.kindAt(seed, player.getBlockX(), player.getBlockZ()) != Kind.CORRIDORS) continue;
			if (!litCampfireNear(level, player.blockPosition())) continue;
			level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.5, player.getZ(), 12, 0.6, 0.4, 0.6, 0.01);
			player.hurtServer(level, level.damageSources().generic(), 1.0F);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.smoke_choke"));
		}
	}

	static boolean litCampfireNear(ServerLevel level, BlockPos centre) {
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-RANGE, -2, -RANGE), centre.offset(RANGE, 2, RANGE))) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) return true;
		}
		return false;
	}
}
