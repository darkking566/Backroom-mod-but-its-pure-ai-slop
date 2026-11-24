package dev.noclip.backrooms.weapons;

import dev.noclip.backrooms.BackroomsMod;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Functional Backrooms firearm. Right-click consumes matching ammo (free in Creative), fires high-speed
 * raycast/pellet/cone attacks with distinct ballistic effects, muzzle particles, and audio cues.
 */
public final class BackroomsFirearmItem extends Item {
	public enum FireMode {
		STANDARD,
		SILENT_NAIL,
		TASER_STUN,
		FLAMETHROWER,
		SHOTGUN,
		ACID_SHOTGUN,
		PIERCING_AP,
		RAILGUN_ARC,
		SONIC_PULSE
	}

	private final int tier;
	private final List<String> ammoIds;
	private final float damage;
	private final int pellets;
	private final float range;
	private final int cooldownTicks;
	private final float spread;
	private final FireMode mode;
	private final String loreKey;

	public BackroomsFirearmItem(Properties properties, int tier, List<String> ammoIds, float damage,
			int pellets, float range, int cooldownTicks, float spread, FireMode mode, String loreKey) {
		super(properties);
		this.tier = tier;
		this.ammoIds = ammoIds;
		this.damage = damage;
		this.pellets = pellets;
		this.range = range;
		this.cooldownTicks = cooldownTicks;
		this.spread = spread;
		this.mode = mode;
		this.loreKey = loreKey;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
		if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;

		String usedAmmo = consumeAmmo(player);
		if (usedAmmo == null) {
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.4F);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.firearm.no_ammo"));
			return InteractionResult.FAIL;
		}

		boolean phosphorBonus = "ammo_556_phosphor".equals(usedAmmo);
		boolean acidAmmo = "ammo_12g_acid".equals(usedAmmo) || mode == FireMode.ACID_SHOTGUN;
		boolean apAmmo = "ammo_308_ap".equals(usedAmmo) || mode == FireMode.PIERCING_AP || mode == FireMode.RAILGUN_ARC;

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();

		if (mode == FireMode.SONIC_PULSE) {
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.9F, 1.3F);
			for (int step = 1; step <= 12; step++) {
				Vec3 p = eye.add(look.scale(step * 1.1));
				serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
				for (LivingEntity e : serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(1.8), ent -> ent != player)) {
					e.hurtServer(serverLevel, serverLevel.damageSources().sonicBoom(player), damage);
					Vec3 push = e.position().subtract(player.position());
					double len = Math.max(1.0E-4, Math.hypot(push.x, push.z));
					e.push((push.x / len) * 0.9, 0.3, (push.z / len) * 0.9);
					e.hurtMarked = true;
				}
			}
		} else if (mode == FireMode.FLAMETHROWER) {
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.9F, 0.85F);
			for (int step = 1; step <= 10; step++) {
				Vec3 p = eye.add(look.scale(step * 0.95));
				serverLevel.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 6, 0.35, 0.35, 0.35, 0.04);
				for (LivingEntity e : serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(1.35), ent -> ent != player)) {
					e.hurtServer(serverLevel, serverLevel.damageSources().onFire(), damage);
					e.igniteForSeconds(5);
				}
			}
		} else {
			playFireSound(serverLevel, player);
			ParticleOptions tracer = switch (mode) {
				case RAILGUN_ARC -> ParticleTypes.ELECTRIC_SPARK;
				case TASER_STUN -> ParticleTypes.ELECTRIC_SPARK;
				case ACID_SHOTGUN -> ParticleTypes.ITEM_SLIME;
				default -> phosphorBonus ? ParticleTypes.END_ROD : ParticleTypes.SMOKE;
			};

			for (int p = 0; p < pellets; p++) {
				Vec3 dir = look.add(
						(player.getRandom().nextDouble() - 0.5) * spread,
						(player.getRandom().nextDouble() - 0.5) * spread,
						(player.getRandom().nextDouble() - 0.5) * spread
				).normalize();

				Vec3 end = eye.add(dir.scale(range));
				BlockHitResult blockHit = serverLevel.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
				double maxDist = (blockHit.getType() == HitResult.Type.BLOCK && !apAmmo)
						? blockHit.getLocation().distanceTo(eye) : range;

				Set<Integer> hitIds = new HashSet<>();
				double stepSize = 0.65;
				for (double d = 1.0; d <= maxDist; d += stepSize) {
					Vec3 pos = eye.add(dir.scale(d));
					if (p == 0 && ((int) (d / stepSize)) % 3 == 0) {
						serverLevel.sendParticles(tracer, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
					}
					List<LivingEntity> hits = serverLevel.getEntitiesOfClass(LivingEntity.class,
							new AABB(pos, pos).inflate(0.55), e -> e != player && e.isAlive() && !hitIds.contains(e.getId()));
					if (!hits.isEmpty()) {
						LivingEntity target = hits.getFirst();
						hitIds.add(target.getId());
						float dealt = damage;
						if (mode == FireMode.ACID_SHOTGUN && "ammo_12g_acid".equals(usedAmmo)) dealt *= 1.35F;
						if (phosphorBonus) {
							String path = target.getType().builtInRegistryHolder().key().identifier().getPath();
							if (path.contains("smiler") || path.contains("duller") || path.contains("bacteria")) {
								dealt *= 2.0F;
							}
							target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0));
						}
						target.invulnerableTime = 0;
						target.hurtServer(serverLevel, apAmmo ? serverLevel.damageSources().magic() : serverLevel.damageSources().playerAttack(player), dealt);
						if (mode == FireMode.TASER_STUN) {
							target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 70, 5));
							target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 70, 2));
						}
						if (acidAmmo) {
							target.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
							target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
						}
						if (mode == FireMode.RAILGUN_ARC) {
							serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1.0, target.getZ(), 18, 0.6, 0.7, 0.6, 0.15);
						}
						if (!apAmmo) break;
					}
				}
			}
		}

		// Loud firearms alert nearby hostile monsters in the Backrooms
		if (mode != FireMode.SILENT_NAIL && mode != FireMode.TASER_STUN) {
			for (Monster m : serverLevel.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(24.0))) {
				if (m.getTarget() == null) m.setTarget(player);
			}
		}

		stack.hurtAndBreak(1, player, hand);
		player.getCooldowns().addCooldown(stack, cooldownTicks);
		return InteractionResult.SUCCESS;
	}

	private void playFireSound(ServerLevel level, Player player) {
		switch (mode) {
			case SILENT_NAIL -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.55F, 1.7F);
			case TASER_STUN -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.4F, 1.9F);
			case SHOTGUN, ACID_SHOTGUN -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.85F, 1.4F);
			case RAILGUN_ARC -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7F, 1.8F);
			case PIERCING_AP -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 1.6F);
			default -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.9F, 1.1F);
		}
	}

	private String consumeAmmo(Player player) {
		if (player.isCreative()) return ammoIds.isEmpty() ? "" : ammoIds.getFirst();
		Inventory inv = player.getInventory();
		for (String ammoId : ammoIds) {
			Item ammoItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(ammoId.contains(":") ? ammoId : BackroomsMod.MOD_ID + ":" + ammoId));
			if (ammoItem == Items.AIR) continue;
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				if (!s.isEmpty() && s.is(ammoItem)) {
					s.shrink(1);
					return ammoId;
				}
			}
		}
		return null;
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
		adder.accept(Component.translatable("tooltip.noclip_backrooms.firearm_stats",
				tier, String.format("%.1f", damage * pellets)).withStyle(color));
		if (!ammoIds.isEmpty()) {
			Component ammoName = Component.translatable("item.noclip_backrooms." + ammoIds.getFirst());
			adder.accept(Component.translatable("tooltip.noclip_backrooms.firearm_ammo", ammoName).withStyle(ChatFormatting.YELLOW));
		}
		if (loreKey != null && !loreKey.isEmpty()) {
			adder.accept(Component.translatable(loreKey).withStyle(ChatFormatting.GRAY));
		}
	}
}
