package dev.noclip.backrooms.level0;

import com.mojang.serialization.Codec;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.level1.Level1Blocks;
import dev.noclip.backrooms.props.BackroomsProps;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Interactive industrial fuse boxes found in Level 0 breaker niches and Chasm outposts.
 * Right-clicking attempts to re-energize nearby dead lamps (70%) or overloads the circuit (30%),
 * blacking out nearby fluorescent lights and attracting a shadow hallucination.
 */
final class FuseBoxEvents {
	private static final int COOLDOWN_TICKS = 1200;
	private static final int RESTORE_RADIUS_XZ = 20;
	private static final int TRIP_RADIUS_XZ = 16;
	private static final int RADIUS_DOWN = 8;
	private static final int RADIUS_UP = 16;

	private FuseBoxEvents() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (hand != InteractionHand.MAIN_HAND || level.isClientSide()) return InteractionResult.PASS;
			if (!(level instanceof ServerLevel serverLevel) || !serverLevel.dimension().equals(Level0.LEVEL_0)) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hitResult.getBlockPos();
			if (!serverLevel.getBlockState(pos).is(BackroomsProps.INDUSTRIAL_FUSE_BOX)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer serverPlayer) {
				trigger(serverLevel, serverPlayer, pos, null);
			}
			return InteractionResult.SUCCESS;
		});
	}

	static boolean trigger(ServerLevel level, ServerPlayer player, BlockPos pos, Boolean forceRestore) {
		FuseState state = level.getDataStorage().computeIfAbsent(FuseState.TYPE);
		int now = level.getServer().getTickCount();
		if (!state.isReady(pos, now)) {
			level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.55F, 1.15F);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.fuse_box.cooldown"));
			return false;
		}
		state.setCooldown(pos, now + COOLDOWN_TICKS);

		boolean restore = forceRestore != null ? forceRestore : level.getRandom().nextInt(10) < 7;
		if (restore) {
			int lit = setNearbyLights(level, pos, RESTORE_RADIUS_XZ, true);
			int unlocked = unlockNearbyUtilityDoors(level, pos, RESTORE_RADIUS_XZ);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
					pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
					18, 0.35, 0.35, 0.35, 0.08);
			level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.75F, 0.85F);
			if (unlocked > 0) {
				level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.85F, 0.78F);
				player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.utility_unlocked"));
			} else {
				player.sendOverlayMessage(lit > 0
						? Component.translatable("message.noclip_backrooms.fuse_box.restored", lit)
						: Component.translatable("message.noclip_backrooms.fuse_box.already_lit"));
			}
			dev.noclip.backrooms.core.story.Missions.onBlockInteract(player, "fuse_box", pos, "0");
		} else {
			setNearbyLights(level, pos, TRIP_RADIUS_XZ, false);
			level.sendParticles(ParticleTypes.SMOKE,
					pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
					16, 0.3, 0.3, 0.3, 0.03);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
					pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
					24, 0.4, 0.4, 0.4, 0.12);
			level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 0.3F, 1.4F);
			level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.75F, 0.6F);
			player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0, true, false, true));
			ShadowHallucination.triggerNear(level, player);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.fuse_box.tripped"));
		}
		return true;
	}

	static boolean isOnCooldown(ServerLevel level, BlockPos pos) {
		FuseState state = level.getDataStorage().computeIfAbsent(FuseState.TYPE);
		return !state.isReady(pos, level.getServer().getTickCount());
	}

	private static int unlockNearbyUtilityDoors(ServerLevel level, BlockPos center, int radiusXz) {
		int opened = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -radiusXz; dx <= radiusXz; dx++) {
			for (int dz = -radiusXz; dz <= radiusXz; dz++) {
				for (int dy = -2; dy <= 4; dy++) {
					cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					BlockState target = level.getBlockState(cursor);
					if (target.is(ModBlocks.LOCKED_UTILITY_DOOR)
							&& !target.getValue(Level0EventBlocks.LockedUtilityDoorBlock.OPEN)) {
						level.setBlockAndUpdate(cursor, target.setValue(Level0EventBlocks.LockedUtilityDoorBlock.OPEN, true));
						opened++;
					}
				}
			}
		}
		return opened;
	}

	private static int setNearbyLights(ServerLevel level, BlockPos center, int radiusXz, boolean turnOn) {
		int changed = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -radiusXz; dx <= radiusXz; dx++) {
			for (int dz = -radiusXz; dz <= radiusXz; dz++) {
				for (int dy = -RADIUS_DOWN; dy <= RADIUS_UP; dy++) {
					cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					BlockState target = level.getBlockState(cursor);
					if (turnOn) {
						if (target.is(ModBlocks.BROKEN_FLUORESCENT_LIGHT)) {
							level.setBlockAndUpdate(cursor, ModBlocks.FLUORESCENT_LIGHT.defaultBlockState());
							changed++;
						} else if (target.is(Level1Blocks.BROKEN_BARE_BULB)) {
							level.setBlockAndUpdate(cursor, Level1Blocks.BARE_BULB.defaultBlockState());
							changed++;
						}
					} else {
						if (target.is(ModBlocks.FLUORESCENT_LIGHT)) {
							level.setBlockAndUpdate(cursor, ModBlocks.BROKEN_FLUORESCENT_LIGHT.defaultBlockState());
							changed++;
						} else if (target.is(Level1Blocks.BARE_BULB)) {
							level.setBlockAndUpdate(cursor, Level1Blocks.BROKEN_BARE_BULB.defaultBlockState());
							changed++;
						}
					}
				}
			}
		}
		return changed;
	}

	static final class FuseState extends SavedData {
		static final Codec<FuseState> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
				.optionalFieldOf("cooldowns", Map.of())
				.xmap(FuseState::fromSerialized, FuseState::toSerialized)
				.codec();

		static final SavedDataType<FuseState> TYPE = new SavedDataType<>(
				BackroomsMod.id("level0_fuse_boxes"),
				FuseState::new,
				CODEC,
				DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

		private final Long2IntOpenHashMap cooldownUntilTick = new Long2IntOpenHashMap();

		FuseState() {
			cooldownUntilTick.defaultReturnValue(0);
		}

		boolean isReady(BlockPos pos, int now) {
			return now >= cooldownUntilTick.get(pos.asLong());
		}

		void setCooldown(BlockPos pos, int readyAtTick) {
			cooldownUntilTick.put(pos.asLong(), readyAtTick);
			setDirty();
		}

		private Map<String, Integer> toSerialized() {
			Map<String, Integer> out = new HashMap<>(cooldownUntilTick.size());
			for (Long2IntMap.Entry entry : cooldownUntilTick.long2IntEntrySet()) {
				out.put(Long.toString(entry.getLongKey()), entry.getIntValue());
			}
			return out;
		}

		private static FuseState fromSerialized(Map<String, Integer> raw) {
			FuseState state = new FuseState();
			for (var entry : raw.entrySet()) {
				try {
					long key = Long.parseLong(entry.getKey());
					int readyAt = entry.getValue();
					state.cooldownUntilTick.put(key, readyAt);
				} catch (NumberFormatException ignored) {
				}
			}
			return state;
		}
	}
}
