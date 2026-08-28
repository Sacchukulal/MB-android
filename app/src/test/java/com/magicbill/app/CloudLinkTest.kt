package com.magicbill.app

import com.magicbill.app.cloud.CloudLink
import com.magicbill.app.cloud.CloudSession
import com.magicbill.app.cloud.SessionStore
import com.magicbill.app.core.Answer
import com.magicbill.app.core.Clock
import com.magicbill.app.prefs.MemoryBox
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudLinkTest {
    private val server = FakeServer()
    private val box = MemoryBox()
    private val sessions = SessionStore(box)
    private var now = 1_000_000_000_000L
    private val link = CloudLink("https://cloud.test", "anon-key", server.client(), sessions, Clock { now })

    private fun signedIn(expiresIn: Long = 3_600_000) {
        sessions.save(CloudSession(CloudSession.Kind.OWNER, "tok-1", "ref-1", now + expiresIn, "o@x.in"))
    }

    @Test fun an_rpc_carries_the_key_and_the_token() = runTest {
        signedIn()
        server.once("POST", "/rest/v1/rpc/mb_my_restaurants", FakeServer.Reply(200, """[{"id":"r1","name":"Anna"}]"""))
        val a = link.rpc("mb_my_restaurants")
        assertTrue(a is Answer.Ok)
        val s = server.sent.single()
        assertEquals("anon-key", s.headers["apikey"])
        assertEquals("Bearer tok-1", s.headers["Authorization"])
    }

    @Test fun a_401_refreshes_once_and_retries() = runTest {
        signedIn()
        server.once("POST", "/rest/v1/rpc/x", FakeServer.Reply(401, """{"message":"JWT expired"}"""))
        server.once("POST", "/auth/v1/token?grant_type=refresh_token", FakeServer.Reply(200, """{"access_token":"tok-2","refresh_token":"ref-2","expires_in":3600}"""))
        server.once("POST", "/rest/v1/rpc/x", FakeServer.Reply(200, """{"ok":true}"""))
        val a = link.rpc("x")
        assertTrue(a.toString(), a is Answer.Ok)
        assertEquals("tok-2", sessions.current()?.accessToken)
        assertEquals("Bearer tok-2", server.sent.last().headers["Authorization"])
        assertEquals(3, server.sent.size)
    }

    @Test fun a_dead_refresh_token_signs_the_phone_out_but_a_network_failure_does_not() = runTest {
        signedIn()
        server.once("POST", "/rest/v1/rpc/x", FakeServer.Reply(401))
        server.fail("/auth/v1/token")
        val a = link.rpc("x")
        assertTrue(a is Answer.Unreachable)
        assertNotNull("a network failure keeps the session", sessions.current())

        server.once("POST", "/rest/v1/rpc/x", FakeServer.Reply(401))
        server.once("POST", "/auth/v1/token", FakeServer.Reply(400, """{"error":"invalid_grant"}"""))
        val b = link.rpc("x")
        assertTrue(b is Answer.SignedOut)
        assertNull("the server said the refresh token is dead", sessions.current())
    }

    @Test fun a_token_about_to_expire_is_refreshed_before_the_call() = runTest {
        signedIn(expiresIn = 30_000)
        server.once("POST", "/auth/v1/token?grant_type=refresh_token", FakeServer.Reply(200, """{"access_token":"tok-2","refresh_token":"ref-2","expires_in":3600}"""))
        server.once("POST", "/rest/v1/rpc/x", FakeServer.Reply(200, "[]"))
        link.rpc("x")
        assertEquals("Bearer tok-2", server.sent.last().headers["Authorization"])
    }

    @Test fun postgrest_refusals_are_sentences() = runTest {
        signedIn()
        server.once("POST", "/rest/v1/rpc/mb_save_staff", FakeServer.Reply(403, """{"code":"42501","message":"you may not manage staff here"}"""))
        val a = link.rpc("mb_save_staff", buildJsonObject { put("x", 1) })
        assertEquals(Answer.Refused("you may not manage staff here", "42501"), a)

        server.once("POST", "/rest/v1/rpc/y", FakeServer.Reply(429, "", mapOf("Retry-After" to "90")))
        val b = link.rpc("y") as Answer.Refused
        assertEquals(90, b.retryAfterSeconds)

        server.once("POST", "/rest/v1/rpc/z", FakeServer.Reply(503, "down"))
        assertTrue(link.rpc("z") is Answer.Unreachable)
    }

    @Test fun not_signed_in_is_said_without_a_call() = runTest {
        val a = link.rpc("x")
        assertTrue(a is Answer.SignedOut)
        assertTrue(server.sent.isEmpty())
    }

    @Test fun password_login() = runTest {
        server.once("POST", "/auth/v1/token?grant_type=password", FakeServer.Reply(400, """{"error_code":"invalid_credentials","msg":"Invalid login credentials"}"""))
        val bad = link.passwordLogin("o@x.in", "nope")
        assertEquals(Answer.Refused("That email and password do not match.", "invalid_credentials"), bad)

        server.once("POST", "/auth/v1/token?grant_type=password", FakeServer.Reply(200, """{"access_token":"a","refresh_token":"r","expires_in":3600,"user":{"email":"o@x.in"}}"""))
        val ok = link.passwordLogin("O@x.in ", "pw") as Answer.Ok
        assertEquals(CloudSession.Kind.OWNER, ok.value.kind)
        assertEquals("o@x.in", ok.value.email)
        assertEquals(now + 3_600_000, ok.value.expiresAtMs)
        assertEquals("a", sessions.current()?.accessToken)
    }

    @Test fun staff_login_is_the_one_metered_call_and_keeps_who_you_are() = runTest {
        server.once("POST", "/functions/v1/staff-login", FakeServer.Reply(200, """{"session":{"access_token":"s","refresh_token":"sr","expires_in":3600},"device_id":"d1","restaurant":{"id":"r1","name":"Anna's","short_code":"K7M2QX"},"staff":{"id":"st1","name":"Ravi"}}"""))
        val ok = link.staffLogin("k7m2qx", "ravi", "1234", "inst-1", "Ravi's phone", "3.0.0") as Answer.Ok
        assertEquals(CloudSession.Kind.STAFF, ok.value.kind)
        assertEquals("Ravi", ok.value.staff?.staffName)
        assertEquals("d1", ok.value.deviceId)
        assertTrue(server.sent.single().body.contains("\"restaurant_code\":\"K7M2QX\""))

        server.once("POST", "/functions/v1/staff-login", FakeServer.Reply(403, """{"code":"refused","message":"Ravi cannot sign in on a phone. Ask the owner."}"""))
        val refused = link.staffLogin("K7M2QX", "RAVI", "1234", "inst-1", "p", "3.0.0")
        assertEquals(Answer.Refused("Ravi cannot sign in on a phone. Ask the owner.", "refused"), refused)

        server.once("POST", "/functions/v1/staff-login", FakeServer.Reply(401, """{"code":"not_recognised"}"""))
        val bad = link.staffLogin("K7M2QX", "RAVI", "0000", "inst-1", "p", "3.0.0") as Answer.Refused
        assertEquals("not_recognised", bad.code)
    }
}
