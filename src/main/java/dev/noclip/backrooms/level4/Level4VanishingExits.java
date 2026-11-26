package dev.noclip.backrooms.level4;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Wiki mechanic: "As with many other exits in the Backrooms, once you lose sight of an exit, they may vanish unexpectedly."
 * Wild vanishing exits ({@link Level4Exit#vanishing}) observed by a wanderer are tracked; once every nearby
 * player looks away for ~4 seconds, the exit dissolves quietly into solid drywall (or carpet for floor hatches).
 * M.E.G. Base Omega stairwells and Level 3 elevators are permanent and never vanish.
 */
final class Level4VanishingExits {
	private static final int CHECK_INTERVAL = 10;
	private static final int VANISH_TICKS = 80;
	private static final int SCAN_RADIUS = 18;
	private static final Map<BlockPos, Integer> UNSEEN_TICKS = new HashMap<>();
	private static int heldOffUntil;

	private Level4VanishingExits() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level4VanishingExits::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			UNSEEN_TICKS.clear();
			heldOffUntil = 0;
		});
	}

	static void holdOff(MinecraftServer server, int ticks) {
		heldOffUntil = server.getTickCount() + ticks;
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % CHECK_INTERVAL != 0 || now < heldOffUntil) return;
		ServerLevel level = server.getLevel(Level4.LEVEL_4);
		if (level == null || level.players().isEmpty()) return;
		long seed = Level4ChunkGenerator.planSeed(level.getChunkSource().randomState());

		// Discover nearby wild vanishing exits when a player looks at them
		BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			Vec3 eye = player.getEyePosition();
			Vec3 look = player.getLookAngle().normalize();
			for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
				for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
					int x = player.getBlockX() + dx, z = player.getBlockZ() + dz;
					if (Level4Layout.inOmegaBase(seed, x, z)) continue;
					scan.set(x, Level4ChunkGenerator.FLOOR_Y + 1, z);
					BlockState st = level.getBlockState(scan);
					if (!(st.getBlock() instanceof Level4ExitBlock eb) || !eb.exit.vanishing) {
						scan.set(x, Level4ChunkGenerator.FLOOR_Y, z);
						st = level.getBlockState(scan);
						if (!(st.getBlock() instanceof Level4ExitBlock eb2) || !eb2.exit.vanishing) continue;
					}
					Vec3 toExit = Vec3.atCenterOf(scan).subtract(eye);
					double dist = toExit.length();
					if (dist > 1.2 && dist < SCAN_RADIUS && look.dot(toExit.normalize()) > 0.65) {
						UNSEEN_TICKS.putIfAbsent(scan.immutable(), 0);
					}
				}
			}
		}

		// Update tracked exits: reset while watched, advance when nobody looks at them
		Iterator<Map.Entry<BlockPos, Integer>> it = UNSEEN_TICKS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<BlockPos, Integer> entry = it.next();
			BlockPos pos = entry.getKey();
			if (!level.isLoaded(pos)) {
				it.remove();
				continue;
			}
			BlockState st = level.getBlockState(pos);
			if (!(st.getBlock() instanceof Level4ExitBlock eb) || !eb.exit.vanishing || Level4Layout.inOmegaBase(seed, pos.getX(), pos.getZ())) {
				it.remove();
				continue;
			}
			boolean watched = false;
			boolean someoneClose = false;
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator()) continue;
				Vec3 toExit = Vec3.atCenterOf(pos).subtract(player.getEyePosition());
				double dist = toExit.length();
				if (dist < 28.0) someoneClose = true;
				if (dist < 24.0 && player.getLookAngle().normalize().dot(toExit.normalize()) > 0.25) {
					watched = true;
					break;
				}
			}
			if (!someoneClose) {
				it.remove();
				continue;
			}
			if (watched) {
				entry.setValue(0);
			} else {
				int next = entry.getValue() + CHECK_INTERVAL;
				if (next >= VANISH_TICKS) {
					dissolve(level, pos, eb.hatch);
					it.remove();
				} else {
					entry.setValue(next);
				}
			}
		}
	}

	private static void dissolve(ServerLevel level, BlockPos pos, boolean hatch) {
		if (hatch) {
			level.setBlock(pos, Level4Blocks.OFFICE_CARPET.defaultBlockState(), 3);
		} else {
			level.setBlock(pos, Level4Blocks.OFFICE_DRYWALL.defaultBlockState(), 3);
			if (level.getBlockState(pos.above()).getBlock() instanceof Level4ExitBlock) {
				level.setBlock(pos.above(), Level4Blocks.OFFICE_DRYWALL_UPPER.defaultBlockState(), 3);
			}
		}
		level.playSound(null, pos, Level4Sounds.VANISH, SoundSource.BLOCKS, 0.7F, 0.8F);
		level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 14, 0.35, 0.5, 0.35, 0.02);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(Vec3.atCenterOf(pos)) <= 24 * 24) {
				player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.exit_vanished"));
			}
		}
	}
}
