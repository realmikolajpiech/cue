import { t, useLanguage } from '@/i18n';
import { Switch } from 'react-native';
import * as Linking from 'expo-linking';
import * as DocumentPicker from 'expo-document-picker';
import { Screen, Copy, Card, Action, InlineError } from '@/components/ui';
import { guardian } from '@/services/guardian';
import { useAction, useGuardianStatus } from '@/services/queries';

export default function Protection() { useLanguage();
  const status = useGuardianStatus(); const action = useAction(); const s = status.data;
  async function importModel() {
    const file = await DocumentPicker.getDocumentAsync({ copyToCacheDirectory: false, multiple: false });
    if (file.canceled) return;
    if (!file.assets[0].name.endsWith('.litertlm')) throw new Error('invalid_format');
    await guardian.importModel(file.assets[0].uri);
  }
  return <Screen>
    <Copy title>{s?.active ? t("Ochrona jest aktywna") : t("Skonfiguruj ochronę")}</Copy>
    <Copy>{s?.active ? t("Analiza działa lokalnie. Brak ostrzeżenia nie gwarantuje bezpieczeństwa.") : t("Guardian potrzebuje lokalnego modelu i dostępu do powiadomień.")}</Copy>
    {status.isPending && <Copy>{t("Sprawdzam stan urządzenia…")}</Copy>}
    <InlineError message={status.isError ? t("Nie można odczytać stanu ochrony.") : action.error} />
    {status.isError && <Action label={t("Spróbuj ponownie")} onPress={() => { void status.refetch(); }} />}
    {s && !s.available && <Card><Copy title>{t("Wymagany Android")}</Copy><Copy>{t("Uruchom własny build Guardian na Androidzie. Expo Go, iOS i web nie obsługują odczytu powiadomień innych aplikacji.")}</Copy></Card>}
    <Card><Copy title>{t("1. Model na urządzeniu")}</Copy>
      <Copy>{s?.modelState === 'ready' ? t('Model gotowy · {{backend}}', { backend: s.backend.toUpperCase() }) : s?.modelState === 'loading' ? t("Ładowanie modelu…") : s?.modelState === 'error' ? t("Model nie uruchomił się. Sprawdź zgodność pliku .litertlm.") : s?.modelInstalled ? t("Model zainstalowany. Włącz monitorowanie, aby go uruchomić.") : t("Nie zaimportowano modelu.")}</Copy>
      <Copy>{t("Gemma 3 1B INT4 · 584 MB. Pobierz gemma3-1b-it-int4.litertlm po zaakceptowaniu licencji na Hugging Face. Guardian sprawdza plik przed instalacją. Import nie włącza monitorowania.")}</Copy>
      <Action label={t("Pobierz Gemma na Hugging Face")} secondary disabled={action.busy} onPress={() => { void action.run(() => Linking.openURL('https://huggingface.co/litert-community/Gemma3-1B-IT/blob/a6306a4e292016480083b73b8dc6f3f939ae04c3/gemma3-1b-it-int4.litertlm')); }} />
      <Action label={action.busy ? t("Trwa operacja…") : t("Importuj model .litertlm")} disabled={!s?.available || action.busy} onPress={() => { void action.run(importModel); }} />
    </Card>
    <Card><Copy title>{t("2. Dostęp do powiadomień")}</Copy><Copy>{t("Po włączeniu dostępu Android udostępni Guardian treść powiadomień. Analizowane są tylko WhatsApp, Messenger, Beeper oraz SMS z Google i Samsung. Wiadomości są przetwarzane lokalnie, a kontekst znika z RAM po 15 minutach.")}</Copy>
      <Copy>{s?.notificationAccess ? s.listenerConnected ? t("Listener jest połączony.") : t("Zgoda udzielona. Oczekiwanie na połączenie…") : t("Dostęp nie jest włączony.")}</Copy>
      <Action label={t("Otwórz ustawienia dostępu")} secondary disabled={!s?.available || action.busy} onPress={() => { void action.run(guardian.openSettings); }} />
    </Card>
    <Card><Copy title>{t("3. Ostrzeżenia systemowe")}</Copy><Copy>{s?.notificationPermission ? t("Powiadomienia są dozwolone.") : t("Zezwól na ostrzeżenia, aby widzieć wysokie ryzyko poza aplikacją.")}</Copy>
      <Action label={t("Zezwól na ostrzeżenia")} secondary disabled={!s?.available} onPress={guardian.warningPermission} />
    </Card>
    <Card><Copy title>{t("Monitorowanie")}</Copy><Copy>{t("Przełącznik włącza analizę nowych powiadomień. Wyłączenie czyści kontekst i zatrzymuje bieżącą analizę.")}</Copy>
      <Switch accessibilityLabel={t("Monitorowanie powiadomień")} value={s?.monitoringEnabled ?? false} disabled={!s?.available || action.busy || (!s.monitoringEnabled && (!s.modelInstalled || !s.notificationAccess))} onValueChange={value => { void action.run(() => guardian.monitor(value)); }} />
      {s?.processing && <Copy>{t("Analizuję kontekst…")}</Copy>}
      {s?.error && <Copy>{t("Ostatnia operacja silnika nie powiodła się. Sprawdź model i uruchom ochronę ponownie.")}</Copy>}
    </Card>
  </Screen>;
}
