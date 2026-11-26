package dev.noclip.backrooms.level4;

import dev.noclip.backrooms.core.rpg.PlayerRpg;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Level 4 Windows: blacked-out windows are safe; untinted bright white windows ({@link Level4Blocks#OFFICE_TRAP_WINDOW})
 * are Entity 2 traps that knock, drag, and injure wanderers who step too close.
 * Players can paint a trap window black using Black Dye, Ink Sac, or Charcoal to neutralize it.
 */
final class Level4Windows {
	private static final double WARN = 5.2, GRAB = 2.3;
	private static final int REACH = 5, GRAB_COOLDOWN = 35, WARN_COOLDOWN = 200;
	private static final float DAMAGE = 4.0F;
	private static final Map<UUID, Integer> NEXT_GRAB = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_WARN = new HashMap<>();

	private Level4Windows() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level4Windows::tick);
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
			if (!world.getBlockState(pos).is(Level4Blocks.OFFICE_TRAP_WINDOW)) return InteractionResult.PASS;
			ItemStack held = sp.getMainHandItem();
			Identifier blackDye = Identifier.withDefaultNamespace("black_dye");
			boolean canPaint = held.is(BuiltInRegistries.ITEM.getValue(blackDye))
					|| held.is(Items.INK_SAC) || held.is(Items.CHARCOAL);
			if (!canPaint) {
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.window_paint_hint"));
				return InteractionResult.SUCCESS;
			}
			if (!sp.getAbilities().instabuild) held.shrink(1);
			// Paint the whole 2-block-tall window column black at once
			for (int dy = -1; dy <= 1; dy++) {
				BlockPos p = pos.offset(0, dy, 0);
				if (world.getBlockState(p).is(Level4Blocks.OFFICE_TRAP_WINDOW)) {
					world.setBlock(p, Level4Blocks.OFFICE_BLACKED_WINDOW.defaultBlockState(), 3);
				}
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
			BlockPos window = nearestTrapWindow(level, player);
			if (window == null) continue;
			Vec3 center = Vec3.atCenterOf(window);
			double dist = player.getEyePosition().distanceTo(center);
			if (dist > WARN) continue;
			if (now >= NEXT_WARN.getOrDefault(player.getUUID(), 0)) {
				NEXT_WARN.put(player.getUUID(), now + WARN_COOLDOWN);
				player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.window_trap"));
				level.playSound(null, window, Level4Sounds.WINDOW_KNOCK, SoundSource.HOSTILE, 0.75F, 0.7F);
			}
			if (dist > GRAB || now < NEXT_GRAB.getOrDefault(player.getUUID(), 0)) continue;
			NEXT_GRAB.put(player.getUUID(), now + GRAB_COOLDOWN);
			player.hurtServer(level, level.damageSources().magic(), DAMAGE);
			player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, true, false, true));
			Vec3 pull = center.subtract(player.position()).multiply(1, 0, 1);
			if (pull.lengthSqr() > 1e-4) {
				pull = pull.normalize().scale(0.42);
				player.push(pull.x, 0.05, pull.z);
				player.hurtMarked = true;
			}
			level.playSound(null, window, Level4Sounds.WINDOW_KNOCK, SoundSource.HOSTILE, 1.0F, 0.45F);
		}
	}

	static BlockPos nearestTrapWindow(ServerLevel level, ServerPlayer player) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = player.getBlockX() - REACH; x <= player.getBlockX() + REACH; x++) {
			for (int z = player.getBlockZ() - REACH; z <= player.getBlockZ() + REACH; z++) {
				for (int y = Level4ChunkGenerator.FLOOR_Y + 2; y <= Level4ChunkGenerator.FLOOR_Y + 7; y++) {
					if (!level.getBlockState(pos.set(x, y, z)).is(Level4Blocks.OFFICE_TRAP_WINDOW)) continue;
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
