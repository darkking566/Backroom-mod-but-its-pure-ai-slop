package dev.noclip.backrooms.level0p1;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;

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
	/** Until when the speakers near each player are announcing (they light up with notes). */
	private static final Map<UUID, Integer> ANNOUNCING = new HashMap<>();

	private ZenithAmbience() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ZenithAmbience::tick);
		// The times are server ticks, which start from 0 in every world: what was due in the last one must not wait here.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			NEXT_HUM.clear();
			NEXT_ALARM.clear();
			NEXT_VOICE.clear();
			NEXT_CREAK.clear();
			ANNOUNCING.clear();
		});
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
				ANNOUNCING.remove(id);
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
				ANNOUNCING.put(id, now + 60);
			}
			if (now % 5 == 0) details(level, player, random, now < ANNOUNCING.getOrDefault(id, 0));
		}
	}

	private static void alarm(ServerLevel level, ServerPlayer player, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2, distance = 24 + random.nextDouble() * 24;
		double x = player.getX() + Math.cos(angle) * distance, z = player.getZ() + Math.sin(angle) * distance;
		level.playSound(null, x, player.getY(), z, ZenithSounds.ALARM, SoundSource.AMBIENT, 1.0F, 0.9F);
	}

	/** Sparks from live wires, steam off the vents, notes from speakers that are talking: sampled around the player, not scanned. */
	private static void details(ServerLevel level, ServerPlayer player, RandomSource random, boolean announcing) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 48; i++) {
			pos.set(player.getBlockX() + random.nextInt(25) - 12, player.getBlockY() + random.nextInt(11) - 4, player.getBlockZ() + random.nextInt(25) - 12);
			if (!level.isLoaded(pos)) continue;
			Block block = level.getBlockState(pos).getBlock();
			double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
			if (block == ZenithBlocks.HANGING_CABLES && random.nextInt(6) == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y - 0.3, z, 6, 0.2, 0.3, 0.2, 0.3);
				level.playSound(null, x, y, z, ZenithSounds.SPARK, SoundSource.AMBIENT, 0.4F, 0.8F + random.nextFloat() * 0.4F);
			} else if (block == ZenithBlocks.HEAT_VENT && random.nextInt(3) == 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.2, z, 2, 0.2, 0.1, 0.2, 0.02);
			} else if (block == ZenithBlocks.CEILING_SPEAKER && announcing) {
				level.sendParticles(ParticleTypes.NOTE, x, y - 0.8, z, 1, 0.3, 0.1, 0.3, 1.0);
			}
		}
	}
}
