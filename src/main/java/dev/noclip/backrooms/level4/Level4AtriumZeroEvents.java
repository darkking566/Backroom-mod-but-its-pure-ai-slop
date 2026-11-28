package dev.noclip.backrooms.level4;

import dev.noclip.backrooms.core.economy.Economy;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.items.BackroomsItems;
import dev.noclip.backrooms.level1.Level1Items;
import dev.noclip.backrooms.level4.Level4Plan.ZoneKind;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Interactive events, persistent one-time rewards, waterfall acoustics, and slow sanity recovery
 * inside Level 4's "Atrium Zero" megastructure.
 */
final class Level4AtriumZeroEvents {
	private static final int CAMP_REST_COOLDOWN = 20 * 120;
	private static final Set<UUID> IN_ATRIUM_ZERO = new HashSet<>();
	private static final Map<UUID, Integer> NEXT_CALM = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_WATERFALL_SFX = new HashMap<>();
	private static final Map<UUID, Integer> CAMP_REST_NEXT = new HashMap<>();

	private Level4AtriumZeroEvents() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			IN_ATRIUM_ZERO.clear();
			NEXT_CALM.clear();
			NEXT_WATERFALL_SFX.clear();
			CAMP_REST_NEXT.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(Level4AtriumZeroEvents::tick);

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			if (!(world instanceof ServerLevel sl) || sl.dimension() != Level4.LEVEL_4) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			BlockState state = sl.getBlockState(pos);
			long seed = Level4ChunkGenerator.planSeed(sl.getChunkSource().randomState());
			if (Level4Layout.tile(seed, pos.getX(), pos.getZ()).zone() != ZoneKind.ATRIUM_ZERO) {
				return InteractionResult.PASS;
			}

			// Central observation plaque
			if (state.is(Level4Blocks.ATRIUM_ZERO_PLAQUE)) {
				if (!Level4PoiEvents.isClaimed(sl, pos)) {
					Level4PoiEvents.markClaimed(sl, pos);
					Level4PoiEvents.giveItem(sp, new ItemStack(BackroomsItems.PURIFIED_ALMOND_ESSENCE, 2));
					Level4PoiEvents.giveItem(sp, new ItemStack(Level1Items.ALMOND_WATER, 2));
					PlayerRpg.grantXp(sp, 25);
					PlayerRpg.restoreSanity(sp, 10.0F);
					sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.85F, 1.05F);
					sl.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8F, 1.0F);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.atrium_zero.plaque_claim"));
				} else {
					sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.45F, 1.15F);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.atrium_zero.plaque_log"));
				}
				return InteractionResult.SUCCESS;
			}

			// Upper y=48 survey camp
			if (pos.getY() >= Level4AtriumZero.UPPER_Y) {
				if (state.is(Level4Blocks.MEG_SUPPLY_CRATE)) {
					if (Level4PoiEvents.isClaimed(sl, pos)) {
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.atrium_zero.camp_crate_empty"));
					} else {
						Level4PoiEvents.markClaimed(sl, pos);
						Level4PoiEvents.giveItem(sp, new ItemStack(BackroomsItems.ROUGH_BANDAGE, 2));
						Level4PoiEvents.giveItem(sp, new ItemStack(BackroomsItems.FLASHLIGHT_BATTERY_PACK, 2));
						Level4PoiEvents.giveItem(sp, new ItemStack(Economy.LOST_COIN, 2));
						sl.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.75F, 1.15F);
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.atrium_zero.camp_crate_loot"));
					}
					return InteractionResult.SUCCESS;
				}
				if (state.is(Level4Blocks.OFFICE_STEEL_SAFE)) {
					if (Level4PoiEvents.isClaimed(sl, pos)) {
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.atrium_zero.camp_safe_empty"));
					} else {
						Level4PoiEvents.markClaimed(sl, pos);
						Level4PoiEvents.giveItem(sp, new ItemStack(Level1Items.ALLOY, 2));
						Level4PoiEvents.giveItem(sp, new ItemStack(BackroomsItems.OLD_CIRCUIT_BOARD, 1));
						Level4PoiEvents.giveItem(sp, new ItemStack(Level4Items.RAIN_GLASS_ORB, 1));
						sl.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.85F, 0.9F);
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.atrium_zero.camp_safe_loot"));
					}
					return InteractionResult.SUCCESS;
				}
				if (state.is(Level4Blocks.CAMP_SLEEPING_BAG)) {
					int now = sl.getServer().getTickCount();
					if (now < CAMP_REST_NEXT.getOrDefault(sp.getUUID(), 0)) {
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.atrium_zero.camp_rest_wait"));
						return InteractionResult.SUCCESS;
					}
					CAMP_REST_NEXT.put(sp.getUUID(), now + CAMP_REST_COOLDOWN);
					sp.heal(4.0F);
					PlayerRpg.restoreSanity(sp, 12.0F);
					sl.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.7F, 1.0F);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.atrium_zero.camp_rest"));
					return InteractionResult.SUCCESS;
				}
			}

			// Lower y=18 abyssal catwalk reward platform
			if (pos.getY() <= Level4AtriumZero.LOWER_Y + 2) {
				if (state.is(Level4Blocks.MEG_SUPPLY_CRATE)) {
					if (Level4PoiEvents.isClaimed(sl, pos)) {
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.atrium_zero.catwalk_crate_empty"));
					} else {
						Level4PoiEvents.markClaimed(sl, pos);
						Level4PoiEvents.giveItem(sp, new ItemStack(BackroomsItems.STERILE_BANDAGE, 2));
						Level4PoiEvents.giveItem(sp, new ItemStack(Level1Items.ALMOND_WATER, 2));
						Level4PoiEvents.giveItem(sp, new ItemStack(Level1Items.ALLOY, 2));
						sl.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.75F, 1.15F);
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.atrium_zero.catwalk_crate_loot"));
					}
					return InteractionResult.SUCCESS;
				}
				if (state.is(Level4Blocks.OFFICE_STEEL_SAFE)) {
					if (Level4PoiEvents.isClaimed(sl, pos)) {
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.atrium_zero.catwalk_safe_empty"));
					} else {
						Level4PoiEvents.markClaimed(sl, pos);
						Level4PoiEvents.giveItem(sp, new ItemStack(BackroomsItems.PURIFIED_ALMOND_ESSENCE, 3));
						Level4PoiEvents.giveItem(sp, new ItemStack(Level1Items.ALLOY, 2));
						Level4PoiEvents.giveItem(sp, new ItemStack(BackroomsItems.MICROCHIP, 1));
						Level4PoiEvents.giveItem(sp, new ItemStack(BackroomsItems.HIGH_VOLTAGE_COIL, 1));
						PlayerRpg.grantXp(sp, 40);
						sl.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.9F, 0.85F);
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.atrium_zero.catwalk_safe_loot"));
					}
					return InteractionResult.SUCCESS;
				}
			}

			return InteractionResult.PASS;
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level4.LEVEL_4);
		if (level == null || level.players().isEmpty()) return;
		int now = server.getTickCount();
		long seed = Level4ChunkGenerator.planSeed(level.getChunkSource().randomState());
		RandomSource rng = level.getRandom();

		for (ServerPlayer sp : level.players()) {
			if (sp.isSpectator()) {
				IN_ATRIUM_ZERO.remove(sp.getUUID());
				continue;
			}
			boolean inside = Level4Layout.tile(seed, sp.getBlockX(), sp.getBlockZ()).zone() == ZoneKind.ATRIUM_ZERO;
			if (!inside) {
				IN_ATRIUM_ZERO.remove(sp.getUUID());
				continue;
			}
			if (IN_ATRIUM_ZERO.add(sp.getUUID())) {
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.atrium_zero.enter"));
			}

			if (now >= NEXT_WATERFALL_SFX.computeIfAbsent(sp.getUUID(), k -> now + 10)) {
				NEXT_WATERFALL_SFX.put(sp.getUUID(), now + 26 + rng.nextInt(11));
				int lx = Math.floorMod(sp.getBlockX(), Level4Plan.SIZE);
				int lz = Math.floorMod(sp.getBlockZ(), Level4Plan.SIZE);
				double centerDist = Math.hypot(lx - Level4AtriumZero.CENTER, lz - Level4AtriumZero.CENTER);
				boolean nearFalls = centerDist <= 22.0 || sp.getBlockY() <= Level4AtriumZero.LOWER_Y + 4;
				float waterVol = nearFalls ? 0.95F : 0.70F;
				float waterPitch = 0.55F + rng.nextFloat() * 0.13F;
				float rainVol = nearFalls ? 0.85F : 0.65F;
				float rainPitch = 0.78F + rng.nextFloat() * 0.10F;
				level.playSound(null, sp.getX(), sp.getEyeY() + 1.0, sp.getZ(),
						SoundEvents.WATER_AMBIENT, SoundSource.AMBIENT, waterVol, waterPitch);
				level.playSound(null, sp.getX(), sp.getEyeY() + 2.0, sp.getZ(),
						Level4Sounds.RAIN, SoundSource.AMBIENT, rainVol, rainPitch);
			}

			if (sp.getBlockY() >= Level4AtriumZero.FLOOR_Y && now >= NEXT_CALM.computeIfAbsent(sp.getUUID(), k -> now + 100)) {
				NEXT_CALM.put(sp.getUUID(), now + 100);
				PlayerRpg.restoreSanity(sp, 2.0F);
			}
		}
	}
}
