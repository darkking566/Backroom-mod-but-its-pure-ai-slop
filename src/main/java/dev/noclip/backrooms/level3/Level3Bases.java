package dev.noclip.backrooms.level3;

import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.people.People;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.level3.Level3Events.Placed;
import dev.noclip.backrooms.level3.Level3Layout.Tile;
import dev.noclip.backrooms.level3.Level3Plan.Hall;
import dev.noclip.backrooms.level3.Level3Plan.HallKind;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

/**
 * Populates Level 3's four faction outposts: M.E.G. Base Gamma staff (Quartermaster & Medic),
 * B.N.T.G. Storage Facility traders (Pawnshop Buyer & Archivist), hostile U.E.C. Scylla Colony operatives,
 * and sends one-time atmosphere/security messages when a wanderer steps into each outpost.
 */
final class Level3Bases {
	static final String STAFF_TAG = "noclip_backrooms_level3_staff";
	static final String UEC_TAG = "noclip_backrooms_level3_uec";
	private static final int INTERVAL = 20 * 10;
	private static final float SNIPER_DAMAGE = 14.0F, SPRING_SANITY = 1.5F;
	private static final Map<UUID, Long> LAST_VISITED = new HashMap<>();
	private static final Map<UUID, Long> CAMERA_NOTICED = new HashMap<>();
	private static final Map<UUID, Integer> HAUNTED_COOLDOWN = new HashMap<>();
	private static final Set<BlockPos> RUBBED_PEDESTALS = new HashSet<>();
	private static final Set<BlockPos> TAKEN_NOTES = new HashSet<>();

	private Level3Bases() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level3Bases::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LAST_VISITED.clear();
			CAMERA_NOTICED.clear();
			HAUNTED_COOLDOWN.clear();
			RUBBED_PEDESTALS.clear();
			TAKEN_NOTES.clear();
		});
		// Gamma's walls hold: no digging round the checkpoint or into the locked parts.
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
				!(world instanceof ServerLevel level) || level.dimension() != Level3.LEVEL_3 || player.isCreative() || !inGamma(level, pos));
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(world instanceof ServerLevel level) || player.isCreative() || player.isSpectator() || !(entity instanceof Person)) return InteractionResult.PASS;
			if (entity.entityTags().stream().noneMatch(tag -> tag.startsWith("noclip_backrooms_gamma_"))) return InteractionResult.PASS;
			level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 2.0F, 0.6F);
			player.hurtServer(level, level.damageSources().generic(), SNIPER_DAMAGE);
			if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level3.gamma_sniper"));
			return InteractionResult.FAIL;
		});
		// Side doors (locked from outside, open from inside), A5 Sanctum pedestal rubbings, and C3 frantic notes.
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			if (!(world instanceof ServerLevel level) || level.dimension() != Level3.LEVEL_3) return InteractionResult.PASS;
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			long seed = Level3ChunkGenerator.planSeed(level.getChunkSource().randomState());
			if (state.is(Blocks.IRON_DOOR) && inGamma(level, pos)) {
				Tile tile = Level3Layout.tile(seed, pos.getX(), pos.getZ());
				if (tile.hall() != null && tile.hall().kind() == HallKind.GAMMA && tile.kind() == Level3Layout.Kind.HALL_GAP) {
					BlockPos base = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
					Direction out = state.getValue(DoorBlock.FACING);
					if (inGamma(level, sp.blockPosition())) {
						BlockPos outside = base.relative(out);
						level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.8F, 1.0F);
						sp.teleportTo(outside.getX() + 0.5, outside.getY(), outside.getZ() + 0.5);
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level3.gamma_side_out").withStyle(ChatFormatting.GRAY));
					} else {
						level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 0.7F);
						sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level3.gamma_side_locked").withStyle(ChatFormatting.RED));
					}
					return InteractionResult.SUCCESS;
				}
			}
			if (state.is(Level3Blocks.PEDESTAL)) {
				BlockPos key = pos.immutable();
				if (RUBBED_PEDESTALS.add(key)) {
					ItemStack rubbing = new ItemStack(Level3Blocks.SANCTUM_RUBBING);
					if (!sp.getInventory().add(rubbing)) sp.drop(rubbing, false);
					level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8F, 0.9F);
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level3.sanctum_rubbing").withStyle(ChatFormatting.GOLD));
					dev.noclip.backrooms.core.story.Missions.onBlockInteract(sp, "pedestal_l3", pos, "3");
				} else {
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level3.sanctum_unreadable").withStyle(ChatFormatting.GRAY));
				}
				return InteractionResult.SUCCESS;
			}
			if (state.is(Level3Blocks.MEG_WARNING_SIGN)) {
				BlockPos key = pos.immutable();
				if (TAKEN_NOTES.add(key)) {
					int page = Math.floorMod(Level3Layout.hash(seed, pos.getX(), pos.getZ(), 391), 8) + 1;
					ItemStack note = new ItemStack(Level3Blocks.FRANTIC_NOTE);
					if (!sp.getInventory().add(note)) sp.drop(note, false);
					level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.7F, 1.1F);
					sp.sendSystemMessage(Component.translatable("message.noclip_backrooms.level3.frantic_note." + page).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
					return InteractionResult.SUCCESS;
				}
			}
			return InteractionResult.PASS;
		});
		// Turn in Sanctum rubbings (A5) and frantic notes (C3) to the M.E.G. or B.N.T.G. Archivist for credits.
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			if (!(world instanceof ServerLevel level) || level.dimension() != Level3.LEVEL_3 || !(entity instanceof Person person)) return InteractionResult.PASS;
			boolean isArchivist = person.entityTags().contains("noclip_backrooms_gamma_archivist")
					|| (person.getCustomName() != null && person.getCustomName().getString().contains("Archivist"));
			if (!isArchivist) return InteractionResult.PASS;
			int rubbings = takeItem(sp, Level3Blocks.SANCTUM_RUBBING);
			int notes = takeItem(sp, Level3Blocks.FRANTIC_NOTE);
			if (rubbings == 0 && notes == 0) {
				sp.sendSystemMessage(Component.translatable("message.noclip_backrooms.level3.archivist_hint").withStyle(ChatFormatting.GRAY));
				return InteractionResult.SUCCESS;
			}
			long reward = rubbings * 80L + notes * 45L;
			Wallet.add(sp, reward);
			PlayerRpg.grantXp(sp, (int) (reward / 3));
			level.playSound(null, person.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.8F, 1.1F);
			sp.sendSystemMessage(Component.translatable("message.noclip_backrooms.level3.archivist_paid", rubbings + notes, reward)
					.withStyle(ChatFormatting.GREEN));
			return InteractionResult.SUCCESS;
		});
	}

	private static int takeItem(ServerPlayer player, net.minecraft.world.item.Item item) {
		int count = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(item)) {
				count += stack.getCount();
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
		return count;
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		ServerLevel level = server.getLevel(Level3.LEVEL_3);
		if (level == null) return;
		long seed = Level3ChunkGenerator.planSeed(level.getChunkSource().randomState());
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			if (now % 20 == 0) {
				checkArrivalMessage(seed, player);
				soak(level, seed, player);
				checkPerimeterCamera(level, seed, player);
				checkHauntedHouse(level, seed, player, now);
			}
			if (now % INTERVAL == 0) populateNear(level, seed, player);
		}
	}

	/** Whether a block is part of Base Gamma, its outer wall included. */
	static boolean inGamma(ServerLevel level, BlockPos pos) {
		Tile tile = Level3Layout.tile(Level3ChunkGenerator.planSeed(level.getChunkSource().randomState()), pos.getX(), pos.getZ());
		return tile.hall() != null && tile.hall().kind() == HallKind.GAMMA;
	}

	/** Gamma's hot spring: sitting in it, the mind settles. */
	private static void soak(ServerLevel level, long seed, ServerPlayer player) {
		Tile tile = Level3Layout.tile(seed, player.getBlockX(), player.getBlockZ());
		if (tile.hall() == null || tile.hall().kind() != HallKind.GAMMA || !player.isInWater()) return;
		PlayerRpg.restoreSanity(player, SPRING_SANITY);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.4, 0.1, 0.4, 0.01);
	}

	/** D1 perimeter cameras in Gamma's region: once spotted in a region, M.E.G. lets you know they see you. */
	private static void checkPerimeterCamera(ServerLevel level, long seed, ServerPlayer player) {
		int rx = Math.floorDiv(player.getBlockX(), Level3Plan.SIZE), rz = Math.floorDiv(player.getBlockZ(), Level3Plan.SIZE);
		if (!GammaPlan.in(seed, rx, rz) || inGamma(level, player.blockPosition())) return;
		long regionKey = ((long) rx << 32) ^ (rz & 0xFFFFFFFFL);
		if (Long.valueOf(regionKey).equals(CAMERA_NOTICED.get(player.getUUID()))) return;
		BlockPos center = player.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-8, 0, -8), center.offset(8, 4, 8))) {
			if (level.isLoaded(p) && level.getBlockState(p).is(Level3Blocks.SECURITY_CAMERA)) {
				CAMERA_NOTICED.put(player.getUUID(), regionKey);
				level.playSound(null, p, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.6F, 1.5F);
				player.sendSystemMessage(Component.translatable("message.noclip_backrooms.level3.gamma_camera_noticed").withStyle(ChatFormatting.AQUA));
				return;
			}
		}
	}

	/** Gamma's haunted house on Mall floor 2 (u 2..9, v 36..45): eerie creak and flicker when stepping inside. */
	private static void checkHauntedHouse(ServerLevel level, long seed, ServerPlayer player, int now) {
		if (now < HAUNTED_COOLDOWN.getOrDefault(player.getUUID(), 0)) return;
		Tile tile = Level3Layout.tile(seed, player.getBlockX(), player.getBlockZ());
		if (tile.hall() == null || tile.hall().kind() != HallKind.GAMMA) return;
		int y = player.getBlockY();
		if (tile.hx() >= 2 && tile.hx() <= 9 && tile.hz() >= 36 && tile.hz() <= 45
				&& y >= GammaLayout.MALL_2 + 1 && y <= GammaLayout.MALL_2 + 5) {
			HAUNTED_COOLDOWN.put(player.getUUID(), now + 20 * 45);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), Level3Sounds.PARANOIA, SoundSource.AMBIENT, 0.6F, 0.75F);
			player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, true, false, false));
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level3.gamma_haunted").withStyle(ChatFormatting.DARK_PURPLE));
		}
	}

	private static void checkArrivalMessage(long seed, ServerPlayer player) {
		Tile tile = Level3Layout.tile(seed, player.getBlockX(), player.getBlockZ());
		if (tile.kind() != Level3Layout.Kind.HALL || tile.hall() == null) return;
		HallKind kind = tile.hall().kind();
		String key = switch (kind) {
			case BASE -> "message.noclip_backrooms.level3.enter_base_gamma";
			case GAMMA -> "message.noclip_backrooms.level3.enter_gamma";
			case BNTG_STORAGE -> "message.noclip_backrooms.level3.enter_bntg";
			case BLUE_SALVATION -> "message.noclip_backrooms.level3.enter_blue_salvation";
			case SCYLLA_COLONY -> "message.noclip_backrooms.level3.enter_scylla";
			default -> null;
		};
		if (key == null) return;
		long hallKey = ((long) Math.floorDiv(player.getBlockX(), Level3Plan.SIZE) << 32)
				^ (Math.floorDiv(player.getBlockZ(), Level3Plan.SIZE) & 0xFFFFFFFFL) ^ ((long) kind.ordinal() << 56);
		Long prev = LAST_VISITED.put(player.getUUID(), hallKey);
		if (prev == null || prev != hallKey) {
			player.sendOverlayMessage(Component.translatable(key));
		}
	}

	static int populateNear(ServerLevel level, long seed, ServerPlayer player) {
		int spawned = 0;
		for (Placed placed : Level3Events.hallsNear(seed, player.getBlockX(), player.getBlockZ(), 48)) {
			Hall hall = placed.hall();
			HallKind kind = hall.kind();
			if (kind == HallKind.GAMMA) {
				spawned += populateGamma(level, placed);
				continue;
			}
			if (kind != HallKind.BASE && kind != HallKind.BNTG_STORAGE && kind != HallKind.SCYLLA_COLONY) continue;
			int x0 = placed.x0() + 1, z0 = placed.z0() + 1;
			int w = Level3Halls.width(hall), d = Level3Halls.depth(hall);
			AABB box = new AABB(x0, Level3ChunkGenerator.FLOOR_Y, z0, x0 + w, Level3ChunkGenerator.ROOF_Y, z0 + d);
			if (kind == HallKind.SCYLLA_COLONY) {
				if (level.getDifficulty() == Difficulty.PEACEFUL) continue;
				if (!level.getEntitiesOfClass(Pillager.class, box, m -> m.entityTags().contains(UEC_TAG)).isEmpty()) continue;
				for (int i = 0; i < 2; i++) {
					BlockPos spot = freeSpot(level, x0, z0, w, d, i);
					if (spot == null) continue;
					Pillager op = EntityTypes.PILLAGER.create(level, EntitySpawnReason.NATURAL);
					if (op == null) continue;
					op.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
					op.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.NATURAL, null);
					op.setPersistenceRequired();
					op.setCustomName(Component.literal("U.E.C. Operative"));
					op.addTag(UEC_TAG);
					level.addFreshEntity(op);
					spawned++;
				}
				continue;
			}
			if (!level.getEntitiesOfClass(Person.class, box).isEmpty()) continue;
			if (kind == HallKind.BASE) {
				spawned += spawnStaff(level, freeSpot(level, x0, z0, w, d, 0), "meg_patrol", "M.E.G. Quartermaster", ShopRole.QUARTERMASTER);
				spawned += spawnStaff(level, freeSpot(level, x0, z0, w, d, 1), "medic", "M.E.G. Medic", ShopRole.SUPPLIER);
			} else {
				spawned += spawnStaff(level, freeSpot(level, x0, z0, w, d, 0), "trader", "B.N.T.G. Pawnshop Trader", ShopRole.BUYER);
				spawned += spawnStaff(level, freeSpot(level, x0, z0, w, d, 1), "colonist", "B.N.T.G. Archivist", null);
			}
		}
		return spawned;
	}

	/** Everyone in Gamma has a place of their own; each is filled in as soon as its chunk is there and nobody stands in it. */
	private static int populateGamma(ServerLevel level, Placed placed) {
		int spawned = 0;
		AABB box = new AABB(placed.x0(), 0, placed.z0(), placed.x0() + GammaLayout.W, Level3ChunkGenerator.HEIGHT, placed.z0() + GammaLayout.D);
		for (GammaLayout.Spot spot : GammaLayout.PEOPLE) {
			BlockPos at = new BlockPos(placed.x0() + spot.u(), spot.y(), placed.z0() + spot.v());
			// Entities load a moment after their chunk: asking before then would find nobody and spawn a second one.
			if (!level.isLoaded(at) || !level.areEntitiesLoaded(ChunkPos.pack(at))) continue;
			String tag = "noclip_backrooms_gamma_" + spot.id();
			// Anywhere in Gamma, not just at the spot: people get pushed about.
			if (!level.getEntitiesOfClass(Person.class, box, p -> p.entityTags().contains(tag)).isEmpty()) continue;
			if (spawnStaff(level, at, spot.look(), spot.name(), spot.role() == null ? null : ShopRole.byId(spot.role()), tag) > 0) spawned++;
		}
		return spawned;
	}

	private static BlockPos freeSpot(ServerLevel level, int x0, int z0, int w, int d, int index) {
		int y = Level3ChunkGenerator.FLOOR_Y + 1;
		int cx = x0 + w / 2, cz = z0 + d / 2;
		for (int ring = 0; ring <= Math.max(w, d); ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					int x = cx + dx, z = cz + dz;
					if (x < x0 + 1 || x >= x0 + w - 1 || z < z0 + 1 || z >= z0 + d - 1) continue;
					if (index > 0 && ((x + z) & 1) == 0) continue;
					BlockPos pos = new BlockPos(x, y, z);
					if (level.isLoaded(pos) && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
						return pos;
					}
				}
			}
		}
		return null;
	}

	private static int spawnStaff(ServerLevel level, BlockPos spot, String look, String name, ShopRole role) {
		return spawnStaff(level, spot, look, name, role, null);
	}

	private static int spawnStaff(ServerLevel level, BlockPos spot, String look, String name, ShopRole role, String tag) {
		if (spot == null) return 0;
		Person person = People.type(look).create(level, EntitySpawnReason.NATURAL);
		if (person == null) return 0;
		person.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
		person.setPersistenceRequired();
		person.setCustomName(Component.literal(name));
		person.addTag(STAFF_TAG);
		if (role != null) person.addTag(role.tag());
		if (tag != null) person.addTag(tag);
		level.addFreshEntity(person);
		return 1;
	}
}
