package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.rpg.PlayerStats;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Multi-form non-door anomalous exits in Level 0 (poster, wall window, floor steam grate,
 * oil vent hatch, peeling carpet, hollow plaster wall, giant mirror).
 *
 * Interaction algorithm:
 * 1. First right-click inspects the object and shows an atmospheric vague clue (plus an extra
 *    perception detail for players with higher sanityStat / level).
 * 2. Second right-click within 12 seconds (or sneaking right-click, or left-click attack/break
 *    for tear/smash exits like the 1980 Appalachia poster, peeling carpet, and hollow plaster wall)
 *    triggers the crossing via {@link LevelExits#use(ServerPlayer, String)}.
 */
final class AnomalyExitBlock extends HorizontalDirectionalBlock {
	static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

	enum Form {
		POSTER_161("161", "poster_161", true, false, SoundEvents.BOOK_PAGE_TURN),
		WINDOW_188("188", "window_188", false, false, SoundEvents.WOODEN_TRAPDOOR_OPEN),
		STEAM_GRATE_2("2", "steam_grate_2", false, false, SoundEvents.IRON_TRAPDOOR_OPEN),
		OIL_VENT_1("1", "oil_vent_1", false, false, SoundEvents.IRON_TRAPDOOR_OPEN),
		CARPET_27("27", "carpet_27", true, false, SoundEvents.WOOL_BREAK),
		PLASTER_194("194", "plaster_194", true, false, SoundEvents.STONE_BREAK),
		MIRROR_0P5("0.5", "mirror_0p5", false, true, SoundEvents.AMETHYST_BLOCK_CHIME);

		final String targetKey;
		final String clueId;
		final boolean tearOrSmash;
		final boolean twoTall;
		final SoundEvent sound;

		Form(String targetKey, String clueId, boolean tearOrSmash, boolean twoTall, SoundEvent sound) {
			this.targetKey = targetKey;
			this.clueId = clueId;
			this.tearOrSmash = tearOrSmash;
			this.twoTall = twoTall;
			this.sound = sound;
		}
	}

	private static final VoxelShape SHAPE_FLOOR = Block.box(0, 0, 0, 16, 16, 16);
	private static final VoxelShape SHAPE_POSTER_N = Block.box(1, 1, 14.5, 15, 15, 16);
	private static final VoxelShape SHAPE_POSTER_S = Block.box(1, 1, 0, 15, 15, 1.5);
	private static final VoxelShape SHAPE_POSTER_W = Block.box(14.5, 1, 1, 16, 15, 15);
	private static final VoxelShape SHAPE_POSTER_E = Block.box(0, 1, 1, 1.5, 15, 15);
	private static final VoxelShape SHAPE_MIRROR_N = Block.box(0, 0, 13, 16, 16, 16);
	private static final VoxelShape SHAPE_MIRROR_S = Block.box(0, 0, 0, 16, 16, 3);
	private static final VoxelShape SHAPE_MIRROR_W = Block.box(13, 0, 0, 16, 16, 16);
	private static final VoxelShape SHAPE_MIRROR_E = Block.box(0, 0, 0, 3, 16, 16);

	private static final Map<UUID, Long> LAST_INSPECT_POS = new HashMap<>();
	private static final Map<UUID, Integer> LAST_INSPECT_TICK = new HashMap<>();
	private static final int CONFIRM_WINDOW_TICKS = 240;

	private final Form form;

	AnomalyExitBlock(Properties properties, Form form) {
		super(properties);
		this.form = form;
		registerDefaultState(stateDefinition.any()
				.setValue(FACING, Direction.SOUTH)
				.setValue(HALF, DoubleBlockHalf.LOWER));
	}

	Form form() {
		return form;
	}

	@Override
	protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return simpleCodec(p -> new AnomalyExitBlock(p, form));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HALF);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState()
				.setValue(FACING, context.getHorizontalDirection().getOpposite())
				.setValue(HALF, DoubleBlockHalf.LOWER);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (form == Form.POSTER_161) {
			return switch (state.getValue(FACING)) {
				case NORTH -> SHAPE_POSTER_N;
				case SOUTH -> SHAPE_POSTER_S;
				case WEST -> SHAPE_POSTER_W;
				default -> SHAPE_POSTER_E;
			};
		}
		if (form == Form.MIRROR_0P5) {
			return switch (state.getValue(FACING)) {
				case NORTH -> SHAPE_MIRROR_N;
				case SOUTH -> SHAPE_MIRROR_S;
				case WEST -> SHAPE_MIRROR_W;
				default -> SHAPE_MIRROR_E;
			};
		}
		return SHAPE_FLOOR;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.CONSUME;
		}
		BlockPos anchor = canonicalPos(state, pos);
		int now = serverLevel.getServer().getTickCount();
		UUID id = serverPlayer.getUUID();
		boolean confirmed = serverPlayer.isShiftKeyDown()
				|| (LAST_INSPECT_POS.getOrDefault(id, Long.MIN_VALUE) == anchor.asLong()
						&& now - LAST_INSPECT_TICK.getOrDefault(id, -1000) <= CONFIRM_WINDOW_TICKS);

		if (!confirmed) {
			LAST_INSPECT_POS.put(id, anchor.asLong());
			LAST_INSPECT_TICK.put(id, now);
			showClue(serverLevel, serverPlayer, anchor);
			return InteractionResult.SUCCESS;
		}
		LAST_INSPECT_POS.remove(id);
		triggerExit(serverLevel, serverPlayer, anchor);
		return InteractionResult.SUCCESS;
	}

	boolean triggerTearOrSmash(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		if (!form.tearOrSmash) return false;
		BlockPos anchor = canonicalPos(state, pos);
		LAST_INSPECT_POS.remove(player.getUUID());
		triggerExit(level, player, anchor);
		return true;
	}

	private BlockPos canonicalPos(BlockState state, BlockPos pos) {
		return form.twoTall && state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
	}

	private void showClue(ServerLevel level, ServerPlayer player, BlockPos pos) {
		level.playSound(null, pos, form.sound, SoundSource.BLOCKS, 0.55F, 0.9F);
		PlayerStats stats = PlayerRpg.get(player);
		boolean keen = stats.sanityStat() >= 3 || stats.level() >= 4;
		String key = keen
				? "message.noclip_backrooms.level0.exit." + form.clueId + ".keen"
				: "message.noclip_backrooms.level0.exit." + form.clueId + ".clue";
		player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.YELLOW));
	}

	private void triggerExit(ServerLevel level, ServerPlayer player, BlockPos pos) {
		level.playSound(null, pos, form.sound, SoundSource.BLOCKS, 0.85F, 0.78F);
		level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
				14, 0.35, 0.35, 0.35, 0.02);
		LevelExits.use(player, form.targetKey);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (form == Form.STEAM_GRATE_2 && random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
					pos.getX() + 0.25 + random.nextDouble() * 0.5,
					pos.getY() + 1.05,
					pos.getZ() + 0.25 + random.nextDouble() * 0.5,
					0.0, 0.04, 0.0);
		} else if (form == Form.OIL_VENT_1 && random.nextInt(8) == 0) {
			level.addParticle(ParticleTypes.SMOKE,
					pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					0.0, 0.02, 0.0);
		} else if (form == Form.MIRROR_0P5 && random.nextInt(10) == 0) {
			level.addParticle(ParticleTypes.END_ROD,
					pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					0.0, 0.01, 0.0);
		}
	}
}
