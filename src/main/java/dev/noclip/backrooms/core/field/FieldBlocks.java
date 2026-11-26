package dev.noclip.backrooms.core.field;

import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
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
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The blocks the field kit puts down (core.field.FieldKit). */
final class FieldBlocks {
	private FieldBlocks() {
	}

	/** A chalk mark: an arrow, a cross or "EXIT", on a floor or a wall. Space drift may turn its arrow (core.field.Drift). */
	static final class ChalkMark extends Block {
		enum Face implements StringRepresentable {
			FLOOR, WALL;

			@Override
			public String getSerializedName() {
				return name().toLowerCase(java.util.Locale.ROOT);
			}
		}

		enum Symbol implements StringRepresentable {
			ARROW, CROSS, EXIT;

			@Override
			public String getSerializedName() {
				return name().toLowerCase(java.util.Locale.ROOT);
			}
		}

		static final EnumProperty<Face> FACE = EnumProperty.create("face", Face.class);
		static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
		static final EnumProperty<Symbol> SYMBOL = EnumProperty.create("symbol", Symbol.class);
		private static final VoxelShape FLOOR = Block.box(0, 0, 0, 16, 0.5, 16);
		private static final VoxelShape[] WALL = {Block.box(0, 0, 15.5, 16, 16, 16), Block.box(0, 0, 0, 0.5, 16, 16),
				Block.box(0, 0, 0, 16, 16, 0.5), Block.box(15.5, 0, 0, 16, 16, 16)};

		ChalkMark(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(FACE, Face.FLOOR).setValue(FACING, Direction.NORTH).setValue(SYMBOL, Symbol.ARROW));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACE, FACING, SYMBOL);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			// A wall mark's model is drawn on the south edge, turned by its facing (south 0, west 1, north 2, east 3).
			return state.getValue(FACE) == Face.FLOOR ? FLOOR : WALL[(state.getValue(FACING).get2DDataValue() + 2) % 4];
		}

		@Override
		protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return Shapes.empty();
		}
	}

	/** A cracked glow stick on the floor: a little green light for ten minutes. */
	static final class GlowStick extends Block {
		static final int LIFE = 12000;
		private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 1, 14);

		GlowStick(Properties properties) {
			super(properties);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return Shapes.empty();
		}

		@Override
		protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
			if (old.is(this)) return;
			level.scheduleTick(pos, this, LIFE);
			if (level instanceof ServerLevel server) Drift.marked(server, pos);
		}

		@Override
		protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			level.removeBlock(pos, false);
		}

		@Override
		protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
			return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
		}
	}

	/** The guide line, laid along the floor as the spool unwinds. */
	static final class RedLine extends Block {
		static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
		private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 0.5, 16);

		RedLine(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(AXIS);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return Shapes.empty();
		}
	}

	/** Planks nailed across a doorway: anything that wants through has to batter it down (core.field.FieldKit#bash). */
	static final class Barricade extends Block {
		static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
		private static final VoxelShape ALONG_X = Block.box(0, 0, 6, 16, 16, 10), ALONG_Z = Block.box(6, 0, 0, 10, 16, 16);

		Barricade(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(AXIS);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			// Across the way the player is looking.
			Direction.Axis look = context.getHorizontalDirection().getAxis();
			return defaultBlockState().setValue(AXIS, look == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return state.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
		}
	}

	/** Wire and empty cans across a corridor: whatever walks through it rattles them, and everyone near hears where. */
	static final class Tripwire extends Block {
		static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
		private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);

		Tripwire(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(AXIS);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			Direction.Axis look = context.getHorizontalDirection().getAxis();
			return defaultBlockState().setValue(AXIS, look == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return Shapes.empty();
		}

		@Override
		protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
			if (!(level instanceof ServerLevel server) || !(entity instanceof LivingEntity) || entity instanceof Player) return;
			FieldKit.trip(server, pos, entity);
		}
	}

	/** A battery lamp: steady light and a steadier mind near it, until its battery dies after three minutes. */
	static final class Lamp extends Block {
		static final BooleanProperty LIT = BlockStateProperties.LIT;
		static final int LIFE = 3600;
		/** Sanity a second for whoever stands in its light. */
		static final float CALM = 1.0F;
		private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 9, 12);

		Lamp(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(LIT, true));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(LIT);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
			if (!old.is(this) && state.getValue(LIT)) level.scheduleTick(pos, this, 20);
		}

		@Override
		protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			if (!state.getValue(LIT)) return;
			int age = FieldKit.lampAge(level, pos, 20);
			if (age >= LIFE) {
				level.setBlockAndUpdate(pos, state.setValue(LIT, false));
				level.playSound(null, pos, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 0.6F, 1.0F);
				return;
			}
			for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(6))) {
				if (Home.inBackrooms(level.dimension())) PlayerRpg.restoreSanity(player, CALM);
			}
			level.scheduleTick(pos, this, 20);
		}
	}

	/** M.E.G.'s mapping beacon: set down far from where the trip came in, it scans for twenty loud seconds. */
	static final class Beacon extends Block {
		private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

		Beacon(Properties properties) {
			super(properties);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (random.nextInt(2) == 0) {
				level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0, 0.05, 0);
			}
		}
	}

	/** For the tripwire's alarm and the beacon: a rough compass direction from one point to another. */
	static Component bearing(BlockPos from, BlockPos to) {
		double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
		int octant = Math.floorMod((int) Math.round(Math.atan2(dx, -dz) / (Math.PI / 4)), 8);
		return Component.translatable("direction.noclip_backrooms." + octant);
	}

	static void warn(ServerLevel level, BlockPos at, Entity what) {
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(at).inflate(48))) {
			int distance = (int) Math.round(Math.sqrt(player.blockPosition().distSqr(at)));
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.field.tripwire", bearing(player.blockPosition(), at), distance)
					.withStyle(ChatFormatting.GOLD));
		}
		level.playSound(null, at, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 1.6F, 1.8F);
		level.playSound(null, at, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.2F, 1.4F);
		if (what instanceof Monster) level.sendParticles(ParticleTypes.CRIT, at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.05);
	}
}
