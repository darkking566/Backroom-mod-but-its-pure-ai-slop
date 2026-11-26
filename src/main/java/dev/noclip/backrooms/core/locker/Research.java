package dev.noclip.backrooms.core.locker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.crafting.StationRecipe;
import dev.noclip.backrooms.crafting.StationType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Team-wide research-pack progression (docs/project/3-crafting/README.md):
 * loading science packs into the institute's {@code anomaly_research_terminal} and returning from a trip
 * permanently unlocks higher-tier Spiderweb Crafting Table recipes:
 * <ul>
 *   <li>{@link Tier#YELLOW} ({@code science_pack_yellow}): unlocks 7x7 Primary Spiderweb recipes;</li>
 *   <li>{@link Tier#GRAY} ({@code science_pack_gray}): unlocks 9x9 Ultimate Spiderweb endgame gear;</li>
 *   <li>{@link Tier#PURPLE} ({@code science_pack_purple}): unlocks the 9x9 Wretch Cure endgame protocol.</li>
 * </ul>
 */
public final class Research {
	public enum Tier {
		YELLOW("yellow", "science_pack_yellow"),
		GRAY("gray", "science_pack_gray"),
		PURPLE("purple", "science_pack_purple");

		private final String id;
		private final String packItemPath;

		Tier(String id, String packItemPath) {
			this.id = id;
			this.packItemPath = packItemPath;
		}

		public String id() {
			return id;
		}

		public String packItemPath() {
			return packItemPath;
		}

		public Component title() {
			return Component.translatable("research.noclip_backrooms." + id);
		}

		public Component packName() {
			return Component.translatable("item.noclip_backrooms." + packItemPath);
		}

		public static Tier byPackPath(String path) {
			for (Tier t : values()) {
				if (t.packItemPath.equals(path)) return t;
			}
			return null;
		}

		public static Tier byId(String id) {
			if (id == null) return null;
			String lower = id.toLowerCase(Locale.ROOT);
			for (Tier t : values()) {
				if (t.id.equals(lower)) return t;
			}
			return null;
		}
	}

	private static volatile Set<String> clientUnlocked = Set.of();

	private Research() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(ResearchPayload.TYPE, ResearchPayload.STREAM_CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player, data(server)));
		registerCommands();
	}

	/** Called on the client when {@link ResearchPayload} arrives or on disconnect. */
	public static void setClientUnlocked(List<String> ids) {
		clientUnlocked = Set.copyOf(ids);
	}

	public static State data(MinecraftServer server) {
		return State.get(server.overworld());
	}

	/** Which research tier gates this workstation recipe, or {@code null} if freely craftable. */
	public static Tier requiredTier(StationRecipe recipe) {
		if (recipe == null) return null;
		for (StationRecipe.ItemCount out : recipe.outputs()) {
			if ("wretch_cure".equals(out.itemId().getPath())) {
				return Tier.PURPLE;
			}
		}
		if (recipe.station() == StationType.ULTIMATE_SPIDERWEB) return Tier.GRAY;
		if (recipe.station() == StationType.SPIDERWEB) return Tier.YELLOW;
		return null;
	}

	public static boolean isUnlocked(MinecraftServer server, Tier tier) {
		return tier == null || data(server).unlocked.contains(tier.id());
	}

	public static boolean canCraft(Player player, StationRecipe recipe) {
		Tier req = requiredTier(recipe);
		if (req == null || player == null || player.isCreative()) return true;
		if (player instanceof ServerPlayer sp) {
			return isUnlocked(sp.level().getServer(), req);
		}
		return clientUnlocked.contains(req.id());
	}

	/** Permanently unlocks {@code tier} for the team; returns true if it was newly unlocked. */
	public static boolean unlock(MinecraftServer server, Tier tier) {
		if (tier == null) return false;
		State state = data(server);
		if (!state.unlocked.add(tier.id())) return false;
		state.setDirty();
		sendAll(server, state);
		Component msg = Component.translatable("message.noclip_backrooms.research.unlocked", tier.title())
				.withStyle(ChatFormatting.AQUA);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.sendSystemMessage(msg);
		}
		return true;
	}

	public static void reset(MinecraftServer server) {
		State state = data(server);
		state.unlocked.clear();
		state.setDirty();
		sendAll(server, state);
	}

	public static void send(ServerPlayer player, State state) {
		ServerPlayNetworking.send(player, new ResearchPayload(new ArrayList<>(state.unlocked)));
	}

	private static void sendAll(MinecraftServer server, State state) {
		ResearchPayload payload = new ResearchPayload(new ArrayList<>(state.unlocked));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private static void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
			var unlock = Commands.literal("unlock");
			for (Tier tier : Tier.values()) {
				unlock.then(Commands.literal(tier.id()).executes(ctx -> {
					unlock(ctx.getSource().getServer(), tier);
					return 1;
				}));
			}
			unlock.then(Commands.literal("all").executes(ctx -> {
				for (Tier tier : Tier.values()) unlock(ctx.getSource().getServer(), tier);
				return 1;
			}));
			dispatcher.register(Commands.literal("backrooms")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.literal("research")
							.then(unlock)
							.then(Commands.literal("reset").executes(ctx -> {
								reset(ctx.getSource().getServer());
								ctx.getSource().sendSuccess(() -> Component.literal("Reset all M.E.G. research unlocks."), false);
								return 1;
							}))
							.then(Commands.literal("status").executes(ctx -> {
								State s = data(ctx.getSource().getServer());
								ctx.getSource().sendSuccess(() -> Component.literal("Research unlocked: " + s.unlocked), false);
								return 1;
							}))));
		});
	}

	public static final class State extends SavedData {
		private static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.listOf().optionalFieldOf("unlocked", List.of()).forGetter(s -> new ArrayList<>(s.unlocked))
		).apply(i, State::new));
		private static final SavedDataType<State> TYPE = new SavedDataType<>(BackroomsMod.id("meg_research"), State::new, CODEC,
				DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

		final Set<String> unlocked = new HashSet<>();

		public State() {
		}

		State(List<String> unlocked) {
			this.unlocked.addAll(unlocked);
		}

		static State get(ServerLevel overworld) {
			return overworld.getDataStorage().computeIfAbsent(TYPE);
		}

		public Set<String> unlockedIds() {
			return Set.copyOf(unlocked);
		}
	}
}
