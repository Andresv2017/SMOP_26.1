package net.darkblade.smop.client.projectile;

import net.darkblade.smop.SMOP;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

/**
 * The spear mesh.
 *
 * <p>Geometry is authored art and travels unchanged. What changed around it is the base class:
 * 26.1's {@code Model} is generic over the state it is handed, and a projectile has none, so it is
 * {@code Model<Unit>} — the same declaration vanilla's own {@code TridentModel} carries. The root
 * part is passed to {@code super} now instead of being rendered by an overridden
 * {@code renderToBuffer}, which no longer exists.
 */
public class NirasSpearModel extends Model<Unit> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(SMOP.id("niras_spear"), "main");

    public static final Identifier TEXTURE = SMOP.id("textures/entity/niras_spear.png");

    public NirasSpearModel(ModelPart root) {
        super(root, RenderTypes::entitySolid);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition bone = root.addOrReplaceChild("bone", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-8.5F, -25.0F, 7.5F, 1.0F, 25.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(4, 5).addBox(-8.5F, -25.0F, 7.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.01F)),
                PartPose.offset(8.0F, 24.0F, -8.0F));

        bone.addOrReplaceChild("cube_r1", CubeListBuilder.create()
                        .texOffs(8, 1).mirror().addBox(-1.0F, -6.0F, 0.0F, 2.0F, 7.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false)
                        .texOffs(9, 0).mirror().addBox(-0.5F, -7.0F, 0.0F, 1.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offsetAndRotation(-8.0F, -25.0F, 8.0F, 0.0F, 0.7854F, 0.0F));

        bone.addOrReplaceChild("cube_r2", CubeListBuilder.create()
                        .texOffs(9, 0).addBox(-0.5F, -7.0F, 0.0F, 1.0F, 1.0F, 0.0F, new CubeDeformation(0.0F))
                        .texOffs(8, 1).addBox(-1.0F, -6.0F, 0.0F, 2.0F, 7.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-8.0F, -25.0F, 8.0F, 0.0F, -0.7854F, 0.0F));

        return LayerDefinition.create(mesh, 32, 32);
    }
}
