package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.mixin.client.WalkAnimationStateAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * What you see past an edge of Level 0 is the same Stage again (Level0Space), so it should hold the same people and
 * things: every player (you too), monster and dropped item in the Stage is shown again one Stage over, in each direction
 * you could see that far, moving as the original moves. The ghosts are only drawn here: they cannot be hit or touched
 * (cross the edge and you meet the real one, standing where its ghost stood).
 */
public final class Level0Ghosts {
	/** Ghost ids are negative, and far from the ones the sanity hallucinations use. */
	private static final int FIRST_ID = -1_900_000_000;
	private static final Map<Long, Entity> GHOSTS = new HashMap<>();
	private static ClientLevel ghostLevel;
	private static int nextId = FIRST_ID;
	private static int ticks;

	private Level0Ghosts() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Level0Ghosts::tick);
	}

	/** How many ghosts are shown now (for the tests). */
	public static int count() {
		return GHOSTS.size();
	}

	private static void tick(Minecraft client) {
		ClientLevel level = client.level;
		if (level != ghostLevel) {
			// A new level: the old one went away with its ghosts.
			GHOSTS.clear();
			ghostLevel = level;
		}
		if (level == null) return;
		if (level.dimension() != Level0.LEVEL_0 || client.player == null) {
			clear(level);
			return;
		}
		ticks++;
		double range = client.options.getEffectiveRenderDistance() * 16.0;
		double ex = client.player.getX(), ez = client.player.getZ();
		List<Entity> originals = new ArrayList<>();
		for (Entity entity : level.entitiesForRendering()) {
			if (entity.getId() >= 0) originals.add(entity);
		}
		Set<Long> shown = new HashSet<>();
		for (Entity original : originals) {
			for (int kx = -1; kx <= 1; kx++) {
				for (int kz = -1; kz <= 1; kz++) {
					if (kx == 0 && kz == 0) continue;
					double dx = kx * Level0Space.PERIOD, dz = kz * Level0Space.PERIOD;
					double gx = original.getX() + dx - ex, gz = original.getZ() + dz - ez;
					if (gx * gx + gz * gz > range * range) continue;
					long key = ((long) original.getId() << 4) | ((kx + 1) * 3 + (kz + 1));
					Entity ghost = GHOSTS.get(key);
					if (ghost == null || ghost.isRemoved()) {
						ghost = create(level, original);
						if (ghost == null) continue;
						ghost.setId(nextId--);
						follow(original, ghost, dx, dz, true);
						level.addEntity(ghost);
						GHOSTS.put(key, ghost);
					} else {
						follow(original, ghost, dx, dz, ticks % 10 == 0);
					}
					shown.add(key);
				}
			}
		}
		GHOSTS.entrySet().removeIf(entry -> {
			if (shown.contains(entry.getKey())) return false;
			level.removeEntity(entry.getValue().getId(), Entity.RemovalReason.DISCARDED);
			return true;
		});
	}

	private static void clear(ClientLevel level) {
		for (Entity ghost : GHOSTS.values()) level.removeEntity(ghost.getId(), Entity.RemovalReason.DISCARDED);
		GHOSTS.clear();
	}

	private static Entity create(ClientLevel level, Entity original) {
		Entity ghost = original instanceof AbstractClientPlayer player ? new RemotePlayer(level, player.getGameProfile())
				: original.getType().create(level, EntitySpawnReason.LOAD);
		// A player's ghost would carry that player's own id (from the profile): two entities, one id.
		if (ghost != null) ghost.setUUID(java.util.UUID.randomUUID());
		return ghost;
	}

	/** Puts the ghost where the original is, one Stage over, posed the same. */
	private static void follow(Entity original, Entity ghost, double dx, double dz, boolean equipment) {
		List<SynchedEntityData.DataValue<?>> data = original.getEntityData().getNonDefaultValues();
		if (data != null) ghost.getEntityData().assignValues(data);
		ghost.setPos(original.getX() + dx, original.getY(), original.getZ() + dz);
		ghost.xo = original.xo + dx;
		ghost.yo = original.yo;
		ghost.zo = original.zo + dz;
		ghost.xOld = original.xOld + dx;
		ghost.yOld = original.yOld;
		ghost.zOld = original.zOld + dz;
		ghost.setYRot(original.getYRot());
		ghost.yRotO = original.yRotO;
		ghost.setXRot(original.getXRot());
		ghost.xRotO = original.xRotO;
		ghost.tickCount = original.tickCount;
		ghost.noPhysics = true;
		ghost.setInvisible(original.isInvisible());
		if (original instanceof LivingEntity from && ghost instanceof LivingEntity to) {
			to.yBodyRot = from.yBodyRot;
			to.yBodyRotO = from.yBodyRotO;
			to.yHeadRot = from.yHeadRot;
			to.yHeadRotO = from.yHeadRotO;
			to.attackAnim = from.attackAnim;
			to.oAttackAnim = from.oAttackAnim;
			to.swinging = from.swinging;
			to.swingTime = from.swingTime;
			WalkAnimationStateAccess walkFrom = (WalkAnimationStateAccess) from.walkAnimation;
			WalkAnimationStateAccess walkTo = (WalkAnimationStateAccess) to.walkAnimation;
			walkTo.noclipBackrooms$setSpeedOld(walkFrom.noclipBackrooms$speedOld());
			walkTo.noclipBackrooms$setSpeed(from.walkAnimation.speed());
			walkTo.noclipBackrooms$setPosition(from.walkAnimation.position());
			walkTo.noclipBackrooms$setPositionScale(walkFrom.noclipBackrooms$positionScale());
			if (equipment) {
				for (EquipmentSlot slot : EquipmentSlot.values()) to.setItemSlot(slot, from.getItemBySlot(slot).copy());
			}
		}
	}
}
