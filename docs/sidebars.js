/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  tutorialSidebar: [
    {
      type: 'doc',
      id: 'index',
      label: '🏠 Home',
    },
    {
      type: 'doc',
      id: 'hello-world',
      label: '🚀 Hello World',
    },
    {
      type: 'doc',
      id: 'essential-guide',
      label: '📖 Essential Guide',
    },
    {
      type: 'doc',
      id: 'advanced-guide',
      label: '🔧 Advanced Guide',
    },
    {
      type: 'category',
      label: '📘 Guides',
      collapsed: true,
      items: [
        'guides/projection',
        'guides/filter-definition',
        'guides/dsl-syntax',
        'guides/property-reference',
        'guides/custom-operators',
      ],
    },
    {
      type: 'category',
      label: '📚 API Reference',
      collapsed: true,
      items: [
        'reference/core',
        'reference/jpa-adapter',
        'reference/spring-adapter',
      ],
    },
    {
      type: 'doc',
      id: 'protocol',
      label: '📋 Protocol',
    },
    {
      type: 'category',
      label: '👥 Community',
      collapsed: true,
      items: [
        'community/contributing',
        'community/changelog',
      ],
    },
  ],
};

export default sidebars;
