import { requireOptionalNativeModule } from 'expo';
import { Platform } from 'react-native';
import type * as ExpoClipboard from 'expo-clipboard';

// Development builds can predate the clipboard dependency. Importing it eagerly
// throws during route discovery and prevents the entire app from mounting.
export const clipboard: typeof ExpoClipboard | null =
  Platform.OS === 'web' || requireOptionalNativeModule('ExpoClipboard')
    // This must stay conditional: a static import crashes builds without the native module.
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    ? require('expo-clipboard')
    : null;
