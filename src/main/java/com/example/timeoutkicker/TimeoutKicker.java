package com.example.timeoutkicker;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TimeoutKicker extends JavaPlugin {

    private static TimeoutKicker instance;
    private static final long TIMEOUT_MS = 5000;
    private final Map<UUID, Long> lastPacketTime = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;

        Set<PacketType> clientPacketTypes = new HashSet<>(Arrays.asList(
                PacketType.Play.Client.KEEP_ALIVE,
                PacketType.Play.Client.CHAT,
                PacketType.Play.Client.ENTITY_ACTION,
                PacketType.Play.Client.BLOCK_DIG,
                PacketType.Play.Client.BLOCK_PLACE,
                PacketType.Play.Client.USE_ITEM,
                PacketType.Play.Client.ARM_ANIMATION
        ));

        com.comphenix.protocol.ProtocolLibrary.getProtocolManager()
                .addPacketListener(new PacketAdapter(this, clientPacketTypes) {
                    @Override
                    public void onPacketReceiving(PacketEvent event) {
                        if (event.getPlayer() != null) {
                            UUID uuid = event.getPlayer().getUniqueId();
                            lastPacketTime.put(uuid, System.currentTimeMillis());
                        }
                    }

                    @Override
                    public void onPacketSending(PacketEvent event) {
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
    }
}