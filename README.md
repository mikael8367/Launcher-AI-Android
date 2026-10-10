# Launcher AI Android

Protótipo de **controle assistido do celular Android**, pensado para Android 12 e aparelhos como o Samsung Galaxy A12.

## Controle do celular

O painel **Controle do celular por IA** usa o serviço oficial de Acessibilidade do Android, que precisa ser ativado manualmente em Configurações > Acessibilidade > Launcher AI. Depois de ativado, o app aceita estes comandos:

- `abrir NOME DO APP` — abre um aplicativo instalado cujo nome corresponda ao texto.
- `voltar`, `início`, `recentes` — navegação do sistema.
- `notificações`, `configurações rápidas` — abre os painéis do sistema.
- `tocar X Y` — toca nas coordenadas da tela em pixels.
- `deslizar X1 Y1 X2 Y2` — executa um gesto entre dois pontos.
- Os botões rápidos Voltar, Início e Recentes executam as mesmas ações.

As coordenadas dependem da resolução e da orientação atuais. O serviço comunica o resultado da última ação na tela.

## Limites importantes

Esta versão **ainda não é uma IA geral que entende qualquer pedido em linguagem natural**. Ela executa os comandos explícitos listados acima. Ações como conceder permissões, instalar APKs, entrar em contas, confirmar compras ou ignorar a tela de bloqueio não são feitas silenciosamente: o Android mantém as confirmações e proteções do sistema sob controle do usuário. A acessibilidade precisa ser habilitada pelo próprio usuário.

O projeto ainda inclui a tela experimental de medição de FPS do Minecraft por OCR do contador F3, com consentimento de captura de tela por sessão e exportação CSV. Essa função é separada do controle do celular.

## Build e teste

1. Abra **Actions** neste repositório.
2. Abra a execução mais recente do workflow **Android build** que terminou com sucesso.
3. Baixe o artefato `Launcher-AI-debug` e extraia `app-debug.apk`.
4. Instale no Android. O APK de depuração não é um release assinado para distribuição.
5. Abra o app, toque em ativar Acessibilidade e habilite **Launcher AI** manualmente.
6. Teste primeiro `voltar`, `início` e `abrir Configurações`.

A compilação do GitHub Actions verifica o código, mas não substitui teste físico no Galaxy A12. Ainda é necessário validar comportamento real e corrigir qualquer erro reportado pelo aparelho.

## Tecnologias

- Kotlin nativo para Android.
- Android AccessibilityService e gestos de acessibilidade.
- ML Kit Text Recognition para a ferramenta experimental de leitura de FPS.
