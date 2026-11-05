package net.kikin.nubecita.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import kotlin.time.Instant

/**
 * A translated post body, cached so a reader who scrolls past a post and back
 * does not pay for it twice.
 *
 * **The target language is part of the key, not a column.** A reader who
 * changes their device language must not be served the previous language's
 * translation; keyed on the post URI alone, they would be, and the staleness
 * would be invisible — the text is in *a* language and looks fine.
 *
 * Translations are disposable. Nothing here is a source of truth: losing the
 * whole table costs the reader a few hundred milliseconds of re-translation,
 * which is why the cache is bounded and evicted by age rather than preserved.
 *
 * [facetsJson] holds the facets that apply to [translatedText], serialized.
 * They are not the post's original facets — translation destroys byte offsets,
 * so these are the reconciled ones, indexing the translated bytes. Storing
 * them avoids re-running reconciliation on every scroll-back.
 */
@Entity(tableName = "post_translation", primaryKeys = ["post_uri", "target_language"])
data class TranslationEntity(
    @ColumnInfo(name = "post_uri") val postUri: String,
    @ColumnInfo(name = "target_language") val targetLanguage: String,
    @ColumnInfo(name = "source_language") val sourceLanguage: String,
    @ColumnInfo(name = "translated_text") val translatedText: String,
    @ColumnInfo(name = "facets_json") val facetsJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)
