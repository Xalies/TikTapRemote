package com.xalies.tiktapremote

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.ProductDetails

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val productDetails by BillingManager.productDetails.collectAsState()
    val purchaseStatus by BillingManager.purchaseStatus.collectAsState()
    val repository = remember { ProfileRepository(context) }
    val currentTier = repository.getCurrentTier()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upgrade Tier") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Unlock Your Remote's Potential",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Status Message
            purchaseStatus?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 16.dp))
            }

            // Tier Cards
            TierCard(
                title = "Essentials",
                price = productDetails[BillingManager.SKU_ESSENTIALS]?.oneTimePurchaseOfferDetails?.formattedPrice ?: "Unavailable",
                features = listOf("2 App Profiles", "Customize Trigger Key", "Double Tap Actions", "Swipe Down Actions"),
                isCurrent = currentTier == AppTier.ESSENTIALS,
                onBuy = { activity?.let { BillingManager.launchPurchaseFlow(it, BillingManager.SKU_ESSENTIALS) } }
            )

            Spacer(modifier = Modifier.height(16.dp))

            TierCard(
                title = "Pro Saver",
                price = productDetails[BillingManager.SKU_PRO_SAVER]?.oneTimePurchaseOfferDetails?.formattedPrice ?: "Unavailable",
                features = listOf("Unlimited App Profiles", "Swipe Left/Right", "Gesture Recording", "Repeat Mode", "Contains Ads"),
                isCurrent = currentTier == AppTier.PRO_SAVER,
                recommended = true,
                onBuy = { activity?.let { BillingManager.launchPurchaseFlow(it, BillingManager.SKU_PRO_SAVER) } }
            )

            Spacer(modifier = Modifier.height(16.dp))

            TierCard(
                title = "Pro",
                price = productDetails[BillingManager.SKU_PRO]?.oneTimePurchaseOfferDetails?.formattedPrice ?: "Unavailable",
                features = listOf("Everything in Pro Saver", "NO ADS", "Support Development ❤️"),
                isCurrent = currentTier == AppTier.PRO,
                onBuy = { activity?.let { BillingManager.launchPurchaseFlow(it, BillingManager.SKU_PRO) } }
            )
        }
    }
}

@Composable
fun TierCard(
    title: String,
    price: String,
    features: List<String>,
    isCurrent: Boolean,
    recommended: Boolean = false,
    onBuy: () -> Unit
) {
    val borderColor = if (recommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (recommended) 2.dp else 1.dp

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(borderWidth, borderColor),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (recommended) {
                        Text("Best Value", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (isCurrent) {
                    Icon(Icons.Default.Check, "Current", tint = Color.Green)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            features.forEach { feature ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Icon(Icons.Default.Star, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(feature, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onBuy,
                enabled = !isCurrent,
                modifier = Modifier.fillMaxWidth(),
                colors = if (recommended) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
            ) {
                Text(if (isCurrent) "Owned" else "Buy $price")
            }
        }
    }
}