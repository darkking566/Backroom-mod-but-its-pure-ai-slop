package dev.noclip.backrooms.level4;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.economy.Economy;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.items.BackroomsItems;
import dev.noclip.backrooms.level1.Level1Items;
import dev.noclip.backrooms.level4.Level4Plan.PlacedExit;
import dev.noclip.backrooms.level4.Level4Plan.RoomBox;
import dev.noclip.backrooms.level4.Level4Plan.ZoneKind;
import dev.noclip.backrooms.level4.Level4PoiPlan.PlacedPoi;
import dev.noclip.backrooms.level4.Level4PoiPlan.PoiKind;
import dev.noclip.backrooms.props.BackroomsProps;
import dev.noclip.backrooms.props.PropBlockEntity;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Interactive events, persistent one-time loot tracking, and ambient effects for Level 4 POIs.
 */
final class Level4PoiEvents {
	private static final int CHALK_COOLDOWN = 20 * 60;
	private static final int MEMO_COOLDOWN = 20 * 60;
	private static final Map<UUID, Integer> CHALK_NEXT = new HashMap<>();
	private static final Map<UUID, Integer> MEMO_NEXT = new HashMap<>();

	private Level4PoiEvents() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			CHALK_NEXT.clear();
			MEMO_NEXT.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(Level4PoiEvents::tick);

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
			int now = sl.getServer().getTickCount();

			// #2 MEG_CHALK_WALL (works on any placed meg_chalk_sign)
			if (state.is(Level4Blocks.MEG_CHALK_SIGN)) {
				boolean slashed = state.getValue(Level4Blocks.MegChalkSignBlock.SLASHED);
				if (slashed) {
					sl.playSound(null, pos, SoundEvents.CHISELED_BOOKSHELF_BREAK, SoundSource.BLOCKS, 0.7F, 0.65F);
					PlayerRpg.drainSanity(sp, 3.0F);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.chalk_slashed"));
				} else {
					int[] omega = Level4Events.nearestOmega(seed, sp.getBlockX(), sp.getBlockZ());
					if (omega != null) {
						int dx = omega[0] - sp.getBlockX(), dz = omega[1] - sp.getBlockZ();
						int dist = (int) Math.round(Math.hypot(dx, dz));
						String dir = Level4Events.compass(dx, dz);
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.chalk_omega", dir, dist));
					} else {
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.chalk_safe"));
					}
					if (now >= CHALK_NEXT.getOrDefault(sp.getUUID(), 0)) {
						CHALK_NEXT.put(sp.getUUID(), now + CHALK_COOLDOWN);
						PlayerRpg.restoreSanity(sp, 5.0F);
					}
					sl.playSound(null, pos, SoundEvents.CALCITE_PLACE, SoundSource.BLOCKS, 0.6F, 1.15F);
				}
				return InteractionResult.SUCCESS;
			}

			// #3 Tear caution tape barrier
			if (state.is(Level4Blocks.CAUTION_TAPE_BARRIER)) {
				sl.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
				if (sl.getBlockState(pos.above()).is(Level4Blocks.CAUTION_TAPE_BARRIER)) {
					sl.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
				}
				if (sl.getBlockState(pos.below()).is(Level4Blocks.CAUTION_TAPE_BARRIER)) {
					sl.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
				}
				sl.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.85F, 1.05F);
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.tape_torn"));
				return InteractionResult.SUCCESS;
			}

			PlacedPoi poi = Level4PoiPlan.atCell(seed, pos.getX(), pos.getZ());
			if (poi == null) return InteractionResult.PASS;
			BlockPos anchor = cellAnchor(pos, poi.baseY());

			switch (poi.kind()) {
				case BREAKROOM_CORNER -> {
					if (!state.is(Blocks.CAULDRON) && !state.is(Blocks.QUARTZ_SLAB)) return InteractionResult.PASS;
					PoiState data = state(sl);
					if (data.isClaimed(anchor)) {
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.breakroom_empty"));
						return InteractionResult.SUCCESS;
					}
					data.markClaimed(anchor);
					RandomSource rng = sl.getRandom();
					giveOrDrop(sp, new ItemStack(Items.GLASS_BOTTLE, 1 + rng.nextInt(2)));
					giveOrDrop(sp, new ItemStack(BackroomsItems.DAMP_PAPER_SCRAP, 1 + rng.nextInt(2)));
					if (rng.nextInt(100) < 18) {
						giveOrDrop(sp, new ItemStack(Level1Items.ALMOND_WATER, 1));
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.breakroom_almond"));
					} else {
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.breakroom_note"));
					}
					sl.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 1.0F);
					return InteractionResult.SUCCESS;
				}
				case SEALED_MEETING_ROOM -> {
					if (!isFoldingTable(sl, pos, state)) return InteractionResult.PASS;
					if (now < MEMO_NEXT.getOrDefault(sp.getUUID(), 0)) {
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.meeting_memo_wait"));
						return InteractionResult.SUCCESS;
					}
					MEMO_NEXT.put(sp.getUUID(), now + MEMO_COOLDOWN);
					int[] clue = nearestExitOrBreaker(seed, sp.getBlockX(), sp.getBlockZ());
					if (clue != null) {
						int dx = clue[0] - sp.getBlockX(), dz = clue[1] - sp.getBlockZ();
						int dist = (int) Math.round(Math.hypot(dx, dz));
						String dir = Level4Events.compass(dx, dz);
						String key = clue[2] == 1
								? "message.noclip_backrooms.level4.poi.meeting_memo_breaker"
								: "message.noclip_backrooms.level4.poi.meeting_memo_exit";
						sp.sendOverlayMessage(Component.translatable(key, dir, dist));
					} else {
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.meeting_memo_empty"));
					}
					sl.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8F, 1.0F);
					return InteractionResult.SUCCESS;
				}
				case CEILING_DRIP_SPOT -> {
					if (!state.is(Blocks.WATER_CAULDRON) && !state.is(Blocks.CAULDRON)) return InteractionResult.PASS;
					ItemStack held = sp.getMainHandItem();
					if (state.is(Blocks.WATER_CAULDRON) && held.is(Items.GLASS_BOTTLE)) {
						if (!sp.getAbilities().instabuild) held.shrink(1);
						giveOrDrop(sp, new ItemStack(BackroomsItems.MURKY_WATER_VIAL));
						int lvl = state.getValue(LayeredCauldronBlock.LEVEL);
						sl.setBlock(pos, lvl > 1
								? state.setValue(LayeredCauldronBlock.LEVEL, lvl - 1)
								: Blocks.CAULDRON.defaultBlockState(), 3);
						sl.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8F, 0.9F);
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.drip_murky"));
					} else if (poi.variantFlag()) {
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.drip_plenum_hint"));
					} else {
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.drip_hint"));
					}
					return InteractionResult.SUCCESS;
				}
				case ACTIVE_TERMINAL_ISLAND -> {
					if (!state.is(BackroomsProps.PROP_DESKTOP_COMPUTER)) return InteractionResult.PASS;
					PoiState data = state(sl);
					if (data.isClaimed(anchor)) {
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.terminal_notice"));
						return InteractionResult.SUCCESS;
					}
					data.markClaimed(anchor);
					RandomSource rng = sl.getRandom();
					ItemStack salvage = switch (rng.nextInt(3)) {
						case 0 -> new ItemStack(BackroomsItems.BALLAST_COIL);
						case 1 -> new ItemStack(BackroomsItems.OLD_CIRCUIT_BOARD);
						default -> new ItemStack(Economy.LOST_COIN);
					};
					giveOrDrop(sp, salvage);
					sl.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.7F, 1.3F);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.terminal_salvage", salvage.getHoverName()));
					return InteractionResult.SUCCESS;
				}
				case WINDOW_PAINT_CART -> {
					if (!state.is(Blocks.BARREL) && !state.is(Blocks.CAULDRON)) return InteractionResult.PASS;
					PoiState data = state(sl);
					if (data.isClaimed(anchor)) {
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.paint_cart_empty"));
						return InteractionResult.SUCCESS;
					}
					data.markClaimed(anchor);
					giveOrDrop(sp, new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("black_dye")), 2));
					giveOrDrop(sp, new ItemStack(Items.CHARCOAL, 1));
					sl.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.75F, 0.9F);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.paint_cart_take"));
					return InteractionResult.SUCCESS;
				}
				case MISSING_WANDERER_CAMP -> {
					if (!state.is(Blocks.BARREL) || pos.getY() != poi.baseY() + 1) return InteractionResult.PASS;
					PoiState data = state(sl);
					if (data.isClaimed(anchor)) {
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.camp_empty"));
						return InteractionResult.SUCCESS;
					}
					data.markClaimed(anchor);
					RandomSource rng = sl.getRandom();
					giveOrDrop(sp, new ItemStack(Level1Items.BANDAGE, 1 + rng.nextInt(2)));
					giveOrDrop(sp, new ItemStack(rng.nextBoolean() ? BackroomsItems.INSULATING_TAPE : BackroomsItems.SCRAP_METAL));
					sl.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.8F, 1.0F);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.camp_loot"));
					return InteractionResult.SUCCESS;
				}
				default -> {
					return InteractionResult.PASS;
				}
			}
		});

		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (!(world instanceof ServerLevel sl) || sl.dimension() != Level4.LEVEL_4) return;
			if (!(player instanceof ServerPlayer sp) || sp.isCreative()) return;
			if (!state.is(Level4Blocks.AMOR_PLANTER)) return;
			long seed = Level4ChunkGenerator.planSeed(sl.getChunkSource().randomState());
			PlacedPoi poi = Level4PoiPlan.atCell(seed, pos.getX(), pos.getZ());
			if (poi != null && poi.kind() == PoiKind.AMOR_FIELD_SHRINE) {
				markAmorDesecrated(sl, sp.getUUID());
				sl.playSound(null, pos, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.45F, 1.25F);
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.poi.amor_shrine_broken"));
			}
		});
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % 25 != 0) return;
		ServerLevel level = server.getLevel(Level4.LEVEL_4);
		if (level == null || level.players().isEmpty()) return;
		long seed = Level4ChunkGenerator.planSeed(level.getChunkSource().randomState());

		for (ServerPlayer sp : level.players()) {
			if (sp.isSpectator()) continue;
			int px = sp.getBlockX(), pz = sp.getBlockZ();
			int gi0 = Math.floorDiv(px - 12, Level4Plan.CELL), gi1 = Math.floorDiv(px + 12, Level4Plan.CELL);
			int gj0 = Math.floorDiv(pz - 12, Level4Plan.CELL), gj1 = Math.floorDiv(pz + 12, Level4Plan.CELL);
			for (int gi = gi0; gi <= gi1; gi++) {
				for (int gj = gj0; gj <= gj1; gj++) {
					int rx = Math.floorDiv(gi, Level4Plan.N), rz = Math.floorDiv(gj, Level4Plan.N);
					int ci = Math.floorMod(gi, Level4Plan.N), cj = Math.floorMod(gj, Level4Plan.N);
					PlacedPoi poi = Level4PoiPlan.of(seed, rx, rz).atCellIndex(Level4Plan.cell(ci, cj));
					if (poi == null || poi.kind() != PoiKind.CEILING_DRIP_SPOT) continue;
					int cx = gi * Level4Plan.CELL + 3, cz = gj * Level4Plan.CELL + 3;
					BlockPos basin = new BlockPos(cx, Level4ChunkGenerator.FLOOR_Y + 1, cz);
					if (!level.isLoaded(basin)) continue;
					if (basin.distSqr(sp.blockPosition()) > 12 * 12) continue;
					BlockState st = level.getBlockState(basin);
					if (!st.is(Blocks.WATER_CAULDRON) && !st.is(Blocks.CAULDRON)) continue;
					level.sendParticles(ParticleTypes.DRIPPING_WATER,
							cx + 0.5, Level4ChunkGenerator.FLOOR_Y + 5.05, cz + 0.5,
							2, 0.08, 0.02, 0.08, 0.0);
					level.playSound(null, basin, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON,
							SoundSource.AMBIENT, 0.4F, 1.05F);
				}
			}
		}
	}

	static boolean isAmorDesecrated(ServerLevel level, UUID playerId) {
		return state(level).isDesecrated(playerId);
	}

	static void markAmorDesecrated(ServerLevel level, UUID playerId) {
		state(level).setDesecrated(playerId, true);
	}

	static void clearAmorDesecrated(ServerLevel level, UUID playerId) {
		state(level).setDesecrated(playerId, false);
	}

	private static PoiState state(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(PoiState.TYPE);
	}

	private static BlockPos cellAnchor(BlockPos pos, int baseY) {
		int cx = Math.floorDiv(pos.getX(), Level4Plan.CELL) * Level4Plan.CELL;
		int cz = Math.floorDiv(pos.getZ(), Level4Plan.CELL) * Level4Plan.CELL;
		return new BlockPos(cx, baseY, cz);
	}

	private static boolean isFoldingTable(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.is(BackroomsProps.PROP_FOLDING_TABLE)) return true;
		if (state.is(BackroomsProps.PROP_PART) && level.getBlockEntity(pos) instanceof PropBlockEntity be) {
			BlockPos master = be.masterPos();
			return master != null && level.getBlockState(master).is(BackroomsProps.PROP_FOLDING_TABLE);
		}
		return false;
	}

	private static int[] nearestExitOrBreaker(long seed, int x, int z) {
		int rx0 = Math.floorDiv(x, Level4Plan.SIZE), rz0 = Math.floorDiv(z, Level4Plan.SIZE);
		int[] best = null;
		double bestDist = Double.MAX_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int rx = rx0 + dx, rz = rz0 + dz;
				Level4Plan plan = Level4Plan.of(seed, rx, rz);
				for (PlacedExit pe : plan.exits) {
					int ex = rx * Level4Plan.SIZE + (pe.cell() % Level4Plan.N) * Level4Plan.CELL + 3;
					int ez = rz * Level4Plan.SIZE + (pe.cell() / Level4Plan.N) * Level4Plan.CELL + 3;
					double d = Math.hypot(ex - x, ez - z);
					if (d < bestDist) {
						bestDist = d;
						best = new int[] {ex, ez, 0};
					}
				}
				for (RoomBox room : plan.rooms) {
					if (room.kind() != ZoneKind.DARK_WING) continue;
					int bx = rx * Level4Plan.SIZE + room.i() * Level4Plan.CELL + 1;
					int bz = rz * Level4Plan.SIZE + room.j() * Level4Plan.CELL + 2;
					double d = Math.hypot(bx - x, bz - z);
					if (d < bestDist) {
						bestDist = d;
						best = new int[] {bx, bz, 1};
					}
				}
			}
		}
		return best;
	}

	private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) player.drop(stack, false);
	}

	static final class PoiState extends SavedData {
		static final Codec<PoiState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.listOf().optionalFieldOf("claimed", List.of()).forGetter(PoiState::claimedList),
				Codec.STRING.listOf().optionalFieldOf("amor_desecrated", List.of()).forGetter(PoiState::desecratedList)
		).apply(instance, PoiState::fromSerialized));

		static final SavedDataType<PoiState> TYPE = new SavedDataType<>(
				BackroomsMod.id("level4_poi_state"),
				PoiState::new,
				CODEC,
				DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

		private final LongOpenHashSet claimed = new LongOpenHashSet();
		private final Set<UUID> amorDesecrated = new HashSet<>();

		boolean isClaimed(BlockPos anchor) {
			return claimed.contains(anchor.asLong());
		}

		void markClaimed(BlockPos anchor) {
			if (claimed.add(anchor.asLong())) setDirty();
		}

		boolean isDesecrated(UUID id) {
			return amorDesecrated.contains(id);
		}

		void setDesecrated(UUID id, boolean value) {
			boolean changed = value ? amorDesecrated.add(id) : amorDesecrated.remove(id);
			if (changed) setDirty();
		}

		private List<String> claimedList() {
			List<String> out = new ArrayList<>(claimed.size());
			LongIterator it = claimed.iterator();
			while (it.hasNext()) out.add(Long.toString(it.nextLong()));
			return out;
		}

		private List<String> desecratedList() {
			List<String> out = new ArrayList<>(amorDesecrated.size());
			for (UUID id : amorDesecrated) out.add(id.toString());
			return out;
		}

		private static PoiState fromSerialized(List<String> rawClaimed, List<String> rawDesecrated) {
			PoiState state = new PoiState();
			for (String s : rawClaimed) {
				try {
					state.claimed.add(Long.parseLong(s));
				} catch (NumberFormatException ignored) {
				}
			}
			for (String s : rawDesecrated) {
				try {
					state.amorDesecrated.add(UUID.fromString(s));
				} catch (IllegalArgumentException ignored) {
				}
			}
			return state;
		}
	}
}
