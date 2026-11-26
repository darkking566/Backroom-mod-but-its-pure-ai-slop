package dev.noclip.backrooms.core.quota;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.trade.ShopRole;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * The M.E.G. quota cycle (docs/project/1-gameplay/README.md): the Wretch countdown of the body and the quota of the
 * employer, on one clock that runs only while someone of the team is in the Overworld (the Backrooms have no time).
 *
 * <ul>
 * <li>It starts the first time anyone walks out of the Backrooms alive. Each new day of home time shows "Day N of the
 * mission" in yellow in the middle of the screen; so does coming home.</li>
 * <li>Days 1-2 the body is fine; on days 3-5 the Wretch rejection climbs (core.trip.Pull reads {@link #rejection}).</li>
 * <li>Selling to the outpost's buyer hands things in: points go first to debt, then to the quota, and only what is
 * left over is paid into the account; required items count when sold. Meeting the quota ends the cycle at once:
 * M.E.G.'s treatment, a care package, a new cycle (each one asks 30% more).</li>
 * <li>At the deadline anything unpaid becomes debt with 20% on top (and again at every deadline after); the outpost is
 * closed to the team (all but the buyer) until the debt is paid, the rejection stays at its worst, and the next
 * morning M.E.G. drops whoever is home into a random level.</li>
 * <li>The commander sends people in: Level 0 free, levels the team has been to for credits.</li>
 * </ul>
 */
public final class Quota {
	/** What a cycle can require: samples from the creatures of the open levels, notes and M.E.G.'s missing squad. */
	static final List<String> REQUIRED_POOL = List.of(
			"noclip_backrooms:hound_fang", "noclip_backrooms:hound_matted_hair", "noclip_backrooms:smiler_remnant",
			"noclip_backrooms:shadow_mote", "noclip_backrooms:tangled_knuckle", "noclip_backrooms:ash_residue",
			"noclip_backrooms:withered_heart", "noclip_backrooms:black_filament", "noclip_backrooms:acid_sac",
			"noclip_backrooms:moth_wing_scale", "noclip_backrooms:faceless_membrane", "noclip_backrooms:thick_disguise_hide",
			"noclip_backrooms:blueprint_scrap", "noclip_backrooms:meg_squad_report");
	/** What M.E.G. charges to send someone straight to a level the team has been to (Level 0 is free). */
	static final Map<String, Long> DEPLOY_PRICE = Map.of("0", 0L, "0.1", 80L, "1", 50L, "2", 120L, "3", 250L);
	static final long DEFAULT_DEPLOY_PRICE = 400L;
	static final double NEAR_COMMANDER = 8.0;

	/** Overworld clock at the last tick someone was home; -1 while nobody is (the clock is not ours to count then). */
	private static long lastClock = -1;

	private Quota() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(QuotaStatusPayload.TYPE, QuotaStatusPayload.STREAM_CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> lastClock = -1);
		ServerTickEvents.END_SERVER_TICK.register(Quota::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player, data(server)));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> {
			QuotaData d = data(player.level().getServer());
			if (to.dimension() == Level.OVERWORLD && d.active) showDay(player, d);
		});
		Floors.onArrive((player, level, run, newLevel) -> {
			QuotaData d = data(player.level().getServer());
			if (d.visited.add(level)) d.changed();
		});
		registerCommands();
	}

	public static QuotaData data(MinecraftServer server) {
		return QuotaData.get(server.overworld());
	}

	// ------------------------------------------------------------------ the clock

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		QuotaData d = QuotaData.get(overworld);
		if (!d.active) return;
		long clock = overworld.getOverworldClockTime();
		List<ServerPlayer> home = home(server);
		if (!home.isEmpty()) {
			// Every tick someone is home counts, even with the day-night cycle switched off; a jump of the clock (sleeping
			// through the night, /time add) counts as the time it skipped. A clock set backwards takes nothing away.
			long add = 1;
			if (lastClock >= 0) {
				long delta = clock - lastClock;
				if (delta > 1 && delta <= QuotaMath.DAY) add = delta;
			}
			d.homeTicks += add;
			d.changed();
			lastClock = clock;
		} else {
			lastClock = -1;
		}

		boolean grew = false;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) grew |= d.seen.add(player.getUUID().toString());
		if (grew && d.seen.size() > d.team) {
			d.team = d.seen.size();
			d.required = Math.max(d.required, QuotaMath.quota(d.paid, d.team));
			for (var e : d.items.entrySet()) e.setValue(Math.max(e.getValue(), QuotaMath.itemCount(d.team)));
			d.changed();
		}

		if (QuotaMath.pastDeadline(d.homeTicks)) {
			miss(server, d, clock);
		} else {
			int day = QuotaMath.day(d.homeTicks);
			if (day > d.lastDay) {
				d.lastDay = day;
				d.changed();
				for (ServerPlayer player : home) showDay(player, d);
			}
		}

		if (d.redeployAt >= 0 && clock >= d.redeployAt) redeploy(server, d);
		if (server.getTickCount() % 20 == 0) sendAll(server, d);
	}

	/** Players who count as home: in the Overworld, alive, not spectating. */
	static List<ServerPlayer> home(MinecraftServer server) {
		List<ServerPlayer> home = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level().dimension() == Level.OVERWORLD && player.isAlive() && !player.isSpectator()) home.add(player);
		}
		return home;
	}

	/** Called when someone walks out of the Backrooms alive (core.trip.Trips): the first time, the cycle starts. */
	public static void onHome(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		QuotaData d = data(server);
		if (d.active) return;
		d.active = true;
		d.cycle = 1;
		startCycle(server, d);
		d.lastDay = 1;
		d.changed();
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.quota.started", d.required).withStyle(ChatFormatting.GOLD));
		sendAll(server, d);
	}

	/** A fresh cycle: day 1, a quota sized to the team and the cycles paid so far, new required items. */
	static void startCycle(MinecraftServer server, QuotaData d) {
		d.homeTicks = 0;
		d.delivered = 0;
		d.lastDay = 0;
		d.seen.clear();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) d.seen.add(player.getUUID().toString());
		d.team = Math.max(1, d.seen.size());
		d.required = QuotaMath.quota(d.paid, d.team);
		d.items.clear();
		d.handed.clear();
		List<String> pool = new ArrayList<>();
		for (String id : REQUIRED_POOL) {
			if (BuiltInRegistries.ITEM.containsKey(Identifier.parse(id))) pool.add(id);
		}
		Collections.shuffle(pool, new java.util.Random(server.overworld().getRandom().nextLong()));
		for (int i = 0; i < Math.min(QuotaMath.itemKinds(d.cycle), pool.size()); i++) d.items.put(pool.get(i), QuotaMath.itemCount(d.team));
		d.changed();
	}

	// ------------------------------------------------------------------ handing things in

	/**
	 * Something sold to the outpost's buyer (core.trade.Shops): counts toward required items, pays debt, then the quota;
	 * returns the credits left over for the player's account.
	 */
	public static long onSold(ServerPlayer player, Item item, int count, long value) {
		MinecraftServer server = player.level().getServer();
		QuotaData d = data(server);
		if (!d.active) return value;
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		Integer need = d.items.get(id);
		if (need != null) {
			int have = d.handed.getOrDefault(id, 0);
			int add = Math.min(count, need - have);
			if (add > 0) d.handed.put(id, have + add);
		}
		return hand(server, d, player, value);
	}

	/** Points from something other than a sale (a finished M.E.G. order); returns what is left over. */
	public static long addPoints(ServerPlayer player, long points) {
		MinecraftServer server = player.level().getServer();
		QuotaData d = data(server);
		if (!d.active) return points;
		return hand(server, d, player, points);
	}

	private static long hand(MinecraftServer server, QuotaData d, ServerPlayer player, long value) {
		long toDebt = Math.min(value, d.debt);
		d.debt -= toDebt;
		value -= toDebt;
		long toQuota = Math.min(value, Math.max(0, d.required - d.delivered));
		d.delivered += toQuota;
		value -= toQuota;
		d.changed();
		if (toDebt > 0 || toQuota > 0) {
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.quota.handed", toQuota, toDebt, d.delivered, d.required)
					.withStyle(ChatFormatting.YELLOW));
		}
		if (toDebt > 0 && d.debt == 0) broadcast(server, Component.translatable("message.noclip_backrooms.quota.debt_cleared").withStyle(ChatFormatting.GREEN));
		if (d.met()) pay(server, d);
		sendAll(server, d);
		return value;
	}

	/** Pays debt from the account, two credits a point. */
	public static boolean payDebt(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		QuotaData d = data(server);
		if (d.debt <= 0) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.quota.no_debt"));
			return false;
		}
		long points = Math.min(d.debt, Wallet.of(player) / QuotaMath.CREDITS_PER_DEBT_POINT);
		if (points <= 0) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.quota.cannot_pay", d.debt * QuotaMath.CREDITS_PER_DEBT_POINT));
			return false;
		}
		Wallet.take(player, points * QuotaMath.CREDITS_PER_DEBT_POINT);
		d.debt -= points;
		d.changed();
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.quota.paid_debt", points, points * QuotaMath.CREDITS_PER_DEBT_POINT, d.debt));
		if (d.debt == 0) broadcast(server, Component.translatable("message.noclip_backrooms.quota.debt_cleared").withStyle(ChatFormatting.GREEN));
		if (d.met()) pay(server, d);
		sendAll(server, d);
		return true;
	}

	/** The quota is met: treatment for everyone, a care package for whoever is home, and the next cycle. */
	static void pay(MinecraftServer server, QuotaData d) {
		d.paid++;
		d.cycle++;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) treat(player);
		for (ServerPlayer player : home(server)) carePackage(player);
		startCycle(server, d);
		broadcast(server, Component.translatable("message.noclip_backrooms.quota.met", d.required).withStyle(ChatFormatting.GREEN));
		for (ServerPlayer player : home(server)) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 0.8F);
		}
	}

	/** M.E.G.'s treatment: the Wretch symptoms go. (The rejection itself follows the new cycle's clock.) */
	static void treat(ServerPlayer player) {
		player.removeEffect(MobEffects.HUNGER);
		player.removeEffect(MobEffects.WEAKNESS);
		player.removeEffect(MobEffects.DARKNESS);
		player.removeEffect(MobEffects.NAUSEA);
	}

	static void carePackage(ServerPlayer player) {
		give(player, "noclip_backrooms:almond_water", 2);
		give(player, "noclip_backrooms:bandage", 2);
		give(player, "minecraft:bread", 4);
	}

	private static void give(ServerPlayer player, String id, int count) {
		Identifier key = Identifier.parse(id);
		if (!BuiltInRegistries.ITEM.containsKey(key)) return;
		ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(key), count);
		if (!player.getInventory().add(stack)) player.drop(stack, false);
	}

	// ------------------------------------------------------------------ missing the deadline

	/** The deadline passed with the quota not met: debt with interest, the outpost closed, redeployment at dawn. */
	static void miss(MinecraftServer server, QuotaData d, long clock) {
		long unpaid = d.debt + Math.max(0, d.required - d.delivered) + d.missingItemPoints();
		d.debt = QuotaMath.debtAfterDeadline(unpaid);
		d.redeployAt = (Math.floorDiv(clock, QuotaMath.DAY) + 1) * QuotaMath.DAY;
		d.cycle++;
		// The quota does not grow after a cycle the team failed to pay.
		startCycle(server, d);
		broadcast(server, Component.translatable("message.noclip_backrooms.quota.missed", d.debt).withStyle(ChatFormatting.RED));
		sendAll(server, d);
	}

	/** Dawn after a missed deadline: M.E.G. takes everyone who is home and drops them into a random level. */
	static void redeploy(MinecraftServer server, QuotaData d) {
		d.redeployAt = -1;
		d.changed();
		List<String> levels = new ArrayList<>(LevelExits.openLevels());
		if (levels.isEmpty()) return;
		Collections.sort(levels);
		for (ServerPlayer player : home(server)) {
			if (player.isCreative()) continue;
			String level = levels.get(player.getRandom().nextInt(levels.size()));
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.quota.redeployed").withStyle(ChatFormatting.DARK_RED));
			LevelExits.fallInto(player, level);
		}
	}

	// ------------------------------------------------------------------ what the rest of the mod asks

	/** Whether the outpost is closed to the team: they owe M.E.G. (only the buyer still takes things). */
	public static boolean locked(MinecraftServer server) {
		QuotaData d = data(server);
		return d.active && d.debt > 0;
	}

	/** How bad the Wretch rejection is in the Overworld right now (0..100): the worst while the team is in debt. */
	public static float rejection(MinecraftServer server) {
		QuotaData d = data(server);
		if (!d.active) return 0.0F;
		return d.debt > 0 ? 100.0F : QuotaMath.rejection(d.homeTicks);
	}

	/** For the GM command: moves the cycle's clock to where the rejection is this bad (and starts the cycle if needed). */
	public static void setRejection(MinecraftServer server, float severity) {
		QuotaData d = data(server);
		d.active = true;
		if (d.required == 0) startCycle(server, d);
		long span = (QuotaMath.CYCLE_DAYS - QuotaMath.SAFE_DAYS) * QuotaMath.DAY;
		d.homeTicks = severity <= 0 ? 0 : Math.min(QuotaMath.CYCLE_DAYS * QuotaMath.DAY - 1,
				QuotaMath.SAFE_DAYS * QuotaMath.DAY + (long) (span * Math.min(100.0F, severity) / 100.0F));
		d.lastDay = QuotaMath.day(d.homeTicks);
		d.changed();
	}

	// ------------------------------------------------------------------ on screen

	/** "Day N of the mission" in yellow in the middle of the screen, with the quota under it. */
	static void showDay(ServerPlayer player, QuotaData d) {
		int day = QuotaMath.day(d.homeTicks);
		Component subtitle = d.debt > 0
				? Component.translatable("quota.noclip_backrooms.day_sub_debt", d.debt).withStyle(ChatFormatting.RED)
				: day >= QuotaMath.CYCLE_DAYS
						? Component.translatable("quota.noclip_backrooms.last_day", d.delivered, d.required).withStyle(ChatFormatting.GOLD)
						: Component.translatable("quota.noclip_backrooms.day_sub", d.delivered, d.required, QuotaMath.daysLeft(d.homeTicks));
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 30));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(
				Component.translatable("quota.noclip_backrooms.day_title", day).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
	}

	static void broadcast(MinecraftServer server, Component message) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) player.sendSystemMessage(message);
	}

	static QuotaStatusPayload status(MinecraftServer server, QuotaData d) {
		if (!d.active) return QuotaStatusPayload.NONE;
		List<QuotaStatusPayload.Need> needs = new ArrayList<>();
		for (var e : d.items.entrySet()) needs.add(new QuotaStatusPayload.Need(e.getKey(), d.handed.getOrDefault(e.getKey(), 0), e.getValue()));
		return new QuotaStatusPayload(true, QuotaMath.day(d.homeTicks), QuotaMath.daysLeft(d.homeTicks), d.delivered, d.required, d.debt,
				d.debt > 0, needs);
	}

	static void send(ServerPlayer player, QuotaData d) {
		ServerPlayNetworking.send(player, status(player.level().getServer(), d));
	}

	static void sendAll(MinecraftServer server, QuotaData d) {
		QuotaStatusPayload status = status(server, d);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(player, status);
	}

	// ------------------------------------------------------------------ the commander

	/** What the outpost commander adds when spoken to: the cycle, sending people in, paying debt. */
	public static void talk(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		QuotaData d = data(server);
		if (d.active) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.quota.status", QuotaMath.day(d.homeTicks),
					QuotaMath.CYCLE_DAYS, d.delivered, d.required).withStyle(ChatFormatting.YELLOW));
			for (var e : d.items.entrySet()) {
				Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(e.getKey()));
				player.sendSystemMessage(Component.translatable("message.noclip_backrooms.quota.need", new ItemStack(item).getHoverName(),
						d.handed.getOrDefault(e.getKey(), 0), e.getValue()));
			}
			if (d.debt > 0) {
				player.sendSystemMessage(Component.translatable("message.noclip_backrooms.quota.debt_line", d.debt).withStyle(ChatFormatting.RED)
						.append(" ").append(button("message.noclip_backrooms.quota.pay_button", "/meg quota paydebt", ChatFormatting.GOLD)));
			}
		}
		MutableComponent line = Component.translatable("message.noclip_backrooms.quota.deploy_header").withStyle(ChatFormatting.GRAY);
		for (String level : deployable(d)) {
			long price = DEPLOY_PRICE.getOrDefault(level, DEFAULT_DEPLOY_PRICE);
			line.append(" ").append(button(price == 0 ? "message.noclip_backrooms.quota.deploy_free" : "message.noclip_backrooms.quota.deploy_paid",
					"/meg deploy " + level, ChatFormatting.AQUA, level, price));
		}
		player.sendSystemMessage(line);
	}

	/** Levels M.E.G. can send the team to: Level 0, and open levels they have been to. */
	static List<String> deployable(QuotaData d) {
		List<String> list = new ArrayList<>();
		if (LevelExits.isOpen("0")) list.add("0");
		List<String> been = new ArrayList<>(d.visited);
		Collections.sort(been);
		for (String level : been) if (!level.equals("0") && LevelExits.isOpen(level)) list.add(level);
		return list;
	}

	private static MutableComponent button(String key, String command, ChatFormatting colour, Object... args) {
		return Component.translatable(key, args).withStyle(style -> style.withColor(colour)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
	}

	static boolean nearCommander(ServerPlayer player) {
		return !player.level().getEntitiesOfClass(Person.class, player.getBoundingBox().inflate(NEAR_COMMANDER),
				person -> ShopRole.of(person) == ShopRole.MASTER).isEmpty();
	}

	/** M.E.G. sends the player in: Level 0 free, a level the team has been to for credits. */
	public static boolean deploy(ServerPlayer player, String level, boolean checkCommander) {
		MinecraftServer server = player.level().getServer();
		QuotaData d = data(server);
		String refusal = player.level().dimension() != Level.OVERWORLD ? "message.noclip_backrooms.quota.deploy_not_home"
				: checkCommander && !nearCommander(player) ? "message.noclip_backrooms.quota.deploy_no_commander"
				: !deployable(d).contains(level) ? "message.noclip_backrooms.quota.deploy_unknown" : null;
		long price = DEPLOY_PRICE.getOrDefault(level, DEFAULT_DEPLOY_PRICE);
		if (refusal == null && price > 0 && !Wallet.take(player, price)) refusal = "message.noclip_backrooms.quota.deploy_no_credits";
		if (refusal != null) {
			player.sendSystemMessage(Component.translatable(refusal, price));
			return false;
		}
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.quota.deployed", level).withStyle(ChatFormatting.GRAY));
		LevelExits.fallInto(player, level);
		return true;
	}

	// ------------------------------------------------------------------ commands

	private static void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
			dispatcher.register(Commands.literal("meg")
					.then(Commands.literal("deploy").then(Commands.argument("level", StringArgumentType.word())
							.executes(ctx -> deploy(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "level"), true) ? 1 : 0)))
					.then(Commands.literal("quota")
							.executes(ctx -> {
								talk(ctx.getSource().getPlayerOrException());
								return 1;
							})
							.then(Commands.literal("paydebt").executes(ctx -> {
								ServerPlayer player = ctx.getSource().getPlayerOrException();
								if (!nearCommander(player)) {
									player.sendSystemMessage(Component.translatable("message.noclip_backrooms.quota.deploy_no_commander"));
									return 0;
								}
								return payDebt(player) ? 1 : 0;
							}))));
			dispatcher.register(Commands.literal("backrooms").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.literal("quota")
							.then(Commands.literal("start").executes(ctx -> {
								onHome(ctx.getSource().getPlayerOrException());
								return 1;
							}))
							.then(Commands.literal("day").then(Commands.argument("day", IntegerArgumentType.integer(1, QuotaMath.CYCLE_DAYS))
									.executes(ctx -> {
										QuotaData d = data(ctx.getSource().getServer());
										d.homeTicks = (IntegerArgumentType.getInteger(ctx, "day") - 1) * QuotaMath.DAY;
										d.changed();
										return 1;
									})))
							.then(Commands.literal("reset").executes(ctx -> {
								QuotaData d = data(ctx.getSource().getServer());
								d.active = false;
								d.debt = 0;
								d.paid = 0;
								d.redeployAt = -1;
								d.changed();
								sendAll(ctx.getSource().getServer(), d);
								return 1;
							}))));
		});
	}

	/** For tests and the GM command: the items this cycle wants, in order. */
	public static Map<String, Integer> required(MinecraftServer server) {
		return new LinkedHashMap<>(data(server).items);
	}
}
