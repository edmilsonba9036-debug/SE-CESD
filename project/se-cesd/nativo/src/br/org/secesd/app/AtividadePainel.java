package br.org.secesd.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Painel principal: acesso às áreas do memorial, com o mesmo menu do app web.
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
        ScrollView rolagem = tela("SE • CESD", "Insígnia • Memória • Conquista — memorial do Soldado Especializado da Aeronáutica");
        LinearLayout coluna = coluna(rolagem);

        LinearLayout cartao = cartao();
        TextView bemVindo = new TextView(this);
        bemVindo.setText("Bem-vindo, " + Cofre.usuarioSessao + "!\nSeus dados ficam só neste aparelho, "
                + "criptografados com a sua senha.");
        bemVindo.setTextColor(CINZA_TEXTO);
        bemVindo.setTextSize(14);
        cartao.addView(bemVindo);
        coluna.addView(cartao, largura());

        menu(coluna, "Meu cadastro", "Fotografia e dados do Soldado Especializado", AtividadeCadastro.class);
        menu(coluna, "Minha trajetória", "Fases e etapas: do concurso à saída da Força Aérea", AtividadeTrajetoria.class);
        menu(coluna, "Memorial & Insígnia", "A divisa, a história da causa CESD, valores e postos", AtividadeMemorial.class);
        menu(coluna, "Galeria", "Até 4 fotos pessoais da sua história na FAB", AtividadeGaleria.class);
        menu(coluna, "Backup no Google Drive", "Enviar, proteger e restaurar seus registros", AtividadeBackup.class);

        View bloquear = botao("Bloquear", false);
        bloquear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Cofre.bloquear();
                ir(AtividadeAcesso.class);
                finish();
            }
        });
        coluna.addView(bloquear, largura());

        View apagar = botao("Apagar tudo", false);
        apagar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmarApagar();
            }
        });
        coluna.addView(apagar, largura());

        TextView rodape = new TextView(this);
        rodape.setText("Brasília • Brasil • Céu de todos nós\n© Homenagem ao Soldado Especializado da Aeronáutica");
        rodape.setTextColor(CINZA_TEXTO);
        rodape.setTextSize(12);
        rodape.setGravity(Gravity.CENTER);
        rodape.setPadding(0, px(18), 0, 0);
        coluna.addView(rodape);

        setContentView(rolagem);
    }

    private void menu(LinearLayout coluna, String titulo, String subtitulo, final Class<?> destino) {
        LinearLayout cartao = cartao();
        cartao.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(titulo);
        t.setTextColor(AZUL_MARINHA);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        TextView s = new TextView(this);
        s.setText(subtitulo);
        s.setTextColor(CINZA_TEXTO);
        s.setTextSize(13);
        cartao.addView(t);
        cartao.addView(s);
        cartao.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ir(destino);
            }
        });
        coluna.addView(cartao, largura());
    }

    private void confirmarApagar() {
        new AlertDialog.Builder(this)
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
