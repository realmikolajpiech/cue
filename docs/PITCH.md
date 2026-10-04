# Cue — pitch dla Undefined

## Wersja około 90 sekund

Każdy zna moment, kiedy patrzy na wiadomość i nie wie, co odpisać. Chcemy kogoś wesprzeć, wyjaśnić nieporozumienie albo pokazać zainteresowanie, ale odpowiednie słowa nie przychodzą od razu. A kontekst — poprzednie rozmowy, plany i obietnice — jest gdzieś daleko w historii.

Cue to klawiatura AI, która łączy kontekst rozmowy, to, co chcemy powiedzieć, i nasz styl pisania.

[Pokaż rozmowę demo i źródło ustalenia.]

Tutaj Marta obiecuje oddać pieniądze za bilet. Cue zachowuje ustalenie i wiadomość, z której ono wynika. Gdy zmienia się termin albo wraca część kwoty, aktualizuje pamięć. Mogę sprawdzić źródło, poprawić wpis albo go usunąć.

[Pokaż intencję i klawiaturę.]

Podczas pisania wybieram rozmowę i ton. Cue uwzględnia moje próbki stylu oraz intencję i podsuwa odpowiedź w klawiaturze. Może też uznać, że nie trzeba już odpisywać. Tekst sprawdzam i wysyłam sam.

Technicznie łączymy natywne integracje Messenger i WhatsApp, lokalną pamięć i analizę stylu, backend Supabase oraz model DeepSeek. Klucz API pozostaje na serwerze. AI jest opcjonalne, a poszczególne rozmowy można wyłączyć z analizy.

Sprawdziliśmy rozwiązanie na syntetycznych przypadkach, w tym zmianie terminu, częściowej spłacie, sprzecznych informacjach i próbie wstrzyknięcia instrukcji. Wyniki i ograniczenia pokazujemy w raporcie; AI nadal wymaga kontroli człowieka.

Jesteśmy Undefined: Mikołaj Piech i Marcel Chudyba. Obaj mamy 18 lat i stworzyliśmy Cue od zera podczas hackathonu. Nie wiesz, co odpisać? Cue podpowie.

## Odpowiedzi na pytania jury

**Dlaczego klawiatura?** Podpowiedź jest dostępna w miejscu pisania. Nie trzeba za każdym razem kopiować historii i tłumaczyć kontekstu osobnemu narzędziu.

**Co jest Waszym wkładem?** Integracja komponentów w produkt, pamięć per czat, przepływ stylu/intencji, aktualizacje ustaleń, weryfikowalne źródła, kontrola i ewaluacja. Model bazowy, mosty komunikatorów i silnik HeliBoard są zewnętrzne i jawnie wskazane.

**Czy to czyta każdą rozmowę?** Synchronizacja pobiera dostępne prywatne czaty. Globalna zgoda AI uruchamia aktualizację pamięci dla kwalifikujących się czatów; na Androidzie można wykluczyć pojedyncze rozmowy. Sugestie wymagają działania użytkownika. Nie twierdzimy, że wcześniejsze kliknięcie analizy każdego czatu jest wymagane.

**Czy wszystko jest prywatne/offline?** Nie. Historia i pamięć są lokalne, ale analiza wysyła wybrany zakres do DeepSeek przez Supabase; odpowiedzi mogą uwzględniać zdjęcia. Informujemy o tym przy zgodzie.

**Czy źródło gwarantuje prawdę?** Nie. Cytat umożliwia sprawdzenie interpretacji; poprawny ID nie oznacza poprawnego wniosku. Użytkownik może skorygować wynik.

**Czy zmierzyliście skuteczność?** Mamy mały syntetyczny zestaw regresyjny, testy implementacji i pomiary opóźnienia endpointu. Nie mamy reprezentatywnego badania użytkowników ani dowodu skuteczności w każdej relacji.
