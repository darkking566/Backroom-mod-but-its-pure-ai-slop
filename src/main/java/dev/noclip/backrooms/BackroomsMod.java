package dev.noclip.backrooms;

import dev.noclip.backrooms.core.economy.Economy;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.guild.Guilds;
import dev.noclip.backrooms.core.people.People;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.trade.Shops;
import dev.noclip.backrooms.crafting.BackroomsCrafting;
import dev.noclip.backrooms.items.BackroomsItems;
import dev.noclip.backrooms.level0.Level0;
import dev.noclip.backrooms.level0p1.Level0p1;
import dev.noclip.backrooms.level1.Level1;
import dev.noclip.backrooms.level2.Level2;
import dev.noclip.backrooms.level3.Level3;
import dev.noclip.backrooms.level4.Level4;
import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.core.guild.Contracts;
import dev.noclip.backrooms.core.rpg.SurvivalXp;
import dev.noclip.backrooms.core.trip.Pull;
import dev.noclip.backrooms.core.trip.Trips;
import dev.noclip.backrooms.props.BackroomsProps;
import dev.noclip.backrooms.weapons.BackroomsWeaponNetworking;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

/**
 * The mod's entry point. Each level lives in its own package (level0, level1, ...) and registers itself;
 * things that cross between levels live in core, and monsters (which can turn up on several levels) in monsters.
 * See README.md for where things go.
 */
public final class BackroomsMod implements ModInitializer {
	public static final String MOD_ID = "noclip_backrooms";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		PlayerRpg.register();
		dev.noclip.backrooms.core.rpg.Badges.register();
		Floors.register();
		dev.noclip.backrooms.core.floors.FloorDifficulty.register();
		dev.noclip.backrooms.core.LevelGates.register();
		BackroomsProps.register();
		// The film's Level 0 objects (after the props, whose block entity they share).
		dev.noclip.backrooms.level0.film.FilmProps.register();
		BackroomsItems.register();
		BackroomsCrafting.register();
		BackroomsWeaponNetworking.register();
		Monsters.register();
		People.register();
		Economy.register();
		Shops.register();
		dev.noclip.backrooms.core.trade.Appraisal.register();
		dev.noclip.backrooms.core.quest.Survivors.register();
		Guilds.register();
		// The horror direction (docs/企劃書/1-遊戲玩法/README.md): trips in and out, being pulled back, contracts, experience.
		Trips.register();
		Pull.register();
		Contracts.register();
		SurvivalXp.register();
		dev.noclip.backrooms.core.quota.Quota.register();
		dev.noclip.backrooms.core.locker.Lockers.register();
		dev.noclip.backrooms.core.locker.Safebox.register();
		dev.noclip.backrooms.core.locker.RelayService.register();
		dev.noclip.backrooms.core.trip.Expedition.register();
		dev.noclip.backrooms.core.trip.Extraction.register();
		dev.noclip.backrooms.core.trip.Report.register();
		dev.noclip.backrooms.core.trip.Anomalies.register();
		dev.noclip.backrooms.core.field.FieldKit.register();
		dev.noclip.backrooms.core.locker.Research.register();
		dev.noclip.backrooms.core.locker.Processing.register();
		dev.noclip.backrooms.core.ending.Endings.register();
		dev.noclip.backrooms.core.radio.Radio.register();
		dev.noclip.backrooms.core.trade.Treatment.register();
		dev.noclip.backrooms.core.guild.GuildRemoval.register();
		dev.noclip.backrooms.frontrooms.Frontrooms.register();
		Level0.register();
		Level0p1.register();
		Level1.register();
		Level2.register();
		Level3.register();
		Level4.register();
	}
}
