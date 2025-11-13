# Protocolo UDP - Cliente ↔ Diretoria

## Formato das Mensagens

As mensagens UDP seguem o formato **KEY=VALUE** separado por pipe `|`:

```
CAMPO1=<valor1>|CAMPO2=<valor2>...
```

## Distinção: Cliente vs Servidor

### 🔵 Mensagens de CLIENTE
- **Formato simples:** `TYPE=<tipo>`
- **SEM campo VER** (cliente não precisa de saber versão da BD)
- **Tipos:** `DISCOVER_SERVER`

### 🟢 Mensagens de SERVIDOR
- **Formato completo:** `VER=1|TYPE=<tipo>|...`
- **COM campo VER obrigatório** (servidores têm versão de BD)
- **Tipos:** `REGISTER`, `HEARTBEAT`, `DEREGISTER`

---

## Mensagens do Cliente para Diretoria

### 1. DISCOVER_SERVER - Descobrir Servidor Principal

**Pedido:**
```
TYPE=DISCOVER_SERVER
```

**Respostas:**
- ✅ `200 PRINCIPAL <ip>:<porta>` - Servidor principal disponível
- ❌ `404 NO_PRINCIPAL` - Nenhum servidor registado
- ❌ `400 BAD_REQUEST TYPE` - Tipo ausente ou inválido

**Exemplo:**
```
→ Cliente: TYPE=DISCOVER_SERVER
← Diretoria: 200 PRINCIPAL 192.168.1.100:9999
```

**Nota:** Cliente NÃO envia `VER=1` porque não gere versões de base de dados.

---

## Mensagens do Servidor para Diretoria

### 1. REGISTER - Registar Novo Servidor

**Pedido:**
```
VER=1|TYPE=REGISTER|ID=<uuid>|TCP=<ip>:<porta>|DBV=<versão_bd>
```

**Campos:**
- `VER` - Versão do protocolo (obrigatório, sempre `1`)
- `ID` - UUID único do servidor
- `TCP` - Endereço TCP no formato `ip:porta`
- `DBV` - Versão da base de dados (opcional, default=1)

**Respostas:**
- ✅ `200 PRINCIPAL <ip>:<porta>` - Registo OK, retorna servidor principal
- ❌ `400 BAD_REQUEST VER` - Versão ausente ou inválida
- ❌ `400 BAD_REQUEST ID` - ID ausente ou vazio
- ❌ `400 BAD_REQUEST TCP` - Endereço TCP inválido
- ❌ `400 BAD_REQUEST TCP_PORT` - Porta TCP inválida
- ❌ `404 NO_PRINCIPAL` - Nenhum servidor disponível

**Exemplo:**
```
→ Servidor: VER=1|TYPE=REGISTER|ID=abc-123|TCP=192.168.1.50:9999|DBV=5
← Diretoria: 200 PRINCIPAL 192.168.1.100:9999
```

---

### 2. HEARTBEAT - Manter Servidor Ativo

**Pedido:**
```
VER=1|TYPE=HEARTBEAT|ID=<uuid>|DBV=<versão_bd>
```

**Campos:**
- `VER` - Versão do protocolo (obrigatório, sempre `1`)
- `ID` - UUID do servidor
- `DBV` - Versão atual da base de dados (opcional)

**Respostas:**
- ✅ `200 OK` - Heartbeat recebido, timestamp atualizado
- ❌ `400 BAD_REQUEST VER` - Versão ausente ou inválida
- ❌ `400 BAD_REQUEST ID` - ID ausente ou vazio
- ❌ `409 CONFLICT UNKNOWN_ID` - ID desconhecido (servidor não registado)

**Exemplo:**
```
→ Servidor: VER=1|TYPE=HEARTBEAT|ID=abc-123|DBV=7
← Diretoria: 200 OK
```

---

### 3. DEREGISTER - Desregistar Servidor

**Pedido:**
```
VER=1|TYPE=DEREGISTER|ID=<uuid>
```

**Campos:**
- `VER` - Versão do protocolo (obrigatório, sempre `1`)
- `ID` - UUID do servidor

**Respostas:**
- ✅ `200 OK` - Servidor removido com sucesso
- ❌ `400 BAD_REQUEST VER` - Versão ausente ou inválida
- ❌ `400 BAD_REQUEST ID` - ID ausente ou vazio
- ❌ `409 CONFLICT UNKNOWN_ID` - ID desconhecido

**Exemplo:**
```
→ Servidor: VER=1|TYPE=DEREGISTER|ID=abc-123
← Diretoria: 200 OK
```

---

## Códigos de Resposta

| Código | Significado | Descrição |
|--------|-------------|-----------|
| `200` | OK/SUCCESS | Operação bem-sucedida |
| `400` | BAD_REQUEST | Pedido mal formatado ou campos inválidos |
| `404` | NOT_FOUND | Recurso não encontrado |
| `409` | CONFLICT | Conflito de estado (ex: ID desconhecido) |

---

## Fluxo Típico - Cliente

1. **Descoberta:** Cliente envia `TYPE=LOGIN` via UDP à diretoria (sem VER)
2. **Resposta:** Diretoria responde com `200 PRINCIPAL <ip>:<porta>`
3. **Conexão:** Cliente conecta ao servidor via TCP
4. **Autenticação:** Cliente envia `LoginRequestDTO` via TCP (serializado)
5. **Uso:** Cliente comunica com servidor via TCP usando objetos `Message<DTO>`

---

## Fluxo Típico - Servidor

1. **Arranque:** Servidor inicia e gera UUID único
2. **Registo:** Envia `REGISTER` à diretoria com endereço TCP e versão BD
3. **Resposta:** Recebe endereço do servidor principal (ou confirma que é o principal)
4. **Heartbeats:** Envia `HEARTBEAT` periodicamente (ex: cada 10s) para manter registo ativo
5. **Shutdown:** Envia `DEREGISTER` antes de terminar

---

## Timeouts e TTL

- **Cliente:** Timeout de descoberta UDP = 5000ms
- **Servidor:** TTL na diretoria = 17000ms (sem heartbeat → servidor removido)
- **Diretoria:** Reaper thread verifica servidores inativos periodicamente

---

## Notas Importantes

⚠️ **A diretoria NÃO faz autenticação** - apenas descobre servidores
⚠️ **Autenticação real é feita via TCP** com o servidor, não com a diretoria
⚠️ **Cliente não envia VER** - apenas `TYPE=LOGIN`
⚠️ **Servidor envia VER obrigatório** - sempre `VER=1|TYPE=...|...`
⚠️ **Case-sensitive:** Todos os campos são maiúsculas (`TYPE`, `ID`, `TCP`, etc.)
⚠️ **Distinção clara:** WorkerRunnable distingue automaticamente cliente vs servidor

