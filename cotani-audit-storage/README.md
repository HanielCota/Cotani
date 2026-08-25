# cotani-audit-storage

SQL adapter for [`cotani-audit`](../cotani-audit/README.md). It stores immutable audit entries with indexed actor,
target, action, severity and timestamp fields, and encodes details through the adapter's value codec.

Register migrations before starting storage:

```java
storageBuilder.migrations(
        CotaniAuditStorages.migrations().toArray(Migration[]::new));

AuditService audit = CotaniAuditStorages.create(storage);
```

`storage` must already be running when the service is created. Repository failures complete the returned
`CompletionStage` exceptionally; compose `audit.closeAsync()` into plugin shutdown.

