package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Layout.Base;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

/**
 * The people of the settlements: Alpha Base's patrol, the colonists, the traders, Hippocrates-1's medics, Tom and his
 * partner, the Speednoclippers' recruiters, and the self-styled justice seekers of Cornucopia (who do not like visitors).
 * They appear when a wanderer comes within sight of a settlement that has none yet, and stay put, harmless but for the last.
 */
final class Residents {
	static final String TAG = "noclip_backrooms_resident";
	private static final int NEAR = 64;
	private static int heldOffUntil;

	private Residents() {
	}

	/** No one moves in for a while, for tests. */
	static void holdOff(MinecraftServer server, int ticks) {
		heldOffUntil = server.getTickCount() + ticks;
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Residents::tick);
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % 100 != 0 || now < heldOffUntil) return;
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		if (level == null) return;
		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			int dx0 = Math.floorDiv(player.getBlockX(), Level1Layout.DISTRICT), dz0 = Math.floorDiv(player.getBlockZ(), Level1Layout.DISTRICT);
			for (int dx = dx0 - 1; dx <= dx0 + 1; dx++) {
				for (int dz = dz0 - 1; dz <= dz0 + 1; dz++) {
					if (Level1Layout.base(seed, dx, dz) == Base.NONE) continue;
					double cx = dx * Level1Layout.DISTRICT + 32, cz = dz * Level1Layout.DISTRICT + 32;
					if (Math.hypot(player.getX() - cx, player.getZ() - cz) <= NEAR) populate(level, dx, dz);
				}
			}
		}
	}

	/** Moves the people into the settlement of district (dx, dz) unless some are already there; returns how many came. */
	static int populate(ServerLevel level, int dx, int dz) {
		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		Base base = Level1Layout.base(seed, dx, dz);
		List<String> people = people(base);
		if (people.isEmpty()) return 0;
		int x0 = dx * Level1Layout.DISTRICT, z0 = dz * Level1Layout.DISTRICT;
		AABB area = new AABB(x0 + BasePlan.MIN_X, Level1ChunkGenerator.FLOOR_Y, z0 + BasePlan.MIN_Z,
				x0 + BasePlan.MAX_X + 1, Level1ChunkGenerator.FLOOR_Y + 4, z0 + BasePlan.MAX_Z + 1);
		if (!level.getEntitiesOfClass(Mob.class, area, mob -> mob.entityTags().contains(TAG)).isEmpty()) return 0;
		RandomSource random = level.getRandom();
		int came = 0;
		for (String who : people) {
			boolean hostile = who.equals("justice");
			for (int attempt = 0; attempt < 60; attempt++) {
				BlockPos at = new BlockPos(x0 + 26 + random.nextInt(13), Level1ChunkGenerator.FLOOR_Y + 1, z0 + 24 + random.nextInt(14));
				if (!level.isLoaded(at) || !free(level, at)) continue;
				EntityType<? extends Mob> type = hostile ? EntityTypes.PILLAGER : EntityTypes.VILLAGER;
				Mob mob = type.create(level, EntitySpawnReason.EVENT);
				if (mob == null) break;
				mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360, 0);
				if (hostile) {
					mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
				} else {
					mob.setNoAi(true);
					mob.setInvulnerable(true);
				}
				mob.setCustomName(Component.translatable("resident.noclip_backrooms." + who));
				mob.setCustomNameVisible(true);
				mob.setPersistenceRequired();
				mob.addTag(TAG);
				level.addFreshEntity(mob);
				came++;
				break;
			}
		}
		return came;
	}

	private static List<String> people(Base base) {
		return switch (base) {
			case FIRST_LIGHT -> List.of("meg_patrol", "meg_patrol");
			case BLINK -> List.of("colonist", "colonist");
			case TRADERS_KEEP -> List.of("trader", "trader");
			case HIPPOCRATES -> List.of("medic", "medic");
			case TOMS -> List.of("tom", "aiko");
			case SIGNUP -> List.of("speednoclipper", "speednoclipper");
			case CORNUCOPIA -> List.of("justice", "justice", "justice");
			case NONE -> List.of();
		};
	}

	private static boolean free(ServerLevel level, BlockPos at) {
		return level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
				&& !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty();
	}
}
