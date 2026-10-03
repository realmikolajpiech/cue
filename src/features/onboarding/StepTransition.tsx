import { useEffect, useRef, useState, type ReactNode } from 'react';
import { AccessibilityInfo, Animated, Easing } from 'react-native';

/** Replace content only after it fades out, then enter from the direction of travel. */
export function StepTransition({ step, children }: {
  step: number;
  children: (displayedStep: number, transitioning: boolean) => ReactNode;
}) {
  const [displayedStep, setDisplayedStep] = useState(step);
  const displayed = useRef(step);
  const [opacity] = useState(() => new Animated.Value(1));
  const [offset] = useState(() => new Animated.Value(0));
  const [reduceMotion, setReduceMotion] = useState(true);

  useEffect(() => {
    let mounted = true;
    void AccessibilityInfo.isReduceMotionEnabled().then(value => {
      if (mounted) setReduceMotion(value);
    }).catch(() => {});
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduceMotion);
    return () => { mounted = false; subscription.remove(); };
  }, []);

  useEffect(() => {
    let frame: number | undefined;
    let animation: Animated.CompositeAnimation;
    const enter = () => {
      animation = Animated.parallel([
        Animated.timing(opacity, { toValue: 1, duration: reduceMotion ? 100 : 190, easing: Easing.out(Easing.cubic), useNativeDriver: true }),
        Animated.timing(offset, { toValue: 0, duration: reduceMotion ? 0 : 190, easing: Easing.out(Easing.cubic), useNativeDriver: true }),
      ]);
      animation.start();
    };
    if (step === displayed.current) {
      // Restore the current step smoothly if a back action interrupted its exit.
      enter();
    } else {
      const direction = step > displayed.current ? 1 : -1;
      animation = Animated.parallel([
        Animated.timing(opacity, { toValue: 0, duration: reduceMotion ? 70 : 90, useNativeDriver: true }),
        Animated.timing(offset, { toValue: reduceMotion ? 0 : -direction * 12, duration: reduceMotion ? 0 : 90, easing: Easing.out(Easing.cubic), useNativeDriver: true }),
      ]);
      animation.start(({ finished }) => {
        if (!finished) return;
        offset.setValue(reduceMotion ? 0 : direction * 16);
        displayed.current = step;
        setDisplayedStep(step);
        // Let the new content commit before its entrance starts.
        frame = requestAnimationFrame(enter);
      });
    }
    return () => {
      if (frame !== undefined) cancelAnimationFrame(frame);
      animation?.stop();
    };
  }, [step, opacity, offset, reduceMotion]);

  return <Animated.View style={{ flex: 1, opacity, transform: [{ translateX: offset }] }}>
    {children(displayedStep, displayedStep !== step)}
  </Animated.View>;
}
