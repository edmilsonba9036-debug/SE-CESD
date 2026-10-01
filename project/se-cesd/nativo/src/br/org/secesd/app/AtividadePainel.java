package br.org.secesd.app;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Painel principal (v1.3): herói em gradiente + menu com emblemas
 * coloridos por área.
 */
public class AtividadePainel extends AtividadeBase {

    private View farol;
    private android.widget.Button botaoMusica;
    private TextView textoMusica;
    private final android.os.Handler relogioMusica = new android.os.Handler();


    private String rotuloMusica() {
        if (!PlayerMusicas.tocando()) return "▶  Tocar canções militares";
        return "⏹  Parar a música";
    }

    private void atualizarMusica() {
        if (botaoMusica == null || textoMusica == null) return;
        botaoMusica.setText(rotuloMusica());
        if (PlayerMusicas.tocando()) {
            textoMusica.setText("♪  " + PlayerMusicas.tituloAtual());
        } else {
            textoMusica.setText("(parado)");
        }
    }

    /** Verde: automatico LIGADO + conta conectada + sem pausa. Vermelho: o contrario. */
    private int corFarol() {
        return farolSaudavel() ? 0xFF2E9E5B : 0xFFC0392B;
    }

    private boolean farolSaudavel() {
        android.content.SharedPreferences p = getSharedPreferences("drive-backup", MODE_PRIVATE);
        return DriveBackup.autoAtivo(this)
                && p.getString("contaNome", null) != null
                && System.currentTimeMillis() >= p.getLong("autoPausaAte", 0);
    }

    private String textoFarol() {
        if (farolSaudavel()) return "Backup automático funcionando ✓";
        android.content.SharedPreferences p = getSharedPreferences("drive-backup", MODE_PRIVATE);
        if (!DriveBackup.autoAtivo(this)) return "Backup automático desligado — ligue na tela Backup.";
        if (p.getString("contaNome", null) == null) return "Backup sem conta Google — conecte na tela Backup.";
        return "Backup pausado — toque em Reparar na tela Backup.";
    }

    private void recolorirFarol() {
        if (farol == null) return;
        android.graphics.drawable.GradientDrawable bolinha =
                new android.graphics.drawable.GradientDrawable();
        bolinha.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bolinha.setColor(corFarol());
        bolinha.setStroke(px(2), 0xFFFFFFFF);
        farol.setBackground(bolinha);
    }

    @Override
    protected void onResume() {
        super.onResume();
        recolorirFarol();
        atualizarMusica();
        relogioMusica.postDelayed(tiqueMusica, 800);
    }

    private final Runnable tiqueMusica = new Runnable() {
        @Override
        public void run() {
            atualizarMusica();
            if (isFinishing()) return;
            relogioMusica.postDelayed(this, 800);
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        relogioMusica.removeCallbacks(tiqueMusica);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        relogioMusica.removeCallbacks(tiqueMusica);
        PlayerMusicas.parar();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Cofre.sessaoAberta()) {
            ir(AtividadeAcesso.class);
            finish();
            return;
        }
        ScrollView rolagem = tela("Painel do Soldado",
                "Sua história militar registrada: do concurso do CESD à passagem pelo Exército — tudo criptografado neste aparelho.");
        LinearLayout coluna = coluna(rolagem);

        LinearLayout cartao = cartao();
        TextView bemVindo = new TextView(this);
        bemVindo.setText("Bem-vindo, " + Cofre.usuarioSessao + "!");
        bemVindo.setGravity(android.view.Gravity.CENTER);
        bemVindo.setTextColor(AZUL_MARINHA);
        bemVindo.setTextSize(16);
        bemVindo.setTypeface(Typeface.DEFAULT_BOLD);
        cartao.addView(bemVindo);

        // BRASA.O DO CESD abaixo da saudacao
        android.widget.ImageView brasao = new android.widget.ImageView(this);
        brasao.setImageResource(br.org.secesd.debug.R.drawable.brasao_cesd);
        android.widget.LinearLayout.LayoutParams lpBrasao = new android.widget.LinearLayout.LayoutParams(px(140), px(140));
        lpBrasao.topMargin = px(10);
        lpBrasao.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        cartao.addView(brasao, lpBrasao);
        coluna.addView(cartao, largura());

        coluna.addView(titulo("Minha história"));
        menu(coluna, "MV", COR_FASE1, "Memorial da minha vida",
                "Fatos e acontecimentos: antes e durante o concurso, após formado, "
                        + "até a baixa — e a passagem pelo Exército",
                AtividadeMemorialVida.class);
        menu(coluna, "TR", COR_FASE3, "Minha trajetória",
                "Etapas da caminhada na Força Aérea, em ordem de data", AtividadeTrajetoria.class);
        menu(coluna, "CD", AZUL_MEDIO, "Meu cadastro",
                "Fotografia e dados do Soldado Especializado", AtividadeCadastro.class);
        menu(coluna, "GA", OURO, "Galeria",
                "4 locais para as fotografias da sua história", AtividadeGaleria.class);
        menu(coluna, "DC", COR_FASE2, "Documentos",
                "Seus documentos (PDF e imagens), criptografados no cofre", AtividadeDocumentos.class);

        coluna.addView(titulo("Institucional"));
        menu(coluna, "BK", COR_EXERCITO, "Backup no Google Drive",
                "Automático, criptografado, no seu Drive", AtividadeBackup.class);

        // CANCOES MILITARES — toca direto da internet
        coluna.addView(titulo("Canções militares"));
        LinearLayout cartaoMusica = cartao();
        botaoMusica = botao(rotuloMusica(), true);
        botaoMusica.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlayerMusicas.alternar(AtividadePainel.this);
                atualizarMusica();
            }
        });
        cartaoMusica.addView(botaoMusica, largura());
        textoMusica = texto("");
        textoMusica.setGravity(android.view.Gravity.CENTER);
        textoMusica.setTypeface(Typeface.DEFAULT_BOLD);
        textoMusica.setPadding(0, px(10), 0, 0);
        textoMusica.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlayerMusicas.proxima(AtividadePainel.this);
                atualizarMusica();
            }
        });
        cartaoMusica.addView(textoMusica, largura());
        TextView dicaMusica = texto("Toque no nome da canção para trocar. Músicas vêm da internet (usa seus dados móveis).");
        dicaMusica.setTextSize(12);
        dicaMusica.setGravity(android.view.Gravity.CENTER);
        cartaoMusica.addView(dicaMusica, largura());
        coluna.addView(cartaoMusica, largura());

        LinearLayout linhaFim = new LinearLayout(this);
        linhaFim.setOrientation(LinearLayout.HORIZONTAL);
        View bloquear = botao("Bloquear", false);
        bloquear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Cofre.bloquear();
                ir(AtividadeAcesso.class);
                finish();
            }
        });
        View apagar = botao("Apagar tudo", false);
        apagar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmarApagar();
            }
        });
        LinearLayout.LayoutParams metade = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        metade.rightMargin = px(5);
        LinearLayout.LayoutParams metade2 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        metade2.leftMargin = px(5);
        metade2.topMargin = px(10);
        LinearLayout.LayoutParams m1 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        m1.rightMargin = px(5);
        m1.topMargin = px(10);
        linhaFim.addView(bloquear, m1);
        linhaFim.addView(apagar, metade2);
        coluna.addView(linhaFim, largura());

        TextView rodape = new TextView(this);
        rodape.setText("CÉU • HONRA • MISSÃO\nBrasília • Brasil — Céu de todos nós");
        rodape.setTextColor(0xFF9DB0CC);
        rodape.setTextSize(12);
        rodape.setGravity(Gravity.CENTER);
        rodape.setPadding(0, px(20), 0, 0);
        coluna.addView(rodape);

        // FAROL do backup automatico: bolinha no canto — verde = funcionando,
        // vermelho = parado. Sem texto; toque curto diz o motivo.
        android.widget.FrameLayout raiz = new android.widget.FrameLayout(this);
        raiz.addView(rolagem, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        farol = new View(this);
        android.graphics.drawable.GradientDrawable bolinha =
                new android.graphics.drawable.GradientDrawable();
        bolinha.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bolinha.setColor(corFarol());
        bolinha.setStroke(px(2), 0xFFFFFFFF);
        farol.setBackground(bolinha);
        farol.setElevation(px(6));
        farol.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                aviso(textoFarol());
            }
        });
        android.widget.FrameLayout.LayoutParams lpFarol = new android.widget.FrameLayout.LayoutParams(
                px(22), px(22), android.view.Gravity.TOP | android.view.Gravity.END);
        lpFarol.topMargin = px(14);
        lpFarol.rightMargin = px(14);
        raiz.addView(farol, lpFarol);
        setContentView(raiz);
    }

    private void menu(LinearLayout coluna, String monograma, int cor,
                      String tituloItem, String subtitulo, final Class<?> destino) {
        LinearLayout cartao = cartao();
        cartao.setOrientation(LinearLayout.HORIZONTAL);
        cartao.setGravity(Gravity.CENTER_VERTICAL);
        View emblema = emblema(monograma, cor);
        LinearLayout.LayoutParams lpE = new LinearLayout.LayoutParams(px(46), px(46));
        lpE.rightMargin = px(14);
        cartao.addView(emblema, lpE);

        LinearLayout textos = new LinearLayout(this);
        textos.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(tituloItem);
        t.setTextColor(AZUL_MARINHA);
        t.setTextSize(15.5f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        TextView s = new TextView(this);
        s.setText(subtitulo);
        s.setTextColor(CINZA_TEXTO);
        s.setTextSize(12.5f);
        textos.addView(t);
        textos.addView(s);
        LinearLayout.LayoutParams lpT = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cartao.addView(textos, lpT);

        TextView seta = new TextView(this);
        seta.setText("›");
        seta.setTextColor(0xFF9DB0CC);
        seta.setTextSize(24);
        seta.setGravity(Gravity.CENTER);
        cartao.addView(seta, new LinearLayout.LayoutParams(px(22),
                ViewGroup.LayoutParams.MATCH_PARENT));

        cartao.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ir(destino);
            }
        });
        coluna.addView(cartao, largura());
    }

    private void confirmarApagar() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Apagar tudo e criar um novo acesso?")
                .setMessage("Por segurança, a senha não pode ser recuperada, e sem ela não há como abrir os dados. "
                        + "Esta ação apaga todos os registros deste aparelho.")
                .setPositiveButton("Apagar tudo", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int qual) {
                        Cofre.apagarTudo(AtividadePainel.this);
                        Intent i = new Intent(AtividadePainel.this, AtividadeAcesso.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                        finish();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
