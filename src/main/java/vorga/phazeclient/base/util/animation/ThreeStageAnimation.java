package vorga.phazeclient.base.util.animation;

import lombok.Getter;

public class ThreeStageAnimation {

    @Getter
    private final double appearDuration;
    @Getter
    private final double existDuration;
    @Getter
    private final double disappearDuration;
    private final double totalDuration;

    private final Interpolation appearInterpolation;
    private final Interpolation disappearInterpolation;

    public ThreeStageAnimation(double appearDuration, double existDuration, double disappearDuration,
                              Interpolation appearInterpolation,
                               Interpolation disappearInterpolation) {
        this.appearDuration = appearDuration;
        this.existDuration = existDuration;
        this.disappearDuration = disappearDuration;
        this.totalDuration = appearDuration + existDuration + disappearDuration;

        this.appearInterpolation = appearInterpolation;
        this.disappearInterpolation = disappearInterpolation;
    }

    public double getValue(double elapsedTime) {
        if (elapsedTime >= totalDuration) {
            return 0.0;
        }

        if (elapsedTime <= appearDuration) {
            double progress = elapsedTime / appearDuration;
            return appearInterpolation.interpolate(progress);
        } else if (elapsedTime <= appearDuration + existDuration) {
            return 1.0;
        } else {
            double progress = (elapsedTime - appearDuration - existDuration) / disappearDuration;
            return 1.0 - disappearInterpolation.interpolate(progress);
        }
    }

    public AnimationStage getStage(double elapsedTime) {
        if (elapsedTime <= appearDuration) {
            return AnimationStage.APPEAR;
        } else if (elapsedTime <= appearDuration + existDuration) {
            return AnimationStage.EXIST;
        } else if (elapsedTime <= totalDuration) {
            return AnimationStage.DISAPPEAR;
        } else {
            return AnimationStage.FINISHED;
        }
    }

    public boolean isFinished(double elapsedTime) {
        return elapsedTime >= totalDuration;
    }

    public enum AnimationStage {
        APPEAR,
        EXIST,
        DISAPPEAR,
        FINISHED
    }
}