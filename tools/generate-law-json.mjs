#!/usr/bin/env node
/**
 * Regenerate the bundled Season 10 Law data from the website's source of truth.
 *
 * The website keeps the law in a plain-data TypeScript module:
 *   saf-baby-website/app/documentation/rules/law/law.ts
 *
 * This script transpiles that file in memory with the TypeScript compiler that
 * the website already has installed, evaluates the CommonJS output in a tiny
 * module wrapper, and writes the result to:
 *   src/main/resources/data/gov/law/season10.json
 *
 * No network access, no separate TypeScript install, and no hand-copied
 * articles. Run it again after any website law change:
 *
 *   node tools/generate-law-json.mjs
 */
import { mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const EXPECTED_CHAPTERS = 12;
const EXPECTED_ARTICLES = 159;

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(scriptDir, '..');
const websiteRoot = process.env.HORIZONS_WEBSITE_ROOT
  ? path.resolve(process.env.HORIZONS_WEBSITE_ROOT)
  : path.resolve(projectRoot, '../../saf-baby-website');
const lawSourcePath = path.join(
  websiteRoot,
  'app',
  'documentation',
  'rules',
  'law',
  'law.ts',
);
const outputPath = path.join(
  projectRoot,
  'src',
  'main',
  'resources',
  'data',
  'gov',
  'law',
  'season10.json',
);

function fail(message) {
  console.error(`generate-law-json: ${message}`);
  process.exitCode = 1;
  throw new Error(message);
}

function readWebsiteTypeScript() {
  try {
    const websiteRequire = createRequire(path.join(websiteRoot, 'package.json'));
    const ts = websiteRequire('typescript');
    if (!ts || typeof ts.transpileModule !== 'function') {
      fail(`the TypeScript package at ${websiteRoot} is not usable`);
    }
    return ts;
  } catch (error) {
    fail(
      `cannot load the website's TypeScript package from ${websiteRoot}. ` +
        `Install website dependencies first (npm install in saf-baby-website). ` +
        `Original error: ${error.message}`,
    );
  }
}

async function readLawSource() {
  try {
    return await readFile(lawSourcePath, 'utf8');
  } catch (error) {
    fail(`cannot read ${lawSourcePath}: ${error.message}`);
  }
}

function evaluateLawModule(ts, source) {
  const transpiled = ts.transpileModule(source, {
    fileName: lawSourcePath,
    reportDiagnostics: true,
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
      esModuleInterop: true,
      isolatedModules: true,
      removeComments: false,
    },
  });

  const errors = (transpiled.diagnostics ?? []).filter(
    (diagnostic) => diagnostic.category === ts.DiagnosticCategory.Error,
  );
  if (errors.length > 0) {
    const messages = errors.map((diagnostic) => {
      const text = ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n');
      if (diagnostic.file && typeof diagnostic.start === 'number') {
        const position = diagnostic.file.getLineAndCharacterOfPosition(diagnostic.start);
        return `${diagnostic.file.fileName}:${position.line + 1}:${position.character + 1} ${text}`;
      }
      return text;
    });
    fail(`TypeScript reported errors:\n${messages.join('\n')}`);
  }

  const module = { exports: {} };
  const wrapped = new Function(
    'exports',
    'module',
    'require',
    '__filename',
    '__dirname',
    transpiled.outputText,
  );
  const websiteRequire = createRequire(path.join(websiteRoot, 'package.json'));
  wrapped(module.exports, module, websiteRequire, lawSourcePath, path.dirname(lawSourcePath));
  return module.exports;
}

function requireString(value, label) {
  if (typeof value !== 'string' || value.length === 0) {
    fail(`${label} must be a non-empty string`);
  }
  return value;
}

function requireArticles(chapter, chapterIndex) {
  if (!Array.isArray(chapter.articles) || chapter.articles.length === 0) {
    fail(`chapter ${chapterIndex + 1} has no articles`);
  }
  return chapter.articles.map((article, articleIndex) => {
    const where = `chapter ${chapterIndex + 1}, article ${articleIndex + 1}`;
    const id = requireString(article.id, `${where}: id`);
    const title = requireString(article.title, `${where}: title`);
    const body = requireString(article.text, `${where}: text`);
    if (article.penalty !== undefined && typeof article.penalty !== 'string') {
      fail(`${where}: penalty must be a string when present`);
    }
    return {
      id,
      title,
      body,
      penalty: article.penalty ?? null,
    };
  });
}

function buildPayload(lawModule) {
  const version = requireString(lawModule.LAW_VERSION, 'LAW_VERSION');
  const lastUpdated = requireString(lawModule.LAW_LAST_UPDATED, 'LAW_LAST_UPDATED');
  if (!Array.isArray(lawModule.lawChapters)) {
    fail('lawChapters must be an array');
  }
  if (lawModule.lawChapters.length !== EXPECTED_CHAPTERS) {
    fail(
      `expected exactly ${EXPECTED_CHAPTERS} chapters, found ${lawModule.lawChapters.length}. ` +
        `Update EXPECTED_CHAPTERS in this generator only after reviewing the new law.`,
    );
  }

  const seenChapterIds = new Set();
  const seenArticleIds = new Set();
  const chapters = lawModule.lawChapters.map((chapter, chapterIndex) => {
    const where = `chapter ${chapterIndex + 1}`;
    const id = requireString(chapter.id, `${where}: id`);
    if (seenChapterIds.has(id)) {
      fail(`duplicate chapter id ${id}`);
    }
    seenChapterIds.add(id);

    const articles = requireArticles(chapter, chapterIndex);
    for (const article of articles) {
      if (seenArticleIds.has(article.id)) {
        fail(`duplicate article id ${article.id}`);
      }
      seenArticleIds.add(article.id);
    }

    return {
      id,
      numeral: requireString(chapter.numeral, `${where}: numeral`),
      title: requireString(chapter.title, `${where}: title`),
      summary: requireString(chapter.summary, `${where}: summary`),
      category: requireString(chapter.category, `${where}: category`),
      articles,
    };
  });

  const articleCount = chapters.reduce((total, chapter) => total + chapter.articles.length, 0);
  if (articleCount !== EXPECTED_ARTICLES) {
    fail(
      `expected exactly ${EXPECTED_ARTICLES} articles, found ${articleCount}. ` +
        `Update EXPECTED_ARTICLES in this generator only after reviewing the new law.`,
    );
  }

  verifyRequiredArticles(chapters);

  return {
    version,
    lastUpdated,
    chapters,
  };
}

function requireArticle(chapters, id) {
  for (const chapter of chapters) {
    for (const article of chapter.articles) {
      if (article.id === id) {
        return article;
      }
    }
  }
  fail(`required article ${id} is missing from the law`);
}

function requireContains(article, fragments) {
  for (const fragment of fragments) {
    if (!article.body.includes(fragment)) {
      fail(
        `article ${article.id} is missing required text ${JSON.stringify(fragment)}; ` +
          `the website law may have changed`,
      );
    }
  }
}

/**
 * These checks anchor the 139-article revision this mod was delivered against. They are
 * deliberately narrow: they verify the specific permit and vehicle-license text the release
 * depends on, while still allowing prose edits elsewhere.
 */
function verifyRequiredArticles(chapters) {
  requireContains(requireArticle(chapters, '5.13'), [
    'Schedule 5.A',
    'Class L',
    'Class S',
    'Class R',
    'Class P',
    'Class H',
    'Class D',
    'Class G',
  ]);
  requireContains(requireArticle(chapters, '6.09'), [
    'Quad License (Q)',
    'Car License (C)',
    'Truck License (T)',
    'Helicopter License (H)',
    'Plane License (P)',
  ]);
  requireArticle(chapters, '6.13');
  requireArticle(chapters, '6.14');
}

async function main() {
  const ts = readWebsiteTypeScript();
  const source = await readLawSource();
  const lawModule = evaluateLawModule(ts, source);
  const payload = buildPayload(lawModule);

  await mkdir(path.dirname(outputPath), { recursive: true });
  // Write to a temp file in the same directory and rename over the target so a
  // concurrent reader (for example a running Gradle build) never sees a
  // partially written JSON file.
  const tempPath = `${outputPath}.${process.pid}.tmp`;
  try {
    await writeFile(tempPath, `${JSON.stringify(payload, null, 2)}\n`, 'utf8');
    await rename(tempPath, outputPath);
  } catch (error) {
    await rm(tempPath, { force: true }).catch(() => {});
    throw error;
  }

  const articleCount = payload.chapters.reduce(
    (total, chapter) => total + chapter.articles.length,
    0,
  );
  console.log(`Generated ${outputPath}`);
  console.log(`  version: ${payload.version}`);
  console.log(`  lastUpdated: ${payload.lastUpdated}`);
  console.log(`  chapters: ${payload.chapters.length}`);
  console.log(`  articles: ${articleCount}`);
  console.log("  required articles: 5.13, 6.09, 6.13, 6.14 verified");
  console.log(`  first article: ${payload.chapters[0].articles[0].id}`);
  console.log(
    `  last article: ${payload.chapters.at(-1).articles.at(-1).id}`,
  );
}

await main();
