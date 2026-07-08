package app.dift.ui.navigation

// Route constants live here; screens must not hardcode route strings.
object Routes {
    const val OVERVIEW = "overview"
    const val BLOCKS = "blocks"
    const val SETTINGS = "settings"

    const val BLOCK_EDITOR = "block_editor"
    const val BLOCK_EDITOR_ARG = "blockId"
    const val BLOCK_EDITOR_ROUTE = "$BLOCK_EDITOR?$BLOCK_EDITOR_ARG={$BLOCK_EDITOR_ARG}"

    fun blockEditor(blockId: Long = 0): String = "$BLOCK_EDITOR?$BLOCK_EDITOR_ARG=$blockId"
}
