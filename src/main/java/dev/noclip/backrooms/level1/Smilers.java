package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.smiler.Smiler;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * Spawns 3D animated Smilers in Level 1 during a Flickering (blackout), and vanishes blackout-spawned ones
 * when the lights return. Manually placed Smilers (via spawn egg) are left untouched.
 */
final class Smilers {
	static final String TAG = "noclip_backrooms_smiler";
	private static final int NEAREST = 10, FARTHEST = 20;

	private static final List<UUID> ACTIVE = new ArrayList<>();
	private static boolean wasDark;

	private Smilers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Smilers::tick);
		// One saved mid-blackout is gone when its chunk loads again.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG)) entity.discard();
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ACTIVE.clear();
			wasDark = false;
		});
	}

	static int count() {
		return ACTIVE.size();
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		if (level == null || level.getDifficulty() == Difficulty.PEACEFUL) return;
		boolean dark = Blackouts.phase() == Blackouts.Phase.DARK;
		if (!dark) {
			if (!ACTIVE.isEmpty()) vanishAll(level);
			wasDark = false;
			return;
		}
		if (!wasDark) {
			wasDark = true;
			for (ServerPlayer player : level.players()) {
				if (player.isCreative() || player.isSpectator()) continue;
				int count = 1 + level.getRandom().nextInt(2);
				for (int i = 0; i < count; i++) spawnNear(player);
			}
		}
		if (server.getTickCount() % 20 == 0) prune(level);
	}

	/** Spawns a 3D Smiler in the dark somewhere around the player; returns it, or null if nowhere would do. */
	static Smiler spawnNear(ServerPlayer player) {
		ServerLevel level = player.level();
		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		int baseY = player.getBlockY();
		for (int attempt = 0; attempt < 30; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = NEAREST + random.nextDouble() * (FARTHEST - NEAREST);
			double x = player.getX() + Math.cos(angle) * distance;
			double z = player.getZ() + Math.sin(angle) * distance;
			int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
			if (!Level1Layout.tile(seed, bx, bz).walkable() || Marauders.nearSettlement(seed, bx, bz)) continue;
			BlockPos feet = Spots.feet(level, Monsters.SMILER.getDimensions(), bx, baseY, bz);
			if (feet == null) continue;
			Smiler smiler = Monsters.SMILER.create(level, EntitySpawnReason.NATURAL);
			if (smiler == null) return null;
			smiler.setPos(bx + 0.5, feet.getY(), bz + 0.5);
			smiler.setTarget(player);
			level.addFreshEntity(smiler);
			// Tagged only once it is in the world, or the clean-up of smilers saved mid-blackout would take it at once.
			smiler.addTag(TAG);
			ACTIVE.add(smiler.getUUID());
			level.playSound(null, smiler.getX(), smiler.getEyeY(), smiler.getZ(), Level1Sounds.SMILER, SoundSource.HOSTILE, 0.7F, 0.65F);
			return smiler;
		}
		return null;
	}

	private static void prune(ServerLevel level) {
		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		for (Iterator<UUID> it = ACTIVE.iterator(); it.hasNext(); ) {
			Entity entity = level.getEntity(it.next());
			if (entity == null || !entity.isAlive()) {
				it.remove();
			} else if (Marauders.nearSettlement(seed, entity.getBlockX(), entity.getBlockZ())) {
				vanish(level, entity);
				it.remove();
			}
		}
	}

	/** Light in hand, sprinting, or off the ground: all draw a smiler in. */
	static boolean drawn(ServerPlayer player) {
		return player.isSprinting() || !player.onGround() || lightSource(player.getMainHandItem()) || lightSource(player.getOffhandItem());
	}

	private static boolean lightSource(ItemStack stack) {
		if (stack.getItem() instanceof BlockItem block && block.getBlock().defaultBlockState().getLightEmission() > 0) return true;
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals("flashlight");
	}

	private static void vanish(ServerLevel level, Entity entity) {
		level.sendParticles(ParticleTypes.SMOKE, entity.getX(), entity.getY() + 1.0, entity.getZ(), 14, 0.3, 0.6, 0.3, 0.02);
		entity.discard();
	}

	private static void vanishAll(ServerLevel level) {
		for (UUID id : ACTIVE) {
			Entity entity = level.getEntity(id);
			if (entity != null) vanish(level, entity);
		}
		ACTIVE.clear();
	}
}
