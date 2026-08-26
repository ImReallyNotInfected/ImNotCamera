package org.imnotcamera.pathing;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class BlockbenchCameraPath {
    //USE THIS IN API
    private List<CameraPath> cameraPaths = new ArrayList<>();
    public CameraPath getCameraPath(String name) {
        return cameraPaths.stream()
                .filter(cameraPath -> cameraPath.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }


    public static BlockbenchCameraPath getCameraPaths(File file) throws IOException {
        BlockbenchCameraPath inst =  new BlockbenchCameraPath();
        ObjectMapper mapper = new ObjectMapper(new JsonFactory());

        JsonNode blockbench = mapper.readTree(file);

        JsonNode animations = blockbench.get("animations");

        animations.forEach(animation -> {
            String name = animation.get("name").asText();
            int snapping = animation.get("snapping").asInt();
            if (snapping != 20) {
                throw new RuntimeException("Snapping MUST be 20 due to Minecraft's TPS!");
            }

            AtomicInteger counter = new AtomicInteger(0); //only needed one

            animation.get("animators").forEach(animator -> {
                if (animator.get("type").asText().equals("camera")) {
                    if (counter.getAndIncrement() > 0) {
                        return;
                    }

                    counter.set(1);
                    //okay create
                    CameraPath path = new CameraPath(name);

                    JsonNode keyframes = animator.get("keyframes");
                    keyframes.forEach(keyframe -> {
                        String channel = keyframe.get("channel").asText();
                        int time = (int) (keyframe.get("time").asDouble() * 20);
                        String interpolation = keyframe.get("interpolation").asText();

                        double x = keyframe.get("data_points").get(0).get("x").asDouble();
                        double y = keyframe.get("data_points").get(0).get("y").asDouble();
                        double z = keyframe.get("data_points").get(0).get("z").asDouble();

                        if (channel.equals("position")) {
                            x = x/16;
                            y = y/16;
                            z = -z/16;
                        } else if (channel.equals("rotation")) {
                            double oldX = x;
                            x = -y;//blockben's x rotation is actually pitch, not yaw
                            y = -oldX; //same
                        }

                        PathKeyframe pathKeyframe = new PathKeyframe(x,y,z, interpolation);

                        if (channel.equals("position")) {
                            path.getPositionKeyframes().put(time, pathKeyframe);
                        } else if (channel.equals("rotation")) {
                            path.getRotationKeyframes().put(time, pathKeyframe);
                        }
                    });

                    inst.cameraPaths.add(path);

                }
            });
        });




        return inst;
    }
}
