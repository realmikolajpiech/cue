import { useCallback, useRef, useState } from 'react';
import { BackHandler, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Copy, Icon } from '@/components/ui';
import { CueMark, CueMascot } from '@/components/CueBrand';
import { useTheme } from '@/theme/useTheme';
import { Button, ui } from './components';
import { useSubtextPreferences } from './preferences';

// Deliberately fictional examples: onboarding never reads or uploads messages.
const examples = [
  { label: 'Na co dzień', name: 'Ola', message: 'To co robimy w sobotę?',
    context: 'Ostatnio mówiła: „Chcę w końcu spróbować tej włoskiej na Kazimierzu”.',
    reply: 'Może ta włoska na Kazimierzu, o której mówiłaś? Pasuje Ci 18?',
    desire: 'Zaproponuj coś, co nawiązuje do Waszej rozmowy.' },
  { label: 'Flirt', name: 'Maja', message: 'Całkiem miło było wczoraj :)',
    context: 'Po spotkaniu żartowaliście, że rewanż w bilard jest obowiązkowy.',
    reply: 'Mi też :) Ale ten rewanż w bilard nadal aktualny. Kiedy dajesz mi szansę?',
    desire: 'Okaż zainteresowanie bez wymuszonego tekstu.' },
  { label: 'Wsparcie', name: 'Kuba', message: 'No i nie wyszło…',
    context: 'Kuba wcześniej pisał o rozmowie rekrutacyjnej, na której mu zależało.',
    reply: 'Kurczę, wiem, że zależało Ci na tej pracy. Chcesz pogadać czy wyjść się przewietrzyć?',
    desire: 'Pokaż, że słuchasz, nawet gdy trudno znaleźć słowa.' },
] as const;

export default function Onboarding({ replay = false }: { replay?: boolean }) {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const finish = useSubtextPreferences(s => s.finish);
  const [step, setStep] = useState(0);
  const [exampleIndex, setExampleIndex] = useState(0);
  const scroll = useRef<ScrollView>(null);
  const leaving = useRef(false);
  const example = examples[exampleIndex];

  function move(next: number) {
    setStep(next);
    scroll.current?.scrollTo({ y: 0, animated: false });
  }
  useFocusEffect(useCallback(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (step === 0) return false;
      setStep(value => value - 1);
      scroll.current?.scrollTo({ y: 0, animated: false });
      return true;
    });
    return () => subscription.remove();
  }, [step]));

  function complete(connect: boolean) {
    if (leaving.current) return;
    leaving.current = true;
    finish();
    router.replace(connect ? '/connections' : '/');
  }
  function dismiss() {
    if (replay && router.canGoBack()) router.back();
    else complete(false);
  }

  return <View style={[styles.screen, { backgroundColor: colors.background }]}>
    <ScrollView ref={scroll} contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={[styles.content, { paddingTop: insets.top + 12, paddingBottom: insets.bottom + 12 }]}>
      <View style={styles.top}>
        {step > 0 ? <Pressable accessibilityRole="button" accessibilityLabel="Poprzedni krok" onPress={() => move(step - 1)}
          style={({ pressed }) => [styles.textAction, { opacity: pressed ? 0.5 : 1 }]}>
          <Icon name="back" /><Copy style={[ui.body, { color: colors.text }]}>Wstecz</Copy>
        </Pressable> : <CueMark size={32} />}
        <Pressable accessibilityRole="button" onPress={dismiss} style={({ pressed }) => [styles.textAction, { opacity: pressed ? 0.5 : 1 }]}>
          <Copy style={ui.small}>{replay ? 'Zamknij' : 'Pomiń'}</Copy>
        </Pressable>
      </View>
      <View accessible accessibilityLabel={`Krok ${step + 1} z 3`} style={styles.progress}>
        {[0, 1, 2].map(index => <View key={index} style={[styles.track, { backgroundColor: index <= step ? colors.accent : colors.border }]} />)}
      </View>

      <View style={styles.main}>
        <View style={styles.heading} accessibilityLiveRegion="polite">
          <Copy title accessibilityRole="header" style={ui.heading}>
            {step === 0 ? 'Mniej rozkmin. Więcej rozmowy.' : step === 1 ? 'Dobre słowa zaczynają się od kontekstu.' : 'Twoje rozmowy. Twoje słowa.'}
          </Copy>
          <Copy style={ui.body}>
            {step === 0 ? 'Chcesz podtrzymać rozmowę, poflirtować albo kogoś wesprzeć. Tylko co odpisać?'
              : step === 1 ? 'Cue łączy dostępne wiadomości i ważne szczegóły, żeby podpowiedź pasowała do Waszej rozmowy.'
                : 'Podpowiedzi AI prosto w klawiaturze, tam, gdzie już piszesz.'}
          </Copy>
        </View>

        {step === 0 && <View style={styles.example}>
          <CueMascot pose="write" size={112} />
          <View style={styles.choices} accessibilityRole="radiogroup" accessibilityLabel="Przykład rozmowy">
            {examples.map((item, index) => <Pressable key={item.label} accessibilityRole="radio"
              accessibilityState={{ checked: exampleIndex === index }} aria-checked={exampleIndex === index} onPress={() => setExampleIndex(index)}
              style={({ pressed }) => [styles.choice, { backgroundColor: exampleIndex === index ? colors.accent : colors.secondary, opacity: pressed ? 0.7 : 1 }]}>
              <Copy style={[ui.small, { fontFamily: 'DMSansSemiBold', color: exampleIndex === index ? colors.onAccent : colors.text }]}>{item.label}</Copy>
            </Pressable>)}
          </View>
          <View style={[styles.bubble, { backgroundColor: colors.surface }]} accessibilityLiveRegion="polite">
            <Copy style={ui.small}>{example.name} · przykładowa rozmowa</Copy>
            <Copy style={[styles.message, { color: colors.text }]}>{example.message}</Copy>
          </View>
          <Copy style={ui.body}>{example.desire}</Copy>
        </View>}

        {step === 1 && <View style={styles.example}>
          <View style={[styles.context, { borderColor: colors.border }]}>
            <View style={styles.inline}><Icon name="history" size={18} color={colors.accent} /><Copy style={[ui.small, { color: colors.accent }]}>Wcześniej w rozmowie</Copy></View>
            <Copy style={[ui.body, { color: colors.text }]}>{example.context}</Copy>
          </View>
          <Copy style={[ui.body, { color: colors.text }]}>{example.name}: „{example.message}”</Copy>
          <View style={[styles.bubble, { backgroundColor: colors.secondary }]}>
            <Copy style={[ui.small, { color: colors.accent, fontFamily: 'DMSansSemiBold' }]}>Przykładowa podpowiedź Cue</Copy>
            <Copy style={[styles.message, { color: colors.text }]}>{example.reply}</Copy>
          </View>
          <Copy style={ui.small}>Ważne szczegóły znajdziesz też w profilu rozmowy.</Copy>
        </View>}

        {step === 2 && <View style={styles.example}>
          <CueMascot pose="wave" size={80} />
          {[
            ['Połącz komunikator', 'Messenger lub WhatsApp.'],
            ['Wybierz osobę', 'Zobacz kontekst Waszej rozmowy.'],
            ['Włącz klawiaturę Cue', 'Wybierz podpowiedź i dopasuj ją do siebie.'],
          ].map(([title, detail], index) => <View key={title} style={styles.setupRow}>
            <View style={[styles.number, { backgroundColor: colors.secondary }]}><Copy style={[ui.body, { color: colors.accent }]}>{index + 1}</Copy></View>
            <View style={styles.setupText}><Copy style={[ui.body, { color: colors.text, fontFamily: 'DMSansSemiBold' }]}>{title}</Copy><Copy style={ui.small}>{detail}</Copy></View>
          </View>)}
          <Copy style={ui.small}>Ty decydujesz, co wysyłasz. Analizę AI włączysz osobno w ustawieniach.</Copy>
        </View>}
      </View>

      <View style={styles.footer}>
        <Button label={step === 0 ? 'Zobacz podpowiedź' : step === 1 ? 'Chcę spróbować' : 'Połącz komunikator'}
          onPress={() => step < 2 ? move(step + 1) : complete(true)} />
        {step === 2 && <Pressable accessibilityRole="button" onPress={() => complete(false)} style={styles.later}>
          <Copy style={ui.small}>Na razie rozejrzę się</Copy>
        </Pressable>}
      </View>
    </ScrollView>
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  content: { flexGrow: 1, paddingHorizontal: 24, gap: 20, width: '100%', maxWidth: 520, alignSelf: 'center' },
  top: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  textAction: { minHeight: 44, minWidth: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  progress: { flexDirection: 'row', gap: 8 },
  track: { height: 4, borderRadius: 2, flex: 1 },
  main: { flexGrow: 1, gap: 24, paddingTop: 8 },
  heading: { gap: 12 },
  example: { gap: 16 },
  choices: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  choice: { minHeight: 44, paddingVertical: 12, paddingHorizontal: 16, borderRadius: 28, borderCurve: 'continuous', justifyContent: 'center' },
  bubble: { padding: 20, borderRadius: 16, borderCurve: 'continuous', gap: 8 },
  context: { borderLeftWidth: 2, paddingLeft: 16, gap: 8 },
  inline: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  message: { fontSize: 18, lineHeight: 28 },
  setupRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  setupText: { flex: 1, gap: 4 },
  number: { width: 36, height: 36, borderRadius: 18, alignItems: 'center', justifyContent: 'center' },
  footer: { gap: 4, paddingTop: 12 },
  later: { minHeight: 44, justifyContent: 'center', alignItems: 'center' },
});
