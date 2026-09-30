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
        bemVindo.setTextColor(AZUL_MARINHA);
        bemVindo.setTextSize(16);
        bemVindo.setTypeface(Typeface.DEFAULT_BOLD);
        cartao.addView(bemVindo);
        TextView nota = new TextView(this);
        nota.setText("Seus registros ficam só neste aparelho, criptografados com a sua senha — "
                + "e vão ao Drive automaticamente quando você conecta o backup.");
        nota.setTextColor(CINZA_TEXTO);
        nota.setTextSize(13);
        nota.setPadding(0, px(4), 0, 0);
        cartao.addView(nota);
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
        menu(coluna, "IV", COR_FASE4, "Insígnia & Valores",
                "A divisa, a causa CESD, valores e postos da FAB", AtividadeMemorial.class);
        menu(coluna, "BK", COR_EXERCITO, "Backup no Google Drive",
                "Automático, criptografado, no seu Drive", AtividadeBackup.class);

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

        setContentView(rolagem);
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
