#!/usr/bin/env node
// ─────────────────────────────────────────────────────────────────────────────
// Alterações feitas DIRETO no site compilado do app.
//
// O código-fonte original do site (React/Vite) não está neste repositório: só a
// versão compilada e minificada, que fica em
//   android/app/src/main/assets/web/index.html
// Este script registra, uma a uma, tudo o que foi mudado nesse arquivo em relação
// ao original guardado no se-cesd.zip. Cada edição é uma substituição exata e o
// script PARA se um trecho não for encontrado exatamente uma vez.
//
// Uso (na raiz do repositório):
//   node tools/patch-web.mjs           gera o index.html alterado a partir do original do zip
//   node tools/patch-web.mjs --check   só confere se o index.html do repositório é igual ao resultado
//
// Se um dia o código-fonte original aparecer, estas mesmas mudanças precisam ser
// refeitas nele; senão a próxima compilação desfaz tudo.
// ─────────────────────────────────────────────────────────────────────────────
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';

const ZIP = 'se-cesd.zip';
const ENTRADA_NO_ZIP = 'se-cesd/reconstructed/app/src/main/assets/web/index.html';
const SAIDA = 'android/app/src/main/assets/web/index.html';
const SHA256_ORIGINAL = 'd94f4b5f36ef9f12970041929d0b2bb3e333dc1c94b642eb5b41e72edf077f7f';

// Cada edição: { id, por_que, find, replace }  ou  { id, por_que, cut: [inicio, fim] }
// (cut apaga do início, inclusive, até o fim, exclusive).
const EDICOES = [
  // ── 1. Retirar o campo "Insígnia" ─────────────────────────────────────────
  {
    id: 'menu',
    por_que: 'Tira "Insígnia SE" do menu (topo e menu do celular).',
    find: '{href:"#insignia",label:"Insígnia SE"},',
    replace: '',
  },
  {
    id: 'pagina',
    por_que: 'Tira a seção "A Insígnia do SE" (abas Imagem, Comparar insígnias e Uso regulamentar) da página.',
    find: 'C.jsx(jm,{}),C.jsx($m,{}),',
    replace: 'C.jsx(jm,{}),',
  },
  {
    id: 'abertura-botao',
    por_que: 'Tira o botão "Ver a divisa SE", que rolava até a seção removida.',
    cut: [
      'C.jsxs("a",{href:"#insignia",className:"group flex items-center justify-center gap-2 rounded-2xl',
      'C.jsxs("a",{href:"#causa-cesd",className:"glass flex items-center justify-center gap-2 rounded-2xl',
    ],
  },
  {
    id: 'abertura-cartao-fundo',
    por_que: 'O quadro branco do cartão da abertura só aparece se houver nome de guerra (o espaço de imagem foi retirado).',
    find: 'C.jsxs("div",{className:"relative bg-white/85 p-6 backdrop-blur-xl sm:p-8",children:[',
    replace: 'g?.nomeGuerra&&C.jsxs("div",{className:"relative bg-white/85 p-6 backdrop-blur-xl sm:p-8",children:[',
  },
  {
    id: 'abertura-cartao-imagem',
    por_que: 'Tira o espaço "Adicionar imagem" (e a exibição da imagem da insígnia) do cartão da abertura.',
    find: 'children:C.jsx(Fm,{})',
    replace: 'children:null',
  },
  {
    id: 'abertura-seta',
    por_que: 'A seta "Role para marchar" passa a apontar para a próxima seção (Cadastro).',
    find: 'C.jsxs("a",{href:"#insignia",className:"relative mx-auto flex flex-col items-center gap-1 py-5',
    replace: 'C.jsxs("a",{href:"#cadastro",className:"relative mx-auto flex flex-col items-center gap-1 py-5',
  },
  {
    id: 'abertura-texto',
    por_que: 'O texto não promete mais a "insígnia de manga", que deixou de existir no app.',
    find: '"Uma divisa no braço. Conheça a insígnia de manga, a história e a jornada do"',
    replace: '"Uma divisa no braço. Conheça a história e a jornada do"',
  },
  {
    id: 'rodape',
    por_que: 'Tira "A Insígnia SE" da navegação do rodapé.',
    find: '["#insignia","A Insígnia SE"],',
    replace: '',
  },
  {
    id: 'apagar-tudo',
    por_que: 'O aviso de "apagar tudo" não cita mais "a imagem" (campo retirado).',
    find: 'a fotografia, a imagem, as fotos da galeria',
    replace: 'a fotografia, as fotos da galeria',
  },

  // ── 2. Fotos e documentos: redução automática, sem travar por formato ou tamanho ──
  {
    id: 'foto-formatos',
    por_que: 'Os campos de foto passam a aceitar qualquer imagem (antes só JPG).',
    find: 'accept:".jpg,.jpeg,image/jpeg"',
    replace: 'accept:"image/*"',
  },
  {
    id: 'foto-dica',
    por_que: 'Texto de ajuda de cada campo de foto, sem o limite de 40 MB e sem exigir JPG.',
    find: '"Opcional. Foto em JPG no tamanho normal (até ",Go," MB). Ela é reduzida automaticamente e a localização (GPS) da foto é removida."',
    replace: '"Opcional. Foto em qualquer formato e tamanho. Ela é reduzida automaticamente para caber no espaço e a localização (GPS) da foto é removida."',
  },
  {
    id: 'foto-validacao',
    por_que: 'Não recusa mais foto por ser PNG/WebP/GIF nem por passar de 40 MB: a redução automática cuida disso. Continua recusando arquivo vazio, HEIC (o aparelho não abre) e arquivo que não é imagem.',
    find:
      'async function Km(g){if(g.size===0)return"O arquivo está vazio. Escolha outra foto.";if(g.size>Go*1024*1024)return`Esta foto tem ${pa(g.size)}. O limite é ${Go} MB.`;switch(XB(new Uint8Array(await g.slice(0,16).arrayBuffer()))){case"jpg":return null;case"heic":return"Esta foto está em HEIC, o formato do iPhone. Envie em JPG: no iPhone, em Ajustes › Câmera › Formatos, escolha “Mais Compatível”.";case"png":return"Esta imagem está em PNG. Envie a foto em JPG.";case"webp":return"Esta imagem está em WebP. Envie a foto em JPG.";case"gif":return"Esta imagem está em GIF. Envie a foto em JPG.";default:return"Este arquivo não é uma foto JPG válida. Escolha outra imagem."}}',
    replace:
      'async function Km(g){if(g.size===0)return"O arquivo está vazio. Escolha outra foto.";const t=XB(new Uint8Array(await g.slice(0,16).arrayBuffer()));return t==="heic"?"Esta foto está em HEIC, o formato do iPhone. Envie em JPG: no iPhone, em Ajustes › Câmera › Formatos, escolha “Mais Compatível”.":t==="desconhecido"&&g.type&&!g.type.startsWith("image/")?"Este arquivo não é uma imagem. Escolha uma foto.":null}',
  },
  {
    id: 'galeria-aviso',
    por_que: 'Aviso da galeria, sem o limite de 40 MB e sem exigir JPG.',
    find: '"Fotos do celular em JPG, até ",Go," MB: são reduzidas automaticamente para até "',
    replace: '"Fotos do celular, em qualquer formato e tamanho: são reduzidas automaticamente para até "',
  },
  {
    id: 'anexos-limite',
    por_que: 'Fotos anexadas não são mais recusadas por passar de 50 MB (elas são reduzidas). O limite continua valendo para PDF, Word, Excel e outros.',
    find: 'if(h.size>Af*1024*1024){i.push(`“${I}” tem ${pa(h.size)}. O limite por arquivo é ${Af} MB.`);continue}t?.(',
    replace: 'if(h.size>Af*1024*1024&&!(h.type||"").startsWith("image/")){i.push(`“${I}” tem ${pa(h.size)}. O limite por arquivo é ${Af} MB.`);continue}t?.(',
  },
  {
    id: 'anexos-imagem-gigante',
    por_que: 'Se uma imagem acima de 50 MB não puder ser aberta para reduzir, ela é recusada em vez de ser guardada inteira.',
    find: 'if(!p){await l(h,new Uint8Array(await h.arrayBuffer()),E==="heic"',
    replace: 'if(!p){if(h.size>Af*1024*1024){i.push(`“${I}” tem ${pa(h.size)}. O limite por arquivo é ${Af} MB.`);continue}await l(h,new Uint8Array(await h.arrayBuffer()),E==="heic"',
  },
  {
    id: 'anexos-ajuda',
    por_que: 'Texto de ajuda do campo de documentos: explica que fotos são reduzidas e que o limite de 50 MB vale para os demais arquivos.',
    find: '"PDF (como o PMAP, com várias folhas), fotos, Word, Excel e outros · até ",Af," MB por arquivo."',
    replace: '"PDF (como o PMAP, com várias folhas), fotos, Word, Excel e outros. Fotos de qualquer tamanho são reduzidas automaticamente; os demais arquivos podem ter até ",Af," MB."',
  },
];

// ── execução ────────────────────────────────────────────────────────────────
const sha = (b) => createHash('sha256').update(b).digest('hex');
const conta = (texto, trecho) => texto.split(trecho).length - 1;
const unico = (texto, trecho, rotulo) => {
  const n = conta(texto, trecho);
  if (n !== 1) throw new Error(`[${rotulo}] esperava achar o trecho 1 vez, mas achei ${n}: ${JSON.stringify(trecho.slice(0, 90))}…`);
};

const bufOriginal = execFileSync('unzip', ['-p', ZIP, ENTRADA_NO_ZIP], { maxBuffer: 256 * 1024 * 1024 });
if (sha(bufOriginal) !== SHA256_ORIGINAL) throw new Error('O index.html dentro do zip não é o original esperado (SHA-256 diferente).');
let texto = bufOriginal.toString('utf8');

for (const e of EDICOES) {
  if (e.cut) {
    const [ini, fim] = e.cut;
    unico(texto, ini, e.id + ' (início)');
    unico(texto, fim, e.id + ' (fim)');
    const a = texto.indexOf(ini);
    const b = texto.indexOf(fim);
    if (!(a < b)) throw new Error(`[${e.id}] o fim do trecho vem antes do início.`);
    texto = texto.slice(0, a) + texto.slice(b);
  } else {
    unico(texto, e.find, e.id);
    texto = texto.replace(e.find, () => e.replace);
  }
}
const resultado = Buffer.from(texto, 'utf8');

if (process.argv.includes('--check')) {
  const atual = readFileSync(SAIDA);
  const igual = sha(atual) === sha(resultado);
  console.log(igual ? `OK: ${SAIDA} é exatamente o original + ${EDICOES.length} edições.` : `DIFERENTE: ${SAIDA} não corresponde ao original + edições.`);
  process.exit(igual ? 0 : 1);
}
writeFileSync(SAIDA, resultado);
console.log(`${EDICOES.length} edições aplicadas.\n  original: ${bufOriginal.length} bytes\n  resultado: ${resultado.length} bytes\n  gravado em ${SAIDA}`);
