import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class TestIconGen {
    public static void main(String[] args) throws Exception {
        int size = 48;

        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setColor(new Color(0xF5, 0xF0, 0xE8));
        g.fillRect(0, 0, size, size);

        g.setColor(Color.BLACK);
        int gridSize = 8;
        int cellSize = size / gridSize;
        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                if ((x + y) % 2 == 0) {
                    g.fillRect(x * cellSize, y * cellSize, cellSize, cellSize);
                }
            }
        }

        g.setColor(Color.RED);
        g.fillOval(size/4, size/4, size/2, size/2);

        g.dispose();

        File file = new File("test_pattern.png");
        ImageIO.write(img, "PNG", file);
        System.out.println("Created test_pattern.png, size: " + file.length() + " bytes");
    }
}
