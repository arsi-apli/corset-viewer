package sk.arsi.corset.app;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * Dialog for selecting the max panel letter when metadata is missing.
 */
public final class PanelSelectionDialog extends Dialog<Character> {
    
    private final ToggleGroup toggleGroup;
    
    /**
     * Create a panel selection dialog.
     * 
     * @param detectedMaxPanel the heuristically detected max panel (used as default selection)
     */
    public PanelSelectionDialog(char detectedMaxPanel) {
        setTitle("Select Panel Range");
        setHeaderText("The SVG file does not contain panel count metadata.\nPlease select the panel range:");
        
        // Create radio buttons for common panel ranges
        toggleGroup = new ToggleGroup();
        
        RadioButton rbF = new RadioButton("A–F (6 panels)");
        rbF.setToggleGroup(toggleGroup);
        rbF.setUserData('F');
        
        RadioButton rbG = new RadioButton("A–G (7 panels)");
        rbG.setToggleGroup(toggleGroup);
        rbG.setUserData('G');
        
        RadioButton rbH = new RadioButton("A–H (8 panels)");
        rbH.setToggleGroup(toggleGroup);
        rbH.setUserData('H');
        
        // Set default selection based on detected max panel
        char detectedUpper = Character.toUpperCase(detectedMaxPanel);
        if (detectedUpper == 'G') {
            rbG.setSelected(true);
        } else if (detectedUpper == 'H') {
            rbH.setSelected(true);
        } else {
            // Default to F
            rbF.setSelected(true);
        }
        
        VBox content = new VBox(10, rbF, rbG, rbH);
        content.setPadding(new Insets(20));
        
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Convert the result to Character when OK is pressed
        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                Toggle selected = toggleGroup.getSelectedToggle();
                if (selected != null) {
                    return (Character) selected.getUserData();
                }
            }
            return null;
        });
    }
    
    /**
     * Show the dialog and return the selected max panel letter.
     * 
     * @return Optional containing the selected panel letter, or empty if cancelled
     */
    public Optional<Character> showAndGetResult() {
        return showAndWait();
    }
}
