package com.netspeedtest.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*

class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    @Volatile
    private var _isPremium = false
    val isPremium: Boolean get() = _isPremium

    private var billingClient: BillingClient? = null
    private var premiumCallback: ((Boolean) -> Unit)? = null

    companion object {
        private const val TAG = "BillingManager"
        const val SKU_REMOVE_ADS = "remove_ads"
        private const val PREFS_NAME = "netspeed_prefs"
        private const val KEY_PREMIUM = "is_premium"
    }

    init {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isPremium = prefs.getBoolean(KEY_PREMIUM, false)
    }

    private fun savePremium(premium: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PREMIUM, premium).apply()
    }

    fun setPremiumCallback(callback: (Boolean) -> Unit) {
        premiumCallback = callback
    }

    fun startConnection() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPurchases()
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun queryPurchases() {
        billingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasPremium = purchases.any { it.products.contains(SKU_REMOVE_ADS) }
                _isPremium = hasPremium
                savePremium(hasPremium)
                premiumCallback?.invoke(hasPremium)
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SKU_REMOVE_ADS)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        billingClient?.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && detailsList.isNotEmpty()) {
                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(detailsList[0])
                                .build()
                        )
                    )
                    .build()
                billingClient?.launchBillingFlow(activity, flowParams)
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.products.contains(SKU_REMOVE_ADS) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (!purchase.isAcknowledged) {
                        val ackParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        billingClient?.acknowledgePurchase(ackParams) { ackResult ->
                            if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                _isPremium = true
                                savePremium(true)
                                premiumCallback?.invoke(true)
                            }
                        }
                    } else {
                        _isPremium = true
                        savePremium(true)
                        premiumCallback?.invoke(true)
                    }
                }
            }
        }
    }

    fun endConnection() {
        billingClient?.endConnection()
    }
}
