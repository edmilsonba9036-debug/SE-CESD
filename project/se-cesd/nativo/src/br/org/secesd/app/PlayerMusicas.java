package br.org.secesd.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;

/**
 * Player das canções militares: toca direto da internet (streaming),
 * uma após a outra, sem guardar nada no aparelho. Se uma fonte falhar,
 * passa automaticamente para a próxima canção.
 */
public final class PlayerMusicas {

    private static final String[] TITULOS = {
            "Hino da Aviação — Banda da Marinha",
            "Canção do Expedicionário — Banda da Marinha",
            "Cisne Branco — Banda da Marinha",
            "Soldados da Liberdade — Banda da Marinha",
            "Hino Nacional Brasileiro — U.S. Navy Band",
            "Hino à Bandeira — Fuzileiros Navais",
            "Hino da Independência — Fuzileiros Navais"
    };

    private static final String[] URLS = {
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

    private PlayerMusicas() {}

    public static synchronized boolean tocando() {
        return tocando || carregando;
    }

    public static synchronized String tituloAtual() {
        return TITULOS[indice];
    }

    /** Toca/para. Devolve o novo estado (true = tocando). */
    public static synchronized boolean alternar(final Context ctx) {
        if (tocando || carregando) {
            parar();
            falhasSeguidas = 0;
            return false;
        }
        tocar(ctx, indice);
        return true;
    }

    /** Troca para a próxima canção (se estiver tocando, reinicia na nova). */
    public static synchronized void proxima(final Context ctx) {
        int alvo = (indice + 1) % TITULOS.length;
        if (tocando || carregando) {
            tocar(ctx, alvo);
        } else {
            indice = alvo;
        }
    }

    public static synchronized void parar() {
        tocando = false;
        carregando = false;
        liberarPlayer();
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
            mp.setDataSource(ctx, Uri.parse(URLS[indice]));
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
                    tocar(ctx, (indice + 1) % TITULOS.length);
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer p, int what, int extra) {
                    if (p != player) return true;
                    tentarSeguinte(ctx);
                    return true;
                }
            });
            mp.prepareAsync();
        } catch (Exception e) {
            tentarSeguinte(ctx);
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
