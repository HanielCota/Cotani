/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  wikiSidebar: [
    'README',
    {
      type: 'category',
      label: 'Começando',
      items: ['getting-started', 'quickstart']
    },
    {
      type: 'category',
      label: 'Conceitos',
      items: ['architecture', 'async-contracts', 'module-index']
    },
    {
      type: 'category',
      label: 'Guias',
      items: ['ai/cotani-cookbook', 'troubleshooting', 'migration-1.x']
    },
    {
      type: 'category',
      label: 'Contribuição',
      items: ['documentation-guide']
    },
    {
      type: 'category',
      label: 'Referências avançadas',
      items: [
        'ai/architecture_diagram',
        'ai/AGENTS-best-practices',
        'review/full-architecture-audit'
      ]
    }
  ]
};

module.exports = sidebars;
