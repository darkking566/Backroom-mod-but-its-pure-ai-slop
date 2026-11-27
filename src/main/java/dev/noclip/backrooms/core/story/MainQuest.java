package dev.noclip.backrooms.core.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.field.FieldKit;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.monsters.Monsters;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The M.E.G. main quest (docs/企劃書/1-遊戲玩法/README.md, "M.E.G. 任務主線"): one assignment a level, in order, for the
 * whole team. The commander gives a reason for each, and the reasons fit less and less; reporting back alive with the
 * job done pays into the quota (the assignment and the quota are one job, not two), lets M.E.G. send the team to the
 * next level, and puts something new behind the glass of the containment cells in B1. The optional jobs are the
 * contracts (core.guild.Contracts).
 */
public final class MainQuest {
	/** What an assignment asks: get there and back, a photo of something, a mapping tape, kills. */
	public enum Kind {
		REACH, PHOTO, MAP, HUNT
	}

	/**
	 * The assignments, in order. {@code specimen} is what turns up in a containment cell once it is done (null: nothing).
	 */
	public enum Chapter {
		LOBBY("lobby", "0", Kind.MAP, "0", 1, 200, null),
		ZENITH("zenith", "0.1", Kind.REACH, "0.1", 1, 220, null),
		PARKING("parking", "1", Kind.PHOTO, "smiler", 1, 300, "smiler"),
		PIPES("pipes", "2", Kind.HUNT, "hound", 2, 380, "hound"),
		POWER("power", "3", Kind.REACH, "3", 1, 450, null),
		OFFICE("office", "4", Kind.PHOTO, "window_entity", 1, 520, "duller");

		public final String id, level, target, specimen;
		public final Kind kind;
		public final int goal;
		public final long reward;

		Chapter(String id, String level, Kind kind, String target, int goal, long reward, String specimen) {
			this.id = id;
			this.level = level;
			this.kind = kind;
			this.target = target;
			this.goal = goal;
			this.reward = reward;
			this.specimen = specimen;
		}

		Component title() {
			return Component.translatable("quest.noclip_backrooms." + id + ".title");
		}
	}

	private static final List<Chapter> CHAPTERS = List.of(Chapter.values());

	private MainQuest() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
			dispatcher.register(Commands.literal("meg").then(Commands.literal("story").executes(ctx -> {
				talk(ctx.getSource().getPlayerOrException());
				return 1;
			})));
			dispatcher.register(Commands.literal("backrooms").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.literal("story").then(Commands.literal("next").executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						Data data = Data.get(player.level().getServer());
						if (data.chapter >= CHAPTERS.size()) return 0;
						data.reached = true;
						data.setDirty();
						report(player);
						return 1;
					}))));
		});
		Floors.onArrive((player, level, run, newLevel) -> {
			Chapter chapter = current(player.level().getServer());
			if (chapter != null && chapter.kind == Kind.REACH && chapter.level.equals(level)) progress(player, chapter, chapter.goal);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof Monster) || !(source.getEntity() instanceof ServerPlayer player)) return;
			Chapter chapter = current(player.level().getServer());
			if (chapter == null || chapter.kind != Kind.HUNT || !chapter.level.equals(Floors.key(player.level()))) return;
			if (!BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().equals(chapter.target)) return;
			progress(player, chapter, Data.get(player.level().getServer()).progress + 1);
		});
		Floors.onHome((player, run) -> report(player));
	}

	/** The assignment the team is on, or null once they have done them all. */
	public static Chapter current(MinecraftServer server) {
		int chapter = Data.get(server).chapter;
		return chapter < CHAPTERS.size() ? CHAPTERS.get(chapter) : null;
	}

	/** Levels M.E.G. has sent the team to so far: the level of every assignment given (for sending people there). */
	public static Set<String> assigned(MinecraftServer server) {
		Set<String> levels = new LinkedHashSet<>();
		int upTo = Math.min(Data.get(server).chapter, CHAPTERS.size() - 1);
		for (int i = 0; i <= upTo; i++) levels.add(CHAPTERS.get(i).level);
		return levels;
	}

	/** The creatures M.E.G. keeps in B1's containment cells, one for each finished assignment that brought one in. */
	public static List<EntityType<?>> specimens(MinecraftServer server) {
		int done = Data.get(server).chapter;
		java.util.ArrayList<EntityType<?>> list = new java.util.ArrayList<>();
		for (int i = 0; i < Math.min(done, CHAPTERS.size()); i++) {
			String specimen = CHAPTERS.get(i).specimen;
			if (specimen != null) list.add(specimenType(specimen));
		}
		return list;
	}

	private static EntityType<?> specimenType(String id) {
		return switch (id) {
			case "smiler" -> Monsters.SMILER;
			case "hound" -> Monsters.HOUND;
			default -> Monsters.DULLER;
		};
	}

	/** A photo was taken (core.field.Evidence): of {@code subject}, in {@code level}. */
	public static void onPhoto(ServerPlayer player, String subject, String level) {
		Chapter chapter = current(player.level().getServer());
		if (chapter != null && chapter.kind == Kind.PHOTO && chapter.target.equals(subject) && chapter.level.equals(level)) {
			progress(player, chapter, chapter.goal);
		}
	}

	/** A mapping beacon finished its scan of {@code level} (core.field.Evidence). */
	public static void onTape(ServerPlayer player, String level) {
		Chapter chapter = current(player.level().getServer());
		if (chapter != null && chapter.kind == Kind.MAP && chapter.target.equals(level)) progress(player, chapter, chapter.goal);
	}

	private static void progress(ServerPlayer player, Chapter chapter, int progress) {
		Data data = Data.get(player.level().getServer());
		if (data.reached) return;
		data.progress = Math.min(progress, chapter.goal);
		data.reached = data.progress >= chapter.goal;
		data.setDirty();
		player.sendSystemMessage((data.reached
				? Component.translatable("message.noclip_backrooms.quest.done", chapter.title())
				: Component.translatable("message.noclip_backrooms.quest.progress", chapter.title(), data.progress, chapter.goal))
				.withStyle(ChatFormatting.GOLD));
	}

	/**
	 * Back home alive (or, for tests, told to): with the assignment done, M.E.G. takes the report, pays it into the quota,
	 * says something that does not quite fit, and gives the next one.
	 */
	static void report(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Data data = Data.get(server);
		Chapter chapter = current(server);
		if (chapter == null || !data.reached) return;
		long left = dev.noclip.backrooms.core.quota.Quota.addPoints(player, chapter.reward);
		if (left > 0) Wallet.add(player, left);
		PlayerRpg.grantXp(player, (int) (chapter.reward / 4));
		data.chapter++;
		data.progress = 0;
		data.reached = false;
		data.beacon = false;
		data.setDirty();
		for (ServerPlayer someone : server.getPlayerList().getPlayers()) {
			if (!dev.noclip.backrooms.core.Home.entered(someone) && someone != player) continue;
			someone.sendSystemMessage(Component.translatable("message.noclip_backrooms.quest.reported", chapter.title(), chapter.reward)
					.withStyle(ChatFormatting.GREEN));
			someone.sendSystemMessage(Component.translatable("quest.noclip_backrooms." + chapter.id + ".after").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
		if (chapter.specimen != null) dev.noclip.backrooms.frontrooms.Containment.refresh(server);
		Chapter next = current(server);
		if (next == null) {
			for (ServerPlayer someone : server.getPlayerList().getPlayers()) {
				if (Home.entered(someone)) someone.sendSystemMessage(Component.translatable("message.noclip_backrooms.quest.finished").withStyle(ChatFormatting.DARK_PURPLE));
			}
		} else {
			brief(player);
		}
	}

	/**
	 * The commander's briefing: the assignment, M.E.G.'s reason for it, and what to do. For a mapping job M.E.G. hands
	 * over a beacon, once.
	 */
	public static void brief(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Chapter chapter = current(server);
		if (chapter == null) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.quest.finished").withStyle(ChatFormatting.DARK_PURPLE));
			return;
		}
		Data data = Data.get(server);
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.quest.header", data.chapter + 1, chapter.title())
				.withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(Component.translatable("quest.noclip_backrooms." + chapter.id + ".reason").withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(Component.translatable("quest.noclip_backrooms." + chapter.id + ".goal").withStyle(ChatFormatting.YELLOW));
		if (data.reached) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.quest.come_back").withStyle(ChatFormatting.GREEN));
		} else if (chapter.goal > 1) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.quest.status", data.progress, chapter.goal).withStyle(ChatFormatting.GRAY));
		}
		if (chapter.kind == Kind.MAP && !data.beacon && Home.isHome(player.level().dimension())) {
			data.beacon = true;
			data.setDirty();
			ItemStack beacon = new ItemStack(FieldKit.BEACON);
			if (!player.getInventory().add(beacon)) player.drop(beacon, false);
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.quest.beacon").withStyle(ChatFormatting.GRAY));
		}
	}

	/** What the commander says about the main quest (core.guild.Contracts.talk, /meg story). */
	public static void talk(ServerPlayer player) {
		brief(player);
	}

	/** For tests: which assignment the team is on (its index; the number of assignments once all are done). */
	public static int chapterIndex(MinecraftServer server) {
		return Data.get(server).chapter;
	}

	static final class Data extends SavedData {
		static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("chapter", 0).forGetter(d -> d.chapter),
				Codec.INT.optionalFieldOf("progress", 0).forGetter(d -> d.progress),
				Codec.BOOL.optionalFieldOf("reached", false).forGetter(d -> d.reached),
				Codec.BOOL.optionalFieldOf("beacon", false).forGetter(d -> d.beacon)).apply(i, Data::new));
		static final SavedDataType<Data> TYPE = new SavedDataType<>(BackroomsMod.id("main_quest"), Data::new, CODEC,
				DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
		int chapter, progress;
		boolean reached, beacon;

		Data() {
			this(0, 0, false, false);
		}

		Data(int chapter, int progress, boolean reached, boolean beacon) {
			this.chapter = chapter;
			this.progress = progress;
			this.reached = reached;
			this.beacon = beacon;
		}

		static Data get(MinecraftServer server) {
			return server.overworld().getDataStorage().computeIfAbsent(TYPE);
		}
	}
}
