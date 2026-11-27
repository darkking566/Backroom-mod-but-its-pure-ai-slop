package dev.noclip.backrooms.core;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.tools.BackroomsToolItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Conditional level unlock & gate framework (docs/project/1-gameplay/README.md, "條件解鎖的樓層"):
 * <ul>
 *   <li>Supports multi-tier Keypad Hackers (Tier 1..4), keycards/credentials (optionally consumed),
 *       and world progression flags (defeated Bosses, restored power generators, rescued survivors);</li>
 *   <li>Once a team breaches or satisfies a gate, {@code targetLevel} is persisted as permanently unlocked
 *       for the world, and integrated with {@link LevelExits#use} and contract deadlock checks.</li>
 * </ul>
 */
public final class LevelGates {
	public static final String BOSS_ENTITY_TAG = "noclip_backrooms_boss";
	private static final int DENY_INTERVAL_TICKS = 40;
	private static final Map<String, Gate> GATES = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> LAST_DENIED = new HashMap<>();

	public record Gate(
			String targetLevel,
			int minHackerTier,
			String requiredItemId,
			boolean consumeItem,
			String requiredFlag,
			String hintKey
	) {
		public Gate {
			targetLevel = targetLevel == null ? "" : targetLevel;
			minHackerTier = Math.max(0, minHackerTier);
			requiredItemId = requiredItemId == null ? "" : requiredItemId;
			requiredFlag = requiredFlag == null ? "" : requiredFlag;
			hintKey = hintKey == null ? "" : hintKey;
		}

		public static Gate hacker(String level, int tier) {
			return new Gate(level, tier, "", false, "", "");
		}

		public static Gate keycard(String level, String itemId, boolean consume) {
			return new Gate(level, 0, itemId, consume, "", "");
		}

		public static Gate flag(String level, String flagId, String hintKey) {
			return new Gate(level, 0, "", false, flagId, hintKey);
		}

		public static Gate of(String level, int minHackerTier, String requiredItemId, boolean consumeItem,
				String requiredFlag, String hintKey) {
			return new Gate(level, minHackerTier, requiredItemId, consumeItem, requiredFlag, hintKey);
		}
	}

	public record CheckResult(boolean allowed, String reasonKey, String detail) {
	}

	public static final class GateData extends SavedData {
		private final Set<String> flags = new HashSet<>();
		private final Set<String> unlockedLevels = new HashSet<>();

		private record Saved(List<String> flags, List<String> unlockedLevels) {
		}

		private static final Codec<Saved> SAVED = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.listOf().optionalFieldOf("flags", List.of()).forGetter(Saved::flags),
				Codec.STRING.listOf().optionalFieldOf("unlocked_levels", List.of()).forGetter(Saved::unlockedLevels)
		).apply(i, Saved::new));

		private static final Codec<GateData> CODEC = SAVED.xmap(GateData::fromSaved, GateData::toSaved);

		public static final SavedDataType<GateData> TYPE = new SavedDataType<>(
				BackroomsMod.id("level_gates"), GateData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

		public GateData() {
		}

		public static GateData get(ServerLevel overworld) {
			return overworld.getDataStorage().computeIfAbsent(TYPE);
		}

		private static GateData fromSaved(Saved s) {
			GateData d = new GateData();
			d.flags.addAll(s.flags);
			d.unlockedLevels.addAll(s.unlockedLevels);
			return d;
		}

		private Saved toSaved() {
			return new Saved(new ArrayList<>(flags), new ArrayList<>(unlockedLevels));
		}
	}

	private LevelGates() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> LAST_DENIED.clear());

		// Defeating a Backrooms Boss automatically records its progression flag ("boss:<entity_path>").
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof Monster monster) || !(entity.level() instanceof ServerLevel serverLevel)) return;
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(monster.getType());
			boolean isBoss = monster.entityTags().contains(BOSS_ENTITY_TAG)
					|| (BackroomsMod.MOD_ID.equals(id.getNamespace()) && monster.getMaxHealth() >= 80.0F);
			if (isBoss) {
				grantFlag(serverLevel.getServer(), "boss:" + id.getPath());
			}
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
				Commands.literal("backrooms")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("gate")
								.then(Commands.literal("status").executes(ctx -> {
									ServerPlayer p = ctx.getSource().getPlayerOrException();
									GateData d = data(p.level().getServer());
									p.sendSystemMessage(Component.translatable(
											"message.noclip_backrooms.gate.status",
											GATES.size(), d.unlockedLevels.size(), d.flags.size()).withStyle(ChatFormatting.AQUA));
									return 1;
								}))
								.then(Commands.literal("flag").then(Commands.argument("id", StringArgumentType.greedyString())
										.executes(ctx -> {
											ServerPlayer p = ctx.getSource().getPlayerOrException();
											String flag = StringArgumentType.getString(ctx, "id");
											grantFlag(p.level().getServer(), flag);
											p.sendSystemMessage(Component.translatable("message.noclip_backrooms.gate.flag_set", flag)
													.withStyle(ChatFormatting.GREEN));
											return 1;
										})))
								.then(Commands.literal("unlock").then(Commands.argument("level", StringArgumentType.word())
										.executes(ctx -> {
											ServerPlayer p = ctx.getSource().getPlayerOrException();
											String lvl = StringArgumentType.getString(ctx, "level");
											unlockLevel(p.level().getServer(), lvl);
											p.sendSystemMessage(Component.translatable("message.noclip_backrooms.gate.unlocked", lvl)
													.withStyle(ChatFormatting.GREEN));
											return 1;
										})))
								.then(Commands.literal("clear").executes(ctx -> {
									clearAll(ctx.getSource().getServer());
									ctx.getSource().sendSuccess(() -> Component.translatable("message.noclip_backrooms.gate.cleared"), false);
									return 1;
								})))));
	}

	public static GateData data(MinecraftServer server) {
		return GateData.get(server.overworld());
	}

	/** Registers or replaces the passage gate condition for {@code gate.targetLevel()}. */
	public static void registerGate(Gate gate) {
		if (gate != null && !gate.targetLevel().isEmpty()) {
			GATES.put(gate.targetLevel(), gate);
		}
	}

	public static void clearGate(String level) {
		if (level != null) GATES.remove(level);
	}

	public static Gate gateFor(String level) {
		return level == null ? null : GATES.get(level);
	}

	public static Map<String, Gate> allGates() {
		return Collections.unmodifiableMap(GATES);
	}

	/** Pure deterministic evaluation of gate requirements (unit-testable without a running server). */
	public static CheckResult evaluate(Gate gate, boolean alreadyUnlocked, int bestHackerTier,
			boolean hasRequiredItem, Set<String> activeFlags) {
		if (gate == null || alreadyUnlocked) return new CheckResult(true, "", "");
		if (gate.minHackerTier() > 0 && bestHackerTier < gate.minHackerTier()) {
			return new CheckResult(false, "message.noclip_backrooms.gate.need_hacker", String.valueOf(gate.minHackerTier()));
		}
		if (!gate.requiredItemId().isEmpty() && !hasRequiredItem) {
			return new CheckResult(false, "message.noclip_backrooms.gate.need_item", gate.requiredItemId());
		}
		if (!gate.requiredFlag().isEmpty() && (activeFlags == null || !activeFlags.contains(gate.requiredFlag()))) {
			String key = gate.hintKey().isEmpty() ? "message.noclip_backrooms.gate.need_flag" : gate.hintKey();
			return new CheckResult(false, key, gate.requiredFlag());
		}
		return new CheckResult(true, "", "");
	}

	/** Returns the Keypad Hacker tier (1..4) of {@code stack}, or 0 if not a keypad hacker tool. */
	public static int hackerTier(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return 0;
		if (stack.getItem() instanceof BackroomsToolItem tool) {
			String path = BuiltInRegistries.ITEM.getKey(tool).getPath();
			if (path.startsWith("keypad_hacker_")) {
				return Math.max(1, tool.tier());
			}
		}
		return 0;
	}

	public static int bestHackerTier(Inventory inv) {
		int best = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			best = Math.max(best, hackerTier(inv.getItem(i)));
		}
		return best;
	}

	public static boolean hasItem(Inventory inv, String itemId) {
		if (itemId == null || itemId.isEmpty()) return true;
		Identifier id = Identifier.tryParse(itemId);
		if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return false;
		Item target = BuiltInRegistries.ITEM.getValue(id);
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(target)) return true;
		}
		return false;
	}

	public static void grantFlag(MinecraftServer server, String flag) {
		if (server == null || flag == null || flag.isEmpty()) return;
		GateData d = data(server);
		if (d.flags.add(flag)) d.setDirty();
	}

	public static boolean hasFlag(MinecraftServer server, String flag) {
		return server != null && flag != null && data(server).flags.contains(flag);
	}

	public static void revokeFlag(MinecraftServer server, String flag) {
		if (server == null || flag == null) return;
		GateData d = data(server);
		if (d.flags.remove(flag)) d.setDirty();
	}

	public static void unlockLevel(MinecraftServer server, String level) {
		if (server == null || level == null || level.isEmpty()) return;
		GateData d = data(server);
		if (d.unlockedLevels.add(level)) d.setDirty();
	}

	public static boolean isLevelUnlocked(MinecraftServer server, String level) {
		return server != null && level != null && data(server).unlockedLevels.contains(level);
	}

	public static void clearAll(MinecraftServer server) {
		if (server == null) return;
		GateData d = data(server);
		d.flags.clear();
		d.unlockedLevels.clear();
		d.setDirty();
	}

	/**
	 * Non-destructive reachability check for contract filtering and shortcut listing:
	 * does not consume items, damage tools, or send denial chat.
	 */
	public static boolean isReachableFor(ServerPlayer player, String level) {
		Gate gate = gateFor(level);
		if (gate == null || player == null || player.isCreative()) return true;
		GateData d = data(player.level().getServer());
		if (d.unlockedLevels.contains(level)) return true;
		CheckResult res = evaluate(
				gate,
				false,
				bestHackerTier(player.getInventory()),
				hasItem(player.getInventory(), gate.requiredItemId()),
				d.flags);
		return res.allowed();
	}

	/**
	 * Attempts to pass a gated exit to {@code level}. If blocked, sends throttled feedback and returns {@code false};
	 * if satisfied, damages/consumes required breaching gear, records permanent team unlock, and returns {@code true}.
	 */
	public static boolean tryPass(ServerPlayer player, String level) {
		Gate gate = gateFor(level);
		if (gate == null || player == null || player.isCreative()) return true;
		MinecraftServer server = player.level().getServer();
		GateData d = data(server);
		if (d.unlockedLevels.contains(level)) return true;

		Inventory inv = player.getInventory();
		CheckResult res = evaluate(
				gate,
				false,
				bestHackerTier(inv),
				hasItem(inv, gate.requiredItemId()),
				d.flags);

		if (!res.allowed()) {
			notifyDenied(player, server.getTickCount(), res);
			return false;
		}

		if (gate.minHackerTier() > 0) {
			damageHacker(player, gate.minHackerTier(), 4 * gate.minHackerTier());
		}
		if (gate.consumeItem() && !gate.requiredItemId().isEmpty()) {
			consumeOne(inv, gate.requiredItemId());
		}
		d.unlockedLevels.add(level);
		d.setDirty();

		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.85F, 1.15F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.65F, 1.25F);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.sendSystemMessage(Component.translatable("message.noclip_backrooms.gate.unlocked", level)
					.withStyle(ChatFormatting.GREEN));
		}
		return true;
	}

	private static void notifyDenied(ServerPlayer player, int now, CheckResult res) {
		Integer last = LAST_DENIED.get(player.getUUID());
		if (last != null && now >= last && now - last < DENY_INTERVAL_TICKS) return;
		LAST_DENIED.put(player.getUUID(), now);

		Component msg;
		if ("message.noclip_backrooms.gate.need_item".equals(res.reasonKey())) {
			Identifier id = Identifier.tryParse(res.detail());
			Component itemName = (id != null && BuiltInRegistries.ITEM.containsKey(id))
					? new ItemStack(BuiltInRegistries.ITEM.getValue(id)).getHoverName()
					: Component.literal(res.detail());
			msg = Component.translatable(res.reasonKey(), itemName);
		} else {
			msg = Component.translatable(res.reasonKey(), res.detail());
		}
		player.sendOverlayMessage(msg.copy().withStyle(ChatFormatting.RED));
		player.sendSystemMessage(msg.copy().withStyle(ChatFormatting.RED));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 0.7F);
	}

	private static void damageHacker(ServerPlayer player, int minTier, int amount) {
		Inventory inv = player.getInventory();
		ItemStack chosen = null;
		int chosenTier = Integer.MAX_VALUE;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			int t = hackerTier(s);
			if (t >= minTier && t < chosenTier) {
				chosen = s;
				chosenTier = t;
			}
		}
		if (chosen != null && chosen.isDamageableItem()) {
			chosen.hurtAndBreak(amount, player, EquipmentSlot.MAINHAND);
		}
	}

	private static void consumeOne(Inventory inv, String itemId) {
		Identifier id = Identifier.tryParse(itemId);
		if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return;
		Item target = BuiltInRegistries.ITEM.getValue(id);
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(target)) {
				s.shrink(1);
				inv.setChanged();
				return;
			}
		}
	}
}
