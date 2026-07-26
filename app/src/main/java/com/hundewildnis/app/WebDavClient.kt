package com.hundewildnis.app

import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Einfacher WebDAV-Client für die HundeWildnis-Synchronisation.
 *
 * Bewusst ohne OkHttp umgesetzt (kein zusätzliches Build-Dependency),
 * sondern mit java.net.HttpURLConnection - analog zum bestehenden
 * Muster in NasenarbeitMapActivity (ORS/Open-Meteo Aufrufe).
 *
 * Unterstützt: PROPFIND (Existenz-/Listen-Check), GET, PUT, MKCOL.
 * Authentifizierung: HTTP Basic Auth.
 */
class WebDavClient(
    serverUrl: String,
    private val username: String,
    private val password: String
) {

    companion object {
        private const val TAG = "WebDavClient"
        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 30000
        private const val USER_AGENT = "HundeWildnisApp/13.4"
    }

    private val baseUrl: String = serverUrl.trimEnd('/')

    private val authHeader: String by lazy {
        val raw = "$username:$password"
        "Basic " + Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    /** Baut die volle URL für einen relativen Pfad, mit URL-Encoding pro Segment. */
    private fun buildUrl(path: String): String {
        if (path.isEmpty()) return baseUrl
        val endsWithSlash = path.endsWith("/")
        val encodedSegments = path.trim('/').split('/').filter { it.isNotEmpty() }.joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        val result = if (encodedSegments.isEmpty()) baseUrl else "$baseUrl/$encodedSegments"
        return if (endsWithSlash && !result.endsWith("/")) "$result/" else result
    }

    private fun openConnection(path: String, method: String): HttpURLConnection {
        val urlStr = buildUrl(path)
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        setRequestMethod(conn, method)
        conn.setRequestProperty("Authorization", authHeader)
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        return conn
    }

    /**
     * HttpURLConnection.setRequestMethod() prüft gegen eine feste Whitelist
     * (GET, POST, PUT, DELETE, HEAD, OPTIONS, TRACE, PATCH) und wirft für
     * WebDAV-Methoden wie PROPFIND oder MKCOL eine ProtocolException.
     *
     * Workaround: Das interne "method"-Feld der Connection (bzw. ihres
     * Delegates) wird per Reflection direkt gesetzt, um die Prüfung zu
     * umgehen. Das ist der in der Android-Welt etablierte Standard-Workaround
     * für WebDAV mit HttpURLConnection (ohne zusätzliches HTTP-Client-Dependency).
     */
    private fun setRequestMethod(connection: HttpURLConnection, method: String) {
        try {
            connection.requestMethod = method
            return // Methode stand bereits auf der Whitelist (GET, PUT, ...)
        } catch (e: java.net.ProtocolException) {
            // Erwarteter Fall für PROPFIND/MKCOL - per Reflection fortfahren
        }

        try {
            // Manche Implementierungen (z.B. HttpsURLConnectionImpl) delegieren
            // an ein internes "delegate"-Objekt - dieses ggf. zuerst auflösen.
            var target: Any = connection
            try {
                val delegateField = connection.javaClass.getDeclaredField("delegate")
                delegateField.isAccessible = true
                (delegateField.get(connection) as? Any)?.let { target = it }
            } catch (e: NoSuchFieldException) {
                // Kein Delegate vorhanden - direkt auf "connection" arbeiten
            }

            var klass: Class<*>? = target.javaClass
            var fieldSet = false
            while (klass != null && !fieldSet) {
                try {
                    val methodField = klass.getDeclaredField("method")
                    methodField.isAccessible = true
                    methodField.set(target, method)
                    fieldSet = true
                } catch (e: NoSuchFieldException) {
                    klass = klass.superclass
                }
            }

            if (!fieldSet) {
                throw java.io.IOException("Konnte HTTP-Methode $method nicht setzen (Reflection fehlgeschlagen)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "setRequestMethod($method) per Reflection fehlgeschlagen", e)
            throw java.io.IOException("WebDAV-Methode $method wird von diesem Gerät nicht unterstützt: ${e.message}")
        }
    }

    /**
     * Legt ein Verzeichnis an (MKCOL). Gibt auch bei "existiert bereits" (405) Erfolg zurück.
     */
    fun createDirectory(path: String): Result<Unit> {
        return try {
            val conn = openConnection(path, "MKCOL")
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299 || code == 405) {
                Result.success(Unit)
            } else {
                Result.failure(java.io.IOException("MKCOL fehlgeschlagen: HTTP $code"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "createDirectory($path) fehlgeschlagen", e)
            Result.failure(e)
        }
    }

    /**
     * Listet Dateinamen in einem Verzeichnis via PROPFIND (Depth: 1).
     * Gibt nur die Dateinamen zurück (nicht die vollen Pfade), ohne das Verzeichnis selbst.
     *
     * Für Änderungserkennung (bedingte Downloads) siehe [listFilesDetailed].
     */
    fun listFiles(path: String): Result<List<String>> =
        listFilesDetailed(path).map { list -> list.map { it.name } }

    /**
     * Listet Dateien in einem Verzeichnis via PROPFIND (Depth: 1) inklusive
     * ETag/Größe/letzter Änderung. Wird für die Sync-Optimierung genutzt:
     * Ein Download lohnt sich nur, wenn sich diese Metadaten seit dem
     * letzten Sync geändert haben (siehe WebDavSyncManager).
     */
    fun listFilesDetailed(path: String): Result<List<WebDavFileInfo>> {
        return try {
            // Wichtig: Verzeichnisse für PROPFIND sollten auf / enden
            val normalizedPath = if (path.isEmpty() || path.endsWith("/")) path else "$path/"
            val conn = openConnection(normalizedPath, "PROPFIND")
            conn.setRequestProperty("Depth", "1")
            conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            conn.doOutput = true

            val body = """<?xml version="1.0" encoding="utf-8"?>
                |<propfind xmlns="DAV:"><prop><displayname/><getetag/><getcontentlength/><getlastmodified/></prop></propfind>
            """.trimMargin()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code != 207 && code !in 200..299) {
                conn.disconnect()
                return Result.failure(java.io.IOException("PROPFIND fehlgeschlagen: HTTP $code"))
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val dirName = path.trim('/').substringAfterLast('/')

            // Extrem flexibler Regex für verschiedene WebDAV-Dialekte
            val result = Regex("<[^>]*?response\\b[^>]*?>(.*?)</[^>]*?response>", 
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .findAll(response)
                .mapNotNull { block ->
                    val content = block.groupValues[1]
                    val href = Regex("<[^>]*?href\\b[^>]*?>(.*?)</[^>]*?href>", 
                        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                        .find(content)?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
                    
                    val decodedHref = java.net.URLDecoder.decode(href.trimEnd('/'), "UTF-8")
                    val name = decodedHref.substringAfterLast('/')
                    
                    if (name.isBlank() || name.equals(dirName, ignoreCase = true)) return@mapNotNull null

                    val etag = Regex("<[^>]*?getetag\\b[^>]*?>(.*?)</[^>]*?getetag>", RegexOption.IGNORE_CASE)
                        .find(content)?.groupValues?.get(1)?.trim()?.trim('"')?.takeIf { it.isNotBlank() }
                    val size = Regex("<[^>]*?getcontentlength\\b[^>]*?>(.*?)</[^>]*?getcontentlength>", RegexOption.IGNORE_CASE)
                        .find(content)?.groupValues?.get(1)?.trim()?.toLongOrNull()
                    val lastModified = Regex("<[^>]*?getlastmodified\\b[^>]*?>(.*?)</[^>]*?getlastmodified>", RegexOption.IGNORE_CASE)
                        .find(content)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

                    WebDavFileInfo(name = name, etag = etag, size = size, lastModified = lastModified)
                }
                .toList()

            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "listFilesDetailed($path) fehlgeschlagen", e)
            Result.failure(e)
        }
    }

    /**
     * Lädt eine Binärdatei vom Server herunter und speichert sie in [destinationFile].
     */
    fun downloadFile(remotePath: String, destinationFile: File): Result<Unit> {
        return try {
            val conn = openConnection(remotePath, "GET")
            val code = conn.responseCode
            if (code == 404) {
                conn.disconnect()
                return Result.failure(WebDavNotFoundException("Datei nicht gefunden: $remotePath"))
            }
            if (code !in 200..299) {
                conn.disconnect()
                return Result.failure(java.io.IOException("GET fehlgeschlagen: HTTP $code"))
            }
            conn.inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            conn.disconnect()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "downloadFile($remotePath) fehlgeschlagen", e)
            Result.failure(e)
        }
    }

    /**
     * Lädt eine lokale Datei zum Server hoch (PUT). Überschreibt vorhandene Dateien.
     */
    fun uploadFile(remotePath: String, localFile: File): Result<Unit> {
        return try {
            val conn = openConnection(remotePath, "PUT")
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setRequestProperty("Content-Length", localFile.length().toString())
            conn.doOutput = true

            localFile.inputStream().use { input ->
                conn.outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(java.io.IOException("PUT fehlgeschlagen: HTTP $code"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadFile($remotePath) fehlgeschlagen", e)
            Result.failure(e)
        }
    }

    /**
     * Löscht eine Datei auf dem Server (DELETE).
     */
    fun deleteFile(remotePath: String): Result<Unit> {
        return try {
            val conn = openConnection(remotePath, "DELETE")
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299 || code == 404) {
                Result.success(Unit)
            } else {
                Result.failure(java.io.IOException("DELETE fehlgeschlagen: HTTP $code"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteFile($remotePath) fehlgeschlagen", e)
            Result.failure(e)
        }
    }

    /**
     * Prüft die Verbindung mit einem PROPFIND auf das Basis-Verzeichnis.
     * Gibt den HTTP-Statuscode zurück (Erfolg = Verbindung steht, unabhängig vom Code-Wert).
     */
    fun testConnection(): Result<Int> {
        return try {
            val conn = openConnection("", "PROPFIND")
            conn.setRequestProperty("Depth", "0")
            conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            conn.doOutput = true
            conn.outputStream.use {
                it.write("""<?xml version="1.0"?><propfind xmlns="DAV:"><prop/></propfind>""".toByteArray())
            }
            val code = conn.responseCode
            conn.disconnect()
            Result.success(code)
        } catch (e: Exception) {
            Log.e(TAG, "testConnection() fehlgeschlagen", e)
            Result.failure(e)
        }
    }
}

/** Markiert einen 404-Fall getrennt von allgemeinen Netzwerkfehlern. */
class WebDavNotFoundException(message: String) : java.io.IOException(message)

/**
 * Metadaten einer Remote-Datei aus einer PROPFIND-Antwort.
 * [etag] ist der zuverlässigste Änderungsindikator, falls vom Server geliefert
 * (nicht alle WebDAV-Server, z.B. manche Storage-Boxen, liefern ihn zuverlässig).
 * [size]/[lastModified] dienen als Fallback zur Änderungserkennung.
 */
data class WebDavFileInfo(
    val name: String,
    val etag: String?,
    val size: Long?,
    val lastModified: String?
)