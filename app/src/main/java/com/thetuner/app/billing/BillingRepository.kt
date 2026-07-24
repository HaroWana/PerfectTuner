package com.thetuner.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchasesUpdatedListener {

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    private val _hasPurchased = MutableStateFlow(false)
    val hasPurchased: StateFlow<Boolean> = _hasPurchased.asStateFlow()

    private val _billingError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val billingError: SharedFlow<String> = _billingError.asSharedFlow()

    // No banner on cancel (silent per UX decision), but the UI must still know
    // to drop any pending "auto-select after unlock" state
    private val _purchaseCancelled = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val purchaseCancelled: SharedFlow<Unit> = _purchaseCancelled.asSharedFlow()

    init {
        connect()
    }

    private fun connect() {
        if (billingClient.connectionState == BillingClient.ConnectionState.CONNECTING) return
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    queryPurchases()
                }
                // Non-OK setup (transient Play outage) is retried by the next
                // queryPurchases() call — the app re-queries on every resume
            }
            override fun onBillingServiceDisconnected() {
                // No-op — enableAutoServiceReconnection() handles retries automatically
            }
        })
    }

    fun queryPurchases() {
        if (!billingClient.isReady) {
            connect() // queries again from onBillingSetupFinished on success
            return
        }
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) {
                val owned = purchases.filter { purchase ->
                    purchase.products.contains(PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                _hasPurchased.value = owned.isNotEmpty()
                // Acknowledge restored/recovered purchases too: Play refunds
                // any purchase left unacknowledged for 3 days
                owned.forEach { handlePurchase(it) }
            }
        }
    }

    fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.products.contains(PRODUCT_ID)) return

        // Grant immediately — the user has paid. A failed acknowledgement is
        // retried by every subsequent queryPurchases() until it sticks.
        _hasPurchased.value = true

        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { /* retried via queryPurchases */ }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingResponseCode.USER_CANCELED -> {
                _purchaseCancelled.tryEmit(Unit)
            }
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Already purchased — refresh state
                queryPurchases()
            }
            else -> {
                _billingError.tryEmit("Purchase failed. Please try again.")
            }
        }
    }

    suspend fun launchPurchase(activity: Activity) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(ProductType.INAPP)
                        .build()
                )
            )
            .build()

        val result = billingClient.queryProductDetails(params)
        val productDetails = result.productDetailsList?.firstOrNull()

        if (productDetails == null) {
            _billingError.tryEmit("Could not load purchase details. Check your connection.")
            return
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        withContext(Dispatchers.Main) {
            billingClient.launchBillingFlow(activity, billingFlowParams)
        }
    }

    companion object {
        const val PRODUCT_ID = "unlock_full_library"
    }
}
