package dev.promptcraft.structure;

import dev.promptcraft.config.PromptCraftConfigManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

/**
 * Процедурный генератор текстур стен и поверхностей.
 * Накладывает детерминированный 3D шум и градиент высоты (мох/износ у основания),
 * превращая монотонные постройки ИИ в детализированные архитектурные объекты.
 */
public final class ProceduralTexturizer {

    private ProceduralTexturizer() {}

    public static BlockState apply(BlockState original, BlockPos pos, int localY, int totalHeight) {
        if (!PromptCraftConfigManager.get().proceduralTexturing) {
            return original;
        }

        // Текстурируем только полнотелые строительные блоки, не затрагивая ступени, полублоки, двери и т.д.
        if (original.isOf(Blocks.STONE_BRICKS)) {
            return textureStoneBricks(original, pos, localY);
        } else if (original.isOf(Blocks.DEEPSLATE_BRICKS)) {
            return textureDeepslateBricks(original, pos);
        } else if (original.isOf(Blocks.DEEPSLATE_TILES)) {
            return textureDeepslateTiles(original, pos);
        } else if (original.isOf(Blocks.COBBLESTONE)) {
            return textureCobblestone(original, pos, localY);
        } else if (original.isOf(Blocks.BRICKS)) {
            return textureBricks(original, pos);
        } else if (original.isOf(Blocks.SANDSTONE)) {
            return textureSandstone(original, pos);
        } else if (original.isOf(Blocks.RED_SANDSTONE)) {
            return textureRedSandstone(original, pos);
        } else if (original.isOf(Blocks.OAK_PLANKS)) {
            return texturePlanks(original, pos, Blocks.STRIPPED_OAK_WOOD.getDefaultState());
        } else if (original.isOf(Blocks.SPRUCE_PLANKS)) {
            return texturePlanks(original, pos, Blocks.STRIPPED_SPRUCE_WOOD.getDefaultState());
        } else if (original.isOf(Blocks.DARK_OAK_PLANKS)) {
            return texturePlanks(original, pos, Blocks.STRIPPED_DARK_OAK_WOOD.getDefaultState());
        }

        return original;
    }

    private static BlockState textureStoneBricks(BlockState orig, BlockPos pos, int localY) {
        double noise = sampleNoise(pos.getX(), pos.getY(), pos.getZ(), 0.18);
        double detailNoise = sampleNoise(pos.getX() + 100, pos.getY(), pos.getZ() + 100, 0.4);

        // Градиент влажности у основания (нижние 1-2 блока)
        if (localY <= 1) {
            if (noise > 0.1) return Blocks.MOSSY_STONE_BRICKS.getDefaultState();
            if (noise < -0.2) return Blocks.COBBLESTONE.getDefaultState();
            if (detailNoise > 0.3) return Blocks.MOSSY_COBBLESTONE.getDefaultState();
            return orig;
        }

        // Верхняя и средняя часть стены: состаривание и кладка
        if (noise > 0.45) {
            return Blocks.CRACKED_STONE_BRICKS.getDefaultState();
        } else if (noise < -0.40) {
            return Blocks.ANDESITE.getDefaultState();
        } else if (noise < -0.25 && detailNoise > 0.2) {
            return Blocks.COBBLESTONE.getDefaultState();
        }

        return orig;
    }

    private static BlockState textureDeepslateBricks(BlockState orig, BlockPos pos) {
        double noise = sampleNoise(pos.getX(), pos.getY(), pos.getZ(), 0.2);
        if (noise > 0.42) {
            return Blocks.CRACKED_DEEPSLATE_BRICKS.getDefaultState();
        } else if (noise < -0.40) {
            return Blocks.COBBLED_DEEPSLATE.getDefaultState();
        } else if (noise < -0.20) {
            return Blocks.POLISHED_DEEPSLATE.getDefaultState();
        }
        return orig;
    }

    private static BlockState textureDeepslateTiles(BlockState orig, BlockPos pos) {
        double noise = sampleNoise(pos.getX(), pos.getY(), pos.getZ(), 0.2);
        if (noise > 0.42) {
            return Blocks.CRACKED_DEEPSLATE_TILES.getDefaultState();
        } else if (noise < -0.38) {
            return Blocks.COBBLED_DEEPSLATE.getDefaultState();
        }
        return orig;
    }

    private static BlockState textureCobblestone(BlockState orig, BlockPos pos, int localY) {
        double noise = sampleNoise(pos.getX(), pos.getY(), pos.getZ(), 0.18);
        if (localY <= 1 && noise > -0.1) {
            return Blocks.MOSSY_COBBLESTONE.getDefaultState();
        }
        if (noise > 0.45) return Blocks.ANDESITE.getDefaultState();
        if (noise < -0.45) return Blocks.GRAVEL.getDefaultState();
        return orig;
    }

    private static BlockState textureBricks(BlockState orig, BlockPos pos) {
        double noise = sampleNoise(pos.getX(), pos.getY(), pos.getZ(), 0.2);
        if (noise > 0.45) return Blocks.MUD_BRICKS.getDefaultState();
        if (noise < -0.40) return Blocks.GRANITE.getDefaultState();
        if (noise < -0.22) return Blocks.TERRACOTTA.getDefaultState();
        return orig;
    }

    private static BlockState textureSandstone(BlockState orig, BlockPos pos) {
        double noise = sampleNoise(pos.getX(), pos.getY(), pos.getZ(), 0.22);
        if (noise > 0.45) return Blocks.SMOOTH_SANDSTONE.getDefaultState();
        if (noise < -0.42) return Blocks.CUT_SANDSTONE.getDefaultState();
        return orig;
    }

    private static BlockState textureRedSandstone(BlockState orig, BlockPos pos) {
        double noise = sampleNoise(pos.getX(), pos.getY(), pos.getZ(), 0.22);
        if (noise > 0.45) return Blocks.SMOOTH_RED_SANDSTONE.getDefaultState();
        if (noise < -0.42) return Blocks.CUT_RED_SANDSTONE.getDefaultState();
        return orig;
    }

    private static BlockState texturePlanks(BlockState orig, BlockPos pos, BlockState stripped) {
        double noise = sampleNoise(pos.getX(), pos.getY(), pos.getZ(), 0.35);
        if (noise > 0.65) return stripped;
        return orig;
    }

    /**
     * Быстрый и плавный 3D шум на основе синусно-хэшевой трилинейной интерполяции.
     * Возвращает значение от -1.0 до 1.0.
     */
    private static double sampleNoise(int x, int y, int z, double freq) {
        double fx = x * freq;
        double fy = y * freq;
        double fz = z * freq;

        int ix = (int) Math.floor(fx);
        int iy = (int) Math.floor(fy);
        int iz = (int) Math.floor(fz);

        double dx = fx - ix;
        double dy = fy - iy;
        double dz = fz - iz;

        // Плавная кривая Эрмита (s-curve: 3t^2 - 2t^3)
        double u = dx * dx * (3.0 - 2.0 * dx);
        double v = dy * dy * (3.0 - 2.0 * dy);
        double w = dz * dz * (3.0 - 2.0 * dz);

        double c000 = hash(ix, iy, iz);
        double c100 = hash(ix + 1, iy, iz);
        double c010 = hash(ix, iy + 1, iz);
        double c110 = hash(ix + 1, iy + 1, iz);
        double c001 = hash(ix, iy, iz + 1);
        double c101 = hash(ix + 1, iy, iz + 1);
        double c011 = hash(ix, iy + 1, iz + 1);
        double c111 = hash(ix + 1, iy + 1, iz + 1);

        double x00 = c000 + u * (c100 - c000);
        double x10 = c010 + u * (c110 - c010);
        double x01 = c001 + u * (c101 - c001);
        double x11 = c011 + u * (c111 - c011);

        double y0 = x00 + v * (x10 - x00);
        double y1 = x01 + v * (x11 - x01);

        return y0 + w * (y1 - y0);
    }

    private static double hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * (int) 3266489917L;
        h = (h ^ (h >> 13)) * 1274126177;
        return (double) ((h ^ (h >> 16)) & 0xFFFF) / 32767.5 - 1.0;
    }
}
