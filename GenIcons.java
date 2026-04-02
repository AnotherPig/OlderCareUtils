import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class GenIcons {
    public static void main(String[] args) throws IOException {
        // Test: create a simple 48x48 red square to verify PNG generation works
        BufferedImage testImg = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = testImg.createGraphics();
        g.setColor(new Color(0xFF0000));
        g.fillRect(10, 10, 28, 28);
        g.dispose();
        ImageIO.write(testImg, "PNG", new File("test_icon.png"));
        System.out.println("Created test_icon.png");

        // Now create old man icons
        createOldManIcons();
    }

    static void createOldManIcons() throws IOException {
        int[] sizes = {48, 72, 96, 144, 192};
        String[] folders = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};

        Color COLOR_BG = new Color(0xF5, 0xF0, 0xE8);
        Color COLOR_SKIN = new Color(0xF5, 0xD0, 0xA9);
        Color COLOR_HAIR = new Color(0x4A, 0x4A, 0x4A);
        Color COLOR_GLASS = new Color(0x2C, 0x2C, 0x2C);
        Color COLOR_GLASS_SHINE = new Color(0xFF, 0xFF, 0xFF);
        Color COLOR_NOSE = new Color(0xE8, 0xC0, 0x9A);
        Color COLOR_MOUTH = new Color(0x8B, 0x69, 0x14);
        Color COLOR_WRINKLE = new Color(0xDD, 0xD0, 0xB8);

        Color[] COLORS = {COLOR_BG, COLOR_HAIR, COLOR_SKIN, COLOR_GLASS,
                         COLOR_GLASS_SHINE, COLOR_NOSE, COLOR_WRINKLE, COLOR_MOUTH};

        int[][] pixels = {
            {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
            {0,0,0,1,1,1,1,1,1,1,1,1,1,0,0,0},
            {0,0,1,1,2,2,2,2,2,2,2,2,1,1,0,0},
            {0,0,1,1,2,2,2,2,2,2,2,2,1,1,0,0},
            {0,0,1,1,2,2,2,2,2,2,2,2,1,1,0,0},
            {0,0,1,1,2,2,3,3,3,3,3,3,1,1,0,0},
            {0,0,1,1,2,2,3,3,4,3,3,3,1,1,0,0},
            {0,0,1,1,2,2,3,3,3,3,3,3,1,1,0,0},
            {0,0,1,1,2,2,2,2,5,2,2,2,1,1,0,0},
            {0,0,1,1,2,2,2,7,7,7,7,7,2,1,1,0,0},
            {0,0,0,0,0,0,0,7,6,7,7,7,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}
        };

        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i];
            String folder = folders[i];

            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();

            // Fill background
            g.setColor(COLORS[0]);
            g.fillRect(0, 0, size, size);

            int pixelSize = Math.max(1, size / 16);
            int offset = (size - (16 * pixelSize)) / 2;

            // Draw pixels
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int colorIndex = pixels[y][x];
                    if (colorIndex > 0 && colorIndex < COLORS.length) {
                        g.setColor(COLORS[colorIndex]);
                        g.fillRect(offset + x * pixelSize, offset + y * pixelSize, pixelSize, pixelSize);
                    }
                }
            }
            g.dispose();

            // Save
            File dir = new File("app/src/main/res/mipmap-" + folder);
            dir.mkdirs();
            File file = new File(dir, "ic_launcher.png");
            ImageIO.write(img, "PNG", file);
            System.out.println("Created: " + file.getPath() + " (" + size + "x" + size + ")");

            // Round version
            img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g.setColor(COLORS[0]);
            g.fillOval(0, 0, size, size);

            BufferedImage square = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = square.createGraphics();
            sg.setColor(COLORS[0]);
            sg.fillRect(0, 0, size, size);
            int ps = Math.max(1, size / 16);
            int off = (size - (16 * ps)) / 2;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int ci = pixels[y][x];
                    if (ci > 0 && ci < COLORS.length) {
                        sg.setColor(COLORS[ci]);
                        sg.fillRect(off + x * ps, off + y * ps, ps, ps);
                    }
                }
            }
            sg.dispose();

            g.setComposite(AlphaComposite.SrcIn);
            g.drawImage(square, 0, 0, null);
            g.dispose();

            file = new File(dir, "ic_launcher_round.png");
            ImageIO.write(img, "PNG", file);
            System.out.println("Created: " + file.getPath());
        }
    }
}
