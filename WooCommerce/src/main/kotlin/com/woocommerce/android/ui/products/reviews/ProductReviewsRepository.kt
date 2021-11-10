package com.woocommerce.android.ui.products.reviews

import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Stat
import com.woocommerce.android.model.ProductReview
import com.woocommerce.android.model.toAppModel
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.util.ContinuationWrapper
import com.woocommerce.android.util.WooLog
import com.woocommerce.android.util.WooLog.T.PRODUCTS
import com.woocommerce.android.util.WooLog.T.REVIEWS
import org.wordpress.android.fluxc.store.WCProductStore
import org.wordpress.android.fluxc.store.WCProductStore.FetchProductReviewsPayload
import javax.inject.Inject

class ProductReviewsRepository @Inject constructor(
    private val selectedSite: SelectedSite,
    private val productStore: WCProductStore
) {
    companion object {
        private const val PRODUCT_REVIEW_STATUS_APPROVED = "approved"
        private const val PAGE_SIZE = WCProductStore.NUM_REVIEWS_PER_FETCH
    }

    private var continuationReviews = ContinuationWrapper<Boolean>(PRODUCTS)

    private var offset = 0
    private var isFetchingProductReviews = false
    private var isLoadingMore = false

    var canLoadMore: Boolean = false
        private set

    /**
     * Submits a fetch request to get a page of product reviews for the current site and [remoteProductId]
     */
    suspend fun fetchApprovedProductReviewsFromApi(
        remoteProductId: Long,
        loadMore: Boolean
    ): List<ProductReview> {
        offset = if (loadMore) offset + PAGE_SIZE else 0
        isFetchingProductReviews = true

        val payload = FetchProductReviewsPayload(
            selectedSite.get(), offset,
            productIds = listOf(remoteProductId),
            filterByStatus = listOf(PRODUCT_REVIEW_STATUS_APPROVED)
        )
        val result = productStore.fetchProductReviews(payload)
        if (result.isError) {
            AnalyticsTracker.track(
                Stat.PRODUCT_REVIEWS_LOAD_FAILED,
                mapOf(
                    AnalyticsTracker.KEY_ERROR_CONTEXT to this::class.java.simpleName,
                    AnalyticsTracker.KEY_ERROR_TYPE to result.error?.type?.toString(),
                    AnalyticsTracker.KEY_ERROR_DESC to result.error?.message
                )
            )

            WooLog.e(
                REVIEWS,
                "Error fetching product review: " +
                    "${result.error?.type} - ${result.error?.message}"
            )
            continuationReviews.continueWith(false)
        } else {
            AnalyticsTracker.track(
                Stat.PRODUCT_REVIEWS_LOADED,
                mapOf(
                    AnalyticsTracker.KEY_IS_LOADING_MORE to isLoadingMore
                )
            )
            isLoadingMore = false
            canLoadMore = result.canLoadMore
            continuationReviews.continueWith(true)
        }
        return getProductReviewsFromDB(remoteProductId)
    }

    /**
     * Returns all product reviews for the current site and product from the local database
     */
    fun getProductReviewsFromDB(remoteProductId: Long): List<ProductReview> {
        return productStore.getProductReviewsForProductAndSiteId(
            localSiteId = selectedSite.get().id,
            remoteProductId = remoteProductId
        ).map { it.toAppModel() }
    }
}
