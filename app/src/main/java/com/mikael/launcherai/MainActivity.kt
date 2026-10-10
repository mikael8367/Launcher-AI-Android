package com.mikael.launcherai

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_CAPTURE = 1001
        private const val REQUEST_SAVE_CSV = 1002
        private const val PREFS = "launcher_ai_metrics"
        private const val URL_FCL = "https://github.com/FCL-Team/FoldCraftLauncher/releases"
        private const val URL_POJAV = "https://github.com/PojavLauncherTeam/PojavLauncher/releases"
    }

    private val green = 0xFF38F58A.toInt()
    private val bg = 0xFF06110F.toInt()
    private val panel = 0xFF10211E.toInt()
    private val muted = 0xFFB4C9C2.toInt()
    private val white = 0xFFF5FFF9.toInt()
    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var statusText: TextView
    private lateinit var deviceText: TextView
    private lateinit var statsText: TextView
    private lateinit var historyText: TextView
    private lateinit var launcherSpinner: Spinner

    private val refreshTask = object : Runnable {
        override fun run() {
            if (::statusText.isInitialized) refreshLiveData()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        render()
        refreshLiveData()
        handler.post(refreshTask)
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) refreshLiveData()
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshTask)
        super.onDestroy()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(18), dp(18), dp(18), dp(26))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        root.addView(label("◈", 44f, green, true, Gravity.CENTER), matchWrap())
        root.addView(label("LAUNCHER AI", 27f, white, true, Gravity.CENTER), matchWrap())
        root.addView(label("Teste real • Minecraft Java 1.12.2 + OptiFine", 13f, muted, false, Gravity.CENTER), matchWrap())
        root.addView(space(16), matchWrap())

        val intro = card()
        intro.addView(label("Comparação de desempenho", 19f, white, true))
        intro.addView(space(7))
        intro.addView(label("A medição lê o contador de FPS do F3 no próprio Minecraft usando captura de tela autorizada por ti. O app não inventa resultados nem grava imagens.", 14f, muted))
        intro.addView(space(9))
        intro.addView(label("Importante: isto mede as leituras exibidas pelo jogo. O OCR pode perder leituras; para resultados comparáveis, use o mesmo mundo, local, configurações e duração.", 12f, 0xFFFFD18A.toInt()))
        root.addView(intro, matchWrap())
        root.addView(space(12), matchWrap())

        val launchers = card()
        launchers.addView(label("LAUNCHERS", 16f, white, true))
        launchers.addView(space(8))
        launchers.addView(label("Escolha o launcher que vai testar. Se não estiver instalado, o botão abre a página oficial de versões; o Android pedirá confirmação ao instalar o APK.", 13f, muted))
        launchers.addView(space(10))
        launcherSpinner = Spinner(this)
        launcherSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("FCL", "PojavLauncher", "Outro / manual")
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        launcherSpinner.setSelection(
            listOf("FCL", "PojavLauncher", "Outro / manual")
                .indexOf(prefs.getString("selected_launcher", "FCL")).coerceAtLeast(0)
        )
        launcherSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putString("selected_launcher", launcherSpinner.selectedItem?.toString() ?: "FCL").apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        })
        launchers.addView(launcherSpinner, matchWrap())
        launchers.addView(space(9))
        addLauncherRow(launchers, "Fold Craft Launcher (FCL)", "com.tungsten.fcl", URL_FCL)
        launchers.addView(space(7))
        addLauncherRow(launchers, "PojavLauncher", "net.kdt.pojavlaunch", URL_POJAV)
        root.addView(launchers, matchWrap())
        root.addView(space(12), matchWrap())

        val capture = card()
        capture.addView(label("TESTE DE FPS REAL", 16f, white, true))
        capture.addView(space(8))
        capture.addView(label("1. Abra o launcher escolhido e inicie Minecraft Java 1.12.2 com OptiFine.\n2. Entre num mundo normal e mostre o contador F3 (tecla F3/teclado virtual).\n3. Volte a este app, toque em INICIAR CAPTURA e autorize a gravação da tela.\n4. Retorne ao Minecraft pelas aplicações recentes e jogue na mesma área por pelo menos 60 segundos.\n5. Volte aqui e toque em PARAR TESTE.", 14f, muted))
        capture.addView(space(10))
        capture.addView(actionButton("INICIAR CAPTURA REAL") { startRealCapture() })
        capture.addView(space(7))
        capture.addView(actionButton("PARAR TESTE E SALVAR RESULTADO") {
            if (prefs.getBoolean("capturing", false)) {
                startService(Intent(this, FpsCaptureService::class.java).setAction(FpsCaptureService.ACTION_STOP))
            } else {
                showMessage("Não há captura ativa. Inicie o teste e autorize a captura de tela primeiro.")
            }
        })
        capture.addView(space(7))
        statusText = label("Estado: pronto.", 13f, green, true)
        capture.addView(statusText)
        root.addView(capture, matchWrap())
        root.addView(space(12), matchWrap())

        val stats = card()
        stats.addView(label("LEITURAS DO JOGO", 16f, white, true))
        stats.addView(space(7))
        statsText = label("Nenhuma leitura ainda. O F3 precisa estar visível no Minecraft.", 14f, green, true)
        stats.addView(statsText)
        stats.addView(space(8))
        stats.addView(actionButton("EXPORTAR CSV (HISTÓRICO + AMOSTRAS)") { exportCsv() })
        root.addView(stats, matchWrap())
        root.addView(space(12), matchWrap())

        val device = card()
        device.addView(label("DIAGNÓSTICO DO CELULAR", 16f, white, true))
        device.addView(space(7))
        deviceText = label("", 13f, muted)
        device.addView(deviceText)
        device.addView(space(8))
        device.addView(actionButton("ATUALIZAR DIAGNÓSTICO") { refreshLiveData() })
        root.addView(device, matchWrap())
        root.addView(space(12), matchWrap())

        val perms = card()
        perms.addView(label("PERMISSÕES NECESSÁRIAS", 16f, white, true))
        perms.addView(space(7))
        perms.addView(label("A captura de tela pede autorização do Android em cada sessão. Acessibilidade e instalação de APKs são ativadas manualmente; arquivos são exportados pelo seletor seguro do Android.", 13f, muted))
        perms.addView(space(8))
        perms.addView(actionButton("ABRIR ACESSIBILIDADE") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        perms.addView(space(7))
        perms.addView(actionButton("AUTORIZAR INSTALAÇÃO DE APKs") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } else showMessage("Esta opção não é necessária nesta versão do Android.")
        })
        if (Build.VERSION.SDK_INT >= 33) {
            perms.addView(space(7))
            perms.addView(actionButton("PERMITIR NOTIFICAÇÕES") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 20)
            })
        }
        root.addView(perms, matchWrap())
        root.addView(space(12), matchWrap())

        val history = card()
        history.addView(label("HISTÓRICO DE TESTES", 16f, white, true))
        history.addView(space(7))
        historyText = label("Nenhum teste salvo ainda.", 13f, muted)
        history.addView(historyText)
        root.addView(history, matchWrap())
        root.addView(space(12), matchWrap())
        root.addView(label("Launcher AI • protótipo em validação. Os resultados dependem do contador F3 visível e da leitura OCR.", 11f, muted, false, Gravity.CENTER), matchWrap())
    }

    private fun addLauncherRow(container: LinearLayout, title: String, packageNameToCheck: String, url: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val name = label(title + "\n" + if (isInstalled(packageNameToCheck)) "Instalado" else "Não detectado", 13f, white, true)
        row.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(actionButton(if (isInstalled(packageNameToCheck)) "Abrir" else "Obter") {
            if (isInstalled(packageNameToCheck)) {
                val launch = packageManager.getLaunchIntentForPackage(packageNameToCheck)
                if (launch != null) startActivity(launch)
                else showMessage("O Android não disponibilizou uma tela de abertura para $title.")
            } else {
                openUrl(url)
            }
        })
        container.addView(row, matchWrap())
    }

    private fun isInstalled(packageNameToCheck: String): Boolean = try {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageNameToCheck, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: Exception) {
        false
    }

    private fun startRealCapture() {
        if (prefs.getBoolean("capturing", false)) {
            showMessage("A captura já está em andamento. Volte ao Minecraft e depois pare o teste.")
            return
        }
        val chosen = launcherSpinner.selectedItem?.toString() ?: "Outro / manual"
        prefs.edit().putString("selected_launcher", chosen).apply()
        AlertDialog.Builder(this)
            .setTitle("Preparar teste real")
            .setMessage("Antes de continuar: Minecraft deve estar instalado, 1.12.2 + OptiFine deve abrir sem erro e tu precisas entrar num mundo normal. Deixe F3 visível. O Android vai pedir permissão para capturar a tela; as imagens serão processadas em memória e não serão salvas.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Continuar") { _, _ ->
                val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
            }
            .show()
    }

    @Deprecated("Activity result API retained for compatibility with API 26+ project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                val serviceIntent = Intent(this, FpsCaptureService::class.java)
                    .putExtra(FpsCaptureService.EXTRA_RESULT_CODE, resultCode)
                    .putExtra(FpsCaptureService.EXTRA_RESULT_DATA, data)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
                else startService(serviceIntent)
                showMessage("Captura iniciada. Volte ao Minecraft pelas aplicações recentes, jogue com F3 visível e depois pare o teste aqui.")
            } else {
                showMessage("Captura cancelada. Nenhum FPS foi registrado.")
            }
        } else if (requestCode == REQUEST_SAVE_CSV && resultCode == RESULT_OK && data?.data != null) {
            saveCsvTo(data.data!!)
        }
    }

    private fun refreshLiveData() {
        if (!::statusText.isInitialized) return
        val capturing = prefs.getBoolean("capturing", false)
        statusText.text = "Estado: " + prefs.getString("state", "Pronto.").orEmpty()
        statusText.setTextColor(if (capturing) green else muted)

        val samples = readSamplePairs()
        if (samples.isEmpty()) {
            statsText.text = "Última leitura: —\nAmostras de FPS: 0\nMédia: — • Mínimo: — • Máximo: —\nAtive F3 no jogo. O contador precisa aparecer literalmente como “60 fps” ou “fps: 60”."
        } else {
            val values = samples.map { it.second }
            val avg = values.average()
            val last = values.last()
            statsText.text = "Última leitura do F3: $last FPS\nAmostras válidas: ${values.size}\nMédia das leituras: ${String.format(Locale.getDefault(), "%.1f", avg)} FPS\nMínimo lido: ${values.minOrNull()} FPS • Máximo lido: ${values.maxOrNull()} FPS\nLinha reconhecida: ${prefs.getString("last_line", "")}"
        }

        val memory = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        memory.getMemoryInfo(memInfo)
        val freeMb = memInfo.availMem / (1024 * 1024)
        val totalMb = if (Build.VERSION.SDK_INT >= 16) memInfo.totalMem / (1024 * 1024) else -1
        val ramText = if (totalMb > 0) "$freeMb MB disponíveis de $totalMb MB" else "$freeMb MB disponíveis"
        val cpu = Build.SUPPORTED_ABIS.firstOrNull() ?: "desconhecido"
        val accessEnabled = try {
            val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            enabled.contains("$packageName/${LauncherAccessibilityService::class.java.name}")
        } catch (_: Exception) { false }
        val notifications = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val install = Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()
        deviceText.text = "Modelo: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nArquitetura: $cpu\nRAM: $ramText\nAcessibilidade: ${if (accessEnabled) "ativada" else "desativada"}\nNotificações: ${if (notifications) "permitidas" else "pendentes"}\nInstalar APKs: ${if (install) "permitido" else "autorização pendente"}\nFCL: ${if (isInstalled("com.tungsten.fcl")) "instalado" else "não detectado"} • Pojav: ${if (isInstalled("net.kdt.pojavlaunch")) "instalado" else "não detectado"}"

        val history = prefs.getString("history", "").orEmpty().lines().filter { it.isNotBlank() }.takeLast(8).reversed()
        historyText.text = if (history.isEmpty()) {
            "Nenhum teste salvo ainda. Pare uma captura com leituras válidas para guardar uma linha no histórico."
        } else {
            history.joinToString("\n\n") { row ->
                val parts = row.split("|")
                if (parts.size >= 6) {
                    val date = try { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(parts[0].toLong())) } catch (_: Exception) { parts[0] }
                    "$date • ${parts[1]}\nMédia ${parts[2]} FPS • mín ${parts[3]} • máx ${parts[4]} • ${parts[5]} amostras"
                } else row
            }
        }
    }

    private fun readSamplePairs(): List<Pair<Long, Int>> =
        prefs.getString("samples", "").orEmpty().split(';').mapNotNull { item ->
            val pieces = item.split(':')
            if (pieces.size == 2) {
                val time = pieces[0].toLongOrNull()
                val fps = pieces[1].toIntOrNull()
                if (time != null && fps != null) time to fps else null
            } else null
        }

    private fun exportCsv() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "launcher-ai-fps.csv")
        }
        startActivityForResult(intent, REQUEST_SAVE_CSV)
    }

    private fun saveCsvTo(uri: Uri) {
        try {
            val output = contentResolver.openOutputStream(uri) ?: throw IllegalStateException("Não foi possível abrir o destino.")
            OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                writer.write("Launcher AI - resultados medidos\n")
                writer.write("Dispositivo,${Build.MANUFACTURER} ${Build.MODEL}\n")
                writer.write("Android,${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\n")
                writer.write("\nHistórico\nEpochMs,Launcher,AvgFps,MinFps,MaxFps,SampleCount\n")
                prefs.getString("history", "").orEmpty().lines().filter { it.isNotBlank() }.forEach { writer.write(it.replace("|", ",") + "\n") }
                writer.write("\nAmostras do último teste\nTempoMs,FPS_lido_do_F3\n")
                readSamplePairs().forEach { writer.write("${it.first},${it.second}\n") }
                writer.write("\nNota,Valores reconhecidos por OCR a partir do F3 visível; amostras ausentes/perdidas não são interpoladas.\n")
            }
            showMessage("CSV exportado com as leituras reconhecidas. O arquivo contém apenas os valores medidos e o histórico local.")
        } catch (e: Exception) {
            showMessage("Falha ao exportar CSV: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { showMessage("Não foi possível abrir o link oficial.") }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(15), dp(15), dp(15))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(panel)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), 0xFF1E4C3D.toInt())
        }
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false, gravityValue: Int = Gravity.START) =
        TextView(this).apply {
            this.text = text
            textSize = size
            gravity = gravityValue
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(2))
        }

    private fun actionButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(0xFF03140B.toInt())
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(green))
        setOnClickListener { action() }
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun showMessage(message: String) =
        AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show()
}
