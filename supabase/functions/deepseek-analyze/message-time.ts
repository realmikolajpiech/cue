/** Models should not do arithmetic on Unix timestamps to resolve calendar dates. */
export function messageTime(timestamp: number, timezone: string, text: string) {
  const parts = new Intl.DateTimeFormat('sv-SE', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(new Date(timestamp));
  const part = (type: string) => parts.find(p => p.type === type)!.value;
  const localDate = `${part('year')}-${part('month')}-${part('day')}`;
  const day = new Date(localDate + 'T12:00:00Z');
  const dateAfter = (days: number) => new Date(day.getTime() + days * 86_400_000).toISOString().slice(0, 10);
  const relativeDates: Record<string, string> = {};
  const lower = text.toLocaleLowerCase('pl-PL');
  if (/jutro|pojutrze|dziś|dzisiaj|dzis|poniedział|poniedzial|wtorek|środ|srod|czwart|piątek|piatek|sobot|niedziel/.test(lower)) {
    relativeDates.dzisiaj = localDate;
    relativeDates.jutro = dateAfter(1);
    relativeDates.pojutrze = dateAfter(2);
    const weekdays = ['niedziela', 'poniedziałek', 'wtorek', 'środa', 'czwartek', 'piątek', 'sobota'];
    weekdays.forEach((name, index) => { relativeDates[name] = dateAfter((index - day.getUTCDay() + 7) % 7); });
  }
  return { sentAtUtc: new Date(timestamp).toISOString(), sentOnLocalDate: localDate,
    sentAtLocalTime: `${part('hour')}:${part('minute')}`, timezone, relativeDates };
}
