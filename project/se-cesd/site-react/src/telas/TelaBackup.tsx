import { useRef, useState } from 'react';
import type { Avisar } from '../App';
import { Botao, Campo, Cartao, Texto, Titulo } from '../componentes/ui';
import * as cofre from '../lib/cofre';
import * as drive from '../lib/drive';
import { NOME_ARQUIVO } from '../lib/tipos';

export default function TelaBackup({ avisar, aoSair, usuario, trocouUsuario }: {
  avisar: Avisar; aoSair: () => void; usuario: string; trocouUsuario: (n: string) => void;
}) {
  const [trabalhando, setTrabalhando] = useState('');
  const [erro, setErro] = useState('');
  const [clienteId, setClienteId] = useState(drive.idCliente());
  const [auto, setAuto] = useState(localStorage.getItem('secesd.autoBackup') !== '0');
  const [novoUsuario, setNovoUsuario] = useState('');
  const [senhaAtual, setSenhaAtual] = useState('');
  const [novaSenha, setNovaSenha] = useState('');
  const [confirmaSenha, setConfirmaSenha] = useState('');
  const arquivoRef = useRef<HTMLInputElement>(null);

  const ultimo = Number(localStorage.getItem('secesd.ultimoBackup') ?? '0');
  const quando = ultimo > 0
    ? new Date(ultimo).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
    : 'nunca';

  function falhou(e: unknown) {
    const msg = String((e as Error)?.message ?? e);
    setErro(msg);
    avisar(msg, false);
  }

  async function enviarDrive(silencioso = false) {
    setErro('');
    try {
      await drive.enviar(cofre.exportar(), (m) => setTrabalhando(m));
      cofre.limparPendente();
      localStorage.setItem('secesd.ultimoBackup', String(Date.now()));
      setTrabalhando('');
      if (!silencioso) avisar('Backup salvo no Google Drive ✓');
    } catch (e) { setTrabalhando(''); falhou(e); }
  }

  function baixarArquivo() {
    try {
      const envelope = {
        formato: 'se-cesd-backup', versao: 1,
        criadoEm: new Date().toISOString().slice(0, 19),
        aparelho: drive.aparelho(),
        dados: cofre.exportar(),
      };
      const blob = new Blob([JSON.stringify(envelope)], { type: 'application/json' });
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = NOME_ARQUIVO;
      a.click();
      URL.revokeObjectURL(a.href);
      avisar('Arquivo de backup baixado ✓ guarde em local seguro.');
    } catch (e) { falhou(e); }
  }

  async function aoEscolherArquivo(e: React.ChangeEvent<HTMLInputElement>) {
    const arquivo = e.target.files?.[0];
    e.target.value = '';
    if (!arquivo) return;
    try {
      const texto = await arquivo.text();
      let cofreJson = texto;
      try {
        const e2 = JSON.parse(texto) as { formato?: string; dados?: string };
        if (e2.formato === 'se-cesd-backup' && e2.dados) cofreJson = e2.dados;
      } catch { /* pode ser o cofre puro */ }
      cofre.substituir(cofreJson);
      avisar('Backup aplicado — entre com a senha DO BACKUP para abrir.');
      aoSair();
    } catch (err) { falhou(err); }
  }

  async function conectar() {
    setErro('');
    drive.configurarCliente(clienteId);
    try {
      const problema = await drive.autorizar();
      if (problema) avisar(problema, false);
    } catch (e) { falhou(e); }
  }

  async function salvarUsuario() {
    const erro = await cofre.mudarUsuario(novoUsuario);
    if (erro) { avisar(erro, false); return; }
    trocouUsuario(novoUsuario.trim());
    setNovoUsuario('');
    avisar('Nome de usuário alterado ✓ Atualizando o backup no Drive…');
    if (drive.configurado()) await enviarDrive(true);
  }

  async function trocarSenha() {
    if (novaSenha !== confirmaSenha) { avisar('As senhas novas não são iguais.', false); return; }
    const erro = await cofre.trocarSenha(usuario, senhaAtual, novaSenha);
    if (erro) { avisar(erro, false); return; }
    setSenhaAtual(''); setNovaSenha(''); setConfirmaSenha('');
    avisar('Senha trocada ✓ Atualizando o backup no Drive…');
    if (drive.configurado()) await enviarDrive(true);
  }

  return (
    <div className="mt-3">
      <Cartao>
        <p className="text-[13px] leading-relaxed text-tinta">
          O cofre vai criptografado (AES-GCM com a sua senha) para a pasta <b>“SE • CESD”</b> do seu Drive.
          Para abrir em outro aparelho, use a mesma senha.
        </p>
        <Texto className="mt-1">Último backup enviado: {quando}</Texto>
        {trabalhando && <p className="mt-2 text-[13px] font-semibold text-medio">{trabalhando}</p>}
        <div className="mt-3 flex flex-col gap-3">
          <Botao primario onClick={() => (drive.configurado() ? enviarDrive() : baixarArquivo())}>
            {drive.configurado() ? 'Enviar backup' : 'Enviar backup (baixar arquivo)'}
          </Botao>
          <Botao onClick={baixarArquivo}>Baixar arquivo de backup</Botao>
          <Botao onClick={() => arquivoRef.current?.click()}>Restaurar de um arquivo…</Botao>
          <input ref={arquivoRef} type="file" accept=".json,application/json" className="hidden" onChange={aoEscolherArquivo} />
        </div>
        {erro && (
          <button
            onClick={() => navigator.clipboard?.writeText(erro).then(() => avisar('Erro copiado!')).catch(() => {})}
            className="mt-3 w-full rounded-xl bg-[#fde7ea] px-4 py-2 text-[12.5px] font-semibold text-[#B00020]"
          >
            📋 Copiar o erro (cole na conversa)
          </button>
        )}
      </Cartao>

      <Titulo>Google Drive (navegador)</Titulo>
      <Cartao>
        <Texto>
          Informe o ID do cliente OAuth <b>Aplicativo Web</b> do seu Google Cloud (com o endereço deste site
          cadastrado em “Origens de redirecionamento autorizadas”). A conexão usa PKCE, sem segredo.
        </Texto>
        <div className="mt-3 flex flex-col gap-3">
          <Campo placeholder="ID do cliente (…apps.googleusercontent.com)" value={clienteId}
                 onChange={(e) => setClienteId(e.target.value)} />
          <Botao primario onClick={conectar}>Conectar ao Drive</Botao>
          <Botao onClick={() => { drive.limparConfig(); setClienteId(''); avisar('Configuração do Drive removida.'); }}>
            Remover configuração
          </Botao>
          <Botao onClick={() => {
            const novo = !auto;
            setAuto(novo);
            localStorage.setItem('secesd.autoBackup', novo ? '1' : '0');
            avisar(novo ? 'Backup automático ATIVADO: envia sozinho ao sair das telas.'
                        : 'Backup automático DESATIVADO: envie pelo botão quando quiser.');
          }}>
            Backup automático: {auto ? 'LIGADO (envia ao sair das telas)' : 'DESLIGADO'}
          </Botao>
        </div>
      </Cartao>

      <Titulo>Segurança</Titulo>
      <Cartao>
        <p className="text-[14px] font-bold text-marinha">Usuário: {usuario}</p>
        <div className="mt-3 flex flex-col gap-3">
          <Campo placeholder="Novo nome de usuário" value={novoUsuario} onChange={(e) => setNovoUsuario(e.target.value)} />
          <Botao onClick={salvarUsuario}>Mudar nome de usuário</Botao>
        </div>
        <p className="mt-4 text-[12px] text-tinta">
          A senha nova re-criptografa tudo neste aparelho e o backup é atualizado no Drive automaticamente.
        </p>
        <div className="mt-3 flex flex-col gap-3">
          <Campo placeholder="Senha atual" type="password" value={senhaAtual} onChange={(e) => setSenhaAtual(e.target.value)} />
          <Campo placeholder="Nova senha (mínimo 8 caracteres)" type="password" value={novaSenha} onChange={(e) => setNovaSenha(e.target.value)} />
          <Campo placeholder="Confirmar nova senha" type="password" value={confirmaSenha} onChange={(e) => setConfirmaSenha(e.target.value)} />
          <Botao primario onClick={trocarSenha}>Trocar senha do cofre</Botao>
        </div>
      </Cartao>

      <Texto className="mt-4 text-center">
        Arquivo único {NOME_ARQUIVO}: cada envio atualiza o mesmo arquivo. Nada é legível sem a sua senha.
      </Texto>
    </div>
  );
}
