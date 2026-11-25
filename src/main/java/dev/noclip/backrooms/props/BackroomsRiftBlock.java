package dev.noclip.backrooms.props;

import com.mojang.serialization.MapCodec;
import dev.noclip.backrooms.core.LevelExits;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Glowing Backrooms Seepage Rift that tears open outdoors near the player's Overworld base once
 * Wretch Rejection reaches Stage 2+. Walking into it or right-clicking lets the player intentionally
 * step back into Level 0 (replacing passive forced teleportation). Closes automatically once the
 * player stabilizes their rejection with Tier 2+ serum or a Reality Anchor Core.
 */
public final class BackroomsRiftBlock extends Block {
	public static final MapCodec<BackroomsRiftBlock> CODEC = simpleCodec(BackroomsRiftBlock::new);
	private static final VoxelShape SHAPE = Block.box(3.0, 1.0, 3.0, 13.0, 15.0, 13.0);
	private static final Map<UUID, Long> LAST_ENTER = new HashMap<>();

	public BackroomsRiftBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			enterRift(sp);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier applier, boolean moving) {
		if (!level.isClientSide() && entity instanceof ServerPlayer sp) {
			enterRift(sp);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		double cx = pos.getX() + 0.5;
		double cy = pos.getY() + 0.6;
		double cz = pos.getZ() + 0.5;
		for (int i = 0; i < 2; i++) {
			level.addParticle(ParticleTypes.PORTAL,
					cx + (random.nextDouble() - 0.5) * 0.8,
					cy + (random.nextDouble() - 0.5) * 1.1,
					cz + (random.nextDouble() - 0.5) * 0.8,
					(random.nextDouble() - 0.5) * 0.15,
					(random.nextDouble() - 0.5) * 0.12,
					(random.nextDouble() - 0.5) * 0.15);
		}
		if (random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.END_ROD,
					cx + (random.nextDouble() - 0.5) * 0.4,
					cy + (random.nextDouble() - 0.5) * 0.8,
					cz + (random.nextDouble() - 0.5) * 0.4,
					0.0, 0.02, 0.0);
		}
	}

	private static void enterRift(ServerPlayer player) {
		if (player.isSpectator() || player.level().dimension() != Level.OVERWORLD) return;
		long now = player.level().getGameTime();
		long last = LAST_ENTER.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
		if (now - last < 40L) return;
		LAST_ENTER.put(player.getUUID(), now);

		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENDERMAN_TELEPORT, SoundSource.AMBIENT, 0.9F, 0.55F);
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.rift.entered").withStyle(ChatFormatting.YELLOW));
		LevelExits.fallInto(player, "0");
	}
}
