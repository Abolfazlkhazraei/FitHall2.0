package com.fithall.app

/**
 * Usage analytics. Only active after the user opts in. Events say which feature was used, never
 * health values, meal contents, names or emails, and no user id is set. Android uses Google
 * Analytics for Firebase.
 */
interface Analytics {
    fun setEnabled(enabled: Boolean)
    /** [name] and parameter keys use snake_case, as Firebase expects. */
    fun event(name: String, params: Map<String, String> = emptyMap())
    fun screen(name: String) = event("screen_view", mapOf("screen_name" to name))
}

object NoAnalytics : Analytics {
    override fun setEnabled(enabled: Boolean) = Unit
    override fun event(name: String, params: Map<String, String>) = Unit
}
