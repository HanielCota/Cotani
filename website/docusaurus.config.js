const baseUrl = '/Cotani/';

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Cotani Wiki',
  tagline: 'Documentação para plugins Paper e Folia',
  favicon: 'img/logo.png',
  url: 'https://hanielcota.github.io',
  baseUrl,
  organizationName: 'HanielCota',
  projectName: 'Cotani',
  onBrokenLinks: 'throw',
  markdown: {
    hooks: {
      onBrokenMarkdownLinks: 'warn'
    }
  },
  trailingSlash: false,
  i18n: {
    defaultLocale: 'pt-BR',
    locales: ['pt-BR']
  },
  presets: [
    [
      'classic',
      {
        docs: {
          path: '../docs',
          routeBasePath: 'docs',
          sidebarPath: require.resolve('./sidebars.js'),
          showLastUpdateTime: true,
          showLastUpdateAuthor: false,
          editUrl: 'https://github.com/HanielCota/Cotani/edit/master/'
        },
        blog: false,
        theme: {
          customCss: require.resolve('./src/css/custom.css')
        }
      }
    ]
  ],
  themeConfig: {
    image: 'img/cotani-social-card.svg',
    navbar: {
      title: 'Cotani',
      logo: {
        alt: 'Logo do Cotani',
        src: 'img/logo.png'
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'wikiSidebar',
          position: 'left',
          label: 'Wiki'
        },
        {
          href: 'https://hanielcota.github.io/Cotani/api/',
          label: 'Javadoc',
          position: 'left'
        },
        {
          href: 'https://github.com/HanielCota/Cotani',
          label: 'GitHub',
          position: 'right'
        }
      ]
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Documentação',
          items: [
            { label: 'Começando', to: '/docs/getting-started' },
            { label: 'Arquitetura', to: '/docs/architecture' },
            { label: 'Módulos', to: '/docs/module-index' }
          ]
        },
        {
          title: 'Projeto',
          items: [
            { label: 'Repositório', href: 'https://github.com/HanielCota/Cotani' },
            { label: 'Contribuição', to: '/docs/documentation-guide' }
          ]
        }
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Cotani contributors.`
    },
    prism: {
      additionalLanguages: ['java', 'kotlin', 'bash', 'yaml', 'toml']
    }
  }
};

module.exports = config;
