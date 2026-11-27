package dev.noclip.backrooms.core.rpg;

import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.level0.Level0;
import dev.noclip.backrooms.level0p1.Level0p1;
import dev.noclip.backrooms.level1.Level1;
import dev.noclip.backrooms.level2.Level2;
import dev.noclip.backrooms.level3.Level3;
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
import net.minecraft.world.entity.Mob;
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

	private static final List<EntityType<? extends Mob>> LEVEL_0_POOL = List.of(
			Monsters.BACTERIA
	);
	private static final List<EntityType<? extends Mob>> LEVEL_1_POOL = List.of(
			Monsters.SMILER,
			Monsters.HOUND,
			Monsters.DULLER,
			Monsters.SKIN_STEALER
	);
	private static final List<EntityType<? extends Mob>> LEVEL_2_POOL = List.of(
			Monsters.HOUND,
			Monsters.SMILER,
			Monsters.CLUMP,
			Monsters.WRETCH
	);
	private static final List<EntityType<? extends Mob>> LEVEL_3_POOL = List.of(
			Monsters.FACELING,
			Monsters.DEATHMOTH,
			Monsters.HOUND,
			Monsters.SMILER,
			Monsters.CLUMP,
			Monsters.SKIN_STEALER
	);
	private static final List<EntityType<? extends Mob>> REJECTION_POOL = List.of(
			Monsters.WRETCH
	);
	private static final List<EntityType<? extends Mob>> DEFAULT_BACKROOMS_POOL = List.of(
			Monsters.SMILER,
			Monsters.HOUND,
			Monsters.SKIN_STEALER
	);

	private static final List<PhantomEntry> PHANTOMS = new ArrayList<>();
	private static final Map<UUID, DisguiseEntry> DISGUISES = new HashMap<>();

	private static ResourceKey<Level> lastDimension;
	private static int nextPhantomId = PHANTOM_ID_BASE;
	private static int spawnCooldownTicks = 120;
	private static int whisperCooldownTicks = 300;
	private static int globalAudioCooldownTicks = 0;
	private static int scareFlashTicks;
	private static int lastHandledSwingTick = -1;

	private SanityHallucinations() {
	}

	/** Dimension-specific low-sanity monster pool so hallucinations always match the current floor. */
	public static List<EntityType<? extends Mob>> poolFor(ResourceKey<Level> dim, boolean rejectionHallucination) {
		if (dim.equals(Level0.LEVEL_0)) return LEVEL_0_POOL;
		if (dim.equals(Level0p1.LEVEL_0_1)) return List.of();
		if (dim.equals(Level1.LEVEL_1)) return LEVEL_1_POOL;
		if (dim.equals(Level2.LEVEL_2)) return LEVEL_2_POOL;
		if (dim.equals(Level3.LEVEL_3)) return LEVEL_3_POOL;
		if (dim.equals(Home.FRONTROOMS)) return List.of();
		if (Home.inBackrooms(dim)) return DEFAULT_BACKROOMS_POOL;
		return rejectionHallucination ? REJECTION_POOL : List.of();
	}

	/** Called every client tick; spawns/updates/clears client-only hallucinations strictly for the local player. */
	public static void tick(Minecraft client) {
		if (scareFlashTicks > 0) scareFlashTicks--;
		if (globalAudioCooldownTicks > 0) globalAudioCooldownTicks--;

		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null || !player.isAlive() || player.isSpectator()) {
			clearAll(level);
			return;
		}

		if (lastDimension != level.dimension()) {
			clearAll(level);
			lastDimension = level.dimension();
			spawnCooldownTicks = level.dimension().equals(Level0.LEVEL_0) ? 240 : 120;
		}

		PlayerStats stats = PlayerRpg.get(player);
		int overworldRejectionStage = dev.noclip.backrooms.core.trip.PullClient.stage();
		boolean rejectionHallucination = overworldRejectionStage >= 3 && !level.isBrightOutside();
		if (!stats.isHallucinating() && !rejectionHallucination) {
			clearAll(level);
			spawnCooldownTicks = level.dimension().equals(Level0.LEVEL_0) ? 240 : 120;
			whisperCooldownTicks = 300;
			return;
		}

		// Severity scales smoothly from 0.0 (at 30% sanity) to 1.0 (at 0% sanity), or from Overworld Stage 3-4 seepage.
		float sanitySev = stats.isHallucinating()
				? Mth.clamp((PlayerStats.HALLUCINATION_THRESHOLD - stats.sanityRatio()) / PlayerStats.HALLUCINATION_THRESHOLD, 0.0F, 1.0F)
				: 0.0F;
		float rejectSev = rejectionHallucination ? (overworldRejectionStage >= 4 ? 0.85F : 0.45F) : 0.0F;
		float severity = Math.max(sanitySev, rejectSev);
		RandomSource random = level.getRandom();
		List<EntityType<? extends Mob>> pool = poolFor(level.dimension(), rejectionHallucination);

		tickPhantoms(level, player, severity, random, pool);
		tickDisguises(level, player, severity, random, pool);
		tickLocalWhispers(level, player, severity, random);
	}

	public static void reset(ClientLevel level) {
		clearAll(level);
		lastDimension = null;
		scareFlashTicks = 0;
		globalAudioCooldownTicks = 0;
		whisperCooldownTicks = 300;
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

	private static void tickPhantoms(ClientLevel level, LocalPlayer player, float severity, RandomSource random,
			List<EntityType<? extends Mob>> pool) {
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
			Mob mob = entry.monster;
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
							PlayerRpg.SANITY_HALLUCINATION_CUE, SoundSource.AMBIENT, 0.45F, 0.85F + random.nextFloat() * 0.15F, false);
					globalAudioCooldownTicks = Math.max(globalAudioCooldownTicks, 200);
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
				// Close-range psychological ambush: eerie cue + HUD flash, zero real damage.
				scareFlashTicks = 16;
				level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
						PlayerRpg.SANITY_HALLUCINATION_CUE, SoundSource.AMBIENT, 0.55F, 0.72F, false);
				globalAudioCooldownTicks = Math.max(globalAudioCooldownTicks, 240);
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

			if (entry.age % 55 == 0 && random.nextFloat() < 0.35F) {
				level.playLocalSound(mob.getX(), mob.getEyeY(), mob.getZ(),
						SoundEvents.WOOL_STEP, SoundSource.AMBIENT, 0.35F, 0.65F, false);
			}
		}

		if (pool.isEmpty()) return;

		boolean isLevel0 = level.dimension().equals(Level0.LEVEL_0);
		// Spawn new client-only phantom stalkers as sanity drops below 30%.
		if (--spawnCooldownTicks <= 0) {
			if (isLevel0) {
				// Level 0: occasional brief Bacteria apparition only (18-30s interval, max 1)
				spawnCooldownTicks = 360 + random.nextInt(240);
			} else {
				int baseInterval = (int) Mth.lerp(severity, 240.0F, 110.0F);
				spawnCooldownTicks = baseInterval + random.nextInt(80);
			}
			int allowed = isLevel0 ? 1 : (severity > 0.45F ? MAX_PHANTOMS : 1);
			if (PHANTOMS.size() < allowed) {
				spawnPhantomNear(level, player, random, pool, isLevel0);
			}
		}
	}

	private static void spawnPhantomNear(ClientLevel level, LocalPlayer player, RandomSource random,
			List<EntityType<? extends Mob>> pool, boolean briefApparition) {
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

		EntityType<? extends Mob> type = pool.get(random.nextInt(pool.size()));
		Mob mob = type.create(level, EntitySpawnReason.EVENT);
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
		int duration = briefApparition ? (100 + random.nextInt(55)) : (220 + random.nextInt(140));
		PHANTOMS.add(new PhantomEntry(entityId, mob, duration));
		if (globalAudioCooldownTicks <= 0) {
			level.playLocalSound(spawnPos.x, spawnPos.y + 1.5, spawnPos.z,
					PlayerRpg.SANITY_HALLUCINATION_CUE, SoundSource.AMBIENT, 0.34F, 0.82F + random.nextFloat() * 0.16F, false);
			globalAudioCooldownTicks = 240;
		}
	}

	private static void tickDisguises(ClientLevel level, LocalPlayer local, float severity, RandomSource random,
			List<EntityType<? extends Mob>> pool) {
		if (pool.isEmpty()) {
			DISGUISES.clear();
			return;
		}
		// Remove entries for teammates who left or are no longer loaded.
		DISGUISES.keySet().removeIf(uuid -> level.players().stream().noneMatch(p -> p.getUUID().equals(uuid)));

		for (var abstractPlayer : level.players()) {
			if (!(abstractPlayer instanceof RemotePlayer remote)) continue;
			if (remote.getUUID().equals(local.getUUID()) || remote.isSpectator()) continue;

			DisguiseEntry entry = DISGUISES.computeIfAbsent(remote.getUUID(), id -> new DisguiseEntry());
			if (entry.monster == null || entry.monster.level() != level) {
				EntityType<? extends Mob> type = pool.get(random.nextInt(pool.size()));
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
					entry.phaseTicks = (int) Mth.lerp(severity, 80.0F, 25.0F) + random.nextInt(35);
				} else {
					// Swap teammate into a floor-appropriate Backrooms monster on this client only!
					if (random.nextFloat() < 0.35F) {
						EntityType<? extends Mob> type = pool.get(random.nextInt(pool.size()));
						Mob swapped = type.create(level, EntitySpawnReason.EVENT);
						if (swapped != null) {
							swapped.setNoGravity(true);
							swapped.setSilent(true);
							entry.monster = swapped;
						}
					}
					entry.active = true;
					entry.phaseTicks = (int) Mth.lerp(severity, 90.0F, 220.0F) + random.nextInt(60);
					if (globalAudioCooldownTicks <= 0) {
						level.playLocalSound(remote.getX(), remote.getEyeY(), remote.getZ(),
								PlayerRpg.SANITY_HALLUCINATION_CUE, SoundSource.AMBIENT, 0.32F, 0.8F, false);
						globalAudioCooldownTicks = 240;
					}
				}
			}
		}
	}

	private static void tickLocalWhispers(ClientLevel level, LocalPlayer player, float severity, RandomSource random) {
		if (--whisperCooldownTicks > 0) return;
		// Low-frequency eerie ambience (26-54 seconds), respecting global cooldown so cues never overlap.
		whisperCooldownTicks = (int) Mth.lerp(severity, 920.0F, 520.0F) + random.nextInt(160);
		if (globalAudioCooldownTicks > 0) return;
		double angle = random.nextDouble() * Math.PI * 2.0;
		double dist = 4.0 + random.nextDouble() * 5.0;
		double sx = player.getX() + Math.cos(angle) * dist;
		double sz = player.getZ() + Math.sin(angle) * dist;
		level.playLocalSound(sx, player.getEyeY(), sz,
				PlayerRpg.SANITY_LOW_DRONE,
				SoundSource.AMBIENT, 0.26F + severity * 0.16F, 0.88F + random.nextFloat() * 0.18F, false);
		globalAudioCooldownTicks = 240;
	}

	private static void syncPose(Mob mob, RemotePlayer remote) {
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
		final Mob monster;
		final int maxAge;
		int age;

		PhantomEntry(int entityId, Mob monster, int maxAge) {
			this.entityId = entityId;
			this.monster = monster;
			this.maxAge = maxAge;
		}
	}

	private static final class DisguiseEntry {
		Mob monster;
		boolean active;
		int phaseTicks = 12;
	}
}
