package br.org.secesd.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Backup no Google Drive: enviar o cofre (já cifrado pela senha),
 * restaurar com confirmação e conectar/renovar o acesso.
 */
public class AtividadeBackup extends AtividadeBase implements DriveBackup.Ouvinte {

    private TextView status;
    private DriveBackup drive;
    private android.widget.Button botaoAuto;
    private android.widget.Button copiarErro;
    private android.widget.Button abrirCadastro;
    private TextView usuarioLinha;
    private String ultimoErro;
    private TextView diagnostico;
    private static final int PEDIR_ARQUIVO = 4405;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Cofre.sessaoAberta()) { ir(AtividadeAcesso.class); finish(); return; }

        drive = new DriveBackup(this, this);
        drive.limparModosAntigos(); // seletor e navegador saem de cena de vez

        ScrollView rolagem = tela("Backup no Google Drive",
                "O cofre vai criptografado (AES-GCM com a sua senha) para a pasta “SE • CESD” do seu Drive. "
                        + "Para abrir em outro aparelho, use a mesma senha.");
        LinearLayout coluna = coluna(rolagem);

        View enviar = botao("Enviar backup", true);
        enviar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { enviar(); }
        });
        coluna.addView(enviar, largura());

        View restaurar = botao("Restaurar do Drive", false);
        restaurar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { drive.restaurar(); }
        });
        coluna.addView(restaurar, largura());

        View conectar = botao("Conectar ao Drive", false);
        conectar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { drive.conectar(); }
        });
        coluna.addView(conectar, largura());

        coluna.addView(titulo("Restaurar de um arquivo"));
        View escolherArquivo = botao("Escolher arquivo de backup…", false);
        escolherArquivo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(i, PEDIR_ARQUIVO);
                } catch (Exception e) {
                    aviso("Este aparelho não tem o seletor de arquivos.");
                }
            }
        });
        coluna.addView(escolherArquivo, largura());

        botaoAuto = botao(rotuloAuto(), false);
        botaoAuto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean novo = !DriveBackup.autoAtivo(AtividadeBackup.this);
                DriveBackup.definirAuto(AtividadeBackup.this, novo);
                botaoAuto.setText(rotuloAuto());
                pintarBotaoAuto();
                atualizarDiagnostico();
                aviso(novo ? "Backup automático ATIVADO: o app envia sozinho ao sair das telas."
                           : "Backup automático DESATIVADO: envie pelo botão quando quiser.");
            }
        });
        coluna.addView(botaoAuto, largura());

        diagnostico = new TextView(this);
        diagnostico.setTextColor(CINZA_TEXTO);
        diagnostico.setTextSize(12.5f);
        diagnostico.setLineSpacing(px(2), 1f);
        diagnostico.setPadding(0, px(10), 0, 0);
        coluna.addView(diagnostico);
        atualizarDiagnostico();

        android.widget.Button reparar = botao("🔧 Reparar backup automático", false);
        reparar.setBackground(arredondado(0xFFFFF3C4, px(16), 0xFFC9A227, px(1)));
        reparar.setTextColor(AZUL_MARINHA);
        reparar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DriveBackup.repararAuto(AtividadeBackup.this);
                atualizarDiagnostico();
                aviso("Reparado ✓ Confirme a janela do Google (autorização) e o backup volta a andar sozinho.");
                drive.conectar();
            }
        });
        coluna.addView(reparar, largura());

        status = new TextView(this);
        status.setTextColor(CINZA_TEXTO);
        status.setTextSize(14);
        status.setPadding(0, px(14), 0, 0);
        coluna.addView(status);

        copiarErro = botao("📋 Copiar o erro (cole aqui na conversa)", false);
        copiarErro.setVisibility(android.view.View.GONE);
        copiarErro.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ultimoErro == null) return;
                try {
                    android.content.ClipboardManager cm =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("erro", ultimoErro));
                    aviso("Erro copiado! Volte à conversa, segure o campo de mensagem e toque em Colar.");
                } catch (Exception e) {
                    aviso("Não consegui copiar. Anote as primeiras palavras do erro.");
                }
            }
        });
        coluna.addView(copiarErro, largura());

        abrirCadastro = botao("🌐 Abrir o cadastro no Google (5 min)", false);
        abrirCadastro.setVisibility(android.view.View.GONE);
        abrirCadastro.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://console.cloud.google.com/apis/credentials?project=stone-host-510104-n1")));
                } catch (Exception e) {
                    aviso("Abra no navegador: console.cloud.google.com → Credenciais");
                }
            }
        });
        coluna.addView(abrirCadastro, largura());

        coluna.addView(texto("\nArquivo único SE-CESD-backup.json: cada envio atualiza o mesmo arquivo, "
                + "mantendo só a versão mais recente. Com o automático LIGADO, qualquer alteração "
                + "(fato, foto, cadastro) vai ao Drive sozinha quando você sai da tela. Nada é legível sem a sua senha; em trânsito há TLS."));

        coluna.addView(titulo("Segurança"));
        usuarioLinha = texto("Usuário: " + Cofre.usuarioGravado(this));
        usuarioLinha.setTextSize(14);
        usuarioLinha.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        coluna.addView(usuarioLinha);
        View mudarUsuario = botao("Mudar nome de usuário…", false);
        mudarUsuario.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { dialogoMudarUsuario(); }
        });
        coluna.addView(mudarUsuario, largura());
        View trocarSenha = botao("Trocar senha do cofre…", false);
        trocarSenha.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { dialogoTrocarSenha(); }
        });
        coluna.addView(trocarSenha, largura());
        coluna.addView(texto("A senha nova re-criptografa tudo neste aparelho e o backup é atualizado "
                + "no Drive automaticamente. Em outro aparelho, entre com a senha nova e toque em Restaurar do Drive."));

        setContentView(rolagem);

        // Cria/verifica a pasta do Drive automaticamente, sem tocar em nada.
        drive.criarPastaAutomatica();
        atualizarDiagnostico();
    }

    private void dialogoMudarUsuario() {
        LinearLayout caixa = new LinearLayout(this);
        caixa.setOrientation(LinearLayout.VERTICAL);
        int p = px(20);
        caixa.setPadding(p, p / 2, p, 0);
        final android.widget.EditText campo = new android.widget.EditText(this);
        campo.setHint("Novo nome de usuário");
        campo.setText(Cofre.usuarioGravado(this));
        caixa.addView(campo);

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Mudar nome de usuário")
                .setMessage("Pelo menos 3 caracteres, sem espaços. Seus dados e sua senha ficam intactos; o backup no Drive é atualizado.")
                .setView(caixa)
                .setPositiveButton("Salvar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface di) {
                ((AlertDialog) di).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String novo = campo.getText().toString();
                        final String erro = Cofre.mudarUsuario(AtividadeBackup.this, novo);
                        if (erro != null) {
                            aviso(erro);
                            return;
                        }
                        ((AlertDialog) di).dismiss();
                        if (usuarioLinha != null) usuarioLinha.setText("Usuário: " + Cofre.usuarioGravado(AtividadeBackup.this));
                        aviso("Nome de usuário alterado ✓ Atualizando o backup no Drive…");
                        try {
                            drive.enviarSmart(Cofre.exportar(AtividadeBackup.this), false);
                        } catch (Exception e) {
                            aviso("Usuário alterado ✓ — toque em Enviar backup para atualizar o Drive.");
                        }
                    }
                });
            }
        });
        d.show();
    }

    private void dialogoTrocarSenha() {
        LinearLayout caixa = new LinearLayout(this);
        caixa.setOrientation(LinearLayout.VERTICAL);
        int p = px(20);
        caixa.setPadding(p, p / 2, p, 0);
        final android.widget.EditText atual = campoSenha("Senha atual");
        final android.widget.EditText nova = campoSenha("Nova senha (mínimo 8 caracteres)");
        final android.widget.EditText conf = campoSenha("Confirmar nova senha");
        caixa.addView(atual);
        caixa.addView(nova);
        caixa.addView(conf);

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Trocar senha do cofre")
                .setMessage("O cofre será re-criptografado com a nova senha e o backup será atualizado no Drive.")
                .setView(caixa)
                .setPositiveButton("Trocar senha", null)
                .setNegativeButton("Cancelar", null)
                .create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface di) {
                ((AlertDialog) di).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String sAtual = atual.getText().toString();
                        final String sNova = nova.getText().toString();
                        if (!sNova.equals(conf.getText().toString())) {
                            aviso("As senhas novas não são iguais.");
                            return;
                        }
                        ((AlertDialog) di).dismiss();
                        aviso("Trocando a senha…");
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                final String erro = Cofre.trocarSenha(AtividadeBackup.this,
                                        Cofre.usuarioGravado(AtividadeBackup.this), sAtual, sNova);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (erro != null) {
                                            aviso(erro);
                                            return;
                                        }
                                        aviso("Senha trocada ✓ Atualizando o backup no Drive…");
                                        try {
                                            drive.enviarSmart(Cofre.exportar(AtividadeBackup.this), false);
                                        } catch (Exception e) {
                                            aviso("Senha trocada ✓ — toque em Enviar backup para atualizar o Drive.");
                                        }
                                    }
                                });
                            }
                        }).start();
                    }
                });
            }
        });
        d.show();
    }

    private android.widget.EditText campoSenha(String dica) {
        android.widget.EditText campo = new android.widget.EditText(this);
        campo.setHint(dica);
        campo.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return campo;
    }

    @Override
    public void status(String mensagem, boolean ok) {
        ultimoErro = ok ? null : mensagem;
        if (copiarErro != null) {
            copiarErro.setVisibility(ok ? android.view.View.GONE : android.view.View.VISIBLE);
        }
        if (abrirCadastro != null) {
            boolean mostra = !ok && mensagem != null && mensagem.contains("ainda não conhece");
            abrirCadastro.setVisibility(mostra ? android.view.View.VISIBLE : android.view.View.GONE);
        }
        if (status != null) {
            status.setText(mensagem);
            status.setTextColor(ok ? 0xFF1B5E20 : 0xFFB00020);
        }
        aviso(mensagem);
    }

    @Override
    public void restaurado(final String cofreJson) {
        new AlertDialog.Builder(this)
                .setTitle("Aplicar o backup?")
                .setMessage("Os dados DESTE aparelho serão substituídos pelos do backup do Drive. "
                        + "Depois, entre com a MESMA senha usada quando o backup foi feito.")
                .setPositiveButton("Aplicar backup", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int qual) {
                        aplicar(cofreJson);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void aplicar(String cofreJson) {
        try {
            Cofre.substituir(this, cofreJson);
            aviso("Backup aplicado ✓ Entre com a senha do backup.");
            Intent i = new Intent(this, AtividadeAcesso.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        } catch (Exception e) {
            aviso("O backup baixado não pôde ser aplicado.");
        }
    }

    private void enviar() {
        try {
            String cofre = Cofre.exportar(this);
            drive.enviarSmart(cofre, false);
        } catch (Exception e) {
            aviso("Não foi possível ler o cofre para enviar.");
        }
    }


    private void restaurarDeUri(final android.net.Uri uri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String cofre = drive.lerBackupDeUri(uri);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            restaurado(cofre);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            aviso(String.valueOf(e.getMessage()));
                        }
                    });
                }
            }
        }).start();
    }

    /** Verde claro quando o automático está saudável: LIGADO + conta conectada + sem pausa. */
    private void pintarBotaoAuto() {
        if (botaoAuto == null) return;
        android.content.SharedPreferences p = getSharedPreferences("drive-backup", MODE_PRIVATE);
        boolean saudavel = DriveBackup.autoAtivo(this)
                && p.getString("contaNome", null) != null
                && System.currentTimeMillis() >= p.getLong("autoPausaAte", 0);
        if (saudavel) {
            botaoAuto.setBackground(arredondado(0xFFDFF5E0, px(16), 0xFF2E7D5B, px(1)));
            botaoAuto.setTextColor(0xFF2E7D5B);
        } else {
            botaoAuto.setBackground(arredondado(0xFFFFFFFF, px(16), 0xFFC9D8EA, px(1)));
            botaoAuto.setTextColor(AZUL_MARINHA);
        }
    }

    private static String quando(long t) {
        if (t <= 0) return "nunca";
        return new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                .format(new java.util.Date(t));
    }

    /** Mostra, em linguagem clara, por onde o backup automático está andando. */
    private void atualizarDiagnostico() {
        if (diagnostico == null) return;
        pintarBotaoAuto();
        android.content.SharedPreferences p = getSharedPreferences("drive-backup", MODE_PRIVATE);
        StringBuilder b = new StringBuilder("COMO ESTÁ O BACKUP AUTOMÁTICO\n");
        b.append("• Automático: ").append(DriveBackup.autoAtivo(this) ? "LIGADO" : "DESLIGADO").append('\n');
        String conta = p.getString("contaNome", null);
        b.append("• Conta Google: ").append(conta == null ? "— nenhuma (toque em Conectar)" : conta).append('\n');
        b.append("• Pendência de envio: ").append(p.getBoolean("pendenteEnviar", false) ? "sim (há novidades para enviar)" : "não (tudo já no Drive)").append('\n');
        b.append("• Último backup enviado: ").append(quando(p.getLong("ultimoBackup", 0))).append('\n');
        long pausa = p.getLong("autoPausaAte", 0);
        if (System.currentTimeMillis() < pausa) {
            b.append("• Pausado até: ").append(quando(pausa)).append(" (toque em Reparar para voltar já)\n");
        }
        String avisoAuto = p.getString("ultimoAviso", null);
        if (avisoAuto != null && !avisoAuto.isEmpty()) {
            b.append("• Último aviso do automático: ").append(avisoAuto)
             .append(" (").append(quando(p.getLong("ultimoAvisoEm", 0))).append(')');
        }
        diagnostico.setText(b.toString());
    }

    private String rotuloAuto() {
        return DriveBackup.autoAtivo(this)
                ? "Backup automático: LIGADO (envia ao sair das telas)"
                : "Backup automático: DESLIGADO";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PEDIR_ARQUIVO) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                restaurarDeUri(data.getData());
            }
            return;
        }
        if (drive != null) drive.onActivityResult(requestCode, data);
    }
}