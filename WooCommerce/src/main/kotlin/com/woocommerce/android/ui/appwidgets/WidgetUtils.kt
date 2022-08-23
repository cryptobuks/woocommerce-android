package com.woocommerce.android.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.woocommerce.android.R
import com.woocommerce.android.extensions.putColorMode
import com.woocommerce.android.ui.main.MainActivity
import com.woocommerce.android.ui.widgets.stats.StatsWidget.Companion.SITE_ID_KEY
import com.woocommerce.android.viewmodel.ResourceProvider
import java.util.Date
import javax.inject.Inject
import kotlin.random.Random

/**
 * Utils class for for displaying data and handling click events in widgets.
 * Currently used to display stats widget list OR error message if stats is unavailable.
 */
class WidgetUtils
@Inject constructor() {
    fun getLayout(colorMode: WidgetColorMode): Int {
        return when (colorMode) {
            WidgetColorMode.DARK -> R.layout.stats_widget_list_dark
            WidgetColorMode.LIGHT -> R.layout.stats_widget_list_light
        }
    }

    fun showList(
        appWidgetManager: AppWidgetManager,
        views: RemoteViews,
        context: Context,
        appWidgetId: Int,
        colorMode: WidgetColorMode,
        siteId: Int
    ) {
        views.setPendingIntentTemplate(R.id.widget_content, getPendingTemplate(context))
        views.setViewVisibility(R.id.widget_content, View.VISIBLE)
        views.setViewVisibility(R.id.widget_error, View.GONE)

        val listIntent = Intent(context, WidgetService::class.java)
        listIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        listIntent.putColorMode(colorMode)
        listIntent.putExtra(SITE_ID_KEY, siteId)
        listIntent.data = Uri.parse(listIntent.toUri(Intent.URI_INTENT_SCHEME))

        views.setRemoteAdapter(R.id.widget_content, listIntent)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_content)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    fun showError(
        appWidgetManager: AppWidgetManager,
        views: RemoteViews,
        appWidgetId: Int,
        errorMessage: Int,
        resourceProvider: ResourceProvider,
        context: Context,
        widgetType: Class<*>
    ) {
        views.setOnClickPendingIntent(
            R.id.widget_title_container,
            PendingIntent.getActivity(
                context,
                0,
                Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        views.setViewVisibility(R.id.widget_content, View.GONE)
        views.setViewVisibility(R.id.widget_error, View.VISIBLE)
        views.setTextViewText(
            R.id.widget_error_message,
            resourceProvider.getString(errorMessage)
        )
        val pendingSync = getRetryIntent(context, widgetType, appWidgetId)
        views.setOnClickPendingIntent(R.id.widget_error, pendingSync)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    fun getPendingSelfIntent(
        context: Context,
        localSiteId: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        // TODO: pass the localSiteId to the activity in order to auto login the
        // user to the widget's site id
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            getRandomId(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun getRetryIntent(
        context: Context,
        widgetType: Class<*>,
        appWidgetId: Int
    ): PendingIntent? {
        val intentSync = Intent(context, widgetType)
        intentSync.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE

        intentSync.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        return PendingIntent.getBroadcast(
            context,
            Random(appWidgetId).nextInt(),
            intentSync,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun getPendingTemplate(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            getRandomId(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun getRandomId(): Int {
        return Random(Date().time).nextInt()
    }
}
