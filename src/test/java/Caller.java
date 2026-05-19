import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import org.imnotcamera.Camera;
import org.imnotcamera.EasingDirection;
import org.imnotcamera.EasingStyle;
import org.imnotcamera.ImNotCamera;

import java.time.Duration;
import java.util.List;

public class Caller {
    public static void main() {
        // Initialization
        MinecraftServer minecraftServer = MinecraftServer.init();

        // Create the instance
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        InstanceContainer instanceContainer = instanceManager.createInstanceContainer();

        // Set the ChunkGenerator
        instanceContainer.setGenerator(unit -> unit.modifier().fillHeight(0, 40, Block.GRASS_BLOCK));

        //CAMERA
        ImNotCamera.init();


        //END

        // Add an event callback to specify the spawning instance (and the spawn position)
        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            final Player player = event.getPlayer();
            event.setSpawningInstance(instanceContainer);
            player.setRespawnPoint(new Pos(0, 42, 0));
        });

        globalEventHandler.addListener(PlayerSpawnEvent.class, event-> {
            Camera camera = ImNotCamera.buildNewCamera(instanceContainer, new Pos(0,45,0));
            Player player = event.getPlayer();
            camera.addPlayer(player);

            MinecraftServer.getSchedulerManager().buildTask(() -> {
               camera.interpolate(new Pos(0,45,8,155,65),30, EasingStyle.CUBIC, EasingDirection.OUT)
                       .thenRun(() -> {
                           camera.interpolate(new Pos(0,45,8,180,0),20,EasingStyle.QUAD,EasingDirection.OUT).thenRun(() -> {
                               camera.interpolate(new Pos(0,45,2,180,0),25,EasingStyle.BACK,EasingDirection.IN)
                                       .thenRun(() -> {
                                           ImNotCamera.terminateCamera(camera);
                                       });
                           });
                       });
            }).delay(Duration.ofSeconds(6)).schedule();
        });

        // Start the server on port 25565
        minecraftServer.start("0.0.0.0", 25565);
    }
}
