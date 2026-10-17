package com.laracha.app.widgets

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HabitListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = HabitListWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { WidgetConfigStore.deleteListWidget(context, it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
