package com.hegg5577.i18n.locale

interface LocaleWriter {
    fun read(): Map<String, String>
    fun set(dottedKey: String, value: String): Boolean
    fun render(): String
}

class JsonLocaleWriter(text: String) : LocaleWriter {

    private val root = com.google.gson.JsonParser.parseString(text).asJsonObject

    override fun read(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        fun walk(obj: com.google.gson.JsonObject, prefix: String) {
            for ((name, value) in obj.entrySet()) {
                val key = if (prefix.isEmpty()) name else "$prefix.$name"
                when {
                    value.isJsonObject -> walk(value.asJsonObject, key)
                    value.isJsonPrimitive -> out[key] = value.asString
                }
            }
        }
        walk(root, "")
        return out
    }

    override fun set(dottedKey: String, value: String): Boolean = runCatching {
        LocaleFileWriter.setNested(root, dottedKey, value)
        true
    }.getOrDefault(false)

    override fun render(): String = LocaleFileWriter.render(root)
}
