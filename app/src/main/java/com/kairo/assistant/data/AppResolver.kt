package com.kairo.assistant.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.kairo.assistant.nlu.FuzzyMatch

class AppResolver(private val context: Context? = null) {

    private var installedApps: List<Pair<String, String>> = emptyList()

    // Constructor/helper for testing
    fun setAppsForTesting(apps: List<Pair<String, String>>) {
        installedApps = apps
    }

    fun loadApps() {
        val context = context ?: return
        val apps = mutableListOf<Pair<String, String>>()
        try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfoList = pm.queryIntentActivities(intent, 0)
            for (resolveInfo in resolveInfoList) {
                val appLabel = resolveInfo.loadLabel(pm).toString()
                val packageName = resolveInfo.activityInfo.packageName
                apps.add(Pair(appLabel, packageName))
            }
        } catch (e: Exception) {
            Log.e("AppResolver", "Error loading apps", e)
        }
        installedApps = apps
    }

    fun resolve(spokenName: String): Pair<String, String>? {
        val match = FuzzyMatch.bestMatch(
            spokenName,
            installedApps.map { it.first },
            threshold = 0.55f
        ) ?: return null
        return installedApps.firstOrNull { it.first == match.first }
    }

    fun refresh() {
        loadApps()
    }
}
