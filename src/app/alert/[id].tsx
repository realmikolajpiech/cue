import NativeAlertDetail from '@/features/native/screens/AlertDetail';
import DemoAlertDetail from '@/features/demo/screens/AlertDetail';

export default function AlertDetailRoute() {
  return process.env.EXPO_OS === 'android' ? <NativeAlertDetail /> : <DemoAlertDetail />;
}
