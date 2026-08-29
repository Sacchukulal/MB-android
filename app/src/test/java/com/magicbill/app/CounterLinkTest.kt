package com.magicbill.app

import com.magicbill.app.core.Answer
import com.magicbill.app.counter.CounterLink
import com.magicbill.app.counter.Credential
import com.magicbill.app.counter.Fingerprints
import com.magicbill.app.counter.Intent
import com.magicbill.app.counter.Ops
import com.magicbill.app.counter.Outcome
import com.magicbill.app.counter.PairCode
import com.magicbill.app.counter.PinnedTrust
import com.magicbill.app.counter.RecordingTrust
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class PinningTest {
    private val cert: X509Certificate = javaClass.getResourceAsStream("/counter.pem")!!.use {
        CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
    }
    private val fingerprint = javaClass.getResourceAsStream("/counter.fingerprint")!!.bufferedReader().readText().trim()

    @Test fun the_pinned_certificate_is_the_only_one_accepted() {
        assertEquals(fingerprint, Fingerprints.sha256Hex(cert))
        PinnedTrust(fingerprint).checkServerTrusted(arrayOf(cert), "ECDHE_ECDSA")
        PinnedTrust("sha256:" + fingerprint.uppercase()).checkServerTrusted(arrayOf(cert), "ECDHE_ECDSA")
        try {
            PinnedTrust("0".repeat(64)).checkServerTrusted(arrayOf(cert), "ECDHE_ECDSA")
            fail("a different certificate must be refused")
        } catch (e: CertificateException) {
            assertEquals("That is not the till on the code.", e.message)
        }
        assertTrue("the platform store is never consulted", PinnedTrust(fingerprint).acceptedIssuers.isEmpty())
    }

    @Test fun the_recording_trust_says_what_it_saw() {
        val r = RecordingTrust()
        assertNull(r.seenFingerprint)
        r.checkServerTrusted(arrayOf(cert), "x")
        assertEquals(fingerprint, r.seenFingerprint)
    }

    @Test fun every_spelling_of_a_fingerprint() {
        val raw = fingerprint.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val b64url = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        assertEquals(43, b64url.length)
        assertEquals(fingerprint, Fingerprints.normalise(b64url))
        assertEquals(fingerprint, Fingerprints.normalise("sha256:$fingerprint"))
        assertEquals(fingerprint, Fingerprints.normalise(fingerprint.chunked(2).joinToString(":").uppercase()))
        assertNull(Fingerprints.normalise("hello"))
        assertTrue(Fingerprints.same(b64url, "sha256:$fingerprint"))
    }

    @Test fun the_qr() {
        val code = PairCode.parse("magicbill://pair?h=192.168.1.7&p=7431&f=" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 7 }) + "&t=8GF-CVC")
        assertNotNull(code)
        assertEquals("192.168.1.7", code!!.host)
        assertEquals(7431, code.port)
        assertEquals("07".repeat(32), code.fingerprint)
        assertEquals("8GF-CVC", code.token)
        assertNull(PairCode.parse("https://example.com"))
        assertNull(PairCode.parse("magicbill://pair?h=x&p=nope&f=bad&t=1"))
    }
}

class CounterLinkTest {
    private val server = FakeServer()
    private val link = CounterLink(clientFactory = { server.client() })
    private val cred = Credential("192.168.1.7", 7431, "0".repeat(64), "srv_1", "Anna's", "dev_1", "secret")

    @Test fun an_intent_is_signed_and_versioned_and_the_outcome_is_the_answer() = runTest {
        server.once("POST", "/v1/intent", FakeServer.Reply(409, """{"outcome":"refused","message":"The kitchen has already made this."}"""))
        val a = link.intent(cred, Intent("i1", "ord_1", 1L, Ops.addItem("itm_dosa", "2", null))) as Answer.Ok
        assertEquals(Outcome.Refused("The kitchen has already made this."), a.value)
        val s = server.sent.single()
        assertEquals("Bearer dev_1.secret", s.headers["Authorization"])
        assertEquals("1", s.headers["x-magicbill-version"])
        assertTrue(s.body.contains("\"do\":\"add_item\""))
        assertTrue("an intent has nowhere to put money", !s.body.contains("price") && !s.body.contains("total"))

        server.once("POST", "/v1/intent", FakeServer.Reply(200, """{"outcome":"ok","order_id":"ord_1","total":"240.00","lines":[{"line":0,"name":"Masala Dosa","qty":"2","amount":"240.00","note":null,"sent_to_kitchen":false}],"token":"7","note":null}"""))
        val ok = (link.intent(cred, Intent("i2", "ord_1", 1L, Ops.sendToKitchen())) as Answer.Ok).value as Outcome.Ok
        assertEquals("240.00", ok.total)
        assertEquals("7", ok.token)
        assertEquals("Masala Dosa", ok.lines.single().name)

        server.once("POST", "/v1/intent", FakeServer.Reply(202, """{"outcome":"held","message":"This was typed more than 12 hours ago.","batch_id":"b1"}"""))
        assertTrue((link.intent(cred, Intent("i3", null, 1L, Ops.printBill())) as Answer.Ok).value is Outcome.Held)
    }

    @Test fun the_counters_sentences_travel_as_is() = runTest {
        server.once("GET", "/v1/me", FakeServer.Reply(401, """{"message":"This phone has been removed from the counter. Ask somebody at the till to add it again."}"""))
        val gone = link.me(cred)
        assertEquals(Answer.SignedOut("This phone has been removed from the counter. Ask somebody at the till to add it again."), gone)

        server.once("GET", "/v1/me", FakeServer.Reply(426, """{"message":"This phone's app is older than the counter. Update the app."}"""))
        val old = link.me(cred) as Answer.Refused
        assertEquals("upgrade", old.code)

        server.once("GET", "/v1/me", FakeServer.Reply(429, """{"message":"The counter is busy. Try again in 3 seconds."}""", mapOf("Retry-After" to "3")))
        assertEquals(3, (link.me(cred) as Answer.Refused).retryAfterSeconds)

        server.fail()
        assertTrue(link.me(cred) is Answer.Unreachable)
    }

    @Test fun the_catalogue_says_unchanged_with_a_304() = runTest {
        server.once("GET", "/v1/catalogue?version=v9", FakeServer.Reply(304))
        assertEquals(Answer.Ok(null), link.catalogue(cred, "v9"))
        server.once("GET", "/v1/catalogue", FakeServer.Reply(200, """{"version":"v10","items":[{"id":"i","name":"Idli","category":"Tiffin","price":"40.00","is_available":false}],"tables":[{"id":"t1","label":"7","section":"Hall","seats":4,"state":"free"}]}"""))
        val c = (link.catalogue(cred, null) as Answer.Ok).value!!
        assertEquals("v10", c.version)
        assertEquals(false, c.items.single().isAvailable)
        assertEquals("Hall", c.tables.single().section)
    }

    @Test fun pairing_waits_for_allow_and_the_counter_hands_over_the_cloud_login() = runTest {
        server.once("POST", "/v1/pair", FakeServer.Reply(202, """{"request_id":"rq1","message":"Waiting…"}"""))
        val asked = (link.pair("h", 1, "0".repeat(64), "Vivo V2443", "8GF-CVC", "inst-1") as Answer.Ok).value
        assertEquals("rq1", asked)
        assertTrue(server.sent.single().body.contains("\"name\":\"Vivo V2443\"") && server.sent.single().body.contains("\"install\":\"inst-1\""))

        server.once("GET", "/v1/pair/rq1", FakeServer.Reply(202, """{"message":"Waiting for somebody at the counter to allow this phone."}"""))
        assertEquals(Answer.Ok(null), link.pairStatus("h", 1, "0".repeat(64), "rq1"))
        server.once("GET", "/v1/pair/rq1", FakeServer.Reply(200, """{"device_id":"dev_9","secret":"s9","server_id":"srv_1"}"""))
        assertEquals("dev_9", (link.pairStatus("h", 1, "0".repeat(64), "rq1") as Answer.Ok).value?.deviceId)
        server.once("GET", "/v1/pair/rq1", FakeServer.Reply(400, """{"message":"That code has expired or has already been used."}"""))
        assertTrue(link.pairStatus("h", 1, "0".repeat(64), "rq1") is Answer.Refused)

        val cred = Credential("h", 1, "0".repeat(64), "srv_1", "Anna", "dev_9", "s9")
        server.once("POST", "/v1/cloud-login", FakeServer.Reply(200, """{"session":{"access_token":"s","refresh_token":"sr","expires_in":3600},"device_id":"d1","restaurant":{"id":"r1","name":"Anna's","short_code":"K7M2QX"},"staff":{"id":"st1","name":"Ravi"}}"""))
        val login = (link.cloudLogin(cred) as Answer.Ok).value
        assertEquals("d1", login["device_id"].toString().trim('"'))
        assertEquals("Bearer dev_9.s9", server.sent.last().headers["Authorization"])
        server.once("POST", "/v1/cloud-login", FakeServer.Reply(403, """{"message":"The counter cannot reach the cloud right now. Orders still work; reports come once it can."}"""))
        val refused = link.cloudLogin(cred) as Answer.Refused
        assertTrue(refused.sentence.startsWith("The counter cannot reach the cloud"))
    }

    @Test fun a_batch_answers_per_intent() = runTest {
        server.once("POST", "/v1/batch", FakeServer.Reply(200, """{"outcomes":[["a",{"outcome":"ok","order_id":"o","total":"1.00","lines":[],"token":null,"note":null}],["b",{"outcome":"held","message":"old","batch_id":"x"}]],"says":"1 change is waiting for somebody at the counter to say whether they still apply."}"""))
        val r = (link.batch(cred, listOf(Intent("a", null, 1, Ops.printBill()), Intent("b", null, 1, Ops.printBill()))) as Answer.Ok).value
        assertEquals(2, r.outcomes.size)
        assertTrue(r.says.startsWith("1 change is waiting"))
        assertTrue(r.outcomes[1].second is Outcome.Held)
    }
}
