package dev.noclip.backrooms.core.trip;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.people.People;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.skinstealer.SkinStealer;
import dev.noclip.backrooms.props.BackroomsLootChestBlock;
import dev.noclip.backrooms.props.BackroomsProps;
import dev.noclip.backrooms.props.NoClipPropBlock;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
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
	/** Entity tag marking a Skin-Stealer disguised as a human figure during a Mimics trip. */
	public static final String MIMIC_FIGURE_TAG = "noclip_backrooms_mimic_figure";
	private static final List<String> MIMIC_LOOKS = List.of("meg_patrol", "colonist", "tom", "aiko");
	/** How often a mimic waits in an unopened crate. */
	static final float MIMIC_CHANCE = 0.25F;
	private static final int MAX_RED_MIST_ROOMS = 6;
	private static final Set<UUID> TOLD = new HashSet<>();
	private static final java.util.Map<UUID, net.minecraft.world.phys.Vec3> LAST = new java.util.HashMap<>();
	private static final Set<Long> RED_MIST_ROOMS = new HashSet<>();

	private Anomalies() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(AnomalyPayload.TYPE, AnomalyPayload.STREAM_CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			TOLD.clear();
			LAST.clear();
			RED_MIST_ROOMS.clear();
		});
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
			if (!isMimicCrate((ServerLevel) level, pos)) return InteractionResult.PASS;
			revealMimicCrate((ServerLevel) level, serverPlayer, pos, false);
			return InteractionResult.SUCCESS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) return InteractionResult.PASS;
			if (entity instanceof Person person && person.entityTags().contains(MIMIC_FIGURE_TAG)) {
				revealMimicFigure((ServerLevel) level, person, serverPlayer, false);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof Person person && person.entityTags().contains(MIMIC_FIGURE_TAG)
					&& person.level() instanceof ServerLevel sl) {
				ServerPlayer attacker = source.getEntity() instanceof ServerPlayer sp ? sp : null;
				revealMimicFigure(sl, person, attacker, false);
				return false;
			}
			return true;
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
		RED_MIST_ROOMS.clear();
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
					if (RED_MIST_ROOMS.size() < MAX_RED_MIST_ROOMS && player.getRandom().nextInt(22) == 0) {
						BlockPos made = infestRedRoomNear(level, player.blockPosition());
						if (made != null) {
							player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.anomaly.red_room_formed").withStyle(ChatFormatting.DARK_RED));
						}
					}
				}
				case MIMICS -> {
					tickMimicFigures(level, player);
				}
			}
		}
	}

	/** Deterministic check so the Bio-Scanner and opening a crate always agree on which crates are mimics. */
	public static boolean isMimicCrate(ServerLevel level, BlockPos pos) {
		if (!Home.inBackrooms(level.dimension()) || current(level.getServer()) != Affix.MIMICS) return false;
		if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity crate) || crate.getLootTable() == null) return false;
		long h = pos.asLong() ^ (level.getSeed() * 0x9E3779B97F4A7C15L) ^ 0x517CC1B727220A95L;
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		h = h ^ (h >>> 31);
		return ((h >>> 8) & 1023L) < (long) (1024.0F * MIMIC_CHANCE);
	}

	/** Watches existing disguised human figures near the player and occasionally spawns new lures in the corridors. */
	private static void tickMimicFigures(ServerLevel level, ServerPlayer player) {
		List<Person> near = level.getEntitiesOfClass(Person.class, player.getBoundingBox().inflate(28),
				p -> p.isAlive() && p.entityTags().contains(MIMIC_FIGURE_TAG));
		for (Person figure : near) {
			figure.getLookControl().setLookAt(player, 25, 25);
			if (figure.distanceToSqr(player) <= 12.25) {
				revealMimicFigure(level, figure, player, false);
			}
		}
		if (near.size() < 2 && player.getRandom().nextInt(20) == 0) {
			var rnd = player.getRandom();
			for (int attempt = 0; attempt < 16; attempt++) {
				int dx = rnd.nextInt(33) - 16;
				int dz = rnd.nextInt(33) - 16;
				if (dx * dx + dz * dz < 81) continue;
				BlockPos at = player.blockPosition().offset(dx, 0, dz);
				if (level.isLoaded(at) && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
						&& level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
					spawnMimicFigure(level, at);
					break;
				}
			}
		}
	}

	/** Spawns a Skin-Stealer disguised as an ordinary Backrooms wanderer/patrolman. */
	public static Person spawnMimicFigure(ServerLevel level, BlockPos pos) {
		String look = MIMIC_LOOKS.get(level.getRandom().nextInt(MIMIC_LOOKS.size()));
		Person person = People.type(look).create(level, EntitySpawnReason.EVENT);
		if (person == null) return null;
		person.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0);
		person.addTag(MIMIC_FIGURE_TAG);
		person.setPersistenceRequired();
		level.addFreshEntity(person);
		return person;
	}

	/** Drops the human disguise and replaces the figure with an active Skin-Stealer (stunned if exposed by Bio-Scanner). */
	public static SkinStealer revealMimicFigure(ServerLevel level, Person figure, ServerPlayer target, boolean stunned) {
		BlockPos pos = figure.blockPosition();
		double x = figure.getX(), y = figure.getY(), z = figure.getZ();
		float yaw = figure.getYRot();
		figure.discard();
		SkinStealer stealer = Monsters.SKIN_STEALER.create(level, EntitySpawnReason.EVENT);
		if (stealer != null) {
			stealer.snapTo(x, y, z, yaw, 0);
			if (target != null) stealer.setTarget(target);
			stealer.beginReveal();
			if (stunned) {
				stealer.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0));
				stealer.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2));
				stealer.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
			}
			level.addFreshEntity(stealer);
		}
		level.sendParticles(ParticleTypes.CRIMSON_SPORE, x, y + 1.0, z, 28, 0.5, 0.8, 0.5, 0.06);
		level.playSound(null, pos, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 0.65F, 1.55F);
		if (target != null) {
			target.sendOverlayMessage(Component.translatable(stunned
					? "message.noclip_backrooms.anomaly.mimic_exposed"
					: "message.noclip_backrooms.anomaly.mimic_figure").withStyle(stunned ? ChatFormatting.AQUA : ChatFormatting.DARK_RED));
		}
		return stealer;
	}

	/**
	 * Red Mist dynamic room infestation: converts a nearby ordinary 7x7 Backrooms room section into a
	 * Red Room enclave (red wallpaper, red fluorescent lights, red anomaly cluster, and an anomaly vault chest),
	 * never touching doors, chests, workbenches, beds, or any BlockEntity.
	 */
	public static BlockPos infestRedRoomNear(ServerLevel level, BlockPos around) {
		var rnd = level.getRandom();
		for (int attempt = 0; attempt < 18; attempt++) {
			int dx = rnd.nextInt(37) - 18;
			int dz = rnd.nextInt(37) - 18;
			if (attempt < 14 && dx * dx + dz * dz < 144) continue;
			BlockPos candidate = around.offset(dx, 0, dz);
			BlockPos res = infestRedRoomAt(level, candidate);
			if (res != null) return res;
		}
		return null;
	}

	/** Converts a specific 7x7 room patch centered at {@code center} (floor+1 level) into a Red Mist enclave. */
	public static BlockPos infestRedRoomAt(ServerLevel level, BlockPos center) {
		if (!Home.inBackrooms(level.dimension()) || !level.isLoaded(center)) return null;
		long key = ((long) Math.floorDiv(center.getX(), 16) << 32) ^ (Math.floorDiv(center.getZ(), 16) & 0xFFFFFFFFL);
		if (RED_MIST_ROOMS.contains(key)) return null;
		if (!level.getBlockState(center).isAir() || !level.getBlockState(center.below()).isFaceSturdy(level, center.below(), Direction.UP)) {
			return null;
		}
		// Never touch any existing BlockEntity (chests, props, workbenches) in the 7x7 footprint
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, 4, 3))) {
			if (level.getBlockEntity(p) != null) return null;
		}
		Block redWall = BuiltInRegistries.BLOCK.getValue(BackroomsMod.id("red_wallpaper"));
		Block redBase = BuiltInRegistries.BLOCK.getValue(BackroomsMod.id("red_wallpaper_baseboard"));
		Block redLight = BuiltInRegistries.BLOCK.getValue(BackroomsMod.id("red_fluorescent_light"));
		if (redWall == Blocks.AIR || redLight == Blocks.AIR) return null;

		int changed = 0;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-3, 0, -3), center.offset(3, 4, 3))) {
			BlockState st = level.getBlockState(p);
			if (st.isAir() || st.hasBlockEntity()) continue;
			var id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
			if (!BackroomsMod.MOD_ID.equals(id.getNamespace())) continue;
			String path = id.getPath();
			if (path.contains("door") || path.contains("exit") || path.contains("bed") || path.contains("cot")
					|| path.contains("stair") || path.contains("ladder") || path.contains("hatch") || path.contains("fence")) continue;
			if (path.contains("light") || path.contains("lamp") || path.contains("bulb")) {
				if (!CARRIED_LIGHTS.contains(path)) {
					level.setBlock(p, redLight.defaultBlockState(), Block.UPDATE_ALL);
					changed++;
				}
			} else if (path.contains("wallpaper") || path.contains("wall") || path.contains("brick") || path.contains("concrete")) {
				Block targetWall = (p.getY() == center.getY() && redBase != Blocks.AIR) ? redBase : redWall;
				level.setBlock(p, targetWall.defaultBlockState(), Block.UPDATE_ALL);
				changed++;
			}
		}
		// Place the crimson anomaly cluster at center + an anomaly vault chest beside it if open
		level.setBlock(center, BackroomsProps.RED_ANOMALY_CLUSTER.defaultBlockState()
				.setValue(NoClipPropBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos beside = center.relative(dir);
			if (level.getBlockState(beside).isAir() && level.getBlockState(beside.below()).isFaceSturdy(level, beside.below(), Direction.UP)) {
				level.setBlock(beside, BackroomsProps.ANOMALY_VAULT_CHEST.defaultBlockState()
						.setValue(BackroomsLootChestBlock.FACING, dir.getOpposite()), Block.UPDATE_ALL);
				break;
			}
		}
		// Ensure overhead red illumination so the room unmistakably glows crimson
		BlockPos ceil = center.above(3);
		if (level.getBlockState(ceil).isSolidRender() || changed == 0) {
			level.setBlock(ceil, redLight.defaultBlockState(), Block.UPDATE_ALL);
		}
		RED_MIST_ROOMS.add(key);
		level.sendParticles(ParticleTypes.CRIMSON_SPORE, center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5, 45, 2.5, 1.2, 2.5, 0.04);
		level.playSound(null, center, SoundEvents.AMBIENT_NETHER_WASTES_MOOD.value(), SoundSource.AMBIENT, 0.9F, 0.65F);
		return center.immutable();
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

	/** The crate was not a crate (stunned if exposed early by a Bio-Scanner). */
	public static SkinStealer revealMimicCrate(ServerLevel level, ServerPlayer player, BlockPos pos, boolean stunned) {
		level.removeBlock(pos, false);
		SkinStealer stealer = Monsters.SKIN_STEALER.create(level, EntitySpawnReason.EVENT);
		if (stealer != null) {
			stealer.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot() + 180, 0);
			stealer.setTarget(player);
			stealer.beginReveal();
			if (stunned) {
				stealer.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0));
				stealer.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2));
				stealer.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
			}
			level.addFreshEntity(stealer);
		}
		level.sendParticles(ParticleTypes.CRIMSON_SPORE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 24, 0.5, 0.6, 0.5, 0.05);
		level.playSound(null, pos, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 0.6F, 1.6F);
		player.sendOverlayMessage(Component.translatable(stunned
				? "message.noclip_backrooms.anomaly.mimic_exposed"
				: "message.noclip_backrooms.anomaly.mimic").withStyle(stunned ? ChatFormatting.AQUA : ChatFormatting.DARK_RED));
		return stealer;
	}
}
