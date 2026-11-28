package dev.noclip.backrooms.core.rpg;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.items.BackroomsItems;
import dev.noclip.backrooms.monsters.beastlevel5.BeastOfLevel5;
import dev.noclip.backrooms.weapons.BackroomsFirearmItem;
import dev.noclip.backrooms.weapons.BackroomsMeleeItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Core cross-level RPG stats, leveling, sanity drain/recovery, and C2S stat-allocation handler.
 * Player sanity is synced solely to the owning client so hallucinations remain 100% client-side.
 */
public final class PlayerRpg {
	public static final AttachmentType<PlayerStats> STATS = AttachmentRegistry.create(
			BackroomsMod.id("player_stats"),
			builder -> builder
					.initializer(PlayerStats::initial)
					.persistent(PlayerStats.CODEC)
					.copyOnDeath()
					.syncWith(PlayerStats.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
	);

	private static final Identifier MOD_STR = BackroomsMod.id("rpg_strength");
	private static final Identifier MOD_STR_KNOCKBACK = BackroomsMod.id("rpg_strength_knockback");
	private static final Identifier MOD_STR_HEAVY_ATK = BackroomsMod.id("rpg_strength_heavy_speed");
	private static final Identifier MOD_AGI_MOVE = BackroomsMod.id("rpg_agility_move");
	private static final Identifier MOD_AGI_ATK = BackroomsMod.id("rpg_agility_attack");
	private static final Identifier MOD_VIT_HP = BackroomsMod.id("rpg_vitality_health");
	private static final Identifier MOD_VIT_TOUGH = BackroomsMod.id("rpg_vitality_toughness");
	private static final Identifier MOD_BACKROOMS_HP = BackroomsMod.id("backrooms_frailty");
	private static final Identifier MOD_SCOUT_ADS = BackroomsMod.id("badge_scout_ads_speed");
	private static final Set<String> PROTECTED_LOOT = Set.of(
			"evidence_photo", "photo_album", "topology_tape", "meg_squad_report", "wretch_cure");
	/** Half of the player's base 20 health, everywhere, once they have been in the Backrooms (docs/企劃書/1-遊戲玩法/README.md). */
	public static final double FRAILTY = -10.0;
	/** In the Backrooms, eating well heals at a quarter of the usual speed (mixin.FoodDataBackroomsRegen). */
	public static final float BACKROOMS_FOOD_REGEN = 0.25F;
	/** Vitality's regeneration waits this long after the player was last hurt: you heal by hiding, not mid-fight. */
	private static final int REGEN_DELAY_TICKS = 100;
	private static final Map<UUID, Long> LAST_HURT = new HashMap<>();

	private static final int SANITY_TICK_INTERVAL = 10; // 0.5 seconds
	/** The Backrooms drain rates below are per second (docs/企劃書/2-玩家能力值/README.md); one step is half a second. */
	private static final float STEP_SECONDS = SANITY_TICK_INTERVAL / 20.0F;

	public static final SoundEvent SANITY_LOW_DRONE = registerSound("sanity.low_drone");
	public static final SoundEvent SANITY_HALLUCINATION_CUE = registerSound("sanity.hallucination_cue");

	private static SoundEvent registerSound(String path) {
		Identifier id = BackroomsMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	private PlayerRpg() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(AllocateStatPayload.TYPE, AllocateStatPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AllocateStatPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			PlayerStats before = get(player);
			if (!before.canAllocate(payload.stat())) return;
			PlayerStats after = before.allocate(payload.stat());
			set(player, after);
			if (payload.stat() == PlayerStats.StatKind.VITALITY) {
				player.heal(2.0F);
			}
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.55F, 1.35F);
		});

		ServerPlayerEvents.JOIN.register(player -> applyAttributes(player, get(player)));
		// The first fall into the Backrooms halves their health from then on.
		dev.noclip.backrooms.core.floors.Floors.onArrive((player, level, run, newLevel) -> applyAttributes(player, get(player)));
		ServerPlayerEvents.LEAVE.register(player -> LAST_HURT.remove(player.getUUID()));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F) {
				LAST_HURT.put(player.getUUID(), player.level().getGameTime());
			}
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			PlayerStats current = get(newPlayer);
			PlayerStats refilled = current.withSanity(current.maxSanity());
			set(newPlayer, refilled);
			newPlayer.setHealth(newPlayer.getMaxHealth());
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(source.getEntity() instanceof ServerPlayer killer)) return;
			if (!(entity instanceof Monster)) return;
			// Killing is not the way to grow (docs/企劃書/1-遊戲玩法/README.md): most experience comes from surviving (SurvivalXp).
			int reward;
			if (entity instanceof BeastOfLevel5 || entity instanceof dev.noclip.backrooms.monsters.shepherdsnake.ShepherdSnake) {
				reward = 150;
			} else if (entity.getType().builtInRegistryHolder().key().identifier().getNamespace().equals(BackroomsMod.MOD_ID)) {
				reward = 12;
			} else {
				reward = 4;
			}
			grantXp(killer, reward);
		});

		ServerTickEvents.END_SERVER_TICK.register(PlayerRpg::tickDynamicAndEffects);
		ServerTickEvents.END_SERVER_TICK.register(PlayerRpg::tickVitalityAndSanity);
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (drops.isEmpty()) return;
			ServerLevel level = context.getLevel();
			ServerPlayer player = resolveLootPlayer(level, context);
			if (player == null) return;
			PlayerStats stats = get(player);
			String ns = table.unwrapKey().map(k -> k.identifier().getNamespace()).orElse("");
			String path = table.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
			if (path.startsWith("blocks/") && BackroomsMod.MOD_ID.equals(ns) && Home.inBackrooms(level.dimension())) {
				double bonus = stats.engSalvageBonusChance() + Badges.extraLootChance(player);
				if (bonus > 0.0 && context.getRandom().nextDouble() < bonus) {
					grantBonusDrop(drops, context.getRandom());
				}
			} else if (path.contains("chest") || path.contains("crate") || path.startsWith("entities/")) {
				double luck = stats.luckLootChance() + Badges.extraLootChance(player);
				if (luck > 0.0 && context.getRandom().nextDouble() < luck) {
					grantBonusDrop(drops, context.getRandom());
					if ((path.contains("chest") || path.contains("crate")) && context.getRandom().nextFloat() < 0.25F) {
						drops.add(new ItemStack(BackroomsItems.LOST_OLD_COIN, 1));
					}
				}
			}
		});
		registerCommands();
	}

	public static PlayerStats get(Player player) {
		return player.getAttachedOrCreate(STATS);
	}

	public static void set(ServerPlayer player, PlayerStats stats) {
		player.setAttached(STATS, stats);
		applyAttributes(player, stats);
	}

	public static void restoreSanity(ServerPlayer player, float amount) {
		if (amount <= 0.0F) return;
		PlayerStats before = get(player);
		PlayerStats after = before.addSanity(amount);
		if (Math.abs(after.sanity() - before.sanity()) > 0.01F) {
			player.setAttached(STATS, after);
		}
	}

	public static void drainSanity(ServerPlayer player, float amount) {
		if (amount <= 0.0F) return;
		PlayerStats before = get(player);
		float effective = amount * (1.0F - before.totalSanityDrainResistance());
		PlayerStats after = before.addSanity(-effective);
		if (Math.abs(after.sanity() - before.sanity()) > 0.01F) {
			player.setAttached(STATS, after);
		}
	}

	/**
	 * Applies a Forbidden Scroll (Option 1): first read unlocks the hidden stat at 1;
	 * repeat scrolls of an already-unlocked stat grant +1 free point directly.
	 */
	public static void applyForbiddenScroll(ServerPlayer player, PlayerStats.StatKind kind, ItemStack stack) {
		PlayerStats before = get(player);
		boolean already = before.isUnlocked(kind);
		PlayerStats after = before.applyScroll(kind);
		set(player, after);
		String shortKey = switch (kind) {
			case LUCK -> "ui.noclip_backrooms.rpg.luk";
			case PSI -> "ui.noclip_backrooms.rpg.psy";
			default -> "ui.noclip_backrooms.rpg.eng";
		};
		player.sendOverlayMessage(Component.translatable(
				already ? "message.noclip_backrooms.scroll.boosted_stat" : "message.noclip_backrooms.scroll.unlocked_stat",
				Component.translatable(shortKey), after.valueOf(kind)));
		player.level().sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 30, 0.6, 0.8, 0.6, 0.1);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.9F, already ? 1.35F : 1.15F);
		if (!player.isCreative()) stack.shrink(1);
		player.getCooldowns().addCooldown(stack, 20);
	}

	public static void grantXp(ServerPlayer player, int amount) {
		if (amount <= 0) return;
		PlayerStats before = get(player);
		PlayerStats after = before.addXp(amount);
		set(player, after);
		if (after.level() > before.level()) {
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.rpg.level_up", after.level(), after.statPoints()));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.75F, 1.15F);
		}
	}

	public static void applyAttributes(ServerPlayer player, PlayerStats stats) {
		updateModifier(player, Attributes.ATTACK_DAMAGE, MOD_STR, stats.attackDamageBonus(), AttributeModifier.Operation.ADD_VALUE);
		updateModifier(player, Attributes.ATTACK_KNOCKBACK, MOD_STR_KNOCKBACK,
				stats.knockbackBonus() * Badges.knockbackMultiplier(player), AttributeModifier.Operation.ADD_VALUE);
		updateModifier(player, Attributes.MOVEMENT_SPEED, MOD_AGI_MOVE, stats.moveSpeedBonus(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		updateModifier(player, Attributes.ATTACK_SPEED, MOD_AGI_ATK, stats.attackSpeedBonus(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		updateModifier(player, Attributes.MAX_HEALTH, MOD_VIT_HP, stats.maxHealthBonus(), AttributeModifier.Operation.ADD_VALUE);
		// Once they have been in the Backrooms, for good: the Wretch Cure ends the rejection, not this (the user, 2026-11-28).
		boolean frail = dev.noclip.backrooms.core.Home.entered(player);
		updateModifier(player, Attributes.MAX_HEALTH, MOD_BACKROOMS_HP, frail ? FRAILTY : 0.0,
				AttributeModifier.Operation.ADD_VALUE);
		updateModifier(player, Attributes.ARMOR_TOUGHNESS, MOD_VIT_TOUGH, 0.0, AttributeModifier.Operation.ADD_VALUE);
		applyDynamicAttributes(player, stats);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void applyDynamicAttributes(ServerPlayer player, PlayerStats stats) {
		ItemStack main = player.getMainHandItem();
		double heavyBonus = (main.getItem() instanceof BackroomsMeleeItem melee && melee.isHeavyWeapon())
				? stats.heavySwingSpeedBonus() : 0.0;
		updateModifier(player, Attributes.ATTACK_SPEED, MOD_STR_HEAVY_ATK, heavyBonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

		double adsBonus = (Badges.active(player) == Badges.Badge.TACTICAL_SCOUT
				&& main.getItem() instanceof BackroomsFirearmItem && player.isUsingItem()) ? 0.85 : 0.0;
		updateModifier(player, Attributes.MOVEMENT_SPEED, MOD_SCOUT_ADS, adsBonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	private static void tickDynamicAndEffects(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % 5 != 0) return;
		boolean shortenHarmful = tick % 20 == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator()) continue;
			PlayerStats stats = get(player);
			applyDynamicAttributes(player, stats);
			if (shortenHarmful && !player.isCreative()) {
				shortenHarmfulEffects(player, stats);
			}
		}
	}

	private static void shortenHarmfulEffects(ServerPlayer player, PlayerStats stats) {
		float red = stats.harmfulDurationReduction();
		if (red < 0.02F) return;
		List<MobEffectInstance> active = new ArrayList<>(player.getActiveEffects());
		for (MobEffectInstance inst : active) {
			if (inst.getEffect().value().getCategory() != MobEffectCategory.HARMFUL || inst.isInfiniteDuration()) continue;
			int cur = inst.getDuration();
			if (cur <= 25) continue;
			int extraCut = Math.min(cur - 20, Math.max(1, Math.round(20.0F * (red / (1.0F - red)))));
			if (extraCut <= 0) continue;
			int nextDur = cur - extraCut;
			player.removeEffect(inst.getEffect());
			player.addEffect(new MobEffectInstance(
					inst.getEffect(), nextDur, inst.getAmplifier(), inst.isAmbient(), inst.isVisible(), inst.showIcon()));
		}
	}

	private static ServerPlayer resolveLootPlayer(ServerLevel level, net.minecraft.world.level.storage.loot.LootContext context) {
		var attacker = context.getOptionalParameter(LootContextParams.ATTACKING_ENTITY);
		if (attacker instanceof ServerPlayer sp) return sp;
		var killer = context.getOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER);
		if (killer instanceof ServerPlayer sp) return sp;
		var target = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
		if (target instanceof ServerPlayer sp) return sp;
		Vec3 origin = context.getOptionalParameter(LootContextParams.ORIGIN);
		if (origin == null && target != null) origin = target.position();
		if (origin == null) return null;
		final Vec3 center = origin;
		return level.players().stream()
				.filter(p -> !p.isSpectator() && p.position().distanceToSqr(center) <= 48.0 * 48.0)
				.min(Comparator.comparingDouble(p -> p.position().distanceToSqr(center)))
				.orElse(null);
	}

	private static void grantBonusDrop(List<ItemStack> drops, net.minecraft.util.RandomSource random) {
		List<ItemStack> eligible = new ArrayList<>();
		for (ItemStack s : drops) {
			if (s.isEmpty() || s.getMaxStackSize() <= 1 || s.getCount() >= s.getMaxStackSize()) continue;
			String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
			if (!PROTECTED_LOOT.contains(path)) eligible.add(s);
		}
		if (!eligible.isEmpty()) {
			ItemStack picked = eligible.get(random.nextInt(eligible.size()));
			picked.grow(1);
		}
	}

	public static boolean inBackrooms(ServerPlayer player) {
		return dev.noclip.backrooms.core.Home.inBackrooms(player.level().dimension());
	}

	private static void updateModifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) return;
		if (Math.abs(amount) < 1.0E-5) {
			instance.removeModifier(id);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	private static void tickVitalityAndSanity(MinecraftServer server) {
		if (server.getTickCount() % SANITY_TICK_INTERVAL != 0) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isCreative() || player.isSpectator()) continue;
			ServerLevel level = player.level();
			PlayerStats stats = get(player);

			// Vitality passive health regeneration (0.5s tick step; not starving, and not hurt in the last 5s / 3.5s).
			long sinceHurt = level.getGameTime() - LAST_HURT.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
			if (stats.vitality() > 0 && player.getHealth() < player.getMaxHealth() && player.getFoodData().getFoodLevel() >= 6
					&& sinceHurt >= Badges.regenDelayTicks(player)) {
				player.heal(stats.healthRegenPerSec() * 0.5F);
			}

			boolean inBackrooms = dev.noclip.backrooms.core.Home.inBackrooms(level.dimension());
			float delta;
			if (!inBackrooms) {
				int rejectionStage = dev.noclip.backrooms.core.trip.Pull.stage(player);
				if (rejectionStage >= 1) {
					// Wretch Rejection in the Overworld: sanity stops recovering and drains steadily!
					float rejectDrain = switch (rejectionStage) {
						case 1 -> -0.08F;
						case 2 -> -0.16F;
						case 3 -> -0.28F;
						default -> -0.42F;
					};
					delta = rejectDrain * (1.0F - stats.totalSanityDrainResistance());
				} else {
					// Overworld safe base steadily restores sanity, accelerated by SAN stat.
					float baseRec = dev.noclip.backrooms.core.Home.isHome(level.dimension()) ? 0.85F : 0.25F;
					delta = (baseRec + stats.passiveSanityRegenPerSec() * 0.5F) * stats.sanityRegenMultiplier();
				}
			} else {
				BlockPos eyePos = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
				int blockLight = level.getBrightness(LightLayer.BLOCK, eyePos);
				float envDelta;
				// Half the old rates, with half the old maximum: about as long to break as before (the user, 2026-11-25).
				if (blockLight <= 4) {
					envDelta = -0.26F * STEP_SECONDS;
				} else if (blockLight <= 8) {
					envDelta = -0.07F * STEP_SECONDS;
				} else if (blockLight >= 11) {
					envDelta = 0.24F;
				} else {
					envDelta = 0.0F;
				}

				List<Monster> nearby = level.getEntitiesOfClass(Monster.class, new AABB(eyePos).inflate(12.0));
				float monsterDrain = Math.min(0.5F, nearby.size() * 0.15F) * STEP_SECONDS;
				// SAN passive composure helps stabilize sanity whenever not in pitch darkness next to monsters.
				float passiveCalm = (blockLight > 4 && nearby.isEmpty()) ? stats.passiveSanityRegenPerSec() * 0.5F : 0.0F;
				float scholarBonus = 0.0F;
				float scholarPerSec = Badges.passiveAnomalySanityPerSec(player);
				if (scholarPerSec > 0.0F && nearby.stream().noneMatch(m -> m.getTarget() == player)) {
					scholarBonus = scholarPerSec * 0.5F;
				}
				float raw = envDelta - monsterDrain + passiveCalm + scholarBonus;
				delta = raw < 0.0F
						? raw * (1.0F - stats.totalSanityDrainResistance())
						: raw * stats.sanityRegenMultiplier();
			}

			if (Math.abs(delta) < 1.0E-4F) continue;
			PlayerStats updated = stats.addSanity(delta);
			if (Math.abs(updated.sanity() - stats.sanity()) >= 0.05F) {
				player.setAttached(STATS, updated);
			}
		}
	}

	private static void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("backrooms")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("sanity")
								.then(Commands.literal("set")
										.then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 500.0F))
												.executes(ctx -> {
													ServerPlayer player = ctx.getSource().getPlayerOrException();
													float val = FloatArgumentType.getFloat(ctx, "value");
													PlayerStats updated = get(player).withSanity(val);
													set(player, updated);
													ctx.getSource().sendSuccess(() -> Component.translatable(
															"message.noclip_backrooms.rpg.sanity_set",
															Math.round(updated.sanity()), Math.round(updated.maxSanity())), false);
													return 1;
												})))
								.then(Commands.literal("get").executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayerOrException();
									PlayerStats stats = get(player);
									ctx.getSource().sendSuccess(() -> Component.translatable(
											"message.noclip_backrooms.rpg.sanity_set",
											Math.round(stats.sanity()), Math.round(stats.maxSanity())), false);
									return 1;
								})))
						.then(Commands.literal("stats")
								.then(Commands.literal("addxp")
										.then(Commands.argument("amount", IntegerArgumentType.integer(1, 100000))
												.executes(ctx -> {
													ServerPlayer player = ctx.getSource().getPlayerOrException();
													int amount = IntegerArgumentType.getInteger(ctx, "amount");
													grantXp(player, amount);
													PlayerStats now = get(player);
													ctx.getSource().sendSuccess(() -> Component.translatable(
															"message.noclip_backrooms.rpg.stats_updated",
															now.level(), now.xp(), now.xpNeeded(), now.statPoints()), false);
													return 1;
												})))
								.then(Commands.literal("addpoints")
										.then(Commands.argument("amount", IntegerArgumentType.integer(1, 100))
												.executes(ctx -> {
													ServerPlayer player = ctx.getSource().getPlayerOrException();
													int amount = IntegerArgumentType.getInteger(ctx, "amount");
													PlayerStats now = get(player).addStatPoints(amount);
													set(player, now);
													ctx.getSource().sendSuccess(() -> Component.translatable(
															"message.noclip_backrooms.rpg.stats_updated",
															now.level(), now.xp(), now.xpNeeded(), now.statPoints()), false);
													return 1;
												})))
								.then(Commands.literal("reset").executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayerOrException();
									PlayerStats reset = PlayerStats.initial();
									set(player, reset);
									ctx.getSource().sendSuccess(() -> Component.translatable(
											"message.noclip_backrooms.rpg.stats_reset"), false);
									return 1;
								}))
								.then(Commands.literal("unlock").then(Commands.argument("stat", StringArgumentType.word())
										.executes(ctx -> {
											ServerPlayer player = ctx.getSource().getPlayerOrException();
											String raw = StringArgumentType.getString(ctx, "stat").toLowerCase(java.util.Locale.ROOT);
											PlayerStats.StatKind kind = switch (raw) {
												case "luck" -> PlayerStats.StatKind.LUCK;
												case "psi", "psy" -> PlayerStats.StatKind.PSI;
												case "eng", "engineering", "tech" -> PlayerStats.StatKind.ENGINEERING;
												default -> null;
											};
											if (kind == null) return 0;
											PlayerStats after = get(player).applyScroll(kind);
											set(player, after);
											return 1;
										}))))
		));
	}
}
