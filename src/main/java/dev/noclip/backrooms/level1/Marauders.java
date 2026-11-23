package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Layout.Tile;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Marauders: people who survive in Level 1 by taking from others. They turn up in small bands out in the halls,
 * out of sight and well away, a while after you arrive; a crossbow and an axe or two. They carry alloy.
 */
final class Marauders {
	static final String TAG = "noclip_backrooms_marauder";
	private static final int GRACE = 2 * 60 * 20, DELAY = 45 * 20, DELAY_SPREAD = 60 * 20;
	private static final int CHANCE = 35, MAX_NEAR = 4, NEAR = 64, NEAREST = 24, FARTHEST = 40;
	/** Marauders keep away from the settlements' barriers. */
	private static final int SETTLEMENT_MARGIN = 12;

	private static final Map<UUID, Integer> NEXT = new HashMap<>();
	private static int heldOffUntil;

	private Marauders() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Marauders::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			NEXT.clear();
			heldOffUntil = 0;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!entity.entityTags().contains(TAG) || !(entity.level() instanceof ServerLevel level)) return;
			entity.spawnAtLocation(level, new ItemStack(Level1Items.ALLOY, 1 + level.getRandom().nextInt(2)));
		});
	}

	/** No bands turn up for a while, for tests. */
	static void holdOff(MinecraftServer server, int ticks) {
		heldOffUntil = server.getTickCount() + ticks;
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % 20 != 0 || now < heldOffUntil) return;
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		if (level == null || level.getDifficulty() == Difficulty.PEACEFUL) return;
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator()) continue;
			int next = NEXT.computeIfAbsent(player.getUUID(), id -> now + GRACE + random.nextInt(DELAY_SPREAD));
			if (now < next) continue;
			NEXT.put(player.getUUID(), now + DELAY + random.nextInt(DELAY_SPREAD));
			if (random.nextInt(100) < CHANCE) spawnBand(player);
		}
	}

	/** Two or three marauders somewhere behind the player in the halls; returns how many came. */
	static int spawnBand(ServerPlayer player) {
		ServerLevel level = player.level();
		int near = level.getEntitiesOfClass(Mob.class, new AABB(player.blockPosition()).inflate(NEAR), mob -> mob.entityTags().contains(TAG)).size();
		if (near >= MAX_NEAR) return 0;
		BlockPos spot = findSpot(player);
		if (spot == null) return 0;
		RandomSource random = level.getRandom();
		int size = 2 + random.nextInt(2), came = 0;
		for (int i = 0; i < size; i++) {
			BlockPos at = i == 0 ? spot : spot.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
			if (!free(level, at)) at = spot;
			EntityType<? extends Mob> type = i == 0 ? EntityTypes.PILLAGER : EntityTypes.VINDICATOR;
			if (spawn(level, type, at) != null) came++;
		}
		return came;
	}

	private static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, BlockPos at) {
		Mob mob = type.create(level, EntitySpawnReason.EVENT);
		if (mob == null) return null;
		mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		mob.setCustomName(Component.translatable("entity.noclip_backrooms.marauder"));
		level.addFreshEntity(mob);
		mob.addTag(TAG);
		return mob;
	}

	private static BlockPos findSpot(ServerPlayer player) {
		ServerLevel level = player.level();
		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
		for (int attempt = 0; attempt < 40; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2, distance = NEAREST + random.nextDouble() * (FARTHEST - NEAREST);
			double ax = Math.cos(angle), az = Math.sin(angle);
			if (ax * look.x + az * look.z > 0.2) continue;
			int x = (int) Math.floor(player.getX() + ax * distance), z = (int) Math.floor(player.getZ() + az * distance);
			Tile tile = Level1Layout.tile(seed, x, z);
			if (tile != Tile.FLOOR && tile != Tile.LINE && tile != Tile.DAMP) continue;
			if (nearSettlement(seed, x, z)) continue;
			BlockPos at = new BlockPos(x, Level1ChunkGenerator.FLOOR_Y + 1, z);
			if (level.isLoaded(at) && free(level, at)) return at;
		}
		return null;
	}

	static boolean nearSettlement(long seed, int x, int z) {
		int dx = Math.floorDiv(x, Level1Layout.DISTRICT), dz = Math.floorDiv(z, Level1Layout.DISTRICT);
		if (Level1Layout.base(seed, dx, dz) == Level1Layout.Base.NONE) return false;
		int lx = Math.floorMod(x, Level1Layout.DISTRICT), lz = Math.floorMod(z, Level1Layout.DISTRICT);
		return lx >= BasePlan.MIN_X - SETTLEMENT_MARGIN && lx <= BasePlan.MAX_X + SETTLEMENT_MARGIN
				&& lz >= BasePlan.MIN_Z - SETTLEMENT_MARGIN && lz <= BasePlan.MAX_Z + SETTLEMENT_MARGIN;
	}

	private static boolean free(ServerLevel level, BlockPos at) {
		return level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
				&& !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty();
	}
}
