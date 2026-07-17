package com.magicbill.app.ui.screens.bills

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.core.Exporter
import com.magicbill.app.core.PermissionKey
import com.magicbill.app.core.billTime
import com.magicbill.app.core.formatINR
import com.magicbill.app.ui.components.MBButton
import com.magicbill.app.ui.components.MBErrorState
import com.magicbill.app.ui.components.MBLoadingState
import com.magicbill.app.ui.components.MBPermissionGate
import com.magicbill.app.ui.components.ScreenHeader
import com.magicbill.app.ui.screens.owner.BillDetailState
import com.magicbill.app.ui.screens.owner.BillDetailViewModel

/**
 * The bill rendered as a paper receipt — an intentional white "thermal slip"
 * floating on the canvas, monospace, dashed rules. Purely renders existing
 * bill data; sharing generates a PDF of this same slip.
 */
@Composable
fun BillDetailScreen(
    billId: String,
    isStaff: Boolean,
    onBack: () -> Unit,
    restaurantName: String = "Magic Bill",
    viewModel: BillDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(billId) { viewModel.load(billId, isStaff) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(title = "Receipt", onBack = onBack)

        when (val s = state) {
            is BillDetailState.Loading -> MBLoadingState(Modifier.fillMaxSize())

            is BillDetailState.Error -> MBErrorState(
                s.message,
                onRetry = { viewModel.load(billId, isStaff) },
            )

            is BillDetailState.Ready -> {
                val bill = s.bill
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(8.dp))
                    ReceiptSlip(bill, restaurantName)
                    Spacer(Modifier.height(20.dp))
                    MBPermissionGate(PermissionKey.ExportReports) {
                        MBButton(
                            "Share receipt",
                            onClick = {
                                Exporter.shareReceiptPdf(
                                    context = context,
                                    restaurantName = restaurantName,
                                    billNumber = bill.bill_number,
                                    tokenNumber = bill.token_number,
                                    billedAt = bill.billed_at,
                                    orderType = bill.order_type,
                                    tableNumber = bill.table_number,
                                    customerName = bill.customer_name,
                                    items = bill.items.orEmpty().map { Triple(it.name, it.quantity, it.price) },
                                    subtotal = bill.subtotal ?: 0.0,
                                    gst = bill.gst ?: 0.0,
                                    total = bill.total ?: 0.0,
                                    paymentMode = bill.payment_mode,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.navigationBarsPadding().height(24.dp))
                }
            }
        }
    }
}

private val Paper = Color(0xFFFDFDF8)
private val Ink = Color(0xFF16181D)
private val InkMuted = Color(0xFF6B7280)

@Composable
private fun ReceiptSlip(bill: com.magicbill.app.core.BillRow, restaurantName: String) {
    val mono = FontFamily.Monospace

    Column(
        Modifier
            .fillMaxWidth()
            .background(Paper, RoundedCornerShape(18.dp))
            .padding(horizontal = 22.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            restaurantName,
            fontFamily = mono, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            color = Ink, textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        val header = buildString {
            if (!bill.bill_number.isNullOrBlank()) append("Bill #${bill.bill_number}")
            if (bill.token_number != null) {
                if (isNotEmpty()) append(" · ")
                append("Token ${bill.token_number}")
            }
        }
        if (header.isNotEmpty()) {
            Text(
                header,
                fontFamily = mono, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = Ink, textAlign = TextAlign.Center,
            )
        }
        Text(
            billTime(bill.billed_at),
            fontFamily = mono, fontSize = 12.sp, color = InkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        val typeLine = buildString {
            if (!bill.order_type.isNullOrBlank()) append(bill.order_type)
            if (!bill.table_number.isNullOrBlank()) {
                if (isNotEmpty()) append(" · ")
                append("Table ${bill.table_number}")
            }
        }
        if (typeLine.isNotEmpty()) {
            Text(typeLine, fontFamily = mono, fontSize = 12.sp, color = InkMuted)
        }
        if (!bill.customer_name.isNullOrBlank()) {
            Text(
                "Customer: ${bill.customer_name}",
                fontFamily = mono, fontSize = 12.sp, color = InkMuted,
            )
        }

        DashedRule()

        bill.items.orEmpty().forEach { item ->
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(item.name, fontFamily = mono, fontSize = 13.sp, color = Ink)
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "  ${item.quantity.toLong()} × ${formatINR(item.price)}",
                        fontFamily = mono, fontSize = 12.sp, color = InkMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatINR(item.price * item.quantity),
                        fontFamily = mono, fontSize = 13.sp, color = Ink,
                    )
                }
            }
        }

        DashedRule()

        ReceiptTotal("Subtotal", formatINR(bill.subtotal ?: 0.0), false)
        ReceiptTotal("GST", formatINR(bill.gst ?: 0.0), false)
        ReceiptTotal("TOTAL", formatINR(bill.total ?: 0.0), true)
        if (!bill.payment_mode.isNullOrBlank()) {
            ReceiptTotal("Paid by", bill.payment_mode!!, false)
        }

        DashedRule()

        Text("Thank you! Visit again.", fontFamily = mono, fontSize = 12.sp, color = InkMuted)
        Text("Powered by Magic Bill", fontFamily = mono, fontSize = 11.sp, color = InkMuted)
    }
}

@Composable
private fun ReceiptTotal(label: String, value: String, emphasize: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontSize = if (emphasize) 15.sp else 13.sp,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
            color = Ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            fontFamily = FontFamily.Monospace,
            fontSize = if (emphasize) 15.sp else 13.sp,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
            color = Ink,
        )
    }
}

@Composable
private fun DashedRule() {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(17.dp),
    ) {
        drawLine(
            color = InkMuted.copy(alpha = 0.6f),
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f), 0f),
        )
    }
}
