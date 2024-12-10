package android.view;

import android.os.IBinder;

public class SurfaceControl {
    public static final class DesiredDisplayModeSpecs {
        public int defaultMode;
    }
    public static DesiredDisplayModeSpecs getDesiredDisplayModeSpecs(
            IBinder displayToken) {
        throw new RuntimeException("stub!");
    }
}
