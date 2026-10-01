package br.org.secesd.app;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Primeira tela: criar acesso (1ª execução) ou entrar.
 * Espelha a tela "Criar acesso / Digite seu usuário e sua senha" do app web.
 */
public class AtividadeAcesso extends AtividadeBase {

    private EditText campoUsuario, campoSenha, campoConfirmar;
    private boolean criando;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        criando = !Cofre.existe(this);
        montar();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Se a sessão já está aberta (voltou de outra tela), vai direto ao painel.
        if (Cofre.sessaoAberta()) {
            startActivity(new Intent(this, AtividadePainel.class));
            finish();
        }
    }

    private void montar() {
        String titulo = criando ? "Criar acesso" : "Entrar";
        String subtitulo = criando
                ? "Crie um usuário e uma senha. Eles protegem seu cadastro e seus registros neste aparelho."
                : "Digite seu usuário e sua senha para continuar.";
        ScrollView rolagem = tela(titulo, subtitulo);
        LinearLayout coluna = coluna(rolagem);

        // Medalhão com o SABRE ALADO da Aeronáutica
        LinearLayout medalhao = new LinearLayout(this);
        medalhao.setOrientation(LinearLayout.VERTICAL);
        medalhao.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable fundoMedalhao = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0A2463, 0xFF16407F});
        fundoMedalhao.setCornerRadius(px(30));
        medalhao.setBackground(fundoMedalhao);
        int pm = px(22);
        medalhao.setPadding(pm, px(26), pm, px(20));

        SabreAladoView sabre = new SabreAladoView(this);
        medalhao.addView(sabre, new LinearLayout.LayoutParams(px(120), px(120)));

        TextView monograma = new TextView(this);
        monograma.setText("SE • CESD");
        monograma.setTextColor(OURO);
        monograma.setTextSize(17);
        monograma.setTypeface(Typeface.DEFAULT_BOLD);
        monograma.setLetterSpacing(0.22f);
        monograma.setPadding(0, px(12), 0, 0);
        medalhao.addView(monograma);

        TextView legenda = new TextView(this);
        legenda.setText("SOLDADO ESPECIALIZADO DA AERONÁUTICA");
        legenda.setTextColor(0xFFC6D6EE);
        legenda.setTextSize(10.5f);
        legenda.setLetterSpacing(0.10f);
        legenda.setPadding(0, px(4), 0, 0);
        medalhao.addView(legenda);

        coluna.addView(medalhao, largura());
        coluna.addView(espaco(12));

        campoUsuario = campo("Usuário");
        coluna.addView(campoUsuario, largura());
        campoSenha = campo(criando ? "Senha (mínimo 8 caracteres)" : "Senha");
        coluna.addView(linhaSenha(campoSenha), largura());
        campoConfirmar = null;
        if (criando) {
            campoConfirmar = campo("Confirmar senha");
            coluna.addView(linhaSenha(campoConfirmar), largura());

            TextView nota = new TextView(this);
            nota.setText("Anote sua senha em local seguro. Por segurança, ela não pode ser recuperada: "
                    + "se esquecê-la, será preciso apagar os dados deste aparelho e começar de novo.");
            nota.setTextColor(CINZA_TEXTO);
            nota.setTextSize(12);
            nota.setPadding(0, px(10), 0, 0);
            coluna.addView(nota);
        }

        LinearLayout botaoCriar = new LinearLayout(this);
        botaoCriar.setOrientation(LinearLayout.VERTICAL);
        View entrar = botao(criando ? "Criar acesso" : "Entrar", true);
        entrar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmar();
            }
        });
        coluna.addView(entrar, largura());

        if (!criando) {
            TextView nota = new TextView(this);
            String usuario = Cofre.usuarioGravado(this);
            nota.setText(usuario.isEmpty() ? "Acesso protegido por senha." : "Usuário: " + usuario);
            nota.setTextColor(CINZA_TEXTO);
            nota.setTextSize(12);
            nota.setPadding(0, px(12), 0, 0);
            coluna.addView(nota);
        }
        setContentView(rolagem);
    }

    /** Campo de senha com botão 👁 para mostrar/ocultar. */
    private LinearLayout linhaSenha(final EditText campoDeSenha) {
        campoDeSenha.setTransformationMethod(new android.text.method.PasswordTransformationMethod());
        LinearLayout linha = new LinearLayout(this);
        linha.setOrientation(LinearLayout.HORIZONTAL);
        linha.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams peso = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        linha.addView(campoDeSenha, peso);

        final android.widget.Button olho = new android.widget.Button(this);
        olho.setText("👁");
        olho.setAllCaps(false);
        olho.setTextSize(16);
        olho.setBackground(arredondado(0xFFFFFFFF, px(14), 0xFFD9E4F2, px(1)));
        olho.setPadding(px(10), px(6), px(10), px(6));
        LinearLayout.LayoutParams lpOlho = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpOlho.leftMargin = px(8);
        linha.addView(olho, lpOlho);

        olho.setOnClickListener(new View.OnClickListener() {
            boolean visivel = false;

            @Override
            public void onClick(View v) {
                visivel = !visivel;
                campoDeSenha.setTransformationMethod(visivel ? null
                        : new android.text.method.PasswordTransformationMethod());
                olho.setText(visivel ? "🙈" : "👁");
                campoDeSenha.setSelection(campoDeSenha.getText().length());
            }
        });
        return linha;
    }

    private void confirmar() {
        String usuario = campoUsuario.getText().toString().trim();
        String senha = campoSenha.getText().toString();
        if (criando) {
            String confirmar = campoConfirmar.getText().toString();
            if (!senha.equals(confirmar)) {
                aviso("As senhas não são iguais.");
                return;
            }
            String erro = Cofre.criar(this, usuario, senha);
            if (erro != null) {
                aviso(erro);
                return;
            }
            aviso("Acesso criado ✓ Seus dados ficam só neste aparelho, criptografados com a sua senha.");
            ir(AtividadeCadastro.class);
            finish();
        } else {
            String erro = Cofre.abrir(this, usuario, senha);
            if (erro != null) {
                aviso(erro);
                return;
            }
            ir(AtividadePainel.class);
            finish();
        }
    }
}
