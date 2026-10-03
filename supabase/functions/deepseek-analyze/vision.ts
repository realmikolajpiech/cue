export type ConversationImage = { messageId: string; dataUrl: string };

export function validateImages(value: unknown, messages: { id: string; text: string }[]): ConversationImage[] {
  if (value === undefined) return [];
  if (!Array.isArray(value) || value.length > 3) throw new Error('invalid_images');
  const ids = new Set(messages.filter(m => m.text.startsWith('[Zdjęcie]')).map(m => m.id));
  const seen = new Set<string>();
  return value.map(image => {
    if (!image || typeof image.messageId !== 'string' || !ids.has(image.messageId) || seen.has(image.messageId) ||
      typeof image.dataUrl !== 'string' || image.dataUrl.length > 600050 ||
      !/^data:image\/jpeg;base64,\/9j\/[A-Za-z0-9+/]*={0,2}$/.test(image.dataUrl) ||
      image.dataUrl.slice('data:image/jpeg;base64,'.length).length % 4 !== 0) throw new Error('invalid_images');
    seen.add(image.messageId);
    return { messageId: image.messageId, dataUrl: image.dataUrl };
  });
}

export function visionContent(context: object, images: ConversationImage[]) {
  return [
    { type: 'text', text: JSON.stringify(context) },
    ...images.flatMap(image => [
      { type: 'text', text: `Obraz do wiadomości o id: ${JSON.stringify(image.messageId)}. Autor i podpis są w messages.` },
      { type: 'image_url', image_url: { url: image.dataUrl } },
    ]),
  ];
}
