package sk.arsi.corset.resize;

/**
 * Resize mode for panel resizing feature.
 */
public enum ResizeMode {
    /**
     * Disabled - no resizing applied.
     */
    DISABLED,
    
    /**
     * Global resize - affects entire panel uniformly.
     */
    GLOBAL,
    
    /**
     * Top resize - affects top portion only.
     * Shifts only the minY point of UP seams and the endpoints of the TOP edge curve.
     * Leaves waist, bottom, and DOWN seams unchanged.
     */
    TOP,
    
    /**
     * Bottom resize - affects bottom portion only (not implemented yet).
     */
    BOTTOM,
    
    /**
     * Hip resize - affects hip area only (not implemented yet).
     */
    HIP
}
