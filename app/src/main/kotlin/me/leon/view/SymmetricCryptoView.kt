package me.leon.view

import java.io.File
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Pos
import javafx.scene.control.*
import me.leon.CHARSETS
import me.leon.controller.SymmetricCryptoController
import me.leon.encode.base.base64Decode
import me.leon.ext.*
import tornadofx.*
import tornadofx.FX.Companion.messages

class SymmetricCryptoView : View(messages["symmetricBlock"]) {
    private val controller: SymmetricCryptoController by inject()
    override val closeable = SimpleBooleanProperty(false)
    private val isFile = SimpleBooleanProperty(false)
    private val isProcessing = SimpleBooleanProperty(false)
    private val isSingleLine = SimpleBooleanProperty(false)
    private lateinit var taInput: TextArea
    private lateinit var tfKey: TextField
    private lateinit var tfIv: TextField
    private var isEncrypt = true
    private lateinit var taOutput: TextArea
    private val inputText: String
        get() = taInput.text
    private val outputText: String
        get() = taOutput.text
    private val info
        get() = "Cipher: $cipher   charset: ${selectedCharset.get()}  file mode:  ${isFile.get()} "
    private lateinit var labelInfo: Label
    private val keyByteArray
        get() =
            when (keyEncode) {
                "raw" -> tfKey.text.toByteArray()
                "hex" -> tfKey.text.hex2ByteArray()
                "base64" -> tfKey.text.base64Decode()
                else -> byteArrayOf()
            }

    private var keyEncode = "raw"
    private var ivEncode = "raw"

    private val ivByteArray
        get() =
            when (ivEncode) {
                "raw" -> tfIv.text.toByteArray()
                "hex" -> tfIv.text.hex2ByteArray()
                "base64" -> tfIv.text.base64Decode()
                else -> byteArrayOf()
            }

    private val eventHandler = fileDraggedHandler {
        taInput.text =
            if (isFile.get())
                it.joinToString(System.lineSeparator(), transform = File::getAbsolutePath)
            else it.first().readText()
    }
    private val algs =
        mutableListOf(
            "DES",
            "DESEDE",
            "AES",
            "SM4",
            "Blowfish",
            "Twofish",
            "Threefish-256",
            "Threefish-512",
            "Threefish-1024",
            "RC2",
            "RC5",
            "RC6",
            "Camellia",
            "CAST5",
            "CAST6",
            "ARIA",
            "Skipjack",
            "Serpent",
            "DSTU7624",
            "IDEA",
            "SEED",
            "TEA",
            "XTEA",
        )
    private val paddingsAlg =
        mutableListOf(
            "PKCS5Padding",
            "PKCS7Padding",
            "ISO10126Padding",
            "ZeroBytePadding",
            "NoPadding",
            "TBCPadding",
            "X923Padding",
            "ISO7816d4Padding",
            "ISO10126d2Padding"
        )
    private val modes = mutableListOf("CBC", "ECB", "CFB", "OFB", "CTR", "GCM", "CCM", "EAX", "OCB")
    private val selectedAlg = SimpleStringProperty(algs[2])
    private val selectedPadding = SimpleStringProperty(paddingsAlg.first())
    private val selectedCharset = SimpleStringProperty(CHARSETS.first())
    private val selectedMod = SimpleStringProperty(modes.first())

    private val cipher
        get() = "${selectedAlg.get()}/${selectedMod.get()}/${selectedPadding.get()}"

    private val centerNode = vbox {
        paddingAll = DEFAULT_SPACING
        spacing = DEFAULT_SPACING
        hbox {
            label(messages["input"])
            button(graphic = imageview("/img/import.png")) {
                action { taInput.text = clipboardText() }
            }
        }
        taInput =
            textarea {
                promptText = messages["inputHint"]
                isWrapText = true
                onDragEntered = eventHandler
                contextmenu {
                    item(messages["loadFromNetLoop"]) {
                        action {
                            runAsync { inputText.simpleReadFromNet() } ui { taInput.text = it }
                        }
                    }
                }
            }
        hbox {
            alignment = Pos.CENTER_LEFT
            spacing = DEFAULT_SPACING
            label(messages["alg"])
            combobox(selectedAlg, algs) { cellFormat { text = it } }
            label("mode:")
            combobox(selectedMod, modes) { cellFormat { text = it } }
            label("padding:")
            combobox(selectedPadding, paddingsAlg) { cellFormat { text = it } }
            label("charset:")
            combobox(selectedCharset, CHARSETS) { cellFormat { text = it } }
        }

        hbox {
            alignment = Pos.CENTER_LEFT
            label("key:")
            tfKey = textfield { promptText = messages["keyHint"] }
            vbox {
                togglegroup {
                    spacing = DEFAULT_SPACING
                    paddingAll = DEFAULT_SPACING
                    radiobutton("raw") { isSelected = true }
                    radiobutton("hex")
                    radiobutton("base64")
                    selectedToggleProperty().addListener { _, _, new ->
                        keyEncode = new.cast<RadioButton>().text
                    }
                }
            }
            label("iv:")
            tfIv = textfield { promptText = messages["ivHint"] }
            vbox {
                togglegroup {
                    spacing = DEFAULT_SPACING
                    paddingAll = DEFAULT_SPACING
                    radiobutton("raw") { isSelected = true }
                    radiobutton("hex")
                    radiobutton("base64")
                    selectedToggleProperty().addListener { _, _, new ->
                        ivEncode = new.cast<RadioButton>().text
                    }
                }
            }
        }
        selectedAlg.addListener { _, _, newValue -> newValue?.run { println("alg $newValue") } }

        hbox {
            alignment = Pos.CENTER_LEFT
            togglegroup {
                spacing = DEFAULT_SPACING
                alignment = Pos.BASELINE_CENTER
                radiobutton(messages["encrypt"]) { isSelected = true }
                radiobutton(messages["decrypt"])
                selectedToggleProperty().addListener { _, _, new ->
                    isEncrypt = new.cast<RadioButton>().text == messages["encrypt"]
                    doCrypto()
                }
            }
            checkbox(messages["fileMode"], isFile)
            checkbox(messages["singleLine"], isSingleLine)
            button(messages["run"], imageview("/img/run.png")) {
                enableWhen(!isProcessing)
                action { doCrypto() }
            }
        }
        hbox {
            spacing = DEFAULT_SPACING
            label(messages["output"])
            button(graphic = imageview("/img/copy.png")) { action { outputText.copy() } }
            button(graphic = imageview("/img/up.png")) {
                action {
                    taInput.text = outputText
                    taOutput.text = ""
                }
            }
        }
        taOutput =
            textarea {
                promptText = messages["outputHint"]
                isWrapText = true
            }
    }

    override val root = borderpane {
        center = centerNode
        bottom = hbox { labelInfo = label(info) }
    }

    private fun doCrypto() {
        runAsync {
            isProcessing.value = true
            if (isEncrypt)
                if (isFile.get())
                    inputText.lineAction2String {
                        controller.encryptByFile(keyByteArray, it, ivByteArray, cipher)
                    }
                else
                    controller.encrypt(
                        keyByteArray,
                        inputText,
                        ivByteArray,
                        cipher,
                        selectedCharset.get(),
                        isSingleLine.get()
                    )
            else if (isFile.get())
                inputText.lineAction2String {
                    controller.decryptByFile(keyByteArray, it, ivByteArray, cipher)
                }
            else {
                controller.decrypt(
                    keyByteArray,
                    inputText,
                    ivByteArray,
                    cipher,
                    selectedCharset.get(),
                    isSingleLine.get()
                )
            }
        } ui
            {
                isProcessing.value = false
                taOutput.text = it
                labelInfo.text = info
                if (Prefs.autoCopy) it.copy().also { primaryStage.showToast(messages["copied"]) }
            }
    }
}