package dev.noclip.backrooms.core.field;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.quota.Quota;
import dev.noclip.backrooms.core.quota.QuotaData;
import dev.noclip.backrooms.core.trip.Anomalies;
import dev.noclip.backrooms.core.trip.Trips;
import dev.noclip.backrooms.level0p1.Level0p1;
import dev.noclip.backrooms.level0p1.ZenithBlackouts;
import dev.noclip.backrooms.level1.Blackouts;
import dev.noclip.backrooms.level1.Level1;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Field evidence for M.E.G.'s quota (docs/企劃書/1-遊戲玩法/README.md, "高風險現場取證"):
 * <ul>
 * <li>the camera: a photo of whatever is in the frame within sixteen blocks, worth more the closer and the worse the
 * subject, three times as much when it is this cycle's photo target; its flash draws the light-seekers (smilers,
 * deathmoths) from far around and anything close;</li>
 * <li>the mapping beacon: set down at least 300 blocks from where the trip came in, it scans for twenty seconds, beeping
 * and flashing and drawing every creature near; left standing to the end, it gives a topology tape of the level. Sold,
 * the tape pays well and maps the level for good: from then on M.E.G. sends people there for nothing.</li>
 * </ul>
 * Photos and tapes are sold to the buyer like anything else; what they are worth is in them (core.trade.Shops asks here).
 */
public final class Evidence {
	static final double CAMERA_RANGE = 16, CLOSE = 8, FLASH_RANGE = 24, NEAR_FLASH = 8;
	static final int MIN_DISTANCE = 300, SCAN_TICKS = 400;
	/** What a photo of each creature is worth; anything else hostile 25; a photo of nothing 5. */
	static final Map<String, Integer> SUBJECTS = Map.ofEntries(Map.entry("smiler", 60), Map.entry("hound", 40), Map.entry("skin_stealer", 80),
			Map.entry("deathmoth", 45), Map.entry("clump", 50), Map.entry("faceling", 35), Map.entry("duller", 70), Map.entry("wretch", 55),
			Map.entry("partygoer", 90), Map.entry("bacteria", 75), Map.entry("shepherd_snake", 85), Map.entry("bone_thief", 80));
	/** Scene photos: special Backrooms events and anomalous landmarks in frame. */
	static final Set<String> SCENE_IDS = Set.of("blackout_moment", "red_room_altar", "anomaly_vault", "electrical_hazard");
	private static final Set<String> RED_ALTAR_BLOCKS = Set.of("red_anomaly_cluster", "red_fluorescent_light", "red_wallpaper");
	private static final Set<String> VAULT_BLOCKS = Set.of("anomaly_vault_chest", "anomaly_altar", "crystal_vein_node", "uec_apparatus", "jerry_mural", "purple_brick");
	private static final Set<String> HAZARD_BLOCKS = Set.of("sparking_outlet", "angel_statue", "black_sludge", "singularity");
	/** One photo target a cycle, in turn (creatures and notable scenes). */
	static final List<String> TARGETS = List.of("smiler", "hound", "blackout_moment", "deathmoth", "red_room_altar", "skin_stealer", "clump", "faceling");
	private static final Map<GlobalPos, Scan> SCANS = new HashMap<>();

	private record SceneShot(String id, int baseValue, double distance) {
	}

	private record Scan(UUID owner, int start, String level, int floor) {
	}

	private Evidence() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> SCANS.clear());
		ServerTickEvents.END_SERVER_TICK.register(Evidence::tick);
	}

	/** This cycle's photo target (an entity id path or scene id), worth three times as much. */
	public static String target(MinecraftServer server) {
		return TARGETS.get(Math.floorMod(Quota.data(server).cycle() - 1, TARGETS.size()));
	}

	/** Localized display name of this cycle's photo target (creature or anomalous scene). */
	public static Component targetName(MinecraftServer server) {
		String id = target(server);
		return SCENE_IDS.contains(id)
				? Component.translatable("scene.noclip_backrooms." + id)
				: Component.translatable("entity.noclip_backrooms." + id);
	}

	/** Whether the buyer values this item by what is in it (photos, tapes). */
	public static boolean valued(Item item) {
		return item == FieldKit.PHOTO || item == FieldKit.TAPE;
	}

	/** What the buyer pays for this photo or tape. */
	public static long value(MinecraftServer server, ItemStack stack) {
		CompoundTag tag = tag(stack);
		if (stack.is(FieldKit.PHOTO)) return tag.getIntOr("value", 5);
		if (stack.is(FieldKit.TAPE)) {
			String level = tag.getStringOr("level", "");
			return Quota.data(server).mapped(level) ? 40 : 200 + 20L * tag.getIntOr("floor", 1);
		}
		return 0;
	}

	/** Sold: a tape maps its level for good. */
	public static void sold(MinecraftServer server, ServerPlayer player, ItemStack stack) {
		if (!stack.is(FieldKit.TAPE)) return;
		String level = tag(stack).getStringOr("level", "");
		QuotaData data = Quota.data(server);
		if (!level.isEmpty() && !data.mapped(level)) {
			data.map(level);
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.field.mapped", level).withStyle(ChatFormatting.AQUA));
		}
	}

	private static CompoundTag tag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	/** The camera: a photo of what is in the frame, and a flash that is seen. */
	static final class Camera extends Item {
		Camera(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack camera = player.getItemInHand(hand);
			if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
			if (player.getCooldowns().isOnCooldown(camera)) return InteractionResult.FAIL;
			LivingEntity subject = subject(serverPlayer);
			String id;
			int value;
			Component name;
			if (subject != null) {
				if (subject.entityTags().contains(Anomalies.MIMIC_FIGURE_TAG)) {
					id = "skin_stealer";
					value = SUBJECTS.getOrDefault(id, 80);
					name = Component.translatable("scene.noclip_backrooms.mimic_figure");
				} else {
					id = BuiltInRegistries.ENTITY_TYPE.getKey(subject.getType()).getPath();
					value = SUBJECTS.getOrDefault(id, subject instanceof Monster ? 25 : 5);
					name = subject.getType().getDescription();
				}
				if (subject.distanceTo(player) <= CLOSE) value = value * 3 / 2;
			} else {
				SceneShot scene = detectScene(server, serverPlayer);
				if (scene != null) {
					id = scene.id();
					value = scene.baseValue();
					if (scene.distance() <= CLOSE) value = value * 3 / 2;
					name = Component.translatable("scene.noclip_backrooms." + id);
				} else {
					id = "scene";
					value = 5;
					name = Component.translatable("item.noclip_backrooms.evidence_photo.scene");
				}
			}
			boolean target = id.equals(target(server.getServer()));
			if (target) value *= 3;
			String where = Floors.key(level);
			CompoundTag tag = new CompoundTag();
			tag.putString("subject", id);
			tag.putString("level", where == null ? "" : where);
			tag.putInt("value", value);
			ItemStack photo = new ItemStack(FieldKit.PHOTO);
			photo.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			photo.set(DataComponents.CUSTOM_NAME, Component.translatable("item.noclip_backrooms.evidence_photo.of", name,
					where == null ? "?" : where, value).withStyle(style -> style.withItalic(false)));
			if (!player.getInventory().add(photo)) player.drop(photo, false);
			dev.noclip.backrooms.core.guild.Contracts.onPhoto(serverPlayer, id, where == null ? "" : where);
			flash(server, serverPlayer);
			camera.hurtAndBreak(1, player, hand);
			player.getCooldowns().addCooldown(camera, 40);
			serverPlayer.sendOverlayMessage(Component.translatable(target ? "message.noclip_backrooms.field.photo_target" : "message.noclip_backrooms.field.photo",
					name, value).withStyle(target ? ChatFormatting.GOLD : ChatFormatting.WHITE));
			return InteractionResult.SUCCESS;
		}

		/** Detects notable Backrooms scenes in frame when no creature is the primary subject. */
		private static SceneShot detectScene(ServerLevel level, ServerPlayer player) {
			if (!Home.inBackrooms(level.dimension())) return null;
			// 1. Live blackout moment in Level 0 (domino blackout), Level 1, or Level 0.1
			if ((level.dimension() == dev.noclip.backrooms.level0.Level0.LEVEL_0
					&& dev.noclip.backrooms.level0.Level0MicroEvents.isDominoBlackout(player))
					|| (level.dimension() == Level1.LEVEL_1 && Blackouts.isBlackout())
					|| (level.dimension() == Level0p1.LEVEL_0_1 && ZenithBlackouts.isBlackout())) {
				return new SceneShot("blackout_moment", 85, 4.0);
			}
			Vec3 eye = player.getEyePosition();
			Vec3 look = player.getLookAngle();
			double minDot = Math.cos(Math.toRadians(28));
			BlockPos origin = player.blockPosition();
			int r = (int) CAMERA_RANGE;

			SceneShot best = null;
			int bestScore = 0;
			int redBlocksSeen = 0;
			double closestRedDist = CAMERA_RANGE;
			boolean brokenLightInDark = false;
			double closestDarkDist = CAMERA_RANGE;

			for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -5, -r), origin.offset(r, 6, r))) {
				BlockState state = level.getBlockState(pos);
				if (state.isAir()) continue;
				var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
				if (!BackroomsMod.MOD_ID.equals(key.getNamespace())) continue;
				String path = key.getPath();

				boolean isRedCluster = path.equals("red_anomaly_cluster");
				boolean isRedBlock = RED_ALTAR_BLOCKS.contains(path);
				boolean isVault = VAULT_BLOCKS.contains(path);
				boolean isHazard = HAZARD_BLOCKS.contains(path);
				boolean isDeadFixture = path.contains("broken_") || path.contains("flickering_") || path.contains("dead_");
				if (!isRedCluster && !isRedBlock && !isVault && !isHazard && !isDeadFixture) continue;

				Vec3 center = Vec3.atCenterOf(pos);
				Vec3 to = center.subtract(eye);
				double dist = to.length();
				if (dist < 0.8 || dist > CAMERA_RANGE) continue;
				if (to.normalize().dot(look) < minDot) continue;
				if (!hasBlockSight(level, eye, center, pos, player)) continue;

				if (isRedCluster) {
					int score = 110;
					if (score > bestScore || (score == bestScore && best != null && dist < best.distance())) {
						bestScore = score;
						best = new SceneShot("red_room_altar", 110, dist);
					}
				} else if (isRedBlock) {
					redBlocksSeen++;
					closestRedDist = Math.min(closestRedDist, dist);
				} else if (isVault) {
					int score = 75;
					if (score > bestScore || (score == bestScore && best != null && dist < best.distance())) {
						bestScore = score;
						best = new SceneShot("anomaly_vault", 75, dist);
					}
				} else if (isHazard) {
					int score = 65;
					if (score > bestScore || (score == bestScore && best != null && dist < best.distance())) {
						bestScore = score;
						best = new SceneShot("electrical_hazard", 65, dist);
					}
				} else if (isDeadFixture && level.getMaxLocalRawBrightness(origin) <= 5
						&& Anomalies.current(level.getServer()) == Anomalies.Affix.STATIC) {
					brokenLightInDark = true;
					closestDarkDist = Math.min(closestDarkDist, dist);
				}
			}
			if (best == null && redBlocksSeen >= 3) {
				best = new SceneShot("red_room_altar", 110, closestRedDist);
			}
			if (best == null && brokenLightInDark) {
				best = new SceneShot("blackout_moment", 85, closestDarkDist);
			}
			return best;
		}

		private static boolean hasBlockSight(ServerLevel level, Vec3 eye, Vec3 targetCenter, BlockPos targetPos, ServerPlayer player) {
			var hit = level.clip(new ClipContext(eye, targetCenter, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
			return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(targetPos);
		}

		/** The nearest living thing (not a player) near the middle of the frame, in plain sight. */
		private static LivingEntity subject(ServerPlayer player) {
			Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
			LivingEntity best = null;
			double bestDistance = Double.MAX_VALUE;
			for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(CAMERA_RANGE),
					e -> e != player && !(e instanceof Player) && e.isAlive())) {
				Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
				double distance = to.length();
				if (distance > CAMERA_RANGE || distance < 0.5) continue;
				// In the frame: the line of sight passes through it, or it is near the middle.
				boolean onLine = e.getBoundingBox().inflate(0.4).clip(eye, eye.add(look.scale(CAMERA_RANGE))).isPresent();
				if (!onLine && to.normalize().dot(look) < Math.cos(Math.toRadians(25))) continue;
				if (!player.hasLineOfSight(e)) continue;
				if (distance < bestDistance) {
					best = e;
					bestDistance = distance;
				}
			}
			return best;
		}

		/** The flash: everything close turns, and the light-seekers come from far around. */
		private static void flash(ServerLevel level, ServerPlayer player) {
			Vec3 eye = player.getEyePosition();
			level.sendParticles(ParticleTypes.END_ROD, eye.x + player.getLookAngle().x, eye.y, eye.z + player.getLookAngle().z, 24, 0.2, 0.2, 0.2, 0.15);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 1.0F, 1.8F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.8F, 2.0F);
			for (Monster monster : level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(FLASH_RANGE), Monster::isAlive)) {
				String id = BuiltInRegistries.ENTITY_TYPE.getKey(monster.getType()).getPath();
				boolean seeker = id.equals("smiler") || id.equals("deathmoth");
				if (seeker || monster.distanceTo(player) <= NEAR_FLASH) monster.setTarget(player);
			}
		}
	}

	static final class Photo extends Item {
		Photo(Properties properties) {
			super(properties);
		}
	}

	static final class Tape extends Item {
		Tape(Properties properties) {
			super(properties);
		}
	}

	/** The mapping beacon: refuses to be set down too near where the trip came in; otherwise starts its scan. */
	static final class BeaconItem extends BlockItem {
		BeaconItem(Block block, Properties properties) {
			super(block, properties);
		}

		@Override
		public InteractionResult place(BlockPlaceContext context) {
			if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level)) return super.place(context);
			String where = Floors.key(level);
			Trips.Entry entry = player.getAttached(Trips.ENTRY);
			BlockPos at = context.getClickedPos();
			String refusal = null;
			int distance = 0;
			if (where == null) refusal = "message.noclip_backrooms.field.beacon_not_here";
			else if (entry == null || !entry.dimension().equals(level.dimension().identifier().toString())) refusal = "message.noclip_backrooms.field.beacon_no_entry";
			else {
				// As walked: Level 0 is a torus and carries players round it (level0.Level0Space).
				int atX = dev.noclip.backrooms.level0.Level0Space.walkedX(player, at.getX());
				int atZ = dev.noclip.backrooms.level0.Level0Space.walkedZ(player, at.getZ());
				distance = (int) Math.sqrt(Math.pow(entry.pos().getX() - atX, 2) + Math.pow(entry.pos().getZ() - atZ, 2));
				if (distance < MIN_DISTANCE) refusal = "message.noclip_backrooms.field.beacon_too_near";
			}
			if (refusal != null) {
				player.sendOverlayMessage(Component.translatable(refusal, distance, MIN_DISTANCE).withStyle(ChatFormatting.RED));
				return InteractionResult.FAIL;
			}
			InteractionResult result = super.place(context);
			if (result.consumesAction()) {
				SCANS.put(GlobalPos.of(level.dimension(), at.immutable()), new Scan(player.getUUID(), level.getServer().getTickCount(), where,
						Math.max(1, Floors.of(player).floor())));
				player.sendSystemMessage(Component.translatable("message.noclip_backrooms.field.beacon_started", SCAN_TICKS / 20).withStyle(ChatFormatting.AQUA));
			}
			return result;
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		int now = server.getTickCount();
		for (Iterator<Map.Entry<GlobalPos, Scan>> it = SCANS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<GlobalPos, Scan> entry = it.next();
			ServerLevel level = server.getLevel(entry.getKey().dimension());
			BlockPos pos = entry.getKey().pos();
			Scan scan = entry.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(scan.owner());
			if (level == null || !level.isLoaded(pos) || !level.getBlockState(pos).is(FieldKit.BEACON_BLOCK)) {
				it.remove();
				if (owner != null) owner.sendSystemMessage(Component.translatable("message.noclip_backrooms.field.beacon_lost").withStyle(ChatFormatting.RED));
				continue;
			}
			level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.5F, 2.0F);
			level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 10, 0.4, 0.6, 0.4, 0.08);
			ServerPlayer near = owner != null && owner.level() == level ? owner : null;
			for (Monster monster : level.getEntitiesOfClass(Monster.class, new AABB(pos).inflate(40), m -> m.isAlive() && m.getTarget() == null)) {
				if (near != null) monster.setTarget(near);
				else monster.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
			}
			if (now - scan.start() < SCAN_TICKS) continue;
			it.remove();
			level.removeBlock(pos, false);
			CompoundTag tag = new CompoundTag();
			tag.putString("level", scan.level());
			tag.putInt("floor", scan.floor());
			ItemStack tape = new ItemStack(FieldKit.TAPE);
			tape.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			tape.set(DataComponents.CUSTOM_NAME, Component.translatable("item.noclip_backrooms.topology_tape.of", scan.level())
					.withStyle(style -> style.withItalic(false)));
			level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, tape));
			level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, new ItemStack(FieldKit.BEACON)));
			level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.4F);
			if (owner != null) {
				owner.sendSystemMessage(Component.translatable("message.noclip_backrooms.field.beacon_done", scan.level()).withStyle(ChatFormatting.AQUA));
				dev.noclip.backrooms.core.guild.Contracts.onTape(owner, scan.level());
			}
		}
	}

	/** For tests and the GM: finish every scan now. */
	public static void finishScans(MinecraftServer server) {
		SCANS.replaceAll((pos, scan) -> new Scan(scan.owner(), server.getTickCount() - SCAN_TICKS, scan.level(), scan.floor()));
	}

	/** For tests: whether a scan is running at a spot. */
	public static boolean scanning(ServerLevel level, BlockPos pos) {
		return SCANS.containsKey(GlobalPos.of(level.dimension(), pos));
	}

	/** Whether this player is where evidence counts (the Backrooms). */
	static boolean inBackrooms(Player player) {
		return Home.inBackrooms(player.level().dimension());
	}
}
