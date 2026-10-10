package com.mikael.launcherai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val green = 0xFF38F58A.toInt()
    private val bg = 0xFF06110F.toInt()
    private val panel = 0xFF10211E.toInt()
    private val muted = 0xFFB4C9C2.toInt()
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) statusText.text = permissionSummary()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(20), dp(18), dp(20), dp(24))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        val logo = TextView(this).apply {
            text = "◈"
            textSize = 48f
            gravity = Gravity.CENTER
            setTextColor(green)
        }
        root.addView(logo, matchWrap())
        root.addView(label("LAUNCHER AI", 28f, 0xFFFFFFFF.toInt(), true), matchWrap())
        root.addView(label("Minecraft Java • laboratório de FPS", 14f, muted), matchWrap())
        root.addView(space(22), matchWrap())

        val hero = card()
        hero.addView(label("Descubra qual launcher funciona melhor no seu celular.", 19f, 0xFFFFFFFF.toInt(), true))
        hero.addView(space(8))
        hero.addView(label("O objetivo inicial é testar Minecraft Java 1.12.2 + OptiFine no Galaxy A12, registrando os resultados sem inventar medições.", 14f, muted))
        hero.addView(space(14))
        hero.addView(actionButton("VERIFICAR PERMISSÕES") { showPermissions() })
        root.addView(hero, matchWrap())
        root.addView(space(14), matchWrap())

        statusText = label(permissionSummary(), 14f, green, true)
        root.addView(card().apply {
            addView(label("PERMISSÕES", 16f, 0xFFFFFFFF.toInt(), true))
            addView(space(6))
            addView(statusText)
            addView(space(12))
            addView(actionButton("Abrir acessibilidade") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
            addView(space(8))
            addView(actionButton("Permitir instalar APKs") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")))
                } else showMessage("Esta opção não é necessária nesta versão do Android.")
            })
            if (Build.VERSION.SDK_INT >= 33) {
                addView(space(8))
                addView(actionButton("Permitir notificações") {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 20)
                })
            }
        }, matchWrap())
        root.addView(space(14), matchWrap())

        val actions = card()
        actions.addView(label("PLANO DE TESTE", 16f, 0xFFFFFFFF.toInt(), true))
        actions.addView(space(8))
        listOf(
            "1. Verificar permissões e espaço livre",
            "2. Preparar Minecraft Java 1.12.2 + OptiFine",
            "3. Aplicar perfil de teste pesado do OptiFine",
            "4. Executar um mundo de teste comparável",
            "5. Registrar FPS real e estabilidade",
            "6. Comparar launchers compatíveis"
        ).forEach { actions.addView(label("•  $it", 14f, muted)); actions.addView(space(7)) }
        actions.addView(space(8))
        actions.addView(actionButton("INICIAR DIAGNÓSTICO") {
            showMessage("Protótipo inicial: o diagnóstico de permissões está disponível. A instalação e a medição automática de FPS ainda precisam ser implementadas e validadas para cada launcher.")
        })
        root.addView(actions, matchWrap())
        root.addView(space(14), matchWrap())
        root.addView(label("Versão 1.0 • O usuário confirma instalações e logins.", 12f, muted), matchWrap())
    }

    private fun showPermissions() {
        val items = arrayOf(
            "Internet: disponível automaticamente",
            "Acessibilidade: ativada manualmente nas Configurações",
            "Instalar apps desconhecidos: autorização específica",
            "Notificações: solicitadas quando o Android exigir",
            "Arquivos: usar seletor de arquivos do Android quando necessário"
        )
        AlertDialog.Builder(this)
            .setTitle("Permissões do Launcher AI")
            .setItems(items) { _, which ->
                when (which) {
                    1 -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    2 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                    }
                    3 -> if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 20)
                    4 -> showMessage("O app solicitará acesso a arquivos por meio do seletor seguro do Android quando esse recurso for implementado.")
                    else -> showMessage("A permissão de internet é declarada no aplicativo.")
                }
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun permissionSummary(): String {
        val notifications = if (Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) "OK" else "pendente"
        val install = if (Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) "OK" else "pendente"
        return "Internet: OK  •  Notificações: $notifications  •  Instalar APKs: $install  •  Acessibilidade: habilitação manual"
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(panel); cornerRadius = dp(18).toFloat()
            setStroke(dp(1), 0xFF1E4C3D.toInt())
        }
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
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

    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun matchWrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun showMessage(message: String) = AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show()
}
