package dev.noclip.backrooms.monsters;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * A badly hurt creature does not stand there and die: below 30% health it breaks off, backs away from the players into
 * the darkest place it can find, and heals out of sight; at 60% it comes back. Killing one means chasing it into the dark
 * (docs/project/1-gameplay/README.md). Bosses, neutral creatures and ones that cannot walk don't use this.
 *
 * Added from a creature's registerGoals with {@code Retreat.add(this, goalSelector, targetSelector)}: one goal takes
 * over moving, the other keeps every other target goal from picking a target while it lasts.
 */
public final class Retreat {
	static final float FLEE_BELOW = 0.3F;
	static final float RETURN_ABOVE = 0.6F;
	/** Players within this distance are fled from; with none within HEAL_CLEAR it heals. */
	static final double FLEE_FROM = 24.0;
	static final double HEAL_CLEAR = 12.0;
	/** Share of max health healed a second while hidden. */
	static final float HEAL_PER_SECOND = 0.01F;

	private Retreat() {
	}

	public static void add(PathfinderMob mob, GoalSelector goals, GoalSelector targets) {
		State state = new State(mob);
		goals.addGoal(0, new Away(state));
		targets.addGoal(0, new HoldFire(state));
	}

	static final class State {
		final PathfinderMob mob;
		boolean active;

		State(PathfinderMob mob) {
			this.mob = mob;
		}

		boolean update() {
			float share = mob.getHealth() / mob.getMaxHealth();
			if (!active && share <= FLEE_BELOW && mob.getLastHurtByMob() instanceof Player) {
				active = true;
			} else if (active && share >= RETURN_ABOVE) {
				active = false;
			}
			return active && mob.isAlive();
		}
	}

	/** Backs away into the dark and heals. */
	static final class Away extends Goal {
		private final State state;
		private int repath;

		Away(State state) {
			this.state = state;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return state.update();
		}

		@Override
		public boolean canContinueToUse() {
			return state.update();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void start() {
			state.mob.setTarget(null);
			repath = 0;
		}

		@Override
		public void stop() {
			state.mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			PathfinderMob mob = state.mob;
			Player near = mob.level().getNearestPlayer(mob, FLEE_FROM);
			if (near != null && !near.isCreative() && !near.isSpectator()) {
				if (--repath <= 0 || mob.getNavigation().isDone()) {
					repath = 20;
					Vec3 spot = darkestAway(mob, near.position());
					if (spot != null) mob.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.3);
				}
			} else {
				mob.getNavigation().stop();
			}
			boolean hidden = mob.level().getNearestPlayer(mob, HEAL_CLEAR) == null;
			if (hidden && mob.tickCount % 20 == 0 && mob.tickCount - mob.getLastHurtByMobTimestamp() > 60) {
				mob.heal(mob.getMaxHealth() * HEAL_PER_SECOND);
			}
		}

		/** Of a few spots away from the player, the one with the least block light. */
		private static Vec3 darkestAway(PathfinderMob mob, Vec3 from) {
			Vec3 best = null;
			int bestLight = Integer.MAX_VALUE;
			for (int i = 0; i < 6; i++) {
				Vec3 spot = DefaultRandomPos.getPosAway(mob, 16, 7, from);
				if (spot == null) continue;
				int light = mob.level().getBrightness(LightLayer.BLOCK, BlockPos.containing(spot));
				if (light < bestLight) {
					best = spot;
					bestLight = light;
				}
			}
			return best;
		}
	}

	/** Holds the target slot so the creature's own target goals stay quiet while it retreats. */
	static final class HoldFire extends Goal {
		private final State state;

		HoldFire(State state) {
			this.state = state;
			setFlags(EnumSet.of(Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			return state.update();
		}

		@Override
		public boolean canContinueToUse() {
			return state.update();
		}

		@Override
		public void start() {
			state.mob.setTarget(null);
		}

		@Override
		public void tick() {
			if (state.mob.getTarget() != null) state.mob.setTarget(null);
		}
	}
}
