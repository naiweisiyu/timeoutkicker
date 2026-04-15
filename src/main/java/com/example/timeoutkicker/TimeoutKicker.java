package com.example.timeoutkicker;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TimeoutKicker extends JavaPlugin {

    private static TimeoutKicker instance;
    private static final long TIMEOUT_MS = 5000;
    private final Map<UUID, Long> lastPacketTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> anomalyCount = new ConcurrentHashMap<>();
    private static final int MAX_ANOMALY_COUNT = 5;

    @Override
    public void onEnable() {
        instance = this;

        Set<PacketType> clientPacketTypes = getAllPacketTypes(PacketType.Play.Client.class);
        Set<PacketType> serverPacketTypes = getAllPacketTypes(PacketType.Play.Server.class);

        com.comphenix.protocol.ProtocolLibrary.getProtocolManager()
                .addPacketListener(new PacketAdapter(this, clientPacketTypes) {
                    @Override
                    public void onPacketReceiving(PacketEvent event) {
                        if (event.getPlayer() == null) {
                            return;
                        }

                        UUID uuid = event.getPlayer().getUniqueId();
                        lastPacketTime.put(uuid, System.currentTimeMillis());

                        if (!isPacketValid(event)) {
                            handleAnomaly(event.getPlayer(), event.getPacketType().toString());
                        }
                    }
                });

        com.comphenix.protocol.ProtocolLibrary.getProtocolManager()
                .addPacketListener(new PacketAdapter(this, serverPacketTypes) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        if (event.getPlayer() == null) {
                            return;
                        }

                        isPacketValid(event);
                    }
                });

        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                long cutoffTime = currentTime - TIMEOUT_MS;

                for (Player player : getServer().getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    Long lastTime = lastPacketTime.get(uuid);

                    if (lastTime == null) {
                        continue;
                    }

                    if (lastTime < cutoffTime) {
                        player.kickPlayer("§c连接超时: 5秒内无响应");
                        lastPacketTime.remove(uuid);
                    }
                }
            }
        }.runTaskTimer(this, 1L, 1L);

        getLogger().info("TimeoutKicker 已启用!");
    }

    @Override
    public void onDisable() {
        lastPacketTime.clear();
        anomalyCount.clear();
    }

    private boolean isPacketValid(PacketEvent event) {
        try {
            event.getPacket().getModifier().read(0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Set<PacketType> getAllPacketTypes(Class<?> packetClass) {
        Set<PacketType> types = new HashSet<>();
        for (Field field : packetClass.getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(null);
                if (value instanceof PacketType) {
                    types.add((PacketType) value);
                }
            } catch (Exception ignored) {
            }
        }
        return types;
    }

    private void handleAnomaly(Player player, String packetType) {
        UUID uuid = player.getUniqueId();
        int count = anomalyCount.getOrDefault(uuid, 0) + 1;
        anomalyCount.put(uuid, count);

        getLogger().warning("玩家 " + player.getName() + " 发送了异常数据包 [" + packetType + "] (累计: " + count + "/" + MAX_ANOMALY_COUNT + ")");

        if (count >= MAX_ANOMALY_COUNT) {
            player.kickPlayer("异常数据包");
            anomalyCount.remove(uuid);
        }
    }
}
