package dev.noclip.backrooms.level0;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Manila Mary Foundation guide folder resting on the table.
 * Right-clicking hands the player a written survival manual explaining Level 0 and the way to Level 1.
 */
final class ManilaNoteBlock extends Block {
	static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 1.5, 14);
	private static final String BOOK_MARKER = "Manila Mary Foundation";

	ManilaNoteBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			if (alreadyCarriesGuide(player)) {
				if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
					serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.manila_guide.already"));
				}
			} else {
				ItemStack book = createGuideBook();
				if (!player.getInventory().add(book)) {
					player.drop(book, false);
				}
				level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.9F, 1.0F);
			}
		}
		return InteractionResult.SUCCESS;
	}

	private static boolean alreadyCarriesGuide(Player player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.is(Items.WRITTEN_BOOK)) continue;
			WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
			if (content != null && BOOK_MARKER.equals(content.author())) return true;
		}
		return false;
	}

	static ItemStack createGuideBook() {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> pages = List.of(
				Filterable.passThrough(Component.translatable("book.noclip_backrooms.manila_guide.page1")),
				Filterable.passThrough(Component.translatable("book.noclip_backrooms.manila_guide.page2")),
				Filterable.passThrough(Component.translatable("book.noclip_backrooms.manila_guide.page3")));
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
				Filterable.passThrough("Manila Room Guide"),
				BOOK_MARKER,
				0,
				pages,
				true));
		return book;
	}
}
