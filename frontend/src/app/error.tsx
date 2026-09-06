'use client';
import { Alert, Box, Button } from '@mui/material';
export default function ErrorPage({ reset }: { reset: () => void }) {
  return <Box sx={{ maxWidth: 480, mx: 'auto', mt: 8, p: 3 }}><Alert severity="error">The inbox could not be displayed. Your saved messages are safe.</Alert><Button onClick={reset}>Try again</Button></Box>;
}
