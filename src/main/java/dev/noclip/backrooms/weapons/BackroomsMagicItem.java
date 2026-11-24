package dev.noclip.backrooms.weapons;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.monsters.Monsters;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * All 19 Backrooms magic & anomalous supernatural weapons. Consumes Almond Mana Cells, Almond Water,
 * durability, or self, and integrates with the Sanity system for forbidden crimson/void casts.
 */
public final class BackroomsMagicItem extends Item {
	public enum Spell {
		PHOSPHOR_TALISMAN,
		ALMOND_CENSER,
		SCREECH_BELL,
		DECOY_MIRROR,
		PRISM_LIGHT,
		STEAM_FLAME,
		ACID_GRIMOIRE,
		ARC_LIGHTNING,
		ABYSSAL_FROST,
		PLAGUE_CENSER,
		NOCLIP_WAND,
		CLUMP_GRAPPLE,
		PARTY_GRIMOIRE,
		STOPMOTION_WATCH,
		CRIMSON_SCYTHE,
		BEAST_SUMMON_BELL,
		FATE_DICE,
		VOID_RIFT,
		ASTRAL_CODEX
	}

	private final int tier;
	private final Spell spell;
	private final String costItemId;
	private final int cooldownTicks;
	private final float sanityCost;
	private final String loreKey;

	public BackroomsMagicItem(Properties properties, int tier, Spell spell, String costItemId,
			int cooldownTicks, float sanityCost, float meleeDamage, String loreKey) {
		super(meleeDamage > 0.0F ? withMeleeDamage(properties, meleeDamage) : properties);
		this.tier = tier;
		this.spell = spell;
		this.costItemId = costItemId;
		this.cooldownTicks = cooldownTicks;
		this.sanityCost = sanityCost;
		this.loreKey = loreKey;
	}

	private static Properties withMeleeDamage(Properties props, float dmg) {
		return props.attributes(ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, dmg - 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.6, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.build());
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		if (spell == Spell.CRIMSON_SCYTHE) {
			attacker.heal(3.0F);
			target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
		}
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
		if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;

		if (!consumeCost(player, stack)) {
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.magic.no_mana"));
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.4F);
			return InteractionResult.FAIL;
		}

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();

		switch (spell) {
			case PHOSPHOR_TALISMAN -> {
				Vec3 target = eye.add(look.scale(6.0));
				serverLevel.sendParticles(ParticleTypes.FLAME, target.x, target.y, target.z, 36, 2.2, 1.0, 2.2, 0.08);
				serverLevel.sendParticles(ParticleTypes.END_ROD, target.x, target.y, target.z, 24, 2.0, 1.0, 2.0, 0.06);
				for (LivingEntity e : serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(target, target).inflate(4.5), ent -> ent instanceof Monster)) {
					e.hurtServer(serverLevel, serverLevel.damageSources().magic(), 10.0F);
					e.igniteForSeconds(4);
					pushBack(e, 1.2, e.getX() - player.getX(), e.getZ() - player.getZ());
				}
				serverLevel.playSound(null, target.x, target.y, target.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.9F, 1.3F);
			}
			case ALMOND_CENSER -> {
				serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 32, 3.0, 1.0, 3.0, 0.04);
				for (Player ally : serverLevel.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(6.0))) {
					ally.removeEffect(MobEffects.DARKNESS);
					ally.removeEffect(MobEffects.NAUSEA);
					ally.removeEffect(MobEffects.BLINDNESS);
					ally.heal(4.0F);
					if (ally instanceof ServerPlayer sp) PlayerRpg.restoreSanity(sp, 30.0F);
				}
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.1F);
			}
			case SCREECH_BELL -> {
				serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1.0, player.getZ(), 3, 1.5, 0.4, 1.5, 0.0);
				for (LivingEntity m : serverLevel.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(7.0), e -> e instanceof Monster)) {
					m.hurtServer(serverLevel, serverLevel.damageSources().magic(), 6.0F);
					m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 4));
					pushBack(m, 1.1, m.getX() - player.getX(), m.getZ() - player.getZ());
				}
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 1.6F);
			}
			case DECOY_MIRROR -> {
				Vec3 decoyPos = eye.add(look.scale(5.5));
				serverLevel.sendParticles(ParticleTypes.GLOW, decoyPos.x, decoyPos.y, decoyPos.z, 35, 0.5, 1.0, 0.5, 0.05);
				for (Monster m : serverLevel.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(12.0))) {
					m.setTarget(null);
					m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 2));
					m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0));
				}
				serverLevel.playSound(null, decoyPos.x, decoyPos.y, decoyPos.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.9F, 1.0F);
			}
			case PRISM_LIGHT -> {
				for (int i = 1; i <= 18; i++) {
					Vec3 p = eye.add(look.scale(i * 1.1));
					serverLevel.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 4, 0.15, 0.15, 0.15, 0.02);
					for (LivingEntity e : serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(1.1), ent -> ent != player)) {
						String path = e.getType().builtInRegistryHolder().key().identifier().getPath();
						float dmg = (path.contains("smiler") || path.contains("wretch") || path.contains("duller") || path.contains("bacteria")) ? 22.0F : 9.0F;
						e.hurtServer(serverLevel, serverLevel.damageSources().magic(), dmg);
						e.igniteForSeconds(3);
						e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0));
					}
				}
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.5F);
			}
			case STEAM_FLAME -> {
				Vec3 impact = eye.add(look.scale(9.0));
				serverLevel.sendParticles(ParticleTypes.FLAME, impact.x, impact.y, impact.z, 48, 2.5, 1.5, 2.5, 0.12);
				serverLevel.sendParticles(ParticleTypes.CLOUD, impact.x, impact.y, impact.z, 24, 2.5, 1.2, 2.5, 0.05);
				for (LivingEntity e : serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(4.5), ent -> ent != player)) {
					e.hurtServer(serverLevel, serverLevel.damageSources().onFire(), 16.0F);
					e.igniteForSeconds(7);
				}
				serverLevel.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.85F, 1.1F);
			}
			case ACID_GRIMOIRE -> {
				Vec3 center = eye.add(look.scale(7.5));
				serverLevel.sendParticles(ParticleTypes.ITEM_SLIME, center.x, center.y + 1.5, center.z, 60, 3.5, 1.8, 3.5, 0.08);
				for (LivingEntity e : serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(5.5), ent -> ent != player)) {
					e.hurtServer(serverLevel, serverLevel.damageSources().magic(), 14.0F);
					e.addEffect(new MobEffectInstance(MobEffects.POISON, 160, 2));
					e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1));
				}
				serverLevel.playSound(null, center.x, center.y, center.z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1.0F, 0.7F);
			}
			case ARC_LIGHTNING -> {
				List<LivingEntity> list = serverLevel.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(12.0), e -> e instanceof Monster);
				int bounces = Math.min(5, list.size());
				for (int i = 0; i < bounces; i++) {
					LivingEntity t = list.get(i);
					t.hurtServer(serverLevel, serverLevel.damageSources().lightningBolt(), 15.0F);
					t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 3));
					serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, t.getX(), t.getY() + 1.0, t.getZ(), 20, 0.5, 0.7, 0.5, 0.12);
				}
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.85F, 1.4F);
			}
			case ABYSSAL_FROST -> {
				Vec3 center = eye.add(look.scale(7.0));
				float bonus = player.isInWater() ? 2.0F : 1.0F;
				serverLevel.sendParticles(ParticleTypes.SNOWFLAKE, center.x, center.y, center.z, 55, 3.0, 1.2, 3.0, 0.08);
				for (LivingEntity e : serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(5.0), ent -> ent != player)) {
					e.hurtServer(serverLevel, serverLevel.damageSources().freeze(), 13.0F * bonus);
					e.setTicksFrozen(200);
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 90, 5));
				}
				serverLevel.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0F, 0.8F);
			}
			case PLAGUE_CENSER -> {
				Vec3 center = eye.add(look.scale(6.0));
				serverLevel.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, center.x, center.y, center.z, 55, 3.5, 1.2, 3.5, 0.04);
				for (LivingEntity e : serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(5.5), ent -> ent != player)) {
					e.hurtServer(serverLevel, serverLevel.damageSources().magic(), 9.0F);
					e.addEffect(new MobEffectInstance(MobEffects.POISON, 180, 1));
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 140, 2));
				}
				serverLevel.playSound(null, center.x, center.y, center.z, SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.9F, 0.7F);
			}
			case NOCLIP_WAND -> {
				// Signature Backrooms spell: phase 5 blocks forward even through 1-2 block thick non-bedrock walls!
				Vec3 dest = player.position();
				for (int dist = 5; dist >= 2; dist--) {
					Vec3 cand = player.position().add(look.x * dist, Math.max(-0.5, look.y * dist * 0.5), look.z * dist);
					BlockPos feet = BlockPos.containing(cand);
					BlockPos head = feet.above();
					BlockState midState = serverLevel.getBlockState(BlockPos.containing(player.position().add(look.scale(dist * 0.5))));
					if (midState.is(Blocks.BEDROCK)) continue;
					if (serverLevel.getBlockState(feet).getCollisionShape(serverLevel, feet).isEmpty()
							&& serverLevel.getBlockState(head).getCollisionShape(serverLevel, head).isEmpty()) {
						dest = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
						break;
					}
				}
				serverLevel.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 28, 0.4, 0.8, 0.4, 0.1);
				player.teleportTo(dest.x, dest.y, dest.z);
				serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, dest.x, dest.y + 1.0, dest.z, 28, 0.4, 0.8, 0.4, 0.1);
				serverLevel.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9F, 1.2F);
			}
			case CLUMP_GRAPPLE -> {
				Vec3 boost = look.scale(1.35).add(0, 0.35, 0);
				player.setDeltaMovement(boost);
				player.fallDistance = 0;
				serverLevel.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.8, player.getZ(), 18, 0.3, 0.3, 0.3, 0.08);
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.9F, 0.7F);
			}
			case PARTY_GRIMOIRE -> {
				List<Monster> mobs = serverLevel.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(10.0));
				for (int i = 0; i < mobs.size(); i++) {
					Monster a = mobs.get(i);
					a.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 160, 0));
					if (mobs.size() > 1) {
						a.setTarget(mobs.get((i + 1) % mobs.size()));
					}
				}
				serverLevel.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(), 40, 4.0, 1.0, 4.0, 0.08);
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.9F, 1.2F);
			}
			case STOPMOTION_WATCH -> {
				serverLevel.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(), 60, 5.0, 1.5, 5.0, 0.05);
				for (LivingEntity m : serverLevel.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(12.0), e -> e != player && !(e instanceof Player))) {
					m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 6));
					m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 2));
				}
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 0.55F);
			}
			case CRIMSON_SCYTHE -> {
				for (int i = 1; i <= 12; i++) {
					Vec3 p = eye.add(look.scale(i * 1.1));
					serverLevel.sendParticles(ParticleTypes.CRIMSON_SPORE, p.x, p.y, p.z, 10, 0.5, 0.3, 0.5, 0.05);
					for (LivingEntity e : serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(1.5), ent -> ent != player)) {
						e.hurtServer(serverLevel, serverLevel.damageSources().magic(), 18.0F);
						e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
						player.heal(1.5F);
					}
				}
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.85F, 0.8F);
			}
			case BEAST_SUMMON_BELL -> {
				BlockPos spawnPos = BlockPos.containing(player.position().add(look.scale(2.5)));
				Mob ally = Monsters.PARTYPOOPER.create(serverLevel, EntitySpawnReason.MOB_SUMMONED);
				if (ally != null) {
					ally.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
					ally.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 400, 2));
					ally.addEffect(new MobEffectInstance(MobEffects.SPEED, 400, 1));
					serverLevel.addFreshEntity(ally);
				}
				serverLevel.sendParticles(ParticleTypes.SOUL, spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5, 30, 0.8, 1.0, 0.8, 0.06);
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 0.7F);
			}
			case FATE_DICE -> {
				int roll = 1 + player.getRandom().nextInt(20);
				player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.magic.fate_roll", roll));
				if (roll >= 18) {
					serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY() + 1.0, player.getZ(), 2, 1.0, 1.0, 1.0, 0.0);
					for (LivingEntity m : serverLevel.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(12.0), e -> e instanceof Monster)) {
						m.hurtServer(serverLevel, serverLevel.damageSources().lightningBolt(), 26.0F);
						m.igniteForSeconds(5);
					}
					player.heal(8.0F);
				} else if (roll >= 8) {
					for (LivingEntity m : serverLevel.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(9.0), e -> e instanceof Monster)) {
						m.hurtServer(serverLevel, serverLevel.damageSources().magic(), 8.0F + roll * 0.6F);
					}
				} else if (roll <= 2) {
					player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0));
					pushBack(player, 0.6, -look.x, -look.z);
				}
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.9F, 0.5F + roll * 0.06F);
			}
			case VOID_RIFT -> {
				Vec3 center = eye.add(look.scale(8.0));
				serverLevel.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 90, 3.5, 2.0, 3.5, 0.15);
				for (LivingEntity e : serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(6.5), ent -> ent != player)) {
					Vec3 pull = center.subtract(e.position()).normalize().scale(0.85);
					e.setDeltaMovement(pull.x, 0.25, pull.z);
					e.hurtServer(serverLevel, serverLevel.damageSources().magic(), 22.0F);
				}
				serverLevel.playSound(null, center.x, center.y, center.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.55F, 1.6F);
			}
			case ASTRAL_CODEX -> {
				serverLevel.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(), 50, 4.0, 1.5, 4.0, 0.06);
				for (Player ally : serverLevel.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(10.0))) {
					ally.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0));
					ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 1));
					if (ally instanceof ServerPlayer sp) PlayerRpg.restoreSanity(sp, 35.0F);
				}
				for (LivingEntity m : serverLevel.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(14.0), e -> e instanceof Monster)) {
					m.hurtServer(serverLevel, serverLevel.damageSources().magic(), 18.0F);
					serverLevel.sendParticles(ParticleTypes.GLOW, m.getX(), m.getY() + 1.0, m.getZ(), 14, 0.4, 0.6, 0.4, 0.05);
				}
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.9F, 1.3F);
			}
		}

		if (sanityCost > 0.0F && player instanceof ServerPlayer sp && !player.isCreative()) {
			PlayerRpg.drainSanity(sp, sanityCost);
		}
		if (stack.isDamageableItem()) {
			stack.hurtAndBreak(1, player, hand);
		}
		player.getCooldowns().addCooldown(stack, cooldownTicks);
		return InteractionResult.SUCCESS;
	}

	private static void pushBack(LivingEntity target, double strength, double dx, double dz) {
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0E-4) return;
		target.push((dx / len) * strength * 0.55, 0.25, (dz / len) * strength * 0.55);
		target.hurtMarked = true;
	}

	private boolean consumeCost(Player player, ItemStack stack) {
		if (player.isCreative()) return true;
		if ("SELF".equals(costItemId)) {
			stack.shrink(1);
			return true;
		}
		if (costItemId == null || costItemId.isEmpty()) return true;
		Item costItem = BuiltInRegistries.ITEM.getValue(BackroomsMod.id(costItemId));
		if (costItem == Items.AIR) return true;
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && s.is(costItem)) {
				s.shrink(1);
				return true;
			}
		}
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		ChatFormatting color = switch (tier) {
			case 1 -> ChatFormatting.WHITE;
			case 2 -> ChatFormatting.AQUA;
			case 3 -> ChatFormatting.LIGHT_PURPLE;
			case 4 -> ChatFormatting.GOLD;
			default -> ChatFormatting.GRAY;
		};
		adder.accept(Component.translatable("tooltip.noclip_backrooms.magic_tier", tier).withStyle(color));
		if (costItemId != null && !costItemId.isEmpty() && !"SELF".equals(costItemId)) {
			adder.accept(Component.translatable("tooltip.noclip_backrooms.magic_cost",
					Component.translatable("item.noclip_backrooms." + costItemId)).withStyle(ChatFormatting.AQUA));
		}
		if (loreKey != null && !loreKey.isEmpty()) {
			adder.accept(Component.translatable(loreKey).withStyle(ChatFormatting.GRAY));
		}
	}
}
