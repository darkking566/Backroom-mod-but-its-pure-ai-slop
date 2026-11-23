package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Layout.Sector;
import dev.noclip.backrooms.level1.Level1Layout.Tile;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Tailchase Sector is forever under construction: figures like shadows in work clothes wander about patching
 * the building, answering nothing and noticing no one. Get too close to one and its tools will hurt you.
 */
final class Workers {
	static final String TAG = "noclip_backrooms_worker";
	private static final int MAX_NEAR = 4, NEAR = 80, FAR = 110;
	private static final Map<UUID, Vec3> GOAL = new HashMap<>();
	private static int heldOffUntil;

	private Workers() {
	}

	static void holdOff(MinecraftServer server, int ticks) {
		heldOffUntil = server.getTickCount() + ticks;
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Workers::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG)) entity.discard();
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		if (level == null) return;
		int now = server.getTickCount();
		long seed = 0;
		boolean seeded = false;
		RandomSource random = level.getRandom();

		if (now % 40 == 0 && now >= heldOffUntil) {
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator()) continue;
				if (!seeded) {
					seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
					seeded = true;
				}
				if (Level1Layout.sector(seed, player.getBlockX(), player.getBlockZ()) != Sector.TAILCHASE) continue;
				int near = level.getEntitiesOfClass(Mob.class, new AABB(player.blockPosition()).inflate(NEAR), m -> m.entityTags().contains(TAG)).size();
				if (near < MAX_NEAR) spawnNear(level, player, seed, random);
			}
		}

		for (Mob worker : level.getEntitiesOfClass(Mob.class, new AABB(-30000000, 0, -30000000, 30000000, 64, 30000000), m -> m.entityTags().contains(TAG))) {
			ServerPlayer closest = null;
			for (ServerPlayer player : level.players()) {
				if (closest == null || player.distanceToSqr(worker) < closest.distanceToSqr(worker)) closest = player;
			}
			if (closest == null || closest.distanceTo(worker) > FAR) {
				worker.discard();
				GOAL.remove(worker.getUUID());
				continue;
			}
			wander(worker, now, random);
			if (now % 20 == 0 && !closest.isSpectator() && closest.distanceTo(worker) < 2.2 && random.nextInt(100) < 30) {
				closest.hurtServer(level, level.damageSources().generic(), 3.0F);
				closest.sendOverlayMessage(Component.translatable("message.noclip_backrooms.worker_hit"));
			}
		}
	}

	private static void wander(Mob worker, int now, RandomSource random) {
		Vec3 goal = GOAL.get(worker.getUUID());
		if (goal == null || now % 160 == 0) {
			goal = worker.position().add(random.nextInt(13) - 6, 0, random.nextInt(13) - 6);
			GOAL.put(worker.getUUID(), goal);
		}
		Walkers.step(worker, goal, 0.04);
		// Patching something: an arm swung now and then.
		if (now % 30 == 0) worker.swing(InteractionHand.MAIN_HAND);
	}

	static boolean spawnNear(ServerLevel level, ServerPlayer player, long seed, RandomSource random) {
		for (int attempt = 0; attempt < 60; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2, distance = 12 + random.nextDouble() * 20;
			int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance), z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
			if (Level1Layout.tile(seed, x, z) != Tile.FLOOR) continue;
			BlockPos at = new BlockPos(x, Level1ChunkGenerator.FLOOR_Y + 1, z);
			if (!level.isLoaded(at) || !level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()) continue;
			// A zombie body (it shows armour, which a villager's does not), with no mind: a shadow in dark work clothes.
			Mob worker = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
			if (worker == null) return false;
			worker.snapTo(x + 0.5, at.getY(), z + 0.5, random.nextFloat() * 360, 0);
			worker.setNoAi(true);
			worker.setInvulnerable(true);
			worker.setSilent(true);
			worker.setPersistenceRequired();
			// A shadow in work clothes: dark armour over the whole figure.
			worker.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
			worker.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
			worker.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
			worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
			worker.setCustomName(Component.translatable("resident.noclip_backrooms.worker"));
			level.addFreshEntity(worker);
			worker.addTag(TAG);
			return true;
		}
		return false;
	}
}
