package dev.noclip.backrooms.frontrooms;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.level0.film.FilmPropBlock;
import dev.noclip.backrooms.props.BackroomsProps;
import dev.noclip.backrooms.props.MultiPartPropBlock;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * The institute's objects as blocks (docs/project/1-gameplay/README.md, "研究所改版"): Blender meshes with baked photo
 * textures (tools/film/make_institute_props.py), drawn like the film props through the props' shared block entity. The
 * list and the places come from {@link InstituteFurnishing}; inside the institute they cannot be broken (Institute), and
 * elsewhere they break into scrap like other furniture.
 */
public final class InstituteProps {
	private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();

	static {
		for (InstituteFurnishing.Type type : InstituteFurnishing.types().values()) {
			BLOCKS.put(type.id(), block(type));
		}
	}

	private InstituteProps() {
	}

	static void register() {
		// The props' block entity was built with Gemini's list; let it live in these blocks too, as the film props do.
		FabricBlockEntityType type = (FabricBlockEntityType) (Object) BackroomsProps.PROP_BLOCK_ENTITY;
		for (Block block : BLOCKS.values()) type.addValidBlock(block);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> BLOCKS.values().forEach(output::accept));
	}

	/** Every object's block by id ("inst_gurney" ...). */
	public static Map<String, Block> all() {
		return Collections.unmodifiableMap(BLOCKS);
	}

	static Block block(String id) {
		return BLOCKS.get(id);
	}

	private static SoundType sound(String material) {
		return switch (material) {
			case "plastic", "hazmat" -> SoundType.WOOL;
			case "lab" -> SoundType.GLASS;
			default -> SoundType.METAL;
		};
	}

	private static Block block(InstituteFurnishing.Type type) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, BackroomsMod.id(type.id()));
		int light = type.light();
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(sound(type.material()))
				.strength(2.0F, 3.0F).lightLevel(s -> light).noOcclusion().setId(key);
		Block block = type.multi() ? new MultiPartPropBlock(type.width(), type.height(), type.depth(), properties)
				: new FilmPropBlock(FilmPropBlock.Mount.valueOf(type.mount().toUpperCase(Locale.ROOT)), properties);
		Registry.register(BuiltInRegistries.BLOCK, key, block);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, BackroomsMod.id(type.id()));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}
}
