package ru.forstudent.schedule.source

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ScheduleNetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** The site's private, undocumented HTTP contract lives only in source/. */
class IubipScheduleClient {
    private val endpoint = "https://iubip.ru/local/templates/univer/include/schedule/ajax/read-file-groups.php"

    fun fetch(group: String): String {
        val body = "do=schedule&group=" + URLEncoder.encode(group, Charsets.UTF_8.name())
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cookie", "beget=begetok")
            setRequestProperty("Referer", "https://iubip.ru/schedule/")
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) throw ScheduleNetworkException("HTTP $status от сайта расписания")
            val contentType = connection.contentType.orEmpty()
            if (!contentType.contains("json", ignoreCase = true) && !contentType.contains("text/plain", ignoreCase = true)) {
                throw ScheduleNetworkException("Сайт вернул не JSON: $contentType")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val text = reader.readText()
                if (text.length > 2_000_000) throw ScheduleNetworkException("Ответ слишком большой")
                text
            }
        } catch (error: IOException) {
            if (error is ScheduleNetworkException) throw error
            throw ScheduleNetworkException("Не удалось загрузить расписание: ${error.message}", error)
        } finally {
            connection.disconnect()
        }
    }
}
