import nx from '@nx/eslint-plugin';

export default [
  ...nx.configs['flat/base'],
  ...nx.configs['flat/typescript'],
  ...nx.configs['flat/javascript'],
  {
    ignores: [
      '**/dist',
      '**/out-tsc',
      '**/vitest.config.*.timestamp*',
      'libs/ui/helm/**/*',
    ],
  },
  {
    files: ['**/*.ts', '**/*.tsx', '**/*.js', '**/*.jsx'],
    rules: {
      '@nx/enforce-module-boundaries': [
        'error',
        {
          enforceBuildableLibDependency: true,
          allow: ['^.*/eslint(\\.base)?\\.config\\.[cm]?[jt]s$'],
          depConstraints: [
            {
              sourceTag: 'type:api',
              onlyDependOnLibsWithTags: ['type:api'],
            },
            {
              sourceTag: 'type:ui',
              onlyDependOnLibsWithTags: ['type:api', 'type:ui'],
            },
            {
              sourceTag: 'type:app',
              onlyDependOnLibsWithTags: [
                'scope:fna',
                'scope:gamecatalog',
                'scope:settings',
                'scope:shared',
              ],
            },
            {
              sourceTag: 'scope:fna',
              onlyDependOnLibsWithTags: ['scope:fna', 'scope:shared'],
            },
            {
              sourceTag: 'scope:gamecatalog',
              onlyDependOnLibsWithTags: ['scope:gamecatalog', 'scope:shared'],
            },
            {
              sourceTag: 'scope:settings',
              onlyDependOnLibsWithTags: ['scope:settings', 'scope:shared'],
            },
          ],
        },
      ],
    },
  },
  {
    files: [
      '**/*.ts',
      '**/*.tsx',
      '**/*.cts',
      '**/*.mts',
      '**/*.js',
      '**/*.jsx',
      '**/*.cjs',
      '**/*.mjs',
    ],
    // Override or add rules here
    rules: {},
  },
];
