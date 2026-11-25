package dev.noclip.backrooms.level1;

import com.mojang.serialization.Codec;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.props.BackroomsProps;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;

/**
 * Interactive events in Level 1 POIs and vignettes:
 * <ul>
 *   <li>{@code industrial_fuse_box}: ends an active blackout, restores nearby broken lights (70%), or trips local power (30%).</li>
 *   <li>{@code abandoned_car} / {@code abandoned_van}: one-time glovebox/trunk scavenging with 25% car-alarm risk.</li>
 *   <li>{@code vending_machine}: trade 1 Alloy for Almond Water (plus a 15% bonus Bandage).</li>
 * </ul>
 */
final class Level1PoiEvents {
	private static final int COOLDOWN_TICKS = 1200;
	private static final int RESTORE_RADIUS_XZ = 24;
	private static final int TRIP_RADIUS_XZ = 16;
	private static final int RADIUS_DOWN = 8;
	private static final int RADIUS_UP = 18;
	private static final int ALARM_RADIUS = 28;

	private Level1PoiEvents() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (hand != InteractionHand.MAIN_HAND || level.isClientSide()) return InteractionResult.PASS;
			if (!(level instanceof ServerLevel serverLevel) || !serverLevel.dimension().equals(Level1.LEVEL_1)) {
				return InteractionResult.PASS;
			}
			if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
			BlockPos pos = hitResult.getBlockPos();
			BlockState target = serverLevel.getBlockState(pos);
			if (target.is(BackroomsProps.INDUSTRIAL_FUSE_BOX)) {
				trigger(serverLevel, serverPlayer, pos, null);
				return InteractionResult.SUCCESS;
			}
			if (target.is(Level1Blocks.ABANDONED_CAR) || target.is(Level1Blocks.ABANDONED_VAN)) {
				scavengeVehicle(serverLevel, serverPlayer, pos, null);
				return InteractionResult.SUCCESS;
			}
			if (target.is(Level1Blocks.VENDING_MACHINE)) {
				useVendingMachine(serverLevel, serverPlayer, pos);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	static boolean scavengeVehicle(ServerLevel level, ServerPlayer player, BlockPos pos, Boolean forceAlarm) {
		VehicleState state = level.getDataStorage().computeIfAbsent(VehicleState.TYPE);
		if (state.isScavenged(pos)) {
			level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.55F, 1.1F);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level1_vehicle.empty"));
			return false;
		}
		state.markScavenged(pos);
		level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.02);

		RandomSource random = level.getRandom();
		boolean alarm = forceAlarm != null ? forceAlarm : random.nextInt(100) < 25;
		if (!alarm) {
			level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.7F, 0.95F);
			level.playSound(null, pos, Level1Sounds.DRAWER, SoundSource.BLOCKS, 0.7F, 1.0F);
			int roll = random.nextInt(100);
			ItemStack loot;
			if (roll < 35) loot = new ItemStack(Level1Items.ALMOND_WATER);
			else if (roll < 60) loot = new ItemStack(Level1Items.ALLOY, 1 + random.nextInt(2));
			else if (roll < 80) loot = new ItemStack(Level1Items.BANDAGE);
			else if (roll < 95) loot = new ItemStack(Level1Items.PHOSPHOR_POWDER, 1 + random.nextInt(2));
			else if (roll < 99) loot = new ItemStack(Level1Items.CROWBAR);
			else loot = new ItemStack(Level1Items.CLIPP_CARD);

			Component name = loot.getHoverName();
			giveOrDrop(player, loot);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level1_vehicle.found", name));
		} else {
			level.playSound(null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 0.9F, 1.4F);
			level.playSound(null, pos, Level1Sounds.RUMBLE, SoundSource.BLOCKS, 0.8F, 1.2F);
			level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.7F, 0.9F);
			giveOrDrop(player, new ItemStack(Level1Items.ALLOY));
			if (!player.isCreative() && !player.isSpectator()) {
				for (Monster monster : level.getEntitiesOfClass(Monster.class, new AABB(pos).inflate(ALARM_RADIUS))) {
					monster.setTarget(player);
				}
			}
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level1_vehicle.alarm"));
		}
		return true;
	}

	static boolean isVehicleScavenged(ServerLevel level, BlockPos pos) {
		return level.getDataStorage().computeIfAbsent(VehicleState.TYPE).isScavenged(pos);
	}

	static boolean useVendingMachine(ServerLevel level, ServerPlayer player, BlockPos pos) {
		ItemStack held = player.getMainHandItem();
		if (!held.is(Level1Items.ALLOY)) {
			level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.7F, 1.1F);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level1_vending.need_alloy"));
			return false;
		}
		if (!player.isCreative()) {
			held.shrink(1);
		}
		level.playSound(null, pos, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 0.8F, 1.0F);
		level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 1.3F);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.25, 0.3, 0.25, 0.04);
		giveOrDrop(player, new ItemStack(Level1Items.ALMOND_WATER));
		if (level.getRandom().nextInt(100) < 15) {
			giveOrDrop(player, new ItemStack(Level1Items.BANDAGE));
		}
		player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level1_vending.dispensed"));
		return true;
	}

	private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			player.drop(stack, false);
		}
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

	static final class VehicleState extends SavedData {
		static final Codec<VehicleState> CODEC = Codec.STRING.listOf()
				.optionalFieldOf("scavenged", List.of())
				.xmap(VehicleState::fromSerialized, VehicleState::toSerialized)
				.codec();

		static final SavedDataType<VehicleState> TYPE = new SavedDataType<>(
				BackroomsMod.id("level1_scavenged_vehicles"),
				VehicleState::new,
				CODEC,
				DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

		private final LongOpenHashSet scavenged = new LongOpenHashSet();

		boolean isScavenged(BlockPos pos) {
			return scavenged.contains(pos.asLong());
		}

		void markScavenged(BlockPos pos) {
			scavenged.add(pos.asLong());
			setDirty();
		}

		private List<String> toSerialized() {
			List<String> out = new ArrayList<>(scavenged.size());
			LongIterator it = scavenged.iterator();
			while (it.hasNext()) {
				out.add(Long.toString(it.nextLong()));
			}
			return out;
		}

		private static VehicleState fromSerialized(List<String> raw) {
			VehicleState state = new VehicleState();
			for (String entry : raw) {
				try {
					state.scavenged.add(Long.parseLong(entry));
				} catch (NumberFormatException ignored) {
				}
			}
			return state;
		}
	}
}
