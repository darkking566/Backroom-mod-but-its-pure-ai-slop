package dev.noclip.backrooms.core.rpg;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.beastlevel5.BeastOfLevel5;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

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
	private static final Identifier MOD_AGI_MOVE = BackroomsMod.id("rpg_agility_move");
	private static final Identifier MOD_AGI_ATK = BackroomsMod.id("rpg_agility_attack");
	private static final Identifier MOD_VIT_HP = BackroomsMod.id("rpg_vitality_health");
	private static final Identifier MOD_VIT_TOUGH = BackroomsMod.id("rpg_vitality_toughness");

	private static final int SANITY_TICK_INTERVAL = 10; // 0.5 seconds

	private PlayerRpg() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(AllocateStatPayload.TYPE, AllocateStatPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AllocateStatPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			PlayerStats before = get(player);
			if (before.statPoints() <= 0) return;
			PlayerStats after = before.allocate(payload.stat());
			set(player, after);
			if (payload.stat() == PlayerStats.StatKind.VITALITY) {
				player.heal(2.0F);
			}
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.55F, 1.35F);
		});

		ServerPlayerEvents.JOIN.register(player -> applyAttributes(player, get(player)));

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			PlayerStats current = get(newPlayer);
			PlayerStats refilled = current.withSanity(current.maxSanity());
			set(newPlayer, refilled);
			newPlayer.setHealth(newPlayer.getMaxHealth());
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(source.getEntity() instanceof ServerPlayer killer)) return;
			if (!(entity instanceof Monster)) return;
			int reward;
			if (entity instanceof BeastOfLevel5) {
				reward = 250;
			} else if (entity.getType().builtInRegistryHolder().key().identifier().getNamespace().equals(BackroomsMod.MOD_ID)) {
				reward = 35;
			} else {
				reward = 18;
			}
			grantXp(killer, reward);
		});

		ServerTickEvents.END_SERVER_TICK.register(PlayerRpg::tickVitalityAndSanity);
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
		float effective = amount * (1.0F - before.sanityDrainResistance());
		PlayerStats after = before.addSanity(-effective);
		if (Math.abs(after.sanity() - before.sanity()) > 0.01F) {
			player.setAttached(STATS, after);
		}
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
		updateModifier(player, Attributes.MOVEMENT_SPEED, MOD_AGI_MOVE, stats.moveSpeedBonus(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		updateModifier(player, Attributes.ATTACK_SPEED, MOD_AGI_ATK, stats.attackSpeedBonus(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		updateModifier(player, Attributes.MAX_HEALTH, MOD_VIT_HP, stats.maxHealthBonus(), AttributeModifier.Operation.ADD_VALUE);
		updateModifier(player, Attributes.ARMOR_TOUGHNESS, MOD_VIT_TOUGH, stats.armorToughnessBonus(), AttributeModifier.Operation.ADD_VALUE);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
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

			// Vitality passive health regeneration (0.5s tick step; requires non-starving food >= 6).
			if (stats.vitality() > 0 && player.getHealth() < player.getMaxHealth() && player.getFoodData().getFoodLevel() >= 6) {
				player.heal(stats.healthRegenPerSec() * 0.5F);
			}

			boolean inBackrooms = level.dimension().identifier().getNamespace().equals(BackroomsMod.MOD_ID);
			float delta;
			if (!inBackrooms) {
				// Overworld safe base steadily restores sanity, accelerated by SAN stat.
				float baseRec = level.dimension() == Level.OVERWORLD ? 0.85F : 0.25F;
				delta = (baseRec + stats.passiveSanityRegenPerSec() * 0.5F) * stats.sanityRegenMultiplier();
			} else {
				BlockPos eyePos = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
				int blockLight = level.getBrightness(LightLayer.BLOCK, eyePos);
				float envDelta;
				if (blockLight <= 4) {
					envDelta = -0.52F;
				} else if (blockLight <= 8) {
					envDelta = -0.14F;
				} else if (blockLight >= 11) {
					envDelta = 0.24F;
				} else {
					envDelta = 0.0F;
				}

				List<Monster> nearby = level.getEntitiesOfClass(Monster.class, new AABB(eyePos).inflate(12.0));
				float monsterDrain = Math.min(1.05F, nearby.size() * 0.30F);
				// SAN passive composure helps stabilize sanity whenever not in pitch darkness next to monsters.
				float passiveCalm = (blockLight > 4 && nearby.isEmpty()) ? stats.passiveSanityRegenPerSec() * 0.5F : 0.0F;
				float raw = envDelta - monsterDrain + passiveCalm;
				delta = raw < 0.0F
						? raw * (1.0F - stats.sanityDrainResistance())
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
								})))
		));
	}
}
