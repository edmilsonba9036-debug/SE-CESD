package br.org.secesd.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
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
    private TextView pastaEscolhida;
    private View limparPasta;
    private android.widget.Button botaoEscolherPasta;
    private android.widget.Button botaoPastaConta;
    private static final int PEDIR_PASTA = 4404;
    private static final int PEDIR_ARQUIVO = 4405;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Cofre.sessaoAberta()) { ir(AtividadeAcesso.class); finish(); return; }

        drive = new DriveBackup(this, this);

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

        View navegador = botao("Acesso pelo navegador (alternativo)", false);
        navegador.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { dialogoNavegador(); }
        });
        coluna.addView(navegador, largura());

        coluna.addView(titulo("Pasta no Drive (mais simples)"));
        botaoEscolherPasta = botao("Escolher pasta do backup…", false);
        botaoEscolherPasta.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                try {
                    startActivityForResult(i, PEDIR_PASTA);
                } catch (Exception e) {
                    aviso("Este aparelho não tem o seletor de pastas.");
                }
            }
        });
        coluna.addView(botaoEscolherPasta, largura());

        pastaEscolhida = new TextView(this);
        pastaEscolhida.setTextSize(13);
        pastaEscolhida.setPadding(0, px(6), 0, 0);
        coluna.addView(pastaEscolhida);

        limparPasta = botao("Limpar pasta escolhida", false);
        limparPasta.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drive.definirSafPasta(null);
                mostrarPasta();
                aviso("Pasta escolhida removida.");
            }
        });
        coluna.addView(limparPasta, largura());

        botaoPastaConta = botao(rotuloPastaConta(), false);
        botaoPastaConta.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { dialogoPastaConta(); }
        });
        coluna.addView(botaoPastaConta, largura());
        TextView dicaPasta = texto("“Trocar pasta” vale para a pasta escolhida no seletor. "
                + "O nome abaixo vale para o backup pela conta Google (cria ou usa a pasta com esse nome no Drive).");
        dicaPasta.setTextSize(11.5f);
        coluna.addView(dicaPasta);
        mostrarPasta();

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
                aviso(novo ? "Backup automático ATIVADO: o app envia sozinho ao sair das telas."
                           : "Backup automático DESATIVADO: envie pelo botão quando quiser.");
            }
        });
        coluna.addView(botaoAuto, largura());

        long ultimo = DriveBackup.ultimoBackup(this);
        if (ultimo > 0) {
            String quando = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                    .format(new java.util.Date(ultimo));
            TextView su = texto("Último backup enviado: " + quando);
            su.setTextSize(12);
            coluna.addView(su);
        }

        status = new TextView(this);
        status.setTextColor(CINZA_TEXTO);
        status.setTextSize(14);
        status.setPadding(0, px(14), 0, 0);
        coluna.addView(status);

        coluna.addView(texto("\nArquivo único SE-CESD-backup.json: cada envio atualiza o mesmo arquivo, "
                + "mantendo só a versão mais recente. Com o automático LIGADO, qualquer alteração "
                + "(fato, foto, cadastro) vai ao Drive sozinha quando você sai da tela. Nada é legível sem a sua senha; em trânsito há TLS."));

        setContentView(rolagem);
    }

    @Override
    public void status(String mensagem, boolean ok) {
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


    private String rotuloPastaConta() {
        return "Pasta da conta Google: " + DriveBackup.pastaContaNome(this);
    }

    private void dialogoPastaConta() {
        LinearLayout formulario = new LinearLayout(this);
        formulario.setOrientation(LinearLayout.VERTICAL);
        int p = px(18);
        formulario.setPadding(p, p, p, 0);
        final android.widget.EditText nome = campo("Nome da pasta no Drive");
        nome.setText(DriveBackup.pastaContaNome(this));
        formulario.addView(nome);
        formulario.addView(texto("Se a pasta já existir no seu Drive com esse nome, o backup usa ela; "
                + "se não existir, o app cria."));
        new AlertDialog.Builder(this)
                .setTitle("Pasta do backup (conta Google)")
                .setView(formulario)
                .setPositiveButton("Salvar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int qual) {
                        String n = nome.getText().toString().trim();
                        if (n.isEmpty()) n = "SE • CESD";
                        DriveBackup.definirPastaContaNome(AtividadeBackup.this, n);
                        botaoPastaConta.setText(rotuloPastaConta());
                        aviso("Pasta da conta Google definida: " + n);
                    }
                })
                .setNeutralButton("Voltar ao padrão", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int qual) {
                        DriveBackup.definirPastaContaNome(AtividadeBackup.this, "");
                        botaoPastaConta.setText(rotuloPastaConta());
                        aviso("Voltou para a pasta padrão “SE • CESD”.");
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void mostrarPasta() {
        if (pastaEscolhida == null) return;
        if (botaoEscolherPasta != null) {
            botaoEscolherPasta.setText(drive.temSaf()
                    ? "Trocar pasta do backup…"
                    : "Escolher pasta do backup…");
        }
        if (drive.temSaf()) {
            pastaEscolhida.setText("Pasta atual: " + drive.nomeSafPasta()
                    + "\nO backup desta pasta usa o app Drive do aparelho — sem autorização extra.");
            pastaEscolhida.setTextColor(0xFF1B5E20);
            limparPasta.setVisibility(View.VISIBLE);
        } else {
            pastaEscolhida.setText("Nenhuma pasta escolhida — o backup usará a conta Google "
                    + "(pasta “SE • CESD” no Drive).");
            pastaEscolhida.setTextColor(CINZA_TEXTO);
            limparPasta.setVisibility(View.GONE);
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

    private String rotuloAuto() {
        return DriveBackup.autoAtivo(this)
                ? "Backup automático: LIGADO (envia ao sair das telas)"
                : "Backup automático: DESLIGADO";
    }

    /**
     * Plano B: configura o cliente OAuth do tipo DESKTOP criado no Google
     * Cloud (não precisa SHA-1 nem pacote) e entra pelo navegador com PKCE.
     */
    private void dialogoNavegador() {
        LinearLayout formulario = new LinearLayout(this);
        formulario.setOrientation(LinearLayout.VERTICAL);
        int p = px(18);
        formulario.setPadding(p, p, p, 0);
        final android.widget.EditText id = campo("ID do cliente (…apps.googleusercontent.com)");
        id.setSingleLine(false);
        id.setMinLines(2);
        final android.widget.EditText segredo = campo("Segredo do cliente (GOCSPX-…)");
        id.setText(drive.idBrowser());
        segredo.setText(drive.segredoParaEdicao());
        formulario.addView(id);
        formulario.addView(segredo, largura());
        TextView dica = texto("No Cloud Console: Credenciais → Criar credenciais → ID do cliente OAuth "
                + "→ tipo “Aplicativo desktop” → Criar. Copie o ID e o segredo e cole aqui.");
        dica.setTextSize(12);
        formulario.addView(dica);
        new AlertDialog.Builder(this)
                .setTitle("Acesso pelo navegador")
                .setView(formulario)
                .setPositiveButton("Salvar e conectar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int qual) {
                        String i = id.getText().toString().trim();
                        String s = segredo.getText().toString().trim();
                        if (i.isEmpty() || s.isEmpty()) {
                            aviso("Preencha o ID e o segredo do cliente desktop.");
                            return;
                        }
                        drive.configurarBrowser(i, s);
                        drive.conectar();
                    }
                })
                .setNeutralButton("Limpar configuração", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int qual) {
                        drive.removerConfigBrowser();
                        aviso("Configuração do navegador removida.");
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PEDIR_PASTA) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                android.net.Uri uri = data.getData();
                try {
                    int bandeiras = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(uri, bandeiras);
                } catch (Exception ignored) {
                }
                drive.definirSafPasta(uri);
                mostrarPasta();
                aviso("Pasta definida: " + drive.nomeSafPasta());
            }
            return;
        }
        if (requestCode == PEDIR_ARQUIVO) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                restaurarDeUri(data.getData());
            }
            return;
        }
        if (drive != null) drive.onActivityResult(requestCode, data);
    }
}