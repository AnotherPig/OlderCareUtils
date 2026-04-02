import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

fun main() {
    val sizes = listOf(
        "mdpi" to 48,
        "hdpi" to 72,
        "xhdpi" to 96,
        "xxhdpi" to 144,
        "xxxhdpi" to 192
    )

    // Colors
    val COLOR_BG = Color(0xF5, 0xF0, 0xE8)
    val COLOR_SKIN = Color(0xF5, 0xD0, 0xA9)
    val COLOR_HAIR = Color(0x4A, 0x4A, 0x4A)
    val COLOR_GLASS = Color(0x2C, 0x2C, 0x2C)
    val COLOR_GLASS_SHINE = Color(0xFF, 0xFF, 0xFF)
    val COLOR_NOSE = Color(0xE8, 0xC0, 0x9A)
    val COLOR_MOUTH = Color(0x8B, 0x69, 0x14)
    val COLOR_WRINKLE = Color(0xDD, 0xD0, 0xB8)

    // 16x16 pixel grid - 0=bg, 1=hair, 2=skin, 3=glass, 4=glass_shine, 5=nose, 6=wrinkle, 7=mouth
    val pixels = arrayOf(
        intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0),
        intArrayOf(0,0,0,1,1,1,1,1,1,1,1,1,1,0,0,0),
        intArrayOf(0,0,1,1,2,2,2,2,2,2,2,2,1,1,0,0),
        intArrayOf(0,0,1,1,2,2,2,2,2,2,2,2,1,1,0,0),
        intArrayOf(0,0,1,1,2,2,2,2,2,2,2,2,1,1,0,0),
        intArrayOf(0,0,1,1,2,2,3,3,3,3,3,3,1,1,0,0),
        intArrayOf(0,0,1,1,2,2,3,3,4,3,3,3,1,1,0,0),
        intArrayOf(0,0,1,1,2,2,3,3,3,3,3,3,1,1,0,0),
        intArrayOf(0,0,1,1,2,2,2,2,5,2,2,2,1,1,0,0),
        intArrayOf(0,0,1,1,2,2,2,7,7,7,7,7,2,1,1,0,0),
        intArrayOf(0,0,0,0,0,0,0,7,6,7,7,7,0,0,0,0,0),
        intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0),
        intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0),
        intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0),
        intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0),
        intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0)
    )

    val colors = listOf(
        COLOR_BG, COLOR_HAIR, COLOR_SKIN, COLOR_GLASS,
        COLOR_GLASS_SHINE, COLOR_NOSE, COLOR_WRINKLE, COLOR_MOUTH
    )

    sizes.forEach { (folder, size) ->
        createIcon(size, "app/src/main/res/mipmap-$folder", "ic_launcher.png", pixels, colors)
        createIcon(size, "app/src/main/res/mipmap-$folder", "ic_launcher_round.png", pixels, colors, round = true)
        println("Created $folder icons ($size x $size)")
    }

    println("Done!")
}

fun createIcon(
    size: Int,
    folder: String,
    filename: String,
    pixels: Array<IntArray>,
    colors: List<Color>,
    round: Boolean = false
) {
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g: Graphics2D = img.createGraphics()

    if (round) {
        g.color = colors[0]
        g.fillOval(0, 0, size, size)
    } else {
        g.color = colors[0]
        g.fillRect(0, 0, size, size)
    }

    val pixelSize = maxOf(1, size / 16)
    val offset = (size - (16 * pixelSize)) / 2

    for (y in 0 until 16) {
        for (x in 0 until 16) {
            val colorIndex = pixels[y][x]
            if (colorIndex > 0 && colorIndex < colors.size) {
                g.color = colors[colorIndex]
                g.fillRect(offset + x * pixelSize, offset + y * pixelSize, pixelSize, pixelSize)
            }
        }
    }

    g.dispose()

    val dir = File(folder)
    dir.mkdirs()
    val file = File(dir, filename)
    ImageIO.write(img, "PNG", file)
}
