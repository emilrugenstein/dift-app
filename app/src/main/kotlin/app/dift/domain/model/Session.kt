package app.dift.domain.model

/**
 * A closed per-app foreground session. Sessions never span local midnight — the deriver
 * pre-splits them so [dayLocal] ("2026-07-07") is exact for daily aggregation.
 */
data class Session(
    val packageName: String,
    val startMs: Long,
    val endMs: Long,
    val dayLocal: String,
) {
    val durationMs: Long get() = endMs - startMs
}

/** A session that has started (RESUMED) but not yet ended; carried across ingest batches. */
data class OpenSession(
    val packageName: String,
    val startMs: Long,
)
