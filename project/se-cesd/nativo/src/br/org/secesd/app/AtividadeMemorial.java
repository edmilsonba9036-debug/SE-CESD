package br.org.secesd.app;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * Memorial & Insígnia: a divisa, a história da causa CESD, valores e postos.
 * Conteúdo informativo (espelha as seções do app web).
 */
public class AtividadeMemorial extends AtividadeBase {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Cofre.sessaoAberta()) { ir(AtividadeAcesso.class); finish(); return; }

        ScrollView rolagem = tela("Memorial & Insígnia", "Insígnia • Memória • Conquista");
        LinearLayout coluna = coluna(rolagem);

        coluna.addView(titulo("A insígnia de manga"));
        LinearLayout cartaoInsig = cartao();
        cartaoInsig.addView(texto("Uma divisa no braço: a conquista do Soldado Especializado da Aeronáutica, "
                + "ingresso por concurso público, formação militar e técnica especializada.\n\n"
                + "• Uso: manga direita e esquerda dos uniformes de serviço\n"
                + "• Posição: centralizada na manga, alinhada ao eixo do braço\n"
                + "• Par idêntico nos dois braços — simetria é inspeção\n"
                + "• Nunca em trajes civis ou uniformes de gala de outras forças\n\n"
                + "Uniforme operacional camuflado: versão emborrachada/baixa visibilidade. "
                + "Gorro com pala: versão bordada reduzida.\n\n"
                + "O uso das insígnias deve seguir o RUMAER e as normas aplicáveis à época."));
        coluna.addView(cartaoInsig, largura());

        coluna.addView(titulo("Capítulo I — A conquista"));
        coluna.addView(texto("Família, estudos, trabalho e a decisão de servir. Inscrição, provas, aprovação "
                + "e o curso de especialização: aprovação em concurso público para ingresso no CESD "
                + "(Criado em 1993 — RCPGAer, Decreto nº 880/93). Conclusão do curso conforme as normas "
                + "aplicáveis à turma."));

        coluna.addView(titulo("Capítulo II — Espírito militar"));
        coluna.addView(texto("Base institucional das Forças Armadas. Respeito à cadeia de comando e cumprimento "
                + "exato do dever. Olhos no céu, guarda alta na terra: o SE de guarda e segurança é o escudo "
                + "silencioso das organizações.\n\n“Ninguém fica para trás.” — espírito de corpo que une recrutas, "
                + "cabos, sargentos e oficiais numa só Força."));

        coluna.addView(titulo("Valores — CÉU • HONRA • MISSÃO"));
        LinearLayout cartaoValores = cartao();
        cartaoValores.addView(texto("• Amar a Pátria acima de tudo — servir ao Brasil com o sacrifício da própria vida, se necessário.\n"
                + "• Fidelidade à instituição, aos superiores, aos pares e à missão recebida. Palavra de soldado não volta atrás.\n"
                + "• Sempre pronto: farda alinhada, equipamento em condições, corpo e mente preparados para o toque de reunir.\n"
                + "• Seis cores, seis atitudes — como as especialidades de um pelotão."));
        coluna.addView(cartaoValores, largura());

        coluna.addView(titulo("Postos e graduações"));
        coluna.addView(texto("A praça especial (SE) tem o mesmo grau dos Soldados de 1ª e 2ª Classe, frequenta o "
                + "círculo dos oficiais subalternos e ocupa posição própria na hierarquia da Força Aérea Brasileira. "
                + "“Você está aqui” — entre o céu e o chão que se guarda.\n\n"
                + "Concurso público • Aeronáutica — Serviço na Força Aérea • Saída da Força Aérea "
                + "(licenciamento do serviço ativo)."));

        coluna.addView(titulo("Compromisso"));
        coluna.addView(texto("Compromisso à Bandeira • Juramento do soldado • Cerimônia de incorporação. "
                + "CÉU • HONRA • MISSÃO."));
        setContentView(rolagem);
    }
}
