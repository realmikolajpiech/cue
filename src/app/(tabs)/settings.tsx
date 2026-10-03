import NativeSettings from '@/features/native/screens/Settings';
import DemoSettings from '@/features/demo/screens/Settings';

export default function SettingsRoute() {
  return process.env.EXPO_OS === 'android' ? <NativeSettings /> : <DemoSettings />;
}
