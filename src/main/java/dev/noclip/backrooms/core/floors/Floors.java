package dev.noclip.backrooms.core.floors;

import dev.noclip.backrooms.BackroomsMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The floor count of docs/project/1-gameplay/README.md. Every time a player comes into a Backrooms level from another one it is one floor
 * more (going back counts too); falling in from the Overworld starts a trip at floor 1; being back in the Overworld ends
 * it. Each arrival puts the level's number and name in the middle of the screen; the client shows the floor at the top
 * left and the level in the inventory (core.floors.FloorsClient).
 */
public final class Floors {
	/** This trip, kept with the player (also through death) and sent to that player only, for the HUD. */
	public static final AttachmentType<FloorRun> RUN = AttachmentRegistry.create(BackroomsMod.id("floor_run"),
			builder -> builder
					.initializer(() -> FloorRun.NONE)
					.persistent(FloorRun.CODEC)
					.copyOnDeath()
					.syncWith(FloorRun.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Told about every arrival in a Backrooms level; {@code newLevel} when it is a level this trip had not seen. */
	@FunctionalInterface
	public interface Arrival {
		void arrived(ServerPlayer player, String level, FloorRun run, boolean newLevel);
	}

	private static final List<Arrival> ARRIVALS = new ArrayList<>();
	private static final List<BiConsumer<ServerPlayer, FloorRun>> HOMECOMINGS = new ArrayList<>();
	/** Server tick each player last woke up after dying: the change of level that comes with it is not a journey. */
	private static final Map<UUID, Integer> RESPAWNED = new HashMap<>();

	private Floors() {
	}

	public static void onArrive(Arrival listener) {
		ARRIVALS.add(listener);
	}

	/** Told when a trip ends with the player walking out alive (not dying), with the trip as it was. */
	public static void onHome(BiConsumer<ServerPlayer, FloorRun> listener) {
		HOMECOMINGS.add(listener);
	}

	public static void register() {
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(Floors::changed);
		// Waking up in the Overworld after dying ends the trip; waking up at a base in the Backrooms carries on.
		ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> {
			RESPAWNED.put(player.getUUID(), player.level().getServer().getTickCount());
			if (key(player.level()) == null) end(player);
		});
	}

	/** The level a Backrooms dimension is ("0", "0.1", "1", ...), or null outside the Backrooms. */
	public static String key(Level level) {
		Identifier id = level.dimension().identifier();
		if (!id.getNamespace().equals(BackroomsMod.MOD_ID) || !id.getPath().startsWith("level_")) return null;
		return id.getPath().substring("level_".length());
	}

	/** A level's name, from the language file ("level.noclip_backrooms.0" is "The Lobby"). */
	public static Component name(String level) {
		return Component.translatableWithFallback("level.noclip_backrooms." + level, "");
	}

	public static FloorRun of(ServerPlayer player) {
		return player.getAttachedOrElse(RUN, FloorRun.NONE);
	}

	private static void changed(ServerPlayer player, ServerLevel from, ServerLevel to) {
		// Waking up somewhere else after dying fires this too: that is handled on respawn, and is neither a way home nor
		// a floor further down.
		Integer woke = RESPAWNED.remove(player.getUUID());
		if (woke != null && woke == player.level().getServer().getTickCount()) return;
		String level = key(to);
		FloorRun run = of(player);
		if (level == null) {
			if (run.floor() != 0) for (var listener : HOMECOMINGS) listener.accept(player, run);
			end(player);
			return;
		}
		boolean starting = key(from) == null || run.floor() == 0;
		if (starting) {
			FloorDifficulty.reset(player);
			FloorDifficulty.recordTransition(player, level);
		}
		boolean pingPong = !starting && FloorDifficulty.recordTransition(player, level);
		FloorRun next = starting ? run.start(level) : (pingPong ? run : run.enter(level));
		player.setAttached(RUN, next);
		announce(player, level);
		boolean newLevel = !starting && !pingPong && next.distinct() > run.distinct();
		for (var listener : ARRIVALS) listener.arrived(player, level, next, newLevel);
	}

	private static void end(ServerPlayer player) {
		FloorDifficulty.reset(player);
		FloorRun run = of(player);
		if (run.floor() != 0) player.setAttached(RUN, run.end());
	}

	/** "Level 0" in the middle of the screen with "The Lobby" under it, for a few seconds. */
	static void announce(ServerPlayer player, String level) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 25));
		player.connection.send(new ClientboundSetSubtitleTextPacket(name(level)));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Level " + level)));
	}
}
