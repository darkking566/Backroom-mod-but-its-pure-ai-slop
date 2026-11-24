package dev.noclip.backrooms.level1;

import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "Flickering": at random times every working light in Level 1 goes out, for an unpredictable while.
 * A buzz and a few stutters come first. Green signs and exit lights run on their own power and stay on,
 * which is how you find your way. Time only passes while someone is in Level 1.
 *
 * Lights are switched where they are: within reach of each player, on a grid the floor plan fixes.
 * The same sweep keeps newly loaded chunks in step with the current state.
 */
final class Blackouts {
	private static final int FIRST_DELAY = 90 * 20, DELAY = 3 * 60 * 20, DELAY_SPREAD = 3 * 60 * 20;
	private static final int DARK = 15 * 20, DARK_SPREAD = 30 * 20;
	private static final int WARNING = 60, RESTORING = 30;
	private static final int REACH = 48;
	private static final int SYNC_INTERVAL = 10;

	enum Phase {
		NORMAL, WARNING, DARK, RESTORING
	}

	private static Phase phase = Phase.NORMAL;
	private static int timer = -1;
	private static boolean lightsOn = true;

	private Blackouts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Blackouts::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> reset());
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

	/** No blackout for the next while, for tests. */
	static void holdOff(int ticks) {
		phase = Phase.NORMAL;
		timer = ticks;
		lightsOn = true;
	}

	private static void reset() {
		phase = Phase.NORMAL;
		timer = -1;
		lightsOn = true;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
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
					play(level, players, Level1Sounds.POWER_FAILING, 1.0F, 1.0F);
				}
			}
			case WARNING -> {
				// Three stutters before it goes.
				lightsOn = !(timer <= 48 && timer > 44 || timer <= 30 && timer > 27 || timer <= 14 && timer > 9);
				if (timer <= 0) {
					phase = Phase.DARK;
					timer = DARK + random.nextInt(DARK_SPREAD);
					lightsOn = false;
					play(level, players, Level1Sounds.POWER_OFF, 1.0F, 0.6F);
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
				lightsOn = timer <= 24 && timer > 18 || timer <= 10;
				if (timer <= 0) {
					phase = Phase.NORMAL;
					timer = DELAY + random.nextInt(DELAY_SPREAD);
					lightsOn = true;
					play(level, players, Level1Sounds.POWER_ON, 0.8F, 0.8F);
					// Supplies tend to turn up after a Flickering.
					Crates.afterFlickering(level);
				}
			}
		}
		if (lightsOn != wasOn || server.getTickCount() % SYNC_INTERVAL == 0) sync(level, players);
	}

	private static void play(ServerLevel level, List<ServerPlayer> players, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		for (ServerPlayer player : players) {
			level.playSound(null, player.getX(), player.getY() + 3, player.getZ(), sound, SoundSource.AMBIENT, volume, pitch);
		}
	}

	/** Sets every working light near every player to the current state. */
	static void sync(ServerLevel level, List<ServerPlayer> players) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (ServerPlayer player : players) {
			int px = player.getBlockX(), pz = player.getBlockZ();
			// Hall lights: x = 5 (mod 9), z = 4 or 12 (mod 16), across all vertical deck and vaulted ceiling heights.
			for (int x = px - REACH - Math.floorMod(px - REACH - 5, Level1Layout.BAY_X); x <= px + REACH; x += Level1Layout.BAY_X) {
				for (int z = pz - REACH - Math.floorMod(pz - REACH - 4, 8); z <= pz + REACH; z += 8) {
					if (Math.floorMod(z, Level1Layout.BAY_Z) == 4 || Math.floorMod(z, Level1Layout.BAY_Z) == 12) {
						for (int hallY : Level1Elevation.LAMP_YS) apply(level, pos.set(x, hallY, z));
					}
				}
			}
			// Corridor bulbs: the middle of every 4-block cell, just under the low ceiling.
			int bulbY = Level1ChunkGenerator.LOW_CEILING_Y - 1;
			for (int x = px - REACH - Math.floorMod(px - REACH - 2, 4); x <= px + REACH; x += 4) {
				for (int z = pz - REACH - Math.floorMod(pz - REACH - 2, 4); z <= pz + REACH; z += 4) {
					apply(level, pos.set(x, bulbY, z));
				}
			}
		}
	}

	private static void apply(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) return;
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof Level1LampBlock && state.getValue(Level1LampBlock.LIT) != lightsOn) {
			level.setBlock(pos, state.setValue(Level1LampBlock.LIT, lightsOn), Block.UPDATE_CLIENTS);
		}
	}
}
