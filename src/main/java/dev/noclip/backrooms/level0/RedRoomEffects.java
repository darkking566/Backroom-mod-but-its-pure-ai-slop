package dev.noclip.backrooms.level0;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Psychological pressure near and inside Red Rooms:
 * - In the peeling-wallpaper warning band outside a Red Room: faint darkness pulse and low heartbeat.
 * - Inside a Red Room: heavier darkness, oppressive heartbeat, mining fatigue, and periodic nausea as dread builds.
 */
final class RedRoomEffects {
	private static final int CHECK_INTERVAL_TICKS = 40;
	private static final Map<UUID, Integer> RED_EXPOSURE = new HashMap<>();

	private RedRoomEffects() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RedRoomEffects::tick);
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % CHECK_INTERVAL_TICKS != 0) return;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			if (player.level().dimension() != Level0.LEVEL_0 || player.isCreative() || player.isSpectator()) {
				RED_EXPOSURE.remove(id);
				continue;
			}

			ServerLevel level = player.level();
			long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
			int x = player.getBlockX(), z = player.getBlockZ();
			Maze.Zone zone = Maze.zoneAt(seed, x, z);

			if (zone == Maze.Zone.RED_ROOM) {
				int exposure = RED_EXPOSURE.getOrDefault(id, 0) + 1;
				RED_EXPOSURE.put(id, exposure);

				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 90, 0, true, false, true));
				player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 90, 2, true, false, true));

				// Periodic nausea after 6+ seconds inside, pulsing on and off so it stays unsettling without being unplayable.
				if (exposure >= 3 && exposure % 4 != 0) {
					player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, true, false, true));
				}

				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.65F, 0.68F);
			} else if (Maze.isNearRedRoom(seed, x, z)) {
				RED_EXPOSURE.put(id, 0);
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 65, 0, true, false, true));
				if ((now / CHECK_INTERVAL_TICKS) % 2 == 0) {
					level.playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.35F, 0.78F);
				}
			} else {
				RED_EXPOSURE.remove(id);
			}
		}
	}
}
