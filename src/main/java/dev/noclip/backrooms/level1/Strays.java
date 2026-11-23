package dev.noclip.backrooms.level1;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.feline.Cat;

/**
 * "A small figure beside the crates": now and then a kitten sits by a crate that has just turned up.
 * It bolts into the pipes as soon as anyone comes close, or after a while on its own.
 */
final class Strays {
	static final String TAG = "noclip_backrooms_stray";
	private static final int CHANCE = 20, LIFETIME = 30 * 20, SHY = 5;

	private record Stray(UUID id, int born) {
	}

	private static final List<Stray> STRAYS = new ArrayList<>();

	private Strays() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Strays::tick);
		// Never there when you come back: one left over from before a restart or an unloaded chunk is gone.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG)) entity.discard();
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> STRAYS.clear());
	}

	static Cat maybeBeside(ServerLevel level, BlockPos crate) {
		if (level.getRandom().nextInt(100) >= CHANCE) return null;
		return beside(level, crate);
	}

	static Cat beside(ServerLevel level, BlockPos crate) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos at = crate.relative(side);
			if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()) continue;
			if (level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()) continue;
			Cat cat = EntityTypes.CAT.create(level, EntitySpawnReason.EVENT);
			if (cat == null) return null;
			cat.setBaby(true);
			cat.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, side.toYRot(), 0);
			level.addFreshEntity(cat);
			cat.addTag(TAG);
			STRAYS.add(new Stray(cat.getUUID(), level.getServer().getTickCount()));
			return cat;
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		if (STRAYS.isEmpty() || server.getTickCount() % 10 != 0) return;
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		if (level == null) return;
		for (Iterator<Stray> it = STRAYS.iterator(); it.hasNext(); ) {
			Stray stray = it.next();
			Entity cat = level.getEntity(stray.id());
			if (cat == null || cat.isRemoved()) {
				it.remove();
				continue;
			}
			boolean old = server.getTickCount() - stray.born() > LIFETIME;
			boolean spotted = level.getNearestPlayer(cat, SHY) != null;
			if (!old && !spotted) continue;
			level.playSound(null, cat.getX(), cat.getY(), cat.getZ(), Level1Sounds.CAT, SoundSource.NEUTRAL, 0.8F, 1.3F);
			level.sendParticles(ParticleTypes.POOF, cat.getX(), cat.getY() + 0.3, cat.getZ(), 6, 0.2, 0.1, 0.2, 0.02);
			cat.discard();
			it.remove();
		}
	}
}
