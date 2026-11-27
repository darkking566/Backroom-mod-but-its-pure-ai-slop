package dev.noclip.backrooms.core.floors;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.level0.Level0;
import dev.noclip.backrooms.level0.Level0Space;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Dual-axis Backrooms difficulty & anti-farm system (docs/project/1-gameplay/README.md, "雙軸難度"):
 * <ul>
 * <li>Vertical axis: {@link FloorRun#floor()} visited on this trip;</li>
 * <li>Horizontal axis: distance walked from the current level's landing spot
 * ({@code <250}, {@code 250..749}, {@code 750..1599}, {@code >=1600} blocks);</li>
 * <li>Anti-farm: ping-ponging rapidly back and forth between the same two levels freezes floor progression, escalates
 * monster threat, and degrades drops;</li>
 * <li>Theme-locked loot scaling: amplifies stackable drops already inside the level's own pool without leaking items from
 * unrelated deep themes.</li>
 * </ul>
 */
public final class FloorDifficulty {
	public record LandingSpot(String dimension, int x, int z) {
		static final Codec<LandingSpot> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("dimension").forGetter(LandingSpot::dimension),
				Codec.INT.fieldOf("x").forGetter(LandingSpot::x),
				Codec.INT.fieldOf("z").forGetter(LandingSpot::z)).apply(i, LandingSpot::new));
	}

	public record Scale(int floor, int band, double distance, double hpMult, double damageMult,
			double lootBonus, double spawnRateMult, int farmPenalty) {
		public static final Scale BASELINE = new Scale(1, 0, 0.0, 1.0, 1.0, 0.0, 1.0, 0);
	}

	public record Transition(String level, long tick) {
	}

	private static final class Holders {
		static final AttachmentType<LandingSpot> LANDING = AttachmentRegistry.create(
				BackroomsMod.id("floor_landing"),
				builder -> builder.persistent(LandingSpot.CODEC).copyOnDeath());
	}

	static final long PING_PONG_WINDOW = 3600L; // 3 minutes
	static final int MAX_PENALTY = 5;
	private static final int MAX_RECENT = 6;
	private static final String SCALED_TAG = "noclip_backrooms_scaled";
	private static final Identifier HP_MOD_ID = BackroomsMod.id("dual_axis_hp");
	private static final Identifier DMG_MOD_ID = BackroomsMod.id("dual_axis_dmg");
	private static final Set<String> PROTECTED_LOOT = Set.of(
			"evidence_photo", "topology_tape", "meg_squad_report", "wretch_cure");

	private static final Map<UUID, Deque<Transition>> HISTORY = new HashMap<>();
	private static final Map<UUID, Integer> FARM_PENALTY = new HashMap<>();

	private FloorDifficulty() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(DifficultyPayload.TYPE, DifficultyPayload.STREAM_CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			HISTORY.clear();
			FARM_PENALTY.clear();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			HISTORY.remove(id);
			FARM_PENALTY.remove(id);
		});
		Floors.onArrive((player, level, run, newLevel) -> {
			recordLanding(player);
			send(player);
		});
		Floors.onHome((player, run) -> {
			reset(player);
			send(player);
		});
		ServerTickEvents.END_SERVER_TICK.register(FloorDifficulty::tick);
		ServerEntityEvents.ENTITY_LOAD.register(FloorDifficulty::onEntityLoad);
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			ServerLevel level = context.getLevel();
			if (!Home.inBackrooms(level.dimension()) || drops.isEmpty()) return;
			ServerPlayer player = resolveLootPlayer(level, context);
			if (player == null) return;
			Scale scale = of(player);
			if (scale.farmPenalty() > 0) {
				degradeDrops(drops, scale.farmPenalty(), context.getRandom());
			} else if (scale.lootBonus() > 0.0) {
				String tablePath = table.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
				boolean isContainer = tablePath.contains("chest") || tablePath.contains("crate");
				amplifyDrops(drops, scale.lootBonus(), isContainer, context.getRandom());
			}
		});
		registerCommands();
	}

	/** Pure band index (0..3) from distance to the current level's landing spot. */
	public static int bandFor(double distance) {
		if (distance >= 1600.0) return 3;
		if (distance >= 750.0) return 2;
		if (distance >= 250.0) return 1;
		return 0;
	}

	/** Pure dual-axis difficulty computation (testable without a running server). */
	public static Scale compute(int floor, double distance, int farmPenalty) {
		int f = Math.max(1, floor);
		double dist = Math.max(0.0, distance);
		int band = bandFor(dist);
		double hHp, hDmg, hLoot, hSpawn;
		switch (band) {
			case 1 -> {
				hHp = 1.35;
				hDmg = 1.20;
				hLoot = 0.25;
				hSpawn = 1.25;
			}
			case 2 -> {
				hHp = 1.85;
				hDmg = 1.50;
				hLoot = 0.55;
				hSpawn = 1.55;
			}
			case 3 -> {
				hHp = 2.50;
				hDmg = 1.90;
				hLoot = 0.90;
				hSpawn = 1.90;
			}
			default -> {
				hHp = 1.00;
				hDmg = 1.00;
				hLoot = 0.00;
				hSpawn = 1.00;
			}
		}
		double vHp = 1.0 + Math.min(2.0, (f - 1) * 0.18);
		double vDmg = 1.0 + Math.min(1.2, (f - 1) * 0.10);
		double vLoot = Math.min(1.20, (f - 1) * 0.15);
		double vSpawn = 1.0 + Math.min(1.5, (f - 1) * 0.12);

		int p = Math.clamp(farmPenalty, 0, MAX_PENALTY);
		double hpMult = hHp * vHp * (1.0 + 0.35 * p);
		double dmgMult = hDmg * vDmg * (1.0 + 0.25 * p);
		double lootBonus = p > 0 ? -Math.min(0.75, 0.25 * p) : (hLoot + vLoot);
		double spawnMult = hSpawn * vSpawn * (1.0 + 0.30 * p);
		return new Scale(f, band, dist, hpMult, dmgMult, lootBonus, spawnMult, p);
	}

	/**
	 * Pure ping-pong check: allows a single backtrack (e.g. {@code 0 -> 1 -> 0}), and flags only rapid back-and-forth
	 * bouncing between the exact same two levels ({@code A -> B -> A -> B}) inside {@link #PING_PONG_WINDOW}.
	 */
	public static boolean isPingPong(List<Transition> recent, String incomingLevel, long nowTick) {
		int n = recent.size();
		if (n < 3) return false;
		Transition a = recent.get(n - 3);
		Transition b = recent.get(n - 2);
		Transition c = recent.get(n - 1);
		if (nowTick - a.tick() > PING_PONG_WINDOW) return false;
		return !a.level().equals(b.level())
				&& c.level().equals(a.level())
				&& incomingLevel.equals(b.level());
	}

	/** Current effective dual-axis scale for {@code player} (baseline if outside the Backrooms). */
	public static Scale of(ServerPlayer player) {
		String key = Floors.key(player.level());
		if (key == null) return Scale.BASELINE;
		int floor = Math.max(1, Floors.of(player).floor());
		double dist = distanceFromLanding(player);
		int penalty = farmPenalty(player);
		return compute(floor, dist, penalty);
	}

	public static int farmPenalty(ServerPlayer player) {
		return FARM_PENALTY.getOrDefault(player.getUUID(), 0);
	}

	/** Clears transition history and anti-farm penalty when a trip begins or ends. */
	public static void reset(ServerPlayer player) {
		UUID id = player.getUUID();
		HISTORY.remove(id);
		FARM_PENALTY.remove(id);
		player.removeAttached(Holders.LANDING);
	}

	/**
	 * Records an arrival in {@code incomingLevel}. Returns {@code true} if rapid ping-ponging was detected so
	 * {@link Floors} freezes floor progression while elevating penalties.
	 */
	static boolean recordTransition(ServerPlayer player, String incomingLevel) {
		UUID id = player.getUUID();
		long now = player.level().getServer().getTickCount();
		Deque<Transition> deque = HISTORY.computeIfAbsent(id, k -> new ArrayDeque<>());
		List<Transition> recent = new ArrayList<>(deque);
		boolean pingPong = isPingPong(recent, incomingLevel, now);
		if (pingPong) {
			int next = Math.min(MAX_PENALTY, FARM_PENALTY.getOrDefault(id, 0) + 1);
			FARM_PENALTY.put(id, next);
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.difficulty.anti_farm", next)
					.withStyle(ChatFormatting.RED));
		} else if (recent.size() >= 2) {
			String l1 = recent.get(recent.size() - 1).level();
			String l2 = recent.get(recent.size() - 2).level();
			if (!incomingLevel.equals(l1) && !incomingLevel.equals(l2)) {
				FARM_PENALTY.remove(id);
			}
		}
		deque.addLast(new Transition(incomingLevel, now));
		while (deque.size() > MAX_RECENT) deque.removeFirst();
		return pingPong;
	}

	/** Records the current position as the landing spot for distance-band scaling. */
	static void recordLanding(ServerPlayer player) {
		String dim = player.level().dimension().identifier().toString();
		int[] coords = effectiveCoords(player);
		player.setAttached(Holders.LANDING, new LandingSpot(dim, coords[0], coords[1]));
	}

	/** Horizontal Euclidean distance from the player's landing spot on the current level (torus-aware in Level 0). */
	public static double distanceFromLanding(ServerPlayer player) {
		LandingSpot spot = player.getAttached(Holders.LANDING);
		String currentDim = player.level().dimension().identifier().toString();
		if (spot == null || !spot.dimension().equals(currentDim)) return 0.0;
		int[] coords = effectiveCoords(player);
		return Math.hypot(coords[0] - (double) spot.x(), coords[1] - (double) spot.z());
	}

	private static int[] effectiveCoords(ServerPlayer player) {
		int bx = player.getBlockX(), bz = player.getBlockZ();
		if (player.level().dimension() == Level0.LEVEL_0) {
			return new int[] {Level0Space.walkedX(player, bx), Level0Space.walkedZ(player, bz)};
		}
		return new int[] {bx, bz};
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (Home.inBackrooms(player.level().dimension())) send(player);
		}
	}

	static void send(ServerPlayer player) {
		if (!Home.inBackrooms(player.level().dimension())) {
			ServerPlayNetworking.send(player, DifficultyPayload.NONE);
			return;
		}
		Scale s = of(player);
		ServerPlayNetworking.send(player, new DifficultyPayload(
				true, s.band(), (int) Math.round(s.distance()), (float) s.hpMult(), s.farmPenalty()));
	}

	private static void onEntityLoad(net.minecraft.world.entity.Entity entity, ServerLevel level) {
		if (!Home.inBackrooms(level.dimension()) || !(entity instanceof Monster monster)) return;
		if (!BackroomsMod.MOD_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(monster.getType()).getNamespace())) return;
		if (monster.entityTags().contains(SCALED_TAG)) return;

		ServerPlayer nearest = level.players().stream()
				.filter(p -> !p.isSpectator() && p.distanceToSqr(monster) <= 96.0 * 96.0)
				.min(Comparator.comparingDouble(p -> p.distanceToSqr(monster)))
				.orElse(null);
		if (nearest == null) return;

		monster.addTag(SCALED_TAG);
		Scale s = of(nearest);
		if (s.hpMult() <= 1.001 && s.damageMult() <= 1.001) return;

		AttributeInstance hpAttr = monster.getAttribute(Attributes.MAX_HEALTH);
		if (hpAttr != null && s.hpMult() > 1.001) {
			float prevMax = monster.getMaxHealth();
			boolean wasFull = monster.getHealth() >= prevMax - 0.5F;
			hpAttr.addOrReplacePermanentModifier(new AttributeModifier(
					HP_MOD_ID, s.hpMult() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			if (wasFull) monster.setHealth(monster.getMaxHealth());
		}

		AttributeInstance dmgAttr = monster.getAttribute(Attributes.ATTACK_DAMAGE);
		if (dmgAttr != null && s.damageMult() > 1.001) {
			dmgAttr.addOrReplacePermanentModifier(new AttributeModifier(
					DMG_MOD_ID, s.damageMult() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	private static ServerPlayer resolveLootPlayer(ServerLevel level, net.minecraft.world.level.storage.loot.LootContext context) {
		var attacker = context.getOptionalParameter(LootContextParams.ATTACKING_ENTITY);
		if (attacker instanceof ServerPlayer sp) return sp;
		var killer = context.getOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER);
		if (killer instanceof ServerPlayer sp) return sp;
		var target = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
		if (target instanceof ServerPlayer sp) return sp;
		Vec3 origin = context.getOptionalParameter(LootContextParams.ORIGIN);
		if (origin == null && target != null) origin = target.position();
		if (origin == null) return null;
		final Vec3 center = origin;
		return level.players().stream()
				.filter(p -> !p.isSpectator() && p.position().distanceToSqr(center) <= 48.0 * 48.0)
				.min(Comparator.comparingDouble(p -> p.position().distanceToSqr(center)))
				.orElse(null);
	}

	private static boolean isProtected(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return PROTECTED_LOOT.contains(path);
	}

	private static void degradeDrops(List<ItemStack> drops, int penalty, net.minecraft.util.RandomSource random) {
		float cutChance = Math.min(0.75F, 0.25F * penalty);
		drops.removeIf(stack -> {
			if (stack.isEmpty() || isProtected(stack)) return false;
			if (random.nextFloat() >= cutChance) return false;
			if (stack.getCount() <= 1) return true;
			stack.setCount(Math.max(1, stack.getCount() / 2));
			return false;
		});
	}

	private static void amplifyDrops(List<ItemStack> drops, double lootBonus, boolean isContainer,
			net.minecraft.util.RandomSource random) {
		List<ItemStack> stackable = new ArrayList<>();
		for (ItemStack stack : drops) {
			if (!stack.isEmpty() && !isProtected(stack) && stack.getMaxStackSize() > 1
					&& stack.getCount() < stack.getMaxStackSize()) {
				stackable.add(stack);
			}
		}
		if (!stackable.isEmpty() && random.nextDouble() < Math.min(0.85, lootBonus)) {
			ItemStack picked = stackable.get(random.nextInt(stackable.size()));
			int extra = lootBonus >= 1.1 ? 2 : 1;
			picked.setCount(Math.min(picked.getMaxStackSize(), picked.getCount() + extra));
		}
		if (isContainer && lootBonus >= 0.65 && random.nextDouble() < Math.min(0.35, (lootBonus - 0.5) * 0.5)) {
			Item coin = BuiltInRegistries.ITEM.getValue(BackroomsMod.id("lost_old_coin"));
			if (coin != net.minecraft.world.item.Items.AIR) {
				drops.add(new ItemStack(coin, 1));
			}
		}
	}

	private static void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
				Commands.literal("backrooms").then(Commands.literal("difficulty").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Scale s = of(player);
					player.sendSystemMessage(Component.translatable(
							"message.noclip_backrooms.difficulty.info",
							s.floor(),
							s.band() + 1,
							(int) Math.round(s.distance()),
							String.format(Locale.ROOT, "%.2f", s.hpMult()),
							String.format(Locale.ROOT, "%.2f", s.damageMult()),
							String.format(Locale.ROOT, "%+.0f%%", s.lootBonus() * 100.0),
							s.farmPenalty()).withStyle(ChatFormatting.AQUA));
					return 1;
				}))));
	}
}
