package dev.noclip.backrooms.level1;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A "friend jade": it glows whenever something hostile is near the one carrying it. */
final class FriendOrbItem extends Item {
	private static final double RANGE = 24;

	FriendOrbItem(Properties properties) {
		super(properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity holder, EquipmentSlot slot) {
		if (!(holder instanceof ServerPlayer player) || level.getGameTime() % 20 != 0) return;
		boolean near = !level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(RANGE)).isEmpty();
		if (!near) return;
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.4, 0.5, 0.4, 0.01);
		if (level.getGameTime() % 100 == 0) player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.friend_orb_glows"));
	}
}
