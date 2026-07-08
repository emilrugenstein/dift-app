package app.dift.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.dift.data.db.dao.BlockDao
import app.dift.data.db.dao.BlockEventDao
import app.dift.data.db.dao.GrantDao
import app.dift.data.db.dao.UsageDao
import app.dift.data.db.entity.BlockEventEntity
import app.dift.data.db.entity.BlockRuleEntity
import app.dift.data.db.entity.DailyUsageEntity
import app.dift.data.db.entity.RuleAppCrossRef
import app.dift.data.db.entity.UnblockGrantEntity
import app.dift.data.db.entity.UsageSessionEntity

/**
 * v2 (docs/DATA_MODEL.md). The table structure is unchanged from v1 — [MIGRATION_1_2] only
 * purges rows retired by the switch to usage-debt-only blocking. Any *structural* change from here
 * requires a real migration and a committed schema JSON (CLAUDE.md invariant #7).
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
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DiftDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun blockDao(): BlockDao
    abstract fun grantDao(): GrantDao
    abstract fun blockEventDao(): BlockEventDao
}
