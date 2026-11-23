package dev.noclip.backrooms;

import com.mojang.math.Transformation;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Level 0's black shadow figure hallucinations and scopophobia cues:
 * - Pitch-black non-mob silhouettes occasionally watch from down a corridor or behind the player.
 * - Looking directly at the silhouette triggers a brief dread jolt before it snaps out of sight.
 */
final class ShadowHallucination {
	private static final int SPAWN_CHECK_INTERVAL = 40;
	private static final int ACTIVE_UPDATE_INTERVAL = 2;
	private static final int MIN_COOLDOWN_TICKS = 1100;
	private static final int MAX_COOLDOWN_TICKS = 2200;
	private static final int MAX_LIFETIME_TICKS = 320;
	private static final int STARE_VANISH_TICKS = 12;
	private static final double MIN_SPAWN_DIST = 12.0;
	private static final double MAX_SPAWN_DIST = 25.0;
	private static final double CLOSE_VANISH_DIST = 6.5;
	private static final double DIRECT_LOOK_DOT = 0.90;

	private static final Map<UUID, ActiveShadow> ACTIVE = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_SPAWN_TICK = new HashMap<>();

	private ShadowHallucination() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ShadowHallucination::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			ServerLevel level0 = server.getLevel(BackroomsMod.LEVEL_0);
			if (level0 != null) discardActive(level0, id);
			NEXT_SPAWN_TICK.remove(id);
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("backrooms")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("shadow")
								.executes(ctx -> spawnCommand(ctx.getSource()))
								.then(Commands.literal("clear").executes(ctx -> clearCommand(ctx.getSource()))))));
	}

	static boolean hasActiveShadow(ServerPlayer player) {
		return ACTIVE.containsKey(player.getUUID());
	}

	private static int spawnCommand(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Must be run by a player."));
			return 0;
		}
		if (player.level().dimension() != BackroomsMod.LEVEL_0) {
			source.sendFailure(Component.literal("You must be inside Level 0."));
			return 0;
		}
		ServerLevel level = player.level();
		discardActive(level, player.getUUID());

		Vec3 spot = findSpotAhead(level, player);
		if (spot == null) {
			spot = findNaturalSpot(level, player, level.getRandom(), false);
		}
		if (spot == null) {
			source.sendFailure(Component.literal("No open corridor spot nearby for a shadow figure."));
			return 0;
		}

		spawnAt(level, player, spot, level.getServer().getTickCount());
		source.sendSuccess(() -> Component.literal("Spawned a shadow figure hallucination nearby."), false);
		return 1;
	}

	private static int clearCommand(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) return 0;
		ServerLevel level0 = source.getServer().getLevel(BackroomsMod.LEVEL_0);
		if (level0 != null) discardActive(level0, player.getUUID());
		source.sendSuccess(() -> Component.literal("Cleared active shadow hallucination."), false);
		return 1;
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		ServerLevel level = server.getLevel(BackroomsMod.LEVEL_0);
		if (level == null) return;

		if (now % ACTIVE_UPDATE_INTERVAL == 0 && !ACTIVE.isEmpty()) {
			for (UUID id : ACTIVE.keySet().toArray(UUID[]::new)) {
				ServerPlayer player = server.getPlayerList().getPlayer(id);
				if (player == null || player.level().dimension() != BackroomsMod.LEVEL_0
						|| player.isSpectator() || !player.isAlive()) {
					discardActive(level, id);
					continue;
				}
				updateActive(level, player, now);
			}
		}

		if (now % SPAWN_CHECK_INTERVAL != 0) return;

		RandomSource random = level.getRandom();
		long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
		for (ServerPlayer player : level.players()) {
			UUID id = player.getUUID();
			if (player.isSpectator() || !player.isAlive() || ACTIVE.containsKey(id)) continue;

			int nextTick = NEXT_SPAWN_TICK.computeIfAbsent(id, k -> now + randomCooldown(random, false));
			if (now < nextTick) continue;

			Maze.Zone zone = Maze.zoneAt(seed, player.getBlockX(), player.getBlockZ());
			boolean eerieZone = zone == Maze.Zone.DARKENED || zone == Maze.Zone.RED_ROOM;
			NEXT_SPAWN_TICK.put(id, now + randomCooldown(random, eerieZone));

			Vec3 spot = findNaturalSpot(level, player, random, true);
			if (spot != null) {
				spawnAt(level, player, spot, now);
			}
		}
	}

	private static int randomCooldown(RandomSource random, boolean faster) {
		int min = faster ? MIN_COOLDOWN_TICKS / 2 : MIN_COOLDOWN_TICKS;
		int max = faster ? MAX_COOLDOWN_TICKS / 2 : MAX_COOLDOWN_TICKS;
		return min + random.nextInt(max - min);
	}

	private static void updateActive(ServerLevel level, ServerPlayer player, int now) {
		UUID id = player.getUUID();
		ActiveShadow shadow = ACTIVE.get(id);
		if (shadow == null) return;

		Entity torso = level.getEntity(shadow.torsoId());
		Entity head = level.getEntity(shadow.headId());
		if (torso == null || head == null || now - shadow.spawnTick() > MAX_LIFETIME_TICKS) {
			discardActive(level, id);
			return;
		}

		double distSq = player.distanceToSqr(shadow.x(), shadow.y(), shadow.z());
		if (distSq < CLOSE_VANISH_DIST * CLOSE_VANISH_DIST) {
			vanish(level, shadow);
			ACTIVE.remove(id);
			return;
		}

		Vec3 playerEye = player.getEyePosition();
		Vec3 shadowEye = new Vec3(shadow.x(), shadow.y() + 1.55, shadow.z());
		Vec3 toShadow = shadowEye.subtract(playerEye);
		double len = toShadow.length();
		double dot = len > 1.0E-4 ? player.getLookAngle().dot(toShadow.scale(1.0 / len)) : 0.0;

		if (dot >= DIRECT_LOOK_DOT && hasClearLineOfSight(level, player, playerEye, shadowEye)) {
			int seen = shadow.seenTicks() + ACTIVE_UPDATE_INTERVAL;
			if (shadow.seenTicks() == 0) {
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 45, 0, true, false, true));
				level.playSound(null, shadow.x(), shadow.y() + 1.5, shadow.z(),
						SoundEvents.ENDERMAN_STARE, SoundSource.AMBIENT, 0.14F, 0.55F);
				if (level.getRandom().nextFloat() < 0.35F) {
					level.playSound(null, shadow.x(), shadow.y() + 1.5, shadow.z(),
							ModSounds.DISTANT_WHISPER, SoundSource.AMBIENT, 0.28F, 0.85F);
				}
			}
			if (seen >= STARE_VANISH_TICKS) {
				vanish(level, shadow);
				ACTIVE.remove(id);
				return;
			}
			ACTIVE.put(id, shadow.withSeenTicks(seen));
		} else if (shadow.seenTicks() == 0 && dot < 0.0 && (now - shadow.spawnTick()) % 44 == 0) {
			// Scopophobia cue while a shadow watches unseen from behind.
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.22F, 0.72F);
		}
	}

	private static boolean hasClearLineOfSight(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
		HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS;
	}

	private static Vec3 findSpotAhead(ServerLevel level, ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		double hx = look.x, hz = look.z;
		double hLen = Math.hypot(hx, hz);
		if (hLen < 1.0E-4) return null;
		hx /= hLen;
		hz /= hLen;

		for (int dist = 10; dist <= 18; dist++) {
			int bx = (int) Math.floor(player.getX() + hx * dist);
			int bz = (int) Math.floor(player.getZ() + hz * dist);
			Vec3 candidate = validStandingSpot(level, bx, bz);
			if (candidate != null && hasClearLineOfSight(level, player, player.getEyePosition(),
					candidate.add(0.0, 1.55, 0.0))) {
				return candidate;
			}
		}
		return null;
	}

	private static Vec3 findNaturalSpot(ServerLevel level, ServerPlayer player, RandomSource random, boolean preferBehind) {
		Vec3 look = player.getLookAngle();
		double lx = look.x, lz = look.z;
		double lLen = Math.hypot(lx, lz);
		if (lLen > 1.0E-4) {
			lx /= lLen;
			lz /= lLen;
		} else {
			lx = 0.0;
			lz = 1.0;
		}

		for (int attempt = 0; attempt < 24; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double dist = MIN_SPAWN_DIST + random.nextDouble() * (MAX_SPAWN_DIST - MIN_SPAWN_DIST);
			double tx = player.getX() + Math.cos(angle) * dist;
			double tz = player.getZ() + Math.sin(angle) * dist;
			double dx = tx - player.getX(), dz = tz - player.getZ();
			double dot = (dx * lx + dz * lz) / Math.hypot(dx, dz);

			if (preferBehind && attempt < 16 && dot > 0.25) continue;

			Vec3 candidate = validStandingSpot(level, (int) Math.floor(tx), (int) Math.floor(tz));
			if (candidate != null) return candidate;
		}
		return null;
	}

	private static Vec3 validStandingSpot(ServerLevel level, int bx, int bz) {
		BlockPos floorPos = new BlockPos(bx, Level0ChunkGenerator.FLOOR_Y, bz);
		if (!level.hasChunkAt(floorPos)) return null;
		BlockState floor = level.getBlockState(floorPos);
		if (floor.getCollisionShape(level, floorPos).isEmpty() || !floor.getFluidState().isEmpty()) return null;

		for (int y = Level0ChunkGenerator.FLOOR_Y + 1; y <= Level0ChunkGenerator.FLOOR_Y + 2; y++) {
			BlockPos pos = new BlockPos(bx, y, bz);
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return null;
		}
		return new Vec3(bx + 0.5, Level0ChunkGenerator.FLOOR_Y + 1, bz + 0.5);
	}

	private static void spawnAt(ServerLevel level, ServerPlayer player, Vec3 pos, int now) {
		Display.BlockDisplay torso = createSilhouettePart(level, pos,
				new Transformation(new Vector3f(-0.23F, 0.0F, -0.12F), new Quaternionf(),
						new Vector3f(0.46F, 1.55F, 0.24F), new Quaternionf()));
		Display.BlockDisplay head = createSilhouettePart(level, pos,
				new Transformation(new Vector3f(-0.19F, 1.55F, -0.19F), new Quaternionf(),
						new Vector3f(0.38F, 0.38F, 0.38F), new Quaternionf()));
		if (torso == null || head == null) return;

		level.addFreshEntity(torso);
		level.addFreshEntity(head);
		ACTIVE.put(player.getUUID(), new ActiveShadow(torso.getUUID(), head.getUUID(), pos.x, pos.y, pos.z, now, 0));
	}

	private static Display.BlockDisplay createSilhouettePart(ServerLevel level, Vec3 pos, Transformation transform) {
		Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
		CompoundTag tag = new CompoundTag();
		BlockState.CODEC.encodeStart(NbtOps.INSTANCE, Blocks.CONCRETE.black().defaultBlockState())
				.result().ifPresent(encoded -> tag.put(Display.BlockDisplay.TAG_BLOCK_STATE, encoded));
		Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE, transform)
				.result().ifPresent(encoded -> tag.put(Display.TAG_TRANSFORMATION, encoded));
		Brightness.CODEC.encodeStart(NbtOps.INSTANCE, new Brightness(0, 0))
				.result().ifPresent(encoded -> tag.put(Display.TAG_BRIGHTNESS, encoded));
		tag.putString(Display.TAG_BILLBOARD, "vertical");
		tag.putFloat(Display.TAG_SHADOW_RADIUS, 0.0F);
		tag.putFloat(Display.TAG_VIEW_RANGE, 1.0F);

		display.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
		display.setPos(pos.x, pos.y, pos.z);
		return display;
	}

	private static void vanish(ServerLevel level, ActiveShadow shadow) {
		discardEntity(level, shadow.torsoId());
		discardEntity(level, shadow.headId());
		level.sendParticles(ParticleTypes.LARGE_SMOKE, shadow.x(), shadow.y() + 1.1, shadow.z(),
				10, 0.18, 0.45, 0.18, 0.01);
	}

	private static void discardActive(ServerLevel level, UUID playerId) {
		ActiveShadow shadow = ACTIVE.remove(playerId);
		if (shadow == null) return;
		discardEntity(level, shadow.torsoId());
		discardEntity(level, shadow.headId());
	}

	private static void discardEntity(ServerLevel level, UUID entityId) {
		Entity entity = level.getEntity(entityId);
		if (entity != null) entity.discard();
	}

	private record ActiveShadow(UUID torsoId, UUID headId, double x, double y, double z, int spawnTick, int seenTicks) {
		ActiveShadow withSeenTicks(int seen) {
			return new ActiveShadow(torsoId, headId, x, y, z, spawnTick, seen);
		}
	}
}
