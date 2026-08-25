---
id: getting-started
title: Começando
sidebar_label: Começando
description: Instale o Cotani e prepare um plugin Paper ou Folia.
---

# Começando

Cotani é distribuído como um conjunto de módulos independentes. Adicione apenas as capacidades utilizadas pelo plugin
e mantenha as versões alinhadas com o BOM.

## Pré-requisitos

- Java 25 para a linha atual do projeto;
- Paper ou Folia compatível com a versão publicada;
- Gradle ou Maven;
- conhecimento básico de plugins Bukkit/Paper;
- Docker apenas para executar as suítes de integração locais.

## Dependência Gradle

Use o BOM para evitar combinações incompatíveis entre os módulos:

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation(platform("com.cotani:cotani-bom:1.1.1"))
    implementation("com.cotani:cotani-task")
}
```

Substitua as linhas de exemplo pelos módulos reais que o plugin utiliza. O [README principal](https://github.com/HanielCota/Cotani#choose-your-modules)
contém a tabela de módulos e as coordenadas atuais.

## Bootstrap mínimo

O plugin principal deve cuidar apenas do lifecycle e da composição das dependências. Serviços, repositórios e listeners
ficam em classes próprias.

```java
public final class ExamplePlugin extends JavaPlugin {
    private Cotani lifecycle;

    @Override
    public void onEnable() {
        var scheduler = SchedulerFactory.create(this);
        lifecycle = Cotani.forPlugin(this)
                .withAsync(scheduler::closeAsync)
                .build();
    }

    @Override
    public void onDisable() {
        if (lifecycle != null) {
            lifecycle.closeAsync();
        }
    }
}
```

O exemplo acima é intencionalmente reduzido. Para um plugin real, registre os módulos em uma composição única e guarde
as referências dos serviços que pertencem ao plugin.

## Regras essenciais

- prefira `CompletionStage<T>` nas APIs públicas;
- nunca use `join()`, `get()` ou `Thread.sleep()` no código da aplicação;
- capture `UUID` e dados imutáveis antes de iniciar trabalho assíncrono;
- retorne à thread global, de região ou de entidade antes de acessar objetos Paper;
- feche recursos criados pelo plugin durante o shutdown;
- use o [índice de módulos](module-index.md) para localizar a API correta.
