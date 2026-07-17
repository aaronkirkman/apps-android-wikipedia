package org.wikipedia.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import org.wikipedia.Constants
import org.wikipedia.R
import org.wikipedia.auth.AccountUtil
import org.wikipedia.settings.Prefs
import org.wikipedia.util.DimenUtil
import org.wikipedia.util.ResourceUtil
import java.util.Locale
import java.util.concurrent.TimeUnit

object NotificationPresenter {

    const val NOTIFICATION_TYPE_LOCAL = "local"
    private var lastPermissionRequestTime = 0L

    fun maybeRequestPermission(context: Context, launcher: ActivityResultLauncher<String>) {
        val millisSinceLastRequest = System.currentTimeMillis() - lastPermissionRequestTime
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Prefs.isInitialOnboardingEnabled &&
            AccountUtil.isLoggedIn &&
            (millisSinceLastRequest > TimeUnit.HOURS.toMillis(1) || millisSinceLastRequest < 0) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            lastPermissionRequestTime = System.currentTimeMillis()
        }
    }

    fun addIntentExtras(intent: Intent, id: Long, type: String = NOTIFICATION_TYPE_LOCAL): Intent {
        return intent
            .putExtra(Constants.INTENT_EXTRA_NOTIFICATION_ID, id)
            .putExtra(Constants.INTENT_EXTRA_NOTIFICATION_TYPE, type)
            .putExtra(Constants.INTENT_EXTRA_INVOKE_SOURCE, Constants.InvokeSource.NOTIFICATION)
    }

    fun getDefaultBuilder(
        context: Context, id: Long, type: String? = NOTIFICATION_TYPE_LOCAL,
        notificationCategory: NotificationCategory = NotificationCategory.SYSTEM,
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, notificationCategory.id)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
    }

    fun showNotification(
        context: Context, builder: NotificationCompat.Builder, id: Int,
        title: String, text: String, longText: CharSequence?, lang: String?,
        @DrawableRes icon: Int?, @ColorRes color: Int, bodyIntent: Intent,
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        builder.setContentIntent(PendingIntentCompat.getActivity(context, 0, bodyIntent, PendingIntent.FLAG_UPDATE_CURRENT, false))
                .setLargeIcon(if (icon != null) drawNotificationBitmap(context, color, icon, lang.orEmpty().uppercase(Locale.getDefault())) else null)
                .setSmallIcon(R.drawable.ic_wikipedia_w)
                .setColor(ContextCompat.getColor(context, color))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(longText))
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun drawNotificationBitmap(context: Context, @ColorRes color: Int, @DrawableRes icon: Int, lang: String): Bitmap {
        val bitmapHalfSize = DimenUtil.roundedDpToPx(24f)
        val iconHalfSize = DimenUtil.roundedDpToPx(14f)
        return createBitmap(bitmapHalfSize * 2, bitmapHalfSize * 2).applyCanvas {
            val p = Paint()
            p.isAntiAlias = true
            p.color = ContextCompat.getColor(context, color)

            if (lang.isNotEmpty()) {
                p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                p.textSize = DimenUtil.dpToPx(12f)
                p.strokeWidth = DimenUtil.dpToPx(1f)

                val textBounds = Rect()
                p.getTextBounds(lang, 0, lang.length, textBounds)

                val rectPadding = DimenUtil.dpToPx(4f)
                val textLeft = bitmapHalfSize.toFloat() - (textBounds.right - textBounds.left) / 2
                val textBottom = bitmapHalfSize * 2 - rectPadding - p.strokeWidth

                drawText(lang, textLeft, textBottom, p)

                p.style = Paint.Style.STROKE
                val rBounds = RectF(textLeft + textBounds.left - rectPadding, textBottom + textBounds.top - rectPadding,
                        textLeft + textBounds.right + rectPadding, textBottom + textBounds.bottom + rectPadding)
                drawRoundRect(rBounds, rectPadding, rectPadding, p)
            }

            val iconBmp = ResourceUtil.bitmapFromVectorDrawable(context, icon, color)
            drawBitmap(iconBmp, null, Rect(bitmapHalfSize - iconHalfSize, 0,
                    bitmapHalfSize + iconHalfSize, iconHalfSize * 2), null)
            iconBmp.recycle()
        }
    }
}
