package com.example.emerisstudytrack

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Centralises all top-level navigation so the dashboard buttons and bottom bar
 * behave in exactly the same way. The tap guard prevents two activities from
 * being launched when a user taps quickly or double-taps a navigation item.
 */
object NavigationHelper {

    private const val TAG = "StudyTrackNavigation"
    private const val TAP_GUARD_MS = 450L

    private var lastNavigationAt = 0L

    fun setup(
        activity: Activity,
        navigation: BottomNavigationView,
        selectedItemId: Int
    ) {
        syncSelection(navigation, selectedItemId)

        navigation.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) {
                return@setOnItemSelectedListener true
            }

            navigateTo(activity, item.itemId)
        }

        navigation.setOnItemReselectedListener {
            // Re-tapping the open tab should never create another Activity.
        }
    }

    /** Updates the visual selection without invoking the navigation listener. */
    fun syncSelection(navigation: BottomNavigationView, selectedItemId: Int) {
        navigation.menu.findItem(selectedItemId)?.isChecked = true
    }

    /**
     * Opens one of the four top-level destinations. Returns true when the tap
     * was handled, including a repeated tap on the activity already on screen.
     */
    fun navigateTo(activity: Activity, destinationItemId: Int): Boolean {
        val destination = destinationClass(destinationItemId) ?: return false

        if (destination.isAssignableFrom(activity.javaClass)) {
            return true
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastNavigationAt < TAP_GUARD_MS) {
            return true
        }
        lastNavigationAt = now

        return try {
            activity.startActivity(
                Intent(activity, destination).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
            activity.overridePendingTransition(0, 0)
            true
        } catch (error: Exception) {
            Log.e(TAG, "Unable to open destination $destinationItemId", error)
            Toast.makeText(
                activity,
                R.string.navigation_error,
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }

    private fun destinationClass(itemId: Int): Class<out Activity>? = when (itemId) {
        R.id.nav_home -> MainActivity::class.java
        R.id.nav_tasks -> AllTasksActivity::class.java
        R.id.nav_today -> TodayPlanActivity::class.java
        R.id.nav_progress -> ProgressActivity::class.java
        else -> null
    }
}
