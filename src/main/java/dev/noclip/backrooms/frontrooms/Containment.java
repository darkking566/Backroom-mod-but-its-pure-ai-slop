package dev.noclip.backrooms.frontrooms;

import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.story.MainQuest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/**
 * B1's containment cells (docs/企劃書/1-遊戲玩法/README.md, "M.E.G. 的秘密"): empty at first; each M.E.G. assignment
 * that brought something back (core.story.MainQuest) puts one of it in a cell, beside the cell's specimen tank, behind
 * the observation glass. They stand still and take no harm: specimens, not monsters on the loose.
 */
public final class Containment {
	public static final String TAG = "noclip_specimen";
	/** Where, round a tank, a specimen may stand (the first free block of these). */
	private static final int[][] AROUND = {{0, -1}, {1, 0}, {-1, 0}, {0, 1}, {1, -1}, {-1, -1}, {0, -2}, {2, 0}, {-2, 0}};

	private Containment() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 100 == 37) refresh(server);
		});
	}

	/** Puts every specimen M.E.G. has in its cell, if that part of B1 is loaded; takes away any that should not be there. */
	public static void refresh(MinecraftServer server) {
		ServerLevel level = server.getLevel(Home.FRONTROOMS);
		if (level == null) return;
		List<EntityType<?>> specimens = MainQuest.specimens(server);
		List<BlockPos> cells = cells();
		for (int i = 0; i < cells.size(); i++) {
			BlockPos at = cells.get(i);
			if (!level.isLoaded(at) || !level.areEntitiesLoaded(ChunkPos.pack(at))) continue;
			String tag = TAG + "_" + i;
			EntityType<?> wanted = i < specimens.size() ? specimens.get(i) : null;
			boolean present = false;
			for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(at).inflate(4), e -> e.entityTags().contains(tag))) {
				if (entity.getType() == wanted && !present) present = true;
				else entity.discard();
			}
			if (wanted == null || present) continue;
			Entity entity = wanted.create(level, EntitySpawnReason.STRUCTURE);
			if (!(entity instanceof Mob mob)) continue;
			mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			mob.setNoAi(true);
			mob.setInvulnerable(true);
			mob.setSilent(true);
			mob.setPersistenceRequired();
			mob.addTag(TAG);
			mob.addTag(tag);
			level.addFreshEntity(mob);
		}
	}

	/** Where each cell's specimen stands: beside its tank, on open floor (B1's tanks, west to east). */
	public static List<BlockPos> cells() {
		int b1 = 1, floorY = InstituteLayout.floorY(b1) + 1;
		List<InstituteFurnishing.Place> tanks = new ArrayList<>();
		for (InstituteFurnishing.Place place : InstituteFurnishing.places()) {
			if (place.floor() == b1 && place.type().id().equals("inst_specimen_tank")) tanks.add(place);
		}
		tanks.sort(Comparator.comparingInt(InstituteFurnishing.Place::x));
		List<BlockPos> cells = new ArrayList<>();
		for (InstituteFurnishing.Place tank : tanks) {
			for (int[] d : AROUND) {
				int x = tank.x() + d[0], z = tank.z() + d[1];
				char floor = InstituteLayout.plan(b1, x, z);
				// The cells are dark rooms (';') behind the observation glass.
				if ((floor != ';' && floor != '.') || InstituteFurnishing.at(b1, x, 0, z) != null || InstituteFurnishing.at(b1, x, 1, z) != null) continue;
				cells.add(new BlockPos(x, floorY, z));
				break;
			}
		}
		return cells;
	}
}
