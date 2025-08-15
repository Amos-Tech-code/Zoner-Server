package com.amos_tech_code.services

// UsernameService.kt
import com.amos_tech_code.database.UsersTable
import com.amos_tech_code.utils.extractClean
import com.amos_tech_code.utils.normalizeAndValidateUsername
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.UUID

object UsernameService {
    private val random = SecureRandom()

    private const val MAX_LEN = 16 // including '@' (so 15 after '@')
    private const val SUGGESTION_LIMIT = 5
    private const val CANDIDATE_POOL = 48 // generate a pool, then batch-check

    private val namePatterns = listOf(
        "{first}", "{first}{last}", "{first}.{last}", "{first}_{last}",
        "{f}{last}", "{first}{yy}", "{first}{yyyy}", "{first}{x}",
        "{first}{xxx}", "real{first}", "the{first}", "{first}official",
        "{first}world", "{first}fan"
    )

    private val genericPatterns = listOf(
        "user{xxxx}", "people{xxxx}", "hello{xxxx}", "im{xxxx}", "its{xxxx}",
        "meet{xxxx}", "true{xxxx}", "only{xxxx}", "the{xxxx}", "just{xxxx}"
    )

    private val fillerWords = listOf("the", "real", "true", "only", "just", "thisis", "meet", "hello", "im", "its")

    /** Public: Case-insensitive availability check. Accepts with/without '@'. */
    fun isUsernameAvailable(input: String, excludeUserId: UUID? = null): Boolean {
        val normalized = normalizeAndValidateUsername(input)
        return transaction {
            val base = normalized.lowercase()
            val q = UsersTable
                .slice(UsersTable.id)
                .select { UsersTable.username eq base }
            val exists = if (excludeUserId == null) q.any() else q.andWhere { UsersTable.id neq excludeUserId }.any()
            !exists
        }
    }

    /** Public: Suggestions (batched DB lookup, minimal queries). */
    fun generateSuggestions(requestedUsername: String, userId: UUID?): List<String> {
        val base = extractClean(requestedUsername)

        return transaction {
            val (firstName, lastName) = if (userId != null) {
                UsersTable
                    .slice(UsersTable.name)
                    .select { UsersTable.id eq userId }
                    .limit(1)
                    .firstOrNull()
                    ?.get(UsersTable.name)
                    ?.let { raw ->
                        val parts = raw.split(Regex("\\s+"))
                            .mapNotNull { it.trim().lowercase().takeIf { s -> s.isNotBlank() } }
                        val first = parts.firstOrNull()
                        val last = parts.drop(1).joinToString("").ifBlank { null }
                        Pair(first, last)
                    } ?: Pair(null, null)
            } else Pair(null, null)

            // 1) Build candidate list (without '@'), respecting length BEFORE prefix
            val candidates = buildSequence(base, firstName, lastName)
                .distinct()
                .take(CANDIDATE_POOL)
                .toList()

            // 2) Normalize with '@' + enforce total length
            val normalized = candidates
                .map { "@${it.lowercase()}" }
                .map { it.take(MAX_LEN) }
                .distinct()

            if (normalized.isEmpty()) return@transaction generateFallbacks(base, firstName)

            // 3) Batch query existing usernames
            val taken: Set<String> = UsersTable
                .slice(UsersTable.username)
                .select { UsersTable.username inList normalized }
                .mapNotNull { it[UsersTable.username]?.lowercase() }
                .toSet()

            // 4) Filter out taken and return top N
            normalized
                .filterNot { it.lowercase() in taken }
                .take(SUGGESTION_LIMIT)
                .ifEmpty { generateFallbacks(base, firstName) }
        }
    }

    // ----- internals -----

    private fun buildSequence(base: String, firstName: String?, lastName: String?) = sequence {
        yieldAll(generateRequestBased(base))
        if (!firstName.isNullOrBlank()) yieldAll(generateNameBased(firstName, lastName))
        yieldAll(generateGeneric())
    }.filter { it.length in 3..15 } // 3–15 BEFORE adding '@'

    private fun generateRequestBased(base: String) = sequence {
        listOf("", "_", ".", "1", "2", "3", "123", "22", "2025").forEach { suffix ->
            if (base.length + suffix.length <= 15) yield("$base$suffix")
        }
        fillerWords.forEach { word ->
            if (base.length + word.length <= 15) {
                yield("$word$base")
                yield("$base$word")
            }
        }
    }

    private fun generateNameBased(first: String, last: String?) = sequence {
        val now = LocalDateTime.now()
        val yy = (now.year % 100).toString()
        val yyyy = now.year.toString()

        namePatterns.forEach { pattern ->
            val username = pattern
                .replace("{first}", first)
                .replace("{last}", (last ?: ""))
                .replace("{f}", first.take(1))
                .replace("{x}", random.nextInt(10).toString())
                .replace("{xx}", random.nextInt(100).toString().padStart(2, '0'))
                .replace("{xxx}", random.nextInt(1000).toString().padStart(3, '0'))
                .replace("{yy}", yy)
                .replace("{yyyy}", yyyy)

            if (username.length <= 15) yield(username)
        }
    }

    private fun generateGeneric() = sequence {
        genericPatterns.forEach { pattern ->
            val username = pattern.replace("{xxxx}", random.nextInt(10000).toString().padStart(4, '0'))
            if (username.length <= 15) yield(username)
        }
    }

    private fun generateFallbacks(base: String, firstName: String?): List<String> =
        listOfNotNull(
            "@${base.take(5)}${random.nextInt(1000)}",
            firstName?.let { "@${it.take(7)}${random.nextInt(1000)}" },
            "@user${random.nextInt(100000)}",
            "@people${random.nextInt(10000)}",
            "@${base.take(3)}${firstName?.take(3) ?: ""}${random.nextInt(100)}"
        ).take(SUGGESTION_LIMIT)
}
