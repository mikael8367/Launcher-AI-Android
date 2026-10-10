# Launcher AI Android

Aplicativo Android inicial para organizar testes de launchers de Minecraft Java, com foco no Minecraft 1.12.2 + OptiFine e no desempenho em celulares como o Samsung Galaxy A12.

## Estado atual

Esta é a primeira base do projeto, não um APK final. Inclui uma interface nativa escura com detalhes verdes, resumo de permissões, atalhos para as configurações do Android e um esqueleto de serviço de acessibilidade opcional.

Ainda NÃO implementa instalação automática de launchers, download de Minecraft/OptiFine, alteração automática de todas as opções do OptiFine, entrada autônoma em mundos ou captura confiável de FPS. Esses fluxos precisam ser construídos e testados por launcher; o Android também exige confirmações do usuário para operações sensíveis.

## Compilar

Abra o repositório no Android Studio e sincronize com Gradle (JDK 17 recomendado). Gere o APK de depuração pelo menu **Build > Build APK(s)**.

## Permissões e segurança

O app não pode conceder a si próprio todas as permissões. Acessibilidade e autorização para instalar APKs precisam ser habilitadas pelo usuário nas configurações do Android. O usuário deve confirmar instalações e autenticações.

## Próximas etapas

1. Testar a compilação e a inicialização no Android 12.
2. Adicionar diagnóstico de espaço livre, RAM e logs.
3. Integrar um fluxo de teste reproduzível para cada launcher suportado.
4. Implementar medição de FPS baseada em dados reais e mostrar média/mínimo/estabilidade.
5. Validar downloads e licenças dos componentes do jogo.
