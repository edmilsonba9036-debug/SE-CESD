# SE • CESD

App Android do memorial do Soldado Especializado (SE) da Aeronáutica. O aplicativo é um WebView que abre, de dentro do próprio APK, o site já compilado (`android/app/src/main/assets/web/`).

## O que tem neste repositório

| Caminho | O que é |
|---|---|
| `se-cesd.zip` | Pacote original enviado. Fica **intacto**, como cópia de segurança. |
| `android/` | Projeto Android (Gradle), extraído do zip. Só código: sem caches, chaves nem APKs. |
| `.github/workflows/build-apk.yml` | Build automático: gera o APK nos servidores do GitHub. |
| `tools/patch-web.mjs` | Registro das mudanças feitas no site compilado (veja abaixo). |

## Como baixar o APK

1. Abra a aba **Actions** do repositório.
2. Clique na execução mais recente de **Gerar APK** (com ✅).
3. No fim da página, em **Artifacts**, clique em **SE-CESD.apk**.
4. Instale no Android (se pedir, permita a instalação por essa fonte).

O build roda sozinho a cada envio de código. Os APKs ficam disponíveis por 30 dias; depois disso basta rodar o build de novo.

## Mudanças feitas no site do app

O site que roda dentro do app está aqui só **compilado**, sem o código-fonte original. As mudanças feitas nele estão registradas, uma a uma, em `tools/patch-web.mjs`:

- **Campo "Insígnia" retirado:** saiu do menu, da página, do rodapé e da abertura (botão "Ver a divisa SE" e espaço "Adicionar imagem"). Isso inclui as abas *Imagem*, *Comparar insígnias* e *Uso regulamentar*. O código delas continua dentro do arquivo compilado, sem uso, e uma imagem já enviada antes segue guardada no aparelho, só não aparece mais.
- **Fotos e documentos:** os campos de foto aceitam qualquer imagem (PNG, WebP, GIF, BMP, JPG…) de qualquer tamanho e a reduzem automaticamente, como já faziam com JPG. Fotos anexadas como documento também não esbarram mais nos 50 MB. PDF, Word, Excel e outros arquivos não podem ser reduzidos e continuam com o limite de 50 MB. HEIC continua sem suporte, porque o Android não consegue abrir.

Para conferir que o site do repositório bate com o registro: `node tools/patch-web.mjs --check`. Se o código-fonte original aparecer, essas mudanças precisam ser refeitas nele; senão a próxima compilação as desfaz.

## Assinatura: leia antes de mudar

O APK é assinado com a **mesma chave de teste** do APK original, que está dentro do `se-cesd.zip`. Por isso o APK novo **instala por cima** do app já instalado, **sem apagar os dados**. O workflow confere a impressão digital (SHA-1) do certificado e **falha** se ela mudar.

- Essa é uma chave de depuração. Enquanto o repositório for público, qualquer pessoa consegue pegá-la no `se-cesd.zip`. Ela não serve para publicar na Play Store.
- Trocar de chave obriga a desinstalar o app, e isso **apaga os dados locais**. Só troque depois de existir backup.

## Ferramentas do projeto

Java 17, Gradle 7.6.4 e plugin Android 7.4.2 (compileSdk/targetSdk 33, minSdk 26), as mesmas do APK original.

Na página de cada execução aparece um aviso amarelo: *"Gradle 7.6.4 is end-of-life"*. É esperado e não atrapalha o build: são ferramentas de 2023. Atualizá-las é uma tarefa à parte, que pede teste no aparelho.
