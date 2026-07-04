# cotani-core

Módulo base da Cotani. Define o ponto de entrada do framework, o gerenciamento de lifecycle e as exceções compartilhadas entre todos os módulos.

## Responsabilidade

- Fornecer `Cotani`, um registro central de recursos que devem ser fechados no desligamento do plugin.
- Definir `CotaniCloseException`, exceção unificada para falhas durante o fechamento de recursos.
- Servir como dependência mínima obrigatória para os demais módulos.
- Estabelecer contratos nulos via JSpecify (`@NullMarked`).

## Stack

- Java 21+
- Paper API (`compileOnlyApi`)
- JSpecify (`api`)

## Uso básico

```java
public final class MeuPlugin extends JavaPlugin {

    private Cotani cotani;

    @Override
    public void onEnable() {
        cotani = Cotani.forPlugin(this)
            .with(scheduler)
            .with(storage)
            .with(configs)
            .build();
    }

    @Override
    public void onDisable() {
        cotani.close(); // fecha tudo na ordem inversa do registro
    }
}
```

## API pública

| Classe/Interface | Descrição |
|------------------|-----------|
| `Cotani` | Registro de recursos `AutoCloseable`. Fecha recursos na ordem inversa de registro e agrega falhas em `CotaniCloseException`. |
| `Cotani.Builder` | Construtor fluente para associar recursos ao lifecycle do plugin. |
| `CotaniCloseException` | Exceção lançada quando um ou mais recursos falham ao fechar. |

## Regras de uso

- Sempre registre recursos criados durante o `onEnable`.
- Não use `Cotani` como localizador de serviço; ele apenas gerencia lifecycle.
- Chame `close()` apenas uma vez; chamadas subsequentes são ignoradas.

## Estrutura de pacotes

```text
com.cotani
├── Cotani.java                 # Registro de lifecycle
├── CotaniCloseException.java   # Exceção de fechamento
└── package-info.java           # @NullMarked
```

## Dependência Gradle

```kotlin
dependencies {
    api(project(":core"))
}
```
