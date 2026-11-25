package dev.noclip.backrooms.props;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.crafting.BackroomsCrafting;
import dev.noclip.backrooms.crafting.BackroomsStationMenu;
import dev.noclip.backrooms.crafting.StationType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/** Horizontal-facing Backrooms crafting workstation block that opens its dedicated recipe GUI on right-click. */
public final class WorkstationBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<WorkstationBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.fieldOf("station").forGetter(b -> b.stationType.id()),
			propertiesCodec()
	).apply(instance, (stationId, props) -> new WorkstationBlock(StationType.byId(stationId), props)));

	private final StationType stationType;

	public WorkstationBlock(StationType stationType, Properties properties) {
		super(properties);
		this.stationType = stationType;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	public StationType stationType() {
		return stationType;
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
		return openStation(level, pos, player);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (player.isShiftKeyDown() && !stack.isEmpty()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		return openStation(level, pos, player);
	}

	private InteractionResult openStation(Level level, BlockPos pos, Player player) {
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		player.openMenu(new SimpleMenuProvider(
				(syncId, inv, p) -> new BackroomsStationMenu(
						BackroomsCrafting.menuType(stationType),
						syncId,
						inv,
						stationType,
						ContainerLevelAccess.create(level, pos),
						this
				),
				stationType.title()
		));
		return InteractionResult.CONSUME;
	}
}
