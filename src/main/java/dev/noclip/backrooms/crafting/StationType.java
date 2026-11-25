package dev.noclip.backrooms.crafting;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The six custom Backrooms crafting workstations (in addition to vanilla 3x3 Crafting Table):
 * - SALVAGE (1->4 real salvage slots)
 * - WORKSHOP (5x5 shaped crafting grid)
 * - DISTILLER (3x3 shaped distillation grid)
 * - ALTAR (3x3 star-ritual shaped grid)
 * - SPIDERWEB (7x7 primary spiderweb shaped grid)
 * - ULTIMATE_SPIDERWEB (9x9 ultimate spiderweb shaped grid)
 */
public enum StationType {
	SALVAGE("salvage_bench", 1, 1, 4, 18, 176, 172),
	WORKSHOP("workshop_bench", 5, 5, 1, 18, 198, 204),
	DISTILLER("almond_distiller", 3, 3, 1, 18, 176, 172),
	ALTAR("anomaly_altar", 3, 3, 1, 18, 176, 172),
	SPIDERWEB("spiderweb_crafting_table", 7, 7, 1, 18, 212, 236),
	ULTIMATE_SPIDERWEB("ultimate_spiderweb_crafting_table", 9, 9, 1, 16, 224, 248);

	private final String id;
	private final int gridWidth;
	private final int gridHeight;
	private final int outputSlots;
	private final int cellStep;
	private final int guiWidth;
	private final int guiHeight;

	StationType(String id, int gridWidth, int gridHeight, int outputSlots, int cellStep, int guiWidth, int guiHeight) {
		this.id = id;
		this.gridWidth = gridWidth;
		this.gridHeight = gridHeight;
		this.outputSlots = outputSlots;
		this.cellStep = cellStep;
		this.guiWidth = guiWidth;
		this.guiHeight = guiHeight;
	}

	public String id() {
		return id;
	}

	public int gridWidth() {
		return gridWidth;
	}

	public int gridHeight() {
		return gridHeight;
	}

	public int inputSlotCount() {
		return gridWidth * gridHeight;
	}

	public int outputSlotCount() {
		return outputSlots;
	}

	public int cellStep() {
		return cellStep;
	}

	public int guiWidth() {
		return guiWidth;
	}

	public int guiHeight() {
		return guiHeight;
	}

	public boolean isSalvage() {
		return this == SALVAGE;
	}

	public boolean isSpiderweb() {
		return this == SPIDERWEB || this == ULTIMATE_SPIDERWEB;
	}

	public int gridOriginX() {
		return switch (this) {
			case SALVAGE -> 36;
			case DISTILLER, ALTAR -> 30;
			case WORKSHOP -> 16;
			case SPIDERWEB -> 14;
			case ULTIMATE_SPIDERWEB -> 12;
		};
	}

	public int gridOriginY() {
		return switch (this) {
			case SALVAGE -> 35;
			case DISTILLER, ALTAR -> 19;
			case WORKSHOP -> 18;
			case SPIDERWEB -> 18;
			case ULTIMATE_SPIDERWEB -> 16;
		};
	}

	public int outputOriginX() {
		return switch (this) {
			case SALVAGE -> 108;
			case DISTILLER, ALTAR -> 124;
			case WORKSHOP -> 150;
			case SPIDERWEB -> 174;
			case ULTIMATE_SPIDERWEB -> 190;
		};
	}

	public int outputOriginY() {
		return switch (this) {
			case SALVAGE -> 26;
			case DISTILLER, ALTAR -> 37;
			case WORKSHOP -> 54;
			case SPIDERWEB -> 72;
			case ULTIMATE_SPIDERWEB -> 80;
		};
	}

	public int invOriginX() {
		return (guiWidth - 162) / 2;
	}

	public int invOriginY() {
		return guiHeight - 82;
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
