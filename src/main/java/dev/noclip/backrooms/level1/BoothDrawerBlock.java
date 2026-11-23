package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.levels.LevelExits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The cash drawer under a toll booth's window. Players' notes say it always holds a flashlight, and that
 * whoever takes it ends up in Level LX; until LX exists, you just keep the flashlight.
 */
final class BoothDrawerBlock extends Block {
	static final BooleanProperty EMPTY = BooleanProperty.create("empty");
	static final String LEADS_TO = "LX";

	BoothDrawerBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(EMPTY, false));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
		if (state.getValue(EMPTY)) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.drawer_empty"));
			return InteractionResult.SUCCESS;
		}
		level.setBlock(pos, state.setValue(EMPTY, true), Block.UPDATE_ALL);
		level.playSound(null, pos, Level1Sounds.DRAWER, SoundSource.BLOCKS, 0.8F, 1.0F);
		ItemStack flashlight = new ItemStack(flashlight());
		if (!serverPlayer.getInventory().add(flashlight)) serverPlayer.drop(flashlight, false);
		serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.drawer_flashlight"));
		if (LevelExits.isOpen(LEADS_TO)) LevelExits.use(serverPlayer, LEADS_TO);
		return InteractionResult.SUCCESS;
	}

	/** The flashlight from Level 0's items, looked up by id; a torch if it is ever gone. */
	private static net.minecraft.world.item.Item flashlight() {
		return BuiltInRegistries.ITEM.getOptional(Level1.id("flashlight")).orElse(Items.TORCH);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(EMPTY);
	}
}
