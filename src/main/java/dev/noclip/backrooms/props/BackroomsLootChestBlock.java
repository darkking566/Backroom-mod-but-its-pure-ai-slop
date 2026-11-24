package dev.noclip.backrooms.props;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Pure blockstate loot chest (no BlockEntity needed for chunk generation).
 * Right-clicking cracks it open and drops its loot table; locked armory crates require a Crowbar or Lockpick Set.
 */
public final class BackroomsLootChestBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<BackroomsLootChestBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.BOOL.fieldOf("locked").forGetter(b -> b.locked),
			propertiesCodec()
	).apply(instance, BackroomsLootChestBlock::new));

	private final boolean locked;

	public BackroomsLootChestBlock(boolean locked, Properties properties) {
		super(properties);
		this.locked = locked;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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

	private InteractionResult tryOpen(Level level, BlockPos pos, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;

		if (locked && !player.isCreative()) {
			ItemStack held = player.getItemInHand(hand);
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
		} else {
			server.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.9F, 0.95F);
		}

		server.destroyBlock(pos, true, player, 512);
		return InteractionResult.CONSUME;
	}
}
