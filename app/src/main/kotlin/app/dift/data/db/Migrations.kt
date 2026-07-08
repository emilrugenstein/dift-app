package app.dift.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: usage-debt is now Dift's only blocking mechanism (docs/features/usage-debt.md). This
 * migration purges the retired rule kinds and grants **without touching the table structure** —
 * `block_rules`, `rule_apps`, `unblock_grants` keep their v1 columns, so the schema Room expects
 * for v2 is byte-identical to v1 and the post-migration validation passes with no risk (important:
 * there is no on-device schema-verification step in CI). The now-unused columns become inert; new
 * code writes them with fixed constants (see BlockRepository). `rule_apps` is repurposed from
 * "apps this rule blocks" to "apps exempt from this block" — the debt rules that survive had no
 * package rows, so the surviving `rule_apps` set is empty and starts clean.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM block_rules WHERE type != 'USAGE_DEBT'")
        db.execSQL("DELETE FROM rule_apps WHERE ruleId NOT IN (SELECT id FROM block_rules)")
        db.execSQL("DELETE FROM unblock_grants")
    }
}
