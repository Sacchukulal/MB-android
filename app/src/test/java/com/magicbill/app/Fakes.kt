package com.magicbill.app

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/**
 * A server that lives inside the OkHttp client: each request is answered by the script, in
 * order of registration, and recorded. No sockets, no ports, no TLS — the client logic is what
 * is under test.
 */
class FakeServer : Interceptor {
    data class Sent(val method: String, val path: String, val body: String, val headers: Map<String, String>)
    data class Reply(val code: Int, val body: String = "", val headers: Map<String, String> = emptyMap())

    private val script = ArrayDeque<(Sent) -> Reply?>()
    val sent = ArrayList<Sent>()

    /** Answer the next request that matches, then forget the rule. */
    fun once(method: String? = null, pathContains: String? = null, reply: Reply) {
        script.addLast { s -> if ((method == null || s.method == method) && (pathContains == null || s.path.contains(pathContains))) reply else null }
    }

    fun always(handler: (Sent) -> Reply?) { script.addLast(handler) }

    fun fail(pathContains: String? = null) {
        script.addLast { s -> if (pathContains == null || s.path.contains(pathContains)) throw IOException("no route to host") else null }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val s = record(req)
        val reply = firstMatch(s) ?: throw IOException("no scripted reply for ${s.method} ${s.path}")
        val builder = Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(reply.code).message("")
            .body(reply.body.toResponseBody("application/json".toMediaType()))
        reply.headers.forEach { (k, v) -> builder.header(k, v) }
        return builder.build()
    }

    private fun firstMatch(s: Sent): Reply? {
        val it = script.iterator()
        while (it.hasNext()) {
            val h = it.next()
            val r = try {
                h(s)
            } catch (e: IOException) {
                if (h !in persistent) it.remove() // a scripted failure is spent, like a reply
                throw e
            }
            if (r != null) {
                if (h !in persistent) it.remove()
                return r
            }
        }
        return null
    }

    private val persistent = HashSet<(Sent) -> Reply?>()
    fun keep(handler: (Sent) -> Reply?) { persistent.add(handler); script.addLast(handler) }

    private fun record(req: Request): Sent {
        val body = req.body?.let { b -> okio.Buffer().also { b.writeTo(it) }.readUtf8() } ?: ""
        val s = Sent(req.method, req.url.encodedPath + (req.url.encodedQuery?.let { "?$it" } ?: ""), body, req.headers.names().associateWith { req.header(it) ?: "" })
        sent.add(s)
        return s
    }

    fun client(): OkHttpClient = OkHttpClient.Builder().addInterceptor(this).build()
}
