package vorga.phazeclient.base.util.animation;

public class Interpolations {

    public static final Interpolation LINEAR = new Interpolation() {
        @Override
        public double interpolate(double progress) {
            return progress;
        }

        @Override
        public String getName() {
            return "Linear";
        }
    };

    public static final Interpolation EASE_IN = new Interpolation() {
        @Override
        public double interpolate(double progress) {
            return progress * progress;
        }

        @Override
        public String getName() {
            return "Smooth";
        }
    };

    public static final Interpolation EASE_OUT = new Interpolation() {
        @Override
        public double interpolate(double progress) {
            return 1 - Math.pow(1 - progress, 2);
        }

        @Override
        public String getName() {
            return "Fast";
        }
    };

    public static final Interpolation EASE_IN_OUT = new Interpolation() {
        @Override
        public double interpolate(double progress) {
            if (progress < 0.5) {
                return 2 * progress * progress;
            } else {
                return 1 - Math.pow(-2 * progress + 2, 2) / 2;
            }
        }

        @Override
        public String getName() {
            return "Balanced";
        }
    };

    public static final Interpolation EASE_OUT_BACK = new Interpolation() {
        @Override
        public double interpolate(double progress) {
            return 1 + 2.70158 * Math.pow(progress - 1, 3) + 1.70158 * Math.pow(progress - 1, 2);
        }

        @Override
        public String getName() {
            return "Back";
        }
    };

    public static final Interpolation EASE_IN_BACK = new Interpolation() {
        @Override
        public double interpolate(double progress) {
            double c1 = 1.70158;
            double c3 = c1 + 1;
            return c3 * progress * progress * progress - c1 * progress * progress;
        }

        @Override
        public String getName() {
            return "Overshoot";
        }
    };

    public static final Interpolation ELASTIC = new Interpolation() {
        @Override
        public double interpolate(double progress) {
            if (progress == 0) return 0;
            if (progress == 1) return 1;

            double c4 = (2 * Math.PI) / 3;
            return Math.pow(2, -10 * progress) * Math.sin((progress * 10 - 0.75) * c4) + 1;
        }

        @Override
        public String getName() {
            return "Elastic";
        }
    };

    public static final Interpolation BOUNCE = new Interpolation() {
        @Override
        public double interpolate(double progress) {
            double n1 = 7.5625;
            double d1 = 2.75;

            if (progress < 1 / d1) {
                return n1 * progress * progress;
            } else if (progress < 2 / d1) {
                return n1 * (progress -= 1.5 / d1) * progress + 0.75;
            } else if (progress < 2.5 / d1) {
                return n1 * (progress -= 2.25 / d1) * progress + 0.9375;
            } else {
                return n1 * (progress -= 2.625 / d1) * progress + 0.984375;
            }
        }

        @Override
        public String getName() {
            return "Bounce";
        }
    };

    public static final Interpolation IOS_EASE_OUT = new Interpolation() {
        @Override
        public double interpolate(double progress) {
            return 1 - Math.pow(1 - progress, 3);
        }

        @Override
        public String getName() {
            return "Ease Out";
        }
    };

    public static final Interpolation IOS_SPRING = new Interpolation() {
        @Override
        public double interpolate(double progress) {
            double damping = 0.7;
            double frequency = 1.5;

            double decay = Math.exp(-damping * frequency * progress);
            double oscillation = Math.cos(frequency * progress * Math.PI * 2);

            return 1 - (decay * oscillation * 0.1 + decay * (1 - progress));
        }

        @Override
        public String getName() {
            return "Spring";
        }
    };

    public static final Interpolation IOS_DECELERATE = new Interpolation() {
        @Override
        public double interpolate(double progress) {
            return progress * (2 - progress);
        }

        @Override
        public String getName() {
            return "Decelerate";
        }
    };

    /**
     * Sentinel name reserved for the per-module legacy behaviour. The
     * {@link #getByName(String)} switch maps it to {@link #LINEAR}
     * because that's the safe identity transform (consumers that
     * receive {@code Default} unrecognised still get a sensible
     * pass-through), but modules that had a non-linear pre-
     * interpolation behaviour (eg exp-decay tab / F5) recognise this
     * name BEFORE calling {@code getByName} and short-circuit to their
     * legacy code path instead. See Animations.tickTabSlide /
     * Animations.tickSmoothF5 for the actual decoding.
     */
    public static final String DEFAULT_NAME = "Default";

    public static Interpolation getByName(String name) {
        return switch (name) {
            case "Default" -> LINEAR;
            case "Linear" -> LINEAR;
            case "Smooth" -> EASE_IN;
            case "Fast" -> EASE_OUT;
            case "Balanced" -> EASE_IN_OUT;
            case "Back" -> EASE_OUT_BACK;
            case "Overshoot" -> EASE_IN_BACK;
            case "Elastic" -> ELASTIC;
            case "Bounce" -> BOUNCE;
            case "Ease Out" -> IOS_EASE_OUT;
            case "Spring" -> IOS_SPRING;
            case "Decelerate" -> IOS_DECELERATE;
            default -> LINEAR;
        };
    }

    public static String[] getAllNames() {
        // "Default" listed first so the dropdown opens on the safe,
        // pre-interpolation behaviour by convention. Modules use this
        // exact name to detect "no easing curve, use legacy path".
        return new String[]{
            "Default",
            "Linear", "Smooth", "Fast", "Balanced",
            "Back", "Overshoot", "Elastic", "Bounce",
            "Ease Out", "Spring", "Decelerate"
        };
    }
}