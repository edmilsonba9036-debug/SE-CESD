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

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Cofre.sessaoAberta()) { ir(AtividadeAcesso.class); finish(); return; }

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

        status = new TextView(this);
        status.setTextColor(CINZA_TEXTO);
        status.setTextSize(14);
        status.setPadding(0, px(14), 0, 0);
        coluna.addView(status);

        coluna.addView(texto("\nArquivo único SE-CESD-backup.json: cada envio atualiza o mesmo arquivo, "
                + "mantendo só a versão mais recente. Nada é legível sem a sua senha; em trânsito há TLS."));

        drive = new DriveBackup(this, this);
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
            drive.enviar(cofre);
        } catch (Exception e) {
            aviso("Não foi possível ler o cofre para enviar.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (drive != null) drive.onActivityResult(requestCode, data);
    }
}
