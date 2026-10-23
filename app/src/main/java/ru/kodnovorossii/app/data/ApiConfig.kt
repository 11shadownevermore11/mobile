package ru.kodnovorossii.app.data

import ru.kodnovorossii.app.BuildConfig

/**
 * Единственный адрес сервера «Кода Новороссии» (API и сайт на одном домене).
 * Задаётся в одном месте — app/build.gradle.kts (API_URL). Все запросы, фото и ссылки на сайт строятся от него.
 */
object ApiConfig {
    val BASE: String = BuildConfig.API_URL.trimEnd('/')

    /** Полный адрес по пути сервера: "/api/places" → "https://…/api/places". */
    fun url(path: String): String = BASE + (if (path.startsWith("/")) path else "/$path")
}
