package dev.noclip.backrooms.level0p1;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * What the Wiki says you hear: the hum in the ceiling that never stops, now and then an alarm far away, and an
 * automated voice telling everyone to stay calm. It has been telling them for a very long time.
 */
final class ZenithAmbience {
	private static final int ANNOUNCEMENTS = 6;
	private static final Map<UUID, Integer> NEXT_HUM = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_ALARM = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_VOICE = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_CREAK = new HashMap<>();

	private ZenithAmbience() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ZenithAmbience::tick);
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			if (player.level().dimension() != Level0p1.LEVEL_0_1) {
				NEXT_HUM.remove(id);
				NEXT_ALARM.remove(id);
				NEXT_VOICE.remove(id);
				NEXT_CREAK.remove(id);
				continue;
			}
			ServerLevel level = player.level();
			RandomSource random = level.getRandom();
			if (now >= NEXT_HUM.getOrDefault(id, now)) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), ZenithSounds.HUM, SoundSource.AMBIENT, 0.5F, 0.8F);
				NEXT_HUM.put(id, now + 200);
			}
			if (now >= NEXT_ALARM.computeIfAbsent(id, k -> now + 600 + random.nextInt(1200))) {
				alarm(level, player, random);
				NEXT_ALARM.put(id, now + 1800 + random.nextInt(2400));
			}
			if (now >= NEXT_CREAK.computeIfAbsent(id, k -> now + 200 + random.nextInt(400))) {
				double angle = random.nextDouble() * Math.PI * 2, distance = 8 + random.nextDouble() * 16;
				level.playSound(null, player.getX() + Math.cos(angle) * distance, player.getY(), player.getZ() + Math.sin(angle) * distance,
						ZenithSounds.CREAK, SoundSource.AMBIENT, 0.5F, 0.6F + random.nextFloat() * 0.4F);
				NEXT_CREAK.put(id, now + 400 + random.nextInt(800));
			}
			if (now >= NEXT_VOICE.computeIfAbsent(id, k -> now + 1200 + random.nextInt(1200))) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), ZenithSounds.CHIME, SoundSource.AMBIENT, 0.7F, 0.9F);
				player.sendSystemMessage(Component.translatable("message.noclip_backrooms.level0p1.announce." + random.nextInt(ANNOUNCEMENTS)));
				NEXT_VOICE.put(id, now + 4800 + random.nextInt(6000));
			}
		}
	}

	private static void alarm(ServerLevel level, ServerPlayer player, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2, distance = 24 + random.nextDouble() * 24;
		double x = player.getX() + Math.cos(angle) * distance, z = player.getZ() + Math.sin(angle) * distance;
		level.playSound(null, x, player.getY(), z, ZenithSounds.ALARM, SoundSource.AMBIENT, 1.0F, 0.9F);
	}
}
