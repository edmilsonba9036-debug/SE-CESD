import { useState } from 'react';
import * as cofre from '../lib/cofre';
import { Botao, Campo, Cartao } from '../componentes/ui';
import type { Avisar } from '../App';

export default function TelaAcesso({ avisar, aoEntrar }: { avisar: Avisar; aoEntrar: () => void }) {
  const criando = !cofre.existe();
  const [usuario, setUsuario] = useState(cofre.usuarioGravado());
  const [senha, setSenha] = useState('');
  const [confirmar, setConfirmar] = useState('');
  const [trabalhando, setTrabalhando] = useState(false);

  async function submeter() {
    if (criando && senha !== confirmar) { avisar('As senhas não são iguais.', false); return; }
    setTrabalhando(true);
    try {
      const erro = criando ? await cofre.criar(usuario, senha) : await cofre.abrir(usuario, senha);
      if (erro) { avisar(erro, false); return; }
      avisar(criando ? 'Acesso criado ✓ Bem-vindo!' : 'Bem-vindo de volta!');
      aoEntrar();
    } catch {
      avisar('Falha de criptografia neste navegador.', false);
    } finally {
      setTrabalhando(false);
    }
  }

  return (
    <div className="mx-auto flex min-h-full w-full max-w-md flex-col px-6 pb-10">
      <div className="mt-10 flex flex-col items-center rounded-3xl bg-gradient-to-b from-marinha to-medio px-6 py-10 text-center text-white shadow-lg">
        <div className="flex h-20 w-20 items-center justify-center rounded-full border-4 border-ouro bg-white/10 text-3xl font-bold text-ouro">
          SE
        </div>
        <h1 className="mt-4 text-2xl font-bold">Soldado Especializado</h1>
        <p className="mt-1 text-sm text-white/80">Seu memorial da passagem pelo CESD — Força Aérea Brasileira</p>
      </div>

      <Cartao className="mt-6">
        <h2 className="text-[15px] font-bold text-marinha">
          {criando ? 'Crie um usuário e uma senha' : 'Digite seu usuário e sua senha'}
        </h2>
        <p className="mb-3 mt-1 text-[12.5px] text-tinta">
          {criando
            ? 'Eles protegem seu cadastro e seus registros neste aparelho.'
            : 'Acesso protegido por senha.'}
        </p>
        <div className="flex flex-col gap-3">
          <Campo placeholder="Usuário" value={usuario} onChange={(e) => setUsuario(e.target.value)} autoComplete="username" />
          <Campo placeholder="Senha (mínimo 8 caracteres)" type="password" value={senha}
                 onChange={(e) => setSenha(e.target.value)}
                 autoComplete={criando ? 'new-password' : 'current-password'} />
          {criando && (
            <Campo placeholder="Confirmar senha" type="password" value={confirmar}
                   onChange={(e) => setConfirmar(e.target.value)} autoComplete="new-password" />
          )}
          <Botao primario disabled={trabalhando} onClick={submeter}>
            {trabalhando ? 'Verificando…' : criando ? 'Criar acesso' : 'Entrar'}
          </Botao>
        </div>
        <p className="mt-3 text-[11.5px] leading-relaxed text-tinta">
          Anote sua senha em local seguro. Por segurança, ela não pode ser recuperada:
          sem ela, os dados não abrem — nem aqui, nem no backup do Drive.
        </p>
      </Cartao>
    </div>
  );
}
