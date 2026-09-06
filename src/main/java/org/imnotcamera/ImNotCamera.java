package org.imnotcamera;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.ServerPacket;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ImNotCamera {
    private static Map<UUID, Camera> cameras = new ConcurrentHashMap<>();
    public static Map<UUID, Camera> getCameras() {
        return new ConcurrentHashMap<>(cameras);
    }

    @Nullable
    public static Camera getCameraFromPlayer(UUID uuid) {
        return cameras.values().stream().filter(camera -> camera.getPlayerUUIDList().containsKey(uuid)).findFirst().orElse(null);
    }

    @Nullable
    public static Camera getCameraFromPlayer(Player player) {
        return getCameraFromPlayer(player.getUuid());
    }

    public static Camera buildNewCamera(Instance instance, Pos startPos) {
        Camera camera = new Camera(instance,startPos);
        cameras.put(UUID.randomUUID(),camera);

        return camera;
    }

    public static void terminateCamera(Camera camera, boolean teleportBack) {
        camera.dead = true;

        cameras.values().remove(camera);

        //now quit all players
        camera.getPlayerUUIDList().forEach((uuid, cameraData) -> {
            camera.removePlayer(uuid, teleportBack);
        });

        if (camera.currentPathTask!=null) {
            camera.currentPathTask.cancel();
        }
        camera.cameraEntity.remove();

    }

    public static void terminateCamera(Camera camera) {
        terminateCamera(camera, true);
    }

    public static void init() {
        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();

        globalEventHandler.addListener(PlayerDisconnectEvent.class, event-> {
            var player = event.getPlayer();
            Camera camera = getCameraFromPlayer(player);
            if (camera!=null) {
                camera.getPlayerUUIDList().remove(player.getUuid());
            }
        });
    }
}
