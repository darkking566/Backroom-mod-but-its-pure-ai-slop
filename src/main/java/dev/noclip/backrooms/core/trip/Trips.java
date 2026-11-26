package dev.noclip.backrooms.core.trip;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.floors.FloorRun;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.locker.Lockers;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

/**
 * A trip into the Backrooms and back (docs/project/1-gameplay/README.md, "死亡" and "安頓與撤離"):
 * <ul>
 * <li>settling at a base (a cot in a settlement) makes it where the player wakes up, for the rest of the trip; from
 * there they can call the people of the base, whichever group runs it, for a pickup ({@code /meg extract}, offered in
 * chat when they settle) and hold out until it comes (core.trip.Extraction);</li>
 * <li>dying in the Backrooms without having settled means waking up where this trip came into the Backrooms, back at
 * its first floor; either way everything they carried stays where they fell;</li>
 * <li>dying outside the Backrooms, once they have been in, means falling back into Level 0 (the team's stretch of it,
 * core.trip.Expedition), and M.E.G. puts what they carried in their locker at the institute (core.locker.Lockers);
 * someone who has never been in dies as the Overworld has it;</li>
 * <li>getting out takes them to the M.E.G. institute in the Frontrooms (core.Home), with a mission report.</li>
 * </ul>
 */
public final class Trips {
	/** Where the player woke up before settling in the Backrooms (empty: the world spawn), given back once home. */
	public static final AttachmentType<Before> HOME = AttachmentRegistry.create(BackroomsMod.id("trip_home"),
			builder -> builder.persistent(Before.CODEC).copyOnDeath());
	/** Where this trip came into the Backrooms: dying without having settled wakes the player up here. */
	public static final AttachmentType<Entry> ENTRY = AttachmentRegistry.create(BackroomsMod.id("trip_entry"),
			builder -> builder.persistent(Entry.CODEC).copyOnDeath());
	/** How near the cot M.E.G. still counts the player as at their base. */
	static final double AT_BASE = 8.0;
	static final int SETTLE_XP = 50;
	/** Walking out alive: this much, and this much more for every floor of the trip. */
	static final int ESCAPE_XP = 30, ESCAPE_XP_PER_FLOOR = 20;

	public record Before(Optional<ServerPlayer.RespawnConfig> respawn) {
		static final Codec<Before> CODEC = RecordCodecBuilder.create(i -> i.group(
				ServerPlayer.RespawnConfig.CODEC.optionalFieldOf("respawn").forGetter(Before::respawn)).apply(i, Before::new));
	}

	public record Entry(String dimension, BlockPos pos, float yaw) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("dimension").forGetter(Entry::dimension),
				BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
				Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(Entry::yaw)).apply(i, Entry::new));
	}

	private Trips() {
	}

	public static void register() {
		// Dying outside the Backrooms once they have been in: M.E.G. collects what they carried.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && Home.isHome(player.level().dimension()) && Home.entered(player)
					&& !((ServerLevel) player.level()).getGameRules().get(GameRules.KEEP_INVENTORY)) {
				int taken = Lockers.collect(player);
				if (taken > 0) player.sendSystemMessage(Component.translatable("message.noclip_backrooms.trip.relics", taken).withStyle(ChatFormatting.GRAY));
			}
			return true;
		});
		// Asked of the body left behind, which stays where it died even across a reload of the world (and whose records
		// are complete: the new body's may not have been copied over yet when this runs).
		ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> {
			if (alive) return;
			if (Home.inBackrooms(old.level().dimension())) {
				if (settled(old)) return;
				player.sendSystemMessage(Component.translatable("message.noclip_backrooms.trip.lost").withStyle(ChatFormatting.GRAY));
				MinecraftServer server = player.level().getServer();
				server.schedule(new TickTask(server.getTickCount(), () -> backToEntry(player)));
			} else if (Home.entered(old)) {
				player.sendSystemMessage(Component.translatable("message.noclip_backrooms.trip.fell_back").withStyle(ChatFormatting.DARK_RED));
				LevelExits.fallInto(player, "0");
			}
		});
		Floors.onArrive((player, level, run, newLevel) -> {
			if (run.floor() == 1) {
				player.setAttached(ENTRY, new Entry(player.level().dimension().identifier().toString(), player.blockPosition(), player.getYRot()));
			}
		});
		Floors.onHome(Trips::cameHome);
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
			dispatcher.register(Commands.literal("meg").then(Commands.literal("extract")
					.executes(ctx -> extract(ctx.getSource().getPlayerOrException()) ? 1 : 0)));
			dispatcher.register(Commands.literal("backrooms").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.literal("extract").then(Commands.literal("now").executes(ctx -> {
						Extraction.finishNow(ctx.getSource().getPlayerOrException());
						return 1;
					}))));
		});
	}

	/** Back where this trip came in, at its first floor (Level 0 if the player has no record of it). */
	private static void backToEntry(ServerPlayer player) {
		if (player.isRemoved() || player.hasDisconnected() || !player.isAlive()) return;
		Entry entry = player.getAttached(ENTRY);
		ServerLevel level = entry == null ? null
				: player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(entry.dimension())));
		if (level == null || !Home.inBackrooms(level.dimension())) {
			LevelExits.fallInto(player, "0");
			return;
		}
		level.getChunk(entry.pos());
		player.stopRiding();
		player.resetFallDistance();
		player.teleport(new net.minecraft.world.level.portal.TeleportTransition(level, Vec3.atBottomCenterOf(entry.pos()), Vec3.ZERO,
				entry.yaw(), 0, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
	}

	static boolean inBackrooms(ResourceKey<Level> dimension) {
		return Home.inBackrooms(dimension);
	}

	/** Whether the player has settled somewhere in the Backrooms: they wake up there. */
	public static boolean settled(ServerPlayer player) {
		ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
		return respawn != null && inBackrooms(respawn.respawnData().dimension());
	}

	/** Settling at a base: from now on the player wakes up here, until they are picked up. */
	public static void settle(ServerPlayer player, LevelData.RespawnData spot) {
		ServerPlayer.RespawnConfig before = player.getRespawnConfig();
		if (!player.hasAttached(HOME)) {
			player.setAttached(HOME, new Before(Optional.ofNullable(before).filter(c -> !inBackrooms(c.respawnData().dimension()))));
		}
		// Once a trip and level: moving from cot to cot inside a base earns nothing.
		boolean elsewhere = before == null || before.respawnData().dimension() != spot.dimension();
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(spot, true), false);
		if (elsewhere) PlayerRpg.grantXp(player, SETTLE_XP);
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.trip.extract_offer").withStyle(ChatFormatting.GRAY)
				.append(" ")
				.append(Component.translatable("message.noclip_backrooms.trip.extract_button").withStyle(style -> style
						.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand("/meg extract"))
						.withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.noclip_backrooms.trip.extract_hover"))))));
	}

	/** A settled player calls their base's people for a pickup, and holds out at the base until it comes. */
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
		return Extraction.start(player, Vec3.atBottomCenterOf(base.pos()), AT_BASE + 4);
	}

	/** Gives back the spot the player woke up at before settling in the Backrooms. */
	private static void restoreHome(ServerPlayer player) {
		Before home = player.getAttached(HOME);
		if (settled(player)) player.setRespawnPosition(home == null ? null : home.respawn().orElse(null), false);
		player.removeAttached(HOME);
	}

	/** Out of the Backrooms alive, however they got out. */
	private static void cameHome(ServerPlayer player, FloorRun run) {
		restoreHome(player);
		player.removeAttached(ENTRY);
		// The first time anyone gets out, the team's M.E.G. cycle starts (the days count from then on).
		dev.noclip.backrooms.core.quota.Quota.onHome(player);
		PlayerRpg.grantXp(player, ESCAPE_XP + ESCAPE_XP_PER_FLOOR * run.floor());
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.trip.home", run.floor()).withStyle(ChatFormatting.GRAY));
		dev.noclip.backrooms.core.radio.Radio.issue(player);
		Report.send(player, run);
	}
}
