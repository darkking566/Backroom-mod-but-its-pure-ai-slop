package dev.noclip.backrooms.level1;

import com.mojang.serialization.Codec;
import dev.noclip.backrooms.BackroomsMod;
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
 * Interactive industrial fuse boxes in Level 1 vertical-space POIs.
 * Right-clicking can snap Level 1 out of an active blackout, restore nearby broken garage lights
 * and bare bulbs (70%), or overload the local circuit (30%).
 */
final class Level1PoiEvents {
	private static final int COOLDOWN_TICKS = 1200;
	private static final int RESTORE_RADIUS_XZ = 24;
	private static final int TRIP_RADIUS_XZ = 16;
	private static final int RADIUS_DOWN = 8;
	private static final int RADIUS_UP = 18;

	private Level1PoiEvents() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (hand != InteractionHand.MAIN_HAND || level.isClientSide()) return InteractionResult.PASS;
			if (!(level instanceof ServerLevel serverLevel) || !serverLevel.dimension().equals(Level1.LEVEL_1)) {
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
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level1_fuse_box.cooldown"));
			return false;
		}
		state.setCooldown(pos, now + COOLDOWN_TICKS);

		if (Blackouts.phase() != Blackouts.Phase.NORMAL && !Boolean.FALSE.equals(forceRestore)) {
			Blackouts.endNow();
			setNearbyLights(level, pos, RESTORE_RADIUS_XZ, true);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
					pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
					22, 0.4, 0.4, 0.4, 0.1);
			level.playSound(null, pos, Level1Sounds.POWER_ON, SoundSource.BLOCKS, 0.85F, 0.95F);
			level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.75F, 0.85F);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level1_fuse_box.blackout_cleared"));
			return true;
		}

		boolean restore = forceRestore != null ? forceRestore : level.getRandom().nextInt(10) < 7;
		if (restore) {
			int lit = setNearbyLights(level, pos, RESTORE_RADIUS_XZ, true);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
					pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
					18, 0.35, 0.35, 0.35, 0.08);
			level.playSound(null, pos, Level1Sounds.POWER_ON, SoundSource.BLOCKS, 0.8F, 0.9F);
			level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.75F, 0.85F);
			player.sendOverlayMessage(lit > 0
					? Component.translatable("message.noclip_backrooms.level1_fuse_box.restored", lit)
					: Component.translatable("message.noclip_backrooms.level1_fuse_box.already_lit"));
		} else {
			setNearbyLights(level, pos, TRIP_RADIUS_XZ, false);
			level.sendParticles(ParticleTypes.SMOKE,
					pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
					16, 0.3, 0.3, 0.3, 0.03);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
					pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
					24, 0.4, 0.4, 0.4, 0.12);
			level.playSound(null, pos, Level1Sounds.POWER_OFF, SoundSource.BLOCKS, 0.85F, 0.7F);
			level.playSound(null, pos, Level1Sounds.RUMBLE, SoundSource.BLOCKS, 0.65F, 0.8F);
			player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, true, false, true));
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level1_fuse_box.tripped"));
		}
		return true;
	}

	static boolean isOnCooldown(ServerLevel level, BlockPos pos) {
		FuseState state = level.getDataStorage().computeIfAbsent(FuseState.TYPE);
		return !state.isReady(pos, level.getServer().getTickCount());
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
						if (target.is(Level1Blocks.BROKEN_GARAGE_LIGHT)) {
							level.setBlockAndUpdate(cursor, Level1Blocks.GARAGE_LIGHT.defaultBlockState()
									.setValue(AxisPlacedBlock.AXIS, target.getValue(AxisPlacedBlock.AXIS))
									.setValue(Level1LampBlock.LIT, true));
							changed++;
						} else if (target.is(Level1Blocks.BROKEN_BARE_BULB)) {
							level.setBlockAndUpdate(cursor, Level1Blocks.BARE_BULB.defaultBlockState()
									.setValue(Level1LampBlock.LIT, true));
							changed++;
						}
					} else {
						if (target.is(Level1Blocks.GARAGE_LIGHT)) {
							level.setBlockAndUpdate(cursor, Level1Blocks.BROKEN_GARAGE_LIGHT.defaultBlockState()
									.setValue(AxisPlacedBlock.AXIS, target.getValue(AxisPlacedBlock.AXIS)));
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
				BackroomsMod.id("level1_fuse_boxes"),
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
