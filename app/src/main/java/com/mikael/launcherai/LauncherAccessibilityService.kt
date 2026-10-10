package com.mikael.launcherai

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Serviço opcional. Não executa ações destrutivas nem confirma instalações sozinho.
 * A automação real precisa ser implementada e testada por fluxo e launcher.
 */
class LauncherAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Futuro: reconhecer telas de launchers compatíveis com consentimento do usuário.
    }

    override fun onInterrupt() = Unit
}
