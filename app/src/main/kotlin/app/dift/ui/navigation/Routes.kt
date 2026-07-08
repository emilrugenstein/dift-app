package app.dift.ui.navigation

// Route constants live here; screens must not hardcode route strings.
object Routes {
    const val DASHBOARD = "dashboard"
    const val APPS = "apps"
    const val RULES = "rules"
    const val SETTINGS = "settings"
    const val HISTORY = "history"

    const val RULE_EDITOR = "rule_editor"
    const val RULE_EDITOR_ARG = "ruleId"
    const val RULE_EDITOR_ROUTE = "$RULE_EDITOR?$RULE_EDITOR_ARG={$RULE_EDITOR_ARG}"

    fun ruleEditor(ruleId: Long = 0): String = "$RULE_EDITOR?$RULE_EDITOR_ARG=$ruleId"
}
