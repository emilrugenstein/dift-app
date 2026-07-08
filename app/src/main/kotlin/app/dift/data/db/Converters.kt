package app.dift.data.db

import androidx.room.TypeConverter
import app.dift.domain.model.BlockOutcome
import app.dift.domain.model.BlockReason
import app.dift.domain.model.GrantMethod
import app.dift.domain.model.RuleType
import app.dift.domain.model.Strictness

/** Enums are stored by name — stable as long as enum constants are never renamed. */
class Converters {
    @TypeConverter fun ruleTypeToString(value: RuleType): String = value.name

    @TypeConverter fun stringToRuleType(value: String): RuleType = RuleType.valueOf(value)

    @TypeConverter fun strictnessToString(value: Strictness): String = value.name

    @TypeConverter fun stringToStrictness(value: String): Strictness = Strictness.valueOf(value)

    @TypeConverter fun reasonToString(value: BlockReason): String = value.name

    @TypeConverter fun stringToReason(value: String): BlockReason = BlockReason.valueOf(value)

    @TypeConverter fun outcomeToString(value: BlockOutcome): String = value.name

    @TypeConverter fun stringToOutcome(value: String): BlockOutcome = BlockOutcome.valueOf(value)

    @TypeConverter fun methodToString(value: GrantMethod): String = value.name

    @TypeConverter fun stringToMethod(value: String): GrantMethod = GrantMethod.valueOf(value)
}
