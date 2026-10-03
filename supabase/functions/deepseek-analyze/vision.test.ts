import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { validateImages, visionContent } from './vision.ts';

const messages = [{ id: 'photo', text: '[Zdjęcie]\nZobacz' }, { id: 'text', text: 'Hej' }];
const image = { messageId: 'photo', dataUrl: 'data:image/jpeg;base64,/9j/AA==' };
test('image stays linked to its source message in multimodal content', () => {
  const images = validateImages([image], messages);
  const blocks = visionContent({ messages }, images);
  assert.equal(blocks[0].type, 'text');
  assert.match(blocks[1].text!, /"photo"/);
  assert.equal(blocks[2].image_url?.url, image.dataUrl);
});
test('text-only callers remain supported', () => {
  assert.deepEqual(validateImages(undefined, messages), []);
  assert.equal(visionContent({ messages }, []).length, 1);
});
test('rejects foreign messages, duplicates, URLs, excessive image count and size', () => {
  for (const input of [
    [{ ...image, messageId: 'foreign' }], [{ ...image, messageId: 'text' }], [image, image],
    [{ ...image, dataUrl: 'https://example.com/photo.jpg' }],
    [image, image, image, image], [{ ...image, dataUrl: 'data:image/jpeg;base64,/9j/' + 'A'.repeat(600100) }],
  ]) assert.throws(() => validateImages(input, messages), /invalid_images/);
});
