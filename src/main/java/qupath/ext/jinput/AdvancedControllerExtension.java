/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.ext.jinput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.viewer.tools.QuPathPenManager;
import qupath.lib.gui.viewer.tools.QuPathPenManager.PenInputManager;

import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.panes.PreferencePane;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.ext.jinput.AdvancedControllerActionFactory;

/**
 * QuPath extension to add advanced input controller support for browsing whole slide images, 
 * using JInput - https://java.net/projects/jinput
 * 
 * Currently, this has been designed for (and only tested with) SpaceNavigator from 3D Connexion:
 *  http://www.3dconnexion.co.uk/products/spacemouse/spacenavigator.html
 * However, it does not make use of most of the 3D features, and therefore *may* work with other
 * similar input controllers, including joysticks (and it may not)..
 * 
 * @author Pete Bankhead
 *
 */
public class AdvancedControllerExtension implements QuPathExtension, GitHubProject {
	
	private static Logger logger = LoggerFactory.getLogger(AdvancedControllerExtension.class);

	
	// Request attempting to load 3D mouse support... needs to be restarted & the mouse plugged in to take effect
	// (And adds ~0.7s to startup time on test Mac Pro)
	private static BooleanProperty requestAdvancedControllers = PathPrefs.createPersistentPreference("requestAdvancedControllers", false);

	public static BooleanProperty requestAdvancedControllersProperty() {
		return requestAdvancedControllers;
	}

	public static boolean getRequestAdvancedControllers() {
		return requestAdvancedControllers.get();
	}

	public static void setRequestAdvancedControllers(boolean request) {
		requestAdvancedControllers.set(request);
	}
	
	private static boolean isInstalled = false;
	
    @Override
    public GitHubRepo getRepository() {
        return GitHubRepo.create("Advanced Control QuPath Extension", "zindy", "qupath-extension-input");
    }

	@Override
	public void installExtension(QuPathGUI qupath) {
		if (isInstalled) {
			logger.warn("Extension already installed!");
			return;
		}
		isInstalled = true;
		
		// Add preference
		PreferencePane panel = qupath.getPreferencePane();
		panel.addPropertyPreference(
				requestAdvancedControllersProperty(),
				Boolean.class,
				"3D mouse support",
				"Viewer",
				"Try to add support for 3D mice - requires QuPath to be restarted to have an effect");
		
		// Try to turn on controllers, if required
		if (getRequestAdvancedControllers()) {
			try {
				// If we have an advanced input controller, try turning it on.
				// Previously, we had a menu item... but here, we assume that if a controller is plugged in, then it's wanted.
				// However, note that it doesn't like it if a controller is unplugged... in which case it won't work, even if it's plugged back in.
				boolean isOn = AdvancedControllerActionFactory.tryToTurnOnAdvancedController(qupath);
				if (isOn)
					logger.info("Advanced controllers turned ON");
				else
					logger.debug("No advanced controllers found - try plugging one in and restarting QuPath if required");
			} catch (Exception e) {
				logger.error("Unable to load advanced controller support");
				logger.debug("{}", e);
			}
		}
		
		// Add a listener to handle property changes
		requestAdvancedControllersProperty().addListener((v, o, n) -> {
			if (n) {
				if (AdvancedControllerActionFactory.tryToTurnOnAdvancedController(qupath)) {
					Dialogs.showInfoNotification("Advanced controllers", "Advanced controllers now turned on");
				} else {
					Dialogs.showErrorNotification("Advanced controller error", "No advanced controllers found - try plugging one in and restarting QuPath if required");
				}
			} else {
				Dialogs.showInfoNotification("Advanced controllers", "Advanced controllers will be turned off whenever QuPath is restarted");
			}
		});
	}

	@Override
	public String getName() {
		return "Advanced controllers extension";
	}

	@Override
	public String getDescription() {
		String description = "Add support for advanced input controllers (e.g. 3D mice for slide navigation) using JInput - https://java.net/projects/jinput";
		return description;
//		if (isOn)
//			return description + "\n(Currently on)";
//		return description + "\n(Currently off)";
	}

}
