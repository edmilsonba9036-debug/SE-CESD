package br.org.secesd.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Player das canções militares: toca direto da internet (streaming),
 * uma após a outra, sem guardar nada no aparelho.
 *
 * A cada toque, o app BUSCA NA INTERNET o repertório atual (catálogo
 * publicado no repositório do projeto, com canções das Forças Armadas
 * brasileiras). Se a busca falhar, usa a lista interna de segurança.
 * Fontes ruins são puladas automaticamente.
 */
public final class PlayerMusicas {

    private static final String CATALOGO_URL =
            "https://raw.githubusercontent.com/edmilsonba9036-debug/SE-CESD"
          + "/arena/01a0ee2d-se-cesd/project/se-cesd/catalogo-musicas.json";

    /** Lista interna de segurança (se a busca do catálogo falhar). */
    private static volatile String[] TITULOS = {
            "Hino da Aviação — Banda da Marinha",
            "Canção do Expedicionário (FEB) — Banda da Marinha",
            "Cisne Branco — Banda da Marinha",
            "Soldados da Liberdade — Banda da Marinha",
            "Hino Nacional Brasileiro — U.S. Navy Band",
            "Hino à Bandeira — Fuzileiros Navais",
            "Hino da Independência — Fuzileiros Navais"
    };

    private static volatile String[] URLS = {
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
    private static long catalogoEm = 0;   // quando o catálogo veio da internet
    private static int catalogoTotal = 0; // canções recebidas da internet

    private PlayerMusicas() {}

    public static synchronized boolean tocando() {
        return tocando || carregando;
    }

    public static synchronized String tituloAtual() {
        return TITULOS[indice % TITULOS.length];
    }

    public static synchronized int total() {
        return TITULOS.length;
    }

    /** O repertório atual veio da internet? */
    public static synchronized boolean catalogoDaInternet() {
        return catalogoEm > 0;
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
                atualizarCatalogo();
                synchronized (PlayerMusicas.class) {
                    tocar(ctx.getApplicationContext(), indice % TITULOS.length);
                }
            }
        }).start();
        return true;
    }

    /** Troca para a próxima canção (buscando o repertório de novo). */
    public static synchronized void proxima(final Context ctx) {
        if (tocando || carregando) {
            carregando = true;
            final int alvo = (indice + 1);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    atualizarCatalogo();
                    synchronized (PlayerMusicas.class) {
                        tocar(ctx.getApplicationContext(), alvo % TITULOS.length);
                    }
                }
            }).start();
        } else {
            indice = (indice + 1) % TITULOS.length;
        }
    }

    public static synchronized void parar() {
        tocando = false;
        carregando = false;
        liberarPlayer();
    }

    /** Busca na internet o repertório atual (silencioso em caso de falha). */
    private static void atualizarCatalogo() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(CATALOGO_URL).openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(6000);
            c.setRequestProperty("Accept", "application/json");
            int codigo = c.getResponseCode();
            InputStream f = codigo >= 200 && codigo < 300 ? c.getInputStream() : c.getErrorStream();
            if (f == null) return;
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            byte[] p = new byte[4096];
            int n;
            while ((n = f.read(p)) != -1) saida.write(p, 0, n);
            f.close();
            c.disconnect();
            JSONObject raiz = new JSONObject(saida.toString("UTF-8"));
            org.json.JSONArray lista = raiz.optJSONArray("musicas");
            if (lista == null || lista.length() == 0) return;
            java.util.ArrayList<String> titulos = new java.util.ArrayList<String>();
            java.util.ArrayList<String> urls = new java.util.ArrayList<String>();
            for (int i = 0; i < lista.length(); i++) {
                JSONObject m = lista.optJSONObject(i);
                if (m == null) continue;
                String t = m.optString("titulo", "").trim();
                String u = m.optString("url", "").trim();
                String o = m.optString("origem", "").trim();
                if (t.isEmpty() || u.isEmpty() || !u.startsWith("http")) continue;
                titulos.add(t + (o.isEmpty() ? "" : " — " + o));
                urls.add(u);
            }
            if (titulos.isEmpty()) return;
            TITULOS = titulos.toArray(new String[0]);
            URLS = urls.toArray(new String[0]);
            catalogoEm = System.currentTimeMillis();
            catalogoTotal = TITULOS.length;
        } catch (Exception ignorado) {
            // sem internet ou catálogo fora do ar: fica a lista interna
        }
    }

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
