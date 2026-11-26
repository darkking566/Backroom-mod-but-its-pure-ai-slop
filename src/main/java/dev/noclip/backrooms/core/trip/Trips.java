package dev.noclip.backrooms.core.trip;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.floors.FloorRun;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

/**
 * A trip into the Backrooms and back (docs/project/1-gameplay/README.md):
 * <ul>
 * <li>settling at a base (a cot in a settlement) makes it where the player wakes up, for the rest of the trip;</li>
 * <li>from there the people of the base, whichever group runs it (M.E.G. or another), can take them home
 * ({@code /meg extract}, offered in chat when they settle);</li>
 * <li>dying in the Backrooms without having settled means waking up lost in Level 0, with everything left where they
 * fell;</li>
 * <li>back home, the spot they woke up at before the trip is theirs again, and the Backrooms leave them alone for a day
 * (core.trip.Pull).</li>
 * </ul>
 */
public final class Trips {
	/** Where the player woke up before settling in the Backrooms (empty: the world spawn), given back once home. */
	public static final AttachmentType<Home> HOME = AttachmentRegistry.create(BackroomsMod.id("trip_home"),
			builder -> builder.persistent(Home.CODEC).copyOnDeath());
	/** How near the cot M.E.G. still counts the player as at their base. */
	static final double AT_BASE = 8.0;
	static final int SETTLE_XP = 50;
	/** Walking out alive: this much, and this much more for every floor of the trip. */
	static final int ESCAPE_XP = 30, ESCAPE_XP_PER_FLOOR = 20;
	private static final Set<UUID> LOST = new HashSet<>();

	public record Home(Optional<ServerPlayer.RespawnConfig> respawn) {
		static final Codec<Home> CODEC = RecordCodecBuilder.create(i -> i.group(
				ServerPlayer.RespawnConfig.CODEC.optionalFieldOf("respawn").forGetter(Home::respawn)).apply(i, Home::new));
	}

	private Trips() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player && inBackrooms(player.level().dimension()) && !settled(player)) {
				LOST.add(player.getUUID());
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> {
			if (alive || !LOST.remove(player.getUUID())) return;
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.trip.lost").withStyle(ChatFormatting.GRAY));
			LevelExits.fallInto(player, "0");
		});
		Floors.onHome(Trips::cameHome);
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
				Commands.literal("meg").then(Commands.literal("extract")
						.executes(ctx -> extract(ctx.getSource().getPlayerOrException()) ? 1 : 0))));
	}

	static boolean inBackrooms(ResourceKey<Level> dimension) {
		return dimension.identifier().getNamespace().equals(BackroomsMod.MOD_ID);
	}

	/** Whether the player has settled somewhere in the Backrooms: they wake up there. */
	public static boolean settled(ServerPlayer player) {
		ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
		return respawn != null && inBackrooms(respawn.respawnData().dimension());
	}

	/** Settling at a base: from now on the player wakes up here, until M.E.G. takes them home. */
	public static void settle(ServerPlayer player, LevelData.RespawnData spot) {
		ServerPlayer.RespawnConfig before = player.getRespawnConfig();
		if (!player.hasAttached(HOME)) {
			player.setAttached(HOME, new Home(Optional.ofNullable(before).filter(c -> !inBackrooms(c.respawnData().dimension()))));
		}
		boolean elsewhere = before == null || before.respawnData().dimension() != spot.dimension()
				|| !before.respawnData().pos().closerThan(spot.pos(), 24);
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(spot, true), false);
		if (elsewhere) PlayerRpg.grantXp(player, SETTLE_XP);
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.trip.extract_offer").withStyle(ChatFormatting.GRAY)
				.append(" ")
				.append(Component.translatable("message.noclip_backrooms.trip.extract_button").withStyle(style -> style
						.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand("/meg extract"))
						.withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.noclip_backrooms.trip.extract_hover"))))));
	}

	/** M.E.G. takes a settled player home from their base. */
	public static boolean extract(ServerPlayer player) {
		if (!inBackrooms(player.level().dimension()) || !settled(player)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.trip.extract_not_settled"));
			return false;
		}
		LevelData.RespawnData base = player.getRespawnConfig().respawnData();
		if (base.dimension() != player.level().dimension() || !player.blockPosition().closerThan(base.pos(), AT_BASE)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.trip.extract_too_far"));
			return false;
		}
		restoreHome(player);
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.trip.extracting").withStyle(ChatFormatting.GRAY));
		MinecraftServer server = player.level().getServer();
		server.schedule(new TickTask(server.getTickCount(), () -> sendHome(player)));
		return true;
	}

	private static void sendHome(ServerPlayer player) {
		if (player.isRemoved() || player.hasDisconnected() || !player.isAlive()) return;
		if (!inBackrooms(player.level().dimension())) return;
		TeleportTransition destination = player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.PLAY_PORTAL_SOUND);
		if (inBackrooms(destination.newLevel().dimension())) {
			ServerLevel overworld = player.level().getServer().overworld();
			Vec3 spawn = Vec3.atBottomCenterOf(player.adjustSpawnLocation(overworld, overworld.getRespawnData().pos()));
			destination = new TeleportTransition(overworld, spawn, Vec3.ZERO, 0, 0, TeleportTransition.PLAY_PORTAL_SOUND);
		}
		player.stopRiding();
		player.resetFallDistance();
		player.teleport(destination);
	}

	/** Gives back the spot the player woke up at before settling in the Backrooms. */
	private static void restoreHome(ServerPlayer player) {
		Home home = player.getAttached(HOME);
		if (settled(player)) player.setRespawnPosition(home == null ? null : home.respawn().orElse(null), false);
		player.removeAttached(HOME);
	}

	/** Out of the Backrooms alive, however they got out. */
	private static void cameHome(ServerPlayer player, FloorRun run) {
		restoreHome(player);
		// The first time anyone gets out, the team's M.E.G. cycle starts (the days at home count from then on).
		dev.noclip.backrooms.core.quota.Quota.onHome(player);
		PlayerRpg.grantXp(player, ESCAPE_XP + ESCAPE_XP_PER_FLOOR * run.floor());
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.trip.home", run.floor()).withStyle(ChatFormatting.GRAY));
	}
}
