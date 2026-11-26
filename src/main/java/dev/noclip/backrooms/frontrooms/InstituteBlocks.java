package dev.noclip.backrooms.frontrooms;

import dev.noclip.backrooms.core.locker.Lockers;
import dev.noclip.backrooms.core.quota.Quota;
import dev.noclip.backrooms.items.BackroomsItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The institute's blocks that do something, or face a way. */
final class InstituteBlocks {
	private InstituteBlocks() {
	}

	/** Anything with a front: lockers, weapon lockers. */
	static class Facing extends Block {
		static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

		Facing(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING);
		}
	}

	/** A personal M.E.G. locker: everyone who opens one finds their own things in it (core.locker.Lockers). */
	static final class Locker extends Facing {
		Locker(Properties properties) {
			super(properties);
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (player instanceof ServerPlayer serverPlayer) {
				Lockers.open(serverPlayer);
				level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.7F, 0.8F);
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** A sign on the quarantine wall, looking into the grounds; it hangs on the wall behind it. */
	static final class Sign extends Facing {
		private static final VoxelShape NORTH = Block.box(1, 2, 15, 15, 14, 16), SOUTH = Block.box(1, 2, 0, 15, 14, 1),
				EAST = Block.box(0, 2, 1, 1, 14, 15), WEST = Block.box(15, 2, 1, 16, 14, 15);

		Sign(Properties properties) {
			super(properties);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return switch (state.getValue(FACING)) {
				case SOUTH -> SOUTH;
				case EAST -> EAST;
				case WEST -> WEST;
				default -> NORTH;
			};
		}
	}

	/** A transport pad in B1: from here M.E.G. sends people down, to the levels the team has been to. */
	static final class Pad extends Block {
		private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

		Pad(Properties properties) {
			super(properties);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
				BlockHitResult hit) {
			if (player instanceof ServerPlayer serverPlayer && player.isShiftKeyDown()
					&& (stack.is(BackroomsItems.KEYPAD_HACKER_T3) || stack.is(BackroomsItems.KEYPAD_HACKER_T4))) {
				dev.noclip.backrooms.core.ending.Endings.tryTrueEscape(serverPlayer, true);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (player instanceof ServerPlayer serverPlayer) Quota.deployOptions(serverPlayer);
			return InteractionResult.SUCCESS;
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (random.nextInt(4) != 0) return;
			level.addParticle(ParticleTypes.PORTAL, pos.getX() + random.nextDouble(), pos.getY() + 0.2, pos.getZ() + random.nextDouble(), 0, 0.3, 0);
		}
	}

	/**
	 * The sealed hatch in B1's floor, over the ladder to the archive in B2. No quota opens it: only a high-grade keypad
	 * hacker (tier 3 or 4) does, and then it stays open for everyone.
	 */
	static final class Hatch extends Block {
		static final BooleanProperty OPEN = BlockStateProperties.OPEN;

		Hatch(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(OPEN, false));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(OPEN);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return state.getValue(OPEN) ? Block.box(0, 0, 0, 16, 1, 16) : Shapes.block();
		}

		@Override
		protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
		}

		@Override
		protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
				BlockHitResult hit) {
			if (state.getValue(OPEN) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
			if (stack.is(BackroomsItems.KEYPAD_HACKER_T3) || stack.is(BackroomsItems.KEYPAD_HACKER_T4)) {
				stack.hurtAndBreak(20, serverPlayer, hand);
				Institute.openArchive((ServerLevel) level);
				level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.6F);
				serverPlayer.sendSystemMessage(Component.translatable("message.noclip_backrooms.institute.archive_opened").withStyle(ChatFormatting.DARK_AQUA));
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (state.getValue(OPEN)) return InteractionResult.PASS;
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.institute.archive_sealed").withStyle(ChatFormatting.RED));
				level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8F, 0.6F);
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** A file in the B2 archive: one page of what M.E.G. kept from its investigators. */
	static final class File extends Block {
		static final int PAGES = 6;

		File(Properties properties) {
			super(properties);
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (player instanceof ServerPlayer serverPlayer) {
				int page = Math.floorMod(pos.getX() * 31 + pos.getZ() * 17, PAGES);
				serverPlayer.sendSystemMessage(Component.translatable("archive.noclip_backrooms.file." + page + ".title")
						.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
				serverPlayer.sendSystemMessage(Component.translatable("archive.noclip_backrooms.file." + page + ".text").withStyle(ChatFormatting.GRAY));
				level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8F, 1.0F);
				dev.noclip.backrooms.core.ending.Endings.recordFile(serverPlayer, page);
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** The live wire along the top of the quarantine wall: it throws back whoever touches it. */
	static final class Fence extends Block {
		Fence(Properties properties) {
			super(properties);
		}

		@Override
		protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
			if (!(level instanceof ServerLevel server) || !(entity instanceof LivingEntity living)) return;
			if (living instanceof Player player && (player.isCreative() || player.isSpectator())) return;
			if (living.hurtServer(server, server.damageSources().lightningBolt(), 3.0F)) {
				Vec3 in = new Vec3(-pos.getX(), 0, -pos.getZ()).normalize().scale(1.2);
				living.setDeltaMovement(in.x, 0.5, in.z);
				living.hurtMarked = true;
				server.playSound(null, pos, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.8F);
				server.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), entity.getY() + 1, entity.getZ(), 12, 0.3, 0.5, 0.3, 0.1);
			}
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (random.nextInt(40) == 0) {
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0, 0, 0);
			}
		}
	}
}
