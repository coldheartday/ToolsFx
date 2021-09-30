package me.leon.ade

import java.net.HttpURLConnection
import java.net.URLDecoder
import java.util.regex.Pattern

object NetHelper {

    private val REG_CONTENT_DISPOSITION =
        Pattern.compile("filename=([^;]*)$|filename\\*=\"?.*'+([^;'\"]+)\"?")
    const val ILLEGAL_FILE_NAME_PATTERN = "[\\/:?*\"<>|]"
    private val REG_CHINESE = Pattern.compile("[\\u4e00-\\u9fa5]")

    const val COMMON_UA =
        "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Mobile Safari/537.36"

    /** 根据响应头或者url获取文件名 */
    fun getNetFileName(response: HttpURLConnection) =
        getCorrectUrl(
            URLDecoder.decode(
                response.getHeaderField("Content-Disposition")?.let { getFileName(it) }
                    ?: response.getHeaderField("content-disposition")?.let { getFileName(it) }
                        ?: getUrlFileName(response.url.toString())
                        ?: "unknownfile_${System.currentTimeMillis()}"
            )
        )

    private fun getCorrectUrl(url: String) =
        url.toByteArray(Charsets.ISO_8859_1).toString(Charsets.UTF_8).takeIf {
            REG_CHINESE.matcher(it).find()
        }
            ?: url

    /**
     * 通过 ‘？’ 和 ‘/’ 判断文件名
     * http://mavin-manzhan.oss-cn-hangzhou.aliyuncs.com/1486631099150286149.jpg?x-oss-process=image/watermark,image_d2F0ZXJtYXJrXzIwMF81MC5wbmc
     */
    private fun getUrlFileName(url: String): String? {
        var filename: String? = null
        val strings = url.split("/").toTypedArray()
        for (string in strings) {
            if (string.contains("?")) {
                val endIndex = string.indexOf('?')
                if (endIndex != -1) {
                    filename = string.substring(0, endIndex)
                    return filename
                }
            }
        }
        if (strings.isNotEmpty()) {
            filename = strings[strings.size - 1]
        }
        return filename
    }

    private fun getFileName(dispositionHeader: String): String? {
        val matcher = REG_CONTENT_DISPOSITION.matcher(dispositionHeader)
        if (matcher.find()) {
            for (i in 1..2) {
                if (!matcher.group(i).isNullOrEmpty()) {
                    return matcher.group(i)
                }
            }
        }
        return null
    }
}
