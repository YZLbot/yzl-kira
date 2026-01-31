package top.tbpdt.utils

import kotlinx.serialization.json.jsonObject
import top.tbpdt.handle.Wordle
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

enum class GuessResult {
    /**
     * 猜出正确单词
     */
    WIN,

    /**
     * 达到最大可猜次数，未猜出正确单词
     */
    LOSS,

    /**
     * 单词重复
     */
    DUPLICATE,

    /**
     * 单词不合法
     */
    ILLEGAL,

    /**
     * 没有出现在词库中的单词
     */
    UNKNOWN,
}

/**
 * @author Takeoff0518
 */
class WordleRound(
    val groupId: String,
    val word: String,
    val chineseMeaning: String,
    val englishMeaning: String
) {
    // 文字块尺寸
    val blockSize = Dimension(40, 40)

    // 文字块之间间距
    val blockPadding = Dimension(10, 10)

    // 边界间距
    val padding = Dimension(20, 20)

    // 边框宽度
    val borderWidth = 2

    // 字体大小
    val fontSize = 20

    // 字体
    val font = loadFont("config/font.ttf", fontSize)

    // 存在且位置正确时的颜色
    val correctColor = Color(134, 163, 115)

    // 存在但位置不正确时的颜色
    val existColor = Color(198, 182, 109)

    // 不存在时颜色
    val wrongColor = Color(123, 123, 124)

    // 边框颜色
    val borderColor = Color(123, 123, 124)

    // 背景颜色
    val bgColor = Color(255, 255, 255)

    // 文字颜色
    val fontColor = Color(255, 255, 255)


    val wordLower: String = word.lowercase()

    val length: Int = word.length

    val rows: Int = length + 1;

    // 是否用过了提示机会

    var isHinted: Boolean = false

    // 已经猜过的词
    val guessedWords: MutableList<String> = mutableListOf()

    val result: String
        get() = "单词: $word\n中释: $chineseMeaning\n英释: $englishMeaning"

    fun guess(word: String): GuessResult? {
        val lowerWord = word.lowercase()
        return when {
            lowerWord == wordLower -> {
                guessedWords.add(lowerWord)
                GuessResult.WIN
            }

            !isWordExistsInDict(lowerWord) -> GuessResult.UNKNOWN
            lowerWord in guessedWords -> GuessResult.DUPLICATE
            !legalWord(lowerWord) -> GuessResult.ILLEGAL
            else -> {
                guessedWords.add(lowerWord)
                if (guessedWords.size == rows) GuessResult.LOSS else null
            }
        }
    }

    /**
     * 绘制单个词块
     */
    private fun drawBlock(color: Color, letter: String): BufferedImage {
        val block = BufferedImage(blockSize.width, blockSize.height, BufferedImage.TYPE_INT_RGB)
        val g2d = block.createGraphics()

        g2d.setRenderingHint(
            java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON
        )

        // 边框
        g2d.color = borderColor
        g2d.fillRect(0, 0, blockSize.width, blockSize.height)

        // 内部方块
        g2d.color = color
        g2d.fillRect(
            borderWidth,
            borderWidth,
            blockSize.width - borderWidth * 2,
            blockSize.height - borderWidth * 2
        )

        // 字母
        if (letter.isNotEmpty()) {
            g2d.color = fontColor
            g2d.font = font

            val metrics = g2d.fontMetrics
            val x = (blockSize.width - metrics.stringWidth(letter)) / 2
            val y = (blockSize.height - metrics.height) / 2 + metrics.ascent

            g2d.drawString(letter.uppercase(), x, y)
        }

        g2d.dispose()
        return block
    }

    /**
     * 外部调用的绘图
     */
    fun draw(): ByteArray {
        val boardWidth = length * blockSize.width +
                (length - 1) * blockPadding.width +
                2 * padding.width
        val boardHeight = rows * blockSize.height +
                (rows - 1) * blockPadding.height +
                2 * padding.height

        val board = BufferedImage(boardWidth, boardHeight, BufferedImage.TYPE_INT_RGB)
        val g2d = board.createGraphics()

        g2d.color = bgColor
        g2d.fillRect(0, 0, boardWidth, boardHeight)

        for (row in 0 until rows) {
            val blocks = if (row < guessedWords.size) {
                val guessedWord = guessedWords[row]
                val wordIncorrect = buildString {
                    for (i in 0 until this@WordleRound.length) { // 妈的找了一节课的 bug
                        append(
                            if (guessedWord[i] != wordLower[i]) wordLower[i] else "0"
                        )
                    }
                }
                mutableListOf<BufferedImage>().apply {
                    for (i in 0 until length) {
                        val letter = guessedWord[i].toString()
                        val color = when {
                            letter[0] == wordLower[i] -> correctColor
                            letter[0] in wordIncorrect -> existColor
                            else -> wrongColor
                        }
                        add(drawBlock(color, letter))
                    }
                }
            } else {
                MutableList(length) { drawBlock(bgColor, "") }
            }

            for ((col, block) in blocks.withIndex()) {
                val x = padding.width + (blockSize.width + blockPadding.width) * col
                val y = padding.height + (blockSize.height + blockPadding.height) * row
                g2d.drawImage(block, x, y, null)
            }
        }

        g2d.dispose()

        // 转换为字节数组
        return saveToPng(board)
    }

    /**
     * 获取提示内容
     */
    fun getHint(): String {
        val letters = guessedWords.flatMap { it.toList() }
            .filter { it in wordLower }
            .toSet()
        return wordLower.map { if (it in letters) it else '*' }.joinToString("")
    }

    /**
     * 绘制提示
     */
    fun drawHint(): ByteArray {
        val hint = getHint()
        val boardWidth = length * blockSize.width +
                (length - 1) * blockPadding.width +
                2 * padding.width
        val boardHeight = blockSize.height + 2 * padding.height

        val board = BufferedImage(boardWidth, boardHeight, BufferedImage.TYPE_INT_RGB)
        val g2d = board.createGraphics()

        g2d.color = bgColor
        g2d.fillRect(0, 0, boardWidth, boardHeight)

        for (i in hint.indices) {
            val letter = hint[i].toString().replace("*", "")
            val color = if (letter.isNotEmpty()) correctColor else bgColor
            val x = padding.width + (blockSize.width + blockPadding.width) * i
            val y = padding.height
            val block = drawBlock(color, letter)
            g2d.drawImage(block, x, y, null)
        }

        g2d.dispose()
        return saveToPng(board)
    }

    private fun saveToPng(image: BufferedImage): ByteArray {
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", baos)
        return baos.toByteArray()
    }

    private fun loadFont(fontName: String, size: Int): Font {
        return try {
            val fontStream = File(fontName)
            val font = Font.createFont(Font.TRUETYPE_FONT, fontStream)
            font.deriveFont(Font.BOLD, size.toFloat())
        } catch (e: Exception) {
            Font("SansSerif", Font.BOLD, size)
        }
    }

    // 实现单词合法性检查
    private fun legalWord(word: String): Boolean {
        return word.length == length && word.all { it in 'a'..'z' }
    }

    // 检查所猜的词是否在词库中
    private fun isWordExistsInDict(word: String): Boolean {
        return Wordle.wordsSet.contains(word)
    }
}
