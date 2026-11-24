package com.example.emerisstudytrack

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Last-resort UI used when a screen cannot be inflated or initialised.
 * It keeps the process alive and gives the user a safe route back to Home.
 */
object ScreenRecovery {

    private const val TAG = "StudyTrackScreen"

    fun show(activity: Activity, screenName: String, error: Throwable) {
        Log.e(TAG, "$screenName could not be opened", error)

        val density = activity.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.screen_recovery_title)
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.screen_recovery_message, screenName)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(24))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        root.addView(Button(activity).apply {
            text = activity.getString(R.string.try_again)
            setOnClickListener { activity.recreate() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        root.addView(Button(activity).apply {
            text = activity.getString(R.string.return_home)
            setOnClickListener {
                activity.startActivity(
                    Intent(activity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                )
                if (activity !is MainActivity) activity.finish()
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(12)
        })

        activity.setContentView(root)
    }
}
