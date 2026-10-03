/** Tipos do SE • CESD — MESMA estrutura dos backups do app nativo. */

export interface Cadastro {
  nomeCompleto: string;
  nomeGuerra: string;
  especialidade: string;
  om: string;
  foto: string; // base64 JPEG (vazio quando não há)
}

export interface FotoSlot {
  legenda?: string;
  foto?: string; // base64 JPEG
}

export interface DocSlot {
  legenda?: string;
  nome?: string;
  tipo?: 'imagem' | 'pdf';
  doc?: string; // base64 (JPEG para imagem, bytes do PDF)
}

export interface Fato {
  titulo: string;
  data: string;
  local: string;
  descricao: string;
}

export interface Memorial {
  fase0?: Fato[];
  fase1?: Fato[];
  fase2?: Fato[];
  fase3?: Fato[];
  exercito?: Fato[];
}

export interface Dados {
  cadastro: Partial<Cadastro>;
  trajetoria: Fato[];
  galeria: FotoSlot[];
  documentos: DocSlot[];
  memorial: Memorial;
}

/** Cofre criptografado — campos idênticos ao app nativo (Cofre.java). */
export interface CofreJson {
  usuario: string;
  resumoUsuario: string;
  sal: string;
  ivVerif: string;
  verif: string;
  ivDados: string;
  dados: string;
}

/** Envelope do backup — idêntico ao do app nativo (SE-CESD-backup.json). */
export interface BackupEnvelope {
  formato: 'se-cesd-backup';
  versao: number;
  criadoEm: string;
  aparelho: string;
  dados: string; // cofre JSON em texto
}

export const MARCA_VERIFICADOR = 'se-cesd-aberto';
export const ITERACOES = 120000;
export const NOME_PASTA = 'SE • CESD';
export const NOME_ARQUIVO = 'SE-CESD-backup.json';

export function dadosVazio(): Dados {
  return { cadastro: {}, trajetoria: [], galeria: [], documentos: [], memorial: {} };
}

export const TRILHAS = [
  'Antes do concurso CESD',
  'Durante o concurso',
  'Após formado — Soldado Especialista',
  'Até a minha baixa',
  'Passagem pelo Exército',
];
export const TRILHAS_SUB = [
  'Família, estudos, trabalho e a decisão de servir.',
  'Inscrição, provas, aprovação e o curso no CESD.',
  'OMs, funções, missões e conquistas na FAB.',
  'Licenciamento do serviço ativo e a despedida da farda.',
  'Sua jornada nas unidades do Exército Brasileiro.',
];
export const CHAVES: (keyof Memorial)[] = ['fase0', 'fase1', 'fase2', 'fase3', 'exercito'];
export const CHIP = ['I', 'II', 'III', 'IV', 'EX'];
export const CORES_FASE = ['#2D7DD2', '#C9A227', '#2E7D5B', '#5A6B85', '#4B5320'];
