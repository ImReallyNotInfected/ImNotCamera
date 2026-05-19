package org.imnotcamera;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;

public class TweenUtils {
    private static final double S = 1.70158;
    private static final double S2 = S * 1.525;

    private static double getEasingPercent(double t, EasingDirection direction, EasingStyle style) {
        return switch (style) {
            case QUAD -> switch (direction) {
                case IN -> t * t;
                case OUT -> t * (2.0 - t);
                case IN_OUT -> t < 0.5 ? 2.0 * t * t : -1.0 + (4.0 - 2.0 * t) * t;
            };
            case CUBIC -> switch (direction) {
                case IN -> t * t * t;
                case OUT -> 1.0 - Math.pow(1.0 - t, 3);
                case IN_OUT -> t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3) / 2.0;
            };
            case QUART -> switch (direction) {
                case IN -> t * t * t * t;
                case OUT -> 1.0 - Math.pow(1.0 - t, 4);
                case IN_OUT -> t < 0.5 ? 8.0 * t * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 4) / 2.0;
            };
            case BACK -> switch (direction) {
                case IN -> t * t * ((S + 1.0) * t - S);
                case OUT -> 1.0 + Math.pow(t - 1.0, 2) * ((S + 1.0) * (t - 1.0) + S);
                case IN_OUT -> t < 0.5
                        ? (Math.pow(2.0 * t, 2) * ((S2 + 1.0) * 2.0 * t - S2)) / 2.0
                        : (Math.pow(2.0 * t - 2.0, 2) * ((S2 + 1.0) * (2.0 * t - 2.0) + S2) + 2.0) / 2.0;
            };
            case LINEAR -> t;
        };
    }

    private static float lerpAngle(float start, float end, float percent) {
        float delta = (end - start + 180.0f) % 360.0f - 180.0f;
        if (delta < -180.0f) delta += 360.0f;
        return start + delta * percent;
    }

    public static Pos getTweenPos(Pos startPos, Pos endPos, double currentTime, double duration, EasingDirection direction, EasingStyle style) {
        double tPrime = currentTime / duration;
        tPrime = Math.max(0.0, Math.min(1.0, tPrime));

        double percent = getEasingPercent(tPrime, direction, style);

        double currentX = startPos.x() + (endPos.x() - startPos.x()) * percent;
        double currentY = startPos.y() + (endPos.y() - startPos.y()) * percent;
        double currentZ = startPos.z() + (endPos.z() - startPos.z()) * percent;


        float currentYaw = lerpAngle(startPos.yaw(), endPos.yaw(), (float) percent);
        float currentPitch = lerpAngle(startPos.pitch(), endPos.pitch(), (float) percent);

        return new Pos(currentX, currentY, currentZ, currentYaw, currentPitch);
    }
}
