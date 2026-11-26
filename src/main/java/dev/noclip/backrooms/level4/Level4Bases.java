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
import java.util.HashMap;
import java.util.Map;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
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
 *   <li><b>M.E.G. Base Omega</b>: safe garrison with cots (settle/respawn), supply lockers, medic heal, and shops.</li>
 *   <li><b>Amor Incrementum</b>: small agricultural cult growing wall-fungi; hostile to outsiders unless holding
 *       a religious relic ({@link Level4Items#BRASS_CRUCIFIX} or {@link Level4Items#FADED_ICON_FRAME}).</li>
 *   <li><b>T.B.D.</b>: 6 reclusive hoarders who ignore you unless spoken to, selling useless curios.</li>
 * </ul>
 */
final class Level4Bases {
	static final String STAFF_TAG = "noclip_backrooms_level4_omega";
	static final String MEDIC_TAG = "noclip_backrooms_level4_omega_medic";
	static final String AMOR_TAG = "noclip_backrooms_level4_amor";
	static final String TBD_TAG = "noclip_backrooms_level4_tbd";
	private static final int POPULATE_INTERVAL = 20 * 8;
	private static final int LOCKER_COOLDOWN = 20 * 60 * 5;
	private static final int MEDIC_COOLDOWN = 20 * 60;
	private static final Map<BlockPos, Integer> LOCKER_NEXT = new HashMap<>();
	private static final Map<BlockPos, Boolean> SHELF_TAKEN = new HashMap<>();
	private static final Map<UUID, Integer> MEDIC_NEXT = new HashMap<>();
	private static final Map<UUID, Integer> AMOR_ATTACK_NEXT = new HashMap<>();
	private static final Map<UUID, Integer> AMOR_WARN_NEXT = new HashMap<>();
	private static final Map<UUID, Integer> AMOR_PACIFY_NEXT = new HashMap<>();

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
				ItemStack drop = rng.nextInt(100) < 20
						? new ItemStack(Level4Items.BRASS_CRUCIFIX)
						: new ItemStack(Level4Items.CURIOS.get(rng.nextInt(Level4Items.CURIOS.size())));
				giveItem(sp, drop);
				world.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.7F, 1.1F);
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.shelf_found"));
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
				if (!Level4Items.isReligiousRelic(sp)) {
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.amor_hostile"));
					return InteractionResult.SUCCESS;
				}
				ItemStack otherHand = Level4Items.isReligious(sp.getMainHandItem()) ? sp.getOffhandItem() : sp.getMainHandItem();
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
					Item reward = rng.nextInt(100) < 18
							? Level4Items.FADED_ICON_FRAME
							: Level4Items.CURIOS.get(rng.nextInt(Level4Items.CURIOS.size()));
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
		}
	}

	private static void tickAmorBehavior(ServerLevel level, int now) {
		for (ServerPlayer sp : level.players()) {
			if (sp.isCreative() || sp.isSpectator()) continue;
			var cultists = level.getEntitiesOfClass(Person.class, sp.getBoundingBox().inflate(9.0),
					p -> p.entityTags().contains(AMOR_TAG));
			if (cultists.isEmpty()) continue;

			if (Level4Items.isReligiousRelic(sp)) {
				for (Person cultist : cultists) face(cultist, sp.position());
				if (now >= AMOR_PACIFY_NEXT.getOrDefault(sp.getUUID(), 0)) {
					AMOR_PACIFY_NEXT.put(sp.getUUID(), now + 240);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.amor_pacified"));
				}
				continue;
			}

			if (now >= AMOR_WARN_NEXT.getOrDefault(sp.getUUID(), 0)) {
				AMOR_WARN_NEXT.put(sp.getUUID(), now + 140);
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.amor_hostile"));
			}
			for (Person cultist : cultists) {
				stepToward(cultist, sp.position(), 0.08);
				if (cultist.distanceTo(sp) <= 2.4 && now >= AMOR_ATTACK_NEXT.getOrDefault(sp.getUUID(), 0)) {
					AMOR_ATTACK_NEXT.put(sp.getUUID(), now + 40);
					sp.hurtServer(level, level.damageSources().mobAttack(cultist), 3.5F);
					level.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 0.7F, 0.9F);
				}
			}
		}
	}

	private static void stepToward(Mob mob, Vec3 target, double speed) {
		face(mob, target);
		Vec3 delta = target.subtract(mob.position()).multiply(1, 0, 1);
		double len = delta.length();
		if (len > 1.3) {
			Vec3 step = delta.scale(speed / len);
			mob.setDeltaMovement(step.x, mob.getDeltaMovement().y, step.z);
			mob.hurtMarked = true;
		}
	}

	private static void face(Mob mob, Vec3 target) {
		double dx = target.x - mob.getX(), dz = target.z - mob.getZ();
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
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
					int cx = rx * Level4Plan.SIZE + room.cx() * Level4Plan.CELL + 3;
					int cz = rz * Level4Plan.SIZE + room.cz() * Level4Plan.CELL + 3;
					if (player.distanceToSqr(cx + 0.5, Level4ChunkGenerator.FLOOR_Y + 1, cz + 0.5) > 56 * 56) continue;
					AABB box = new AABB(x0, Level4ChunkGenerator.FLOOR_Y, z0,
							x0 + room.w() * Level4Plan.CELL, Level4ChunkGenerator.FLOOR_Y + 5, z0 + room.h() * Level4Plan.CELL);
					if (!level.getEntitiesOfClass(Person.class, box).isEmpty()) continue;

					switch (room.kind()) {
						case OMEGA_BASE -> {
							spawned += spawnPerson(level, cx - 2, cz, "meg_patrol", "M.E.G. Omega Guard", STAFF_TAG, null);
							spawned += spawnPerson(level, cx + 2, cz, "medic", "M.E.G. Omega Medic", STAFF_TAG, MEDIC_TAG);
							spawned += spawnPerson(level, cx, cz + 2, "meg_patrol", "M.E.G. Omega Quartermaster", STAFF_TAG, ShopRole.QUARTERMASTER.tag());
							spawned += spawnPerson(level, cx, cz - 2, "trader", "M.E.G. Omega Trader", STAFF_TAG, ShopRole.SUPPLIER.tag());
						}
						case AMOR_CAMP -> {
							spawned += spawnPerson(level, cx - 1, cz, "colonist", "Amor Incrementum Cultivator", AMOR_TAG, null);
							spawned += spawnPerson(level, cx + 1, cz + 1, "colonist", "Amor Incrementum Cultivator", AMOR_TAG, null);
							spawned += spawnPerson(level, cx, cz - 1, "colonist", "Amor Incrementum Cultivator", AMOR_TAG, null);
						}
						case TBD_ROOM -> {
							spawned += spawnPerson(level, cx - 1, cz, "trader", "T.B.D. Collector", TBD_TAG, null);
							spawned += spawnPerson(level, cx + 1, cz, "colonist", "T.B.D. Member", TBD_TAG, null);
							spawned += spawnPerson(level, cx, cz + 1, "colonist", "T.B.D. Member", TBD_TAG, null);
						}
						default -> {
						}
					}
				}
			}
		}
		return spawned;
	}

	private static int spawnPerson(ServerLevel level, int x, int z, String look, String name, String factionTag, String extraTag) {
		BlockPos spot = new BlockPos(x, Level4ChunkGenerator.FLOOR_Y + 1, z);
		if (!level.isLoaded(spot) || !level.getBlockState(spot).isAir() || !level.getBlockState(spot.above()).isAir()) return 0;
		Person person = People.type(look).create(level, EntitySpawnReason.NATURAL);
		if (person == null) return 0;
		person.snapTo(x + 0.5, spot.getY(), z + 0.5, level.getRandom().nextFloat() * 360, 0);
		person.setPersistenceRequired();
		person.setNoAi(true);
		person.setInvulnerable(true);
		person.setCustomName(Component.literal(name));
		person.setCustomNameVisible(true);
		person.addTag(factionTag);
		if (extraTag != null) person.addTag(extraTag);
		level.addFreshEntity(person);
		return 1;
	}

	private static void giveLockerLoot(ServerPlayer sp, RandomSource rng) {
		giveItem(sp, new ItemStack(Level1Items.ALMOND_WATER, 1 + rng.nextInt(2)));
		int r = rng.nextInt(100);
		if (r < 35) giveItem(sp, new ItemStack(Level1Items.BANDAGE, 1 + rng.nextInt(2)));
		else if (r < 60) giveItem(sp, new ItemStack(Economy.LOST_COIN, 1 + rng.nextInt(2)));
		else if (r < 80) giveItem(sp, new ItemStack(Level1Items.PHOSPHOR_POWDER, 2));
		else if (r < 92) giveItem(sp, new ItemStack(Items.GLASS_BOTTLE, 2));
		else giveItem(sp, new ItemStack(Level4Items.BRASS_CRUCIFIX));
		sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.locker_opened"));
	}

	private static void giveItem(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) player.drop(stack, false);
	}
}
