package vorga.phazeclient.api.system.animation.implement;

import vorga.phazeclient.api.system.animation.Animation;

public class DecelerateAnimation extends Animation {

    @Override
    public double calculation(double value) {
        double x = value / ms;
        return 1 - (x - 1) * (x - 1);
    }
}
