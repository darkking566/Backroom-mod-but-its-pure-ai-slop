package dev.noclip.backrooms.level2;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * Windows (Entity 2): rare windows at the ends of dead-end tunnels, bright as daylight. Wanderers know not to go
 * near them. Close up you hear knocking; any closer and something on the other side reaches through and drags.
 */
final class Level2Windows {
	private static final double WARN = 5.0, GRAB = 2.2;
	private static final int REACH = 5, GRAB_COOLDOWN = 30, WARN_COOLDOWN = 200;
	private static final float DAMAGE = 4.0F;
	private static final Map<UUID, Integer> NEXT_GRAB = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_WARNING = new HashMap<>();

	private Level2Windows() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level2Windows::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			NEXT_GRAB.clear();
			NEXT_WARNING.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % 5 != 0) return;
		ServerLevel level = server.getLevel(Level2.LEVEL_2);
		if (level == null) return;
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator()) continue;
			BlockPos window = nearestWindow(level, player);
			if (window == null) continue;
			Vec3 centre = Vec3.atCenterOf(window);
			double distance = player.getEyePosition().distanceTo(centre);
			if (distance > WARN) continue;
			if (now >= NEXT_WARNING.getOrDefault(player.getUUID(), 0)) {
				NEXT_WARNING.put(player.getUUID(), now + WARN_COOLDOWN);
				player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level2.window"));
				level.playSound(null, window, Level2Sounds.WINDOW, SoundSource.HOSTILE, 0.7F, 0.6F);
			}
			if (distance > GRAB || now < NEXT_GRAB.getOrDefault(player.getUUID(), 0)) continue;
			NEXT_GRAB.put(player.getUUID(), now + GRAB_COOLDOWN);
			player.hurtServer(level, level.damageSources().magic(), DAMAGE);
			player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, true, false, true));
			Vec3 pull = centre.subtract(player.position()).multiply(1, 0, 1).normalize().scale(0.45);
			player.push(pull.x, 0.05, pull.z);
			player.hurtMarked = true;
			level.playSound(null, window, Level2Sounds.WINDOW, SoundSource.HOSTILE, 1.0F, 0.45F);
		}
	}

	static BlockPos nearestWindow(ServerLevel level, ServerPlayer player) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int y = Level2ChunkGenerator.FLOOR_Y + 2;
		for (int x = player.getBlockX() - REACH; x <= player.getBlockX() + REACH; x++) {
			for (int z = player.getBlockZ() - REACH; z <= player.getBlockZ() + REACH; z++) {
				if (!level.getBlockState(pos.set(x, y, z)).is(Level2Blocks.TUNNEL_WINDOW)) continue;
				double distance = player.distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = pos.immutable();
				}
			}
		}
		return best;
	}
}
