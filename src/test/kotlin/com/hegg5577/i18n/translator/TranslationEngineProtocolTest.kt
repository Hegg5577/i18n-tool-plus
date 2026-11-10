package com.hegg5577.i18n.translator

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 仅请求本机临时服务，验证各引擎实际发送的协议，不依赖外部服务或真实凭据。 */
class TranslationEngineProtocolTest {
    private data class Request(val path: String, val query: String, val body: String, val authorization: String?)

    /** DeepL 自定义端点必须收到 text 数组及正确的鉴权头。 */
    @Test
    fun deepLUsesCustomEndpoint() = withResponse("""{"translations":[{"text":"你好"}]}""") { url, requests ->
        assertEquals(listOf("你好"), DeepLTranslationEngine("test-key", false, "$url/relay").translate(listOf("Hello"), "zh", "en"))
        val request = requests.single()
        val body = JsonParser.parseString(request.body).asJsonObject
        assertEquals("/relay", request.path)
        assertEquals("DeepL-Auth-Key test-key", request.authorization)
        assertEquals("Hello", body.getAsJsonArray("text")[0].asString)
        assertFalse(body.has("texts"))
        assertEquals("EN", body.get("source_lang").asString)
    }

    /** DeepL 不能把代理返回的凭据或原文展示在错误消息中。 */
    @Test
    fun deepLErrorsDoNotExposeResponseBody() = withResponse("secret-key", 400) { url, _ ->
        try {
            DeepLTranslationEngine("test-key", apiUrl = url).translate(listOf("Hello"), "zh", "en")
            throw AssertionError("必须拒绝错误响应")
        } catch (e: TranslationException) {
            assertTrue(e.message.orEmpty().contains("400"))
            assertFalse(e.message.orEmpty().contains("secret-key"))
        }
    }

    /** 不完整或非字符串结果必须整批拒绝，避免译文与原文错位。 */
    @Test
    fun deepLRejectsMalformedResults() {
        for (response in listOf("""{"translations":[{"text":"One"}]}""",
            """{"translations":[{}, {"text":"Two"}]}""",
            """{"translations":[{"text":42}, {"text":"Two"}]}""")) {
            withResponse(response) { url, _ ->
                try {
                    DeepLTranslationEngine("test-key", apiUrl = url).translate(listOf("A", "B"), "zh", "en")
                    throw AssertionError("必须拒绝不完整结果：$response")
                } catch (_: TranslationException) {
                    // 整批失败才能保证调用方不会保存错位译文。
                }
            }
        }
    }

    /** 无密钥 Google 和国内选项均保留免费协议，并对原文独立编码。 */
    @Test
    fun googleFreeAndDomesticUseConfiguredEndpoint() = withResponse("""[[["你好","Hello",null,null]],null,"en"]""") { url, requests ->
        for (domestic in listOf(false, true)) {
            val engine = GoogleTranslationEngine(apiUrl = "$url/free", domestic = domestic)
            assertEquals(listOf("你好"), engine.translate(listOf("Hello + 中文"), "zh", "en"))
        }
        assertEquals(2, requests.size)
        for (request in requests) {
            val query = decodeForm(request.query)
            assertEquals("Hello + 中文", query["q"])
            assertEquals("gtx", query["client"])
            assertEquals("zh-CN", query["tl"])
            assertEquals("/free", request.path)
        }
    }

    /** Google 配置密钥后必须切换为 Cloud 协议，并还原其 HTML 实体。 */
    @Test
    fun googleKeySelectsCloudProtocol() = withResponse("""{"data":{"translations":[{"translatedText":"Tom &amp; Jerry"}]}}""") { url, requests ->
        assertEquals(listOf("Tom & Jerry"), GoogleTranslationEngine("cloud-key", "$url/cloud")
            .translate(listOf("Hello"), "en-US", "zh"))
        val request = requests.single()
        val body = JsonParser.parseString(request.body).asJsonObject
        assertEquals("cloud-key", decodeForm(request.query)["key"])
        assertEquals("en", body.get("target").asString)
        assertEquals("Hello", body.get("q").asString)
        assertEquals("text", body.get("format").asString)
    }

    /** 百度签名使用编码前的原文，并把日语转换为其 API 所需的 jp。 */
    @Test
    fun baiduSignsOriginalText() = withResponse("""{"trans_result":[{"dst":"こんにちは"}]}""") { url, requests ->
        val text = "Hello + 中文"
        assertEquals(listOf("こんにちは"), BaiduTranslationEngine("app-id", "secret", "$url/baidu")
            .translate(listOf(text), "ja", "zh-CN"))
        val body = decodeForm(requests.single().body)
        assertEquals(text, body["q"])
        assertEquals("jp", body["to"])
        assertEquals("zh", body["from"])
        val expected = MessageDigest.getInstance("MD5")
            .digest(("app-id" + text + body.getValue("salt") + "secret").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals(expected, body["sign"])
    }

    /** 百度即使返回 HTTP 200，也必须识别协议中的业务错误。 */
    @Test(expected = TranslationException::class)
    fun baiduRejectsServiceError() = withResponse("""{"error_code":"54003","error_msg":"rate limited"}""") { url, _ ->
        BaiduTranslationEngine("app-id", "secret", "$url/baidu").translate(listOf("Hello"), "zh", "en")
    }

    /** LibreTranslate 实例没有启用鉴权时，不应多传 api_key。 */
    @Test
    fun libreTranslateSupportsOptionalKey() = withResponse("""{"translatedText":"你好"}""") { url, requests ->
        for (key in listOf("", "local-key")) {
            assertEquals(listOf("你好"), LibreTranslationEngine("$url/translate", key).translate(listOf("Hello"), "zh", "en"))
        }
        assertFalse(JsonParser.parseString(requests[0].body).asJsonObject.has("api_key"))
        val body = JsonParser.parseString(requests[1].body).asJsonObject
        assertEquals("local-key", body.get("api_key").asString)
        assertEquals("text", body.get("format").asString)
    }

    /** Ollama 使用原生非流式接口，完整端点后不应重复追加 /api/chat。 */
    @Test
    fun ollamaUsesNonStreamingChat() {
        val response = JsonObject().apply {
            add("message", JsonObject().apply { addProperty("content", """{"translations":["你好"]}""") })
        }
        withResponse(response.toString()) { url, requests ->
            assertEquals(listOf("你好"), OllamaTranslationEngine("local-model", "$url/api/chat/")
                .translate(listOf("Hello"), "zh", "en"))
            val request = requests.single()
            val body = JsonParser.parseString(request.body).asJsonObject
            assertEquals("/api/chat", request.path)
            assertEquals("local-model", body.get("model").asString)
            assertFalse(body.get("stream").asBoolean)
            assertEquals("json", body.get("format").asString)
        }
    }

    /** OpenAI 同时保留多条结果的顺序、换行及占位符，不再依赖分隔符。 */
    @Test
    fun openaiPreservesTranslationBoundaries() {
        val response = JsonParser.parseString("""{"choices":[{"message":{"content":""}}]}""").asJsonObject
        response.getAsJsonArray("choices")[0].asJsonObject.getAsJsonObject("message")
            .addProperty("content", """{"translations":["第一行\n第二行","你好 __P0__"]}""")
        withResponse(response.toString()) { url, requests ->
            assertEquals(listOf("第一行\n第二行", "你好 __P0__"), OpenAITranslationEngine("test-key", "test-model", "$url/v1/")
                .translate(listOf("First\nSecond", "Hello __P0__"), "zh", "en"))
            assertEquals("/v1/chat/completions", requests.single().path)
            assertEquals("Bearer test-key", requests.single().authorization)
        }
    }

    /** HTTP 错误不能被当作译文，也不能回显可能包含密钥的服务响应。 */
    @Test
    fun httpErrorsDoNotExposeResponseBody() = withResponse("""{"error":"secret-key"}""", 403) { url, _ ->
        try {
            TranslationHttp.request(url)
            throw AssertionError("应拒绝 HTTP 错误")
        } catch (e: TranslationException) {
            assertTrue(e.message.orEmpty().contains("403"))
            assertFalse(e.message.orEmpty().contains("secret-key"))
        }
    }

    /** 每个测试拥有独立端口，结束时始终释放服务。 */
    private fun withResponse(response: String, status: Int = 200, block: (String, List<Request>) -> Unit) {
        val requests = CopyOnWriteArrayList<Request>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += Request(exchange.requestURI.path, exchange.requestURI.rawQuery.orEmpty(),
                exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }, exchange.requestHeaders.getFirst("Authorization"))
            val bytes = response.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}", requests)
        } finally {
            server.stop(0)
        }
    }

    /** 还原参数后比较原文，避免测试遗漏 URL 编码造成的数据变化。 */
    private fun decodeForm(form: String): Map<String, String> = form.split('&').associate {
        val parts = it.split('=', limit = 2)
        URLDecoder.decode(parts[0], Charsets.UTF_8) to URLDecoder.decode(parts.getOrElse(1) { "" }, Charsets.UTF_8)
    }
}
