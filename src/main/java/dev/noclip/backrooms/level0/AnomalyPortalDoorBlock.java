package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.rpg.PlayerStats;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Multi-theme anomalous exit doors for Level 0 branching exits (Level 499 bowling alley,
 * Level 0.2 remodel mess, Level 193 floating house door, Level 13 glass madness,
 * Level 68 echoing diner door, and Level ! red fake EXIT trap door).
 */
final class AnomalyPortalDoorBlock extends HorizontalDirectionalBlock {
	static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
	static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);

	enum Kind implements StringRepresentable {
		BOWLING("bowling", "499", false),
		REMODEL("remodel", "0.2", false),
		FLOATING("floating", "193", false),
		GLASS("glass", "13", false),
		DINER("diner", "68", false),
		FAKE_EXIT("fake_exit", "!", true);

		final String serialized;
		final String targetKey;
		final boolean immediateTrap;

		Kind(String serialized, String targetKey, boolean immediateTrap) {
			this.serialized = serialized;
			this.targetKey = targetKey;
			this.immediateTrap = immediateTrap;
		}

		@Override
		public String getSerializedName() {
			return serialized;
		}
	}

	private static final Map<UUID, Long> LAST_INSPECT_POS = new HashMap<>();
	private static final Map<UUID, Integer> LAST_INSPECT_TICK = new HashMap<>();
	private static final int CONFIRM_WINDOW_TICKS = 240;

	AnomalyPortalDoorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(FACING, Direction.SOUTH)
				.setValue(HALF, DoubleBlockHalf.LOWER)
				.setValue(KIND, Kind.BOWLING));
	}

	@Override
	protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return simpleCodec(AnomalyPortalDoorBlock::new);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HALF, KIND);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState()
				.setValue(FACING, context.getHorizontalDirection().getOpposite())
				.setValue(HALF, DoubleBlockHalf.LOWER)
				.setValue(KIND, Kind.BOWLING);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.CONSUME;
		}
		Kind kind = state.getValue(KIND);
		BlockPos anchor = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
		int now = serverLevel.getServer().getTickCount();
		UUID id = serverPlayer.getUUID();

		boolean confirmed = kind.immediateTrap || serverPlayer.isShiftKeyDown()
				|| (LAST_INSPECT_POS.getOrDefault(id, Long.MIN_VALUE) == anchor.asLong()
						&& now - LAST_INSPECT_TICK.getOrDefault(id, -1000) <= CONFIRM_WINDOW_TICKS);

		if (!confirmed) {
			LAST_INSPECT_POS.put(id, anchor.asLong());
			LAST_INSPECT_TICK.put(id, now);
			serverLevel.playSound(null, anchor, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 0.45F, 0.85F);
			PlayerStats stats = PlayerRpg.get(serverPlayer);
			boolean keen = stats.sanityStat() >= 3 || stats.level() >= 4;
			String key = "message.noclip_backrooms.level0.exit.door_" + kind.serialized + (keen ? ".keen" : ".clue");
			serverPlayer.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.YELLOW));
			return InteractionResult.SUCCESS;
		}

		LAST_INSPECT_POS.remove(id);
		if (kind == Kind.FAKE_EXIT) {
			serverLevel.playSound(null, anchor, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 0.9F, 0.55F);
			PlayerRpg.drainSanity(serverPlayer, 4.0F);
		} else {
			serverLevel.playSound(null, anchor, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.75F, 0.8F);
		}
		serverLevel.sendParticles(ParticleTypes.PORTAL, anchor.getX() + 0.5, anchor.getY() + 1.0, anchor.getZ() + 0.5,
				18, 0.35, 0.65, 0.35, 0.05);
		LevelExits.use(serverPlayer, kind.targetKey);
		return InteractionResult.SUCCESS;
	}

	static int lightFor(BlockState state) {
		return switch (state.getValue(KIND)) {
			case BOWLING -> 9;
			case FAKE_EXIT -> 10;
			case GLASS -> 4;
			default -> 0;
		};
	}

	static String variantName(Kind kind, DoubleBlockHalf half) {
		return kind.serialized + "_" + half.name().toLowerCase(Locale.ROOT);
	}
}
