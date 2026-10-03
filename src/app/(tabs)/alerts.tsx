import NativeAlerts from '@/features/native/screens/Alerts';
import DemoAlerts from '@/features/demo/screens/Alerts';

export default function AlertsRoute() {
  return process.env.EXPO_OS === 'android' ? <NativeAlerts /> : <DemoAlerts />;
}
