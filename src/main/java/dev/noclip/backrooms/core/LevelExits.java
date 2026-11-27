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

	/** Every level someone can be sent to right now. */
	public static java.util.Set<String> openLevels() {
		return java.util.Set.copyOf(OPEN.keySet());
	}

	public static boolean isOpen(String level) {
		return OPEN.containsKey(level);
	}

	private static final java.util.List<java.util.function.BiConsumer<ServerPlayer, String>> LISTENERS = new java.util.ArrayList<>();

	/** Called with every exit anyone uses, whether it leads anywhere yet or not (for secret routes). */
	public static void onUse(java.util.function.BiConsumer<ServerPlayer, String> listener) {
		LISTENERS.add(listener);
	}

	private static final Map<UUID, Integer> LOCKED_UNTIL = new HashMap<>();
	private static final Map<UUID, int[]> ANCHORS = new HashMap<>();
	/** How far a trip through an exit lands from the exit, in blocks, and how far out anyone is let go. */
	private static final int NEAR = 40_000, SPREAD = 120_000, LIMIT = 2_000_000;

	/** For this long no exit takes the player anywhere (a teleport berry's price). */
	public static void lock(ServerPlayer player, int ticks) {
		LOCKED_UNTIL.put(player.getUUID(), player.level().getServer().getTickCount() + ticks);
	}

	/** Takes the player through an exit to the given level, or tells them nothing is there yet. */
	public static void use(ServerPlayer player, String level) {
		MinecraftServer server = player.level().getServer();
		for (var listener : LISTENERS) listener.accept(player, level);
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
		if (!LevelGates.tryPass(player, level)) {
			return;
		}
		var from = player.level().dimension();
		// Not in the middle of an interaction or a movement tick; and only once, however often it was asked for.
		server.schedule(new TickTask(server.getTickCount(), () -> {
			if (player.isRemoved() || player.hasDisconnected() || !player.isAlive()) return;
			if (player.level().dimension() != from) return;
			ANCHORS.put(player.getUUID(), fresh(player, level));
			try {
				arrival.arrive(player);
			} finally {
				ANCHORS.remove(player.getUUID());
			}
		}));
	}

	/**
	 * Drops the player into a level as if they had fallen in where they stand: no exit, no lock, nothing refused. For
	 * the Backrooms pulling someone back in (core.trip.Pull), waking up lost after dying, and M.E.G. contracts.
	 */
	public static boolean fallInto(ServerPlayer player, String level) {
		Arrival arrival = OPEN.get(level);
		if (arrival == null) return false;
		MinecraftServer server = player.level().getServer();
		var from = player.level().dimension();
		server.schedule(new TickTask(server.getTickCount(), () -> {
			if (player.isRemoved() || player.hasDisconnected() || !player.isAlive()) return;
			if (player.level().dimension() != from) return;
			// The team's stretch of that level (core.trip.Expedition): whoever falls in after the first lands near them.
			ANCHORS.put(player.getUUID(), dev.noclip.backrooms.core.trip.Expedition.zone(server, level));
			try {
				arrival.arrive(player);
			} finally {
				ANCHORS.remove(player.getUUID());
			}
		}));
		return true;
	}

	/**
	 * Where a level should put a player who is arriving, as {x, z}. Through an exit it is a stretch of the level far
	 * from the exit, so that coming back to a level is coming to a new part of it, as in 狐霖's Backrooms (docs/project/1-gameplay/README.md);
	 * it depends only on where the exit is and where it leads, so whoever takes the same exit lands together.
	 * Falling in from outside (core.trip.Expedition) it is the team's stretch of that level; otherwise (commands) it is
	 * where the player stands.
	 */
	public static int[] anchor(ServerPlayer player) {
		int[] at = ANCHORS.get(player.getUUID());
		return at != null ? at : new int[] {player.getBlockX(), player.getBlockZ()};
	}

	private static int[] fresh(ServerPlayer player, String level) {
		int x = player.getBlockX(), z = player.getBlockZ();
		long h = player.level().dimension().identifier().toString().hashCode() * 0x9E3779B97F4A7C15L;
		h = (h ^ level.hashCode()) * 0xBF58476D1CE4E5B9L;
		h = (h ^ Math.floorDiv(x, 32)) * 0x94D049BB133111EBL;
		h = (h ^ Math.floorDiv(z, 32)) * 0xBF58476D1CE4E5B9L;
		h ^= h >>> 31;
		double angle = (h >>> 11) * 0x1.0p-53 * Math.PI * 2;
		int distance = NEAR + (int) Math.floorMod(h, (long) SPREAD);
		return new int[] {fold(x + (int) Math.round(Math.cos(angle) * distance)), fold(z + (int) Math.round(Math.sin(angle) * distance))};
	}

	/** Bounces a coordinate that would go past LIMIT back toward the middle, well inside the world border. */
	private static int fold(int v) {
		return Math.abs(v) <= LIMIT ? v : Integer.signum(v) * (2 * LIMIT - Math.abs(v));
	}

	private static void refuse(ServerPlayer player, String level, int now) {
		Integer last = LAST_REFUSED.get(player.getUUID());
		// The tick count starts again from zero in every world loaded, while this map lives as long as the game.
		if (last != null && now >= last && now - last < REFUSE_INTERVAL) return;
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
