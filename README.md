# Launcher AI Android

Aplicativo Android para investigar o desempenho de launchers de Minecraft Java, com foco em Minecraft 1.12.2 + OptiFine e celulares como o Samsung Galaxy A12.

## O que já faz o código

- Detecta se FCL/PojavLauncher estão instalados (quando usam os identificadores de pacote declarados) e fornece acesso às páginas oficiais de versões.
- Solicita a permissão de captura de tela do Android para uma sessão de teste.
- Lê por OCR o contador de FPS visível no overlay F3 do próprio Minecraft, usando ML Kit localmente.
- Registra as leituras reconhecidas, calcula média/mínimo/máximo, mantém histórico local e exporta histórico/amostras para CSV.
- Mostra modelo do celular, versão do Android, ABI, RAM disponível e estado de algumas permissões.
- Compila um APK de depuração via GitHub Actions e publica o APK como artefato do workflow.

## Como obter e testar o APK

1. Abra **Actions** no repositório.
2. Entre na execução mais recente de **Android build** que terminou com sucesso.
3. Baixe o artefato Launcher-AI-debug e extraia o app-debug.apk.
4. Instale no Android 12. O app não é ainda um release assinado para distribuição.

## Como medir FPS do jogo de verdade

1. Instale/abra um launcher a partir da página oficial acessada pelo app.
2. Pelo launcher, obtenha Minecraft Java 1.12.2 e instale OptiFine; entre com sua conta/licença conforme solicitado.
3. Entre num mundo normal. Para comparar launchers, use o mesmo mundo, posição, distância de renderização, configurações gráficas, runtime, renderizador, brilho e duração.
4. Deixe o F3 visível no jogo. O app só registra leituras quando o OCR reconhece um padrão explícito como “60 fps” ou “fps: 60”.
5. Volte ao Launcher AI, escolha o launcher no seletor e toque em **Iniciar captura real**. Aceite a tela de consentimento do Android e volte ao Minecraft pelas aplicações recentes.
6. Deixe o jogo rodar sem mexer por pelo menos 60 segundos. Depois volte ao Launcher AI e pare o teste. Confira o número de amostras reconhecidas e exporte o CSV.

## Precisão / limitações conhecidas

- Os números vêm do contador F3 real na tela, não de dados fictícios. O OCR pode perder ou interpretar incorretamente uma leitura; amostras não reconhecidas não são interpoladas. Por isso, não é correto prometer precisão de 100% apenas com OCR. A média é das leituras reconhecidas, não de cada frame individual.
- Para medição frame-a-frame mais precisa seria necessária integração dentro de Minecraft/OptiFine ou um mod/agente de benchmark compatível com a instância testada.
- A captura exige consentimento por sessão e processa as imagens em memória; imagens não são salvas.
- O app atualmente não faz login, não baixa automaticamente o jogo ou OptiFine, não instala APKs silenciosamente, não altera sozinho todas as opções do OptiFine e não percorre mundos automaticamente. O Android exige confirmação para instalação/autenticação e cada launcher tem fluxos e armazenamento próprios.
- O serviço de acessibilidade é apenas uma base opcional e ainda não executa a navegação automática. Não habilite acessibilidade para conceder permissões às cegas; o usuário mantém o controle.

## Compilação

Projeto nativo Kotlin, minSdk 26, targetSdk 35, Android Gradle Plugin 8.7.3 e Gradle 8.9. A automação de build está em .github/workflows/android.yml.
