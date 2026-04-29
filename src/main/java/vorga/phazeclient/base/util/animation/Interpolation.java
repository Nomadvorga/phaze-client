package vorga.phazeclient.base.util.animation;

public interface Interpolation {

    double interpolate(double progress);

    String getName();
}