package dev.noclip.backrooms.core.rpg;

import dev.noclip.backrooms.monsters.Monsters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 100% client-side sanity hallucination controller (Sanity < 30%).
 * <p>
 * Nothing here touches the server or other clients:
 * <ul>
 *   <li><b>Phantom Stalkers</b>: client-only Backrooms monsters spawned solely in {@link ClientLevel} with
 *       negative entity IDs that stalk the local player and dissolve in smoke when swung at (teammates only
 *       see the player swinging wildly at empty air).</li>
 *   <li><b>Teammate Disguises</b>: locally replaces nearby {@link RemotePlayer} models with animated Backrooms
 *       monsters through {@code EntityRenderDispatcherHallucination}, invisible to everyone else.</li>
 * </ul>
 */
public final class SanityHallucinations {
	private static final int PHANTOM_ID_BASE = -740100;
	private static final int MAX_PHANTOMS = 2;

	private static final List<EntityType<? extends Monster>> STALKER_POOL = List.of(
			Monsters.SMILER,
			Monsters.HOUND,
			Monsters.BACTERIA,
			Monsters.SKIN_STEALER,
			Monsters.PARTYGOER,
			Monsters.WRETCH
	);

	private static final List<EntityType<? extends Monster>> DISGUISE_POOL = List.of(
			Monsters.SKIN_STEALER,
			Monsters.SMILER,
			Monsters.PARTYGOER,
			Monsters.BACTERIA,
			Monsters.WRETCH,
			Monsters.HOUND
	);

	private static final List<PhantomEntry> PHANTOMS = new ArrayList<>();
	private static final Map<UUID, DisguiseEntry> DISGUISES = new HashMap<>();

	private static ResourceKey<Level> lastDimension;
	private static int nextPhantomId = PHANTOM_ID_BASE;
	private static int spawnCooldownTicks = 30;
	private static int whisperCooldownTicks = 60;
	private static int scareFlashTicks;
	private static int lastHandledSwingTick = -1;

	private SanityHallucinations() {
	}

	/** Called every client tick; spawns/updates/clears client-only hallucinations strictly for the local player. */
	public static void tick(Minecraft client) {
		if (scareFlashTicks > 0) scareFlashTicks--;

		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null || !player.isAlive() || player.isSpectator()) {
			clearAll(level);
			return;
		}

		if (lastDimension != level.dimension()) {
			clearAll(level);
			lastDimension = level.dimension();
		}

		PlayerStats stats = PlayerRpg.get(player);
		int overworldRejectionStage = dev.noclip.backrooms.core.trip.PullClient.stage();
		boolean rejectionHallucination = overworldRejectionStage >= 3 && !level.isBrightOutside();
		if (!stats.isHallucinating() && !rejectionHallucination) {
			clearAll(level);
			spawnCooldownTicks = 30;
			return;
		}

		// Severity scales smoothly from 0.0 (at 30% sanity) to 1.0 (at 0% sanity), or from Overworld Stage 3-4 seepage.
		float sanitySev = stats.isHallucinating()
				? Mth.clamp((PlayerStats.HALLUCINATION_THRESHOLD - stats.sanityRatio()) / PlayerStats.HALLUCINATION_THRESHOLD, 0.0F, 1.0F)
				: 0.0F;
		float rejectSev = rejectionHallucination ? (overworldRejectionStage >= 4 ? 0.85F : 0.45F) : 0.0F;
		float severity = Math.max(sanitySev, rejectSev);
		RandomSource random = level.getRandom();

		tickPhantoms(level, player, severity, random);
		tickDisguises(level, player, severity, random);
		tickLocalWhispers(level, player, severity, random);
	}

	public static void reset(ClientLevel level) {
		clearAll(level);
		lastDimension = null;
		scareFlashTicks = 0;
	}

	/** 0..1 intensity for the brief screen-edge panic flash when a phantom lunges into the player. */
	public static float scareFlashStrength() {
		return scareFlashTicks / 16.0F;
	}

	/**
	 * Returns a client-only Backrooms monster synced to {@code remote}'s pose if the local player is
	 * currently hallucinating that teammate as a monster; otherwise returns {@code null}.
	 */
	public static LivingEntity disguiseFor(RemotePlayer remote) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer local = client.player;
		if (local == null || client.level == null || local.isSpectator()) return null;
		if (!PlayerRpg.get(local).isHallucinating()) return null;
		DisguiseEntry entry = DISGUISES.get(remote.getUUID());
		if (entry == null || !entry.active || entry.monster == null) return null;
		syncPose(entry.monster, remote);
		return entry.monster;
	}

	private static void tickPhantoms(ClientLevel level, LocalPlayer player, float severity, RandomSource random) {
		// Detect fresh weapon/hand swing on the local player: slashing a phantom dispels it locally
		// while teammates only see the local player slashing empty air.
		boolean swungThisTick = player.swinging && player.swingTime == 1 && lastHandledSwingTick != player.tickCount;
		if (swungThisTick) {
			lastHandledSwingTick = player.tickCount;
		}

		Vec3 eyePos = player.getEyePosition();
		Vec3 look = player.getLookAngle().normalize();

		Iterator<PhantomEntry> it = PHANTOMS.iterator();
		while (it.hasNext()) {
			PhantomEntry entry = it.next();
			Monster mob = entry.monster;
			entry.age++;

			if (mob.isRemoved() || level.getEntity(entry.entityId) == null
					|| entry.age > entry.maxAge
					|| mob.distanceToSqr(player) > 34.0 * 34.0) {
				removePhantom(level, entry, false);
				it.remove();
				continue;
			}

			// Check if local player swung toward this phantom.
			if (swungThisTick) {
				Vec3 toPhantom = mob.getEyePosition().subtract(eyePos);
				double dist = toPhantom.length();
				if (dist <= 5.2 && dist > 1.0E-4 && look.dot(toPhantom.scale(1.0 / dist)) > 0.62) {
					level.playLocalSound(mob.getX(), mob.getEyeY(), mob.getZ(),
							SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 0.75F, 0.55F + random.nextFloat() * 0.2F, false);
					burstSmoke(level, mob.position(), 18, true);
					removePhantom(level, entry, false);
					it.remove();
					continue;
				}
			}

			// Advance phantom smoothly toward the local player.
			Vec3 toPlayer = player.position().subtract(mob.position());
			double horizDist = Math.hypot(toPlayer.x, toPlayer.z);
			if (horizDist <= 1.45 && Math.abs(toPlayer.y) <= 2.2) {
				// Close-range psychological ambush: loud screech + HUD flash, zero real damage.
				scareFlashTicks = 16;
				level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
						SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 0.85F, 0.6F, false);
				burstSmoke(level, mob.position(), 22, true);
				removePhantom(level, entry, false);
				it.remove();
				continue;
			}

			double speed = 0.14 + severity * 0.11;
			double dx = horizDist > 1.0E-4 ? (toPlayer.x / horizDist) * speed : 0.0;
			double dz = horizDist > 1.0E-4 ? (toPlayer.z / horizDist) * speed : 0.0;
			double nextX = mob.getX() + dx;
			double nextZ = mob.getZ() + dz;
			double nextY = resolveFloorY(level, nextX, mob.getY(), nextZ);

			mob.xo = mob.getX();
			mob.yo = mob.getY();
			mob.zo = mob.getZ();
			mob.setPosRaw(nextX, nextY, nextZ);

			float targetYaw = (float) (Mth.atan2(toPlayer.z, toPlayer.x) * Mth.RAD_TO_DEG) - 90.0F;
			mob.yRotO = mob.getYRot();
			mob.yBodyRotO = mob.yBodyRot;
			mob.yHeadRotO = mob.yHeadRot;
			mob.setYRot(targetYaw);
			mob.yBodyRot = targetYaw;
			mob.yHeadRot = targetYaw;
			mob.xRotO = mob.getXRot();
			mob.setXRot(0.0F);
			mob.tickCount++;
			mob.walkAnimation.update((float) Math.min(1.0, speed * 4.2), 0.4F, 1.0F);

			if (entry.age % 35 == 0 && random.nextFloat() < 0.55F) {
				level.playLocalSound(mob.getX(), mob.getEyeY(), mob.getZ(),
						SoundEvents.WARDEN_STEP, SoundSource.HOSTILE, 0.55F, 0.7F, false);
			}
		}

		// Spawn new client-only phantom stalkers as sanity drops below 30%.
		if (--spawnCooldownTicks <= 0) {
			int baseInterval = (int) Mth.lerp(severity, 170.0F, 65.0F);
			spawnCooldownTicks = baseInterval + random.nextInt(55);
			int allowed = severity > 0.45F ? MAX_PHANTOMS : 1;
			if (PHANTOMS.size() < allowed) {
				spawnPhantomNear(level, player, random);
			}
		}
	}

	private static void spawnPhantomNear(ClientLevel level, LocalPlayer player, RandomSource random) {
		Vec3 look = player.getLookAngle();
		double baseAngle = Math.atan2(look.z, look.x);
		Vec3 spawnPos = null;
		for (int attempt = 0; attempt < 18; attempt++) {
			// Prefer peripheral vision or just behind the player so turning around reveals the stalker.
			double offset = attempt < 12
					? (random.nextBoolean() ? 1.0 : -1.0) * (1.25 + random.nextDouble() * 1.85)
					: random.nextDouble() * Math.PI * 2.0;
			double angle = baseAngle + offset;
			double dist = 8.5 + random.nextDouble() * 6.5;
			double tx = player.getX() + Math.cos(angle) * dist;
			double tz = player.getZ() + Math.sin(angle) * dist;
			double ty = findWalkableY(level, (int) Math.floor(tx), player.getBlockY(), (int) Math.floor(tz));
			if (!Double.isNaN(ty)) {
				spawnPos = new Vec3(tx, ty, tz);
				break;
			}
		}
		if (spawnPos == null) return;

		EntityType<? extends Monster> type = STALKER_POOL.get(random.nextInt(STALKER_POOL.size()));
		Monster mob = type.create(level, EntitySpawnReason.EVENT);
		if (mob == null) return;

		int entityId = nextPhantomId--;
		if (nextPhantomId < PHANTOM_ID_BASE - 5000) nextPhantomId = PHANTOM_ID_BASE;
		mob.setId(entityId);
		mob.setNoGravity(true);
		mob.setSilent(true);
		mob.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
		mob.xo = spawnPos.x;
		mob.yo = spawnPos.y;
		mob.zo = spawnPos.z;
		float yaw = (float) (Mth.atan2(player.getZ() - spawnPos.z, player.getX() - spawnPos.x) * Mth.RAD_TO_DEG) - 90.0F;
		mob.setYRot(yaw);
		mob.yRotO = yaw;
		mob.yBodyRot = yaw;
		mob.yBodyRotO = yaw;
		mob.yHeadRot = yaw;
		mob.yHeadRotO = yaw;

		level.addEntity(mob);
		PHANTOMS.add(new PhantomEntry(entityId, mob, 220 + random.nextInt(140)));
		level.playLocalSound(spawnPos.x, spawnPos.y + 1.5, spawnPos.z,
				SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 0.35F, 0.52F, false);
	}

	private static void tickDisguises(ClientLevel level, LocalPlayer local, float severity, RandomSource random) {
		// Remove entries for teammates who left or are no longer loaded.
		DISGUISES.keySet().removeIf(uuid -> level.players().stream().noneMatch(p -> p.getUUID().equals(uuid)));

		for (var abstractPlayer : level.players()) {
			if (!(abstractPlayer instanceof RemotePlayer remote)) continue;
			if (remote.getUUID().equals(local.getUUID()) || remote.isSpectator()) continue;

			DisguiseEntry entry = DISGUISES.computeIfAbsent(remote.getUUID(), id -> new DisguiseEntry());
			if (entry.monster == null || entry.monster.level() != level) {
				EntityType<? extends Monster> type = DISGUISE_POOL.get(random.nextInt(DISGUISE_POOL.size()));
				entry.monster = type.create(level, EntitySpawnReason.EVENT);
				if (entry.monster != null) {
					entry.monster.setNoGravity(true);
					entry.monster.setSilent(true);
				}
			}

			if (--entry.phaseTicks <= 0) {
				if (entry.active) {
					// Brief flicker back to human (very short at very low sanity).
					entry.active = false;
					entry.phaseTicks = (int) Mth.lerp(severity, 55.0F, 12.0F) + random.nextInt(25);
				} else {
					// Swap teammate into a Backrooms monster on this client only!
					if (random.nextFloat() < 0.35F) {
						EntityType<? extends Monster> type = DISGUISE_POOL.get(random.nextInt(DISGUISE_POOL.size()));
						Monster swapped = type.create(level, EntitySpawnReason.EVENT);
						if (swapped != null) {
							swapped.setNoGravity(true);
							swapped.setSilent(true);
							entry.monster = swapped;
						}
					}
					entry.active = true;
					entry.phaseTicks = (int) Mth.lerp(severity, 90.0F, 220.0F) + random.nextInt(60);
					level.playLocalSound(remote.getX(), remote.getEyeY(), remote.getZ(),
							SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.45F, 0.65F, false);
				}
			}
		}
	}

	private static void tickLocalWhispers(ClientLevel level, LocalPlayer player, float severity, RandomSource random) {
		if (--whisperCooldownTicks > 0) return;
		whisperCooldownTicks = (int) Mth.lerp(severity, 130.0F, 48.0F) + random.nextInt(45);
		double angle = random.nextDouble() * Math.PI * 2.0;
		double dist = 3.0 + random.nextDouble() * 4.5;
		double sx = player.getX() + Math.cos(angle) * dist;
		double sz = player.getZ() + Math.sin(angle) * dist;
		level.playLocalSound(sx, player.getEyeY(), sz,
				random.nextBoolean() ? SoundEvents.WARDEN_HEARTBEAT : SoundEvents.SOUL_ESCAPE.value(),
				SoundSource.AMBIENT, 0.38F + severity * 0.25F, 0.55F + random.nextFloat() * 0.2F, false);
	}

	private static void syncPose(Monster mob, RemotePlayer remote) {
		mob.xo = remote.xo;
		mob.yo = remote.yo;
		mob.zo = remote.zo;
		mob.setPosRaw(remote.getX(), remote.getY(), remote.getZ());
		mob.yRotO = remote.yRotO;
		mob.setYRot(remote.getYRot());
		mob.xRotO = remote.xRotO;
		mob.setXRot(remote.getXRot());
		mob.yBodyRotO = remote.yBodyRotO;
		mob.yBodyRot = remote.yBodyRot;
		mob.yHeadRotO = remote.yHeadRotO;
		mob.yHeadRot = remote.yHeadRot;
		mob.tickCount = remote.tickCount;
		mob.hurtTime = remote.hurtTime;
		mob.walkAnimation.setSpeed(remote.walkAnimation.speed());
		if (remote.walkAnimation.isMoving()) {
			mob.walkAnimation.update(remote.walkAnimation.speed(), 0.2F, 1.0F);
		}
	}

	private static double resolveFloorY(ClientLevel level, double x, double currentY, double z) {
		double found = findWalkableY(level, (int) Math.floor(x), (int) Math.floor(currentY), (int) Math.floor(z));
		return Double.isNaN(found) ? currentY : Mth.lerp(0.35, currentY, found);
	}

	private static double findWalkableY(ClientLevel level, int bx, int refY, int bz) {
		for (int dy = 0; Math.abs(dy) <= 3; dy = dy <= 0 ? -dy + 1 : -dy) {
			int floorY = refY - 1 + dy;
			BlockPos floor = new BlockPos(bx, floorY, bz);
			if (!level.hasChunkAt(floor)) return Double.NaN;
			if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) continue;
			BlockPos feet = floor.above();
			BlockPos head = feet.above();
			if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
					&& level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
				return floorY + 1.0;
			}
		}
		return Double.NaN;
	}

	private static void burstSmoke(ClientLevel level, Vec3 pos, int count, boolean soul) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < count; i++) {
			double ox = (random.nextDouble() - 0.5) * 0.9;
			double oy = 0.4 + random.nextDouble() * 1.4;
			double oz = (random.nextDouble() - 0.5) * 0.9;
			level.addParticle(soul && i % 3 == 0 ? ParticleTypes.SOUL : ParticleTypes.LARGE_SMOKE,
					pos.x + ox, pos.y + oy, pos.z + oz,
					(random.nextDouble() - 0.5) * 0.04, 0.03 + random.nextDouble() * 0.04, (random.nextDouble() - 0.5) * 0.04);
		}
	}

	private static void removePhantom(ClientLevel level, PhantomEntry entry, boolean quiet) {
		if (level != null) {
			level.removeEntity(entry.entityId, Entity.RemovalReason.DISCARDED);
		}
		if (!quiet) {
			entry.monster.discard();
		}
	}

	private static void clearAll(ClientLevel level) {
		for (PhantomEntry entry : PHANTOMS) {
			removePhantom(level, entry, false);
		}
		PHANTOMS.clear();
		DISGUISES.clear();
	}

	private static final class PhantomEntry {
		final int entityId;
		final Monster monster;
		final int maxAge;
		int age;

		PhantomEntry(int entityId, Monster monster, int maxAge) {
			this.entityId = entityId;
			this.monster = monster;
			this.maxAge = maxAge;
		}
	}

	private static final class DisguiseEntry {
		Monster monster;
		boolean active;
		int phaseTicks = 12;
	}
}
