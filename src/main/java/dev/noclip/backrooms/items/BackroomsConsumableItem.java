package dev.noclip.backrooms.items;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Functional survival consumables, utility gear, and the Backrooms Compendium guidebook. */
public final class BackroomsConsumableItem extends Item {
	public enum Kind {
		COMPENDIUM,
		FIELD_BANDAGE,
		ALMOND_ELIXIR,
		BATTERY_PACK,
		UV_FLASHLIGHT,
		PHOSPHOR_FLARE,
		SHRAPNEL_MINE
	}

	private final Kind kind;
	private final String loreKey;

	public BackroomsConsumableItem(Properties properties, Kind kind, String loreKey) {
		super(properties);
		this.kind = kind;
		this.loreKey = loreKey;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
		if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;

		switch (kind) {
			case COMPENDIUM -> {
				if (player instanceof ServerPlayer sp) {
					sp.sendSystemMessage(Component.translatable("message.noclip_backrooms.compendium.guide"));
				}
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.0F);
				player.getCooldowns().addCooldown(stack, 20);
			}
			case FIELD_BANDAGE -> {
				player.heal(6.0F);
				player.removeEffect(MobEffects.WITHER);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.8F, 1.2F);
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 30);
			}
			case ALMOND_ELIXIR -> {
				player.removeEffect(MobEffects.DARKNESS);
				player.removeEffect(MobEffects.NAUSEA);
				player.removeEffect(MobEffects.BLINDNESS);
				player.removeEffect(MobEffects.POISON);
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 240, 1));
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 900, 0));
				if (player instanceof ServerPlayer sp) PlayerRpg.restoreSanity(sp, 85.0F);
				server.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 18, 0.5, 0.7, 0.5, 0.05);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 0.9F, 1.15F);
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 40);
			}
			case BATTERY_PACK -> {
				Item flashlight = BuiltInRegistries.ITEM.getValue(BackroomsMod.id("flashlight"));
				boolean recharged = false;
				for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
					ItemStack invStack = player.getInventory().getItem(i);
					if (invStack.is(flashlight) && invStack.isDamaged()) {
						invStack.setDamageValue(0);
						recharged = true;
						break;
					}
				}
				if (recharged) {
					if (!player.isCreative()) stack.shrink(1);
					server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.5F);
					if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.battery.recharged"));
				} else if (player instanceof ServerPlayer sp) {
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.battery.none_needed"));
				}
				player.getCooldowns().addCooldown(stack, 15);
			}
			case UV_FLASHLIGHT -> {
				Vec3 eye = player.getEyePosition();
				Vec3 look = player.getLookAngle();
				for (int i = 1; i <= 12; i++) {
					Vec3 p = eye.add(look.scale(i * 0.9));
					server.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0.2, 0.2, 0.2, 0.01);
					for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(1.2), ent -> ent instanceof Monster)) {
						String path = e.getType().builtInRegistryHolder().key().identifier().getPath();
						if (path.contains("smiler") || path.contains("duller") || path.contains("bacteria") || path.contains("shadow")) {
							e.hurtServer(server, server.damageSources().magic(), 9.0F);
							e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 4));
							e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0));
						}
					}
				}
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
				stack.hurtAndBreak(1, player, hand);
				player.getCooldowns().addCooldown(stack, 30);
			}
			case PHOSPHOR_FLARE -> {
				Vec3 pos = player.getEyePosition().add(player.getLookAngle().scale(6.5));
				server.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 60, 3.0, 1.5, 3.0, 0.04);
				server.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 30, 2.0, 1.0, 2.0, 0.03);
				for (LivingEntity m : server.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(8.0), e -> e instanceof Monster)) {
					m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400, 0));
					m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2));
				}
				server.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.9F, 1.3F);
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 30);
			}
			case SHRAPNEL_MINE -> {
				Vec3 pos = player.position().add(player.getLookAngle().scale(2.5));
				server.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.5, pos.z, 4, 0.8, 0.4, 0.8, 0.05);
				for (LivingEntity m : server.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(4.5), e -> e != player && e instanceof Monster)) {
					m.hurtServer(server, server.damageSources().explosion(player, player), 14.0F);
					m.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
					m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2));
				}
				server.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.85F, 1.3F);
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 40);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		if (loreKey != null && !loreKey.isEmpty()) {
			adder.accept(Component.translatable(loreKey).withStyle(ChatFormatting.GRAY));
		}
	}
}
