package qupath.ext.jinput;

import net.java.games.input.Controller;

public interface QuPathAdvancedController {

        String getControllerName();

        /**
         * Return true if the update is successful and the controller remains in a valid state, false otherwise.
         *
         * If false is returned, then the controller may be stopped.
         *
         * @return
         */
        boolean updateViewer();

        Controller getController();
}
