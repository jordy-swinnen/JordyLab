#!/usr/bin/env node
import path from 'node:path';
import process from 'node:process';
import fs from 'node:fs';
import { runEsBuildBuilder } from '@softarc/native-federation-esbuild';

function parseArgs(argv) {
  const args = {
    config: 'federation.config.ts',
    output: 'dist',
    tsConfig: 'tsconfig.app.json',
    projectName: undefined,
    entryPoints: [],
    dev: false,
    watch: false,
    verbose: false,
    rebuildDelay: 50,
  };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    switch (arg) {
      case '--config':
        args.config = argv[++i];
        break;
      case '--output':
        args.output = argv[++i];
        break;
      case '--tsConfig':
        args.tsConfig = argv[++i];
        break;
      case '--projectName':
        args.projectName = argv[++i];
        break;
      case '--entryPoint':
        args.entryPoints.push(argv[++i]);
        break;
      case '--dev':
        args.dev = true;
        break;
      case '--watch':
        args.watch = true;
        break;
      case '--verbose':
        args.verbose = true;
        break;
      case '--rebuildDelay':
        args.rebuildDelay = Number(argv[++i]);
        break;
      case '--help':
      case '-h':
        printHelp();
        process.exit(0);
        break;
      default:
        if (arg.startsWith('--')) {
          console.error(`[federation-build] Unknown option: ${arg}`);
          process.exit(1);
        }
    }
  }
  return args;
}

function printHelp() {
  console.log(`Usage: federation-build.mjs [options]

Options:
  --config <path>         Path to federation.config.ts (relative to workspaceRoot)
  --output <path>         Output directory for remoteEntry.json + chunks (relative to workspaceRoot)
  --tsConfig <path>       Path to tsconfig (relative to workspaceRoot)
  --projectName <name>    Project name (defaults to federation config name)
  --entryPoint <path>     Entry point to scan for used deps (repeatable; required for shell apps with no exposes)
  --dev                   Build in dev mode (cheaper, no integrity hashes)
  --watch                 Watch source files and rebuild on change
  --verbose               Verbose logging
  --rebuildDelay <ms>     Debounce rebuild delay (default 50)
  -h, --help              Show this help
`);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const workspaceRoot = process.cwd();

  if (!args.projectName) {
    const cfgModule = await import(
      pathToFileUrl(path.resolve(workspaceRoot, args.config))
    );
    args.projectName = cfgModule.default?.name ?? path.basename(path.dirname(path.resolve(workspaceRoot, args.config)));
  }

  const configPath = path.relative(workspaceRoot, path.resolve(workspaceRoot, args.config));
  const outputPath = path.relative(workspaceRoot, path.resolve(workspaceRoot, args.output));
  const tsConfigPath = path.relative(workspaceRoot, path.resolve(workspaceRoot, args.tsConfig));

  const outputAbs = path.resolve(workspaceRoot, outputPath);
  fs.mkdirSync(outputAbs, { recursive: true });

  console.log(`[federation-build] project=${args.projectName} output=${outputPath} dev=${args.dev} watch=${args.watch}`);

  const builder = await runEsBuildBuilder(configPath, {
    workspaceRoot,
    outputPath,
    tsConfig: tsConfigPath,
    projectName: args.projectName,
    entryPoints: args.entryPoints.length > 0 ? args.entryPoints : undefined,
    dev: args.dev,
    watch: args.watch,
    verbose: args.verbose,
    rebuildDelay: args.rebuildDelay,
  });

  if (args.watch) {
    const shutdown = async () => {
      console.log('\n[federation-build] shutting down...');
      try {
        await builder.close();
      } catch (err) {
        console.error('[federation-build] error during shutdown:', err);
      }
      process.exit(0);
    };
    process.on('SIGINT', shutdown);
    process.on('SIGTERM', shutdown);
  } else {
    await builder.close();
  }
}

function pathToFileUrl(p) {
  return new URL(`file://${p}`).href;
}

main().catch((err) => {
  console.error('[federation-build] failed:', err);
  process.exit(1);
});
