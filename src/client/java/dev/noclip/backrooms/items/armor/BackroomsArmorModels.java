package dev.noclip.backrooms.items.armor;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * Richly sculpted 3D worn humanoid armor models for all 5 Backrooms armor tiers:
 * Almond Gas Mask, Scrap Tactical, Skin-Stealer Cloak, Boiler Thermal Suit, and Abyssal Void Rig.
 * Each model uses a 64x64 multi-swatch shaded armor atlas + optional emissive glow texture.
 */
public final class BackroomsArmorModels {
	public enum ArmorSet {
		GAS_MASK("almond_gas_mask", true),
		SCRAP_TACTICAL("scrap_tactical", true),
		SKIN_STEALER("skin_stealer", false),
		BOILER_THERMAL("boiler_thermal", true),
		ABYSSAL_VOID("abyssal_void", true);

		private final String id;
		private final boolean hasEmissive;
		private final ModelLayerLocation mainLayer;
		private final ModelLayerLocation legsLayer;
		private final Identifier texture;
		private final Identifier emissiveTexture;

		ArmorSet(String id, boolean hasEmissive) {
			this.id = id;
			this.hasEmissive = hasEmissive;
			this.mainLayer = new ModelLayerLocation(BackroomsMod.id("armor_" + id), "main");
			this.legsLayer = new ModelLayerLocation(BackroomsMod.id("armor_" + id), "legs");
			this.texture = BackroomsMod.id("textures/entity/armor3d/" + id + ".png");
			this.emissiveTexture = BackroomsMod.id("textures/entity/armor3d/" + id + "_glow.png");
		}

		public String id() {
			return id;
		}

		public boolean hasEmissive() {
			return hasEmissive;
		}

		public ModelLayerLocation mainLayer() {
			return mainLayer;
		}

		public ModelLayerLocation legsLayer() {
			return legsLayer;
		}

		public Identifier texture() {
			return texture;
		}

		public Identifier emissiveTexture() {
			return emissiveTexture;
		}
	}

	private BackroomsArmorModels() {
	}

	public static HumanoidModel<HumanoidRenderState> wrap(ModelPart root) {
		return new HumanoidModel<>(root);
	}

	private static MeshDefinition blankHumanoidMesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
		return mesh;
	}

	/**
	 * 256x256 leak-proof armor texture layout (64x64 tiled 16px-density swatches across 4x4 grid,
	 * guaranteeing even the largest 11x12x10 cuboid fits 100% inside its own swatch with zero UV bleed):
	 * (0,0)=primary armor, (64,0)=secondary dark, (128,0)=trim/brass, (192,0)=glowing visor/core
	 * (0,64)=straps/cloth, (64,64)=riveted plate, (128,64)=pipe/copper, (192,64)=hazard/accent
	 */
	private static final int U0 = 0;
	private static final int U1 = 64;
	private static final int U2 = 128;
	private static final int U3 = 192;
	private static final int V0 = 0;
	private static final int V1 = 64;

	public static LayerDefinition createMainLayer(ArmorSet set) {
		return switch (set) {
			case GAS_MASK -> buildGasMask();
			case SCRAP_TACTICAL -> buildScrapTacticalMain();
			case SKIN_STEALER -> buildSkinStealerMain();
			case BOILER_THERMAL -> buildBoilerThermalMain();
			case ABYSSAL_VOID -> buildAbyssalVoidMain();
		};
	}

	public static LayerDefinition createLegsLayer(ArmorSet set) {
		return switch (set) {
			case GAS_MASK -> LayerDefinition.create(blankHumanoidMesh(), 256, 256);
			case SCRAP_TACTICAL -> buildScrapTacticalLegs();
			case SKIN_STEALER -> buildSkinStealerLegs();
			case BOILER_THERMAL -> buildBoilerThermalLegs();
			case ABYSSAL_VOID -> buildAbyssalVoidLegs();
		};
	}

	private static LayerDefinition buildGasMask() {
		MeshDefinition mesh = blankHumanoidMesh();
		PartDefinition head = mesh.getRoot().getChild("head");
		// Tight respirator hood + head straps
		head.addOrReplaceChild("hood", CubeListBuilder.create()
				.texOffs(U0, V1).addBox(-4.35F, -8.35F, -4.35F, 8.7F, 8.7F, 8.7F)
				.texOffs(U1, V0).addBox(-4.6F, -5.2F, -4.5F, 9.2F, 1.5F, 9.0F)
				.texOffs(U1, V0).addBox(-1.2F, -8.65F, -4.5F, 2.4F, 8.9F, 9.0F), PartPose.ZERO);
		// Protruding rubber snout & central filter exhaust grill
		head.addOrReplaceChild("snout", CubeListBuilder.create()
				.texOffs(U0, V0).addBox(-2.4F, -3.5F, -6.2F, 4.8F, 3.3F, 2.2F)
				.texOffs(U2, V0).addBox(-1.5F, -2.9F, -6.9F, 3.0F, 2.2F, 1.0F)
				.texOffs(U1, V1).addBox(-1.0F, -2.4F, -7.2F, 2.0F, 1.2F, 0.5F), PartPose.ZERO);
		// Twin protruding angled cheek filter canisters
		head.addOrReplaceChild("filter_right", CubeListBuilder.create()
				.texOffs(U1, V1).addBox(-1.6F, -1.6F, -2.8F, 3.2F, 3.2F, 3.0F)
				.texOffs(U3, V1).addBox(-1.75F, -1.75F, -2.2F, 3.5F, 3.5F, 1.0F)
				.texOffs(U2, V0).addBox(-1.2F, -1.2F, -3.2F, 2.4F, 2.4F, 0.5F),
				PartPose.offsetAndRotation(-3.6F, -1.8F, -4.6F, 0.18F, 0.55F, 0.0F));
		head.addOrReplaceChild("filter_left", CubeListBuilder.create()
				.texOffs(U1, V1).addBox(-1.6F, -1.6F, -2.8F, 3.2F, 3.2F, 3.0F)
				.texOffs(U3, V1).addBox(-1.75F, -1.75F, -2.2F, 3.5F, 3.5F, 1.0F)
				.texOffs(U2, V0).addBox(-1.2F, -1.2F, -3.2F, 2.4F, 2.4F, 0.5F),
				PartPose.offsetAndRotation(3.6F, -1.8F, -4.6F, 0.18F, -0.55F, 0.0F));
		// Twin brass-rimmed glowing cyan goggles
		head.addOrReplaceChild("goggles", CubeListBuilder.create()
				.texOffs(U2, V0).addBox(-3.9F, -5.8F, -5.4F, 3.3F, 2.5F, 1.3F)
				.texOffs(U2, V0).addBox(0.6F, -5.8F, -5.4F, 3.3F, 2.5F, 1.3F)
				.texOffs(U1, V0).addBox(-0.7F, -5.1F, -5.2F, 1.4F, 1.1F, 1.0F)
				.texOffs(U3, V0).addBox(-3.4F, -5.3F, -5.65F, 2.3F, 1.5F, 0.5F)
				.texOffs(U3, V0).addBox(1.1F, -5.3F, -5.65F, 2.3F, 1.5F, 0.5F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 256, 256);
	}

	private static LayerDefinition buildScrapTacticalMain() {
		MeshDefinition mesh = blankHumanoidMesh();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head");
		head.addOrReplaceChild("helmet_shell", CubeListBuilder.create()
				.texOffs(U0, V0).addBox(-4.6F, -8.8F, -4.6F, 9.2F, 5.4F, 9.2F)
				.texOffs(U1, V1).addBox(-4.9F, -6.2F, -5.2F, 9.8F, 1.4F, 2.0F)
				.texOffs(U1, V0).addBox(-4.8F, -3.6F, -4.2F, 1.2F, 3.8F, 8.6F)
				.texOffs(U1, V0).addBox(3.6F, -3.6F, -4.2F, 1.2F, 3.8F, 8.6F)
				.texOffs(U1, V0).addBox(-4.6F, -3.6F, 3.5F, 9.2F, 3.8F, 1.3F)
				.texOffs(U2, V0).addBox(-3.6F, -1.3F, -5.1F, 7.2F, 1.5F, 1.2F), PartPose.ZERO);
		head.addOrReplaceChild("side_lamp", CubeListBuilder.create()
				.texOffs(U2, V1).addBox(-5.8F, -6.7F, -2.5F, 1.4F, 1.6F, 3.0F)
				.texOffs(U3, V0).addBox(-5.7F, -6.5F, -2.8F, 1.2F, 1.2F, 0.5F), PartPose.ZERO);
		head.addOrReplaceChild("visor_bar", CubeListBuilder.create()
				.texOffs(U3, V0).addBox(-3.5F, -4.8F, -5.15F, 7.0F, 1.2F, 0.5F), PartPose.ZERO);

		PartDefinition body = root.getChild("body");
		body.addOrReplaceChild("vest", CubeListBuilder.create()
				.texOffs(U0, V1).addBox(-4.4F, -0.2F, -2.5F, 8.8F, 12.0F, 5.0F)
				.texOffs(U1, V1).addBox(-3.8F, 0.8F, -3.35F, 7.6F, 6.2F, 1.2F)
				.texOffs(U1, V1).addBox(-3.8F, 0.8F, 2.2F, 7.6F, 7.0F, 1.3F)
				.texOffs(U2, V0).addBox(-4.0F, 7.2F, -3.4F, 2.2F, 2.8F, 1.3F)
				.texOffs(U2, V0).addBox(-1.1F, 7.2F, -3.4F, 2.2F, 2.8F, 1.3F)
				.texOffs(U2, V0).addBox(1.8F, 7.2F, -3.4F, 2.2F, 2.8F, 1.3F)
				.texOffs(U1, V0).addBox(-4.6F, 9.8F, -2.9F, 9.2F, 2.0F, 5.8F), PartPose.ZERO);

		PartDefinition rArm = root.getChild("right_arm");
		rArm.addOrReplaceChild("pauldron_r", CubeListBuilder.create()
				.texOffs(U0, V0).addBox(-3.8F, -2.6F, -2.8F, 5.2F, 4.4F, 5.6F)
				.texOffs(U1, V1).addBox(-4.2F, -1.6F, -2.4F, 1.2F, 3.6F, 4.8F)
				.texOffs(U1, V0).addBox(-3.4F, 4.4F, -2.5F, 4.8F, 4.8F, 5.0F), PartPose.ZERO);
		PartDefinition lArm = root.getChild("left_arm");
		lArm.addOrReplaceChild("pauldron_l", CubeListBuilder.create()
				.texOffs(U0, V0).addBox(-1.4F, -2.6F, -2.8F, 5.2F, 4.4F, 5.6F)
				.texOffs(U1, V1).addBox(3.0F, -1.6F, -2.4F, 1.2F, 3.6F, 4.8F)
				.texOffs(U1, V0).addBox(-1.4F, 4.4F, -2.5F, 4.8F, 4.8F, 5.0F), PartPose.ZERO);

		addArmoredBoot(root.getChild("right_leg"), U0, V0, U1, V1, U2, V0);
		addArmoredBoot(root.getChild("left_leg"), U0, V0, U1, V1, U2, V0);
		return LayerDefinition.create(mesh, 256, 256);
	}

	private static LayerDefinition buildScrapTacticalLegs() {
		MeshDefinition mesh = blankHumanoidMesh();
		PartDefinition root = mesh.getRoot();
		root.getChild("body").addOrReplaceChild("belt", CubeListBuilder.create()
				.texOffs(U1, V0).addBox(-4.5F, 9.6F, -2.8F, 9.0F, 2.4F, 5.6F)
				.texOffs(U2, V0).addBox(-1.4F, 9.8F, -3.2F, 2.8F, 2.0F, 0.8F), PartPose.ZERO);
		for (String side : new String[]{"right_leg", "left_leg"}) {
			float outer = side.equals("right_leg") ? -2.8F : 1.6F;
			root.getChild(side).addOrReplaceChild("thigh_guard", CubeListBuilder.create()
					.texOffs(U0, V1).addBox(-2.35F, 0.0F, -2.35F, 4.7F, 7.2F, 4.7F)
					.texOffs(U1, V1).addBox(outer, 0.8F, -1.8F, 1.2F, 4.6F, 3.6F)
					.texOffs(U1, V1).addBox(-2.1F, 4.2F, -3.0F, 4.2F, 2.6F, 1.1F), PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 256, 256);
	}

	private static LayerDefinition buildSkinStealerMain() {
		MeshDefinition mesh = blankHumanoidMesh();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head");
		head.addOrReplaceChild("cowl", CubeListBuilder.create()
				.texOffs(U0, V0).addBox(-4.7F, -8.9F, -4.6F, 9.4F, 9.2F, 9.4F)
				.texOffs(U1, V0).addBox(-4.9F, -9.2F, -5.6F, 9.8F, 2.6F, 2.2F)
				.texOffs(U0, V1).addBox(-5.0F, -7.2F, -5.1F, 1.5F, 7.6F, 2.0F)
				.texOffs(U0, V1).addBox(3.5F, -7.2F, -5.1F, 1.5F, 7.6F, 2.0F)
				.texOffs(U2, V0).addBox(-3.6F, -2.4F, -4.9F, 7.2F, 2.6F, 1.2F), PartPose.ZERO);
		PartDefinition body = root.getChild("body");
		body.addOrReplaceChild("cloak_torso", CubeListBuilder.create()
				.texOffs(U0, V0).addBox(-4.5F, -0.2F, -2.6F, 9.0F, 12.2F, 5.2F)
				.texOffs(U0, V1).addBox(-5.4F, -0.6F, -3.0F, 10.8F, 3.4F, 6.2F)
				.texOffs(U1, V0).addBox(-4.6F, 2.4F, 2.4F, 9.2F, 9.8F, 1.1F)
				.texOffs(U2, V0).addBox(-3.6F, 1.2F, -3.1F, 7.2F, 1.4F, 0.8F), PartPose.ZERO);
		root.getChild("right_arm").addOrReplaceChild("sleeve_r", CubeListBuilder.create()
				.texOffs(U0, V1).addBox(-3.7F, -2.4F, -2.6F, 5.0F, 4.2F, 5.2F)
				.texOffs(U1, V0).addBox(-3.4F, 3.8F, -2.4F, 4.8F, 5.4F, 4.8F), PartPose.ZERO);
		root.getChild("left_arm").addOrReplaceChild("sleeve_l", CubeListBuilder.create()
				.texOffs(U0, V1).addBox(-1.3F, -2.4F, -2.6F, 5.0F, 4.2F, 5.2F)
				.texOffs(U1, V0).addBox(-1.4F, 3.8F, -2.4F, 4.8F, 5.4F, 4.8F), PartPose.ZERO);
		addArmoredBoot(root.getChild("right_leg"), U1, V0, U0, V1, U2, V0);
		addArmoredBoot(root.getChild("left_leg"), U1, V0, U0, V1, U2, V0);
		return LayerDefinition.create(mesh, 256, 256);
	}

	private static LayerDefinition buildSkinStealerLegs() {
		MeshDefinition mesh = blankHumanoidMesh();
		PartDefinition root = mesh.getRoot();
		root.getChild("body").addOrReplaceChild("waist_sash", CubeListBuilder.create()
				.texOffs(U2, V0).addBox(-4.6F, 9.4F, -2.9F, 9.2F, 2.6F, 5.8F), PartPose.ZERO);
		for (String side : new String[]{"right_leg", "left_leg"}) {
			root.getChild(side).addOrReplaceChild("cloak_leg", CubeListBuilder.create()
					.texOffs(U0, V0).addBox(-2.4F, 0.0F, -2.4F, 4.8F, 7.6F, 4.8F)
					.texOffs(U1, V0).addBox(-2.2F, 1.0F, 2.1F, 4.4F, 6.8F, 0.9F), PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 256, 256);
	}

	private static LayerDefinition buildBoilerThermalMain() {
		MeshDefinition mesh = blankHumanoidMesh();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head");
		head.addOrReplaceChild("boiler_dome", CubeListBuilder.create()
				.texOffs(U0, V0).addBox(-4.8F, -8.8F, -4.8F, 9.6F, 9.2F, 9.6F)
				.texOffs(U2, V0).addBox(-3.8F, -9.6F, -3.8F, 7.6F, 1.2F, 7.6F)
				.texOffs(U2, V1).addBox(-1.2F, -10.8F, -1.2F, 2.4F, 1.4F, 2.4F)
				.texOffs(U2, V0).addBox(-3.6F, -6.8F, -5.8F, 7.2F, 4.8F, 1.4F)
				.texOffs(U3, V0).addBox(-2.7F, -6.0F, -6.05F, 5.4F, 3.2F, 0.5F)
				.texOffs(U1, V0).addBox(-0.4F, -6.3F, -6.35F, 0.8F, 3.8F, 0.5F)
				.texOffs(U1, V0).addBox(-3.0F, -4.7F, -6.35F, 6.0F, 0.7F, 0.5F), PartPose.ZERO);

		PartDefinition body = root.getChild("body");
		body.addOrReplaceChild("boiler_chest", CubeListBuilder.create()
				.texOffs(U0, V0).addBox(-4.6F, -0.3F, -2.8F, 9.2F, 12.2F, 5.6F)
				.texOffs(U1, V1).addBox(-3.8F, 1.0F, -3.6F, 7.6F, 6.8F, 1.2F)
				.texOffs(U3, V0).addBox(-1.4F, 2.5F, -3.9F, 2.8F, 2.4F, 0.6F)
				.texOffs(U2, V1).addBox(-4.0F, 0.6F, 2.8F, 3.4F, 9.6F, 3.2F)
				.texOffs(U2, V1).addBox(0.6F, 0.6F, 2.8F, 3.4F, 9.6F, 3.2F)
				.texOffs(U2, V0).addBox(-3.4F, -0.4F, 3.3F, 6.8F, 1.4F, 2.2F)
				.texOffs(U3, V1).addBox(-4.2F, 4.2F, 2.6F, 8.4F, 1.6F, 3.6F), PartPose.ZERO);

		root.getChild("right_arm").addOrReplaceChild("boiler_arm_r", CubeListBuilder.create()
				.texOffs(U0, V0).addBox(-4.1F, -2.8F, -2.9F, 5.6F, 4.8F, 5.8F)
				.texOffs(U2, V0).addBox(-4.3F, 1.0F, -3.0F, 5.8F, 1.1F, 6.0F)
				.texOffs(U1, V1).addBox(-3.6F, 4.4F, -2.6F, 5.0F, 5.2F, 5.2F), PartPose.ZERO);
		root.getChild("left_arm").addOrReplaceChild("boiler_arm_l", CubeListBuilder.create()
				.texOffs(U0, V0).addBox(-1.5F, -2.8F, -2.9F, 5.6F, 4.8F, 5.8F)
				.texOffs(U2, V0).addBox(-1.5F, 1.0F, -3.0F, 5.8F, 1.1F, 6.0F)
				.texOffs(U1, V1).addBox(-1.4F, 4.4F, -2.6F, 5.0F, 5.2F, 5.2F), PartPose.ZERO);

		addArmoredBoot(root.getChild("right_leg"), U1, V1, U2, V1, U2, V0);
		addArmoredBoot(root.getChild("left_leg"), U1, V1, U2, V1, U2, V0);
		return LayerDefinition.create(mesh, 256, 256);
	}

	private static LayerDefinition buildBoilerThermalLegs() {
		MeshDefinition mesh = blankHumanoidMesh();
		PartDefinition root = mesh.getRoot();
		root.getChild("body").addOrReplaceChild("boiler_belt", CubeListBuilder.create()
				.texOffs(U2, V0).addBox(-4.8F, 9.2F, -3.1F, 9.6F, 2.8F, 6.2F), PartPose.ZERO);
		for (String side : new String[]{"right_leg", "left_leg"}) {
			float outer = side.equals("right_leg") ? -3.0F : 1.8F;
			root.getChild(side).addOrReplaceChild("boiler_thigh", CubeListBuilder.create()
					.texOffs(U0, V1).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 7.4F, 5.0F)
					.texOffs(U2, V1).addBox(outer, 0.6F, -1.2F, 1.2F, 6.2F, 2.4F)
					.texOffs(U1, V1).addBox(-2.2F, 4.0F, -3.2F, 4.4F, 2.8F, 1.2F), PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 256, 256);
	}

	private static LayerDefinition buildAbyssalVoidMain() {
		MeshDefinition mesh = blankHumanoidMesh();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head");
		head.addOrReplaceChild("void_helm", CubeListBuilder.create()
				.texOffs(U0, V0).addBox(-4.7F, -8.8F, -4.7F, 9.4F, 9.2F, 9.4F)
				.texOffs(U1, V0).addBox(-3.6F, -9.6F, -4.0F, 7.2F, 1.2F, 8.2F)
				.texOffs(U1, V1).addBox(-4.2F, -5.8F, -5.5F, 8.4F, 2.4F, 1.2F)
				.texOffs(U3, V0).addBox(-3.6F, -5.3F, -5.85F, 7.2F, 1.4F, 0.5F)
				.texOffs(U3, V0).addBox(-1.1F, -6.1F, -6.1F, 2.2F, 2.2F, 0.6F)
				.texOffs(U2, V0).addBox(-5.4F, -3.4F, -3.0F, 1.0F, 2.8F, 4.6F)
				.texOffs(U2, V0).addBox(4.4F, -3.4F, -3.0F, 1.0F, 2.8F, 4.6F), PartPose.ZERO);
		head.addOrReplaceChild("fin_right", CubeListBuilder.create()
				.texOffs(U2, V0).addBox(-0.6F, -3.6F, -1.0F, 1.2F, 4.2F, 5.2F)
				.texOffs(U3, V0).addBox(-0.3F, -3.2F, 1.6F, 0.6F, 3.2F, 2.8F),
				PartPose.offsetAndRotation(-4.6F, -7.8F, 0.8F, 0.35F, -0.22F, -0.32F));
		head.addOrReplaceChild("fin_left", CubeListBuilder.create()
				.texOffs(U2, V0).addBox(-0.6F, -3.6F, -1.0F, 1.2F, 4.2F, 5.2F)
				.texOffs(U3, V0).addBox(-0.3F, -3.2F, 1.6F, 0.6F, 3.2F, 2.8F),
				PartPose.offsetAndRotation(4.6F, -7.8F, 0.8F, 0.35F, 0.22F, 0.32F));

		PartDefinition body = root.getChild("body");
		body.addOrReplaceChild("void_cuirass", CubeListBuilder.create()
				.texOffs(U0, V0).addBox(-4.6F, -0.3F, -2.7F, 9.2F, 12.2F, 5.4F)
				.texOffs(U1, V1).addBox(-4.0F, 0.6F, -3.6F, 8.0F, 6.6F, 1.4F)
				.texOffs(U2, V0).addBox(-2.2F, 1.6F, -4.1F, 4.4F, 4.4F, 0.9F)
				.texOffs(U3, V0).addBox(-1.4F, 2.4F, -4.45F, 2.8F, 2.8F, 0.6F)
				.texOffs(U1, V0).addBox(-3.6F, 0.8F, 2.6F, 7.2F, 8.8F, 2.6F)
				.texOffs(U3, V0).addBox(-2.6F, 1.8F, 5.0F, 1.2F, 6.8F, 0.8F)
				.texOffs(U3, V0).addBox(1.4F, 1.8F, 5.0F, 1.2F, 6.8F, 0.8F), PartPose.ZERO);

		PartDefinition rArm = root.getChild("right_arm");
		rArm.addOrReplaceChild("void_pauldron_r", CubeListBuilder.create()
				.texOffs(U0, V0).addBox(-4.2F, -3.0F, -3.0F, 5.8F, 4.8F, 6.0F)
				.texOffs(U2, V0).addBox(-4.8F, -3.6F, -2.4F, 1.4F, 3.8F, 4.8F)
				.texOffs(U3, V0).addBox(-5.0F, -2.8F, -1.4F, 0.6F, 2.2F, 2.8F)
				.texOffs(U1, V1).addBox(-3.6F, 4.2F, -2.6F, 5.0F, 5.4F, 5.2F), PartPose.ZERO);
		PartDefinition lArm = root.getChild("left_arm");
		lArm.addOrReplaceChild("void_pauldron_l", CubeListBuilder.create()
				.texOffs(U0, V0).addBox(-1.6F, -3.0F, -3.0F, 5.8F, 4.8F, 6.0F)
				.texOffs(U2, V0).addBox(3.4F, -3.6F, -2.4F, 1.4F, 3.8F, 4.8F)
				.texOffs(U3, V0).addBox(4.4F, -2.8F, -1.4F, 0.6F, 2.2F, 2.8F)
				.texOffs(U1, V1).addBox(-1.4F, 4.2F, -2.6F, 5.0F, 5.4F, 5.2F), PartPose.ZERO);

		addArmoredBoot(root.getChild("right_leg"), U0, V0, U1, V1, U3, V0);
		addArmoredBoot(root.getChild("left_leg"), U0, V0, U1, V1, U3, V0);
		return LayerDefinition.create(mesh, 256, 256);
	}

	private static LayerDefinition buildAbyssalVoidLegs() {
		MeshDefinition mesh = blankHumanoidMesh();
		PartDefinition root = mesh.getRoot();
		root.getChild("body").addOrReplaceChild("void_tassets", CubeListBuilder.create()
				.texOffs(U2, V0).addBox(-4.8F, 9.2F, -3.1F, 9.6F, 2.8F, 6.2F)
				.texOffs(U3, V0).addBox(-1.2F, 9.6F, -3.45F, 2.4F, 2.0F, 0.6F), PartPose.ZERO);
		for (String side : new String[]{"right_leg", "left_leg"}) {
			float outer = side.equals("right_leg") ? -3.0F : 2.1F;
			root.getChild(side).addOrReplaceChild("void_thigh", CubeListBuilder.create()
					.texOffs(U0, V0).addBox(-2.45F, 0.0F, -2.45F, 4.9F, 7.6F, 4.9F)
					.texOffs(U3, V0).addBox(outer, 0.8F, -0.7F, 0.9F, 5.8F, 1.4F)
					.texOffs(U2, V0).addBox(-2.2F, 4.0F, -3.2F, 4.4F, 2.8F, 1.2F), PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 256, 256);
	}

	private static void addArmoredBoot(PartDefinition leg, int uMain, int vMain, int uPlate, int vPlate, int uAccent, int vAccent) {
		leg.addOrReplaceChild("boot", CubeListBuilder.create()
				.texOffs(uMain, vMain).addBox(-2.55F, 6.8F, -2.55F, 5.1F, 5.3F, 5.1F)
				.texOffs(uPlate, vPlate).addBox(-2.65F, 9.8F, -3.55F, 5.3F, 2.4F, 2.2F)
				.texOffs(uAccent, vAccent).addBox(-2.2F, 7.5F, -2.85F, 4.4F, 1.4F, 0.6F), PartPose.ZERO);
	}
}
