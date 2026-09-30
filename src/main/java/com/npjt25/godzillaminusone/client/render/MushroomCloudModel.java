package com.npjt25.godzillaminusone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Alex's Caves' mushroom cloud (MushroomCloudModel, by AlexModGuy, written with Citadel's AdvancedModelBox): the same
 * parts, pivots, rotations, boxes and 512 x 512 texture offsets, built as vanilla model parts (which, like the
 * source's parts with scaleChildren set, pass their scale on to their children), and its animation
 * ({@link #animate}, the source's animateParticle and hideFireball). tools/mushroom_cloud_model.py holds the same
 * model for tools/preview_cloud.py.
 *
 * <pre>
 *   mushroom_cloud
 *     lightBall          the fireball: a 32 px cube
 *       lowerCloud       the low cloud spreading over the ground (100 x 16 x 100) and four crossed planes of smoke
 *       plume            the rising column: two crossed planes (64 x 122) that stretch upwards as it grows
 *         plumeBlast     the fiery bulge at its top
 *         plumeRing      a flat ring that races outwards: the shockwave
 *         upperCloud     the cap (80 x 24 x 80 and 64 x 15 x 64 on top), four crossed planes of billowing smoke,
 *           rings        and two flat rings above it that spread out and turn
 * </pre>
 */
public final class MushroomCloudModel {
    private static final float QUARTER = 0.7854F;

    private final ModelPart root;
    private final ModelPart lowerCloud;
    private final ModelPart plume;
    private final ModelPart plumeRing;
    private final ModelPart upperCloud;
    private final ModelPart upperRing;
    private final ModelPart lowerRing;

    public MushroomCloudModel() {
        this.root = createLayer().bakeRoot();
        ModelPart lightBall = this.root.getChild("mushroom_cloud").getChild("lightBall");
        this.lowerCloud = lightBall.getChild("lowerCloud");
        this.plume = lightBall.getChild("plume");
        this.plumeRing = this.plume.getChild("plumeRing");
        this.upperCloud = this.plume.getChild("upperCloud");
        ModelPart rings = this.upperCloud.getChild("rings");
        this.upperRing = rings.getChild("upperRing");
        this.lowerRing = rings.getChild("lowerRing");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition cloud = mesh.getRoot().addOrReplaceChild("mushroom_cloud", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
        PartDefinition lightBall = cloud.addOrReplaceChild("lightBall", CubeListBuilder.create().texOffs(300, 0)
                .addBox(-16.0F, -16.0F, -16.0F, 32.0F, 32.0F, 32.0F), PartPose.offset(0.0F, -19.0F, 0.0F));

        PartDefinition lowerCloud = lightBall.addOrReplaceChild("lowerCloud", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-50.0F, -9.0F, -50.0F, 100.0F, 16.0F, 100.0F), PartPose.offset(0.0F, 5.0F, 0.0F));
        PartDefinition lowerPlanes = lowerCloud.addOrReplaceChild("lowerCloud_planes", CubeListBuilder.create(), PartPose.ZERO);
        lowerPlanes.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(142, 342)
                .addBox(-66.0F, -13.0F, 0.0F, 66.0F, 40.0F, 0.0F, true), PartPose.offsetAndRotation(-16.0F, -20.0F, 16.0F, 0.0F, QUARTER, 0.0F));
        lowerPlanes.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(142, 342)
                .addBox(0.0F, -13.0F, 0.0F, 66.0F, 40.0F, 0.0F), PartPose.offsetAndRotation(16.0F, -20.0F, -16.0F, 0.0F, QUARTER, 0.0F));
        lowerPlanes.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(142, 342)
                .addBox(0.0F, -13.0F, 0.0F, 66.0F, 40.0F, 0.0F), PartPose.offsetAndRotation(16.0F, -20.0F, 16.0F, 0.0F, -QUARTER, 0.0F));
        lowerPlanes.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(142, 342)
                .addBox(-66.0F, -13.0F, 0.0F, 66.0F, 40.0F, 0.0F, true), PartPose.offsetAndRotation(-16.0F, -20.0F, -16.0F, 0.0F, -QUARTER, 0.0F));

        PartDefinition plume = lightBall.addOrReplaceChild("plume", CubeListBuilder.create(), PartPose.offset(0.0F, 9.0F, 0.0F));
        plume.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(256, 220)
                .addBox(-32.0F, -61.0F, 0.0F, 64.0F, 122.0F, 0.0F), PartPose.offsetAndRotation(0.0F, -44.8333F, 0.0F, 0.0F, -QUARTER, 0.0F));
        plume.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(256, 220)
                .addBox(-32.0F, -61.0F, 0.0F, 64.0F, 122.0F, 0.0F), PartPose.offsetAndRotation(0.0F, -44.8333F, 0.0F, 0.0F, QUARTER, 0.0F));
        plume.addOrReplaceChild("plumeBlast", CubeListBuilder.create().texOffs(240, 116)
                .addBox(-18.0F, 0.0F, -18.0F, 36.0F, 29.0F, 36.0F), PartPose.offset(0.0F, -80.8333F, 0.0F));
        plume.addOrReplaceChild("plumeRing", CubeListBuilder.create().texOffs(296, 440)
                .addBox(-36.0F, 0.0F, -36.0F, 72.0F, 0.0F, 72.0F), PartPose.offset(0.0F, -35.8333F, 0.0F));

        PartDefinition upperCloud = plume.addOrReplaceChild("upperCloud", CubeListBuilder.create().texOffs(0, 116)
                .addBox(-40.0F, -24.0F, -40.0F, 80.0F, 24.0F, 80.0F), PartPose.offset(0.0F, -80.8333F, 0.0F));
        upperCloud.addOrReplaceChild("upperCloud1", CubeListBuilder.create().texOffs(0, 220)
                .addBox(-32.0F, -15.0F, -32.0F, 64.0F, 15.0F, 64.0F), PartPose.offset(0.0F, -24.0F, 0.0F));
        PartDefinition upperPlanes = upperCloud.addOrReplaceChild("upperCloud_planes", CubeListBuilder.create(), PartPose.offset(0.0F, -12.0F, 0.0F));
        upperPlanes.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(0, 299)
                .addBox(-23.0F, -152.0F, 0.0F, 71.0F, 69.0F, 0.0F), PartPose.offsetAndRotation(16.0F, 112.0F, -16.0F, 0.0F, QUARTER, 0.0F));
        upperPlanes.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(0, 299)
                .addBox(-23.0F, -152.0F, 0.0F, 71.0F, 69.0F, 0.0F), PartPose.offsetAndRotation(16.0F, 112.0F, 16.0F, 0.0F, -QUARTER, 0.0F));
        upperPlanes.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(0, 299)
                .addBox(-48.0F, -152.0F, 0.0F, 71.0F, 69.0F, 0.0F, true), PartPose.offsetAndRotation(-16.0F, 112.0F, 16.0F, 0.0F, QUARTER, 0.0F));
        upperPlanes.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(0, 299)
                .addBox(-48.0F, -152.0F, 0.0F, 71.0F, 69.0F, 0.0F, true), PartPose.offsetAndRotation(-16.0F, 112.0F, -16.0F, 0.0F, -QUARTER, 0.0F));
        PartDefinition rings = upperCloud.addOrReplaceChild("rings", CubeListBuilder.create(), PartPose.offset(0.0F, -55.0F, 0.0F));
        rings.addOrReplaceChild("upperRing", CubeListBuilder.create().texOffs(296, 440)
                .addBox(-36.0F, 0.0F, -36.0F, 72.0F, 0.0F, 72.0F), PartPose.offset(0.0F, -4.0F, 0.0F));
        rings.addOrReplaceChild("lowerRing", CubeListBuilder.create().texOffs(296, 440)
                .addBox(-36.0F, 0.0F, -36.0F, 72.0F, 0.0F, 72.0F), PartPose.offset(0.0F, 4.0F, 0.0F));
        return LayerDefinition.create(mesh, 512, 512);
    }

    /**
     * Poses the cloud (the source's animateParticle and hideFireball).
     *
     * @param fireballOnly before the fireball has burst into the mushroom (its first 10 ticks) only the ball shows
     * @param life         how far it has grown: the source's smoothed log(1 + t) * 2, about 0 as the mushroom appears,
     *                     1 when it's fully grown
     * @param ticks        its age in ticks (with the partial tick), for the turning rings
     */
    public void animate(boolean fireballOnly, float life, float ticks) {
        this.root.getAllParts().forEach(ModelPart::resetPose);
        this.plume.visible = !fireballOnly;
        this.lowerCloud.visible = !fireballOnly;
        this.upperCloud.visible = !fireballOnly;
        float base = life + life * life;
        float upper = Mth.clamp(life + 0.3F, 0.4F, 1.5F);
        float plumeRing = Mth.clamp(life, 0.0F, 0.5F) * 45.0F;
        float lowerRing = Mth.clamp(life, 0.1F, 0.65F) * 20.0F;
        float upperRing = Mth.clamp(life, 0.2F, 0.65F) * 15.0F;
        scale(this.plume, 1.0F, life * 1.5F, 1.0F);
        scale(this.upperCloud, upper, 1.0F, upper);
        scale(this.plumeRing, plumeRing, 1.0F, plumeRing);
        scale(this.lowerRing, lowerRing, 1.0F, lowerRing);
        scale(this.upperRing, upperRing, 1.0F, upperRing);
        scale(this.lowerCloud, base, base, base);
        this.plumeRing.yRot += ticks * 0.1F;
        this.lowerRing.yRot -= ticks * 0.2F;
        this.upperRing.yRot += ticks * 0.1F;
    }

    /** (A part scaled to nothing would give its faces infinite normals: they're kept a hair above zero instead.) */
    private static void scale(ModelPart part, float x, float y, float z) {
        part.xScale = Math.max(x, 1.0E-4F);
        part.yScale = Math.max(y, 1.0E-4F);
        part.zScale = Math.max(z, 1.0E-4F);
    }

    public void render(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, light, overlay, red, green, blue, alpha);
    }
}
