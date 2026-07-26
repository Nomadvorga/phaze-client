package vorga.phazeclient.api.system.animation.implement;

import vorga.phazeclient.api.system.animation.Animation;

public class LinearAnimation extends Animation {

    @Override
    public double calculation(double value) {
        return value / ms;
    }
}