package dev.noclip.backrooms.core.guild;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.trade.ShopRole;
import java.util.Locale;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * The overworld base: an adventurers' guild beside every village, with merchants (core.trade) who buy what players bring
 * back from the Backrooms and sell them supplies for the next trip. See docs/guild.md.
 */
public final class Guilds {
	public static final StructureType<GuildStructure> GUILD = register("guild", () -> GuildStructure.CODEC);
	public static final StructurePieceType HALL = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, BackroomsMod.id("guild_hall"),
			(StructurePieceType.ContextlessType) GuildPiece::new);
	public static final StructurePieceType ROAD = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, BackroomsMod.id("guild_road"),
			(StructurePieceType.ContextlessType) GuildRoadPiece::new);
	/** For /locate and tests: {@code #noclip_backrooms:guilds}. */
	public static final TagKey<Structure> GUILDS = TagKey.create(Registries.STRUCTURE, BackroomsMod.id("guilds"));
	/** On everyone who belongs to a guild hall. */
	public static final String MEMBER_TAG = "noclip_backrooms_guild";

	private Guilds() {
	}

	private static <S extends Structure> StructureType<S> register(String name, StructureType<S> type) {
		return Registry.register(BuiltInRegistries.STRUCTURE_TYPE, BackroomsMod.id(name), type);
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Guilds::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			var place = Commands.literal("place");
			for (GuildPalette palette : GuildPalette.values()) {
				place.then(Commands.literal(palette.name().toLowerCase(Locale.ROOT)).executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					place(player, palette);
					return 1;
				}));
			}
			dispatcher.register(Commands.literal("backrooms").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.literal("guild").then(place)));
		});
	}

	/** /backrooms guild place <style>: a hall a few blocks in front of the player, its door toward them. */
	public static GuildPiece place(ServerPlayer player, GuildPalette palette) {
		Direction ahead = player.getDirection();
		BlockPos front = player.blockPosition().relative(ahead, 4).below();
		return GuildPiece.placeNow(player.level(), front, ahead, palette);
	}

	/** The merchants turn their heads to whoever comes up to the counter. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 4 != 0) return;
		ServerLevel level = server.overworld();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			for (Person person : level.getEntitiesOfClass(Person.class, player.getBoundingBox().inflate(7), p -> p.entityTags().contains(MEMBER_TAG))) {
				if (ShopRole.of(person) == null) continue;
				float yaw = (float) Math.toDegrees(Math.atan2(-(player.getX() - person.getX()), player.getZ() - person.getZ()));
				float turn = Mth.clamp(Mth.wrapDegrees(yaw - person.yBodyRot), -70, 70);
				person.setYHeadRot(person.yBodyRot + turn);
			}
		}
	}
}
