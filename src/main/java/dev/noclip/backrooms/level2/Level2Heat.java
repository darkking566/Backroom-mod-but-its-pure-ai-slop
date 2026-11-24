package dev.noclip.backrooms.level2;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Some stretches of Level 2 are unreasonably hot. In them you tire and hunger fast; stay half a minute and the
 * heat starts to weaken you. The way to Level 127 lies in these hot spots.
 */
final class Level2Heat {
	/** Exhaustion per second in the heat: about a hunger point every ten seconds. */
	static final float EXHAUSTION = 0.4F;
	private static final int WEAKENS_AFTER = 30;
	private static final Map<UUID, Integer> SECONDS = new HashMap<>();

	private Level2Heat() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level2Heat::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> SECONDS.clear());
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		ServerLevel level = server.getLevel(Level2.LEVEL_2);
		if (level == null) return;
		long seed = Level2ChunkGenerator.planSeed(level.getChunkSource().randomState());
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator() || !Level2Layout.hot(seed, player.getBlockX(), player.getBlockZ())) {
				SECONDS.remove(player.getUUID());
				continue;
			}
			int seconds = SECONDS.merge(player.getUUID(), 1, Integer::sum);
			if (seconds == 1) player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level2.heat"));
			player.causeFoodExhaustion(EXHAUSTION);
			if (seconds >= WEAKENS_AFTER) {
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, true, false, true));
			}
		}
	}

	static boolean feelingIt(ServerPlayer player) {
		return SECONDS.containsKey(player.getUUID());
	}
}
