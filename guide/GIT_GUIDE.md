# Guia Git — PDProject2025

---

## Fluxo de Branches
- **main** → código estável (release).
- **dev** → integração de features.~~~~
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
O pull é um fetch(busca atualização do remoto) + merge
git checkout dev
git pull origin dev
git checkout -b feature/nome-da-feature
git push -u origin feature/nome-da-feature
```

### Fazer merge 
```bash
git fetch
git checkout dev
git pull
git merge feature/minha-feature

Se abrir Vim e digitamos:
:wq 
Para concluir o merge
Sair sem salvar:
:q!

Caso contrário:
git status
git commit -m "merge: ..."

```

### Commit básico
```bash
git add .
git commit -m "feat: descrição curta"
git push
```

### Commit básico
```bash
# apagar localmente
git branch -d feature/model
# apagar do GitHub (remoto)
git push origin --delete feature/model
# limpar referências antigas
git fetch --prune
```

### Atualizar branch local com remoto
```bash
git pull
```

### Atualizar o JDK
```bash

Ctrl + Alt + Shift + S

```