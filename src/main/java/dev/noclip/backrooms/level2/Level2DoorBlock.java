package dev.noclip.backrooms.level2;

import dev.noclip.backrooms.core.LevelExits;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;

/** The steel doors in the tunnel walls: they swing in towards the room behind. Many are locked. */
final class Level2DoorBlock extends DoorBlock {
	private final boolean locked;

	Level2DoorBlock(BlockSetType type, Properties properties, boolean locked) {
		super(type, properties);
		this.locked = locked;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!locked) {
			// Wikidot non-door exit: opening an unlocked door while well-hydrated/nauseous (or holding an empty bottle) can lead to Level 189.
			if (!level.isClientSide() && !state.getValue(OPEN) && level.dimension() == Level2.LEVEL_2 && player instanceof ServerPlayer sp) {
				boolean bottle = sp.getMainHandItem().is(Items.GLASS_BOTTLE);
				boolean urge = sp.getFoodData().getFoodLevel() >= 18 && (sp.getFoodData().getSaturationLevel() > 0 || sp.hasEffect(MobEffects.NAUSEA));
				if (bottle || (urge && level.getRandom().nextInt(100) < 8)) {
					level.playSound(null, pos, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.7F, 0.9F);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level2.restroom_exit"));
					LevelExits.use(sp, "189");
					return InteractionResult.SUCCESS;
				}
			}
			return super.useWithoutItem(state, level, pos, player, hit);
		}
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level2.locked"));
			level.playSound(null, pos, Level2Sounds.LOCKED, SoundSource.BLOCKS, 0.8F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		}
		return InteractionResult.SUCCESS;
	}
}
