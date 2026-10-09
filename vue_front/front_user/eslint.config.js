import js from '@eslint/js'
import vue from 'eslint-plugin-vue'
import i18n from '@intlify/eslint-plugin-vue-i18n'
import globals from 'globals'
export default [
  { ignores: ['node_modules/**', 'dist/**'] },
  js.configs.recommended,
  ...vue.configs['flat/recommended'],
  {
    languageOptions: { globals: { ...globals.browser, ...globals.node } },
    plugins: { '@intlify/vue-i18n': i18n },
    rules: {
      'no-console': 'error',
      'no-debugger': 'error',
      '@intlify/vue-i18n/no-raw-text': [
        'error',
        { attributes: { '/.+/': ['title', 'placeholder', 'aria-label', 'alt'] } },
      ],
    },
  },
]
