package dev.noclip.backrooms.level4;

import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.windowentity.WindowEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Level 4 Windows (Backrooms Wiki): "Most of the windows are completely blacked out. Any windows that are not
 * blacked out are considered traps and should be avoided at all costs."
 * Both {@link Level4Blocks#OFFICE_TRAP_WINDOW} and {@link Level4Blocks#OFFICE_RAIN_WINDOW} are Entity 2 traps that
 * whisper/lure nearby wanderers, drain sanity, and violently grab anyone who steps too close or touches them
 * empty-handed. Players can paint any un-blacked window black with Black Dye, Ink Sac, or Charcoal to neutralize it.
 */
final class Level4Windows {
	private static final double LURE_DIST = 6.5, GRAB_DIST = 2.4;
	private static final int REACH = 6, GRAB_COOLDOWN = 35, WARN_COOLDOWN = 160;
	private static final float DAMAGE = 4.0F;
	private static final Map<UUID, Integer> NEXT_GRAB = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_WARN = new HashMap<>();

	private Level4Windows() {
	}

	static boolean isTrapWindow(BlockState state) {
		return state.is(Level4Blocks.OFFICE_TRAP_WINDOW);
	}

	static boolean isPaintableWindow(BlockState state) {
		return state.is(Level4Blocks.OFFICE_TRAP_WINDOW) || state.is(Level4Blocks.OFFICE_RAIN_WINDOW);
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level4Windows::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(WindowEntity.LEVEL4_WINDOW_TAG)) entity.discard();
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			NEXT_GRAB.clear();
			NEXT_WARN.clear();
		});
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			if (world.dimension() != Level4.LEVEL_4) return InteractionResult.PASS;
			BlockPos pos = hit.getBlockPos();
			BlockState state = world.getBlockState(pos);
			if (!isPaintableWindow(state)) return InteractionResult.PASS;
			ItemStack held = sp.getMainHandItem();
			Identifier blackDye = Identifier.withDefaultNamespace("black_dye");
			boolean canPaint = held.is(BuiltInRegistries.ITEM.getValue(blackDye))
					|| held.is(Items.INK_SAC) || held.is(Items.CHARCOAL);
			if (!canPaint) {
				if (!isTrapWindow(state)) return InteractionResult.PASS;
				if (!sp.isCreative() && !sp.isSpectator()) {
					grabPlayer((ServerLevel) world, sp, pos, true);
				} else {
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.window_paint_hint"));
				}
				return InteractionResult.SUCCESS;
			}
			if (!sp.getAbilities().instabuild) held.shrink(1);
			// Paint the whole connected trap/rain window bay black at once and banish any bound WindowEntity
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					for (int dy = -2; dy <= 2; dy++) {
						BlockPos p = pos.offset(dx, dy, dz);
						if (isPaintableWindow(world.getBlockState(p))) {
							world.setBlock(p, Level4Blocks.OFFICE_BLACKED_WINDOW.defaultBlockState(), 3);
						}
					}
				}
			}
			for (WindowEntity we : ((ServerLevel) world).getEntitiesOfClass(WindowEntity.class, new AABB(pos).inflate(5.5))) {
				we.triggerBanish();
			}
			world.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.85F, 0.8F);
			PlayerRpg.grantXp(sp, 15);
			sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.window_painted"));
			return InteractionResult.SUCCESS;
		});
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % 5 != 0) return;
		ServerLevel level = server.getLevel(Level4.LEVEL_4);
		if (level == null) return;
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator()) continue;
			if (now % 20 == 0) {
				spawnNearbyWindowEntities(level, player);
			}
			BlockPos window = nearestTrapWindow(level, player);
			if (window == null) continue;
			Vec3 center = Vec3.atCenterOf(window);
			double dist = player.getEyePosition().distanceTo(center);
			if (dist > LURE_DIST) continue;

			if (now >= NEXT_WARN.getOrDefault(player.getUUID(), 0)) {
				NEXT_WARN.put(player.getUUID(), now + WARN_COOLDOWN);
				player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.window_lure"));
				level.playSound(null, window, Level4Sounds.WINDOW_KNOCK, SoundSource.HOSTILE, 0.75F, 0.75F);
				PlayerRpg.drainSanity(player, 2.5F);
				level.sendParticles(ParticleTypes.ASH, center.x, center.y, center.z, 6, 0.35, 0.5, 0.35, 0.01);
				Vec3 lurePull = center.subtract(player.position()).multiply(1, 0, 1);
				if (lurePull.lengthSqr() > 1e-4) {
					lurePull = lurePull.normalize().scale(0.10);
					player.push(lurePull.x, 0.0, lurePull.z);
					player.hurtMarked = true;
				}
			}
			if (dist > GRAB_DIST || now < NEXT_GRAB.getOrDefault(player.getUUID(), 0)) continue;
			NEXT_GRAB.put(player.getUUID(), now + GRAB_COOLDOWN);
			grabPlayer(level, player, window, false);
		}
	}

	private static void grabPlayer(ServerLevel level, ServerPlayer player, BlockPos window, boolean onTouch) {
		Vec3 center = Vec3.atCenterOf(window);
		player.hurtServer(level, level.damageSources().magic(), DAMAGE);
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, true, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, true, false, true));
		PlayerRpg.drainSanity(player, onTouch ? 8.0F : 6.0F);
		Vec3 pull = center.subtract(player.position()).multiply(1, 0, 1);
		if (pull.lengthSqr() > 1e-4) {
			pull = pull.normalize().scale(0.45);
			player.push(pull.x, 0.06, pull.z);
			player.hurtMarked = true;
		}
		level.sendParticles(ParticleTypes.SMOKE, center.x, center.y, center.z, 12, 0.3, 0.4, 0.3, 0.02);
		level.playSound(null, window, Level4Sounds.WINDOW_KNOCK, SoundSource.HOSTILE, 1.0F, 0.45F);
		level.playSound(null, window, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.55F, 0.65F);
		for (WindowEntity we : level.getEntitiesOfClass(WindowEntity.class, new AABB(window).inflate(4.5))) {
			we.triggerLunge();
		}
		player.sendOverlayMessage(Component.translatable(
				onTouch ? "message.noclip_backrooms.level4.window_touch_grab"
						: "message.noclip_backrooms.level4.window_grab"));
	}

	static void spawnNearbyWindowEntities(ServerLevel level, ServerPlayer player) {
		// Never clutter corridors/courtyards with a row of WindowEntities; at most one active near the player.
		if (!level.getEntitiesOfClass(WindowEntity.class, player.getBoundingBox().inflate(28.0)).isEmpty()) {
			return;
		}
		long seed = Level4ChunkGenerator.planSeed(level.getChunkSource().randomState());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int y = Math.max(Level4ChunkGenerator.FLOOR_Y + 2, player.getBlockY() + 1);
		int px = player.getBlockX(), pz = player.getBlockZ();
		BlockPos bestPos = null;
		Direction bestOut = null;
		double bestDistSq = 12.5 * 12.5;

		for (int x = px - 12; x <= px + 12; x++) {
			for (int z = pz - 12; z <= pz + 12; z++) {
				int cx = Math.floorMod(x, Level4Plan.CELL);
				int cz = Math.floorMod(z, Level4Plan.CELL);
				if (cx != 3 && cz != 3) continue;
				pos.set(x, y, z);
				if (!level.isLoaded(pos) || !level.getBlockState(pos).is(Level4Blocks.OFFICE_TRAP_WINDOW)) continue;
				Direction outDir = outsideDir(level, seed, pos);
				if (outDir == null) continue;
				Direction inDir = outDir.getOpposite();
				double dx = player.getX() - (x + 0.5);
				double dz = player.getZ() - (z + 0.5);
				// Only manifest when the player is on the indoor side looking toward this window
				if (dx * inDir.getStepX() + dz * inDir.getStepZ() <= 0.35) continue;
				double dSq = dx * dx + dz * dz;
				if (dSq < bestDistSq) {
					bestDistSq = dSq;
					bestPos = pos.immutable();
					bestOut = outDir;
				}
			}
		}
		if (bestPos == null || bestOut == null) return;

		// Tuck slightly deeper into the mist & subtly off-center, low enough to hide floating feet
		double side = ((Math.floorMod(bestPos.getX() * 31 + bestPos.getZ() * 17, 2) == 0) ? 1.0 : -1.0) * 0.22;
		double sx = bestPos.getX() + 0.5 + bestOut.getStepX() * 1.35 + (-bestOut.getStepZ()) * side;
		double sz = bestPos.getZ() + 0.5 + bestOut.getStepZ() * 1.35 + bestOut.getStepX() * side;
		double sy = bestPos.getY() - 1.15;
		WindowEntity entity = Monsters.WINDOW_ENTITY.create(level, EntitySpawnReason.NATURAL);
		if (entity == null) return;
		entity.snapTo(sx, sy, sz, bestOut.getOpposite().toYRot(), 0.0F);
		entity.bindToWindow(bestPos, bestOut.getOpposite());
		level.addFreshEntity(entity);
		entity.addTag(WindowEntity.LEVEL4_WINDOW_TAG);
	}

	private static Direction outsideDir(ServerLevel level, long seed, BlockPos pos) {
		for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			BlockPos out = pos.relative(d);
			BlockPos in = pos.relative(d.getOpposite());
			if (level.isLoaded(out) && level.isLoaded(in)
					&& level.getBlockState(out).isAir()
					&& level.getBlockState(in).isAir()
					&& Level4Layout.tile(seed, out.getX(), out.getZ()).kind() == Level4Layout.Kind.OUTSIDE_AIR
					&& Level4Layout.tile(seed, in.getX(), in.getZ()).kind() == Level4Layout.Kind.AIR) {
				return d;
			}
		}
		return null;
	}

	static BlockPos nearestTrapWindow(ServerLevel level, ServerPlayer player) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = player.getBlockX() - REACH; x <= player.getBlockX() + REACH; x++) {
			for (int z = player.getBlockZ() - REACH; z <= player.getBlockZ() + REACH; z++) {
				for (int y = Level4ChunkGenerator.FLOOR_Y + 2; y <= Level4ChunkGenerator.FLOOR_Y + 7; y++) {
					if (!isTrapWindow(level.getBlockState(pos.set(x, y, z)))) continue;
					double d = player.distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
					if (d < bestDist) {
						bestDist = d;
						best = pos.immutable();
					}
				}
			}
		}
		return best;
	}
}
