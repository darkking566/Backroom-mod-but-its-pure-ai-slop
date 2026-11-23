package dev.noclip.backrooms.monsters;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.noclip.backrooms.BackroomsMod;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A monster's look and moves as exported from Blender: boxes hung on a tree of joints, plus keyframed clips
 * that turn the joints. Read from assets/noclip_backrooms/monsters/NAME.json (the format is described in
 * art/monsters/hound/README.md). Every monster drawn this way only needs that file and a texture.
 */
public final class BoxRig {
	private final List<JsonObject> parts = new ArrayList<>();
	private final Map<String, String> parents = new LinkedHashMap<>();
	private final Map<String, Clip> clips = new HashMap<>();
	private final int textureWidth, textureHeight;

	private BoxRig(JsonObject spec) {
		JsonObject atlas = spec.getAsJsonObject("texture_atlas");
		textureWidth = atlas.get("width").getAsInt();
		textureHeight = atlas.get("height").getAsInt();
		// Parents first, whatever order the file lists them in.
		List<JsonObject> pending = new ArrayList<>();
		spec.getAsJsonArray("parts").forEach(part -> pending.add(part.getAsJsonObject()));
		while (!pending.isEmpty()) {
			int before = pending.size();
			pending.removeIf(part -> {
				String parent = part.get("parent").getAsString();
				if (!parent.equals("root") && !parents.containsKey(parent)) return false;
				parts.add(part);
				parents.put(part.get("name").getAsString(), parent);
				return true;
			});
			if (pending.size() == before) throw new IllegalStateException("joints with missing parents: " + pending.size());
		}
		for (Map.Entry<String, JsonElement> clip : spec.getAsJsonObject("animations").entrySet()) {
			clips.put(clip.getKey(), new Clip(clip.getValue().getAsJsonObject()));
		}
	}

	public static BoxRig load(String name) {
		String file = "assets/" + BackroomsMod.MOD_ID + "/monsters/" + name + ".json";
		Path path = FabricLoader.getInstance().getModContainer(BackroomsMod.MOD_ID)
				.flatMap(mod -> mod.findPath(file))
				.orElseThrow(() -> new IllegalStateException("missing " + file));
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return new BoxRig(JsonParser.parseReader(reader).getAsJsonObject());
		} catch (IOException e) {
			throw new IllegalStateException("could not read " + file, e);
		}
	}

	/** The boxes, for ModelLayerRegistry. A box turned on its own becomes a small child part of its joint. */
	public LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		Map<String, PartDefinition> defined = new HashMap<>();
		defined.put("root", mesh.getRoot());
		for (JsonObject part : parts) {
			String name = part.get("name").getAsString();
			CubeListBuilder boxes = CubeListBuilder.create();
			List<JsonObject> turned = new ArrayList<>();
			for (JsonElement element : part.getAsJsonArray("cubes")) {
				JsonObject cube = element.getAsJsonObject();
				if (cube.has("needs_subpart") && cube.get("needs_subpart").getAsBoolean()) {
					turned.add(cube);
				} else {
					addBox(boxes, cube);
				}
			}
			float[] pivot = floats(part, "pivot_mc"), rest = floats(part, "rest_rot_rad_mc");
			PartDefinition joint = defined.get(part.get("parent").getAsString()).addOrReplaceChild(name, boxes,
					PartPose.offsetAndRotation(pivot[0], pivot[1], pivot[2], rest[0], rest[1], rest[2]));
			for (int i = 0; i < turned.size(); i++) {
				JsonObject cube = turned.get(i);
				float[] offset = floats(cube, "subpart_offset_mc"), turn = floats(cube, "subpart_rot_rad_mc");
				joint.addOrReplaceChild("box_" + i, addBox(CubeListBuilder.create(), cube),
						PartPose.offsetAndRotation(offset[0], offset[1], offset[2], turn[0], turn[1], turn[2]));
			}
			defined.put(name, joint);
		}
		return LayerDefinition.create(mesh, textureWidth, textureHeight);
	}

	private static CubeListBuilder addBox(CubeListBuilder boxes, JsonObject cube) {
		JsonArray uv = cube.getAsJsonArray("uv_region");
		float[] origin = floats(cube, "origin_mc"), size = floats(cube, "size_mc");
		return boxes.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt()).addBox(origin[0], origin[1], origin[2], size[0], size[1], size[2]);
	}

	/** The baked joints by name. */
	public Map<String, ModelPart> bind(ModelPart root) {
		Map<String, ModelPart> joints = new HashMap<>();
		for (Map.Entry<String, String> joint : parents.entrySet()) {
			ModelPart parent = joint.getValue().equals("root") ? root : joints.get(joint.getValue());
			joints.put(joint.getKey(), parent.getChild(joint.getKey()));
		}
		return joints;
	}

	public Clip clip(String name) {
		Clip clip = clips.get(name);
		if (clip == null) throw new IllegalArgumentException("no clip " + name);
		return clip;
	}

	private static float[] floats(JsonObject object, String key) {
		JsonArray array = object.getAsJsonArray(key);
		float[] values = new float[array.size()];
		for (int i = 0; i < values.length; i++) values[i] = array.get(i).getAsFloat();
		return values;
	}

	/**
	 * One keyframed clip. Joint angles (and the pelvis position) are absolute, so a clip replaces the pose
	 * rather than adding to it. A looping clip whose last key repeats the first loops on that key; otherwise
	 * it closes the loop over one more key spacing. Keys are joined with Catmull-Rom curves.
	 */
	public static final class Clip {
		private final boolean loop;
		private final float length;
		private final float[] times;
		private final String[] joints;
		/** [joint][key * 3 + axis]; positions are null for joints that only turn. */
		private final float[][] turns, moves;

		Clip(JsonObject clip) {
			loop = clip.get("loop").getAsBoolean();
			JsonArray keys = clip.getAsJsonArray("keyframes");
			JsonObject first = keys.get(0).getAsJsonObject().getAsJsonObject("joints");
			joints = first.keySet().toArray(String[]::new);
			float[] allTimes = new float[keys.size()];
			float[][] allTurns = new float[joints.length][keys.size() * 3], allMoves = new float[joints.length][];
			for (int j = 0; j < joints.length; j++) {
				if (first.getAsJsonObject(joints[j]).has("pos_mc")) allMoves[j] = new float[keys.size() * 3];
			}
			for (int k = 0; k < keys.size(); k++) {
				JsonObject key = keys.get(k).getAsJsonObject();
				allTimes[k] = key.get("time_sec").getAsFloat();
				JsonObject pose = key.getAsJsonObject("joints");
				for (int j = 0; j < joints.length; j++) {
					JsonObject joint = pose.getAsJsonObject(joints[j]);
					System.arraycopy(floats(joint, "rot_rad_mc"), 0, allTurns[j], k * 3, 3);
					if (allMoves[j] != null) System.arraycopy(floats(joint, "pos_mc"), 0, allMoves[j], k * 3, 3);
				}
			}
			int count = allTimes.length;
			float last = allTimes[count - 1];
			if (loop && repeats(allTurns, allMoves, count)) {
				count--;
				length = last;
			} else {
				length = loop ? last + (allTimes[1] - allTimes[0]) : last;
			}
			times = java.util.Arrays.copyOf(allTimes, count);
			turns = new float[joints.length][];
			moves = new float[joints.length][];
			for (int j = 0; j < joints.length; j++) {
				turns[j] = java.util.Arrays.copyOf(allTurns[j], count * 3);
				if (allMoves[j] != null) moves[j] = java.util.Arrays.copyOf(allMoves[j], count * 3);
			}
		}

		private static boolean repeats(float[][] turns, float[][] moves, int count) {
			for (int j = 0; j < turns.length; j++) {
				for (float[] values : new float[][] {turns[j], moves[j]}) {
					if (values == null) continue;
					for (int axis = 0; axis < 3; axis++) {
						if (Math.abs(values[axis] - values[(count - 1) * 3 + axis]) > 1.0E-3F) return false;
					}
				}
			}
			return true;
		}

		/** Seconds. */
		public float length() {
			return length;
		}

		/** Blends the clip at time t (seconds) into the parts' current pose; weight 1 replaces it. */
		public void apply(Map<String, ModelPart> parts, float t, float weight) {
			if (weight <= 0) return;
			int count = times.length;
			float time = loop ? Mth.positiveModulo(t, length) : Mth.clamp(t, 0, times[count - 1]);
			int key = count - 1;
			for (int k = 0; k < count - 1; k++) {
				if (time < times[k + 1]) {
					key = k;
					break;
				}
			}
			int next = key + 1;
			float end;
			if (next < count) {
				end = times[next];
			} else if (loop) {
				next = 0;
				end = length;
			} else {
				next = key;
				end = times[key] + 1;
			}
			float progress = end > times[key] ? (time - times[key]) / (end - times[key]) : 0;
			int before = key > 0 ? key - 1 : loop ? count - 1 : 0;
			int after = next + 1 < count ? next + 1 : loop ? (next + 1) % count : next;
			for (int j = 0; j < joints.length; j++) {
				ModelPart part = parts.get(joints[j]);
				if (part == null) continue;
				float[] turn = turns[j];
				part.xRot = Mth.lerp(weight, part.xRot, curve(turn, before, key, next, after, 0, progress));
				part.yRot = Mth.lerp(weight, part.yRot, curve(turn, before, key, next, after, 1, progress));
				part.zRot = Mth.lerp(weight, part.zRot, curve(turn, before, key, next, after, 2, progress));
				float[] move = moves[j];
				if (move == null) continue;
				part.x = Mth.lerp(weight, part.x, curve(move, before, key, next, after, 0, progress));
				part.y = Mth.lerp(weight, part.y, curve(move, before, key, next, after, 1, progress));
				part.z = Mth.lerp(weight, part.z, curve(move, before, key, next, after, 2, progress));
			}
		}

		private static float curve(float[] values, int a, int b, int c, int d, int axis, float t) {
			return Mth.catmullrom(t, values[a * 3 + axis], values[b * 3 + axis], values[c * 3 + axis], values[d * 3 + axis]);
		}
	}
}
