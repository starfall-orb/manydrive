package com.starfall.gsadrive

import java.util.Locale

/** Returns localized application copy. English is always the source language. */
fun tr(source: String): String = AppLanguage.translate(source, AppLanguage.currentLocale())

internal object AppLanguage {
    internal fun currentLocale(): Locale = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.os.LocaleList.getDefault().get(0) ?: Locale.getDefault()
        } else {
            Locale.getDefault()
        }
    }.getOrDefault(Locale.getDefault())

    val supportedLocales: Set<String> = setOf(
        "en", "vi", "fr", "ja", "zh-Hans", "zh-Hant", "de", "es", "th", "id", "ko", "ru", "it"
    )

    private val exactIndex by lazy(LazyThreadSafetyMode.PUBLICATION) {
        AppLanguageCatalog.exactSources.withIndex().associate { (index, source) -> source to index }
    }

    private data class TemplateMatcher(val regex: Regex, val placeholderCount: Int)

    private val templateMatchers by lazy(LazyThreadSafetyMode.PUBLICATION) {
        AppLanguageCatalog.templateSources.map { source ->
            val token = Regex("\\{(\\d+)}")
            var last = 0
            var count = 0
            val pattern = buildString {
                append('^')
                token.findAll(source).forEach { match ->
                    append(Regex.escape(source.substring(last, match.range.first)))
                    append("(.*?)")
                    last = match.range.last + 1
                    count++
                }
                append(Regex.escape(source.substring(last)))
                append('$')
            }
            TemplateMatcher(Regex(pattern), count)
        }
    }

    fun translate(source: String, locale: Locale): String {
        val localeKey = localeKey(locale)
        if (localeKey == "en") return source

        exactIndex[source]?.let { index ->
            AppLanguageCatalog.exactTranslations[localeKey]?.getOrNull(index)?.let { return it }
        }

        templateMatchers.forEachIndexed { index, template ->
            val match = template.regex.matchEntire(source) ?: return@forEachIndexed
            var localized = AppLanguageCatalog.templateTranslations[localeKey]?.getOrNull(index)
                ?: return@forEachIndexed
            repeat(template.placeholderCount) { placeholder ->
                localized = localized.replace("{$placeholder}", match.groupValues[placeholder + 1])
            }
            return localized
        }

        // Unknown strings remain readable because source code itself is English.
        return source
    }

    internal fun localeKey(locale: Locale): String {
        val language = locale.language.lowercase(Locale.ROOT)
        if (language == "zh") {
            val script = locale.script.lowercase(Locale.ROOT)
            val country = locale.country.uppercase(Locale.ROOT)
            return if (script == "hant" || country in setOf("TW", "HK", "MO")) "zh-Hant" else "zh-Hans"
        }
        return when (language) {
            "en", "vi", "fr", "ja", "de", "es", "th", "id", "ko", "ru", "it" -> language
            else -> "en"
        }
    }
}
