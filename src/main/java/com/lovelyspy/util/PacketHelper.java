package com.lovelyspy.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.block.data.BlockData;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

public final class PacketHelper {

    private static Class<?> blockPosClass;
    private static Constructor<?> blockPosConstructor;
    
    private static Class<?> blockUpdatePacketClass;
    private static Constructor<?> blockUpdatePacketConstructor;
    
    private static Class<?> openSignEditorPacketClass;
    private static Constructor<?> openSignEditorPacketConstructor;
    
    private static Class<?> blockEntityDataPacketClass;
    private static Constructor<?> blockEntityDataPacketConstructor;
    private static Constructor<?> closeContainerPacketConstructor;
    
    private static Object signType;
    
    static {
        try {
            blockPosClass = Class.forName("net.minecraft.core.BlockPos");
            blockPosConstructor = blockPosClass.getConstructor(int.class, int.class, int.class);
            
            Class<?> blockStateClass = Class.forName("net.minecraft.world.level.block.state.BlockState");
            blockUpdatePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket");
            blockUpdatePacketConstructor = blockUpdatePacketClass.getConstructor(blockPosClass, blockStateClass);
            
            openSignEditorPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket");
            openSignEditorPacketConstructor = openSignEditorPacketClass.getConstructor(blockPosClass, boolean.class);
            
            Class<?> blockEntityTypeClass = Class.forName("net.minecraft.world.level.block.entity.BlockEntityType");
            signType = blockEntityTypeClass.getField("SIGN").get(null);
            
            Class<?> compoundTagClass = Class.forName("net.minecraft.nbt.CompoundTag");
            blockEntityDataPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket");
            blockEntityDataPacketConstructor = blockEntityDataPacketClass.getConstructor(blockPosClass, blockEntityTypeClass, compoundTagClass);

            Class<?> closeContainerPacketClass = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundContainerClosePacket");
            closeContainerPacketConstructor = closeContainerPacketClass.getConstructor(int.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Object createBlockPos(int x, int y, int z) throws Exception {
        return blockPosConstructor.newInstance(x, y, z);
    }

    private static Object getNmsBlockState(BlockData blockData) throws Exception {
        Method getStateMethod = blockData.getClass().getMethod("getState");
        return getStateMethod.invoke(blockData);
    }

    public static boolean sendVirtualSign(Player player, org.bukkit.Location loc, List<String> lines) {
        try {
            Object bp = createBlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            
            // 1. Block change to Oak Sign
            Object signState = getNmsBlockState(Material.OAK_SIGN.createBlockData());
            Object blockUpdate = blockUpdatePacketConstructor.newInstance(bp, signState);
            
            // 2. Block entity data for sign text
            Object nbt = buildSignNbt(loc, lines);
            Object blockEntityData = blockEntityDataPacketConstructor.newInstance(bp, signType, nbt);
            
            // 3. Open editor packet
            Object openSign = openSignEditorPacketConstructor.newInstance(bp, true);
            
            boolean sent = sendPacket(player, blockUpdate)
                    && sendPacket(player, blockEntityData)
                    && sendPacket(player, openSign);
            // Close the editor immediately. The client still returns its sign update,
            // but the probe UI is not left visible to the player.
            if (sent && closeContainerPacketConstructor != null) {
                sendPacket(player, closeContainerPacketConstructor.newInstance(0));
            }
            return sent;
        } catch (Exception e) {
            player.sendMessage("§c[LovelySpy] Failed to send virtual sign: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static void restoreVirtualSign(Player player, org.bukkit.Location loc) {
        try {
            Object bp = createBlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            Object originalState = getNmsBlockState(loc.getBlock().getBlockData());
            Object blockUpdate = blockUpdatePacketConstructor.newInstance(bp, originalState);
            sendPacket(player, blockUpdate);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean sendPacket(Player player, Object packet) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object conn = null;
            for (String name : new String[]{"connection", "networkManager", "playerConnection"}) {
                try {
                    Field f;
                    try { f = handle.getClass().getField(name); }
                    catch (NoSuchFieldException ex) {
                        f = handle.getClass().getDeclaredField(name);
                        f.setAccessible(true);
                    }
                    Object v = f.get(handle);
                    if (v != null) { conn = v; break; }
                } catch (Exception ignored) {}
            }
            if (conn == null) return false;
            
            Method send = null;
            for (Method m : conn.getClass().getMethods()) {
                if (m.getName().equals("send") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isAssignableFrom(packet.getClass())) {
                    send = m;
                    break;
                }
            }
            if (send == null) {
                for (Method m : conn.getClass().getMethods()) {
                    if (m.getName().equals("send") && m.getParameterCount() == 1) {
                        send = m;
                        break;
                    }
                }
            }
            if (send != null) {
                send.invoke(conn, packet);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static Object buildSignNbt(org.bukkit.Location loc, List<String> lines) throws Exception {
        Class<?> compoundTagClass = Class.forName("net.minecraft.nbt.CompoundTag");
        Class<?> listTagClass = Class.forName("net.minecraft.nbt.ListTag");
        Class<?> stringTagClass = Class.forName("net.minecraft.nbt.StringTag");
        Class<?> tagClass = Class.forName("net.minecraft.nbt.Tag");

        Object root = compoundTagClass.getConstructor().newInstance();
        
        Method putString = compoundTagClass.getMethod("putString", String.class, String.class);
        Method put = compoundTagClass.getMethod("put", String.class, tagClass);
        Method stringValueOf = stringTagClass.getMethod("valueOf", String.class);

        putString.invoke(root, "id", "minecraft:sign");
        
        Method putInt = compoundTagClass.getMethod("putInt", String.class, int.class);
        putInt.invoke(root, "x", loc.getBlockX());
        putInt.invoke(root, "y", loc.getBlockY());
        putInt.invoke(root, "z", loc.getBlockZ());
        
        Method putBoolean = compoundTagClass.getMethod("putBoolean", String.class, boolean.class);
        putBoolean.invoke(root, "is_waxed", false);

        // Front text
        Object frontText = compoundTagClass.getConstructor().newInstance();
        Object messagesList = listTagClass.getConstructor().newInstance();

        for (int i = 0; i < 4; i++) {
            String lineContent = "";
            if (i < lines.size()) {
                lineContent = lines.get(i);
            }
            String json;
            if (lineContent.startsWith("key.forward") || lineContent.startsWith("gui.yes")) {
                json = "{\"keybind\":\"" + lineContent + "\"}";
            } else if (!lineContent.isEmpty()) {
                json = "{\"translate\":\"" + lineContent + "\",\"fallback\":\"" + lineContent + "\"}";
            } else {
                json = "{\"text\":\"\"}";
            }
            Object stringTag = stringValueOf.invoke(null, json);
            addToNbtList(messagesList, stringTag);
        }

        put.invoke(frontText, "messages", messagesList);
        putString.invoke(frontText, "color", "black");
        putBoolean.invoke(frontText, "has_glowing_text", false);

        put.invoke(root, "front_text", frontText);
        
        // Back text (empty)
        Object backText = compoundTagClass.getConstructor().newInstance();
        Object backMessagesList = listTagClass.getConstructor().newInstance();
        for (int i = 0; i < 4; i++) {
            Object stringTag = stringValueOf.invoke(null, "{\"text\":\"\"}");
            addToNbtList(backMessagesList, stringTag);
        }
        put.invoke(backText, "messages", backMessagesList);
        putString.invoke(backText, "color", "black");
        putBoolean.invoke(backText, "has_glowing_text", false);
        
        put.invoke(root, "back_text", backText);

        return root;
    }

    @SuppressWarnings("unchecked")
    private static void addToNbtList(Object listTag, Object tag) throws Exception {
        if (listTag instanceof List list) {
            list.add(tag);
            return;
        }
        if (listTag instanceof Collection collection) {
            collection.add(tag);
            return;
        }

        for (Method method : listTag.getClass().getMethods()) {
            if (!method.getName().equals("add") || method.getParameterCount() != 1) {
                continue;
            }

            Class<?> parameterType = method.getParameterTypes()[0];
            if (!parameterType.isAssignableFrom(tag.getClass()) && !parameterType.equals(Object.class)) {
                continue;
            }

            try {
                method.invoke(listTag, tag);
                return;
            } catch (IllegalArgumentException ignored) {
                // Try the next bridge/overload if Paper's reflection remapper rejected this signature.
            }
        }

        throw new NoSuchMethodException(listTag.getClass().getName() + ".add(" + tag.getClass().getName() + ")");
    }

    public static io.netty.channel.Channel getChannel(Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Field connectionField = null;
            for (Field f : handle.getClass().getFields()) {
                if (f.getType().getSimpleName().equals("ServerGamePacketListenerImpl")) {
                    connectionField = f;
                    break;
                }
            }
            if (connectionField == null) {
                for (Field f : handle.getClass().getDeclaredFields()) {
                    if (f.getType().getSimpleName().equals("ServerGamePacketListenerImpl")) {
                        connectionField = f;
                        connectionField.setAccessible(true);
                        break;
                    }
                }
            }
            if (connectionField == null) return null;
            Object listener = connectionField.get(handle);

            Field networkManagerField = null;
            Class<?> current = listener.getClass();
            while (current != null && current != Object.class) {
                for (Field f : current.getDeclaredFields()) {
                    if (f.getType().getSimpleName().equals("Connection")) {
                        networkManagerField = f;
                        networkManagerField.setAccessible(true);
                        break;
                    }
                }
                if (networkManagerField != null) break;
                current = current.getSuperclass();
            }
            if (networkManagerField == null) return null;
            Object networkManager = networkManagerField.get(listener);

            Field channelField = null;
            for (Field f : networkManager.getClass().getDeclaredFields()) {
                if (f.getType().equals(io.netty.channel.Channel.class)) {
                    channelField = f;
                    channelField.setAccessible(true);
                    break;
                }
            }
            if (channelField == null) return null;
            return (io.netty.channel.Channel) channelField.get(networkManager);
        } catch (Exception e) {
            return null;
        }
    }
}
