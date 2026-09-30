# SE • CESD

App Android do memorial do Soldado Especializado (SE) da Aeronáutica. O aplicativo é um WebView que abre, de dentro do próprio APK, o site já compilado (`android/app/src/main/assets/web/`).

## O que tem neste repositório

| Caminho | O que é |
|---|---|
| `se-cesd.zip` | Pacote original enviado. Fica **intacto**, como cópia de segurança. |
| `android/` | Projeto Android (Gradle), extraído do zip. Só código: sem caches, chaves nem APKs. |
| `.github/workflows/build-apk.yml` | Build automático: gera o APK nos servidores do GitHub. |

## Como baixar o APK

1. Abra a aba **Actions** do repositório.
2. Clique na execução mais recente de **Gerar APK** (com ✅).
3. No fim da página, em **Artifacts**, clique em **SE-CESD.apk**.
4. Instale no Android (se pedir, permita a instalação por essa fonte).

O build roda sozinho a cada envio de código. Os APKs ficam disponíveis por 30 dias; depois disso basta rodar o build de novo.

## Assinatura: leia antes de mudar

O APK é assinado com a **mesma chave de teste** do APK original, que está dentro do `se-cesd.zip`. Por isso o APK novo **instala por cima** do app já instalado, **sem apagar os dados**. O workflow confere a impressão digital (SHA-1) do certificado e **falha** se ela mudar.

- Essa é uma chave de depuração. Enquanto o repositório for público, qualquer pessoa consegue pegá-la no `se-cesd.zip`. Ela não serve para publicar na Play Store.
- Trocar de chave obriga a desinstalar o app, e isso **apaga os dados locais**. Só troque depois de existir backup.

## Ferramentas do projeto

Java 17, Gradle 7.6.4 e plugin Android 7.4.2 (compileSdk/targetSdk 33, minSdk 26), as mesmas do APK original.
