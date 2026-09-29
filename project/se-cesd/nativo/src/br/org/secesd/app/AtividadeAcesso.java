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

        TextView marca = new TextView(this);
        marca.setText("SE • CESD\nSOLDADO ESPECIALIZADO DA AERONÁUTICA");
        marca.setTextColor(AZUL_MARINHA);
        marca.setTextSize(13);
        marca.setTypeface(Typeface.DEFAULT_BOLD);
        marca.setLineSpacing(px(3), 1f);
        coluna.addView(marca);
        coluna.addView(espaco(10));

        campoUsuario = campo("Usuário");
        coluna.addView(campoUsuario, largura());
        campoSenha = campo(criando ? "Senha (mínimo 8 caracteres)" : "Senha");
        campoSenha.setTransformationMethod(new android.text.method.PasswordTransformationMethod());
        coluna.addView(campoSenha, largura());
        campoConfirmar = null;
        if (criando) {
            campoConfirmar = campo("Confirmar senha");
            campoConfirmar.setTransformationMethod(new android.text.method.PasswordTransformationMethod());
            coluna.addView(campoConfirmar, largura());

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
