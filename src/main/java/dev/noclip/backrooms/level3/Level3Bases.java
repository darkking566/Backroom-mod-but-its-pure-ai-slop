package dev.noclip.backrooms.level3;

import dev.noclip.backrooms.core.people.People;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.level3.Level3Events.Placed;
import dev.noclip.backrooms.level3.Level3Layout.Tile;
import dev.noclip.backrooms.level3.Level3Plan.Hall;
import dev.noclip.backrooms.level3.Level3Plan.HallKind;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Pillager;
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
	private static final Map<UUID, Long> LAST_VISITED = new HashMap<>();

	private Level3Bases() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level3Bases::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> LAST_VISITED.clear());
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		ServerLevel level = server.getLevel(Level3.LEVEL_3);
		if (level == null) return;
		long seed = Level3ChunkGenerator.planSeed(level.getChunkSource().randomState());
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			if (now % 20 == 0) checkArrivalMessage(seed, player);
			if (now % INTERVAL == 0) populateNear(level, seed, player);
		}
	}

	private static void checkArrivalMessage(long seed, ServerPlayer player) {
		Tile tile = Level3Layout.tile(seed, player.getBlockX(), player.getBlockZ());
		if (tile.kind() != Level3Layout.Kind.HALL || tile.hall() == null) return;
		HallKind kind = tile.hall().kind();
		String key = switch (kind) {
			case BASE -> "message.noclip_backrooms.level3.enter_base_gamma";
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
		if (spot == null) return 0;
		Person person = People.type(look).create(level, EntitySpawnReason.NATURAL);
		if (person == null) return 0;
		person.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
		person.setPersistenceRequired();
		person.setCustomName(Component.literal(name));
		person.addTag(STAFF_TAG);
		if (role != null) person.addTag(role.tag());
		level.addFreshEntity(person);
		return 1;
	}
}
