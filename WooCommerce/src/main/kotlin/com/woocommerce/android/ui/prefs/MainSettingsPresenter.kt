package com.woocommerce.android.ui.prefs

import androidx.lifecycle.MutableLiveData
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.payments.banner.BannerDisplayEligibilityChecker
import com.woocommerce.android.ui.whatsnew.FeatureAnnouncementRepository
import com.woocommerce.android.util.BuildConfigWrapper
import com.woocommerce.android.util.StringUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import org.wordpress.android.fluxc.store.AccountStore
import org.wordpress.android.fluxc.store.WooCommerceStore
import javax.inject.Inject

class MainSettingsPresenter @Inject constructor(
    private val selectedSite: SelectedSite,
    private val accountStore: AccountStore,
    private val wooCommerceStore: WooCommerceStore,
    private val featureAnnouncementRepository: FeatureAnnouncementRepository,
    private val buildConfigWrapper: BuildConfigWrapper,
    private val bannerDisplayEligibilityChecker: BannerDisplayEligibilityChecker,
) : MainSettingsContract.Presenter {
    private var appSettingsFragmentView: MainSettingsContract.View? = null

    private var jetpackMonitoringJob: Job? = null

    override val isEligibleForInPersonPayments: MutableLiveData<Boolean> = MutableLiveData(false)

    override fun takeView(view: MainSettingsContract.View) {
        appSettingsFragmentView = view
        coroutineScope.launch {
            isEligibleForInPersonPayments.value = bannerDisplayEligibilityChecker.isEligibleForInPersonPayments()
        }
    }

    override fun dropView() {
        appSettingsFragmentView = null
    }

    override fun getUserDisplayName(): String = accountStore.account.displayName

    override fun getStoreDomainName(): String {
        return selectedSite.getIfExists()?.let { site ->
            StringUtils.getSiteDomainAndPath(site)
        } ?: ""
    }

    override fun hasMultipleStores() = wooCommerceStore.getWooCommerceSites().size > 1

    override fun setupAnnouncementOption() {
        coroutineScope.launch {
            val result = featureAnnouncementRepository.getLatestFeatureAnnouncement(true)
                ?: featureAnnouncementRepository.getLatestFeatureAnnouncement(false)
            result?.let {
                if (it.canBeDisplayedOnAppUpgrade(buildConfigWrapper.versionName)) {
                    appSettingsFragmentView?.showLatestAnnouncementOption(it)
                }
            }
        }
    }

    override fun setupJetpackInstallOption() {
        appSettingsFragmentView?.handleJetpackInstallOption(selectedSite.get().isJetpackCPConnected)
        jetpackMonitoringJob?.cancel()
        if (selectedSite.get().isJetpackCPConnected) {
            jetpackMonitoringJob = coroutineScope.launch {
                selectedSite.observe()
                    .filter { it?.isJetpackConnected == true }
                    .take(1)
                    .collect { setupJetpackInstallOption() }
            }
        }
    }

    override fun onCtaClicked(source: String) {
        coroutineScope.launch {
            appSettingsFragmentView?.openPurchaseCardReaderLink(
                bannerDisplayEligibilityChecker.getPurchaseCardReaderUrl(source)
            )
        }
    }

    override fun canShowCardReaderUpsellBanner(currentTimeInMillis: Long, source: String): Boolean {
        return bannerDisplayEligibilityChecker.canShowCardReaderUpsellBanner(currentTimeInMillis, source)
    }
}
