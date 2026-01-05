package sk.arsi.corset.resize;

/**
 * Resize mode for panel resizing operation.
 */
public enum ResizeMode {
    /**
     * Apply resize to all curves (TOP, WAIST, BOTTOM and all seams).
     */
    GLOBAL,
    
    /**
     * Apply resize only to TOP curve and UP seams (seamToPrevUp, seamToNextUp).
     * Waist remains unchanged.
     */
    TOP,
    
    /**
     * Apply resize only to BOTTOM curve and DOWN seams (seamToPrevDown, seamToNextDown).
     * Waist remains unchanged.
     */
    BOTTOM
}
