package com.woocommerce.android.ui.login

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.woocommerce.android.AppPrefs
import com.woocommerce.android.AppPrefsWrapper
import com.woocommerce.android.AppUrls
import com.woocommerce.android.AppUrls.LOGIN_WITH_EMAIL_WHAT_IS_WORDPRESS_COM_ACCOUNT
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsEvent
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Companion.KEY_FLOW
import com.woocommerce.android.analytics.AnalyticsTracker.Companion.KEY_SOURCE
import com.woocommerce.android.analytics.AnalyticsTracker.Companion.VALUE_JETPACK_INSTALLATION_SOURCE_WEB
import com.woocommerce.android.analytics.AnalyticsTracker.Companion.VALUE_LOGIN_WITH_WORDPRESS_COM
import com.woocommerce.android.analytics.ExperimentTracker
import com.woocommerce.android.databinding.ActivityLoginBinding
import com.woocommerce.android.extensions.parcelable
import com.woocommerce.android.support.ZendeskExtraTags
import com.woocommerce.android.support.ZendeskHelper
import com.woocommerce.android.support.help.HelpActivity
import com.woocommerce.android.support.help.HelpOrigin
import com.woocommerce.android.ui.base.UIMessageResolver
import com.woocommerce.android.ui.login.LoginPrologueCarouselFragment.PrologueCarouselListener
import com.woocommerce.android.ui.login.LoginPrologueFragment.PrologueFinishedListener
import com.woocommerce.android.ui.login.UnifiedLoginTracker.Click
import com.woocommerce.android.ui.login.UnifiedLoginTracker.Flow
import com.woocommerce.android.ui.login.UnifiedLoginTracker.Flow.LOGIN_SITE_ADDRESS
import com.woocommerce.android.ui.login.UnifiedLoginTracker.Source
import com.woocommerce.android.ui.login.UnifiedLoginTracker.Step.ENTER_SITE_ADDRESS
import com.woocommerce.android.ui.login.accountmismatch.AccountMismatchErrorFragment
import com.woocommerce.android.ui.login.accountmismatch.AccountMismatchErrorFragmentArgs
import com.woocommerce.android.ui.login.accountmismatch.AccountMismatchErrorViewModel.AccountMismatchErrorType
import com.woocommerce.android.ui.login.accountmismatch.AccountMismatchErrorViewModel.AccountMismatchPrimaryButton
import com.woocommerce.android.ui.login.localnotifications.LoginHelpNotificationType
import com.woocommerce.android.ui.login.localnotifications.LoginHelpNotificationType.DEFAULT_HELP
import com.woocommerce.android.ui.login.localnotifications.LoginHelpNotificationType.LOGIN_SITE_ADDRESS_EMAIL_ERROR
import com.woocommerce.android.ui.login.localnotifications.LoginHelpNotificationType.LOGIN_SITE_ADDRESS_ERROR
import com.woocommerce.android.ui.login.localnotifications.LoginHelpNotificationType.LOGIN_SITE_ADDRESS_PASSWORD_ERROR
import com.woocommerce.android.ui.login.localnotifications.LoginHelpNotificationType.LOGIN_WPCOM_EMAIL_ERROR
import com.woocommerce.android.ui.login.localnotifications.LoginHelpNotificationType.LOGIN_WPCOM_PASSWORD_ERROR
import com.woocommerce.android.ui.login.localnotifications.LoginNotificationScheduler
import com.woocommerce.android.ui.login.overrides.WooLoginEmailFragment
import com.woocommerce.android.ui.login.overrides.WooLoginEmailPasswordFragment
import com.woocommerce.android.ui.login.overrides.WooLoginSiteAddressFragment
import com.woocommerce.android.ui.login.overrides.WooLoginUsernamePasswordFragment
import com.woocommerce.android.ui.login.qrcode.QrCodeLoginListener
import com.woocommerce.android.ui.login.qrcode.ValidateScannedValue
import com.woocommerce.android.ui.login.signup.SignUpFragment
import com.woocommerce.android.ui.login.signup.SignUpFragment.NextStep.SITE_PICKER
import com.woocommerce.android.ui.login.signup.SignUpFragment.NextStep.STORE_CREATION
import com.woocommerce.android.ui.main.MainActivity
import com.woocommerce.android.util.ActivityUtils
import com.woocommerce.android.util.ChromeCustomTabUtils
import com.woocommerce.android.util.UrlUtils
import com.woocommerce.android.util.WooLog
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.parcelize.Parcelize
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.MAIN
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.network.MemorizingTrustManager
import org.wordpress.android.fluxc.store.AccountStore.AuthEmailPayloadScheme.WOOCOMMERCE
import org.wordpress.android.fluxc.store.AccountStore.AuthOptionsErrorType.UNKNOWN_USER
import org.wordpress.android.fluxc.store.AccountStore.OnAuthOptionsFetched
import org.wordpress.android.fluxc.store.SiteStore
import org.wordpress.android.fluxc.store.SiteStore.ConnectSiteInfoPayload
import org.wordpress.android.fluxc.store.SiteStore.OnConnectSiteInfoChecked
import org.wordpress.android.login.AuthOptions
import org.wordpress.android.login.GoogleFragment.GoogleListener
import org.wordpress.android.login.Login2FaFragment
import org.wordpress.android.login.LoginAnalyticsListener
import org.wordpress.android.login.LoginEmailFragment
import org.wordpress.android.login.LoginEmailPasswordFragment
import org.wordpress.android.login.LoginGoogleFragment
import org.wordpress.android.login.LoginListener
import org.wordpress.android.login.LoginMagicLinkRequestFragment
import org.wordpress.android.login.LoginMode
import org.wordpress.android.login.LoginSiteAddressFragment
import org.wordpress.android.util.ToastUtils
import javax.inject.Inject
import kotlin.text.RegexOption.IGNORE_CASE

// TODO Extract logic out of LoginActivity to reduce size
@Suppress("SameParameterValue", "LargeClass")
@AndroidEntryPoint
class LoginActivity :
    AppCompatActivity(),
    LoginListener,
    GoogleListener,
    PrologueFinishedListener,
    PrologueCarouselListener,
    HasAndroidInjector,
    LoginNoJetpackListener,
    LoginEmailHelpDialogFragment.Listener,
    WooLoginEmailFragment.Listener,
    WooLoginEmailPasswordFragment.Listener,
    LoginNoWPcomAccountFoundFragment.Listener,
    SignUpFragment.Listener,
    QrCodeLoginListener {
    companion object {
        private const val FORGOT_PASSWORD_URL_SUFFIX = "wp-login.php?action=lostpassword"
        private const val MAGIC_LOGIN = "magic-login"
        private const val TOKEN_PARAMETER = "token"
        private const val JETPACK_CONNECT_URL = "https://wordpress.com/jetpack/connect"
        private const val JETPACK_CONNECTED_REDIRECT_URL = "woocommerce://jetpack-connected"

        private const val KEY_UNIFIED_TRACKER_SOURCE = "KEY_UNIFIED_TRACKER_SOURCE"
        private const val KEY_UNIFIED_TRACKER_FLOW = "KEY_UNIFIED_TRACKER_FLOW"
        private const val KEY_LOGIN_HELP_NOTIFICATION = "KEY_LOGIN_HELP_NOTIFICATION"
        private const val KEY_CONNECT_SITE_INFO = "KEY_CONNECT_SITE_INFO"

        const val LOGIN_WITH_WPCOM_EMAIL_ACTION = "login_with_wpcom_email"
        const val EMAIL_PARAMETER = "email"

        fun createIntent(
            context: Context,
            notificationType: LoginHelpNotificationType,
        ): Intent {
            val intent = Intent(context, LoginActivity::class.java)
            intent.apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(KEY_LOGIN_HELP_NOTIFICATION, notificationType.toString())
                LoginMode.WOO_LOGIN_MODE.putInto(this)
            }
            return intent
        }
    }

    @Inject internal lateinit var androidInjector: DispatchingAndroidInjector<Any>
    @Inject internal lateinit var loginAnalyticsListener: LoginAnalyticsListener
    @Inject internal lateinit var unifiedLoginTracker: UnifiedLoginTracker
    @Inject internal lateinit var zendeskHelper: ZendeskHelper
    @Inject internal lateinit var urlUtils: UrlUtils
    @Inject internal lateinit var experimentTracker: ExperimentTracker
    @Inject internal lateinit var appPrefsWrapper: AppPrefsWrapper
    @Inject internal lateinit var dispatcher: Dispatcher
    @Inject internal lateinit var loginNotificationScheduler: LoginNotificationScheduler
    @Inject internal lateinit var uiMessageResolver: UIMessageResolver

    private var loginMode: LoginMode? = null
    private lateinit var binding: ActivityLoginBinding

    private var connectSiteInfo: ConnectSiteInfo? = null

    override fun androidInjector(): AndroidInjector<Any> = androidInjector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackPress()
                }
            }
        )

        dispatcher.register(this)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val loginHelpNotification = getLoginHelpNotification()

        when {
            intent?.action == LOGIN_WITH_WPCOM_EMAIL_ACTION -> {
                val email = intent.extras!!.getString(EMAIL_PARAMETER)
                gotWpcomEmail(email, verifyEmail = true, null)
            }
            hasJetpackConnectedIntent() -> {
                AnalyticsTracker.track(
                    stat = AnalyticsEvent.LOGIN_JETPACK_SETUP_COMPLETED,
                    properties = mapOf(KEY_SOURCE to VALUE_JETPACK_INSTALLATION_SOURCE_WEB)
                )
                startLoginViaWPCom()
            }
            hasMagicLinkLoginIntent() -> {
                getAuthTokenFromIntent()?.let { showMagicLinkInterceptFragment(it) }
            }
            !loginHelpNotification.isNullOrBlank() -> {
                processLoginHelpNotification(loginHelpNotification)
            }
            savedInstanceState == null -> {
                loginAnalyticsListener.trackLoginAccessed()
                showPrologue()
            }
        }

        savedInstanceState?.let { ss ->
            unifiedLoginTracker.setSource(ss.getString(KEY_UNIFIED_TRACKER_SOURCE, Source.DEFAULT.value))
            unifiedLoginTracker.setFlow(ss.getString(KEY_UNIFIED_TRACKER_FLOW))
            connectSiteInfo = ss.parcelable(KEY_CONNECT_SITE_INFO)
        }
    }

    private fun handleBackPress() {
        AnalyticsTracker.trackBackPressed(this)
        if (supportFragmentManager.backStackEntryCount == 1) {
            finish()
        } else {
            supportFragmentManager.popBackStack()
        }
    }

    override fun onResume() {
        super.onResume()
        AnalyticsTracker.trackViewShown(this)
    }

    override fun onDestroy() {
        super.onDestroy()

        dispatcher.unregister(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putString(KEY_UNIFIED_TRACKER_SOURCE, unifiedLoginTracker.getSource().value)
        outState.putParcelable(KEY_CONNECT_SITE_INFO, connectSiteInfo)
        unifiedLoginTracker.getFlow()?.value?.let {
            outState.putString(KEY_UNIFIED_TRACKER_FLOW, it)
        }
    }

    private fun showPrologue() {
        if (!appPrefsWrapper.hasOnboardingCarouselBeenDisplayed()) {
            showPrologueCarouselFragment()
        } else {
            showPrologueFragment()
        }
    }

    private fun hasMagicLinkLoginIntent(): Boolean {
        val action = intent.action
        val uri = intent.data
        val host = uri?.host ?: ""
        return Intent.ACTION_VIEW == action && host.contains(MAGIC_LOGIN)
    }

    private fun getAuthTokenFromIntent(): String? {
        val uri = intent.data
        return uri?.getQueryParameter(TOKEN_PARAMETER)
    }

    private fun showMagicLinkInterceptFragment(authToken: String) {
        val fragment = MagicLinkInterceptFragment.newInstance(authToken)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, MagicLinkInterceptFragment.TAG)
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }

    private fun hasJetpackConnectedIntent(): Boolean {
        val action = intent.action
        val uri = intent.data

        return Intent.ACTION_VIEW == action && uri.toString() == JETPACK_CONNECTED_REDIRECT_URL
    }

    private fun changeFragment(
        fragment: Fragment,
        shouldAddToBackStack: Boolean,
        tag: String,
        animate: Boolean = true
    ) {
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        if (animate) {
            fragmentTransaction.setCustomAnimations(
                R.anim.default_enter_anim,
                R.anim.default_exit_anim,
                R.anim.default_pop_enter_anim,
                R.anim.default_pop_exit_anim
            )
        }
        fragmentTransaction.replace(R.id.fragment_container, fragment, tag)
        if (shouldAddToBackStack) {
            fragmentTransaction.addToBackStack(tag)
            fragmentTransaction.setPrimaryNavigationFragment(fragment)
        }
        fragmentTransaction.commitAllowingStateLoss()
    }

    /**
     * The normal layout for the login library will include social login but
     * there is an alternative layout used specifically for logging in using the
     * site address flow. This layout includes an option to sign in with site
     * credentials.
     *
     * @param siteCredsLayout If true, use the layout that includes the option to log
     * in with site credentials.
     */
    private fun getLoginEmailFragment(siteCredsLayout: Boolean): LoginEmailFragment? {
        val fragment = if (siteCredsLayout) {
            supportFragmentManager.findFragmentByTag(LoginEmailFragment.TAG_SITE_CREDS_LAYOUT)
        } else {
            supportFragmentManager.findFragmentByTag(LoginEmailFragment.TAG)
        }
        return if (fragment == null) null else fragment as LoginEmailFragment
    }

    private fun getPrologueFragment(): LoginPrologueFragment? =
        supportFragmentManager.findFragmentByTag(LoginPrologueFragment.TAG) as? LoginPrologueFragment

    private fun getLoginViaSiteAddressFragment(): LoginSiteAddressFragment? =
        supportFragmentManager.findFragmentByTag(LoginSiteAddressFragment.TAG) as? WooLoginSiteAddressFragment

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }

        return false
    }

    override fun getLoginMode(): LoginMode {
        if (loginMode != null) {
            // returned the cached value
            return loginMode as LoginMode
        }

        // compute and cache the Login mode
        loginMode = LoginMode.fromIntent(intent)

        return loginMode as LoginMode
    }

    override fun startOver() {
        // Clear logged in url from AppPrefs
        AppPrefs.removeLoginSiteAddress()

        // Pop all the fragments from the backstack until we get to the Prologue fragment
        supportFragmentManager.popBackStack(LoginPrologueFragment.TAG, 0)
    }

    override fun onPrimaryButtonClicked() {
        unifiedLoginTracker.trackClick(Click.LOGIN_WITH_SITE_ADDRESS)
        loginViaSiteAddress()
    }

    override fun onSecondaryButtonClicked() {
        unifiedLoginTracker.trackClick(Click.CONTINUE_WITH_WORDPRESS_COM)
        startLoginViaWPCom()
    }

    override fun onNewToWooButtonClicked() {
        ChromeCustomTabUtils.launchUrl(this, AppUrls.NEW_TO_WOO_DOC)
    }

    override fun onGetStartedClicked() {
        changeFragment(SignUpFragment.newInstance(STORE_CREATION), true, SignUpFragment.TAG)
    }

    private fun showMainActivityAndFinish() {
        experimentTracker.log(ExperimentTracker.LOGIN_SUCCESSFUL_EVENT)
        loginNotificationScheduler.onLoginSuccess()

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun jumpToUsernamePassword(username: String?, password: String?) {
        val loginUsernamePasswordFragment = WooLoginUsernamePasswordFragment.newInstance(
            "wordpress.com", "wordpress.com", username, password, true
        )
        changeFragment(loginUsernamePasswordFragment, true, WooLoginUsernamePasswordFragment.TAG)
    }

    private fun startLoginViaWPCom() {
        // Clean previously saved site address, e.g: if merchants return from a store address flow.
        AppPrefs.removeLoginSiteAddress()

        unifiedLoginTracker.setFlow(Flow.WORDPRESS_COM.value)
        showEmailLoginScreen()
    }

    override fun gotWpcomEmail(email: String?, verifyEmail: Boolean, authOptions: AuthOptions?) {
        val isMagicLinkEnabled =
            getLoginMode() != LoginMode.WPCOM_LOGIN_DEEPLINK && getLoginMode() != LoginMode.SHARE_INTENT
        email?.let { appPrefsWrapper.setLoginEmail(it) }
        if (authOptions != null) {
            if (authOptions.isPasswordless) {
                showMagicLinkRequestScreen(email, verifyEmail, allowPassword = false, forceRequestAtStart = true)
            } else {
                showEmailPasswordScreen(email, verifyEmail)
            }
        } else {
            if (isMagicLinkEnabled) {
                showMagicLinkRequestScreen(email, verifyEmail, allowPassword = true, forceRequestAtStart = false)
            } else {
                showEmailPasswordScreen(email, verifyEmail)
            }
        }
    }

    private fun showEmailPasswordScreen(email: String?, verifyEmail: Boolean) {
        val wooLoginEmailPasswordFragment = WooLoginEmailPasswordFragment
            .newInstance(email, verifyMagicLinkEmail = verifyEmail)
        changeFragment(wooLoginEmailPasswordFragment, true, LoginEmailPasswordFragment.TAG)
    }

    private fun showMagicLinkRequestScreen(
        email: String?,
        verifyEmail: Boolean,
        allowPassword: Boolean,
        forceRequestAtStart: Boolean
    ) {
        val scheme = WOOCOMMERCE
        val loginMagicLinkRequestFragment = LoginMagicLinkRequestFragment
            .newInstance(
                email, scheme, false, null, verifyEmail, allowPassword, forceRequestAtStart
            )
        changeFragment(loginMagicLinkRequestFragment, true, LoginMagicLinkRequestFragment.TAG, false)
    }

    override fun loginViaSiteAddress() {
        unifiedLoginTracker.setFlowAndStep(LOGIN_SITE_ADDRESS, ENTER_SITE_ADDRESS)
        val loginSiteAddressFragment = getLoginViaSiteAddressFragment() ?: WooLoginSiteAddressFragment()
        changeFragment(loginSiteAddressFragment, true, LoginSiteAddressFragment.TAG)
    }

    private fun showPrologueCarouselFragment() {
        val fragment = LoginPrologueCarouselFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, LoginPrologueCarouselFragment.TAG)
            .addToBackStack(LoginPrologueCarouselFragment.TAG)
            .commitAllowingStateLoss()
    }

    private fun showPrologueFragment() = lifecycleScope.launchWhenStarted {
        val prologueFragment = getPrologueFragment() ?: LoginPrologueFragment()
        changeFragment(prologueFragment, true, LoginPrologueFragment.TAG)
    }

    override fun loginViaSocialAccount(
        email: String?,
        idToken: String?,
        service: String?,
        isPasswordRequired: Boolean
    ) {
        val loginEmailPasswordFragment = WooLoginEmailPasswordFragment.newInstance(
            emailAddress = email,
            idToken = idToken,
            service = service,
            isSocialLogin = isPasswordRequired
        )
        changeFragment(loginEmailPasswordFragment, true, LoginEmailPasswordFragment.TAG)
    }

    override fun loggedInViaSocialAccount(oldSitesIds: ArrayList<Int>, doLoginUpdate: Boolean) {
        loginAnalyticsListener.trackLoginSocialSuccess()
        showMainActivityAndFinish()
    }

    override fun loginViaWpcomUsernameInstead() {
        jumpToUsernamePassword(null, null)
    }

    override fun showMagicLinkSentScreen(email: String?, allowPassword: Boolean) {
        val loginMagicLinkSentFragment = LoginMagicLinkSentImprovedFragment.newInstance(email, true)
        changeFragment(loginMagicLinkSentFragment, true, LoginMagicLinkSentImprovedFragment.TAG, false)
    }

    override fun openEmailClient(isLogin: Boolean) {
        if (ActivityUtils.isEmailClientAvailable(this)) {
            loginAnalyticsListener.trackLoginMagicLinkOpenEmailClientClicked()
            ActivityUtils.openEmailClient(this)
        } else {
            ToastUtils.showToast(this, R.string.login_email_client_not_found)
        }
    }

    override fun usePasswordInstead(email: String?) {
        loginAnalyticsListener.trackLoginMagicLinkExited()
        val loginEmailPasswordFragment = WooLoginEmailPasswordFragment.newInstance(email)
        changeFragment(loginEmailPasswordFragment, true, LoginEmailPasswordFragment.TAG)
    }

    override fun forgotPassword(url: String?) {
        loginAnalyticsListener.trackLoginForgotPasswordClicked()
        ChromeCustomTabUtils.launchUrl(this, url + FORGOT_PASSWORD_URL_SUFFIX)
    }

    override fun needs2fa(email: String?, password: String?) {
        val login2FaFragment = Login2FaFragment.newInstance(email, password)
        changeFragment(login2FaFragment, true, Login2FaFragment.TAG)
    }

    override fun needs2faSocial(
        email: String?,
        userId: String?,
        nonceAuthenticator: String?,
        nonceBackup: String?,
        nonceSms: String?
    ) {
        loginAnalyticsListener.trackLoginSocial2faNeeded()
        val login2FaFragment = Login2FaFragment.newInstanceSocial(
            email, userId,
            nonceAuthenticator, nonceBackup, nonceSms
        )
        changeFragment(login2FaFragment, true, Login2FaFragment.TAG)
    }

    override fun needs2faSocialConnect(email: String?, password: String?, idToken: String?, service: String?) {
        loginAnalyticsListener.trackLoginSocial2faNeeded()
        val login2FaFragment = Login2FaFragment.newInstanceSocialConnect(email, password, idToken, service)
        changeFragment(login2FaFragment, true, Login2FaFragment.TAG)
    }

    override fun loggedInViaPassword(oldSitesIds: ArrayList<Int>) {
        showMainActivityAndFinish()
    }

    override fun alreadyLoggedInWpcom(oldSitesIds: ArrayList<Int>) {
        ToastUtils.showToast(this, R.string.already_logged_in_wpcom, ToastUtils.Duration.LONG)
        showMainActivityAndFinish()
    }

    override fun gotWpcomSiteInfo(siteAddress: String?) {
        // Save site address to app prefs so it's available to MainActivity regardless of how the user
        // logs into the app.
        siteAddress?.let { AppPrefs.setLoginSiteAddress(it) }
        showEmailLoginScreen(siteAddress)
    }

    override fun gotConnectedSiteInfo(siteAddress: String, redirectUrl: String?, hasJetpack: Boolean) {
        // If the redirect url is available, use that as the preferred url. Pass this url to the other fragments
        // with the protocol since it is needed for initiating forgot password flow etc in the login process.
        val inputSiteAddress = urlUtils.sanitiseUrl(redirectUrl ?: siteAddress)

        // Save site address to app prefs so it's available to MainActivity regardless of how the user
        // logs into the app. Strip the protocol from this url string prior to saving to AppPrefs since it's
        // not needed and may cause issues when attempting to match the url to the authenticated account later
        // in the login process.
        val protocolRegex = Regex("^(http[s]?://)", IGNORE_CASE)
        val siteAddressClean = inputSiteAddress.replaceFirst(protocolRegex, "")
        AppPrefs.setLoginSiteAddress(siteAddressClean)

        if (hasJetpack) {
            showEmailLoginScreen(inputSiteAddress.takeIf { connectSiteInfo?.isWPCom != true })
        } else {
            // Let user log in via site credentials first before showing Jetpack missing screen.
            loginViaSiteCredentials(inputSiteAddress)
        }
    }

    /**
     * Method called when Login with Site credentials link is clicked in the [LoginEmailFragment]
     * This method is called instead of [LoginListener.gotXmlRpcEndpoint] since calling that method overrides
     * the already saved [inputSiteAddress] without the protocol, with the same site address but with
     * the protocol. This may cause issues when attempting to match the url to the authenticated account later
     * in the login process.
     */
    override fun loginViaSiteCredentials(inputSiteAddress: String?) {
        // hide the keyboard
        org.wordpress.android.util.ActivityUtils.hideKeyboard(this)

        unifiedLoginTracker.trackClick(Click.LOGIN_WITH_SITE_CREDS)
        showUsernamePasswordScreen(inputSiteAddress, null, null, null)
    }

    override fun gotXmlRpcEndpoint(inputSiteAddress: String?, endpointAddress: String?) {
        // Save site address to app prefs so it's available to MainActivity regardless of how the user
        // logs into the app.
        inputSiteAddress?.let { AppPrefs.setLoginSiteAddress(it) }

        val loginUsernamePasswordFragment = WooLoginUsernamePasswordFragment.newInstance(
            inputSiteAddress, endpointAddress, null, null, false
        )
        changeFragment(loginUsernamePasswordFragment, true, WooLoginUsernamePasswordFragment.TAG)
    }

    override fun handleSslCertificateError(
        memorizingTrustManager: MemorizingTrustManager?,
        callback: LoginListener.SelfSignedSSLCallback?
    ) {
        WooLog.e(WooLog.T.LOGIN, "Self-signed SSL certificate detected - can't proceed with the login.")
        // TODO: Support self-signed SSL sites and show dialog (only needed when XML-RPC support is added)
    }

    private fun viewHelpAndSupport(origin: HelpOrigin) {
        val extraSupportTags = arrayListOf(ZendeskExtraTags.connectingJetpack)
        val flow = unifiedLoginTracker.getFlow()
        val step = unifiedLoginTracker.previousStepBeforeHelpStep

        startActivity(HelpActivity.createIntent(this, origin, extraSupportTags, flow?.value, step?.value))
    }

    override fun helpSiteAddress(url: String?) {
        viewHelpAndSupport(HelpOrigin.LOGIN_SITE_ADDRESS)
    }

    override fun helpFindingSiteAddress(username: String?, siteStore: SiteStore?) {
        unifiedLoginTracker.trackClick(Click.HELP_FINDING_SITE_ADDRESS)
        zendeskHelper.createNewTicket(this, HelpOrigin.LOGIN_SITE_ADDRESS, null)
    }

    // TODO This can be modified to also receive the URL the user entered, so we can make that the primary store
    override fun loggedInViaUsernamePassword(oldSitesIds: ArrayList<Int>) {
        showMainActivityAndFinish()
    }

    override fun helpEmailScreen(email: String?) {
        viewHelpAndSupport(HelpOrigin.LOGIN_EMAIL)
    }

    override fun helpSocialEmailScreen(email: String?) {
        viewHelpAndSupport(HelpOrigin.LOGIN_SOCIAL)
    }

    @Suppress("DEPRECATION")
    override fun addGoogleLoginFragment(isSignupFromLoginEnabled: Boolean) {
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        val loginGoogleFragment = LoginGoogleFragment().apply {
            retainInstance = true
        }
        fragmentTransaction.add(loginGoogleFragment, LoginGoogleFragment.TAG)
        fragmentTransaction.commitAllowingStateLoss()
    }

    override fun helpMagicLinkRequest(email: String?) {
        viewHelpAndSupport(HelpOrigin.LOGIN_MAGIC_LINK)
    }

    override fun helpMagicLinkSent(email: String?) {
        viewHelpAndSupport(HelpOrigin.LOGIN_MAGIC_LINK)
    }

    override fun helpEmailPasswordScreen(email: String?) {
        viewHelpAndSupport(HelpOrigin.LOGIN_EMAIL_PASSWORD)
    }

    override fun help2FaScreen(email: String?) {
        viewHelpAndSupport(HelpOrigin.LOGIN_2FA)
    }

    override fun startPostLoginServices() {
        // TODO Start future NotificationsUpdateService
    }

    override fun helpUsernamePassword(url: String?, username: String?, isWpcom: Boolean) {
        viewHelpAndSupport(HelpOrigin.LOGIN_USERNAME_PASSWORD)
    }

    override fun helpNoJetpackScreen(
        siteAddress: String,
        endpointAddress: String?,
        username: String,
        password: String,
        userAvatarUrl: String?,
        checkJetpackAvailability: Boolean
    ) {
        if (connectSiteInfo?.isJetpackActive == true) {
            // If jetpack is present, but we can't find the connected email, then show account mismatch error
            val fragment = AccountMismatchErrorFragment().apply {
                arguments = AccountMismatchErrorFragmentArgs(
                    siteUrl = siteAddress,
                    primaryButton = AccountMismatchPrimaryButton.CONNECT_JETPACK,
                    errorType = AccountMismatchErrorType.ACCOUNT_NOT_CONNECTED
                ).toBundle()
            }
            changeFragment(
                fragment = fragment,
                shouldAddToBackStack = true,
                tag = AccountMismatchErrorFragment::class.java.simpleName
            )
        } else {
            val jetpackReqFragment = LoginNoJetpackFragment.newInstance(
                siteAddress, endpointAddress, username, password, userAvatarUrl,
                checkJetpackAvailability
            )
            changeFragment(
                fragment = jetpackReqFragment as Fragment,
                shouldAddToBackStack = true,
                tag = LoginNoJetpackFragment.TAG
            )
        }
    }

    override fun helpHandleDiscoveryError(
        siteAddress: String,
        endpointAddress: String?,
        username: String,
        password: String,
        userAvatarUrl: String?,
        errorMessage: Int
    ) {
        val discoveryErrorFragment = LoginDiscoveryErrorFragment.newInstance(
            siteAddress, endpointAddress, username, password, userAvatarUrl, errorMessage
        )
        changeFragment(
            fragment = discoveryErrorFragment as Fragment,
            shouldAddToBackStack = true,
            tag = LoginDiscoveryErrorFragment.TAG
        )
    }

    // SmartLock

    override fun saveCredentialsInSmartLock(
        username: String?,
        password: String?,
        displayName: String,
        profilePicture: Uri?
    ) {
        // TODO: Hook for smartlock, if using
    }

    // Signup

    override fun helpSignupEmailScreen(email: String?) {
        viewHelpAndSupport(HelpOrigin.SIGNUP_EMAIL)
    }

    override fun helpSignupMagicLinkScreen(email: String?) {
        viewHelpAndSupport(HelpOrigin.SIGNUP_MAGIC_LINK)
    }

    override fun showSignupMagicLink(email: String?) {
        // TODO: Signup
    }

    override fun showSignupToLoginMessage() {
        // TODO: Signup
    }

    override fun onTermsOfServiceClicked() {
        ChromeCustomTabUtils.launchUrl(this, urlUtils.tosUrlWithLocale)
    }

    //  -- END: LoginListener implementation methods

    //  -- BEGIN: GoogleListener implementation methods

    override fun onGoogleEmailSelected(email: String?) {
        (supportFragmentManager.findFragmentByTag(LoginEmailFragment.TAG) as? LoginEmailFragment)?.setGoogleEmail(email)
    }

    override fun onGoogleLoginFinished() {
        (supportFragmentManager.findFragmentByTag(LoginEmailFragment.TAG) as? LoginEmailFragment)?.finishLogin()
    }

    override fun onGoogleSignupFinished(name: String?, email: String?, photoUrl: String?, username: String?) {
        // TODO: Signup
    }

    override fun onGoogleSignupError(msg: String?) {
        msg?.let {
            uiMessageResolver.showSnack(it)
        }
    }

    //  -- END: GoogleListener implementation methods

    override fun showJetpackInstructions() {
        ChromeCustomTabUtils.launchUrl(this, AppUrls.JETPACK_INSTRUCTIONS)
    }

    override fun showJetpackTroubleshootingTips() {
        ChromeCustomTabUtils.launchUrl(this, AppUrls.JETPACK_TROUBLESHOOTING)
    }

    override fun showWhatIsJetpackDialog() {
        LoginWhatIsJetpackDialogFragment().show(supportFragmentManager, LoginWhatIsJetpackDialogFragment.TAG)
    }

    override fun showHelpFindingConnectedEmail() {
        AnalyticsTracker.track(AnalyticsEvent.LOGIN_BY_EMAIL_HELP_FINDING_CONNECTED_EMAIL_LINK_TAPPED)
        unifiedLoginTracker.trackClick(Click.HELP_FINDING_CONNECTED_EMAIL)

        LoginEmailHelpDialogFragment.newInstance(this)
            .show(supportFragmentManager, LoginEmailHelpDialogFragment.TAG)
    }

    override fun onEmailNeedMoreHelpClicked() {
        startActivity(HelpActivity.createIntent(this, HelpOrigin.LOGIN_CONNECTED_EMAIL_HELP, null))
    }

    override fun showEmailLoginScreen(siteAddress: String?) {
        if (siteAddress != null) {
            // Show the layout that includes the option to login with site credentials.
            val loginEmailFragment = getLoginEmailFragment(
                siteCredsLayout = true
            ) ?: LoginEmailFragment.newInstance(siteAddress, true)
            changeFragment(loginEmailFragment as Fragment, true, LoginEmailFragment.TAG_SITE_CREDS_LAYOUT)
        } else {
            val loginEmailFragment = getLoginEmailFragment(
                siteCredsLayout = false
            ) ?: WooLoginEmailFragment()
            changeFragment(loginEmailFragment as Fragment, true, LoginEmailFragment.TAG)
        }
    }

    override fun showUsernamePasswordScreen(
        siteAddress: String?,
        endpointAddress: String?,
        inputUsername: String?,
        inputPassword: String?
    ) {
        val loginUsernamePasswordFragment = WooLoginUsernamePasswordFragment.newInstance(
            siteAddress, endpointAddress, inputUsername, inputPassword, false
        )
        changeFragment(loginUsernamePasswordFragment, true, WooLoginUsernamePasswordFragment.TAG)
    }

    override fun startJetpackInstall(siteAddress: String?) {
        siteAddress?.let {
            val url = "$JETPACK_CONNECT_URL?url=$it&mobile_redirect=$JETPACK_CONNECTED_REDIRECT_URL&from=mobile"
            ChromeCustomTabUtils.launchUrl(this, url)
        }
    }

    override fun gotUnregisteredEmail(email: String?) {
        // Show the 'No WordPress.com account found' screen
        val fragment = LoginNoWPcomAccountFoundFragment.newInstance(email)
        changeFragment(
            fragment = fragment as Fragment,
            shouldAddToBackStack = true,
            tag = LoginNoWPcomAccountFoundFragment.TAG
        )
    }

    override fun gotUnregisteredSocialAccount(
        email: String?,
        displayName: String?,
        idToken: String?,
        photoUrl: String?,
        service: String?
    ) {
        TODO("Not yet implemented")
    }

    override fun helpSignupConfirmationScreen(email: String?) {
        TODO("Not yet implemented")
    }

    override fun showSignupSocial(
        email: String?,
        displayName: String?,
        idToken: String?,
        photoUrl: String?,
        service: String?
    ) {
        TODO("Not yet implemented")
    }

    override fun useMagicLinkInstead(email: String?, verifyEmail: Boolean) {
        showMagicLinkRequestScreen(email, verifyEmail, allowPassword = false, forceRequestAtStart = true)
    }

    /**
     * Allows for special handling of errors that come up during the login by address: check site address.
     */
    override fun handleSiteAddressError(siteInfo: ConnectSiteInfoPayload) {
        if (!siteInfo.isWordPress) {
            // The url entered is not a WordPress site.
            val protocolRegex = Regex("^(http[s]?://)", IGNORE_CASE)
            val siteAddressClean = siteInfo.url.replaceFirst(protocolRegex, "")
            val errorMessage = getString(R.string.login_not_wordpress_site_v2)

            // hide the keyboard
            org.wordpress.android.util.ActivityUtils.hideKeyboard(this)

            // show the "not WordPress error" screen
            val genericErrorFragment = LoginSiteCheckErrorFragment.newInstance(siteAddressClean, errorMessage)
            changeFragment(
                fragment = genericErrorFragment,
                shouldAddToBackStack = true,
                tag = LoginSiteCheckErrorFragment.TAG
            )
            loginNotificationScheduler.scheduleNotification(LOGIN_SITE_ADDRESS_ERROR)
        } else {
            // Just in case we use this method for a different scenario in the future
            TODO("Handle a new error scenario")
        }
    }

    override fun onWhatIsWordPressLinkClicked() {
        ChromeCustomTabUtils.launchUrl(this, LOGIN_WITH_EMAIL_WHAT_IS_WORDPRESS_COM_ACCOUNT)
        unifiedLoginTracker.trackClick(Click.WHAT_IS_WORDPRESS_COM)
    }

    override fun onWhatIsWordPressLinkNoWpcomAccountScreenClicked() {
        ChromeCustomTabUtils.launchUrl(this, LOGIN_WITH_EMAIL_WHAT_IS_WORDPRESS_COM_ACCOUNT)
        unifiedLoginTracker.trackClick(Click.WHAT_IS_WORDPRESS_COM_ON_INVALID_EMAIL_SCREEN)
    }

    override fun onCreateAccountClicked() {
        changeFragment(SignUpFragment.newInstance(SITE_PICKER), true, SignUpFragment.TAG)
    }

    private fun getLoginHelpNotification(): String? =
        intent.extras?.getString(KEY_LOGIN_HELP_NOTIFICATION)

    override fun onCarouselFinished() {
        showPrologueFragment()
    }

    override fun onPasswordError() {
        val notificationType = when {
            !appPrefsWrapper.getLoginSiteAddress()
                .isNullOrBlank() -> LOGIN_SITE_ADDRESS_PASSWORD_ERROR

            else -> LOGIN_WPCOM_PASSWORD_ERROR
        }
        loginNotificationScheduler.scheduleNotification(notificationType)
    }

    override fun onAccountCreated(nextStep: SignUpFragment.NextStep) {
        showMainActivityAndFinish()
    }

    override fun onLoginClicked() {
        startLoginViaWPCom()
    }

    private fun processLoginHelpNotification(loginHelpNotification: String) {
        when (LoginHelpNotificationType.fromString(loginHelpNotification)) {
            LOGIN_SITE_ADDRESS_ERROR -> startLoginViaWPCom()
            LOGIN_SITE_ADDRESS_PASSWORD_ERROR,
            LOGIN_WPCOM_PASSWORD_ERROR -> useMagicLinkInstead(appPrefsWrapper.getLoginEmail(), verifyEmail = false)

            LOGIN_WPCOM_EMAIL_ERROR,
            LOGIN_SITE_ADDRESS_EMAIL_ERROR,
            DEFAULT_HELP ->
                WooLog.e(WooLog.T.NOTIFICATIONS, "Invalid notification type to be handled by LoginActivity")
        }
        loginNotificationScheduler.onNotificationTapped(loginHelpNotification)
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = MAIN)
    fun onAuthOptionsFetched(event: OnAuthOptionsFetched) {
        if (event.error?.type == UNKNOWN_USER) {
            loginNotificationScheduler.onPasswordLoginError()
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = MAIN)
    fun onFetchedConnectSiteInfo(event: OnConnectSiteInfoChecked) {
        if (event.isError) {
            connectSiteInfo = null
        } else {
            connectSiteInfo = event.info.let {
                ConnectSiteInfo(
                    isWPCom = it.isWPCom,
                    isJetpackConnected = it.isJetpackConnected,
                    isJetpackActive = it.isJetpackActive
                )
            }
        }
    }

    override fun onScanQrCodeClicked(source: String) {
        AnalyticsTracker.track(
            stat = AnalyticsEvent.LOGIN_WITH_QR_CODE_BUTTON_TAPPED,
            properties = mapOf(
                KEY_FLOW to VALUE_LOGIN_WITH_WORDPRESS_COM,
                KEY_SOURCE to source
            )
        )
        openQrCodeScannerFragment()
    }

    private fun openQrCodeScannerFragment() {
        GmsBarcodeScanning.getClient(this).startScan()
            .addOnSuccessListener { rawValue ->
                if (ValidateScannedValue.validate(rawValue.rawValue)) {
                    AnalyticsTracker.track(stat = AnalyticsEvent.LOGIN_WITH_QR_CODE_SCANNED)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawValue.rawValue))
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                } else {
                    Toast.makeText(
                        this, resources.getText(R.string.not_a_valid_qr_code), Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    @Parcelize
    private data class ConnectSiteInfo(
        val isWPCom: Boolean,
        val isJetpackConnected: Boolean,
        val isJetpackActive: Boolean
    ) : Parcelable
}
