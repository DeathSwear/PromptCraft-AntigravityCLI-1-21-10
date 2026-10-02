package dev.promptcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GhostRenderer {
    private static final int MAX_DETAILED_BLOCKS = 40000;

    private GhostRenderer() {}

    public static void render(WorldRenderContext context) {
        if (!GhostPreviewState.isActive()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        Map<BlockPos, BlockState> blocks = GhostPreviewState.getFlattenedRotatedBlocks();
        if (blocks.isEmpty()) return;

        BlockPos anchor = GhostPreviewState.getCurrentAnchor();
        Vec3d cam = context.camera().getPos();

        // Только видимая оболочка: воздух и полностью замурованные блоки не рисуем.
        List<Map.Entry<BlockPos, BlockState>> visible = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            if (e.getValue().isAir()) continue;
            if (isEnclosed(e.getKey(), blocks)) continue;
            visible.add(e);
        }
        if (visible.isEmpty()) return;

        if (visible.size() > MAX_DETAILED_BLOCKS) {
            renderBox(context, blocks, anchor, cam, 0.35f, 0.85f, 1.0f, 0.30f);
            return;
        }

        BlockRenderManager brm = client.getBlockRenderManager();
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        int light = LightmapTextureManager.pack(15, 15);
        MatrixStack matrices = context.matrixStack();
        Random random = Random.create();

        // Непрозрачно, с отсечением задних граней и записью глубины -> настоящий
        // макет из текстур блоков. Никакого наложения полупрозрачных слоёв.
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);

        for (Map.Entry<BlockPos, BlockState> entry : visible) {
            BlockState state = entry.getValue();
            BlockPos local = entry.getKey();
            BakedModel model = brm.getModel(state);

            matrices.push();
            matrices.translate(
                anchor.getX() + local.getX() - cam.x,
                anchor.getY() + local.getY() - cam.y,
                anchor.getZ() + local.getZ() - cam.z
            );

            // Чуть раздуваем блок вокруг центра, чтобы его грани не совпадали
            // в одной плоскости с блоками мира -> убирает z-fighting/мерцание.
            float s = 1.01f;
            matrices.translate(0.5, 0.5, 0.5);
            matrices.scale(s, s, s);
            matrices.translate(-0.5, -0.5, -0.5);

            RenderLayer layer = RenderLayers.getEntityBlockLayer(state, false);
            VertexConsumer vc = immediate.getBuffer(layer);
            brm.getModelRenderer().render(
                matrices.peek(), vc, state, model,
                1.0f, 1.0f, 1.0f, light, OverlayTexture.DEFAULT_UV
            );
            matrices.pop();
        }
        immediate.draw();

        // Тонкий цветной габарит поверх - чтобы читалось как "предпросмотр".
        renderBox(context, blocks, anchor, cam, 0.35f, 0.85f, 1.0f, 0.10f);
    }

    private static boolean isEnclosed(BlockPos p, Map<BlockPos, BlockState> b) {
        return present(b, p.up()) && present(b, p.down())
            && present(b, p.north()) && present(b, p.south())
            && present(b, p.east()) && present(b, p.west());
    }

    private static boolean present(Map<BlockPos, BlockState> b, BlockPos p) {
        BlockState s = b.get(p);
        return s != null && !s.isAir();
    }

    private static void renderBox(WorldRenderContext context, Map<BlockPos, BlockState> blocks,
                                  BlockPos anchor, Vec3d cam, float r, float g, float bl, float a) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : blocks.keySet()) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        Box box = new Box(
            anchor.getX() + minX, anchor.getY() + minY, anchor.getZ() + minZ,
            anchor.getX() + maxX + 1, anchor.getY() + maxY + 1, anchor.getZ() + maxZ + 1
        ).offset(-cam.x, -cam.y, -cam.z).expand(0.02);

        Matrix4f m = context.matrixStack().peek().getPositionMatrix();
        var tess = net.minecraft.client.render.Tessellator.getInstance();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(net.minecraft.client.render.GameRenderer::getPositionColorProgram);

        var buf = tess.begin(net.minecraft.client.render.VertexFormat.DrawMode.QUADS,
                  net.minecraft.client.render.VertexFormats.POSITION_COLOR);
        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;
        buf.vertex(m, x1, y1, z1).color(r, g, bl, a); buf.vertex(m, x2, y1, z1).color(r, g, bl, a);
        buf.vertex(m, x2, y1, z2).color(r, g, bl, a); buf.vertex(m, x1, y1, z2).color(r, g, bl, a);
        buf.vertex(m, x1, y2, z2).color(r, g, bl, a); buf.vertex(m, x2, y2, z2).color(r, g, bl, a);
        buf.vertex(m, x2, y2, z1).color(r, g, bl, a); buf.vertex(m, x1, y2, z1).color(r, g, bl, a);
        buf.vertex(m, x1, y1, z1).color(r, g, bl, a); buf.vertex(m, x1, y2, z1).color(r, g, bl, a);
        buf.vertex(m, x2, y2, z1).color(r, g, bl, a); buf.vertex(m, x2, y1, z1).color(r, g, bl, a);
        buf.vertex(m, x2, y1, z2).color(r, g, bl, a); buf.vertex(m, x2, y2, z2).color(r, g, bl, a);
        buf.vertex(m, x1, y2, z2).color(r, g, bl, a); buf.vertex(m, x1, y1, z2).color(r, g, bl, a);
        buf.vertex(m, x1, y1, z2).color(r, g, bl, a); buf.vertex(m, x1, y2, z2).color(r, g, bl, a);
        buf.vertex(m, x1, y2, z1).color(r, g, bl, a); buf.vertex(m, x1, y1, z1).color(r, g, bl, a);
        buf.vertex(m, x2, y1, z1).color(r, g, bl, a); buf.vertex(m, x2, y2, z1).color(r, g, bl, a);
        buf.vertex(m, x2, y2, z2).color(r, g, bl, a); buf.vertex(m, x2, y1, z2).color(r, g, bl, a);
        net.minecraft.client.render.BufferRenderer.drawWithGlobalProgram(buf.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}