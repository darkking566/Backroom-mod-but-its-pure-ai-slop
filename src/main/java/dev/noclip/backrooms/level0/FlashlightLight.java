package dev.noclip.backrooms.level0;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Projects a continuous gradient chain of temporary vanilla Light blocks along the player's gaze
 * (plus subtle airborne dust motes) while a lit Flashlight is held, working in both Vanilla and
 * Iris/Sodium shaders, and cleanly removes all light blocks when turned off, put away, or stopped.
 */
final class FlashlightLight {
	private static final int UPDATE_INTERVAL_TICKS = 2;
	private static final int DUST_INTERVAL_TICKS = 4;
	private static final int BATTERY_DRAIN_TICKS = 20;
	private static final double BEAM_RANGE = 18.0;
	private static final double BEAM_STEP = 3.5;
	private static final int NEAR_LIGHT_LEVEL = 8;
	private static final int FOCUS_LIGHT_LEVEL = 15;
	private static final DustParticleOptions BEAM_DUST = new DustParticleOptions(0xFFF6D6, 0.35F);

	private static final Map<UUID, ActiveBeam> ACTIVE = new HashMap<>();

	private FlashlightLight() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FlashlightLight::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				clearBeam(server, handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (UUID id : ACTIVE.keySet().toArray(UUID[]::new)) {
				clearBeam(server, id);
			}
		});
	}

	static boolean hasActiveBeam(ServerPlayer player) {
		return ACTIVE.containsKey(player.getUUID());
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % UPDATE_INTERVAL_TICKS != 0) return;

		boolean drainBattery = now % BATTERY_DRAIN_TICKS == 0;
		boolean emitDust = now % DUST_INTERVAL_TICKS == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			updatePlayerBeam(server, player, drainBattery, emitDust);
		}
	}

	private static void updatePlayerBeam(MinecraftServer server, ServerPlayer player, boolean drainBattery, boolean emitDust) {
		UUID id = player.getUUID();
		if (player.isSpectator() || !player.isAlive()) {
			clearBeam(server, id);
			return;
		}

		ItemStack stack = findHeldLitFlashlight(player);
		if (stack.isEmpty()) {
			clearBeam(server, id);
			return;
		}

		ServerLevel level = player.level();
		if (drainBattery && !player.isCreative()) {
			int nextDamage = Math.min(stack.getMaxDamage() - 1, stack.getDamageValue() + 1);
			stack.setDamageValue(nextDamage);
			if (!FlashlightItem.hasBattery(stack)) {
				FlashlightItem.setLit(stack, false);
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.PLAYERS, 0.45F, 0.65F);
				clearBeam(server, id);
				return;
			}
		}

		boolean flicker = false;
		// Red Rooms and active shadow stares cause eerie brief flashlight flickers.
		if (level.dimension() == Level0.LEVEL_0) {
			long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
			boolean unstable = Maze.zoneAt(seed, player.getBlockX(), player.getBlockZ()) == Maze.Zone.RED_ROOM
					|| ShadowHallucination.hasActiveShadow(player);
			flicker = unstable && level.getRandom().nextInt(18) == 0;
		}

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(BEAM_RANGE));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		double hitDist = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().distanceTo(eye) : BEAM_RANGE;

		Map<BlockPos, Integer> nextLights = computeBeamLights(level, player, eye, look, hit, hitDist, flicker);
		if (emitDust && !flicker && hitDist > 3.0) {
			emitBeamDust(level, player, eye, look, hitDist, level.getRandom());
		}

		ActiveBeam prev = ACTIVE.get(id);
		if (prev != null && prev.dimension().equals(level.dimension()) && prev.lights().equals(nextLights)) {
			return;
		}

		ACTIVE.put(id, new ActiveBeam(level.dimension(), nextLights));

		if (prev != null) {
			ServerLevel prevLevel = server.getLevel(prev.dimension());
			if (prevLevel != null) {
				for (BlockPos oldPos : prev.lights().keySet()) {
					if (!nextLights.containsKey(oldPos)) {
						removeLightIfUnused(prevLevel, oldPos, id);
					}
				}
			}
		}

		for (var entry : nextLights.entrySet()) {
			placeLight(level, entry.getKey(), entry.getValue());
		}
	}

	private static Map<BlockPos, Integer> computeBeamLights(ServerLevel level, ServerPlayer player,
			Vec3 eye, Vec3 look, BlockHitResult hit, double hitDist, boolean flicker) {
		Map<BlockPos, Integer> lights = new LinkedHashMap<>();

		addLightPoint(lights, findValidLightPos(level, player.blockPosition().above()),
				flicker ? 2 : NEAR_LIGHT_LEVEL);

		// Gradient chain along the corridor so the mid-beam path stays smoothly lit without blowing out.
		for (double d = 4.0; d < hitDist - 1.2; d += BEAM_STEP) {
			BlockPos sample = findValidLightPos(level, BlockPos.containing(eye.add(look.scale(d))));
			int grad = 9 + (int) Math.round(4.0 * (d / BEAM_RANGE));
			addLightPoint(lights, sample, flicker ? 2 : Math.min(13, grad));
		}

		BlockPos focusPos = findFocusPos(level, eye, look, hit);
		addLightPoint(lights, focusPos, flicker ? 3 : FOCUS_LIGHT_LEVEL);
		return lights;
	}

	private static void addLightPoint(Map<BlockPos, Integer> lights, BlockPos pos, int level) {
		if (pos == null) return;
		lights.merge(pos, level, Math::max);
	}

	private static BlockPos findFocusPos(ServerLevel level, Vec3 eye, Vec3 look, BlockHitResult hit) {
		Vec3 targetVec = hit.getType() == HitResult.Type.BLOCK
				? hit.getLocation().subtract(look.scale(0.6))
				: eye.add(look.scale(BEAM_RANGE * 0.85));
		BlockPos valid = findValidLightPos(level, BlockPos.containing(targetVec));
		if (valid != null) return valid;
		if (hit.getType() == HitResult.Type.BLOCK) {
			return findValidLightPos(level, hit.getBlockPos().relative(hit.getDirection()));
		}
		return null;
	}

	private static void emitBeamDust(ServerLevel level, ServerPlayer player,
			Vec3 eye, Vec3 look, double hitDist, RandomSource random) {
		double maxSample = Math.min(12.5, hitDist - 0.6);
		if (maxSample <= 2.4) return;
		double d = 2.2 + random.nextDouble() * (maxSample - 2.2);
		Vec3 mote = eye.add(look.scale(d));
		if (findValidLightPos(level, BlockPos.containing(mote)) == null) return;
		level.sendParticles(player, BEAM_DUST, false, false, mote.x, mote.y, mote.z, 1, 0.14, 0.14, 0.14, 0.0);
	}

	private static ItemStack findHeldLitFlashlight(ServerPlayer player) {
		ItemStack main = player.getMainHandItem();
		if (FlashlightItem.isLit(main)) return main;
		ItemStack off = player.getOffhandItem();
		if (FlashlightItem.isLit(off)) return off;
		return ItemStack.EMPTY;
	}

	private static BlockPos findValidLightPos(ServerLevel level, BlockPos pos) {
		if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) return null;
		BlockState state = level.getBlockState(pos);
		return (state.isAir() || state.is(Blocks.LIGHT)) ? pos : null;
	}

	private static void placeLight(ServerLevel level, BlockPos pos, int lightLevel) {
		BlockState state = level.getBlockState(pos);
		if (!state.isAir() && !state.is(Blocks.LIGHT)) return;
		BlockState target = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lightLevel);
		if (!state.equals(target)) {
			level.setBlock(pos, target, LightBlock.UPDATE_CLIENTS);
		}
	}

	private static void clearBeam(MinecraftServer server, UUID playerId) {
		ActiveBeam prev = ACTIVE.remove(playerId);
		if (prev == null) return;
		ServerLevel level = server.getLevel(prev.dimension());
		if (level == null) return;
		for (BlockPos pos : prev.lights().keySet()) {
			removeLightIfUnused(level, pos, playerId);
		}
	}

	private static void removeLightIfUnused(ServerLevel level, BlockPos pos, UUID ignoringPlayer) {
		for (var entry : ACTIVE.entrySet()) {
			if (entry.getKey().equals(ignoringPlayer)) continue;
			ActiveBeam other = entry.getValue();
			if (other.dimension().equals(level.dimension()) && other.lights().containsKey(pos)) return;
		}
		if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.LIGHT)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), LightBlock.UPDATE_CLIENTS);
		}
	}

	private record ActiveBeam(ResourceKey<Level> dimension, Map<BlockPos, Integer> lights) {
	}
}
