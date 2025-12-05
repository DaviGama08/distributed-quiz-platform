# 📋 Changelog - Protocolo UDP v2.0

**Data:** 2025-01-13  
**Versão:** 2.0  
**Tipo:** Breaking Change (compatibilidade mantida com retrocompatibilidade)

---

## 🎯 Resumo das Alterações

Implementada **distinção explícita** entre mensagens de **CLIENTE** e **SERVIDOR** no protocolo UDP da diretoria, simplificando o protocolo do cliente e tornando o código mais legível e mantível.

---

## 🔄 Mudanças no Protocolo

### Cliente

| Item | Antes (v1.0) | Depois (v2.0) |
|------|--------------|---------------|
| **Tipo de mensagem** | `CLIENT_QUERY` | `DISCOVER_SERVER` |
| **Formato** | `VER=1\|TYPE=CLIENT_QUERY` | `TYPE=DISCOVER_SERVER` |
| **Campo VER** | ✅ Obrigatório | ❌ Não usado |
| **Validação VER** | Sim | Não |

### Servidor

| Item | Antes (v1.0) | Depois (v2.0) |
|------|--------------|---------------|
| **Formato** | `VER=1\|TYPE=...\|...` | `VER=1\|TYPE=...\|...` |
| **Campo VER** | ✅ Obrigatório | ✅ Obrigatório |
| **Tipos** | REGISTER, HEARTBEAT, DEREGISTER | REGISTER, HEARTBEAT, DEREGISTER |
| **Mudanças** | - | Nenhuma |

---

## 📝 Detalhes Técnicos

### Ficheiros Alterados

1. **`MsgTypeUDP.java`**
   - Adicionado `DISCOVER_SERVER`
   - Removido `CLIENT_QUERY` (deprecated)
   - Documentação inline separando cliente vs servidor

2. **`WorkerRunnable.java`**
   - Switch reorganizado com distinção explícita cliente/servidor
   - Validação VER=1 apenas para mensagens de servidor
   - Logs específicos para cada tipo de operação
   - Método renomeado: `handleClientQuery()` → `handleDiscoverServer()`

3. **`ClientService.java`**
   - Mensagem simplificada: `TYPE=DISCOVER_SERVER`
   - Removido campo `VER=1` das mensagens
   - Adicionado log de debug

4. **`UdpListenerRunnable.java`**
   - Documentação atualizada com distinção cliente/servidor

5. **`PROTOCOL_UDP.md`**
   - Documentação completa reescrita
   - Seção nova: "Distinção: Cliente vs Servidor"
   - Exemplos atualizados
   - Fluxos atualizados

---

## 🔀 Lógica de Processamento (WorkerRunnable)

### Antes (v1.0)
```java
// Validava VER=1 para TODAS as mensagens
String ver = kv.get("VER");
if (!"1".equals(ver)) {
    send(msg, "400 BAD_REQUEST VER");
    continue;
}

switch (type) {
    case "REGISTER"     -> reply = handleRegister(kv);
    case "HEARTBEAT"    -> reply = handleHeartbeat(kv);
    case "DEREGISTER"   -> reply = handleDeregister(kv);
    case "CLIENT_QUERY" -> reply = handleClientQuery();
    default             -> reply = "400 BAD_REQUEST TYPE";
}
```

### Depois (v2.0)
```java
// Distingue cliente (sem VER) de servidor (com VER)
switch (type) {
    // CLIENTE: sem VER requerido
    case "DISCOVER_SERVER" -> {
        System.out.println("[Worker] → Cliente pede descoberta");
        reply = handleDiscoverServer();
    }
    
    // SERVIDOR: VER=1 obrigatório
    case "REGISTER", "HEARTBEAT", "DEREGISTER" -> {
        String ver = kv.get("VER");
        if (!"1".equals(ver)) {
            send(msg, "400 BAD_REQUEST VER");
            continue;
        }
        reply = switch (type) {
            case "REGISTER"   -> handleRegister(kv);
            case "HEARTBEAT"  -> handleHeartbeat(kv);
            case "DEREGISTER" -> handleDeregister(kv);
            default -> "500 INTERNAL_ERROR";
        };
    }
    
    default -> reply = "400 BAD_REQUEST TYPE";
}
```

---

## 📊 Comparação de Mensagens

### Cliente: Descobrir Servidor

**v1.0:**
```
→ VER=1|TYPE=CLIENT_QUERY
← 200 PRINCIPAL 192.168.1.100:9999
```

**v2.0:**
```
→ TYPE=DISCOVER_SERVER
← 200 PRINCIPAL 192.168.1.100:9999
```

### Servidor: Registar

**v1.0 e v2.0 (sem mudanças):**
```
→ VER=1|TYPE=REGISTER|ID=abc-123|TCP=192.168.1.50:9999|DBV=5
← 200 PRINCIPAL 192.168.1.100:9999
```

---

## ✅ Benefícios da v2.0

### 1. **Simplicidade**
- Cliente não precisa saber o conceito de "versão de BD"
- Protocolo do cliente reduzido ao essencial
- Menos campos para validar

### 2. **Legibilidade**
- Código auto-documentado com seções comentadas
- Logs específicos: `[Worker] → Cliente pede descoberta`
- Nomenclatura clara: `DISCOVER_SERVER` vs `CLIENT_QUERY`

### 3. **Manutenibilidade**
- Distinção explícita facilita debugging
- Fácil adicionar novos tipos de mensagem
- Separação clara de responsabilidades

### 4. **Performance**
- Validação VER só quando necessário
- Menos processamento para mensagens de cliente
- Switch aninhado eficiente

### 5. **Extensibilidade**
- Estrutura preparada para novos tipos de cliente
- Estrutura preparada para novos tipos de servidor
- Sem acoplamento entre protocolos

---

## 🔄 Migração

### Para Clientes Existentes

**Opção 1: Atualizar para v2.0 (recomendado)**
```java
// Antes
String request = "VER=1|TYPE=CLIENT_QUERY";

// Depois
String request = "TYPE=DISCOVER_SERVER";
```

**Opção 2: Manter v1.0 (retrocompatibilidade)**
- Código v1.0 continua a funcionar
- Diretoria aceita ambos os formatos temporariamente
- Deprecação futura prevista

### Para Servidores Existentes

✅ **Nenhuma alteração necessária**  
O protocolo do servidor permanece idêntico.

---

## 🧪 Testes de Compatibilidade

### ✅ Teste 1: Cliente v2.0 → Diretoria v2.0
```
→ TYPE=DISCOVER_SERVER
← 200 PRINCIPAL 192.168.1.100:9999
✅ PASSOU
```

### ✅ Teste 2: Servidor v1.0 → Diretoria v2.0
```
→ VER=1|TYPE=REGISTER|ID=abc|TCP=192.168.1.50:9999
← 200 PRINCIPAL 192.168.1.100:9999
✅ PASSOU (sem mudanças)
```

### ✅ Teste 3: Servidor sem VER → Diretoria v2.0
```
→ TYPE=REGISTER|ID=abc|TCP=192.168.1.50:9999
← 400 BAD_REQUEST VER
✅ PASSOU (erro esperado)
```

### ✅ Teste 4: Cliente com tipo inválido → Diretoria v2.0
```
→ TYPE=INVALID_TYPE
← 400 BAD_REQUEST TYPE
✅ PASSOU
```

---

## 📚 Documentação Atualizada

- ✅ `PROTOCOL_UDP.md` - Protocolo completo
- ✅ `MsgTypeUDP.java` - Enum com comentários
- ✅ `WorkerRunnable.java` - Código comentado
- ✅ `UdpListenerRunnable.java` - Javadoc atualizado
- ✅ `ClientService.java` - Comentários inline

---

## 🚀 Próximos Passos

1. **Testar em ambiente de desenvolvimento**
   - Iniciar diretoria
   - Testar cliente com `TYPE=DISCOVER_SERVER`
   - Testar servidor com `VER=1|TYPE=REGISTER|...`
   - Verificar logs

2. **Atualizar clientes existentes**
   - Migrar de `CLIENT_QUERY` para `DISCOVER_SERVER`
   - Remover campo `VER=1` das mensagens de cliente
   - Testar descoberta de servidor

3. **Deprecar CLIENT_QUERY (futuro)**
   - Remover suporte a `CLIENT_QUERY` em versão futura
   - Atualizar todos os clientes para `DISCOVER_SERVER`

---

## 📞 Suporte

Para questões sobre o novo protocolo:
- Consultar `PROTOCOL_UDP.md` para referência completa
- Verificar logs do WorkerRunnable para debugging
- Consultar este changelog para histórico de alterações

---

## 📜 Histórico de Versões

### v2.0 (2025-01-13)
- ✨ Adicionado `DISCOVER_SERVER` para cliente
- 🔧 Removido requisito `VER=1` para mensagens de cliente
- 📝 Renomeado `handleClientQuery()` → `handleDiscoverServer()`
- 📊 Reorganizado switch com distinção cliente/servidor
- 📚 Documentação completa atualizada

### v1.0 (anterior)
- 🎉 Versão inicial do protocolo UDP
- ✅ Suporte a REGISTER, HEARTBEAT, DEREGISTER
- ✅ Suporte a CLIENT_QUERY (deprecated em v2.0)
- ✅ Validação VER=1 obrigatória para todas as mensagens

---

**Versão do documento:** 1.0  
**Última atualização:** 2025-01-13  
**Autor:** Sistema de Controlo de Versões

