package dev.noclip.backrooms.level2;

import dev.noclip.backrooms.core.people.People;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.level2.Level2Plan.Room;
import dev.noclip.backrooms.level2.Level2Plan.RoomKind;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.world.phys.AABB;

/**
 * Populates B.N.T.G. Office Area EL3A outposts with staff ({@link People}) and places cargo minecarts along the
 * cleared narrow-gauge railway tunnels.
 */
final class Level2Bases {
	static final String STAFF_TAG = "noclip_backrooms_level2_bntg";
	static final String CART_TAG = "noclip_backrooms_level2_cart";
	static final ResourceKey<LootTable> SUPPLIES = ResourceKey.create(Registries.LOOT_TABLE, BackroomsMod.id("chests/level2_supplies"));
	private static final int INTERVAL = 20 * 10;

	private Level2Bases() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level2Bases::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL != 0) return;
		ServerLevel level = server.getLevel(Level2.LEVEL_2);
		if (level == null) return;
		long seed = Level2ChunkGenerator.planSeed(level.getChunkSource().randomState());
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			populateNearEl3a(level, seed, player);
			populateNearRail(level, seed, player);
		}
	}

	static int populateNearEl3a(ServerLevel level, long seed, ServerPlayer player) {
		int rx0 = Math.floorDiv(player.getBlockX(), Level2Plan.SIZE);
		int rz0 = Math.floorDiv(player.getBlockZ(), Level2Plan.SIZE);
		int spawned = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int rx = rx0 + dx, rz = rz0 + dz;
				Level2Plan plan = Level2Plan.of(seed, rx, rz);
				if (!plan.hasEl3a) continue;
				for (Room room : plan.rooms) {
					if (room.kind() != RoomKind.EL3A) continue;
					int cx = rx * Level2Plan.SIZE + (room.x0() + room.x1()) / 2;
					int cz = rz * Level2Plan.SIZE + (room.z0() + room.z1()) / 2;
					if (player.distanceToSqr(cx + 0.5, Level2ChunkGenerator.FLOOR_Y + 1, cz + 0.5) > 48 * 48) continue;
					BlockPos spot = new BlockPos(cx - 1, Level2ChunkGenerator.FLOOR_Y + 1, cz - 1);
					if (!level.isLoaded(spot)) continue;
					AABB box = new AABB(rx * Level2Plan.SIZE + room.x0(), Level2ChunkGenerator.FLOOR_Y,
							rz * Level2Plan.SIZE + room.z0(), rx * Level2Plan.SIZE + room.x1() + 1,
							Level2ChunkGenerator.CEILING_Y, rz * Level2Plan.SIZE + room.z1() + 1);
					if (!level.getEntitiesOfClass(Person.class, box).isEmpty()) continue;
					spawned += spawnStaff(level, spot, "trader", "B.N.T.G. Trader");
					spawned += spawnStaff(level, spot.offset(0, 0, 2), "colonist", "B.N.T.G. Clerk");
				}
			}
		}
		return spawned;
	}

	private static int spawnStaff(ServerLevel level, BlockPos spot, String look, String name) {
		if (!level.getBlockState(spot).isAir() || !level.getBlockState(spot.above()).isAir()) return 0;
		Person person = People.type(look).create(level, EntitySpawnReason.NATURAL);
		if (person == null) return 0;
		person.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
		person.setPersistenceRequired();
		person.setCustomName(Component.literal(name));
		person.addTag(STAFF_TAG);
		level.addFreshEntity(person);
		return 1;
	}

	static int populateNearRail(ServerLevel level, long seed, ServerPlayer player) {
		AABB near = new AABB(player.blockPosition()).inflate(36);
		if (!level.getEntitiesOfClass(MinecartChest.class, near).isEmpty()) return 0;
		int px = player.getBlockX(), pz = player.getBlockZ();
		for (int dx = -24; dx <= 24; dx += 2) {
			for (int dz = -24; dz <= 24; dz += 2) {
				int x = px + dx, z = pz + dz;
				if (Level2Layout.railAxis(seed, x, z) < 0) continue;
				BlockPos pos = new BlockPos(x, Level2ChunkGenerator.FLOOR_Y + 1, z);
				if (!level.isLoaded(pos)) continue;
				var cartType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("chest_minecart"));
				if (!(cartType.create(level, EntitySpawnReason.NATURAL) instanceof MinecartChest cart)) return 0;
				cart.snapTo(x + 0.5, pos.getY() + 0.0625, z + 0.5, 0, 0);
				cart.addTag(CART_TAG);
				cart.setLootTable(SUPPLIES, Level2Layout.hash(seed, x, z, 0x10C8));
				level.addFreshEntity(cart);
				return 1;
			}
		}
		return 0;
	}
}
