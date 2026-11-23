package dev.noclip.backrooms.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Where exits lead. Every exit in the Backrooms names the level on its other side ("0", "4.3", "188", ...).
 * A level that exists registers how players arrive in it with {@link #open}; until then, every exit
 * leading there stays shut and says so. Adding a level later opens all the ways into it at once.
 */
public final class LevelExits {
	/** Puts a player into a level; returns whether they were moved. */
	public interface Arrival {
		boolean arrive(ServerPlayer player);
	}

	private static final Map<String, Arrival> OPEN = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> LAST_REFUSED = new HashMap<>();
	private static final int REFUSE_INTERVAL = 40;
	private static int refusals;

	private LevelExits() {
	}

	public static void open(String level, Arrival arrival) {
		OPEN.put(level, arrival);
	}

	public static boolean isOpen(String level) {
		return OPEN.containsKey(level);
	}

	private static final Map<UUID, Integer> LOCKED_UNTIL = new HashMap<>();

	/** For this long no exit takes the player anywhere (a teleport berry's price). */
	public static void lock(ServerPlayer player, int ticks) {
		LOCKED_UNTIL.put(player.getUUID(), player.level().getServer().getTickCount() + ticks);
	}

	/** Takes the player through an exit to the given level, or tells them nothing is there yet. */
	public static void use(ServerPlayer player, String level) {
		MinecraftServer server = player.level().getServer();
		Integer lockedUntil = LOCKED_UNTIL.get(player.getUUID());
		if (lockedUntil != null && server.getTickCount() < lockedUntil) {
			Integer last = LAST_REFUSED.get(player.getUUID());
			if (last == null || server.getTickCount() - last >= REFUSE_INTERVAL) {
				LAST_REFUSED.put(player.getUUID(), server.getTickCount());
				player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.exit_locked"));
			}
			return;
		}
		Arrival arrival = OPEN.get(level);
		if (arrival == null) {
			refuse(player, level, server.getTickCount());
			return;
		}
		var from = player.level().dimension();
		// Not in the middle of an interaction or a movement tick; and only once, however often it was asked for.
		server.schedule(new TickTask(server.getTickCount(), () -> {
			if (player.isRemoved() || player.hasDisconnected() || !player.isAlive()) return;
			if (player.level().dimension() != from) return;
			arrival.arrive(player);
		}));
	}

	private static void refuse(ServerPlayer player, String level, int now) {
		Integer last = LAST_REFUSED.get(player.getUUID());
		if (last != null && now - last < REFUSE_INTERVAL) return;
		LAST_REFUSED.put(player.getUUID(), now);
		refusals++;
		player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.exit_closed", level));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8F, 0.7F);
	}

	/** How many times an exit has refused someone since the game started, for tests. */
	public static int refusals() {
		return refusals;
	}
}
