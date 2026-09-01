package com.plutani.locust

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/* ---------------- navigation ---------------- */

sealed class Screen {
    data object Library : Screen()
    data object Desk : Screen()
    data object Profile : Screen()
    data class StoryView(val storyId: String) : Screen()
    data class Editor(val storyId: String, val chapterId: String) : Screen()
}

class MainActivity : ComponentActivity() {

    private var pendingExport: String? = null

    private val createDoc = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val data = pendingExport ?: return@registerForActivityResult
        pendingExport = null
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) }
            toast("Backup written")
        } catch (e: Exception) {
            toast("Could not write: ${e.message}")
        }
    }

    private val openDoc = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return@registerForActivityResult
            Store.applyJson(JSONObject(text), replace = false)
            Store.save()
            toast("Backup loaded")
        } catch (e: Exception) {
            toast("Could not read that file: ${e.message}")
        }
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()

    fun exportBackup() {
        pendingExport = Store.toJson().toString()
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US)
            .format(java.util.Date())
        createDoc.launch("locust-$stamp.json")
    }

    fun importBackup() = openDoc.launch(arrayOf("application/json", "text/plain", "*/*"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        setContent {
            val profile by Store.profile
            LocustTheme(accent = profile.accent) { App(this) }
        }
    }

    override fun onPause() {
        super.onPause()
        Store.save()
    }
}

/* ---------------- shell ---------------- */

@Composable
fun App(activity: MainActivity) {
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }

    BackHandler(enabled = screen !is Screen.Library) {
        screen = when (val s = screen) {
            is Screen.Editor -> Screen.StoryView(s.storyId)
            is Screen.StoryView -> Screen.Library
            else -> Screen.Library
        }
    }

    Surface(color = Void, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (val s = screen) {
                    is Screen.Library -> LibraryScreen(
                        onOpen = { screen = Screen.StoryView(it.id) },
                        onResume = { st, ch -> screen = Screen.Editor(st.id, ch.id) }
                    )
                    is Screen.Desk -> DeskScreen(activity)
                    is Screen.Profile -> ProfileScreen(activity)
                    is Screen.StoryView -> {
                        val story = Store.stories.firstOrNull { it.id == s.storyId }
                        if (story == null) screen = Screen.Library
                        else StoryScreen(
                            story = story,
                            onBack = { screen = Screen.Library },
                            onEdit = { screen = Screen.Editor(story.id, it.id) }
                        )
                    }
                    is Screen.Editor -> {
                        val story = Store.stories.firstOrNull { it.id == s.storyId }
                        val chapter = story?.chapters?.firstOrNull { it.id == s.chapterId }
                        if (story == null || chapter == null) screen = Screen.Library
                        else EditorScreen(
                            story = story,
                            chapter = chapter,
                            onBack = { screen = Screen.StoryView(story.id) }
                        )
                    }
                }
            }

            if (screen is Screen.Library || screen is Screen.Desk || screen is Screen.Profile) {
                BottomBar(screen) { screen = it }
            }
        }
    }
}

@Composable
fun BottomBar(current: Screen, go: (Screen) -> Unit) {
    val accent = accentOf(Store.profile.value.accent)
    Row(
        Modifier.fillMaxWidth().background(Surface1).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf(
            "Library" to Screen.Library,
            "Desk" to Screen.Desk,
            "Profile" to Screen.Profile
        ).forEach { (label, target) ->
            val on = current::class == target::class
            Text(
                text = label,
                color = if (on) accent else TextFaint,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { go(target) }
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            )
        }
    }
}

/* ---------------- shared bits ---------------- */

@Composable
fun Eyebrow(text: String) = Text(
    text.uppercase(),
    color = TextFaint,
    fontSize = 10.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.6.sp,
    modifier = Modifier.padding(bottom = 8.dp)
)

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Surface1)
            .padding(18.dp),
        content = content
    )
}

@Composable
fun Field(label: String, value: String, minLines: Int = 1, onChange: (String) -> Unit) {
    Column(Modifier.padding(bottom = 14.dp)) {
        Text(label, color = TextMuted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(color = TextMain, fontSize = 16.sp),
            cursorBrush = SolidColor(accentOf(Store.profile.value.accent)),
            minLines = minLines,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Surface2)
                .padding(13.dp)
        )
    }
}

/* ---------------- library ---------------- */

@Composable
fun LibraryScreen(onOpen: (Story) -> Unit, onResume: (Story, Chapter) -> Unit) {
    val accent = accentOf(Store.profile.value.accent)
    val sorted = Store.stories.sortedByDescending { it.updatedAt }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Locust", style = MaterialTheme.typography.displaySmall, color = TextMain)
                Text("Your desk. This device only.", color = TextFaint, fontSize = 11.5.sp)
            }
            Button(
                onClick = { onOpen(Store.newStory()) },
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) { Text("New") }
        }
        Spacer(Modifier.height(16.dp))

        Store.lastError.value?.let {
            Text(
                it, color = Color(0xFFFF8177), fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x333A1116)).padding(12.dp)
            )
            Spacer(Modifier.height(12.dp))
        }

        // continue writing
        val last = Store.stories
            .flatMap { s -> s.chapters.map { s to it } }
            .filter { it.second.body.isNotBlank() }
            .maxByOrNull { it.second.updatedAt }

        if (last != null) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Surface1)
                    .clickable { onResume(last.first, last.second) }
                    .padding(15.dp)
            ) {
                Eyebrow("Continue writing")
                Text(
                    last.second.title.ifBlank { Store.label(last.first, last.second) },
                    style = MaterialTheme.typography.titleLarge, color = TextMain,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    last.first.title.ifBlank { "Untitled story" },
                    color = TextMuted, fontSize = 12.5.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (sorted.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Nothing on the shelf", style = MaterialTheme.typography.headlineMedium, color = TextMain)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Every story starts as a title and a bad first line.",
                    color = TextMuted, fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(sorted, key = { it.id }) { s ->
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(16.dp)).background(Surface1)
                            .clickable { onOpen(s) }.padding(15.dp)
                    ) {
                        Text(
                            s.title.ifBlank { "Untitled story" },
                            style = MaterialTheme.typography.titleLarge, color = TextMain,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${Store.chapterCount(s)} ch · ${Store.storyWords(s)} words",
                            color = TextFaint, fontSize = 12.sp
                        )
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

/* ---------------- story ---------------- */

@Composable
fun StoryScreen(story: Story, onBack: () -> Unit, onEdit: (Chapter) -> Unit) {
    val accent = accentOf(Store.profile.value.accent)
    var tick by remember { mutableIntStateOf(0) }
    val nums = remember(tick) { Store.chapterNumbers(story) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(18.dp))
        Text("‹ Library", color = TextMuted, fontSize = 14.sp,
            modifier = Modifier.clickable { onBack() })
        Spacer(Modifier.height(14.dp))

        Panel {
            Eyebrow("Story identity")
            Field("Title", story.title) { story.title = it; story.updatedAt = System.currentTimeMillis(); tick++; Store.save() }
            Field("Description", story.description, minLines = 3) { story.description = it; tick++; Store.save() }
        }

        Panel {
            Eyebrow("Contents")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Chapters", style = MaterialTheme.typography.headlineMedium, color = TextMain)
                    Text(
                        "${Store.chapterCount(story)} chapters · ${Store.storyWords(story)} words",
                        color = TextMuted, fontSize = 12.5.sp
                    )
                }
                Button(
                    onClick = {
                        val c = Chapter(title = "Chapter ${Store.chapterCount(story) + 1}")
                        story.chapters.add(c); Store.save(); tick++
                        onEdit(c)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) { Text("Add") }
            }
            Spacer(Modifier.height(14.dp))

            story.chapters.forEachIndexed { i, c ->
                key(c.id) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 9.dp)
                            .clip(RoundedCornerShape(14.dp)).background(Surface2)
                            .clickable { onEdit(c) }.padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (c.kind == Kind.CHAPTER) "${nums[c.id] ?: ""}" else c.kind.label.take(3).uppercase(),
                            color = if (c.kind == Kind.CHAPTER) TextFaint else accent,
                            fontSize = if (c.kind == Kind.CHAPTER) 13.sp else 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(30.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                c.title.ifBlank { Store.label(story, c) },
                                color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (c.kind.counts) "${countWords(c.body)} words" else "not counted",
                                color = TextFaint, fontSize = 11.5.sp
                            )
                        }
                        Text(
                            "✕", color = TextFaint, fontSize = 16.sp,
                            modifier = Modifier
                                .clickable {
                                    story.chapters.remove(c)
                                    if (story.chapters.isEmpty())
                                        story.chapters.add(Chapter(title = "Chapter 1"))
                                    Store.save(); tick++
                                }
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }

        Text(
            "Move to trash",
            color = Color(0xFFFF8177), fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 30.dp).clickable {
                Store.trashStory(story); onBack()
            }
        )
    }
}

/* ---------------- editor ---------------- */

@Composable
fun EditorScreen(story: Story, chapter: Chapter, onBack: () -> Unit) {
    val accent = accentOf(Store.profile.value.accent)
    var title by remember(chapter.id) { mutableStateOf(chapter.title) }
    var body by remember(chapter.id) { mutableStateOf(chapter.body) }
    var kindMenu by remember { mutableStateOf(false) }
    val startWords = remember(chapter.id) { countWords(chapter.body) }

    // Word count is derived, not recomputed per keystroke by the caller.
    val wordCount by remember { derivedStateOf { countWords(body) } }

    DisposableEffect(chapter.id) {
        onDispose {
            chapter.title = title
            chapter.body = body
            chapter.updatedAt = System.currentTimeMillis()
            story.updatedAt = System.currentTimeMillis()
            if (chapter.kind.counts) Store.addWords(countWords(body) - startWords)
            Store.save()
        }
    }

    // Autosave a couple of seconds after typing stops.
    LaunchedEffect(body, title) {
        kotlinx.coroutines.delay(1500)
        chapter.title = title
        chapter.body = body
        chapter.updatedAt = System.currentTimeMillis()
        story.updatedAt = System.currentTimeMillis()
        Store.save()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", color = TextMuted, fontSize = 26.sp,
                modifier = Modifier.clickable { onBack() }.padding(horizontal = 10.dp))
            Spacer(Modifier.weight(1f))
            Box {
                Text("⋮", color = TextMuted, fontSize = 22.sp,
                    modifier = Modifier.clickable { kindMenu = true }.padding(horizontal = 12.dp))
                DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                    Kind.entries.forEach { k ->
                        DropdownMenuItem(
                            text = { Text(k.label + if (chapter.kind == k) "  ✓" else "") },
                            onClick = { chapter.kind = k; Store.save(); kindMenu = false }
                        )
                    }
                }
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = TextStyle(
                    color = TextMain, fontSize = 22.sp,
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold
                ),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            )
            HorizontalDivider(color = LineColor)
            Spacer(Modifier.height(14.dp))
            BasicTextField(
                value = body,
                onValueChange = { body = it },
                textStyle = TextStyle(
                    color = Color(0xFFDDDFE9), fontSize = 16.5.sp,
                    fontFamily = FontFamily.Serif, lineHeight = 28.sp
                ),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 320.dp)
            )
            Spacer(Modifier.height(180.dp))
        }

        Row(
            Modifier.fillMaxWidth().background(Surface1).padding(14.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text("$wordCount words", color = TextFaint, fontSize = 11.5.sp)
        }
    }
}

/* ---------------- desk ---------------- */

@Composable
fun DeskScreen(activity: MainActivity) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(20.dp))
        Text("Desk", style = MaterialTheme.typography.displaySmall, color = TextMain)
        Spacer(Modifier.height(16.dp))

        val chapters = Store.stories.sumOf { Store.chapterCount(it) }
        val words = Store.stories.sumOf { Store.storyWords(it) }

        Panel {
            Eyebrow("Totals")
            Text("${Store.stories.size} stories", color = TextMain, fontSize = 15.sp)
            Text("$chapters chapters", color = TextMain, fontSize = 15.sp)
            Text("$words words", color = TextMain, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "${Store.stats[Store.todayKey()] ?: 0} words today",
                color = accentOf(Store.profile.value.accent), fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Panel {
            Eyebrow("Backup")
            Text(
                "Your writing lives in this app's storage. Export a copy to a folder you choose — that file survives uninstalling.",
                color = TextMuted, fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))
            Row {
                Button(
                    onClick = { activity.exportBackup() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentOf(Store.profile.value.accent)
                    )
                ) { Text("Export") }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { activity.importBackup() }) { Text("Import") }
            }
        }

        if (Store.trash.isNotEmpty()) {
            Panel {
                Eyebrow("Trash")
                Store.trash.toList().forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            s.title.ifBlank { "Untitled story" },
                            color = TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f)
                        )
                        Text("Restore", color = accentOf(Store.profile.value.accent),
                            fontSize = 12.5.sp,
                            modifier = Modifier.clickable { Store.restoreStory(s) })
                        Spacer(Modifier.width(14.dp))
                        Text("Delete", color = Color(0xFFFF8177), fontSize = 12.5.sp,
                            modifier = Modifier.clickable { Store.deleteForever(s) })
                    }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

/* ---------------- profile ---------------- */

@Composable
fun ProfileScreen(activity: MainActivity) {
    var p by Store.profile
    var tick by remember { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(20.dp))
        Text("Profile", style = MaterialTheme.typography.displaySmall, color = TextMain)
        Spacer(Modifier.height(16.dp))

        Panel {
            Eyebrow("Identity")
            Field("Pen name", p.name) {
                p = Profile(it, p.handle, p.about, p.author, p.accent); Store.save(); tick++
            }
            Field("Handle", p.handle) {
                p = Profile(p.name, it, p.about, p.author, p.accent); Store.save(); tick++
            }
            Field("About", p.about, minLines = 3) {
                p = Profile(p.name, p.handle, it, p.author, p.accent); Store.save(); tick++
            }
            Field("Author name on exports", p.author) {
                p = Profile(p.name, p.handle, p.about, it, p.accent); Store.save(); tick++
            }
        }

        Panel {
            Eyebrow("Accent")
            Row {
                ACCENTS.forEach { (key, pair) ->
                    Box(
                        Modifier.padding(end = 12.dp).size(40.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(pair.first)
                            .clickable {
                                p = Profile(p.name, p.handle, p.about, p.author, key)
                                Store.save(); tick++
                            }
                    )
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}
