# Agentske tehnologije - Projekat

## Tipovi Agenata

### 1) Ping/Pong agenti (Ping, Pong)

Nakon pokretanja aplikacije kreirati Ping i Pong agente i poslati REQUEST poruku pong agentu od strane Pinga.

Ping agent reaguje na REQUEST i INFORM tipove poruka, a Pong agent na REQUEST.
REQUEST poruka se uz pomoc JMS Queue-a (vazi za sve agente) prosledjuje Pong agentu sa performativom REQUEST. Kada Pong primi poruku, salje 
INFORM poruku natrag ka Pingu.

### 2) Contract-Net protokol(Initiator, Participant)

Nakon pokretanja aplikacije kreirati Initiator agenta i proizvoljan broj Participant agenata. Inicijator agenat sadrzi 
sledece performative: REQUEST, RESUME, REFUSE, PROPOSE, FAILURE, INFORM. Participant agenat sadrzi sledece performative:
CALL_FOR_PROPOSAL, REJECT_PROPOSAL, ACCEPT_PROPOSAL. 

Kada Initiator agenat posalje REQUEST poruku, dobavljaju se svi postojeci
Participant agenti i svakom od njih se salje CALL_FOR_PROPOSAL poruka. ContactNet sesija se 
smesta u mapu u formi kljuc=Id konverzacije, vrednost=ponude agentu.

Nakon sto Participant agent primi CALL_FOR_PROPOSAL poruku, vrsi se random potvrda ili odbijanje ponude. Ukoliko je doslo
do potvrde, salje se PROPOSE poruka Initiator agentu sa random vrednostcu u opsegu [0-100]. U slucaju odbijanja, salje se REFUSE poruka.

U sledecem koraku Initiator agent prima PROPOSE ili REFUSE poruku. U slucaju REFUSE obavestave se da je agent odbio ponudu.
U slucaju PROPOSE, ponuda se dodaje u sesiju i pokrece se uspavljujuca nit koja traje 10 sekundi.

Nakon 10 sekundi, pokrece se RESUME performativa u kojoj se bira optimalni agent na osnovu najvise vrednosti ponude. 
Participant agent koji je prihvacen, kao i initiator se obavestavaju slanjem ACCEPT_PROPOSAL poruke, dok agenti koji nisu prihvaceni 
dobijaju REJECT_PROPOSAL poruku.

### 3) Default zadatak (resavanje ML problema) - Predikcija rezultata fudbalskog meca
#### 3.1) Locator, Predictor agenti
Predictor agenat podrzava PREDICT, INFORM i RESUME performative, dok lokator podrzava SEARCH performativu. Kreiranje predictora
na master nodu i slanja SEARCH poruke proizvoljnom broju lokator agenata na razlicitim hostovima se izvlace podaci o fudbalskim utakmicama.

Podaci su smesteni u data.csv fajlu i dobijeni su sa sajta Football-data.co.uk. Nakon sto prediktor agent posalje SEARCH poruku lokatoru,
parsiraju se podaci iz fajla u salje se INFORM poruka natrag ka prediktoru sa listom Match objekata. 

Match model se sastoji od polja key, homeTeam, awayTeam, homeGoals, awayGoals i result. Kada prediktor primi INFORM poruku,
unique vrednosti meceva se cuvaju u listi.

Poslednje sto treba uraditi je poslati PREDICT zahtev predictor agentu. Prilikom ovog zahteva se kreira novi csv fajl u bin folderu Wildfly servera sa
svim podacima koji se nalaze u listi. Nakon toga se okida RESUME poruka koja pokrece python proces uz pomoc skripte koja se takodje nalazi u bin
folderu servera.

#### 3.2) ML proces

Za predikciju je koriscen statisticki model koji se oslanja na Poasonovu raspodelu. Sustina modela je da procenjuje kvalitet napada i odbrane tima na
osnovu broja postignutih ili primljenih golova i daje predikciju. 

Primetno je da domaci timovi postizu vise golova u odnosu na gostujuce, sto je objasnjeno takozvanim "home team advantage"
fenomenom, koji nije specifican samo za fudbal. Ovo je dobra prilika da uvedemo pojam Poasonove raspodele. Ona predstavlja
diskretnu raspodela verovatnoće koja izražava verovatnoću da se određeni broj događaja dogodio u fiksnom intervalu vremena (90 minuta).
Vazna pretpostavka je da su dogadjaji nezavisni, sto znaci da golovi ne postaju cesci ukoliko je vec postignuto nekoliko golova.

Ovakav statisticki model mozemo koristiti kako bi predvideli verovatnocu odredjenih dogadjaja, npr. verovatnoca neresenog rezultata
je suma verovatnoca gde oba tima daju isti broj golova.

Za kreiranje modela koriscena je scipy biblioteka i smf.glm(...) funkcija koja se zasniva na regresiji. Prikazom model.summary()
mozemo videti da koaficijent coef ima pozitivnu vrednost za domace timove, ali i da penalizuje na osnovu kvaliteta protivnickog tima.

Finalna predikcija sabira predikcije pojedinacnih verovatnoca broja postignutih golova i vraca izlaz u formi: 
Verovatnoca pobede domaceg tima = x, verovatnoca neresenog rezultata = y, verovatnoca pobede gostujuceg tima = z. Naravno, x + y + z = 1