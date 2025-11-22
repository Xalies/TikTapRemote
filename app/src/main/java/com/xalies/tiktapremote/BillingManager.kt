package com.xalies.tiktapremote

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object BillingManager {
    private const val TAG = "BillingManager"

    // --- CONFIGURATION ---
    const val SKU_ESSENTIALS = "tier_essentials"
    const val SKU_PRO_SAVER = "tier_pro_saver"
    const val SKU_PRO = "tier_pro"

    private val LIST_OF_SKUS = listOf(SKU_ESSENTIALS, SKU_PRO_SAVER, SKU_PRO)

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails = _productDetails.asStateFlow()

    private val _purchaseStatus = MutableStateFlow<String?>(null)
    val purchaseStatus = _purchaseStatus.asStateFlow()

    private lateinit var billingClient: BillingClient
    private lateinit var profileRepository: ProfileRepository

    fun initialize(context: Context) {
        profileRepository = ProfileRepository(context)

        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            // .enablePendingPurchases() is deprecated and enabled by default in newer versions
            .build()

        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing Setup Finished")
                    querySkuDetails()
                    queryPurchases() // Check what they already own
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d(TAG, "Billing Disconnected")
                // Retry logic could go here
            }
        })
    }

    private fun querySkuDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                LIST_OF_SKUS.map { sku ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(sku)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val detailsMap = productDetailsList.associateBy { it.productId }
                _productDetails.value = detailsMap
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, sku: String) {
        val details = _productDetails.value[sku]
        if (details != null) {
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .build()
                    )
                )
                .build()
            billingClient.launchBillingFlow(activity, flowParams)
        } else {
            _purchaseStatus.value = "Error: Product not found"
        }
    }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            _purchaseStatus.value = "Purchase Canceled"
        } else {
            _purchaseStatus.value = "Purchase Error: ${billingResult.debugMessage}"
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        grantEntitlement(purchase)
                    }
                }
            } else {
                grantEntitlement(purchase)
            }
        }
    }

    private fun grantEntitlement(purchase: Purchase) {
        CoroutineScope(Dispatchers.IO).launch {
            for (product in purchase.products) {
                val tier = when (product) {
                    SKU_ESSENTIALS -> AppTier.ESSENTIALS
                    SKU_PRO_SAVER -> AppTier.PRO_SAVER
                    SKU_PRO -> AppTier.PRO
                    else -> null
                }

                if (tier != null) {
                    profileRepository.setCurrentTier(tier)
                    withContext(Dispatchers.Main) {
                        _purchaseStatus.value = "Purchase Successful! You are now on ${tier.name}."
                    }
                }
            }
        }
    }

    private fun queryPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchases) {
                    handlePurchase(purchase)
                }
            }
        }
    }
}