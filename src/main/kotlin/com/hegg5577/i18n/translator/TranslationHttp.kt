package com.hegg5577.i18n.translator

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder

/** 各引擎共用请求编码与连接释放，避免新增引擎重复遗漏超时和错误处理。 */
internal object TranslationHttp {
    /** 允许填写基础地址或完整端点，避免重复拼接路径。 */
    fun endpoint(baseUrl: String, path: String): String {
        val base = baseUrl.trim().substringBefore('?').trimEnd('/')
        val endpoint = if (base.endsWith(path)) base else "$base$path"
        return if ('?' in baseUrl) "$endpoint?${baseUrl.trim().substringAfter('?')}" else endpoint
    }

    /** 统一校验网络地址，防止配置错误被误报为网络故障。 */
    fun validateUrl(url: String) {
        val uri = try {
            URI.create(url.trim())
        } catch (_: IllegalArgumentException) {
            throw TranslationException("请求地址格式无效")
        }
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.fragment != null) {
            throw TranslationException("请求地址必须是有效的 HTTP 或 HTTPS URL，且不能包含用户名、密码或片段")
        }
    }

    /** 对每个参数分别编码，防止原文中的换行、加号和中文改变请求含义。 */
    fun form(parameters: Map<String, String>): String = parameters.entries.joinToString("&") {
        "${URLEncoder.encode(it.key, Charsets.UTF_8)}=${URLEncoder.encode(it.value, Charsets.UTF_8)}"
    }

    /** 为已有查询参数的自定义端点追加参数。 */
    fun query(url: String, parameters: Map<String, String>): String =
        url + (if ('?' in url) "&" else "?") + form(parameters)

    /** 禁止自动重定向，避免中转服务把密钥转发到另一个地址。 */
    fun request(
        url: String,
        body: String? = null,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap(),
        readTimeout: Int = 60_000,
    ): JsonElement {
        validateUrl(url)
        val connection = URI.create(url.trim()).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = readTimeout
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "i18n-tool-plus")
            for ((key, value) in headers) connection.setRequestProperty(key, value)
            if (body != null) {
                connection.setRequestProperty("Content-Type", "$contentType; charset=UTF-8")
                connection.doOutput = true
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                // 不回显服务端原始响应，第三方服务可能把请求中的密钥原样返回。
                throw TranslationException("翻译服务返回 HTTP $code，请检查请求地址、密钥、额度及服务状态")
            }
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return try {
                JsonParser.parseString(response)
            } catch (_: RuntimeException) {
                throw TranslationException("翻译服务返回了无效 JSON，请检查请求地址是否为 API 端点")
            }
        } catch (e: SocketTimeoutException) {
            throw TranslationException("翻译请求超时，请检查网络或服务是否已启动", e)
        } catch (_: IOException) {
            throw TranslationException("翻译服务连接失败，请检查地址、网络和 IDE 代理设置")
        } finally {
            connection.disconnect()
        }
    }
}
