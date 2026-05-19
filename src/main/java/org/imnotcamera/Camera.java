package org.imnotcamera;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.*;
import net.minestom.server.entity.metadata.display.BlockDisplayMeta;
import net.minestom.server.event.entity.EntityTeleportEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.component.ConsumeEffect;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.BundlePacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.timer.Scheduler;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.utils.EaseFunction;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Camera {
    private final static int DISPLAY_ENTITY_POS_ROT_INDEX = 10;

    public static class CameraData {
        private final Pos lastPos;
        private final GameMode lastGamemode;
        private final Instance lastInstance;
        public Pos getLastPos() {
            return lastPos;
        }
        public GameMode getLastGamemode() {
            return lastGamemode;
        }
        public Instance getLastInstance() {
            return lastInstance;
        }

        public CameraData(Pos lastPos, GameMode lastGamemode, Instance lastInstance) {
            this.lastPos = lastPos;
            this.lastGamemode = lastGamemode;
            this.lastInstance = lastInstance;
        }
    }

    Entity cameraEntity;
    boolean dead = false;
    private Pos lastGlobalPos;
    private Map<UUID, CameraData> playerUUIDList = new ConcurrentHashMap<>();
    public Pos getLastGlobalPos() {
        return lastGlobalPos;
    }
    public Map<UUID, CameraData> getPlayerUUIDList() {
        return playerUUIDList;
    }

    public boolean isDead() {
        return dead;
    }

    public void addPlayer(Player player) {
        if (isDead()) {return;}

        CameraData cameraData = new CameraData(player.getPosition(),player.getGameMode(),player.getInstance());

        //now set
        playerUUIDList.put(player.getUuid(), cameraData);
        player.setGameMode(GameMode.SPECTATOR);
        player.spectate(cameraEntity);
    }

    public void addPlayer(UUID uuid) {
        Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid);
        if (player == null) {return;}

        addPlayer(player);
    }

    public void removePlayer(UUID uuid) {
        CameraData lastData = playerUUIDList.getOrDefault(uuid,null);
        Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid);

        playerUUIDList.remove(uuid);

        if (lastData !=null && player!= null) {
            lastData.getLastInstance().loadChunk(lastData.getLastPos()).thenRun(() -> {
                if (!player.isOnline()) {return;}

                System.out.println(lastData.getLastGamemode());
                player.setGameMode(lastData.getLastGamemode());
                player.stopSpectating();
                if (player.getInstance().equals(lastData.getLastInstance())) {
                    player.teleport(lastData.getLastPos());
                } else {
                    player.setInstance(lastData.getLastInstance(),lastData.getLastPos());
                }
            });
        }
    }

    public void removePlayer(Player player) {
        removePlayer(player.getUuid());
    }

    //now actual camera stuff
    private CompletableFuture<Void> interpolateCore(Pos newPos, int interpolationDuration, int delayTicks, EasingDirection direction, EasingStyle style) {
        if (isDead()) {return CompletableFuture.runAsync(() -> {});} // just an empty shell.

        BlockDisplayMeta meta = (BlockDisplayMeta) cameraEntity.getEntityMeta();
        this.lastGlobalPos = newPos;

        if (style.equals(EasingStyle.LINEAR)) {
            meta.setPosRotInterpolationDuration(interpolationDuration);
            return CompletableFuture.runAsync(() -> {
                if (cameraEntity.getInstance() != null) {
                    //go, reason I check bc it might be too fast
                    cameraEntity.teleport(newPos);
                }

                Phaser doneYet = new Phaser(1);
                doneYet.register();

                MinecraftServer.getSchedulerManager().buildTask(doneYet::arriveAndDeregister).delay(TaskSchedule.tick(delayTicks)).schedule();

                doneYet.awaitAdvance(doneYet.arrive());

                return;
            });
        } else {
            meta.setPosRotInterpolationDuration(1);
            Pos startPos = cameraEntity.getPosition();
            return CompletableFuture.runAsync(() -> {
                Phaser doneYet = new Phaser(1);
                doneYet.register();

                var currentTick = new AtomicInteger(0);
                var task = MinecraftServer.getSchedulerManager().buildTask(() -> {
                    if (isDead()) {
                        doneYet.arriveAndDeregister();
                        return;
                    }
                    if (currentTick.get() >= interpolationDuration) {
                        doneYet.arriveAndDeregister();
                        return;
                    }

//                    System.out.println(currentTick.get());

                    if (currentTick.get() % 1 == 0) {
                        int tickGonnaBe = Math.min(currentTick.get()+2,interpolationDuration);
                        Pos gonnaBe = TweenUtils.getTweenPos(startPos, newPos, tickGonnaBe, interpolationDuration, direction, style);
//                        System.out.println(tickGonnaBe);
//                        System.out.println("Gonnabe Pos : "+gonnaBe.x() + " "+gonnaBe.y()+ " "+ gonnaBe.z());
//                        System.out.println("Start Pos : "+startPos.x() + " "+startPos.y()+ " "+ startPos.z());
//                        System.out.println("End Pos : "+newPos.x() + " "+newPos.y()+ " "+ newPos.z());
//                        System.out.println((double) tickGonnaBe/interpolationDuration);
                        cameraEntity.teleport(gonnaBe);
                    }
                    currentTick.addAndGet(1);

//                    System.out.println("---------------------------");
                }).repeat(TaskSchedule.tick(1)).schedule();

                doneYet.awaitAdvance(doneYet.arrive());
                //done
                task.cancel();

                return;
            });
        }
    }

    public CompletableFuture<Void> interpolate(Pos newPos, int interpolationDuration) {
        return interpolateCore(newPos, interpolationDuration, interpolationDuration, EasingDirection.OUT, EasingStyle.LINEAR);
    }

    public CompletableFuture<Void> interpolate(Pos newPos, int interpolationDuration, EasingStyle style, EasingDirection direction) {
        return interpolateCore(newPos, interpolationDuration, interpolationDuration, direction, style);
    }

    public CompletableFuture<Void> interpolateLagFree(Pos newPos, int interpolationDuration) {
        return interpolateCore(newPos, interpolationDuration, interpolationDuration + 1, EasingDirection.OUT, EasingStyle.LINEAR);
    }

//    public void interpolateLocally(Pos startPos, Pos newPos, int interpolationDuration, List<Player> players) {
//        if (isDead()) return;
//
//        EntityTeleportPacket snapPacket = new EntityTeleportPacket(cameraEntity.getEntityId(), startPos, Pos.ZERO, 1, false);
//        EntityMetaDataPacket snapDurationPack = new EntityMetaDataPacket(cameraEntity.getEntityId(),
//                Map.of(DISPLAY_ENTITY_POS_ROT_INDEX, Metadata.VarInt(0)));
//
//        players.forEach(player -> player.sendPackets(snapDurationPack, snapPacket));
//
//        MinecraftServer.getSchedulerManager().buildTask(() -> {
//            if (isDead()) return;
//
//            EntityTeleportPacket newPacket = new EntityTeleportPacket(cameraEntity.getEntityId(), newPos, Pos.ZERO, 1, false);
//            EntityMetaDataPacket newDurationPack = new EntityMetaDataPacket(cameraEntity.getEntityId(),
//                    Map.of(DISPLAY_ENTITY_POS_ROT_INDEX, Metadata.VarInt(interpolationDuration)));
//
//            players.forEach(player -> {
//                if (player.isOnline()) {
//                    player.sendPackets(newDurationPack, newPacket);
//                }
//            });
//        }).delay(TaskSchedule.tick(1)).schedule();
//    }

    public Camera (Instance world, Pos startPos) {
        this.lastGlobalPos = startPos;
        cameraEntity = new Entity(EntityType.BLOCK_DISPLAY);

        cameraEntity.setHasPhysics(false);
        cameraEntity.setNoGravity(true);

        world.loadChunk(startPos).thenRun(() -> cameraEntity.setInstance(world,startPos));
    }
}
