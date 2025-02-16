package com.woocommerce.android.ui.login

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle.State
import com.woocommerce.android.AppPrefsWrapper
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.databinding.FragmentLoginNoWpcomAccountFoundBinding
import com.woocommerce.android.databinding.ViewLoginEpilogueButtonBarBinding
import com.woocommerce.android.ui.login.UnifiedLoginTracker.Click
import com.woocommerce.android.ui.login.UnifiedLoginTracker.Step
import com.zendesk.util.StringUtils
import dagger.hilt.android.AndroidEntryPoint
import org.wordpress.android.login.LoginListener
import javax.inject.Inject

@AndroidEntryPoint
class LoginNoWPcomAccountFoundFragment : Fragment(R.layout.fragment_login_no_wpcom_account_found), MenuProvider {
    interface Listener {
        fun onWhatIsWordPressLinkNoWpcomAccountScreenClicked()
        fun onCreateAccountClicked()
    }

    companion object {
        const val TAG = "LoginNoWPcomAccountFoundFragment"
        const val ARG_EMAIL_ADDRESS = "email_address"

        fun newInstance(emailAddress: String?): LoginNoWPcomAccountFoundFragment {
            val fragment = LoginNoWPcomAccountFoundFragment()
            val args = Bundle()
            args.putString(ARG_EMAIL_ADDRESS, emailAddress)
            fragment.arguments = args
            return fragment
        }
    }

    private var loginListener: LoginListener? = null
    private var emailAddress: String? = null

    @Inject
    internal lateinit var appPrefsWrapper: AppPrefsWrapper

    @Inject
    internal lateinit var unifiedLoginTracker: UnifiedLoginTracker

    private lateinit var listener: Listener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            emailAddress = it.getString(ARG_EMAIL_ADDRESS, null)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, State.RESUMED)

        val binding = FragmentLoginNoWpcomAccountFoundBinding.bind(view)
        val btnBinding = binding.loginEpilogueButtonBar

        val toolbar = view.findViewById(R.id.toolbar) as Toolbar
        (activity as AppCompatActivity).setSupportActionBar(toolbar)

        (activity as AppCompatActivity).supportActionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
            it.setDisplayShowTitleEnabled(false)
        }

        setupButtons(btnBinding)

        binding.btnLoginWhatIsWordpress.setOnClickListener {
            listener.onWhatIsWordPressLinkNoWpcomAccountScreenClicked()
        }

        binding.btnFindConnectedEmail.setOnClickListener {
            loginListener?.showHelpFindingConnectedEmail()
        }
    }

    private fun setupButtons(btnBinding: ViewLoginEpilogueButtonBarBinding) {
        with(btnBinding.buttonPrimary) {
            text = getString(R.string.login_create_an_account)
            setOnClickListener {
                appPrefsWrapper.setStoreCreationSource(AnalyticsTracker.VALUE_LOGIN_EMAIL_ERROR)
                unifiedLoginTracker.trackClick(Click.CREATE_ACCOUNT)

                listener.onCreateAccountClicked()
            }
        }

        with(btnBinding.buttonSecondary) {
            visibility = View.VISIBLE
            text = getString(R.string.login_try_another_account)
            setOnClickListener {
                unifiedLoginTracker.trackClick(Click.TRY_ANOTHER_ACCOUNT)

                loginListener?.startOver()
            }
        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_login, menu)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.help) {
            unifiedLoginTracker.trackClick(Click.SHOW_HELP)
            loginListener?.helpEmailScreen(emailAddress ?: StringUtils.EMPTY_STRING)
            return true
        }

        return false
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        // this will throw if parent activity doesn't implement the interfaces
        loginListener = context as LoginListener
        listener = activity as Listener
    }

    override fun onDetach() {
        super.onDetach()
        loginListener = null
    }

    override fun onResume() {
        super.onResume()

        unifiedLoginTracker.track(step = Step.NO_WPCOM_ACCOUNT_FOUND)
    }
}
