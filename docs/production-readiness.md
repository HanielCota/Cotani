---
id: production-readiness
title: Prontidão para produção
sidebar_label: Prontidão para produção
description: Processo reproduzível de staging, testes de falha, observabilidade e rollback do Cotani.
---

# Prontidão para produção

Este documento define o que precisa ser verificado antes de usar uma versão do Cotani em um servidor Paper ou Folia.
Os testes unitários e de contrato são executáveis no build. O teste de integração usa Testcontainers e precisa de Docker.
O teste de ciclo completo do plugin ainda precisa de um servidor Paper/Folia real, porque os objetos e threads do servidor
não são reproduzidos integralmente por mocks.

## Validação automatizada

Execute na raiz do repositório:

```bash
./gradlew check
./gradlew aggregateJavadoc
./gradlew integrationTest
./gradlew releaseVerification
```

`integrationTest` agrega as suítes de banco e Redis. Sem Docker, os testes marcados com `disabledWithoutDocker` são
ignorados; isso não deve ser tratado como aprovação de integração. Em uma máquina de release, confirme `docker info`
antes de considerar o resultado verde.

## Staging local

O arquivo [`docker-compose.staging.yml`](../docker-compose.staging.yml) fornece Redis, MySQL e MariaDB isolados em
portas locais. Copie `.env.staging.example` para `.env.staging`, troque todos os valores e carregue as variáveis no
processo do shell. O arquivo com credenciais reais não deve ser commitado.

No PowerShell:

```powershell
Get-Content .env.staging | ForEach-Object {
    if ($_ -match '^([^#][^=]*)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') }
}
.\scripts\start-staging.ps1
```

Para parar sem apagar volumes:

```powershell
.\scripts\start-staging.ps1 -Down
```

Para o smoke test em um servidor real, use o harness isolado de Paper/Folia:

```powershell
.\scripts\run-server-staging.ps1 -ServerType paper -DurationSeconds 120
.\scripts\run-server-staging.ps1 -ServerType folia -DurationSeconds 120
```

O harness baixa o build estável oficial compatível, compila um plugin temporário com `cotani-core` e `cotani-task`,
valida o boot, executa `/cotani-staging`, mantém tarefas assíncronas por todo o período e exige shutdown gracioso.
Para um soak de release, aumente `DurationSeconds` para pelo menos `1800`; o smoke de 120 segundos não substitui esse
soak prolongado.

Para validar o plugin de exemplo real, com o `plugin.yml` e a classe principal de `docs-examples`, execute:

```powershell
.\scripts\run-real-plugin-staging.ps1 -ServerType paper -DurationSeconds 120
.\scripts\run-real-plugin-staging.ps1 -ServerType folia -DurationSeconds 120
```

Esse teste empacota `CotaniQuickStartPlugin` com `cotani-core` e `cotani-task`, inicia o mesmo JAR em Paper e Folia,
valida o registro do comando, confirma a guarda de sender e exige o desligamento limpo. Como o smoke test envia o
comando pelo console, a transição para a entity thread deve ser validada adicionalmente com um jogador conectado.

O banco deve ser testado tanto com MySQL quanto com MariaDB. O Redis deve ser iniciado com autenticação, para evitar que
um teste passe apenas porque uma configuração insegura de desenvolvimento foi usada.

## Soak test

Para um candidato a release, execute pelo menos 30 minutos; antes de uma implantação importante, execute 2 horas. Use
dados de teste isolados e gere carga concorrente envolvendo cache, storage, economia, recompensas, market, cooldown,
party/trade e Redis Pub/Sub/RPC quando esses módulos estiverem habilitados.

Colete, no mínimo:

- taxa de erro e timeout por operação;
- latência p50, p95 e p99;
- rejeições de filas e tarefas atrasadas;
- conexões ativas, erros de pool e tempo de migração do banco;
- reconexões e falhas de Redis;
- claims, settlements e compras pendentes;
- tempo do shutdown e exceções durante o shutdown;
- uso de heap, threads e pausas de GC.

O servidor não deve apresentar duplicação de moeda/itens, confirmação perdida, estado parcialmente persistido ou
referências a `Player`, `World`, `Entity`, `Inventory` ou `Block` sobrevivendo em fluxos assíncronos.

## Testes de falha obrigatórios

Durante o staging, execute cada cenário e confirme recuperação sem intervenção manual nos dados:

1. parar e iniciar o Redis;
2. interromper o Redis durante uma operação de lock, Pub/Sub, RPC e rate limit;
3. pausar ou reiniciar MySQL e MariaDB durante leitura, escrita e migração;
4. reiniciar o plugin depois de uma falha no meio de uma compra, reward claim, trade ou punição;
5. desligar o servidor enquanto há tarefas assíncronas e verificar a conclusão do lifecycle;
6. iniciar e fechar o módulo repetidamente, incluindo `closeAsync()` durante `startAsync()`;
7. no Folia, repetir as operações em jogadores de regiões diferentes e confirmar que mutações retornam à entity/region
   thread correta.

Para cada cenário, registre causa, operação afetada, tempo de recuperação, dados pendentes recuperados e evidência do
log/metric. Operações repetíveis devem manter seus identificadores de idempotência; não gere um novo identificador para
“tentar de novo” uma operação que já pode ter sido aceita.

## Paper/Folia manual

O checklist mínimo do servidor real é:

- boot limpo, bootstrap dos módulos e fechamento ordenado;
- join/quit de jogadores e reload de cache sem referências vivas em async;
- comandos síncronos leves, comandos de I/O em `executesAsync` e mutações de jogador em `executesEntity`;
- GUI, inventário, teleport, display, NPC, HUD e nametag sob carga;
- persistência após crash/restart e migrações em cópia restaurável do banco;
- execução no Paper e no Folia suportado pelo projeto;
- ausência de `/reload` como procedimento de operação; use stop/start completo do servidor.

## Observabilidade e operação

Ative o módulo de métricas em staging e valide que o endpoint Prometheus está acessível apenas pela interface de
observabilidade autorizada. O padrão loopback é intencional; se houver proxy, firewall ou autenticação na frente dele,
teste também o caminho real usado pela equipe de operações.

Crie alertas para aumento de p95/p99, erros de conexão, timeouts, rejeição de tarefas, reconexões de Redis, falhas de
migração, crescimento de claims/settlements pendentes e shutdown acima do orçamento definido. Logs devem incluir o
nome da operação, módulo, identificador imutável e causa encadeada, sem senhas, tokens ou payloads sensíveis.

## Rollback

Antes da implantação, guarde o jar anterior, configuração efetiva, versão do Cotani, versão do Paper/Folia e backup
verificado do banco. Em caso de falha:

1. pare novas escritas e faça shutdown normal do servidor;
2. preserve logs, métricas e o banco para investigação;
3. restaure o jar anterior somente se suas migrações forem compatíveis com o schema atual;
4. nunca faça downgrade destrutivo de schema; restaure um backup ou aplique uma migração corretiva para frente;
5. valide recovery de claims, compras, trades e saldos antes de reabrir o servidor;
6. documente a causa e bloqueie a mesma versão até haver uma correção testada.

## Critério de go/no-go

É `go` apenas quando `check`, Javadoc e integração com Docker passam, o soak não revela regressões, os testes de falha
foram evidenciados e Paper/Folia real foi validado com o mesmo conjunto de módulos e configurações. Este repositório não
pode declarar essa última etapa concluída sem acesso ao servidor e aos serviços de staging correspondentes.
