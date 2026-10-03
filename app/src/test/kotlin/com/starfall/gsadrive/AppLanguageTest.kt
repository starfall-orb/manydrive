package com.starfall.gsadrive

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageTest {
    @Test fun englishIsTheSourceLanguage() {
        assertEquals("Settings", AppLanguage.translate("Settings", Locale.ENGLISH))
        assertEquals("Unknown source copy", AppLanguage.translate("Unknown source copy", Locale.FRENCH))
    }

    @Test fun allRequestedLocalesAreSupported() {
        assertEquals(
            setOf("en", "vi", "fr", "ja", "zh-Hans", "zh-Hant", "de", "es", "th", "id", "ko", "ru", "it"),
            AppLanguage.supportedLocales
        )
        val locales = listOf(
            Locale("vi"), Locale.FRENCH, Locale.JAPANESE, Locale.forLanguageTag("zh-Hans"),
            Locale.forLanguageTag("zh-Hant"), Locale.GERMAN, Locale("es"), Locale("th"),
            Locale("id"), Locale.KOREAN, Locale("ru"), Locale.ITALIAN
        )
        locales.forEach { locale ->
            assertNotEquals("Settings", AppLanguage.translate("Settings", locale))
        }
    }

    @Test fun vietnameseIsNowATranslationPack() {
        assertEquals("Cài đặt", AppLanguage.translate("Settings", Locale("vi")))
        assertEquals("Tệp", AppLanguage.translate("Files", Locale("vi")))
    }

    @Test fun simplifiedAndTraditionalChineseAreDistinct() {
        val simplified = AppLanguage.translate("Files", Locale.forLanguageTag("zh-Hans"))
        val traditional = AppLanguage.translate("Files", Locale.forLanguageTag("zh-Hant"))
        assertNotEquals(simplified, traditional)
        assertEquals("zh-Hant", AppLanguage.localeKey(Locale("zh", "TW")))
        assertEquals("zh-Hans", AppLanguage.localeKey(Locale("zh", "CN")))
    }

    @Test fun dynamicMessagesKeepRuntimeValues() {
        val french = AppLanguage.translate("Sign out of An?", Locale.FRENCH)
        assertTrue(french.contains("An"))
        assertNotEquals("Sign out of An?", french)
    }

    @Test fun everyTranslationPackMatchesTheEnglishCatalog() {
        val placeholder = Regex("\\{\\d+}")
        AppLanguageCatalog.exactTranslations.forEach { (locale, values) ->
            assertEquals("exact catalog size for $locale", AppLanguageCatalog.exactSources.size, values.size)
            assertTrue("blank exact translation in $locale", values.all { it.isNotBlank() })
        }
        AppLanguageCatalog.templateTranslations.forEach { (locale, values) ->
            assertEquals("template catalog size for $locale", AppLanguageCatalog.templateSources.size, values.size)
            AppLanguageCatalog.templateSources.zip(values).forEach { (source, translated) ->
                assertEquals(
                    "placeholder mismatch for $locale: $source",
                    placeholder.findAll(source).map { it.value }.toSet(),
                    placeholder.findAll(translated).map { it.value }.toSet()
                )
            }
        }
    }

    @Test fun unsupportedLocalesFallBackToEnglishSource() {
        assertEquals("Settings", AppLanguage.translate("Settings", Locale("ar")))
    }
}
