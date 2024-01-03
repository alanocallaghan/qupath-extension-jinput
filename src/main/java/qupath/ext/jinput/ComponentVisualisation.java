package qupath.ext.jinput;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import net.java.games.input.Component;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class ComponentVisualisation extends HBox {
    private static final Logger logger = LoggerFactory.getLogger(ComponentVisualisation.class);
    int heartbeat = 25;

    Timeline timeline;

    public ComponentVisualisation(Component component) {
        super();
        this.setAlignment(Pos.CENTER_LEFT);
        this.setPadding(new Insets(2));
        String labelText = component.getName();
        if (component.isAnalog()) {
            labelText += " (analog)";
        }
        Label label = new Label(labelText + ": ");
        this.getChildren().add(label);
        TextField text = new TextField();
        text.setText(String.valueOf(component.getPollData()));
        text.setEditable(false);
        this.getChildren().add(text);
        // todo: analog vs digital components
        // todo: digital components with more than two values (eg D-pad)
        timeline = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        actionEvent -> {
                            Platform.runLater(() -> text.textProperty().set(String.valueOf(component.getPollData())));
                        }
                ),
                new KeyFrame(
                        Duration.millis(heartbeat)
                )
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }
}
