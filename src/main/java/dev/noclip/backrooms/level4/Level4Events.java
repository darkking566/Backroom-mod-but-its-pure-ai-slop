package dev.noclip.backrooms.level4;

import dev.noclip.backrooms.core.economy.Economy;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.items.BackroomsItems;
import dev.noclip.backrooms.level4.Level4Layout.Kind;
import dev.noclip.backrooms.level4.Level4Plan.RoomBox;
import dev.noclip.backrooms.level4.Level4Plan.ZoneKind;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Quiet psychological micro-events and ambient atmosphere for Level 4:
 * 1. Continuous gentle rain pattering near windows/atriums + 60Hz HVAC hum (with sudden rain-silence omens)
 * 2. Dark-wing breaker panel restoration (switches broken/flickering troffers back to lit)
 * 3. Office photocopier printing wanderer notes / scraps / lost coins
 * 4. Unplugged desk phone ringing with directional Omega Base clues or static whispers
 * 5. Phantom chair scrapes / footsteps over indented office carpet
 * 6. Rain-glass & window silence moments
 */
final class Level4Events {
	private static final int COPIER_COOLDOWN = 20 * 90;
	private static final int BREAKER_COOLDOWN = 20 * 60;
	private static final Map<UUID, Integer> NEXT_AMBIENT = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_PHONE = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_DENT_STEP = new HashMap<>();
	private static final Map<UUID, Integer> RAIN_SILENT_UNTIL = new HashMap<>();
	private static final Map<BlockPos, Integer> COPIER_NEXT = new HashMap<>();
	private static final Map<BlockPos, Integer> BREAKER_NEXT = new HashMap<>();

	private Level4Events() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			NEXT_AMBIENT.clear();
			NEXT_PHONE.clear();
			NEXT_DENT_STEP.clear();
			RAIN_SILENT_UNTIL.clear();
			COPIER_NEXT.clear();
			BREAKER_NEXT.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(Level4Events::tick);

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			if (world.dimension() != Level4.LEVEL_4) return InteractionResult.PASS;
			BlockPos pos = hit.getBlockPos();
			BlockState state = world.getBlockState(pos);
			int now = sp.level().getServer().getTickCount();

			if (state.is(Level4Blocks.OFFICE_BREAKER)) {
				if (now < BREAKER_NEXT.getOrDefault(pos, 0)) {
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.breaker_cooldown"));
					return InteractionResult.SUCCESS;
				}
				BREAKER_NEXT.put(pos.immutable(), now + BREAKER_COOLDOWN);
				int fixed = restoreLights((ServerLevel) world, pos);
				world.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1.0F, 0.85F);
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.breaker_restored", fixed));
				return InteractionResult.SUCCESS;
			}

			if (state.is(Level4Blocks.OFFICE_PHOTOCOPIER)) {
				if (now < COPIER_NEXT.getOrDefault(pos, 0)) {
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.copier_jammed"));
					return InteractionResult.SUCCESS;
				}
				COPIER_NEXT.put(pos.immutable(), now + COPIER_COOLDOWN);
				world.playSound(null, pos, Level4Sounds.PHOTOCOPIER, SoundSource.BLOCKS, 0.85F, 1.0F);
				RandomSource rng = world.getRandom();
				ItemStack printed = switch (rng.nextInt(4)) {
					case 0 -> new ItemStack(BackroomsItems.DAMP_PAPER_SCRAP, 2);
					case 1 -> new ItemStack(Economy.LOST_COIN, 1);
					case 2 -> new ItemStack(Items.PAPER, 2);
					default -> new ItemStack(Items.WRITABLE_BOOK, 1);
				};
				if (!sp.getInventory().add(printed)) sp.drop(printed, false);
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.copier_printed"));
				return InteractionResult.SUCCESS;
			}

			if (state.is(Level4Blocks.OFFICE_DESK_PHONE)) {
				world.playSound(null, pos, Level4Sounds.PHONE_RING, SoundSource.BLOCKS, 0.7F, 1.15F);
				long seed = Level4ChunkGenerator.planSeed(((ServerLevel) world).getChunkSource().randomState());
				int[] omega = nearestOmega(seed, sp.getBlockX(), sp.getBlockZ());
				if (omega != null && world.getRandom().nextInt(100) < 75) {
					int dx = omega[0] - sp.getBlockX(), dz = omega[1] - sp.getBlockZ();
					int dist = (int) Math.round(Math.hypot(dx, dz));
					String dir = compass(dx, dz);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.phone_omega", dir, dist));
				} else {
					PlayerRpg.drainSanity(sp, 6.0F);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.phone_static"));
				}
				return InteractionResult.SUCCESS;
			}

			return InteractionResult.PASS;
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level4.LEVEL_4);
		if (level == null || level.players().isEmpty()) return;
		int now = server.getTickCount();
		RandomSource rng = level.getRandom();
		long seed = Level4ChunkGenerator.planSeed(level.getChunkSource().randomState());

		for (ServerPlayer sp : level.players()) {
			if (sp.isSpectator()) continue;

			// Ambient rain / HVAC hum + sudden rain-silence omen
			if (now >= NEXT_AMBIENT.computeIfAbsent(sp.getUUID(), k -> now + 40 + rng.nextInt(80))) {
				NEXT_AMBIENT.put(sp.getUUID(), now + 90 + rng.nextInt(110));
				if (now >= RAIN_SILENT_UNTIL.getOrDefault(sp.getUUID(), 0)) {
					var tile = Level4Layout.tile(seed, sp.getBlockX(), sp.getBlockZ());
					boolean nearAtrium = tile.zone() == ZoneKind.ATRIUM;
					if (rng.nextInt(100) < (nearAtrium ? 12 : 5)) {
						RAIN_SILENT_UNTIL.put(sp.getUUID(), now + 120);
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.rain_stops"));
					} else {
						double a = rng.nextDouble() * Math.PI * 2;
						double d = 4 + rng.nextDouble() * 8;
						level.playSound(null, sp.getX() + Math.cos(a) * d, sp.getEyeY(), sp.getZ() + Math.sin(a) * d,
								nearAtrium || rng.nextInt(100) < 45 ? Level4Sounds.RAIN : Level4Sounds.HUM,
								SoundSource.AMBIENT, 0.45F, 0.85F + rng.nextFloat() * 0.2F);
					}
				}
			}

			// Phantom footsteps / chair scrape when walking across desk-indented carpet
			if (now % 20 == 0 && !sp.isCreative()) {
				BlockPos under = sp.blockPosition().below();
				if (level.getBlockState(under).is(Level4Blocks.OFFICE_CARPET_DENT)
						&& now >= NEXT_DENT_STEP.getOrDefault(sp.getUUID(), 0)
						&& rng.nextInt(100) < 18) {
					NEXT_DENT_STEP.put(sp.getUUID(), now + 260);
					level.playSound(null, sp.getX() - sp.getLookAngle().x * 3, sp.getY(), sp.getZ() - sp.getLookAngle().z * 3,
							SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.AMBIENT, 0.25F, 1.4F);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.phantom_chair"));
				}
			}

			// Distant phone ring event
			if (now % 40 == 0 && now >= NEXT_PHONE.computeIfAbsent(sp.getUUID(), k -> now + 200)) {
				NEXT_PHONE.put(sp.getUUID(), now + 400 + rng.nextInt(400));
				BlockPos phone = findNearby(level, sp.blockPosition(), Level4Blocks.OFFICE_DESK_PHONE, 14);
				if (phone != null) {
					level.playSound(null, phone, Level4Sounds.PHONE_RING, SoundSource.BLOCKS, 0.75F, 1.1F);
				}
			}
		}
	}

	static int restoreLights(ServerLevel level, BlockPos center) {
		int fixed = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -20; dx <= 20; dx++) {
			for (int dz = -20; dz <= 20; dz++) {
				for (int dy = 1; dy <= 8; dy++) {
					pos.set(center.getX() + dx, Level4ChunkGenerator.FLOOR_Y + dy, center.getZ() + dz);
					BlockState st = level.getBlockState(pos);
					if (st.is(Level4Blocks.OFFICE_TROFFER_BROKEN) || st.is(Level4Blocks.OFFICE_TROFFER_FLICKER)
							|| st.is(Level4Blocks.OFFICE_TROFFER_DIM)) {
						Direction.Axis axis = st.getValue(Level4AxisBlock.AXIS);
						level.setBlock(pos, Level4Blocks.OFFICE_TROFFER_LIT.defaultBlockState().setValue(Level4AxisBlock.AXIS, axis), 3);
						level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
								4, 0.2, 0.1, 0.2, 0.05);
						fixed++;
					}
				}
			}
		}
		return fixed;
	}

	private static BlockPos findNearby(ServerLevel level, BlockPos around, net.minecraft.world.level.block.Block block, int r) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx += 2) {
			for (int dz = -r; dz <= r; dz += 2) {
				pos.set(around.getX() + dx, Level4ChunkGenerator.FLOOR_Y + 1, around.getZ() + dz);
				if (level.isLoaded(pos) && level.getBlockState(pos).is(block)) return pos.immutable();
			}
		}
		return null;
	}

	static int[] nearestOmega(long seed, int x, int z) {
		int rx0 = Math.floorDiv(x, Level4Plan.SIZE), rz0 = Math.floorDiv(z, Level4Plan.SIZE);
		int[] best = null;
		double bestDist = Double.MAX_VALUE;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				Level4Plan plan = Level4Plan.of(seed, rx0 + dx, rz0 + dz);
				for (RoomBox room : plan.rooms) {
					if (room.kind() != ZoneKind.OMEGA_BASE) continue;
					int ox = (rx0 + dx) * Level4Plan.SIZE + room.cx() * Level4Plan.CELL + 3;
					int oz = (rz0 + dz) * Level4Plan.SIZE + room.cz() * Level4Plan.CELL + 3;
					double d = Math.hypot(ox - x, oz - z);
					if (d < bestDist) {
						bestDist = d;
						best = new int[] {ox, oz};
					}
				}
			}
		}
		return best;
	}

	static String compass(int dx, int dz) {
		if (Math.abs(dx) > Math.abs(dz) * 2) return dx > 0 ? "E" : "W";
		if (Math.abs(dz) > Math.abs(dx) * 2) return dz > 0 ? "S" : "N";
		return (dz < 0 ? "N" : "S") + (dx > 0 ? "E" : "W");
	}
}
