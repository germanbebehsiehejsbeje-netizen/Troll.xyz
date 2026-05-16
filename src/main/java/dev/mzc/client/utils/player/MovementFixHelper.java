package dev.mzc.client.utils.player;

public final class MovementFixHelper {
    private MovementFixHelper() {
    }

    public static DirectionalInput findBestInput(float correctedForward, float correctedSideways, MovementPenalty penalty) {
        float bestForward = 0.0f;
        float bestStrafe = 0.0f;
        double bestScore = Double.MAX_VALUE;

        for (float forward = -1.0f; forward <= 1.0f; forward += 1.0f) {
            for (float strafe = -1.0f; strafe <= 1.0f; strafe += 1.0f) {
                if (forward == 0.0f && strafe == 0.0f) {
                    continue;
                }

                double score = squaredDifference(correctedForward, correctedSideways, forward, strafe);
                if (penalty != null) {
                    score += penalty.apply(forward, strafe);
                }

                if (score < bestScore) {
                    bestScore = score;
                    bestForward = forward;
                    bestStrafe = strafe;
                }
            }
        }

        return new DirectionalInput(bestForward, bestStrafe);
    }

    public static double squaredDifference(float desiredForward, float desiredSideways, float forward, float strafe) {
        double forwardDiff = desiredForward - forward;
        double strafeDiff = desiredSideways - strafe;
        return forwardDiff * forwardDiff + strafeDiff * strafeDiff;
    }

    @FunctionalInterface
    public interface MovementPenalty {
        double apply(float forward, float strafe);
    }
}
