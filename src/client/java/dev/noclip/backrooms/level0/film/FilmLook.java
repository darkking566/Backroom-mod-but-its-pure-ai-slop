package dev.noclip.backrooms.level0.film;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.look.MeshModel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * The film props' meshes (models/film/<id>.obj, baked in Blender by tools/film/) and their textures
 * (textures/film/<id>.png), drawn by props.BlenderPropRenderer; the institute's objects, made the same way, are in
 * models/institute and textures/institute ("inst_" ids), the staff town's in models/town and textures/town ("town_"). Loaded the first time a prop is seen, dropped when the resource
 * packs reload.
 */
public final class FilmLook {
	private static final Map<String, MeshModel> MESHES = new ConcurrentHashMap<>();

	private FilmLook() {
	}

	public static void register() {
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(BackroomsMod.id("film_look"),
				(ResourceManagerReloadListener) manager -> MESHES.clear());
	}

	/** The prop's mesh, or MeshModel.EMPTY when there is none. */
	public static MeshModel mesh(String id) {
		return MESHES.computeIfAbsent(id, key -> MeshModel.load(BackroomsMod.id("models/" + folder(key) + "/" + key + ".obj")));
	}

	public static Identifier texture(String id) {
		return BackroomsMod.id("textures/" + folder(id) + "/" + id + ".png");
	}

	private static String folder(String id) {
		return id.startsWith("inst_") ? "institute" : id.startsWith("town_") ? "town" : "film";
	}
}
