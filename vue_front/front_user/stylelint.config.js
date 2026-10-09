export default {
  extends: ['stylelint-config-standard-scss'],
  rules: {
    'color-no-hex': true,
    'selector-pseudo-class-no-unknown': [true, { ignorePseudoClasses: ['deep'] }],
    'selector-class-pattern': '^[a-z][a-z0-9]*([-_]+[a-z0-9]+)*$',
    'color-named': 'never',
    'function-disallowed-list': ['rgb', 'rgba', 'hsl', 'hsla'],
  },
  overrides: [
    {
      files: ['src/styles/tokens.scss'],
      rules: {
        'color-no-hex': null,
        'color-named': null,
        'function-disallowed-list': null,
      },
    },
  ],
}
