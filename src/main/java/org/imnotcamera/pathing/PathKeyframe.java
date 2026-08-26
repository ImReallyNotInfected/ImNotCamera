package org.imnotcamera.pathing;

import net.minestom.server.coordinate.Pos;
import org.imnotcamera.EasingStyle;

public record PathKeyframe(double x, double y, double z, String interpolation) {
    public Pos toPos() {
        return new Pos(x, y, z);
    }

    public EasingStyle getEasingStyle() {
        if (interpolation == null) return EasingStyle.LINEAR;
        return switch (interpolation.toLowerCase()) {
            case "step" -> EasingStyle.STEP;
            case "bezier" -> EasingStyle.BEZIER;
            case "quad", "quadratic" -> EasingStyle.QUAD;
            case "cubic" -> EasingStyle.CUBIC;
            case "quart", "quartic" -> EasingStyle.QUART;
            case "back" -> EasingStyle.BACK;
            default -> EasingStyle.LINEAR;
        };
    }
}
