package sk.arsi.corset.resize;

import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and edits SVG path data strings.
 * Supports M/m, L/l, C/c, Z/z commands.
 * Can extract endpoint nodes and modify their coordinates.
 */
public final class SvgPathEditor {

    /**
     * Represents a path command with its type and coordinates.
     */
    private static final class PathCommand {
        final char type; // M, m, L, l, C, c, Z, z
        final double[] coords;

        PathCommand(char type, double[] coords) {
            this.type = type;
            this.coords = coords;
        }
    }

    /**
     * Parse SVG path data into commands.
     */
    public static List<PathCommand> parse(String d) {
        if (d == null || d.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty path data");
        }

        List<PathCommand> commands = new ArrayList<>();
        
        // Pattern to match path commands: letter followed by numbers
        // Handles negative numbers, decimals, scientific notation
        Pattern pattern = Pattern.compile("([MmLlCcZzHhVvSsQqTtAa])([^MmLlCcZzHhVvSsQqTtAa]*)");
        Matcher matcher = pattern.matcher(d);

        while (matcher.find()) {
            char cmd = matcher.group(1).charAt(0);
            String coordsStr = matcher.group(2).trim();

            if (cmd == 'Z' || cmd == 'z') {
                commands.add(new PathCommand(cmd, new double[0]));
                continue;
            }

            double[] coords = parseCoordinates(coordsStr);
            commands.add(new PathCommand(cmd, coords));
        }

        return commands;
    }

    /**
     * Parse coordinate string into array of doubles.
     */
    private static double[] parseCoordinates(String coordsStr) {
        if (coordsStr == null || coordsStr.trim().isEmpty()) {
            return new double[0];
        }

        // Split by comma or whitespace, handle multiple delimiters
        String[] parts = coordsStr.trim().split("[,\\s]+");
        List<Double> coords = new ArrayList<>();
        
        for (String part : parts) {
            if (!part.isEmpty()) {
                coords.add(Double.parseDouble(part));
            }
        }

        double[] result = new double[coords.size()];
        for (int i = 0; i < coords.size(); i++) {
            result[i] = coords.get(i);
        }
        return result;
    }

    /**
     * Extract endpoint nodes from path data.
     * Returns positions where path segments END (the target points).
     * For M/L: the point itself.
     * For C: the last point (endpoint, not control points).
     */
    public static List<Pt> extractEndpoints(String d) {
        List<PathCommand> commands = parse(d);
        List<Pt> endpoints = new ArrayList<>();
        
        double currentX = 0.0;
        double currentY = 0.0;
        double startX = 0.0;
        double startY = 0.0;

        for (PathCommand cmd : commands) {
            char type = cmd.type;
            double[] coords = cmd.coords;

            switch (type) {
                case 'M': // absolute moveto
                    if (coords.length >= 2) {
                        currentX = coords[0];
                        currentY = coords[1];
                        startX = currentX;
                        startY = currentY;
                        endpoints.add(new Pt(currentX, currentY));
                    }
                    break;
                
                case 'm': // relative moveto
                    if (coords.length >= 2) {
                        currentX += coords[0];
                        currentY += coords[1];
                        startX = currentX;
                        startY = currentY;
                        endpoints.add(new Pt(currentX, currentY));
                    }
                    break;
                
                case 'L': // absolute lineto
                    if (coords.length >= 2) {
                        currentX = coords[0];
                        currentY = coords[1];
                        endpoints.add(new Pt(currentX, currentY));
                    }
                    break;
                
                case 'l': // relative lineto
                    if (coords.length >= 2) {
                        currentX += coords[0];
                        currentY += coords[1];
                        endpoints.add(new Pt(currentX, currentY));
                    }
                    break;
                
                case 'C': // absolute cubic bezier
                    if (coords.length >= 6) {
                        // coords[0,1] = control1, coords[2,3] = control2, coords[4,5] = endpoint
                        currentX = coords[4];
                        currentY = coords[5];
                        endpoints.add(new Pt(currentX, currentY));
                    }
                    break;
                
                case 'c': // relative cubic bezier
                    if (coords.length >= 6) {
                        currentX += coords[4];
                        currentY += coords[5];
                        endpoints.add(new Pt(currentX, currentY));
                    }
                    break;
                
                case 'Z':
                case 'z': // closepath
                    currentX = startX;
                    currentY = startY;
                    // Don't add endpoint for Z - it just closes to start
                    break;
            }
        }

        return endpoints;
    }

    /**
     * Modify coordinates of a specific endpoint node in path data.
     * 
     * @param d original path data
     * @param endpointIndex which endpoint to modify (0-based)
     * @param deltaX change in X coordinate
     * @param deltaY change in Y coordinate
     * @return modified path data (converted to absolute commands for simplicity)
     */
    public static String modifyEndpoint(String d, int endpointIndex, double deltaX, double deltaY) {
        List<PathCommand> commands = parse(d);
        
        double currentX = 0.0;
        double currentY = 0.0;
        double startX = 0.0;
        double startY = 0.0;
        int endpointCounter = 0;

        StringBuilder result = new StringBuilder();

        for (PathCommand cmd : commands) {
            char type = cmd.type;
            double[] coords = cmd.coords;

            switch (type) {
                case 'M':
                case 'm': {
                    if (coords.length >= 2) {
                        double x, y;
                        if (type == 'M') {
                            x = coords[0];
                            y = coords[1];
                        } else {
                            x = currentX + coords[0];
                            y = currentY + coords[1];
                        }
                        
                        if (endpointCounter == endpointIndex) {
                            x += deltaX;
                            y += deltaY;
                        }
                        
                        result.append("M ").append(x).append(" ").append(y).append(" ");
                        currentX = x;
                        currentY = y;
                        startX = x;
                        startY = y;
                        endpointCounter++;
                    }
                    break;
                }
                
                case 'L':
                case 'l': {
                    if (coords.length >= 2) {
                        double x, y;
                        if (type == 'L') {
                            x = coords[0];
                            y = coords[1];
                        } else {
                            x = currentX + coords[0];
                            y = currentY + coords[1];
                        }
                        
                        if (endpointCounter == endpointIndex) {
                            x += deltaX;
                            y += deltaY;
                        }
                        
                        result.append("L ").append(x).append(" ").append(y).append(" ");
                        currentX = x;
                        currentY = y;
                        endpointCounter++;
                    }
                    break;
                }
                
                case 'C':
                case 'c': {
                    if (coords.length >= 6) {
                        double x1, y1, x2, y2, x, y;
                        if (type == 'C') {
                            x1 = coords[0];
                            y1 = coords[1];
                            x2 = coords[2];
                            y2 = coords[3];
                            x = coords[4];
                            y = coords[5];
                        } else {
                            x1 = currentX + coords[0];
                            y1 = currentY + coords[1];
                            x2 = currentX + coords[2];
                            y2 = currentY + coords[3];
                            x = currentX + coords[4];
                            y = currentY + coords[5];
                        }
                        
                        if (endpointCounter == endpointIndex) {
                            x += deltaX;
                            y += deltaY;
                        }
                        
                        result.append("C ")
                              .append(x1).append(" ").append(y1).append(" ")
                              .append(x2).append(" ").append(y2).append(" ")
                              .append(x).append(" ").append(y).append(" ");
                        currentX = x;
                        currentY = y;
                        endpointCounter++;
                    }
                    break;
                }
                
                case 'Z':
                case 'z':
                    result.append("Z ");
                    currentX = startX;
                    currentY = startY;
                    break;
            }
        }

        return result.toString().trim();
    }

    /**
     * Find the index of the endpoint with minimum Y coordinate.
     */
    public static int findMinYEndpoint(String d) {
        List<Pt> endpoints = extractEndpoints(d);
        if (endpoints.isEmpty()) {
            return -1;
        }

        int minIndex = 0;
        double minY = endpoints.get(0).getY();
        
        for (int i = 1; i < endpoints.size(); i++) {
            double y = endpoints.get(i).getY();
            if (y < minY) {
                minY = y;
                minIndex = i;
            }
        }
        
        return minIndex;
    }

    /**
     * Find the index of the endpoint with maximum Y coordinate.
     */
    public static int findMaxYEndpoint(String d) {
        List<Pt> endpoints = extractEndpoints(d);
        if (endpoints.isEmpty()) {
            return -1;
        }

        int maxIndex = 0;
        double maxY = endpoints.get(0).getY();
        
        for (int i = 1; i < endpoints.size(); i++) {
            double y = endpoints.get(i).getY();
            if (y > maxY) {
                maxY = y;
                maxIndex = i;
            }
        }
        
        return maxIndex;
    }

    /**
     * Find indices of leftmost and rightmost endpoints (by X coordinate).
     * Returns [leftIndex, rightIndex].
     */
    public static int[] findLeftRightEndpoints(String d) {
        List<Pt> endpoints = extractEndpoints(d);
        if (endpoints.isEmpty()) {
            return new int[]{-1, -1};
        }

        int leftIndex = 0;
        int rightIndex = 0;
        double minX = endpoints.get(0).getX();
        double maxX = endpoints.get(0).getX();
        
        for (int i = 1; i < endpoints.size(); i++) {
            double x = endpoints.get(i).getX();
            if (x < minX) {
                minX = x;
                leftIndex = i;
            }
            if (x > maxX) {
                maxX = x;
                rightIndex = i;
            }
        }
        
        return new int[]{leftIndex, rightIndex};
    }

    /**
     * Find indices of two endpoints with minimum Y (top edge endpoints).
     * Among all minY endpoints, returns leftmost and rightmost.
     */
    public static int[] findTopEdgeEndpoints(String d) {
        List<Pt> endpoints = extractEndpoints(d);
        if (endpoints.isEmpty()) {
            return new int[]{-1, -1};
        }

        // Find minimum Y
        double minY = endpoints.get(0).getY();
        for (int i = 1; i < endpoints.size(); i++) {
            double y = endpoints.get(i).getY();
            if (y < minY) {
                minY = y;
            }
        }

        // Find all endpoints with minY, track leftmost and rightmost
        int leftIndex = -1;
        int rightIndex = -1;
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < endpoints.size(); i++) {
            Pt p = endpoints.get(i);
            if (Math.abs(p.getY() - minY) < 1e-6) {
                double x = p.getX();
                if (x < minX) {
                    minX = x;
                    leftIndex = i;
                }
                if (x > maxX) {
                    maxX = x;
                    rightIndex = i;
                }
            }
        }

        return new int[]{leftIndex, rightIndex};
    }
}
