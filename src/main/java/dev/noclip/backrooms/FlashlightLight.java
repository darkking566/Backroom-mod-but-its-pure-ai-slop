package dev.noclip.backrooms;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Projects temporary vanilla Light blocks along the player's gaze while a lit Flashlight is held,
 * working cleanly in both Vanilla and Iris/Sodium shaders, and clears them when put away or on shutdown.
 */
final class FlashlightLight {
	private static final int UPDATE_INTERVAL_TICKS = 2;
	private static final int BATTERY_DRAIN_TICKS = 20;
	private static final double BEAM_RANGE = 18.0;
	private static final int NEAR_LIGHT_LEVEL = 7;
	private static final int FAR_LIGHT_LEVEL = 14;

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
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			updatePlayerBeam(server, player, now, drainBattery);
		}
	}

	private static void updatePlayerBeam(MinecraftServer server, ServerPlayer player, int now, boolean drainBattery) {
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

		int farLevel = FAR_LIGHT_LEVEL;
		int nearLevel = NEAR_LIGHT_LEVEL;
		// Red Rooms and active shadow stares cause eerie brief flashlight flickers.
		if (level.dimension() == BackroomsMod.LEVEL_0) {
			long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
			boolean unstable = Maze.zoneAt(seed, player.getBlockX(), player.getBlockZ()) == Maze.Zone.RED_ROOM
					|| ShadowHallucination.hasActiveShadow(player);
			if (unstable && level.getRandom().nextInt(18) == 0) {
				farLevel = 3;
				nearLevel = 2;
			}
		}

		BlockPos nearPos = findValidLightPos(level, player.blockPosition().above());
		BlockPos farPos = findBeamTargetPos(level, player);
		if (Objects.equals(farPos, nearPos)) {
			// Single merged spot at max brightness when shining right next to a wall.
			nearPos = null;
		}

		ActiveBeam prev = ACTIVE.get(id);
		if (prev != null && prev.dimension().equals(level.dimension())
				&& Objects.equals(prev.nearPos(), nearPos)
				&& Objects.equals(prev.farPos(), farPos)
				&& prev.nearLevel() == nearLevel
				&& prev.farLevel() == farLevel) {
			return;
		}

		ActiveBeam next = new ActiveBeam(level.dimension(), nearPos, farPos, nearLevel, farLevel);
		ACTIVE.put(id, next);

		if (prev != null) {
			ServerLevel prevLevel = server.getLevel(prev.dimension());
			if (prevLevel != null) {
				if (prev.nearPos() != null && !prev.nearPos().equals(nearPos) && !prev.nearPos().equals(farPos)) {
					removeLightIfUnused(prevLevel, prev.nearPos(), id);
				}
				if (prev.farPos() != null && !prev.farPos().equals(nearPos) && !prev.farPos().equals(farPos)) {
					removeLightIfUnused(prevLevel, prev.farPos(), id);
				}
			}
		}

		if (nearPos != null) placeLight(level, nearPos, nearLevel);
		if (farPos != null) placeLight(level, farPos, farLevel);
	}

	private static ItemStack findHeldLitFlashlight(ServerPlayer player) {
		ItemStack main = player.getMainHandItem();
		if (FlashlightItem.isLit(main)) return main;
		ItemStack off = player.getOffhandItem();
		if (FlashlightItem.isLit(off)) return off;
		return ItemStack.EMPTY;
	}

	private static BlockPos findBeamTargetPos(ServerLevel level, ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(BEAM_RANGE));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));

		Vec3 targetVec = hit.getType() == HitResult.Type.BLOCK
				? hit.getLocation().subtract(look.scale(0.6))
				: eye.add(look.scale(BEAM_RANGE * 0.75));
		BlockPos pos = BlockPos.containing(targetVec);
		BlockPos valid = findValidLightPos(level, pos);
		if (valid != null) return valid;
		if (hit.getType() == HitResult.Type.BLOCK) {
			return findValidLightPos(level, hit.getBlockPos().relative(hit.getDirection()));
		}
		return null;
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
		if (prev.nearPos() != null) removeLightIfUnused(level, prev.nearPos(), playerId);
		if (prev.farPos() != null) removeLightIfUnused(level, prev.farPos(), playerId);
	}

	private static void removeLightIfUnused(ServerLevel level, BlockPos pos, UUID ignoringPlayer) {
		for (var entry : ACTIVE.entrySet()) {
			if (entry.getKey().equals(ignoringPlayer)) continue;
			ActiveBeam other = entry.getValue();
			if (!other.dimension().equals(level.dimension())) continue;
			if (pos.equals(other.nearPos()) || pos.equals(other.farPos())) return;
		}
		if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.LIGHT)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), LightBlock.UPDATE_CLIENTS);
		}
	}

	private record ActiveBeam(ResourceKey<Level> dimension, BlockPos nearPos, BlockPos farPos, int nearLevel, int farLevel) {
	}
}
