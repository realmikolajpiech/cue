import NativeOnboarding from '@/features/native/screens/Onboarding';
import DemoOnboarding from '@/features/demo/screens/Onboarding';

export default function OnboardingRoute() {
  return process.env.EXPO_OS === 'android' ? <NativeOnboarding /> : <DemoOnboarding />;
}
