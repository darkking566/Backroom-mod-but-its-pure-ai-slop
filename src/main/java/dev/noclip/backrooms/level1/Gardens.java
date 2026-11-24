package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Layout.Sector;
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
 * "Stay an hour in the Garden Sector and your body starts to twist, until you turn into a plant." Here the hour is
 * seven minutes: time spent among the plants builds up, drains away outside, and at the end you take root.
 */
final class Gardens {
	private static final int ITCH = 90, STIFF = 180, SPROUT = 300, ROOTED = 420;
	private static final Map<UUID, Integer> EXPOSURE = new HashMap<>();

	private Gardens() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Gardens::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> EXPOSURE.clear());
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		if (level == null || server.getTickCount() % 20 != 0) return;
		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative()) continue;
			int exposure = EXPOSURE.getOrDefault(player.getUUID(), 0);
			boolean inGarden = Level1Layout.sector(seed, player.getBlockX(), player.getBlockZ()) == Sector.GARDEN;
			exposure = inGarden ? exposure + 1 : Math.max(0, exposure - 2);
			EXPOSURE.put(player.getUUID(), exposure);

			if (exposure == ITCH) player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.garden_itch"));
			if (exposure == STIFF) player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.garden_stiff"));
			if (exposure == SPROUT) player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.garden_sprout"));
			if (exposure >= STIFF) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, exposure >= SPROUT ? 2 : 0, true, false));
			}
			if (exposure >= ROOTED) {
				EXPOSURE.put(player.getUUID(), 0);
				player.sendSystemMessage(Component.translatable("message.noclip_backrooms.garden_rooted", player.getDisplayName()));
				player.kill(level);
			}
		}
	}
}
