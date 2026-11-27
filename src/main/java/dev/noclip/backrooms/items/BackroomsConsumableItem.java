package dev.noclip.backrooms.items;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.rpg.PlayerStats;
import dev.noclip.backrooms.core.trip.Pull;
import java.util.Set;
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

/** Functional survival consumables, utility gear, geodes, scrolls, extraction anchor, and the Backrooms Compendium. */
public final class BackroomsConsumableItem extends Item {
	public enum Kind {
		COMPENDIUM,
		FIELD_BANDAGE,
		ALMOND_ELIXIR,
		BATTERY_PACK,
		UV_FLASHLIGHT,
		PHOSPHOR_FLARE,
		SHRAPNEL_MINE,
		OLD_BANDAID,
		EXPIRED_PAINKILLERS,
		EXPIRED_CANNED_FOOD,
		RATION_BISCUIT,
		IMPROVISED_CALMING_TEA,
		ROUGH_BANDAGE,
		STERILE_BANDAGE,
		AGED_ALMOND_RESERVE,
		REALITY_STABILIZER_T1,
		REALITY_SERUM_T2,
		REALITY_ANCHOR_CORE,
		WRETCH_CURE,
		MOLOTOV_COCKTAIL,
		TACTICAL_CORNER_MIRROR,
		HOMEWARD_ANCHOR,
		LOST_OLD_COIN,
		GEODE_SAFE,
		GEODE_DRIVE,
		FORBIDDEN_SCROLL_LUCK,
		FORBIDDEN_SCROLL_PSY,
		FORBIDDEN_SCROLL_TECH
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
				if (player instanceof ServerPlayer sp) {
					PlayerRpg.restoreSanity(sp, 85.0F);
					Pull.applyMinorRelief(sp, 25.0F, 12_000L);
				}
				server.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 18, 0.5, 0.7, 0.5, 0.05);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 0.9F, 1.15F);
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 40);
			}
			case BATTERY_PACK -> {
				boolean recharged = false;
				for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
					ItemStack invStack = player.getInventory().getItem(i);
					if (dev.noclip.backrooms.level0.FlashlightItem.needsCharge(invStack)) {
						dev.noclip.backrooms.level0.FlashlightItem.recharge(invStack, dev.noclip.backrooms.level0.FlashlightItem.MAX_BATTERY);
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
			case OLD_BANDAID -> {
				player.heal(2.5F);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.6F, 1.35F);
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 20);
			}
			case EXPIRED_PAINKILLERS -> {
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 180, 0));
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0));
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.8F, 1.1F);
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 60);
			}
			case EXPIRED_CANNED_FOOD -> {
				player.getFoodData().eat(4, 0.35F);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.9F, 0.95F);
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 25);
			}
			case RATION_BISCUIT -> {
				player.getFoodData().eat(6, 0.65F);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.9F, 1.15F);
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 25);
			}
			case IMPROVISED_CALMING_TEA -> {
				player.heal(2.0F);
				if (player instanceof ServerPlayer sp) PlayerRpg.restoreSanity(sp, 22.0F);
				server.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.4, 0.5, 0.4, 0.04);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 0.85F, 1.05F);
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 30);
			}
			case ROUGH_BANDAGE -> {
				player.heal(3.5F);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.75F, 1.1F);
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 25);
			}
			case STERILE_BANDAGE -> {
				player.heal(8.0F);
				player.removeEffect(MobEffects.POISON);
				player.removeEffect(MobEffects.WITHER);
				server.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.1, player.getZ(), 5, 0.4, 0.4, 0.4, 0.02);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.85F, 1.3F);
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 35);
			}
			case AGED_ALMOND_RESERVE -> {
				player.removeEffect(MobEffects.DARKNESS);
				player.removeEffect(MobEffects.NAUSEA);
				player.removeEffect(MobEffects.BLINDNESS);
				player.removeEffect(MobEffects.POISON);
				player.heal(8.0F);
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 220, 1));
				player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 0));
				if (player instanceof ServerPlayer sp) {
					PlayerRpg.restoreSanity(sp, 100.0F);
					Pull.applyMinorRelief(sp, 45.0F, 24_000L);
				}
				server.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.1, player.getZ(), 24, 0.5, 0.7, 0.5, 0.06);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 1.0F, 1.25F);
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 40);
			}
			case REALITY_STABILIZER_T1 -> {
				player.removeEffect(MobEffects.HUNGER);
				player.removeEffect(MobEffects.WEAKNESS);
				player.removeEffect(MobEffects.NAUSEA);
				if (player instanceof ServerPlayer sp) {
					PlayerRpg.restoreSanity(sp, 30.0F);
					Pull.useTier1Stabilizer(sp);
				}
				server.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.5, 0.6, 0.5, 0.04);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 0.9F, 1.2F);
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 40);
			}
			case REALITY_SERUM_T2 -> {
				player.removeEffect(MobEffects.HUNGER);
				player.removeEffect(MobEffects.WEAKNESS);
				player.removeEffect(MobEffects.DARKNESS);
				player.removeEffect(MobEffects.NAUSEA);
				player.heal(4.0F);
				if (player instanceof ServerPlayer sp) {
					PlayerRpg.restoreSanity(sp, 50.0F);
					Pull.useTier2Serum(sp);
				}
				server.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 28, 0.7, 0.8, 0.7, 0.06);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.75F, 1.35F);
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 60);
			}
			case REALITY_ANCHOR_CORE -> {
				player.removeEffect(MobEffects.HUNGER);
				player.removeEffect(MobEffects.WEAKNESS);
				player.removeEffect(MobEffects.DARKNESS);
				player.removeEffect(MobEffects.NAUSEA);
				player.heal(player.getMaxHealth());
				if (player instanceof ServerPlayer sp) {
					PlayerRpg.restoreSanity(sp, 100.0F);
					Pull.useTier3Core(sp);
				}
				server.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.1, player.getZ(), 48, 1.2, 1.0, 1.2, 0.08);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.9F, 1.1F);
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 80);
			}
			case WRETCH_CURE -> {
				if (player instanceof ServerPlayer sp) {
					dev.noclip.backrooms.core.ending.Endings.cure(sp);
				}
				server.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(), 64, 1.2, 1.2, 1.2, 0.12);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.15F);
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 100);
			}
			case MOLOTOV_COCKTAIL -> {
				Vec3 impact = player.getEyePosition().add(player.getLookAngle().scale(6.0));
				server.sendParticles(ParticleTypes.FLAME, impact.x, impact.y, impact.z, 55, 2.2, 0.8, 2.2, 0.09);
				server.sendParticles(ParticleTypes.SMOKE, impact.x, impact.y + 0.4, impact.z, 25, 1.8, 0.8, 1.8, 0.05);
				for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(3.8), ent -> ent != player)) {
					e.hurtServer(server, server.damageSources().onFire(), 10.0F);
					e.igniteForSeconds(6);
				}
				server.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.9F, 1.1F);
				server.playSound(null, impact.x, impact.y, impact.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.9F, 0.9F);
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 30);
			}
			case TACTICAL_CORNER_MIRROR -> {
				Vec3 eye = player.getEyePosition();
				Vec3 look = player.getLookAngle();
				for (int i = 1; i <= 16; i++) {
					Vec3 p = eye.add(look.scale(i * 0.95));
					for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(3.0), ent -> ent instanceof Monster)) {
						e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
						String path = e.getType().builtInRegistryHolder().key().identifier().getPath();
						if (path.contains("smiler") || path.contains("duller") || path.contains("bacteria")) {
							e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 2));
						}
					}
				}
				server.sendParticles(ParticleTypes.GLOW, eye.x + look.x, eye.y + look.y, eye.z + look.z, 10, 0.3, 0.3, 0.3, 0.02);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.5F);
				stack.hurtAndBreak(1, player, hand);
				player.getCooldowns().addCooldown(stack, 30);
			}
			case HOMEWARD_ANCHOR -> {
				// A pickup call like any other (core.trip.Extraction): hold out where you drove the anchor in, then M.E.G.
				// takes you to the institute. Only spent if the call went out.
				if (player instanceof ServerPlayer sp && dev.noclip.backrooms.core.trip.Extraction.start(sp, sp.position(), 6.0)) {
					server.sendParticles(ParticleTypes.PORTAL, sp.getX(), sp.getY() + 1.0, sp.getZ(), 45, 0.6, 1.0, 0.6, 0.12);
					server.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.homeward_anchor.used"));
					if (!sp.isCreative()) stack.shrink(1);
					sp.getCooldowns().addCooldown(stack, 60);
				}
			}
			case LOST_OLD_COIN -> {
				server.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.4, 0.3, 0.03);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.65F);
				if (player instanceof ServerPlayer sp) {
					PlayerRpg.grantXp(sp, 8);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.coin.collected"));
				}
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 8);
			}
			case GEODE_SAFE, GEODE_DRIVE -> {
				Item[] pool = kind == Kind.GEODE_SAFE
						? new Item[]{BackroomsItems.LOST_OLD_COIN, BackroomsItems.BLUEPRINT_SCRAP, BackroomsItems.STATIC_CASSETTE, BackroomsItems.STAPLER, BackroomsItems.NOCLIP_SHARD}
						: new Item[]{BackroomsItems.GREEN_CIRCUIT, BackroomsItems.RED_CIRCUIT, BackroomsItems.MICROCHIP, BackroomsItems.SCIENCE_PACK_YELLOW, BackroomsItems.FORBIDDEN_SCROLL_TECH};
				int roll = player.getRandom().nextInt(pool.length);
				int qty = (roll == 0) ? 3 : 1;
				if (player instanceof ServerPlayer sp && pool[roll].getDefaultMaxStackSize() > 1) {
					double luckBonus = PlayerRpg.get(sp).luckLootChance();
					if (luckBonus > 0.0 && player.getRandom().nextDouble() < luckBonus) {
						qty++;
					}
				}
				ItemStack reward = new ItemStack(pool[roll], qty);
				if (!player.getInventory().add(reward)) player.drop(reward, false);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 0.85F, 1.25F);
				if (player instanceof ServerPlayer sp) {
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.appraisal.wild_hint")
							.withStyle(ChatFormatting.GRAY));
				}
				if (!player.isCreative()) stack.shrink(1);
				player.getCooldowns().addCooldown(stack, 15);
			}
			case FORBIDDEN_SCROLL_LUCK, FORBIDDEN_SCROLL_PSY, FORBIDDEN_SCROLL_TECH -> {
				if (player instanceof ServerPlayer sp) {
					PlayerStats.StatKind hiddenStat = switch (kind) {
						case FORBIDDEN_SCROLL_LUCK -> PlayerStats.StatKind.LUCK;
						case FORBIDDEN_SCROLL_PSY -> PlayerStats.StatKind.PSI;
						default -> PlayerStats.StatKind.ENGINEERING;
					};
					PlayerRpg.applyForbiddenScroll(sp, hiddenStat, stack);
				}
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
