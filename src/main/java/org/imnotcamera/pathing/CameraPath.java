package org.imnotcamera.pathing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CameraPath {
    private String name;
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    private Map<Integer, PathKeyframe> positionKeyframes = new HashMap<>();
    public Map<Integer, PathKeyframe> getPositionKeyframes() {
        return positionKeyframes;
    }

    private Map<Integer, PathKeyframe> rotationKeyframes = new HashMap<>();
    public Map<Integer, PathKeyframe> getRotationKeyframes() {
        return rotationKeyframes;
    }

    public CameraPath(String name) {
        this.name = name;
    }
}
