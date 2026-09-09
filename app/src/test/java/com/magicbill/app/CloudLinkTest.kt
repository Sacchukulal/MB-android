package com.magicbill.app

import com.magicbill.app.cloud.CloudLink
import com.magicbill.app.cloud.CloudSession
import com.magicbill.app.cloud.SessionStore
import com.magicbill.app.cloud.SignUp
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
    private val link = CloudLink("https://cloud.test", "anon-key", server.client(), sessions, Clock { now }, siteUrl = "https://site.test")

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

    @Test fun a_4xx_that_does_not_name_a_dead_token_keeps_the_session() = runTest {
        signedIn()
        // A rate limit at the auth door, a proxy's HTML page, an empty body: "not now", never "signed out".
        for (reply in listOf(
            FakeServer.Reply(400, """{"error_code":"over_request_rate_limit","msg":"Request rate limit reached"}"""),
            FakeServer.Reply(403, "<html>blocked</html>"),
            FakeServer.Reply(401, ""),
        )) {
            server.once("POST", "/rest/v1/rpc/x", FakeServer.Reply(401))
            server.once("POST", "/auth/v1/token", reply)
            val a = link.rpc("x")
            assertTrue("$reply → $a", a is Answer.Unreachable)
            assertNotNull("kept after $reply", sessions.current())
        }
        // The newer Supabase wording is a verdict too.
        server.once("POST", "/rest/v1/rpc/x", FakeServer.Reply(401))
        server.once("POST", "/auth/v1/token", FakeServer.Reply(400, """{"code":400,"error_code":"refresh_token_not_found","msg":"Invalid Refresh Token: Refresh Token Not Found"}"""))
        assertTrue(link.rpc("x") is Answer.SignedOut)
        assertNull(sessions.current())
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

    @Test fun a_login_the_counter_fetched_is_kept_as_a_staff_session_without_a_call() = runTest {
        val o = kotlinx.serialization.json.Json.parseToJsonElement("""{"session":{"access_token":"s","refresh_token":"sr","expires_in":3600},"device_id":"d1","restaurant":{"id":"r1","name":"Anna's","short_code":"K7M2QX"},"staff":{"id":"st1","name":"Ravi"}}""") as kotlinx.serialization.json.JsonObject
        val ok = link.adoptCounterLogin(o)!!
        assertEquals(CloudSession.Kind.STAFF, ok.kind)
        assertEquals("Ravi", ok.staff?.staffName)
        assertEquals("d1", ok.deviceId)
        assertEquals("s", sessions.current()?.accessToken)
        assertTrue("the phone made no call of its own", server.sent.isEmpty())
        assertNull(link.adoptCounterLogin(kotlinx.serialization.json.buildJsonObject { }))
    }

    @Test fun a_sign_up_goes_to_the_websites_route_with_the_sites_own_fields() = runTest {
        server.once("POST", "/api/auth/signup", FakeServer.Reply(200, """{"ok":true}"""))
        val form = SignUp(" Asha ", "Anna Kuteera", "12 MG Road, Udupi", "98765 01234", " Asha@x.in", "secret-pw")
        assertTrue(link.signUp(form) is Answer.Ok)
        val s = server.sent.single()
        assertEquals("/api/auth/signup", s.path)
        val o = kotlinx.serialization.json.Json.parseToJsonElement(s.body) as kotlinx.serialization.json.JsonObject
        assertEquals("Asha", (o["name"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("Anna Kuteera", (o["restaurantName"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("12 MG Road, Udupi", (o["restaurantAddress"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("9876501234", (o["phone"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("Asha@x.in", (o["email"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("secret-pw", (o["password"] as kotlinx.serialization.json.JsonPrimitive).content)
        // The website's route, not the cloud's: no anon key, and no session until the login.
        assertNull(s.headers["apikey"])
        assertNull(sessions.current())
    }

    @Test fun the_websites_refusal_is_shown_as_it_was_written() = runTest {
        val form = SignUp("Asha", "Anna", "Udupi", "9876501234", "a@x.in", "secret-pw")
        server.once("POST", "/api/auth/signup", FakeServer.Reply(409, """{"error":"An account with this email already exists — try logging in."}"""))
        val taken = link.signUp(form) as Answer.Refused
        assertEquals("An account with this email already exists — try logging in.", taken.sentence)
        assertEquals("exists", taken.code)

        server.once("POST", "/api/auth/signup", FakeServer.Reply(429, """{"error":"Too many attempts. Try again in a minute."}"""))
        assertEquals("Too many attempts. Try again in a minute.", (link.signUp(form) as Answer.Refused).sentence)

        server.fail("/api/auth/signup")
        assertTrue(link.signUp(form) is Answer.Unreachable)
    }
}
