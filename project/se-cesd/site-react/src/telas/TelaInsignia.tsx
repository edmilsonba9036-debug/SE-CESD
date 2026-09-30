import { Cartao, Texto, Titulo } from '../componentes/ui';

const VALORES = [
  { nome: 'Disciplina', texto: 'A base da ordem unida: cumprir o dever com precisão e respeito.' },
  { nome: 'Honra', texto: 'Agir sempre de modo que a farda e a própria consciência aprovem.' },
  { nome: 'Cohesão', texto: 'Camaradagem: ninguém vence sozinho dentro da caserna.' },
  { nome: 'Serviço', texto: 'Pátria acima de si: servir a população é a razão da existência da FAB.' },
];

const POSTOS = [
  'Soldado de 2ª Classe', 'Soldado de 1ª Classe', 'Cabo',
  '3º Sargento', '2º Sargento', '1º Sargento', 'Suboficial',
];

export default function TelaInsignia() {
  return (
    <div className="mt-3">
      <Cartao>
        <div className="flex flex-col items-center text-center">
          <div className="flex h-20 w-20 items-center justify-center rounded-full border-4 border-ouro bg-marinha text-3xl font-bold text-ouro">
            SE
          </div>
          <h2 className="mt-3 text-lg font-bold text-marinha">A insígnia SE</h2>
          <Texto>
            A divisa do Soldado Especializado carrega as cores da Força Aérea Brasileira e o ouro da
            especialização: o ingresso por concurso público do CESD — Casa do Soldado Especializado.
          </Texto>
        </div>
      </Cartao>

      <Titulo>Valores</Titulo>
      <div className="flex flex-col gap-3">
        {VALORES.map((v) => (
          <Cartao key={v.nome}>
            <h3 className="text-[14px] font-bold text-marinha">{v.nome}</h3>
            <Texto>{v.texto}</Texto>
          </Cartao>
        ))}
      </div>

      <Titulo>Postos da graduação (FAB)</Titulo>
      <Cartao>
        <ul className="list-inside list-disc text-[13px] leading-relaxed text-tinta">
          {POSTOS.map((p) => <li key={p}>{p}</li>)}
        </ul>
      </Cartao>
    </div>
  );
}
