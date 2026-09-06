'use client';
import { createTheme, CssBaseline, ThemeProvider } from '@mui/material';
import { AppRouterCacheProvider } from '@mui/material-nextjs/v15-appRouter';

const theme = createTheme({
  palette: {
    primary: { main: '#216b52', light: '#e8f2ed', dark: '#154d3a', contrastText: '#ffffff' },
    secondary: { main: '#c98130' }, background: { default: '#f6f7f5', paper: '#ffffff' },
    text: { primary: '#202b27', secondary: '#6b7771' }, divider: '#e8ece8',
    error: { main: '#b84d42' }, warning: { main: '#a56624' }, success: { main: '#216b52' },
  },
  typography: { fontFamily: 'Arial, Helvetica, sans-serif', fontSize: 14,
    h4: { fontSize: '1.8rem', fontWeight: 700, letterSpacing: '-0.05em' },
    h5: { fontSize: '1.3rem', fontWeight: 700, letterSpacing: '-0.03em' },
    h6: { fontSize: '1rem', fontWeight: 700 }, body1: { fontSize: '1rem', lineHeight: 1.65 },
    body2: { fontSize: '0.875rem', lineHeight: 1.5 }, button: { textTransform: 'none', fontWeight: 600 },
  }, shape: { borderRadius: 12 },
  components: {
    MuiButton: { defaultProps: { disableElevation: true }, styleOverrides: { root: { minHeight: 44, borderRadius: 10 } } },
    MuiIconButton: { styleOverrides: { root: { minWidth: 44, minHeight: 44 } } },
    MuiChip: { styleOverrides: { root: { borderRadius: 6 }, sizeSmall: { height: 26, fontSize: 12 } } },
    MuiOutlinedInput: { styleOverrides: { root: { backgroundColor: '#f6f7f5', borderRadius: 10 }, notchedOutline: { borderColor: '#e8ece8' } } },
    MuiPaper: { defaultProps: { elevation: 0 } },
    MuiCssBaseline: { styleOverrides: { body: { overscrollBehavior: 'none' }, '*': { boxSizing: 'border-box' }, '::selection': { background: '#d4e9dd' }, '@media (prefers-reduced-motion: reduce)': { '*': { animationDuration: '0.01ms !important', transitionDuration: '0.01ms !important', scrollBehavior: 'auto !important' } } } },
  },
});
export function Providers({ children }: { children: React.ReactNode }) {
  return <AppRouterCacheProvider><ThemeProvider theme={theme}><CssBaseline />{children}</ThemeProvider></AppRouterCacheProvider>;
}
