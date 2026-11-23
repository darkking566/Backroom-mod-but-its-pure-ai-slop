package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.hound.Hound;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

/**
 * Hounds hunt in Level 1's blackouts. A little while into the dark, one or two come out of the halls behind you;
 * when the lights come back, the ones that are not on top of anyone slink off.
 */
final class Hounds {
	static final String TAG = "noclip_backrooms_level1_hound";
	private static final int AFTER_DARK = 6 * 20, INTERVAL = 5 * 20, CHANCE = 30;
	private static final int MAX_NEAR = 2, NEAR = 48, NEAREST = 20, FARTHEST = 36, LEAVE_BEYOND = 16;

	private static int darkSince = -1, heldOffUntil;

	private Hounds() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Hounds::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			darkSince = -1;
			heldOffUntil = 0;
		});
	}

	/** No hounds come out for a while, for tests. */
	static void holdOff(MinecraftServer server, int ticks) {
		heldOffUntil = server.getTickCount() + ticks;
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % 20 != 0) return;
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		if (level == null) return;
		if (Blackouts.phase() != Blackouts.Phase.DARK) {
			darkSince = -1;
			if (Blackouts.phase() == Blackouts.Phase.NORMAL) slinkOff(level);
			return;
		}
		if (darkSince < 0) darkSince = now;
		int dark = now - darkSince;
		if (now < heldOffUntil || level.getDifficulty() == Difficulty.PEACEFUL || dark < AFTER_DARK || dark % INTERVAL != 0) return;
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator()) continue;
			if (random.nextInt(100) < CHANCE) spawnNear(player);
		}
	}

	/** A hound somewhere behind the player in the halls, unless two are already about; null if none came. */
	static Hound spawnNear(ServerPlayer player) {
		ServerLevel level = player.level();
		if (level.getEntitiesOfClass(Hound.class, new AABB(player.blockPosition()).inflate(NEAR)).size() >= MAX_NEAR) return null;
		BlockPos at = Marauders.findSpot(player, NEAREST, FARTHEST);
		if (at == null) return null;
		Hound hound = Monsters.HOUND.create(level, EntitySpawnReason.EVENT);
		if (hound == null) return null;
		hound.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
		hound.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		hound.addTag(TAG);
		level.addFreshEntity(hound);
		return hound;
	}

	/** With the lights back on, hounds with nobody close by go back into the halls. */
	static int slinkOff(ServerLevel level) {
		int gone = 0;
		for (Hound hound : level.getEntities(Monsters.HOUND, hound -> hound.entityTags().contains(TAG))) {
			if (level.getNearestPlayer(hound, LEAVE_BEYOND) != null) continue;
			hound.discard();
			gone++;
		}
		return gone;
	}
}
