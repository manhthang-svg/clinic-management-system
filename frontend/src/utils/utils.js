import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Merges CSS class names using clsx and tailwind-merge.
 */
export function cn(...inputs) {
  return twMerge(clsx(inputs));
}

export default cn;
