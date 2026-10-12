/**
 * Cofre criptografado — WebCrypto, 100% compatível com o app nativo:
 * PBKDF2-SHA256 (120.000 iterações) → AES-256-GCM; verificador "se-cesd-aberto".
 */
import {
  CofreJson, Dados, MARCA_VERIFICADOR, ITERACOES, dadosVazio,
} from './tipos';

const CHAVE_ARMAZENADA = 'secesd.cofre';
const PENDENTE = 'secesd.pendente';

let chaveSessao: CryptoKey | null = null;
let usuarioSessao = '';

const enc = new TextEncoder();
const dec = new TextDecoder();

export function bytesParaBase64(bytes: Uint8Array): string {
  let bin = '';
  const passo = 0x8000;
  for (let i = 0; i < bytes.length; i += passo) {
    bin += String.fromCharCode(...bytes.subarray(i, i + passo));
  }
  return btoa(bin);
}

export function base64ParaBytes(b64: string): Uint8Array {
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

function aleatorio(n: number): Uint8Array {
  return crypto.getRandomValues(new Uint8Array(n));
}

async function chave(senha: string, sal: Uint8Array): Promise<CryptoKey> {
  const base = await crypto.subtle.importKey('raw', enc.encode(senha), 'PBKDF2', false, ['deriveKey']);
  const alg: Pbkdf2Params = {
    name: 'PBKDF2',
    salt: sal as unknown as BufferSource,
    iterations: ITERACOES,
    hash: 'SHA-256',
  };
  return crypto.subtle.deriveKey(alg, base, { name: 'AES-GCM', length: 256 }, false, [
    'encrypt',
    'decrypt',
  ]);
}

async function cifrar(chaveAes: CryptoKey, iv: Uint8Array, texto: string): Promise<Uint8Array> {
  const bruto = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: iv as unknown as BufferSource, tagLength: 128 },
    chaveAes,
    enc.encode(texto),
  );
  return new Uint8Array(bruto);
}

async function decifrar(chaveAes: CryptoKey, iv: Uint8Array, blob: Uint8Array): Promise<string> {
  const bruto = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: iv as unknown as BufferSource, tagLength: 128 },
    chaveAes,
    blob as unknown as BufferSource,
  );
  return dec.decode(bruto);
}

async function resumo(texto: string): Promise<string> {
  const h = await crypto.subtle.digest('SHA-256', enc.encode(texto));
  return bytesParaBase64(new Uint8Array(h));
}

function lerCofre(): CofreJson | null {
  const bruto = localStorage.getItem(CHAVE_ARMAZENADA);
  return bruto ? (JSON.parse(bruto) as CofreJson) : null;
}

function gravarCofre(c: CofreJson): void {
  localStorage.setItem(CHAVE_ARMAZENADA, JSON.stringify(c));
}

export function existe(): boolean {
  return localStorage.getItem(CHAVE_ARMAZENADA) !== null;
}

export function usuarioGravado(): string {
  return lerCofre()?.usuario ?? '';
}

export function sessaoAberta(): boolean {
  return chaveSessao !== null;
}

export function bloquear(): void {
  chaveSessao = null;
  usuarioSessao = '';
}

export function marcarPendente(): void {
  localStorage.setItem(PENDENTE, '1');
}

export function pendenteEnviar(): boolean {
  return localStorage.getItem(PENDENTE) === '1';
}

export function limparPendente(): void {
  localStorage.removeItem(PENDENTE);
}

/** Cria o cofre com o primeiro acesso. Devolve erro ou null. */
export async function criar(usuario: string, senha: string): Promise<string | null> {
  const u = usuario.trim();
  if (u.length < 3) return 'O usuário deve ter pelo menos 3 caracteres.';
  if (u.includes(' ')) return 'Não use espaços no usuário.';
  if (!senha || senha.length < 8) return 'Use pelo menos 8 caracteres na senha.';
  if (senha.toLowerCase().includes(u.toLowerCase())) return 'A senha não pode conter o usuário.';
  if (existe()) return 'Já existe um acesso criado neste aparelho.';

  const sal = aleatorio(16);
  const k = await chave(senha, sal);
  const iv = aleatorio(12);
  const verif = await cifrar(k, iv, MARCA_VERIFICADOR);
  const ivDados = aleatorio(12);
  const dados = await cifrar(k, ivDados, JSON.stringify(dadosVazio()));

  const cofre: CofreJson = {
    usuario: u,
    resumoUsuario: await resumo(u + '/' + u),
    sal: bytesParaBase64(sal),
    ivVerif: bytesParaBase64(iv),
    verif: bytesParaBase64(verif),
    ivDados: bytesParaBase64(ivDados),
    dados: bytesParaBase64(dados),
  };
  gravarCofre(cofre);
  chaveSessao = k;
  usuarioSessao = u;
  return null;
}

/** Abre a sessão; devolve null quando ok ou a mensagem de erro. */
export async function abrir(usuario: string, senha: string): Promise<string | null> {
  const c = lerCofre();
  if (!c) return 'Nenhum acesso criado neste aparelho.';
  if (c.usuario !== usuario.trim()) return 'Usuário ou senha incorretos.';
  try {
    const chaveReal = await chave(senha, base64ParaBytes(c.sal));
    const marca = await decifrar(chaveReal, base64ParaBytes(c.ivVerif), base64ParaBytes(c.verif));
    if (marca !== MARCA_VERIFICADOR) return 'Usuário ou senha incorretos.';
    chaveSessao = chaveReal;
    usuarioSessao = c.usuario;
    return null;
  } catch {
    return 'Usuário ou senha incorretos.';
  }
}

export function usuarioAtual(): string {
  return usuarioSessao;
}

/** Lê os registros (decifrados). Chamar com sessão aberta. */
export async function lerDados(): Promise<Dados> {
  const c = lerCofre();
  if (!c || !chaveSessao) throw new Error('Sessão fechada.');
  const texto = await decifrar(chaveSessao, base64ParaBytes(c.ivDados), base64ParaBytes(c.dados));
  const base = dadosVazio();
  const lidos = JSON.parse(texto) as Partial<Dados>;
  return {
    cadastro: lidos.cadastro ?? base.cadastro,
    trajetoria: lidos.trajetoria ?? [],
    galeria: lidos.galeria ?? [],
    documentos: lidos.documentos ?? [],
    memorial: lidos.memorial ?? {},
  };
}

/** Salva os registros cifrando de novo com IV novo. */
export async function salvarDados(dados: Dados): Promise<void> {
  const c = lerCofre();
  if (!c || !chaveSessao) throw new Error('Sessão fechada.');
  const ivDados = aleatorio(12);
  const blob = await cifrar(chaveSessao, ivDados, JSON.stringify(dados));
  c.ivDados = bytesParaBase64(ivDados);
  c.dados = bytesParaBase64(blob);
  gravarCofre(c);
  marcarPendente();
}

/** O cofre completo em JSON (para o backup). */
export function exportar(): string {
  const c = lerCofre();
  if (!c) throw new Error('Nenhum cofre neste aparelho.');
  return JSON.stringify(c);
}

/** Substitui o cofre inteiro (restauração). A sessão é encerrada. */
export function substituir(cofreJson: string): void {
  const novo = JSON.parse(cofreJson) as CofreJson;
  if (!novo.sal || !novo.verif || !novo.dados) throw new Error('Backup inválido.');
  gravarCofre(novo);
  bloquear();
}

export function apagarTudo(): void {
  localStorage.removeItem(CHAVE_ARMAZENADA);
  limparPendente();
  bloquear();
}

/** Troca a senha: prova a atual, re-criptografa tudo e mantém a sessão. */
export async function trocarSenha(usuario: string, senhaAtual: string, novaSenha: string): Promise<string | null> {
  const erro = await abrir(usuario, senhaAtual);
  if (erro) return erro;
  if (!novaSenha || novaSenha.length < 8) return 'Use pelo menos 8 caracteres na nova senha.';
  if (novaSenha.toLowerCase().includes(usuario.toLowerCase())) return 'A nova senha não pode conter o usuário.';
  if (novaSenha === senhaAtual) return 'A nova senha precisa ser diferente da atual.';
  const c = lerCofre();
  if (!c || !chaveSessao) return 'Sessão fechada.';
  const abertos = await decifrar(chaveSessao, base64ParaBytes(c.ivDados), base64ParaBytes(c.dados));

  const novoSal = aleatorio(16);
  const novaChave = await chave(novaSenha, novoSal);
  const novoIvVerif = aleatorio(12);
  const verif = await cifrar(novaChave, novoIvVerif, MARCA_VERIFICADOR);
  const novoIvDados = aleatorio(12);
  const novosDados = await cifrar(novaChave, novoIvDados, abertos);

  c.sal = bytesParaBase64(novoSal);
  c.ivVerif = bytesParaBase64(novoIvVerif);
  c.verif = bytesParaBase64(verif);
  c.ivDados = bytesParaBase64(novoIvDados);
  c.dados = bytesParaBase64(novosDados);
  gravarCofre(c);
  chaveSessao = novaChave;
  marcarPendente();
  return null;
}

/** Muda o nome de usuário (dados e senha intactos). */
export async function mudarUsuario(novoUsuario: string): Promise<string | null> {
  const novo = novoUsuario.trim();
  if (novo.length < 3) return 'O usuário deve ter pelo menos 3 caracteres.';
  if (novo.includes(' ')) return 'Não use espaços no usuário.';
  const c = lerCofre();
  if (!c || !chaveSessao) return 'Sessão fechada.';
  if (novo === c.usuario) return 'Esse já é o seu nome de usuário.';
  c.usuario = novo;
  c.resumoUsuario = await resumo(novo + '/' + novo);
  gravarCofre(c);
  usuarioSessao = novo;
  marcarPendente();
  return null;
}
