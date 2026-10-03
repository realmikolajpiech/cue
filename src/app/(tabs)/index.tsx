import NativeProtection from '@/features/native/screens/Protection';
import DemoProtection from '@/features/demo/screens/Protection';

export default function ProtectionRoute() {
  return process.env.EXPO_OS === 'android' ? <NativeProtection /> : <DemoProtection />;
}
