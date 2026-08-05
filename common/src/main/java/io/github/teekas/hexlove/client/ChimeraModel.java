package io.github.teekas.hexlove.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.teekas.hexlove.Hexlove;
import io.github.teekas.hexlove.entity.Chimera;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The authored Blockbench chimera geometry and its procedural animation rig.
 *
 * <p>The source model was built facing local -X rather than Minecraft's conventional -Z. A single
 * parent bone corrects that orientation while preserving every authored pivot. In that local space
 * the legs therefore stride around Z and head pitch is also a Z rotation.</p>
 */
public final class ChimeraModel extends EntityModel<Chimera> {
    public static final ModelLayerLocation LAYER_LOCATION =
        new ModelLayerLocation(Hexlove.id("chimera"), "main");

    private static final float DEG_TO_RAD = Mth.PI / 180.0F;
    private static final float THIRD_TURN = Mth.TWO_PI / 3.0F;
    private static final float HEADBUTT_IMPACT_TIME = 0.20F;

    private final ModelPart root;
    private final ModelPart[] allParts;
    private final ModelPart leftBackLeg;
    private final ModelPart tail;
    private final ModelPart rightFrontLeg;
    private final ModelPart body;
    private final ModelPart rightBackLeg;
    private final ModelPart leftFrontLeg;
    private final ModelPart foot;
    private final ModelPart fingerSmall;
    private final ModelPart fingerBasic;
    private final ModelPart fingerLong;
    private final ModelPart smallestFinger;
    private final ModelPart head;
    private final ModelPart jaw;

    public ChimeraModel(ModelPart bakedRoot) {
        this.root = bakedRoot.getChild("chimera");
        this.allParts = root.getAllParts().toArray(ModelPart[]::new);
        this.leftBackLeg = root.getChild("left_back_leg");
        this.tail = root.getChild("tail");
        this.rightFrontLeg = root.getChild("right_front_leg");
        this.body = root.getChild("body");
        this.rightBackLeg = root.getChild("right_back_leg");
        this.leftFrontLeg = root.getChild("left_front_leg");
        this.foot = leftFrontLeg.getChild("foot");
        this.fingerSmall = foot.getChild("finger_small");
        this.fingerBasic = foot.getChild("finger_basic");
        this.fingerLong = foot.getChild("finger_long");
        this.smallestFinger = foot.getChild("smalles_finger");
        this.head = root.getChild("head");
        this.jaw = head.getChild("jaw");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition layerRoot = meshDefinition.getRoot();

        // Blockbench's forward was -X. Rotate once at the rig root so Minecraft sees forward as -Z.
        PartDefinition root = layerRoot.addOrReplaceChild(
            "chimera",
            CubeListBuilder.create(),
            // The authored geometry is centred about three pixels to local -Z. After the corrective
            // quarter-turn that becomes a visible rightward offset, so compensate in model space.
            PartPose.offsetAndRotation(-3.0F, 0.0F, 0.0F, 0.0F, -Mth.HALF_PI, 0.0F)
        );

        PartDefinition leftBackLeg = root.addOrReplaceChild(
            "left_back_leg",
            CubeListBuilder.create(),
            PartPose.offset(15.0F, 7.0F, -7.7F)
        );
        leftBackLeg.addOrReplaceChild(
            "left_back_leg_r1",
            CubeListBuilder.create().texOffs(26, 28)
                .addBox(-2.0F, -0.003F, -1.8255F, 4.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.0349F, 0.0F, 0.0F)
        );

        PartDefinition tail = root.addOrReplaceChild(
            "tail",
            CubeListBuilder.create(),
            PartPose.offset(16.6826F, 5.5529F, -1.9366F)
        );
        tail.addOrReplaceChild(
            "tail_r1",
            CubeListBuilder.create().texOffs(43, 32)
                .addBox(-0.6535F, -3.1678F, -2.3483F, 3.0F, 7.0F, 5.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0483F, -0.0385F, -0.3688F)
        );

        root.addOrReplaceChild(
            "right_front_leg",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 17.0F, 4.0F, new CubeDeformation(0.0F)),
            PartPose.offset(-5.0F, 7.0F, 1.6F)
        );

        PartDefinition body = root.addOrReplaceChild(
            "body",
            CubeListBuilder.create(),
            PartPose.offset(5.6883F, 2.6698F, -1.693F)
        );

        PartDefinition front = body.addOrReplaceChild(
            "front",
            CubeListBuilder.create(),
            PartPose.offset(-2.3117F, 4.6698F, -5.693F)
        );
        front.addOrReplaceChild(
            "pig_nipple_1_r1",
            CubeListBuilder.create().texOffs(45, 40)
                .addBox(-0.4237F, -0.1073F, 0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(3.3F, -0.5F, 0.1F, -0.0133F, -0.0685F, 0.1924F)
        );
        front.addOrReplaceChild(
            "pig_nipple_2_r1",
            CubeListBuilder.create().texOffs(54, 32)
                .addBox(-0.4237F, -0.1073F, 0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(3.0F, -0.8F, 6.1F, -0.0133F, -0.0685F, 0.1924F)
        );
        front.addOrReplaceChild(
            "pig_nipple_3_r1",
            CubeListBuilder.create().texOffs(53, 36)
                .addBox(-0.3855F, 0.089F, 0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(6.2F, -0.2F, 0.3F, -0.0133F, -0.0685F, 0.1924F)
        );
        front.addOrReplaceChild(
            "pig_nipple_4_r1",
            CubeListBuilder.create().texOffs(54, 34)
                .addBox(-0.4237F, -0.1073F, 0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(6.0F, -0.4F, 6.3F, -0.0133F, -0.0685F, 0.1924F)
        );
        front.addOrReplaceChild(
            "base_r1",
            CubeListBuilder.create().texOffs(0, 5)
                .addBox(-10.0F, -11.0F, -15.0F, 14.0F, 11.0F, 16.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-1.3767F, -0.3395F, 11.3861F, 0.0391F, -0.0641F, -0.1253F)
        );

        PartDefinition back = body.addOrReplaceChild(
            "back",
            CubeListBuilder.create(),
            PartPose.offset(-3.6883F, 1.3302F, 5.693F)
        );
        back.addOrReplaceChild(
            "pig_nipple_5_r1",
            CubeListBuilder.create().texOffs(46, 38)
                .addBox(-0.4813F, -0.1844F, -0.507F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(1.2767F, 3.0395F, -11.3861F, 0.0181F, -0.0674F, -0.2624F)
        );
        back.addOrReplaceChild(
            "back_r1",
            CubeListBuilder.create().texOffs(44, 16)
                .addBox(-10.0F, -11.0F, -15.1F, 14.0F, 11.0F, 16.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(11.5F, 3.5F, 0.75F, 0.0265F, -0.0558F, 0.0851F)
        );

        PartDefinition rightBackLeg = root.addOrReplaceChild(
            "right_back_leg",
            CubeListBuilder.create(),
            PartPose.offset(14.5F, 6.2F, 4.6F)
        );
        rightBackLeg.addOrReplaceChild(
            "bottom_r1",
            CubeListBuilder.create().texOffs(43, 10)
                .addBox(-4.0F, 3.0F, -2.0F, 4.0F, 9.0F, 2.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(2.4F, 5.4F, 1.0F, 0.0F, 0.0F, -0.1309F)
        );
        rightBackLeg.addOrReplaceChild(
            "top_r1",
            CubeListBuilder.create().texOffs(60, 4)
                .addBox(-5.0F, -2.0F, -2.0F, 6.0F, 9.0F, 3.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(1.9F, 1.5F, 0.4F, 0.0F, 0.0F, -0.1309F)
        );

        PartDefinition leftFrontLeg = root.addOrReplaceChild(
            "left_front_leg",
            CubeListBuilder.create(),
            PartPose.offset(-4.8897F, 8.3395F, -9.85F)
        );
        leftFrontLeg.addOrReplaceChild(
            "base_r2",
            CubeListBuilder.create().texOffs(88, 15)
                .addBox(-1.0F, -2.0F, -1.0F, 2.0F, 15.0F, 2.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-0.1103F, 1.8605F, 0.05F, 0.0F, 0.0F, 0.0873F)
        );

        PartDefinition foot = leftFrontLeg.addOrReplaceChild(
            "foot",
            CubeListBuilder.create().texOffs(16, 1)
                .addBox(-1.0F, -1.0F, -0.4F, 4.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(88, 14)
                .addBox(-0.5F, -1.9F, 0.1F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)),
            PartPose.offset(-2.6103F, 15.6605F, -1.05F)
        );

        foot.addOrReplaceChild(
            "finger_small",
            CubeListBuilder.create().texOffs(90, 14)
                .addBox(-1.8488F, 0.45F, -0.5976F, 1.0F, 0.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(13, 0)
                .addBox(-0.8488F, -0.55F, -0.5976F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-1.0F, -0.45F, 0.2F, 0.0F, -0.2443F, 0.0F)
        );
        foot.addOrReplaceChild(
            "finger_basic",
            CubeListBuilder.create().texOffs(13, 2)
                .addBox(-2.1118F, -0.4F, -0.5915F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(90, 14)
                .addBox(-3.1118F, 0.6F, -0.5915F, 1.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-0.7F, -0.6F, 2.1F, 0.0F, 0.2618F, 0.0F)
        );
        foot.addOrReplaceChild(
            "finger_long",
            CubeListBuilder.create().texOffs(94, 15)
                .addBox(-3.3F, -0.6F, -0.6F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(90, 14)
                .addBox(-4.3F, 0.4F, -0.6F, 1.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)),
            PartPose.offset(-0.7F, -0.4F, 1.2F)
        );
        PartDefinition smallestFinger = foot.addOrReplaceChild(
            "smalles_finger",
            CubeListBuilder.create(),
            PartPose.offset(1.9F, -0.5F, 0.1F)
        );
        smallestFinger.addOrReplaceChild(
            "smalles_finger_r1",
            CubeListBuilder.create().texOffs(30, 3)
                .addBox(-2.0823F, -0.5F, -0.5309F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.9774F, 0.0F)
        );

        PartDefinition head = root.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(96, 8)
                .addBox(-7.2217F, -8.9975F, -5.011F, 7.0F, 9.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(104, 29)
                .addBox(-7.7217F, -4.9975F, -4.911F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-8.1F, 1.4F, -4.1F, 0.0F, -0.0698F, -0.4363F)
        );

        head.addOrReplaceChild(
            "eye_right",
            CubeListBuilder.create().texOffs(110, 29)
                .addBox(-5.0F, -5.0F, -8.5F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(110, 26)
                .addBox(-4.9F, -5.5F, -8.5F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(98, 29)
                .addBox(-4.9F, -3.5F, -8.4F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(102, 28)
                .addBox(-4.9F, -5.0F, -7.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(106, 26)
                .addBox(-4.9F, -5.0F, -9.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
            PartPose.offset(-2.7217F, 0.0025F, 9.889F)
        );

        head.addOrReplaceChild(
            "jaw",
            CubeListBuilder.create().texOffs(120, 28)
                .addBox(-27.0F, -22.0F, -48.0F, 1.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(0, 32)
                .addBox(-31.0F, -22.5F, -50.5F, 3.0F, 3.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(104, 26)
                .addBox(-32.0F, -22.0F, -50.0F, 5.0F, 3.0F, 7.0F, new CubeDeformation(0.0F)),
            PartPose.offset(24.7783F, 22.0025F, 45.989F)
        );

        PartDefinition horn = head.addOrReplaceChild(
            "horn",
            CubeListBuilder.create(),
            PartPose.offsetAndRotation(-4.1413F, -3.2908F, 5.839F, 3.1416F, 0.0F, -2.9758F)
        );
        horn.addOrReplaceChild(
            "horn_part_10_r1",
            CubeListBuilder.create().texOffs(13, 33)
                .addBox(-0.4233F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-0.481F, -0.5671F, -0.25F, 0.0F, 0.0F, 1.5359F)
        );
        horn.addOrReplaceChild(
            "horn_part_9_r1",
            CubeListBuilder.create().texOffs(0, 36)
                .addBox(0.0F, -1.5F, -1.0F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(0.4474F, -0.9934F, -0.35F, 0.0F, 0.0F, 1.6232F)
        );
        horn.addOrReplaceChild(
            "horn_part_8_r1",
            CubeListBuilder.create().texOffs(119, 13)
                .addBox(-1.0F, -1.5F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(1.9991F, -0.1436F, -0.25F, 0.0F, 0.0F, 2.1468F)
        );
        horn.addOrReplaceChild(
            "horn_part_7_r1",
            CubeListBuilder.create().texOffs(120, 36)
                .addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(2.5802F, 1.3605F, -0.15F, 0.0F, 0.0F, -3.0281F)
        );
        horn.addOrReplaceChild(
            "horn_part_6_r1",
            CubeListBuilder.create().texOffs(112, 36)
                .addBox(-1.0F, -1.5F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(0.7359F, 3.0571F, -0.05F, 0.0F, 0.0F, -1.9897F)
        );
        horn.addOrReplaceChild(
            "horn_part_5_r1",
            CubeListBuilder.create().texOffs(104, 36)
                .addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-1.0588F, 2.8862F, 0.05F, 0.0F, 0.0F, -1.021F)
        );
        horn.addOrReplaceChild(
            "horn_part_4_r1",
            CubeListBuilder.create().texOffs(42, 32)
                .addBox(-0.5F, -1.0F, -4.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-3.0377F, 0.9727F, 3.15F, 0.0F, 0.0F, -0.3665F)
        );
        horn.addOrReplaceChild(
            "horn_part_3_r1",
            CubeListBuilder.create().texOffs(30, 23)
                .addBox(-1.5F, -1.0F, -4.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-1.9932F, -1.4519F, 3.25F, 0.0F, 0.0F, 0.192F)
        );
        horn.addOrReplaceChild(
            "horn_part_2_r1",
            CubeListBuilder.create().texOffs(4, 32)
                .addBox(-1.5F, -1.0F, -4.0F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-0.6827F, -2.8093F, 2.75F, 0.0F, 0.0F, 0.8901F)
        );
        horn.addOrReplaceChild(
            "horn_part_1_r1",
            CubeListBuilder.create().texOffs(14, 32)
                .addBox(-2.5F, -1.0F, -4.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(1.384F, -2.6492F, 2.85F, 0.0F, 0.0F, 1.4661F)
        );

        PartDefinition pigNose = head.addOrReplaceChild(
            "pig_nouse",
            CubeListBuilder.create().texOffs(78, 1)
                .addBox(-2.0F, -3.0F, -2.0F, 2.0F, 5.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(77, 2)
                .addBox(-2.01F, -3.0F, 3.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(77, 0)
                .addBox(-2.01F, -3.0F, -2.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(76, 4)
                .addBox(-2.01F, -1.0F, 2.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(80, 4)
                .addBox(-2.01F, -1.0F, -1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
            PartPose.offset(-7.2217F, 0.1025F, -1.511F)
        );
        pigNose.addOrReplaceChild(
            "chicken_tuft",
            CubeListBuilder.create().texOffs(46, 1)
                .addBox(-0.5F, -3.6667F, -2.3333F, 1.0F, 3.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(44, 1)
                .addBox(-0.5F, -0.6667F, -1.3333F, 1.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(42, 1)
                .addBox(-0.5F, 1.3333F, -1.3333F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)),
            PartPose.offset(-1.0F, 5.6667F, 0.8333F)
        );

        return LayerDefinition.create(meshDefinition, 128, 64);
    }

    @Override
    public void setupAnim(
        Chimera entity,
        float limbSwing,
        float limbSwingAmount,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        for (ModelPart part : allParts) {
            part.resetPose();
        }

        float movement = Mth.clamp(limbSwingAmount * 1.35F, 0.0F, 1.0F);
        float idle = 1.0F - movement;
        float phase = limbSwing * 0.72F;

        // Three usable legs take their turns 120 degrees apart. The airborne pig hoof never plants:
        // it only trails the body with a short, soft pendulum motion.
        float rightFrontPhase = phase;
        float leftFrontPhase = phase + THIRD_TURN;
        float rightBackPhase = phase + THIRD_TURN * 2.0F;
        float rightFrontStride = Mth.cos(rightFrontPhase) * 0.62F * movement;
        float leftFrontStride = Mth.cos(leftFrontPhase) * 0.58F * movement;
        float rightBackStride = Mth.cos(rightBackPhase) * 0.54F * movement;
        float rightFrontLift = positiveSine(rightFrontPhase) * movement;
        float leftFrontLift = positiveSine(leftFrontPhase) * movement;
        float rightBackLift = positiveSine(rightBackPhase) * movement;

        rightFrontLeg.zRot += rightFrontStride;
        leftFrontLeg.zRot += leftFrontStride;
        rightBackLeg.zRot += rightBackStride;
        rightFrontLeg.y -= rightFrontLift * 0.72F;
        leftFrontLeg.y -= leftFrontLift * 0.62F;
        rightBackLeg.y -= rightBackLift * 0.52F;

        leftBackLeg.zRot += 0.10F
            + Mth.sin(phase - 0.85F) * 0.15F * movement
            + Mth.sin(ageInTicks * 0.075F + 0.8F) * 0.025F;
        leftBackLeg.yRot += Mth.sin(phase * 0.5F + 1.3F) * 0.055F * movement;

        // Keep the bird foot near the ground while its long shin swings, then spread and curl the
        // toes as weight moves on and off it.
        foot.zRot -= leftFrontStride * 0.72F;
        foot.xRot += leftFrontLift * 0.08F;
        float toeSpread = 0.035F + leftFrontLift * 0.10F;
        fingerSmall.yRot -= toeSpread;
        fingerBasic.yRot += toeSpread * 0.55F;
        fingerLong.yRot -= toeSpread * 0.18F;
        smallestFinger.yRot += toeSpread * 0.72F;
        float toeCurl = leftFrontLift * 0.075F * movement;
        fingerSmall.zRot += toeCurl;
        fingerBasic.zRot += toeCurl * 0.8F;
        fingerLong.zRot += toeCurl * 1.15F;

        // At rest every toe rises a fraction and returns exactly to its authored pose; none rotates
        // below the baseline, so even the longest claw cannot dip through the ground. The shortest
        // outer toe also makes a tiny independent side-to-side adjustment.
        float idleFingerLift = (0.5F + 0.5F * Mth.sin(ageInTicks * 0.115F)) * 0.032F * idle;
        float idleBasicLift = (0.5F + 0.5F * Mth.sin(ageInTicks * 0.105F + 0.9F)) * 0.024F * idle;
        float idleLongLift = (0.5F + 0.5F * Mth.sin(ageInTicks * 0.095F + 1.8F)) * 0.019F * idle;
        fingerSmall.zRot += idleFingerLift;
        fingerBasic.zRot += idleBasicLift;
        fingerLong.zRot += idleLongLift;
        smallestFinger.zRot += idleFingerLift * 0.72F;
        smallestFinger.yRot += Mth.sin(ageInTicks * 0.087F + 0.5F) * 0.028F * idle;

        // Uneven support gives the animal a restrained weight shift and vertical hitch. Breathing
        // continues during motion, but becomes subtler so it never fights the gait.
        float breath = Mth.sin(ageInTicks * 0.105F);
        float hitch = (rightFrontLift + leftFrontLift + rightBackLift) / 3.0F;
        root.y -= hitch * 0.32F;
        root.xRot += Mth.sin(phase + 0.45F) * 0.022F * movement;
        root.zRot += Mth.sin(phase * 0.5F - 0.6F) * 0.028F * movement;
        body.y -= breath * (0.13F * idle + 0.045F * movement);
        body.xRot += Mth.sin(ageInTicks * 0.075F + 0.7F) * 0.012F;
        body.zRot += Mth.sin(phase - 0.2F) * 0.018F * movement;

        // A successful server-side hit starts an explicit 14-tick client animation. Its longer,
        // dedicated clock cannot be swallowed by the generic six-tick hand swing.
        float partialTick = Mth.clamp(ageInTicks - entity.tickCount, 0.0F, 1.0F);
        float attack = entity.headbuttAnimationProgress(partialTick);
        float headbutt;
        if (attack < HEADBUTT_IMPACT_TIME) {
            headbutt = smoothStep(attack / HEADBUTT_IMPACT_TIME);
        } else {
            float recovery = (attack - HEADBUTT_IMPACT_TIME) / (1.0F - HEADBUTT_IMPACT_TIME);
            headbutt = 1.0F - smoothStep(recovery);
        }

        // The regular Mob look controller supplies these values, so LookAtPlayerGoal and tempting
        // with raw meat naturally make the chimera follow a player just like a cow. During a
        // headbutt the gaze yields to the strike pose instead of twisting the lunging head sideways.
        float lookWeight = 1.0F - headbutt * 0.62F;
        float trackedYaw = Mth.clamp(netHeadYaw, -48.0F, 48.0F) * DEG_TO_RAD;
        float trackedPitch = Mth.clamp(headPitch, -28.0F, 34.0F) * DEG_TO_RAD;
        head.yRot += trackedYaw * 0.88F * lookWeight;
        head.zRot -= trackedPitch * 0.78F * lookWeight;
        head.yRot += Mth.sin(ageInTicks * 0.037F + 0.9F) * 0.028F * idle;
        head.zRot += breath * 0.018F * idle - rightFrontLift * 0.025F;
        head.xRot += Mth.sin(ageInTicks * 0.051F) * 0.012F * idle;

        // Turn the event progress into a heavy headbutt: the jaw flashes open first, the skull and
        // torso drive forward and down, the planted legs brace, and the unused rear hoof kicks with
        // the recoil. A damped aftershock keeps the impact readable before the weighted return.
        if (attack > 0.0F) {
            float brace = Mth.sin(attack * Mth.PI);
            float jawOpen = Mth.sin(Mth.clamp(attack / 0.38F, 0.0F, 1.0F) * Mth.PI);
            float aftershock = attack > HEADBUTT_IMPACT_TIME
                ? Mth.sin((attack - HEADBUTT_IMPACT_TIME) * Mth.PI * 4.0F) * (1.0F - attack)
                : 0.0F;

            head.x -= headbutt * 5.60F;
            head.y += headbutt * 1.35F;
            head.zRot -= headbutt * 0.62F;
            head.xRot += aftershock * 0.075F;
            jaw.zRot -= jawOpen * 0.42F;

            body.x -= headbutt * 1.10F;
            body.y += headbutt * 0.26F;
            body.zRot -= headbutt * 0.14F;
            root.y += headbutt * 0.42F;

            rightFrontLeg.zRot += brace * 0.18F;
            leftFrontLeg.zRot += brace * 0.15F;
            rightBackLeg.zRot -= brace * 0.10F;
            leftBackLeg.zRot += headbutt * 0.24F;
            tail.zRot += aftershock * 0.09F;
        }

        // The authored tail is very short, so it only makes restrained balance corrections. Jaw,
        // eye, snout and chicken comb otherwise move with the head; only an attack briefly opens the
        // jaw so the strike silhouette stays easy to distinguish from ordinary looking and walking.
        tail.yRot += Mth.sin(ageInTicks * 0.145F + 0.4F) * (0.045F + 0.018F * movement);
        tail.yRot += Mth.sin(phase * 0.67F + 1.8F) * 0.035F * movement;
        tail.zRot += Mth.sin(ageInTicks * 0.09F - 0.5F) * 0.016F
            + Mth.cos(phase + 0.2F) * 0.014F * movement;
        tail.xRot += Mth.sin(ageInTicks * 0.071F) * 0.007F;
    }

    private static float positiveSine(float value) {
        return Math.max(0.0F, Mth.sin(value));
    }

    private static float smoothStep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    @Override
    public void renderToBuffer(
        PoseStack poseStack,
        VertexConsumer vertexConsumer,
        int packedLight,
        int packedOverlay,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        root.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
