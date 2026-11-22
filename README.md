# CijferNu

SOMtoday, maar dan fijn. Je cijfers, rooster, berichten en meldingen in één rustige Android-app.
Download de nieuwste `CijferNu.apk` bij [Releases](../../releases/latest); daarna werkt hij zichzelf bij.

## Wat hij kan

- **Vandaag**: welke les nu bezig is of als volgende komt, uitval, hoe morgen eruitziet, je cijfers in het kort en de volgende vakantie. (Geen huiswerk: dat zetten de docenten niet in SOMtoday.)
- **Rooster**: per week, vegen voor de volgende dag, uitval doorgestreept en lokaalwijzigingen.
- **Cijfers**: nieuwste eerst of per vak, gemiddelden per periode, een grafiek per vak en *Wat moet ik halen?*. Nieuwe cijfers kun je eerst verbergen tot je erop tikt.
- **Taken**: je eigen planner (de +-knop) voor huiswerk en toetsen die je in de les opkrijgt, met een herinnering de avond ervoor of die ochtend. Zet een docent toch iets in SOMtoday, dan staat het er ook bij.
- **Berichten**, **afwezigheid**, **leermiddelen** en je **rooster in je agenda** (iCal-link).
- **Meldingen**: nieuwe cijfers, uitval of een ander lokaal en berichten (de app kijkt ongeveer elk kwartier), plus de herinneringen voor je taken.
- Een **demo** met verzonnen gegevens, om rond te kijken zonder in te loggen.

## Hoe het werkt

Je logt in bij SOMtoday zelf (school kiezen, schoolaccount, Microsoft of Entree), net als in de officiële app (OAuth met PKCE).
De tokens blijven op je telefoon en alle verzoeken gaan rechtstreeks van de app naar SOMtoday; er zit geen server tussen.
De pagina's (`app/`) zitten in de APK; `MainActivity` geeft ze een brug (`CijferNative.api`) naar `Som.kt`, en `Meldingen.kt` kijkt met WorkManager op de achtergrond.

CijferNu is een eigen project en hoort niet bij SOMtoday of Topicus.

## Bouwen

```sh
cd android && ./gradlew assembleRelease
```

Bij elke push naar `main` bouwt GitHub Actions een ondertekende APK en zet die als release online (`v1.0.N`). De app kijkt daar zelf naar updates.
