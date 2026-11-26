package dev.noclip.backrooms.core.trip;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.skinstealer.SkinStealer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;

/**
 * The one strange thing about this trip (docs/project/1-gameplay/README.md, "每趟隨機樓層異常現象"). Whenever the team
 * opens a new stretch of the Backrooms (core.trip.Expedition), one anomaly is drawn for the whole trip:
 * <ul>
 * <li>static overload: lights spark and burst, flashlights drain twice as fast, but electronics salvage doubles;</li>
 * <li>absolute silence: every step, door and break carries twice as far and wakes what hears it, but creatures and
 * crates give up more;</li>
 * <li>red mist: the mind goes faster, under a red haze;</li>
 * <li>mimics: some unopened crates are not crates.</li>
 * </ul>
 * From five quotas met, the commander says which one the next trip will be.
 */
public final class Anomalies {
	public enum Affix {
		STATIC, SILENCE, RED_MIST, MIMICS;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public Component title() {
			return Component.translatable("anomaly.noclip_backrooms." + id());
		}

		public static Affix byId(String id) {
			for (Affix affix : values()) if (affix.id().equals(id)) return affix;
			return null;
		}
	}

	/** Electronics a static-overloaded stretch gives twice of. */
	static final Set<String> ELECTRONICS = Set.of("noclip_backrooms:old_circuit_board", "noclip_backrooms:battery_cell",
			"noclip_backrooms:capacitor", "noclip_backrooms:flashlight_battery_pack", "noclip_backrooms:green_circuit");
	/** The players' own lights (core.field.FieldKit) are not the building's fixtures: static leaves them be. */
	static final Set<String> CARRIED_LIGHTS = Set.of("emergency_lamp", "glow_stick", "mapping_beacon");
	/** How often a mimic waits in an unopened crate. */
	static final float MIMIC_CHANCE = 0.25F;
	private static final Set<UUID> TOLD = new HashSet<>();
	private static final java.util.Map<UUID, net.minecraft.world.phys.Vec3> LAST = new java.util.HashMap<>();

	private Anomalies() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(AnomalyPayload.TYPE, AnomalyPayload.STREAM_CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
		ServerTickEvents.END_SERVER_TICK.register(Anomalies::tick);
		Floors.onArrive((player, level, run, newLevel) -> {
			send(player);
			Affix affix = current(player.level().getServer());
			if (affix != null && TOLD.add(player.getUUID())) {
				player.sendSystemMessage(Component.translatable("message.noclip_backrooms.anomaly.now", affix.title(),
						Component.translatable("anomaly.noclip_backrooms." + affix.id() + ".desc")).withStyle(ChatFormatting.DARK_PURPLE));
			}
		});
		Floors.onHome((player, run) -> send(player));
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			ServerLevel level = context.getLevel();
			if (!Home.inBackrooms(level.dimension())) return;
			Affix affix = current(level.getServer());
			if (affix == Affix.STATIC) {
				for (ItemStack stack : List.copyOf(drops)) {
					if (ELECTRONICS.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) drops.add(stack.copy());
				}
			} else if (affix == Affix.SILENCE && context.hasParameter(LootContextParams.THIS_ENTITY) && !drops.isEmpty()
					&& context.getRandom().nextFloat() < 0.5F) {
				// What a creature leaves behind: one more of something.
				drops.add(drops.get(context.getRandom().nextInt(drops.size())).copyWithCount(1));
			}
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer serverPlayer && current(level.getServer()) == Affix.SILENCE && Home.inBackrooms(level.dimension())) {
				alert((ServerLevel) level, serverPlayer, 32);
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) return InteractionResult.PASS;
			if (!Home.inBackrooms(level.dimension()) || current(level.getServer()) != Affix.MIMICS) return InteractionResult.PASS;
			BlockPos pos = hit.getBlockPos();
			if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity crate) || crate.getLootTable() == null) return InteractionResult.PASS;
			if (serverPlayer.getRandom().nextFloat() >= MIMIC_CHANCE) return InteractionResult.PASS;
			mimic((ServerLevel) level, serverPlayer, pos);
			return InteractionResult.SUCCESS;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
			var set = Commands.literal("set");
			for (Affix affix : Affix.values()) {
				set.then(Commands.literal(affix.id()).executes(ctx -> {
					Expedition.setAffix(ctx.getSource().getServer(), affix);
					for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) send(player);
					return 1;
				}));
			}
			dispatcher.register(Commands.literal("backrooms").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.literal("anomaly").then(set)));
		});
	}

	/** This trip's anomaly, or null when the team is not on one. */
	public static Affix current(MinecraftServer server) {
		return Affix.byId(Expedition.affix(server));
	}

	/** The one the next trip will bring (the commander can tell, from five quotas met). */
	public static Affix next(MinecraftServer server) {
		return Affix.byId(Expedition.nextAffix(server));
	}

	/** A new stretch opened: everyone gets told again on their next arrival. */
	static void opened(MinecraftServer server) {
		TOLD.clear();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player);
	}

	static void send(ServerPlayer player) {
		Affix affix = Home.inBackrooms(player.level().dimension()) ? current(player.level().getServer()) : null;
		ServerPlayNetworking.send(player, new AnomalyPayload(affix == null ? "" : affix.id()));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		Affix affix = current(server);
		if (affix == null) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!Home.inBackrooms(player.level().dimension()) || player.isSpectator() || player.isCreative()) continue;
			ServerLevel level = (ServerLevel) player.level();
			switch (affix) {
				case STATIC -> {
					drainFlashlight(player);
					if (player.getRandom().nextInt(12) == 0) burstLight(level, player);
				}
				case SILENCE -> {
					// Walking, let alone running, is heard; only creeping is quiet. (Moved since last second: the server's
					// player is moved by the client, so its velocity says little.)
					var at = player.position();
					var before = LAST.put(player.getUUID(), at);
					boolean moving = before != null && before.distanceToSqr(at) > 0.25;
					if (moving && !player.isShiftKeyDown()) alert(level, player, player.isSprinting() ? 32 : 20);
				}
				case RED_MIST -> {
					PlayerRpg.drainSanity(player, 1.0F);
					if (player.getRandom().nextInt(3) == 0) {
						level.sendParticles(ParticleTypes.CRIMSON_SPORE, player.getX(), player.getY() + 1, player.getZ(), 12, 4, 2, 4, 0.01);
					}
				}
				case MIMICS -> {
				}
			}
		}
	}

	/** The flashlight (Level 0's) loses a second charge each second it is lit. */
	private static void drainFlashlight(ServerPlayer player) {
		for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
			if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("noclip_backrooms:flashlight")) continue;
			CustomData data = stack.get(DataComponents.CUSTOM_DATA);
			if (data == null || !data.copyTag().getBooleanOr("lit", false)) continue;
			if (stack.getDamageValue() < stack.getMaxDamage() - 2) stack.setDamageValue(stack.getDamageValue() + 1);
		}
	}

	/** A lamp near the player sparks and dies. */
	private static void burstLight(ServerLevel level, ServerPlayer player) {
		BlockPos centre = player.blockPosition();
		var random = player.getRandom();
		for (int attempt = 0; attempt < 40; attempt++) {
			BlockPos pos = centre.offset(random.nextInt(25) - 12, random.nextInt(9) - 2, random.nextInt(25) - 12);
			BlockState state = level.getBlockState(pos);
			var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
			if (state.getLightEmission() < 8 || !key.getNamespace().equals(BackroomsMod.MOD_ID) || CARRIED_LIGHTS.contains(key.getPath())) continue;
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.9F, 1.3F);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.2);
			return;
		}
	}

	/** Whatever hostile hears the player comes for them. */
	static void alert(ServerLevel level, ServerPlayer player, double range) {
		for (Monster monster : level.getEntitiesOfClass(Monster.class, new AABB(player.blockPosition()).inflate(range),
				m -> m.isAlive() && m.getTarget() == null)) {
			monster.setTarget(player);
		}
	}

	/** The crate was not a crate. */
	private static void mimic(ServerLevel level, ServerPlayer player, BlockPos pos) {
		level.removeBlock(pos, false);
		SkinStealer stealer = Monsters.SKIN_STEALER.create(level, EntitySpawnReason.EVENT);
		if (stealer != null) {
			stealer.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot() + 180, 0);
			stealer.setTarget(player);
			level.addFreshEntity(stealer);
		}
		level.playSound(null, pos, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 0.6F, 1.6F);
		player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.anomaly.mimic").withStyle(ChatFormatting.DARK_RED));
	}
}
