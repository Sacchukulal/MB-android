package com.magicbill.app

import com.magicbill.app.counter.LineView
import com.magicbill.app.counter.Ops
import com.magicbill.app.ui.screens.floor.step
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantityReductionTest {
    private val sent = LineView(0, "Dosa", "2", "240.00", "No onion", "2", true)

    @Test fun two_sent_items_can_be_stepped_to_one_and_require_cancellation() {
        val target = step(sent.qty, -1)
        assertEquals("1", target)
        assertTrue(sent.needsKitchenCancellation(target))
        assertFalse(sent.needsKitchenCancellation("2"))
        assertFalse(sent.needsKitchenCancellation("3"))
    }

    @Test fun only_the_quantity_already_in_the_kitchen_needs_cancelling() {
        val partlySent = sent.copy(qty = "3", sentToKitchen = false)
        assertFalse(partlySent.needsKitchenCancellation("2"))
        assertTrue(partlySent.needsKitchenCancellation("1"))
        assertFalse(sent.copy(inKitchen = "0", sentToKitchen = false).needsKitchenCancellation("1"))
    }

    @Test fun quantity_buttons_use_whole_numbers_and_never_drop_below_one() {
        assertEquals("2", step("3", -1))
        assertEquals("1", step("2", -1))
        assertEquals("1", step("1", -1))
        assertEquals("2", step("1", +1))
        assertEquals("3", step("3.5", -1))
        assertEquals("4", step("3.5", +1))
        assertEquals("1", step("0.5", -1))
    }

    @Test fun cancellation_carries_the_original_line_so_stale_or_retried_changes_are_safe() {
        val request = Ops.reduceQty(sent, "1", "  Customer changed mind  ")
        assertEquals("reduce_qty", request["do"]!!.jsonPrimitive.content)
        assertEquals("1", request["qty"]!!.jsonPrimitive.content)
        assertEquals("Customer changed mind", request["reason"]!!.jsonPrimitive.content)
        assertEquals(sent.toJson(), request["expected"])
    }
}
