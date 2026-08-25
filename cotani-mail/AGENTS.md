# cotani-mail module instructions

- Require a positive TTL for every message and preserve MailId across retries.
- Use bounded MailQuery pages; expired messages must remain invisible to inbox reads.
- Keep MailService free of Bukkit objects and serialize mutations through CompletionStage.
- Register storage migrations before creating a SQL-backed repository and close the service asynchronously.

