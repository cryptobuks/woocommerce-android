package com.woocommerce.android.ui.login.localnotifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.woocommerce.android.R
import com.woocommerce.android.model.Notification
import com.woocommerce.android.push.NotificationChannelType
import com.woocommerce.android.push.WooNotificationBuilder
import com.woocommerce.android.push.WooNotificationType
import com.woocommerce.android.ui.login.localnotifications.LoginFlowUsageTracker.Companion.LOGIN_NOTIFICATION_TYPE_KEY
import com.woocommerce.android.ui.login.localnotifications.LoginFlowUsageTracker.LoginSupportNotificationType
import com.woocommerce.android.ui.login.localnotifications.LoginFlowUsageTracker.LoginSupportNotificationType.DEFAULT_SUPPORT
import com.woocommerce.android.ui.login.localnotifications.LoginFlowUsageTracker.LoginSupportNotificationType.LOGIN_ERROR_WRONG_EMAIL
import com.woocommerce.android.ui.login.localnotifications.LoginFlowUsageTracker.LoginSupportNotificationType.NO_LOGIN_INTERACTION
import com.woocommerce.android.ui.login.localnotifications.LoginFlowUsageTracker.LoginSupportNotificationType.valueOf
import com.woocommerce.android.viewmodel.ResourceProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class LocalNotificationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val wooNotificationBuilder: WooNotificationBuilder,
    private val resourceProvider: ResourceProvider
) : Worker(appContext, workerParams) {
    companion object {
        const val PRE_LOGIN_LOCAL_NOTIFICATION_ID = 1
    }

    override fun doWork(): Result {
        when (getNotificationType()) {
            NO_LOGIN_INTERACTION -> noInteractionNotification()
            LOGIN_ERROR_WRONG_EMAIL -> wrongEmailNotification()
            DEFAULT_SUPPORT -> defaultLoginSupportNotification()
        }
        // Indicate whether the work finished successfully with the Result
        return Result.success()
    }

    private fun defaultLoginSupportNotification() {
        wooNotificationBuilder.buildAndDisplayPreLoginLocalNotification(
            notificationLocalId = PRE_LOGIN_LOCAL_NOTIFICATION_ID,
            appContext.getString(R.string.notification_channel_pre_login_id),
            notification = Notification(
                noteId = PRE_LOGIN_LOCAL_NOTIFICATION_ID,
                uniqueId = 0,
                remoteNoteId = 0,
                remoteSiteId = 0,
                icon = null,
                noteTitle = resourceProvider.getString(R.string.login_local_notification_no_interaction_title),
                noteMessage = resourceProvider.getString(R.string.login_local_notification_no_interaction_description),
                noteType = WooNotificationType.PRE_LOGIN,
                channelType = NotificationChannelType.PRE_LOGIN
            )
        )
    }

    private fun wrongEmailNotification() {
        TODO("Not yet implemented")
    }

    private fun noInteractionNotification() {
        TODO("Not yet implemented")
    }

    private fun getNotificationType(): LoginSupportNotificationType = runCatching {
        valueOf(inputData.getString(LOGIN_NOTIFICATION_TYPE_KEY).orEmpty())
    }.getOrDefault(DEFAULT_SUPPORT)
}
