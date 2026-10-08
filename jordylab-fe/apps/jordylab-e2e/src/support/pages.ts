// The signed-in pages every cross-cutting journey (accessibility, Content-Security-Policy) walks through. A new page needs one line here.
export const PAGES: readonly {
  readonly name: string;
  readonly path: string;
  readonly heading: string;
}[] = [
  { name: 'FNA articles', path: '/fna/articles', heading: 'Articles' },
  { name: 'FNA portfolio', path: '/fna/portfolio', heading: 'Portfolio' },
  {
    name: 'FNA briefing',
    path: '/fna/briefing',
    heading: 'Investment briefing',
  },
  { name: 'Game library', path: '/games/grid', heading: 'Library' },
  { name: 'LibBot', path: '/games/libbot', heading: 'LibBot' },
  { name: 'Game sources', path: '/games/sources', heading: 'Sources' },
  { name: 'Consoles', path: '/games/consoles', heading: 'Consoles' },
  { name: 'Settings users', path: '/settings/users', heading: 'Users' },
  {
    name: 'Settings AI models',
    path: '/settings/ai-models',
    heading: 'AI models',
  },
];
