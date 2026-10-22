import * as React from 'react'
import { cn } from '../../utils/utils'

/**
 * Reusable Badge component matching WellNest pill tags.
 */
export function Badge({ className, variant = 'default', children, ...props }) {
  const variants = {
    default: 'bg-[#142534] text-white',
    mint: 'bg-[#d8f8f3] text-[#0c7a6d] font-semibold',
    secondary: 'bg-[#e8faf6] text-[#0c7a6d]',
    success: 'bg-[#d8f8f3] text-[#0c7a6d]',
    warning: 'bg-[#fef6e4] text-[#b45309]',
    destructive: 'bg-[#fdece8] text-[#d9534f]',
    outline: 'border border-[#d5e0dc] text-[#142534] bg-white',
    navy: 'bg-[#142534] text-white',
  }

  return (
    <span
      className={cn(
        'inline-flex items-center px-3 py-1 rounded-full text-xs font-medium tracking-wide transition-colors',
        variants[variant] || variants.default,
        className,
      )}
      {...props}
    >
      {children}
    </span>
  )
}
