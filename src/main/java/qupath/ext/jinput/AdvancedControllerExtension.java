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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.BiPredicate;

import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import qupath.fx.prefs.controlsfx.PropertyItemBuilder;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.QuPathExtension;

import qupath.lib.gui.panes.PreferencePane;
import qupath.lib.gui.prefs.PathPrefs;

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
	private final static ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.jinput.strings");
	private static final Logger logger = LoggerFactory.getLogger(AdvancedControllerExtension.class);

	// Request attempting to load 3D mouse support... needs to be restarted & the mouse plugged in to take effect
	// (And adds ~0.7s to startup time on test Mac Pro)
	private static final BooleanProperty requestAdvancedControllers = PathPrefs.createPersistentPreference("requestAdvancedControllers", true);
	private static final BooleanProperty invertControllerScrolling = PathPrefs.createPersistentPreference("invertControllerScrolling", false);
	private final static String EXTENSION_NAME = resources.getString("extension.title");
	private final static String EXTENSION_DESCRIPTION = resources.getString("extension.description");
	private final static String EXTENSION_QUPATH_VERSION = resources.getString("extension.qupath.version");

	public static BooleanProperty requestAdvancedControllersProperty() {
		return requestAdvancedControllers;
	}

	public static boolean getRequestAdvancedControllers() {
		return requestAdvancedControllers.get();
	}

	public static void setRequestAdvancedControllers(boolean request) {
		requestAdvancedControllers.set(request);
	}
	
	public static BooleanProperty invertControllerScrollingProperty() {
		return invertControllerScrolling;
	}

	public static boolean getInvertControllerScrolling() {
		return invertControllerScrolling.get();
	}

	public static void setInvertControllerScrolling(boolean request) {
		invertControllerScrolling.set(request);
	}
	
	private static boolean alreadyInstalled = false;

	private static boolean nativeLibraryLoaded = false;

	static {
		try {
			nativeLibraryLoaded = loadNativeLibrary();
			if (nativeLibraryLoaded)
				logger.debug("Native library loaded");
			else
				logger.debug("Unable to preload JInput native library (I couldn't find it)");
		} catch (Throwable t) {
			logger.warn("Unable to preload JInput native library: " + t.getLocalizedMessage(), t);
		}
	}
	
    @Override
    public GitHubRepo getRepository() {
        return GitHubRepo.create("Advanced Control QuPath Extension", "zindy", "qupath-extension-input");
    }

	@Override
	public void installExtension(QuPathGUI qupath) {
		if (alreadyInstalled) {
			logger.warn("Extension already installed!");
			return;
		}
		try {
			loadNativeLibrary();
		} catch (Throwable t) {
			logger.warn("Unable to preload JInput native library: " + t.getLocalizedMessage(), t);
			return;
		}
		alreadyInstalled = true;
		addMenuItem(qupath);

		// Add preference
		// todo: move me
		PreferencePane panel = qupath.getPreferencePane();
		panel.getPropertySheet().getItems().add(
				new PropertyItemBuilder<>(
						requestAdvancedControllersProperty(),
						Boolean.class)
				.bundle("qupath.ext.jinput.strings")
				.key("menu.title")
				.category(resources.getString("extension.title"))
				.build()
		);

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
				logger.error("Unable to load advanced controller support", e);
			}
		}

		// Add a listener to handle property changes
		// todo: notifs redundant
		requestAdvancedControllersProperty().addListener((v, o, n) -> {
			if (n) {
				if (AdvancedControllerActionFactory.tryToTurnOnAdvancedController(qupath)) {
					Dialogs.showInfoNotification(
							resources.getString("extension.title"),
							resources.getString("notif.extensionOn"));
				} else {
					Dialogs.showErrorNotification(
							resources.getString("extension.title"),
							resources.getString("notif.noControllers"));
				}
			} else {
				Dialogs.showInfoNotification(
						resources.getString("extension.title"),
						resources.getString("notif.extensionOff")
				);
			}
		});

		// todo: move me (parsed items?)
		panel.getPropertySheet().getItems().add(
				new PropertyItemBuilder<>(
						invertControllerScrollingProperty(),
						Boolean.class)
						.bundle("qupath.ext.jinput.strings")
						.key("invert")
						.category(resources.getString("extension.title"))
						.build()
		);

		// Try to turn on controllers, if required
		if (getInvertControllerScrolling()) {
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
				logger.error("Unable to load advanced controller support", e);
			}
		}

		// Add a listener to handle property changes
		// todo: notifs redundant
		invertControllerScrollingProperty().addListener((v, o, n) -> {
			if (n) {
                Dialogs.showInfoNotification(resources.getString("extension.title"),
						resources.getString("notif.inverted"));
			} else {
				Dialogs.showInfoNotification(
						resources.getString("extension.title"),
						resources.getString("notif.nonInverted"));
			}
            //invertControllerScrolling.set(n);
            setInvertControllerScrolling(n);
		});
	}

	private void addMenuItem(QuPathGUI qupath) {
		var menu = qupath.getMenu("Extensions>" + EXTENSION_NAME, true);
		MenuItem menuItem = new MenuItem(resources.getString("extension.title"));
		menuItem.setOnAction(e -> {
			// todo: if one controller is found
			// todo: make a big stage/scene/pane
			// todo: loop through all controls
			// todo: create either a digital or analog visual representation

			Stage stage = new Stage();
			BorderPane pane = new BorderPane();
			FlowPane flowPane = new FlowPane();
			Scene scene = new Scene(flowPane);
			stage.setScene(scene);

			var controller = AdvancedControllerActionFactory.getChangeListener().getController().getController();
			var components = controller.getComponents();
			for (var component: components) {
				flowPane.getChildren().add(new ComponentVisualisation(component));
			}
			stage.show();
		});
		menu.getItems().add(menuItem);
	}

	/**
	 * Try to load native library from the extension jar.
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws SecurityException
	 * @throws IllegalArgumentException
	 */
	private static boolean loadNativeLibrary() throws URISyntaxException, IOException, SecurityException, IllegalArgumentException {

		if (nativeLibraryLoaded) {
			logger.info("Native library already loaded, skipping");
			return true;
		}

		URL url = AdvancedControllerExtension.class.getClassLoader().getResource("natives");
		logger.debug("JInput url: {}", url);
		if (url == null)
			return false;
		URI uri = url.toURI();
		Path tempDirPath = null;

		if (uri.getScheme().equals("jar")) {
			try (var fs = FileSystems.newFileSystem(uri, Map.of())) {
				var pathRoot = fs.getPath("natives");
				tempDirPath = extractLibs(pathRoot);
			}
		} else {
			// FIXME Not sure what to put here...
			//path = Files.find(Paths.get(uri), 1, createMatcher()).findFirst().orElse(null);
			return false;
		}

		if (Files.isDirectory(tempDirPath)) {
			logger.debug("Setting {} as the \"net.java.games.input.librarypath\"", tempDirPath);
		
			//For jinput, we need to set a system variable
			System.setProperty("net.java.games.input.librarypath", tempDirPath.toAbsolutePath().toString());
			
			//Not sure if this is going to help (we have more than one, and JInput does its own initialisation)
			//System.load(path.toAbsolutePath().toString());
			
			// Try to update for the providers we use
			/*
			logger.trace("Updating cocoa");
			setLoaded(CocoaProvider.class);
			logger.trace("Updating xinput");
			setLoaded(XinputProvider.class);
			logger.trace("Updating wintab");
			setLoaded(WintabProvider.class);
			*/

			return true;
		} else {
			logger.debug("Path is not a directory: {}", tempDirPath);
			return false;
		}
	}
	
	/**
	 * Extract native library to a temp file.
	 * @param pathRoot
	 * @return
	 * @throws IOException
	 */
	private static Path extractLibs(Path pathRoot) throws IOException {
		List<Path> fileList;
		try (var find = Files.find(pathRoot, 1, createMatcher())) {
			fileList = find.toList();
		}

        if (fileList.isEmpty()) {
			logger.debug("Could not find any compatible native files in the JAR");
			return null;
		}

		Path tempDir = Files.createTempDirectory("qupath-");
		tempDir.toFile().deleteOnExit();
		logger.debug("Extract native libraries to: {}", tempDir);

		for (Path path : fileList) {
			logger.debug("Extracting: {}", path);
			Path tempFile = tempDir.resolve(pathRoot.relativize(path).toString());
			logger.trace("Requesting delete on exit");
			tempFile.toFile().deleteOnExit();
			logger.debug("Copying {} to {}", path, tempFile);
			Files.copy(path, tempFile);
		}

		return tempDir;
	}

	private static BiPredicate<Path, BasicFileAttributes> createMatcher() {
		if (GeneralTools.isMac())
			return (p, a) -> matchLib(p, a, ".jnilib", ".dylib");
		if (GeneralTools.isWindows())
			return (p, a) -> matchLib(p, a, "64.dll");
		if (GeneralTools.isLinux())
			return (p, a) -> matchLib(p, a, "64.so");
		return (p, a) -> false;
	}

	private static boolean matchLib(Path path, BasicFileAttributes attr, String... exts) {
		if (attr.isDirectory())
			return false;
		var name = path.getFileName().toString().toLowerCase();
		logger.trace("Checking name: {} against {}", name, Arrays.asList(exts));
		if (!name.startsWith("jinput") && !name.startsWith("libjinput"))
			return false;
		for (var ext : exts) {
			if (name.endsWith(ext))
				return true;
		}
		return false;
	}

	@Override
	public String getName() {
		return EXTENSION_NAME;
	}

	@Override
	public String getDescription() {
        return EXTENSION_DESCRIPTION;
	}

}
