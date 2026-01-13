// Global theme colors for the application
export const colors = {
  // Primary colors
  primary: {
    DEFAULT: "#171717",
    light: "#383838",
    dark: "#0a0a0a",
  },
  
  // Text colors
  foreground: {
    light: "#171717",
    dark: "#ededed",
  },
  
  // Brand colors
  accent: {
    DEFAULT: "#0070f3",
    hover: "#0051cc",
  },
  
} as const;

// Export as CSS variables string for inline styles
export const colorsAsCssVars = {
  "--color-primary": colors.primary.DEFAULT,
  "--color-primary-light": colors.primary.light,
  "--color-primary-dark": colors.primary.dark,
  "--color-foreground-light": colors.foreground.light,
  "--color-foreground-dark": colors.foreground.dark,
  "--color-accent": colors.accent.DEFAULT,
  "--color-accent-hover": colors.accent.hover,
} as const;

// Type for theme colors
export type ThemeColors = typeof colors;
