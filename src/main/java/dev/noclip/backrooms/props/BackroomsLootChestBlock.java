package dev.noclip.backrooms.props;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.LevelGates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Tiered pure-blockstate loot chest (no BlockEntity needed for chunk generation):
 * <ul>
 *   <li>{@link ChestLockTier#ARMORY}: cracked open with a Crowbar or Lockpick Set;</li>
 *   <li>{@link ChestLockTier#ANOMALY_VAULT}: unsealed with a Tier 2+ Keypad Hacker;</li>
 *   <li>{@link ChestLockTier#BOSS_TROPHY}: sealed while a nearby Boss guardian remains alive;</li>
 *   <li>{@link ChestLockTier#NONE}: opens immediately on right-click.</li>
 * </ul>
 */
public final class BackroomsLootChestBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<BackroomsLootChestBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.fieldOf("lock_tier").forGetter(b -> b.lockTier.id()),
			propertiesCodec()
	).apply(instance, (tierId, props) -> new BackroomsLootChestBlock(ChestLockTier.byId(tierId), props)));

	private final ChestLockTier lockTier;

	public BackroomsLootChestBlock(ChestLockTier lockTier, Properties properties) {
		super(properties);
		this.lockTier = lockTier == null ? ChestLockTier.NONE : lockTier;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	public ChestLockTier lockTier() {
		return lockTier;
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return PropShapes.outline(this, state.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return PropShapes.collisionAt(this, state.getValue(FACING), 0, 0, 0);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return tryOpen(level, pos, player, InteractionHand.MAIN_HAND);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		return tryOpen(level, pos, player, hand);
	}

	/** Returns whether {@code stack} is a Tier 2+ Keypad Hacker capable of unsealing Anomaly Vault chests. */
	public static boolean canUnsealAnomalyVault(ItemStack stack) {
		return LevelGates.hackerTier(stack) >= 2;
	}

	/** Returns true if an alive Boss entity currently guards this chest within 24 blocks horizontally. */
	public static boolean hasNearbyBossGuardian(ServerLevel level, BlockPos pos) {
		AABB area = AABB.ofSize(Vec3.atCenterOf(pos), 48.0, 24.0, 48.0);
		return !level.getEntitiesOfClass(Monster.class, area, m -> {
			if (!m.isAlive()) return false;
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(m.getType());
			return m.entityTags().contains(LevelGates.BOSS_ENTITY_TAG)
					|| (BackroomsMod.MOD_ID.equals(id.getNamespace()) && m.getMaxHealth() >= 80.0F);
		}).isEmpty();
	}

	private InteractionResult tryOpen(Level level, BlockPos pos, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;

		if (lockTier != ChestLockTier.NONE && !player.isCreative()) {
			ItemStack held = player.getItemInHand(hand);
			switch (lockTier) {
				case ARMORY -> {
					Item crowbar1 = BuiltInRegistries.ITEM.getValue(BackroomsMod.id("improvised_crowbar"));
					Item crowbar2 = BuiltInRegistries.ITEM.getValue(BackroomsMod.id("crowbar"));
					Item lockpick = BuiltInRegistries.ITEM.getValue(BackroomsMod.id("lockpick_set"));
					boolean hasCrowbar = held.is(crowbar1) || held.is(crowbar2);
					boolean hasLockpick = held.is(lockpick);
					if (!hasCrowbar && !hasLockpick) {
						if (player instanceof ServerPlayer sp) {
							sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.chest.locked"));
						}
						server.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.7F, 0.75F);
						return InteractionResult.CONSUME;
					}
					if (hasLockpick) {
						held.shrink(1);
						server.playSound(null, pos, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 0.65F, 1.4F);
					} else {
						if (held.isDamageableItem()) held.hurtAndBreak(2, player, hand);
						server.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.7F, 1.1F);
					}
				}
				case ANOMALY_VAULT -> {
					if (!canUnsealAnomalyVault(held)) {
						if (player instanceof ServerPlayer sp) {
							sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.chest.need_hacker_t2"));
						}
						server.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.7F, 0.6F);
						return InteractionResult.CONSUME;
					}
					if (held.isDamageableItem()) held.hurtAndBreak(8, player, hand);
					server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F, 1.25F);
					server.playSound(null, pos, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 0.7F, 1.35F);
				}
				case BOSS_TROPHY -> {
					if (hasNearbyBossGuardian(server, pos)) {
						if (player instanceof ServerPlayer sp) {
							sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.chest.boss_guarded"));
						}
						server.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 0.55F);
						return InteractionResult.CONSUME;
					}
					server.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 0.75F, 1.05F);
					server.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.9F, 1.0F);
				}
				default -> server.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.9F, 0.95F);
			}
		} else {
			server.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.9F, 0.95F);
		}

		server.destroyBlock(pos, true, player, 512);
		return InteractionResult.CONSUME;
	}
}
