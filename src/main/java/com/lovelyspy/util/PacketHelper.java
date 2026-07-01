package com.lovelyspy.util;

import io.papermc.paper.math.Position;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.block.data.BlockData;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class PacketHelper {

    private static Constructor<?> closeContainerPacketConstructor;

    static {
        try {
            Class<?> closeContainerPacketClass = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundContainerClosePacket");
            closeContainerPacketConstructor = closeContainerPacketClass.getConstructor(int.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean sendVirtualSign(Player player, org.bukkit.Location loc, List<String> lines) {
        // ExploitFixer's default max_size_sign is 96. Refuse an oversized custom
        // signature before sending any packets so LovelySpy can never trigger it.
        for (String line : lines) {
            if (!ProbeComponentSerializer.fitsDefaultSignLimiter(line)) {
                return false;
            }
        }
        try {
            BlockData signData = Material.OAK_SIGN.createBlockData();
            BlockState blockState = signData.createBlockState();
            if (!(blockState instanceof Sign sign)) {
                return false;
            }

            for (int index = 0; index < 4; index++) {
                String value = index < lines.size() ? lines.get(index) : "";
                sign.getSide(Side.FRONT).line(index, toProbeComponent(value));
            }
            sign.setWaxed(false);
            sign.setAllowedEditorUniqueId(player.getUniqueId());

            // Since 1.21.5, text components in block-entity data use structured
            // NBT rather than JSON stored inside StringTags. Let Paper encode the
            // virtual tile state so the client receives actual translatable
            // components instead of visible {"translate":"..."} literals.
            player.sendBlockChange(loc, signData);
            player.sendBlockUpdate(loc, sign);
            player.openVirtualSign(Position.block(loc), Side.FRONT);
            return true;
        } catch (Exception e) {
            player.sendMessage("§c[LovelySpy] Failed to send virtual sign: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static Component toProbeComponent(String value) {
        if (value == null || value.isEmpty()) {
            return Component.empty();
        }
        if (value.equals("key.forward") || value.equals("gui.yes")) {
            return Component.keybind(value);
        }
        return Component.translatable(value);
    }

    public static void restoreVirtualSign(Player player, org.bukkit.Location loc) {
        try {
            player.sendBlockChange(loc, loc.getBlock().getBlockData());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void closeScreen(Player player) {
        if (closeContainerPacketConstructor == null) return;
        try {
            sendPacket(player, closeContainerPacketConstructor.newInstance(0));
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
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
