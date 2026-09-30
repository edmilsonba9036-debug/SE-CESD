/**
 * Backup no Google Drive pelo NAVEGADOR — OAuth 2.0 + PKCE (sem backend,
 * sem segredo de cliente). Requer um cliente OAuth do tipo APlicativo WEB
 * no Google Cloud, com o redirect deste site cadastrado.
 */
import {
  BackupEnvelope, NOME_ARQUIVO, NOME_PASTA,
} from './tipos';

const AUTORIZAR = 'https://accounts.google.com/o/oauth2/auth';
const TOKEN = 'https://oauth2.googleapis.com/token';
const ESCOPO = 'https://www.googleapis.com/auth/drive.file';
const API = 'https://www.googleapis.com/drive/v3/';
const UPLOAD = 'https://www.googleapis.com/upload/drive/v3/';

const K_CLIENTE = 'secesd.drive.cliente';
const K_REFRESH = 'secesd.drive.refresh';
const K_TOKEN = 'secesd.drive.token';
const K_EXPIRA = 'secesd.drive.expira';
const K_VERIF = 'secesd.drive.verificador';
const K_ESTADO = 'secesd.drive.estado';
const K_PASTA = 'secesd.drive.pastaId';

export function idCliente(): string {
  return localStorage.getItem(K_CLIENTE) ?? '';
}

export function configurarCliente(id: string): void {
  localStorage.setItem(K_CLIENTE, id.trim());
}

export function configurado(): boolean {
  return idCliente() !== '';
}

export function limparConfig(): void {
  [K_CLIENTE, K_REFRESH, K_TOKEN, K_EXPIRA, K_PASTA].forEach((k) => localStorage.removeItem(k));
}

function redirecionamento(): string {
  return location.origin + location.pathname;
}

function b64url(bytes: Uint8Array): string {
  return bytesParaBase64Url(bytes);
}

function bytesParaBase64Url(bytes: Uint8Array): string {
  let bin = '';
  const passo = 0x8000;
  for (let i = 0; i < bytes.length; i += passo) {
    bin += String.fromCharCode(...bytes.subarray(i, i + passo));
  }
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** Inicia a autorização (redireciona para o Google). */
export async function autorizar(): Promise<string | null> {
  const cliente = idCliente();
  if (!cliente) return 'Informe o ID do cliente OAuth (Aplicativo Web) antes de conectar.';
  const aleatorio = crypto.getRandomValues(new Uint8Array(48));
  const verificador = b64url(aleatorio);
  const desafio = bytesParaBase64Url(
    new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verificador))),
  );
  const estado = b64url(crypto.getRandomValues(new Uint8Array(12)));
  localStorage.setItem(K_VERIF, verificador);
  localStorage.setItem(K_ESTADO, estado);
  const url = AUTORIZAR
    + '?client_id=' + encodeURIComponent(cliente)
    + '&redirect_uri=' + encodeURIComponent(redirecionamento())
    + '&response_type=code'
    + '&scope=' + encodeURIComponent(ESCOPO)
    + '&code_challenge=' + encodeURIComponent(desafio)
    + '&code_challenge_method=S256'
    + '&state=' + encodeURIComponent(estado)
    + '&access_type=offline&prompt=consent';
  location.assign(url);
  return null;
}

/** Se o Google voltou com ?code=..., troca pelos tokens. true = concluído. */
export async function concluirRetorno(): Promise<boolean> {
  const params = new URLSearchParams(location.search);
  const codigo = params.get('code');
  const estado = params.get('state');
  if (!codigo || !estado) return false;
  const esperado = localStorage.getItem(K_ESTADO);
  const verificador = localStorage.getItem(K_VERIF);
  history.replaceState(null, '', location.pathname);
  if (!esperado || estado !== esperado || !verificador) return false;
  const resposta = await fetch(TOKEN, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      code: codigo,
      client_id: idCliente(),
      redirect_uri: redirecionamento(),
      grant_type: 'authorization_code',
      code_verifier: verificador,
    }).toString(),
  });
  if (!resposta.ok) throw new Error('O Google recusou a troca de tokens (HTTP ' + resposta.status + ').');
  const tokens = (await resposta.json()) as { access_token: string; expires_in: number; refresh_token?: string };
  localStorage.setItem(K_TOKEN, tokens.access_token);
  localStorage.setItem(K_EXPIRA, String(Date.now() + (tokens.expires_in - 120) * 1000));
  if (tokens.refresh_token) localStorage.setItem(K_REFRESH, tokens.refresh_token);
  return true;
}

async function obterToken(): Promise<string> {
  const token = localStorage.getItem(K_TOKEN);
  const expira = Number(localStorage.getItem(K_EXPIRA) ?? '0');
  if (token && Date.now() < expira) return token;
  const renovacao = localStorage.getItem(K_REFRESH);
  if (!renovacao) throw new Error('Não conectado — toque em Conectar ao Drive.');
  const resposta = await fetch(TOKEN, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: idCliente(),
      refresh_token: renovacao,
      grant_type: 'refresh_token',
    }).toString(),
  });
  if (!resposta.ok) throw new Error('Falha ao renovar a autorização do Google (HTTP ' + resposta.status + '). Conecte de novo.');
  const tokens = (await resposta.json()) as { access_token: string; expires_in: number };
  localStorage.setItem(K_TOKEN, tokens.access_token);
  localStorage.setItem(K_EXPIRA, String(Date.now() + (tokens.expires_in - 120) * 1000));
  return tokens.access_token;
}

async function buscar(t: string, q: string): Promise<{ id: string; name: string }[]> {
  const resposta = await fetch(
    API + 'files?q=' + encodeURIComponent(q) + '&spaces=drive&fields=files(id,name)&pageSize=5',
    { headers: { Authorization: 'Bearer ' + t } },
  );
  if (!resposta.ok) throw new Error('O Google recusou a busca (HTTP ' + resposta.status + ').');
  const corpo = (await resposta.json()) as { files?: { id: string; name: string }[] };
  return corpo.files ?? [];
}

async function garantirPasta(t: string): Promise<string> {
  const salvo = localStorage.getItem(K_PASTA);
  if (salvo) return salvo;
  const achou = await buscar(t, "name='" + NOME_PASTA + "' and mimeType='application/vnd.google-apps.folder' and trashed=false");
  let id = achou[0]?.id ?? '';
  if (!id) {
    const resposta = await fetch(API + 'files?fields=id', {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + t, 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: NOME_PASTA, mimeType: 'application/vnd.google-apps.folder' }),
    });
    if (!resposta.ok) throw new Error('HTTP ' + resposta.status + ' ao criar a pasta.');
    id = ((await resposta.json()) as { id: string }).id;
  }
  localStorage.setItem(K_PASTA, id);
  return id;
}

export function aparelho(): string {
  const ua = navigator.userAgent;
  const m = ua.match(/Android[^;)]*|iPhone[^;)]*|iPad[^;)]*|Linux[^;)]*|Mac OS X[^;)]*|Windows NT[^;)]*/);
  return m ? m[0] : 'Navegador';
}

/** Envia o backup (cria ou atualiza o mesmo arquivo na pasta). */
export async function enviar(cofreJson: string, status: (msg: string) => void): Promise<void> {
  const envelope: BackupEnvelope = {
    formato: 'se-cesd-backup',
    versao: 1,
    criadoEm: new Date().toISOString().slice(0, 19),
    aparelho: aparelho(),
    dados: cofreJson,
  };
  const bytes = new TextEncoder().encode(JSON.stringify(envelope));
  status('Preparando o backup…');
  const t = await obterToken();
  const pastaId = await garantirPasta(t);
  const achou = await buscar(t, "name='" + NOME_ARQUIVO + "' and '" + pastaId + "' in parents and trashed=false");
  status(achou.length ? 'Atualizando o backup no Google Drive…' : 'Criando o backup no Google Drive…');

  let resposta: Response;
  if (achou.length === 0) {
    const limite = 'secesd' + Date.now();
    const meta = JSON.stringify({ name: NOME_ARQUIVO, parents: [pastaId] });
    const partes: BlobPart[] = [
      '--' + limite + '\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n' + meta,
      '\r\n--' + limite + '\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n',
      bytes as unknown as BlobPart,
      '\r\n--' + limite + '--\r\n',
    ];
    resposta = await fetch(UPLOAD + 'files?uploadType=multipart&fields=id', {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + t, 'Content-Type': 'multipart/related; boundary=' + limite },
      body: new Blob(partes, { type: 'multipart/related; boundary=' + limite }),
    });
  } else {
    resposta = await fetch(UPLOAD + 'files/' + achou[0].id + '?uploadType=media&fields=id', {
      method: 'PATCH',
      headers: { Authorization: 'Bearer ' + t, 'Content-Type': 'application/json; charset=UTF-8' },
      body: bytes as unknown as BodyInit,
    });
  }
  if (!resposta.ok) throw new Error('HTTP ' + resposta.status + ' ao enviar o backup.');
}

/** Baixa o backup do Drive e devolve o cofre (texto). null = não há backup. */
export async function baixar(status: (msg: string) => void): Promise<string | null> {
  status('Procurando o backup no Drive…');
  const t = await obterToken();
  const pastaId = await garantirPasta(t);
  let achou = await buscar(t, "name='" + NOME_ARQUIVO + "' and '" + pastaId + "' in parents and trashed=false");
  if (achou.length === 0) achou = await buscar(t, "name='" + NOME_ARQUIVO + "' and trashed=false");
  if (achou.length === 0) return null;
  status('Baixando o backup…');
  const resposta = await fetch(API + 'files/' + achou[0].id + '?alt=media', {
    headers: { Authorization: 'Bearer ' + t },
  });
  if (!resposta.ok) throw new Error('HTTP ' + resposta.status + ' ao baixar o backup.');
  const corpo = JSON.stringify(await resposta.json());
  return corpo;
}

/** Confere o envelope e devolve o cofre interno (texto). */
export function cofreDoEnvelope(corpo: string): string {
  const e = JSON.parse(corpo) as BackupEnvelope;
  if (e.formato !== 'se-cesd-backup') throw new Error('Este arquivo não é um backup do SE • CESD.');
  return e.dados;
}
