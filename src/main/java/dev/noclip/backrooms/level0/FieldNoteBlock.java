package dev.noclip.backrooms.level0;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Wanderer and maintenance notes found inside upper lofts, overlooks, and sub-level rooms.
 * Right-clicking hands the player a readable field log explaining nearby Level 0 anomalies.
 */
final class FieldNoteBlock extends Block {
	static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);
	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 1.5, 14);
	private static final String AUTHOR = "Level 0 Survey";
	private static final String[] TITLES = {
			"Electrician Log #04",
			"Overlook Report #07",
			"Sub-Level Memo #12"
	};

	FieldNoteBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(VARIANT, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, VARIANT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection()).setValue(VARIANT, 0);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			int variant = state.getValue(VARIANT);
			String title = TITLES[variant];
			if (alreadyCarriesNote(player, title)) {
				if (player instanceof ServerPlayer serverPlayer) {
					serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.field_note.already"));
				}
			} else {
				ItemStack book = createLogBook(variant);
				if (!player.getInventory().add(book)) {
					player.drop(book, false);
				}
				level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.9F, 1.0F);
			}
		}
		return InteractionResult.SUCCESS;
	}

	private static boolean alreadyCarriesNote(Player player, String title) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.is(Items.WRITTEN_BOOK)) continue;
			WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
			if (content != null && AUTHOR.equals(content.author()) && title.equals(content.title().raw())) {
				return true;
			}
		}
		return false;
	}

	static ItemStack createLogBook(int variant) {
		int idx = Math.max(0, Math.min(TITLES.length - 1, variant));
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> pages = List.of(
				Filterable.passThrough(Component.translatable("book.noclip_backrooms.field_note." + idx + ".page1")),
				Filterable.passThrough(Component.translatable("book.noclip_backrooms.field_note." + idx + ".page2")));
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
				Filterable.passThrough(TITLES[idx]),
				AUTHOR,
				0,
				pages,
				true));
		return book;
	}
}
