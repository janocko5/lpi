Cvičenie 8
==========

**Riešenie odovzdávajte podľa
[pokynov na konci tohoto zadania](#technické-detaily-riešenia)
do stredy 17.4. 23:59:59.**

Súbory potrebné pre toto cvičenie si môžete stiahnuť ako jeden zip
[`pu08.zip`](https://github.com/FMFI-UK-1-AIN-412/lpi/archive/pu08.zip).

Príprava na SAT solver
----------------------

Na tomto a nasledujúcom cvičení naprogramujete SAT solver, ktorý zisťuje, či je
vstupná formula (v konjunktívnej normálnej forme) splniteľná.

Na prednáške ste videli základnú kostru metódy DPLL, ktorej hlavnou ideou je
propagácia klauzúl s iba jednou premennou (_jednotková klauzula_,
<i lang="en">unit clause</i>). Tá ale hovorí o veciach ako *vymazávanie
literálov* z klauzúl a *vymazávanie klauzúl*, čo sú veci, ktoré nie je také
ľahké efektívne (či už časovo alebo pamäťovo) implementovať, hlavne ak počas
<i lang="en">backtrack</i>ovania treba zmazané literály resp. klauzuly správne
naspäť obnovovať.

Na tomto cvičení si preto naprogramujeme techniku *sledovaných literálov*,
ktorá výrazne zjednodušuje „menežment“ literálov, klauzúl a dátových štruktúr.
Na budúcom cvičení ju použijeme pri implementácii samotnej metódy DPLL.

## Sledované literály (<i lang="en">watched literals</i>)

Základným problémom pri DPLL metóde je vedieť povedať, či klauzula:
*   už obsahuje nejaký literál ohodnotený `true` (a teda je už _splnená_), alebo
*   obsahuje práve jeden neohodnotený literál (a teda je _jednotková_), alebo
*   už má všetky literály ohodnotené `false` (a teda je _nesplnená_ a treba
    <i lang="en">backtrack</i>ovať).

Namiesto mazania / obnovovania literálov a klauzúl budeme v každej klauzule
_sledovať_ (označíme si) dva jej literály (<i lang="en">watched literals</i>), pričom budeme požadovať (pokiaľ je to možné), aby každý z nich
- buď ešte nemal priradenú hodnotu,
- alebo mal priradenú hodnotu `true`.

Ak nejaký literál počas prehľadávania nastavíme na `true`, tak očividne
nemusíme nič meniť. Ak ho nastavíme na `false` (lebo sme napríklad jeho
komplement (negáciu) nastavili na `true`), tak pre každú klauzulu, v ktorej je
sledovaný, musíme nájsť nový literál, ktorý spĺňa horeuvedené podmienky. Môžu
nastať nasledovné možnosti:
- našli sme iný literál, ktorý je buď nenastavený, alebo je `true`, odteraz
  sledujeme ten,
- nenašli sme už literál, ktorý by spĺňal naše podmienky (všetky ostatné sú
 `false`):
    - ak druhý sledovaný literál bol `true`, tak to nevadí (klauzula je aj tak splnená),
    - ale ak bol druhý literál _nenastavený_, tak nám práve vznikla jednotková klauzula, a mali by sme ho
      nastaviť na `true`.
    - podľa toho, ako presne implementujeme propagáciu, sa nám môže stať, že sa
      dostaneme do momentu, že aj druhý sledovaný literál sa práve stal `false`,
      v tom prípade sme práve našli nesplnenú klauzulu a musíme <i lang="en">backtrack</i>ovať.

Bonus navyše: ak <i lang="en">backtrack</i>ujeme (meníme nejaký `true` alebo `false` literál naspäť
na nenastavený), tak nemusíme vôbec nič robiť (so sledovanými literálmi v klauzulách;
samotný literál / premennú samozrejme musíme korektne „odnastaviť“).

### Implementácia sledovania

Sledovanie literálov doimplementujeme do tried na reprezentáciu formúl v CNF
z minulých cvičení.

Trieda `Literal` dostala metódy `setTrue` a `unset`, ktorými literál nastavíme
na pravdivý, resp. jeho nastavenie zrušíme. Metódy `isSet()` a `isTrue()` 
zase zisťujú, či literál má nastavenú pravdivostnú hodnotu, resp. či je
pravdivý. `Literal` má aj novú metódu `watchedIn()`, ktorá vráti množinu
sledujúcich klauzúl (teda takých, v ktorých je literál sledovaný).

Samotné sledovanie literálov v klauzulách implementujte v triede `Clause`
v metódach `setWatch` a `findNewWatch`. Táto trieda má nový atribút `watched`
(a jeho accessor `watched()`), dvojprvkové pole sledovaných literálov.

Metóda `void setWatch(int index, Literal lit)` nastaví `index`-tý prvok poľa
`watched` na literál `lit`, ktorý sa v klauzule musí vyskytovať.
Okrem toho literálu `lit` pridá túto klauzulu do jeho množiny sledujúcich
klauzúl (`lit.watchedIn()`). Ak navyše klauzula predtým nejaký literál s týmto
`index`-om sledovala, odoberie ju z jeho množiny sledujúcich klauzúl.

Metóda `boolean findNewWatch(Literal old)` nahradí doteraz sledovaný literál
`old`, ak je to potrebné, teda ak je literál `old` nastavený a nepravdivý.
V tom prípade sa pokúsi nájsť nový pravdivý alebo nenastavený literál,
ktorý ešte nie je sledovaný v tejto klauzule a pomocou metódy `setWatch` ním
nahradí literál `old` a vráti `true`. Ak sa jej to podarí, alebo literál `old`
netreba nahrádzať, metóda `findNewWatch` vráti `true`. Inak (všetky literály
sú nepravdivé alebo už sledované) vráti `false`.

### Implementácia nastavovania literálov

Na nastavovanie a „odnastavovanie“ literálov počas behu algoritmu DPLL
implementujeme v triede `Theory` metódy `setLiteral` a `unsetLiteral`.

Metóda `boolean setLiteral(Literal l, Set<UnitClause> units)` nastaví
literál `l` na pravdivý a vo všetkých klauzulách, v ktoré sledujú jeho
opačný literál `l.not()`, sa pokúsi nájsť nový literál na sledovanie (pomocou
metódy `findNewWatch`). Všetky klauzuly, o ktorých pritom zistí, že už majú iba
jeden nenastavený literál, pridá do množiny jednotkových klauzúl `units`. Ak sa
pri hľadaní sledovaných literálov zistí, že niektorá klauzula už má všetky
literály nastavené na nepravdivé, metóda `setLiteral` vráti `false`. Inak vráti
`true`.

Metóda `public void unsetLiteral()` „odnastaví“ naposledy nastavený literál.

## Technické detaily riešenia

Riešenie odovzdajte do vetvy `pu08` v adresári `prakticke/pu08`.
Odovzdávajte knižnicu [`Cnf.java`](java/Cnf.java).
Program [`WatchedLiteralsTest.java`](java/WatchedLiteralsTest.java)
musí korektne zbehnúť s vašou knižnicou.

Či ste správne vytvorili pull request si môžete overiť
v [tomto zozname PR pre pu08](https://github.com/pulls?utf8=%E2%9C%93&q=is%3Aopen+is%3Apr+user%3AFMFI-UK-1-AIN-412+base%3Apu08).

Odovzdávanie riešení v iných jazykoch konzultujte s cvičiacimi.
