package dev.promptcraft.structure;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Сканирует выделенную область мира и преобразует её в компактный список
 * операций (fill / place), понятный нейросети, с локальными координатами относительно min.
 */
public final class StructureScanner {
    private static final Gson GSON = new Gson();

    private StructureScanner() {}

    public static String scanToCompactJson(ServerWorld world, BlockPos min, BlockPos max, int maxOps) {
        int minX = Math.min(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxX = Math.max(min.getX(), max.getX());
        int maxY = Math.max(min.getY(), max.getY());
        int maxZ = Math.max(min.getZ(), max.getZ());

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;

        JsonArray operations = new JsonArray();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int count = 0;

        for (int y = 0; y < height && count < maxOps; y++) {
            for (int z = 0; z < depth && count < maxOps; z++) {
                int x = 0;
                while (x < width && count < maxOps) {
                    mutable.set(minX + x, minY + y, minZ + z);
                    BlockState state = world.getBlockState(mutable);
                    if (state.isAir()) {
                        x++;
                        continue;
                    }

                    int startX = x;
                    while (x + 1 < width) {
                        mutable.set(minX + x + 1, minY + y, minZ + z);
                        if (world.getBlockState(mutable).equals(state)) {
                            x++;
                        } else {
                            break;
                        }
                    }
                    int endX = x;
                    String encoded = StructureBlockCodec.encode(state);

                    JsonObject op = new JsonObject();
                    if (startX == endX) {
                        op.addProperty("type", "place");
                        JsonArray pos = new JsonArray();
                        pos.add(startX); pos.add(y); pos.add(z);
                        op.add("pos", pos);
                    } else {
                        op.addProperty("type", "fill");
                        JsonArray from = new JsonArray();
                        from.add(startX); from.add(y); from.add(z);
                        JsonArray to = new JsonArray();
                        to.add(endX); to.add(y); to.add(z);
                        op.add("from", from);
                        op.add("to", to);
                    }
                    op.addProperty("block", encoded);
                    operations.add(op);
                    count++;
                    x++;
                }
            }
        }

        return GSON.toJson(operations);
    }
}
