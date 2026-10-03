import { Page } from './components';
import WritingStyleContent, { type WritingStyleProps } from './WritingStyleContent';

export default function WritingStyle(props: WritingStyleProps) {
  return <Page><WritingStyleContent {...props} /></Page>;
}
