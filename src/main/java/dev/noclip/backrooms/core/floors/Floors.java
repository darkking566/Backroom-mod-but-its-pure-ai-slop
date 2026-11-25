package dev.noclip.backrooms.core.floors;

import dev.noclip.backrooms.BackroomsMod;
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
 * The floor count of docs/FLOORS.md. Every time a player comes into a Backrooms level from another one it is one floor
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

	private Floors() {
	}

	public static void register() {
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(Floors::changed);
		// Waking up in the Overworld after dying ends the trip; waking up at a base in the Backrooms carries on.
		ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> {
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
		String level = key(to);
		if (level == null) {
			end(player);
			return;
		}
		FloorRun run = of(player);
		player.setAttached(RUN, key(from) == null || run.floor() == 0 ? run.start(level) : run.enter(level));
		announce(player, level);
	}

	private static void end(ServerPlayer player) {
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
