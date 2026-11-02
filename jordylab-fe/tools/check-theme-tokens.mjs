#!/usr/bin/env bun
// Fails when a hex colour literal appears in application code. Colours are authored once, in themes.js, and reach the screens as
// theme variables, so a literal in a template or component is a colour that will not follow the theme.
//
//   bun tools/check-theme-tokens.mjs
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

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

if (findings.length > 0) {
  console.error(`Hex colour literals found (${findings.length}). Use a theme variable or add the colour to themes.js:\n${findings.join('\n')}`);
  process.exit(1);
}
console.log('No hex colour literals outside the theme files.');
