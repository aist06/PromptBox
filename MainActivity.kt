package com.example.promptbox

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Prompt(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val text: String,
    val category: String = "Другое",
    val tags: String = "",
    val favorite: Boolean = false
)

class PromptStore(context: Context) {
    private val prefs = context.getSharedPreferences("promptbox", Context.MODE_PRIVATE)
    private val gson = Gson()
    fun load(): MutableList<Prompt> {
        val raw = prefs.getString("prompts", null) ?: return demoPrompts().toMutableList()
        return try {
            val type = object : TypeToken<MutableList<Prompt>>() {}.type
            gson.fromJson<MutableList<Prompt>>(raw, type) ?: mutableListOf()
        } catch (_: Exception) { mutableListOf() }
    }
    fun save(items: List<Prompt>) {
        prefs.edit().putString("prompts", gson.toJson(items)).apply()
    }
    fun exportJson(items: List<Prompt>) = gson.toJson(items)
    fun importJson(json: String): MutableList<Prompt> {
        val type = object : TypeToken<MutableList<Prompt>>() {}.type
        return gson.fromJson<MutableList<Prompt>>(json, type) ?: mutableListOf()
    }
}

private fun demoPrompts() = listOf(
    Prompt(title = "Кинематографический портрет",
        text = "Transform this photo into a cinematic portrait. Preserve the person's identity and facial features. Add soft directional lighting, subtle film grain, rich natural colors and a beautifully blurred background.",
        category = "Портреты", tags = "cinematic, portrait"),
    Prompt(title = "Студийная фотосессия",
        text = "Create a professional studio photoshoot look. Keep the subject's face and identity unchanged. Use softbox lighting, realistic skin texture, a clean elegant background and high-end editorial photography.",
        category = "Фотосессии", tags = "studio, editorial"),
    Prompt(title = "Замена фона",
        text = "Replace the background with a realistic atmospheric location. Keep the person completely unchanged, including face, pose, body proportions and clothing. Match lighting, perspective and shadows naturally.",
        category = "Фон", tags = "background")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = PromptStore(this)
        setContent { PromptBoxApp(store) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptBoxApp(store: PromptStore) {
    val prompts = remember { mutableStateListOf<Prompt>().apply { addAll(store.load()) } }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Все") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<Prompt?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val categories = listOf("Все", "Портреты", "Одежда", "Фон", "Ретушь", "Фотосессии", "Кинематограф", "Улучшение качества", "Другое")
    val filtered = prompts.filter {
        (category == "Все" || it.category == category) &&
        (!favoritesOnly || it.favorite) &&
        (query.isBlank() || it.title.contains(query, true) || it.text.contains(query, true) || it.tags.contains(query, true))
    }.sortedByDescending { it.favorite }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let {
            try {
                val json = store.exportJson(prompts)
                // Content is written through the app content resolver.
                context.contentResolver.openOutputStream(it)?.use { stream -> stream.write(json.toByteArray()) }
                toast = "Экспорт готов"
            } catch (_: Exception) { toast = "Не удалось экспортировать" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                val json = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } ?: ""
                val imported = store.importJson(json)
                prompts.clear(); prompts.addAll(imported); store.save(prompts)
                toast = "Импортировано: ${prompts.size}"
            } catch (_: Exception) { toast = "Ошибка импорта JSON" }
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF6750A4),
        secondary = androidx.compose.ui.graphics.Color(0xFF625B71),
        background = androidx.compose.ui.graphics.Color(0xFFF7F5FA)
    )) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("PromptBox", fontWeight = FontWeight.Bold)
                            Text("Твоя библиотека промптов", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    actions = {
                        IconButton(onClick = { exportLauncher.launch("promptbox-backup.json") }) {
                            Icon(Icons.Default.FileUpload, "Экспорт")
                        }
                        IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }) {
                            Icon(Icons.Default.FileDownload, "Импорт")
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { editor = null; showEditor = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Новый промпт") }
                )
            },
            snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск по названию, тексту, тегам") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null) } },
                    singleLine = true, shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = !favoritesOnly, onClick = { favoritesOnly = false }, label = { Text("Все") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = true }, label = { Text("★ Избранное") })
                    Spacer(Modifier.weight(1f))
                    Text("${filtered.size} шт.", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Категория", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    Box {
                        OutlinedButton(onClick = { showCategories = true }) {
                            Text(category); Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = showCategories, onDismissRequest = { showCategories = false }) {
                            categories.forEach { item ->
                                DropdownMenuItem(text = { Text(item) }, onClick = { category = item; showCategories = false })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Промптов пока нет.\nНажми «Новый промпт», чтобы добавить первый.", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filtered, key = { it.id }) { item ->
                            PromptCard(
                                prompt = item,
                                onCopy = { clipboard.setText(AnnotatedString(item.text)); toast = "Промпт скопирован" },
                                onFavorite = {
                                    val i = prompts.indexOfFirst { it.id == item.id }
                                    if (i >= 0) { prompts[i] = item.copy(favorite = !item.favorite); store.save(prompts) }
                                },
                                onEdit = { editor = item; showEditor = true },
                                onDelete = { prompts.removeAll { it.id == item.id }; store.save(prompts) }
                            )
                        }
                    }
                }
            }
        }
        if (showEditor) {
            PromptEditor(
                initial = editor,
                onDismiss = { showEditor = false },
                onSave = { saved ->
                    val index = prompts.indexOfFirst { it.id == saved.id }
                    if (index >= 0) prompts[index] = saved else prompts.add(0, saved)
                    store.save(prompts)
                    showEditor = false
                }
            )
        }
    }
}

@Composable
fun PromptCard(prompt: Prompt, onCopy: () -> Unit, onFavorite: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(prompt.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(prompt.category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onFavorite) {
                    Icon(if (prompt.favorite) Icons.Default.Star else Icons.Default.StarBorder, "Избранное",
                        tint = if (prompt.favorite) androidx.compose.ui.graphics.Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Меню") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Редактировать") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Удалить") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Text(prompt.text, maxLines = 4, style = MaterialTheme.typography.bodyMedium)
            if (prompt.tags.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text("Теги: ${prompt.tags}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onCopy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Копировать промпт")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptEditor(initial: Prompt?, onDismiss: () -> Unit, onSave: (Prompt) -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "Другое") }
    var tags by remember { mutableStateOf(initial?.tags ?: "") }
    var expanded by remember { mutableStateOf(false) }
    val categories = listOf("Портреты", "Одежда", "Фон", "Ретушь", "Фотосессии", "Кинематограф", "Улучшение качества", "Другое")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый промпт" else "Редактировать") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(text, { text = it }, label = { Text("Текст промпта") }, minLines = 5)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(category, {}, readOnly = true, label = { Text("Категория") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor())
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { category = c; expanded = false }) }
                    }
                }
                OutlinedTextField(tags, { tags = it }, label = { Text("Теги через запятую") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && text.isNotBlank(),
                onClick = { onSave(Prompt(initial?.id ?: System.currentTimeMillis(), title.trim(), text.trim(), category, tags.trim(), initial?.favorite ?: false)) }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
