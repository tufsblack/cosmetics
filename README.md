# Cosmetics 1.2.0

3D-kronen en 3D-hoeden, kill-effecten en boog-effecten voor je SMP, met een menu voor spelers (VIP's)
en een duidelijk staff-menu om cosmetics te geven en af te pakken.

## Wat is nieuw in 1.1
- **40 3D-kronen** (5 vormen × 8 uitvoeringen) en **23 eigen 3D-hoeden**, met een resourcepack erbij.
- Kill-effecten hebben in het menu **aparte schakelaars: bij spelers / bij mobs**. De config is nu alleen nog de grens voor de hele server.
- Boog-effecten en kill-effecten zijn **herschreven en zichtbaarder**: het spoor loopt nu ononderbroken door (ook bij een snelle pijl), en elk effect heeft een eigen klap bij inslag.
- **`/cosadmin effect`** om elk effect meteen te testen, zonder het te bezitten.
- Het **staff-menu** is opnieuw gemaakt: tabs per soort, duidelijke kleuren en uitleg, en een bevestiging bij afpakken.

## Installeren
1. Upload de map naar GitHub; de workflow bouwt `Cosmetics.jar` (Actions → Build Cosmetics → Artifacts).
2. Zet de jar in `plugins/` van de server waar spelers hun cosmetics gebruiken (survival).
3. Zet `CosmeticsPack.zip` online (zie "Het resourcepack") en vul de link in `config.yml` in.
4. Herstart. Daarna rechten geven met LuckPerms (zie onder).

## Snelstart (alles zit in deze zip)
1. Upload de **inhoud** van deze zip naar een **publieke** GitHub-repository (inclusief `CosmeticsPack.zip`).
2. Zet in `config.yml` bij `pack: url:` je link: `https://raw.githubusercontent.com/JOUWNAAM/JOUWREPO/main/CosmeticsPack.zip`
3. Laat de workflow bouwen, zet `Cosmetics.jar` in `plugins/` (die van de map kopieer je mee: de `config.yml` hierboven is de standaard), herstart. Klaar.

## Het resourcepack (voor de 3D-kronen en 3D-hoeden)
De 3D-modellen zitten in `CosmeticsPack.zip`. Spelers hebben het pack nodig om ze te zien; de plugin stuurt het automatisch bij het joinen.
1. Zet `CosmeticsPack.zip` op een plek met een **directe download-link**. Het makkelijkst is een GitHub *Release*:
   repository → *Releases* → *Draft a new release* → sleep het zip-bestand erin → publiceren → rechtermuisknop op het bestand → link kopiëren.
2. Vul in `plugins/Cosmetics/config.yml`:
   ```yaml
   pack:
     url: "https://github.com/JOUW-NAAM/JOUW-REPO/releases/download/v1/CosmeticsPack.zip"
     sha1: "91a3f4f28a62c9583e92439e00285767e34d18c7"
   ```
   (de sha1 staat ook in `CosmeticsPack.sha1`; hij moet bij elke nieuwe versie van het zip-bestand opnieuw ingevuld worden)
3. `/cosadmin reload`. Daarna krijgen spelers het pack bij het joinen. Wie het mist: `/cosmetics pack` of de knop in het menu.
- **Zonder pack** zien spelers een **gewone helm** (goud, ijzer, leer, ...) in plaats van de 3D-kroon of 3D-hoed, nooit een kapot item.
- Stuur je het pack al op een andere manier (server.properties, Velocity, andere plugin)? Zet dan `pack.extern: true`.
- Lobby én survival? Zet de plugin alleen op de server waar cosmetics werken; het pack wordt daar verstuurd.
- Het pack is gemaakt voor Minecraft 26.1 en nieuwer (`pack_format` 84). Staat er bij een speler een melding dat het pack voor een andere versie is, dan is dat alleen een waarschuwing.

## Dezelfde cosmetics op lobby en survival (database)
Zet de plugin op beide servers en laat ze dezelfde MySQL/MariaDB-database gebruiken. Wat een speler krijgt, kiest en instelt staat dan overal gelijk
(binnen ongeveer 5 seconden; bij het joinen meteen). De plugin maakt de tabel zelf aan.
1. Maak in je database een gebruiker aan (of gebruik een bestaande) met rechten op één database.
2. Zet op **beide** servers in `plugins/Cosmetics/config.yml`:
```yaml
database:
  aan: true
  host: "..."
  poort: 3306
  naam: "..."
  gebruiker: "..."
  wachtwoord: "..."
```
3. Herstart beide servers. In de console staat `Database-sync staat aan`.
- Zet het wachtwoord alleen in de config op de server zelf, nooit in GitHub.
- De **eerste keer** wint de database: staat een speler al in de database, dan wordt zijn lokale gegevens vervangen. Een kopie van je oude bestand blijft bewaard als `spelers.yml.voor-database`. Begin dus met de server waar de meeste gegevens op staan.
- Valt de database even uit, dan werkt alles lokaal gewoon door en wordt het daarna ingehaald.
- Rangen (LuckPerms) horen bij elke server zelf: laat ze op beide servers kloppen (zelfde LuckPerms-database of dezelfde permissies).

## Wat zit erin?
- **40 kronen:** klassiek, hoog, koninklijk (met hermelijn, bogen en rijksappel), doornenkroon en tiara, elk in goud, zilver, diamant, smaragd, robijn, amethist, netherite en regenboog.
- **34 hoeden:** 23 in 3D (hoge hoed, tovenaar, cowboy, piraat, kok, feestmuts, kerstmuts, heiligenschijn, duivelshoorns, kattenoren, konijnenoren, viking, propellerpet, astronaut, ninjaband, regenwolk, taart, badeend, UFO, regenboog, bloemenkrans, alien, drakenhelm) en 11 gewone (pompoen, drakenkop, creeper, skelet, zombie, wither-skelet, piglin, TNT, baken, diamantblok, glazen bol).
  Kroon en hoed delen het hoofd: je draagt er één tegelijk. Ze staan **niet in je inventaris**; je echte helm blijft van jou.
  Je ziet je eigen hoed zelf niet (ook niet in F5): anders zou de client hem in je helm-slot tonen en kun je hem pakken of dupliceren. Alle anderen zien hem wel. Wil je dat toch, zet `hoeden.zelf-zien: true`.
- **10 kill-effecten:** bliksem, harten, vlammen, zielen, explosie, totem, sterren, vuurwerk, kleurbol, spiraal.
  Alleen deeltjes en geluid: er gaat niets kapot en niemand krijgt schade.
- **10 boog-effecten:** een spoor achter je pijl (vuur, harten, kleur, regenboog, sterren, noten, wolk, elektrisch, ender, zielenvuur) met een eigen klap bij inslag. Werkt ook voor kruisbogen.

## Rechten (LuckPerms)
| Permissie | Wat |
|---|---|
| `cosmetics.use` | Mag `/cosmetics` openen (je ziet alleen wat je bezit). Standaard **niemand**. |
| `cosmetics.staff` | Staff-menu en `/cosadmin`. Standaard alleen ops. |
| `cosmetics.crown.klassiek_goud` | Eén cosmetic via een rang. Formaat: `cosmetics.<soort>.<id>` |
| `cosmetics.crown.*` / `cosmetics.hat.*` / `cosmetics.kill.*` / `cosmetics.bow.*` | Alle cosmetics van één soort |
| `cosmetics.*` | Alle cosmetics |

Voorbeeld voor een VIP-rang met alle kronen, alle hoeden en één kill-effect:
```
/lp group vip permission set cosmetics.use true
/lp group vip permission set cosmetics.crown.* true
/lp group vip permission set cosmetics.hat.* true
/lp group vip permission set cosmetics.kill.bliksem true
/lp group staff permission set cosmetics.staff true
```
Alle namen vind je in het staff-menu onder **Alle cosmetics** of met `/cosadmin lijst`.

## Twee manieren om aan een cosmetic te komen
1. **Via een rang** (LuckPerms-permissie). Pak je die weg in LuckPerms, dan stopt de cosmetic bij de speler automatisch.
2. **Gegeven door staff** met het menu of `/cosadmin geef`. Dit onthoudt de plugin zelf (`spelers.yml`).
Een speler heeft een cosmetic als één van de twee klopt. Afpakken in het menu haalt alleen wat staff gaf weg;
een rang-recht blijft staan tot je het in LuckPerms weghaalt (het menu zegt dat erbij).

## Het speler-menu (`/cosmetics`)
- Hoofdmenu: Kronen, Hoeden, Kill-effecten, Boog-effecten, alles uitzetten, alles aan/uit en (als er een pack is) een pack-knop.
- Lijst: klik = kiezen, nog een keer = uitzetten. Alleen wat je bezit staat erin
  (`toon-vergrendeld: true` laat ook de rest zien als vergrendeld).
- **Aanpassen** (kill en boog): kleur (10 kleuren + regenboog), sterkte (laag/normaal/hoog),
  zichtbaar voor iedereen of alleen voor jou, en een **Testen**-knop.
  Bij kill-effecten ook **Bij spelers aan/uit** en **Bij mobs aan/uit**: elke speler kiest dat zelf.

## Het staff-menu (`/cosadmin`)
- **1. Speler kiezen** (online) of **2. Speler zoeken** (typ de naam, ook offline). **3. Alle cosmetics** is een overzicht per soort.
- Per speler: een **tab per soort** (Kronen, Hoeden, Kill-effecten, Boog-effecten). **Groen** = heeft hij, **rood** = heeft hij niet.
  **Linksklik** = geven of afpakken, **rechtsklik** = laten stoppen met dragen. Onderaan: alle van deze tab geven/afpakken,
  stoppen met dragen, instellingen resetten, en ALLES geven/afpakken. Afpakken vraagt een tweede klik ter bevestiging.
- Hoofdmenu: systeem aan/uit voor iedereen, config opnieuw laden, het resourcepack naar iedereen sturen.

## Commando's
Spelers: `/cosmetics` (`/cos`, `/cosmetica`) · `/cosmetics pack` (pack opnieuw laden).
Staff: `/cosadmin` · `/cosadmin geef <speler> <crown:klassiek_goud | alle>` · `/cosadmin neem <speler> <...>` ·
`/cosadmin lijst [speler]` · `/cosadmin reset <speler>` · `/cosadmin reload` ·
`/cosadmin effect <kill:bliksem | bow:vlam | alle-kill | alle-boog>` · `/cosadmin pack [speler|alle]`.
Een korte naam die in twee soorten bestaat (zoals `harten`) schrijf je volledig: `kill:harten`.

### Een effect testen
`/cosadmin effect bow:vlam` laat een onzichtbare pijl 30 blokken voor je uit vliegen met het spoor en de klap, precies zoals bij een echte pijl.
`/cosadmin effect alle-boog` en `alle-kill` spelen alle effecten na elkaar af (de naam staat in je actiebalk).
Alleen jij ziet het, en je hoeft het effect niet te bezitten. Zie je daar iets niet, dan ligt het niet aan de rechten maar aan de client
(bij *Opties → Videoinstellingen → Deeltjes* op **Minimaal** zie je veel minder deeltjes).

## config.yml
Alles staat uitgelegd in het bestand: aan/uit voor het hele systeem, vergrendelde cosmetics tonen,
cosmetics uitschakelen (`uitgeschakeld: ["hat:tnt"]`), het resourcepack (`pack`), afstand voor hoeden,
kill-effecten bij spelers en mobs (de grens voor de server; spelers kiezen daarbinnen zelf) en limieten voor boogsporen.

## Goed om te weten
- Hoeden en kronen werken met nep-uitrusting die alleen naar de clients gestuurd wordt (er komt niets in je inventaris).
  Draag je een echte helm, dan zien anderen de hoed erover; zet je een helm aan of uit, dan klopt het binnen ongeveer 1 seconde.
- Een 3D-kroon of -hoed is een gewoon item met een eigen model in het pack. Moet een speler het pack nog laden, dan ziet hij zolang een vervangende helm.
- Op de lobby heb je de plugin niet nodig, tenzij je daar ook hoeden wilt.
- Gegevens staan in `spelers.yml`. Een kapot bestand wordt als `spelers.yml.kapot-<tijd>` bewaard.
- Van v1.0 naar v1.1: de gewone hoeden **kroon** en **taart** zijn vervangen door de 3D-kronen en de 3D-taart.
  Wie `hat:taart` had, heeft nu de 3D-taart; `hat:kroon` bestaat niet meer (gebruik `crown:klassiek_goud`). De rest blijft werken.
