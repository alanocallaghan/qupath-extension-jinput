/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.ext.jinput;

import net.java.games.input.Component;
import net.java.games.input.Controller;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.prefs.PathPrefs;


/**
 * 
 * Advanced input controller for browsing whole slide images in digital pathology, using
 * JInput - https://java.net/projects/jinput
 * 
 * Currently, this has been designed for (and only tested with) SpaceNavigator from 3D Connexion:
 *  http://www.3dconnexion.co.uk/products/spacemouse/spacenavigator.html
 * However, it does not make use of most of the 3D features, and therefore *may* work with other
 * similar input controllers, including joysticks (and it may not).
 * 
 * @author Pete Bankhead
 *
 */
public class QuPathAdvancedStickController implements QuPathAdvancedController {

	private final Controller controller;
	private final QuPathGUI qupath;
	//private static BooleanProperty invertControllerScrolling = PathPrefs.createPersistentPreference("invertControllerScrolling", false);

	private boolean zoomInPressed = false;
	private boolean zoomOutPressed = false;

	private final boolean isInvertedScrolling = false;

	//We'll use these to compare new and old values
	private double old_dx = 0;
	private double old_dy = 0;
	private double old_dz = 0;
	private double old_dr = 0;

//		private long lastTimestamp = 0;

	transient int MAX_SKIP = 5;
	transient int skipCount = 0;

	public QuPathAdvancedStickController(final Controller controller, final QuPathGUI qupath, final int heartbeat) {
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
	 * Try to poll the controller to update the viewer.
	 * @return true if the update is successful and the controller remains in a valid state, false otherwise.
	 * 	 * If false is returned, then the controller may be stopped.
	 */
	@Override
	public boolean updateViewer() {

//			// If we seem to be falling behind, skip this event
//			long timestamp = System.currentTimeMillis();
//			if (timestamp - lastTimestamp > heartbeat * 1.75 && skipCount < MAX_SKIP) {
//				System.out.println("3D input event: Skipping update - timestamp difference is " + (timestamp - lastTimestamp) + " ms");
//				skipCount++;
//				lastTimestamp = timestamp;
//				return;
//			}
//			skipCount = 0;
//			lastTimestamp = timestamp;

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

		double scrollScale = 10;
		if (magnification > 80)
			scrollScale = 25;
		else if (magnification > 40)
			scrollScale = 100;
		else if (magnification >= 4)
			scrollScale = 200;
		else
			scrollScale = 1000;

		// Checking if we need to invert
		if (!PathPrefs.createPersistentPreference("invertControllerScrolling", false).get())
			scrollScale = -scrollScale;

		double dx = old_dx;
		double dy = old_dy;
		double dz = old_dz;
		double dr = old_dr; //rotation
		double rot = viewer.getRotation();

		// Zooming in or out
		int zoom = 0;

		for (Component c : controller.getComponents()) {
			//Use a non-locale version of c.getName()
			String name = c.getIdentifier().toString();
			double polled = c.getPollData();
			System.out.printf("%s: %f\n", name, polled);
			if (Math.abs(polled) < c.getDeadZone()) polled = 0;

			if ("x".equals(name)) {
				dx = polled;
			} else if ("y".equals(name)) {
				dy = polled;
			} else if ("z".equals(name)) {
				dz = polled;
			} else if ("rx".equals(name)) {
			} else if ("ry".equals(name)) {
			} else if ("rz".equals(name)) {
				dr = polled;
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

		boolean xMoved = Math.abs(old_dx - dx) > 1e-5;
		boolean yMoved = Math.abs(old_dy - dy) > 1e-5;
		boolean zMoved = Math.abs(old_dz - dz) > 1e-5;
		boolean rMoved = Math.abs(old_dr - dr) > 1e-5;

		if ( !xMoved && !yMoved && !zMoved && !rMoved && zoom == 0)
			return true;

		old_dx = dx; old_dy = dy; old_dz = dz; old_dr = dr;

		if (zoom != 0) {
			if (zoom > 0)
				downsample = serverMag / getHigherMagnification(magnification);
			else
				downsample = serverMag / getLowerMagnification(magnification);
			viewer.setDownsampleFactor(downsample, -1, -1);
		} else if (zMoved && Math.abs(dz * 20) >= 1) {
			if (dz < 0)
				viewer.zoomIn((int)(Math.abs(dz * 20)));
			else
				viewer.zoomOut((int)(Math.abs(dz * 20)));

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

			double dx2 = dx * scrollScale;
			double dy2 = dy * scrollScale;

			double dx3 = cos * dx2 - sin * dy2;
			double dy3 = sin * dx2 + cos * dy2;

			viewer.setCenterPixelLocation(
					viewer.getCenterPixelX() + dx3,
					viewer.getCenterPixelY() + dy3);
		}

		//System.out.println("rot:" + rot + " dx: " + dx + ", dy: " + dy + ", dz: " + dz + ", dr: " + dr + "scrollScale: " + scrollScale + "rot: " + rot);
		return true;
	}

	@Override
	public Controller getController() {
		return controller;
	}
}
