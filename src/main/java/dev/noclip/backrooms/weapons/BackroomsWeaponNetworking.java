package dev.noclip.backrooms.weapons;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Registers weapon C2S firing, S2C hitmarker networking, and melee guard damage mitigation. */
public final class BackroomsWeaponNetworking {
	private BackroomsWeaponNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(FireWeaponPayload.TYPE, FireWeaponPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(WeaponHitPayload.TYPE, WeaponHitPayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(FireWeaponPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (!player.isAlive() || player.isSpectator()) return;
			ItemStack held = player.getMainHandItem();
			if (held.getItem() instanceof BackroomsFirearmItem firearm) {
				firearm.fireWeapon(player.level(), player, InteractionHand.MAIN_HAND, held, payload.aiming());
			}
		});

		// Defensive right-click melee guard absorbs 35% damage and plays a metallic parry clang
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!(entity instanceof ServerPlayer player) || !player.isAlive() || damageTaken <= 0.2F) return;
			if (player.isUsingItem() && player.getUseItem().getItem() instanceof BackroomsMeleeItem melee && !melee.hasActiveRightClickSkill()) {
				player.heal(damageTaken * 0.35F);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.75F, 1.25F);
			}
		});
	}

	public static void sendHitmarker(ServerPlayer player, boolean killed) {
		if (ServerPlayNetworking.canSend(player, WeaponHitPayload.TYPE)) {
			ServerPlayNetworking.send(player, new WeaponHitPayload(killed));
		}
	}
}
