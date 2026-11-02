#!/usr/bin/env bun
// Fails when a hex colour literal appears in application code. Colours are authored once, in themes.js, and reach the screens as
// theme variables, so a literal in a template or component is a colour that will not follow the theme.
//
//   bun tools/check-theme-tokens.mjs
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { themes } from '../themes.js';

const workspaceRoot = join(import.meta.dirname, '..');
const scannedFolders = ['apps', 'libs'];
const scannedExtensions = ['.ts', '.html', '.css', '.svg'];
const hexLiteral = /#[0-9a-fA-F]{8}\b|#[0-9a-fA-F]{6}\b|#[0-9a-fA-F]{3}\b(?![0-9a-zA-Z-])/g;

/** Paths where a hex value is allowed: brand assets, static browser chrome colours, specs and mocks, native resources. */
const allowedPathPatterns = [
  /\.spec\.ts$/,
  /\.mock\.ts$/,
  /\/mocks\//,
  /\/public\//,
  /\/src\/index\.html$/,
  /\/android\//,
  /\/node_modules\//,
  /\/dist\//,
];

function filesBelow(folder) {
  return readdirSync(folder).flatMap((entry) => {
    const path = join(folder, entry);
    if (entry === 'node_modules' || entry === 'dist') {
      return [];
    }
    return statSync(path).isDirectory() ? filesBelow(path) : [path];
  });
}

const findings = [];
for (const folder of scannedFolders) {
  for (const file of filesBelow(join(workspaceRoot, folder))) {
    const path = relative(workspaceRoot, file);
    if (!scannedExtensions.some((extension) => file.endsWith(extension))) {
      continue;
    }
    if (allowedPathPatterns.some((pattern) => pattern.test(`/${path}`))) {
      continue;
    }
    readFileSync(file, 'utf8')
      .split('\n')
      .forEach((line, index) => {
        for (const match of line.matchAll(hexLiteral)) {
          findings.push(`${path}:${index + 1}: ${match[0]}`);
        }
      });
  }
}

/**
 * The sign-in page lives on the identity provider's origin and cannot read the app's stylesheet, so jordylab.css carries its own
 * copy of two palettes: dark (Content Led) and light (Paper). They must equal the token table in themes.js.
 */
const loginStylesheet = join(workspaceRoot, '..', 'jordylab-be', 'compose', 'keycloak-theme', 'jordylab', 'login', 'resources', 'css', 'jordylab.css');
const loginTokenNames = {
  ground: 'ground',
  rail: 'rail',
  surface: 'surface',
  line: 'line',
  'line-strong': 'line-strong',
  text: 'text',
  'text-2': 'text-2',
  'text-3': 'text-3',
  accent: 'accent',
  'accent-ink': 'accent-ink',
  'accent-text': 'accent-text',
  danger: 'mark-liked',
};

function paletteOf(block) {
  return Object.fromEntries([...block.matchAll(/--jl-([a-z0-9-]+):\s*(#[0-9a-fA-F]{6});/g)].map((match) => [match[1], match[2].toLowerCase()]));
}

const loginCss = readFileSync(loginStylesheet, 'utf8');
const darkBlock = /^:root \{([^}]*)\}/m.exec(loginCss)?.[1] ?? '';
const lightBlock = /@media \(prefers-color-scheme: light\) \{\s*:root \{([^}]*)\}/.exec(loginCss)?.[1] ?? '';
for (const [label, block, themeId] of [['dark', darkBlock, 'content'], ['light', lightBlock, 'paper']]) {
  const palette = paletteOf(block);
  const theme = themes.find((candidate) => candidate.id === themeId);
  for (const [loginName, tokenName] of Object.entries(loginTokenNames)) {
    const expected = theme.tokens[tokenName].toLowerCase();
    if (palette[loginName] !== expected) {
      findings.push(`jordylab.css (${label} palette) --jl-${loginName} is ${palette[loginName] ?? 'missing'}, ${themeId} ${tokenName} is ${expected}`);
    }
  }
}
for (const forbidden of [/#ff6a3d/i, /#ff8a66/i, /Bricolage/i, /Martian/i, /Hanken/i]) {
  if (forbidden.test(loginCss)) {
    findings.push(`jordylab.css still contains ${forbidden}`);
  }
}

if (findings.length > 0) {
  console.error(`Hex colour literals found (${findings.length}). Use a theme variable or add the colour to themes.js:\n${findings.join('\n')}`);
  process.exit(1);
}
console.log('No hex colour literals outside the theme files; the sign-in palettes match themes.js.');
