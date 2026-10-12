# SE • CESD — Memorial do Soldado Especializado (React + Vite + Tailwind)

Projeto web completo (PWA) do memorial pessoal do Soldado Especializado da Aeronáutica,
com cofre criptografado e backup no Google Drive.

## Tecnologias
- React 18 + TypeScript
- Vite 5
- Tailwind CSS 3
- WebCrypto (PBKDF2-SHA256 120k + AES-256-GCM) — **compatível com o app nativo**:
  um backup gerado aqui abre no app Android e vice-versa.

## Rodar
```bash
npm install
npm run dev        # desenvolvimento
npm run build      # produção (gera dist/)
npm run preview    # serve o build
npm run typecheck  # checagem de tipos
npm run icones     # regenera os ícones PNG (usa sharp)
```

## Estrutura
- `src/lib/cofre.ts` — criptografia do cofre (mesmo formato do Android: usuario, sal,
  ivVerif, verif, ivDados, dados; marca "se-cesd-aberto").
- `src/lib/drive.ts` — backup no Drive pelo navegador (OAuth 2.0 + PKCE, sem backend).
- `src/lib/imagens.ts` — EXIF (foto de câmera nunca nasce deitada), girar, JPEG.
- `src/telas/` — Acesso, Painel, Cadastro, Memorial (5 fases), Trajetória, Galeria
  (4 locais), Documentos (4 locais, PDF/imagem), Backup, Insígnia & Valores.
- `public/sw.js` — service worker (cache offline); `public/manifest.webmanifest` — PWA.
- `scripts/gerar-icones.mjs` — gera os ícones a partir de `public/icons/cesd-icon.svg`.

## Google Drive (web)
1. No Google Cloud → Credenciais → criar cliente OAuth do tipo **Aplicativo Web**.
2. Em "Origens de redirecionamento autorizadas", cadastre o endereço exato do site
   (ex.: `https://seuusuario.github.io/SE-CESD/`).
3. No app: Backup → cole o ID do cliente → Conectar ao Drive.
   O escopo é `drive.file` (o app só enxerga os arquivos que ele mesmo cria).
