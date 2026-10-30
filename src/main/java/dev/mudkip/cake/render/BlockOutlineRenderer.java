package dev.mudkip.cake.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mudkip.cake.module.modules.BlockOutlineModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.ARGB;

public final class BlockOutlineRenderer {
    private static final double OVERLAY_EXPAND = 0.002D;

    private BlockOutlineRenderer() {
    }

    public static void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, LevelRenderState levelRenderState, BlockOutlineModule module) {
        var state = levelRenderState.blockOutlineRenderState;

        if (state == null) {
            return;
        }

        var borderColor = module.getBorderColor();
        var overlayColor = module.getOverlayColor();
        var border = module.isBorderEnabled() && ARGB.alpha(borderColor) > 0;
        var overlay = module.isOverlayEnabled() && ARGB.alpha(overlayColor) > 0;

        if (!border && !overlay) {
            return;
        }

        var camera = levelRenderState.cameraRenderState.pos;
        var blockPosition = state.pos();
        poseStack.pushPose();
        poseStack.translate((double) blockPosition.getX() - camera.x, (double) blockPosition.getY() - camera.y, (double) blockPosition.getZ() - camera.z);

        if (overlay) {
            BlockOutlineRenderer.submitOverlay(
                    poseStack,
                    submitNodeCollector,
                    state,
                    overlayColor,
                    camera.x - blockPosition.getX(),
                    camera.y - blockPosition.getY(),
                    camera.z - blockPosition.getZ()
            );
        }

        if (border) {
            var minecraft = Minecraft.getInstance();
            var renderType = minecraft.gameRenderer.useImprovedTransparency()
                    ? RenderTypes.linesTranslucentNoDepthWrite()
                    : RenderTypes.linesTranslucent();
            var width = minecraft.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth * module.getBorderThickness();
            submitNodeCollector.submitShapeOutline(poseStack, state.shape(), renderType, borderColor, width, state.isTranslucent());
        }

        poseStack.popPose();
    }

    private static void submitOverlay(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, BlockOutlineRenderState state, int color, double cameraX, double cameraY, double cameraZ) {
        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) ->
                state.shape().forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
                    var x1 = minX - BlockOutlineRenderer.OVERLAY_EXPAND;
                    var y1 = minY - BlockOutlineRenderer.OVERLAY_EXPAND;
                    var z1 = minZ - BlockOutlineRenderer.OVERLAY_EXPAND;
                    var x2 = maxX + BlockOutlineRenderer.OVERLAY_EXPAND;
                    var y2 = maxY + BlockOutlineRenderer.OVERLAY_EXPAND;
                    var z2 = maxZ + BlockOutlineRenderer.OVERLAY_EXPAND;

                    if (cameraX < x1) {
                        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color);
                        buffer.addVertex(pose, (float) x1, (float) y1, (float) z2).setColor(color);
                        buffer.addVertex(pose, (float) x1, (float) y2, (float) z2).setColor(color);
                        buffer.addVertex(pose, (float) x1, (float) y2, (float) z1).setColor(color);
                    } else if (cameraX > x2) {
                        buffer.addVertex(pose, (float) x2, (float) y1, (float) z1).setColor(color);
                        buffer.addVertex(pose, (float) x2, (float) y2, (float) z1).setColor(color);
                        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color);
                        buffer.addVertex(pose, (float) x2, (float) y1, (float) z2).setColor(color);
                    }

                    if (cameraY < y1) {
                        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color);
                        buffer.addVertex(pose, (float) x2, (float) y1, (float) z1).setColor(color);
                        buffer.addVertex(pose, (float) x2, (float) y1, (float) z2).setColor(color);
                        buffer.addVertex(pose, (float) x1, (float) y1, (float) z2).setColor(color);
                    } else if (cameraY > y2) {
                        buffer.addVertex(pose, (float) x1, (float) y2, (float) z1).setColor(color);
                        buffer.addVertex(pose, (float) x1, (float) y2, (float) z2).setColor(color);
                        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color);
                        buffer.addVertex(pose, (float) x2, (float) y2, (float) z1).setColor(color);
                    }

                    if (cameraZ < z1) {
                        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color);
                        buffer.addVertex(pose, (float) x1, (float) y2, (float) z1).setColor(color);
                        buffer.addVertex(pose, (float) x2, (float) y2, (float) z1).setColor(color);
                        buffer.addVertex(pose, (float) x2, (float) y1, (float) z1).setColor(color);
                    } else if (cameraZ > z2) {
                        buffer.addVertex(pose, (float) x1, (float) y1, (float) z2).setColor(color);
                        buffer.addVertex(pose, (float) x2, (float) y1, (float) z2).setColor(color);
                        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color);
                        buffer.addVertex(pose, (float) x1, (float) y2, (float) z2).setColor(color);
                    }
                }));
    }
}
