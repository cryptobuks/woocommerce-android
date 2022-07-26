package com.woocommerce.android.ui.login

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.woocommerce.android.AppPrefs
import com.woocommerce.android.AppPrefsWrapper
import com.woocommerce.android.AppUrls
import com.woocommerce.android.AppUrls.LOGIN_WITH_EMAIL_WHAT_IS_WORDPRESS_COM_ACCOUNT
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsEvent
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Companion.KEY_SOURCE
import com.woocommerce.android.analytics.AnalyticsTracker.Companion.VALUE_JETPACK_INSTALLATION_SOURCE_WEB
import com.woocommerce.android.analytics.ExperimentTracker
import com.woocommerce.android.databinding.ActivityLoginBinding
import com.woocommerce.android.experiment.SiteLoginExperiment
import com.woocommerce.android.support.HelpActivity
import com.woocommerce.android.support.HelpActivity.Origin
import com.woocommerce.android.support.ZendeskExtraTags
import com.woocommerce.android.support.ZendeskHelper
import com.woocommerce.android.ui.login.LoginPrologueCarouselFragment.PrologueCarouselListener
import com.woocommerce.android.ui.login.LoginPrologueFragment.PrologueFinishedListener
import com.woocommerce.android.ui.login.UnifiedLoginTracker.Click
import com.woocommerce.android.ui.login.UnifiedLoginTracker.Flow
import com.woocommerce.android.ui.login.UnifiedLoginTracker.Flow.LOGIN_SITE_ADDRESS
import com.woocommerce.android.ui.login.UnifiedLoginTracker.Source
import com.woocommerce.android.ui.login.UnifiedLoginTracker.Step.ENTER_SITE_ADDRESS
import com.woocommerce.android.ui.login.localnotifications.LoginNotificationScheduler
import com.woocommerce.android.ui.login.localnotifications.LoginNotificationScheduler.Companion.LOGIN_HELP_NOTIFICATION_ID
import com.woocommerce.android.ui.login.localnotifications.LoginNotificationScheduler.Companion.LOGIN_HELP_NOTIFICATION_TAG
import com.woocommerce.android.ui.login.localnotifications.LoginNotificationScheduler.LoginHelpNotificationType
import com.woocommerce.android.ui.login.overrides.WooLoginEmailFragment
import com.woocommerce.android.ui.login.overrides.WooLoginSiteAddressFragment
import com.woocommerce.android.ui.main.MainActivity
import com.woocommerce.android.util.ActivityUtils
import com.woocommerce.android.util.ChromeCustomTabUtils
import com.woocommerce.android.util.UrlUtils
import com.woocommerce.android.util.WooLog
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import dagger.hilt.android.AndroidEntryPoint
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.MAIN
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.network.MemorizingTrustManager
import org.wordpress.android.fluxc.store.AccountStore.AuthEmailPayloadScheme.WOOCOMMERCE
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
import org.wordpress.android.login.LoginMagicLinkSentFragment
import org.wordpress.android.login.LoginMode
import org.wordpress.android.login.LoginSiteAddressFragment
import org.wordpress.android.login.LoginUsernamePasswordFragment
import org.wordpress.android.util.ToastUtils
import javax.inject.Inject
import kotlin.text.RegexOption.IGNORE_CASE

@Suppress("SameParameterValue")
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
    WooLoginEmailFragment.Listener {
    companion object {
        private const val FORGOT_PASSWORD_URL_SUFFIX = "wp-login.php?action=lostpassword"
        private const val MAGIC_LOGIN = "magic-login"
        private const val TOKEN_PARAMETER = "token"
        private const val JETPACK_CONNECT_URL = "https://wordpress.com/jetpack/connect"
        private const val JETPACK_CONNECTED_REDIRECT_URL = "woocommerce://jetpack-connected"

        private const val KEY_UNIFIED_TRACKER_SOURCE = "KEY_UNIFIED_TRACKER_SOURCE"
        private const val KEY_UNIFIED_TRACKER_FLOW = "KEY_UNIFIED_TRACKER_FLOW"
        private const val KEY_LOGIN_HELP_NOTIFICATION = "KEY_LOGIN_HELP_NOTIFICATION"

        fun createIntent(
            context: Context,
            notificationType: LoginHelpNotificationType
        ): Intent {
            val intent = Intent(context, LoginActivity::class.java)
            intent.apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(KEY_LOGIN_HELP_NOTIFICATION, notificationType.toString())
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
    @Inject internal lateinit var siteLoginExperiment: SiteLoginExperiment

    private var loginMode: LoginMode? = null
    private var isSiteOnWPcom: Boolean? = null

    private lateinit var binding: ActivityLoginBinding

    override fun androidInjector(): AndroidInjector<Any> = androidInjector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dispatcher.register(this)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val loginHelpNotification = getLoginHelpNotification()

        if (hasJetpackConnectedIntent()) {
            AnalyticsTracker.track(
                stat = AnalyticsEvent.LOGIN_JETPACK_SETUP_COMPLETED,
                properties = mapOf(KEY_SOURCE to VALUE_JETPACK_INSTALLATION_SOURCE_WEB)
            )
            startLoginViaWPCom()
        } else if (hasMagicLinkLoginIntent()) {
            getAuthTokenFromIntent()?.let { showMagicLinkInterceptFragment(it) }
        } else if (!loginHelpNotification.isNullOrBlank()) {
            processLoginHelpNotification(loginHelpNotification)
        } else if (savedInstanceState == null) {
            loginAnalyticsListener.trackLoginAccessed()

            showPrologue()
        }

        savedInstanceState?.let { ss ->
            unifiedLoginTracker.setSource(ss.getString(KEY_UNIFIED_TRACKER_SOURCE, Source.DEFAULT.value))
            unifiedLoginTracker.setFlow(ss.getString(KEY_UNIFIED_TRACKER_FLOW))
        }
    }

    private fun processLoginHelpNotification(loginHelpNotification: String) {
        startLoginViaWPCom()
        NotificationManagerCompat.from(this).cancel(
            LOGIN_HELP_NOTIFICATION_TAG,
            LOGIN_HELP_NOTIFICATION_ID
        )
        AnalyticsTracker.track(
            AnalyticsEvent.LOGIN_LOCAL_NOTIFICATION_TAPPED,
            mapOf(AnalyticsTracker.KEY_TYPE to loginHelpNotification)
        )
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
        unifiedLoginTracker.getFlow()?.value?.let {
            outState.putString(KEY_UNIFIED_TRACKER_FLOW, it)
        }
    }

    private fun showPrologueCarouselFragment() {
        val fragment = LoginPrologueCarouselFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, LoginPrologueCarouselFragment.TAG)
            .addToBackStack(LoginPrologueCarouselFragment.TAG)
            .commitAllowingStateLoss()

        experimentTracker.log(ExperimentTracker.PROLOGUE_CAROUSEL_DISPLAYED_EVENT)
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
            .replace(R.id.fragment_container, fragment, LoginPrologueFragment.TAG)
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }

    private fun hasJetpackConnectedIntent(): Boolean {
        val action = intent.action
        val uri = intent.data

        return Intent.ACTION_VIEW == action && uri.toString() == JETPACK_CONNECTED_REDIRECT_URL
    }

    private fun slideInFragment(fragment: Fragment, shouldAddToBackStack: Boolean, tag: String) {
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.setCustomAnimations(
            R.anim.default_enter_anim,
            R.anim.default_exit_anim,
            R.anim.default_pop_enter_anim,
            R.anim.default_pop_exit_anim
        )
        fragmentTransaction.replace(R.id.fragment_container, fragment, tag)
        if (shouldAddToBackStack) {
            fragmentTransaction.addToBackStack(null)
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

    private fun getLoginViaSiteAddressFragment(): LoginSiteAddressFragment? =
        supportFragmentManager.findFragmentByTag(LoginSiteAddressFragment.TAG) as? WooLoginSiteAddressFragment

    private fun getPrologueFragment(): LoginPrologueFragment? =
        supportFragmentManager.findFragmentByTag(LoginPrologueFragment.TAG) as? LoginPrologueFragment

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }

        return false
    }

    override fun onBackPressed() {
        AnalyticsTracker.trackBackPressed(this)

        if (supportFragmentManager.backStackEntryCount == 1) {
            finish()
        } else {
            super.onBackPressed()
        }
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

    private fun showMainActivityAndFinish() {
        siteLoginExperiment.trackSuccess()
        loginNotificationScheduler.onLoginSuccess()

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun jumpToUsernamePassword(username: String?, password: String?) {
        val loginUsernamePasswordFragment = LoginUsernamePasswordFragment.newInstance(
            "wordpress.com", "wordpress.com", username, password, true
        )
        slideInFragment(loginUsernamePasswordFragment, true, LoginUsernamePasswordFragment.TAG)
    }

    private fun startLoginViaWPCom() {
        unifiedLoginTracker.setFlow(Flow.WORDPRESS_COM.value)
        showEmailLoginScreen()
    }

    override fun gotWpcomEmail(email: String?, verifyEmail: Boolean, authOptions: AuthOptions?) {
        val isMagicLinkEnabled =
            getLoginMode() != LoginMode.WPCOM_LOGIN_DEEPLINK && getLoginMode() != LoginMode.SHARE_INTENT

        if (authOptions != null) {
            if (authOptions.isPasswordless) {
                showMagicLinkRequestScreen(email, verifyEmail, allowPassword = false, forceRequestAtStart = true)
            } else {
                showEmailPasswordScreen(email, verifyEmail, isMagicLinkEnabled)
            }
        } else {
            if (isMagicLinkEnabled) {
                showMagicLinkRequestScreen(email, verifyEmail, allowPassword = true, forceRequestAtStart = false)
            } else {
                showEmailPasswordScreen(email, verifyEmail, false)
            }
        }
    }

    private fun showEmailPasswordScreen(email: String?, verifyEmail: Boolean, allowMagicLink: Boolean) {
        val loginEmailPasswordFragment = LoginEmailPasswordFragment
            .newInstance(email, null, null, null, false, allowMagicLink, verifyEmail)
        slideInFragment(loginEmailPasswordFragment, true, LoginEmailPasswordFragment.TAG)
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
        slideInFragment(loginMagicLinkRequestFragment, true, LoginMagicLinkRequestFragment.TAG)
    }

    override fun loginViaSiteAddress() {
        unifiedLoginTracker.setFlowAndStep(LOGIN_SITE_ADDRESS, ENTER_SITE_ADDRESS)
        val loginSiteAddressFragment = getLoginViaSiteAddressFragment() ?: WooLoginSiteAddressFragment()
        slideInFragment(loginSiteAddressFragment, true, LoginSiteAddressFragment.TAG)
    }

    private fun showPrologueFragment() {
        val prologueFragment = getPrologueFragment() ?: LoginPrologueFragment()
        slideInFragment(prologueFragment, true, LoginPrologueFragment.TAG)
    }

    override fun loginViaSocialAccount(
        email: String?,
        idToken: String?,
        service: String?,
        isPasswordRequired: Boolean
    ) {
        val loginEmailPasswordFragment = LoginEmailPasswordFragment.newInstance(
            email, null, idToken,
            service, isPasswordRequired
        )
        slideInFragment(loginEmailPasswordFragment, true, LoginEmailPasswordFragment.TAG)
    }

    override fun loggedInViaSocialAccount(oldSitesIds: ArrayList<Int>, doLoginUpdate: Boolean) {
        loginAnalyticsListener.trackLoginSocialSuccess()
        showMainActivityAndFinish()
    }

    override fun loginViaWpcomUsernameInstead() {
        jumpToUsernamePassword(null, null)
    }

    override fun showMagicLinkSentScreen(email: String?, allowPassword: Boolean) {
        val loginMagicLinkSentFragment = LoginMagicLinkSentFragment.newInstance(email, allowPassword)
        slideInFragment(loginMagicLinkSentFragment, true, LoginMagicLinkSentFragment.TAG)
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
        val loginEmailPasswordFragment = LoginEmailPasswordFragment.newInstance(email, null, null, null, false)
        slideInFragment(loginEmailPasswordFragment, true, LoginEmailPasswordFragment.TAG)
    }

    override fun forgotPassword(url: String?) {
        loginAnalyticsListener.trackLoginForgotPasswordClicked()
        ChromeCustomTabUtils.launchUrl(this, url + FORGOT_PASSWORD_URL_SUFFIX)
    }

    override fun needs2fa(email: String?, password: String?) {
        val login2FaFragment = Login2FaFragment.newInstance(email, password)
        slideInFragment(login2FaFragment, true, Login2FaFragment.TAG)
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
        slideInFragment(login2FaFragment, true, Login2FaFragment.TAG)
    }

    override fun needs2faSocialConnect(email: String?, password: String?, idToken: String?, service: String?) {
        loginAnalyticsListener.trackLoginSocial2faNeeded()
        val login2FaFragment = Login2FaFragment.newInstanceSocialConnect(email, password, idToken, service)
        slideInFragment(login2FaFragment, true, Login2FaFragment.TAG)
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
        val inputSiteAddress = redirectUrl ?: siteAddress

        // Save site address to app prefs so it's available to MainActivity regardless of how the user
        // logs into the app. Strip the protocol from this url string prior to saving to AppPrefs since it's
        // not needed and may cause issues when attempting to match the url to the authenticated account later
        // in the login process.
        val protocolRegex = Regex("^(http[s]?://)", IGNORE_CASE)
        val siteAddressClean = inputSiteAddress.replaceFirst(protocolRegex, "")
        AppPrefs.setLoginSiteAddress(siteAddressClean)

        lifecycleScope.launchWhenStarted {
            if (hasJetpack) {
                // if a site is self-hosted, we show either email login screen or site credentials login screen
                if (isSiteOnWPcom != true) {
                    siteLoginExperiment.run(inputSiteAddress, ::showEmailLoginScreen, ::loginViaSiteCredentials)
                } else {
                    showEmailLoginScreen(inputSiteAddress)
                }
            } else {
                // Let user log in via site credentials first before showing Jetpack missing screen.
                loginViaSiteCredentials(inputSiteAddress)
            }
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

        val loginUsernamePasswordFragment = LoginUsernamePasswordFragment.newInstance(
            inputSiteAddress, endpointAddress, null, null, false
        )
        slideInFragment(loginUsernamePasswordFragment, true, LoginUsernamePasswordFragment.TAG)
    }

    override fun handleSslCertificateError(
        memorizingTrustManager: MemorizingTrustManager?,
        callback: LoginListener.SelfSignedSSLCallback?
    ) {
        WooLog.e(WooLog.T.LOGIN, "Self-signed SSL certificate detected - can't proceed with the login.")
        // TODO: Support self-signed SSL sites and show dialog (only needed when XML-RPC support is added)
    }

    private fun viewHelpAndSupport(origin: Origin) {
        val extraSupportTags = arrayListOf(ZendeskExtraTags.connectingJetpack)
        startActivity(HelpActivity.createIntent(this, origin, extraSupportTags))
    }

    override fun helpSiteAddress(url: String?) {
        viewHelpAndSupport(Origin.LOGIN_SITE_ADDRESS)
    }

    override fun helpFindingSiteAddress(username: String?, siteStore: SiteStore?) {
        unifiedLoginTracker.trackClick(Click.HELP_FINDING_SITE_ADDRESS)
        zendeskHelper.createNewTicket(this, Origin.LOGIN_SITE_ADDRESS, null)
    }

    // TODO This can be modified to also receive the URL the user entered, so we can make that the primary store
    override fun loggedInViaUsernamePassword(oldSitesIds: ArrayList<Int>) {
        showMainActivityAndFinish()
    }

    override fun helpEmailScreen(email: String?) {
        viewHelpAndSupport(Origin.LOGIN_EMAIL)
    }

    override fun helpSocialEmailScreen(email: String?) {
        viewHelpAndSupport(Origin.LOGIN_SOCIAL)
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
        viewHelpAndSupport(Origin.LOGIN_MAGIC_LINK)
    }

    override fun helpMagicLinkSent(email: String?) {
        viewHelpAndSupport(Origin.LOGIN_MAGIC_LINK)
    }

    override fun helpEmailPasswordScreen(email: String?) {
        viewHelpAndSupport(Origin.LOGIN_EMAIL_PASSWORD)
    }

    override fun help2FaScreen(email: String?) {
        viewHelpAndSupport(Origin.LOGIN_2FA)
    }

    override fun startPostLoginServices() {
        // TODO Start future NotificationsUpdateService
    }

    override fun helpUsernamePassword(url: String?, username: String?, isWpcom: Boolean) {
        viewHelpAndSupport(Origin.LOGIN_USERNAME_PASSWORD)
    }

    override fun helpNoJetpackScreen(
        siteAddress: String,
        endpointAddress: String?,
        username: String,
        password: String,
        userAvatarUrl: String?,
        checkJetpackAvailability: Boolean
    ) {
        val jetpackReqFragment = LoginNoJetpackFragment.newInstance(
            siteAddress, endpointAddress, username, password, userAvatarUrl,
            checkJetpackAvailability
        )
        slideInFragment(
            fragment = jetpackReqFragment as Fragment,
            shouldAddToBackStack = true,
            tag = LoginJetpackRequiredFragment.TAG
        )
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
        slideInFragment(
            fragment = discoveryErrorFragment as Fragment,
            shouldAddToBackStack = true,
            tag = LoginJetpackRequiredFragment.TAG
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
        viewHelpAndSupport(Origin.SIGNUP_EMAIL)
    }

    override fun helpSignupMagicLinkScreen(email: String?) {
        viewHelpAndSupport(Origin.SIGNUP_MAGIC_LINK)
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
        Snackbar.make(binding.mainView, msg ?: "", BaseTransientBottomBar.LENGTH_LONG).show()
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

        LoginEmailHelpDialogFragment().show(supportFragmentManager, LoginEmailHelpDialogFragment.TAG)
    }

    override fun onEmailNeedMoreHelpClicked() {
        startActivity(HelpActivity.createIntent(this, Origin.LOGIN_CONNECTED_EMAIL_HELP, null))
    }

    override fun showEmailLoginScreen(siteAddress: String?) {
        if (siteAddress != null) {
            // Show the layout that includes the option to login with site credentials.
            val loginEmailFragment = getLoginEmailFragment(
                siteCredsLayout = true
            ) ?: LoginEmailFragment.newInstance(siteAddress, true)
            slideInFragment(loginEmailFragment as Fragment, true, LoginEmailFragment.TAG_SITE_CREDS_LAYOUT)
        } else {
            val loginEmailFragment = getLoginEmailFragment(
                siteCredsLayout = false
            ) ?: WooLoginEmailFragment()
            slideInFragment(loginEmailFragment as Fragment, true, LoginEmailFragment.TAG)
        }
    }

    override fun showUsernamePasswordScreen(
        siteAddress: String?,
        endpointAddress: String?,
        inputUsername: String?,
        inputPassword: String?
    ) {
        val loginUsernamePasswordFragment = LoginUsernamePasswordFragment.newInstance(
            siteAddress, endpointAddress, inputUsername, inputPassword, false
        )
        slideInFragment(loginUsernamePasswordFragment, true, LoginUsernamePasswordFragment.TAG)
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
        slideInFragment(
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
            slideInFragment(
                fragment = genericErrorFragment,
                shouldAddToBackStack = true,
                tag = LoginSiteCheckErrorFragment.TAG
            )
            loginNotificationScheduler.scheduleNotification(LoginHelpNotificationType.LOGIN_SITE_ADDRESS_ERROR)
        } else {
            // Just in case we use this method for a different scenario in the future
            TODO("Handle a new error scenario")
        }
    }

    override fun onWhatIsWordPressLinkClicked() {
        ChromeCustomTabUtils.launchUrl(this, LOGIN_WITH_EMAIL_WHAT_IS_WORDPRESS_COM_ACCOUNT)
        unifiedLoginTracker.trackClick(Click.WHAT_IS_WORDPRESS_COM)
    }

    private fun getLoginHelpNotification(): String? =
        intent.extras?.getString(KEY_LOGIN_HELP_NOTIFICATION)

    override fun onCarouselFinished() {
        showPrologueFragment()
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = MAIN)
    fun onFetchedConnectSiteInfo(event: OnConnectSiteInfoChecked) {
        isSiteOnWPcom = event.info.isWPCom
    }
}
