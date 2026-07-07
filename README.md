# DUO Nakijken

Proof-of-concept voor samenwerking met DUO rond **CheckMate nakijken** en het **stapeling-/clusteringalgoritme** (response scan ordering).

Bevat:
- **Java/Spring Boot backend** met het clustering-algoritme als library + REST-endpoints
- **Angular 21 frontend** met UNO-stijl componenten (placeholder tot `uno-ng` registry-toegang)
- **Demo-data** van CheckMate staging: delivery `ZSVY`, assessment `demo` (Fotosynthese)

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
2. Installeer `@uno/ng`, `@uno/styles` en `@uno/core`
3. Vervang componenten in `frontend/src/app/uno/` door echte UNO imports

## Lokaal Docker-image testen

```bash
docker build -t duo-nakijken .
docker run --rm -p 8080:8080 -e DEMO_MANUAL_SCORES_PATH=/tmp/demo-manual-scores.json duo-nakijken
# → http://localhost:8080/review?delivery=ZSVY
```

Het image bundelt Angular UI en Spring API op dezelfde poort. Scores worden naar het pad in `DEMO_MANUAL_SCORES_PATH` geschreven.
