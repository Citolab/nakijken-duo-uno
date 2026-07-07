# DUO Nakijken

Proof-of-concept voor samenwerking met DUO rond **CheckMate nakijken** en het **stapeling-/clusteringalgoritme** (response scan ordering).

Bevat:
- **Java/Spring Boot backend** met het clustering-algoritme als library + REST-endpoints
- **Angular 21 frontend** met UNO-stijl componenten (placeholder tot `uno-ng` registry-toegang)
- **Demo-data** van CheckMate staging: delivery `ZSVY`, assessment `demo` (Fotosynthese)

## Antwoorden op jullie vragen

### 1. Hoe gebruik je het algoritme?

**Beide opties zijn mogelijk:**

| Optie | Wanneer |
|-------|---------|
| **Java library** | Integratie in een bestaande Java/Spring-applicatie (bijv. DUO HOST/MBOS) |
| **HTTP API** | Andere stacks, microservices, of snelle integratie zonder library-koppeling |

In deze repo staat de library in `backend/.../ordering/ResponseScanOrderingService.java`.
De REST-laag roept die aan via `GET /api/deliveries/{deliveryId}/item-stats?sortingMode=scan`.

### 2. GUI met DUO UNO componenten?

De frontend gebruikt nu **UNO-compatible placeholder componenten** (`frontend/src/app/uno/`) met DUO design tokens (`#154273`).
Zodra jullie toegang hebben tot het DUO npm registry (`uno-ng`, `uno-ng-header`, etc.), kunnen die placeholders 1-op-1 worden vervangen.

## Projectstructuur

```
DUO-nakijken/
├── backend/          Spring Boot API + clustering library
├── frontend/         Angular 21 nakijken-UI
└── README.md
```

## Starten

### Backend (poort 8080)

Vereist: Java 21, Maven

```bash
cd backend
mvn spring-boot:run
```

### Frontend (poort 4200)

```bash
cd frontend
npm install
npm start
```

Open: [http://localhost:4200/review?delivery=ZSVY](http://localhost:4200/review?delivery=ZSVY)

De dev-server proxy't `/api` naar `http://localhost:8080`.

## API (demo)

| Endpoint | Beschrijving |
|----------|--------------|
| `GET /api/assessments/demo` | Assessment metadata + items |
| `GET /api/deliveries/ZSVY/item-stats?sortingMode=scan\|grading` | Geclusterde/geordende antwoorden per item |
| `GET /api/assessments/demo/items/{itemId}/scoring-definition` | Antwoordmodel |
| `POST /api/aigrading/teacher-score` | Docent-score aanpassen (in-memory) |
| `POST /api/assessments/demo/responses/confirm-scores` | Antwoorden bevestigen (in-memory) |

## Demo-functionaliteit

Vergelijkbaar met [CheckMate demo](https://checkmate-staging.citolab.nl/demo/assessment/demo/review?delivery=ZSVY):

- Nakijken per open vraag (`extendedTextEntry`)
- Sortering op **Gelijkheid** (scan/cluster) of **Score**
- Visuele clustering via `familyId`
- Antwoordmodel, voortgang, score-knoppen, bevestigen
- QTI-items/resources via `frontend/public/demo-package/`

## UNO integratie

1. Configureer `.npmrc` voor het DUO registry (zie [uno.duo.nl](https://uno.duo.nl))
2. Installeer `@duo/uno-ng` packages
3. Vervang componenten in `frontend/src/app/uno/` door echte UNO imports

## Herkomst data/algoritme

- Demo fixture: geëxporteerd van CheckMate production delivery (anonymized), zelfde dataset als Cito admin demo
- Algoritme: port van `QtiAzureBackend` `ResponseScanOrderingService` (deterministische scan ordering pipeline)

## Deploy op Fly.io (gratis tier)

Eén Docker-image serveert **Angular UI + Spring API** op dezelfde URL (`/api/...`).

### Vereisten

- [Fly CLI](https://fly.io/docs/hands-on/install-flyctl/)
- Fly account (`fly auth signup`)

### Eerste deploy

```bash
cd DUO-nakijken

# Maak app aan (kies regio ams, sla Dockerfile/fly.toml over als die al bestaan)
fly launch --no-deploy

# Persistente opslag voor docent-scores (1 GB gratis volume)
fly volumes create duo_nakijken_data --size 1 --region ams

# Deploy
fly deploy

# Open in browser
fly open /review?delivery=ZSVY
```

Na deploy: `https://<jouw-app>.fly.dev/review?delivery=ZSVY`

### Handige commando's

```bash
fly logs          # live logs
fly status        # machine + health
fly ssh console   # shell in container
fly scale count 1 # machine aan laten staan (minder cold start)
```

### Lokaal Docker-image testen

```bash
docker build -t duo-nakijken .
# Het image gebruikt het fly-profiel, dat scores naar /data schrijft.
# Lokaal bestaat dat pad niet, dus geef een schrijfbaar pad mee:
docker run --rm -p 8080:8080 -e DEMO_MANUAL_SCORES_PATH=/tmp/demo-manual-scores.json duo-nakijken
# → http://localhost:8080/review?delivery=ZSVY
```

### Opmerkingen

- **Cold start:** gratis machines stoppen na idle; eerste request kan ~5–10 s duren (`auto_start_machines` staat aan).
- **Scores:** worden op het Fly-volume `/data/demo-manual-scores.json` bewaard.
- **Geen AI score suggestions** in demo-data; puur handmatig nakijken.
- **Geen authenticatie:** iedereen met de URL kan scores aanpassen of wissen. Prima voor deze demo (alleen fixture-data), maar deel de URL bewust.
