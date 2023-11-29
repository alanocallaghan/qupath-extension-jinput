package qupath.ext.jinput;

import net.java.games.input.Component;
import net.java.games.input.Controller;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;

import java.awt.*;

public class QuPathAdvancedGamepadController implements QuPathAdvancedController {

    private Controller controller;
    private QuPathGUI qupath;
    //private static BooleanProperty invertControllerScrolling = PathPrefs.createPersistentPreference("invertControllerScrolling", false);

    private boolean zoomInPressed = false;
    private boolean zoomOutPressed = false;

    private boolean isInvertedScrolling = false;


//		private long lastTimestamp = 0;

    transient int MAX_SKIP = 5;
    transient int skipCount = 0;

    public QuPathAdvancedGamepadController(final Controller controller, final QuPathGUI qupath, final int heartbeat) {
        this.controller = controller;
        this.qupath = qupath;
//			lastTimestamp = System.currentTimeMillis();
    }
    @Override
    public String getControllerName() {
        return controller.getName();
    }

    double getHigherMagnification(double mag) {
        if (mag >= 10 - 0.0001)
            return 40;
        else if (mag >= 4 - 0.0001)
            return 10;
        else if (mag >= 1 - 0.0001)
            return 4;
        else if (mag >= 0.25 - 0.0001)
            return 1;
        return 0.25;
    }

    double getLowerMagnification(double mag) {
        if (mag > 40 + 0.0001)
            return 40;
        if (mag > 10 + 0.0001)
            return 10;
        else if (mag > 4 + 0.0001)
            return 4;
        else if (mag > 1 + 0.0001)
            return 1;
        return 0.25;
    }

    /**
     * Return true if the update is successful and the controller remains in a valid state, false otherwise.
     *
     * If false is returned, then the controller may be stopped.
     *
     * @return
     */
    @Override
    public boolean updateViewer() {

        // Check the device
        if (!controller.poll())
            return false;

        // Check we have a viewer & server
        QuPathViewer viewer = qupath.getViewer();
        if (viewer == null || viewer.getServer() == null)
            return true;

        double serverMag = viewer.getServer().getMetadata().getMagnification();
        double magnification = viewer.getMagnification();
        double downsample = viewer.getDownsampleFactor();

        // Assume x40 if no other info...
        if (Double.isNaN(serverMag)) {
            serverMag = 40;
            magnification = serverMag / downsample;
        }

        // todo: scale this based on current downsample dimensions
        Shape displayedRegionShape = viewer.getDisplayedRegionShape();
        int width = displayedRegionShape.getBounds().width;
        double scrollScale;
        if (width <= 1) {
            scrollScale = 2;
        } else {
            scrollScale = (double) width / 30;
        }


        // Checking if we need to invert
        if (!PathPrefs.createPersistentPreference("invertControllerScrolling", false).get())
            scrollScale = -scrollScale;

        double rot = viewer.getRotation();

        double dx = 0, dy = 0, z = 0, rz = 0, dr = 0;
        // Zooming in or out
        int zoom = 0;
        for (Component c : controller.getComponents()) {
            //Use a non-locale version of c.getName()
            String name = c.getIdentifier().toString();
            double polled = c.getPollData();
//            System.out.printf("%s: %f\n", name, polled);
            if (Math.abs(polled) < c.getDeadZone()) polled = 0;

            if ("x".equals(name)) {
                dx = polled;
            } else if ("y".equals(name)) {
                dy = polled;
            } else if ("z".equals(name)) {
                z = polled;
            } else if ("rx".equals(name)) {
            } else if ("ry".equals(name)) {
            } else if ("rz".equals(name)) {
                rz = polled;
            } else if ("0".equals(name)) {
                if (polled != 0) {
                    if (!zoomInPressed) // Don't zoom again if the button was already pressed
                        zoom -= 1;
                    zoomInPressed = true;
                } else
                    zoomInPressed = false;
            } else if ("1".equals(name)) {
                if (polled != 0) {
                    if (!zoomOutPressed) // Don't zoom again if the button was already pressed
                        zoom += 1;
                    zoomOutPressed = true;
                } else
                    zoomOutPressed = false;
            }
        }
        z = (z + 1) / 2;
        rz = (rz + 1) / 2;
        double dz = rz - z;

        boolean xMoved = Math.abs(dx) > 1e-1;
        boolean yMoved = Math.abs(dy) > 1e-1;
        boolean zMoved = Math.abs(dz) > 1e-1;
        boolean rMoved = Math.abs(dr) > 1e-1;

        if (!xMoved && !yMoved && !zMoved && !rMoved && zoom == 0)
            return true;

        if (zoom != 0) {
            if (zoom > 0)
                downsample = serverMag / getHigherMagnification(magnification);
            else
                downsample = serverMag / getLowerMagnification(magnification);
            viewer.setDownsampleFactor(downsample, -1, -1);
        } else if (zMoved && Math.abs(dz * 20) >= 1) {
            viewer.zoomIn((int)(dz * 20));

            // If we're zooming this way, we're done - ignore other small x,y adjustments
            //return true;
        }

        //Here we test the rotation
        if (rMoved) {
            dr = dr/8;
            viewer.setRotation(rot + dr);
        }

        if (xMoved || yMoved) {
            // Shift as required - correcting for rotation (Pete's code)
            double sin = Math.sin(-rot);
            double cos = Math.cos(-rot);

            double dx2 = -dx * scrollScale;
            double dy2 = -dy * scrollScale;

            double dx3 = cos * dx2 - sin * dy2;
            double dy3 = sin * dx2 + cos * dy2;

            viewer.setCenterPixelLocation(
                    viewer.getCenterPixelX() + dx3,
                    viewer.getCenterPixelY() + dy3);
        }

        //System.out.println("rot:" + rot + " dx: " + dx + ", dy: " + dy + ", dz: " + dz + ", dr: " + dr + "scrollScale: " + scrollScale + "rot: " + rot);
        return true;
    }

}
