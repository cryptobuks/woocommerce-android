package com.woocommerce.android.ui.shipping

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.ExperimentalTransitionApi
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.createChildTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import com.woocommerce.android.ui.shipping.InstallWCShippingViewModel.ViewState
import com.woocommerce.android.ui.shipping.InstallWCShippingViewModel.ViewState.InstallationState

@Composable
fun InstallWCShippingScreen(viewModel: InstallWCShippingViewModel) {
    val installWcShippingFlowState by viewModel.viewState.observeAsState()
    installWcShippingFlowState?.let {
        InstallWCShippingScreen(it)
    }
}

@OptIn(ExperimentalTransitionApi::class, ExperimentalAnimationApi::class)
@Composable
fun InstallWCShippingScreen(viewState: ViewState) {
    val transition = updateTransition(viewState, label = "MainTransition")
    Box(modifier = Modifier.background(color = MaterialTheme.colors.surface)) {
        transition.AnimatedContent(
            transitionSpec = {
                if (initialState is ViewState.Onboarding && targetState is InstallationState) {
                    // Apply a fade-in/fade-out globally,
                    // then each child will animate the individual components separately
                    fadeIn(tween(500, delayMillis = 500))
                        .with(fadeOut(tween(500, easing = LinearOutSlowInEasing)))
                } else {
                    // TODO
                    EnterTransition.None.with(ExitTransition.None)
                }
            }
        ) { targetState ->
            when (targetState) {
                is ViewState.Onboarding -> InstallWcShippingOnboarding(
                    viewState = targetState,
                    transition = transition.createChildTransition(label = "OnboardingTransition") {
                        it is ViewState.Onboarding
                    }
                )
                is InstallationState -> InstallWCShippingFlow(
                    viewState = targetState,
                    transition = transition.createChildTransition(label = "InstallationTransition") {
                        it is InstallationState
                    }
                )
            }
        }
    }
}
