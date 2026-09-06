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
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.utils.EaseFunction;
import org.imnotcamera.pathing.CameraPath;
import org.imnotcamera.pathing.PathKeyframe;

import java.util.*;
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
    private CameraPath currentPath;
    public Task currentPathTask = null;

    private Instance lastInstance;
    public Instance getLastInstance() {
        return lastInstance;
    }

    public Entity getCameraEntity() {
        return cameraEntity;
    }

    private Map<UUID, CameraData> playerUUIDList = new ConcurrentHashMap<>();
    public Pos getLastGlobalPos() {
        return lastGlobalPos;
    }
    public Map<UUID, CameraData> getPlayerUUIDList() {
        return playerUUIDList;
    }
    public CameraPath getCurrentPath() {
        return currentPath;
    }

    public boolean isDead() {
        return dead;
    }

    public void addPlayer(Player player) {
        if (isDead()) {return;}

        CameraData cameraData = new CameraData(player.getPosition(),player.getGameMode(),player.getInstance());

        //now set
        playerUUIDList.put(player.getUuid(), cameraData);

        if (!getLastInstance().equals(player.getInstance())) {
            player.setInstance(getLastInstance()).thenRun(() -> {
                if (isDead()) {return;}
                player.setGameMode(GameMode.SPECTATOR);
                player.spectate(cameraEntity);
            });
        } else {
            player.setGameMode(GameMode.SPECTATOR);
            player.spectate(cameraEntity);
        }

    }

    public void addPlayer(UUID uuid) {
        Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid);
        if (player == null) {return;}

        addPlayer(player);
    }

    public void removePlayer(UUID uuid, boolean teleportBack) {
        CameraData lastData = playerUUIDList.getOrDefault(uuid,null);
        Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid);

        playerUUIDList.remove(uuid);

        if (player != null && player.isOnline()) {
            player.stopSpectating();
        }

        if (lastData !=null && player!= null && teleportBack) {
            lastData.getLastInstance().loadChunk(lastData.getLastPos()).thenRun(() -> {
                if (!player.isOnline()) {return;}

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

    public void removePlayer(Player player, boolean teleportBack) {
        removePlayer(player.getUuid(), teleportBack);
    }

    //now actual camera stuff
    private CompletableFuture<Void> interpolateCore(Pos newPos, int interpolationDuration, int delayTicks, EasingDirection direction, EasingStyle style) {
        if (isDead()) {return CompletableFuture.runAsync(() -> {});} // just an empty shell.

        BlockDisplayMeta meta = (BlockDisplayMeta) cameraEntity.getEntityMeta();
        this.lastGlobalPos = newPos;

        if (style == EasingStyle.STEP) {
            meta.setPosRotInterpolationDuration(0);
            if (cameraEntity.getInstance() != null) {
                cameraEntity.teleport(newPos);
            }

            return CompletableFuture.runAsync(() -> {
                Phaser doneYet = new Phaser(1);
                doneYet.register();

                MinecraftServer.getSchedulerManager()
                        .buildTask(doneYet::arriveAndDeregister)
                        .delay(TaskSchedule.tick(delayTicks))
                        .schedule();

                doneYet.awaitAdvance(doneYet.arrive());
            });
        }

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

    //PATH RELATED
    private Pos getPositionWithOffset(Pos absolutePos, Pos offset) {
        return new Pos(absolutePos.x() + offset.x(), absolutePos.y() + offset.y(), absolutePos.z() + offset.z(),
                absolutePos.yaw() + offset.yaw(), absolutePos.pitch() + offset.pitch());
    }

    public void interpolatePath(CameraPath path) {
        if (isDead() || path == null) return;
        if (currentPathTask!=null) {
            currentPathTask.cancel();
        }

        Pos absolutePos = getLastGlobalPos();
        currentPath = path;

        Map<Integer, PathKeyframe> posMap = path.getPositionKeyframes();
        Map<Integer, PathKeyframe> rotMap = path.getRotationKeyframes();


        if (posMap.isEmpty() || rotMap.isEmpty()) {
            return;
        }

        if (posMap.get(0) == null || rotMap.get(0) == null) {
            System.out.println("WARNING : Path's First Keyframe must set to time 0 in the timeline!");
            return;
        }

        PathKeyframe lastPosKf = posMap.get(0);
        PathKeyframe lastRotKf = rotMap.get(0);
        //now get the largest time and set!
        TreeSet<Integer> availableTicks = new TreeSet<>();
        availableTicks.addAll(posMap.keySet());
        availableTicks.addAll(rotMap.keySet());

        // Collect all distinct keyframe tick markers
        TreeSet<Integer> posTicks = new TreeSet<>(posMap.keySet());
        TreeSet<Integer> rotTicks = new TreeSet<>(rotMap.keySet());

        AtomicInteger nextTick = new AtomicInteger(0);
        int maxTicks = availableTicks.stream().max(Comparator.comparingInt(Integer::intValue)).orElse(0);

        currentPathTask = MinecraftServer.getSchedulerManager().buildTask(() -> {
            if (isDead()) return;
            if (nextTick.get() > maxTicks) {
                currentPathTask.cancel();
                return;
            }

            if (nextTick.get() == 0) {
                setGlobalPos(getPositionWithOffset(absolutePos, lastPosKf.toPos().withView(
                        (float) lastRotKf.x(),(float) lastRotKf.y()
                )));
            } else {
                //interpolate time
                Pos interpolatedOffsetPos;

                Integer prevPosTick = posTicks.floor(nextTick.get());
                Integer nextPosTick = posTicks.ceiling(nextTick.get());

                if (prevPosTick == null) prevPosTick = posTicks.first();
                if (nextPosTick == null) nextPosTick = posTicks.last();

                PathKeyframe prevPosKf = posMap.get(prevPosTick);
                PathKeyframe nextPosKf = posMap.get(nextPosTick);

                Pos startPos = prevPosKf.toPos();
                Pos endPos = nextPosKf.toPos();

                int posSegmentDuration = nextPosTick - prevPosTick;
                double localPosTime = nextTick.get() - prevPosTick;

                if (posSegmentDuration <= 0 || localPosTime <= 0) {
                    interpolatedOffsetPos = startPos;
                } else if (localPosTime >= posSegmentDuration) {
                    interpolatedOffsetPos = endPos;
                } else {
                    EasingStyle style = nextPosKf.getEasingStyle() != null ? nextPosKf.getEasingStyle() : EasingStyle.LINEAR;
                    interpolatedOffsetPos = TweenUtils.getTweenPos(
                            startPos,
                            endPos,
                            localPosTime,
                            posSegmentDuration,
                            EasingDirection.OUT,
                            style
                    );
                }

                float currentYaw;
                float currentPitch;

                // Find the bounding keyframes for Rotation around tick t
                Integer prevRotTick = rotTicks.floor(nextTick.get());
                Integer nextRotTick = rotTicks.ceiling(nextTick.get());

                if (prevRotTick == null) prevRotTick = rotTicks.first();
                if (nextRotTick == null) nextRotTick = rotTicks.last();

                PathKeyframe prevRotKf = rotMap.get(prevRotTick);
                PathKeyframe nextRotKf = rotMap.get(nextRotTick);

                Pos startRotPos = new Pos(0, 0, 0, (float) prevRotKf.x(), (float) prevRotKf.y());
                Pos endRotPos = new Pos(0, 0, 0, (float) nextRotKf.x(), (float) nextRotKf.y());

                int rotSegmentDuration = nextRotTick - prevRotTick;
                double localRotTime = nextTick.get() - prevRotTick;

                if (rotSegmentDuration <= 0 || localRotTime <= 0) {
                    currentYaw = startRotPos.yaw();
                    currentPitch = startRotPos.pitch();
                } else if (localRotTime >= rotSegmentDuration) {
                    currentYaw = endRotPos.yaw();
                    currentPitch = endRotPos.pitch();
                } else {
                    EasingStyle style = nextRotKf.getEasingStyle();
                    Pos tweenedRot = TweenUtils.getTweenPos(
                            startRotPos,
                            endRotPos,
                            localRotTime,
                            rotSegmentDuration,
                            EasingDirection.OUT,
                            style
                    );
                    currentYaw = tweenedRot.yaw();
                    currentPitch = tweenedRot.pitch();
                }

                Pos finalOffsetPos = new Pos(
                        interpolatedOffsetPos.x(),
                        interpolatedOffsetPos.y(),
                        interpolatedOffsetPos.z(),
                        currentYaw,
                        currentPitch
                );

                Pos targetGlobalPos = getPositionWithOffset(absolutePos, finalOffsetPos);

                interpolate(targetGlobalPos, 1);
            }

            nextTick.addAndGet(1);
        }).repeat(TaskSchedule.tick(1)).schedule();
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

    public void setGlobalPos(Pos pos) {
        lastGlobalPos = pos;

        BlockDisplayMeta meta = (BlockDisplayMeta) cameraEntity.getEntityMeta();
        meta.setPosRotInterpolationDuration(0);

        cameraEntity.teleport(pos);
    }

    public Camera (Instance world, Pos startPos) {
        this.lastGlobalPos = startPos;
        cameraEntity = new Entity(EntityType.BLOCK_DISPLAY);
        this.lastInstance = world;

        cameraEntity.setHasPhysics(false);
        cameraEntity.setNoGravity(true);

        cameraEntity.setInstance(world,startPos);
        //world.loadChunk(startPos).thenRun(() -> cameraEntity.setInstance(world,startPos));
    }
}
