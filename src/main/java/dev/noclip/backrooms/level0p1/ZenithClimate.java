package dev.noclip.backrooms.level0p1;

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
 * The Wiki: the damaged parts of the station are "sometimes extremely cold or hot, close to lethal", the climate
 * control gone wrong. Zones 1 and 2 of {@link ZenithLayout#zone} are those: in the cold you freeze, in the heat you
 * tire and hunger, and after half a minute either one slows you down.
 */
final class ZenithClimate {
	private static final Map<UUID, Integer> SECONDS = new HashMap<>();
	private static final Map<UUID, Integer> LAST = new HashMap<>();

	private ZenithClimate() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ZenithClimate::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SECONDS.clear();
			LAST.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		ServerLevel level = server.getLevel(Level0p1.LEVEL_0_1);
		if (level == null) return;
		long seed = ZenithChunkGenerator.planSeed(level.getChunkSource().randomState());
		for (ServerPlayer player : level.players()) {
			int zone = ZenithLayout.zoneAt(seed, player.getBlockX(), player.getBlockY(), player.getBlockZ());
			if (player.isCreative() || player.isSpectator() || zone != 1 && zone != 2) {
				SECONDS.remove(player.getUUID());
				LAST.remove(player.getUUID());
				continue;
			}
			int seconds = SECONDS.merge(player.getUUID(), 1, Integer::sum);
			Integer before = LAST.put(player.getUUID(), zone);
			if (before == null || before != zone) {
				player.sendOverlayMessage(Component.translatable(zone == 1 ? "message.noclip_backrooms.level0p1.cold" : "message.noclip_backrooms.level0p1.hot"));
			}
			if (zone == 1) {
				// Frost builds: stand in it long enough and it starts to hurt.
				player.setTicksFrozen(Math.min(player.getTicksRequiredToFreeze() + 20, player.getTicksFrozen() + 40));
			} else {
				player.causeFoodExhaustion(0.4F);
			}
			if (seconds >= 30) player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0, true, false, true));
		}
	}
}
