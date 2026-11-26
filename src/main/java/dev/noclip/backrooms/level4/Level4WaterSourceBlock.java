package dev.noclip.backrooms.level4;

import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.level1.Level1Items;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Almond Water cooler or fountain pool in Level 4: holds up to 2 bottles of Almond Water.
 * Right-click with an empty {@link Items#GLASS_BOTTLE} to fill 1 {@link Level1Items#ALMOND_WATER}
 * (max 2 bottles per source), or right-click empty-handed to take a sip directly.
 */
class Level4WaterSourceBlock extends Block {
	static final IntegerProperty BOTTLES = IntegerProperty.create("bottles", 0, 2);
	static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final VoxelShape COOLER_SHAPE = Block.box(3, 0, 3, 13, 16, 13);
	private static final VoxelShape POOL_FULL = Block.box(0, 0, 0, 16, 12, 16);
	private static final VoxelShape POOL_HALF = Block.box(0, 0, 0, 16, 7, 16);
	private static final VoxelShape POOL_EMPTY = Block.box(0, 0, 0, 16, 2, 16);

	final boolean cooler;

	Level4WaterSourceBlock(Properties properties) {
		super(properties);
		this.cooler = isCooler();
		if (cooler) {
			registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(BOTTLES, 2));
		} else {
			registerDefaultState(stateDefinition.any().setValue(BOTTLES, 2));
		}
	}

	static Level4WaterSourceBlock create(Properties properties, boolean cooler) {
		return cooler ? new Level4WaterSourceBlock(properties) {
			@Override
			boolean isCooler() {
				return true;
			}
		} : new Level4WaterSourceBlock(properties);
	}

	boolean isCooler() {
		return false;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (cooler) return COOLER_SHAPE;
		return switch (state.getValue(BOTTLES)) {
			case 2 -> POOL_FULL;
			case 1 -> POOL_HALF;
			default -> POOL_EMPTY;
		};
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(Items.GLASS_BOTTLE)) return InteractionResult.TRY_WITH_EMPTY_HAND;
		int left = state.getValue(BOTTLES);
		if (left <= 0) {
			if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.source_empty"));
			return InteractionResult.SUCCESS;
		}
		if (!level.isClientSide()) {
			level.setBlock(pos, state.setValue(BOTTLES, left - 1), 3);
			if (!player.getAbilities().instabuild) stack.shrink(1);
			ItemStack filled = new ItemStack(Level1Items.ALMOND_WATER);
			if (!player.getInventory().add(filled)) player.drop(filled, false);
			level.playSound(null, pos, Level4Sounds.WATER_FILL, SoundSource.BLOCKS, 0.85F, 1.0F);
			if (player instanceof ServerPlayer sp) {
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.source_filled", left - 1));
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		int left = state.getValue(BOTTLES);
		if (left <= 0) {
			if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.source_empty"));
			return InteractionResult.SUCCESS;
		}
		if (player instanceof ServerPlayer sp) {
			level.setBlock(pos, state.setValue(BOTTLES, left - 1), 3);
			sp.heal(4.0F);
			sp.removeEffect(MobEffects.NAUSEA);
			sp.removeEffect(MobEffects.DARKNESS);
			sp.removeEffect(MobEffects.BLINDNESS);
			sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0));
			PlayerRpg.restoreSanity(sp, 25.0F);
			level.playSound(null, pos, Level4Sounds.WATER_DRINK, SoundSource.PLAYERS, 0.85F, 1.0F);
			sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.source_sipped", left - 1));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return cooler
				? defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(BOTTLES, 2)
				: defaultBlockState().setValue(BOTTLES, 2);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BOTTLES);
		if (isCooler()) builder.add(FACING);
	}
}
