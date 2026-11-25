package dev.noclip.backrooms.items.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.noclip.backrooms.items.BackroomsItems;
import dev.noclip.backrooms.items.armor.BackroomsArmorModels.ArmorSet;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Custom 3D ArmorRenderer for all 17 Backrooms armor items (Gas Mask + 4 full armor sets).
 * Uses dedicated immutable-visibility per-slot HumanoidModel instances so MC 26.2's deferred
 * SubmitNodeCollector always draws every equipped armor piece cleanly without slot visibility clobbering,
 * plus a full-bright emissive pass (RenderTypes.eyes) for glowing visors, goggles, gauges, and Void cores.
 */
public final class BackroomsArmorRenderer implements ArmorRenderer {
	private static final int FULL_BRIGHT = 0xF000F0;

	private final ArmorSet set;
	private final HumanoidModel<HumanoidRenderState> headModel;
	private final HumanoidModel<HumanoidRenderState> chestModel;
	private final HumanoidModel<HumanoidRenderState> legsModel;
	private final HumanoidModel<HumanoidRenderState> bootsModel;

	private BackroomsArmorRenderer(ArmorSet set, EntityRendererProvider.Context ctx) {
		this.set = set;
		this.headModel = BackroomsArmorModels.wrap(ctx.bakeLayer(set.mainLayer()));
		hideAll(this.headModel);
		this.headModel.head.visible = true;
		this.headModel.hat.visible = true;

		this.chestModel = BackroomsArmorModels.wrap(ctx.bakeLayer(set.mainLayer()));
		hideAll(this.chestModel);
		this.chestModel.body.visible = true;
		this.chestModel.rightArm.visible = true;
		this.chestModel.leftArm.visible = true;

		this.legsModel = BackroomsArmorModels.wrap(ctx.bakeLayer(set.legsLayer()));
		hideAll(this.legsModel);
		this.legsModel.body.visible = true;
		this.legsModel.rightLeg.visible = true;
		this.legsModel.leftLeg.visible = true;

		this.bootsModel = BackroomsArmorModels.wrap(ctx.bakeLayer(set.mainLayer()));
		hideAll(this.bootsModel);
		this.bootsModel.rightLeg.visible = true;
		this.bootsModel.leftLeg.visible = true;
	}

	private static void hideAll(HumanoidModel<HumanoidRenderState> m) {
		m.head.visible = false;
		m.hat.visible = false;
		m.body.visible = false;
		m.rightArm.visible = false;
		m.leftArm.visible = false;
		m.rightLeg.visible = false;
		m.leftLeg.visible = false;
	}

	public static void registerAll() {
		for (ArmorSet set : ArmorSet.values()) {
			ModelLayerRegistry.registerModelLayer(set.mainLayer(), () -> BackroomsArmorModels.createMainLayer(set));
			ModelLayerRegistry.registerModelLayer(set.legsLayer(), () -> BackroomsArmorModels.createLegsLayer(set));
		}

		registerSet(ArmorSet.GAS_MASK, BackroomsItems.ALMOND_GAS_MASK);
		registerSet(ArmorSet.SCRAP_TACTICAL,
				BackroomsItems.SCRAP_TACTICAL_HELMET,
				BackroomsItems.SCRAP_TACTICAL_CHESTPLATE,
				BackroomsItems.SCRAP_TACTICAL_LEGGINGS,
				BackroomsItems.SCRAP_TACTICAL_BOOTS);
		registerSet(ArmorSet.SKIN_STEALER,
				BackroomsItems.SKIN_STEALER_HELMET,
				BackroomsItems.SKIN_STEALER_CHESTPLATE,
				BackroomsItems.SKIN_STEALER_LEGGINGS,
				BackroomsItems.SKIN_STEALER_BOOTS);
		registerSet(ArmorSet.BOILER_THERMAL,
				BackroomsItems.BOILER_THERMAL_HELMET,
				BackroomsItems.BOILER_THERMAL_CHESTPLATE,
				BackroomsItems.BOILER_THERMAL_LEGGINGS,
				BackroomsItems.BOILER_THERMAL_BOOTS);
		registerSet(ArmorSet.ABYSSAL_VOID,
				BackroomsItems.ABYSSAL_VOID_HELMET,
				BackroomsItems.ABYSSAL_VOID_CHESTPLATE,
				BackroomsItems.ABYSSAL_VOID_LEGGINGS,
				BackroomsItems.ABYSSAL_VOID_BOOTS);
	}

	private static void registerSet(ArmorSet set, net.minecraft.world.level.ItemLike... items) {
		ArmorRenderer.register(ctx -> new BackroomsArmorRenderer(set, ctx), items);
	}

	@Override
	public void render(PoseStack matrices, SubmitNodeCollector collector, ItemStack stack,
			HumanoidRenderState state, EquipmentSlot slot, int light,
			HumanoidModel<HumanoidRenderState> contextModel) {
		HumanoidModel<HumanoidRenderState> model = switch (slot) {
			case HEAD -> headModel;
			case CHEST -> chestModel;
			case LEGS -> legsModel;
			case FEET -> bootsModel;
			default -> null;
		};
		if (model == null) return;

		ArmorRenderer.submitTransformCopyingModel(
				contextModel, state,
				model, state,
				false,
				collector,
				matrices,
				RenderTypes.armorCutoutNoCull(set.texture()),
				light,
				OverlayTexture.NO_OVERLAY,
				-1,
				null,
				0,
				null
		);

		if (set.hasEmissive()) {
			ArmorRenderer.submitTransformCopyingModel(
					contextModel, state,
					model, state,
					false,
					collector,
					matrices,
					RenderTypes.eyes(set.emissiveTexture()),
					FULL_BRIGHT,
					OverlayTexture.NO_OVERLAY,
					-1,
					null,
					0,
					null
			);
		}
	}
}
