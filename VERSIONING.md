# 🌿 Versionamento Git — Linee guida

Linee guida da seguire per il versionamento del progetto **Apulia Library — Microservices** in ogni fase di sviluppo.

Repository: <https://github.com/davide-projects/Library-Microservice-Architecture>

---

## Modello di branch

| Branch          | Ruolo                                                                 |
|-----------------|-----------------------------------------------------------------------|
| `feature`       | branch di implementazione staccato da `dev`: ogni task/fase si sviluppa qui con i relativi commit, tenendo `dev` pulito; concluso e verificato, viene fatto confluire in `dev` |
| `dev` (default) | branch di integrazione: riceve i merge delle `feature` completate e testate, non riceve commit di lavoro diretti |
| `prod`          | snapshot stabile, raggiunto solo da merge verificati di `dev`         |
| `backup`        | copia di sicurezza allineata a `prod` (e a `dev` a lavoro concluso)   |

---

## Quando versionare

1. **A fine task** (ogni singolo item `- [ ]` o gruppo coerente di item) → commit sulla `feature` corrente: non si inizia una fase con il working tree sporco.
2. **A fine fase** (test verdi + documentazione aggiornata) → merge della `feature` in `dev` + `git push origin dev`; la `feature` conclusa può essere eliminata.
3. **Merge `dev` → `prod` + tag `vX.Y.Z` solo quando**:
   - `.\mvnw.cmd test` verde su tutti i moduli;
   - `docker-compose up -d` con tutti gli health check `healthy`;
   - test end-to-end Postman OK.
4. **Dopo ogni release** → sincronizzare `backup` come snapshot.

---

## Convenzione dei messaggi

Allinea lo storico misto `Feature:` / `feature:`:

| Prefisso | Uso |
|---|---|
| `Feature:` | nuova funzionalità o fase completata |
| `Fix:` | correzione di bug o comportamento |
| `Test:` | aggiunta/modifica di test |
| `DOCS:` | documentazione (README, TODO, jwt-task) |
| `Chore:` | build, dipendenze, configurazione, docker |

---

## Commit di lavoro

I commit di lavoro avvengono sulla `feature`, staccata da `dev` all'avvio del task:

```bash
git checkout dev
git checkout -b feature/<nome-task>   # si stacca la feature da dev

git status                            # mai committare .env, target/ o *.log (già in .gitignore)
git add <file>
git commit -m "Feature: descrizione"
git push origin feature/<nome-task>
```

A fine fase, la `feature` rientra in `dev`:

```bash
git checkout dev
git merge feature/<nome-task>
git push origin dev
git branch -d feature/<nome-task>
```

---

## Procedura di release

```bash
git checkout dev
git push origin dev                 # dev aggiornato e testato

git checkout prod
git merge dev
git tag -a vX.Y.Z -m "Release vX.Y.Z"
git push origin prod --tags

git checkout backup
git merge prod
git push origin backup
```
