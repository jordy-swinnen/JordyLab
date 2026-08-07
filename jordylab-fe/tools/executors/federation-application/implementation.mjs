#!/usr/bin/env node
// @ts-check
import { createRequire } from 'node:module';
import { buildApplicationInternal, emitFilesToDisk, ResultKind } from '@angular/build/private';
import path from 'node:path';

const require = createRequire(import.meta.url);

/** Mirror of @angular/build BuildOutputFileType (not exported via /private). */
const FILE_TYPE = {
  Browser: 0,
  Media: 1,
  ServerApplication: 2,
  ServerRoot: 3,
  Root: 4,
};

/**
 * Load federation.config.ts and return the default export (the config object).
 * Uses jiti (already in the dep tree via @softarc/native-federation) for
 * Node-side TypeScript transpilation.
 */
async function loadFederationConfig(federationConfigPath, workspaceRoot) {
  const jitiMod = await import('jiti');
  const createJiti = jitiMod.createJiti ?? jitiMod.default?.createJiti ?? jitiMod.default;
  const jiti = createJiti(workspaceRoot, { interopDefault: true, esmResolve: true });
  const absolutePath = federationConfigPath.startsWith('/')
    ? federationConfigPath
    : `${workspaceRoot.replace(/\/$/, '')}/${federationConfigPath}`;
  const mod = jiti(absolutePath);
  const config = mod.default ?? mod;
  if (!config || typeof config !== 'object') {
    throw new Error(
      `[federation-application] federation config at ${federationConfigPath} did not export a config object`,
    );
  }
  return config;
}

/**
 * Derive the externals list from a federation config. Returns ONLY the
 * `shared` map keys (true npm packages like @angular/core, rxjs, etc).
 * Intentionally does NOT include `sharedMappings` (= workspace aliases
 * like @jordylab-fe/fna/ui, @spartan-ng/ui-*-helm): those are bundled
 * inline so standalone remote runs resolve them at compile time.
 */
function getExternalsFromConfig(config) {
  return Object.keys(config.shared ?? {});
}

/**
 * Wrap a non-Nx-Project metadata lookup so the angular builder's
 * `context.getProjectMetadata(name)` call works. Nx ExecutorContext already
 * carries the project graph snapshot — we project the data field.
 */
function makeProjectMetadataFetcher(nxContext) {
  return async (projectName) => {
    const node = nxContext.projectGraph?.nodes?.[projectName];
    return node?.data ?? {};
  };
}

/**
 * Bridge the Nx ExecutorContext into an Angular `BuilderContext`-shaped object
 * sufficient for the application builder to run.
 *
 * The application builder only needs a small surface of the context:
 *   - workspaceRoot, currentDirectory, target, projectName (from target)
 *   - logger
 *   - getProjectMetadata(name)  → returns project data object
 *   - signal / addTeardown for abort handling
 *   - reportStatus / reportRunning for progress UI
 *
 * Everything else (scheduleTarget, scheduleBuilder, analytics, telemetry) is
 * stubbed since we don't trigger cross-builder runs.
 */
function bridgeToBuilderContext(nxContext) {
  const target = nxContext.targetName && nxContext.target
    ? {
        project: nxContext.projectName ?? '',
        target: nxContext.targetName,
        configuration: nxContext.configurationName,
      }
    : undefined;

  // Build a robust logger. Nx ExecutorContext.logger is an @nrwl/devkit
  // LoggerApi (with createChild, fatal/error/warn/info/debug/verbose).
  // Angular's logMessages calls logger.info / logger.warn / logger.error
  // directly. We coerce any missing method to a console fallback so the
  // builder never crashes on undefined.
  const fallback = {
    fatal: (msg) => console.error(msg),
    error: (msg) => console.error(msg),
    warn: (msg) => console.warn(msg),
    info: (msg) => console.info(msg),
    debug: (msg) => console.debug(msg),
    verbose: (msg) => console.debug(msg),
    createChild: () => fallback,
  };
  const logger = {
    ...fallback,
    ...(nxContext.logger ?? {}),
    createChild: nxContext.logger?.createChild?.bind(nxContext.logger) ?? (() => fallback),
  };

  const teardowns = [];
  return {
    id: 0,
    builder: {
      builderName: '@angular/build:application',
      description: 'Federation application build (wrapped)',
      optionSchema: {},
    },
    logger,
    workspaceRoot: nxContext.root,
    currentDirectory: nxContext.cwd,
    target,
    scheduleTarget: async () => {
      throw new Error('scheduleTarget is not supported in federation-application executor');
    },
    scheduleBuilder: async () => {
      throw new Error('scheduleBuilder is not supported in federation-application executor');
    },
    getTargetOptions: async () => ({}),
    getProjectMetadata: makeProjectMetadataFetcher(nxContext),
    getBuilderNameForTarget: async () => '@angular/build:application',
    validateOptions: async (options) => /** @type {any} */ (options),
    reportRunning: () => {},
    reportStatus: (status) => {
      if (status) {
        logger.info(status);
      }
    },
    reportProgress: () => {},
    addTeardown: (teardown) => {
      teardowns.push(teardown);
    },
    signal: undefined,
  };
}

export default async function* runExecutor(options, context) {
  if (!options.federationConfig) {
    throw new Error(
      '[federation-application] options.federationConfig is required (path to federation.config.ts relative to workspaceRoot)',
    );
  }

  const config = await loadFederationConfig(options.federationConfig, context.root);
  const externals = getExternalsFromConfig(config);
  context.logger?.info?.(
    `[federation-application] externals: ${externals.length} npm packages from ${options.federationConfig} (currently a no-op; see MVP caveat)`,
  );

  const builderContext = bridgeToBuilderContext(context);
  // NOTE: the externals plugin is intentionally not applied in this MVP.
  // Marking shared npm packages as external in the Angular esbuild pipeline
  // breaks the federation chunks: the @softarc/native-federation-esbuild
  // library's `external` flag is a prefix match, so subpath imports like
  // `import "@angular/common/http"` inside the federation chunks are
  // externalized and left as bare specifiers. The importmap only knows
  // about base package names, so the browser cannot resolve those subpath
  // imports. Inline re-bundling the federation chunks would need the
  // Angular compiler plugin, which requires a heavy Angular compilation
  // setup. Documented as a follow-up in the PR description.
  const plugin = createNoopPlugin();

  const delegated = buildApplicationInternal(
    { ...options, incrementalResults: true },
    builderContext,
    { codePlugins: externals.length > 0 ? [plugin] : [] },
  );

  let didInitial = false;
  for await (const result of delegated) {
    const outputOptions = result.detail?.outputOptions;
    const isFailure = result.kind === ResultKind.Failure;
    const isFull = result.kind === ResultKind.Full || result.kind === ResultKind.Incremental;

    if (isFailure) {
      yield { success: false };
      continue;
    }

    if (isFull && outputOptions) {
      if (!didInitial) {
        didInitial = true;
      }
      builderContext.logger.info(`Output location: ${outputOptions.base}\n`);
      const ignoreServer = !!outputOptions.ignoreServer;
      await emitFilesToDisk(Object.entries(result.files ?? {}), async ([filePath, file]) => {
        if (
          ignoreServer &&
          (file.type === FILE_TYPE.ServerApplication || file.type === FILE_TYPE.ServerRoot)
        ) {
          return;
        }
        const fullFilePath = generateFullPath(filePath, file.type, outputOptions);
        await writeFileWithDirs(fullFilePath, file);
      });

      // After the Angular build writes its index.html, inline the
      // pre-generated importmap.json so the browser can resolve bare
      // imports of shared packages at module load time. This must run
      // AFTER emitFilesToDisk because Angular's build wipes + rewrites
      // the browser subdir, and we want the importmap to be the LAST
      // write so it survives.
      const browserDir = path.join(outputOptions.base, outputOptions.browser);
      await injectImportMapIntoIndexHtml({ browserDir, logger: builderContext.logger });

      // KNOWN LIMITATION: the federation chunks (written by
      // tools/federation-build.mjs) externalize ALL subpaths of a
      // shared package, leaving bare imports like `import "@angular/common/http"`
      // unresolved inside the chunk. Angular's main bundle subpath imports
      // are inlined (since we don't externalize subpaths), so the main app
      // works. The federation chunks have unresolved bare imports — they
      // will fail at runtime if a remote (via host's loadRemoteModule)
      // needs them. For this MVP, the host's main.js has all code inline
      // and the federation runtime's loadRemoteModule path isn't used in
      // the tested flow (the browser only loads the host). Documented in
      // the PR description as a follow-up.
    }

    yield {
      success: !result?.errors?.length,
      ...(outputOptions?.base ? { baseOutputPath: outputOptions.base } : {}),
    };
  }
}

/**
 * Build an esbuild plugin that marks a list of package names as external.
 *
 * Only BASE package names are externalized — subpath imports like
 * `@angular/common/http` are intentionally bundled inline.
 *
 * NOTE: Currently NOT applied in MVP — see the comment in `runExecutor`
 * above. Kept here for the planned follow-up once the federation
 * subpath-imports issue is resolved.
 */
function createExternalsPlugin(externals) {
  const escaped = externals.map((e) => e.replace(/[-/\\^$*+?.()|[\]{}]/g, '\\$&'));
  if (escaped.length === 0) {
    return {
      name: 'jordylab-federation-externals-empty',
      setup() {},
    };
  }
  // Match only exact base package names (followed by end of string).
  // `@angular/common` matches; `@angular/common/http` does NOT.
  const filter = new RegExp(`^(?:${escaped.join('|')})$`);
  return {
    name: 'jordylab-federation-externals',
    setup(build) {
      build.onResolve({ filter }, () => ({ external: true }));
    },
  };
}

/** No-op esbuild plugin — used as a placeholder when externals are disabled. */
function createNoopPlugin() {
  return {
    name: 'jordylab-federation-externals-noop',
    setup() {},
  };
}

function generateFullPath(filePath, fileType, outputOptions) {
  if (
    fileType === FILE_TYPE.ServerApplication ||
    fileType === FILE_TYPE.ServerRoot
  ) {
    return path.join(outputOptions.base, outputOptions.server, filePath);
  }
  return path.join(outputOptions.base, outputOptions.browser, filePath);
}

async function writeFileWithDirs(fullFilePath, file) {
  const { promises: fs } = await import('node:fs');
  await fs.mkdir(path.dirname(fullFilePath), { recursive: true });
  if (file.origin === 'memory') {
    await fs.writeFile(fullFilePath, file.contents);
  } else if (file.inputPath) {
    await fs.cp(file.inputPath, fullFilePath, { force: true });
  }
}

/**
 * Reads `importmap.json` from the browser output dir (written by the
 * separate federation-build target) and inlines its contents as a
 * `<script type="importmap">` tag inside the `<head>` of `index.html`.
 * If `importmap.json` is missing (e.g. federation-build hasn't been run
 * yet for a new app), this is a no-op with a warning.
 *
 * Per the import-maps spec 2.4.2, relative URLs in an importmap must start
 * with `/`, `./`, or `../`. Bare paths (e.g. `_angular_core.abc.js`) are
 * invalid and resolve to null in the browser. We prefix every value with
 * `./` if it doesn't already start with one.
 */
async function injectImportMapIntoIndexHtml({ browserDir, logger }) {
  const { promises: fs } = await import('node:fs');
  const importMapPath = path.join(browserDir, 'importmap.json');
  const indexHtmlPath = path.join(browserDir, 'index.html');

  let rawMap;
  try {
    const raw = await fs.readFile(importMapPath, 'utf-8');
    rawMap = JSON.parse(raw);
  } catch {
    logger.warn(
      `[federation-application] no importmap.json at ${importMapPath}; skipping importmap injection. ` +
        `Did you run the federation-build target for this app?`,
    );
    return;
  }

  const normalizedMap = {
    ...rawMap,
    imports: Object.fromEntries(
      Object.entries(rawMap.imports ?? {}).map(([k, v]) => [
        k,
        typeof v === 'string' && !/^(https?:|\/|\.\/|\.\.\/)/.test(v) ? `./${v}` : v,
      ]),
    ),
  };

  let html;
  try {
    html = await fs.readFile(indexHtmlPath, 'utf-8');
  } catch {
    return;
  }

  const importMapTag = `<script type="importmap" id="jordylab-federation-importmap">${JSON.stringify(normalizedMap)}</script>`;

  const existingTagRegex = /<script type="importmap" id="jordylab-federation-importmap"[^>]*>[\s\S]*?<\/script>/;
  if (existingTagRegex.test(html)) {
    html = html.replace(existingTagRegex, importMapTag);
  } else {
    const headCloseIndex = html.indexOf('</head>');
    if (headCloseIndex === -1) {
      logger.error(`[federation-application] cannot find </head> in ${indexHtmlPath}`);
      return;
    }
    html = html.slice(0, headCloseIndex) + importMapTag + '\n' + html.slice(headCloseIndex);
  }

  await fs.writeFile(indexHtmlPath, html);
  logger.info(`[federation-application] inlined importmap into ${path.basename(indexHtmlPath)}`);
}
