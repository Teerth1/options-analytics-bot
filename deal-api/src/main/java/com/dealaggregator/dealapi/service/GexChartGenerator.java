package com.dealaggregator.dealapi.service;

import com.dealaggregator.dealapi.service.GexService.GexResult;
import com.dealaggregator.dealapi.service.GexService.GexRow;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class GexChartGenerator {

    // Canvas Settings
    private static final int WIDTH = 800;   // Total width of the image
    private static final int ROW_HEIGHT = 20; // Height per strike row
    private static final int PADDING = 40;  // Padding on sides
    private static final int MARGIN_TOP = 60; // Space for header
    private static final int MARGIN_BOTTOM = 60; 
    
    // Colors mimicking the dark dashboard theme
    private static final Color BG_COLOR = new Color(17, 17, 17);         // #111111 Dark background
    private static final Color GRID_COLOR = new Color(50, 50, 50);       // Subtle grid lines
    private static final Color TEXT_COLOR = new Color(200, 200, 200);    // Light gray text
    private static final Color CALL_GEX_COLOR = new Color(138, 43, 226); // Purple for Positive (Calls)
    private static final Color PUT_GEX_COLOR = new Color(255, 140, 0);   // Orange for Negative (Puts)
    private static final Color SPOT_LINE_COLOR = new Color(0, 191, 255); // Teal line for current spot

    /**
     * Generates a PNG image of the GEX profile and returns it as a byte array
     * to be uploaded directly to Discord.
     */
    public byte[] generateChart(GexResult result) {
        try {
            // Find max GEX absolute value for X-axis scaling
            double maxGexAbs = 0;
            for (GexRow row : result.rows) {
                maxGexAbs = Math.max(maxGexAbs, Math.abs(row.netGex));
            }
            maxGexAbs *= 1.1; // 10% padding so bars don't touch edges

            // Dynamic height based on rows
            int numStrikes = result.rows.size();
            int height = MARGIN_TOP + MARGIN_BOTTOM + (numStrikes * ROW_HEIGHT);

            // Create canvas
            BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = image.createGraphics();

            // Anti-aliasing
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Background
            g2.setColor(BG_COLOR);
            g2.fillRect(0, 0, WIDTH, height);

            // Center zero line (X-axis origin)
            int centerX = WIDTH / 2;
            g2.setColor(GRID_COLOR);
            g2.drawLine(centerX, MARGIN_TOP, centerX, height - MARGIN_BOTTOM);

            // Font setup
            g2.setFont(new Font("Consolas", Font.BOLD, 12));

            // Setup scaling: (pixels from center to padding edge) / maxGEX
            double pixelsPerGex = (centerX - PADDING) / maxGexAbs;

            // Draw bars and labels
            int y = MARGIN_TOP;
            for (GexRow row : result.rows) {
                // Determine bar width
                int barWidth = (int) (Math.abs(row.netGex) * pixelsPerGex);

                // Bar height is slightly smaller than row height for spacing
                int barHeight = ROW_HEIGHT - 4;
                int barY = y + 2; 

                if (row.netGex >= 0) {
                    // Call GEX -> Purple -> Right side
                    g2.setColor(CALL_GEX_COLOR);
                    g2.fillRect(centerX, barY, barWidth, barHeight);
                } else {
                    // Put GEX -> Orange -> Left side
                    g2.setColor(PUT_GEX_COLOR);
                    g2.fillRect(centerX - barWidth, barY, barWidth, barHeight);
                }

                // Draw strike price label on the left edge
                g2.setColor(TEXT_COLOR);
                g2.drawString(String.format("%.0f", row.strike), PADDING / 2, y + 14);

                // Overlay spot line across the row if it's the closest strike
                boolean nearSpot = Math.abs(row.strike - result.spotPrice) <= result.spotPrice * 0.001;
                if (nearSpot) {
                    g2.setColor(SPOT_LINE_COLOR);
                    g2.drawLine(PADDING, y + ROW_HEIGHT / 2, WIDTH - PADDING, y + ROW_HEIGHT / 2);
                    g2.drawString(String.format("SPOT: %.2f", result.spotPrice), WIDTH - PADDING * 3, y + 14);
                }

                y += ROW_HEIGHT; // Move down for the next strike row
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
