// @ts-check
import {themes as prismThemes} from 'prism-react-renderer';

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'FilterQL',
  tagline: 'Type-safe dynamic filtering for Java',
  favicon: 'img/logo.png',

  url: 'https://cyfko.github.io',
  baseUrl: '/filter-ql/',

  organizationName: 'cyfko',
  projectName: 'filter-ql',

  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',

  i18n: {
    defaultLocale: 'en',
    locales: ['en', 'fr'],
    localeConfigs: {
      en: {
        label: 'English',
        htmlLang: 'en-US',
      },
      fr: {
        label: 'Français',
        htmlLang: 'fr-FR',
      },
    },
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: './sidebars.js',
          editUrl: 'https://github.com/cyfko/filter-ql/tree/main/docs/',
        },
        theme: {
          customCss: './src/css/custom.css',
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      image: 'img/filterql-social-card.png',
      navbar: {
        title: 'FilterQL',
        logo: {
          alt: 'FilterQL Logo',
          src: 'img/logo.png',
          href: '/docs/',
        },
        items: [
          {
            type: 'docSidebar',
            sidebarId: 'tutorialSidebar',
            position: 'left',
            label: 'Documentation',
          },
          {
            type: 'localeDropdown',
            position: 'right',
          },
          {
            href: 'https://github.com/cyfko/filter-ql',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Documentation',
            items: [
              {
                label: 'Hello World',
                to: '/docs/hello-world',
              },
              {
                label: 'Référence API',
                to: '/docs/reference/core',
              },
            ],
          },
          {
            title: 'Communauté',
            items: [
              {
                label: 'GitHub Discussions',
                href: 'https://github.com/cyfko/filter-ql/discussions',
              },
              {
                label: 'Issues',
                href: 'https://github.com/cyfko/filter-ql/issues',
              },
            ],
          },
          {
            title: 'Plus',
            items: [
              {
                label: 'Changelog',
                to: '/docs/community/changelog',
              },
              {
                label: 'Contribuer',
                to: '/docs/community/contributing',
              },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} Kunrin SA. Documentation sous licence MIT.`,
      },
      prism: {
        theme: prismThemes.github,
        darkTheme: prismThemes.dracula,
        additionalLanguages: ['java', 'json', 'bash'],
      },
    }),
};

export default config;
