package dev.noclip.backrooms.level0p1;

import com.mojang.math.Transformation;
import dev.noclip.backrooms.BackroomsMod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Level 0.1's auditory/visual hallucinations (matching Level 0's isolation effects) and navigation failure:
 * <ul>
 * <li>Shadow figures briefly watch from down a corridor or behind the player (more often in damaged zones or
 * blackouts) and snap out of sight when looked at directly or approached;</li>
 * <li>Phantom footsteps, distant whispers and hull scratches echo behind the player;</li>
 * <li>Navigation tools fail under the singularity's interference: empty maps refuse to chart, and holding a compass,
 * recovery compass, map or clock warns that the needles and readings spin wildly.</li>
 * </ul>
 */
final class ZenithHallucinations {
	private static final int SPAWN_CHECK_INTERVAL = 40;
	private static final int ACTIVE_UPDATE_INTERVAL = 2;
	private static final int MIN_COOLDOWN_TICKS = 1400;
	private static final int MAX_COOLDOWN_TICKS = 2600;
	private static final int MAX_LIFETIME_TICKS = 300;
	private static final int STARE_VANISH_TICKS = 12;
	private static final int AUDIO_MIN_TICKS = 800;
	private static final int AUDIO_MAX_TICKS = 1700;
	private static final int NAV_WARN_COOLDOWN = 20 * 30;
	private static final double MIN_SPAWN_DIST = 12.0;
	private static final double MAX_SPAWN_DIST = 24.0;
	private static final double CLOSE_VANISH_DIST = 6.0;
	private static final double DIRECT_LOOK_DOT = 0.90;

	private static final String TAG = "noclip_backrooms_zenith_shadow";
	private static final Map<UUID, ActiveShadow> ACTIVE = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_SHADOW = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_AUDIO = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_NAV_WARN = new HashMap<>();

	private ZenithHallucinations() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ZenithHallucinations::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG)) entity.discard();
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> clearAll());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ServerLevel level = server.getLevel(Level0p1.LEVEL_0_1);
			if (level != null) for (UUID id : ACTIVE.keySet().toArray(UUID[]::new)) discardActive(level, id);
			clearAll();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			ServerLevel level = server.getLevel(Level0p1.LEVEL_0_1);
			if (level != null) discardActive(level, id);
			NEXT_SHADOW.remove(id);
			NEXT_AUDIO.remove(id);
			NEXT_NAV_WARN.remove(id);
		});
		// Maps cannot chart Level 0.1 under the singularity's spatial distortion.
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.dimension() != Level0p1.LEVEL_0_1) return InteractionResult.PASS;
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(Items.MAP)) return InteractionResult.PASS;
			if (!level.isClientSide() && player instanceof ServerPlayer sp) {
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0p1.nav_map_jammed")
						.withStyle(ChatFormatting.GRAY));
				level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), ZenithSounds.SPARK, SoundSource.AMBIENT, 0.35F, 0.6F);
			}
			return InteractionResult.FAIL;
		});
	}

	private static void clearAll() {
		ACTIVE.clear();
		NEXT_SHADOW.clear();
		NEXT_AUDIO.clear();
		NEXT_NAV_WARN.clear();
	}

	static boolean hasActiveShadow(ServerPlayer player) {
		return ACTIVE.containsKey(player.getUUID());
	}

	/** Whether an item is a navigation tool that fails in Level 0.1. */
	static boolean isNavTool(ItemStack stack) {
		return stack.is(Items.COMPASS) || stack.is(Items.RECOVERY_COMPASS)
				|| stack.is(Items.MAP) || stack.is(Items.FILLED_MAP) || stack.is(Items.CLOCK);
	}

	static boolean triggerNear(ServerLevel level, ServerPlayer player) {
		if (player.isSpectator() || !player.isAlive()) return false;
		UUID id = player.getUUID();
		if (ACTIVE.containsKey(id)) return true;
		Vec3 spot = findSpot(level, player, level.getRandom(), false);
		if (spot == null) return false;
		spawnAt(level, player, spot, level.getServer().getTickCount());
		return true;
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		ServerLevel level = server.getLevel(Level0p1.LEVEL_0_1);
		if (level == null) return;

		if (now % ACTIVE_UPDATE_INTERVAL == 0 && !ACTIVE.isEmpty()) {
			for (UUID id : ACTIVE.keySet().toArray(UUID[]::new)) {
				ServerPlayer player = server.getPlayerList().getPlayer(id);
				if (player == null || player.level().dimension() != Level0p1.LEVEL_0_1
						|| player.isSpectator() || !player.isAlive()) {
					discardActive(level, id);
					continue;
				}
				updateActive(level, player, now);
			}
		}

		if (now % SPAWN_CHECK_INTERVAL != 0) return;

		RandomSource random = level.getRandom();
		long seed = ZenithChunkGenerator.planSeed(level.getChunkSource().randomState());
		for (ServerPlayer player : level.players()) {
			UUID id = player.getUUID();
			if (player.isSpectator() || !player.isAlive()) continue;

			// Navigation interference warning when holding a compass, map or clock.
			if ((isNavTool(player.getMainHandItem()) || isNavTool(player.getOffhandItem()))
					&& now >= NEXT_NAV_WARN.getOrDefault(id, 0)) {
				NEXT_NAV_WARN.put(id, now + NAV_WARN_COOLDOWN);
				player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0p1.nav_jammed")
						.withStyle(ChatFormatting.GRAY));
			}

			boolean eerie = ZenithBlackouts.isBlackout()
					|| ZenithLayout.zoneAt(seed, player.getBlockX(), player.getBlockY(), player.getBlockZ()) != 0;

			// Auditory hallucinations: phantom footsteps, whispers, or metallic scratches behind the player.
			int nextAudio = NEXT_AUDIO.computeIfAbsent(id, k -> now + AUDIO_MIN_TICKS / 2 + random.nextInt(AUDIO_MIN_TICKS));
			if (now >= nextAudio) {
				playAudioHallucination(level, player, random);
				int span = eerie ? (AUDIO_MAX_TICKS - AUDIO_MIN_TICKS) / 2 : (AUDIO_MAX_TICKS - AUDIO_MIN_TICKS);
				NEXT_AUDIO.put(id, now + (eerie ? AUDIO_MIN_TICKS / 2 : AUDIO_MIN_TICKS) + random.nextInt(span));
			}

			if (player.isCreative() || ACTIVE.containsKey(id)) continue;
			int nextShadow = NEXT_SHADOW.computeIfAbsent(id, k -> now + randomCooldown(random, false));
			if (now < nextShadow) continue;
			NEXT_SHADOW.put(id, now + randomCooldown(random, eerie));
			Vec3 spot = findSpot(level, player, random, true);
			if (spot != null) spawnAt(level, player, spot, now);
		}
	}

	private static void playAudioHallucination(ServerLevel level, ServerPlayer player, RandomSource random) {
		Vec3 look = player.getLookAngle();
		double len = Math.hypot(look.x, look.z);
		double bx = len > 1.0E-4 ? -look.x / len : 0.0;
		double bz = len > 1.0E-4 ? -look.z / len : -1.0;
		double dist = 7.0 + random.nextDouble() * 6.0;
		double sx = player.getX() + bx * dist + (random.nextDouble() - 0.5) * 4.0;
		double sz = player.getZ() + bz * dist + (random.nextDouble() - 0.5) * 4.0;
		int roll = random.nextInt(3);
		if (roll == 0) {
			level.playSound(null, sx, player.getY(), sz, SoundEvents.METAL_STEP, SoundSource.AMBIENT, 0.42F, 0.75F + random.nextFloat() * 0.2F);
		} else {
			SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(
					BackroomsMod.id(roll == 1 ? "ambient.distant_whisper" : "ambient.wall_scratch"));
			level.playSound(null, sx, player.getEyeY(), sz, sound != null ? sound : ZenithSounds.CREAK,
					SoundSource.AMBIENT, 0.28F, 0.80F + random.nextFloat() * 0.18F);
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
		if (player.distanceToSqr(shadow.x(), shadow.y(), shadow.z()) < CLOSE_VANISH_DIST * CLOSE_VANISH_DIST) {
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
			}
			if (seen >= STARE_VANISH_TICKS) {
				vanish(level, shadow);
				ACTIVE.remove(id);
				return;
			}
			ACTIVE.put(id, shadow.withSeenTicks(seen));
		} else if (shadow.seenTicks() == 0 && dot < 0.0 && (now - shadow.spawnTick()) % 44 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.22F, 0.72F);
		}
	}

	private static boolean hasClearLineOfSight(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
		HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS;
	}

	private static Vec3 findSpot(ServerLevel level, ServerPlayer player, RandomSource random, boolean preferBehind) {
		Vec3 look = player.getLookAngle();
		double lLen = Math.hypot(look.x, look.z);
		double lx = lLen > 1.0E-4 ? look.x / lLen : 0.0;
		double lz = lLen > 1.0E-4 ? look.z / lLen : 1.0;
		for (int attempt = 0; attempt < 24; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double dist = MIN_SPAWN_DIST + random.nextDouble() * (MAX_SPAWN_DIST - MIN_SPAWN_DIST);
			double tx = player.getX() + Math.cos(angle) * dist;
			double tz = player.getZ() + Math.sin(angle) * dist;
			double dx = tx - player.getX(), dz = tz - player.getZ();
			double dot = (dx * lx + dz * lz) / Math.hypot(dx, dz);
			if (preferBehind && attempt < 16 && dot > 0.25) continue;
			Vec3 candidate = validStandingSpot(level, (int) Math.floor(tx), player.getBlockY(), (int) Math.floor(tz));
			if (candidate != null) return candidate;
		}
		return null;
	}

	private static Vec3 validStandingSpot(ServerLevel level, int bx, int refY, int bz) {
		for (int dy = 0; Math.abs(dy) <= 2; dy = dy <= 0 ? -dy + 1 : -dy) {
			int floorY = refY - 1 + dy;
			BlockPos floorPos = new BlockPos(bx, floorY, bz);
			if (!level.hasChunkAt(floorPos)) return null;
			BlockState floor = level.getBlockState(floorPos);
			if (floor.getCollisionShape(level, floorPos).isEmpty() || !floor.getFluidState().isEmpty()) continue;
			if (level.getBlockState(floorPos.above()).getCollisionShape(level, floorPos.above()).isEmpty()
					&& level.getBlockState(floorPos.above(2)).getCollisionShape(level, floorPos.above(2)).isEmpty()) {
				return new Vec3(bx + 0.5, floorY + 1, bz + 0.5);
			}
		}
		return null;
	}

	private static void spawnAt(ServerLevel level, ServerPlayer player, Vec3 pos, int now) {
		Display.BlockDisplay torso = createPart(level, pos, new Transformation(new Vector3f(-0.23F, 0.0F, -0.12F),
				new Quaternionf(), new Vector3f(0.46F, 1.55F, 0.24F), new Quaternionf()));
		Display.BlockDisplay head = createPart(level, pos, new Transformation(new Vector3f(-0.19F, 1.55F, -0.19F),
				new Quaternionf(), new Vector3f(0.38F, 0.38F, 0.38F), new Quaternionf()));
		if (torso == null || head == null) return;
		level.addFreshEntity(torso);
		level.addFreshEntity(head);
		// Tag after adding, or the ENTITY_LOAD cleanup handler discards them on the spot.
		torso.addTag(TAG);
		head.addTag(TAG);
		ACTIVE.put(player.getUUID(), new ActiveShadow(torso.getUUID(), head.getUUID(), pos.x, pos.y, pos.z, now, 0));
	}

	private static Display.BlockDisplay createPart(ServerLevel level, Vec3 pos, Transformation transform) {
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
		level.sendParticles(ParticleTypes.LARGE_SMOKE, shadow.x(), shadow.y() + 1.1, shadow.z(), 10, 0.18, 0.45, 0.18, 0.01);
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
