import type { PropsTela } from '../App';
import type { Tela } from '../App';
import { Botao, Cartao, Titulo } from '../componentes/ui';
import * as cofre from '../lib/cofre';

const ITENS: { sigla: string; cor: string; titulo: string; sub: string; tela: Tela }[] = [
  { sigla: 'MV', cor: '#2D7DD2', titulo: 'Memorial da minha vida', sub: 'Fatos e acontecimentos: antes e durante o concurso, após formado, até a baixa — e a passagem pelo Exército', tela: 'memorial' },
  { sigla: 'TR', cor: '#2E7D5B', titulo: 'Minha trajetória', sub: 'Etapas da caminhada na Força Aérea, em ordem de data', tela: 'trajetoria' },
  { sigla: 'CD', cor: '#1E5AA8', titulo: 'Meu cadastro', sub: 'Fotografia e dados do Soldado Especializado', tela: 'cadastro' },
  { sigla: 'GA', cor: '#C9A227', titulo: 'Galeria', sub: '4 locais para as fotografias da sua história', tela: 'galeria' },
  { sigla: 'DC', cor: '#C9A227', titulo: 'Documentos', sub: 'Seus documentos (PDF e imagens), criptografados no cofre', tela: 'documentos' },
  { sigla: 'BK', cor: '#4B5320', titulo: 'Backup no Google Drive', sub: 'Automático, criptografado, no seu Drive', tela: 'backup' },
];

export default function TelaPainel({ ir, aoSair, avisar, usuario }: PropsTela & { usuario: string }) {
  function apagarTudo() {
    if (!window.confirm('Apagar TUDO neste aparelho? Os dados do Drive continuam no backup.')) return;
    cofre.apagarTudo();
    avisar('Cofre apagado deste aparelho.');
    aoSair();
  }

  return (
    <div>
      <div className="mt-4 rounded-3xl bg-gradient-to-b from-marinha to-medio px-6 py-8 text-white shadow-lg">
        <h1 className="text-xl font-bold">Bem-vindo, {usuario}!</h1>
        <p className="mt-1 text-[13px] text-white/80">
          Sua história militar registrada: do concurso do CESD à passagem pelo Exército — tudo criptografado.
        </p>
      </div>

      <Titulo>Minha história</Titulo>
      <div className="flex flex-col gap-3">
        {ITENS.map((item) => (
          <Cartao key={item.sigla} className="cursor-pointer hover:shadow-lg" >
            <button onClick={() => ir(item.tela)} className="flex w-full items-start gap-3 text-left">
              <span
                className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-[13px] font-bold text-white"
                style={{ backgroundColor: item.cor }}
              >
                {item.sigla}
              </span>
              <span>
                <span className="block text-[15px] font-bold text-marinha">{item.titulo}</span>
                <span className="mt-0.5 block text-[12.5px] leading-snug text-tinta">{item.sub}</span>
              </span>
            </button>
          </Cartao>
        ))}
      </div>

      <Titulo>Sessão</Titulo>
      <div className="flex flex-col gap-3">
        <Botao onClick={() => { cofre.bloquear(); aoSair(); }}>Bloquear</Botao>
        <Botao onClick={apagarTudo}>Apagar tudo (neste aparelho)</Botao>
      </div>
    </div>
  );
}
