package dev.noclip.backrooms.level0p1;

import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;

/**
 * A power failure on the whole station: the alarm sounds, the ceiling lights stutter and go out, and some while
 * later they come back. Stair cores, rail lamps, glow orbs and the girder lamps run on their own power and stay lit,
 * which is how you find the stairs. Lights the station never had (dead stretches) stay dead.
 * Time only passes while someone is on the station.
 *
 * Lights are swapped near each player (REACH); the same sweep, run now and then even in NORMAL, brings chunks loaded
 * later in step with the current state.
 */
final class ZenithBlackouts {
	private static final int FIRST_DELAY = 3 * 60 * 20, DELAY = 6 * 60 * 20, DELAY_SPREAD = 4 * 60 * 20;
	private static final int DARK = 20 * 20, DARK_SPREAD = 30 * 20;
	private static final int WARNING = 80, RESTORING = 40;
	// ABOVE reaches a two-storey room's ceiling (14) and the hydroponic atrium's (46) from their floors.
	private static final int REACH = 24, BELOW = 6, ABOVE = 48;
	private static final int SYNC_INTERVAL = 20, NORMAL_SYNC_INTERVAL = 100;

	enum Phase {
		NORMAL, WARNING, DARK, RESTORING
	}

	private static Phase phase = Phase.NORMAL;
	private static int timer = -1;
	private static boolean lightsOn = true;

	private ZenithBlackouts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ZenithBlackouts::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> holdOff(-1));
	}

	static Phase phase() {
		return phase;
	}

	/** Starts the warning now, for tests and debugging. */
	static void startNow() {
		phase = Phase.WARNING;
		timer = WARNING;
	}

	/** Ends a blackout now, for tests and debugging. */
	static void endNow() {
		phase = Phase.RESTORING;
		timer = 1;
	}

	/** No blackout for the next ticks (-1: draw a fresh first delay), for tests. */
	static void holdOff(int ticks) {
		phase = Phase.NORMAL;
		timer = ticks;
		lightsOn = true;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level0p1.LEVEL_0_1);
		if (level == null) return;
		List<ServerPlayer> players = level.players();
		if (players.isEmpty()) return;
		RandomSource random = level.getRandom();
		if (timer < 0) timer = FIRST_DELAY + random.nextInt(DELAY_SPREAD);

		boolean wasOn = lightsOn;
		timer--;
		switch (phase) {
			case NORMAL -> {
				lightsOn = true;
				if (timer <= 0) {
					phase = Phase.WARNING;
					timer = WARNING;
					play(level, players, ZenithSounds.ALARM, 0.8F, 1.2F);
					tell(players, "blackout.warning");
				}
			}
			case WARNING -> {
				// Three stutters before it goes.
				lightsOn = !(timer <= 60 && timer > 55 || timer <= 38 && timer > 34 || timer <= 18 && timer > 10);
				if (timer <= 0) {
					phase = Phase.DARK;
					timer = DARK + random.nextInt(DARK_SPREAD);
					lightsOn = false;
					play(level, players, ZenithSounds.CREAK, 1.0F, 0.5F);
					tell(players, "blackout.dark");
				}
			}
			case DARK -> {
				lightsOn = false;
				if (timer <= 0) {
					phase = Phase.RESTORING;
					timer = RESTORING;
				}
			}
			case RESTORING -> {
				lightsOn = timer <= 30 && timer > 24 || timer <= 12;
				if (timer <= 0) {
					phase = Phase.NORMAL;
					timer = DELAY + random.nextInt(DELAY_SPREAD);
					lightsOn = true;
					play(level, players, ZenithSounds.CHIME, 0.8F, 0.8F);
					tell(players, "blackout.restored");
				}
			}
		}
		int every = phase == Phase.NORMAL ? NORMAL_SYNC_INTERVAL : SYNC_INTERVAL;
		if (lightsOn != wasOn || server.getTickCount() % every == 0) sync(level, players);
	}

	private static void play(ServerLevel level, List<ServerPlayer> players, SoundEvent sound, float volume, float pitch) {
		for (ServerPlayer player : players) {
			level.playSound(null, player.getX(), player.getY() + 3, player.getZ(), sound, SoundSource.AMBIENT, volume, pitch);
		}
	}

	private static void tell(List<ServerPlayer> players, String key) {
		for (ServerPlayer player : players) player.sendSystemMessage(Component.translatable("message.noclip_backrooms.level0p1." + key));
	}

	/** Sets every ceiling light the station has near every player to the current state. */
	static void sync(ServerLevel level, List<ServerPlayer> players) {
		long seed = ZenithChunkGenerator.planSeed(level.getChunkSource().randomState());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (ServerPlayer player : players) {
			int px = player.getBlockX(), py = player.getBlockY(), pz = player.getBlockZ();
			for (int x = px - REACH; x <= px + REACH; x++) {
				for (int z = pz - REACH; z <= pz + REACH; z++) {
					for (int y = py - BELOW; y <= py + ABOVE; y++) {
						pos.set(x, y, z);
						if (!level.isLoaded(pos)) break;
						Block block = level.getBlockState(pos).getBlock();
						if (lightsOn && block == ZenithBlocks.DEAD_CEILING_LIGHT
								&& ZenithLayout.kind(ZenithLayout.cell(seed, x, y, z)) == ZenithLayout.Kind.LIGHT) {
							level.setBlock(pos, ZenithBlocks.CEILING_LIGHT.defaultBlockState(), Block.UPDATE_CLIENTS);
						} else if (!lightsOn && block == ZenithBlocks.CEILING_LIGHT) {
							level.setBlock(pos, ZenithBlocks.DEAD_CEILING_LIGHT.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					}
				}
			}
		}
	}
}
