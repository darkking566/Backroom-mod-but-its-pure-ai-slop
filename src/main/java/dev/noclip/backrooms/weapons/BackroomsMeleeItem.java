package dev.noclip.backrooms.weapons;

import dev.noclip.backrooms.BackroomsMod;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Configurable Backrooms melee weapon supporting all 3 styles (Medieval Blunt, Western, Chinese),
 * exact attack damage/speed/reach attributes, tier tooltips, and active/on-hit combat traits.
 */
public final class BackroomsMeleeItem extends Item {
	public enum Style {
		BLUNT, WESTERN, CHINESE
	}

	public enum Trait {
		NONE,
		KNOCKBACK,
		BLEED,
		ARMOR_PIERCE_20,
		ARMOR_PIERCE_35,
		ARMOR_PIERCE_45,
		HEAVY_SLAM,
		BOSS_BREAKER,
		CRIMSON_SHOCKWAVE,
		THUNDER_AOE,
		BACKSTAB,
		CLEAVE,
		EXECUTE,
		ACID_CORRODE,
		UNDEAD_SMILER_BANE,
		VAMPIRIC_CLEANSE,
		VOID_WAVE,
		PEACHWOOD_BANE,
		COMBO_STUN,
		METEOR_PULL,
		SLEEVE_DARTS,
		STOPMOTION_FREEZE,
		COIN_WARD,
		ALPHA_BLOODLUST
	}

	private static final net.minecraft.resources.Identifier REACH_MOD_ID = BackroomsMod.id("melee_reach_bonus");

	private final Style style;
	private final int tier;
	private final float baseDamage;
	private final Trait trait;
	private final String loreKey;

	public BackroomsMeleeItem(Properties properties, Style style, int tier, float attackDamage,
			float attackSpeed, float extraReach, Trait trait, String loreKey) {
		super(properties.attributes(buildAttributes(attackDamage, attackSpeed, extraReach)));
		this.style = style;
		this.tier = tier;
		this.baseDamage = attackDamage;
		this.trait = trait;
		this.loreKey = loreKey;
	}

	private static ItemAttributeModifiers buildAttributes(float attackDamage, float attackSpeed, float extraReach) {
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE,
						new AttributeModifier(BASE_ATTACK_DAMAGE_ID, attackDamage - 1.0, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED,
						new AttributeModifier(BASE_ATTACK_SPEED_ID, attackSpeed - 4.0, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.MAINHAND);
		if (extraReach > 0.01F) {
			builder.add(Attributes.ENTITY_INTERACTION_RANGE,
					new AttributeModifier(REACH_MOD_ID, extraReach, AttributeModifier.Operation.ADD_VALUE),
					EquipmentSlotGroup.MAINHAND);
		}
		return builder.build();
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		if (!(attacker.level() instanceof ServerLevel serverLevel)) return;

		String entityPath = target.getType().builtInRegistryHolder().key().identifier().getPath();
		boolean isShadowOrSmiler = entityPath.contains("smiler") || entityPath.contains("duller")
				|| entityPath.contains("bacteria") || entityPath.contains("wretch") || entityPath.contains("shadow");

		switch (trait) {
			case KNOCKBACK -> pushBack(target, 0.65, target.getX() - attacker.getX(), target.getZ() - attacker.getZ());
			case BLEED -> target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
			case ARMOR_PIERCE_20 -> target.hurtServer(serverLevel, serverLevel.damageSources().magic(), baseDamage * 0.20F);
			case ARMOR_PIERCE_35 -> target.hurtServer(serverLevel, serverLevel.damageSources().magic(), baseDamage * 0.35F);
			case ARMOR_PIERCE_45 -> target.hurtServer(serverLevel, serverLevel.damageSources().magic(), baseDamage * 0.45F);
			case HEAVY_SLAM -> {
				pushBack(target, 1.0, target.getX() - attacker.getX(), target.getZ() - attacker.getZ());
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2));
			}
			case BOSS_BREAKER -> {
				if (target.getMaxHealth() >= 50.0F || target.getArmorValue() >= 6) {
					target.hurtServer(serverLevel, serverLevel.damageSources().magic(), baseDamage * 0.30F);
				}
			}
			case CRIMSON_SHOCKWAVE -> {
				serverLevel.sendParticles(ParticleTypes.CRIMSON_SPORE, target.getX(), target.getY() + 1.0, target.getZ(), 18, 1.5, 0.5, 1.5, 0.05);
				for (LivingEntity near : serverLevel.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3.5), e -> e != attacker && e != target && e instanceof Monster)) {
					near.hurtServer(serverLevel, serverLevel.damageSources().magic(), 5.0F);
					pushBack(near, 0.8, near.getX() - attacker.getX(), near.getZ() - attacker.getZ());
				}
			}
			case THUNDER_AOE -> {
				serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1.0, target.getZ(), 24, 2.0, 0.8, 2.0, 0.12);
				serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6F, 1.5F);
				for (LivingEntity near : serverLevel.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(4.5), e -> e != attacker && e instanceof Monster)) {
					near.hurtServer(serverLevel, serverLevel.damageSources().lightningBolt(), 6.0F);
					near.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 3));
				}
			}
			case BACKSTAB -> {
				Vec3 lookT = target.getLookAngle();
				Vec3 toAttacker = attacker.position().subtract(target.position()).normalize();
				if (lookT.dot(toAttacker) < -0.25) {
					target.hurtServer(serverLevel, serverLevel.damageSources().magic(), baseDamage * 0.80F);
					target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
				}
			}
			case CLEAVE -> {
				for (LivingEntity near : serverLevel.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(2.5), e -> e != attacker && e != target && e instanceof Monster)) {
					near.hurtServer(serverLevel, serverLevel.damageSources().mobAttack(attacker), baseDamage * 0.55F);
				}
			}
			case EXECUTE -> {
				if (target.getHealth() / Math.max(1.0F, target.getMaxHealth()) <= 0.35F) {
					target.hurtServer(serverLevel, serverLevel.damageSources().magic(), baseDamage * 0.40F);
				}
			}
			case ACID_CORRODE -> {
				target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
				target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
				serverLevel.sendParticles(ParticleTypes.ITEM_SLIME, target.getX(), target.getY() + 1.0, target.getZ(), 10, 0.4, 0.5, 0.4, 0.05);
			}
			case UNDEAD_SMILER_BANE -> {
				if (isShadowOrSmiler) {
					target.hurtServer(serverLevel, serverLevel.damageSources().magic(), baseDamage * 0.50F);
					serverLevel.sendParticles(ParticleTypes.GLOW, target.getX(), target.getY() + 1.0, target.getZ(), 12, 0.4, 0.6, 0.4, 0.05);
				}
			}
			case VAMPIRIC_CLEANSE -> {
				attacker.heal(2.5F);
				attacker.removeEffect(MobEffects.DARKNESS);
				attacker.removeEffect(MobEffects.NAUSEA);
			}
			case PEACHWOOD_BANE -> {
				if (isShadowOrSmiler) {
					target.hurtServer(serverLevel, serverLevel.damageSources().magic(), baseDamage * 1.80F);
					target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
					serverLevel.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 1.0, target.getZ(), 16, 0.5, 0.7, 0.5, 0.08);
				}
			}
			case COMBO_STUN -> {
				if (attacker.getRandom().nextFloat() < 0.30F) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 4));
					target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 1));
				}
			}
			case STOPMOTION_FREEZE -> {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 5));
				serverLevel.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + 1.0, target.getZ(), 12, 0.5, 0.6, 0.5, 0.05);
			}
			case COIN_WARD -> {
				if (isShadowOrSmiler) {
					target.hurtServer(serverLevel, serverLevel.damageSources().magic(), baseDamage * 2.0F);
				}
			}
			case ALPHA_BLOODLUST -> {
				target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
				attacker.heal(3.0F);
			}
			default -> {
			}
		}
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (trait != Trait.VOID_WAVE && trait != Trait.METEOR_PULL && trait != Trait.SLEEVE_DARTS && trait != Trait.COIN_WARD) {
			return super.use(level, player, hand);
		}
		if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
		if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();

		switch (trait) {
			case VOID_WAVE -> {
				for (int i = 1; i <= 10; i++) {
					Vec3 p = eye.add(look.scale(i * 1.1));
					serverLevel.sendParticles(ParticleTypes.PORTAL, p.x, p.y, p.z, 8, 0.3, 0.3, 0.3, 0.05);
					for (LivingEntity e : serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(1.3), ent -> ent != player)) {
						e.hurtServer(serverLevel, serverLevel.damageSources().magic(), 11.0F);
					}
				}
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 0.7F);
				player.getCooldowns().addCooldown(stack, 40);
			}
			case METEOR_PULL -> {
				Vec3 end = eye.add(look.scale(7.5));
				List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class,
						new AABB(eye, end).inflate(1.5), e -> e != player);
				if (!targets.isEmpty()) {
					LivingEntity t = targets.getFirst();
					t.hurtServer(serverLevel, serverLevel.damageSources().playerAttack(player), 10.0F);
					Vec3 pull = player.position().subtract(t.position()).normalize().scale(0.95);
					t.setDeltaMovement(pull.x, 0.35, pull.z);
				}
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.8F, 1.2F);
				player.getCooldowns().addCooldown(stack, 35);
			}
			case SLEEVE_DARTS -> {
				for (int d = 0; d < 3; d++) {
					Vec3 spread = look.add((player.getRandom().nextDouble() - 0.5) * 0.08, (player.getRandom().nextDouble() - 0.5) * 0.06, (player.getRandom().nextDouble() - 0.5) * 0.08).normalize();
					for (int step = 1; step <= 14; step++) {
						Vec3 p = eye.add(spread.scale(step * 0.9));
						if (step % 3 == 0) serverLevel.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
						List<LivingEntity> hit = serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(0.75), e -> e != player);
						if (!hit.isEmpty()) {
							hit.getFirst().hurtServer(serverLevel, serverLevel.damageSources().playerAttack(player), 6.0F);
							break;
						}
					}
				}
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.65F, 1.6F);
				player.getCooldowns().addCooldown(stack, 24);
			}
			case COIN_WARD -> {
				serverLevel.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.5, player.getZ(), 45, 4.5, 0.6, 4.5, 0.05);
				player.removeEffect(MobEffects.DARKNESS);
				player.removeEffect(MobEffects.NAUSEA);
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0));
				for (LivingEntity m : serverLevel.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(8.0), e -> e instanceof Monster)) {
					m.hurtServer(serverLevel, serverLevel.damageSources().magic(), 8.0F);
					pushBack(m, 1.2, m.getX() - player.getX(), m.getZ() - player.getZ());
					m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
				}
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.9F, 1.2F);
				player.getCooldowns().addCooldown(stack, 140);
			}
			default -> {
			}
		}
		stack.hurtAndBreak(1, player, hand);
		return InteractionResult.SUCCESS;
	}

	private static void pushBack(LivingEntity target, double strength, double dx, double dz) {
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0E-4) return;
		target.push((dx / len) * strength * 0.55, 0.25, (dz / len) * strength * 0.55);
		target.hurtMarked = true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		ChatFormatting color = switch (tier) {
			case 0 -> ChatFormatting.GRAY;
			case 1 -> ChatFormatting.WHITE;
			case 2 -> ChatFormatting.AQUA;
			case 3 -> ChatFormatting.LIGHT_PURPLE;
			case 4 -> ChatFormatting.GOLD;
			default -> ChatFormatting.WHITE;
		};
		String styleKey = switch (style) {
			case BLUNT -> "tooltip.noclip_backrooms.style.blunt";
			case WESTERN -> "tooltip.noclip_backrooms.style.western";
			case CHINESE -> "tooltip.noclip_backrooms.style.chinese";
		};
		adder.accept(Component.translatable("tooltip.noclip_backrooms.weapon_tier", tier, Component.translatable(styleKey)).withStyle(color));
		if (loreKey != null && !loreKey.isEmpty()) {
			adder.accept(Component.translatable(loreKey).withStyle(ChatFormatting.GRAY));
		}
	}
}
