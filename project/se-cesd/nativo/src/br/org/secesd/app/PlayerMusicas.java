package br.org.secesd.app;

/**
 * PLAYER de canções militares (estático, sem tela própria).
 * Histórico: v1.10.0 nasceu com 7 faixas; v1.10.2 busca em tempo real
 * (Archive + Commons); v1.10.3 banco curado na frente das descobertas;
 * v1.10.6 escolher faixa (tocarIndice); v1.10.11 integra ao
 * salvamento/backup automático.
 */
import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Player das canções militares com BUSCA EM TEMPO REAL: a cada toque,
 * o app pergunta à internet (Internet Archive e Wikimedia Commons)
 * quais são as canções militares disponíveis naquele momento — de
 * preferência brasileiras, das Forças Armadas — e monta o repertório
 * na hora, ordenado pelas mais populares. Se a busca falhar, toca a
 * lista interna de segurança. Nada fica programado: cada toque é uma
 * busca nova.
 */
public final class PlayerMusicas {

    private static final String[] TERMOS = {
            "hino militar brasileiro",
            "banda de musica militar hino",
            "dobrado militar banda",
            "hino marinha exercito aeronautica",
            "canção militar brasileira",
            "ardor do infante"
    };

    /** Palavras-chave que confirmam que o resultado é mesmo canção militar. */
    private static final String[] PALAVRAS = {
            "hino", "canção", "cancao", "dobrado", "banda", "militar", "marinha",
            "exército", "exercito", "aeronáutica", "aeronautica", "fuzileiros",
            "bandeira", "independência", "independencia", "proclamação",
            "proclamacao", "expedicionário", "expedicionario", "aviação",
            "aviacao", "cisne", "viracopos", "soldado", "herói", "heroi",
            "farda", "céu", "ceu", "sargento", "escola naval", "feb"
    };

    /** BANCO CURADO como base (sempre toca) + descobertas ao vivo. */
    private static volatile String[] TITULOS = BancoMusicas.titulos();
    private static volatile String[] URLS = BancoMusicas.urls();

    /* (lista antiga mantida em comentario para referencia historica)
    private static volatile String[] TITULOS_ANTIGA = {
            "Hino da Aviação — Banda da Marinha",
            "Canção do Expedicionário (FEB) — Banda da Marinha",
            "Cisne Branco — Banda da Marinha",
            "Soldados da Liberdade — Banda da Marinha",
            "Hino Nacional Brasileiro — U.S. Navy Band",
            "Hino à Bandeira — Fuzileiros Navais",
            "Hino da Independência — Fuzileiros Navais"
    };
    */

    private static volatile String[] URLS_ANTIGA = {
            "https://www.marinha.mil.br/sites/www.marinha.mil.br.en/files/upload/Hino%20da%20Avia%C3%A7%C3%A3o.mp3",
            "https://www.marinha.mil.br/sites/www.marinha.mil.br.en/files/upload/Can%C3%A7%C3%A3o%20Expedicion%C3%A1rio.mp3",
            "https://www.marinha.mil.br/sites/www.marinha.mil.br.en/files/upload/Cisne%20Branco.mp3",
            "https://www.marinha.mil.br/sites/www.marinha.mil.br.en/files/upload/Soldados%20da%20Liberdade.mp3",
            "https://upload.wikimedia.org/wikipedia/commons/9/9b/Hino_Nacional_Brasileiro_instrumental.ogg",
            "https://archive.org/download/lp_hinario-nacional_banda-sinfonica-do-corpo-de-fuzileiros/disc1/01.04.%20Hino%20%C3%80%20Bandeira.mp3",
            "https://archive.org/download/lp_hinario-nacional_banda-sinfonica-do-corpo-de-fuzileiros/disc1/01.02.%20Hino%20Da%20Independ%C3%AAncia.mp3"
    };

    private static MediaPlayer player;
    private static int indice = 0;
    private static boolean tocando = false;
    private static boolean carregando = false;
    private static int falhasSeguidas = 0;
    private static int contadorBuscas = 0;
    private static volatile boolean buscaOk = false;   // última busca achou na internet
    private static volatile String termoUsado = "";

    private PlayerMusicas() {}

    public static synchronized boolean tocando() {
        return tocando || carregando;
    }

    public static synchronized String tituloAtual() {
        return TITULOS[indice % TITULOS.length];
    }

    /** URL da canção atual (para o botão de baixar). */
    public static synchronized String urlAtual() {
        return URLS[indice % URLS.length];
    }

    /** Índice da canção atual. */
    public static synchronized int indiceAtual() {
        return indice % TITULOS.length;
    }

    /** Título da canção na posição i do repertório. */
    public static synchronized String tituloEm(int i) {
        return TITULOS[i % TITULOS.length];
    }

    /** URL da canção na posição i do repertório. */
    public static synchronized String urlEm(int i) {
        return URLS[i % URLS.length];
    }

    /** Cópia da lista de títulos do repertório atual. */
    public static synchronized String[] listaTitulos() {
        return TITULOS.clone();
    }

    public static synchronized int total() {
        return TITULOS.length;
    }

    public static synchronized int totalBanco() {
        return BancoMusicas.total();
    }

    /** A última montagem de repertório veio da busca em tempo real? */
    public static synchronized boolean buscaEmTempoRealOk() {
        return buscaOk;
    }

    public static synchronized String termoBuscado() {
        return termoUsado;
    }

    /** Toca/para. Devolve o novo estado (true = tocando). */
    public static synchronized boolean alternar(final Context ctx) {
        if (tocando || carregando) {
            parar();
            falhasSeguidas = 0;
            return false;
        }
        carregando = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                buscarEmTempoReal();
                synchronized (PlayerMusicas.class) {
                    tocar(ctx.getApplicationContext(), 0);
                }
            }
        }).start();
        return true;
    }

    /** Troca para a próxima canção (com busca nova em tempo real). */
    public static synchronized void proxima(final Context ctx) {
        if (tocando || carregando) {
            carregando = true;
            final int alvo = (indice + 1);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    buscarEmTempoReal();
                    synchronized (PlayerMusicas.class) {
                        tocar(ctx.getApplicationContext(), alvo % TITULOS.length);
                    }
                }
            }).start();
        } else {
            indice = (indice + 1) % TITULOS.length;
        }
    }

    /** Toca a canção ESCOLHIDA pelo usuário no repertório atual (sem nova busca). */
    public static synchronized void tocarIndice(final Context ctx, final int i) {
        carregando = true;
        falhasSeguidas = 0;
        final int alvo = i % TITULOS.length;
        indice = alvo;
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (PlayerMusicas.class) {
                    tocar(ctx.getApplicationContext(), alvo);
                }
            }
        }).start();
    }

    public static synchronized void parar() {
        tocando = false;
        carregando = false;
        liberarPlayer();
    }

    // ------------------------------------------------------------------
    // BUSCA EM TEMPO REAL
    // ------------------------------------------------------------------

    /** BANCO curado na frente; descobertas da busca ao vivo entram no fim. */
    private static void buscarEmTempoReal() {
        String termo = TERMOS[contadorBuscas++ % TERMOS.length];
        ArrayList<String> titulos = new ArrayList<String>();
        ArrayList<String> urls = new ArrayList<String>();
        HashSet<String> usadas = new HashSet<String>();

        buscarNoArchive(termo, titulos, urls, usadas);
        if (titulos.size() < 8) buscarNoCommons(termo, titulos, urls, usadas);

        if (!titulos.isEmpty()) {
            // BANCO primeiro (sem repetir), depois as descobertas
            String[] baseT = BancoMusicas.titulos();
            String[] baseU = BancoMusicas.urls();
            for (int i = 0; i < baseT.length; i++) usadas.add(baseU[i]);
            ArrayList<String> finaisT = new ArrayList<String>();
            ArrayList<String> finaisU = new ArrayList<String>();
            for (int i = 0; i < baseT.length; i++) {
                finaisT.add(baseT[i]);
                finaisU.add(baseU[i]);
            }
            for (int i = 0; i < titulos.size() && finaisT.size() < BancoMusicas.total() + 8; i++) {
                if (usadas.contains(urls.get(i))) continue;
                usadas.add(urls.get(i));
                finaisT.add(titulos.get(i));
                finaisU.add(urls.get(i));
            }
            TITULOS = finaisT.toArray(new String[0]);
            URLS = finaisU.toArray(new String[0]);
            buscaOk = true;
            termoUsado = termo;
        } else {
            TITULOS = BancoMusicas.titulos();
            URLS = BancoMusicas.urls();
            buscaOk = false;
        }
    }

    /** Internet Archive: as gravações mais baixadas que casam com o termo. */
    private static void buscarNoArchive(String termo, ArrayList<String> titulos,
                                        ArrayList<String> urls, HashSet<String> usadas) {
        try {
            String q = URLEncoder.encode(termo + " AND mediatype:audio", "UTF-8");
            String u = "https://archive.org/advancedsearch.php?q=" + q
                    + "&fl%5B%5D=identifier&fl%5B%5D=title&rows=25"
                    + "&sort%5B%5D=downloads+desc&output=json";
            String corpo = baixar(u, 6000, 8000);
            JSONArray docs = new JSONObject(corpo)
                    .getJSONObject("response").optJSONArray("docs");
            if (docs == null) return;
            int achados = 0;
            for (int i = 0; i < docs.length() && achados < 6; i++) {
                JSONObject doc = docs.optJSONObject(i);
                if (doc == null) continue;
                String id = doc.optString("identifier", "");
                String titulo = doc.optString("title", "");
                if (id.isEmpty()) continue;
                if (!pareceMilitar(titulo)) continue;
                String audio = primeiroAudioDoItem(id);
                if (audio == null || usadas.contains(audio)) continue;
                usadas.add(audio);
                titulos.add(resumir(titulo) + " · Archive.org");
                urls.add(audio);
                achados++;
            }
        } catch (Exception ignorado) {
        }
    }

    /** Wikimedia Commons: áudios (ogg) que casam com o termo. */
    private static void buscarNoCommons(String termo, ArrayList<String> titulos,
                                        ArrayList<String> urls, HashSet<String> usadas) {
        try {
            String q = URLEncoder.encode(termo + " filemime:audio/ogg", "UTF-8");
            String u = "https://commons.wikimedia.org/w/api.php?action=query&generator=search"
                    + "&gsrsearch=" + q + "&gsrnamespace=6&gsrlimit=15"
                    + "&prop=imageinfo&iiprop=url&format=json";
            String corpo = baixar(u, 5000, 7000);
            JSONObject paginas = new JSONObject(corpo).getJSONObject("query")
                    .optJSONObject("pages");
            if (paginas == null) return;
            JSONArray chaves = paginas.names();
            if (chaves == null) return;
            int achados = 0;
            for (int i = 0; i < chaves.length() && achados < 4; i++) {
                JSONObject pg = paginas.optJSONObject(chaves.getString(i));
                if (pg == null) continue;
                String titulo = pg.optString("title", "");
                JSONArray infos = pg.optJSONArray("imageinfo");
                if (infos == null || infos.length() == 0) continue;
                String url = infos.optJSONObject(0).optString("url", "");
                if (url.isEmpty() || usadas.contains(url)) continue;
                if (!pareceMilitar(titulo)) continue;
                usadas.add(url);
                titulos.add(resumir(titulo.replaceAll("^File:", "")) + " · Commons");
                urls.add(url);
                achados++;
            }
        } catch (Exception ignorado) {
        }
    }

    /** Pega o primeiro MP3/OGG de um item do Archive. */
    private static String primeiroAudioDoItem(String id) {
        try {
            String corpo = baixar("https://archive.org/metadata/" + URLEncoder.encode(id, "UTF-8"),
                    5000, 7000);
            JSONArray arquivos = new JSONObject(corpo).optJSONArray("files");
            if (arquivos == null) return null;
            String reserva = null;
            for (int i = 0; i < arquivos.length(); i++) {
                JSONObject f = arquivos.optJSONObject(i);
                if (f == null) continue;
                String nome = f.optString("name", "");
                String formato = f.optString("format", "").toLowerCase();
                String baixo = nome.toLowerCase();
                boolean audio = formato.contains("mp3") || formato.contains("vorbis")
                        || baixo.endsWith(".mp3") || baixo.endsWith(".ogg") || baixo.endsWith(".oga");
                if (!audio) continue;
                if (formato.contains("peaks") || formato.contains("spectrogram")) continue;
                String url = "https://archive.org/download/" + id + "/"
                        + URLEncoder.encode(nome, "UTF-8").replace("+", "%20");
                if (formato.contains("mp3") || baixo.endsWith(".mp3")) return url;
                if (reserva == null) reserva = url;
            }
            return reserva;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean pareceMilitar(String t) {
        String s = t.toLowerCase();
        for (String p : PALAVRAS) {
            if (s.contains(p)) return true;
        }
        return false;
    }

    private static String resumir(String s) {
        if (s.length() > 58) return s.substring(0, 58) + "…";
        return s;
    }

    /** Baixa binária (para salvar a canção no Drive). Só 2xx devolve bytes. */
    public static byte[] baixarBytes(String url, int tempoConexao, int tempoLeitura) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(tempoConexao);
        c.setReadTimeout(tempoLeitura);
        c.setRequestProperty("Accept", "audio/*;q=0.9,*/*;q=0.5");
        int codigo = c.getResponseCode();
        if (codigo < 200 || codigo >= 300) {
            c.disconnect();
            throw new Exception("HTTP " + codigo);
        }
        InputStream f = c.getInputStream();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        byte[] p = new byte[8192];
        int n;
        while ((n = f.read(p)) != -1) saida.write(p, 0, n);
        f.close();
        c.disconnect();
        return saida.toByteArray();
    }

    private static String baixar(String url, int tempoConexao, int tempoLeitura) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(tempoConexao);
        c.setReadTimeout(tempoLeitura);
        c.setRequestProperty("Accept", "application/json,audio/*;q=0.9,*/*;q=0.5");
        int codigo = c.getResponseCode();
        InputStream f = codigo >= 200 && codigo < 300 ? c.getInputStream() : c.getErrorStream();
        if (f == null) throw new Exception("HTTP " + codigo);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        byte[] p = new byte[4096];
        int n;
        while ((n = f.read(p)) != -1) saida.write(p, 0, n);
        f.close();
        c.disconnect();
        return saida.toString("UTF-8");
    }

    // ------------------------------------------------------------------
    // REPRODUÇÃO
    // ------------------------------------------------------------------

    private static void liberarPlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignorado) { }
            try { player.release(); } catch (Exception ignorado) { }
            player = null;
        }
    }

    private static void tocar(final Context ctx, final int deOnde) {
        liberarPlayer();
        tocando = false;
        carregando = true;
        indice = deOnde;
        final MediaPlayer mp = new MediaPlayer();
        player = mp;
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setDataSource(ctx, Uri.parse(URLS[indice % URLS.length]));
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer p) {
                    if (p != player) return;
                    carregando = false;
                    tocando = true;
                    falhasSeguidas = 0;
                    p.start();
                }
            });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer p) {
                    if (p != player) return;
                    final Context aplicacao = ctx.getApplicationContext();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (PlayerMusicas.class) {
                                tocar(aplicacao, (indice + 1) % TITULOS.length);
                            }
                        }
                    }).start();
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer p, int what, int extra) {
                    if (p != player) return true;
                    tentarSeguinte(ctx.getApplicationContext());
                    return true;
                }
            });
            mp.prepareAsync();
        } catch (Exception e) {
            tentarSeguinte(ctx.getApplicationContext());
        }
    }

    /** Fonte ruim: passa para a próxima; se todas falharem em sequência, para. */
    private static void tentarSeguinte(final Context ctx) {
        boolean deviaTocar = tocando || carregando;
        liberarPlayer();
        tocando = false;
        carregando = false;
        if (!deviaTocar) return;
        falhasSeguidas++;
        if (falhasSeguidas >= TITULOS.length) {
            falhasSeguidas = 0;
            return;
        }
        tocar(ctx, (indice + 1) % TITULOS.length);
    }
}
