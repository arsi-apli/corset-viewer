package sk.arsi.corset.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Panel identifier (A..Z). Previously an enum A..F, now dynamic to support A..H
 * etc.
 */
public final class PanelId implements Comparable<PanelId> {

    private final char letter; // 'A'..'Z'

    private PanelId(char letter) {
        char up = Character.toUpperCase(letter);
        if (up < 'A' || up > 'Z') {
            throw new IllegalArgumentException("PanelId must be A..Z, got: " + letter);
        }
        this.letter = up;
    }

    public static PanelId of(char letter) {
        return new PanelId(letter);
    }

    public char letter() {
        return letter;
    }

    public String name() {
        return String.valueOf(letter);
    }

    public PanelId prev() {
        return (letter == 'A') ? null : new PanelId((char) (letter - 1));
    }

    public PanelId next() {
        return (letter == 'Z') ? null : new PanelId((char) (letter + 1));
    }

    public static List<PanelId> rangeInclusive(char maxPanelLetter) {
        char max = Character.toUpperCase(maxPanelLetter);
        if (max < 'A' || max > 'Z') {
            throw new IllegalArgumentException("maxPanelLetter must be A..Z, got: " + maxPanelLetter);
        }
        List<PanelId> out = new ArrayList<>();
        for (char c = 'A'; c <= max; c++) {
            out.add(new PanelId(c));
        }
        return out;
    }

    @Override
    public int compareTo(PanelId o) {
        return Character.compare(this.letter, o.letter);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PanelId)) {
            return false;
        }
        return ((PanelId) o).letter == this.letter;
    }

    @Override
    public int hashCode() {
        return Objects.hash(letter);
    }

    @Override
    public String toString() {
        return name();
    }
}
