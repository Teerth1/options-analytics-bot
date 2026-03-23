package com.dealaggregator.dealapi.service;

import com.dealaggregator.dealapi.service.GexService.GexResult;
import com.dealaggregator.dealapi.service.GexService.GexRow;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class GexChartGenerator {

    // Canvas Settings
    private static final int WIDTH = 800;   // Total width of the vertical image
    private static final int ROW_HEIGHT = 20; // Height per strike row
    private static final int COLUMN_WIDTH = 30; // Width per strike column (horizontal mode)
    private static final int HORIZONTAL_HEIGHT = 600; // Fixed height for horizontal chart
    private static final int PADDING = 40;  // Padding on sides
    private static final int MARGIN_TOP = 60; // Space for header
    private static final int MARGIN_BOTTOM = 60; 
    
    // Colors mimicking the dark dashboard theme
    private static final Color BG_COLOR = new Color(17, 17, 17);         
    private static final Color GRID_COLOR = new Color(50, 50, 50);       
    private static final Color TEXT_COLOR = new Color(200, 200, 200);    
    private static final Color CALL_GEX_COLOR = new Color(138, 43, 226); 
    private static final Color PUT_GEX_COLOR = new Color(255, 140, 0);   
    private static final Color SPOT_LINE_COLOR = new Color(0, 191, 255); 

    /**
     * Entry method. Routes to either horizontal or vertical renderer.
     */
    public byte[] generateChart(GexResult result, boolean isHorizontal) {
        if (isHorizontal) {
            return generateHorizontalChart(result);
        } else {
            return generateVerticalChart(result);
        }
    }

    private byte[] generateHorizontalChart(GexResult result) {
        try {
            double range = result.spotPrice * 0.020;
            java.util.List<GexRow> filteredRows = result.rows.stream()
                .filter(r -> Math.abs(r.strike - result.spotPrice) <= range)
                .collect(java.util.stream.Collectors.toList());

            if (filteredRows.isEmpty()) {
                filteredRows = result.rows; 
            }

            // Reverse to ensure ascending order for X-axis (lowest strike -> highest strike)
            // assuming result.rows is strictly given highest->lowest
            if (filteredRows.size() > 1 && filteredRows.get(0).strike > filteredRows.get(filteredRows.size() - 1).strike) {
                java.util.Collections.reverse(filteredRows); 
            }

            double maxGexAbs = 0;
            for (GexRow row : filteredRows) {
                maxGexAbs = Math.max(maxGexAbs, Math.abs(row.netGex));
            }
            maxGexAbs *= 1.1; 

            int numStrikes = filteredRows.size();
            int width = PADDING * 2 + (numStrikes * COLUMN_WIDTH);
            int height = HORIZONTAL_HEIGHT;

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = image.createGraphics();

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2.setColor(BG_COLOR);
            g2.fillRect(0, 0, width, height);

            int centerY = height / 2;
            g2.setColor(GRID_COLOR);
            g2.drawLine(PADDING, centerY, width - PADDING, centerY);

            g2.setFont(new Font("Consolas", Font.BOLD, 12));

            double pixelsPerGex = (double)(centerY - MARGIN_TOP) / maxGexAbs;

            GexRow closestRow = null;
            double minDist = Double.MAX_VALUE;
            for (GexRow r : filteredRows) {
                double dist = Math.abs(r.strike - result.spotPrice);
                if (dist < minDist) {
                    minDist = dist;
                    closestRow = r;
                }
            }

            int x = PADDING;

            for (GexRow row : filteredRows) {
                int barHeight = (int) (Math.abs(row.netGex) * pixelsPerGex);
                int barWidth = COLUMN_WIDTH - 4;
                int barX = x + 2; 

                if (row.netGex >= 0) {
                    g2.setColor(CALL_GEX_COLOR);
                    g2.fillRect(barX, centerY - barHeight, barWidth, barHeight);
                } else {
                    g2.setColor(PUT_GEX_COLOR);
                    g2.fillRect(barX, centerY, barWidth, barHeight);
                }

                // Draw strike label rotated vertically
                g2.setColor(TEXT_COLOR);
                String strikeStr = String.format("%.0f", row.strike);
                
                AffineTransform orig = g2.getTransform();
                g2.translate(x + 10, height - 15);
                g2.rotate(-Math.PI / 4); // 45 degrees
                g2.drawString(strikeStr, 0, 0);
                g2.setTransform(orig);

                if (row == closestRow) {
                    g2.setColor(SPOT_LINE_COLOR);
                    g2.drawLine(x + COLUMN_WIDTH / 2, MARGIN_TOP, x + COLUMN_WIDTH / 2, height - MARGIN_BOTTOM + 20);
                    g2.drawString(String.format("SPOT: %.2f", result.spotPrice), x + 10, MARGIN_TOP - 10);
                }

                x += COLUMN_WIDTH; 
            }

            g2.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private byte[] generateVerticalChart(GexResult result) {
        try {
            double range = result.spotPrice * 0.020;
            java.util.List<GexRow> filteredRows = result.rows.stream()
                .filter(r -> Math.abs(r.strike - result.spotPrice) <= range)
                .collect(java.util.stream.Collectors.toList());

            if (filteredRows.isEmpty()) {
                filteredRows = result.rows; 
            }

            double maxGexAbs = 0;
            for (GexRow row : filteredRows) {
                maxGexAbs = Math.max(maxGexAbs, Math.abs(row.netGex));
            }
            maxGexAbs *= 1.1; 

            int numStrikes = filteredRows.size();
            int height = MARGIN_TOP + MARGIN_BOTTOM + (numStrikes * ROW_HEIGHT);

            BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = image.createGraphics();

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2.setColor(BG_COLOR);
            g2.fillRect(0, 0, WIDTH, height);

            int centerX = WIDTH / 2;
            g2.setColor(GRID_COLOR);
            g2.drawLine(centerX, MARGIN_TOP, centerX, height - MARGIN_BOTTOM);

            g2.setFont(new Font("Consolas", Font.BOLD, 12));

            double pixelsPerGex = (centerX - PADDING) / maxGexAbs;

            GexRow closestRow = null;
            double minDist = Double.MAX_VALUE;
            for (GexRow r : filteredRows) {
                double dist = Math.abs(r.strike - result.spotPrice);
                if (dist < minDist) {
                    minDist = dist;
                    closestRow = r;
                }
            }

            int y = MARGIN_TOP;
            for (GexRow row : filteredRows) {
                int barWidth = (int) (Math.abs(row.netGex) * pixelsPerGex);
                int barHeight = ROW_HEIGHT - 4;
                int barY = y + 2; 

                if (row.netGex >= 0) {
                    g2.setColor(CALL_GEX_COLOR);
                    g2.fillRect(centerX, barY, barWidth, barHeight);
                } else {
                    g2.setColor(PUT_GEX_COLOR);
                    g2.fillRect(centerX - barWidth, barY, barWidth, barHeight);
                }

                g2.setColor(TEXT_COLOR);
                g2.drawString(String.format("%.0f", row.strike), PADDING / 2, y + 14);

                if (row == closestRow) {
                    g2.setColor(SPOT_LINE_COLOR);
                    g2.drawLine(PADDING, y + ROW_HEIGHT / 2, WIDTH - PADDING, y + ROW_HEIGHT / 2);
                    g2.drawString(String.format("SPOT: %.2f", result.spotPrice), WIDTH - PADDING * 3, y + 14);
                }

                y += ROW_HEIGHT; 
            }

            g2.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
