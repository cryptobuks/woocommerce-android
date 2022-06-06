package com.woocommerce.android.ui.payments

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.databinding.FragmentTakePaymentBinding
import com.woocommerce.android.extensions.exhaustive
import com.woocommerce.android.extensions.handleDialogNotice
import com.woocommerce.android.extensions.handleDialogResult
import com.woocommerce.android.extensions.navigateSafely
import com.woocommerce.android.ui.base.BaseFragment
import com.woocommerce.android.ui.dialog.WooDialog
import com.woocommerce.android.ui.payments.TakePaymentViewModel.NavigateToCardReaderHubFlow
import com.woocommerce.android.ui.payments.TakePaymentViewModel.NavigateToCardReaderPaymentFlow
import com.woocommerce.android.ui.payments.TakePaymentViewModel.NavigateToCardReaderRefundFlow
import com.woocommerce.android.ui.payments.TakePaymentViewModel.SharePaymentUrl
import com.woocommerce.android.ui.payments.TakePaymentViewModel.TakePaymentViewState.Loading
import com.woocommerce.android.ui.payments.TakePaymentViewModel.TakePaymentViewState.Success
import com.woocommerce.android.ui.payments.cardreader.connect.CardReaderConnectDialogFragment
import com.woocommerce.android.ui.payments.cardreader.payment.CardReaderPaymentDialogFragment
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.Exit
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowDialog
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowSnackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TakePaymentFragment : BaseFragment(R.layout.fragment_take_payment) {
    private val viewModel: TakePaymentViewModel by viewModels()
    private val sharePaymentUrlLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onSharePaymentUrlCompleted()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = FragmentTakePaymentBinding.bind(view)

        setUpObservers(binding)
        setupResultHandlers()
    }

    private fun setUpObservers(binding: FragmentTakePaymentBinding) {
        handleViewState(binding)
        handleEvents(binding)
    }

    private fun handleViewState(binding: FragmentTakePaymentBinding) {
        viewModel.viewStateData.observe(viewLifecycleOwner) { state ->
            when (state) {
                Loading -> renderLoadingState(binding)
                is Success -> renderSuccessfulState(binding, state)
            }.exhaustive
        }
    }

    private fun renderLoadingState(binding: FragmentTakePaymentBinding) {
        binding.container.isVisible = false
        binding.pbLoading.isVisible = true
    }

    private fun renderSuccessfulState(
        binding: FragmentTakePaymentBinding,
        state: Success
    ) {
        binding.container.isVisible = true
        binding.pbLoading.isVisible = false
        requireActivity().title = getString(
            R.string.simple_payments_take_payment_button,
            state.orderTotal
        )

        binding.textCash.setOnClickListener {
            viewModel.onCashPaymentClicked()
        }
        binding.textCard.setOnClickListener {
            viewModel.onCardPaymentClicked()
        }

        if (state.paymentUrl.isNotEmpty()) {
            binding.textShare.setOnClickListener {
                viewModel.onSharePaymentUrlClicked()
            }
        } else {
            binding.textShare.isVisible = false
        }
    }

    private fun handleEvents(binding: FragmentTakePaymentBinding) {
        viewModel.event.observe(
            viewLifecycleOwner
        ) { event ->
            when (event) {
                is ShowDialog -> {
                    event.showDialog()
                }
                is ShowSnackbar -> {
                    Snackbar.make(
                        binding.container,
                        event.message,
                        BaseTransientBottomBar.LENGTH_LONG
                    ).show()
                }
                is Exit -> {
                    findNavController().navigateSafely(R.id.orders)
                }
                is SharePaymentUrl -> {
                    sharePaymentUrl(event.storeName, event.paymentUrl)
                }
                is NavigateToCardReaderPaymentFlow -> {
                    val action =
                        TakePaymentFragmentDirections.actionTakePaymentFragmentToCardReaderPaymentFlow(
                            event.cardReaderFlowParam
                        )
                    findNavController().navigate(action)
                }
                is NavigateToCardReaderHubFlow -> {
                    val action =
                        TakePaymentFragmentDirections.actionTakePaymentFragmentToCardReaderHubFlow(
                            event.cardReaderFlowParam
                        )
                    findNavController().navigate(action)
                }
                is NavigateToCardReaderRefundFlow -> {
                    val action =
                        TakePaymentFragmentDirections.actionTakePaymentFragmentToCardReaderRefundFlow(
                            event.cardReaderFlowParam
                        )
                    findNavController().navigate(action)
                }
            }
        }
    }

    private fun setupResultHandlers() {
        handleDialogResult<Boolean>(
            key = CardReaderConnectDialogFragment.KEY_CONNECT_TO_READER_RESULT,
            entryId = R.id.takePaymentFragment
        ) { connected ->
            viewModel.onConnectToReaderResultReceived(connected)
        }

        handleDialogNotice<String>(
            key = CardReaderPaymentDialogFragment.KEY_CARD_PAYMENT_RESULT,
            entryId = R.id.takePaymentFragment
        ) {
            viewModel.onCardReaderPaymentCompleted()
        }
    }

    private fun sharePaymentUrl(storeName: String, paymentUrl: String) {
        val title = getString(R.string.simple_payments_share_payment_dialog_title, storeName)
        val shareIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, paymentUrl)
            putExtra(Intent.EXTRA_SUBJECT, title)
            type = "text/plain"
        }
        sharePaymentUrlLauncher.launch(Intent.createChooser(shareIntent, title))
    }

    override fun onResume() {
        super.onResume()
        AnalyticsTracker.trackViewShown(this)
    }

    override fun onStop() {
        super.onStop()
        WooDialog.onCleared()
    }
}
