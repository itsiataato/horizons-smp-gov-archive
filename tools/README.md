# Regenerating the bundled law JSON

The website is the source of truth:

```
../saf-baby-website/app/documentation/rules/law/law.ts
```

Gov ships a generated copy at:

```
src/main/resources/data/gov/law/season10.json
```

## When the website law changes

From this project directory run:

```bash
node tools/generate-law-json.mjs
gradle build --stacktrace
```

The script uses the TypeScript compiler already installed in the website's
`node_modules`. It transpiles `law.ts` in memory, evaluates the exported data,
maps each article's `text` field to the mod's `body` field, and writes the JSON.
It does not download anything and does not need a second TypeScript install.

## Checks performed by the generator

- exactly 12 chapters and exactly 159 articles;
- no duplicate chapter or article ids;
- every chapter and article has its required non-empty string fields;
- every article body is copied verbatim from the website source;
- `penalty` is preserved when present and written as `null` when the source
  article has no penalty;
- article 5.13 still contains Schedule 5.A and all seven permit classes;
- article 6.09 still contains the Q, C, T, H, and P vehicle licenses;
- articles 6.13 and 6.14 still exist.

If the law legitimately gains or loses a chapter or article, update
`EXPECTED_CHAPTERS` / `EXPECTED_ARTICLES` in the script only after reviewing
the new law. The build itself does not enforce those totals, but this generator
does, so a silent truncation cannot ship.

## Website in a different location

The default is the sibling checkout `../../saf-baby-website` relative to this
project. Override it with:

```bash
HORIZONS_WEBSITE_ROOT=/path/to/saf-baby-website node tools/generate-law-json.mjs
```

The website must have its dependencies installed (`npm install` in the website
project) because the generator deliberately loads the website's own
`typescript` package.
