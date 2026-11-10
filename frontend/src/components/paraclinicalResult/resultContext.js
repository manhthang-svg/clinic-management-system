import { createContext } from 'react';

/** What the result API in use can do; the real API cannot store images yet (TBD: file storage). */
export const ResultApiContext = createContext({ supportsFiles: true });
