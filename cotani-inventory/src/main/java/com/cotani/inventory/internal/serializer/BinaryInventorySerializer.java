package com.cotani.inventory.internal.serializer;

import com.cotani.api.InternalApi;
import com.cotani.inventory.api.InventorySerializer;
import com.cotani.inventory.api.InventorySnapshot;
import com.cotani.inventory.api.PotionEffectSnapshot;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * Loss-less binary serializer for {@link InventorySnapshot} utilizing native Paper DataComponents.
 */
@InternalApi
@NullMarked
public final class BinaryInventorySerializer implements InventorySerializer {

    public static final BinaryInventorySerializer INSTANCE = new BinaryInventorySerializer();

    private static final int MAGIC_HEADER = 0x434F5449; // "COTI"
    private static final int PROTOCOL_VERSION = 1;
    private static final int MAX_ITEMS_PER_SECTION = 256;
    private static final int MAX_POTION_EFFECTS = 256;
    private static final int MAX_SERIALIZED_ITEM_BYTES = 1_048_576;

    private BinaryInventorySerializer() {}

    @Override
    public byte[] serialize(InventorySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");

        try (var byteOut = new ByteArrayOutputStream();
                var out = new DataOutputStream(byteOut)) {

            out.writeInt(MAGIC_HEADER);
            out.writeInt(PROTOCOL_VERSION);

            out.writeLong(snapshot.playerId().getMostSignificantBits());
            out.writeLong(snapshot.playerId().getLeastSignificantBits());
            out.writeInt(snapshot.version());
            out.writeLong(snapshot.createdAt());

            writeItemArray(out, snapshot.mainContents());
            writeItemArray(out, snapshot.armorContents());
            writeSingleItem(out, snapshot.offHand());
            writeItemArray(out, snapshot.enderChestContents());

            out.writeInt(snapshot.totalExperience());
            out.writeInt(snapshot.level());
            out.writeFloat(snapshot.exp());
            out.writeDouble(snapshot.health());
            out.writeDouble(snapshot.maxHealth());
            out.writeInt(snapshot.foodLevel());
            out.writeFloat(snapshot.saturation());

            out.writeInt(snapshot.potionEffects().size());
            for (var effect : snapshot.potionEffects()) {
                out.writeUTF(effect.type().getKey().asString());
                out.writeInt(effect.durationTicks());
                out.writeInt(effect.amplifier());
                out.writeBoolean(effect.ambient());
                out.writeBoolean(effect.particles());
                out.writeBoolean(effect.icon());
            }

            out.writeUTF(snapshot.gameMode().name());
            out.writeBoolean(snapshot.allowFlight());
            out.writeBoolean(snapshot.flying());

            out.flush();
            return byteOut.toByteArray();
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to serialize InventorySnapshot", error);
        }
    }

    @Override
    public InventorySnapshot deserialize(byte[] data) {
        Objects.requireNonNull(data, "data");

        try (var byteIn = new ByteArrayInputStream(data);
                var in = new DataInputStream(byteIn)) {

            int magic = in.readInt();
            if (magic != MAGIC_HEADER) {
                throw new IllegalArgumentException("Invalid inventory snapshot binary header: " + magic);
            }

            int protocol = in.readInt();
            if (protocol != PROTOCOL_VERSION) {
                throw new IllegalArgumentException("Unsupported inventory snapshot protocol version: " + protocol);
            }

            long mostSig = in.readLong();
            long leastSig = in.readLong();
            var playerId = new UUID(mostSig, leastSig);

            int version = in.readInt();
            long createdAt = in.readLong();

            var mainContents = readItemArray(in);
            var armorContents = readItemArray(in);
            var offHand = readSingleItem(in);
            var enderChest = readItemArray(in);

            int totalExperience = in.readInt();
            int level = in.readInt();
            float exp = in.readFloat();
            double health = in.readDouble();
            double maxHealth = in.readDouble();
            int foodLevel = in.readInt();
            float saturation = in.readFloat();

            int effectCount = readCount(in, MAX_POTION_EFFECTS, "potion effects");
            var effects = new ArrayList<PotionEffectSnapshot>(effectCount);
            for (int i = 0; i < effectCount; i++) {
                String keyStr = in.readUTF();
                int duration = in.readInt();
                int amplifier = in.readInt();
                boolean ambient = in.readBoolean();
                boolean particles = in.readBoolean();
                boolean icon = in.readBoolean();

                var key = NamespacedKey.fromString(keyStr);
                var effectType = key != null ? Registry.POTION_EFFECT_TYPE.get(key) : null;
                if (effectType == null) {
                    throw new IllegalArgumentException("Unknown potion effect type: " + keyStr);
                }
                effects.add(new PotionEffectSnapshot(effectType, duration, amplifier, ambient, particles, icon));
            }

            String gameModeStr = in.readUTF();
            GameMode gameMode;
            try {
                gameMode = GameMode.valueOf(gameModeStr);
            } catch (IllegalArgumentException _) {
                throw new IllegalArgumentException("Unknown game mode: " + gameModeStr);
            }

            boolean allowFlight = in.readBoolean();
            boolean flying = in.readBoolean();

            if (in.available() != 0) {
                throw new IllegalArgumentException("Unexpected trailing data in inventory snapshot");
            }

            return new InventorySnapshot(
                    playerId,
                    version,
                    createdAt,
                    mainContents,
                    armorContents,
                    offHand,
                    enderChest,
                    totalExperience,
                    level,
                    exp,
                    health,
                    maxHealth,
                    foodLevel,
                    saturation,
                    effects,
                    gameMode,
                    allowFlight,
                    flying);
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to deserialize InventorySnapshot", error);
        }
    }

    private static void writeItemArray(DataOutputStream out, List<ItemStack> items) throws IOException {
        out.writeInt(items.size());
        for (var item : items) {
            writeSingleItem(out, item);
        }
    }

    private static void writeSingleItem(DataOutputStream out, ItemStack item) throws IOException {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            out.writeInt(0);
            return;
        }

        byte[] serialized = item.serializeAsBytes();
        out.writeInt(serialized.length);
        out.write(serialized);
    }

    private static List<ItemStack> readItemArray(DataInputStream in) throws IOException {
        int size = readCount(in, MAX_ITEMS_PER_SECTION, "items");
        var items = new ArrayList<ItemStack>(size);
        for (int i = 0; i < size; i++) {
            items.add(readSingleItem(in));
        }
        return items;
    }

    private static ItemStack readSingleItem(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length == 0) {
            return ItemStack.empty();
        }
        if (length < 0 || length > MAX_SERIALIZED_ITEM_BYTES) {
            throw new IllegalArgumentException("Invalid serialized item length: " + length);
        }

        byte[] itemBytes = new byte[length];
        in.readFully(itemBytes);
        try {
            return ItemStack.deserializeBytes(itemBytes);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Failed to deserialize item stack", error);
        }
    }

    private static int readCount(DataInputStream in, int maximum, String field) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid " + field + " count: " + count);
        }
        return count;
    }
}
