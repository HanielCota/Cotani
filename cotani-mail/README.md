<div align="center">

<img src="../logo.png" alt="Cotani logo" width="220">

# cotani-mail

</div>

Correio persistente e assíncrono entre jogadores para Paper e Folia.

O módulo mantém mensagens como dados imutáveis, usa `UUID` em vez de objetos Bukkit e suporta reenvio seguro por
`MailId`. Mensagens têm TTL obrigatório, inbox paginada, contador de não lidas, leitura, exclusão e limpeza de
expiradas.

```java
var mail = CotaniMails.inMemory();

mail.sendAsync(senderId, recipientId, "Bem-vindo", "Sua recompensa está na caixa postal.")
        .thenCompose(message -> mail.markReadAsync(recipientId, message.id()))
        .thenCompose(ignored -> mail.inboxAsync(recipientId, MailQuery.firstPage(20)));
```

Para persistência SQL, registre `StorageMailRepository.migrations()` antes de iniciar `CotaniStorage` e crie o
serviço com `CotaniMails.fromRepository(new StorageMailRepository(storage))`.

## Garantias

- contratos públicos usam `CompletionStage` e não bloqueiam;
- `MailId` torna retries idempotentes;
- mensagens expiradas não aparecem na inbox;
- paginação é limitada por `MailServiceOptions.maxPageSize`;
- mutações são serializadas por serviço e esperam a operação durável mesmo quando o chamador recebe timeout;
- o núcleo não acessa `Player`, `World` ou qualquer objeto Bukkit;
- a persistência SQL usa inserção idempotente com chave única, índices de inbox e limpeza incremental.
