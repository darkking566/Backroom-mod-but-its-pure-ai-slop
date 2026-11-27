package dev.noclip.backrooms.level3;

import dev.noclip.backrooms.weapons.BackroomsFirearmItem;
import dev.noclip.backrooms.weapons.BackroomsMagicItem;
import dev.noclip.backrooms.weapons.BackroomsMeleeItem;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The metal detector at Base Gamma's checkpoint (CN wiki: a finger-prick blood test, a metal detector, and no weapons
 * past it). Nobody walks through it: right-click, and the guards let you through to the other side, if you carry no
 * weapons in. Going out is always allowed. The lockers beside it are for leaving weapons behind.
 */
final class GammaCheckpointBlock extends Level3PartBlock {
	@SuppressWarnings("unchecked")
	private static final Map<Direction, VoxelShape>[] OUTLINE_PARTS = new Map[] {
			Level3Shapes.facing(
					new double[] {0, 0, 6, 3, 16, 10},
					new double[] {13, 0, 6, 16, 16, 10},
					new double[] {3, 7, 7, 13, 9, 9}
			),
			Level3Shapes.facing(
					new double[] {0, 0, 6, 3, 16, 10},
					new double[] {13, 0, 6, 16, 16, 10},
					new double[] {3, 12, 6, 13, 16, 10}
			)
	};

	@SuppressWarnings("unchecked")
	private static final Map<Direction, VoxelShape>[] COLLISION_PARTS = new Map[] {
			Level3Shapes.facing(0, 0, 6, 16, 16, 10),
			Level3Shapes.facing(0, 0, 6, 16, 16, 10)
	};

	GammaCheckpointBlock(Properties properties) {
		super(properties, null, OUTLINE_PARTS, COLLISION_PARTS);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
		BlockPos base = pos.below(state.getValue(PART));
		Direction out = state.getValue(FACING);
		double side = (player.getX() - base.getX() - 0.5) * out.getStepX() + (player.getZ() - base.getZ() - 0.5) * out.getStepZ();
		boolean entering = side > 0;
		if (entering) {
			if (armed(player)) {
				level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0F, 0.5F);
				serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level3.gamma_armed"));
				return InteractionResult.SUCCESS;
			}
			serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level3.gamma_pass"));
		}
		Direction to = entering ? out.getOpposite() : out;
		BlockPos land = base.relative(to);
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.8F, 1.4F);
		serverPlayer.teleportTo(land.getX() + 0.5, land.getY(), land.getZ() + 0.5);
		return InteractionResult.SUCCESS;
	}

	/** Whether a player carries anything the guards would call a weapon. */
	static boolean armed(Player player) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (weapon(inventory.getItem(i))) return true;
		}
		return false;
	}

	static boolean weapon(ItemStack stack) {
		if (stack.isEmpty()) return false;
		// The detector sees into bundles and boxes, however deeply packed.
		var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
		if (bundle != null && bundle.items().stream().anyMatch(inside -> weapon(inside.create()))) return true;
		var box = stack.get(DataComponents.CONTAINER);
		if (box != null && box.nonEmptyItemCopyStream().anyMatch(GammaCheckpointBlock::weapon)) return true;
		var item = stack.getItem();
		return item instanceof BackroomsMeleeItem || item instanceof BackroomsFirearmItem || item instanceof BackroomsMagicItem
				|| item instanceof ProjectileWeaponItem || item instanceof TridentItem || item instanceof MaceItem
				|| stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES);
	}
}
