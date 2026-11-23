package dev.noclip.backrooms.level1;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Right-click any face to leave a glowing smear there, one pinch per mark. */
final class PhosphorPowderItem extends Item {
	PhosphorPowderItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos mark = context.getClickedPos().relative(context.getClickedFace());
		if (!level.getBlockState(mark).canBeReplaced()) return InteractionResult.FAIL;
		if (!level.isClientSide()) {
			level.setBlock(mark, Level1Blocks.PHOSPHOR_MARK.defaultBlockState().setValue(PhosphorMarkBlock.FACING, context.getClickedFace()),
					Block.UPDATE_ALL);
			level.playSound(null, mark, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.6F, 1.4F);
			Player player = context.getPlayer();
			if (player == null || !player.getAbilities().instabuild) context.getItemInHand().shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
