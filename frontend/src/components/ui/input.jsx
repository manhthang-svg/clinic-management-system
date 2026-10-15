import * as React from 'react'
import { cn } from '../../lib/utils'

/**
 * Reusable Input component following shadcn/ui patterns and WellNest styling.
 */
export const Input = React.forwardRef(
  ({ className, type = 'text', leadingIcon, trailingIcon, ...props }, ref) => {
    return (
      <div className="relative flex items-center w-full">
        {leadingIcon && (
          <div className="absolute left-3.5 flex items-center pointer-events-none text-[#7e909a]">
            {leadingIcon}
          </div>
        )}
        <input
          type={type}
          ref={ref}
          className={cn(
            'flex w-full h-11 rounded-xl border border-[#d5e0dc] bg-[#fbfdfe] px-4 py-2 text-sm text-[#142534] transition-all duration-150',
            'placeholder:text-[#94a3b8] focus:bg-white focus:border-[#14b8a6] focus:outline-none focus:ring-4 focus:ring-[#14b8a6]/15',
            'disabled:cursor-not-allowed disabled:bg-[#f1f5f9] disabled:text-[#94a3b8]',
            leadingIcon && 'pl-11',
            trailingIcon && 'pr-11',
            className,
          )}
          {...props}
        />
        {trailingIcon && (
          <div className="absolute right-3.5 flex items-center text-[#7e909a]">
            {trailingIcon}
          </div>
        )}
      </div>
    )
  },
)

Input.displayName = 'Input'
