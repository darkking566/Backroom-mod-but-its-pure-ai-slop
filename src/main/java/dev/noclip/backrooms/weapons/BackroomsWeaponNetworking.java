package dev.noclip.backrooms.weapons;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Monster;
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

		// Signature weapon evolution: monster kills level up held Backrooms weapons (Tier 1 -> 2 -> 3)
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof Monster)) return;
			if (!(source.getEntity() instanceof ServerPlayer killer)) return;
			ItemStack held = killer.getMainHandItem();
			if (WeaponEvolution.isEvolvable(held)) {
				WeaponEvolution.recordKill(killer, held);
			}
		});

		// Command: /backrooms weapon tier <1|2|3> to preview/test Tier 1..3 visual evolution immediately
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("backrooms")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("weapon")
								.then(Commands.literal("tier")
										.then(Commands.argument("tier", IntegerArgumentType.integer(1, 3))
												.executes(ctx -> {
													ServerPlayer player = ctx.getSource().getPlayerOrException();
													ItemStack held = player.getMainHandItem();
													if (!WeaponEvolution.isEvolvable(held)) {
														ctx.getSource().sendFailure(Component.translatable("message.noclip_backrooms.weapon.not_evolvable"));
														return 0;
													}
													int tier = IntegerArgumentType.getInteger(ctx, "tier");
													WeaponEvolution.setTier(held, tier);
													player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
															SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 0.9F + tier * 0.15F);
													ctx.getSource().sendSuccess(() -> Component.translatable(
															"message.noclip_backrooms.weapon.tier_set", held.getHoverName(), tier), false);
													return 1;
												}))))
		));
	}

	public static void sendHitmarker(ServerPlayer player, boolean killed) {
		if (ServerPlayNetworking.canSend(player, WeaponHitPayload.TYPE)) {
			ServerPlayNetworking.send(player, new WeaponHitPayload(killed));
		}
	}
}
