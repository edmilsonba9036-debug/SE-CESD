/** Decodificação com EXIF (fotos da câmera deixam de aparecer deitadas) + utilidades. */

export async function bitmapComExif(arquivo: File | Blob): Promise<ImageBitmap | HTMLImageElement> {
  try {
    // 'from-image' aplica a rotação da câmera gravada no EXIF
    return await createImageBitmap(arquivo, { imageOrientation: 'from-image' });
  } catch {
    return await new Promise((ok, falha) => {
      const img = new Image();
      const url = URL.createObjectURL(arquivo);
      img.onload = () => { URL.revokeObjectURL(url); ok(img); };
      img.onerror = () => { URL.revokeObjectURL(url); falha(new Error('imagem inválida')); };
      img.src = url;
    });
  }
}

export async function paraBase64Jpeg(
  fonte: ImageBitmap | HTMLImageElement | HTMLCanvasElement,
  maxLado = 1280,
): Promise<string> {
  const w = 'width' in fonte ? fonte.width : 0;
  const h = 'height' in fonte ? fonte.height : 0;
  const escala = Math.min(1, maxLado / Math.max(w, h));
  const tela = document.createElement('canvas');
  tela.width = Math.max(1, Math.round(w * escala));
  tela.height = Math.max(1, Math.round(h * escala));
  const ctx = tela.getContext('2d')!;
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, tela.width, tela.height);
  ctx.drawImage(fonte, 0, 0, tela.width, tela.height);
  return tela.toDataURL('image/jpeg', 0.85).split(',')[1] ?? '';
}

/** Gira uma imagem em base64 (270 = 90° anti-horário) e devolve o novo base64. */
export async function girarBase64(base64: string, graus: number): Promise<string> {
  const img = new Image();
  await new Promise<void>((ok, falha) => {
    img.onload = () => ok();
    img.onerror = () => falha(new Error('imagem inválida'));
    img.src = 'data:image/jpeg;base64,' + base64;
  });
  const radial = (graus * Math.PI) / 180;
  const troca = Math.abs(Math.sin(radial)) > 0.5;
  const tela = document.createElement('canvas');
  tela.width = troca ? img.height : img.width;
  tela.height = troca ? img.width : img.height;
  const ctx = tela.getContext('2d')!;
  ctx.translate(tela.width / 2, tela.height / 2);
  ctx.rotate(radial);
  ctx.drawImage(img, -img.width / 2, -img.height / 2);
  return tela.toDataURL('image/jpeg', 0.85).split(',')[1] ?? '';
}

/** Lê bytes de um arquivo (com limite). */
export function lerBytes(arquivo: File, limite: number): Promise<Uint8Array> {
  return new Promise((ok, falha) => {
    const leitor = new FileReader();
    leitor.onload = () => ok(new Uint8Array(leitor.result as ArrayBuffer));
    leitor.onerror = () => falha(new Error('falha ao ler'));
    leitor.readAsArrayBuffer(arquivo);
  }).then((bytes) => {
    if ((bytes as Uint8Array).length > limite) throw new Error('muito grande');
    return bytes as Uint8Array;
  });
}
