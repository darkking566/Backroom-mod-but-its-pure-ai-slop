package dev.noclip.backrooms.level4;

import dev.noclip.backrooms.core.economy.Economy;
import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.people.People;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.core.trip.Trips;
import dev.noclip.backrooms.items.BackroomsItems;
import dev.noclip.backrooms.level1.Level1Items;
import dev.noclip.backrooms.level4.Level4Plan.RoomBox;
import dev.noclip.backrooms.level4.Level4Plan.ZoneKind;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Populates and handles interactions for Level 4's three documented communities and interactive fixtures:
 * <ul>
 *   <li><b>M.E.G. Base Omega</b>: major multi-squad garrison (10 personnel) with active checkpoint guards,
 *       medics, cots (settle/respawn), supply lockers, and shops.</li>
 *   <li><b>Amor Incrementum</b>: 10-member agricultural cult growing Level 0/1 wall-fungi; hostile (and fightable)
 *       unless holding a religious relic, which allows trading or direct relic offering.</li>
 *   <li><b>T.B.D.</b>: 6 reclusive hoarders guarding personal supply crates who ignore you and only sell
 *       useless curios.</li>
 * </ul>
 */
final class Level4Bases {
	static final String STAFF_TAG = "noclip_backrooms_level4_omega";
	static final String OMEGA_GUARD_TAG = "noclip_backrooms_level4_omega_guard";
	static final String OMEGA_SQUAD_TAG = "noclip_backrooms_level4_omega_squad";
	static final String MEDIC_TAG = "noclip_backrooms_level4_omega_medic";
	static final String AMOR_TAG = "noclip_backrooms_level4_amor";
	static final String TBD_TAG = "noclip_backrooms_level4_tbd";

	private static final int POPULATE_INTERVAL = 20 * 8;
	private static final int LOCKER_COOLDOWN = 20 * 60 * 5;
	private static final int MEDIC_COOLDOWN = 20 * 60;
	private static final int GUARD_ATTACK_COOLDOWN = 25;

	private static final class StrollState {
		Vec3 goal;
		int waitUntil;
		int giveUp;
	}

	private static final Map<BlockPos, Integer> LOCKER_NEXT = new HashMap<>();
	private static final Map<BlockPos, Boolean> SHELF_TAKEN = new HashMap<>();
	private static final Map<UUID, Integer> MEDIC_NEXT = new HashMap<>();
	private static final Map<UUID, Integer> AMOR_ATTACK_NEXT = new HashMap<>();
	private static final Map<UUID, Integer> AMOR_WARN_NEXT = new HashMap<>();
	private static final Map<UUID, Integer> AMOR_PACIFY_NEXT = new HashMap<>();
	private static final Map<UUID, Integer> GUARD_ATTACK_NEXT = new HashMap<>();
	private static final Map<UUID, StrollState> STROLL_STATES = new HashMap<>();

	private Level4Bases() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOCKER_NEXT.clear();
			SHELF_TAKEN.clear();
			MEDIC_NEXT.clear();
			AMOR_ATTACK_NEXT.clear();
			AMOR_WARN_NEXT.clear();
			AMOR_PACIFY_NEXT.clear();
			GUARD_ATTACK_NEXT.clear();
			STROLL_STATES.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(Level4Bases::tick);

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			if (world.dimension() != Level4.LEVEL_4) return InteractionResult.PASS;
			BlockPos pos = hit.getBlockPos();
			BlockState state = world.getBlockState(pos);
			int now = sp.level().getServer().getTickCount();

			if (state.is(Level4Blocks.OMEGA_COT)) {
				LevelData.RespawnData spot = LevelData.RespawnData.of(world.dimension(), sp.blockPosition(), sp.getYRot(), 0);
				Trips.settle(sp, spot);
				world.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8F, 0.8F);
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.settled",
						Component.translatable("settlement.noclip_backrooms.omega")));
				return InteractionResult.SUCCESS;
			}

			if (state.is(Level4Blocks.OMEGA_LOCKER)) {
				ItemStack held = sp.getMainHandItem();
				boolean alloySwap = held.is(Level1Items.ALLOY);
				int next = LOCKER_NEXT.getOrDefault(pos, 0);
				if (!alloySwap && now < next) {
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.locker_empty"));
					return InteractionResult.SUCCESS;
				}
				if (alloySwap && !sp.getAbilities().instabuild) held.shrink(1);
				LOCKER_NEXT.put(pos.immutable(), now + LOCKER_COOLDOWN);
				giveLockerLoot(sp, world.getRandom());
				world.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.7F, 1.2F);
				return InteractionResult.SUCCESS;
			}

			if (state.is(Level4Blocks.OFFICE_VENDING)) {
				ItemStack held = sp.getMainHandItem();
				if (held.is(Economy.LOST_COIN) || held.is(Level1Items.ALLOY)) {
					if (!sp.getAbilities().instabuild) held.shrink(1);
					giveItem(sp, new ItemStack(Level1Items.ALMOND_WATER));
					if (world.getRandom().nextInt(100) < 25) giveItem(sp, new ItemStack(Level1Items.BANDAGE));
					world.playSound(null, pos, Level4Sounds.VENDING, SoundSource.BLOCKS, 0.85F, 1.0F);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.vending_bought"));
				} else {
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.vending_hint"));
				}
				return InteractionResult.SUCCESS;
			}

			if (state.is(Level4Blocks.TBD_CURIO_SHELF)) {
				if (SHELF_TAKEN.getOrDefault(pos, false)) {
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.shelf_empty"));
					return InteractionResult.SUCCESS;
				}
				SHELF_TAKEN.put(pos.immutable(), true);
				RandomSource rng = world.getRandom();
				ItemStack drop = new ItemStack(Level4Items.CURIOS.get(rng.nextInt(Level4Items.CURIOS.size())));
				giveItem(sp, drop);
				world.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.7F, 1.1F);
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.shelf_found"));
				return InteractionResult.SUCCESS;
			}

			if (state.is(Level4Blocks.TBD_HOARD_CRATE)) {
				world.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.7F, 1.0F);
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.tbd_hoard_refuse"));
				return InteractionResult.SUCCESS;
			}

			return InteractionResult.PASS;
		});

		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			if (!(entity instanceof Person person) || world.dimension() != Level4.LEVEL_4) return InteractionResult.PASS;
			int now = sp.level().getServer().getTickCount();

			if (person.entityTags().contains(MEDIC_TAG)) {
				if (now < MEDIC_NEXT.getOrDefault(sp.getUUID(), 0)) {
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.medic_wait"));
					return InteractionResult.SUCCESS;
				}
				MEDIC_NEXT.put(sp.getUUID(), now + MEDIC_COOLDOWN);
				sp.setHealth(sp.getMaxHealth());
				sp.getFoodData().eat(8, 0.8F);
				sp.removeAllEffects();
				PlayerRpg.restoreSanity(sp, 25.0F);
				world.playSound(null, sp.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.1F);
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.medic_healed"));
				return InteractionResult.SUCCESS;
			}

			if (person.entityTags().contains(AMOR_TAG)) {
				ServerLevel sl = (ServerLevel) world;
				boolean desecrated = Level4PoiEvents.isAmorDesecrated(sl, sp.getUUID());
				ItemStack mainHand = sp.getMainHandItem();
				if (desecrated) {
					if (Level4Items.isReligious(mainHand)) {
						if (!sp.getAbilities().instabuild) mainHand.shrink(1);
						Level4PoiEvents.clearAmorDesecrated(sl, sp.getUUID());
						world.playSound(null, person.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.0F);
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.amor_forgiven"));
					} else {
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.amor_desecrated"));
					}
					return InteractionResult.SUCCESS;
				}
				if (!Level4Items.isReligiousRelic(sp)) {
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.amor_hostile"));
					return InteractionResult.SUCCESS;
				}
				ItemStack offHand = sp.getOffhandItem();
				ItemStack otherHand = Level4Items.isReligious(mainHand) ? offHand : mainHand;
				if (otherHand.is(Economy.LOST_COIN) || otherHand.is(Level1Items.ALLOY)) {
					if (!sp.getAbilities().instabuild) otherHand.shrink(1);
					RandomSource rng = world.getRandom();
					ItemStack reward = rng.nextBoolean()
							? new ItemStack(BackroomsItems.PURIFIED_ALMOND_ESSENCE)
							: new ItemStack(Level1Items.ALMOND_WATER, 2);
					giveItem(sp, reward);
					if (rng.nextInt(100) < 40) giveItem(sp, new ItemStack(BackroomsItems.TOXIC_SPORE_SAC));
					world.playSound(null, person.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.7F, 1.0F);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.amor_traded"));
				} else if (Level4Items.isReligious(mainHand)) {
					if (!sp.getAbilities().instabuild) mainHand.shrink(1);
					giveItem(sp, new ItemStack(BackroomsItems.PURIFIED_ALMOND_ESSENCE, 2));
					giveItem(sp, new ItemStack(Level1Items.ALMOND_WATER, 2));
					giveItem(sp, new ItemStack(BackroomsItems.TOXIC_SPORE_SAC, 2));
					world.playSound(null, person.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.0F);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.amor_offered"));
				} else {
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.amor_hint"));
				}
				return InteractionResult.SUCCESS;
			}

			if (person.entityTags().contains(TBD_TAG)) {
				ItemStack held = sp.getMainHandItem();
				boolean usedCoin = held.is(Economy.LOST_COIN);
				if (usedCoin || Wallet.of(sp) >= 10) {
					if (usedCoin) {
						if (!sp.getAbilities().instabuild) held.shrink(1);
					} else {
						Wallet.take(sp, 10);
					}
					RandomSource rng = world.getRandom();
					Item reward = Level4Items.CURIOS.get(rng.nextInt(Level4Items.CURIOS.size()));
					giveItem(sp, new ItemStack(reward));
					world.playSound(null, person.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.7F, 1.1F);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.tbd_sold"));
				} else {
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.tbd_hint"));
				}
				return InteractionResult.SUCCESS;
			}

			return InteractionResult.PASS;
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level4.LEVEL_4);
		if (level == null) return;
		int now = server.getTickCount();
		long seed = Level4ChunkGenerator.planSeed(level.getChunkSource().randomState());

		if (now % POPULATE_INTERVAL == 0) {
			for (ServerPlayer sp : level.players()) {
				if (!sp.isSpectator()) populateNear(level, seed, sp);
			}
		}

		if (now % 5 == 0) {
			tickAmorBehavior(level, now);
			tickOmegaGuards(level, now);
		}

		if (!level.players().isEmpty()) {
			tickBaseStroll(level, seed, now);
		}
	}

	private static void tickOmegaGuards(ServerLevel level, int now) {
		Set<UUID> seen = new HashSet<>();
		for (ServerPlayer sp : level.players()) {
			var guards = level.getEntitiesOfClass(Person.class, sp.getBoundingBox().inflate(48),
					p -> p.isAlive() && p.entityTags().contains(OMEGA_GUARD_TAG));
			for (Person guard : guards) {
				if (!seen.add(guard.getUUID())) continue;
				var monsters = level.getEntitiesOfClass(Monster.class, guard.getBoundingBox().inflate(16.0), Entity::isAlive);
				if (monsters.isEmpty()) continue;
				Monster target = monsters.stream()
						.min(Comparator.comparingDouble(m -> m.distanceToSqr(guard)))
						.orElse(null);
				if (target == null) continue;
				moveToward(guard, target.position(), 0.22);
				if (guard.distanceTo(target) <= 3.0 && now >= GUARD_ATTACK_NEXT.getOrDefault(guard.getUUID(), 0)) {
					GUARD_ATTACK_NEXT.put(guard.getUUID(), now + GUARD_ATTACK_COOLDOWN);
					target.hurtServer(level, level.damageSources().mobAttack(guard), 6.0F);
					Vec3 push = target.position().subtract(guard.position()).multiply(1, 0, 1);
					if (push.lengthSqr() > 1e-4) {
						push = push.normalize().scale(0.45);
						target.push(push.x, 0.12, push.z);
						target.hurtMarked = true;
					}
					level.playSound(null, guard.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 0.8F, 1.0F);
				}
			}
		}
	}

	private static void tickAmorBehavior(ServerLevel level, int now) {
		for (ServerPlayer sp : level.players()) {
			if (sp.isCreative() || sp.isSpectator()) continue;
			var cultists = level.getEntitiesOfClass(Person.class, sp.getBoundingBox().inflate(9.0),
					p -> p.isAlive() && p.entityTags().contains(AMOR_TAG));
			if (cultists.isEmpty()) continue;

			boolean desecrated = Level4PoiEvents.isAmorDesecrated(level, sp.getUUID());
			if (!desecrated && Level4Items.isReligiousRelic(sp)) {
				for (Person cultist : cultists) face(cultist, sp.position());
				if (now >= AMOR_PACIFY_NEXT.getOrDefault(sp.getUUID(), 0)) {
					AMOR_PACIFY_NEXT.put(sp.getUUID(), now + 240);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.amor_pacified"));
				}
				continue;
			}

			if (now >= AMOR_WARN_NEXT.getOrDefault(sp.getUUID(), 0)) {
				AMOR_WARN_NEXT.put(sp.getUUID(), now + 140);
				sp.sendOverlayMessage(Component.translatable(desecrated
						? "message.noclip_backrooms.level4.poi.amor_desecrated"
						: "message.noclip_backrooms.level4.amor_hostile"));
			}
			for (Person cultist : cultists) {
				moveToward(cultist, sp.position(), 0.12);
				if (cultist.distanceTo(sp) <= 2.4 && now >= AMOR_ATTACK_NEXT.getOrDefault(sp.getUUID(), 0)) {
					AMOR_ATTACK_NEXT.put(sp.getUUID(), now + 40);
					sp.hurtServer(level, level.damageSources().mobAttack(cultist), 3.5F);
					level.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 0.7F, 0.9F);
				}
			}
		}
	}

	private static void tickBaseStroll(ServerLevel level, long seed, int now) {
		Set<UUID> seen = new HashSet<>();
		for (ServerPlayer sp : level.players()) {
			for (Person person : level.getEntitiesOfClass(Person.class, sp.getBoundingBox().inflate(40),
					p -> p.isAlive() && (p.entityTags().contains(OMEGA_SQUAD_TAG)
							|| p.entityTags().contains(TBD_TAG)
							|| p.entityTags().contains(AMOR_TAG)))) {
				if (!seen.add(person.getUUID())) continue;
				if (person.entityTags().contains(AMOR_TAG) && nearbyOutsider(level, person)) continue;
				strollInsideZone(level, seed, person, now);
			}
		}
		if (now % 200 == 0) STROLL_STATES.keySet().removeIf(id -> !seen.contains(id));
	}

	private static boolean nearbyOutsider(ServerLevel level, Person cultist) {
		return level.players().stream().anyMatch(p -> !p.isCreative() && !p.isSpectator()
				&& p.distanceTo(cultist) <= 9.0
				&& (Level4PoiEvents.isAmorDesecrated(level, p.getUUID()) || !Level4Items.isReligiousRelic(p)));
	}

	private static void strollInsideZone(ServerLevel level, long seed, Person person, int now) {
		StrollState state = STROLL_STATES.computeIfAbsent(person.getUUID(), id -> new StrollState());
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceTo(person) < 3.5) {
				face(person, player.position());
				return;
			}
		}
		if (now < state.waitUntil) return;
		RandomSource rng = level.getRandom();
		ZoneKind ownZone = Level4Layout.tile(seed, person.getBlockX(), person.getBlockZ()).zone();
		if (state.goal == null || now > state.giveUp) {
			state.goal = pickZoneGoal(level, seed, person, ownZone, rng);
			state.giveUp = now + 240;
			if (state.goal == null) state.waitUntil = now + 80;
			return;
		}
		double dist = moveToward(person, state.goal, 0.032);
		if (dist < 0.6) {
			state.goal = null;
			state.waitUntil = now + 70 + rng.nextInt(130);
		}
	}

	private static Vec3 pickZoneGoal(ServerLevel level, long seed, Person person, ZoneKind ownZone, RandomSource rng) {
		for (int attempt = 0; attempt < 16; attempt++) {
			int x = person.getBlockX() + rng.nextInt(9) - 4;
			int z = person.getBlockZ() + rng.nextInt(9) - 4;
			var tile = Level4Layout.tile(seed, x, z);
			if (tile.kind() != Level4Layout.Kind.AIR || tile.zone() != ownZone) continue;
			BlockPos at = new BlockPos(x, Level4ChunkGenerator.FLOOR_Y + 1, z);
			if (freeSpot(level, at)) return new Vec3(x + 0.5, at.getY(), z + 0.5);
		}
		return null;
	}

	private static double moveToward(Mob mob, Vec3 target, double speed) {
		face(mob, target);
		Vec3 delta = target.subtract(mob.position()).multiply(1, 0, 1);
		double len = delta.length();
		if (len > 0.45) {
			Vec3 step = delta.scale(Math.min(speed, len) / len);
			mob.move(MoverType.SELF, new Vec3(step.x, -0.1, step.z));
		}
		return len;
	}

	private static void face(Mob mob, Vec3 target) {
		float yaw = (float) Math.toDegrees(Math.atan2(-(target.x - mob.getX()), target.z - mob.getZ()));
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
	}

	static int populateNear(ServerLevel level, long seed, ServerPlayer player) {
		int rx0 = Math.floorDiv(player.getBlockX(), Level4Plan.SIZE);
		int rz0 = Math.floorDiv(player.getBlockZ(), Level4Plan.SIZE);
		int spawned = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int rx = rx0 + dx, rz = rz0 + dz;
				Level4Plan plan = Level4Plan.of(seed, rx, rz);
				for (RoomBox room : plan.rooms) {
					if (room.kind() != ZoneKind.OMEGA_BASE && room.kind() != ZoneKind.AMOR_CAMP && room.kind() != ZoneKind.TBD_ROOM) {
						continue;
					}
					int x0 = rx * Level4Plan.SIZE + room.i() * Level4Plan.CELL;
					int z0 = rz * Level4Plan.SIZE + room.j() * Level4Plan.CELL;
					int x1 = x0 + room.w() * Level4Plan.CELL - 1;
					int z1 = z0 + room.h() * Level4Plan.CELL - 1;
					int cx = rx * Level4Plan.SIZE + room.cx() * Level4Plan.CELL + 3;
					int cz = rz * Level4Plan.SIZE + room.cz() * Level4Plan.CELL + 3;
					if (player.distanceToSqr(cx + 0.5, Level4ChunkGenerator.FLOOR_Y + 1, cz + 0.5) > 48 * 48) continue;
					if (!level.isLoaded(new BlockPos(x0, Level4ChunkGenerator.FLOOR_Y, z0))
							|| !level.isLoaded(new BlockPos(x1, Level4ChunkGenerator.FLOOR_Y, z0))
							|| !level.isLoaded(new BlockPos(x0, Level4ChunkGenerator.FLOOR_Y, z1))
							|| !level.isLoaded(new BlockPos(x1, Level4ChunkGenerator.FLOOR_Y, z1))) {
						continue;
					}
					AABB box = new AABB(x0, Level4ChunkGenerator.FLOOR_Y, z0, x1 + 1, Level4ChunkGenerator.FLOOR_Y + 5, z1 + 1);
					if (!level.getEntitiesOfClass(Person.class, box).isEmpty()) continue;

					switch (room.kind()) {
						case OMEGA_BASE -> {
							// 4 active checkpoint guards at N/S/W/E gates
							spawned += spawnFactionPerson(level, seed, room.kind(), x0, z0, x1, z1, cx, z0 + 3, "meg_patrol", "M.E.G. Omega Guard", STAFF_TAG, OMEGA_GUARD_TAG, true);
							spawned += spawnFactionPerson(level, seed, room.kind(), x0, z0, x1, z1, cx, z1 - 3, "meg_patrol", "M.E.G. Omega Guard", STAFF_TAG, OMEGA_GUARD_TAG, true);
							spawned += spawnFactionPerson(level, seed, room.kind(), x0, z0, x1, z1, x0 + 3, cz, "meg_patrol", "M.E.G. Omega Guard", STAFF_TAG, OMEGA_GUARD_TAG, true);
							spawned += spawnFactionPerson(level, seed, room.kind(), x0, z0, x1, z1, x1 - 3, cz, "meg_patrol", "M.E.G. Omega Guard", STAFF_TAG, OMEGA_GUARD_TAG, true);
							// 2 medics in south medical bay
							spawned += spawnFactionPerson(level, seed, room.kind(), x0, z0, x1, z1, cx - 7, z1 - 4, "medic", "M.E.G. Omega Medic", STAFF_TAG, MEDIC_TAG, true);
							spawned += spawnFactionPerson(level, seed, room.kind(), x0, z0, x1, z1, cx + 7, z1 - 4, "medic", "M.E.G. Omega Medic", STAFF_TAG, MEDIC_TAG, true);
							// Quartermaster & Supplier trader in central command
							spawned += spawnFactionPerson(level, seed, room.kind(), x0, z0, x1, z1, cx - 2, cz, "meg_patrol", "M.E.G. Omega Quartermaster", STAFF_TAG, ShopRole.QUARTERMASTER.tag(), true);
							spawned += spawnFactionPerson(level, seed, room.kind(), x0, z0, x1, z1, cx + 2, cz, "trader", "M.E.G. Omega Trader", STAFF_TAG, ShopRole.SUPPLIER.tag(), true);
							// 2 strolling squad operatives
							spawned += spawnFactionPerson(level, seed, room.kind(), x0, z0, x1, z1, x0 + 4, cz - 5, "colonist", "M.E.G. Squad Operative", STAFF_TAG, OMEGA_SQUAD_TAG, true);
							spawned += spawnFactionPerson(level, seed, room.kind(), x0, z0, x1, z1, x0 + 4, cz + 5, "speednoclipper", "M.E.G. Squad Operative", STAFF_TAG, OMEGA_SQUAD_TAG, true);
						}
						case AMOR_CAMP -> {
							// Wiki: exactly 10 members, fightable
							spawned += spawnFactionPerson(level, seed, room.kind(), x0, z0, x1, z1, cx - 1, cz, "colonist", "Amor Incrementum Elder", AMOR_TAG, null, false);
							int[][] offsets = {
									{2, 0}, {0, 2}, {0, -2}, {-5, -5}, {5, -5}, {-5, 5}, {5, 5}, {-6, 1}, {6, -1}
							};
							for (int[] off : offsets) {
								spawned += spawnFactionPerson(level, seed, room.kind(), x0, z0, x1, z1,
										cx + off[0], cz + off[1], "colonist", "Amor Incrementum Cultivator", AMOR_TAG, null, false);
							}
						}
						case TBD_ROOM -> {
							// Wiki: exactly 6 members
							spawned += spawnFactionPerson(level, seed, room.kind(), x0, z0, x1, z1, cx, cz - 2, "trader", "T.B.D. Collector", TBD_TAG, null, true);
							int[][] offsets = {{-2, 0}, {2, 0}, {0, 3}, {-3, -4}, {3, 4}};
							String[] looks = {"colonist", "aiko", "tom", "colonist", "aiko"};
							for (int k = 0; k < offsets.length; k++) {
								spawned += spawnFactionPerson(level, seed, room.kind(), x0, z0, x1, z1,
										cx + offsets[k][0], cz + offsets[k][1], looks[k], "T.B.D. Member", TBD_TAG, null, true);
							}
						}
						default -> {
						}
					}
				}
			}
		}
		return spawned;
	}

	private static int spawnFactionPerson(ServerLevel level, long seed, ZoneKind zone,
			int minX, int minZ, int maxX, int maxZ, int prefX, int prefZ,
			String look, String name, String factionTag, String extraTag, boolean invulnerable) {
		BlockPos spot = findFreeSpotInRoom(level, seed, zone, minX, minZ, maxX, maxZ, prefX, prefZ);
		if (spot == null) return 0;
		Person person = People.type(look).create(level, EntitySpawnReason.NATURAL);
		if (person == null) return 0;
		person.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
		person.setPersistenceRequired();
		person.setNoAi(true);
		person.setInvulnerable(invulnerable);
		person.setCustomName(Component.literal(name));
		person.setCustomNameVisible(true);
		person.addTag(factionTag);
		if (extraTag != null) person.addTag(extraTag);
		level.addFreshEntity(person);
		return 1;
	}

	private static BlockPos findFreeSpotInRoom(ServerLevel level, long seed, ZoneKind zone,
			int minX, int minZ, int maxX, int maxZ, int prefX, int prefZ) {
		for (int r = 0; r <= 6; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
					int x = prefX + dx, z = prefZ + dz;
					if (x < minX + 1 || x > maxX - 1 || z < minZ + 1 || z > maxZ - 1) continue;
					var tile = Level4Layout.tile(seed, x, z);
					if (tile.kind() != Level4Layout.Kind.AIR || tile.zone() != zone) continue;
					BlockPos spot = new BlockPos(x, Level4ChunkGenerator.FLOOR_Y + 1, z);
					if (freeSpot(level, spot)) return spot;
				}
			}
		}
		return null;
	}

	private static boolean freeSpot(ServerLevel level, BlockPos spot) {
		return level.isLoaded(spot)
				&& level.getBlockState(spot).isAir()
				&& level.getBlockState(spot.above()).isAir()
				&& !level.getBlockState(spot.below()).getCollisionShape(level, spot.below()).isEmpty();
	}

	private static void giveLockerLoot(ServerPlayer sp, RandomSource rng) {
		giveItem(sp, new ItemStack(Level1Items.ALMOND_WATER, 1 + rng.nextInt(2)));
		int r = rng.nextInt(100);
		if (r < 32) giveItem(sp, new ItemStack(Level1Items.BANDAGE, 1 + rng.nextInt(2)));
		else if (r < 56) giveItem(sp, new ItemStack(Economy.LOST_COIN, 1 + rng.nextInt(2)));
		else if (r < 76) giveItem(sp, new ItemStack(Level1Items.PHOSPHOR_POWDER, 2));
		else if (r < 88) giveItem(sp, new ItemStack(Items.GLASS_BOTTLE, 2));
		else giveItem(sp, new ItemStack(rng.nextBoolean() ? Level4Items.BRASS_CRUCIFIX : Level4Items.FADED_ICON_FRAME));
		sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.locker_opened"));
	}

	private static void giveItem(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) player.drop(stack, false);
	}
}
