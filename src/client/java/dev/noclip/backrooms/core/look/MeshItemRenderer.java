package dev.noclip.backrooms.core.look;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * An item drawn from a Blender mesh with its baked texture, everywhere an item is drawn (inventory, hands, ground,
 * frames): the realistic items pack's {@code "type": "noclip_backrooms:mesh"} special model, with the poses of its
 * "base" model. The mesh is read when models are baked, so it follows the resource packs.
 */
public record MeshItemRenderer(MeshModel mesh, Identifier texture) implements SpecialModelRenderer<Void> {
	@Override
	public void submit(Void argument, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean glint, int outline) {
		if (mesh.isEmpty()) return;
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(texture), (p, vc) -> mesh.emit(p, vc, light, overlay));
	}

	@Override
	public void getExtents(Consumer<Vector3fc> out) {
		for (int i = 0; i < 8; i++) {
			out.accept(new Vector3f((i & 1) == 0 ? mesh.min[0] : mesh.max[0], (i & 2) == 0 ? mesh.min[1] : mesh.max[1],
					(i & 4) == 0 ? mesh.min[2] : mesh.max[2]));
		}
	}

	@Override
	public Void extractArgument(ItemStack stack) {
		return null;
	}

	public record Unbaked(Identifier mesh, Identifier texture) implements SpecialModelRenderer.Unbaked<Void> {
		public static final MapCodec<Unbaked> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Identifier.CODEC.fieldOf("mesh").forGetter(Unbaked::mesh),
				Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture)).apply(i, Unbaked::new));

		@Override
		public SpecialModelRenderer<Void> bake(BakingContext context) {
			return new MeshItemRenderer(MeshModel.load(mesh), texture);
		}

		@Override
		public MapCodec<Unbaked> type() {
			return CODEC;
		}
	}
}
