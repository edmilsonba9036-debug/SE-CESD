package br.org.secesd.app;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Backup no Google Drive via framework (AccountManager + Drive REST v3),
 * sem bibliotecas externas e sem fluxo de navegador.
 *
 * O consentimento do escopo drive.file é desenhado pelo serviço de contas
 * Google (Play Services) do aparelho, que valida pacote + SHA-1.
 */
public class DriveBackup {

    public static final int RC_CONTA = 4204;
    public static final int RC_CONSENTIMENTO = 4205;

    static final String TIPO_CONTA = "com.google";
    static final String ESCOPO = "https://www.googleapis.com/auth/drive.file";
    static final String API = "https://www.googleapis.com/drive/v3/";
    static final String API_UPLOAD = "https://www.googleapis.com/upload/drive/v3/";
    static final String NOME_PASTA = "SE • CESD";
    static final String NOME_ARQUIVO = "SE-CESD-backup.json";

    /** Aviso para a tela e entrega do backup baixado. */
    public interface Ouvinte {
        void status(String mensagem, boolean ok);
        void restaurado(String cofreJson);
    }

    private final Activity atividade;
    private final Ouvinte ouvinte;
    private final ExecutorService fila = Executors.newSingleThreadExecutor();
    private String token;

    public DriveBackup(Activity atividade, Ouvinte ouvinte) {
        this.atividade = atividade;
        this.ouvinte = ouvinte;
    }

    private enum Acao { NENHUMA, CONECTAR, ENVIAR, RESTAURAR }
    private Acao pendente = Acao.NENHUMA;
    private String payloadPendente;

    // ------------------------------------------------------------------
    // Ações
    // ------------------------------------------------------------------

    public void conectar() {
        iniciar(Acao.CONECTAR, null);
    }

    public void enviar(String cofreJson) {
        iniciar(Acao.ENVIAR, cofreJson);
    }

    public void restaurar() {
        iniciar(Acao.RESTAURAR, null);
    }

    /** Chamar a partir de Activity.onActivityResult. Devolve true se era do Drive. */
    public boolean onActivityResult(int requestCode, Intent data) {
        if (requestCode == RC_CONTA) {
            String nome = data != null ? data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) : null;
            if (nome == null) {
                avisar("Nenhuma conta escolhida.", false);
            } else {
                salvarConta(nome);
                continuar();
            }
            return true;
        }
        if (requestCode == RC_CONSENTIMENTO) {
            continuar();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Fluxo
    // ------------------------------------------------------------------

    private synchronized void iniciar(Acao acao, String payload) {
        pendente = acao;
        payloadPendente = payload;
        token = null;
        fila.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (conta() == null) {
                        escolherConta();
                        return;
                    }
                    executar();
                } catch (PrecisaTela e) {
                    avisar("Conclua a autorização no Google para continuar.", true);
                } catch (Exception e) {
                    avisar(erroAmigavel(e), false);
                }
            }
        });
    }

    private void continuar() {
        fila.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    executar();
                } catch (PrecisaTela e) {
                    avisar("Conclua a autorização no Google para continuar.", true);
                } catch (Exception e) {
                    avisar(erroAmigavel(e), false);
                }
            }
        });
    }

    private void executar() throws Exception {
        Acao acao = pendente;
        pendente = Acao.NENHUMA;
        if (acao == Acao.CONECTAR) {
            obterToken();
            avisar("Conectado ao Google Drive ✓", true);
        } else if (acao == Acao.ENVIAR) {
            String payload = payloadPendente;
            payloadPendente = null;
            enviar(payload);
        } else if (acao == Acao.RESTAURAR) {
            baixar();
        }
    }

    private void avisar(final String msg, final boolean ok) {
        atividade.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ouvinte.status(msg, ok);
            }
        });
    }

    // ------------------------------------------------------------------
    // Conta e token
    // ------------------------------------------------------------------

    private Account conta() {
        String nome = atividade.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE)
                .getString("contaNome", null);
        return nome == null ? null : new Account(nome, TIPO_CONTA);
    }

    private void salvarConta(String nome) {
        atividade.getSharedPreferences("drive-backup", Activity.MODE_PRIVATE)
                .edit().putString("contaNome", nome).apply();
    }

    private void escolherConta() throws PrecisaTela {
        atividade.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent seletor = AccountManager.newChooseAccountIntent(
                            null, null, new String[]{TIPO_CONTA}, true, null, null, null, null);
                    atividade.startActivityForResult(seletor, RC_CONTA);
                } catch (Exception e) {
                    avisar("Este aparelho não tem conta Google (Play Services) para o backup.", false);
                }
            }
        });
        throw new PrecisaTela();
    }

    private static class PrecisaTela extends IOException {
        PrecisaTela() { super("precisa-tela"); }
    }

    private String obterToken() throws IOException {
        if (token != null) return token;
        Account conta = conta();
        if (conta == null) {
            escolherConta();
            throw new PrecisaTela();
        }
        AccountManager am = AccountManager.get(atividade);
        Bundle resultado;
        try {
            AccountManagerFuture<Bundle> futuro = am.getAuthToken(
                    conta, "oauth2:" + ESCOPO, new Bundle(), null, null, null);
            resultado = futuro.getResult();
        } catch (android.accounts.OperationCanceledException e) {
            throw new IOException("Você cancelou a autorização do Google.");
        } catch (android.accounts.AuthenticatorException e) {
            throw new IOException("O serviço de contas Google não respondeu. Verifique o Play Services.");
        }
        Intent tela = (Intent) resultado.getParcelable(AccountManager.KEY_INTENT);
        if (tela != null) {
            final Intent i = tela;
            atividade.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    atividade.startActivityForResult(i, RC_CONSENTIMENTO);
                }
            });
            throw new PrecisaTela();
        }
        String t = resultado.getString(AccountManager.KEY_AUTHTOKEN);
        if (t == null || t.isEmpty()) throw new IOException("O Google não devolveu o token de acesso.");
        token = t;
        return token;
    }

    // ------------------------------------------------------------------
    // Enviar / baixar
    // ------------------------------------------------------------------

    /** Ação pendente… */
    private void executarEnvio(String cofreJson) throws Exception {
        avisar("Preparando o backup…", true);
        JSONObject conteudo = new JSONObject();
        conteudo.put("formato", "se-cesd-backup");
        conteudo.put("versao", 1);
        conteudo.put("criadoEm", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));
        conteudo.put("aparelho", android.os.Build.MODEL == null ? "Android" : android.os.Build.MODEL);
        conteudo.put("dados", cofreJson);
        byte[] bytes = conteudo.toString().getBytes(StandardCharsets.UTF_8);

        for (int tentativa = 0; tentativa < 2; tentativa++) {
            try {
                String t = obterToken();
                String pastaId = garantirPasta(t);
                String arquivoId = localizarArquivo(t, pastaId);
                final boolean novo = arquivoId == null;
                avisar(novo ? "Criando o backup no Google Drive…" : "Atualizando o backup no Google Drive…", true);
                HttpURLConnection conn;
                if (novo) {
                    JSONObject meta = new JSONObject();
                    meta.put("name", NOME_ARQUIVO);
                    org.json.JSONArray pais = new org.json.JSONArray();
                    pais.put(pastaId);
                    meta.put("parents", pais);
                    String limite = "secesd" + System.currentTimeMillis();
                    byte[] corpo = multipart(limite, meta.toString(), bytes);
                    conn = abrir(API_UPLOAD + "files?uploadType=multipart&fields=id", t, "POST");
                    conn.setRequestProperty("Content-Type", "multipart/related; boundary=" + limite);
                    escrever(conn, corpo);
                } else {
                    conn = abrir(API_UPLOAD + "files/" + arquivoId + "?uploadType=media&fields=id", t, "PATCH");
                    escrever(conn, bytes);
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                }
                int codigo = conn.getResponseCode();
                String corpo = ler(conn, codigo);
                conn.disconnect();
                if (codigo >= 200 && codigo < 300) {
                    avisar("Backup salvo no Google Drive ✓", true);
                    return;
                }
                if (codigo == 401 && tentativa == 0) { token = null; continue; }
                throw new IOException("HTTP " + codigo + ": " + resumo(corpo));
            } catch (PrecisaTela e) {
                throw e;
            } catch (IOException e) {
                if (tentativa == 0 && String.valueOf(e.getMessage()).contains("401")) { token = null; continue; }
                throw e;
            }
        }
    }

    private void baixar() throws Exception {
        avisar("Procurando o backup no Drive…", true);
        String arquivoId = null;
        for (int tentativa = 0; tentativa < 2 && arquivoId == null; tentativa++) {
            String t = obterToken();
            arquivoId = localizarArquivo(t, localizarPasta(t));
        }
        if (arquivoId == null) {
            avisar("Nenhum backup encontrado neste Drive. Envie um backup primeiro.", false);
            return;
        }
        avisar("Baixando o backup…", true);
        String corpo = null;
        for (int tentativa = 0; tentativa < 2; tentativa++) {
            String t = obterToken();
            HttpURLConnection conn = abrir(API + "files/" + arquivoId + "?alt=media", t, "GET");
            int codigo = conn.getResponseCode();
            corpo = ler(conn, codigo);
            conn.disconnect();
            if (codigo >= 200 && codigo < 300) break;
            if (codigo == 401 && tentativa == 0) { token = null; continue; }
            throw new IOException("HTTP " + codigo + ": " + resumo(corpo));
        }
        JSONObject conteudo = new JSONObject(corpo);
        if (!"se-cesd-backup".equals(conteudo.optString("formato"))) {
            avisar("O arquivo do Drive não é um backup válido do SE • CESD.", false);
            return;
        }
        String dados = conteudo.optString("dados", "");
        if (dados.isEmpty()) {
            avisar("O backup está vazio.", false);
            return;
        }
        final String cofre = dados;
        avisar("Backup de " + conteudo.optString("criadoEm", "?") + " pronto para aplicar.", true);
        atividade.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ouvinte.restaurado(cofre);
            }
        });
    }

    // ------------------------------------------------------------------
    // Pasta / arquivo
    // ------------------------------------------------------------------

    private String garantirPasta(String t) throws Exception {
        String id = localizarPasta(t);
        if (id != null) return id;
        JSONObject meta = new JSONObject();
        meta.put("name", NOME_PASTA);
        meta.put("mimeType", "application/vnd.google-apps.folder");
        HttpURLConnection conn = abrir(API + "files?fields=id", t, "POST");
        escrever(conn, meta.toString().getBytes(StandardCharsets.UTF_8));
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        int codigo = conn.getResponseCode();
        String corpo = ler(conn, codigo);
        conn.disconnect();
        if (codigo < 200 || codigo >= 300) throw new IOException("HTTP " + codigo + " ao criar a pasta.");
        return new JSONObject(corpo).getString("id");
    }

    private String localizarPasta(String t) throws Exception {
        String q = "name='" + NOME_PASTA + "' and mimeType='application/vnd.google-apps.folder' and trashed=false";
        return primeiro(buscar(t, q));
    }

    private String localizarArquivo(String t, String pastaId) throws Exception {
        String q = pastaId != null
                ? "name='" + NOME_ARQUIVO + "' and '" + pastaId + "' in parents and trashed=false"
                : "name='" + NOME_ARQUIVO + "' and trashed=false";
        return primeiro(buscar(t, q));
    }

    private org.json.JSONArray buscar(String t, String q) throws Exception {
        HttpURLConnection conn = abrir(API + "files?q=" + URLEncoder.encode(q, "UTF-8")
                + "&spaces=drive&fields=files(id,name)&pageSize=5", t, "GET");
        int codigo = conn.getResponseCode();
        String corpo = ler(conn, codigo);
        conn.disconnect();
        if (codigo < 200 || codigo >= 300) throw new IOException("HTTP " + codigo + " ao buscar.");
        return new JSONObject(corpo).optJSONArray("files");
    }

    private String primeiro(org.json.JSONArray arquivos) {
        if (arquivos != null && arquivos.length() > 0) {
            return arquivos.optJSONObject(0).optString("id", null);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------

    private HttpURLConnection abrir(String url, String t, String metodo) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(metodo);
        conn.setRequestProperty("Authorization", "Bearer " + t);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(!"GET".equals(metodo));
        return conn;
    }

    private void escrever(HttpURLConnection conn, byte[] bytes) throws IOException {
        conn.setFixedLengthStreamingMode(bytes.length);
        OutputStream saida = conn.getOutputStream();
        saida.write(bytes);
        saida.flush();
        saida.close();
    }

    private byte[] multipart(String limite, String metaJson, byte[] media) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(("--" + limite + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        saida.write(metaJson.getBytes(StandardCharsets.UTF_8));
        saida.write(("\r\n--" + limite + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        saida.write(media);
        saida.write(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return saida.toByteArray();
    }

    private String ler(HttpURLConnection conn, int codigo) throws IOException {
        InputStream fluxo = codigo >= 200 && codigo < 300 ? conn.getInputStream() : conn.getErrorStream();
        if (fluxo == null) return "";
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        byte[] p = new byte[8192];
        int n;
        while ((n = fluxo.read(p)) != -1) saida.write(p, 0, n);
        fluxo.close();
        return saida.toString("UTF-8");
    }

    private String resumo(String corpo) {
        if (corpo == null) return "";
        return corpo.length() > 250 ? corpo.substring(0, 250) + "…" : corpo;
    }

    private String erroAmigavel(Exception e) {
        String m = String.valueOf(e.getMessage());
        if (m.contains("HTTP 403")) return "O Google recusou (403). Confira a Drive API ativa e o escopo drive.file no Console.";
        if (m.contains("HTTP")) return "O Google recusou a operação (" + resumo(m) + ").";
        if (m.contains("precisa-tela")) return "Conclua a autorização no Google para continuar.";
        return m.isEmpty() ? "Sem conexão com o Google Drive." : m;
    }
}
