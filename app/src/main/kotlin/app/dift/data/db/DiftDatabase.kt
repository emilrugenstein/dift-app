package app.dift.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.dift.data.db.dao.BlockDao
import app.dift.data.db.dao.BlockEventDao
import app.dift.data.db.dao.ChallengeDao
import app.dift.data.db.dao.GrantDao
import app.dift.data.db.dao.UsageDao
import app.dift.data.db.entity.BlockEventEntity
import app.dift.data.db.entity.BlockRuleEntity
import app.dift.data.db.entity.ChallengeEntity
import app.dift.data.db.entity.ChallengeEntryEntity
import app.dift.data.db.entity.DailyUsageEntity
import app.dift.data.db.entity.RuleAppCrossRef
import app.dift.data.db.entity.UnblockGrantEntity
import app.dift.data.db.entity.UsageSessionEntity

/**
 * v3 (docs/DATA_MODEL.md). v1 → v2 ([MIGRATION_1_2]) only purged rows; v2 → v3 adds the two
 * challenge tables as a Room [AutoMigration] generated from the committed schema JSONs in
 * `app/schemas/` (CLAUDE.md invariant #7 — CI fails if an export is missing or uncommitted).
 */
@Database(
    entities = [
        UsageSessionEntity::class,
        DailyUsageEntity::class,
        BlockRuleEntity::class,
        RuleAppCrossRef::class,
        UnblockGrantEntity::class,
        BlockEventEntity::class,
        ChallengeEntity::class,
        ChallengeEntryEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 2, to = 3)],
)
@TypeConverters(Converters::class)
abstract class DiftDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun blockDao(): BlockDao
    abstract fun grantDao(): GrantDao
    abstract fun blockEventDao(): BlockEventDao
    abstract fun challengeDao(): ChallengeDao

    companion object {
        const val NAME = "dift.db"
    }
}
