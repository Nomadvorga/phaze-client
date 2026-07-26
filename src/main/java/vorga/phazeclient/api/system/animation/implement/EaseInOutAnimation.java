package vorga.phazeclient.api.system.animation.implement;

import vorga.phazeclient.api.system.animation.Animation;

public class EaseInOutAnimation extends Animation {

    @Override
    public double calculation(double value) {
        double x = value / ms;

        if (x < 0.5) {
            return 4 * x * x * x;
        } else {
            double f = (2 * x) - 2;
            return 1 - (f * f * f) / 2;
        }
    }
}
