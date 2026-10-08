package dev.pitersonix.artificehandfix.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "io.redspace.irons_artifice.client.gun.GunInHandRenderer", remap = false)
public abstract class GunInHandRendererMixin {
    @Unique
    private static final ThreadLocal<List<mt_artifice_hand_fix$QueuedHand>> mt_artifice_hand_fix$queuedHands =
            ThreadLocal.withInitial(ArrayList::new);
    @Unique
    private static final ThreadLocal<Integer> mt_artifice_hand_fix$renderDepth = ThreadLocal.withInitial(() -> 0);
    @Unique
    private static PlayerModel<?> mt_artifice_hand_fix$wideArms;
    @Unique
    private static PlayerModel<?> mt_artifice_hand_fix$slimArms;

    @Inject(method = "renderRecursively(Lcom/mojang/blaze3d/vertex/PoseStack;Lio/redspace/irons_artifice/item/GunItem;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIII)V", at = @At("HEAD"), remap = false)
    private void mt_artifice_hand_fix$beginModelPass(CallbackInfo ci) {
        if (mt_artifice_hand_fix$renderDepth.get() == 0) {
            mt_artifice_hand_fix$queuedHands.get().clear();
        }
        mt_artifice_hand_fix$renderDepth.set(mt_artifice_hand_fix$renderDepth.get() + 1);
    }

    @Inject(method = "renderRecursively(Lcom/mojang/blaze3d/vertex/PoseStack;Lio/redspace/irons_artifice/item/GunItem;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIII)V", at = @At("TAIL"), remap = false)
    private void mt_artifice_hand_fix$finishModelPass(CallbackInfo ci) {
        int depth = mt_artifice_hand_fix$renderDepth.get() - 1;
        mt_artifice_hand_fix$renderDepth.set(depth);
        if (depth != 0) {
            return;
        }

        List<mt_artifice_hand_fix$QueuedHand> hands = mt_artifice_hand_fix$queuedHands.get();
        for (mt_artifice_hand_fix$QueuedHand hand : hands) {
            renderHand(hand);
        }
        hands.clear();
    }

    @Inject(method = "renderFirstPersonHand", at = @At("HEAD"), cancellable = true, remap = false)
    private void mt_artifice_hand_fix$queueHand(MultiBufferSource buffers, RenderType renderType,
                                                ModelPart modelPart, PoseStack poseStack, int packedLight,
                                                CallbackInfo ci) {
        PoseStack.Pose pose = poseStack.last();
        PoseStack savedPose = new PoseStack();
        savedPose.last().pose().set(pose.pose());
        savedPose.last().normal().set(pose.normal());
        mt_artifice_hand_fix$queuedHands.get().add(
                new mt_artifice_hand_fix$QueuedHand(buffers, renderType, mt_artifice_hand_fix$vanillaArm(modelPart), savedPose, packedLight));
        ci.cancel();
    }

    @Unique
    private static ModelPart mt_artifice_hand_fix$vanillaArm(ModelPart renderedArm) {
        Minecraft minecraft = Minecraft.getInstance();
        AbstractClientPlayer player = minecraft.player;
        if (player == null) {
            return renderedArm;
        }
        EntityRenderer<? super AbstractClientPlayer> entityRenderer = minecraft.getEntityRenderDispatcher().getRenderer(player);
        if (!(entityRenderer instanceof PlayerRenderer playerRenderer)
                || !(playerRenderer.getModel() instanceof PlayerModel<?> emfModel)) {
            return renderedArm;
        }

        boolean leftArm = renderedArm == emfModel.leftArm;
        boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
        PlayerModel<?> vanillaModel = mt_artifice_hand_fix$vanillaModel(slim);
        return leftArm ? vanillaModel.leftArm : vanillaModel.rightArm;
    }

    @Unique
    private static PlayerModel<?> mt_artifice_hand_fix$vanillaModel(boolean slim) {
        if (slim) {
            if (mt_artifice_hand_fix$slimArms == null) {
                mt_artifice_hand_fix$slimArms = mt_artifice_hand_fix$createVanillaModel(true);
            }
            return mt_artifice_hand_fix$slimArms;
        }
        if (mt_artifice_hand_fix$wideArms == null) {
            mt_artifice_hand_fix$wideArms = mt_artifice_hand_fix$createVanillaModel(false);
        }
        return mt_artifice_hand_fix$wideArms;
    }

    @Unique
    private static PlayerModel<?> mt_artifice_hand_fix$createVanillaModel(boolean slim) {
        return new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
    }

    @Unique
    private static void renderHand(mt_artifice_hand_fix$QueuedHand hand) {
        ModelPart arm = hand.modelPart();
        arm.x = arm.y = arm.z = 0.0F;
        arm.xRot = arm.yRot = arm.zRot = 0.0F;

        PoseStack pose = hand.poseStack();
        pose.pushPose();
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(1.0F / 16.0F, -10.0F / 16.0F, 0.0F);
        arm.render(pose, hand.buffers().getBuffer(hand.renderType()), hand.packedLight(), OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    @Unique
    private record mt_artifice_hand_fix$QueuedHand(MultiBufferSource buffers, RenderType renderType,
                                                    ModelPart modelPart, PoseStack poseStack, int packedLight) { }
}
