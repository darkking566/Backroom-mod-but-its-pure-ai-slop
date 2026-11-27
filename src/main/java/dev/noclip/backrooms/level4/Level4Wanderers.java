package dev.noclip.backrooms.level4;

import dev.noclip.backrooms.core.economy.Economy;
import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.people.People;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.items.BackroomsItems;
import dev.noclip.backrooms.level1.Level1Items;
import dev.noclip.backrooms.level4.Level4Layout.Kind;
import dev.noclip.backrooms.level4.Level4Plan.RoomBox;
import dev.noclip.backrooms.level4.Level4Plan.ZoneKind;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Friendly roaming wanderers in Level 4: rare fellow survivors strolling through open-plan office bays,
 * suites, and atriums who provide one-time role-specific field aid, useful supply barters (including buying
 * T.B.D. curios and office tech scraps), and directional tips.
 */
final class Level4Wanderers {
	static final String TAG = "noclip_backrooms_level4_wanderer";
	private static final int SPAWN_INTERVAL = 20 * 40;
	private static final int MAX_NEAR = 2, NEAR_RADIUS = 64, MIN_DIST = 20, MAX_DIST = 44, DESPAWN_DIST = 80;
	private static final double WALK_SPEED = 0.045;
	private static final int CHAT_COOLDOWN = 50, SANITY_COOLDOWN = 20 * 60, MAX_BARTERS = 3;

	private static final String[][] ROLES = {
			{"colonist", "drifter"},
			{"trader", "barterer"},
			{"speednoclipper", "scout"},
			{"aiko", "cartographer"},
			{"tom", "restless"}
	};

	private static final class WanderState {
		Vec3 goal;
		int waitUntil;
		int giveUp;
		int stuckTicks;
		int sidestepTicks;
		int blockedTicks;
	}

	private static final Map<UUID, WanderState> STATES = new HashMap<>();
	private static final Map<UUID, String> ROLES_BY_ID = new HashMap<>();
	private static final Set<UUID> AID_GIVEN = new HashSet<>();
	private static final Map<UUID, Integer> BARTERS_LEFT = new HashMap<>();
	private static final Map<UUID, Integer> CHAT_INDEX = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_CHAT = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_SANITY = new HashMap<>();
	private static int heldOffUntil;

	private Level4Wanderers() {
	}

	static boolean isWanderZone(ZoneKind zone) {
		return zone == ZoneKind.OPEN_OFFICE || zone == ZoneKind.SUITE || zone == ZoneKind.ATRIUM;
	}

	static void holdOff(MinecraftServer server, int ticks) {
		heldOffUntil = server.getTickCount() + ticks;
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			STATES.clear();
			ROLES_BY_ID.clear();
			AID_GIVEN.clear();
			BARTERS_LEFT.clear();
			CHAT_INDEX.clear();
			NEXT_CHAT.clear();
			NEXT_SANITY.clear();
			heldOffUntil = 0;
		});

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG)) entity.discard();
		});

		ServerTickEvents.END_SERVER_TICK.register(Level4Wanderers::tick);

		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			if (world.dimension() != Level4.LEVEL_4 || !(entity instanceof Person person) || !person.entityTags().contains(TAG)) {
				return InteractionResult.PASS;
			}
			int now = sp.level().getServer().getTickCount();
			face(person, sp.position());
			WanderState state = STATES.computeIfAbsent(person.getUUID(), id -> new WanderState());
			state.waitUntil = Math.max(state.waitUntil, now + 100);

			ItemStack held = sp.getMainHandItem();
			if (isBarterItem(held)) {
				return handleBarter(sp, person, held);
			}

			// First empty-handed encounter with each wanderer provides real role-specific field aid
			if (AID_GIVEN.add(person.getUUID())) {
				giveFieldAid(sp, person);
				return InteractionResult.SUCCESS;
			}

			if (now < NEXT_CHAT.getOrDefault(sp.getUUID(), 0)) {
				return InteractionResult.SUCCESS;
			}
			NEXT_CHAT.put(sp.getUUID(), now + CHAT_COOLDOWN);

			if (now >= NEXT_SANITY.getOrDefault(sp.getUUID(), 0)) {
				NEXT_SANITY.put(sp.getUUID(), now + SANITY_COOLDOWN);
				PlayerRpg.restoreSanity(sp, 5.0F);
			}

			world.playSound(null, person.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.7F, 1.0F);
			long seed = Level4ChunkGenerator.planSeed(((ServerLevel) world).getChunkSource().randomState());
			int idx = CHAT_INDEX.getOrDefault(person.getUUID(), 0);
			CHAT_INDEX.put(person.getUUID(), (idx + 1) % 7);
			sp.sendOverlayMessage(tipMessage(seed, sp.getBlockX(), sp.getBlockZ(), idx));
			return InteractionResult.SUCCESS;
		});
	}

	private static void giveFieldAid(ServerPlayer sp, Person person) {
		String role = ROLES_BY_ID.getOrDefault(person.getUUID(), "drifter");
		switch (role) {
			case "barterer" -> {
				Wallet.add(sp, 12);
				giveItem(sp, new ItemStack(BackroomsItems.FLASHLIGHT_BATTERY_PACK));
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.wanderer_aid_barterer"));
			}
			case "scout" -> {
				sp.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 45, 0, true, false, true));
				sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 45, 0, true, false, true));
				giveItem(sp, new ItemStack(Level1Items.PHOSPHOR_POWDER, 2));
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.wanderer_aid_scout"));
			}
			case "cartographer" -> {
				PlayerRpg.grantXp(sp, 15);
				PlayerRpg.restoreSanity(sp, 10.0F);
				giveItem(sp, new ItemStack(Economy.LOST_COIN));
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.wanderer_aid_cartographer"));
			}
			case "restless" -> {
				sp.heal(6.0F);
				sp.removeEffect(MobEffects.DARKNESS);
				sp.removeEffect(MobEffects.SLOWNESS);
				sp.removeEffect(MobEffects.WEAKNESS);
				PlayerRpg.restoreSanity(sp, 10.0F);
				giveItem(sp, new ItemStack(BackroomsItems.ROUGH_BANDAGE));
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.wanderer_aid_restless"));
			}
			default -> {
				giveItem(sp, new ItemStack(Items.CHARCOAL, 2));
				PlayerRpg.restoreSanity(sp, 8.0F);
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.wanderer_aid_drifter"));
			}
		}
		sp.level().playSound(null, person.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.7F, 1.05F);
	}

	private static boolean isBarterItem(ItemStack held) {
		return held.is(Economy.LOST_COIN)
				|| held.is(Level1Items.ALLOY)
				|| held.is(BackroomsItems.OLD_CIRCUIT_BOARD)
				|| held.is(BackroomsItems.OFFICE_KEYBOARD)
				|| Level4Items.CURIOS.contains(held.getItem())
				|| ((held.is(BackroomsItems.DAMP_PAPER_SCRAP) || held.is(Items.PAPER)) && held.getCount() >= 2);
	}

	private static InteractionResult handleBarter(ServerPlayer sp, Person person, ItemStack held) {
		int left = BARTERS_LEFT.getOrDefault(person.getUUID(), MAX_BARTERS);
		if (left <= 0) {
			sp.level().playSound(null, person.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.7F, 1.0F);
			sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.wanderer_barter_done"));
			return InteractionResult.SUCCESS;
		}
		if (held.is(Economy.LOST_COIN)) {
			if (!sp.getAbilities().instabuild) held.shrink(1);
			giveItem(sp, new ItemStack(Level1Items.ALMOND_WATER));
			giveItem(sp, new ItemStack(Items.GLASS_BOTTLE));
			giveItem(sp, new ItemStack(BackroomsItems.FLASHLIGHT_BATTERY_PACK));
			if (sp.level().getRandom().nextInt(100) < 18) {
				giveItem(sp, new ItemStack(Level4Items.FADED_ICON_FRAME));
			}
			sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.wanderer_barter_coin"));
		} else if (held.is(Level1Items.ALLOY)) {
			if (!sp.getAbilities().instabuild) held.shrink(1);
			giveItem(sp, new ItemStack(BackroomsItems.PURIFIED_ALMOND_ESSENCE));
			giveItem(sp, new ItemStack(BackroomsItems.STERILE_BANDAGE));
			giveItem(sp, new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("black_dye")), 2));
			sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.wanderer_barter_alloy"));
		} else if (Level4Items.CURIOS.contains(held.getItem())) {
			if (!sp.getAbilities().instabuild) held.shrink(1);
			Wallet.add(sp, 20);
			giveItem(sp, new ItemStack(BackroomsItems.STERILE_BANDAGE));
			sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.wanderer_barter_curio"));
		} else if (held.is(BackroomsItems.OLD_CIRCUIT_BOARD) || held.is(BackroomsItems.OFFICE_KEYBOARD)) {
			if (!sp.getAbilities().instabuild) held.shrink(1);
			giveItem(sp, new ItemStack(Economy.LOST_COIN));
			giveItem(sp, new ItemStack(BackroomsItems.FLASHLIGHT_BATTERY_PACK));
			sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.wanderer_barter_tech"));
		} else {
			if (!sp.getAbilities().instabuild) held.shrink(2);
			giveItem(sp, new ItemStack(Items.CHARCOAL, 2));
			giveItem(sp, new ItemStack(BackroomsItems.ROUGH_BANDAGE));
			sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.wanderer_barter_paper"));
		}
		BARTERS_LEFT.put(person.getUUID(), left - 1);
		sp.level().playSound(null, person.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.75F, 1.0F);
		return InteractionResult.SUCCESS;
	}

	private static Component tipMessage(long seed, int x, int z, int idx) {
		return switch (idx) {
			case 0 -> {
				int[] omega = Level4Events.nearestOmega(seed, x, z);
				if (omega != null) {
					int dx = omega[0] - x, dz = omega[1] - z;
					int dist = (int) Math.round(Math.hypot(dx, dz));
					yield Component.translatable("message.noclip_backrooms.level4.wanderer_tip_omega",
							Level4Events.compass(dx, dz), dist);
				}
				yield Component.translatable("message.noclip_backrooms.level4.wanderer_tip_barter");
			}
			case 1 -> {
				int[] atrium = nearestAtrium(seed, x, z);
				if (atrium != null) {
					int dx = atrium[0] - x, dz = atrium[1] - z;
					int dist = (int) Math.round(Math.hypot(dx, dz));
					yield Component.translatable("message.noclip_backrooms.level4.wanderer_tip_atrium",
							Level4Events.compass(dx, dz), dist);
				}
				yield Component.translatable("message.noclip_backrooms.level4.wanderer_tip_windows");
			}
			case 2 -> Component.translatable("message.noclip_backrooms.level4.wanderer_tip_windows");
			case 3 -> Component.translatable("message.noclip_backrooms.level4.wanderer_tip_exits");
			case 4 -> Component.translatable("message.noclip_backrooms.level4.wanderer_tip_amor");
			case 5 -> Component.translatable("message.noclip_backrooms.level4.wanderer_tip_tbd");
			default -> Component.translatable("message.noclip_backrooms.level4.wanderer_tip_barter");
		};
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level4.LEVEL_4);
		if (level == null) return;
		int now = server.getTickCount();
		long seed = Level4ChunkGenerator.planSeed(level.getChunkSource().randomState());

		if (!level.players().isEmpty()) {
			Set<UUID> seen = new HashSet<>();
			for (ServerPlayer player : level.players()) {
				for (Person person : level.getEntitiesOfClass(Person.class, player.getBoundingBox().inflate(52),
						p -> p.entityTags().contains(TAG))) {
					if (seen.add(person.getUUID())) {
						stroll(level, seed, person, now);
					}
				}
			}
			if (now % 100 == 0) {
				pruneFar(level, seen);
			}
		} else if (now % 100 == 0) {
			STATES.clear();
			ROLES_BY_ID.clear();
			AID_GIVEN.clear();
			BARTERS_LEFT.clear();
			CHAT_INDEX.clear();
		}

		if (now % SPAWN_INTERVAL == 0 && now >= heldOffUntil && !level.players().isEmpty()) {
			RandomSource rng = level.getRandom();
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator()) continue;
				int near = countNear(level, player);
				if (near < MAX_NEAR && rng.nextInt(100) < 45) {
					spawnNear(level, seed, player, 1);
				}
			}
		}
	}

	private static void pruneFar(ServerLevel level, Set<UUID> activeNearPlayers) {
		for (ServerPlayer player : level.players()) {
			for (Person person : level.getEntitiesOfClass(Person.class, player.getBoundingBox().inflate(DESPAWN_DIST + 24),
					p -> p.entityTags().contains(TAG))) {
				boolean anyClose = level.players().stream()
						.anyMatch(pl -> pl.distanceTo(person) <= DESPAWN_DIST);
				if (!anyClose) {
					person.discard();
				}
			}
		}
		STATES.keySet().removeIf(id -> !activeNearPlayers.contains(id));
		ROLES_BY_ID.keySet().removeIf(id -> !activeNearPlayers.contains(id));
		AID_GIVEN.removeIf(id -> !activeNearPlayers.contains(id));
		BARTERS_LEFT.keySet().removeIf(id -> !activeNearPlayers.contains(id));
		CHAT_INDEX.keySet().removeIf(id -> !activeNearPlayers.contains(id));
	}

	private static int countNear(ServerLevel level, ServerPlayer player) {
		AABB box = new AABB(player.blockPosition()).inflate(NEAR_RADIUS);
		return level.getEntitiesOfClass(Person.class, box, p -> p.isAlive() && p.entityTags().contains(TAG)).size();
	}

	static int spawnNear(ServerLevel level, long seed, ServerPlayer player, int count) {
		RandomSource rng = level.getRandom();
		int spawned = 0;
		for (int n = 0; n < count; n++) {
			for (int attempt = 0; attempt < 48; attempt++) {
				double angle = rng.nextDouble() * Math.PI * 2;
				double dist = MIN_DIST + rng.nextDouble() * (MAX_DIST - MIN_DIST);
				int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
				int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
				var tile = Level4Layout.tile(seed, x, z);
				if (tile.kind() != Kind.AIR || !isWanderZone(tile.zone())) continue;
				BlockPos spot = new BlockPos(x, Level4ChunkGenerator.FLOOR_Y + 1, z);
				if (!freeSpot(level, spot) || nearTrapWindow(level, spot)) continue;
				String[] pick = ROLES[rng.nextInt(ROLES.length)];
				Person person = People.type(pick[0]).create(level, EntitySpawnReason.NATURAL);
				if (person == null) break;
				float yaw = rng.nextFloat() * 360.0F;
				person.snapTo(x + 0.5, spot.getY(), z + 0.5, yaw, 0.0F);
				person.setYHeadRot(yaw);
				person.setYBodyRot(yaw);
				person.setNoAi(true);
				person.setInvulnerable(true);
				person.setPersistenceRequired();
				person.setCustomName(Component.translatable("entity.noclip_backrooms.level4_wanderer." + pick[1]));
				person.setCustomNameVisible(true);
				level.addFreshEntity(person);
				// Tag only after adding to world so ENTITY_LOAD cleanup does not discard newly spawned wanderers.
				person.addTag(TAG);
				ROLES_BY_ID.put(person.getUUID(), pick[1]);
				BARTERS_LEFT.put(person.getUUID(), MAX_BARTERS);
				spawned++;
				break;
			}
		}
		return spawned;
	}

	private static void stroll(ServerLevel level, long seed, Person person, int now) {
		WanderState state = STATES.computeIfAbsent(person.getUUID(), id -> new WanderState());
		ServerPlayer near = null;
		double bestDist = 4.5;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			double d = player.distanceTo(person);
			if (d < bestDist) {
				bestDist = d;
				near = player;
			}
		}
		if (near != null) {
			face(person, near.position());
			return;
		}
		if (now < state.waitUntil) return;

		RandomSource rng = level.getRandom();
		if (state.goal == null || now > state.giveUp) {
			state.goal = pickGoal(level, seed, person, rng);
			state.giveUp = now + 320;
			state.stuckTicks = 0;
			state.sidestepTicks = 0;
			state.blockedTicks = 0;
			if (state.goal == null) state.waitUntil = now + 60;
			return;
		}

		double remaining = step(person, state, state.goal, WALK_SPEED);
		if (remaining < 0.6) {
			state.goal = null;
			state.waitUntil = now + 50 + rng.nextInt(110);
		}
	}

	private static Vec3 pickGoal(ServerLevel level, long seed, Person person, RandomSource rng) {
		for (int attempt = 0; attempt < 24; attempt++) {
			int dx = rng.nextInt(17) - 8;
			int dz = rng.nextInt(17) - 8;
			if (dx * dx + dz * dz < 25) continue;
			int x = person.getBlockX() + dx;
			int z = person.getBlockZ() + dz;
			var tile = Level4Layout.tile(seed, x, z);
			if (tile.kind() != Kind.AIR || !isWanderZone(tile.zone())) continue;
			BlockPos at = new BlockPos(x, Level4ChunkGenerator.FLOOR_Y + 1, z);
			if (freeSpot(level, at) && !nearTrapWindow(level, at)) {
				return new Vec3(x + 0.5, at.getY(), z + 0.5);
			}
		}
		return null;
	}

	private static double step(Mob mob, WanderState state, Vec3 target, double speed) {
		Vec3 to = new Vec3(target.x - mob.getX(), 0, target.z - mob.getZ());
		double distance = to.length();
		if (distance < 0.5) return distance;
		Vec3 heading = to.scale(1.0 / distance);
		if (state.sidestepTicks > 0) {
			heading = new Vec3(-heading.z, 0, heading.x).scale((state.sidestepTicks & 1) == 0 ? 1 : -1);
			state.sidestepTicks--;
		}
		Vec3 move = heading.scale(Math.min(speed, distance));
		Vec3 before = mob.position();
		mob.move(MoverType.SELF, new Vec3(move.x, -0.1, move.z));
		double moved = mob.position().subtract(before).horizontalDistance();
		if (moved < speed * 0.3) {
			state.blockedTicks++;
			if (++state.stuckTicks > 10) {
				state.stuckTicks = 0;
				state.sidestepTicks = 22 + mob.getRandom().nextInt(2);
			}
			if (state.blockedTicks > 45) {
				state.goal = null;
				state.blockedTicks = 0;
			}
		} else {
			state.stuckTicks = 0;
			state.blockedTicks = Math.max(0, state.blockedTicks - 2);
		}
		float yaw = (float) Math.toDegrees(Math.atan2(-heading.x, heading.z));
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.yBodyRot = yaw;
		return distance;
	}

	private static void face(Mob mob, Vec3 target) {
		float yaw = (float) Math.toDegrees(Math.atan2(-(target.x - mob.getX()), target.z - mob.getZ()));
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.yBodyRot = yaw;
	}

	private static boolean freeSpot(ServerLevel level, BlockPos at) {
		return level.isLoaded(at)
				&& level.getBlockState(at).isAir()
				&& level.getBlockState(at.above()).isAir()
				&& !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty();
	}

	private static boolean nearTrapWindow(ServerLevel level, BlockPos at) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int dy = 0; dy <= 2; dy++) {
					pos.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
					if (level.isLoaded(pos) && Level4Windows.isTrapWindow(level.getBlockState(pos))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static int[] nearestAtrium(long seed, int x, int z) {
		int rx0 = Math.floorDiv(x, Level4Plan.SIZE), rz0 = Math.floorDiv(z, Level4Plan.SIZE);
		int[] best = null;
		double bestDist = Double.MAX_VALUE;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				Level4Plan plan = Level4Plan.of(seed, rx0 + dx, rz0 + dz);
				for (RoomBox room : plan.rooms) {
					if (room.kind() != ZoneKind.ATRIUM) continue;
					int ax = (rx0 + dx) * Level4Plan.SIZE + room.cx() * Level4Plan.CELL + 3;
					int az = (rz0 + dz) * Level4Plan.SIZE + room.cz() * Level4Plan.CELL + 3;
					double d = Math.hypot(ax - x, az - z);
					if (d < bestDist) {
						bestDist = d;
						best = new int[] {ax, az};
					}
				}
			}
		}
		return best;
	}

	private static void giveItem(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) player.drop(stack, false);
	}
}
