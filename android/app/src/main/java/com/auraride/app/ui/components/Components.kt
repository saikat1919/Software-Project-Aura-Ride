package com.auraride.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import com.auraride.app.ui.theme.AuraColors

/** Checkmark-in-shield — the consistent trust motif. Drawn, not an emoji. */
@Composable
fun ShieldCheck(color: Color, sizeDp: Int = 16, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(sizeDp.dp).semantics { contentDescription = "Verified" }) {
        val w = size.width; val h = size.height
        val shield = Path().apply {
            moveTo(w * 0.5f, h * 0.04f)
            lineTo(w * 0.92f, h * 0.20f)
            lineTo(w * 0.92f, h * 0.52f)
            cubicTo(w * 0.92f, h * 0.80f, w * 0.72f, h * 0.95f, w * 0.5f, h * 0.98f)
            cubicTo(w * 0.28f, h * 0.95f, w * 0.08f, h * 0.80f, w * 0.08f, h * 0.52f)
            lineTo(w * 0.08f, h * 0.20f)
            close()
        }
        drawPath(shield, color)
        // check mark
        val check = Path().apply {
            moveTo(w * 0.33f, h * 0.52f)
            lineTo(w * 0.46f, h * 0.66f)
            lineTo(w * 0.70f, h * 0.38f)
        }
        drawPath(check, Color.White, style = Stroke(width = w * 0.09f))
    }
}

/** Persistent "Verified · women only" strip — top of every authenticated screen. */
@Composable
fun VerifiedStrip(modifier: Modifier = Modifier) {
    val ext = AuraColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ext.verifiedContainer)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShieldCheck(color = ext.verified, sizeDp = 15)
        Spacer(Modifier.width(7.dp))
        Text(
            "Verified · women only",
            color = ext.verified,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        )
    }
}

/** Small green "Verified" pill for profile/driver cards. */
@Composable
fun VerifiedBadge(modifier: Modifier = Modifier) {
    val ext = AuraColors.current
    Row(
        modifier = modifier
            .background(ext.verifiedContainer, RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShieldCheck(color = ext.verified, sizeDp = 12)
        Spacer(Modifier.width(4.dp))
        Text("Verified", color = ext.verified, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

/** Primary action — bottom-anchored, 44px+ target (a11y). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}
