package com.example.bookswap.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Title/author fetched for an ISBN. */
data class IsbnResult(val title: String, val author: String)

/**
 * Looks up book metadata by ISBN via the Open Library Books API (keyless).
 *
 * PRD Epic 3: a valid ISBN auto-populates Title & Author, which stay editable before
 * saving. Returns null if nothing is found or the request fails — the form then just
 * lets the user type the fields manually.
 *
 * A plain HttpURLConnection GET is used rather than pulling in Retrofit for a single
 * read-only endpoint.
 */
suspend fun lookupIsbn(isbn: String): IsbnResult? = withContext(Dispatchers.IO) {
    val cleaned = isbn.filter { it.isDigit() || it == 'X' || it == 'x' }
    if (cleaned.length != 10 && cleaned.length != 13) return@withContext null

    val url = URL("https://openlibrary.org/api/books?bibkeys=ISBN:$cleaned&jscmd=data&format=json")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 10_000
    }
    try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(body)
        val entry = root.optJSONObject("ISBN:$cleaned") ?: return@withContext null

        val title = entry.optString("title").ifBlank { return@withContext null }
        val authors = entry.optJSONArray("authors")
        val author = buildString {
            if (authors != null) {
                for (i in 0 until authors.length()) {
                    val name = authors.getJSONObject(i).optString("name")
                    if (name.isNotBlank()) {
                        if (isNotEmpty()) append(", ")
                        append(name)
                    }
                }
            }
        }
        IsbnResult(title = title, author = author)
    } catch (e: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}
