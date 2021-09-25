package me.leon.view

import java.awt.Rectangle
import java.awt.Robot
import javafx.beans.property.SimpleBooleanProperty
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.*
import javafx.scene.layout.*
import javafx.scene.paint.Paint
import javafx.stage.Stage
import javafx.stage.StageStyle
import kotlin.math.abs
import me.leon.ext.*
import tornadofx.*

class QrcodeView : View("Qrcode") {
    // 切图区域的起始位置x
    private var startX = 0.0

    // 切图区域的起始位置y
    private var startY = 0.0

    // 切图区域宽
    private var w = 0.0

    // 切图区域高
    private var h = 0.0

    // 切图区域
    private lateinit var hBox: HBox
    private lateinit var button: Button
    private lateinit var ta: TextArea

    // 切成的图片展示区域
    private lateinit var iv: ImageView

    override val closeable = SimpleBooleanProperty(false)

    override val root = vbox {
        paddingAll = DEFAULT_SPACING_2X
        spacing = DEFAULT_SPACING_2X
        hbox {
            spacing = DEFAULT_SPACING_2X
            label(messages["recognize"])
            button =
                button(messages["shotReco"]) {
                    action { this@QrcodeView.show() }
                    shortcut(KeyCombination.valueOf("Ctrl+Q"))
                    tooltip("快捷键Ctrl+Q")
                }

            button(messages["clipboardReco"]) {
                action { clipboardImage()?.toBufferImage()?.qrReader()?.let { ta.text = it } }
            }
            button(messages["fileReco"]) {
                shortcut(KeyCombination.valueOf("Ctrl+F"))
                tooltip("快捷键Ctrl+F")
                action {
                    primaryStage.fileChooser(messages["chooseFile"])?.let {
                        iv.image = Image(it.inputStream())
                        ta.text = it.qrReader()
                    }
                }
            }
        }

        hbox {
            spacing = DEFAULT_SPACING_3X
            label(messages["content"])
            button(graphic = imageview("/img/copy.png")) {
                action { ta.text.copy().also { if (it) primaryStage.showToast("复制成功") } }
            }
            button(graphic = imageview("/img/import.png")) { action { ta.text = clipboardText() } }
        }
        ta =
            textarea {
                promptText = messages["qrHint"]
                isWrapText = true
                prefHeight = DEFAULT_SPACING_10X
            }

        hbox {
            spacing = DEFAULT_SPACING_2X
            button(messages["genQrcode"]) {
                action {
                    if (ta.text.isNotEmpty()) {
                        iv.image = createQR(ta.text)
                    }
                }
                shortcut(KeyCombination.valueOf("F9"))
                tooltip("快捷键F9")
            }
        }
        hbox {
            label(messages["qrImg"])
            button(graphic = imageview("/img/copy.png")) {
                action { iv.image?.copy()?.also { if (it) primaryStage.showToast("复制二维码成功") } }
            }
        }
        hbox {
            alignment = Pos.CENTER
            iv = imageview()
        }
    }

    private fun show() {
        // 将主舞台缩放到任务栏
        primaryStage.isIconified = true
        // 创建辅助舞台，并设置场景与布局
        val stage = Stage()
        // 锚点布局采用半透明
        val anchorPane = AnchorPane()
        anchorPane.style = "-fx-background-color: #85858522"
        // 场景设置白色全透明
        val scene = Scene(anchorPane)
        scene.fill = Paint.valueOf("#ffffff00")
        stage.scene = scene
        // 清楚全屏中间提示文字
        stage.fullScreenExitHint = ""
        stage.initStyle(StageStyle.TRANSPARENT)
        stage.isFullScreen = true
        stage.show()

        // 切图窗口绑定鼠标按下事件
        anchorPane.onMousePressed =
            EventHandler { event: MouseEvent ->
                // 清除锚点布局中所有子元素
                anchorPane.children.clear()
                // 创建切图区域
                hBox =
                    HBox().apply {
                        background = null
                        border =
                            Border(
                                BorderStroke(
                                    Paint.valueOf("#c03700"),
                                    BorderStrokeStyle.SOLID,
                                    null,
                                    BorderWidths(2.0)
                                )
                            )
                    }
                anchorPane.children.add(hBox)
                // 记录并设置起始位置
                startX = event.sceneX
                startY = event.sceneY
                AnchorPane.setLeftAnchor(hBox, startX)
                AnchorPane.setTopAnchor(hBox, startY)
            }
        // 绑定鼠标按下拖拽的事件
        addMouseDraggedEvent(anchorPane)
        // 绑定鼠标松开事件
        addMouseReleasedEvent(anchorPane, stage)
        scene.onKeyPressed =
            EventHandler { event: KeyEvent ->
                if (event.code == KeyCode.ESCAPE) {
                    stage.close()
                    primaryStage.isIconified = false
                }
            }
    }

    private fun addMouseReleasedEvent(anchorPane: AnchorPane, stage: Stage) {
        anchorPane.onMouseReleased =
            EventHandler { event: MouseEvent ->
                // 记录最终长宽
                w = abs(event.sceneX - startX)
                h = abs(event.sceneY - startY)
                anchorPane.style = "-fx-background-color: #00000000"
                // 添加剪切按钮，并显示在切图区域的底部
                val b = Button(messages["cut"])
                hBox.border =
                    Border(
                        BorderStroke(
                            Paint.valueOf("#85858544"),
                            BorderStrokeStyle.SOLID,
                            null,
                            BorderWidths(2.0)
                        )
                    )
                hBox.children.add(b)
                hBox.alignment = Pos.BOTTOM_RIGHT
                // 为切图按钮绑定切图事件
                b.onAction =
                    EventHandler {
                        // 切图辅助舞台
                        stage.close()
                        runCatching { captureImg() }.onFailure {
                            it.printStackTrace()
                            primaryStage.showToast(messages["recognizeError"])
                        }
                        // 主舞台还原
                        primaryStage.isIconified = false
                    }
            }
    }

    private fun addMouseDraggedEvent(anchorPane: AnchorPane) {
        anchorPane.onMouseDragged =
            EventHandler { event: MouseEvent ->
                // 用label记录切图区域的长宽
                val label =
                    Label().apply {
                        alignment = Pos.CENTER
                        prefHeight = DEFAULT_SPACING_4X
                        prefWidth = DEFAULT_SPACING_20X
                        textFill = Paint.valueOf("#ffffff") // 白色填充
                        style = "-fx-background-color: #000000" // 黑背景
                    }

                anchorPane.children.add(label)
                AnchorPane.setLeftAnchor(label, startX + DEFAULT_SPACING_4X)
                AnchorPane.setTopAnchor(label, startY)

                // 计算宽高并且完成切图区域的动态效果
                w = abs(event.sceneX - startX)
                h = abs(event.sceneY - startY)
                hBox.prefWidth = w
                hBox.prefHeight = h
                label.text = "宽：$w 高：$h"
            }
    }

    @Throws(Exception::class)
    fun captureImg() {
        val robot = Robot()
        val re = Rectangle(startX.toInt(), startY.toInt(), w.toInt(), h.toInt())
        val screenCapture = robot.createScreenCapture(re)
        val bufferedImage = screenCapture.toFxImg()
        iv.image = bufferedImage
        ta.text = screenCapture.qrReader()
    }

    private fun createQR(data: String = "this is test data"): Image {
        return data.createQR().toFxImg()
    }
}