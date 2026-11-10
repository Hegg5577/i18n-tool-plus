package com.hegg5577.i18n.translator

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI

/**
 * DeepL 翻译引擎实现。
 *
 * 需要有效的 DeepL API Key（可从 https://www.deepl.com/pro 获取）。
 * 支持免费版和专业版 API Key，按配置选择端点（api-free.deepl.com 或 api.deepl.com）。
 *
 * @param apiKey DeepL API Key
 * @param isFreeApi 是否使用免费版端点。为 true 时使用 api-free.deepl.com，否则使用 api.deepl.com
 * @param apiUrl 自定义完整翻译请求地址，留空时按账户类型选择官方端点
 */
class DeepLTranslationEngine(
    private val apiKey: String,
    private val isFreeApi: Boolean = true,
    private val apiUrl: String = "",
) : TranslationEngine {

    override val id: String = "deepl"

    override val displayName: String =
        "DeepL ${if (isFreeApi) "（免费版）" else "（专业版）"}"

    /** 校验密钥后请求翻译，保留现有的失败重试行为。 */
    override fun translate(
        texts: List<String>,
        targetLocale: String,
        sourceLocale: String,
    ): List<String> {
        if (texts.isEmpty()) return emptyList()
        if (apiKey.isBlank()) {
            throw TranslationException("DeepL API Key 未配置")
        }

        var lastError: Exception? = null

        repeat(MAX_RETRIES) { attempt ->
            ProgressManager.checkCanceled()
            try {
                return requestBatch(texts, targetLocale, sourceLocale)
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw e
                    }
                }
            }
        }
        throw TranslationException(
            "DeepL 翻译失败（已重试 $MAX_RETRIES 次）：${lastError?.message ?: "未知原因"}",
            lastError,
        )
    }

    /** 分别处理源语言和目标语言，避免把仅用于目标语言的地区变体传给 source_lang。 */
    private fun createRequestBody(
        texts: List<String>,
        targetLocale: String,
        sourceLocale: String,
    ): JsonObject = JsonObject().apply {
        add("text", com.google.gson.JsonArray().apply {
            for (text in texts) {
                add(text)
            }
        })
        addProperty("target_lang", deepLCode(targetLocale))
        addProperty("source_lang", sourceLocale.uppercase().substringBefore('-'))
    }

    /** 使用 DeepL v2 协议并校验完整结果，避免代理异常造成错位或暴露凭据。 */
    private fun requestBatch(
        texts: List<String>,
        targetLocale: String,
        sourceLocale: String,
    ): List<String> {
        val requestBody = createRequestBody(texts, targetLocale, sourceLocale)

        val url = endpoint()
        TranslationHttp.validateUrl(url)
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Authorization", "DeepL-Auth-Key ${apiKey.trim()}")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(requestBody.toString())
            }

            val code = connection.responseCode
            if (code == HTTP_TOO_MANY_REQUESTS) {
                throw TranslationException("被限流（HTTP 429），请降低并发或稍后再试")
            }
            if (code == HTTP_UNAUTHORIZED) {
                throw TranslationException("DeepL API Key 无效或已过期")
            }
            if (code !in HTTP_OK until HTTP_MULTIPLE_CHOICES) {
                throw TranslationException("DeepL 服务返回异常状态码：$code，请检查请求地址、账户权限及服务状态")
            }

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val results = parseTranslations(body)
            if (results.size != texts.size) throw TranslationException("DeepL 返回的译文数量与原文不一致")
            return results
        } catch (e: TranslationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw TranslationException("请求超时", e)
        } catch (e: IOException) {
            throw TranslationException("网络不可达：${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 解析 DeepL API 响应：
     * ```json
     * {
     *   "translations": [
     *     {"text": "翻译文本1"},
     *     {"text": "翻译文本2"}
     *   ]
     * }
     * ```
     */
    private fun parseTranslations(body: String): List<String> {
        val root = runCatching { JsonParser.parseString(body).asJsonObject }
            .getOrElse { throw TranslationException("无法解析 DeepL 响应，请检查请求地址与服务协议") }

        val translations = runCatching { root.getAsJsonArray("translations") }
            .getOrNull() ?: throw TranslationException("响应格式异常：缺少 translations 数组")

        return translations.map { item ->
            val text = item.takeIf { it.isJsonObject }?.asJsonObject?.get("text")
            if (text == null || !text.isJsonPrimitive || !text.asJsonPrimitive.isString || text.asString.isBlank()) {
                throw TranslationException("DeepL 响应格式异常：译文必须为非空字符串")
            }
            text.asString
        }
    }

    /** 免费版与专业版使用不同域名，但都遵循 v2 翻译协议。 */
    private fun endpoint(): String =
        apiUrl.trim().ifBlank {
            if (isFreeApi) "https://api-free.deepl.com/v2/translate" else "https://api.deepl.com/v2/translate"
        }

    /**
     * i18next 语言码 -> DeepL 语言码。
     *
     * 映射规则：
     * - i18next 用 `zh` 表示简体中文，DeepL 用 `ZH`
     * - i18next 用 `zh-TW` / `zh-HK` 表示繁体，DeepL 也支持
     * - 其它大多情况下 i18next 用小写短码，DeepL 用大写短码
     */
    private fun deepLCode(locale: String): String = when (locale.uppercase()) {
        "ZH", "ZH-CN" -> "ZH"
        "ZH-TW", "ZH-HK" -> "ZH"
        "PT", "PT-BR" -> "PT-BR"
        "EN" -> "EN-US"
        else -> locale.uppercase()
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0"
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_RETRIES = 3
        const val RETRY_DELAY_MS = 600L
        const val HTTP_OK = 200
        const val HTTP_MULTIPLE_CHOICES = 300
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_UNAUTHORIZED = 401
    }
}
