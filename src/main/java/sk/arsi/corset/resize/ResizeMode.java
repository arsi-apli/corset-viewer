package sk.arsi.corset.resize;

public enum ResizeMode {
    DISABLED("Disabled"),
    GLOBAL("Global"),
    TOP("Top"),
    BOTTOM("Bottom"),
    HIP("Hip (CD-DC lower)"),
    HIP1("Hip (BC-CB,CD-DC,DE-ED lower)"),
    RIB("Rib (CD-DC upper)"),
    RIB1("Rib (BC-CB,CD-DC,DE-ED upper)"),
    WAIST("Waist");

    private String description;

    private ResizeMode(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return description;
    }

}
