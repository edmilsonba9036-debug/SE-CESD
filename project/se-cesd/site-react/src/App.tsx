import { useCallback, useEffect, useState } from 'react';
import * as cofre from './lib/cofre';
import * as drive from './lib/drive';
import TelaAcesso from './telas/TelaAcesso';
import TelaPainel from './telas/TelaPainel';
import TelaCadastro from './telas/TelaCadastro';
import TelaMemorial from './telas/TelaMemorial';
import TelaTrajetoria from './telas/TelaTrajetoria';
import TelaGaleria from './telas/TelaGaleria';
import TelaDocumentos from './telas/TelaDocumentos';
import TelaBackup from './telas/TelaBackup';
import TelaInsignia from './telas/TelaInsignia';

export type Tela = 'painel' | 'cadastro' | 'memorial' | 'trajetoria'
  | 'galeria' | 'documentos' | 'backup' | 'insignia';

export type Avisar = (msg: string, ok?: boolean) => void;

export interface PropsTela {
  avisar: Avisar;
  ir: (t: Tela) => void;
  aoSair: () => void;
  trocouUsuario: (novo: string) => void;
}

export default function App() {
  const [aberto, setAberto] = useState(cofre.sessaoAberta());
  const [tela, setTela] = useState<Tela>('painel');
  const [usuario, setUsuario] = useState(cofre.usuarioGravado());
  const [aviso, setAviso] = useState<{ msg: string; ok: boolean } | null>(null);

  const avisar = useCallback((msg: string, ok = true) => {
    setAviso({ msg, ok });
    window.setTimeout(() => setAviso(null), 4500);
  }, []);

  useEffect(() => {
    drive.concluirRetorno()
      .then((ok) => { if (ok) avisar('Conectado pelo Google ✓'); })
      .catch((e) => avisar(String(e?.message ?? e), false));
  }, [avisar]);

  const ir = useCallback((destino: Tela) => {
    // Backup automático ao sair das telas (como no app nativo)
    if (cofre.pendenteEnviar() && drive.configurado()
        && localStorage.getItem('secesd.autoBackup') !== '0') {
      try {
        drive.enviar(cofre.exportar(), () => { /* silencioso */ })
          .then(() => { cofre.limparPendente(); localStorage.setItem('secesd.ultimoBackup', String(Date.now())); })
          .catch(() => { /* o aviso do envio manual cobre depois */ });
      } catch { /* silencioso */ }
    }
    setTela(destino);
    window.scrollTo(0, 0);
  }, []);

  const aoSair = useCallback(() => { setAberto(false); setTela('painel'); }, []);
  const trocouUsuario = useCallback((novo: string) => setUsuario(novo), []);

  if (!aberto || !cofre.sessaoAberta()) {
    return <TelaAcesso avisar={avisar} aoEntrar={() => { setUsuario(cofre.usuarioGravado()); setAberto(true); }} />;
  }

  const props: PropsTela = { avisar, ir, aoSair, trocouUsuario };
  const titulos: Record<Tela, string> = {
    painel: 'Painel do Soldado',
    cadastro: 'Meu cadastro',
    memorial: 'Memorial da minha vida',
    trajetoria: 'Minha trajetória',
    galeria: 'Galeria',
    documentos: 'Documentos',
    backup: 'Backup no Google Drive',
    insignia: 'Insígnia & Valores',
  };

  return (
    <div className="mx-auto min-h-full w-full max-w-2xl px-4 pb-10">
      {tela !== 'painel' && (
        <header className="flex items-center gap-3 pt-4">
          <button
            onClick={() => ir('painel')}
            className="rounded-xl bg-white px-3 py-2 text-sm font-bold text-marinha shadow"
          >
            ← Voltar
          </button>
          <h1 className="text-lg font-bold text-marinha">{titulos[tela]}</h1>
        </header>
      )}

      {aviso && (
        <div
          className={
            'mt-3 rounded-xl px-4 py-3 text-[13px] font-semibold shadow ' +
            (aviso.ok ? ' bg-[#e3f4e8] text-[#1B5E20] ' : ' bg-[#fde7ea] text-[#B00020] ')
          }
        >
          {aviso.msg}
        </div>
      )}

      <main>
        {tela === 'painel' && <TelaPainel {...props} usuario={usuario} />}
        {tela === 'cadastro' && <TelaCadastro {...props} />}
        {tela === 'memorial' && <TelaMemorial {...props} />}
        {tela === 'trajetoria' && <TelaTrajetoria {...props} />}
        {tela === 'galeria' && <TelaGaleria {...props} />}
        {tela === 'documentos' && <TelaDocumentos {...props} />}
        {tela === 'backup' && <TelaBackup {...props} usuario={usuario} />}
        {tela === 'insignia' && <TelaInsignia />}
      </main>
    </div>
  );
}
