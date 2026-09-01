package com.plutani.locust

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/* ---------------- model ----------------
   Deliberately mirrors the JSON the web version of Locust exports, so a
   vault file from the old app loads here without conversion. */

enum class Kind(val id: String, val label: String, val counts: Boolean) {
    CHAPTER("chapter", "Chapter", true),
    PROLOGUE("prologue", "Prologue", true),
    EPILOGUE("epilogue", "Epilogue", true),
    BIO("bio", "Bio", true),
    NOTE("note", "Author's note", false);

    companion object {
        fun from(id: String?): Kind = entries.firstOrNull { it.id == id } ?: CHAPTER
    }
}

class Chapter(
    var id: String = newId(),
    var kind: Kind = Kind.CHAPTER,
    var title: String = "",
    var body: String = "",
    var updatedAt: Long = System.currentTimeMillis()
)

class Story(
    var id: String = newId(),
    var title: String = "",
    var description: String = "",
    var category: String = "Fanfiction",
    var tags: MutableList<String> = mutableListOf(),
    var goal: Int = 0,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var deletedAt: Long = 0L,
    val chapters: MutableList<Chapter> = mutableListOf()
)

class Profile(
    var name: String = "",
    var handle: String = "",
    var about: String = "",
    var author: String = "",
    var accent: String = "iris"
)

fun newId(): String =
    java.lang.Long.toString(System.nanoTime(), 36) + (100..999).random()

/* Counts words, treating CJK as one word per character. */
fun countWords(text: String): Int {
    if (text.isEmpty()) return 0
    var cjk = 0
    val sb = StringBuilder(text.length)
    for (ch in text) {
        val block = Character.UnicodeBlock.of(ch)
        val isCjk = block === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block === Character.UnicodeBlock.HIRAGANA ||
                block === Character.UnicodeBlock.KATAKANA ||
                block === Character.UnicodeBlock.HANGUL_SYLLABLES
        if (isCjk) { cjk++; sb.append(' ') } else sb.append(ch)
    }
    var latin = 0
    var inWord = false
    for (ch in sb) {
        val part = Character.isLetterOrDigit(ch) || ch == '\'' || ch == '\u2019' || ch == '-'
        if (part && Character.isLetterOrDigit(ch)) {
            if (!inWord) { latin++; inWord = true }
        } else if (!part) inWord = false
    }
    return cjk + latin
}

/* The web editor stored HTML. Flatten it so imported chapters read as text. */
fun htmlToText(raw: String): String {
    if (raw.isEmpty()) return ""
    if (!raw.contains('<')) return raw
    var s = raw
    s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
    s = s.replace(Regex("(?i)</(div|p)>"), "\n")
    s = s.replace(Regex("(?i)<img[^>]*>"), "")
    s = s.replace(Regex("<[^>]+>"), "")
    s = s.replace("&nbsp;", " ").replace("&amp;", "&")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'")
        .replace("&quot;", "\"")
    s = s.replace(Regex("\n{3,}"), "\n\n")
    return s.trim()
}

/* ---------------- store ----------------
   One JSON document on disk. No database, no code generation, no migrations —
   and the file is byte-identical in shape to a vault export. */

object Store {

    val stories = mutableStateListOf<Story>()
    val trash = mutableStateListOf<Story>()
    val profile = mutableStateOf(Profile())
    val stats = HashMap<String, Int>()
    val lastError = mutableStateOf<String?>(null)

    private lateinit var file: File
    private lateinit var backupFile: File
    private val io = Executors.newSingleThreadExecutor()
    private var loaded = false

    fun init(ctx: Context) {
        if (loaded) return
        file = File(ctx.filesDir, "locust.json")
        backupFile = File(ctx.filesDir, "locust.prev.json")
        load()
        loaded = true
    }

    private fun load() {
        val text = readSafely(file) ?: readSafely(backupFile) ?: return
        try {
            applyJson(JSONObject(text), replace = true)
        } catch (e: Exception) {
            lastError.value = "Could not read saved data: ${e.message}"
        }
    }

    private fun readSafely(f: File): String? = try {
        if (f.exists() && f.length() > 0) f.readText() else null
    } catch (e: Exception) { null }

    /** Writes atomically via a temp file, keeping the previous version as a fallback. */
    fun save() {
        val snapshot = toJson().toString()
        io.execute {
            try {
                val tmp = File(file.parentFile, "locust.tmp.json")
                tmp.writeText(snapshot)
                if (file.exists()) file.copyTo(backupFile, overwrite = true)
                if (!tmp.renameTo(file)) {
                    file.writeText(snapshot)
                    tmp.delete()
                }
                lastError.value = null
            } catch (e: Exception) {
                lastError.value = "Save failed: ${e.message}"
            }
        }
    }

    /* ---------------- json ---------------- */

    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("app", "locust")
        root.put("kind", "vault")
        root.put("version", 4)
        root.put("savedAt", System.currentTimeMillis())

        val arr = JSONArray()
        (stories + trash).forEach { arr.put(storyJson(it)) }
        root.put("stories", arr)

        val p = JSONObject()
        val pr = profile.value
        p.put("name", pr.name); p.put("handle", pr.handle)
        p.put("line", pr.about); p.put("author", pr.author); p.put("accent", pr.accent)
        root.put("profile", p)

        val st = JSONObject()
        stats.forEach { (k, v) -> st.put(k, v) }
        root.put("stats", st)
        return root
    }

    private fun storyJson(s: Story): JSONObject {
        val o = JSONObject()
        o.put("id", s.id); o.put("title", s.title); o.put("description", s.description)
        o.put("category", s.category); o.put("goal", s.goal)
        o.put("createdAt", s.createdAt); o.put("updatedAt", s.updatedAt)
        if (s.deletedAt > 0) o.put("deletedAt", s.deletedAt)
        val tags = JSONArray(); s.tags.forEach { tags.put(it) }
        o.put("tags", tags)
        val chs = JSONArray()
        s.chapters.forEach { c ->
            val co = JSONObject()
            co.put("id", c.id); co.put("kind", c.kind.id); co.put("title", c.title)
            co.put("body", c.body); co.put("updatedAt", c.updatedAt)
            chs.put(co)
        }
        o.put("chapters", chs)
        return o
    }

    /** Reads a vault from either the web app or this one. */
    fun applyJson(root: JSONObject, replace: Boolean) {
        val incoming = root.optJSONArray("stories") ?: JSONArray()
        if (replace) { stories.clear(); trash.clear() }

        for (i in 0 until incoming.length()) {
            val o = incoming.optJSONObject(i) ?: continue
            val s = Story(
                id = o.optString("id", newId()),
                title = o.optString("title", ""),
                description = o.optString("description", ""),
                category = o.optString("category", "Fanfiction"),
                goal = o.optInt("goal", 0),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                deletedAt = o.optLong("deletedAt", 0L)
            )
            o.optJSONArray("tags")?.let { t ->
                for (j in 0 until t.length()) s.tags.add(t.optString(j))
            }
            o.optJSONArray("chapters")?.let { cs ->
                for (j in 0 until cs.length()) {
                    val c = cs.optJSONObject(j) ?: continue
                    s.chapters.add(
                        Chapter(
                            id = c.optString("id", newId()),
                            kind = Kind.from(c.optString("kind", "chapter")),
                            title = c.optString("title", ""),
                            body = htmlToText(c.optString("body", "")),
                            updatedAt = c.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
            if (s.chapters.isEmpty()) s.chapters.add(Chapter(title = "Chapter 1"))

            val existing = (stories + trash).firstOrNull { it.id == s.id }
            if (existing != null) { stories.remove(existing); trash.remove(existing) }
            if (s.deletedAt > 0) trash.add(s) else stories.add(s)
        }

        root.optJSONObject("profile")?.let { p ->
            val pr = Profile(
                name = p.optString("name", ""),
                handle = p.optString("handle", ""),
                about = p.optString("line", p.optString("about", "")),
                author = p.optString("author", ""),
                accent = p.optString("accent", "iris")
            )
            profile.value = pr
        }
        root.optJSONObject("stats")?.let { st ->
            val keys = st.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                stats[k] = maxOf(stats[k] ?: 0, st.optInt(k))
            }
        }
    }

    /* ---------------- helpers ---------------- */

    fun chapterNumbers(s: Story): Map<String, Int> {
        val map = HashMap<String, Int>()
        var n = 0
        s.chapters.forEach { if (it.kind == Kind.CHAPTER) map[it.id] = ++n }
        return map
    }

    fun label(s: Story, c: Chapter): String =
        if (c.kind != Kind.CHAPTER) c.kind.label
        else "Chapter " + (chapterNumbers(s)[c.id] ?: 1)

    fun storyWords(s: Story): Int =
        s.chapters.sumOf { if (it.kind.counts) countWords(it.body) else 0 }

    fun chapterCount(s: Story): Int = s.chapters.count { it.kind == Kind.CHAPTER }

    fun todayKey(): String {
        val c = java.util.Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            c.get(java.util.Calendar.YEAR),
            c.get(java.util.Calendar.MONTH) + 1,
            c.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    fun addWords(n: Int) {
        if (n <= 0) return
        val k = todayKey()
        stats[k] = (stats[k] ?: 0) + n
    }

    fun newStory(): Story {
        val s = Story(title = "")
        s.chapters.add(Chapter(title = "Chapter 1"))
        stories.add(s)
        save()
        return s
    }

    fun trashStory(s: Story) {
        s.deletedAt = System.currentTimeMillis()
        stories.remove(s); trash.add(s)
        save()
    }

    fun restoreStory(s: Story) {
        s.deletedAt = 0
        trash.remove(s); stories.add(s)
        save()
    }

    fun deleteForever(s: Story) {
        trash.remove(s)
        save()
    }
}
