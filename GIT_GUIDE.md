# Guia Git — PDProject2025

---

## Fluxo de Branches
- **main** → código estável (release).
- **dev** → integração de features.
- **feature/...** → cada funcionalidade é feita numa branch própria.

---

## Comandos Git Essenciais

### Primeira vez 
```bash
git init
git branch -M main
git remote add origin https://github.com/DaviGama08/PDProject2025.git
git push -u origin main
```

### Ver estado do repositório
```bash
git status
```

### Ver branches
```bash
git branch -a
```

### Criar e mudar para branch dev
```bash
git checkout -b dev
git push -u origin dev
```

### Criar nova feature (a partir da dev)
```bash
git checkout dev
git pull origin dev
git checkout -b feature/nome-da-feature
git push -u origin feature/nome-da-feature
```
### Commit básico
```bash
git add .
git commit -m "feat: descrição curta"
git push
```

### Atualizar branch local com remoto
```bash
git pull
```