package org.imnotcamera;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;

public class TweenUtils {
    private static final double S = 1.70158;
    private static final double S2 = S * 1.525;

    // Default CSS ease curve control points (0.25, 0.1, 0.25, 1.0)
    private static final double BEZIER_X1 = 0.25;
    private static final double BEZIER_Y1 = 0.1;
    private static final double BEZIER_X2 = 0.25;
    private static final double BEZIER_Y2 = 1.0;

    private static double getEasingPercent(double t, EasingDirection direction, EasingStyle style) {
        return switch (style) {
            case STEP -> getStepPercent(t);
            case BEZIER -> getBlockbenchBezierPercent(t);
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

    private static double getCubicBezierValue(double p1, double p2, double u) {
        double oneMinusU = 1.0 - u;
        return 3.0 * oneMinusU * oneMinusU * u * p1 + 3.0 * oneMinusU * u * u * p2 + u * u * u;
    }

    private static double getCubicBezierDerivative(double p1, double p2, double u) {
        double oneMinusU = 1.0 - u;
        return 3.0 * oneMinusU * oneMinusU * BEZIER_X1 + 6.0 * oneMinusU * u * (p2 - p1) + 3.0 * u * u * (1.0 - p2);
    }

    private static double getBlockbenchBezierPercent(double t) {
        if (t <= 0.0) return 0.0;
        if (t >= 1.0) return 1.0;

        // Solve u for X(u) = t using Newton-Raphson
        double u = t;
        for (int i = 0; i < 8; i++) {
            double currentX = getCubicBezierValue(BEZIER_X1, BEZIER_X2, u) - t;
            double derivativeX = getCubicBezierDerivative(BEZIER_X1, BEZIER_X2, u);

            if (Math.abs(derivativeX) < 1e-6) break;
            u -= currentX / derivativeX;
        }

        u = Math.max(0.0, Math.min(1.0, u));
        return getCubicBezierValue(BEZIER_Y1, BEZIER_Y2, u);
    }

    private static double getStepPercent(double t) {
        return t >= 1.0 ? 1.0 : 0.0;
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