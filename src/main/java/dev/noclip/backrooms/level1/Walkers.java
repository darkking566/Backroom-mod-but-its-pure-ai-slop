package dev.noclip.backrooms.level1;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Walks the settlements' people about without their own AI (villagers think with a brain that fights any path we set):
 * a straight step toward the target with collisions, sliding along walls, and a sidestep when stuck on a pillar.
 */
final class Walkers {
	private static final Map<UUID, int[]> STUCK = new HashMap<>();

	private Walkers() {
	}

	/** One tick of walking toward target at speed blocks per tick; returns the horizontal distance still to go. */
	static double step(Mob mob, Vec3 target, double speed) {
		Vec3 to = new Vec3(target.x - mob.getX(), 0, target.z - mob.getZ());
		double distance = to.length();
		if (distance < 0.4) return distance;
		int[] stuck = STUCK.computeIfAbsent(mob.getUUID(), id -> new int[] {0, 0, 0});
		Vec3 heading = to.scale(1 / distance);
		if (stuck[1] > 0) {
			// Sidestepping round something: walk across the way for a few ticks.
			heading = new Vec3(-heading.z, 0, heading.x).scale(stuck[1] % 2 == 0 ? 1 : -1);
			stuck[1]--;
		}
		Vec3 move = heading.scale(Math.min(speed, distance));
		Vec3 before = mob.position();
		mob.move(MoverType.SELF, new Vec3(move.x, -0.1, move.z));
		double moved = mob.position().subtract(before).horizontalDistance();
		if (moved < speed * 0.3) {
			stuck[2]++;
			if (++stuck[0] > 10) {
				stuck[0] = 0;
				stuck[1] = 24 + (mob.getRandom().nextInt(2));
			}
			// Boxed in by racks or pillars for too long: step over to open floor ahead, as a person would walk round.
			if (stuck[2] > 50) {
				stuck[2] = 0;
				hop(mob, heading);
			}
		} else {
			stuck[0] = 0;
			stuck[2] = Math.max(0, stuck[2] - 2);
		}
		float yaw = (float) (Math.toDegrees(Math.atan2(-heading.x, heading.z)));
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.yBodyRot = yaw;
		return distance;
	}

	/** Moves the mob to the first free floor spot 3 to 7 blocks ahead, or just to one side of that line. */
	private static void hop(Mob mob, Vec3 heading) {
		Vec3 side = new Vec3(-heading.z, 0, heading.x);
		for (int ahead = 3; ahead <= 7; ahead++) {
			for (int lateral : new int[] {0, 2, -2}) {
				BlockPos at = BlockPos.containing(mob.getX() + heading.x * ahead + side.x * lateral, mob.getY(), mob.getZ() + heading.z * ahead + side.z * lateral);
				var level = mob.level();
				if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
						&& !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()) {
					mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, mob.getYRot(), 0);
					return;
				}
			}
		}
	}

	static void face(Mob mob, Vec3 target) {
		float yaw = (float) Math.toDegrees(Math.atan2(-(target.x - mob.getX()), target.z - mob.getZ()));
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.yBodyRot = yaw;
	}

	static void forget(Mob mob) {
		STUCK.remove(mob.getUUID());
	}
}
