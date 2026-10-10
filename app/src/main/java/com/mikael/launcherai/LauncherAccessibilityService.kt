package com.mikael.launcherai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Controle assistido do Android. Só atua depois que o usuário habilita
 * manualmente este serviço nas configurações de Acessibilidade.
 */
class LauncherAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile private var active: LauncherAccessibilityService? = null
        @Volatile var lastResult: String = "Ative a Acessibilidade para começar."
            private set

        fun isConnected() = active != null

        fun command(raw: String): Boolean {
            val service = active ?: run {
                lastResult = "Acessibilidade desativada. Ative o serviço nas configurações."
                return false
            }
            return service.runCommand(raw.trim())
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        active = this
        lastResult = "Controle conectado. Digite um comando."
    }

    override fun onDestroy() {
        if (active === this) active = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { lastResult = "Controle interrompido." }

    private fun runCommand(raw: String): Boolean {
        if (raw.isBlank()) {
            lastResult = "Digite um comando."
            return false
        }
        val text = raw.lowercase(Locale.ROOT)
        return try {
            when {
                text in listOf("voltar", "volta", "back") ->
                    global(GLOBAL_ACTION_BACK, "Voltei uma tela.")
                text in listOf("início", "inicio", "home", "tela inicial") ->
                    global(GLOBAL_ACTION_HOME, "Abri a tela inicial.")
                text in listOf("recentes", "apps recentes", "multitarefas") ->
                    global(GLOBAL_ACTION_RECENTS, "Abri os aplicativos recentes.")
                text in listOf("notificações", "notificacoes", "abrir notificações") ->
                    global(GLOBAL_ACTION_NOTIFICATIONS, "Abri as notificações.")
                text in listOf("configurações rápidas", "configuracoes rapidas", "painel rápido", "painel rapido") ->
                    global(GLOBAL_ACTION_QUICK_SETTINGS, "Abri as configurações rápidas.")
                text.startsWith("tocar ") || text.startsWith("toque ") -> {
                    val nums = Regex("\\d+").findAll(text).mapNotNull { it.value.toIntOrNull() }.toList()
                    if (nums.size < 2) fail("Use: tocar X Y (coordenadas da tela).")
                    else gesture(nums[0].toFloat(), nums[1].toFloat(), nums[0].toFloat(), nums[1].toFloat(), 80, "Toque enviado.")
                }
                text.startsWith("deslizar ") || text.startsWith("arrastar ") -> {
                    val nums = Regex("\\d+").findAll(text).mapNotNull { it.value.toIntOrNull() }.toList()
                    if (nums.size < 4) fail("Use: deslizar X1 Y1 X2 Y2.")
                    else gesture(nums[0].toFloat(), nums[1].toFloat(), nums[2].toFloat(), nums[3].toFloat(), 450, "Deslize enviado.")
                }
                text.startsWith("abrir ") -> openApp(raw.substringAfter(' ').trim())
                text in listOf("ajuda", "comandos", "o que posso fazer") -> {
                    lastResult = "Comandos: voltar, início, recentes, notificações, configurações rápidas, abrir NOME DO APP, tocar X Y, deslizar X1 Y1 X2 Y2."
                    true
                }
                else -> {
                    lastResult = "Ainda não reconheço esse comando. Digite 'ajuda' para ver os comandos disponíveis."
                    false
                }
            }
        } catch (e: Exception) {
            lastResult = "Falha ao executar: ${e.message ?: e.javaClass.simpleName}"
            false
        }
    }

    private fun global(action: Int, message: String): Boolean {
        val ok = performGlobalAction(action)
        lastResult = if (ok) message else "O Android não aceitou essa ação."
        return ok
    }

    private fun fail(message: String): Boolean { lastResult = message; return false }

    private fun gesture(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long, message: String): Boolean {
        val dm = resources.displayMetrics
        if (x1 < 0 || y1 < 0 || x2 < 0 || y2 < 0 ||
            x1 >= dm.widthPixels || x2 >= dm.widthPixels ||
            y1 >= dm.heightPixels || y2 >= dm.heightPixels) {
            return fail("Coordenadas fora da tela: ${dm.widthPixels} × ${dm.heightPixels}.")
        }
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration.coerceAtLeast(80))
        val accepted = dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        lastResult = if (accepted) message else "O Android recusou o gesto."
        return accepted
    }

    private fun openApp(query: String): Boolean {
        if (query.isBlank()) return fail("Diga o nome do aplicativo: abrir YouTube.")
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = pm.queryIntentActivities(intent, 0)
        val found = matches.firstOrNull {
            it.loadLabel(pm).toString().contains(query, ignoreCase = true)
        } ?: return fail("Não encontrei um aplicativo chamado '$query'.")
        val launch = pm.getLaunchIntentForPackage(found.activityInfo.packageName)
            ?: return fail("Não consegui abrir esse aplicativo.")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
        lastResult = "Abrindo ${found.loadLabel(pm)}."
        return true
    }
}
