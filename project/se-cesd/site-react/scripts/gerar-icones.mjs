/**
 * Gera os ícones PNG do PWA a partir de public/icons/cesd-icon.svg.
 * Uso: npm run icones   (requer o devDependency "sharp")
 */
import sharp from 'sharp';
import { readFileSync } from 'node:fs';

const origem = 'public/icons/cesd-icon.svg';
const alvos = [
  { arquivo: 'public/icons/icon-512.png', lado: 512 },
  { arquivo: 'public/icons/icon-192.png', lado: 192 },
  { arquivo: 'public/icons/apple-touch-icon.png', lado: 180 },
  { arquivo: 'public/icons/favicon-32.png', lado: 32 },
];

for (const alvo of alvos) {
  await sharp(readFileSync(origem)).resize(alvo.lado, alvo.lado).png().toFile(alvo.arquivo);
  console.log('gerado:', alvo.arquivo, alvo.lado + 'x' + alvo.lado);
}
