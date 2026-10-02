package dev.promptcraft.structure;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.util.Optional;

public final class StructureBlockCodec {
    private StructureBlockCodec() {
    }

    public static Optional<BlockState> parse(String encoded) {
        if (encoded == null || encoded.isBlank()) return Optional.empty();

        String[] parts = encoded.split("\\[", 2);
        String blockId = parts[0];

        Identifier id = Identifier.tryParse(blockId);
        if (id == null) return Optional.empty();

        Optional<Block> blockOpt = Registries.BLOCK.getOptionalValue(id);
        if (blockOpt.isEmpty()) return Optional.empty();

        BlockState state = blockOpt.get().getDefaultState();

        if (parts.length > 1) {
            String props = parts[1].replace("]", "");
            if (!props.isBlank()) {
                for (String prop : props.split(",")) {
                    String[] kv = prop.split("=");
                    if (kv.length == 2) {
                        state = applyProperty(state, kv[0], kv[1]);
                    }
                }
            }
        }

        return Optional.of(state);
    }

    public static String encode(BlockState state) {
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        StringBuilder sb = new StringBuilder(id.toString());

        var props = state.getProperties();
        if (!props.isEmpty()) {
            sb.append('[');
            boolean first = true;
            for (Property<?> prop : props) {
                if (!first) sb.append(',');
                first = false;
                sb.append(prop.getName()).append('=').append(nameOf(state, prop));
            }
            sb.append(']');
        }

        return sb.toString();
    }

    private static <T extends Comparable<T>> String nameOf(BlockState state, Property<T> prop) {
        return prop.name(state.get(prop));
    }

    private static BlockState applyProperty(BlockState state, String key, String value) {
        for (Property<?> prop : state.getProperties()) {
            if (prop.getName().equals(key)) {
                return parseAndSet(state, prop, value);
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState parseAndSet(BlockState state, Property<T> prop, String value) {
        return prop.parse(value).map(t -> state.with(prop, t)).orElse(state);
    }
}