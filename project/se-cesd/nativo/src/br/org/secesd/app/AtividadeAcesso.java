package br.org.secesd.app;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
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

        android.widget.ImageView sabre = new android.widget.ImageView(this);
        sabre.setImageResource(br.org.secesd.debug.R.drawable.sabre_prata);
        medalhao.addView(sabre, new LinearLayout.LayoutParams(px(130), px(130)));

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
            View btnDigital = botao("👆  Entrar com a digital", false);
            btnDigital.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    entradaDigital();
                }
            });
            LinearLayout.LayoutParams lpDigital = (LinearLayout.LayoutParams) btnDigital.getLayoutParams();
            lpDigital.topMargin = px(10);
            coluna.addView(btnDigital, lpDigital);

            TextView dicaDigital = new TextView(this);
            dicaDigital.setText(Digital.habilitada(this)
                    ? "Digital ativa ✓ — a próxima entrada pode ser só com o dedo."
                    : "Primeiro uso: toque acima e confirme com sua senha e sua digital.");
            dicaDigital.setTextColor(CINZA_TEXTO);
            dicaDigital.setTextSize(12);
            dicaDigital.setPadding(0, px(8), 0, 0);
            coluna.addView(dicaDigital);

            if (Digital.habilitada(this)) {
                TextView desligar = new TextView(this);
                desligar.setText("Desabilitar entrada pela digital");
                desligar.setTextColor(AZUL_MEDIO);
                desligar.setTextSize(13);
                desligar.setTypeface(Typeface.DEFAULT_BOLD);
                desligar.setPadding(0, px(6), 0, 0);
                desligar.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        confirmarDesabilitar();
                    }
                });
                coluna.addView(desligar);
            }

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

    // ------------------------------------------------------------------
    // Entrada pela digital
    // ------------------------------------------------------------------

    private void entradaDigital() {
        if (!Digital.leitorPresente(this)) {
            aviso("Este aparelho não tem leitor de digital.");
            return;
        }
        if (!Digital.digitalCadastrada(this)) {
            aviso("Nenhuma digital cadastrada no aparelho. Cadastre em: Configurações › Segurança › Digital (impressão digital).");
            return;
        }
        if (Digital.habilitada(this)) {
            abrirPorDigital();
        } else {
            pedirSenhaParaHabilitar();
        }
    }

    /** Entrada direta: prepara a decifragem e pede a digital. */
    private void abrirPorDigital() {
        try {
            javax.crypto.Cipher c = Digital.cifradorAbrir(this);
            autenticar(c, false, null, null);
        } catch (Exception e) {
            Digital.desabilitar(this);
            montar();
            aviso("As digitais do aparelho mudaram desde a habilitação. Habilite a digital de novo, com sua senha.");
        }
    }

    /** Habilitação: prova a senha SEM abrir a sessão e então confirma a digital. */
    private void pedirSenhaParaHabilitar() {
        final EditText campoConf = campo("Sua senha de acesso");
        android.widget.FrameLayout moldura = new android.widget.FrameLayout(this);
        int p = px(20);
        moldura.setPadding(p, px(6), p, 0);
        moldura.addView(campoConf, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new android.app.AlertDialog.Builder(this)
                .setTitle("Habilitar entrada pela digital")
                .setMessage("Para ativar, confirme sua senha de acesso. Depois, encoste o dedo no leitor.")
                .setView(moldura)
                .setPositiveButton("Continuar", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int qual) {
                        String usuario = Cofre.usuarioGravado(AtividadeAcesso.this);
                        String senha = campoConf.getText().toString();
                        String erro = Cofre.verificarSenha(AtividadeAcesso.this, usuario, senha);
                        if (erro != null) {
                            aviso(erro);
                            return;
                        }
                        try {
                            javax.crypto.Cipher c = Digital.cifradorHabilitar();
                            autenticar(c, true, usuario, senha);
                        } catch (Exception e) {
                            aviso("Não foi possível preparar a digital: " + e.getMessage());
                        }
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmarDesabilitar() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Desabilitar entrada pela digital")
                .setMessage("A entrada voltará a ser sempre com usuário e senha. Quer desabilitar?")
                .setPositiveButton("Desabilitar", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int qual) {
                        Digital.desabilitar(AtividadeAcesso.this);
                        montar();
                        aviso("Entrada pela digital desabilitada.");
                    }
                })
                .setNegativeButton("Manter", null)
                .show();
    }

    /** Pede a digital ao Android (BiometricPrompt no Android 9+, FingerprintManager antes). */
    private void autenticar(final javax.crypto.Cipher cifra, final boolean habilitando,
                            final String usuarioHab, final String senhaHab) {
        final android.os.CancellationSignal sinal = new android.os.CancellationSignal();
        final java.util.concurrent.Executor exec = new java.util.concurrent.Executor() {
            private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());

            @Override
            public void execute(Runnable r) {
                h.post(r);
            }
        };

        if (Build.VERSION.SDK_INT >= 28) {
            android.hardware.biometrics.BiometricPrompt bp =
                    new android.hardware.biometrics.BiometricPrompt.Builder(this)
                            .setTitle(habilitando ? "Habilitar entrada pela digital" : "Entrar com a digital")
                            .setSubtitle("SE • CESD")
                            .setDescription(habilitando
                                    ? "Encoste o dedo no leitor para confirmar a habilitação."
                                    : "Encoste o dedo no leitor para abrir seus dados.")
                            .setNegativeButton("Cancelar", exec,
                                    new android.content.DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(android.content.DialogInterface d, int qual) {
                                        }
                                    })
                            .build();
            bp.authenticate(new android.hardware.biometrics.BiometricPrompt.CryptoObject(cifra),
                    sinal, exec, new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(
                                android.hardware.biometrics.BiometricPrompt.AuthenticationResult r) {
                            sucessoDigital(r.getCryptoObject().getCipher(), habilitando, usuarioHab, senhaHab);
                        }
                    });
        } else {
            android.hardware.fingerprint.FingerprintManager fm =
                    getSystemService(android.hardware.fingerprint.FingerprintManager.class);
            if (fm == null) {
                aviso("Leitor de digital indisponível.");
                return;
            }
            fm.authenticate(new android.hardware.fingerprint.FingerprintManager.CryptoObject(cifra),
                    sinal, 0, new android.hardware.fingerprint.FingerprintManager.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(
                                android.hardware.fingerprint.FingerprintManager.AuthenticationResult r) {
                            sucessoDigital(r.getCryptoObject().getCipher(), habilitando, usuarioHab, senhaHab);
                        }
                    }, new android.os.Handler(android.os.Looper.getMainLooper()));
        }
    }

    /** Digital aceita: grava (habilitação) ou abre o cofre (entrada). */
    private void sucessoDigital(javax.crypto.Cipher cifra, boolean habilitando,
                                String usuarioHab, String senhaHab) {
        try {
            if (habilitando) {
                Digital.salvar(this, cifra, usuarioHab, senhaHab);
                String erro = Cofre.abrir(this, usuarioHab, senhaHab);
                if (erro != null) {
                    Digital.desabilitar(this);
                    aviso(erro);
                    return;
                }
                aviso("Digital habilitada ✓ Da próxima vez, é só encostar o dedo.");
                ir(AtividadePainel.class);
                finish();
            } else {
                String[] creds = Digital.decifrar(this, cifra);
                String erro = Cofre.abrir(this, creds[0], creds[1]);
                if (erro != null) {
                    Digital.desabilitar(this);
                    montar();
                    aviso("Sua senha mudou desde a habilitação. Entre pela senha e habilite a digital de novo.");
                    return;
                }
                ir(AtividadePainel.class);
                finish();
            }
        } catch (Exception e) {
            Digital.desabilitar(this);
            montar();
            aviso("Não deu para validar a digital. Habilite de novo, com sua senha.");
        }
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
