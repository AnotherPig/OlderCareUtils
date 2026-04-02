import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class GenBigIcon {
    public static void main(String[] args) throws Exception {
        int[] sizes = {48, 72, 96, 144, 192};
        String[] folders = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};

        Color BG = new Color(0xF5, 0xF0, 0xE8);
        Color SKIN = new Color(0xF5, 0xD0, 0xA9);
        Color HAIR = new Color(0x4A, 0x4A, 0x4A);
        Color GLASS = new Color(0x2C, 0x2C, 0x2C);
        Color MOUTH = new Color(0x8B, 0x69, 0x14);

        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i];
            String folder = folders[i];

            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Background
            g.setColor(BG);
            g.fillRect(0, 0, size, size);

            // Face (large circle)
            int faceSize = (int)(size * 0.7);
            int faceX = (size - faceSize) / 2;
            int faceY = (int)(size * 0.2);
            g.setColor(SKIN);
            g.fillOval(faceX, faceY, faceSize, faceSize);

            // Hair (top arc)
            g.setColor(HAIR);
            g.fillArc(faceX, faceY - faceSize/6, faceSize, faceSize/2, 0, 180);

            // Hair sides
            g.fillRect(faceX - faceSize/10, faceY, faceSize/10, faceSize/3);
            g.fillRect(faceX + faceSize, faceY, faceSize/10, faceSize/3);

            // Glasses (two rectangles)
            int glassSize = faceSize / 3;
            int glassY = faceY + faceSize / 3;
            g.setColor(GLASS);
            g.fillRect(faceX + faceSize/6 - 2, glassY, glassSize + 4, glassSize/2);
            g.fillRect(faceX + faceSize*2/3 - 2, glassY, glassSize + 4, glassSize/2);
            // Bridge
            g.fillRect(faceX + faceSize/2 - 4, glassY + 2, 8, 3);

            // Nose
            g.fillOval(size/2 - 3, faceY + faceSize/2, 6, 8);

            // Mouth (smile)
            g.setColor(MOUTH);
            g.drawArc(faceX + faceSize/4, faceY + faceSize*2/3, faceSize/2, faceSize/4, 180, 180);

            g.dispose();

            File dir = new File("app/src/main/res/mipmap-" + folder);
            dir.mkdirs();
            File file = new File(dir, "ic_launcher.png");
            ImageIO.write(img, "PNG", file);
            System.out.println("Created: " + folder + "/ic_launcher.png (" + size + "x" + size + ", " + file.length() + " bytes)");

            // Round version
            BufferedImage round = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = round.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BG);
            g2.fillOval(0, 0, size, size);
            g2.setComposite(AlphaComposite.SrcIn);
            g2.drawImage(img, 0, 0, null);
            g2.dispose();

            file = new File(dir, "ic_launcher_round.png");
            ImageIO.write(round, "PNG", file);
            System.out.println("Created: " + folder + "/ic_launcher_round.png (" + file.length() + " bytes)");
        }
    }
}
