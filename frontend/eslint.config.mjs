import { defineConfig, globalIgnores } from 'eslint/config';
import nextVitals from 'eslint-config-next/core-web-vitals';
import nextTs from 'eslint-config-next/typescript';
import sonarjs from 'eslint-plugin-sonarjs';
export default defineConfig([
  ...nextVitals, ...nextTs,
  { plugins: { sonarjs }, rules: { 'sonarjs/cognitive-complexity': ['error', 7] } },
  globalIgnores(['.next/**', 'next-env.d.ts']),
]);
