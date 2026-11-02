package app.dift.data.repo

import app.dift.data.db.dao.ChallengeDao
import app.dift.data.db.entity.ChallengeEntity
import app.dift.data.db.entity.ChallengeEntryEntity
import app.dift.domain.model.Challenge
import app.dift.domain.model.ChallengeEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and writes challenges + entries (docs/features/challenges.md); maps entities ↔ domain. */
@Singleton
class ChallengeRepository @Inject constructor(
    private val dao: ChallengeDao,
) {

    val challenges: Flow<List<Challenge>> =
        dao.observeChallenges().map { list -> list.map { it.toDomain() } }

    fun observeEntries(challengeId: Long): Flow<List<ChallengeEntry>> =
        dao.observeEntries(challengeId).map { list -> list.map { it.toDomain() } }

    fun observeDayTotals(challengeId: Long): Flow<Map<LocalDate, Long>> =
        dao.observeDayTotals(challengeId).map { totals ->
            totals.associate { LocalDate.parse(it.dayLocal) to it.totalMs }
        }

    suspend fun challenge(id: Long): Challenge? = dao.challengeById(id)?.toDomain()

    suspend fun upsert(challenge: Challenge, nowMs: Long): Long {
        val existing = if (challenge.id != 0L) dao.challengeById(challenge.id) else null
        val entity = challenge.toEntity(createdAt = existing?.createdAt ?: nowMs)
        return if (existing == null) {
            dao.insertChallenge(entity)
        } else {
            dao.updateChallenge(entity)
            challenge.id
        }
    }

    suspend fun delete(challengeId: Long) = dao.deleteChallenge(challengeId)

    suspend fun addEntry(challengeId: Long, startMs: Long, endMs: Long, day: LocalDate, manual: Boolean): Long =
        dao.insertEntry(
            ChallengeEntryEntity(
                challengeId = challengeId,
                startEpochMs = startMs,
                endEpochMs = endMs,
                dayLocal = day.toString(),
                manual = manual,
            ),
        )

    suspend fun deleteEntry(entryId: Long) = dao.deleteEntry(entryId)

    suspend fun timedEntriesInRange(fromMs: Long, toMs: Long): List<ChallengeEntry> =
        dao.timedEntriesInRange(fromMs, toMs).map { it.toDomain() }

    suspend fun dayTotalsInRange(from: LocalDate, to: LocalDate): Map<LocalDate, Long> =
        dao.dayTotalsInRange(from.toString(), to.toString())
            .associate { LocalDate.parse(it.dayLocal) to it.totalMs }

    private fun ChallengeEntity.toDomain() = Challenge(
        id = id,
        name = name,
        startDay = LocalDate.parse(startDay),
        endDay = LocalDate.parse(endDay),
        goalMinutesPerDay = goalMinutesPerDay,
    )

    private fun Challenge.toEntity(createdAt: Long) = ChallengeEntity(
        id = id,
        name = name,
        startDay = startDay.toString(),
        endDay = endDay.toString(),
        goalMinutesPerDay = goalMinutesPerDay,
        createdAt = createdAt,
    )

    private fun ChallengeEntryEntity.toDomain() = ChallengeEntry(
        id = id,
        challengeId = challengeId,
        startMs = startEpochMs,
        endMs = endEpochMs,
        day = LocalDate.parse(dayLocal),
        manual = manual,
    )
}
