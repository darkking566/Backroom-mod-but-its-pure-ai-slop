package dev.noclip.backrooms.core.economy;

import com.mojang.serialization.Codec;
import dev.noclip.backrooms.BackroomsMod;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The permanent money account of the design spec (docs/project, sections 2.3 and 4.1): credits kept with the player through
 * death and every trip, never dropped. Coins found in the Backrooms are put into it (CoinItem); the guild's merchants
 * take and pay in it (core.trade). Sent to that player only, for the inventory and the shop screens.
 */
public final class Wallet {
	public static final AttachmentType<Long> CREDITS = AttachmentRegistry.create(BackroomsMod.id("credits"),
			builder -> builder
					.initializer(() -> 0L)
					.persistent(Codec.LONG)
					.copyOnDeath()
					.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	private Wallet() {
	}

	public static long of(Player player) {
		return player.getAttachedOrElse(CREDITS, 0L);
	}

	public static void add(ServerPlayer player, long amount) {
		player.setAttached(CREDITS, Math.max(0L, of(player) + amount));
	}

	public static void set(ServerPlayer player, long amount) {
		player.setAttached(CREDITS, Math.max(0L, amount));
	}

	/** Takes the credits if the player has them; returns whether it did. */
	public static boolean take(ServerPlayer player, long amount) {
		long have = of(player);
		if (amount < 0 || have < amount) return false;
		player.setAttached(CREDITS, have - amount);
		return true;
	}
}
