package com.brayanemu

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.InputStream
import java.security.MessageDigest

data class Console(
    val name: String,
    val shortName: String,
    val biosRequired: Boolean,
    val notes: String
)

data class RomFile(
    val name: String,
    val uri: Uri,
    val size: Long,
    val system: String
)

data class BiosRequirement(
    val system: String,
    val filename: String,
    val md5: String,
    val description: String
)

data class BiosResult(
    val filename: String,
    val size: Long,
    val md5: String,
    val status: String,
    val description: String
)

private val consoles = listOf(
    Console("Nintendo Entertainment System", "NES", false, "Core pendiente de integrar"),
    Console("Super Nintendo", "SNES", false, "Core pendiente de integrar"),
    Console("Game Boy / Color", "GB/GBC", false, "Core pendiente de integrar"),
    Console("Game Boy Advance", "GBA", false, "Core pendiente de integrar"),
    Console("Nintendo 64", "N64", false, "Core pendiente de integrar"),
    Console("Nintendo DS", "NDS", true, "Firmware depende del core"),
    Console("Nintendo 3DS", "3DS", true, "Firmware depende del core"),
    Console("GameCube / Wii", "GC/Wii", true, "Firmware/software del sistema depende del core"),
    Console("PlayStation", "PS1", false, "Algunos cores pueden usar HLE/OpenBIOS"),
    Console("PlayStation 2", "PS2", true, "Requiere BIOS del sistema para LRPS2"),
    Console("PSP", "PSP", false, "El core puede funcionar sin BIOS propietaria"),
    Console("Saturn", "SATURN", true, "Firmware depende del core"),
    Console("Dreamcast", "DREAMCAST", true, "Firmware depende del core"),
    Console("Arcade", "ARCADE", false, "BIOS depende del juego/driver")
)

private val ps1Bios = listOf(
    BiosRequirement("PS1", "scph5500.bin", "8dd7d5296a650fac7319bce665a6a53c", "PS1 Japón"),
    BiosRequirement("PS1", "scph5501.bin", "490f666e1afb15b7362b406ed1cea246", "PS1 EE. UU."),
    BiosRequirement("PS1", "scph5502.bin", "32736f17079d0b2b7024407c39bd3050", "PS1 Europa"),
    BiosRequirement("PS1", "PSXONPSP660.bin", "c53ca5908936d412331790f4426c6c33", "BIOS alternativa región libre"),
    BiosRequirement("PS1", "ps1_rom.bin", "81bbe60ba7a3d1cea1d48c14cbcc647b", "BIOS alternativa región libre")
)

class BrayanEmuViewModel : ViewModel() {
    var roms by mutableStateOf<List<RomFile>>(emptyList())
    var bios by mutableStateOf<List<BiosResult>>(emptyList())
    var romFolderName by mutableStateOf("No seleccionada")
    var biosFolderName by mutableStateOf("No seleccionada")
    var busy by mutableStateOf(false)
    var message by mutableStateOf("Listo")

    suspend fun scanRoms(context: android.content.Context, uri: Uri) {
        busy = true
        message = "Escaneando ROMs…"
        val result = withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, uri)
            val list = mutableListOf<RomFile>()
            fun walk(dir: DocumentFile) {
                dir.listFiles().forEach { f ->
                    if (f.isDirectory) walk(f)
                    else {
                        val ext = f.name?.substringAfterLast('.', "")?.lowercase() ?: ""
                        if (ext in setOf("nes","smc","sfc","gb","gbc","gba","n64","z64","v64","nds","3ds","cia",
                                "iso","cso","chd","cue","bin","pbp","mdf","img","rom","zip")) {
                            list += RomFile(f.name ?: "ROM", f.uri, f.length(), detectSystem(ext))
                        }
                    }
                }
            }
            if (root != null) walk(root)
            list.sortedBy { it.name.lowercase() }
        }
        roms = result
        romFolderName = "Carpeta seleccionada (${result.size} ROMs)"
        message = "${result.size} ROM(s) encontradas"
        busy = false
    }

    suspend fun scanBios(context: android.content.Context, uri: Uri) {
        busy = true
        message = "Calculando hashes MD5…"
        val result = withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, uri)
            val list = mutableListOf<BiosResult>()
            fun walk(dir: DocumentFile) {
                dir.listFiles().forEach { f ->
                    if (f.isDirectory) walk(f)
                    else {
                        val name = f.name ?: return@forEach
                        val md5 = try { md5(context, f.uri) } catch (_: Exception) { "" }
                        val match = ps1Bios.firstOrNull {
                            it.md5.equals(md5, ignoreCase = true)
                        }
                        val status = when {
                            match != null && match.filename.equals(name, ignoreCase = true) -> "✓ VÁLIDA"
                            match != null -> "⚠ HASH VÁLIDO / NOMBRE DISTINTO"
                            else -> "✗ NO RECONOCIDA"
                        }
                        list += BiosResult(name, f.length(), md5, status, match?.description ?: "Sin coincidencia")
                    }
                }
            }
            if (root != null) walk(root)
            list
        }
        bios = result
        biosFolderName = "Carpeta seleccionada (${result.size} archivos)"
        message = "${result.count { it.status.startsWith("✓") }} BIOS válidas"
        busy = false
    }

    private fun detectSystem(ext: String): String = when (ext) {
        "nes" -> "NES"
        "smc","sfc" -> "SNES"
        "gb","gbc" -> "GB/GBC"
        "gba" -> "GBA"
        "n64","z64","v64" -> "N64"
        "nds" -> "NDS"
        "3ds","cia" -> "3DS"
        "iso","cso" -> "ISO / disco"
        "cue","bin","pbp" -> "PS1 / disco"
        else -> "Desconocido"
    }

    private fun md5(context: android.content.Context, uri: Uri): String {
        val md = MessageDigest.getInstance("MD5")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            val buffer = ByteArray(1024 * 1024)
            var n: Int
            while (input.read(buffer).also { n = it } > 0) md.update(buffer, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

@Composable
fun BrayanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFFF1744),
            secondary = Color(0xFFFFC107),
            background = Color(0xFF08090C),
            surface = Color(0xFF15171D)
        ),
        content = content
    )
}

@Composable
fun App(vm: BrayanEmuViewModel = viewModel(), pickRoms: () -> Unit, pickBios: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("BrayanEmu", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    Text("v0.7", color = MaterialTheme.colorScheme.secondary)
                }
                Text(vm.message, color = Color.LightGray)
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, label = { Text("ROMs") }, icon = { Text("🎮") })
                NavigationBarItem(tab == 1, { tab = 1 }, label = { Text("BIOS") }, icon = { Text("⚙") })
                NavigationBarItem(tab == 2, { tab = 2 }, label = { Text("Motores") }, icon = { Text("🧩") })
            }
        }
    ) { padding ->
        when (tab) {
            0 -> RomScreen(Modifier.padding(padding), vm, pickRoms)
            1 -> BiosScreen(Modifier.padding(padding), vm, pickBios)
            2 -> CoreScreen(Modifier.padding(padding))
        }
    }
}

@Composable
fun RomScreen(modifier: Modifier, vm: BrayanEmuViewModel, pick: () -> Unit) {
    Column(modifier.padding(16.dp)) {
        Button(onClick = pick, modifier = Modifier.fillMaxWidth()) { Text("Seleccionar carpeta de ROMs") }
        Spacer(Modifier.height(8.dp))
        Text(vm.romFolderName, color = Color.LightGray)
        Spacer(Modifier.height(12.dp))
        if (vm.roms.isEmpty()) {
            Text("Aún no hay ROMs escaneadas.", color = Color.Gray)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.roms) { r ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(r.name, fontWeight = FontWeight.Bold)
                            Text("${r.system}  •  ${formatSize(r.size)}", color = Color.LightGray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BiosScreen(modifier: Modifier, vm: BrayanEmuViewModel, pick: () -> Unit) {
    Column(modifier.padding(16.dp)) {
        Button(onClick = pick, modifier = Modifier.fillMaxWidth()) { Text("Seleccionar carpeta de BIOS") }
        Spacer(Modifier.height(8.dp))
        Text(vm.biosFolderName, color = Color.LightGray)
        Spacer(Modifier.height(12.dp))
        Text("Referencias PS1 integradas", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        ps1Bios.forEach { req ->
            Text("${req.filename}  •  ${req.md5}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.bios) { b ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(b.filename, fontWeight = FontWeight.Bold)
                        Text(b.status, color = if (b.status.startsWith("✓")) MaterialTheme.colorScheme.secondary else Color.LightGray)
                        Text("MD5: ${b.md5}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text(b.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun CoreScreen(modifier: Modifier) {
    Column(modifier.padding(16.dp)) {
        Text("Motores / cores", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Esta versión prepara la gestión de ROMs y firmware. Los cores nativos todavía deben compilarse e incluirse como bibliotecas Android (.so); esta pantalla evita fingir que un catálogo equivale a un motor real.",
            color = Color.LightGray
        )
        Spacer(Modifier.height(14.dp))
        consoles.forEach { c ->
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.Bold)
                        Text(c.notes, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(if (c.biosRequired) "BIOS" else "—", color = Color.LightGray)
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

class MainActivity : ComponentActivity() {
    private val romPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val vm = currentVm
            if (vm != null) vmScope?.let { scope ->
                scope.launch { vm.scanRoms(this@MainActivity, uri) }
            }
        }
    }
    private val biosPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val vm = currentVm
            if (vm != null) vmScope?.let { scope ->
                scope.launch { vm.scanBios(this@MainActivity, uri) }
            }
        }
    }
    private var currentVm: BrayanEmuViewModel? = null
    private var vmScope: CoroutineScope? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: BrayanEmuViewModel = viewModel()
            currentVm = vm
            vmScope = rememberCoroutineScope()
            BrayanTheme {
                App(vm, pickRoms = { romPicker.launch(null) }, pickBios = { biosPicker.launch(null) })
            }
        }
    }
}
