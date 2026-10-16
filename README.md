# ⚽ Fanta Calcio Intelligence

[![Architecture](https://img.shields.io/badge/Architecture-Microservices%20%2F%20Monorepo-blue.svg)](#-architettura-del-sistema)
[![Frontend](https://img.shields.io/badge/Frontend-Next.js%20%2F%20React-000000.svg?logo=next.js)](#-stack-tecnologico)
[![Backend](https://img.shields.io/badge/Backend-Java%20%2F%20Spring%20Boot-6DB33F.svg?logo=spring)](#-stack-tecnologico)
[![AI Engine](https://img.shields.io/badge/AI%20%2F%20Analytics-Python-3776AB.svg?logo=python)](#-stack-tecnologico)
[![Database](https://img.shields.io/badge/Database-PostgreSQL-4169E1.svg?logo=postgresql)](#-stack-tecnologico)
[![VPN](https://img.shields.io/badge/Network-Tailscale%20VPN-orange.svg)](#-infrastruttura-e-accesso-remoto)

> **Fanta Calcio Intelligence** è una piattaforma analitica avanzata e modulare progettata per supportare i fanta-allenatori nella gestione strategica della rosa, nell'asta in tempo reale e nell'ottimizzazione delle scelte di formazione settimanali tramite modelli predittivi e metriche statistiche avanzate.

---

## 🎯 Visione & Obiettivi del Progetto

Nel panorama del Fantacalcio moderno, le decisioni basate esclusivamente sull'intuito lasciano spazio all'analisi quantitativa dei dati (*Data-Driven Decision Making*). 

Il sistema combina ingegneria dei dati e sviluppo full-stack per offrire:
* **Algoritmi di Data Identity & Reconciliation**: Consolidamento e pulizia dei dati provenienti da fonti eterogenee (Fantacalcio, Transfermarkt, FBref) tramite script Python dedicati.
* **Predictive Analytics**: Stima delle probabilità di voto, appetibilità in asta e trend di prestazione dei calciatori.
* **Smart Dashboard**: Un'interfaccia utente moderna e reattiva per un'esperienza d'uso fluida sia da desktop che da mobile.

---

## 🏗️ Architettura del Sistema

Il progetto adotta un approccio **Monorepo** strutturato in moduli indipendenti:

```text
├── frontend/               # UI Reattiva e performante sviluppata in Next.js / React
├── backend/                # API REST, Business Logic e migrazioni DB (Java / Spring Boot + Flyway)
├── ai-service/             # Engine di analisi predittiva e machine learning (Python)
├── scripts/                # Pipeline di data ingestion, web scraping e identity matching
└── data/                   # Data catalog certificato e dataset consolidati
```

### Componenti Principali
1. **Frontend (`/frontend`)**: Dashboard interattiva ottimizzata per la consultazione rapida delle statistiche di gioco.
2. **Backend Services (`/backend`)**: Core aziendale per la gestione delle leghe, rose, calcolo dei punteggi e persistenza sicura tramite PostgreSQL.
3. **Analytics & Scraper Engine (`/ai-service` & `/scripts`)**: Pipeline in Python per la normalizzazione degli alias e la riconciliazione automatica delle identità dei calciatori.

---

## 🛠️ Stack Tecnologico

* **Frontend**: Next.js, React, Tailwind CSS
* **Backend**: Java 17+, Spring Boot, Spring Data JPA, Flyway DB Migration
* **Database**: PostgreSQL
* **Data Processing & AI**: Python (Pandas, Scikit-learn, Script di scraping mirati)
* **Testing & Quality**: JUnit, Playwright
* **Infrastruttura & Networking**: Oracle Cloud (Hosting remoto) & **Tailscale VPN** (Accesso sicuro multi-dispositivo)

---

## 🚀 Funzionalità Chiave

* 📊 **Identity Resolution Engine**: Sistema di matching automatico per riconciliare i calciatori tra diverse fonti dati senza duplicati o disallineamenti di listone.
* 📈 **Asta Manager & Live Stats**: Dashboard di supporto decisionale in tempo reale durante le aste di riparazione e di inizio stagione.
* 🔮 **Formazione Ideale**: Algoritmo per la selezione dell'undici titolare basato su indici di schierabilità, stato di forma e difficoltà del match.
* 📱 **Accesso Remoto Sicuro**: Infrastruttura configurata tramite **Tailscale VPN** per consentire l'interrogazione e la gestione dell'app da mobile o da postazioni di lavoro remote in totale sicurezza.

---

## ⚙️ Installazione e Configurazione Locale

### Prerequisiti
* Node.js (v18+)
* Java JDK 17+
* Python 3.10+
* PostgreSQL

### 1. Clona il repository
```bash
git clone https://github.com/antozito2845-design/fantacalcio-intelligence.git
cd fantacalcio-intelligence
```

### 2. Configura le variabili d'ambiente
Copia i file di esempio e configura le credenziali locali:
```bash
cp .env.example .env
cp frontend/.env.example frontend/.env.local
```

### 3. Avvio dei Servizi
* **Frontend:**
  ```bash
  cd frontend
  npm install
  npm run dev
  ```
* **Backend:**
  ```bash
  cd backend
  ./mvnw spring-boot:run
  ```

---

## 🔒 Sicurezza e Best Practices
* Nessun segreto o chiave API esposta (gestione rigorosa tramite `.env` e `.gitignore`).
* Migrazioni del database gestite in modo sicuro e tracciabile tramite **Flyway**.
* Isolamento dei dati di test e backup locali esclusi dal versionamento pubblico.

---

## 👨‍💻 Autore & Contatti

**Antonio Zito**
* **GitHub**: [@antozito2845-design](https://github.com/antozito2845-design)
* **LinkedIn**: [Antonio Zito](https://www.linkedin.com/in/antonio-zito-)

---
> *Progetto sviluppato a scopo dimostrativo per l'ingegneria del software e l'analisi dati avanzata.*