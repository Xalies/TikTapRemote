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

object BillingManager {
    private const val TAG = "BillingManager"

    // SKU Constants - REPLACE THESE with your actual Google Play Console Product IDs
    const val SKU_ESSENTIALS = "tier_essentials"
    const val SKU_PRO_SAVER = "tier_pro_saver"
    const val SKU_PRO = "tier_pro"

    private lateinit var billingClient: BillingClient
    private lateinit var repository: ProfileRepository

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails = _productDetails.asStateFlow()

    private val _purchaseStatus = MutableStateFlow<String?>(null)
    val purchaseStatus = _purchaseStatus.asStateFlow()

    // Callback for purchase updates
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            _purchaseStatus.value = "Purchase Canceled"
        } else {
            _purchaseStatus.value = "Error: ${billingResult.debugMessage}"
        }
    }

    fun initialize(context: Context) {
        repository = ProfileRepository(context)
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing Setup Finished")
                    queryAvailableProducts()
                    queryPurchases() // Check for existing purchases (restoring transactions)
                }
            }

            override fun onBillingServiceDisconnected() {
                // Retry connection logic could go here
                Log.e(TAG, "Billing Disconnected")
            }
        })
    }

    private fun queryAvailableProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_ESSENTIALS)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_PRO_SAVER)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_PRO)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val map = productDetailsList.associateBy { it.productId }
                _productDetails.value = map
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, skuId: String) {
        val productDetails = _productDetails.value[skuId]
        if (productDetails != null) {
            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build()
            )

            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()

            billingClient.launchBillingFlow(activity, billingFlowParams)
        } else {
            _purchaseStatus.value = "Product details not found"
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
                        Log.d(TAG, "Purchase Acknowledged")
                        // Grant Entitlement
                        grantEntitlement(purchase.products)
                    }
                }
            } else {
                grantEntitlement(purchase.products)
            }
        }
    }

    private fun grantEntitlement(products: List<String>) {
        CoroutineScope(Dispatchers.Main).launch {
            // LEGIT PURCHASE CHECK:
            // If the user bought the app, remove the "Backdoor User" flag so they aren't wiped later.
            repository.setBackdoorUsed(false)

            when {
                products.contains(SKU_PRO) -> {
                    repository.setCurrentTier(AppTier.PRO)
                    _purchaseStatus.value = "Upgraded to PRO!"
                }
                products.contains(SKU_PRO_SAVER) -> {
                    repository.setCurrentTier(AppTier.PRO_SAVER)
                    _purchaseStatus.value = "Upgraded to Pro Saver!"
                }
                products.contains(SKU_ESSENTIALS) -> {
                    // Only set Essentials if not already on a higher tier
                    if (repository.getCurrentTier() == AppTier.FREE) {
                        repository.setCurrentTier(AppTier.ESSENTIALS)
                        _purchaseStatus.value = "Upgraded to Essentials!"
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