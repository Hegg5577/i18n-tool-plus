package com.hegg5577.i18n.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** 缺失和空白必须分别展示，不能把纯空白误判为已翻译。 */
class TranslationStatusTest {
    /** 缺失、空字符串和纯空白都需要填写，非空文本才属于已翻译。 */
    @Test
    fun distinguishesMissingEmptyAndTranslated() {
        assertEquals(TranslationStatus.MISSING, TranslationStatus.of(null))
        assertEquals(TranslationStatus.EMPTY, TranslationStatus.of(""))
        assertEquals(TranslationStatus.EMPTY, TranslationStatus.of(" \t\n"))
        assertEquals(TranslationStatus.TRANSLATED, TranslationStatus.of("确定"))
    }
}
