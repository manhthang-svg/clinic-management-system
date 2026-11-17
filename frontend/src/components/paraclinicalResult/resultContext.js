import { createContext } from 'react';

/**
 * What the result API in use can do. uploadImage / deleteImage are set when the API stores images (the real one,
 * on Cloudinary); without them the files stay a local preview (demo API).
 */
export const ResultApiContext = createContext({ supportsFiles: true });
