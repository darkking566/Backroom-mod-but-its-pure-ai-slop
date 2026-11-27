package dev.noclip.backrooms.core.quest;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.LevelGates;
import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.people.People;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.rpg.Badges;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.items.BackroomsItems;
import dev.noclip.backrooms.props.BackroomsProps;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

/**
 * Reusable Trapped Survivor side-quest system (docs/project/1-gameplay/README.md, "受困倖存者支線" & "小女孩事件"):
 * <ol>
 *   <li><b>STRANDED</b>: calm the survivor by handing over Almond Water or food;</li>
 *   <li><b>SEEKING_ITEM</b>: recover their dropped keepsake nearby (guided by compass direction &amp; distance);</li>
 *   <li><b>ESCORTING</b>: guide them back to a safe base, settled cot, relay box, or institute outpost;</li>
 *   <li><b>RESCUED</b>: awards survival XP, credits, Lost Old Coins, sanity, a floor clue, and {@code quest:<id>}.</li>
 * </ol>
 */
public final class Survivors {
	public static final String ENTITY_TAG = "noclip_backrooms_survivor";
	public static final String KEEPSAKE_TAG_KEY = "noclip_survivor_keepsake";
	private static final double ESCORT_STOP_DIST = 2.2;
	private static final double ESCORT_FOLLOW_DIST = 18.0;
	private static final double ESCORT_TELEPORT_DIST = 64.0;
	private static final double ESCORT_SPEED = 0.18;

	private static final Map<UUID, Long> LAST_CLICK = new ConcurrentHashMap<>();
	private static final Map<UUID, int[]> STUCK = new HashMap<>();

	public enum Script {
		LITTLE_GIRL("little_girl", "aiko"),
		LOST_SURVEYOR("lost_surveyor", "colonist");

		public final String id;
		public final String look;

		Script(String id, String look) {
			this.id = id;
			this.look = look;
		}

		public static Script byId(String id) {
			if (id != null) {
				String lower = id.toLowerCase(Locale.ROOT);
				for (Script s : values()) {
					if (s.id.equals(lower)) return s;
				}
			}
			return null;
		}
	}

	public enum Stage {
		STRANDED,
		SEEKING_ITEM,
		ESCORTING,
		RESCUED;

		public static Stage byName(String name) {
			if (name != null) {
				for (Stage s : values()) {
					if (s.name().equalsIgnoreCase(name)) return s;
				}
			}
			return STRANDED;
		}
	}

	public record Record(
			String scriptId,
			String stageName,
			String rescuerUuid,
			int keepsakeX,
			int keepsakeY,
			int keepsakeZ,
			boolean keepsakeSpawned
	) {
		private static final Codec<Record> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("script").forGetter(Record::scriptId),
				Codec.STRING.fieldOf("stage").forGetter(Record::stageName),
				Codec.STRING.optionalFieldOf("rescuer", "").forGetter(Record::rescuerUuid),
				Codec.INT.optionalFieldOf("kx", 0).forGetter(Record::keepsakeX),
				Codec.INT.optionalFieldOf("ky", 0).forGetter(Record::keepsakeY),
				Codec.INT.optionalFieldOf("kz", 0).forGetter(Record::keepsakeZ),
				Codec.BOOL.optionalFieldOf("k_spawned", false).forGetter(Record::keepsakeSpawned)
		).apply(i, Record::new));

		Stage stage() {
			return Stage.byName(stageName);
		}

		Script script() {
			Script s = Script.byId(scriptId);
			return s != null ? s : Script.LITTLE_GIRL;
		}
	}

	public static final class SurvivorData extends SavedData {
		private final Map<String, Record> records = new HashMap<>();

		private static final Codec<Map<String, Record>> MAP_CODEC = Codec.unboundedMap(Codec.STRING, Record.CODEC);
		private static final Codec<SurvivorData> CODEC = MAP_CODEC.xmap(SurvivorData::fromMap, d -> d.records);

		public static final SavedDataType<SurvivorData> TYPE = new SavedDataType<>(
				BackroomsMod.id("trapped_survivors"), SurvivorData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

		public SurvivorData() {
		}

		public static SurvivorData get(ServerLevel overworld) {
			return overworld.getDataStorage().computeIfAbsent(TYPE);
		}

		private static SurvivorData fromMap(Map<String, Record> map) {
			SurvivorData data = new SurvivorData();
			data.records.putAll(map);
			return data;
		}
	}

	private Survivors() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LAST_CLICK.clear();
			STUCK.clear();
		});

		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(entity instanceof Person survivor)) {
				return InteractionResult.PASS;
			}
			if (!survivor.entityTags().contains(ENTITY_TAG) || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			long now = sp.level().getGameTime();
			Long last = LAST_CLICK.put(sp.getUUID(), now);
			if (last != null && now >= last && now - last < 5) {
				return InteractionResult.SUCCESS;
			}
			interact(sp, survivor);
			return InteractionResult.SUCCESS;
		});

		ServerTickEvents.END_SERVER_TICK.register(Survivors::tick);

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
			dispatcher.register(Commands.literal("backrooms")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.literal("survivor")
							.then(Commands.literal("spawn").then(Commands.argument("script", StringArgumentType.word())
									.executes(ctx -> {
										ServerPlayer p = ctx.getSource().getPlayerOrException();
										Script script = Script.byId(StringArgumentType.getString(ctx, "script"));
										if (script == null) {
											p.sendSystemMessage(Component.translatable("message.noclip_backrooms.survivor.unknown_script")
													.withStyle(ChatFormatting.RED));
											return 0;
										}
										spawn(p.level(), p.getX(), p.getY(), p.getZ(), script);
										return 1;
									})))));
			dispatcher.register(Commands.literal("meg").then(Commands.literal("survivor")
					.then(Commands.literal("status").executes(ctx -> {
						showStatus(ctx.getSource().getPlayerOrException());
						return 1;
					}))));
		});
	}

	// ---- Pure testable helpers ----

	public static Stage nextStage(Stage current) {
		return switch (current) {
			case STRANDED -> Stage.SEEKING_ITEM;
			case SEEKING_ITEM -> Stage.ESCORTING;
			case ESCORTING, RESCUED -> Stage.RESCUED;
		};
	}

	public static String compassDirection(int dx, int dz) {
		if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) return "nearby";
		double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north (-Z), 90 = east (+X), 180 = south (+Z), -90 = west (-X)
		if (angle >= -22.5 && angle < 22.5) return "north";
		if (angle >= 22.5 && angle < 67.5) return "north_east";
		if (angle >= 67.5 && angle < 112.5) return "east";
		if (angle >= 112.5 && angle < 157.5) return "south_east";
		if (angle >= 157.5 || angle < -157.5) return "south";
		if (angle >= -157.5 && angle < -112.5) return "south_west";
		if (angle >= -112.5 && angle < -67.5) return "west";
		return "north_west";
	}

	/** Accepts Almond Water, Almond Elixirs/Essence, rations, or any edible food item. */
	public static boolean isSupplyItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		if (stack.has(DataComponents.FOOD)) return true;
		Item item = stack.getItem();
		if (item == BackroomsItems.ALMOND_ELIXIR
				|| item == BackroomsItems.AGED_ALMOND_RESERVE
				|| item == BackroomsItems.PURIFIED_ALMOND_ESSENCE
				|| item == BackroomsItems.PURE_ALMOND_DROP
				|| item == BackroomsItems.IMPROVISED_CALMING_TEA
				|| item == BackroomsItems.RATION_BISCUIT
				|| item == BackroomsItems.EXPIRED_CANNED_FOOD) {
			return true;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		return "noclip_backrooms".equals(id.getNamespace()) && "almond_water".equals(id.getPath());
	}

	public static ItemStack createKeepsake(UUID survivorId, Script script) {
		Item base = script == Script.LOST_SURVEYOR ? BackroomsItems.STATIC_CASSETTE : BackroomsItems.PASTEL_CHARM;
		ItemStack stack = new ItemStack(base, 1);
		stack.set(DataComponents.CUSTOM_NAME,
				Component.translatable("item.noclip_backrooms.survivor.keepsake." + script.id)
						.withStyle(ChatFormatting.LIGHT_PURPLE));
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(KEEPSAKE_TAG_KEY, survivorId.toString()));
		return stack;
	}

	public static boolean matchesKeepsake(ItemStack stack, UUID survivorId) {
		if (stack == null || stack.isEmpty() || survivorId == null) return false;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) return false;
		CompoundTag tag = data.copyTag();
		return survivorId.toString().equals(tag.getStringOr(KEEPSAKE_TAG_KEY, ""));
	}

	// ---- World spawning & queries ----

	public static Person spawn(ServerLevel level, double x, double y, double z, Script script) {
		Script resolved = script == null ? Script.LITTLE_GIRL : script;
		Person person = People.type(resolved.look).create(level, EntitySpawnReason.EVENT);
		if (person == null) return null;
		person.snapTo(x, y, z, 0.0F, 0.0F);
		person.setNoAi(true);
		person.setInvulnerable(true);
		person.setPersistenceRequired();
		person.addTag(ENTITY_TAG);
		person.setCustomName(Component.translatable("survivor.noclip_backrooms." + resolved.id + ".name"));
		person.setCustomNameVisible(true);
		level.addFreshEntity(person);

		SurvivorData data = data(level.getServer());
		data.records.put(person.getUUID().toString(),
				new Record(resolved.id, Stage.STRANDED.name(), "", 0, 0, 0, false));
		data.setDirty();
		return person;
	}

	public static SurvivorData data(MinecraftServer server) {
		return SurvivorData.get(server.overworld());
	}

	public static Stage stageOf(ServerLevel level, UUID survivorId) {
		if (level == null || survivorId == null) return Stage.STRANDED;
		Record rec = data(level.getServer()).records.get(survivorId.toString());
		return rec == null ? Stage.STRANDED : rec.stage();
	}

	public static BlockPos keepsakePosOf(ServerLevel level, UUID survivorId) {
		if (level == null || survivorId == null) return null;
		Record rec = data(level.getServer()).records.get(survivorId.toString());
		if (rec == null || !rec.keepsakeSpawned()) return null;
		return new BlockPos(rec.keepsakeX(), rec.keepsakeY(), rec.keepsakeZ());
	}

	// ---- Dialogue & quest interaction ----

	private static void interact(ServerPlayer player, Person survivor) {
		MinecraftServer server = player.level().getServer();
		SurvivorData data = data(server);
		String key = survivor.getUUID().toString();
		Record rec = data.records.computeIfAbsent(key,
				k -> new Record(Script.LITTLE_GIRL.id, Stage.STRANDED.name(), "", 0, 0, 0, false));
		Script script = rec.script();

		face(survivor, player.position());

		switch (rec.stage()) {
			case STRANDED -> {
				ItemStack held = player.getMainHandItem();
				if (isSupplyItem(held)) {
					if (!player.isCreative()) {
						held.shrink(1);
						player.getInventory().setChanged();
					}
					BlockPos spot = pickKeepsakeSpot((ServerLevel) survivor.level(), survivor.blockPosition());
					spawnKeepsakeEntity((ServerLevel) survivor.level(), spot, survivor.getUUID(), script);
					Record next = new Record(
							script.id,
							Stage.SEEKING_ITEM.name(),
							player.getUUID().toString(),
							spot.getX(), spot.getY(), spot.getZ(),
							true);
					data.records.put(key, next);
					data.setDirty();

					survivor.level().playSound(null, survivor.getX(), survivor.getY(), survivor.getZ(),
							SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.85F, 1.25F);
					if (survivor.level() instanceof ServerLevel sl) {
						sl.sendParticles(ParticleTypes.HEART, survivor.getX(), survivor.getY() + 1.5, survivor.getZ(),
								6, 0.3, 0.3, 0.3, 0.02);
					}
					sendKeepsakeHint(player, survivor, script, spot, true);
				} else {
					player.sendSystemMessage(Component.translatable("message.noclip_backrooms.survivor." + script.id + ".plea")
							.withStyle(ChatFormatting.YELLOW));
				}
			}
			case SEEKING_ITEM -> {
				if (consumeKeepsake(player, survivor.getUUID())) {
					Record next = new Record(
							script.id,
							Stage.ESCORTING.name(),
							player.getUUID().toString(),
							rec.keepsakeX(), rec.keepsakeY(), rec.keepsakeZ(),
							true);
					data.records.put(key, next);
					data.setDirty();

					survivor.level().playSound(null, survivor.getX(), survivor.getY(), survivor.getZ(),
							SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.9F, 1.3F);
					player.sendSystemMessage(Component.translatable("message.noclip_backrooms.survivor." + script.id + ".found")
							.withStyle(ChatFormatting.GREEN));
					player.sendSystemMessage(Component.translatable("message.noclip_backrooms.survivor.escort_start")
							.withStyle(ChatFormatting.AQUA));
				} else {
					BlockPos spot = new BlockPos(rec.keepsakeX(), rec.keepsakeY(), rec.keepsakeZ());
					if (!hasWorldKeepsake((ServerLevel) survivor.level(), survivor.getUUID(), spot)) {
						BlockPos freshSpot = pickKeepsakeSpot((ServerLevel) survivor.level(), survivor.blockPosition());
						spawnKeepsakeEntity((ServerLevel) survivor.level(), freshSpot, survivor.getUUID(), script);
						Record refreshed = new Record(
								script.id, Stage.SEEKING_ITEM.name(), player.getUUID().toString(),
								freshSpot.getX(), freshSpot.getY(), freshSpot.getZ(), true);
						data.records.put(key, refreshed);
						data.setDirty();
						spot = freshSpot;
					}
					sendKeepsakeHint(player, survivor, script, spot, false);
				}
			}
			case ESCORTING -> {
				if (!player.getUUID().toString().equals(rec.rescuerUuid())) {
					data.records.put(key, new Record(
							script.id, Stage.ESCORTING.name(), player.getUUID().toString(),
							rec.keepsakeX(), rec.keepsakeY(), rec.keepsakeZ(), rec.keepsakeSpawned()));
					data.setDirty();
				}
				if (isAtSafePoint(survivor, player)) {
					completeRescue(survivor, player, data, rec);
				} else {
					player.sendSystemMessage(Component.translatable("message.noclip_backrooms.survivor.escort_remind")
							.withStyle(ChatFormatting.AQUA));
				}
			}
			case RESCUED -> player.sendSystemMessage(
					Component.translatable("message.noclip_backrooms.survivor." + script.id + ".rescued_chat")
							.withStyle(ChatFormatting.GREEN));
		}
	}

	private static void sendKeepsakeHint(ServerPlayer player, Person survivor, Script script, BlockPos spot, boolean fedNow) {
		if (fedNow) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.survivor." + script.id + ".fed")
					.withStyle(ChatFormatting.GREEN));
		}
		int dx = spot.getX() - survivor.getBlockX();
		int dz = spot.getZ() - survivor.getBlockZ();
		int dist = Math.max(1, (int) Math.round(Math.hypot(dx, dz)));
		Component dirName = Component.translatable("direction.noclip_backrooms." + compassDirection(dx, dz));
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.survivor.seek_hint", dirName, dist)
				.withStyle(ChatFormatting.GOLD));
	}

	private static BlockPos pickKeepsakeSpot(ServerLevel level, BlockPos origin) {
		int[][] rings = { {10, 22}, {4, 10}, {2, 4} };
		for (int[] ring : rings) {
			for (int attempt = 0; attempt < 24; attempt++) {
				double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
				double radius = ring[0] + level.getRandom().nextDouble() * (ring[1] - ring[0]);
				int x = origin.getX() + (int) Math.round(Math.cos(angle) * radius);
				int z = origin.getZ() + (int) Math.round(Math.sin(angle) * radius);
				for (int dy = -2; dy <= 2; dy++) {
					BlockPos candidate = new BlockPos(x, origin.getY() + dy, z);
					if (level.getBlockState(candidate).isAir()
							&& level.getBlockState(candidate.above()).isAir()
							&& !level.getBlockState(candidate.below()).getCollisionShape(level, candidate.below()).isEmpty()) {
						return candidate;
					}
				}
			}
		}
		return origin.east(2);
	}

	private static void spawnKeepsakeEntity(ServerLevel level, BlockPos pos, UUID survivorId, Script script) {
		ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.35, pos.getZ() + 0.5,
				createKeepsake(survivorId, script));
		drop.setDeltaMovement(Vec3.ZERO);
		drop.setGlowingTag(true);
		drop.setUnlimitedLifetime();
		drop.setNoPickUpDelay();
		level.addFreshEntity(drop);
	}

	private static boolean hasWorldKeepsake(ServerLevel level, UUID survivorId, BlockPos hintPos) {
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
				new net.minecraft.world.phys.AABB(hintPos).inflate(40.0))) {
			if (item.isAlive() && matchesKeepsake(item.getItem(), survivorId)) return true;
		}
		return false;
	}

	private static boolean consumeKeepsake(ServerPlayer player, UUID survivorId) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (matchesKeepsake(s, survivorId)) {
				s.shrink(1);
				inv.setChanged();
				return true;
			}
		}
		return false;
	}

	// ---- Escort following & safe arrival ----

	private static void tick(MinecraftServer server) {
		SurvivorData data = data(server);
		if (data.records.isEmpty()) return;
		boolean checkSafe = server.getTickCount() % 10 == 0;

		for (ServerLevel level : server.getAllLevels()) {
			List<Person> people = level.getEntitiesOfClass(Person.class,
					new net.minecraft.world.phys.AABB(-30000000, -64, -30000000, 30000000, 320, 30000000),
					p -> p.isAlive() && p.entityTags().contains(ENTITY_TAG));
			if (people.isEmpty()) continue;

			for (Person survivor : people) {
				Record rec = data.records.get(survivor.getUUID().toString());
				if (rec == null || rec.stage() != Stage.ESCORTING) continue;
				ServerPlayer rescuer = findRescuer(level, rec.rescuerUuid());
				if (rescuer == null || !rescuer.isAlive()) continue;

				double dist = survivor.distanceTo(rescuer);
				if (dist > ESCORT_FOLLOW_DIST && dist <= ESCORT_TELEPORT_DIST) {
					survivor.snapTo(rescuer.getX(), rescuer.getY(), rescuer.getZ(), survivor.getYRot(), 0.0F);
				} else if (dist > ESCORT_STOP_DIST && dist <= ESCORT_FOLLOW_DIST) {
					stepToward(survivor, rescuer.position(), ESCORT_SPEED);
				} else {
					face(survivor, rescuer.position());
				}

				if (checkSafe && isAtSafePoint(survivor, rescuer)) {
					completeRescue(survivor, rescuer, data, rec);
				}
			}
		}
	}

	private static ServerPlayer findRescuer(ServerLevel level, String rescuerUuid) {
		if (rescuerUuid == null || rescuerUuid.isEmpty()) return null;
		try {
			UUID id = UUID.fromString(rescuerUuid);
			Entity e = level.getEntity(id);
			if (e instanceof ServerPlayer sp && sp.level() == level) return sp;
		} catch (IllegalArgumentException ignored) {
		}
		return null;
	}

	public static boolean isAtSafePoint(Person survivor, ServerPlayer rescuer) {
		if (survivor == null || rescuer == null || survivor.level() != rescuer.level()) return false;
		if (Home.inBackrooms(survivor.level().dimension())) {
			if (Badges.inSafeZone(rescuer) && survivor.distanceTo(rescuer) <= 12.0) return true;
			BlockPos center = survivor.blockPosition();
			for (BlockPos p : BlockPos.betweenClosed(center.offset(-10, -4, -10), center.offset(10, 4, 10))) {
				if (survivor.level().getBlockState(p).is(BackroomsProps.RELAY_DROPOFF_BOX)) return true;
			}
			return !survivor.level().getEntitiesOfClass(Person.class, survivor.getBoundingBox().inflate(10.0),
					p -> ShopRole.of(p) != null).isEmpty();
		}
		return !survivor.level().getEntitiesOfClass(Person.class, survivor.getBoundingBox().inflate(14.0), p -> {
			ShopRole r = ShopRole.of(p);
			return r == ShopRole.MASTER || r == ShopRole.MEDIC || r == ShopRole.SUPPLIER || r == ShopRole.BUYER;
		}).isEmpty();
	}

	private static void completeRescue(Person survivor, ServerPlayer rescuer, SurvivorData data, Record rec) {
		Script script = rec.script();
		data.records.put(survivor.getUUID().toString(),
				new Record(script.id, Stage.RESCUED.name(), rescuer.getUUID().toString(),
						rec.keepsakeX(), rec.keepsakeY(), rec.keepsakeZ(), rec.keepsakeSpawned()));
		data.setDirty();
		STUCK.remove(survivor.getUUID());

		int floor = Home.inBackrooms(rescuer.level().dimension()) ? Math.max(1, Floors.of(rescuer).floor()) : 1;
		int luck = PlayerRpg.get(rescuer).luckUnlocked() ? PlayerRpg.get(rescuer).luck() : 0;
		int xp = 80 + floor * 20;
		long credits = 120L + floor * 35L;
		int coins = 2 + (luck >= 6 ? 1 : 0);

		PlayerRpg.grantXp(rescuer, xp);
		Wallet.add(rescuer, credits);
		ItemStack coinStack = new ItemStack(BackroomsItems.LOST_OLD_COIN, coins);
		if (!rescuer.getInventory().add(coinStack)) rescuer.drop(coinStack, false);
		PlayerRpg.restoreSanity(rescuer, 25.0F);
		LevelGates.grantFlag(rescuer.level().getServer(), "quest:" + script.id);

		survivor.level().playSound(null, survivor.getX(), survivor.getY(), survivor.getZ(),
				SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
		if (survivor.level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, survivor.getX(), survivor.getY() + 1.4, survivor.getZ(),
					16, 0.5, 0.6, 0.5, 0.05);
		}
		rescuer.sendSystemMessage(Component.translatable("message.noclip_backrooms.survivor.reward",
				survivor.getName(), xp, credits, coins).withStyle(ChatFormatting.GOLD));
		String clueSuffix = switch (rescuer.level().dimension().identifier().getPath()) {
			case "level_0", "level_1", "level_2", "level_3", "level_4", "frontrooms" ->
					rescuer.level().dimension().identifier().getPath();
			default -> "default";
		};
		rescuer.sendSystemMessage(Component.translatable("message.noclip_backrooms.survivor.clue." + clueSuffix)
				.withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	private static void showStatus(ServerPlayer player) {
		List<Person> nearby = player.level().getEntitiesOfClass(Person.class, player.getBoundingBox().inflate(48.0),
				p -> p.isAlive() && p.entityTags().contains(ENTITY_TAG));
		if (nearby.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.survivor.status_none")
					.withStyle(ChatFormatting.GRAY));
			return;
		}
		SurvivorData data = data(player.level().getServer());
		for (Person s : nearby) {
			Record rec = data.records.get(s.getUUID().toString());
			Stage stage = rec == null ? Stage.STRANDED : rec.stage();
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.survivor.status_line",
					s.getName(), Component.translatable("survivor.noclip_backrooms.stage." + stage.name().toLowerCase(Locale.ROOT)))
					.withStyle(ChatFormatting.AQUA));
		}
	}

	private static void stepToward(Person mob, Vec3 target, double speed) {
		Vec3 to = new Vec3(target.x - mob.getX(), 0, target.z - mob.getZ());
		double distance = to.length();
		if (distance < 0.4) return;
		int[] stuck = STUCK.computeIfAbsent(mob.getUUID(), id -> new int[] {0, 0, 0});
		Vec3 heading = to.scale(1.0 / distance);
		if (stuck[1] > 0) {
			heading = new Vec3(-heading.z, 0, heading.x).scale(stuck[1] % 2 == 0 ? 1 : -1);
			stuck[1]--;
		}
		Vec3 move = heading.scale(Math.min(speed, distance));
		Vec3 before = mob.position();
		mob.move(MoverType.SELF, new Vec3(move.x, -0.1, move.z));
		double moved = mob.position().subtract(before).horizontalDistance();
		if (moved < speed * 0.3) {
			stuck[2]++;
			if (++stuck[0] > 10) {
				stuck[0] = 0;
				stuck[1] = 20;
			}
			if (stuck[2] > 45) {
				stuck[2] = 0;
				mob.snapTo(target.x, target.y, target.z, mob.getYRot(), 0.0F);
			}
		} else {
			stuck[0] = 0;
			stuck[2] = Math.max(0, stuck[2] - 2);
		}
		float yaw = (float) Math.toDegrees(Math.atan2(-heading.x, heading.z));
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.yBodyRot = yaw;
	}

	private static void face(Person mob, Vec3 target) {
		float yaw = (float) Math.toDegrees(Math.atan2(-(target.x - mob.getX()), target.z - mob.getZ()));
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.yBodyRot = yaw;
	}
}
