package dev.noclip.backrooms.core;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.floors.Floors;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Where "home" is (docs/project/1-gameplay/README.md, "流程與前室總部定案"). Once someone has fallen into the
 * Backrooms, every way out takes them to the M.E.G. institute in the Frontrooms, not back to the Overworld; time runs
 * there (and in the Overworld) and stops in the Backrooms. Someone who has never been in plays the Overworld as it is.
 */
public final class Home {
	/** The Frontrooms: M.E.G.'s quarantine institute and the grounds around it. */
	public static final ResourceKey<Level> FRONTROOMS = ResourceKey.create(Registries.DIMENSION, BackroomsMod.id("frontrooms"));
	private static Consumer<ServerPlayer> receiver = Home::toWorldSpawn;

	private Home() {
	}

	/** Set by the Frontrooms: how M.E.G. receives someone who got out. */
	public static void setReceiver(Consumer<ServerPlayer> receive) {
		receiver = receive;
	}

	/** Takes a player out of the Backrooms (or wherever they are) to the institute. */
	public static void send(ServerPlayer player) {
		receiver.accept(player);
	}

	/** Not the Backrooms: the Frontrooms and the Overworld (and anything else that is not one of our levels). */
	public static boolean isHome(ResourceKey<Level> dimension) {
		return !dimension.identifier().getNamespace().equals(BackroomsMod.MOD_ID) || dimension == FRONTROOMS;
	}

	/** One of the Backrooms' levels. */
	public static boolean inBackrooms(ResourceKey<Level> dimension) {
		return !isHome(dimension);
	}

	/** Whether the player has ever been in the Backrooms (and so belongs to M.E.G. now). */
	public static boolean entered(ServerPlayer player) {
		return Floors.of(player).best() > 0;
	}

	/** Only if the Frontrooms are missing: the Overworld's spawn. */
	private static void toWorldSpawn(ServerPlayer player) {
		ServerLevel overworld = player.level().getServer().overworld();
		Vec3 spawn = Vec3.atBottomCenterOf(player.adjustSpawnLocation(overworld, overworld.getRespawnData().pos()));
		player.stopRiding();
		player.resetFallDistance();
		player.teleport(new TeleportTransition(overworld, spawn, Vec3.ZERO, 0, 0, TeleportTransition.PLAY_PORTAL_SOUND));
	}
}
