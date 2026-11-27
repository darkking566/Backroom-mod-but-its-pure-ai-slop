package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.core.people.People;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.level1.Level1Layout.Base;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
import net.minecraft.world.phys.Vec3;

/**
 * The people of the settlements: Alpha Base's patrol, the colonists, the traders, Hippocrates-1's medics, Tom and his
 * partner, the Speednoclippers' recruiters, and the self-styled justice seekers of Cornucopia (who do not like visitors).
 * They appear when a wanderer comes within sight of a settlement that has none yet, and stay put, harmless but for the last.
 */
final class Residents {
	static final String TAG = "noclip_backrooms_resident";
	private static final int NEAR = 64;
	private static final Map<UUID, State> STATES = new HashMap<>();
	/** When each settlement last had people moved in; Cornucopia's raiders wander off or die, so they come back slowly. */
	private static final Map<Long, Integer> MOVED_IN = new HashMap<>();
	private static final int RETURN_TICKS = 6000;
	private static int heldOffUntil;

	private Residents() {
	}

	/** No one moves in for a while, for tests. */
	static void holdOff(MinecraftServer server, int ticks) {
		heldOffUntil = server.getTickCount() + ticks;
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Residents::tick);
		ServerTickEvents.END_SERVER_TICK.register(Residents::walk);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> MOVED_IN.clear());
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

	private static final class State {
		Vec3 goal;
		int waitUntil, giveUp;
	}

	/** Strolls the people about their settlement: a few blocks at a time, a pause between, and a turn to face anyone close. */
	private static void walk(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		if (level == null || level.players().isEmpty()) return;
		int now = server.getTickCount();
		Set<UUID> seen = new HashSet<>();
		for (ServerPlayer player : level.players()) {
			for (Person person : level.getEntitiesOfClass(Person.class, player.getBoundingBox().inflate(48), p -> p.entityTags().contains(TAG))) {
				if (seen.add(person.getUUID())) stroll(level, person, now);
			}
		}
		if (now % 200 == 0) STATES.keySet().removeIf(id -> !seen.contains(id));
	}

	private static void stroll(ServerLevel level, Person person, int now) {
		State state = STATES.computeIfAbsent(person.getUUID(), id -> new State());
		ServerPlayer near = null;
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceTo(person) < 4) near = player;
		}
		if (near != null) {
			Walkers.face(person, near.position());
			return;
		}
		if (now < state.waitUntil) return;
		RandomSource random = level.getRandom();
		if (state.goal == null || now > state.giveUp) {
			state.goal = pickGoal(level, person, random);
			state.giveUp = now + 400;
			if (state.goal == null) state.waitUntil = now + 100;
			return;
		}
		if (Walkers.step(person, state.goal, 0.035) < 0.5) {
			state.goal = null;
			state.waitUntil = now + 60 + random.nextInt(140);
		}
	}

	/** A free spot a few blocks off, kept inside the settlement's open middle. */
	private static Vec3 pickGoal(ServerLevel level, Person person, RandomSource random) {
		int x0 = Math.floorDiv(person.getBlockX(), Level1Layout.DISTRICT) * Level1Layout.DISTRICT;
		int z0 = Math.floorDiv(person.getBlockZ(), Level1Layout.DISTRICT) * Level1Layout.DISTRICT;
		for (int attempt = 0; attempt < 20; attempt++) {
			int x = person.getBlockX() + random.nextInt(13) - 6, z = person.getBlockZ() + random.nextInt(13) - 6;
			if (x < x0 + 20 || x > x0 + 44 || z < z0 + 18 || z > z0 + 44) continue;
			BlockPos at = new BlockPos(x, Level1ChunkGenerator.FLOOR_Y + 1, z);
			if (level.isLoaded(at) && free(level, at)) return new Vec3(x + 0.5, at.getY(), z + 0.5);
		}
		return null;
	}

	/** For tests: where the first resident near this point is, or null. */
	static Vec3 residentNear(ServerLevel level, BlockPos around) {
		var found = level.getEntitiesOfClass(Person.class, new AABB(around).inflate(24), p -> p.entityTags().contains(TAG));
		return found.stream().min(java.util.Comparator.comparing(Person::getUUID)).map(Person::position).orElse(null);
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
		int now = level.getServer().getTickCount();
		Integer last = MOVED_IN.get(((long) dx << 32) ^ (dz & 0xffffffffL));
		if (last != null && now >= last && now - last < RETURN_TICKS) return 0;
		RandomSource random = level.getRandom();
		int came = 0;
		for (String who : people) {
			boolean hostile = who.equals("justice");
			boolean blackMarket = who.equals("black_market");
			String look = blackMarket ? ShopRole.BLACK_MARKET.look() : who;
			for (int attempt = 0; attempt < 60; attempt++) {
				BlockPos at = new BlockPos(x0 + 26 + random.nextInt(13), Level1ChunkGenerator.FLOOR_Y + 1, z0 + 24 + random.nextInt(14));
				if (!level.isLoaded(at) || !free(level, at)) continue;
				EntityType<? extends Mob> type = hostile ? EntityTypes.PILLAGER : People.type(look);
				Mob mob = type.create(level, EntitySpawnReason.EVENT);
				if (mob == null) break;
				float yaw = random.nextFloat() * 360;
					mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
					mob.setYBodyRot(yaw);
					mob.setYHeadRot(yaw);
				if (hostile) {
					mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
				} else {
					mob.setNoAi(true);
					mob.setInvulnerable(true);
				}
				mob.setCustomName(blackMarket ? ShopRole.BLACK_MARKET.title() : Component.translatable("resident.noclip_backrooms." + who));
				mob.setCustomNameVisible(true);
				mob.setPersistenceRequired();
				level.addFreshEntity(mob);
				mob.addTag(TAG);
				if (blackMarket) mob.addTag(ShopRole.BLACK_MARKET.tag());
				came++;
				break;
			}
		}
		if (came > 0) MOVED_IN.put(((long) dx << 32) ^ (dz & 0xffffffffL), now);
		return came;
	}

	private static List<String> people(Base base) {
		return switch (base) {
			case FIRST_LIGHT -> List.of("meg_patrol", "meg_patrol");
			case BLINK -> List.of("colonist", "colonist");
			case TRADERS_KEEP -> List.of("trader", "trader", "black_market");
			case HIPPOCRATES -> List.of("medic", "medic");
			case TOMS -> List.of("tom", "aiko");
			case SIGNUP -> List.of("speednoclipper", "speednoclipper", "black_market");
			case CORNUCOPIA -> List.of("justice", "justice", "justice");
			case NONE -> List.of();
		};
	}

	private static boolean free(ServerLevel level, BlockPos at) {
		return level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
				&& !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty();
	}
}
