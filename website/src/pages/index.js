import React from 'react';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import styles from './index.module.css';

const cards = [
  {
    title: 'Comece em cinco minutos',
    description: 'Instale os módulos necessários e crie seu primeiro plugin Cotani.',
    to: '/docs/getting-started'
  },
  {
    title: 'Entenda a arquitetura',
    description: 'Veja como lifecycle, execução assíncrona, persistência e domínio se conectam.',
    to: '/docs/architecture'
  },
  {
    title: 'Encontre um módulo',
    description: 'Navegue pelas APIs de task, storage, cache, comandos, gameplay e operações.',
    to: '/docs/module-index'
  }
];

export default function Home() {
  const logoUrl = useBaseUrl('/img/logo.png');

  return (
    <Layout title="Wiki" description="Documentação oficial do framework Cotani">
      <header className={styles.hero}>
        <div className="container">
          <img className={styles.logo} src={logoUrl} alt="Cotani" />
          <Heading as="h1">Cotani Wiki</Heading>
          <p className={styles.subtitle}>
            APIs modulares, execução não bloqueante e arquitetura segura para plugins Paper e Folia.
          </p>
          <div className={styles.buttons}>
            <Link className="button button--primary button--lg" to="/docs/getting-started">
              Começar agora
            </Link>
            <Link className="button button--secondary button--lg" to="/docs/architecture">
              Ver arquitetura
            </Link>
          </div>
        </div>
      </header>
      <main>
        <section className="container margin-vert--xl">
          <div className={styles.cards}>
            {cards.map((card) => (
              <Link className={styles.card} key={card.to} to={card.to}>
                <Heading as="h2">{card.title}</Heading>
                <p>{card.description}</p>
              </Link>
            ))}
          </div>
        </section>
      </main>
    </Layout>
  );
}
