package dev.noclip.backrooms.crafting;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** The five custom Backrooms crafting workstations (in addition to vanilla 3x3 Crafting Table). */
public enum StationType {
	SALVAGE("salvage_bench"),
	WORKSHOP("workshop_bench"),
	DISTILLER("almond_distiller"),
	ALTAR("anomaly_altar"),
	SPIDERWEB("spiderweb_crafting_table");

	private final String id;

	StationType(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public Identifier blockId() {
		return BackroomsMod.id(id);
	}

	public Identifier categoryId() {
		return BackroomsMod.id("station_" + id);
	}

	public Component title() {
		return Component.translatable("block.noclip_backrooms." + id);
	}

	public Item stationItem() {
		Item item = BuiltInRegistries.ITEM.getValue(blockId());
		return item == Items.AIR ? Items.CRAFTING_TABLE : item;
	}

	public static StationType byId(String id) {
		for (StationType type : values()) {
			if (type.id.equals(id)) return type;
		}
		throw new IllegalArgumentException("Unknown station type: " + id);
	}
}
