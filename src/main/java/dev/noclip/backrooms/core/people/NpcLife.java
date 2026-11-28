package dev.noclip.backrooms.core.people;

import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.core.trip.Anomalies;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Gives static Backrooms and Institute {@link Person} NPCs subtle life: idle posture, looking toward
 * nearby players, periodic friendly waves, gentle bounded strolling in the Backrooms, role greetings,
 * and eerie stalking / click-to-reveal for disguised Skin-Stealers.
 */
public final class NpcLife {
	private static final String INSTITUTE_TAG = "noclip_backrooms_institute";
	private static final String MIMIC_TAG = "noclip_backrooms_mimic_figure";
	private static final Set<String> DEDICATED_AI_TAGS = Set.of(
			"noclip_backrooms_town",
			"noclip_backrooms_town_picnic",
			"noclip_backrooms_398",
			"noclip_specimen",
			"noclip_backrooms_patrol",
			"noclip_backrooms_resident",
			"noclip_backrooms_worker",
			"noclip_backrooms_level4_wanderer",
			"noclip_backrooms_level4_omega_guard",
			"noclip_backrooms_level4_omega_squad",
			"noclip_backrooms_level4_amor",
			"noclip_backrooms_level4_tbd"
	);

	private static final int GREET_COOLDOWN_TICKS = 20 * 45;
	private static final double STROLL_STEP = 0.068; // Applied every 2 ticks (~0.68 blocks/sec).
	private static final double MIMIC_STEP = 0.055;

	private static final class State {
		final Vec3 home;
		final float homeYaw;
		Vec3 goal;
		int waitUntil;
		int giveUpAt;
		int nextWaveAt;

		State(Person p, int now, RandomSource rng) {
			this.home = p.position();
			this.homeYaw = p.getYRot();
			this.waitUntil = now + 20 + rng.nextInt(80);
			this.nextWaveAt = now + 40 + rng.nextInt(120);
		}
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Map<String, Integer> GREETED_AT = new HashMap<>();

	private NpcLife() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(NpcLife::tick);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			if (!(world instanceof ServerLevel level) || !(entity instanceof Person person)) {
				return InteractionResult.PASS;
			}
			if (person.entityTags().contains(MIMIC_TAG)) {
				Anomalies.revealMimicFigure(level, person, sp, false);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			STATES.clear();
			GREETED_AT.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if ((now & 1) != 0) return;

		Set<UUID> seen = new HashSet<>();
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty()) continue;
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator()) continue;
				for (Person person : level.getEntitiesOfClass(Person.class, player.getBoundingBox().inflate(28), Entity::isAlive)) {
					if (!seen.add(person.getUUID())) continue;
					if (person.entityTags().contains(MIMIC_TAG)) {
						tickMimicStalk(level, person, now);
						continue;
					}
					if (hasDedicatedAi(person)) continue;
					tickFriendlyNpc(level, person, now);
				}
			}
		}

		if (now % 400 == 0) {
			STATES.entrySet().removeIf(e -> now - e.getValue().waitUntil > 6000 && e.getValue().goal == null);
			for (Iterator<Map.Entry<String, Integer>> it = GREETED_AT.entrySet().iterator(); it.hasNext(); ) {
				if (now - it.next().getValue() > GREET_COOLDOWN_TICKS * 2) it.remove();
			}
		}
	}

	private static boolean hasDedicatedAi(Person person) {
		for (String tag : person.entityTags()) {
			if (DEDICATED_AI_TAGS.contains(tag)) return true;
		}
		return false;
	}

	private static void tickMimicStalk(ServerLevel level, Person mimic, int now) {
		ServerPlayer target = nearestPlayer(level, mimic, 24.0);
		if (target == null) return;
		face(mimic, target.position());
		double distSq = mimic.distanceToSqr(target);
		if (distSq <= 20.25 && mimic.tickCount >= 10) {
			Anomalies.revealMimicFigure(level, mimic, target, false);
			return;
		}
		if (distSq <= 256.0 && distSq > 40.0) {
			stepTowardPassable(level, mimic, target.position(), MIMIC_STEP);
		}
	}

	private static void tickFriendlyNpc(ServerLevel level, Person person, int now) {
		RandomSource rng = level.getRandom();
		State st = STATES.computeIfAbsent(person.getUUID(), id -> new State(person, now, rng));
		ServerPlayer near = nearestPlayer(level, person, 5.5);

		if (near != null) {
			face(person, near.position());
			if (now >= st.nextWaveAt) {
				person.swing(InteractionHand.MAIN_HAND);
				st.nextWaveAt = now + 70 + rng.nextInt(90);
			}
			if (near.distanceToSqr(person) <= 17.64) {
				maybeGreet(near, person, now);
			}
			return;
		}

		boolean stationed = person.entityTags().contains(INSTITUTE_TAG)
				|| (Home.isHome(level.dimension()) && ShopRole.of(person) != null);
		if (stationed) {
			float glance = (float) Math.sin((now + Math.abs(person.getUUID().hashCode() % 200)) * 0.045) * 14.0F;
			applyYaw(person, st.homeYaw + glance);
			return;
		}

		// Backrooms NPCs gently pace within a tight radius of their post so they feel alive without drifting away.
		if (now < st.waitUntil) {
			float glance = (float) Math.sin((now + Math.abs(person.getUUID().hashCode() % 200)) * 0.05) * 18.0F;
			applyYaw(person, st.homeYaw + glance);
			return;
		}

		int roamRadius = ShopRole.of(person) != null ? 1 : 2;
		if (st.goal == null || now > st.giveUpAt) {
			st.goal = pickSafeGoal(level, st.home, roamRadius, rng);
			st.giveUpAt = now + 180;
			if (st.goal == null) st.waitUntil = now + 60 + rng.nextInt(80);
			return;
		}

		double dist = stepTowardPassable(level, person, st.goal, STROLL_STEP);
		if (dist < 0.35 || !canContinueToward(level, person, st.goal)) {
			st.goal = null;
			st.waitUntil = now + 60 + rng.nextInt(120);
		}
	}

	private static ServerPlayer nearestPlayer(ServerLevel level, Person person, double maxRange) {
		double bestSq = maxRange * maxRange;
		ServerPlayer best = null;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			double dSq = player.distanceToSqr(person);
			if (dSq <= bestSq) {
				bestSq = dSq;
				best = player;
			}
		}
		return best;
	}

	private static Vec3 pickSafeGoal(ServerLevel level, Vec3 home, int radius, RandomSource rng) {
		int hx = (int) Math.floor(home.x);
		int hy = (int) Math.floor(home.y);
		int hz = (int) Math.floor(home.z);
		for (int attempt = 0; attempt < 10; attempt++) {
			int gx = hx + rng.nextInt(radius * 2 + 1) - radius;
			int gz = hz + rng.nextInt(radius * 2 + 1) - radius;
			BlockPos feet = new BlockPos(gx, hy, gz);
			if (isWalkable(level, feet)) {
				return new Vec3(gx + 0.5, hy, gz + 0.5);
			}
		}
		return null;
	}

	private static boolean canContinueToward(ServerLevel level, Person person, Vec3 target) {
		Vec3 delta = target.subtract(person.position()).multiply(1, 0, 1);
		double len = delta.length();
		if (len <= 0.1) return true;
		Vec3 next = person.position().add(delta.scale(Math.min(0.45, len) / len));
		return isWalkable(level, BlockPos.containing(next.x, person.getY() + 0.1, next.z));
	}

	private static double stepTowardPassable(ServerLevel level, Mob mob, Vec3 target, double speed) {
		face(mob, target);
		Vec3 delta = target.subtract(mob.position()).multiply(1, 0, 1);
		double len = delta.length();
		if (len > 0.25) {
			Vec3 step = delta.scale(Math.min(speed, len) / len);
			Vec3 probe = mob.position().add(step.x, 0.1, step.z);
			if (isWalkable(level, BlockPos.containing(probe.x, probe.y, probe.z))) {
				mob.move(MoverType.SELF, new Vec3(step.x, -0.08, step.z));
			}
		}
		return len;
	}

	private static boolean isWalkable(ServerLevel level, BlockPos feet) {
		if (!level.isLoaded(feet)) return false;
		BlockState below = level.getBlockState(feet.below());
		if (!below.isFaceSturdy(level, feet.below(), Direction.UP)) return false;
		BlockState s0 = level.getBlockState(feet);
		BlockState s1 = level.getBlockState(feet.above());
		if (s0.getBlock() instanceof DoorBlock || s1.getBlock() instanceof DoorBlock) return false;
		return s0.getCollisionShape(level, feet).isEmpty()
				&& s1.getCollisionShape(level, feet.above()).isEmpty()
				&& s0.getFluidState().isEmpty();
	}

	private static void maybeGreet(ServerPlayer player, Person person, int now) {
		String key = player.getUUID() + "|" + person.getUUID();
		Integer last = GREETED_AT.get(key);
		if (last != null && now - last < GREET_COOLDOWN_TICKS) return;
		GREETED_AT.put(key, now);

		Component line = greetingFor(person);
		if (line != null) {
			player.sendOverlayMessage(line);
		}
	}

	private static Component greetingFor(Person person) {
		ShopRole role = ShopRole.of(person);
		if (role != null) {
			return Component.translatable("message.noclip_backrooms.npc_life.role." + role.id())
					.withStyle(role == ShopRole.MARTIAL_MASTER ? ChatFormatting.GOLD : ChatFormatting.AQUA);
		}
		Set<String> tags = person.entityTags();
		if (tags.contains("noclip_backrooms_gamma_archivist")
				|| (person.getCustomName() != null && person.getCustomName().getString().contains("Archivist"))) {
			return Component.translatable("message.noclip_backrooms.npc_life.archivist").withStyle(ChatFormatting.YELLOW);
		}
		if (tags.contains("noclip_backrooms_level2_bntg") || tags.contains("noclip_backrooms_level3_staff")) {
			return Component.translatable("message.noclip_backrooms.npc_life.outpost_staff").withStyle(ChatFormatting.GRAY);
		}
		if (tags.contains("noclip_backrooms_level3_uec")) {
			return Component.translatable("message.noclip_backrooms.npc_life.uec").withStyle(ChatFormatting.GRAY);
		}
		if (tags.contains("noclip_backrooms_level4_omega_medic")) {
			return Component.translatable("message.noclip_backrooms.npc_life.medic").withStyle(ChatFormatting.LIGHT_PURPLE);
		}
		if (tags.contains("noclip_backrooms_level4_tbd")) {
			return Component.translatable("message.noclip_backrooms.npc_life.tbd").withStyle(ChatFormatting.GRAY);
		}
		return null;
	}

	private static void face(Mob mob, Vec3 target) {
		float yaw = (float) Math.toDegrees(Math.atan2(-(target.x - mob.getX()), target.z - mob.getZ()));
		applyYaw(mob, yaw);
	}

	private static void applyYaw(Mob mob, float yaw) {
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
	}
}
