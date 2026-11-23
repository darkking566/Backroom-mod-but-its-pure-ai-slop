package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Layout.Base;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * "M.E.G. patrols actively look for newly arrived wanderers and escort them to the base": a little after you arrive
 * in Level 1, a pair of patrol members turns up in the halls, walks to you, and leads you to the nearest First Light
 * outpost, waiting whenever you fall behind.
 */
final class Patrols {
	static final String TAG = "noclip_backrooms_patrol";
	private static final int ARRIVAL_DELAY = 20 * 20, SEARCH = 12;
	private static final double SPEED = 0.17;
	private static int heldOffUntil;

	private enum Phase {
		SEEK, LEAD
	}

	private static final class Patrol {
		final List<Mob> members = new ArrayList<>();
		Phase phase = Phase.SEEK;
		Vec3 base;
	}

	private static final Map<UUID, Patrol> PATROLS = new HashMap<>();
	private static final Map<UUID, Integer> ARRIVED = new HashMap<>();
	private static final Set<UUID> ESCORTED = new HashSet<>();

	private Patrols() {
	}

	/** No patrols go out for a while, for tests. */
	static void holdOff(MinecraftServer server, int ticks) {
		heldOffUntil = server.getTickCount() + ticks;
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Patrols::tick);
		// A patrol left over from before a restart is gone.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG)) entity.discard();
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			PATROLS.clear();
			ARRIVED.clear();
			ESCORTED.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		if (level == null) return;
		int now = server.getTickCount();

		Set<UUID> here = new HashSet<>();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative()) continue;
			here.add(player.getUUID());
			int since = now - ARRIVED.computeIfAbsent(player.getUUID(), id -> now);
			if (now >= heldOffUntil && since > ARRIVAL_DELAY && ESCORTED.add(player.getUUID())) dispatch(level, player);
		}
		// Someone who left starts afresh next time; a patrol with no one to find goes home.
		ARRIVED.keySet().removeIf(id -> !here.contains(id));
		ESCORTED.removeIf(id -> !here.contains(id));

		for (Iterator<Map.Entry<UUID, Patrol>> it = PATROLS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Patrol> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Patrol patrol = entry.getValue();
			if (player == null || player.level() != level || !player.isAlive() || patrol.members.stream().anyMatch(Mob::isRemoved)) {
				patrol.members.forEach(Mob::discard);
				it.remove();
				continue;
			}
			if (advance(level, player, patrol)) it.remove();
		}
	}

	/** Sends a patrol to the player from the nearest outpost; returns whether one set out. */
	static boolean dispatch(ServerLevel level, ServerPlayer player) {
		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		Vec3 base = nearestOutpost(seed, player.getBlockX(), player.getBlockZ());
		if (base == null || PATROLS.containsKey(player.getUUID())) return false;
		BlockPos spot = Marauders.findSpot(player, 18, 28);
		if (spot == null) return false;
		Patrol patrol = new Patrol();
		patrol.base = base;
		for (int i = 0; i < 2; i++) {
			Mob mob = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
			if (mob == null) break;
			mob.snapTo(spot.getX() + 0.5 + i, spot.getY(), spot.getZ() + 0.5, 0, 0);
			mob.setNoAi(true);
			mob.setInvulnerable(true);
			mob.setPersistenceRequired();
			mob.setCustomName(Component.translatable("resident.noclip_backrooms.meg_patrol"));
			mob.setCustomNameVisible(true);
			level.addFreshEntity(mob);
			// Tagged only once it is in the world, or the clean-up of stale patrols on load would take it at once.
			mob.addTag(TAG);
			patrol.members.add(mob);
		}
		if (patrol.members.isEmpty()) return false;
		PATROLS.put(player.getUUID(), patrol);
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.patrol_coming"));
		return true;
	}

	/** One tick of a patrol; returns true once it has delivered its charge. */
	private static boolean advance(ServerLevel level, ServerPlayer player, Patrol patrol) {
		double nearest = Double.MAX_VALUE;
		for (Mob member : patrol.members) nearest = Math.min(nearest, member.position().distanceTo(player.position()));

		if (patrol.phase == Phase.SEEK && nearest < 4) {
			patrol.phase = Phase.LEAD;
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.patrol_found"));
		}
		Vec3 target = patrol.phase == Phase.SEEK ? player.position() : patrol.base;
		boolean waiting = patrol.phase == Phase.LEAD && nearest > 14;
		double toBase = Double.MAX_VALUE;
		for (int i = 0; i < patrol.members.size(); i++) {
			Mob member = patrol.members.get(i);
			if (waiting) {
				Walkers.face(member, player.position());
				continue;
			}
			double left = Walkers.step(member, target.add(i * 1.5, 0, 0), patrol.phase == Phase.SEEK ? SPEED * 1.3 : SPEED);
			if (patrol.phase == Phase.LEAD) toBase = Math.min(toBase, left);
		}
		if (patrol.phase == Phase.LEAD && toBase < 8) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.patrol_arrived"));
			// They stay on at the outpost with the rest of its people.
			for (Mob member : patrol.members) {
				member.removeTag(TAG);
				member.addTag(Residents.TAG);
				Walkers.forget(member);
			}
			return true;
		}
		return false;
	}

	/** The centre of the nearest First Light outpost within SEARCH districts, or null. */
	static Vec3 nearestOutpost(long seed, int x, int z) {
		int dx0 = Math.floorDiv(x, Level1Layout.DISTRICT), dz0 = Math.floorDiv(z, Level1Layout.DISTRICT);
		Vec3 best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = dx0 - SEARCH; dx <= dx0 + SEARCH; dx++) {
			for (int dz = dz0 - SEARCH; dz <= dz0 + SEARCH; dz++) {
				if (Level1Layout.base(seed, dx, dz) != Base.FIRST_LIGHT) continue;
				double cx = dx * Level1Layout.DISTRICT + 32, cz = dz * Level1Layout.DISTRICT + 32;
				double distance = Math.hypot(cx - x, cz - z);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = new Vec3(cx, Level1ChunkGenerator.FLOOR_Y + 1, cz);
				}
			}
		}
		return best;
	}

	static int active() {
		return PATROLS.size();
	}

	/** For tests: how many patrols are leading someone, where the first one's first member is, and where it is headed. */
	static int leading() {
		return (int) PATROLS.values().stream().filter(p -> p.phase == Phase.LEAD).count();
	}

	static Vec3 memberPosition() {
		return PATROLS.values().stream().findFirst().map(p -> p.members.getFirst().position()).orElse(null);
	}

	static Vec3 destination() {
		return PATROLS.values().stream().findFirst().map(p -> p.base).orElse(null);
	}
}
