package app.dift.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.dift.data.db.dao.BlockEventDao
import app.dift.data.db.dao.GrantDao
import app.dift.data.db.dao.RuleDao
import app.dift.data.db.dao.UsageDao
import app.dift.data.db.entity.BlockEventEntity
import app.dift.data.db.entity.BlockRuleEntity
import app.dift.data.db.entity.DailyUsageEntity
import app.dift.data.db.entity.RuleAppCrossRef
import app.dift.data.db.entity.UnblockGrantEntity
import app.dift.data.db.entity.UsageSessionEntity

/**
 * Schema v1 covers the full data model (docs/DATA_MODEL.md) — defined before any release
 * shipped, so no migrations exist yet. Any schema change from here on requires a migration
 * and a committed schema JSON (CLAUDE.md invariant #7).
 */
@Database(
    entities = [
        UsageSessionEntity::class,
        DailyUsageEntity::class,
        BlockRuleEntity::class,
        RuleAppCrossRef::class,
        UnblockGrantEntity::class,
        BlockEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DiftDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun ruleDao(): RuleDao
    abstract fun grantDao(): GrantDao
    abstract fun blockEventDao(): BlockEventDao
}
