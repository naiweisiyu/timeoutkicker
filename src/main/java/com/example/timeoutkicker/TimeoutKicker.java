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
    private final Map<UUID, Long> lastPacketTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> anomalyCount = new ConcurrentHashMap<>();

    private boolean timeoutEnabled;
    private int timeoutSeconds;
    private String timeoutKickMessage;
    private boolean packetAnomalyEnabled;
    private int maxAnomalyCount;
    private String packetKickMessage;
    private boolean verboseLogging;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        loadConfig();

        if (timeoutEnabled) {
            Set<PacketType> clientPacketTypes = getAllPacketTypes(PacketType.Play.Client.class);

            com.comphenix.protocol.ProtocolLibrary.getProtocolManager()
                    .addPacketListener(new PacketAdapter(this, clientPacketTypes) {
                        @Override
                        public void onPacketReceiving(PacketEvent event) {
                            if (event.getPlayer() == null) {
                                return;
                            }

                            UUID uuid = event.getPlayer().getUniqueId();
                            lastPacketTime.put(uuid, System.currentTimeMillis());

                            if (packetAnomalyEnabled && !isPacketValid(event)) {
                                handleAnomaly(event.getPlayer(), event.getPacketType().toString());
                            }
                        }
                    });

            new BukkitRunnable() {
                @Override
                public void run() {
                    long currentTime = System.currentTimeMillis();
                    long cutoffTime = currentTime - (timeoutSeconds * 1000L);

                    for (Player player : getServer().getOnlinePlayers()) {
                        UUID uuid = player.getUniqueId();
                        Long lastTime = lastPacketTime.get(uuid);

                        if (lastTime == null) {
                            continue;
                        }

                        if (lastTime < cutoffTime) {
                            player.kickPlayer(timeoutKickMessage);
                            lastPacketTime.remove(uuid);
                        }
                    }
                }
            }.runTaskTimer(this, 1L, 1L);
        } else if (packetAnomalyEnabled) {
            Set<PacketType> clientPacketTypes = getAllPacketTypes(PacketType.Play.Client.class);

            com.comphenix.protocol.ProtocolLibrary.getProtocolManager()
                    .addPacketListener(new PacketAdapter(this, clientPacketTypes) {
                        @Override
                        public void onPacketReceiving(PacketEvent event) {
                            if (event.getPlayer() == null) {
                                return;
                            }

                            if (!isPacketValid(event)) {
                                handleAnomaly(event.getPlayer(), event.getPacketType().toString());
                            }
                        }
                    });
        }

        if (packetAnomalyEnabled) {
            Set<PacketType> serverPacketTypes = getAllPacketTypes(PacketType.Play.Server.class);

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
        }

        getLogger().info("TimeoutKicker 已启用!");
    }

    @Override
    public void onDisable() {
        lastPacketTime.clear();
        anomalyCount.clear();
    }

    private void loadConfig() {
        timeoutEnabled = getConfig().getBoolean("timeout.enabled", true);
        timeoutSeconds = getConfig().getInt("timeout.timeout-seconds", 5);
        timeoutKickMessage = getConfig().getString("timeout.kick-message", "§c连接超时");

        packetAnomalyEnabled = getConfig().getBoolean("packet-anomaly.enabled", true);
        maxAnomalyCount = getConfig().getInt("packet-anomaly.max-anomaly-count", 5);
        packetKickMessage = getConfig().getString("packet-anomaly.kick-message", "异常数据包");

        verboseLogging = getConfig().getBoolean("logging.verbose", true);
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

        if (verboseLogging) {
            getLogger().warning("玩家 " + player.getName() + " 发送了异常数据包 [" + packetType + "] (累计: " + count + "/" + maxAnomalyCount + ")");
        }

        if (count >= maxAnomalyCount) {
            player.kickPlayer(packetKickMessage);
            anomalyCount.remove(uuid);
        }
    }
}
