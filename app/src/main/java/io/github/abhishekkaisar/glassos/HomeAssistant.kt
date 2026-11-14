package io.github.abhishekkaisar.glassos.personal

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal local Home Assistant REST client for the lamp button: read one entity's state, toggle it, and list the
 * entities that can be toggled. Blocking calls; run them off the main thread.
 */
object HomeAssistant {
    class Entity(val id: String, val name: String, val on: Boolean?)

    /** The token was refused (401/403): the user needs a new long-lived access token. */
    class AuthException : IOException("Home Assistant refused the access token")

    private fun open(base: String, token: String, path: String, method: String = "GET"): HttpURLConnection {
        val c = URL(HaLogic.normalizeUrl(base) + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 4000
        c.readTimeout = 6000
        c.useCaches = false
        c.setRequestProperty("Authorization", "Bearer " + token.trim())
        c.setRequestProperty("Content-Type", "application/json")
        return c
    }

    private fun read(c: HttpURLConnection): String {
        val code = c.responseCode
        if (code == 401 || code == 403) throw AuthException()
        if (code !in 200..299) throw IOException("Home Assistant answered HTTP $code")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    /** True on, false off, null when the entity is unavailable or unknown. */
    fun state(base: String, token: String, entity: String): Boolean? {
        val c = open(base, token, "/api/states/$entity")
        try {
            return HaLogic.isOn(JSONObject(read(c)).optString("state"))
        } finally { c.disconnect() }
    }

    fun toggle(base: String, token: String, entity: String) {
        val c = open(base, token, "/api/services/${HaLogic.domain(entity)}/toggle", "POST")
        try {
            c.doOutput = true
            val body = JSONObject().put("entity_id", entity).toString().toByteArray(Charsets.UTF_8)
            c.outputStream.use { it.write(body) }
            read(c)
        } finally { c.disconnect() }
    }

    /** Lights, switches, input booleans and fans, lamps first. Also checks the address and the token. */
    fun toggleable(base: String, token: String): List<Entity> {
        val c = open(base, token, "/api/states")
        try {
            val arr = JSONArray(read(c))
            val out = ArrayList<Entity>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("entity_id")
                if (!HaLogic.isToggleable(id)) continue
                val name = (o.optJSONObject("attributes")?.optString("friendly_name") ?: "").trim().ifEmpty { id }
                out.add(Entity(id, name, HaLogic.isOn(o.optString("state"))))
            }
            return out.sortedWith(compareBy<Entity>({ HaLogic.rank(it.id, it.name) }, { it.name.lowercase() }))
        } finally { c.disconnect() }
    }

    /** A short, plain message for a failed call. */
    fun describe(t: Throwable): String = when (t) {
        is AuthException -> "Home Assistant refused the token"
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException,
        is java.net.NoRouteToHostException -> "Home Assistant is not reachable"
        is javax.net.ssl.SSLException -> "Home Assistant HTTPS certificate not accepted"
        is java.net.MalformedURLException -> "Home Assistant address is not valid"
        else -> "Home Assistant: " + (t.message ?: t.javaClass.simpleName)
    }
}
